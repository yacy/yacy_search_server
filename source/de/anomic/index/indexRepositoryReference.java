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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterCharacterCoding;
import de.anomic.http.JakartaCommonsHttpClient;
import de.anomic.http.JakartaCommonsHttpResponse;
import de.anomic.http.httpRemoteProxyConfig;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroSplitTable;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public final class indexRepositoryReference {

    // class objects
    kelondroIndex urlIndexFile;
    private Export      exportthread    = null; // will have a export thread assigned if exporter is running
    private File        location        = null;
    ArrayList<hostStat> statsDump       = null;
    
    public indexRepositoryReference(final File indexSecondaryPath) {
        super();
        this.location = new File(indexSecondaryPath, "TEXT");        
        urlIndexFile = new kelondroCache(new kelondroSplitTable(this.location, "urls", indexURLReference.rowdef, false));
    }

    public void clearCache() {
        if (urlIndexFile instanceof kelondroCache) ((kelondroCache) urlIndexFile).clearCache();
        statsDump = null;
    }
    
    public void clear() throws IOException {
        if (exportthread != null) exportthread.interrupt();
        urlIndexFile.clear();
        statsDump = null;
    }
    
    public int size() {
        return urlIndexFile.size();
    }

    public void close() {
        statsDump = null;
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

    public synchronized indexURLReference load(final String urlHash, final indexRWIEntry searchedWord, final long ranking) {
        // generates an plasmaLURLEntry using the url hash
        // if the url cannot be found, this returns null
        if (urlHash == null) return null;
        assert urlIndexFile != null;
        try {
            final kelondroRow.Entry entry = urlIndexFile.get(urlHash.getBytes());
            if (entry == null) return null;
            return new indexURLReference(entry, searchedWord, ranking);
        } catch (final IOException e) {
            return null;
        }
    }

    public synchronized void store(final indexURLReference entry) throws IOException {
        // Check if there is a more recent Entry already in the DB
        indexURLReference oldEntry;
        try {
            if (exists(entry.hash())) {
                oldEntry = load(entry.hash(), null, 0);
            } else {
                oldEntry = null;
            }
        } catch (final Exception e) {
            e.printStackTrace();
            oldEntry = null;
        }
        if ((oldEntry != null) && (entry.isOlder(oldEntry))) {
            // the fetched oldEntry is better, so return its properties instead of the new ones
            // this.urlHash = oldEntry.urlHash; // unnecessary, should be the same
            // this.url = oldEntry.url; // unnecessary, should be the same
            // doesn't make sense, since no return value:
            //entry = oldEntry;
            return; // this did not need to be stored, but is updated
        }

        urlIndexFile.put(entry.toRowEntry(), new Date() /*entry.loaddate()*/);
        statsDump = null;
    }
    
    public synchronized boolean remove(final String urlHash) {
        if (urlHash == null) return false;
        try {
            final kelondroRow.Entry r = urlIndexFile.remove(urlHash.getBytes());
            if (r != null) statsDump = null;
            return r != null;
        } catch (final IOException e) {
            return false;
        }
    }

    public synchronized boolean exists(final String urlHash) {
        if (urlIndexFile == null) return false; // case may happen during shutdown
        return urlIndexFile.has(urlHash.getBytes());
    }

    public kelondroCloneableIterator<indexURLReference> entries(final boolean up, final String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, firstHash);
    }

    public class kiter implements kelondroCloneableIterator<indexURLReference> {
        // enumerates entry elements
        private final Iterator<kelondroRow.Entry> iter;
        private final boolean error;
        boolean up;

        public kiter(final boolean up, final String firstHash) throws IOException {
            this.up = up;
            this.iter = urlIndexFile.rows(up, (firstHash == null) ? null : firstHash.getBytes());
            this.error = false;
        }

        public kiter clone(final Object secondHash) {
            try {
                return new kiter(up, (String) secondHash);
            } catch (final IOException e) {
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
    public void deadlinkCleaner(final httpRemoteProxyConfig proxyConfig) {
        final serverLog log = new serverLog("URLDBCLEANUP");
        final HashSet<String> damagedURLS = new HashSet<String>();
        try {
            final Iterator<indexURLReference> eiter = entries(true, null);
            int iteratorCount = 0;
            while (eiter.hasNext()) try {
                eiter.next();
                iteratorCount++;
            } catch (final RuntimeException e) {
                if(e.getMessage() != null) {
                    final String m = e.getMessage();
                    damagedURLS.add(m.substring(m.length() - 12));
                } else {
                    log.logSevere("RuntimeException:", e);
                }
            }
            try { Thread.sleep(1000); } catch (final InterruptedException e) { }
            log.logInfo("URLs vorher: " + urlIndexFile.size() + " Entries loaded during Iteratorloop: " + iteratorCount + " kaputte URLs: " + damagedURLS.size());

            final Iterator<String> eiter2 = damagedURLS.iterator();
            String urlHash;
            while (eiter2.hasNext()) {
                urlHash = eiter2.next();

                // trying to fix the invalid URL
                String oldUrlStr = null;
                try {
                    // getting the url data as byte array
                    final kelondroRow.Entry entry = urlIndexFile.get(urlHash.getBytes());

                    // getting the wrong url string
                    oldUrlStr = entry.getColString(1, null).trim();

                    int pos = -1;
                    if ((pos = oldUrlStr.indexOf("://")) != -1) {
                        // trying to correct the url
                        final String newUrlStr = "http://" + oldUrlStr.substring(pos + 3);
                        final yacyURL newUrl = new yacyURL(newUrlStr, null);

                        // doing a http head request to test if the url is correct
                        final JakartaCommonsHttpClient client = new JakartaCommonsHttpClient(10000);
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
                } catch (final Exception e) {
                    remove(urlHash);
                    log.logInfo("UrlDB-Entry with urlHash '" + urlHash + "' removed\n\tURL: " + oldUrlStr + "\n\tExecption: " + e.getMessage());
                }
            }

            log.logInfo("URLs nachher: " + size() + " kaputte URLs: " + damagedURLS.size());
        } catch (final IOException e) {
            log.logSevere("IOException", e);
        }
    }

    public BlacklistCleaner getBlacklistCleaner(final indexReferenceBlacklist blacklist) {
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
        private final indexReferenceBlacklist blacklist;

        public BlacklistCleaner(final indexReferenceBlacklist blacklist) {
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
                            } catch (final InterruptedException e) {
                                serverLog.logWarning("URLDBCLEANER", "InterruptedException", e);
                                this.run = false;
                                return;
                            }
                        }
                    }
                    final indexURLReference entry = eiter.next();
                    if (entry == null) {
                        if (serverLog.isFine("URLDBCLEANER")) serverLog.logFine("URLDBCLEANER", "entry == null");
                    } else if (entry.hash() == null) {
                        if (serverLog.isFine("URLDBCLEANER")) serverLog.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + "hash == null");
                    } else {
                        final indexURLReference.Components comp = entry.comp();
                        totalSearchedUrls++;
                        if (comp.url() == null) {
                            if (serverLog.isFine("URLDBCLEANER")) serverLog.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + entry.hash() + "URL == null");
                            remove(entry.hash());
                        } else if (blacklist.isListed(indexReferenceBlacklist.BLACKLIST_CRAWLER, comp.url()) ||
                                blacklist.isListed(indexReferenceBlacklist.BLACKLIST_DHT, comp.url())) {
                            lastBlacklistedUrl = comp.url().toNormalform(true, true);
                            lastBlacklistedHash = entry.hash();
                            if (serverLog.isFine("URLDBCLEANER")) serverLog.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + entry.hash() + " " + comp.url().toNormalform(false, true));
                            remove(entry.hash());
                            if (blacklistedUrls % 100 == 0) {
                                serverLog.logInfo("URLDBCLEANER", "Deleted " + blacklistedUrls + " URLs until now. Last deleted URL-Hash: " + lastBlacklistedUrl);
                            }
                        }
                        lastUrl = comp.url().toNormalform(true, true);
                        lastHash = entry.hash();
                    }
                }
            } catch (final RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().indexOf("not found in LURL") != -1) {
                    serverLog.logWarning("URLDBCLEANER", "urlHash not found in LURL", e);
                }
                else {
                    serverLog.logWarning("URLDBCLEANER", "RuntimeException", e);
                    run = false;
                }
            } catch (final IOException e) {
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
    public Export export(final File f, final String filter, final int format, final boolean dom) {
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
        private final File f;
        private final String filter;
        private int count;
        private String failure;
        private final int format;
        private final boolean dom;
        
        public Export(final File f, final String filter, final int format, boolean dom) {
            // format: 0=text, 1=html, 2=rss/xml
            this.f = f;
            this.filter = filter;
            this.count = 0;
            this.failure = null;
            this.format = format;
            this.dom = dom;
            if ((dom) && (format == 2)) dom = false;
        }
        
        public void run() {
            try {
                f.getParentFile().mkdirs();
                final PrintWriter pw = new PrintWriter(new BufferedOutputStream(new FileOutputStream(f)));
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
                
                if (dom) {
                    TreeSet<String> set = domainNameCollector(-1);
                    for (String host: set) {
                        if (!host.matches(filter)) continue;
                        if (format == 0) pw.println(host);
                        if (format == 1) pw.println("<a href=\"http://" + host + "\">" + host + "</a><br>");
                        count++;
                    }
                } else {
                    final Iterator<indexURLReference> i = entries(true, null); // iterates indexURLEntry objects
                    indexURLReference entry;
                    indexURLReference.Components comp;
                    String url;
                    while (i.hasNext()) {
                        entry = i.next();
                        comp = entry.comp();
                        url = comp.url().toNormalform(true, false);
                        if (!url.matches(filter)) continue;
                        if (format == 0) {
                            pw.println(url);
                        }
                        if (format == 1) {
                            pw.println("<a href=\"" + url + "\">" + htmlFilterCharacterCoding.unicode2xml(comp.dc_title(), true) + "</a><br>");
                        }
                        if (format == 2) {
                            pw.println("<item>");
                            pw.println("<title>" + htmlFilterCharacterCoding.unicode2xml(comp.dc_title(), true) + "</title>");
                            pw.println("<link>" + yacyURL.escape(url) + "</link>");
                            if (comp.dc_creator().length() > 0) pw.println("<author>" + htmlFilterCharacterCoding.unicode2xml(comp.dc_creator(), true) + "</author>");
                            if (comp.dc_subject().length() > 0) pw.println("<description>" + htmlFilterCharacterCoding.unicode2xml(comp.dc_subject(), true) + "</description>");
                            pw.println("<pubDate>" + entry.moddate().toString() + "</pubDate>");
                            pw.println("<guid isPermaLink=\"false\">" + entry.hash() + "</guid>");
                            pw.println("</item>");
                        }
                        count++;
                    }
                }
                if (format == 1) {
                    pw.println("</body></html>");
                }
                if (format == 2) {
                    pw.println("</channel>");
                    pw.println("</rss>");
                }
                pw.close();
            } catch (final IOException e) {
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
    
    private HashMap<String, hashStat> domainSampleCollector() throws IOException {
        HashMap<String, hashStat> map = new HashMap<String, hashStat>();
        // first collect all domains and calculate statistics about it
        kelondroCloneableIterator<byte[]> i = this.urlIndexFile.keys(true, null);
        String urlhash, hosthash;
        hashStat ds;
        if (i != null) while (i.hasNext()) {
            urlhash = new String(i.next());
            hosthash = urlhash.substring(6);
            ds = map.get(hosthash);
            if (ds == null) {
                ds = new hashStat(urlhash);
                map.put(hosthash, ds);
            } else {
                ds.count++;
            }
        }
        return map;
    }
    
    public TreeSet<String> domainNameCollector(int count) throws IOException {
        // collect hashes from all domains
        HashMap<String, hashStat> map = domainSampleCollector();
        
        // fetch urls from the database to determine the host in clear text
        indexURLReference urlref;
        if (count < 0 || count > map.size()) count = map.size();
        statsDump = new ArrayList<hostStat>();
        TreeSet<String> set = new TreeSet<String>();
        for (hashStat hs: map.values()) {
            if (hs == null) continue;
            urlref = this.load(hs.urlhash, null, 0);
            if (urlref == null || urlref.comp() == null || urlref.comp().url() == null || urlref.comp().url().getHost() == null) continue;
            set.add(urlref.comp().url().getHost());
            count--;
            if (count == 0) break;
        }
        return set;
    }
    
    public Iterator<hostStat> statistics(int count) throws IOException {
        // prevent too heavy IO.
        if (statsDump != null && count <= statsDump.size()) return statsDump.iterator();
        
        // collect hashes from all domains
        HashMap<String, hashStat> map = domainSampleCollector();
        
        // order elements by size
        kelondroMScoreCluster<String> s = new kelondroMScoreCluster<String>();
        for (Map.Entry<String, hashStat> e: map.entrySet()) {
            s.addScore(e.getValue().urlhash, e.getValue().count);
        }
    
        // fetch urls from the database to determine the host in clear text
        Iterator<String> j = s.scores(false); // iterate urlhash-examples in reverse order (biggest first)
        indexURLReference urlref;
        String urlhash;
        count += 10; // make some more to prevent that we have to do this again after deletions too soon.
        if (count < 0 || count > s.size()) count = s.size();
        statsDump = new ArrayList<hostStat>();
        indexURLReference.Components comps;
        yacyURL url;
        while (j.hasNext()) {
            urlhash = j.next();
            if (urlhash == null) continue;
            urlref = this.load(urlhash, null, 0);
            if (urlref == null || urlref.comp() == null || urlref.comp().url() == null || urlref.comp().url().getHost() == null) continue;
            if (statsDump == null) return new ArrayList<hostStat>().iterator(); // some other operation has destroyed the object
            comps = urlref.comp();
            url = comps.url();
            statsDump.add(new hostStat(url.getHost(), url.getPort(), urlhash.substring(6), s.getScore(urlhash)));
            count--;
            if (count == 0) break;
        }
        // finally return an iterator for the result array
        return (statsDump == null) ? new ArrayList<hostStat>().iterator() : statsDump.iterator();
    }
    
    public class hashStat {
        public String urlhash;
        public int count;
        public hashStat(String urlhash) {
            this.urlhash = urlhash;
            this.count = 1;
        }
    }
    
    public class hostStat {
        public String hostname, hosthash;
        public int port;
        public int count;
        public hostStat(String host, int port, String urlhashfragment, int count) {
            assert urlhashfragment.length() == 6;
            this.hostname = host;
            this.port = port;
            this.hosthash = urlhashfragment;
            this.count = count;
        }
    }
    
    /**
     * using a fragment of the url hash (5 bytes: bytes 6 to 10) it is possible to address all urls from a specific domain
     * here such a fragment can be used to delete all these domains at once
     * @param hosthash
     * @return number of deleted domains
     * @throws IOException
     */
    public int deleteDomain(String hosthash) throws IOException {
        // first collect all url hashes that belong to the domain
        assert hosthash.length() == 6;
        ArrayList<String> l = new ArrayList<String>();
        kelondroCloneableIterator<byte[]> i = this.urlIndexFile.keys(true, null);
        String hash;
        while (i.hasNext()) {
            hash = new String(i.next());
            if (hosthash.equals(hash.substring(6))) l.add(hash);
        }
        
        // then delete the urls using this list
        int cnt = 0;
        for (String h: l) {
            if (urlIndexFile.remove(h.getBytes()) != null) cnt++;
        }
        
        // finally remove the line with statistics
        if (statsDump != null) {
            Iterator<hostStat> hsi = statsDump.iterator();
            hostStat hs;
            while (hsi.hasNext()) {
                hs = hsi.next();
                if (hs.hosthash.equals(hosthash)) {
                    hsi.remove();
                    break;
                }
            }
        }
        
        return cnt;
    }
}
