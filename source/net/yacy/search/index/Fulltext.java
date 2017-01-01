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

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.connector.*;
import net.yacy.cora.federate.solr.instance.EmbeddedInstance;
import net.yacy.cora.federate.solr.instance.InstanceMirror;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.federate.solr.instance.ShardInstance;
import net.yacy.cora.federate.solr.responsewriter.EnhancedXMLResponseWriter;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.sorting.WeakPriorityBlockingQueue;
import net.yacy.cora.storage.ZIPReader;
import net.yacy.cora.storage.ZIPWriter;
import net.yacy.cora.util.CRIgnoreWriter;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.data.meta.URIMetadataNode;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphConfiguration;
import net.yacy.search.schema.WebgraphSchema;
import org.apache.lucene.util.Version;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.SolrInfoMBean;

import java.io.*;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.apache.solr.util.DateFormatUtil;

public final class Fulltext {

    private static final String SOLR_PATH = "solr_6_3"; // the number should be identical to the number in the property luceneMatchVersion in solrconfig.xml
    private static final String SOLR_OLD_PATH[] = new String[]{"solr_36", "solr_40", "solr_44", "solr_45", "solr_46", "solr_47", "solr_4_9", "solr_4_10", "solr_5_2"};
    
    // class objects
    private final File                    segmentPath;
    private final File                    archivePath;
    private       Export                  exportthread; // will have a export thread assigned if exporter is running
    private       InstanceMirror          solrInstances;
    private final CollectionConfiguration collectionConfiguration;
    private final WebgraphConfiguration   webgraphConfiguration;
    private       boolean                 writeWebgraph;

    protected Fulltext(final File segmentPath, final File archivePath,
            final CollectionConfiguration collectionConfiguration, final WebgraphConfiguration webgraphConfiguration) {
        this.segmentPath = segmentPath;
        this.archivePath = archivePath;
        this.exportthread = null; // will have a export thread assigned if exporter is running
        this.solrInstances = new InstanceMirror();
        this.collectionConfiguration = collectionConfiguration;
        this.webgraphConfiguration = webgraphConfiguration;
        this.writeWebgraph = false;
    }
    
    public void setUseWebgraph(boolean check) {
        this.writeWebgraph = check;
    }
    
    public boolean useWebgraph() {
        return this.writeWebgraph;
    }

    public CollectionConfiguration getDefaultConfiguration() {
        return this.collectionConfiguration;
    }

    public WebgraphConfiguration getWebgraphConfiguration() {
        return this.webgraphConfiguration;
    }

    public boolean connectedLocalSolr() {
        return this.solrInstances.isConnectedEmbedded();
    }

    public void connectLocalSolr() throws IOException {
        File solrLocation = new File(this.segmentPath, SOLR_PATH);
        // migrate old solr to new
        for (String oldVersion: SOLR_OLD_PATH) {
            File oldLocation = new File(this.segmentPath, oldVersion);
            if (oldLocation.exists()) oldLocation.renameTo(solrLocation);
        }
        
        EmbeddedInstance localCollectionInstance = new EmbeddedInstance(new File(new File(Switchboard.getSwitchboard().appPath, "defaults"), "solr"), solrLocation, CollectionSchema.CORE_NAME, new String[]{CollectionSchema.CORE_NAME, WebgraphSchema.CORE_NAME});
        Version luceneVersion = localCollectionInstance.getDefaultCore().getSolrConfig().getLuceneVersion("luceneMatchVersion");
        String lvn = luceneVersion.major + "_" + luceneVersion.minor;
        ConcurrentLog.info("Fulltext", "using lucene version " + lvn);
        assert SOLR_PATH.endsWith(lvn) : "luceneVersion = " + lvn + ", solrPath = " + SOLR_PATH + ", check defaults/solr/solrconfig.xml";
        ConcurrentLog.info("Fulltext", "connected solr in " + solrLocation.toString() + ", lucene version " + lvn);
        this.solrInstances.connectEmbedded(localCollectionInstance);
    }

    public void disconnectLocalSolr() {
        this.solrInstances.disconnectEmbedded();
    }

    public boolean connectedRemoteSolr() {
        return this.solrInstances.isConnectedRemote();
    }

    public void connectRemoteSolr(final ArrayList<RemoteInstance> instances, final ShardSelection.Method shardMethod, final boolean writeEnabled) {
        this.solrInstances.connectRemote(new ShardInstance(instances, shardMethod, writeEnabled));
    }

    public void disconnectRemoteSolr() {
        this.solrInstances.disconnectRemote();
    }

    public EmbeddedSolrConnector getDefaultEmbeddedConnector() {
        return this.solrInstances.getDefaultEmbeddedConnector();
    }

    public EmbeddedSolrConnector getEmbeddedConnector(String corename) {
        return this.solrInstances.getEmbeddedConnector(corename);
    }

    public SolrConnector getConnectorForRead(String corename) {
        if (this.solrInstances.isConnectedRemote()) return this.solrInstances.getRemoteConnector(corename);
        if (this.solrInstances.isConnectedEmbedded()) return this.solrInstances.getEmbeddedConnector(corename);
        return null;
    }

    public RemoteSolrConnector getDefaultRemoteSolrConnector() {
        try {
            return this.solrInstances.getDefaultRemoteConnector(true);
        } catch (IOException e) {
            return null;
        }
    }
    
    public EmbeddedInstance getEmbeddedInstance() {
        synchronized (this.solrInstances) {
            if (this.solrInstances.isConnectedEmbedded()) return this.solrInstances.getEmbedded();
            return null;
        }
    }
    
    public SolrConnector getDefaultConnector() {
        synchronized (this.solrInstances) {
            return this.solrInstances.getDefaultMirrorConnector();
        }
    }
    
    public SolrConnector getWebgraphConnector() {
        if (!this.writeWebgraph) return null;
        synchronized (this.solrInstances) {
            return this.solrInstances.getGenericMirrorConnector(WebgraphSchema.CORE_NAME);
        }
    }
    
    public Map<String, SolrInfoMBean> getSolrInfoBeans() {
        EmbeddedSolrConnector esc = this.solrInstances.getDefaultEmbeddedConnector();
        if (esc == null) return new HashMap<String, SolrInfoMBean>();
        return esc.getSolrInfoBeans();
    }
    
    public int bufferSize() {
        return this.solrInstances.bufferSize();
    }
    
    public void clearCaches() {
        this.solrInstances.clearCaches();
    }

    public void clearLocalSolr() throws IOException {
        if (this.exportthread != null) this.exportthread.interrupt();
        synchronized (this.solrInstances) {
            EmbeddedInstance instance = this.solrInstances.getEmbedded();
            if (instance != null) {
                for (String name: instance.getCoreNames()) {
                    this.solrInstances.getEmbeddedConnector(name).clear();
                }
                this.commit(false);
            }
            this.solrInstances.clearCaches();
        }
    }

    public void clearRemoteSolr() throws IOException {
        synchronized (this.solrInstances) {
            ShardInstance instance = this.solrInstances.getRemote();
            if (instance != null) {
                for (String name: instance.getCoreNames()) {
                    this.solrInstances.getRemoteConnector(name).clear();
                }
            }
            this.solrInstances.clearCaches();
        }
    }

    /**
     * get the size of the default index
     * @return
     */
    private long collectionSizeLastAccess = 0;
    private long collectionSizeLastValue = 0;
    public long collectionSize() {
        long t = System.currentTimeMillis();
        if (t - this.collectionSizeLastAccess < 1000) return this.collectionSizeLastValue;
        SolrConnector sc = getDefaultConnector();
        if (sc == null) return 0;
        long size = sc.getSize();
        this.collectionSizeLastAccess = t;
        this.collectionSizeLastValue = size;
        return size;
    }
    
    /**
     * get the size of the webgraph index
     * @return
     */
    public long webgraphSize() {
        return this.writeWebgraph ? this.getWebgraphConnector().getSize() : 0;
    }

    public void close() {
        try {
            this.solrInstances.close();
        } catch (Throwable e) {
        	ConcurrentLog.logException(e);
        }
    }
    
    private long lastCommit = 0;
    public void commit(boolean softCommit) {
        long t = System.currentTimeMillis();
        if (lastCommit + 10000 > t) return;
        lastCommit = t;
        getDefaultConnector().commit(softCommit);
        if (this.writeWebgraph) getWebgraphConnector().commit(softCommit);
    }

    /**
     * Loads the meta data stored in the embedded solr index for the url referenced
     * by the WordReference.
     * The WordReference and the YaCy score (element.weight()) become part (and
     * are accessible) of the returned document.
     * If the no document with url.hash = solrdocument.id is found in the embedded
     * Solr index null is return.
     * 
     * @param element rwi wordreference
     * @return URIMetadataNode (solrdocument) with all fields stored in embedded solr index
     */
    public URIMetadataNode getMetadata(final WeakPriorityBlockingQueue.Element<WordReferenceVars> element) {
        if (element == null) return null;
        WordReferenceVars wre = element.getElement();        
        if (wre == null) return null; // all time was already wasted in takeRWI to get another element
        long score = element.getWeight();
        URIMetadataNode node = getMetadata(wre.urlhash(), wre, score);
        return node;
    }

    public URIMetadataNode getMetadata(final byte[] urlHash) {
        if (urlHash == null) return null;
        return getMetadata(urlHash, null, 0L);
    }
    
    private URIMetadataNode getMetadata(final byte[] urlHash, final WordReferenceVars wre, final long score) {
        String u = ASCII.String(urlHash);
        
        // get the metadata from Solr
        try {
            SolrDocument doc = this.getDefaultConnector().getDocumentById(u);
            if (doc != null) {
            	return new URIMetadataNode(doc, wre, score);
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        return null;
    }

    public void putDocument(final SolrInputDocument doc) throws IOException {
        SolrConnector connector = this.getDefaultConnector();
        if (connector == null) return;
        String id = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
        String url = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
        assert url != null && url.length() < 30000;
        ConcurrentLog.info("Fulltext", "indexing: " + id + " " + url);
        try {
            connector.add(doc);
        } catch (final SolrException e) {
            throw new IOException(e.getMessage(), e);
        }
        if (MemoryControl.shortStatus()) clearCaches();
    }

    public void putEdges(final Collection<SolrInputDocument> edges) throws IOException {
        if (!this.useWebgraph()) return;
        if (edges == null || edges.size() == 0) return;
        try {
            this.getWebgraphConnector().add(edges);
        } catch (final SolrException e) {
            throw new IOException(e.getMessage(), e);
        }
        if (MemoryControl.shortStatus()) clearCaches();
    }

    /**
     * deprecated method to store document metadata, use Solr documents wherever possible
     */
    public void putMetadata(final URIMetadataNode entry) throws IOException {
        byte[] idb = entry.hash();
        String id = ASCII.String(idb);
        try {
            // because node entries are richer than metadata entries we must check if they exist to prevent that they are overwritten
            long date = this.getLoadTime(id);
            if (date == -1) {
                // document does not exist
                putDocument(getDefaultConfiguration().metadata2solr(entry));
            } else {
                // check if document contains rich data
                if (date < entry.loaddate().getTime()) {
                    SolrDocument doc = this.getDefaultConnector().getDocumentById(id, CollectionSchema.collection_sxt.getSolrFieldName());
                    if (doc == null || !doc.containsKey(CollectionSchema.collection_sxt.getSolrFieldName())) {
                        putDocument(getDefaultConfiguration().metadata2solr(entry));
                    } else {
                        Collection<Object> collections = doc.getFieldValues(CollectionSchema.collection_sxt.getSolrFieldName());
                        // collection dht is used to identify metadata from full crawled documents (if "dht" exists don't overwrite rich crawldata with metadata
                        if (!collections.contains("dht")) return;
                        
                        // passed all checks, overwrite document
                        putDocument(getDefaultConfiguration().metadata2solr(entry));
                    }
                }
            }
        } catch (final SolrException e) {
            throw new IOException(e.getMessage(), e);
        }
        if (MemoryControl.shortStatus()) clearCaches();
    }

    /**
     * using a fragment of the url hash (6 bytes: bytes 6 to 11) it is possible to address all urls from a specific domain
     * here such a fragment can be used to delete all these domains at once
     * @param hosthash the hash of the host to be deleted
     * @param freshdate either NULL or a date in the past which is the limit for deletion. Only documents older than this date are deleted
     * @throws IOException
     */
    public void deleteStaleDomainHashes(final Set<String> hosthashes, Date freshdate) {
        // delete in solr
        Date now = new Date();
        deleteDomainWithConstraint(this.getDefaultConnector(), CollectionSchema.host_id_s.getSolrFieldName(), hosthashes,
                (freshdate == null || freshdate.after(now)) ? null :
                (CollectionSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]"));
        if (this.writeWebgraph) deleteDomainWithConstraint(this.getWebgraphConnector(), WebgraphSchema.source_host_id_s.getSolrFieldName(), hosthashes,
                (freshdate == null || freshdate.after(now)) ? null :
                (WebgraphSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]"));
    }

    public void deleteStaleDomainNames(final Set<String> hostnames, Date freshdate) {

        Date now = new Date();
        deleteDomainWithConstraint(this.getDefaultConnector(), CollectionSchema.host_s.getSolrFieldName(), hostnames,
                (freshdate == null || freshdate.after(now)) ? null :
                (CollectionSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]"));
        if (this.writeWebgraph) deleteDomainWithConstraint(this.getWebgraphConnector(), WebgraphSchema.source_host_s.getSolrFieldName(), hostnames,
                (freshdate == null || freshdate.after(now)) ? null :
                (WebgraphSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]"));
    }
    
    /**
     * delete all documents within a domain that are registered as error document
     * @param hosthashes
     */
    public void deleteDomainErrors(final Set<String> hosthashes) {
        deleteDomainWithConstraint(this.getDefaultConnector(), CollectionSchema.host_id_s.getSolrFieldName(), hosthashes, CollectionSchema.failreason_s.getSolrFieldName() + AbstractSolrConnector.CATCHALL_DTERM);
    }
    
    private static void deleteDomainWithConstraint(SolrConnector connector, String fieldname, final Set<String> hosthashes, String constraintQuery) {
        if (hosthashes == null || hosthashes.size() == 0) return;
        int subsetscount = 1 + (hosthashes.size() / 255); // if the list is too large, we get a "too many boolean clauses" exception
        int c = 0;
        @SuppressWarnings("unchecked")
        List<String>[] subsets = (List<String>[]) Array.newInstance(ArrayList.class, subsetscount);
        for (int i = 0; i < subsetscount; i++) subsets[i] = new ArrayList<String>();
        for (String hosthash: hosthashes) subsets[c++ % subsetscount].add(hosthash);
        for (List<String> subset: subsets) {
            try {
                StringBuilder query = new StringBuilder();
                for (String hosthash: subset) {
                    if (query.length() > 0) query.append(" OR ");
                    //query.append(CollectionSchema.host_id_s.getSolrFieldName()).append(":\"").append(hosthash).append(":\"");
                    query.append("({!cache=false raw f=").append(fieldname).append('}').append(hosthash).append(")");
                }
                if (constraintQuery == null) connector.deleteByQuery(query.toString()); else connector.deleteByQuery("(" + query.toString() + ") AND " + constraintQuery);
            } catch (final IOException e) {
            }
        }
    }

    public void deleteOldDocuments(final long deltaToNow, final boolean loaddate) {
        Date deleteageDate = new Date(System.currentTimeMillis() - deltaToNow);
        final String collection1Query = (loaddate ? CollectionSchema.load_date_dt : CollectionSchema.last_modified).getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(deleteageDate) + "]";
        final String webgraphQuery = (loaddate ? WebgraphSchema.load_date_dt : WebgraphSchema.last_modified).getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(deleteageDate) + "]";
        try {
            this.getDefaultConnector().deleteByQuery(collection1Query);
            if (this.getWebgraphConnector() != null) this.getWebgraphConnector().deleteByQuery(webgraphQuery);
        } catch (final IOException e) {
        }
    }
    
    
    /**
     * remove a full subpath from the index
     * @param subpath the left path of the url; at least until the end of the host
     * @param freshdate either NULL or a date in the past which is the limit for deletion. Only documents older than this date are deleted
     * @param concurrently if true, then the method returnes immediately and runs concurrently
     */
    public int remove(final String basepath, Date freshdate) {
        DigestURL uri;
        try {uri = new DigestURL(basepath);} catch (final MalformedURLException e) {return 0;}
        final String host = uri.getHost();
        final String collectionQuery = CollectionSchema.host_s.getSolrFieldName() + ":\"" + host + "\"" +
                ((freshdate != null && freshdate.before(new Date())) ? (" AND " + CollectionSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]") : "");
        final AtomicInteger count = new AtomicInteger(0);
        final BlockingQueue<SolrDocument> docs = Fulltext.this.getDefaultConnector().concurrentDocumentsByQuery(collectionQuery, null, 0, 1000000, Long.MAX_VALUE, 100, 1, false, CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName());
        try {
            Set<String> deleteIDs = new HashSet<String>();
            SolrDocument doc;
            while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                String u = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
                if (u.startsWith(basepath)) {
                    deleteIDs.add((String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                    count.incrementAndGet();
                }
            }
            remove(deleteIDs);
            if (count.get() > 0) Fulltext.this.commit(true);
        } catch (final InterruptedException e) {}
        return count.get();
    }
    
    /**
     * remove a list of id's from the index
     * @param deleteIDs a list of urlhashes; each denoting a document
     * @param concurrently if true, then the method returnes immediately and runs concurrently
     */
    public void remove(final Collection<String> deleteIDs) {
        if (deleteIDs == null || deleteIDs.size() == 0) return;
        try {
            this.getDefaultConnector().deleteByIds(deleteIDs);
            if (this.writeWebgraph) this.getWebgraphConnector().deleteByIds(deleteIDs);
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
        }
    }
    
    public boolean remove(final byte[] urlHash) {
        if (urlHash == null) return false;
        try {
            String id = ASCII.String(urlHash);
            this.getDefaultConnector().deleteById(id);
            if (this.writeWebgraph) this.getWebgraphConnector().deleteById(id);
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
        }
        return false;
    }

    public DigestURL getURL(final String urlHash) throws IOException {
        if (urlHash == null || this.getDefaultConnector() == null) return null;
        
        SolrConnector.LoadTimeURL md = this.getDefaultConnector().getLoadTimeURL(urlHash);
        if (md == null) return null;
        return new DigestURL(md.url, ASCII.getBytes(urlHash));
    }
    
    /**
     * get the load time of a resource.
     * @param urlHash
     * @return the time in milliseconds since epoch for the load time or -1 if the document does not exist
     */
    public long getLoadTime(final String urlHash) throws IOException {
        if (urlHash == null) return -1l;
        SolrConnector.LoadTimeURL md = this.getDefaultConnector().getLoadTimeURL(urlHash);
        if (md == null) return -1l;
        return md.date;
    }
    
    public List<File> dumpFiles() {
        EmbeddedInstance esc = this.solrInstances.getEmbedded();
        ArrayList<File> zips = new ArrayList<File>();
        if (esc == null) {
            ConcurrentLog.warn("Fulltext", "HOT DUMP selected solr0 == NULL, no dump list!");
            return zips;
        }
        if (esc.getContainerPath() == null) {
            ConcurrentLog.warn("Fulltext", "HOT DUMP selected solr0.getStoragePath() == NULL, no dump list!");
            return zips;
        }
        if (this.archivePath == null) {
            ConcurrentLog.warn("Fulltext", "HOT DUMP selected esc.getStoragePath().getParentFile() == NULL, no dump list!");
            return zips;
        }
        ConcurrentLog.info("Fulltext", "HOT DUMP dump path = " + this.archivePath.toString());
        for (String p: this.archivePath.list()) {
            if (p.endsWith("zip")) zips.add(new File(this.archivePath, p));
        }
        return zips;
    }
    
    /**
     * create a dump file from the current solr directory
     * @return
     */
    public File dumpSolr() {
        EmbeddedInstance esc = this.solrInstances.getEmbedded();
        File storagePath = esc.getContainerPath();
        File zipOut = new File(this.archivePath, storagePath.getName() + "_" + GenericFormatter.SHORT_DAY_FORMATTER.format() + ".zip");
        synchronized (this.solrInstances) {
            this.disconnectLocalSolr();
            try {
                ZIPWriter.zip(storagePath, zipOut);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } finally {
                try {
                    this.connectLocalSolr();
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
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
        EmbeddedInstance esc = this.solrInstances.getEmbedded();
        File storagePath = esc.getContainerPath();
        synchronized (this.solrInstances) {
            // this.disconnectLocalSolr(); // moved to (InstanceMirror) sorlInstances.close()
            this.solrInstances.close();
            try {
                ZIPReader.unzip(solrDumpZipFile, storagePath);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } finally {
                this.solrInstances = new InstanceMirror();
                try {
                    this.connectLocalSolr();
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                }
            }
        }
    }

    /**
     * optimize solr (experimental to check resource management)
     * @param size
     */
    public void optimize(final int size) {
        if (size < 1) return;
        getDefaultConnector().optimize(size);
        if (this.writeWebgraph) getWebgraphConnector().optimize(size);
    }
    
    /**
     * reboot solr (experimental to check resource management)
     */
    public void rebootSolr() {
        synchronized (this.solrInstances) {
            this.disconnectLocalSolr();
            // this.solrInstances.close(); // moved to (InstanceMirror) sorlInstances.close()
            this.solrInstances = new InstanceMirror();
            try {
                this.connectLocalSolr();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }
    }

    public static enum ExportFormat {
        text("txt"), html("html"), rss("rss"), solr("xml");
        private final String ext;
        private ExportFormat(String ext) {this.ext = ext;}
        public String getExt() {return this.ext;}
    }
    
    public final static String yacy_dump_prefix = "yacy_dump_";
    public Export export(Fulltext.ExportFormat format, String filter, String query, final int maxseconds, File path, boolean dom, boolean text) throws IOException {
        
        // modify query according to maxseconds
        long now = System.currentTimeMillis();
        if (maxseconds > 0) {
            long from = now - maxseconds * 1000L;
            String nowstr = DateFormatUtil.formatExternal(new Date(now));
            String fromstr = DateFormatUtil.formatExternal(new Date(from));
            String dateq = CollectionSchema.load_date_dt.getSolrFieldName() + ":[" + fromstr + " TO " + nowstr + "]";
            query = query == null || AbstractSolrConnector.CATCHALL_QUERY.equals(query) ? dateq : query + " AND " + dateq; 
        } else {
            query = query == null? AbstractSolrConnector.CATCHALL_QUERY : query;
        }
        
        // check the oldest and latest entry in the index for this query
        SolrDocumentList firstdoclist, lastdoclist;
        firstdoclist = this.getDefaultConnector().getDocumentListByQuery(
               query, CollectionSchema.load_date_dt.getSolrFieldName() + " asc", 0, 1,CollectionSchema.load_date_dt.getSolrFieldName());
        lastdoclist = this.getDefaultConnector().getDocumentListByQuery(
               query, CollectionSchema.load_date_dt.getSolrFieldName() + " desc", 0, 1,CollectionSchema.load_date_dt.getSolrFieldName());

        if (firstdoclist.size() == 0 || lastdoclist.size() == 0) {
            assert firstdoclist.size() == 0 && lastdoclist.size() == 0;
            throw new IOException("number of exported documents == 0");
        }
        assert firstdoclist.size() == 1 && lastdoclist.size() == 1;
        long doccount = firstdoclist.getNumFound();
        
        // create the export name
        SolrDocument firstdoc = firstdoclist.get(0);
        SolrDocument lastdoc = lastdoclist.get(0);
        Object firstdateobject = firstdoc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
        Object lastdateobject = lastdoc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
        Date firstdate = (Date) firstdateobject;
        Date lastdate = (Date) lastdateobject;
        String s = new File(path, yacy_dump_prefix +
                "f" + GenericFormatter.FORMAT_SHORT_MINUTE.format(firstdate) + "_" +
                "l" + GenericFormatter.FORMAT_SHORT_MINUTE.format(lastdate) + "_" +
                "n" + GenericFormatter.FORMAT_SHORT_MINUTE.format(new Date(now)) + "_" +
                "c" + String.format("%1$012d", doccount)).getAbsolutePath() + "_tc"; // the name ends with the transaction token ('c' = 'created')
        
        // create export file name
        if (s.indexOf('.',0) < 0) s += "." + format.getExt();
        final File f = new File(s);
        f.getParentFile().mkdirs();
        
        return export(f, filter, query, format, dom, text);
    }
    
    // export methods
    public Export export(final File f, final String filter, final String query, final ExportFormat format, final boolean dom, final boolean text) {
        if ((this.exportthread != null) && (this.exportthread.isAlive())) {
            ConcurrentLog.warn("LURL-EXPORT", "cannot start another export thread, already one running");
            return this.exportthread;
        }
        this.exportthread = new Export(f, filter, query, format, dom, text);
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
        private String failure, query;
        private final ExportFormat format;
        private final boolean dom, text;

        private Export(final File f, final String filter, final String query, final ExportFormat format, final boolean dom, final boolean text) {
        	super("Fulltext.Export");
            // format: 0=text, 1=html, 2=rss/xml
            this.f = f;
            this.pattern = filter == null ? null : Pattern.compile(filter);
            this.query = query == null? AbstractSolrConnector.CATCHALL_QUERY : query;
            this.count = 0;
            this.failure = null;
            this.format = format;
            this.dom = dom;
            this.text = text;
            //if ((dom) && (format == 2)) dom = false;
        }

        @Override
        public void run() {
            try {
                final File parentf = this.f.getParentFile();
                if (parentf != null) parentf.mkdirs();
                OutputStream os = new FileOutputStream(this.format == ExportFormat.solr ? new File(this.f.getAbsolutePath() + ".gz") : this.f);
                if (this.format == ExportFormat.solr) os = new GZIPOutputStream(os, 65536){{def.setLevel(Deflater.BEST_COMPRESSION);}};
                final PrintWriter pw = new PrintWriter(new BufferedOutputStream(os));
                if (this.format == ExportFormat.html) {
                    pw.println("<html><head></head><body>");
                }
                if (this.format == ExportFormat.rss) {
                    pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                    pw.println("<?xml-stylesheet type='text/xsl' href='/yacysearch.xsl' version='1.0'?>");
                    pw.println("<rss version=\"2.0\" xmlns:yacy=\"http://www.yacy.net/\" xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\" xmlns:atom=\"http://www.w3.org/2005/Atom\">");
                    pw.println("<channel>");
                    pw.println("<title>YaCy Peer-to-Peer - Web-Search URL Export</title>");
                    pw.println("<description></description>");
                    pw.println("<link>http://yacy.net</link>");
                }
                if (this.format == ExportFormat.solr) {
                    pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                    pw.println("<response>");
                    pw.println("<result>");
                }
                if (this.dom) {
                    Map<String, ReversibleScoreMap<String>> scores = Fulltext.this.getDefaultConnector().getFacets(this.query + " AND " + CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", 100000000, CollectionSchema.host_s.getSolrFieldName());
                    ReversibleScoreMap<String> stats = scores.get(CollectionSchema.host_s.getSolrFieldName());
                    for (final String host: stats) {
                        if (this.pattern != null && !this.pattern.matcher(host).matches()) continue;
                        if (this.format == ExportFormat.text) pw.println(host);
                        if (this.format == ExportFormat.html) pw.println("<a href=\"http://" + host + "\">" + host + "</a><br>");
                        this.count++;
                    }
                } else {
                    if (this.format == ExportFormat.solr || (this.text && this.format == ExportFormat.text)) {
                        BlockingQueue<SolrDocument> docs = Fulltext.this.getDefaultConnector().concurrentDocumentsByQuery(this.query + " AND " + CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", null, 0, 100000000, Long.MAX_VALUE, 100, 1, true);
                        SolrDocument doc;
                        while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                            String url = getStringFrom(doc.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
                            if (this.pattern != null && !this.pattern.matcher(url).matches()) continue;
                            CRIgnoreWriter sw = new CRIgnoreWriter();
                            if (this.text) sw.write((String) doc.getFieldValue(CollectionSchema.text_t.getSolrFieldName())); else EnhancedXMLResponseWriter.writeDoc(sw, doc);
                            sw.close();
                            String d = sw.toString();
                            pw.println(d);
                            this.count++;
                        }
                    } else {
                        BlockingQueue<SolrDocument> docs = Fulltext.this.getDefaultConnector().concurrentDocumentsByQuery(this.query + " AND " + CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", null, 0, 100000000, Long.MAX_VALUE, 100, 1, true, 
                                CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName(), CollectionSchema.title.getSolrFieldName(),
                                CollectionSchema.author.getSolrFieldName(), CollectionSchema.description_txt.getSolrFieldName(), CollectionSchema.size_i.getSolrFieldName(), CollectionSchema.last_modified.getSolrFieldName());
                        SolrDocument doc;
                        String url, hash, title, author, description;
                        Integer size;
                        Date date;
                        while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                            hash = getStringFrom(doc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                            url = getStringFrom(doc.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
                            title = getStringFrom(doc.getFieldValue(CollectionSchema.title.getSolrFieldName()));
                            author = getStringFrom(doc.getFieldValue(CollectionSchema.author.getSolrFieldName()));
                            description = getStringFrom(doc.getFieldValue(CollectionSchema.description_txt.getSolrFieldName()));
                            size = (Integer) doc.getFieldValue(CollectionSchema.size_i.getSolrFieldName());
                            date = (Date) doc.getFieldValue(CollectionSchema.last_modified.getSolrFieldName());
                            if (this.pattern != null && !this.pattern.matcher(url).matches()) continue;
                            if (this.format == ExportFormat.text) {
                                pw.println(url);
                            }
                            if (this.format == ExportFormat.html) {
                                if (title != null) pw.println("<a href=\"" + MultiProtocolURL.escape(url) + "\">" + CharacterCoding.unicode2xml(title, true) + "</a>");
                            }
                            if (this.format == ExportFormat.rss) {
                                pw.println("<item>");
                                if (title != null) pw.println("<title>" + CharacterCoding.unicode2xml(title, true) + "</title>");
                                pw.println("<link>" + MultiProtocolURL.escape(url) + "</link>");
                                if (author != null && !author.isEmpty()) pw.println("<author>" + CharacterCoding.unicode2xml(author, true) + "</author>");
                                if (description != null && !description.isEmpty()) pw.println("<description>" + CharacterCoding.unicode2xml(description, true) + "</description>");
                                if (date != null) pw.println("<pubDate>" + HeaderFramework.formatRFC1123(date) + "</pubDate>");
                                if (size != null) pw.println("<yacy:size>" + size.intValue() + "</yacy:size>");
                                pw.println("<guid isPermaLink=\"false\">" + hash + "</guid>");
                                pw.println("</item>");
                            }
                            this.count++;
                        }
                    }
                }
                if (this.format == ExportFormat.html) {
                    pw.println("</body></html>");
                }
                if (this.format == ExportFormat.rss) {
                    pw.println("</channel>");
                    pw.println("</rss>");
                }
                if (this.format == ExportFormat.solr) {
                    pw.println("</result>");
                    pw.println("</response>");
                }
                pw.close();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                this.failure = e.getMessage();
            } catch (final Exception e) {
                ConcurrentLog.logException(e);
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
        
        @SuppressWarnings("unchecked")
		private String getStringFrom(final Object o) {
        	if (o == null) return "";
        	if (o instanceof ArrayList) return ((ArrayList<String>) o).get(0);
        	return (String) o;
        }

    }

}
