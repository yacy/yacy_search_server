/**
 *  SolrSelectServlet
 *  Copyright 2012 by Michael Peter Christen
 *  First released 23.08.2012 at http://yacy.net
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
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.responsewriter.EnhancedXMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.GSAResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.GrepHTMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.HTMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.OpensearchResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.YJsonResponseWriter;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.BinaryResponseWriter;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.XSLTResponseWriter;
import org.apache.solr.search.DocList;
import org.apache.solr.servlet.SolrRequestParsers;
import org.apache.solr.servlet.cache.HttpCacheHeaderUtil;
import org.apache.solr.servlet.cache.Method;
import org.apache.solr.util.FastWriter;

/*
 * taken from the Solr 3.6.0 code, which is now deprecated;
 * this is now done in Solr 4.x.x with org.apache.solr.servlet.SolrDispatchFilter
 * implemented as servlet (we don't use multicore)
 */
public class SolrSelectServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    public final static Map<String, QueryResponseWriter> RESPONSE_WRITER = new HashMap<String, QueryResponseWriter>();
    static {
        RESPONSE_WRITER.putAll(SolrCore.DEFAULT_RESPONSE_WRITERS);
        XSLTResponseWriter xsltWriter = new XSLTResponseWriter();
        OpensearchResponseWriter opensearchResponseWriter = new OpensearchResponseWriter();
        @SuppressWarnings("rawtypes")
        NamedList initArgs = new NamedList();
        xsltWriter.init(initArgs);
        RESPONSE_WRITER.put("xslt", xsltWriter); // try i.e. http://localhost:8090/solr/select?q=*:*&start=0&rows=10&wt=xslt&tr=json.xsl
        RESPONSE_WRITER.put("exml", new EnhancedXMLResponseWriter());
        RESPONSE_WRITER.put("html", new HTMLResponseWriter());
        RESPONSE_WRITER.put("grephtml", new GrepHTMLResponseWriter());
        RESPONSE_WRITER.put("rss", opensearchResponseWriter); //try http://localhost:8090/solr/select?wt=rss&q=olympia&hl=true&hl.fl=text_t,h1,h2
        RESPONSE_WRITER.put("opensearch", opensearchResponseWriter); //try http://localhost:8090/solr/select?wt=rss&q=olympia&hl=true&hl.fl=text_t,h1,h2
        RESPONSE_WRITER.put("yjson", new YJsonResponseWriter()); //try http://localhost:8090/solr/select?wt=json&q=olympia&hl=true&hl.fl=text_t,h1,h2
        RESPONSE_WRITER.put("gsa", new GSAResponseWriter());
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
            boolean authenticated = true;
            
            // count remote searches if this was part of a p2p search
            if (mmsp.getMap().containsKey("partitions")) {
                final int partitions = mmsp.getInt("partitions", 30);
                sb.searchQueriesGlobal += 1.0f / partitions; // increase query counter
            }
            
            // get the ranking profile id
            int profileNr = mmsp.getInt("profileNr", 0);
            
            // rename post fields according to result style
            String querystring = "";
            if (!mmsp.getMap().containsKey(CommonParams.Q) && mmsp.getMap().containsKey("query")) {
                querystring = mmsp.get("query", "");
                mmsp.getMap().remove("query");
                QueryModifier modifier = new QueryModifier();
                querystring = modifier.parse(querystring);
                modifier.apply(mmsp);
                QueryGoal qg = new QueryGoal(querystring);
                StringBuilder solrQ = qg.collectionTextQueryString(sb.index.fulltext().getDefaultConfiguration(), profileNr, false);
                mmsp.getMap().put(CommonParams.Q, new String[]{solrQ.toString()}); // sru patch
            }
            String q = mmsp.get(CommonParams.Q, "");
            if (querystring.length() == 0) querystring = q;
            if (!mmsp.getMap().containsKey(CommonParams.START)) {
                int startRecord = mmsp.getFieldInt("startRecord", null, 0);
                mmsp.getMap().remove("startRecord");
                mmsp.getMap().put(CommonParams.START, new String[]{Integer.toString(startRecord)}); // sru patch
            }
            if (!mmsp.getMap().containsKey(CommonParams.ROWS)) {
                int maximumRecords = mmsp.getFieldInt("maximumRecords", null, 10);
                mmsp.getMap().remove("maximumRecords");
                mmsp.getMap().put(CommonParams.ROWS, new String[]{Integer.toString(maximumRecords)}); // sru patch
            } 
            mmsp.getMap().put(CommonParams.ROWS, new String[]{Integer.toString(Math.min(mmsp.getInt(CommonParams.ROWS, 10), (authenticated) ? 100000000 : 100))});            
            
            // set ranking according to profile number if ranking attributes are not given in the request
            if (!mmsp.getMap().containsKey("sort") && !mmsp.getMap().containsKey("bq") && !mmsp.getMap().containsKey("bf") && !mmsp.getMap().containsKey("boost")) {
                if (!mmsp.getMap().containsKey("defType")) mmsp.getMap().put("defType", new String[]{"edismax"});        
                Ranking ranking = sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr);
                String bq = ranking.getBoostQuery();
                String bf = ranking.getBoostFunction();
                if (bq.length() > 0) mmsp.getMap().put("bq", new String[]{bq});
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
            if ((responseWriter instanceof YJsonResponseWriter || responseWriter instanceof OpensearchResponseWriter) && "true".equals(mmsp.get("hl", "true"))) {
                // add options for snippet generation
                if (!mmsp.getMap().containsKey("hl.q")) mmsp.getMap().put("hl.q", new String[]{q});
                if (!mmsp.getMap().containsKey("hl.fl")) mmsp.getMap().put("hl.fl", new String[]{CollectionSchema.description_txt + "," + CollectionSchema.h4_txt.getSolrFieldName() + "," + CollectionSchema.h3_txt.getSolrFieldName() + "," + CollectionSchema.h2_txt.getSolrFieldName() + "," + CollectionSchema.h1_txt.getSolrFieldName() + "," + CollectionSchema.text_t.getSolrFieldName()});
                if (!mmsp.getMap().containsKey("hl.alternateField")) mmsp.getMap().put("hl.alternateField", new String[]{CollectionSchema.description_txt.getSolrFieldName()});
                if (!mmsp.getMap().containsKey("hl.simple.pre")) mmsp.getMap().put("hl.simple.pre", new String[]{"<b>"});
                if (!mmsp.getMap().containsKey("hl.simple.post")) mmsp.getMap().put("hl.simple.post", new String[]{"</b>"});
                if (!mmsp.getMap().containsKey("hl.fragsize")) mmsp.getMap().put("hl.fragsize", new String[]{Integer.toString(SearchEvent.SNIPPET_MAX_LENGTH)});
            }
            
            if (!mmsp.getMap().containsKey(DisMaxParams.QF) && !mmsp.getMap().containsKey(CommonParams.DF)) {
                mmsp.getMap().put(CommonParams.DF, new String[]{CollectionSchema.text_t.getSolrFieldName()});
            }

            // get the embedded connector
            String requestURI = hrequest.getRequestURI();
            boolean defaultConnector = (requestURI.startsWith("/solr/" + WebgraphSchema.CORE_NAME)) ? false : requestURI.startsWith("/solr/" + CollectionSchema.CORE_NAME) || mmsp.get("core", CollectionSchema.CORE_NAME).equals(CollectionSchema.CORE_NAME);
            mmsp.getMap().remove("core");
            EmbeddedSolrConnector connector = defaultConnector ? sb.index.fulltext().getDefaultEmbeddedConnector() : sb.index.fulltext().getEmbeddedConnector(WebgraphSchema.CORE_NAME);
            if (connector == null) throw new ServletException("no core");

            // do the solr request, generate facets if we use a special YaCy format
            req = connector.request(mmsp);

            SolrQueryResponse rsp = connector.query(req);

            // prepare response
            hresponse.setHeader("Cache-Control", "no-cache");
            HttpCacheHeaderUtil.checkHttpCachingVeto(rsp, hresponse, reqMethod);

            // check error
            if (rsp.getException() != null) {
                AccessTracker.addToDump(querystring, "0", new Date());
                sendError(hresponse, rsp.getException());
                return;
            }
            

            NamedList<?> values = rsp.getValues();
            DocList r = ((ResultContext) values.get("response")).docs;
            int numFound = r.matches();
            AccessTracker.addToDump(querystring, Integer.toString(numFound), new Date());
            
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
                out = new FastWriter(new OutputStreamWriter(response.getOutputStream(), UTF8.charset));
                responseWriter.write(out, req, rsp);
                out.flush();
            }
            
        } catch (final Throwable ex) {
            sendError(hresponse, ex);
        } finally {
            if (req != null) {
                req.close();
            }
            SolrRequestInfo.clearRequestInfo();
            if (out != null) try {out.close();} catch (final IOException e1) {}
        }
    }

    private static void sendError(HttpServletResponse hresponse, Throwable ex) throws IOException {
        int code = (ex instanceof SolrException) ? ((SolrException) ex).code() : 500;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        hresponse.sendError((code < 100) ? 500 : code, ex.getMessage() + "\n\n" + sw.toString());
    }

    public static void waitForSolr(String context, int port) throws Exception {
        // A raw term query type doesn't check the schema
        URL url = new URL("http://127.0.0.1:" + port + context + "/select?q={!raw+f=test_query}ping");

        Exception ex=null;
        // Wait for a total of 20 seconds: 100 tries, 200 milliseconds each
        for (int i = 0; i < 600; i++) {
            try {
                InputStream stream = url.openStream();
                stream.close();
            } catch (final IOException e) {
                ex=e;
                Thread.sleep(200);
                continue;
            }
            return;
        }
        throw new RuntimeException("Jetty/Solr unresponsive", ex);
    }

    public static class Servlet404 extends HttpServlet {
        private static final long serialVersionUID=-4497069674942245148L;
        @Override
        public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
            res.sendError(404, "Can not find: " + req.getRequestURI());
        }
    }

}
