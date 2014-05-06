/**
 *  SolrServerConnector
 *  Copyright 2012 by Michael Peter Christen
 *  First released 21.06.2012 at http://yacy.net
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

import net.yacy.cora.federate.solr.instance.ServerShard;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.schema.CollectionSchema;

import org.apache.lucene.analysis.NumericTokenStream;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.LukeRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.LukeResponse.FieldInfo;
import org.apache.solr.client.solrj.response.LukeResponse;
import org.apache.solr.client.solrj.response.QueryResponse;

public abstract class SolrServerConnector extends AbstractSolrConnector implements SolrConnector {

    protected final static ConcurrentLog log = new ConcurrentLog(SolrServerConnector.class.getName());
    public final static NumericTokenStream classLoaderSynchro = new NumericTokenStream();
    // pre-instantiate this object to prevent sun.misc.Launcher$AppClassLoader deadlocks
    // this is a very nasty problem; solr instantiates objects dynamically which can cause deadlocks
    static {
        assert classLoaderSynchro != null;
    }
    protected SolrServer server;

    protected SolrServerConnector() {
        this.server = null;
    }

    protected void init(SolrServer server) {
        this.server = server;
    }

    public SolrServer getServer() {
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
                this.server.shutdown(); // if the server is embedded, resources are freed, if it is a HttpSolrServer, only the httpclient is shut down, not the remote server
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
        List<String> l = new ArrayList<String>();
        for (String s: ids) l.add(s);
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
     * @param id the url hash of the entry
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
        synchronized (this.server) {
            try {
                if (solrdoc.containsKey("_version_")) solrdoc.setField("_version_",0L); // prevent Solr "version conflict"
                this.server.add(solrdoc, -1);
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                ConcurrentLog.logException(e);
                // catches "version conflict for": try this again and delete the document in advance
                try {
                    this.server.deleteById((String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
                } catch (final SolrServerException e1) {
                    ConcurrentLog.logException(e1);
                }
                try {
                    this.server.add(solrdoc, -1);
                } catch (final Throwable ee) {
                    ConcurrentLog.logException(ee);
                    try {
                        this.server.commit();
                    } catch (final Throwable eee) {
                        ConcurrentLog.logException(eee);
                        // a time-out may occur here
                    }
                    try {
                        this.server.add(solrdoc, -1);
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
        synchronized (this.server) {
            try {
                for (SolrInputDocument solrdoc : solrdocs) {
                    if (solrdoc.containsKey("_version_")) solrdoc.setField("_version_",0L); // prevent Solr "version conflict"
                }
                this.server.add(solrdocs, -1);
            } catch (final Throwable e) {
                clearCaches(); // prevent further OOM if this was caused by OOM
                ConcurrentLog.logException(e);
                // catches "version conflict for": try this again and delete the document in advance
                List<String> ids = new ArrayList<String>();
                for (SolrInputDocument solrdoc : solrdocs) ids.add((String) solrdoc.getFieldValue(CollectionSchema.id.getSolrFieldName()));
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
                    this.server.add(solrdocs, -1);
                } catch (final Throwable ee) {
                    ConcurrentLog.logException(ee);
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
    public SolrDocumentList getDocumentListByParams(ModifiableSolrParams params) throws IOException, SolrException {
        if (this.server == null) throw new IOException("server disconnected");
        // during the solr query we set the thread name to the query string to get more debugging info in thread dumps
        String q = params.get("q");
        String threadname = Thread.currentThread().getName();
        if (q != null) Thread.currentThread().setName("solr query: q = " + q);
        QueryResponse rsp;
        try {
            rsp = this.server.query(params);
            if (q != null) Thread.currentThread().setName(threadname);
            if (rsp != null) if (log.isFine()) log.fine(rsp.getResults().getNumFound() + " results for q=" + q);
            return rsp.getResults();
        } catch (final SolrServerException e) {
            clearCaches(); // prevent further OOM if this was caused by OOM
            throw new SolrException(ErrorCode.UNKNOWN, e);
        } catch (final Throwable e) {
            clearCaches(); // prevent further OOM if this was caused by OOM
            throw new IOException("Error executing query", e);
        }
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
            LukeResponse lukeResponse = getIndexBrowser(false);
            NamedList<Object> info = lukeResponse.getIndexInfo();
            if (info == null) return 0;
            Integer segmentCount = (Integer) info.get("segmentCount");
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
            if (useluke == 1) return getSizeLukeRequest();
            if (useluke == -1) return getSizeQueryRequest();
            long ls = getSizeLukeRequest();
            long qs = getSizeQueryRequest();
            if (ls == qs) {
                useluke = 1;
                return ls;
            }
            useluke = -1;
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
            LukeResponse lukeResponse = getIndexBrowser(false);
            if (lukeResponse == null) return 0;
            Integer numDocs = lukeResponse.getNumDocs();
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
        } catch (IOException e) {
            throw new SolrServerException(e.getMessage());
        }
        return lukeResponse;
    }

}
