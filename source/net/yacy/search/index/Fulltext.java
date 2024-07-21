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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.SolrConfig;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.connector.AbstractSolrConnector;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.connector.RemoteSolrConnector;
import net.yacy.cora.federate.solr.connector.ShardSelection;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.instance.EmbeddedInstance;
import net.yacy.cora.federate.solr.instance.InstanceMirror;
import net.yacy.cora.federate.solr.instance.RemoteInstance;
import net.yacy.cora.federate.solr.instance.ShardInstance;
import net.yacy.cora.federate.solr.responsewriter.EnhancedXMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.FlatJSONResponseWriter;
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
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphConfiguration;
import net.yacy.search.schema.WebgraphSchema;

public final class Fulltext {

    private static final String SOLR_PATH = "solr_9_0"; // the number should be identical to the number in the property luceneMatchVersion in solrconfig.xml
    private static final String SOLR_OLD_PATH[] = new String[]{
            "solr_36", "solr_40", "solr_44", "solr_45", "solr_46", "solr_47",
            "solr_4_9",  // yacy_v1.80 (solr 4.9.0)
            "solr_4_10", // yacy_v1.81 (solr 4.10.3), yacy_v1.82 (solr 4.10.3)
            "solr_5_2",  // yacy_v1.90 (solr 5.5.1, sic!)
            "solr_5_5",  // yacy_v1.91 (solr 5.5.2), yacy_v1.92 (solr 5.5.2)
            "solr_6_6",  // yacy_v1.922 (solr 6.6, init fail), yacy_v1.924_20210209_10069 (solr 7.7.3), yacy_v1.926 (solr 8.9.0)
            "solr_8_8_1", //
            "solr_8_11_2"
	};

    // class objects
    private final File                    segmentPath;
    private final File                    archivePath;
    private       Export                  exportthread; // will have a export thread assigned if exporter is running
    private       InstanceMirror          solrInstances;

    /** Synchronization lock for solrInstances property */
    private final ReentrantLock solrInstancesLock;

    private final CollectionConfiguration collectionConfiguration;
    private final WebgraphConfiguration   webgraphConfiguration;
    private       boolean                 writeWebgraph;

    protected Fulltext(final File segmentPath, final File archivePath,
            final CollectionConfiguration collectionConfiguration, final WebgraphConfiguration webgraphConfiguration) {
        this.segmentPath = segmentPath;
        this.archivePath = archivePath;
        this.exportthread = null; // will have a export thread assigned if exporter is running
        this.solrInstances = new InstanceMirror();
        this.solrInstancesLock = new ReentrantLock();
        this.collectionConfiguration = collectionConfiguration;
        this.webgraphConfiguration = webgraphConfiguration;
        this.writeWebgraph = false;
    }

    public void setUseWebgraph(final boolean check) {
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
        final File solrLocation = new File(this.segmentPath, SOLR_PATH);

        // migrate old solr to new
        for (final String oldVersion: SOLR_OLD_PATH) {
            final File oldLocation = new File(this.segmentPath, oldVersion);
            if (oldLocation.exists()) {
                if (!oldLocation.renameTo(solrLocation)) {
                    ConcurrentLog.severe("Fulltext", "Failed renaming old Solr location ("
                            + oldLocation.getAbsolutePath() + ") to new location : " + solrLocation.getAbsolutePath());
                }
            }
        }

        final EmbeddedInstance localCollectionInstance = new EmbeddedInstance(new File(new File(Switchboard.getSwitchboard().appPath, "defaults"), "solr"), solrLocation, CollectionSchema.CORE_NAME, new String[]{CollectionSchema.CORE_NAME, WebgraphSchema.CORE_NAME});
        final SolrConfig config = localCollectionInstance.getDefaultCore().getSolrConfig();
        final String lvn = config.luceneMatchVersion.major + "_" + config.luceneMatchVersion.minor + "_" + config.luceneMatchVersion.bugfix;
        //assert SOLR_PATH.endsWith(lvn) : "luceneVersion = " + lvn + ", solrPath = " + SOLR_PATH + ", check defaults/solr/solrconfig.xml";

        ConcurrentLog.info("Fulltext", "using lucene version " + lvn);
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

    public EmbeddedSolrConnector getEmbeddedConnector(final String corename) {
        return this.solrInstances.getEmbeddedConnector(corename);
    }

    public SolrConnector getConnectorForRead(final String corename) {
        if (this.solrInstances.isConnectedRemote()) return this.solrInstances.getRemoteConnector(corename);
        if (this.solrInstances.isConnectedEmbedded()) return this.solrInstances.getEmbeddedConnector(corename);
        return null;
    }

    public RemoteSolrConnector getDefaultRemoteSolrConnector() {
        try {
            boolean useBinaryResponseWriter = SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED_DEFAULT;
            if (Switchboard.getSwitchboard() != null) {
                useBinaryResponseWriter = Switchboard.getSwitchboard().getConfigBool(
                        SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED,
                        SwitchboardConstants.REMOTE_SOLR_BINARY_RESPONSE_ENABLED_DEFAULT);
            }
            return this.solrInstances.getDefaultRemoteConnector(useBinaryResponseWriter);
        } catch (final IOException e) {
            return null;
        }
    }

    public EmbeddedInstance getEmbeddedInstance() {
        this.solrInstancesLock.lock();
        try {
            if (this.solrInstances.isConnectedEmbedded()) {
                return this.solrInstances.getEmbedded();
            }
            return null;
        } finally {
            this.solrInstancesLock.unlock();
        }
    }

    public SolrConnector getDefaultConnector() {
        this.solrInstancesLock.lock();
        try {
            return this.solrInstances.getDefaultMirrorConnector();
        } finally {
            this.solrInstancesLock.unlock();
        }
    }

    public SolrConnector getWebgraphConnector() {
        if (!this.writeWebgraph) {
            return null;
        }
        this.solrInstancesLock.lock();
        try {
            return this.solrInstances.getGenericMirrorConnector(WebgraphSchema.CORE_NAME);
        } finally {
            this.solrInstancesLock.unlock();
        }
    }

    public int bufferSize() {
        return this.solrInstances.bufferSize();
    }

    public void clearCaches() {
        this.solrInstances.clearCaches();
    }

    public void clearLocalSolr() throws IOException {
        if (this.exportthread != null) {
            this.exportthread.interrupt();
        }
        this.solrInstancesLock.lock();
        try {
            final EmbeddedInstance instance = this.solrInstances.getEmbedded();
            if (instance != null) {
                for (final String name: instance.getCoreNames()) {
                    this.solrInstances.getEmbeddedConnector(name).clear();
                }
                this.commit(false);
            }
            this.solrInstances.clearCaches();
        } finally {
            this.solrInstancesLock.unlock();
        }
    }

    public void clearRemoteSolr() throws IOException {
        this.solrInstancesLock.lock();
        try {
            final ShardInstance instance = this.solrInstances.getRemote();
            if (instance != null) {
                for (final String name: instance.getCoreNames()) {
                    this.solrInstances.getRemoteConnector(name).clear();
                }
            }
            this.solrInstances.clearCaches();
        } finally {
            this.solrInstancesLock.unlock();
        }
    }

    /**
     * get the size of the default index
     * @return
     */
    private long collectionSizeLastAccess = 0;
    private long collectionSizeLastValue = 0;
    public long collectionSize() {
        final long t = System.currentTimeMillis();
        if (t - this.collectionSizeLastAccess < 1000) return this.collectionSizeLastValue;
        final SolrConnector sc = getDefaultConnector();
        if (sc == null) return 0;
        final long size = sc.getSize();
        this.collectionSizeLastAccess = t;
        this.collectionSizeLastValue = size;
        return size;
    }

    /**
     * @return the size of the webgraph index
     */
    public long webgraphSize() {
        return this.writeWebgraph ? this.getWebgraphConnector().getSize() : 0;
    }

    public void close() {
        try {
            this.solrInstances.close();
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
        }
    }

    private long lastCommit = 0;
    public void commit(final boolean softCommit) {
        final long t = System.currentTimeMillis();
        if (this.lastCommit + 10000 > t) return;
        this.lastCommit = t;
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
        final WordReferenceVars wre = element.getElement();
        if (wre == null) return null; // all time was already wasted in takeRWI to get another element
        final long score = element.getWeight();
        final URIMetadataNode node = getMetadata(wre.urlhash(), wre, score);
        return node;
    }

    public URIMetadataNode getMetadata(final byte[] urlHash) {
        if (urlHash == null) return null;
        return getMetadata(urlHash, null, 0L);
    }

    private URIMetadataNode getMetadata(final byte[] urlHash, final WordReferenceVars wre, final long score) {
        final String u = ASCII.String(urlHash);

        // get the metadata from Solr
        try {
            final SolrDocument doc = this.getDefaultConnector().getDocumentById(u);
            if (doc != null) {
                return new URIMetadataNode(doc, wre, score);
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }

        return null;
    }

    public void putDocument(final SolrInputDocument doc) throws IOException {
        final SolrConnector connector = this.getDefaultConnector();
        if (connector == null || connector.isClosed()) return;
        final String id = (String) doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
        final String url = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
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
        final byte[] idb = entry.hash();
        final String id = ASCII.String(idb);
        try {
            // because node entries are richer than metadata entries we must check if they exist to prevent that they are overwritten
            final SolrDocument doc = this.getDefaultConnector().getDocumentById(id, CollectionSchema.collection_sxt.getSolrFieldName());
            if (doc == null || !doc.containsKey(CollectionSchema.collection_sxt.getSolrFieldName())) {
                // document does not exist
                putDocument(getDefaultConfiguration().metadata2solr(entry));
            } else {
                final Collection<Object> collections = doc.getFieldValues(CollectionSchema.collection_sxt.getSolrFieldName());
                // collection dht is used to identify metadata from full crawled documents (if "dht" exists don't overwrite rich crawldata with metadata
                if (!collections.contains("dht")) return;

                // passed all checks, overwrite document
                putDocument(getDefaultConfiguration().metadata2solr(entry));
            }
        } catch (final SolrException e) {
            throw new IOException(e.getMessage(), e);
        }
        if (MemoryControl.shortStatus()) clearCaches();
    }

    /**
     * using a fragment of the url hash (6 bytes: bytes 6 to 11) it is possible to address all urls from a specific domain
     * here such a fragment can be used to delete all these domains at once
     * @param hosthashes the hashes of the hosts to be deleted
     * @param freshdate either NULL or a date in the past which is the limit for deletion. Only documents older than this date are deleted
     * @throws IOException
     */
    public void deleteStaleDomainHashes(final Set<String> hosthashes, final Date freshdate) {
        // delete in solr
        final Date now = new Date();
        deleteDomainWithConstraint(this.getDefaultConnector(), CollectionSchema.host_id_s.getSolrFieldName(), hosthashes,
                (freshdate == null || freshdate.after(now)) ? null :
                    (CollectionSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]"));
        if (this.writeWebgraph) deleteDomainWithConstraint(this.getWebgraphConnector(), WebgraphSchema.source_host_id_s.getSolrFieldName(), hosthashes,
                (freshdate == null || freshdate.after(now)) ? null :
                    (WebgraphSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]"));
    }

    public void deleteStaleDomainNames(final Set<String> hostnames, final Date freshdate) {

        final Date now = new Date();
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

    private static void deleteDomainWithConstraint(final SolrConnector connector, final String fieldname, final Set<String> hosthashes, final String constraintQuery) {
        if (hosthashes == null || hosthashes.size() == 0) return;
        final int subsetscount = 1 + (hosthashes.size() / 255); // if the list is too large, we get a "too many boolean clauses" exception
        int c = 0;
        @SuppressWarnings("unchecked")
        final
        List<String>[] subsets = (List<String>[]) Array.newInstance(ArrayList.class, subsetscount);
        for (int i = 0; i < subsetscount; i++) subsets[i] = new ArrayList<>();
        for (final String hosthash: hosthashes) subsets[c++ % subsetscount].add(hosthash);
        for (final List<String> subset: subsets) {
            try {
                final StringBuilder query = new StringBuilder();
                for (final String hosthash: subset) {
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
        final Date deleteageDate = new Date(System.currentTimeMillis() - deltaToNow);
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
     * @param basepath the left path of the url; at least until the end of the host
     * @param freshdate either NULL or a date in the past which is the limit for deletion. Only documents older than this date are deleted
     */
    public int remove(final String basepath, final Date freshdate) {
        DigestURL uri;
        try {uri = new DigestURL(basepath);} catch (final MalformedURLException e) {return 0;}
        final String host = uri.getHost();
        final String collectionQuery = CollectionSchema.host_s.getSolrFieldName() + ":\"" + host + "\"" +
                ((freshdate != null && freshdate.before(new Date())) ? (" AND " + CollectionSchema.load_date_dt.getSolrFieldName() + ":[* TO " + ISO8601Formatter.FORMATTER.format(freshdate) + "]") : "");
        final AtomicInteger count = new AtomicInteger(0);
        final BlockingQueue<SolrDocument> docs = Fulltext.this.getDefaultConnector().concurrentDocumentsByQuery(collectionQuery, null, 0, 1000000, Long.MAX_VALUE, 100, 1, false, CollectionSchema.id.getSolrFieldName(), CollectionSchema.sku.getSolrFieldName());
        try {
            final Set<String> deleteIDs = new HashSet<>();
            SolrDocument doc;
            while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                final String u = (String) doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
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
     * remove a list of id's from the index (matching fulltext.id and webgraph.source_id_s
     * @param deleteIDs a list of urlhashes; each denoting a document
     */
    public void remove(final Collection<String> deleteIDs) {
        if (deleteIDs == null || deleteIDs.size() == 0) return;
        try {
            this.getDefaultConnector().deleteByIds(deleteIDs);
            if (this.writeWebgraph) { // Webgraph.id is combination of sourceHash+targetHash+hexCounter, to be successful use source_id_s and/or target_id_s
                for (final String id : deleteIDs) {
                    /* Add quotes around the url hash to prevent Solr logging a ParseException stack trace when the hash start with a '-' character */
                    this.getWebgraphConnector().deleteByQuery(WebgraphSchema.source_id_s.name() + ":\"" + id  + "\"");
                }
            }
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
        }
    }

    /**
     * Deletes document with id=urlHash from fulltext index and document with
     * source_id_s=urlHash from webgraph index
     * @param urlHash the document id
     * @return false
     */
    public boolean remove(final byte[] urlHash) {
        if (urlHash == null) return false;
        try {
            final String id = ASCII.String(urlHash);
            this.getDefaultConnector().deleteById(id);
            if (this.writeWebgraph) { // Webgraph.id is combination of sourceHash+targetHash+hexCounter, to be successful use source_id_s and/or target_id_s
                /* Add quotes around the url hash to prevent Solr logging a ParseException stack trace when the hash start with a '-' character */
                this.getWebgraphConnector().deleteByQuery(WebgraphSchema.source_id_s + ":\"" + id + "\"");
            }
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
        }
        return false;
    }

    public String getURL(final String urlHash) throws IOException {
        if (urlHash == null || this.getDefaultConnector() == null) return null;

        return this.getDefaultConnector().getURL(urlHash);
    }

    /**
     * check if a given document, identified by url hash as document id exists
     * @param id the url hash and document id
     * @return whether the documents exists
     */
    public boolean exists(final String id) {
        return this.getDefaultConnector().exists(id);
    }

    public List<File> dumpFiles() {
        final EmbeddedInstance esc = this.solrInstances.getEmbedded();
        final ArrayList<File> zips = new ArrayList<>();
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
        for (final String p: this.archivePath.list()) {
            if (p.endsWith("zip")) zips.add(new File(this.archivePath, p));
        }
        return zips;
    }

    /**
     * Create a dump file from the current embedded solr directory
     * @return file reference to the dump
     * @throws SolrException when no embedded Solr is available
     */
    public File dumpEmbeddedSolr() throws SolrException {
        final EmbeddedInstance esc = this.solrInstances.getEmbedded();
        if(esc == null) {
            throw new SolrException(ErrorCode.SERVICE_UNAVAILABLE, "No embedded Solr available.");
        }
        final File storagePath = esc.getContainerPath();
        final File zipOut = new File(this.archivePath, storagePath.getName() + "_" + GenericFormatter.SHORT_DAY_FORMATTER.format() + ".zip");
        this.solrInstancesLock.lock();
        try {
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
        } finally {
            this.solrInstancesLock.unlock();
        }
        return zipOut;
    }

    /**
     * Restore a solr dump to the current embedded solr directory
     * @param solrDumpZipFile the dump file to use
     * @throws SolrException when no embedded Solr is available
     */
    public void restoreEmbeddedSolr(final File solrDumpZipFile) {
        final EmbeddedInstance esc = this.solrInstances.getEmbedded();
        if(esc == null) {
            throw new SolrException(ErrorCode.SERVICE_UNAVAILABLE, "No embedded Solr available.");
        }
        final File storagePath = esc.getContainerPath();
        this.solrInstancesLock.lock();
        try {
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
        } finally {
            this.solrInstancesLock.unlock();
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
     * Reboot the local embedded Solr (experimental to check resource management).
     * Please check before that the local embedded Solr is enabled and no external remote Solr is attached.
     */
    public void rebootEmbeddedLocalSolr() {
        this.solrInstancesLock.lock();
        try {
            this.disconnectLocalSolr();
            // this.solrInstances.close(); // moved to (InstanceMirror) sorlInstances.close()
            this.solrInstances = new InstanceMirror();
            try {
                this.connectLocalSolr();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        } finally {
            this.solrInstancesLock.unlock();
        }
    }

    public static enum ExportFormat {
        text("txt"), html("html"), rss("rss"), solr("xml"), elasticsearch("flatjson");
        private final String ext;
        private ExportFormat(final String ext) {this.ext = ext;}
        public String getExt() {return this.ext;}
    }

    public final static String yacy_dump_prefix = "yacy_dump_";
    public Export export(
            final Fulltext.ExportFormat format, final String filter, String query,
            final int maxseconds, final File path, final boolean dom, final boolean text,
            final long maxChunkSize, final boolean minified) throws IOException {

        // modify query according to maxseconds
        final long now = System.currentTimeMillis();
        if (maxseconds > 0) {
            final long from = now - maxseconds * 1000L;
            final String nowstr = new Date(now).toInstant().toString();
            final String fromstr = new Date(from).toInstant().toString();
            final String dateq = CollectionSchema.load_date_dt.getSolrFieldName() + ":[" + fromstr + " TO " + nowstr + "]";
            query = query == null || AbstractSolrConnector.CATCHALL_QUERY.equals(query) ? dateq : query + " AND " + dateq;
        } else {
            query = query == null? AbstractSolrConnector.CATCHALL_QUERY : query;
        }

        // check the oldest and latest entry in the index for this query
        SolrDocumentList firstdoclist, lastdoclist;
        Object firstdateobject, lastdateobject;
        firstdoclist = this.getDefaultConnector().getDocumentListByQuery(
                query, CollectionSchema.load_date_dt.getSolrFieldName() + " asc", 0, 1,CollectionSchema.load_date_dt.getSolrFieldName());
        lastdoclist = this.getDefaultConnector().getDocumentListByQuery(
                query, CollectionSchema.load_date_dt.getSolrFieldName() + " desc", 0, 1,CollectionSchema.load_date_dt.getSolrFieldName());

        final long doccount;
        final Date firstdate, lastdate;
        if (firstdoclist.size() == 0 || lastdoclist.size() == 0) {
            /* Now check again the number of documents without sorting, for compatibility with old fields indexed without DocValues fields (prior to YaCy 1.90)
             * When the local Solr index contains such old documents, requests with sort query return nothing and trace in logs
             * "java.lang.IllegalStateException: unexpected docvalues type NONE for field..." */
            doccount = this.getDefaultConnector().getCountByQuery(query);
            if(doccount == 0) {
                /* Finally no document to export was found */
                throw new IOException("number of exported documents == 0");
            }
            /* we use default date values just to generate a proper dump file path */
            firstdate = new Date(0);
            lastdate = new Date(0);

        } else {
            doccount = firstdoclist.getNumFound();

            // create the export name
            final SolrDocument firstdoc = firstdoclist.get(0);
            final SolrDocument lastdoc = lastdoclist.get(0);
            firstdateobject = firstdoc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());
            lastdateobject = lastdoc.getFieldValue(CollectionSchema.load_date_dt.getSolrFieldName());

            /* When firstdate or lastdate is null, we use a default one just to generate a proper dump file path
             * This should not happen because load_date_dt field is mandatory in the main Solr schema,
             * but for some reason some documents might end up here with an empty load_date_dt field value */
            if(firstdateobject instanceof Date) {
                firstdate = (Date) firstdateobject;
            } else {
                ConcurrentLog.warn("Fulltext", "The required field " + CollectionSchema.load_date_dt.getSolrFieldName() + " is empty on document with id : "
                        + firstdoc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                firstdate = new Date(0);
            }
            if(lastdateobject instanceof Date) {
                lastdate = (Date) lastdateobject;
            } else {
                ConcurrentLog.warn("Fulltext", "The required field " + CollectionSchema.load_date_dt.getSolrFieldName() + " is empty on document with id : "
                        + lastdoc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                lastdate = new Date(0);
            }
        }

        final String filename = yacy_dump_prefix +
                "f" + GenericFormatter.SHORT_MINUTE_FORMATTER.format(firstdate) + "_" +
                "l" + GenericFormatter.SHORT_MINUTE_FORMATTER.format(lastdate) + "_" +
                "n" + GenericFormatter.SHORT_MINUTE_FORMATTER.format(new Date(now)) + "_" +
                "c" + String.format("%1$012d", doccount)+ "_tc"; // the name ends with the transaction token ('c' = 'created')

        return export(path, filename, format.getExt(), filter, query, format, dom, text, maxChunkSize, minified);
    }

    // export methods
    public Export export(
            final File path, final String filename,
            final String fileext, final String filter, final String query,
            final ExportFormat format, final boolean dom, final boolean text,
            final long maxChunkSize, final boolean minified) {
        if ((this.exportthread != null) && (this.exportthread.isAlive())) {
            ConcurrentLog.warn("LURL-EXPORT", "cannot start another export thread, already one running");
            return this.exportthread;
        }
        this.exportthread = new Export(path, filename, fileext, filter, query, format, dom, text, maxChunkSize, minified);
        this.exportthread.start();
        return this.exportthread;
    }

    public static void main(final String args[]) {
        final Date firstdate = null;
        System.out.println(GenericFormatter.SHORT_MINUTE_FORMATTER.format(firstdate));
    }

    public Export export() {
        return this.exportthread;
    }

    private final static Set<String> minified_keys = new HashSet<>();
    static {
        //minified_keys.add(CollectionSchema.id.getSolrFieldName());
        minified_keys.add(CollectionSchema.sku.getSolrFieldName());
        minified_keys.add(CollectionSchema.title.getSolrFieldName());
        //minified_keys.add(CollectionSchema.author.getSolrFieldName());
        minified_keys.add(CollectionSchema.description_txt.getSolrFieldName());
        //minified_keys.add(CollectionSchema.size_i.getSolrFieldName());
        minified_keys.add(CollectionSchema.last_modified.getSolrFieldName());
        minified_keys.add(CollectionSchema.text_t.getSolrFieldName());
    }

    public class Export extends Thread {
        private final File path;
        private final String filename, fileext;
        private final Pattern pattern;
        private String failure;
        private final String query;
        private final ExportFormat format;
        private final boolean dom, text;
        private int docCount, chunkSize, chunkCount;
        private final long maxChunkSize;
        private final boolean minified;

        private Export(
                final File path, final String filename,
                final String fileext, final String filter, final String query,
                final ExportFormat format, final boolean dom, final boolean text,
                final long maxChunkSize, final boolean minified) {
            super("Fulltext.Export");
            // format: 0=text, 1=html, 2=rss/xml
            this.path = path;
            this.filename = filename;
            this.fileext = fileext;
            this.pattern = filter == null ? null : Pattern.compile(filter);
            this.query = query == null? AbstractSolrConnector.CATCHALL_QUERY : query;
            this.failure = null;
            this.format = format;
            this.dom = dom;
            this.text = text;
            this.docCount = 0; // number of all documents exported so far
            this.chunkSize = 0; // number of documents in the current chunk
            this.chunkCount = 0; // number of chunks opened so far
            this.maxChunkSize = maxChunkSize; // number of maximum document count per chunk
            this.minified = minified;
            //if ((dom) && (format == 2)) dom = false;
        }

        private void printHead(final PrintWriter pw) {
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
                pw.println("<lst name=\"responseHeader\">");
                pw.println(" <str format=\"yacy.index.export.solr.xml\"/>");
                pw.println(" <lst name=\"params\">");
                pw.println("  <str name=\"q\">" + this.query + "</str>");
                pw.println(" </lst>");
                pw.println("</lst>");
                pw.println("<result>");
            }
        }

        private void printTail(final PrintWriter pw) {
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
        }

        @Override
        public void run() {
            try {
                if (this.path != null) this.path.mkdirs();
            } catch(final Exception e) {
                ConcurrentLog.logException(e);
                this.failure = e.getMessage();
                return;
            }

            try {
                this.docCount = 0;
                this.chunkSize = 0;
                this.chunkCount = 0;
                PrintWriter pw = getWriter();
                printHead(pw);
                if (this.dom) {
                    final Map<String, ReversibleScoreMap<String>> scores = Fulltext.this.getDefaultConnector().getFacets(this.query + " AND " + CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", 100000000, CollectionSchema.host_s.getSolrFieldName());
                    final ReversibleScoreMap<String> stats = scores.get(CollectionSchema.host_s.getSolrFieldName());
                    for (final String host: stats) {
                        if (this.pattern != null && !this.pattern.matcher(host).matches()) continue;
                        if (this.format == ExportFormat.text) pw.println(host);
                        if (this.format == ExportFormat.html) pw.println("<a href=\"http://" + host + "\">" + host + "</a><br>");
                        this.docCount++; this.chunkSize++;
                    }
                } else {
                    if (this.format == ExportFormat.solr || this.format == ExportFormat.elasticsearch || (this.text && this.format == ExportFormat.text)) {
                        final BlockingQueue<SolrDocument> docs = Fulltext.this.getDefaultConnector().concurrentDocumentsByQuery(this.query + " AND " + CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", null, 0, 100000000, Long.MAX_VALUE, 100, 1, true);
                        SolrDocument doc;
                        while ((doc = docs.take()) != AbstractSolrConnector.POISON_DOCUMENT) {
                            final String url = getStringFrom(doc.getFieldValue(CollectionSchema.sku.getSolrFieldName()));
                            if (this.pattern != null && !this.pattern.matcher(url).matches()) continue;
                            if (this.minified) {
                                final Iterator<Entry<String, Object>> i = doc.iterator();
                                while (i.hasNext()) {
                                    if (!minified_keys.contains(i.next().getKey())) i.remove();
                                }
                            }
                            final CRIgnoreWriter sw = new CRIgnoreWriter();
                            if (this.text) sw.write((String) doc.getFieldValue(CollectionSchema.text_t.getSolrFieldName()));
                            if (this.format == ExportFormat.solr) EnhancedXMLResponseWriter.writeDoc(sw, doc);
                            if (this.format == ExportFormat.elasticsearch) FlatJSONResponseWriter.writeDoc(sw, doc);
                            sw.close();
                            if (this.format == ExportFormat.elasticsearch) pw.println("{\"index\":{}}");
                            final String d = sw.toString();
                            pw.println(d);
                            this.docCount++; this.chunkSize++;
                            if (this.chunkSize >= this.maxChunkSize) {
                                printTail(pw);
                                pw.close();
                                this.chunkCount++;
                                pw = getWriter();
                                printHead(pw);
                                this.chunkSize = 0;
                            }
                        }
                    } else {
                        final BlockingQueue<SolrDocument> docs = Fulltext.this.getDefaultConnector().concurrentDocumentsByQuery(this.query + " AND " + CollectionSchema.httpstatus_i.getSolrFieldName() + ":200", null, 0, 100000000, Long.MAX_VALUE, 100, 1, true,
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
                            this.docCount++; this.chunkSize++;
                            if (this.chunkSize >= this.maxChunkSize) {
                                printTail(pw);
                                pw.close();
                                this.chunkCount++;
                                pw = getWriter();
                                printHead(pw);
                                this.chunkSize = 0;
                            }
                        }
                    }
                }
                printTail(pw);
                pw.close();
            } catch (final Exception e) {
                /* Catch but log any IO exception that can occur on copy, automatic closing or streams creation */
                ConcurrentLog.logException(e);
                this.failure = e.getMessage();
            }
            // terminate process
        }

        public File file() {
            final File f = new File(this.path, this.filename + "_" + chunkcount(this.chunkCount) + "." + this.fileext);
            return f;
        }

        private PrintWriter getWriter() throws IOException {
            final File f = file();
            final OutputStream os = new FileOutputStream(this.format == ExportFormat.solr ? new File(f.getAbsolutePath() + ".gz") : f);
            final PrintWriter pw =  new PrintWriter(new BufferedOutputStream(((this.format == ExportFormat.solr)) ? new GZIPOutputStream(os, 65536){{this.def.setLevel(Deflater.BEST_COMPRESSION);}} : os));
            return pw;
        }

        private String chunkcount(final int count) {
            if (count < 10) return "000" + count;
            if (count < 100) return "00" + count;
            if (count < 1000) return "0" + count;
            return "" + count;
        }

        public File path() {
            return this.path;
        }

        public String filename() {
            return this.filename;
        }

        public String fileext() {
            return this.fileext;
        }

        public String failed() {
            return this.failure;
        }

        public int docCount() {
            return this.docCount;
        }

        public int chunkCount() {
            return this.chunkCount;
        }

        @SuppressWarnings("unchecked")
        private String getStringFrom(final Object o) {
            if (o == null) return "";
            if (o instanceof ArrayList) return ((ArrayList<String>) o).get(0);
            return (String) o;
        }

    }

}
