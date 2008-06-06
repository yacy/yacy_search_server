// indexRepositoryReference.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2006 as part of 'plasmaCrawlLURL.java' on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.index;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;

import de.anomic.data.htmlTools;
import de.anomic.http.JakartaCommonsHttpClient;
import de.anomic.http.JakartaCommonsHttpResponse;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.kelondro.kelondroSplitTable;
import de.anomic.server.serverCodings;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public final class indexRepositoryReference {

    // class objects
    kelondroIndex urlIndexFile;
    private Export exportthread = null; // will habe a export thread assigned if exporter is running
    private File location = null;
    
    public indexRepositoryReference(File indexSecondaryPath) {
        super();
        this.location = new File(indexSecondaryPath, "TEXT");        
        urlIndexFile = new kelondroSplitTable(this.location, "urls", indexURLReference.rowdef, false);
    }

    public void clear() throws IOException {
        if (exportthread != null) exportthread.interrupt();
        urlIndexFile.clear();
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

    public synchronized int writeCacheSize() {
        if (urlIndexFile instanceof kelondroSplitTable) return ((kelondroSplitTable) urlIndexFile).writeBufferSize();
        if (urlIndexFile instanceof kelondroCache) return ((kelondroCache) urlIndexFile).writeBufferSize();
        return 0;
    }

    public synchronized indexURLReference load(String urlHash, indexRWIEntry searchedWord, long ranking) {
        // generates an plasmaLURLEntry using the url hash
        // if the url cannot be found, this returns null
        if (urlHash == null) return null;
        assert urlIndexFile != null;
        try {
            kelondroRow.Entry entry = urlIndexFile.get(urlHash.getBytes());
            if (entry == null) return null;
            return new indexURLReference(entry, searchedWord, ranking);
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized void store(indexURLReference entry) throws IOException {
        // Check if there is a more recent Entry already in the DB
        indexURLReference oldEntry;
        try {
            if (exists(entry.hash())) {
                oldEntry = load(entry.hash(), null, 0);
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

        urlIndexFile.put(entry.toRowEntry(), new Date() /*entry.loaddate()*/);
    }

    public synchronized indexURLReference newEntry(String propStr) {
        if (propStr != null && propStr.startsWith("{") && propStr.endsWith("}")) try {
            return new indexURLReference(serverCodings.s2p(propStr.substring(1, propStr.length() - 1)));
        } catch (kelondroException e) {
            // wrong format
            return null;
        } else {
            return null;
        }
    }
    
    public synchronized boolean remove(String urlHash) {
        if (urlHash == null) return false;
        try {
            kelondroRow.Entry r = urlIndexFile.remove(urlHash.getBytes(), false);
            return r != null;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean exists(String urlHash) {
        if (urlIndexFile == null) return false; // case may happen during shutdown
        try {
            return urlIndexFile.has(urlHash.getBytes());
        } catch (IOException e) {
            return false;
        }
    }

    public kelondroCloneableIterator<indexURLReference> entries(boolean up, String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, firstHash);
    }

    public class kiter implements kelondroCloneableIterator<indexURLReference> {
        // enumerates entry elements
        private Iterator<kelondroRow.Entry> iter;
        private boolean error;
        boolean up;

        public kiter(boolean up, String firstHash) throws IOException {
            this.up = up;
            this.iter = urlIndexFile.rows(up, (firstHash == null) ? null : firstHash.getBytes());
            this.error = false;
        }

        public kiter clone(Object secondHash) {
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

        public final indexURLReference next() {
            kelondroRow.Entry e = null;
            if (this.iter == null) { return null; }
            if (this.iter.hasNext()) { e = this.iter.next(); }
            if (e == null) { return null; }
            return new indexURLReference(e, null, 0);
        }

        public final void remove() {
            this.iter.remove();
        }
    }


    /**
     * Uses an Iteration over urlHash.db to detect malformed URL-Entries.
     * Damaged URL-Entries will be marked in a HashSet and removed at the end of the function.
     * 
     * @param proxyConfig 
     */
    public void deadlinkCleaner(httpRemoteProxyConfig proxyConfig) {
        serverLog log = new serverLog("URLDBCLEANUP");
        HashSet<String> damagedURLS = new HashSet<String>();
        try {
            Iterator<indexURLReference> eiter = entries(true, null);
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
            log.logInfo("URLs vorher: " + urlIndexFile.size() + " Entries loaded during Iteratorloop: " + iteratorCount + " kaputte URLs: " + damagedURLS.size());

            Iterator<String> eiter2 = damagedURLS.iterator();
            String urlHash;
            while (eiter2.hasNext()) {
                urlHash = eiter2.next();

                // trying to fix the invalid URL
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
                        yacyURL newUrl = new yacyURL(newUrlStr, null);

                        // doing a http head request to test if the url is correct
                        JakartaCommonsHttpClient client = new JakartaCommonsHttpClient(10000, null, null);
                        client.setProxy(proxyConfig);
                        JakartaCommonsHttpResponse res = null;
                        try {
                            res = client.HEAD(newUrl.toString());
                        } finally {
                            if(res != null) {
                                // release connection
                                res.closeStream();
                            }
                        }

                        if (res != null && res.getStatusCode() == 200) {
                            entry.setCol(1, newUrl.toString().getBytes());
                            urlIndexFile.put(entry);
                            log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' corrected\n\tURL: " + oldUrlStr + " -> " + newUrlStr);
                        } else {
                            remove(urlHash);
                            log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' removed\n\tURL: " + oldUrlStr + "\n\tConnection Status: " + (res == null ? "null" : res.getStatusLine()));
                        }
                    }
                } catch (Exception e) {
                    remove(urlHash);
                    log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' removed\n\tURL: " + oldUrlStr + "\n\tExecption: " + e.getMessage());
                }
            }

            log.logInfo("URLs nachher: " + size() + " kaputte URLs: " + damagedURLS.size());
        } catch (IOException e) {
            log.logSevere("IOException", e);
        }
    }

    public BlacklistCleaner getBlacklistCleaner(indexReferenceBlacklist blacklist) {
        return new BlacklistCleaner(blacklist);
    }
    
    public class BlacklistCleaner extends Thread {

        private boolean run = true;
        private boolean pause;
        public int blacklistedUrls = 0;
        public int totalSearchedUrls = 1;
        public String lastBlacklistedUrl = "";
        public String lastBlacklistedHash = "";
        public String lastUrl = "";
        public String lastHash = "";
        private indexReferenceBlacklist blacklist;

        public BlacklistCleaner(indexReferenceBlacklist blacklist) {
            this.blacklist = blacklist;
        }

        public void run() {
            try {
                serverLog.logInfo("URLDBCLEANER", "UrldbCleaner-Thread startet");
                final Iterator<indexURLReference> eiter = entries(true, null);
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
                    final indexURLReference entry = eiter.next();
                    if (entry == null) {
                        serverLog.logFine("URLDBCLEANER", "entry == null");
                    } else if (entry.hash() == null) {
                        serverLog.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + "hash == null");
                    } else {
                        final indexURLReference.Components comp = entry.comp();
                        totalSearchedUrls++;
                        if (comp.url() == null) {
                            serverLog.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + entry.hash() + "URL == null");
                            remove(entry.hash());
                        } else if (blacklist.isListed(indexReferenceBlacklist.BLACKLIST_CRAWLER, comp.url()) ||
                                blacklist.isListed(indexReferenceBlacklist.BLACKLIST_DHT, comp.url())) {
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
    
    // export methods
    public Export export(File f, String filter, int format, boolean dom) {
        if ((exportthread != null) && (exportthread.isAlive())) {
            serverLog.logWarning("LURL-EXPORT", "cannot start another export thread, already one running");
            return exportthread;
        }
        this.exportthread = new Export(f, filter, format, dom);
        this.exportthread.start();
        return exportthread;
    }
    
    public Export export() {
        return this.exportthread;
    }
    
    public class Export extends Thread {
        private File f;
        private String filter;
        private int count;
        private String failure;
        private int format;
        private boolean dom;
        private kelondroRowSet doms;
        
        public Export(File f, String filter, int format, boolean dom) {
            // format: 0=text, 1=html, 2=rss/xml
            this.f = f;
            this.filter = filter;
            this.count = 0;
            this.failure = null;
            this.format = format;
            this.dom = dom;
            if ((dom) && (format == 2)) dom = false;
            this.doms = new kelondroRowSet(new kelondroRow("String hash-6", kelondroBase64Order.enhancedCoder, 0), 0);
        }
        
        public void run() {
            try {
                f.getParentFile().mkdirs();
                PrintWriter pw = new PrintWriter(new BufferedOutputStream(new FileOutputStream(f)));
                if (format == 1) {
                    pw.println("<html><head></head><body>");
                }
                if (format == 2) {
                    pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                    pw.println("<?xml-stylesheet type='text/xsl' href='/yacysearch.xsl' version='1.0'?>");
                    pw.println("<rss version=\"2.0\">");
                    pw.println("<channel>");
                    pw.println("<title>YaCy Peer-to-Peer - Web-Search LURL Export</title>");
                    pw.println("<description></description>");
                    pw.println("<link>http://yacy.net</link>");
                }
                
                Iterator<indexURLReference> i = entries(true, null); // iterates indexURLEntry objects
                indexURLReference entry;
                indexURLReference.Components comp;
                String url;
                loop: while (i.hasNext()) {
                    entry = i.next();
                    comp = entry.comp();
                    url = comp.url().toNormalform(true, false);
                    if (!url.matches(filter)) continue;
                    if (dom) {
                        if (doms.has(entry.hash().substring(6).getBytes())) continue loop;
                        doms.add(entry.hash().substring(6).getBytes());
                        url = comp.url().getHost();
                        if (format == 0) {
                            pw.println(url);
                        }
                        if (format == 1) {
                            pw.println("<a href=\"http://" + url + "\">" + url + "</a><br>");
                        }
                    } else {
                        if (format == 0) {
                            pw.println(url);
                        }
                        if (format == 1) {
                            pw.println("<a href=\"" + url + "\">" + htmlTools.encodeUnicode2html(comp.dc_title(), true, true) + "</a><br>");
                        }
                        if (format == 2) {
                            pw.println("<item>");
                            pw.println("<title>" + htmlTools.encodeUnicode2html(comp.dc_title(), true, true) + "</title>");
                            pw.println("<link>" + yacyURL.escape(url) + "</link>");
                            if (comp.dc_creator().length() > 0) pw.println("<author>" + htmlTools.encodeUnicode2html(comp.dc_creator(), true, true) + "</author>");
                            if (comp.dc_subject().length() > 0) pw.println("<description>" + htmlTools.encodeUnicode2html(comp.dc_subject(), true, true) + "</description>");
                            pw.println("<pubDate>" + entry.moddate().toString() + "</pubDate>");
                            pw.println("<guid isPermaLink=\"false\">" + entry.hash() + "</guid>");
                            pw.println("</item>");
                        }
                    }
                    count++;
                }
                if (format == 1) {
                    pw.println("</body></html>");
                }
                if (format == 2) {
                    pw.println("</channel>");
                    pw.println("</rss>");
                }
                pw.close();
            } catch (IOException e) {
                e.printStackTrace();
                this.failure = e.getMessage();
            }
            // terminate process
        }
        
        public File file() {
            return this.f;
        }
        
        public String failed() {
            return this.failure;
        }
        
        public int count() {
            return this.count;
        }
        
    }
}
