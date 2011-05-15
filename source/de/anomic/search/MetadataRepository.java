// indexRepositoryReference.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2006 as part of 'plasmaCrawlLURL.java' on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.search;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.crawler.CrawlStacker;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.storage.ConcurrentScoreMap;
import net.yacy.cora.storage.ScoreMap;
import net.yacy.cora.storage.WeakPriorityBlockingQueue;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.index.Cache;
import net.yacy.kelondro.index.HandleSet;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.table.SplitTable;
import net.yacy.repository.Blacklist;

public final class MetadataRepository implements Iterable<byte[]> {

    // class objects
    protected Index               urlIndexFile;
    private   Export              exportthread; // will have a export thread assigned if exporter is running
    private   File                location;
    private   ArrayList<hostStat> statsDump;

    public MetadataRepository(
            final File path,
            final String tablename,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.location = path;
        Index backupIndex = null;
        try {
            backupIndex = new SplitTable(this.location, tablename, URIMetadataRow.rowdef, useTailCache, exceed134217727);
        } catch (RowSpaceExceededException e) {
            try {
                backupIndex = new SplitTable(this.location, tablename, URIMetadataRow.rowdef, false, exceed134217727);
            } catch (RowSpaceExceededException e1) {
                Log.logException(e1);
            }
        }
        this.urlIndexFile = backupIndex; //new Cache(backupIndex, 20000000, 20000000);
        this.exportthread = null; // will have a export thread assigned if exporter is running
        this.statsDump = null;
    }

    public void clearCache() {
        if (urlIndexFile instanceof Cache) ((Cache) urlIndexFile).clearCache();
        statsDump = null;
    }
    
    public void clear() throws IOException {
        if (exportthread != null) exportthread.interrupt();
        urlIndexFile.clear();
        statsDump = null;
    }
    
    public int size() {
        return urlIndexFile == null ? 0 : urlIndexFile.size();
    }

    public void close() {
        statsDump = null;
        if (urlIndexFile != null) {
            urlIndexFile.close();
            urlIndexFile = null;
        }
    }

    public int writeCacheSize() {
        if (urlIndexFile instanceof SplitTable) return ((SplitTable) urlIndexFile).writeBufferSize();
        if (urlIndexFile instanceof Cache) return ((Cache) urlIndexFile).writeBufferSize();
        return 0;
    }
    
    /**
     * generates an plasmaLURLEntry using the url hash
     * if the url cannot be found, this returns null
     * @param obrwi
     * @return
     */
    public URIMetadataRow load(final WeakPriorityBlockingQueue.Element<WordReferenceVars> obrwi) {
        if (urlIndexFile == null) return null;
        if (obrwi == null) return null; // all time was already wasted in takeRWI to get another element
        byte[] urlHash = obrwi.getElement().metadataHash();
        if (urlHash == null) return null;
        try {
            final Row.Entry entry = urlIndexFile.get(urlHash);
            if (entry == null) return null;
            return new URIMetadataRow(entry, obrwi.getElement(), obrwi.getWeight());
        } catch (final IOException e) {
            return null;
        }
    }
    
    public URIMetadataRow load(final byte[] urlHash) {
        if (urlIndexFile == null) return null;
        if (urlHash == null) return null;
        try {
            final Row.Entry entry = urlIndexFile.get(urlHash);
            if (entry == null) return null;
            return new URIMetadataRow(entry, null, 0);
        } catch (final IOException e) {
            return null;
        }
    }

    public void store(final URIMetadataRow entry) throws IOException {
        // Check if there is a more recent Entry already in the DB
        URIMetadataRow oldEntry;
        if (urlIndexFile == null) return; // case may happen during shutdown or startup
        try {
            Row.Entry oe = urlIndexFile.get(entry.hash());
            oldEntry = (oe == null) ? null : new URIMetadataRow(oe, null, 0);
        } catch (final Exception e) {
            Log.logException(e);
            oldEntry = null;
        }
        if (oldEntry != null && entry.isOlder(oldEntry)) {
            // the fetched oldEntry is better, so return its properties instead of the new ones
            // this.urlHash = oldEntry.urlHash; // unnecessary, should be the same
            // this.url = oldEntry.url; // unnecessary, should be the same
            // doesn't make sense, since no return value:
            //entry = oldEntry;
            return; // this did not need to be stored, but is updated
        }

        try {
            urlIndexFile.put(entry.toRowEntry());
        } catch (RowSpaceExceededException e) {
            throw new IOException("RowSpaceExceededException in " + this.urlIndexFile.filename() + ": " + e.getMessage());
        }
        statsDump = null;
    }
    
    public boolean remove(final byte[] urlHashBytes) {
        if (urlHashBytes == null) return false;
        try {
            final Row.Entry r = urlIndexFile.remove(urlHashBytes);
            if (r != null) statsDump = null;
            return r != null;
        } catch (final IOException e) {
            return false;
        }
    }

    public boolean exists(final byte[] urlHash) {
        if (urlIndexFile == null) return false; // case may happen during shutdown
        return urlIndexFile.has(urlHash);
    }

    public CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) {
        try {
            return this.urlIndexFile.keys(up, firstKey);
        } catch (IOException e) {
            Log.logException(e);
            return null;
        }
    }

    public Iterator<byte[]> iterator() {
        return keys(true, null);
    }
    
    public CloneableIterator<URIMetadataRow> entries() throws IOException {
        // enumerates entry elements
        return new kiter();
    }

    public CloneableIterator<URIMetadataRow> entries(final boolean up, final String firstHash) throws IOException {
        // enumerates entry elements
        return new kiter(up, firstHash);
    }

    public class kiter implements CloneableIterator<URIMetadataRow> {
        // enumerates entry elements
        private final Iterator<Row.Entry> iter;
        private final boolean error;
        boolean up;

        public kiter() throws IOException {
            this.up = true;
            this.iter = urlIndexFile.rows();
            this.error = false;
        }

        public kiter(final boolean up, final String firstHash) throws IOException {
            this.up = up;
            this.iter = urlIndexFile.rows(up, (firstHash == null) ? null : UTF8.getBytes(firstHash));
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

        public final URIMetadataRow next() {
            Row.Entry e = null;
            if (this.iter == null) { return null; }
            if (this.iter.hasNext()) { e = this.iter.next(); }
            if (e == null) { return null; }
            return new URIMetadataRow(e, null, 0);
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
    public void deadlinkCleaner() {
        final Log log = new Log("URLDBCLEANUP");
        final HashSet<String> damagedURLS = new HashSet<String>();
        try {
            final Iterator<URIMetadataRow> eiter = entries(true, null);
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
            log.logInfo("URLs vorher: " + urlIndexFile.size() + " Entries loaded during Iteratorloop: " + iteratorCount + " kaputte URLs: " + damagedURLS.size());

            final HTTPClient client = new HTTPClient();
            final Iterator<String> eiter2 = damagedURLS.iterator();
            byte[] urlHashBytes;
            while (eiter2.hasNext()) {
                urlHashBytes = UTF8.getBytes(eiter2.next());

                // trying to fix the invalid URL
                String oldUrlStr = null;
                try {
                    // getting the url data as byte array
                    final Row.Entry entry = urlIndexFile.get(urlHashBytes);

                    // getting the wrong url string
                    oldUrlStr = entry.getColString(1).trim();

                    int pos = -1;
                    if ((pos = oldUrlStr.indexOf("://")) != -1) {
                        // trying to correct the url
                        final String newUrlStr = "http://" + oldUrlStr.substring(pos + 3);
                        final DigestURI newUrl = new DigestURI(newUrlStr);

                        if (client.HEADResponse(newUrl.toString()) != null
                        		&& client.getHttpResponse().getStatusLine().getStatusCode() == 200) {
                            entry.setCol(1, UTF8.getBytes(newUrl.toString()));
                            urlIndexFile.put(entry);
                            if (log.isInfo()) log.logInfo("UrlDB-Entry with urlHash '" + UTF8.String(urlHashBytes) + "' corrected\n\tURL: " + oldUrlStr + " -> " + newUrlStr);
                        } else {
                            remove(urlHashBytes);
                            if (log.isInfo()) log.logInfo("UrlDB-Entry with urlHash '" + UTF8.String(urlHashBytes) + "' removed\n\tURL: " + oldUrlStr + "\n\tConnection Status: " + (client.getHttpResponse() == null ? "null" : client.getHttpResponse().getStatusLine()));
                        }
                    }
                } catch (final Exception e) {
                    remove(urlHashBytes);
                    if (log.isInfo()) log.logInfo("UrlDB-Entry with urlHash '" + UTF8.String(urlHashBytes) + "' removed\n\tURL: " + oldUrlStr + "\n\tExecption: " + e.getMessage());
                }
            }

            log.logInfo("URLs nachher: " + size() + " kaputte URLs: " + damagedURLS.size());
        } catch (final IOException e) {
            log.logSevere("IOException", e);
        }
    }

    public BlacklistCleaner getBlacklistCleaner(final Blacklist blacklist, final CrawlStacker crawlStacker) {
        return new BlacklistCleaner(blacklist, crawlStacker);
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
        private final Blacklist blacklist;
        private final CrawlStacker crawlStacker;

        public BlacklistCleaner(final Blacklist blacklist, final CrawlStacker crawlStacker) {
            this.blacklist = blacklist;
            this.crawlStacker = crawlStacker;
        }

        public void run() {
            try {
                Log.logInfo("URLDBCLEANER", "UrldbCleaner-Thread startet");
                final Iterator<URIMetadataRow> eiter = entries(true, null);
                while (eiter.hasNext() && run) {
                    synchronized (this) {
                        if (this.pause) {
                            try {
                                this.wait();
                            } catch (final InterruptedException e) {
                                Log.logWarning("URLDBCLEANER", "InterruptedException", e);
                                this.run = false;
                                return;
                            }
                        }
                    }
                    final URIMetadataRow entry = eiter.next();
                    if (entry == null) {
                        if (Log.isFine("URLDBCLEANER")) Log.logFine("URLDBCLEANER", "entry == null");
                    } else if (entry.hash() == null) {
                        if (Log.isFine("URLDBCLEANER")) Log.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + "hash == null");
                    } else {
                        final URIMetadataRow.Components metadata = entry.metadata();
                        totalSearchedUrls++;
                        if (metadata == null) {
                            if (Log.isFine("URLDBCLEANER")) Log.logFine("URLDBCLEANER", "corrupted entry for hash = " + UTF8.String(entry.hash()));
                            remove(entry.hash());
                            continue;
                        }
                        if (metadata.url() == null) {
                            if (Log.isFine("URLDBCLEANER")) Log.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + UTF8.String(entry.hash()) + "URL == null");
                            remove(entry.hash());
                            continue;
                        }
                        if (blacklist.isListed(Blacklist.BLACKLIST_CRAWLER, metadata.url()) ||
                            blacklist.isListed(Blacklist.BLACKLIST_DHT, metadata.url()) ||
                            (crawlStacker.urlInAcceptedDomain(metadata.url()) != null)) {
                            lastBlacklistedUrl = metadata.url().toNormalform(true, true);
                            lastBlacklistedHash = UTF8.String(entry.hash());
                            if (Log.isFine("URLDBCLEANER")) Log.logFine("URLDBCLEANER", ++blacklistedUrls + " blacklisted (" + ((double) blacklistedUrls / totalSearchedUrls) * 100 + "%): " + UTF8.String(entry.hash()) + " " + metadata.url().toNormalform(false, true));
                            remove(entry.hash());
                            if (blacklistedUrls % 100 == 0) {
                                Log.logInfo("URLDBCLEANER", "Deleted " + blacklistedUrls + " URLs until now. Last deleted URL-Hash: " + lastBlacklistedUrl);
                            }
                        }
                        lastUrl = metadata.url().toNormalform(true, true);
                        lastHash = UTF8.String(entry.hash());
                    }
                }
            } catch (final RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().indexOf("not found in LURL") != -1) {
                    Log.logWarning("URLDBCLEANER", "urlHash not found in LURL", e);
                }
                else {
                    Log.logWarning("URLDBCLEANER", "RuntimeException", e);
                    run = false;
                }
            } catch (final IOException e) {
                Log.logException(e);
                run = false;
            } catch (final Exception e) {
                Log.logException(e);
                run = false;
            }
            Log.logInfo("URLDBCLEANER", "UrldbCleaner-Thread stopped");
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
                    Log.logInfo("URLDBCLEANER", "UrldbCleaner-Thread paused");
                }
            }
        }

        public void endPause() {
            synchronized(this) {
                if (pause) {
                    pause = false;
                    this.notifyAll();
                    Log.logInfo("URLDBCLEANER", "UrldbCleaner-Thread resumed");
                }
            }
        }
    }
    
    // export methods
    public Export export(final File f, final String filter, HandleSet set, final int format, final boolean dom) {
        if ((exportthread != null) && (exportthread.isAlive())) {
            Log.logWarning("LURL-EXPORT", "cannot start another export thread, already one running");
            return exportthread;
        }
        this.exportthread = new Export(f, filter, set, format, dom);
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
        private final HandleSet set;
        
        public Export(final File f, final String filter, HandleSet set, final int format, boolean dom) {
            // format: 0=text, 1=html, 2=rss/xml
            this.f = f;
            this.filter = filter;
            this.count = 0;
            this.failure = null;
            this.format = format;
            this.dom = dom;
            this.set = set;
            if ((dom) && (format == 2)) dom = false;
        }
        
        public void run() {
            try {
                File parentf = f.getParentFile();
                if (parentf != null) parentf.mkdirs();
                final PrintWriter pw = new PrintWriter(new BufferedOutputStream(new FileOutputStream(f)));
                if (format == 1) {
                    pw.println("<html><head></head><body>");
                }
                if (format == 2) {
                    pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                    pw.println("<?xml-stylesheet type='text/xsl' href='/yacysearch.xsl' version='1.0'?>");
                    pw.println("<rss version=\"2.0\" xmlns:yacy=\"http://www.yacy.net/\" xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\" xmlns:atom=\"http://www.w3.org/2005/Atom\">");
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
                    final Iterator<URIMetadataRow> i = entries(); // iterates indexURLEntry objects
                    URIMetadataRow entry;
                    URIMetadataRow.Components metadata;
                    String url;
                    while (i.hasNext()) {
                        entry = i.next();
                        if (this.set != null && !set.has(entry.hash())) continue;
                        metadata = entry.metadata();
                        url = metadata.url().toNormalform(true, false);
                        if (!url.matches(filter)) continue;
                        if (format == 0) {
                            pw.println(url);
                        }
                        if (format == 1) {
                            pw.println("<a href=\"" + url + "\">" + CharacterCoding.unicode2xml(metadata.dc_title(), true) + "</a><br>");
                        }
                        if (format == 2) {
                            pw.println("<item>");
                            pw.println("<title>" + CharacterCoding.unicode2xml(metadata.dc_title(), true) + "</title>");
                            pw.println("<link>" + MultiProtocolURI.escape(url) + "</link>");
                            if (metadata.dc_creator().length() > 0) pw.println("<author>" + CharacterCoding.unicode2xml(metadata.dc_creator(), true) + "</author>");
                            if (metadata.dc_subject().length() > 0) pw.println("<description>" + CharacterCoding.unicode2xml(metadata.dc_subject(), true) + "</description>");
                            pw.println("<pubDate>" + entry.moddate().toString() + "</pubDate>");
                            pw.println("<yacy:size>" + entry.size() + "</yacy:size>");
                            pw.println("<guid isPermaLink=\"false\">" + UTF8.String(entry.hash()) + "</guid>");
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
                Log.logException(e);
                this.failure = e.getMessage();
            } catch (final Exception e) {
                Log.logException(e);
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
    
    private Map<String, hashStat> domainSampleCollector() throws IOException {
        Map<String, hashStat> map = new HashMap<String, hashStat>();
        // first collect all domains and calculate statistics about it
        CloneableIterator<byte[]> i = this.urlIndexFile.keys(true, null);
        String urlhash, hosthash;
        hashStat ds;
        if (i != null) while (i.hasNext()) {
            urlhash = UTF8.String(i.next());
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
        Map<String, hashStat> map = domainSampleCollector();
        
        // fetch urls from the database to determine the host in clear text
        URIMetadataRow urlref;
        if (count < 0 || count > map.size()) count = map.size();
        statsDump = new ArrayList<hostStat>();
        TreeSet<String> set = new TreeSet<String>();
        for (hashStat hs: map.values()) {
            if (hs == null) continue;
            urlref = this.load(UTF8.getBytes(hs.urlhash));
            if (urlref == null || urlref.metadata() == null || urlref.metadata().url() == null || urlref.metadata().url().getHost() == null) continue;
            set.add(urlref.metadata().url().getHost());
            count--;
            if (count == 0) break;
        }
        return set;
    }
    
    public Iterator<hostStat> statistics(int count) throws IOException {
        // prevent too heavy IO.
        if (statsDump != null && count <= statsDump.size()) return statsDump.iterator();
        
        // collect hashes from all domains
        Map<String, hashStat> map = domainSampleCollector();
        
        // order elements by size
        ScoreMap<String> s = new ConcurrentScoreMap<String>();
        for (Map.Entry<String, hashStat> e: map.entrySet()) {
            s.inc(e.getValue().urlhash, e.getValue().count);
        }
    
        // fetch urls from the database to determine the host in clear text
        Iterator<String> j = s.keys(false); // iterate urlhash-examples in reverse order (biggest first)
        URIMetadataRow urlref;
        String urlhash;
        count += 10; // make some more to prevent that we have to do this again after deletions too soon.
        if (count < 0 || s.sizeSmaller(count)) count = s.size();
        statsDump = new ArrayList<hostStat>();
        URIMetadataRow.Components comps;
        DigestURI url;
        while (j.hasNext()) {
            urlhash = j.next();
            if (urlhash == null) continue;
            urlref = this.load(UTF8.getBytes(urlhash));
            if (urlref == null || urlref.metadata() == null || urlref.metadata().url() == null || urlref.metadata().url().getHost() == null) continue;
            if (statsDump == null) return new ArrayList<hostStat>().iterator(); // some other operation has destroyed the object
            comps = urlref.metadata();
            url = comps.url();
            statsDump.add(new hostStat(url.getHost(), url.getPort(), urlhash.substring(6), s.get(urlhash)));
            count--;
            if (count == 0) break;
        }
        // finally return an iterator for the result array
        return (statsDump == null) ? new ArrayList<hostStat>().iterator() : statsDump.iterator();
    }
    
    private static class hashStat {
        public String urlhash;
        public int count;
        public hashStat(String urlhash) {
            this.urlhash = urlhash;
            this.count = 1;
        }
    }
    
    public static class hostStat {
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
        CloneableIterator<byte[]> i = this.urlIndexFile.keys(true, null);
        String hash;
        while (i != null && i.hasNext()) {
            hash = UTF8.String(i.next());
            if (hosthash.equals(hash.substring(6))) l.add(hash);
        }
        
        // then delete the urls using this list
        int cnt = 0;
        for (String h: l) {
            if (urlIndexFile.delete(UTF8.getBytes(h))) cnt++;
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
