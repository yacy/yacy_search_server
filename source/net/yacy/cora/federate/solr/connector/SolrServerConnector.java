/**
 *  SolrServerConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 21.06.2012 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.federate.solr.connector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
//import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.LukeRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.LukeResponse;
import org.apache.solr.client.solrj.response.LukeResponse.FieldInfo;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;

import net.yacy.cora.federate.solr.embedded.EmbeddedSolrServer;
import net.yacy.cora.federate.solr.instance.ServerShard;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.schema.CollectionSchema;

public abstract class SolrServerConnector extends AbstractSolrConnector implements SolrConnector {

    protected final static ConcurrentLog log = new ConcurrentLog(SolrServerConnector.class.getName());
    public final static org.apache.lucene.analysis.CharArrayMap<Byte> classLoaderSynchro = new org.apache.lucene.analysis.CharArrayMap<>(0, true);
    // pre-instantiate this object to prevent sun.misc.Launcher$AppClassLoader deadlocks
    // this is a very nasty problem; solr instantiates objects dynamically which can cause deadlocks
    static {
        assert classLoaderSynchro != null;
    }
    protected SolrClient server;

    protected SolrServerConnector() {
        this.server = null;
    }

    protected void init(SolrClient server) {
        this.server = server;
    }

    public SolrClient getServer() {
        return this.server;
    }

    @Override
    public void commit(final boolean softCommit) {
        if (this.server == null) return;
        synchronized (this.server) {
            try {
                this.server.commit(true, true, softCommit);
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                //Log.logException(e);
            }
        }
    }

    /**
     * force an explicit merge of segments
     * @param maxSegments the maximum number of segments. Set to 1 for maximum optimization
     */
    @Override
    public void optimize(int maxSegments) {
        if (this.server == null) return;
        synchronized (this.server) {
            try {
                //this.server.optimize(true, true, maxSegments);
                new UpdateRequest().setAction(UpdateRequest.ACTION.OPTIMIZE, true, true, maxSegments, true).process(this.server); // this includes a 'true' for expungeDelete
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                ConcurrentLog.logException(e);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return this.server == null; // we cannot now this exactly when server != null, because SolrServer does not provide a method to test the close status
    }

    @Override
    public void close() {
        if (this.server == null) return;
        try {
            if (this.server instanceof EmbeddedSolrServer) {
                synchronized (this.server) {
                    this.server.commit(true, true, false);
                }
            }
            synchronized (this.server) {
                this.server.close(); // if the server is embedded, resources are freed, if it is a HttpSolrServer, only the httpclient is shut down, not the remote server
            }
            this.server = null;
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
        }
    }

    /**
     * delete everything in the solr index
     * @throws IOException
     */
    @Override
    public void clear() throws IOException {
        if (this.server == null) return;
        synchronized (this.server) {
            try {
                this.server.deleteByQuery(AbstractSolrConnector.CATCHALL_QUERY);
                this.server.commit(true, true, false);
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                throw new IOException(e);
            }
        }
    }

    @Override
    public void deleteById(final String id) throws IOException {
        if (this.server == null) return;
        synchronized (this.server) {
            try {
                this.server.deleteById(id, -1);
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                throw new IOException(e);
            }
        }
    }

    @Override
    public void deleteByIds(final Collection<String> ids) throws IOException {
        if (this.server == null) return;
        final List<String> l = new ArrayList<>();
        for (final String s: ids) l.add(s);
        synchronized (this.server) {
            try {
                this.server.deleteById(l, -1);
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                throw new IOException(e);
            }
        }
    }

    /**
     * delete entries from solr according the given solr query string
     * @param querystring
     * @throws IOException
     */
    @Override
    public  void deleteByQuery(final String querystring) throws IOException {
        if (this.server == null) return;
        synchronized (this.server) {
            try {
                this.server.deleteByQuery(querystring, -1);
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                throw new IOException(e);
            }
        }
    }

    public void add(final File file, final String solrId) throws IOException {
        final ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addFile(file, "application/octet-stream");
        up.setParam("literal.id", solrId);
        up.setParam("uprefix", "attr_");
        up.setParam("fmap.content", "attr_content");
        up.setCommitWithin(-1);
        //up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        try {
            this.server.request(up);
        } catch (final Throwable e) {
            clearCaches(); // prevent further OOM if this was caused by OOM
            throw new IOException(e);
        }
    }

    @Override
    public void add(final SolrInputDocument solrdoc) throws IOException, SolrException {
        if (this.server == null) return;
        if (solrdoc.containsKey("_version_")) solrdoc.setField("_version_",0L); // prevent Solr "version conflict"
        synchronized (this.server) {
            try {
                this.server.add(solrdoc);
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                ConcurrentLog.logException(e);
                // catches "version conflict for": try this again and delete the document in advance
                // with possible partial update docs, don't try to delete index doc and reinsert solrdoc
                // as this would result in a index doc with just the updated fields
                try {
                    this.server.deleteById((String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                } catch (final SolrServerException e1) {
                    ConcurrentLog.logException(e1);
                }
                try {
                    this.server.commit();
                } catch (final Throwable eee) {
                    ConcurrentLog.logException(eee);
                    // a time-out may occur here
                }
                try {
                    this.server.add(solrdoc);
                } catch (final Throwable ee) {
                    ConcurrentLog.logException(ee);
                    try {
                        this.server.commit();
                    } catch (final Throwable eee) {
                        ConcurrentLog.logException(eee);
                        // a time-out may occur here
                    }
                    try {
                        this.server.add(solrdoc);
                    } catch (final Throwable eee) {
                        ConcurrentLog.logException(eee);
                        throw new IOException(eee);
                    }
                }
            }
        }
    }

    @Override
    public void add(final Collection<SolrInputDocument> solrdocs) throws IOException, SolrException {
        if (this.server == null) return;
        for (final SolrInputDocument solrdoc : solrdocs) {
            if (solrdoc.containsKey("_version_")) solrdoc.setField("_version_",0L); // prevent Solr "version conflict"
        }
        synchronized (this.server) {
            try {
                this.server.add(solrdocs);
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                ConcurrentLog.logException(e);
                // catches "version conflict for": try this again and delete the document in advance
                // with possible partial update docs, don't try to delete index doc and reinsert solrdoc
                // as this would result in a index doc with just the updated fields

                List<String> ids = new ArrayList<>();
                for (final SolrInputDocument solrdoc : solrdocs) ids.add((String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                try {
                    this.server.deleteById(ids);
                } catch (final SolrServerException e1) {
                    ConcurrentLog.logException(e1);
                }
                try {
                    this.server.commit();
                } catch (final Throwable eee) {
                    ConcurrentLog.logException(eee);
                    // a time-out may occur here
                }
                try {
                    this.server.add(solrdocs);
                } catch (final Throwable ee) {
                    ConcurrentLog.logException(ee);
                    ids = new ArrayList<>();
                    for (final SolrInputDocument solrdoc : solrdocs) ids.add((String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                    log.warn(e.getMessage() + " IDs=" + ids.toString());
                    throw new IOException(ee);
                }
            }
        }
    }

    /**
     * get the solr document list from a query response
     * This differs from getResponseByParams in such a way that it does only create the fields of the response but
     * never search snippets and there are also no facets generated.
     * @param params
     * @return
     * @throws IOException
     * @throws SolrException
     */
    @Override
    public SolrDocumentList getDocumentListByParams(ModifiableSolrParams params) throws IOException {
        if (this.server == null) throw new IOException("server disconnected");
        // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
        final String q = params.get(CommonParams.Q);
        final String fq = params.get(CommonParams.FQ);
        final String sort = params.get(CommonParams.SORT);
        final String fl = params.get(CommonParams.FL);
        final String threadname = Thread.currentThread().getName();
        QueryResponse rsp;
        int retry = 0;
        Throwable error = null;
        while (retry++ < 10) {
            try {
                if (q != null) Thread.currentThread().setName("solr query: q = " + q + (fq == null ? "" : ", fq = " + fq) + (sort == null ? "" : ", sort = " + sort) + "; retry = " + retry + "; fl = " + fl); // for debugging in Threaddump
                rsp = this.server.query(params);
                if (q != null) Thread.currentThread().setName(threadname);
                if (rsp != null) if (log.isFine()) log.fine(rsp.getResults().getNumFound() + " results for q=" + q);
                return rsp.getResults();
            } catch (final SolrServerException e) {
                error = e;
            } catch (final Throwable e) {
                error = e;
                clearCaches(); // prevent further OOM if this was caused by OOM
            }
            ConcurrentLog.severe("SolrServerConnector", "Failed to query remote Solr: " + error.getMessage() + ", query:" + q + (fq == null ? "" : ", fq = " + fq));
            try {Thread.sleep(1000);} catch (final InterruptedException e) {}
        }
        throw new IOException("Error executing query", error);
    }

    // luke requests: these do not work for attached SolrCloud Server

    public Collection<FieldInfo> getFields() throws SolrServerException {
        // get all fields contained in index
        return getIndexBrowser(false).getFieldInfo().values();
    }

    /**
     * get the number of segments.
     * @return the number of segments, or 0 if unknown
     */
    @Override
    public int getSegmentCount() {
        if (this.server == null) return 0;
        try {
            final LukeResponse lukeResponse = getIndexBrowser(false);
            final NamedList<Object> info = lukeResponse.getIndexInfo();
            if (info == null) return 0;
            final Integer segmentCount = (Integer) info.get("segmentCount");
            if (segmentCount == null) return 1;
            return segmentCount.intValue();
        } catch (final Throwable e) {
            clearCaches(); // prevent further OOM if this was caused by OOM
            log.warn(e);
            return 0;
        }
    }

    private int useluke = 0; // 3-value logic: 1=yes, -1=no, 0=dontknow

    @Override
    public long getSize() {
        if (this.server == null) return 0;
        if (this.server instanceof ServerShard) {
            // the server can be a single shard; we don't know here
            // to test that, we submit requests to bots variants
            if (this.useluke == 1) return getSizeLukeRequest();
            if (this.useluke == -1) return getSizeQueryRequest();
            final long ls = getSizeLukeRequest();
            final long qs = getSizeQueryRequest();
            if (ls == 0 && qs == 0) {
                // we don't know if this is caused by an error or not; don't change the useluke value
                return 0;
            }
            if (ls == qs) {
                this.useluke = 1;
                return ls;
            }
            this.useluke = -1;
            return qs;
        }
        return getSizeLukeRequest();
    }

    private long getSizeQueryRequest() {
        if (this.server == null) return 0;
        try {
            final QueryResponse rsp = getResponseByParams(AbstractSolrConnector.catchSuccessQuery);
            if (rsp == null) return 0;
            final SolrDocumentList docs = rsp.getResults();
            if (docs == null) return 0;
            return docs.getNumFound();
        } catch (final Throwable e) {
            log.warn(e);
            return 0;
        }
    }

    private long getSizeLukeRequest() {
        if (this.server == null) return 0;
        try {
            final LukeResponse lukeResponse = getIndexBrowser(false);
            if (lukeResponse == null) return 0;
            final Integer numDocs = lukeResponse.getNumDocs();
            if (numDocs == null) return 0;
            return numDocs.longValue();
        } catch (final Throwable e) {
            clearCaches(); // prevent further OOM if this was caused by OOM
            log.warn(e);
            return 0;
        }
    }

    private LukeResponse getIndexBrowser(final boolean showSchema) throws SolrServerException {
        // get all fields contained in index
        final LukeRequest lukeRequest = new LukeRequest();
        lukeRequest.setResponseParser(new XMLResponseParser());
        lukeRequest.setNumTerms(0);
        lukeRequest.setShowSchema(showSchema);
        LukeResponse lukeResponse = null;
        try {
            lukeResponse = lukeRequest.process(this.server);
        } catch (final IOException e) {
            throw new SolrServerException(e.getMessage());
        }
        return lukeResponse;
    }

}
