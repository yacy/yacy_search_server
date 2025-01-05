/**
 *  SolrSelectServlet
 *  Copyright 2012 by Michael Peter Christen
 *  First released 23.08.2012 at https://yacy.net
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

package net.yacy.http.servlets;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.federate.solr.responsewriter.EmbeddedSolrResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.EnhancedXMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.GrepHTMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.HTMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.OpensearchResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.SnapshotImagesReponseWriter;
import net.yacy.cora.federate.solr.responsewriter.SolrjResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.YJsonResponseWriter;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.UserDB;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.response.BinaryResponseWriter;
import org.apache.solr.response.CSVResponseWriter;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.RawResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.scripting.xslt.XSLTResponseWriter;
import org.apache.solr.search.DocList;
import org.apache.solr.servlet.SolrRequestParsers;
import org.apache.solr.servlet.cache.HttpCacheHeaderUtil;
import org.apache.solr.servlet.cache.Method;

/*
 * taken from the Solr 3.6.0 code, which is now deprecated;
 * this is now done in Solr 4.x.x with org.apache.solr.servlet.SolrDispatchFilter
 * implemented as servlet
 */
public class SolrSelectServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public final Map<String, QueryResponseWriter> RESPONSE_WRITER = new HashMap<String, QueryResponseWriter>();

    /**
     * Default initialization, adds additional and custom result response writers
     * in addition to the Solr default writers.
     */
    @Override
    public void init() {
        RESPONSE_WRITER.putAll(SolrCore.DEFAULT_RESPONSE_WRITERS);
        XSLTResponseWriter xsltWriter = new XSLTResponseWriter();
        OpensearchResponseWriter opensearchResponseWriter = new OpensearchResponseWriter();
        NamedList<?> initArgs = new NamedList<>();
        xsltWriter.init(initArgs);
        RESPONSE_WRITER.put("xslt", xsltWriter); // try i.e. http://localhost:8090/solr/select?q=*:*&start=0&rows=10&wt=xslt&tr=json.xsl
        RESPONSE_WRITER.put("exml", new EnhancedXMLResponseWriter());
        RESPONSE_WRITER.put("html", new HTMLResponseWriter());
        RESPONSE_WRITER.put("snapshots", new SnapshotImagesReponseWriter());
        RESPONSE_WRITER.put("grephtml", new GrepHTMLResponseWriter());
        RESPONSE_WRITER.put("rss", opensearchResponseWriter); //try http://localhost:8090/solr/select?wt=rss&q=olympia&hl=true&hl.fl=text_t,h1,h2
        RESPONSE_WRITER.put("opensearch", opensearchResponseWriter); //try http://localhost:8090/solr/select?wt=rss&q=olympia&hl=true&hl.fl=text_t,h1,h2
        RESPONSE_WRITER.put("yjson", new YJsonResponseWriter()); //try http://localhost:8090/solr/select?wt=yjson&q=olympia&hl=true&hl.fl=text_t,h1,h2
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException {

        HttpServletRequest hrequest = (HttpServletRequest) request;
        HttpServletResponse hresponse = (HttpServletResponse) response;
        SolrQueryRequest req = null;

        final Method reqMethod = Method.getMethod(hrequest.getMethod());

        Writer out = null;
        try {
            // prepare request to solr
            MultiMapSolrParams mmsp = SolrRequestParsers.parseQueryString(hrequest.getQueryString());

            Switchboard sb = Switchboard.getSwitchboard();
            // TODO: isUserInRole needs a login to jetty container (not done automatically on admin from localhost)
            boolean authenticated = hrequest.isUserInRole(UserDB.AccessRight.ADMIN_RIGHT.toString());

            // count remote searches if this was part of a p2p search
            if (mmsp.getMap().containsKey("partitions")) {
                final int partitions = mmsp.getInt("partitions", 30);
                sb.searchQueriesGlobal += 1.0f / partitions; // increase query counter
            }

            // get the ranking profile id
            int profileNr = mmsp.getInt("profileNr", 0);

            // rename post fields according to result style
            String querystring = "";
            if (!mmsp.getMap().containsKey(CommonParams.Q) && mmsp.getMap().containsKey(CommonParams.QUERY)) {
                querystring = mmsp.get(CommonParams.QUERY, "");
                mmsp.getMap().remove(CommonParams.QUERY);
                QueryModifier modifier = new QueryModifier(0);
                querystring = modifier.parse(querystring);
                modifier.apply(mmsp);
                QueryGoal qg = new QueryGoal(querystring);
                StringBuilder solrQ = qg.collectionTextQuery();
                mmsp.getMap().put(CommonParams.Q, new String[]{solrQ.toString()}); // sru patch

                // experimental p2p enrichment if flag to do so is set
                /*
                final String p2pQuery = querystring;
                new Thread() {
                    @Override
                    public void run() {
                        FederateSearchManager.getManager().query(p2pQuery);
                    }
                }.start();
                */
            }
            String q = mmsp.get(CommonParams.Q, "");
            ConcurrentLog.info("SolrSelect", "client=" + RequestHeader.client(request) + " q=" + q); // to detect bots and dos

            if (querystring.length() == 0) querystring = q;
            if (!mmsp.getMap().containsKey(CommonParams.START)) {
                int startRecord = mmsp.getFieldInt("startRecord", null, CommonParams.START_DEFAULT);
                mmsp.getMap().remove("startRecord");
                mmsp.getMap().put(CommonParams.START, new String[]{Integer.toString(startRecord)}); // sru patch
            }
            if (!mmsp.getMap().containsKey(CommonParams.ROWS)) {
                int maximumRecords = mmsp.getFieldInt("maximumRecords", null, CommonParams.ROWS_DEFAULT);
                mmsp.getMap().remove("maximumRecords");
                mmsp.getMap().put(CommonParams.ROWS, new String[]{Integer.toString(maximumRecords)}); // sru patch
            } 
            mmsp.getMap().put(CommonParams.ROWS, new String[]{Integer.toString(Math.min(mmsp.getInt(CommonParams.ROWS, CommonParams.ROWS_DEFAULT), (authenticated) ? 100000000 : 100))});
            boolean zeroDoc = mmsp.getInt(CommonParams.ROWS, CommonParams.ROWS_DEFAULT) < 1;
            boolean multiDoc = mmsp.getInt(CommonParams.ROWS, CommonParams.ROWS_DEFAULT) > 1;

            // set ranking according to profile number if ranking attributes are not given in the request
            Ranking ranking = sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr);
            if (multiDoc && !mmsp.getMap().containsKey(CommonParams.SORT) && !mmsp.getMap().containsKey(DisMaxParams.BQ) && !mmsp.getMap().containsKey(DisMaxParams.BF) && !mmsp.getMap().containsKey("boost")) {
                if (!mmsp.getMap().containsKey("defType")) mmsp.getMap().put("defType", new String[]{"edismax"});        
                String fq = ranking.getFilterQuery();
                String bq = ranking.getBoostQuery();
                String bf = ranking.getBoostFunction();
                if (fq.length() > 0) mmsp.getMap().put(CommonParams.FQ, new String[]{fq});
                if (bq.length() > 0) mmsp.getMap().put(DisMaxParams.BQ, StringUtils.split(bq,"\t\n\r\f")); // bq split into multiple query params, allowing space in single query
                if (bf.length() > 0) mmsp.getMap().put("boost", new String[]{bf}); // a boost function extension, see http://wiki.apache.org/solr/ExtendedDisMax#bf_.28Boost_Function.2C_additive.29
            }

            // get a response writer for the result
            String wt = mmsp.get(CommonParams.WT, "xml"); // maybe use /solr/select?q=*:*&start=0&rows=10&wt=exml
            QueryResponseWriter responseWriter = RESPONSE_WRITER.get(wt);
            if (responseWriter == null) throw new ServletException("no response writer");
            if (responseWriter instanceof OpensearchResponseWriter) {
                // set the title every time, it is possible that it has changed
                final String promoteSearchPageGreeting =
                                (sb.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) ? sb.getConfig(
                                    "network.unit.description",
                                    "") : sb.getConfig(SwitchboardConstants.GREETING, "");
                ((OpensearchResponseWriter) responseWriter).setTitle(promoteSearchPageGreeting);
            }

            // if this is a call to YaCys special search formats, enhance the query with field assignments
            if (!zeroDoc && (responseWriter instanceof YJsonResponseWriter || responseWriter instanceof OpensearchResponseWriter) && "true".equals(mmsp.get("hl", "true"))) {
                // add options for snippet generation
                if (!mmsp.getMap().containsKey("hl.q")) mmsp.getMap().put("hl.q", new String[]{q});
                if (!mmsp.getMap().containsKey("hl.fl")) mmsp.getMap().put("hl.fl", new String[]{CollectionSchema.description_txt.getSolrFieldName() + "," + CollectionSchema.h4_txt.getSolrFieldName() + "," + CollectionSchema.h3_txt.getSolrFieldName() + "," + CollectionSchema.h2_txt.getSolrFieldName() + "," + CollectionSchema.h1_txt.getSolrFieldName() + "," + CollectionSchema.text_t.getSolrFieldName()});
                if (!mmsp.getMap().containsKey("hl.alternateField")) mmsp.getMap().put("hl.alternateField", new String[]{CollectionSchema.description_txt.getSolrFieldName()});
                if (!mmsp.getMap().containsKey("hl.simple.pre")) mmsp.getMap().put("hl.simple.pre", new String[]{"<b>"});
                if (!mmsp.getMap().containsKey("hl.simple.post")) mmsp.getMap().put("hl.simple.post", new String[]{"</b>"});
                if (!mmsp.getMap().containsKey("hl.fragsize")) mmsp.getMap().put("hl.fragsize", new String[]{Integer.toString(SearchEvent.SNIPPET_MAX_LENGTH)});
                if (!mmsp.getMap().containsKey(CommonParams.FL)) mmsp.getMap().put(CommonParams.FL, new String[]{
                    CollectionSchema.sku.getSolrFieldName() + "," +
                    CollectionSchema.title.getSolrFieldName() + "," +
                    CollectionSchema.description_txt.getSolrFieldName() + "," +
                    CollectionSchema.id.getSolrFieldName() + "," +
                    CollectionSchema.author.getSolrFieldName() + "," +
                    CollectionSchema.publisher_t.getSolrFieldName() + "," +
                    CollectionSchema.keywords.getSolrFieldName() + "," +
                    CollectionSchema.url_paths_sxt.getSolrFieldName() + "," +
                    CollectionSchema.last_modified.getSolrFieldName() + "," +
                    CollectionSchema.size_i.getSolrFieldName() + "," +
                    CollectionSchema.url_protocol_s.getSolrFieldName() + "," +
                    CollectionSchema.url_file_ext_s.getSolrFieldName()});
            }

            // get the embedded connector
            final String requestURI = hrequest.getRequestURI();
            boolean defaultConnector = (requestURI.startsWith("/solr/" + WebgraphSchema.CORE_NAME)) ? false : requestURI.startsWith("/solr/" + CollectionSchema.CORE_NAME) || mmsp.get("core", CollectionSchema.CORE_NAME).equals(CollectionSchema.CORE_NAME);
            mmsp.getMap().remove("core");
            SolrConnector connector = defaultConnector ? sb.index.fulltext().getDefaultEmbeddedConnector() : sb.index.fulltext().getEmbeddedConnector(WebgraphSchema.CORE_NAME);
            if (connector == null) {
                connector = defaultConnector ? sb.index.fulltext().getDefaultConnector() : sb.index.fulltext().getConnectorForRead(WebgraphSchema.CORE_NAME);
            }
            if (connector == null) throw new ServletException("no core");

            // add default queryfield parameter according to local ranking config (or defaultfield)
            if (multiDoc) {
                if (ranking != null) { // ranking normally never null
                    final String qf = ranking.getQueryFields();
                    if (qf.length() > 4 && !mmsp.getMap().containsKey(DisMaxParams.QF)) { // make sure qf has content (else use df)
                        MultiMapSolrParams.addParam(DisMaxParams.QF, qf, mmsp.getMap()); // add QF that we set to be best suited for our index
                                // TODO: if every peer applies a decent QF itself, this can be reverted to getMap().put()
                    } else if(!mmsp.getMap().containsKey(CommonParams.DF)) {
                        mmsp.getMap().put(CommonParams.DF, new String[]{CollectionSchema.text_t.getSolrFieldName()});
                    }
                } else if(!mmsp.getMap().containsKey(CommonParams.DF)) {
                    mmsp.getMap().put(CommonParams.DF, new String[]{CollectionSchema.text_t.getSolrFieldName()});
                }
            }

            // do the solr request, generate facets if we use a special YaCy format
            final SolrQueryResponse rsp;
            if (connector instanceof EmbeddedSolrConnector) {
                req = ((EmbeddedSolrConnector) connector).request(mmsp);

                /* Add the servlet request URI to the context for eventual computation of relative paths in writers */
                req.getContext().put("requestURI", requestURI);

                rsp = ((EmbeddedSolrConnector) connector).query(req);

                // prepare response
                hresponse.setHeader("Cache-Control", "no-cache, no-store");
                HttpCacheHeaderUtil.checkHttpCachingVeto(rsp, hresponse, reqMethod);

                // check error
                if (rsp.getException() != null) {
                    AccessTracker.addToDump(querystring, 0, new Date(), "sq");
                    sendError(hresponse, rsp.getException());
                    return;
                }

                final Object responseObj = rsp.getResponse();
                if(responseObj instanceof ResultContext) {
                    /* Regular response object */
                    final DocList r = ((ResultContext) responseObj).getDocList();
                    AccessTracker.addToDump(querystring, r.matches(), new Date(), "sq");
                } else if(responseObj instanceof SolrDocumentList){
                    /*
                     * The response object can be a SolrDocumentList when the response is partial,
                     * for example when the allowed processing time has been exceeded
                     */
                    final SolrDocumentList r = (SolrDocumentList) responseObj;
                    AccessTracker.addToDump(querystring, r.getNumFound(), new Date(), "sq");
                }

                // write response header
                final String contentType = responseWriter.getContentType(req, rsp);
                if (null != contentType) response.setContentType(contentType);

                if (Method.HEAD == reqMethod) {
                    return;
                }

                // write response body
                if (responseWriter instanceof BinaryResponseWriter) {
                    ((BinaryResponseWriter) responseWriter).write(response.getOutputStream(), req, rsp);
                } else {
                    OutputStreamWriter osw = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
                    responseWriter.write(osw, req, rsp);
                    osw.close();
                }
            } else {
                if (responseWriter instanceof EmbeddedSolrResponseWriter || responseWriter instanceof CSVResponseWriter
                        || responseWriter instanceof XSLTResponseWriter || responseWriter instanceof RawResponseWriter) {
                    /* These writers need a non null req.getSearcher(), req.getSchema() and/or req.getCore() */
                    throw new ServletException("The writer " + responseWriter.getClass().getSimpleName() + " can only process responses from an embedded Solr server.");
                } 

                QueryResponse queryRsp = connector.getResponseByParams(ModifiableSolrParams.of(mmsp));

                /* Create SolrQueryRequestBase and SolrQueryResponse instances as these types are requited by Solr standard writers.
                 * WARNING : the SolrQueryRequestBase instance will return null for the getSearcher(), getCore() and getSchema() functions.
                 * Be sure thath the responseWriter instance can handle this properly.  */
                req = new SolrQueryRequestBase(null, mmsp) {};

                /* Add the servlet request URI to the context for eventual computation of relative paths in writers */
                req.getContext().put("requestURI", requestURI);

                rsp = new SolrQueryResponse();
                rsp.setHttpCaching(false);
                rsp.setAllValues(queryRsp.getResponse());

                if(!mmsp.getBool(CommonParams.OMIT_HEADER, false)) {
                    NamedList<Object> responseHeader = rsp.getResponseHeader();
                    if (responseHeader == null) {
                        /* The remote Solr provided no response header ? Not likely to happen but let's add one */
                        responseHeader = new SimpleOrderedMap<Object>();
                        responseHeader.add("params", mmsp.toNamedList());
                        rsp.addResponseHeader(responseHeader);
                    } else {
                        final int paramsIndex = responseHeader.indexOf("params", 0);
                        if (paramsIndex >= 0) {
                            /* Write this Solr servlet initial params to the response header and not the params sent to the remote Solr that differ a little (notably the wt param) */
                            responseHeader.setVal(paramsIndex, mmsp.toNamedList());
                        } else {
                            responseHeader.add("params", mmsp.toNamedList());
                        }
                    }
                }

                // prepare response
                hresponse.setHeader("Cache-Control", "no-cache, no-store");

                final SolrDocumentList documentsList = queryRsp.getResults();
                long numFound = documentsList.getNumFound();
                AccessTracker.addToDump(querystring, numFound, new Date(), "sq");

                // write response header
                final String contentType = responseWriter.getContentType(req, rsp);
                if (null != contentType) {
                    response.setContentType(contentType);
                }

                if (Method.HEAD == reqMethod) {
                    return;
                }

                // write response body
                if (responseWriter instanceof SolrjResponseWriter) {
                    out = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
                    ((SolrjResponseWriter) responseWriter).write(out, req,
                            defaultConnector ? CollectionSchema.CORE_NAME : WebgraphSchema.CORE_NAME, queryRsp);
                } else if(responseWriter instanceof BinaryResponseWriter) {
                    ((BinaryResponseWriter) responseWriter).write(response.getOutputStream(), req, rsp);
                } else {
                    OutputStreamWriter osw = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
                    responseWriter.write(osw, req, rsp);
                    osw.close();
                }
            }
        } catch (final Throwable ex) {
            sendError(hresponse, ex);
        } finally {
            if (req != null) {
                req.close();
            }
            if (out != null) {
                try {
                    out.close();
                } catch (final IOException e1) {
                    ConcurrentLog.info("SolrSelect", "Could not close output writer."
                            + (e1.getMessage() != null ? "Cause : " + e1.getMessage() : ""));
                }
            }
        }
    }

    private void sendError(HttpServletResponse hresponse, Throwable ex) throws IOException {
        int code = (ex instanceof SolrException) ? ((SolrException) ex).code() : 500;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        hresponse.sendError((code < 100) ? 500 : code, ex.getMessage() + "\n\n" + sw.toString());
    }

}
