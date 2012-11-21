// Fulltext.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 2006 as part of 'plasmaCrawlLURL.java' on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.search.index;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.federate.solr.YaCySchema;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.connector.MirrorSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.sorting.ConcurrentScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.storage.ZIPReader;
import net.yacy.cora.storage.ZIPWriter;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.index.Cache;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.table.SplitTable;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.util.MergeIterator;
import net.yacy.search.Switchboard;

import org.apache.lucene.util.Version;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;

public final class Fulltext implements Iterable<byte[]> {

    private static final String SOLR_PATH = "solr_40"; // the number should be identical to the number in the property luceneMatchVersion in solrconfig.xml
    private static final String SOLR_OLD_PATH[] = new String[]{"solr_36"};
    
    private static final long forcedCommitTimeout = 3000; // wait this time until a next forced commit is executed
    
    // class objects
	private final File                location;
    private       Index               urlIndexFile;
    private       Export              exportthread; // will have a export thread assigned if exporter is running
    private       String              tablename;
    private       ArrayList<HostStat> statsDump;
    private final MirrorSolrConnector solr;
    private final SolrConfiguration   solrScheme;
    private long                forcedCommitTime;

    protected Fulltext(final File path, final SolrConfiguration solrScheme) {
        this.location = path;
        this.tablename = null;
        this.urlIndexFile = null;
        this.exportthread = null; // will have a export thread assigned if exporter is running
        this.statsDump = null;
        this.solr = new MirrorSolrConnector(10000, 10000, 100);
        this.solrScheme = solrScheme;
        this.forcedCommitTime = 0;
    }

    protected void connectUrlDb(final String tablename, final boolean useTailCache, final boolean exceed134217727) {
    	if (this.urlIndexFile != null) return;
        this.tablename = tablename;
    	this.urlIndexFile = new SplitTable(this.location, tablename, URIMetadataRow.rowdef, useTailCache, exceed134217727);
    }

    public void disconnectUrlDb() {
    	if (this.urlIndexFile == null) return;
    	this.urlIndexFile.close();
    	this.urlIndexFile = null;
    }

    public SolrConfiguration getSolrScheme() {
        return this.solrScheme;
    }

    public boolean connectedLocalSolr() {
        return this.solr.isConnected0();
    }

    public void connectLocalSolr(final int commitWithin) throws IOException {
        File baseLocation = this.location;
        if (baseLocation.getName().equals("default")) baseLocation = baseLocation.getParentFile();
        File solrLocation = new File(baseLocation, SOLR_PATH);
        // migrate old solr to new
        for (String oldVersion: SOLR_OLD_PATH) {
            File oldLocation = new File(baseLocation, oldVersion);
            if (oldLocation.exists()) oldLocation.renameTo(solrLocation);
        }
        EmbeddedSolrConnector esc = new EmbeddedSolrConnector(solrLocation, new File(new File(Switchboard.getSwitchboard().appPath, "defaults"), "solr"));
        esc.setCommitWithinMs(commitWithin);
        Version luceneVersion = esc.getConfig().getLuceneVersion("luceneMatchVersion");
        String lvn = luceneVersion.name();
        int p = lvn.indexOf('_');
        assert SOLR_PATH.endsWith(lvn.substring(p)) : "luceneVersion = " + lvn + ", solrPath = " + SOLR_PATH + ", p = " + p + ", check defaults/solr/solrconfig.xml";
        Log.logInfo("MetadataRepository", "connected solr in " + solrLocation.toString() + ", lucene version " + lvn);
        this.solr.connect0(esc);
    }

    public void disconnectLocalSolr() {
        this.solr.disconnect0();
    }

    public boolean connectedRemoteSolr() {
        return this.solr.isConnected1();
    }

    public void connectRemoteSolr(final SolrConnector rs) {
        this.solr.connect1(rs);
    }

    public void disconnectRemoteSolr() {
        this.solr.disconnect1();
    }

    public SolrConnector getLocalSolr() {
        return this.solr.getSolr0();
    }

    public SolrConnector getRemoteSolr() {
        return this.solr.getSolr1();
    }

    public SolrConnector getSolr() {
        return this.solr;
    }

    public void clearCache() {
        if (this.urlIndexFile != null && this.urlIndexFile instanceof Cache) ((Cache) this.urlIndexFile).clearCache();
        if (this.statsDump != null) this.statsDump.clear();
        this.statsDump = null;
        this.solr.clearCache();
    }

    public void clear() throws IOException {
        if (this.exportthread != null) this.exportthread.interrupt();
        if (this.urlIndexFile == null) {
            SplitTable.delete(this.location, this.tablename);
        } else {
            this.urlIndexFile.clear();
        }
        this.solr.clear();
        // the remote solr is not cleared here because that shall be done separately
        this.statsDump = null;
    }

    public int size() {
        int size = 0;
        size += this.urlIndexFile == null ? 0 : this.urlIndexFile.size();
        size += this.solr.getSize();
        return size;
    }

    public void close() {
        this.statsDump = null;
        if (this.urlIndexFile != null) {
            this.urlIndexFile.close();
            this.urlIndexFile = null;
        }
        this.solr.close();
    }

    public int getCommitWithinMs() {
        return this.solr.getCommitWithinMs();
    }
    
    public void commit() {
        if (this.forcedCommitTime + forcedCommitTimeout > System.currentTimeMillis()) return;
        this.forcedCommitTime = Long.MAX_VALUE - forcedCommitTimeout; // set the time high to prevent that other processes get to this point meanwhile
        this.solr.commit();
        this.forcedCommitTime = System.currentTimeMillis(); // set the exact time
    }

    public Date getLoadDate(final String urlHash) {
        if (urlHash == null) return null;
        SolrDocument doc;
        try {
            doc = this.solr.getById(urlHash, YaCySchema.load_date_dt.getSolrFieldName());
        } catch (IOException e) {
            return null;
        }
        if (doc == null) return null;
        Date x = (Date) doc.getFieldValue(YaCySchema.load_date_dt.getSolrFieldName());
        if (x == null) return new Date(0);
        Date now = new Date();
        return x.after(now) ? now : x;
    }
    
    /**
     * generates an plasmaLURLEntry using the url hash
     * if the url cannot be found, this returns null
     * @param obrwi
     * @return
     */
    public URIMetadataNode getMetadata(WordReference wre, long weight) {
        if (wre == null) return null; // all time was already wasted in takeRWI to get another element
        return getMetadata(wre.urlhash(), wre, weight);
    }

    public URIMetadataNode getMetadata(final byte[] urlHash) {
        if (urlHash == null) return null;
        return getMetadata(urlHash, null, 0);
    }

    private URIMetadataNode getMetadata(final byte[] urlHash, WordReference wre, long weight) {

        // get the metadata from Solr
        try {
            SolrDocument doc = this.solr.getById(ASCII.String(urlHash));
            if (doc != null) {
            	if (this.urlIndexFile != null) this.urlIndexFile.remove(urlHash);
            	return new URIMetadataNode(doc, wre, weight);
            }
        } catch (IOException e) {
            Log.logException(e);
        }

        // get the metadata from the old metadata index
        if (this.urlIndexFile != null) try {
    		// slow migration to solr
    		final Row.Entry entry = this.urlIndexFile.remove(urlHash);
            if (entry == null) return null;
			URIMetadataRow row = new URIMetadataRow(entry, wre);
			SolrInputDocument solrInput = this.solrScheme.metadata2solr(row);
			this.putDocument(solrInput);
			return new URIMetadataNode(solrInput, wre, weight);
        } catch (final IOException e) {
            Log.logException(e);
        }

        return null;
    }

    public void putDocument(final SolrInputDocument doc) throws IOException {
        String id = (String) doc.getFieldValue(YaCySchema.id.getSolrFieldName());
        byte[] idb = ASCII.getBytes(id);
        try {
        	if (this.urlIndexFile != null) this.urlIndexFile.remove(idb);
        	SolrDocument sd = this.solr.getById(id, YaCySchema.last_modified.getSolrFieldName());
        	Date now = new Date();
        	Date sdDate = sd == null ? null : URIMetadataNode.getDate(sd, YaCySchema.last_modified);
        	if (sdDate == null || sdDate.after(now)) sdDate = now;
        	Date docDate = SolrConfiguration.getDate(doc, YaCySchema.last_modified);
        	if (docDate.after(now)) docDate = now;
        	if (sd == null || sdDate.before(docDate)) {
                if (this.solrScheme.contains(YaCySchema.ip_s)) {
                    // ip_s needs a dns lookup which causes blockings during search here
                    this.solr.add(doc);
                } else synchronized (this.solr) {
                    this.solr.add(doc);
                }
        	}
        } catch (SolrException e) {
            throw new IOException(e.getMessage(), e);
        }
        this.statsDump = null;
        if (MemoryControl.shortStatus()) clearCache();
    }

    public void putMetadata(final URIMetadataRow entry) throws IOException {
    	URIMetadataRow row = entry;

        byte[] idb = row.hash();
        String id = ASCII.String(idb);
        try {
        	if (this.urlIndexFile != null) this.urlIndexFile.remove(idb);
            SolrDocument sd = this.solr.getById(id);
            if (sd == null || (new URIMetadataNode(sd)).isOlder(row)) {
                if (this.solrScheme.contains(YaCySchema.ip_s)) {
                    // ip_s needs a dns lookup which causes blockings during search here
                    this.solr.add(getSolrScheme().metadata2solr(row));
                }  else synchronized (this.solr) {
                    this.solr.add(getSolrScheme().metadata2solr(row));
                }
            }
        } catch (SolrException e) {
            throw new IOException(e.getMessage(), e);
        }
        this.statsDump = null;
        if (MemoryControl.shortStatus()) clearCache();
    }

    /**
     * using a fragment of the url hash (6 bytes: bytes 6 to 11) it is possible to address all urls from a specific domain
     * here such a fragment can be used to delete all these domains at once
     * @param hosthash the hash of the host to be deleted
     * @param freshdate either NULL or a date in the past which is the limit for deletion. Only documents older than this date are deleted
     * @return number of deleted domains
     * @throws IOException
     */
    public int deleteDomain(final String hosthash, Date freshdate, boolean concurrent) {
        // first collect all url hashes that belong to the domain
        assert hosthash.length() == 6;
        final String q = YaCySchema.host_id_s.getSolrFieldName() + ":\"" + hosthash + "\"" +
                ((freshdate != null && freshdate.before(new Date())) ? (" AND " + YaCySchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]") : "");
        final AtomicInteger count = new AtomicInteger(0);
        Thread t = new Thread() {
            public void run() {
                // delete in solr
                synchronized (Fulltext.this.solr) {
                    try {
                        count.addAndGet(Fulltext.this.solr.deleteByQuery(q));
                        Fulltext.this.solr.commit();
                    } catch (IOException e) {}
                }
        
                // delete in old metadata structure
                if (Fulltext.this.urlIndexFile != null) {
                    final ArrayList<String> l = new ArrayList<String>();
                    synchronized (this) {
                        CloneableIterator<byte[]> i;
                        try {
                            i = Fulltext.this.urlIndexFile.keys(true, null);
                            String hash;
                            while (i != null && i.hasNext()) {
                                hash = ASCII.String(i.next());
                                if (hosthash.equals(hash.substring(6))) l.add(hash);
                            }
                            
                            // then delete the urls using this list
                            for (final String h: l) Fulltext.this.urlIndexFile.delete(ASCII.getBytes(h));
                        } catch (IOException e) {}
                    }
                }
        
                // finally remove the line with statistics
                if (Fulltext.this.statsDump != null) {
                    final Iterator<HostStat> hsi = Fulltext.this.statsDump.iterator();
                    HostStat hs;
                    while (hsi.hasNext()) {
                        hs = hsi.next();
                        if (hs.hosthash.equals(hosthash)) {
                            hsi.remove();
                            break;
                        }
                    }
                }
            }
        };
        if (concurrent) t.start(); else t.run();
        return count.get();
    }

    /**
     * remove a full subpath from the index
     * @param subpath the left path of the url; at least until the end of the host
     * @param freshdate either NULL or a date in the past which is the limit for deletion. Only documents older than this date are deleted
     * @param concurrently if true, then the method returnes immediately and runs concurrently
     */
    public int remove(String subpath, Date freshdate, final boolean concurrently) {
        int p = subpath.substring(0, subpath.length() - 1).lastIndexOf('/');
        final String path = p > 8 ? subpath.substring(0, p + 1) : subpath;
        DigestURI uri;
        try {uri = new DigestURI(path);} catch (MalformedURLException e) {return 0;}
        final String host = uri.getHost();
        final String q = YaCySchema.host_s.getSolrFieldName() + ":" + host +
                ((freshdate != null && freshdate.before(new Date())) ? (" AND " + YaCySchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]") : "");
        final AtomicInteger count = new AtomicInteger(0);
        Thread t = new Thread(){
            public void run() {
                final BlockingQueue<SolrDocument> docs = getSolr().concurrentQuery(q, 0, 1000000, 600000, -1, YaCySchema.id.getSolrFieldName(), YaCySchema.sku.getSolrFieldName());
                try {
                    SolrDocument doc;
                    while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                        String u = (String) doc.getFieldValue(YaCySchema.sku.getSolrFieldName());
                        if (u.startsWith(path)) {
                            remove(ASCII.getBytes((String) doc.getFieldValue(YaCySchema.id.getSolrFieldName())));
                            count.incrementAndGet();
                        }
                    }
                    if (count.get() > 0) Fulltext.this.solr.commit();
                } catch (InterruptedException e) {}
            }
        };
        if (concurrently) t.start(); else t.run();
        return count.get();
    }
    
    /**
     * remove a list of id's from the index
     * @param deleteIDs a list of urlhashes; each denoting a document
     * @param concurrently if true, then the method returnes immediately and runs concurrently
     */
    public void remove(final List<byte[]> deleteIDs, final boolean concurrently) {
        if (deleteIDs == null || deleteIDs.size() == 0) return;
        Thread t = new Thread() {
            public void run() {
                try {
                    synchronized (Fulltext.this.solr) {
                        for (byte[] urlHash: deleteIDs) {
                            Fulltext.this.solr.delete(ASCII.String(urlHash));
                        }
                        Fulltext.this.solr.commit();
                    }
                } catch (final Throwable e) {
                    Log.logException(e);
                }
                if (Fulltext.this.urlIndexFile != null) try {
                    for (byte[] urlHash: deleteIDs) {
                        final Row.Entry r = Fulltext.this.urlIndexFile.remove(urlHash);
                        if (r != null) Fulltext.this.statsDump = null;
                    }
                } catch (final IOException e) {}
        }};
        if (concurrently) t.start(); else t.run();
    }
    
    public boolean remove(final byte[] urlHash) {
        if (urlHash == null) return false;
        try {
            synchronized (this.solr) {
                this.solr.delete(ASCII.String(urlHash));
            }
        } catch (final Throwable e) {
            Log.logException(e);
        }
        if (this.urlIndexFile != null) try {
            final Row.Entry r = this.urlIndexFile.remove(urlHash);
            if (r != null) this.statsDump = null;
            return r != null;
        } catch (final IOException e) {
            return false;
        }
        return false;
    }

    public boolean exists(final byte[] urlHash) {
        if (urlHash == null) return false;
        if (this.urlIndexFile != null && this.urlIndexFile.has(urlHash)) return true;
        try {
            if (this.solr.exists(YaCySchema.id.getSolrFieldName(), ASCII.String(urlHash))) return true;
        } catch (final Throwable e) {
            Log.logException(e);
        }
        return false;
    }

    public String failReason(final String urlHash) throws IOException {
        if (urlHash == null) return null;
        SolrDocument doc = this.solr.getById(urlHash, YaCySchema.failreason_t.getSolrFieldName());
        if (doc == null) return null;
        String reason = (String) doc.getFieldValue(YaCySchema.failreason_t.getSolrFieldName());
        return reason == null ? null : reason.length() == 0 ? null : reason;
    }

    @Override
    public Iterator<byte[]> iterator() {
    	CloneableIterator<byte[]> a = null;
    	if (this.urlIndexFile != null) try {a = this.urlIndexFile.keys(true, null);} catch (IOException e) {}
    	final Iterator<String> idi = this.solr.iterator();
    	CloneableIterator<byte[]> b = new CloneableIterator<byte[]>() {
			@Override
			public boolean hasNext() {
				return idi.hasNext();
			}
			@Override
			public byte[] next() {
				String s = idi.next();
				return s == null ? null : ASCII.getBytes(s);
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
			@Override
			public CloneableIterator<byte[]> clone(Object modifier) {
				return this;
			}
			@Override
			public void close() {
			}
    	};
    	if (a == null) return b;
        return new MergeIterator<byte[]>(a, b,
                URIMetadataRow.rowdef.objectOrder,
                MergeIterator.simpleMerge,
                true);
    }

    public CloneableIterator<URIMetadataNode> entries() {
        // enumerates entry elements
    	final Iterator<byte[]> ids = iterator();
        return new CloneableIterator<URIMetadataNode>() {
            @Override
            public CloneableIterator<URIMetadataNode> clone(final Object secondHash) {
                return this;
            }
            @Override
            public final boolean hasNext() {
                return ids.hasNext();
            }
            @Override
            public final URIMetadataNode next() {
                byte[] id = ids.next();
                if (id == null) return null;
                return getMetadata(id);
            }
            @Override
            public final void remove() {
                ids.remove();
            }
            @Override
            public void close() {
            }
        };
    }
    
    public List<File> dumpFiles() {
        EmbeddedSolrConnector esc = (EmbeddedSolrConnector) this.solr.getSolr0();
        ArrayList<File> zips = new ArrayList<File>();
        if (esc == null) {
            Log.logWarning("Fulltext", "HOT DUMP selected solr0 == NULL, no dump list!");
            return zips;
        }
        if (esc.getStoragePath() == null) {
            Log.logWarning("Fulltext", "HOT DUMP selected solr0.getStoragePath() == NULL, no dump list!");
            return zips;
        }
        File storagePath = esc.getStoragePath().getParentFile();
        if (storagePath == null) {
            Log.logWarning("Fulltext", "HOT DUMP selected esc.getStoragePath().getParentFile() == NULL, no dump list!");
            return zips;
        }
        Log.logInfo("Fulltext", "HOT DUMP dump path = " + storagePath.toString());
        for (String p: storagePath.list()) {
            if (p.endsWith("zip")) zips.add(new File(storagePath, p));
        }
        return zips;
    }
    
    /**
     * create a dump file from the current solr directory
     * @return
     */
    public File dumpSolr() {
        EmbeddedSolrConnector esc = (EmbeddedSolrConnector) this.solr.getSolr0();
        int commitWithin = esc.getCommitWithinMs();
        File storagePath = esc.getStoragePath();
        File zipOut = new File(storagePath.toString() + "_" + GenericFormatter.SHORT_DAY_FORMATTER.format() + ".zip");
        synchronized (this.solr) {
            this.disconnectLocalSolr();
            try {
                ZIPWriter.zip(storagePath, zipOut);
            } catch (IOException e) {
                Log.logException(e);
            } finally {
                try {
                    this.connectLocalSolr(commitWithin);
                } catch (IOException e) {
                    Log.logException(e);
                }
            }
        }
        return zipOut;
    }
    
    /**
     * restore a solr dump to the current solr directory
     * @param solrDumpZipFile
     */
    public void restoreSolr(File solrDumpZipFile) {
        EmbeddedSolrConnector esc = (EmbeddedSolrConnector) this.solr.getSolr0();
        int commitWithin = esc.getCommitWithinMs();
        File storagePath = esc.getStoragePath();
        synchronized (this.solr) {
            this.disconnectLocalSolr();
            try {
                ZIPReader.unzip(solrDumpZipFile, storagePath);
            } catch (IOException e) {
                Log.logException(e);
            } finally {
                try {
                    this.connectLocalSolr(commitWithin);
                } catch (IOException e) {
                    Log.logException(e);
                }
            }
        }
    }
    
    // export methods
    public Export export(final File f, final String filter, final HandleSet set, final int format, final boolean dom) {
        if ((this.exportthread != null) && (this.exportthread.isAlive())) {
            Log.logWarning("LURL-EXPORT", "cannot start another export thread, already one running");
            return this.exportthread;
        }
        this.exportthread = new Export(f, filter, set, format, dom);
        this.exportthread.start();
        return this.exportthread;
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

        private Export(final File f, final String filter, final HandleSet set, final int format, boolean dom) {
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

        @Override
        public void run() {
            try {
                final File parentf = this.f.getParentFile();
                if (parentf != null) parentf.mkdirs();
                final PrintWriter pw = new PrintWriter(new BufferedOutputStream(new FileOutputStream(this.f)));
                if (this.format == 1) {
                    pw.println("<html><head></head><body>");
                }
                if (this.format == 2) {
                    pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                    pw.println("<?xml-stylesheet type='text/xsl' href='/yacysearch.xsl' version='1.0'?>");
                    pw.println("<rss version=\"2.0\" xmlns:yacy=\"http://www.yacy.net/\" xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\" xmlns:atom=\"http://www.w3.org/2005/Atom\">");
                    pw.println("<channel>");
                    pw.println("<title>YaCy Peer-to-Peer - Web-Search LURL Export</title>");
                    pw.println("<description></description>");
                    pw.println("<link>http://yacy.net</link>");
                }

                if (this.dom) {
                    final TreeSet<String> set = domainNameCollector(-1, domainSampleCollector());
                    for (final String host: set) {
                        if (!host.matches(this.filter)) continue;
                        if (this.format == 0) pw.println(host);
                        if (this.format == 1) pw.println("<a href=\"http://" + host + "\">" + host + "</a><br>");
                        this.count++;
                    }
                } else {
                    final Iterator<URIMetadataNode> i = entries(); // iterates indexURLEntry objects
                    URIMetadataNode entry;
                    String url;
                    while (i.hasNext()) {
                        entry = i.next();
                        if (this.set != null && !this.set.has(entry.hash())) continue;
                        url = entry.url().toNormalform(true);
                        if (!url.matches(this.filter)) continue;
                        if (this.format == 0) {
                            pw.println(url);
                        }
                        if (this.format == 1) {
                            pw.println("<a href=\"" + url + "\">" + CharacterCoding.unicode2xml(entry.dc_title(), true) + "</a><br>");
                        }
                        if (this.format == 2) {
                            pw.println("<item>");
                            pw.println("<title>" + CharacterCoding.unicode2xml(entry.dc_title(), true) + "</title>");
                            pw.println("<link>" + MultiProtocolURI.escape(url) + "</link>");
                            if (!entry.dc_creator().isEmpty()) pw.println("<author>" + CharacterCoding.unicode2xml(entry.dc_creator(), true) + "</author>");
                            if (!entry.dc_subject().isEmpty()) pw.println("<description>" + CharacterCoding.unicode2xml(entry.dc_subject(), true) + "</description>");
                            pw.println("<pubDate>" + entry.moddate().toString() + "</pubDate>");
                            pw.println("<yacy:size>" + entry.size() + "</yacy:size>");
                            pw.println("<guid isPermaLink=\"false\">" + ASCII.String(entry.hash()) + "</guid>");
                            pw.println("</item>");
                        }
                        this.count++;
                    }
                }
                if (this.format == 1) {
                    pw.println("</body></html>");
                }
                if (this.format == 2) {
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

    /**
     * collect domain samples: all url hashes from the metadata database is listed and the domain part
     * of the url hashes is used to count how many of these domain hashes appear
     * @return a map from domain hashes to hash statistics
     * @throws IOException
     */
    public Map<String, URLHashCounter> domainSampleCollector() throws IOException {
        final Map<String, URLHashCounter> map = new HashMap<String, URLHashCounter>();
        // first collect all domains and calculate statistics about it
        synchronized (this) {
            final Iterator<byte[]> i = this.iterator();
            String hosthash;
            byte[] urlhashb;
            URLHashCounter ds;
            if (i != null) while (i.hasNext()) {
                urlhashb = i.next();
                hosthash = ASCII.String(urlhashb, 6, 6);
                ds = map.get(hosthash);
                if (ds == null) {
                    ds = new URLHashCounter(urlhashb);
                    map.put(hosthash, ds);
                } else {
                    ds.count++;
                }
            }
        }
        return map;
    }

    /**
     * create a list of domain names in this database
     * @param count number of entries or -1 for all
     * @param domainSamples a map from domain hashes to hash statistics
     * @return a set of domain names, ordered by name of the domains
     */
    private TreeSet<String> domainNameCollector(int count, final Map<String, URLHashCounter> domainSamples) {
        // collect hashes from all domains

        // fetch urls from the database to determine the host in clear text
        URIMetadataNode urlref;
        if (count < 0 || count > domainSamples.size()) count = domainSamples.size();
        this.statsDump = new ArrayList<HostStat>();
        final TreeSet<String> set = new TreeSet<String>();
        for (final URLHashCounter hs: domainSamples.values()) {
            if (hs == null) continue;
            urlref = this.getMetadata(hs.urlhashb);
            if (urlref == null || urlref.url() == null || urlref.url().getHost() == null) continue;
            set.add(urlref.url().getHost());
            count--;
            if (count == 0) break;
        }
        return set;
    }

    /**
     * calculate a score map for url hash samples: each sample is a single url hash
     * that stands for all entries for the corresponding domain. The map counts the number
     * of occurrences of the domain
     * @param domainSamples a map from domain hashes to hash statistics
     * @return a map from url hash samples to counters
     */
    public ScoreMap<String> urlSampleScores(final Map<String, URLHashCounter> domainSamples) {
        final ScoreMap<String> urlSampleScore = new ConcurrentScoreMap<String>();
        for (final Map.Entry<String, URLHashCounter> e: domainSamples.entrySet()) {
            urlSampleScore.inc(ASCII.String(e.getValue().urlhashb), e.getValue().count);
        }
        return urlSampleScore;
    }

    /**
     * calculate all domain names for all domain hashes
     * @param domainSamples a map from domain hashes to hash statistics
     * @return a map from domain hashes to host stats including domain names
     */
    public Map<String, HostStat> domainHashResolver(final Map<String, URLHashCounter> domainSamples) {
        final HashMap<String, HostStat> hostMap = new HashMap<String, HostStat>();
        URIMetadataNode urlref;

        final ScoreMap<String> hosthashScore = new ConcurrentScoreMap<String>();
        for (final Map.Entry<String, URLHashCounter> e: domainSamples.entrySet()) {
            hosthashScore.inc(ASCII.String(e.getValue().urlhashb, 6, 6), e.getValue().count);
        }
        DigestURI url;
        for (final Map.Entry<String, URLHashCounter> e: domainSamples.entrySet()) {
            urlref = this.getMetadata(e.getValue().urlhashb);
            url = urlref.url();
            hostMap.put(e.getKey(), new HostStat(url.getHost(), url.getPort(), e.getKey(), hosthashScore.get(e.getKey())));
        }
        return hostMap;
    }

    public Iterator<HostStat> statistics(int count, final ScoreMap<String> domainScore) {
        // prevent too heavy IO.
        if (this.statsDump != null && count <= this.statsDump.size()) return this.statsDump.iterator();

        // fetch urls from the database to determine the host in clear text
        final Iterator<String> j = domainScore.keys(false); // iterate urlhash-examples in reverse order (biggest first)
        URIMetadataNode urlref;
        String urlhash;
        count += 10; // make some more to prevent that we have to do this again after deletions too soon.
        if (count < 0 || domainScore.sizeSmaller(count)) count = domainScore.size();
        this.statsDump = new ArrayList<HostStat>();
        DigestURI url;
        while (j.hasNext()) {
            urlhash = j.next();
            if (urlhash == null) continue;
            urlref = this.getMetadata(ASCII.getBytes(urlhash));
            if (urlref == null || urlref.url() == null || urlref.url().getHost() == null) continue;
            if (this.statsDump == null) return new ArrayList<HostStat>().iterator(); // some other operation has destroyed the object
            url = urlref.url();
            this.statsDump.add(new HostStat(url.getHost(), url.getPort(), urlhash.substring(6), domainScore.get(urlhash)));
            count--;
            if (count == 0) break;
        }
        // finally return an iterator for the result array
        return (this.statsDump == null) ? new ArrayList<HostStat>().iterator() : this.statsDump.iterator();
    }

    private static class URLHashCounter {
        public byte[] urlhashb;
        public int count;
        public URLHashCounter(final byte[] urlhashb) {
            this.urlhashb = urlhashb;
            this.count = 1;
        }
    }

    public static class HostStat {
        public String hostname, hosthash;
        public int port;
        public int count;
        private HostStat(final String host, final int port, final String urlhashfragment, final int count) {
            assert urlhashfragment.length() == 6;
            this.hostname = host;
            this.port = port;
            this.hosthash = urlhashfragment;
            this.count = count;
        }
    }
}
