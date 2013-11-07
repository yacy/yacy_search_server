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

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.schema.CollectionSchema;

import org.apache.lucene.analysis.NumericTokenStream;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.LukeRequest;
import org.apache.solr.client.solrj.response.LukeResponse.FieldInfo;
import org.apache.solr.client.solrj.response.LukeResponse;

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
        synchronized (this.server) {
            try {
                this.server.commit(true, true, softCommit);
                //if (this.server instanceof HttpSolrServer) ((HttpSolrServer) this.server).shutdown();
            } catch (final Throwable e) {
                //Log.logException(e);
            }
        }
    }

    /**
     * force an explicit merge of segments
     * @param maxSegments the maximum number of segments. Set to 1 for maximum optimization
     */
    public void optimize(int maxSegments) {
        if (this.server == null) return;
        if (getSegmentCount() <= maxSegments) return;
        synchronized (this.server) {
            try {
                this.server.optimize(true, true, maxSegments);
            } catch (final Throwable e) {
                ConcurrentLog.logException(e);
            }
        }
    }
    
    /**
     * get the number of segments.
     * @return the number of segments, or 0 if unknown
     */
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
            log.warn(e);
            return 0;
        }
    }
    

    @Override
    public void close() {
        if (this.server == null) return;
        try {
            if (this.server instanceof EmbeddedSolrServer) synchronized (this.server) {this.server.commit(true, true, false);}
            this.server = null;
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
        }
    }

    @Override
    public long getSize() {
        if (this.server == null) return 0;
        try {
            LukeResponse lukeResponse = getIndexBrowser(false);
            if (lukeResponse == null) return 0;
            Integer numDocs = lukeResponse.getNumDocs();
            if (numDocs == null) return 0;
            return numDocs.longValue();
        } catch (final Throwable e) {
            log.warn(e);
            return 0;
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
                this.server.deleteByQuery(AbstractSolrConnector.CATCHALL_TERM);
                this.server.commit(true, true, false);
            } catch (final Throwable e) {
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
    
    public Collection<FieldInfo> getFields() throws SolrServerException {
        // get all fields contained in index
        return getIndexBrowser(false).getFieldInfo().values();
    }
    
    private LukeResponse getIndexBrowser(final boolean showSchema) throws SolrServerException {
        // get all fields contained in index
        final LukeRequest lukeRequest = new LukeRequest();
        lukeRequest.setResponseParser(new XMLResponseParser());
        lukeRequest.setNumTerms(0);
        lukeRequest.setShowSchema(showSchema);
        /*
        final SolrRequest lukeRequest = new SolrRequest(METHOD.GET, "/admin/luke") {
            private static final long serialVersionUID = 1L;
            @Override
            public Collection<ContentStream> getContentStreams() throws IOException {
                return null;
            }
            @Override
            public SolrParams getParams() {
                ModifiableSolrParams params = new ModifiableSolrParams();
                //params.add("numTerms", "1");
                params.add("_", "" + System.currentTimeMillis()); // cheat a proxy
                if (showSchema) params.add("show", "schema");
                return params;
            }
            @Override
            public LukeResponse process(SolrServer server) throws SolrServerException, IOException {
              long startTime = System.currentTimeMillis();
              LukeResponse res = new LukeResponse();
              this.setResponseParser(new XMLResponseParser());
              NamedList<Object> response = server.request(this); 
              res.setResponse(response);
              res.setElapsedTime(System.currentTimeMillis() - startTime);
              return res;
            }
        };
        */
        LukeResponse lukeResponse = null;
        try {
            lukeResponse = lukeRequest.process(this.server);
        } catch (IOException e) {
            throw new SolrServerException(e.getMessage());
        }
        return lukeResponse;
    }

}
