// yacySearch.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 13.06.2004
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
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.yacy;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;

import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.plasma.plasmaSnippetCache;
import de.anomic.plasma.plasmaCrawlLURL;
import de.anomic.plasma.plasmaSearch;

public class yacySearch extends Thread {

    private Set wordhashes;
    private int count;
    private boolean global;
    private plasmaCrawlLURL urlManager;
    private plasmaSearch searchManager;
    private plasmaSnippetCache snippetCache;
    private yacySeed targetPeer;
    private int links;
    private long duetime;

    public yacySearch(Set wordhashes, int count, boolean global, yacySeed targetPeer,
		      plasmaCrawlLURL urlManager, plasmaSearch searchManager, plasmaSnippetCache snippetCache, long duetime) {
        super("yacySearch_" + targetPeer.getName());
        this.wordhashes = wordhashes;
        this.count = count;
        this.global = global;
        this.urlManager = urlManager;
        this.searchManager = searchManager;
        this.snippetCache = snippetCache;
        this.targetPeer = targetPeer;
        this.links = -1;
        this.duetime = duetime;
    }

    public void run() {
        this.links = yacyClient.search(set2string(wordhashes), count, global, targetPeer, urlManager, searchManager, snippetCache, duetime);
        if (links != 0) {
            //yacyCore.log.logInfo("REMOTE SEARCH - remote peer '" + targetPeer.get("Name", "anonymous") + "' contributed " + links + " links for word hash " + wordhashes);
            yacyCore.seedDB.mySeed.incRI(links);
            yacyCore.seedDB.mySeed.incRU(links);
        }
    }
    
    public static String set2string(Set hashes) {
        String wh = "";
        Iterator i = hashes.iterator();
        while (i.hasNext()) wh = wh + (String) i.next();
        return wh;
    }
    
    public int links() {
        return this.links;
    }

    private static yacySeed[] selectPeers(Set wordhashes, int seedcount) {
	// find out a specific number of seeds, that would be relevant for the given word hash(es)
	// the result is ordered by relevance: [0] is most relevant
	// the seedcount is the maximum number of wanted results
	if (yacyCore.seedDB == null) return null;
	if (seedcount > yacyCore.seedDB.sizeConnected()) seedcount = yacyCore.seedDB.sizeConnected();

        kelondroMScoreCluster ranking = new kelondroMScoreCluster();
        yacySeed seed;
        Enumeration dhtEnum; 
        Iterator i = wordhashes.iterator();
        int c;
        while (i.hasNext()) {
            dhtEnum = yacyCore.dhtAgent.getDHTSeeds(true, (String) i.next());
            c = 0;
            while ((dhtEnum.hasMoreElements()) && (c < seedcount)) {
                seed = (yacySeed) dhtEnum.nextElement();
                ranking.addScore(seed.hash, c++);
            }
        }
        if (ranking.size() < seedcount) seedcount = ranking.size();
        yacySeed[] result = new yacySeed[seedcount];
        Iterator e = ranking.scores(true);
        c = 0;
        while ((e.hasNext()) && (c < result.length))
            result[c++] = yacyCore.seedDB.getConnected((String) e.next());
            
	//System.out.println("DEBUG yacySearch.selectPeers = " + seedcount + " seeds:"); for (int i = 0; i < seedcount; i++) System.out.println(" #" + i + ":" + result[i]); // debug
	return result;
    }
    
    public static int searchHashes(Set wordhashes, plasmaCrawlLURL urlManager, plasmaSearch searchManager,
			     int count, int targets, plasmaSnippetCache snippetCache, long waitingtime) {
        // check own peer status
        if ((yacyCore.seedDB.mySeed == null) || (yacyCore.seedDB.mySeed.getAddress() == null)) return 0;
        
        // start delay control
        long start = System.currentTimeMillis();
        
        // set a duetime for clients
        long duetime = waitingtime - 4000; // subtract network traffic overhead, guessed 4 seconds
        if (duetime < 1000) duetime = 1000;
        
        // prepare seed targets and threads
        //Set wordhashes = plasmaSearch.words2hashes(querywords);
        yacySeed[] targetPeers = selectPeers(wordhashes, targets);
        if (targetPeers == null) return 0;
        targets = targetPeers.length;
        if (targets == 0) return 0;
        yacySearch[] searchThreads = new yacySearch[targets];
        for (int i = 0; i < targets; i++) {
            searchThreads[i]= new yacySearch(wordhashes, count, true, targetPeers[i],
                    urlManager, searchManager, snippetCache, duetime);
            searchThreads[i].start();
            try {Thread.currentThread().sleep(20);} catch (InterruptedException e) {}
            if ((System.currentTimeMillis() - start) > waitingtime) {
                targets = i + 1;
                break;
            }
        }

        int c;
        // wait until wanted delay passed or wanted result appeared
        boolean anyIdle = true;
        while ((anyIdle) && ((System.currentTimeMillis() - start) < waitingtime)) {
            // wait..
            try {Thread.currentThread().sleep(200);} catch (InterruptedException e) {}
            // check if all threads have been finished or results so far are enough
            c = 0;
            anyIdle = false;
            for (int i = 0; i < targets; i++) {
                if (searchThreads[i].links() < 0) anyIdle = true; else c = c + searchThreads[i].links();
            }
            if ((c >= count * 3) && ((System.currentTimeMillis() - start) > (waitingtime * 2 / 3))) {
                System.out.println("DEBUG yacySearch: c=" + c + ", count=" + count + ", waitingtime=" + waitingtime);
                break; // we have enough
            }
            if (c >= count * 5) break;
        }

        // collect results
        c = 0;
        for (int i = 0; i < targets; i++) c = c + ((searchThreads[i].links() > 0) ? searchThreads[i].links() : 0);
        return c;
    }

}
