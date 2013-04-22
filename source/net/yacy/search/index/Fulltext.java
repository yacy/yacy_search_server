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
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.ASCII;
import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.ShardSelection;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.EmbeddedInstance;
import net.yacy.cora.federate.solr.instance.InstanceMirror;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.federate.solr.instance.ShardInstance;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.sorting.ScoreMap;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.cora.storage.ZIPReader;
import net.yacy.cora.storage.ZIPWriter;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.meta.URIMetadataRow;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.index.Cache;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.table.SplitTable;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphConfiguration;
import net.yacy.search.schema.WebgraphSchema;

import org.apache.commons.httpclient.util.DateUtil;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.lucene.util.Version;
import org.apache.solr.core.CoreContainer;

public final class Fulltext {

    private static final String SOLR_PATH = "solr_40"; // the number should be identical to the number in the property luceneMatchVersion in solrconfig.xml
    private static final String SOLR_OLD_PATH[] = new String[]{"solr_36"};
    
    // class objects
	private final File                 segmentPath;
    private       Index                urlIndexFile;
    private       Export               exportthread; // will have a export thread assigned if exporter is running
    private       String               tablename;
    private       ArrayList<HostStat>  statsDump;
    private       InstanceMirror       solrInstances;
    private final CollectionConfiguration collectionConfiguration;
    private final WebgraphConfiguration   webgraphConfiguration;
    private final LinkedBlockingQueue<URIMetadataRow> pendingCollectionInputRows;
    private final LinkedBlockingQueue<SolrInputDocument> pendingCollectionInputDocuments;

    protected Fulltext(final File segmentPath, final CollectionConfiguration collectionConfiguration, final WebgraphConfiguration webgraphConfiguration) {
        this.segmentPath = segmentPath;
        this.tablename = null;
        this.urlIndexFile = null;
        this.exportthread = null; // will have a export thread assigned if exporter is running
        this.statsDump = null;
        this.solrInstances = new InstanceMirror();
        this.collectionConfiguration = collectionConfiguration;
        this.webgraphConfiguration = webgraphConfiguration;
        this.pendingCollectionInputRows = new LinkedBlockingQueue<URIMetadataRow>();
        this.pendingCollectionInputDocuments = new LinkedBlockingQueue<SolrInputDocument>();
    }

    /**
     * @deprecated
     * used only for migration
     * @return the connected URLDb

     */
    @Deprecated
    public Index getURLDb() {
        return this.urlIndexFile;
    }

    /**
     * true if old metadata index URLDb is connected.
     * used only for migration
     * @deprecated
     * current and future versions use Solr for metadata
     */
    @Deprecated
    public boolean connectedURLDb() {
        return this.urlIndexFile != null;
    }

    protected void connectUrlDb(final String tablename, final boolean useTailCache, final boolean exceed134217727) {
    	if (this.urlIndexFile != null) return;
        this.tablename = tablename;
    	this.urlIndexFile = new SplitTable(new File(this.segmentPath, "default"), tablename, URIMetadataRow.rowdef, useTailCache, exceed134217727);
        // SplitTable always returns != null, even if no file exists.
        // as old UrlDb should be null if not exist, check and close if empty
        // TODO: check if a SplitTable.open() returning null or error status on not existing file is preferable
        if (this.urlIndexFile.isEmpty()) {
            disconnectUrlDb();
        }
    }

    public void disconnectUrlDb() {
    	if (this.urlIndexFile == null) return;
    	this.urlIndexFile.close();
    	this.urlIndexFile = null;
    }

    public CollectionConfiguration getDefaultConfiguration() {
        return this.collectionConfiguration;
    }

    public WebgraphConfiguration getWebgraphConfiguration() {
        return this.webgraphConfiguration;
    }

    public boolean connectedLocalSolr() {
        return this.solrInstances.isConnected0();
    }

    public void connectLocalSolr() throws IOException {
        File solrLocation = new File(this.segmentPath, SOLR_PATH);
        // migrate old solr to new
        for (String oldVersion: SOLR_OLD_PATH) {
            File oldLocation = new File(this.segmentPath, oldVersion);
            if (oldLocation.exists()) oldLocation.renameTo(solrLocation);
        }
        assert CoreContainer.DEFAULT_DEFAULT_CORE_NAME.equals(CollectionSchema.CORE_NAME); // check that solr and we use the same default core name
        
        EmbeddedInstance localCollectionInstance = new EmbeddedInstance(new File(new File(Switchboard.getSwitchboard().appPath, "defaults"), "solr"), solrLocation, CollectionSchema.CORE_NAME, new String[]{CollectionSchema.CORE_NAME, WebgraphSchema.CORE_NAME});
        EmbeddedSolrConnector localCollectionConnector = new EmbeddedSolrConnector(localCollectionInstance);

        Version luceneVersion = localCollectionConnector.getConfig().getLuceneVersion("luceneMatchVersion");
        String lvn = luceneVersion.name();
        Log.logInfo("Fulltext", "using lucene version " + lvn);
        int p = lvn.indexOf('_');
        assert SOLR_PATH.endsWith(lvn.substring(p)) : "luceneVersion = " + lvn + ", solrPath = " + SOLR_PATH + ", p = " + p + ", check defaults/solr/solrconfig.xml";
        Log.logInfo("Fulltext", "connected solr in " + solrLocation.toString() + ", lucene version " + lvn + ", default core size: " + localCollectionConnector.getSize());
        this.solrInstances.connect0(localCollectionInstance);
    }

    public void disconnectLocalSolr() {
        this.solrInstances.disconnect0();
    }

    public boolean connectedRemoteSolr() {
        return this.solrInstances.isConnected1();
    }

    public void connectRemoteSolr(final ArrayList<RemoteInstance> instances) {
        this.solrInstances.connect1(new ShardInstance(instances, ShardSelection.Method.MODULO_HOST_MD5));
    }

    public void disconnectRemoteSolr() {
        this.solrInstances.disconnect1();
    }

    public EmbeddedSolrConnector getDefaultEmbeddedConnector() {
        return this.solrInstances.getDefaultEmbeddedConnector();
    }

    public EmbeddedSolrConnector getEmbeddedConnector(String corename) {
        return this.solrInstances.getEmbeddedConnector(corename);
    }

    public RemoteSolrConnector getDefaultRemoteSolrConnector() {
        if (this.solrInstances.getSolr1() == null) return null;
        try {
            return new RemoteSolrConnector(this.solrInstances.getSolr1());
        } catch (IOException e) {
            return null;
        }
    }
    
    public SolrConnector getDefaultConnector() {
        return this.solrInstances.getDefaultMirrorConnector();
    }
    
    public SolrConnector getWebgraphConnector() {
        return this.solrInstances.getMirrorConnector(WebgraphSchema.CORE_NAME);
    }

    public void clearCache() {
        if (this.urlIndexFile != null && this.urlIndexFile instanceof Cache) ((Cache) this.urlIndexFile).clearCache();
        if (this.statsDump != null) this.statsDump.clear();
        this.solrInstances.clearCache();
        this.statsDump = null;
    }

    public void clearURLIndex() throws IOException {
        if (this.exportthread != null) this.exportthread.interrupt();
        if (this.urlIndexFile == null) {
            SplitTable.delete(new File(this.segmentPath, "default"), this.tablename);
        } else {
            this.urlIndexFile.clear();
        }
        this.statsDump = null;
        this.commit(true);
    }

    public void clearLocalSolr() throws IOException {
        EmbeddedInstance instance = this.solrInstances.getSolr0();
        if (instance != null) {
            for (String name: instance.getCoreNames()) new EmbeddedSolrConnector(instance, name).clear();
        }
        this.commit(false);
        this.solrInstances.clearCache();
    }

    public void clearRemoteSolr() throws IOException {
        ShardInstance instance = this.solrInstances.getSolr1();
        if (instance != null) {
            for (String name: instance.getCoreNames()) new RemoteSolrConnector(instance, name).clear();
        }
        this.solrInstances.clearCache();
    }

    /**
     * get the size of the default index
     * @return
     */
    public long collectionSize() {
        long size = this.urlIndexFile == null ? 0 : this.urlIndexFile.size();
        size += this.getDefaultConnector().getSize();
        return size;
    }
    
    /**
     * get the size of the webgraph index
     * @return
     */
    public long webgraphSize() {
        return this.getWebgraphConnector().getSize();
    }

    public void close() {
        this.statsDump = null;
        if (this.urlIndexFile != null) {
            this.urlIndexFile.close();
            this.urlIndexFile = null;
        }
        this.solrInstances.close();
    }
    
    public void commit(boolean softCommit) {
        getDefaultConnector().commit(softCommit);
        getWebgraphConnector().commit(softCommit);
    }

    public Date getLoadDate(final String urlHash) {
        if (urlHash == null) return null;
        Date x;
        try {
            x = (Date) this.getDefaultConnector().getFieldById(urlHash, CollectionSchema.load_date_dt.getSolrFieldName());
        } catch (IOException e) {
            return null;
        }
        return x;
    }

    public DigestURI getURL(final byte[] urlHash) {
        if (urlHash == null) return null;

        // try to get the data from the delayed cache; this happens if we retrieve this from a fresh search result
        String u = ASCII.String(urlHash);
        for (URIMetadataRow entry: this.pendingCollectionInputRows) {
            if (u.equals(ASCII.String(entry.hash()))) {
                if (this.urlIndexFile != null) try {this.urlIndexFile.remove(urlHash);} catch (IOException e) {} // migration
                return entry.url();
            }
        }

        for (SolrInputDocument doc: this.pendingCollectionInputDocuments) {
            if (u.equals(doc.getFieldValue(CollectionSchema.id.getSolrFieldName()))) {
                if (this.urlIndexFile != null) try {this.urlIndexFile.remove(urlHash);} catch (IOException e) {} // migration
                String url = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                if (url != null) try {return new DigestURI(url);} catch (MalformedURLException e) {}
            }
        }
        
        String x;
        try {
            x = (String) this.getDefaultConnector().getFieldById(ASCII.String(urlHash), CollectionSchema.sku.getSolrFieldName());
        } catch (IOException e) {
            return null;
        }
        if (x == null) return null;
        try {
            DigestURI uri = new DigestURI(x, urlHash);
            return uri;
        } catch (MalformedURLException e) {
            return null;
        }
    }
    
    public URIMetadataNode getMetadata(WeakPriorityBlockingQueue.Element<WordReferenceVars> element) {
        if (element == null) return null;
        WordReferenceVars wre = element.getElement();
        long weight = element.getWeight();
        if (wre == null) return null; // all time was already wasted in takeRWI to get another element
        URIMetadataNode node = getMetadata(wre.urlhash(), wre, weight);
        return node;
    }

    public URIMetadataNode getMetadata(final byte[] urlHash) {
        if (urlHash == null) return null;
        return getMetadata(urlHash, null, 0);
    }
    
    private URIMetadataNode getMetadata(final byte[] urlHash, WordReferenceVars wre, long weight) {
        String u = ASCII.String(urlHash);
        
        // try to get the data from the delayed cache; this happens if we retrieve this from a fresh search result
        for (URIMetadataRow entry: this.pendingCollectionInputRows) {
            if (u.equals(ASCII.String(entry.hash()))) {
                if (this.urlIndexFile != null) try {this.urlIndexFile.remove(urlHash);} catch (IOException e) {} // migration
                SolrDocument sd = this.collectionConfiguration.toSolrDocument(getDefaultConfiguration().metadata2solr(entry));
                return new URIMetadataNode(sd, wre, weight);
            }
        }

        for (SolrInputDocument doc: this.pendingCollectionInputDocuments) {
            if (u.equals(doc.getFieldValue(CollectionSchema.id.getSolrFieldName()))) {
                if (this.urlIndexFile != null) try {this.urlIndexFile.remove(urlHash);} catch (IOException e) {} // migration
                SolrDocument sd = this.collectionConfiguration.toSolrDocument(doc);
                return new URIMetadataNode(sd, wre, weight);
            }
        }
        
        // get the metadata from Solr
        try {
            SolrDocument doc = this.getDefaultConnector().getById(u);
            if (doc != null) {
            	if (this.urlIndexFile != null) this.urlIndexFile.remove(urlHash); // migration
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
			SolrInputDocument solrInput = this.collectionConfiguration.metadata2solr(row);
			this.putDocument(solrInput);
			SolrDocument sd = this.collectionConfiguration.toSolrDocument(solrInput);
			return new URIMetadataNode(sd, wre, weight);
        } catch (final IOException e) {
            Log.logException(e);
        }

        return null;
    }

    public void putDocument(final SolrInputDocument doc) throws IOException {
        SolrConnector connector = this.getDefaultConnector();
        if (connector == null) return;
        String id = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
        byte[] idb = ASCII.getBytes(id);
        try {
            if (this.urlIndexFile != null) this.urlIndexFile.remove(idb);
            //Date sdDate = (Date) connector.getFieldById(id, CollectionSchema.last_modified.getSolrFieldName());
            //Date docDate = null;
            //if (sdDate == null || (docDate = SchemaConfiguration.getDate(doc, CollectionSchema.last_modified)) == null || sdDate.before(docDate)) {
                if (this.collectionConfiguration.contains(CollectionSchema.ip_s)) {
                    // ip_s needs a dns lookup which causes blockings during search here
                    connector.add(doc);
                } else synchronized (this.solrInstances) {
                    connector.add(doc);
                }
            //}
        } catch (SolrException e) {
            throw new IOException(e.getMessage(), e);
        }
        this.statsDump = null;
        if (MemoryControl.shortStatus()) clearCache();
    }
    
    public void putDocuments(final Collection<SolrInputDocument> docs) throws IOException {
        if (docs == null || docs.size() == 0) return;
        this.getDefaultConnector().add(docs);
        this.statsDump = null;
        if (MemoryControl.shortStatus()) clearCache();
    }
    
    public void putDocumentLater(final SolrInputDocument doc) {
        if (MemoryControl.shortStatus()) {
            try {
                putDocument(doc);
                return;
            } catch (IOException ee) {
                Log.logException(ee);
            }
        }
        try {
            this.pendingCollectionInputDocuments.put(doc);
        } catch (InterruptedException e) {
            try {
                putDocument(doc);
            } catch (IOException ee) {
                Log.logException(ee);
            }
        }
    }
    
    public int pendingInputDocuments() {
        return this.pendingCollectionInputRows.size() + this.pendingCollectionInputDocuments.size();
    }
    
    public int processPendingInputDocuments(int count) throws IOException {
        if (count == 0) return 0;
        boolean shortMemStatus = MemoryControl.shortStatus();
        if (!shortMemStatus || this.pendingCollectionInputDocuments.size() < count) {
            pendingRows2Docs(count);
        }
        SolrInputDocument doc;
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument>(count);
        while ((shortMemStatus || count-- > 0) && (doc = this.pendingCollectionInputDocuments.poll()) != null) {
            docs.add(doc);
        }
        if (docs.size() > 0) this.putDocuments(docs);
        return docs.size();
    }
    
    private void pendingRows2Docs(int count) throws IOException {
        URIMetadataRow entry;
        while (count-- > 0 && (entry = this.pendingCollectionInputRows.poll()) != null) {
            byte[] idb = entry.hash();
            String id = ASCII.String(idb);
            try {
                if (this.urlIndexFile != null) this.urlIndexFile.remove(idb);
                // because node entries are richer than metadata entries we must check if they exist to prevent that they are overwritten
                SolrDocument sd = this.getDefaultConnector().getById(id);
                if (sd == null || (new URIMetadataNode(sd)).isOlder(entry)) {
                    putDocumentLater(getDefaultConfiguration().metadata2solr(entry));
                }                                                                                                                     
            } catch (SolrException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    }
    
    public void putEdges(final Collection<SolrInputDocument> edges) throws IOException {
        if (edges == null || edges.size() == 0) return;
        try {
            this.getWebgraphConnector().add(edges);
        } catch (SolrException e) {
            throw new IOException(e.getMessage(), e);
        }
        this.statsDump = null;
        if (MemoryControl.shortStatus()) clearCache();
    }
    
    public void putMetadata(final URIMetadataRow entry) throws IOException {
        byte[] idb = entry.hash();
        String id = ASCII.String(idb);
        try {
            if (this.urlIndexFile != null) this.urlIndexFile.remove(idb);
            // because node entries are richer than metadata entries we must check if they exist to prevent that they are overwritten
            SolrDocument sd = this.getDefaultConnector().getById(id);
            if (sd == null || (new URIMetadataNode(sd)).isOlder(entry)) {
                putDocument(getDefaultConfiguration().metadata2solr(entry));
            }                                                                                                                     
        } catch (SolrException e) {
            throw new IOException(e.getMessage(), e);
        }
        this.statsDump = null;
        if (MemoryControl.shortStatus()) clearCache();
    }

    public void putMetadataLater(final URIMetadataRow entry) throws IOException {
        if (MemoryControl.shortStatus()) {
            putMetadata(entry);
            return;
        }
        try {
            this.pendingCollectionInputRows.put(entry);
        } catch (InterruptedException e) {
            try {
                putMetadata(entry);
            } catch (IOException ee) {
                Log.logException(ee);
            }
        }
    }

    /**
     * using a fragment of the url hash (6 bytes: bytes 6 to 11) it is possible to address all urls from a specific domain
     * here such a fragment can be used to delete all these domains at once
     * @param hosthash the hash of the host to be deleted
     * @param freshdate either NULL or a date in the past which is the limit for deletion. Only documents older than this date are deleted
     * @throws IOException
     */
    public void deleteDomainHashpart(final String hosthash, Date freshdate, boolean concurrent) {
        // first collect all url hashes that belong to the domain
        assert hosthash.length() == 6;
        final String collection1Query = CollectionSchema.host_id_s.getSolrFieldName() + ":\"" + hosthash + "\"" +
                ((freshdate != null && freshdate.before(new Date())) ?
                        (" AND " + CollectionSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]") :
                        ""
                );
        final String webgraphQuery = WebgraphSchema.source_host_id_s.getSolrFieldName() + ":\"" + hosthash + "\"" +
                ((freshdate != null && freshdate.before(new Date())) ?
                        (" AND " + WebgraphSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]") :
                        ""
                );
        Thread t = new Thread() {
            public void run() {
                // delete in solr
                synchronized (Fulltext.this.solrInstances) {
                    try {Fulltext.this.getDefaultConnector().deleteByQuery(collection1Query);} catch (IOException e) {}
                    try {Fulltext.this.getWebgraphConnector().deleteByQuery(webgraphQuery);} catch (IOException e) {}
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
        if (concurrent) t.start(); else {
            t.run();
            Fulltext.this.commit(true);
        }
    }

    public void deleteDomainHostname(final String hostname, Date freshdate, boolean concurrent) {
        // first collect all url hashes that belong to the domain
        final String collectionQuery =
                CollectionSchema.host_s.getSolrFieldName() + ":\"" + hostname + "\"" +
                ((freshdate != null && freshdate.before(new Date())) ?
                        (" AND " + CollectionSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]") :
                        ""
                );
        final String webgraphQuery =
                WebgraphSchema.source_host_s.getSolrFieldName() + ":\"" + hostname + "\"" +
                ((freshdate != null && freshdate.before(new Date())) ?
                        (" AND " + WebgraphSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]") :
                        ""
                );
        Thread t = new Thread() {
            public void run() {
                // delete in solr
                synchronized (Fulltext.this.solrInstances) {
                    try {Fulltext.this.getDefaultConnector().deleteByQuery(collectionQuery);} catch (IOException e) {}
                    try {Fulltext.this.getWebgraphConnector().deleteByQuery(webgraphQuery);} catch (IOException e) {}
                }
                // finally remove the line with statistics
                if (Fulltext.this.statsDump != null) {
                    final Iterator<HostStat> hsi = Fulltext.this.statsDump.iterator();
                    HostStat hs;
                    while (hsi.hasNext()) {
                        hs = hsi.next();
                        if (hs.hostname.equals(hostname)) {
                            hsi.remove();
                            break;
                        }
                    }
                }
            }
        };
        if (concurrent) t.start(); else {
            t.run();
            Fulltext.this.commit(true);
        }
    }

    /**
     * remove a full subpath from the index
     * @param subpath the left path of the url; at least until the end of the host
     * @param freshdate either NULL or a date in the past which is the limit for deletion. Only documents older than this date are deleted
     * @param concurrently if true, then the method returnes immediately and runs concurrently
     */
    public int remove(final String basepath, Date freshdate, final boolean concurrently) {
        DigestURI uri;
        try {uri = new DigestURI(basepath);} catch (MalformedURLException e) {return 0;}
        final String host = uri.getHost();
        final String collectionQuery = CollectionSchema.host_s.getSolrFieldName() + ":\"" + host + "\"" +
                ((freshdate != null && freshdate.before(new Date())) ? (" AND " + CollectionSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]") : "");
        final AtomicInteger count = new AtomicInteger(0);
        Thread t = new Thread(){
            public void run() {
                final BlockingQueue<SolrDocument> docs = Fulltext.this.getDefaultConnector().concurrentQuery(collectionQuery, 0, 1000000, 600000, -1, CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName());
                try {
                    SolrDocument doc;
                    while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                        String u = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                        if (u.startsWith(basepath)) {
                            remove(ASCII.getBytes((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName())));
                            count.incrementAndGet();
                        }
                    }
                    if (count.get() > 0) Fulltext.this.commit(true);
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
                    synchronized (Fulltext.this.solrInstances) {
                        for (byte[] urlHash: deleteIDs) {
                            Fulltext.this.getDefaultConnector().deleteById(ASCII.String(urlHash));
                            Fulltext.this.getWebgraphConnector().deleteByQuery(WebgraphSchema.source_id_s.getSolrFieldName() + ":\"" + ASCII.String(urlHash) + "\"");
                        }
                        Fulltext.this.commit(true);
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
            synchronized (this.solrInstances) {
                this.getDefaultConnector().deleteById(ASCII.String(urlHash));
                this.getWebgraphConnector().deleteByQuery(WebgraphSchema.source_id_s.getSolrFieldName() + ":\"" + ASCII.String(urlHash) + "\"");
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

    public boolean exists(final String urlHash) {
        if (urlHash == null) return false;
        for (URIMetadataRow entry: this.pendingCollectionInputRows) {
            if (urlHash.equals(ASCII.String(entry.hash()))) return true;
        }
        for (SolrInputDocument doc: this.pendingCollectionInputDocuments) {
            if (urlHash.equals(doc.getFieldValue(CollectionSchema.id.getSolrFieldName()))) return true;
        }
        try {
            if (this.getDefaultConnector().existsByQuery(AbstractSolrConnector.idQuery(urlHash))) return true;
        } catch (final Throwable e) {
            Log.logException(e);
        }
        if (this.urlIndexFile != null && this.urlIndexFile.has(ASCII.getBytes(urlHash))) return true;
        return false;
    }

    public String failReason(final String urlHash) throws IOException {
        if (urlHash == null) return null;
        String reason = (String) this.getDefaultConnector().getFieldById(urlHash, CollectionSchema.failreason_t.getSolrFieldName());
        if (reason == null) return null;
        return reason == null ? null : reason.length() == 0 ? null : reason;
    }
    
    public List<File> dumpFiles() {
        EmbeddedInstance esc = this.solrInstances.getSolr0();
        ArrayList<File> zips = new ArrayList<File>();
        if (esc == null) {
            Log.logWarning("Fulltext", "HOT DUMP selected solr0 == NULL, no dump list!");
            return zips;
        }
        if (esc.getContainerPath() == null) {
            Log.logWarning("Fulltext", "HOT DUMP selected solr0.getStoragePath() == NULL, no dump list!");
            return zips;
        }
        File storagePath = esc.getContainerPath().getParentFile();
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
        EmbeddedInstance esc = this.solrInstances.getSolr0();
        File storagePath = esc.getContainerPath();
        File zipOut = new File(storagePath.toString() + "_" + GenericFormatter.SHORT_DAY_FORMATTER.format() + ".zip");
        synchronized (this.solrInstances) {
            this.disconnectLocalSolr();
            this.solrInstances.close();
            try {
                ZIPWriter.zip(storagePath, zipOut);
            } catch (IOException e) {
                Log.logException(e);
            } finally {
                this.solrInstances = new InstanceMirror();
                try {
                    this.connectLocalSolr();
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
        EmbeddedInstance esc = this.solrInstances.getSolr0();
        File storagePath = esc.getContainerPath();
        synchronized (this.solrInstances) {
            this.disconnectLocalSolr();
            this.solrInstances.close();
            try {
                ZIPReader.unzip(solrDumpZipFile, storagePath);
            } catch (IOException e) {
                Log.logException(e);
            } finally {
                this.solrInstances = new InstanceMirror();
                try {
                    this.connectLocalSolr();
                } catch (IOException e) {
                    Log.logException(e);
                }
            }
        }
    }
    
    // export methods
    public Export export(final File f, final String filter, final int format, final boolean dom) {
        if ((this.exportthread != null) && (this.exportthread.isAlive())) {
            Log.logWarning("LURL-EXPORT", "cannot start another export thread, already one running");
            return this.exportthread;
        }
        this.exportthread = new Export(f, filter, format, dom);
        this.exportthread.start();
        return this.exportthread;
    }

    public Export export() {
        return this.exportthread;
    }

    public class Export extends Thread {
        private final File f;
        private final Pattern pattern;
        private int count;
        private String failure;
        private final int format;
        private final boolean dom;

        private Export(final File f, final String filter, final int format, boolean dom) {
            // format: 0=text, 1=html, 2=rss/xml
            this.f = f;
            this.pattern = filter == null ? null : Pattern.compile(filter);
            this.count = 0;
            this.failure = null;
            this.format = format;
            this.dom = dom;
            //if ((dom) && (format == 2)) dom = false;
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
                    pw.println("<title>YaCy Peer-to-Peer - Web-Search URL Export</title>");
                    pw.println("<description></description>");
                    pw.println("<link>http://yacy.net</link>");
                }
                
               
                if (this.dom) {
                    Map<String, ReversibleScoreMap<String>> scores = Fulltext.this.getDefaultConnector().getFacets(CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", 100000000, CollectionSchema.host_s.getSolrFieldName());
                    ReversibleScoreMap<String> stats = scores.get(CollectionSchema.host_s.getSolrFieldName());
                    for (final String host: stats) {
                        if (this.pattern != null && !this.pattern.matcher(host).matches()) continue;
                        if (this.format == 0) pw.println(host);
                        if (this.format == 1) pw.println("<a href=\"http://" + host + "\">" + host + "</a><br>");
                        this.count++;
                    }
                } else {
                    BlockingQueue<SolrDocument> docs = Fulltext.this.getDefaultConnector().concurrentQuery(CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", 0, 100000000, 10 * 60 * 60 * 1000, 100,
                            CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName(), CollectionSchema.title.getSolrFieldName(),
                            CollectionSchema.author.getSolrFieldName(), CollectionSchema.description.getSolrFieldName(), CollectionSchema.size_i.getSolrFieldName(), CollectionSchema.last_modified.getSolrFieldName());
                    SolrDocument doc;
                    ArrayList<?> title;
                    String url, author, description, hash;
                    Integer size;
                    Date date;
                    while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                        hash = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
                        url = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                        title = (ArrayList<?>) doc.getFieldValue(CollectionSchema.title.getSolrFieldName());
                        author = (String) doc.getFieldValue(CollectionSchema.author.getSolrFieldName());
                        description = (String) doc.getFieldValue(CollectionSchema.description.getSolrFieldName());
                        size = (Integer) doc.getFieldValue(CollectionSchema.size_i.getSolrFieldName());
                        date = (Date) doc.getFieldValue(CollectionSchema.last_modified.getSolrFieldName());
                        if (this.pattern != null && !this.pattern.matcher(url).matches()) continue;
                        if (this.format == 0) {
                            pw.println(url);
                        }
                        if (this.format == 1) {
                            if (title != null) pw.println("<a href=\"" + MultiProtocolURI.escape(url) + "\">" + CharacterCoding.unicode2xml((String) title.iterator().next(), true) + "</a>");
                        }
                        if (this.format == 2) {
                            pw.println("<item>");
                            if (title != null) pw.println("<title>" + CharacterCoding.unicode2xml((String) title.iterator().next(), true) + "</title>");
                            pw.println("<link>" + MultiProtocolURI.escape(url) + "</link>");
                            if (author != null && !author.isEmpty()) pw.println("<author>" + CharacterCoding.unicode2xml(author, true) + "</author>");
                            if (description != null && !description.isEmpty()) pw.println("<description>" + CharacterCoding.unicode2xml(description, true) + "</description>");
                            if (date != null) pw.println("<pubDate>" + DateUtil.formatDate(date) + "</pubDate>");
                            if (size != null) pw.println("<yacy:size>" + size.intValue() + "</yacy:size>");
                            pw.println("<guid isPermaLink=\"false\">" + hash + "</guid>");
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
    
    public Iterator<HostStat> statistics(int count, final ScoreMap<String> domainScore) {
        // prevent too heavy IO.
        if (this.statsDump != null && count <= this.statsDump.size()) return this.statsDump.iterator();

        // fetch urls from the database to determine the host in clear text
        final Iterator<String> j = domainScore.keys(false); // iterate urlhash-examples in reverse order (biggest first)
        String urlhash;
        count += 10; // make some more to prevent that we have to do this again after deletions too soon.
        if (count < 0 || domainScore.sizeSmaller(count)) count = domainScore.size();
        this.statsDump = new ArrayList<HostStat>();
        DigestURI url;
        while (j.hasNext()) {
            urlhash = j.next();
            if (urlhash == null) continue;
            url = this.getURL(ASCII.getBytes(urlhash));
            if (url == null || url.getHost() == null) continue;
            if (this.statsDump == null) return new ArrayList<HostStat>().iterator(); // some other operation has destroyed the object
            this.statsDump.add(new HostStat(url.getHost(), url.getPort(), urlhash.substring(6), domainScore.get(urlhash)));
            count--;
            if (count == 0) break;
        }
        // finally return an iterator for the result array
        return (this.statsDump == null) ? new ArrayList<HostStat>().iterator() : this.statsDump.iterator();
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
