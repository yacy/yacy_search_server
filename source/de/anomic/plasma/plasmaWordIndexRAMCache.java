// plasmaIndexRAMCache.java
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 22.12.2004
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// compile with
// javac -classpath classes -sourcepath source -d classes -g source/de/anomic/plasma/*.java


package de.anomic.plasma;

import java.io.*;
import java.util.*;
import de.anomic.yacy.*;
import de.anomic.server.*;
import de.anomic.kelondro.*;

public class plasmaWordIndexRAMCache extends Thread {

    static String minKey, maxKey;

    // class variables
    TreeMap cache;
    kelondroMScoreCluster hashScore;
    plasmaWordIndexFileCache pic;
    boolean terminate;
    long terminateUntil;
    int maxWords;

    static {
	maxKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += 'z';
	minKey = "";
	for (int i = 0; i < yacySeedDB.commonHashLength; i++) maxKey += '-';
    }

    public plasmaWordIndexRAMCache(File databaseRoot, int maxWords, int bufferkb) throws IOException {
	this.pic = new plasmaWordIndexFileCache(databaseRoot, bufferkb);
	this.cache = new TreeMap();
	this.hashScore = new kelondroMScoreCluster();
	this.maxWords = maxWords;
	this.terminate = false;
    }
    
    public void run() {
	serverLog.logSystem("PLASMA INDEXING", "started word cache management");
	int check;
	// permanently flush cache elements
        while (!(terminate)) {
	    if (hashScore.size() < 100) try {Thread.currentThread().sleep(10000);} catch (InterruptedException e) {}
            while ((!(terminate)) && (cache != null) && (hashScore.size() > 0)) try {
		//check = hashScore.size();
		flushSpecific(true);
		//serverLog.logDebug("PLASMA INDEXING", "single flush. bevore=" + check + "; after=" + hashScore.size());
                try {Thread.currentThread().sleep(200 + (maxWords / (1 + hashScore.size())));} catch (InterruptedException e) {}
            } catch (IOException e) {
		serverLog.logError("PLASMA INDEXING", "PANIK! exception in main cache loop: " + e.getMessage());
                e.printStackTrace();
		terminate = true;
                cache = null;
	    }
	}

	serverLog.logSystem("PLASMA INDEXING", "CATCHED TERMINATION SIGNAL: start final flush");

        // close all;
	try {
	    // first flush everything
	    while ((hashScore.size() > 0) && (System.currentTimeMillis() < terminateUntil)) {
                flushSpecific(false);
            }

	    // then close file cache:
	    pic.close();
	} catch (IOException e) {
	    serverLog.logDebug("PLASMA INDEXING", "interrupted final flush: " + e.toString());
	}
        // report
        if (hashScore.size() == 0)
            serverLog.logSystem("PLASMA INDEXING", "finished final flush; flushed all words");
        else
            serverLog.logError("PLASMA INDEXING", "terminated final flush; " + hashScore.size() + " words lost");

	// delete data
	cache = null;
	hashScore = null;
	
    }

    public void close(int waitingBoundSeconds) {
        terminate = true;
	// wait until terination is done
	// we can do at least 6 flushes/second
	int waitingtime = 10 + (((cache == null) ? 0 : cache.size()) / 5); // seconds
	if (waitingtime > waitingBoundSeconds) waitingtime = waitingBoundSeconds; // upper bound
        this.terminateUntil = System.currentTimeMillis() + (waitingtime * 1000);
        terminate = true;
	while ((cache != null) && (waitingtime > 0)) {
	    serverLog.logDebug("PLASMA INDEXING", "final word flush; cache.size=" + cache.size() + "; time-out in " + waitingtime + " seconds");
	    try {Thread.currentThread().sleep(5000);} catch (InterruptedException e) {}
	    waitingtime -= 5;
	}
    }

    private synchronized int flushSpecific(boolean greatest) throws IOException {
	//System.out.println("DEBUG: plasmaIndexRAMCache.flushSpecific(" + ((greatest) ? "greatest" : "smallest") + "); cache.size() = " + cache.size());
	if ((hashScore.size() == 0) && (cache.size() == 0)) {
	    serverLog.logDebug("PLASMA INDEXING", "flushSpecific: called but cache is empty");
	    return 0;
	}
	if ((hashScore.size() == 0) && (cache.size() != 0)) {
	    serverLog.logError("PLASMA INDEXING", "flushSpecific: hashScore.size=0 but cache.size=" + cache.size());
	    return 0;
	}
	if ((hashScore.size() != 0) && (cache.size() == 0)) {
	    serverLog.logError("PLASMA INDEXING", "flushSpecific: hashScore.size=" + hashScore.size() + " but cache.size=0");
	    return 0;
	}

	//serverLog.logDebug("PLASMA INDEXING", "flushSpecific: hashScore.size=" + hashScore.size() + ", cache.size=" + cache.size());

	String key = (String) ((greatest) ? hashScore.getMaxObject() : hashScore.getMinObject());
	return flushKey(key, "flushSpecific");
    }

    private synchronized int flushKey(String key, String caller) throws IOException {
	Vector v = (Vector) cache.get(key);
	if (v == null) {
	    //serverLog.logDebug("PLASMA INDEXING", "flushKey: '" + caller + "' forced to flush non-existing key " + key);
	    return 0;
	}
	pic.addEntriesToIndex(key, v);
	cache.remove(key);
	hashScore.deleteScore(key);
	return v.size();
    }

    public synchronized Iterator wordHashesMem(String wordHash, int count) throws IOException {
	// returns a list of hashes from a specific start point
	// we need to flush some of the elements in the cache first
	// maybe we flush too much, but this is not easy to find out and it does not matter
	TreeMap subMap = new TreeMap(cache.subMap((wordHash == null) ? minKey : wordHash, maxKey));
        int flushcount = subMap.size();
        if (flushcount > count) flushcount = count;
	String key;
	for (int i = 0; i < flushcount ; i++) {
	    key = (String) subMap.firstKey();
	    flushKey(key, "getSequentialWordHashesMem");
	    subMap.remove(key);
	}
	// finally return the result from the underlying hash list:
	return pic.wordHashes(wordHash, true);
    }

    public plasmaWordIndexEntity getIndexMem(String wordHash, boolean deleteIfEmpty) throws IOException {
	flushKey(wordHash, "getIndexMem");
	return pic.getIndex(wordHash, deleteIfEmpty);
    }

    public synchronized int addEntryToIndexMem(String wordHash, plasmaWordIndexEntry entry) throws IOException {
	// make space for new words
	int flushc = 0;
	//serverLog.logDebug("PLASMA INDEXING", "addEntryToIndexMem: cache.size=" + cache.size() + "; hashScore.size=" + hashScore.size());
	while (hashScore.size() > maxWords) flushc += flushSpecific(false);
	//if (flushc > 0) serverLog.logDebug("PLASMA INDEXING", "addEntryToIndexMem - flushed " + flushc + " entries");

	// put new words into cache
	Vector v = (Vector) cache.get(wordHash); // null pointer exception? wordhash != null! must be cache==null
	if (v == null) v = new Vector();
	v.add(entry);
	cache.put(wordHash, v);
	hashScore.incScore(wordHash);
	return flushc;
    }
    
    public synchronized void deleteComplete(String wordHash) throws IOException {
	cache.remove(wordHash);
	hashScore.deleteScore(wordHash);
	pic.deleteComplete(wordHash);
    }

    public int removeEntriesMem(String wordHash, String[] urlHashes, boolean deleteComplete) throws IOException {
	flushKey(wordHash, "removeEntriesMem");
        return pic.removeEntries(wordHash, urlHashes, deleteComplete);
    }

    public int sizeMin() {
	// it is not easy to find out the correct size of the cache
	// to make the result correct, it would be necessary to flush the complete ram cache
	// instead, we return the minimum size of the cache, which is the maximun of either the
	// ram or table cache
	if ((hashScore == null) || (pic == null)) return 0;
	return (hashScore.size() < pic.size()) ? pic.size() : hashScore.size();
    }


}
