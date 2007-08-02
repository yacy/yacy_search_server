// plasmaCrawlLURL.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

/*
   This class provides storage functions for the plasma search engine.
   - the url-specific properties, including condenser results
   - the text content of the url
   Both entities are accessed with a hash, which is based on the MD5
   algorithm. The MD5 is not encoded as a hex value, but a b64 value.
*/

package de.anomic.plasma;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import de.anomic.http.httpc;
import de.anomic.http.httpc.response;
import de.anomic.index.indexRWIEntry;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroFlexSplitTable;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRow;
import de.anomic.net.URL;
import de.anomic.plasma.urlPattern.plasmaURLPattern;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;

public final class plasmaCrawlLURL {

    // result stacks;
    // these have all entries of form
    // strings: urlHash + initiatorHash + ExecutorHash
    private final LinkedList externResultStack; // 1 - remote index: retrieved by other peer
    private final LinkedList searchResultStack; // 2 - partly remote/local index: result of search queries
    private final LinkedList transfResultStack; // 3 - partly remote/local index: result of index transfer
    private final LinkedList proxyResultStack;  // 4 - local index: result of proxy fetch/prefetch
    private final LinkedList lcrawlResultStack; // 5 - local index: result of local crawling
    private final LinkedList gcrawlResultStack; // 6 - local index: triggered external

    // the class object
    private kelondroIndex urlIndexFile;

    public plasmaCrawlLURL(File indexPath, long preloadTime) {
        super();

        urlIndexFile = new kelondroFlexSplitTable(new File(indexPath, "PUBLIC/TEXT"), "urls", preloadTime, indexURLEntry.rowdef, false);

        // init result stacks
        externResultStack = new LinkedList();
        searchResultStack = new LinkedList();
        transfResultStack = new LinkedList();
        proxyResultStack  = new LinkedList();
        lcrawlResultStack = new LinkedList();
        gcrawlResultStack = new LinkedList();
    }

    public int size() {
        return urlIndexFile.size();
    }

    public void close() {
        if (urlIndexFile != null) {
                urlIndexFile.close();
            urlIndexFile = null;
        }
    }

    public synchronized void stack(indexURLEntry e, String initiatorHash, String executorHash, int stackType) {
        if (e == null) { return; }
        try {
            if (initiatorHash == null) { initiatorHash = plasmaURL.dummyHash; }
            if (executorHash == null) { executorHash = plasmaURL.dummyHash; }
            switch (stackType) {
                case 0: break;
                case 1: externResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 2: searchResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 3: transfResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 4: proxyResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 5: lcrawlResultStack.add(e.hash() + initiatorHash + executorHash); break;
                case 6: gcrawlResultStack.add(e.hash() + initiatorHash + executorHash); break;
            }
            return;
        } catch (Exception ex) {
            System.out.println("INTERNAL ERROR in newEntry/2: " + ex.toString());
            return;
        }
    }

    public synchronized void notifyGCrawl(String urlHash, String initiatorHash, String executorHash) {
        gcrawlResultStack.add(urlHash + initiatorHash + executorHash);
    }

    public synchronized void flushCacheSome() {
        try {
            if (urlIndexFile instanceof kelondroFlexSplitTable) ((kelondroFlexSplitTable) urlIndexFile).flushSome();
            if (urlIndexFile instanceof kelondroCache) ((kelondroCache) urlIndexFile).flushSome();
        } catch (IOException e) {}
    }

    public synchronized int writeCacheSize() {
        if (urlIndexFile instanceof kelondroFlexSplitTable) return ((kelondroFlexSplitTable) urlIndexFile).writeBufferSize();
        if (urlIndexFile instanceof kelondroCache) return ((kelondroCache) urlIndexFile).writeBufferSize();
        return 0;
    }

    public synchronized indexURLEntry load(String urlHash, indexRWIEntry searchedWord) {
        // generates an plasmaLURLEntry using the url hash
        // to speed up the access, the url-hashes are buffered
        // in the hash cache.
        // we have two options to find the url:
        // - look into the hash cache
        // - look into the filed properties
        // if the url cannot be found, this returns null
        try {
            kelondroRow.Entry entry = urlIndexFile.get(urlHash.getBytes());
            if (entry == null) return null;
            return new indexURLEntry(entry, searchedWord);
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized void store(indexURLEntry entry) throws IOException {
        // Check if there is a more recent Entry already in the DB
        indexURLEntry oldEntry;
        try {
            if (exists(entry.hash())) {
                oldEntry = load(entry.hash(), null);
            } else {
                oldEntry = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            oldEntry = null;
        }
        if ((oldEntry != null) && (entry.isOlder(oldEntry))) {
            // the fetched oldEntry is better, so return its properties instead of the new ones
            // this.urlHash = oldEntry.urlHash; // unnecessary, should be the same
            // this.url = oldEntry.url; // unnecessary, should be the same
            entry = oldEntry;
            return; // this did not need to be stored, but is updated
        }

        urlIndexFile.put(entry.toRowEntry(), entry.loaddate());
    }

    public synchronized indexURLEntry newEntry(String propStr) {
        if (propStr != null && propStr.startsWith("{") && propStr.endsWith("}")) try {
            return new indexURLEntry(serverCodings.s2p(propStr.substring(1, propStr.length() - 1)));
        } catch (kelondroException e) {
        	// wrong format
        	return null;
        } else {
            return null;
        }
    }

    public synchronized indexURLEntry newEntry(
            URL url,
            String descr,
            String author,
            String tags,
            String ETag,
            Date mod,
            Date load,
            Date fresh,
            String referrer,
            byte[] md5,
            long size,
            int wc,
            char dt,
            kelondroBitfield flags,
            String lang,
            int llocal,
            int lother,
            int laudio,
            int limage,
            int lvideo,
            int lapp) {
        return new indexURLEntry(url, descr, author, tags, ETag, mod, load, fresh, referrer, md5,
                    size, wc, dt, flags, lang, llocal, lother, laudio, limage, lvideo, lapp);
    }

    public synchronized int getStackSize(int stack) {
        switch (stack) {
            case 1: return externResultStack.size();
            case 2: return searchResultStack.size();
            case 3: return transfResultStack.size();
            case 4: return proxyResultStack.size();
            case 5: return lcrawlResultStack.size();
            case 6: return gcrawlResultStack.size();
        }
        return -1;
    }

    public synchronized String getUrlHash(int stack, int pos) {
        switch (stack) {
            case 1: return ((String) externResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 2: return ((String) searchResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 3: return ((String) transfResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 4: return ((String) proxyResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 5: return ((String) lcrawlResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
            case 6: return ((String) gcrawlResultStack.get(pos)).substring(0, yacySeedDB.commonHashLength);
        }
        return null;
    }

    public synchronized String getInitiatorHash(int stack, int pos) {
        switch (stack) {
            case 1: return ((String) externResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 2: return ((String) searchResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 3: return ((String) transfResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 4: return ((String) proxyResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 5: return ((String) lcrawlResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
            case 6: return ((String) gcrawlResultStack.get(pos)).substring(yacySeedDB.commonHashLength, yacySeedDB.commonHashLength * 2);
        }
        return null;
    }

    public synchronized String getExecutorHash(int stack, int pos) {
        switch (stack) {
            case 1: return ((String) externResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 2: return ((String) searchResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 3: return ((String) transfResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 4: return ((String) proxyResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 5: return ((String) lcrawlResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
            case 6: return ((String) gcrawlResultStack.get(pos)).substring(yacySeedDB.commonHashLength * 2, yacySeedDB.commonHashLength * 3);
        }
        return null;
    }

    public synchronized boolean removeStack(int stack, int pos) {
        Object prevElement = null;
        switch (stack) {
            case 1: prevElement = externResultStack.remove(pos); break;
            case 2: prevElement = searchResultStack.remove(pos); break;
            case 3: prevElement = transfResultStack.remove(pos); break;
            case 4: prevElement = proxyResultStack.remove(pos); break;
            case 5: prevElement = lcrawlResultStack.remove(pos); break;
            case 6: prevElement = gcrawlResultStack.remove(pos); break;
        }
        return prevElement != null;
    }

    public synchronized void clearStack(int stack) {
        switch (stack) {
            case 1: externResultStack.clear(); break;
            case 2: searchResultStack.clear(); break;
            case 3: transfResultStack.clear(); break;
            case 4: proxyResultStack.clear(); break;
            case 5: lcrawlResultStack.clear(); break;
            case 6: gcrawlResultStack.clear(); break;
        }
    }

    public synchronized boolean remove(String urlHash) {
        if (urlHash == null) return false;
        try {
            kelondroRow.Entry r = urlIndexFile.remove(urlHash.getBytes());
            if (r == null) return false;
            for (int stack = 1; stack <= 6; stack++) {
                for (int i = getStackSize(stack) - 1; i >= 0; i--) {
                    if (getUrlHash(stack, i).equals(urlHash)) {
                        removeStack(stack, i);
                        return true;
                    }
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean exists(String urlHash) {
        try {
            return urlIndexFile.has(urlHash.getBytes());
        } catch (IOException e) {
            return false;
        }
    }

    public kelondroCloneableIterator entries(boolean up, String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, firstHash);
    }

    public class kiter implements kelondroCloneableIterator {
        // enumerates entry elements
        private Iterator iter;
        private boolean error;
        boolean up;

        public kiter(boolean up, String firstHash) throws IOException {
            this.up = up;
            this.iter = plasmaCrawlLURL.this.urlIndexFile.rows(up, (firstHash == null) ? null : firstHash.getBytes());
            this.error = false;
        }

        public Object clone(Object secondHash) {
            try {
                return new kiter(up, (String) secondHash);
            } catch (IOException e) {
                return null;
            }
        }
        
        public final boolean hasNext() {
            if (this.error) return false;
            if (this.iter == null) return false;
            return this.iter.hasNext();
        }

        public final Object next() {
            kelondroRow.Entry e = null;
            if (this.iter == null) { return null; }
            if (this.iter.hasNext()) { e = (kelondroRow.Entry) this.iter.next(); }
            if (e == null) { return null; }
            return new indexURLEntry(e, null);
        }

        public final void remove() {
            this.iter.remove();
        }
    }

    /**
     * Uses an Iteration over urlHash.db to detect malformed URL-Entries.
     * Damaged URL-Entries will be marked in a HashSet and removed at the end of the function.
     *
     * @param homePath Root-Path where all information is to be found.
     */
    public void urldbcleanup() {
        serverLog log = new serverLog("URLDBCLEANUP");
        HashSet damagedURLS = new HashSet();
        try {
            Iterator eiter = entries(true, null);
            int iteratorCount = 0;
            while (eiter.hasNext()) try {
                eiter.next();
                iteratorCount++;
            } catch (RuntimeException e) {
                if(e.getMessage() != null) {
                    String m = e.getMessage();
                    damagedURLS.add(m.substring(m.length() - 12));
                } else {
                    log.logSevere("RuntimeException:", e);
                }
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { }
            log.logInfo("URLs vorher: " + size() + " Entries loaded during Iteratorloop: " + iteratorCount + " kaputte URLs: " + damagedURLS.size());

            Iterator eiter2 = damagedURLS.iterator();
            String urlHash;
            while (eiter2.hasNext()) {
                urlHash = (String) eiter2.next();

                // trying to fix the invalid URL
                httpc theHttpc = null;
                String oldUrlStr = null;
                try {
                    // getting the url data as byte array
                    kelondroRow.Entry entry = urlIndexFile.get(urlHash.getBytes());

                    // getting the wrong url string
                    oldUrlStr = entry.getColString(1, null).trim();

                    int pos = -1;
                    if ((pos = oldUrlStr.indexOf("://")) != -1) {
                        // trying to correct the url
                        String newUrlStr = "http://" + oldUrlStr.substring(pos + 3);
                        URL newUrl = new URL(newUrlStr);

                        // doing a http head request to test if the url is correct
                        theHttpc = httpc.getInstance(newUrl.getHost(), newUrl.getHost(), newUrl.getPort(), 30000, false, plasmaSwitchboard.getSwitchboard().remoteProxyConfig);
                        response res = theHttpc.HEAD(newUrl.getPath(), null);

                        if (res.statusCode == 200) {
                            entry.setCol(1, newUrl.toString().getBytes());
                            urlIndexFile.put(entry);
                            log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' corrected\n\tURL: " + oldUrlStr + " -> " + newUrlStr);
                        } else {
                            remove(urlHash);
                            log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' removed\n\tURL: " + oldUrlStr + "\n\tConnection Status: " + res.status);
                        }
                    }
                } catch (Exception e) {
                    remove(urlHash);
                    log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' removed\n\tURL: " + oldUrlStr + "\n\tExecption: " + e.getMessage());
                } finally {
                    if (theHttpc != null) try {
                        theHttpc.close();
                        httpc.returnInstance(theHttpc);
                    } catch (Exception e) { }
                }
            }

            log.logInfo("URLs nachher: " + size() + " kaputte URLs: " + damagedURLS.size());
        } catch (IOException e) {
            log.logSevere("IOException", e);
        }
    }

    //  The Cleaner class was provided as "UrldbCleaner" by Hydrox
    public Cleaner makeCleaner() {
        return new Cleaner();
    }

    public class Cleaner extends Thread {

        private boolean run = true;
        private boolean pause;
        public int blacklistedUrls = 0;
        public int totalSearchedUrls = 1;
        public String lastBlacklistedUrl = "";
        public String lastBlacklistedHash = "";
        public String lastUrl = "";
        public String lastHash = "";

        public Cleaner() {
        }

        public void run() {
            try {
                serverLog.logInfo("URLDBCLEANER", "UrldbCleaner-Thread startet");
                final Iterator eiter = entries(true, null);
                while (eiter.hasNext() && run) {
                    synchronized (this) {
                        if (this.pause) {
                            try {
                                this.wait();
                            } catch (InterruptedException e) {
                                serverLog.logWarning("URLDBCLEANER", "InterruptedException", e);
                                this.run = false;
                                return;
                            }
                        }
                    }
                    final indexURLEntry entry = (indexURLEntry) eiter.next();
                    if (entry == null) {
                        serverLog.logFine("URLDBCLEANER", "entry == null");
                    } else if (entry.hash() == null) {
                        serverLog.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + "hash == null");
                    } else {
                        final indexURLEntry.Components comp = entry.comp();
                        totalSearchedUrls++;
                        if (comp.url() == null) {
                            serverLog.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + entry.hash() + "URL == null");
                            remove(entry.hash());
                        } else if (plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_CRAWLER, comp.url()) ||
                                   plasmaSwitchboard.urlBlacklist.isListed(plasmaURLPattern.BLACKLIST_DHT, comp.url())) {
                            lastBlacklistedUrl = comp.url().toNormalform(true, true);
                            lastBlacklistedHash = entry.hash();
                            serverLog.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + entry.hash() + " " + comp.url().toNormalform(false, true));
                            remove(entry.hash());
                            if (blacklistedUrls % 100 == 0) {
                                serverLog.logInfo("URLDBCLEANER", "Deleted " + blacklistedUrls + " URLs until now. Last deleted URL-Hash: " + lastBlacklistedUrl);
                            }
                        }
                        lastUrl = comp.url().toNormalform(true, true);
                        lastHash = entry.hash();
                    }
                }
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().indexOf("not found in LURL") != -1) {
                    serverLog.logWarning("URLDBCLEANER", "urlHash not found in LURL", e);
                }
                else {
                    serverLog.logWarning("URLDBCLEANER", "RuntimeException", e);
                    run = false;
                }
            } catch (IOException e) {
                e.printStackTrace();
                run = false;
            }
            serverLog.logInfo("URLDBCLEANER", "UrldbCleaner-Thread stopped");
        }

        public void abort() {
            synchronized(this) {
                run = false;
                this.notifyAll();
            }
        }

        public void pause() {
            synchronized(this) {
                if (!pause) {
                    pause = true;
                    serverLog.logInfo("URLDBCLEANER", "UrldbCleaner-Thread paused");
                }
            }
        }

        public void endPause() {
            synchronized(this) {
                if (pause) {
                    pause = false;
                    this.notifyAll();
                    serverLog.logInfo("URLDBCLEANER", "UrldbCleaner-Thread resumed");
                }
            }
        }
    }


    public static void main(String[] args) {
        // test-generation of url hashes for debugging
        // one argument requires, will be treated as url
        // returns url-hash
        if (args[0].equals("-h")) try {
            // arg 1 is url
            System.out.println("HASH: " + plasmaURL.urlHash(new URL(args[1])));
        } catch (MalformedURLException e) {}
        if (args[0].equals("-l")) try {
            // arg 1 is path to URLCache
            final plasmaCrawlLURL urls = new plasmaCrawlLURL(new File(args[2]), 0);
            final Iterator enu = urls.entries(true, null);
            while (enu.hasNext()) {
                System.out.println(((indexURLEntry) enu.next()).toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
