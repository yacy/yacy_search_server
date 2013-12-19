/**
 *  SolrServlet
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

package net.yacy.cora.federate.solr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.responsewriter.EnhancedXMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.GrepHTMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.HTMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.OpensearchResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.YJsonResponseWriter;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.BinaryResponseWriter;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.servlet.SolrRequestParsers;
import org.apache.solr.servlet.cache.HttpCacheHeaderUtil;
import org.apache.solr.servlet.cache.Method;
import org.apache.solr.util.FastWriter;


public class SolrServlet implements Filter {

    private static EmbeddedSolrConnector connector;
    private final Map<String, QueryResponseWriter> RESPONSE_WRITER = new HashMap<String, QueryResponseWriter>();
    
    public SolrServlet() { 
    }

    @Override
    public void init(FilterConfig config) throws ServletException {

        OpensearchResponseWriter opensearchResponseWriter = new OpensearchResponseWriter();

        // xml and xslt reponseWriter included in SorlCore.DEFAULT_RESPONSE_WRITERS 
        // DEFAULT_RESPONSE_WRITERS is allways checke, here only additional response writers
        RESPONSE_WRITER.put("exml", new EnhancedXMLResponseWriter());
        RESPONSE_WRITER.put("html", new HTMLResponseWriter());
        RESPONSE_WRITER.put("grephtml", new GrepHTMLResponseWriter());
        RESPONSE_WRITER.put("rss", opensearchResponseWriter); //try http://localhost:8090/solr/select?wt=rss&q=olympia&hl=true&hl.fl=text_t,h1,h2
        RESPONSE_WRITER.put("opensearch", opensearchResponseWriter); //try http://localhost:8090/solr/select?wt=rss&q=olympia&hl=true&hl.fl=text_t,h1,h2
        RESPONSE_WRITER.put("yjson", new YJsonResponseWriter()); //try http://localhost:8090/solr/select?wt=json&q=olympia&hl=true&hl.fl=text_t,h1,h2
        // GSA response implemented in separate servlet
        // RESPONSE_WRITER.put("gsa", new GSAResponseWriter());
    }

    @Override
    public void destroy() {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            if (chain != null) chain.doFilter(request, response);
            return;
        }

        HttpServletRequest hrequest = (HttpServletRequest) request;
        HttpServletResponse hresponse = (HttpServletResponse) response;

        // check if this servlet was called correctly
        String pathInfo = hrequest.getPathInfo();
        String path = pathInfo == null ? hrequest.getServletPath() : hrequest.getServletPath() + pathInfo; // should be "/select" after this

        if (!EmbeddedSolrConnector.SELECT.equals(path)) {
            // this is not for this servlet
            if (chain != null) chain.doFilter(request, response);
            return;
        }
        if (!EmbeddedSolrConnector.CONTEXT.equals(hrequest.getContextPath())) {
            // this is not for this servlet
            if (chain != null) chain.doFilter(request, response);
            return;
        }

        // reject POST which is not supported here
        final Method reqMethod = Method.getMethod(hrequest.getMethod());
        if (reqMethod == null || (reqMethod != Method.GET && reqMethod != Method.HEAD)) {
            throw new ServletException("Unsupported method: " + hrequest.getMethod());
        }

     
        // prepare request to solr        
        MultiMapSolrParams mmsp = SolrRequestParsers.parseQueryString(hrequest.getQueryString()); 
        String corename = mmsp.get("core",CollectionSchema.CORE_NAME);

        // get the embedded connector
        boolean defaultConnector = corename.equals(CollectionSchema.CORE_NAME);
        connector = defaultConnector ? Switchboard.getSwitchboard().index.fulltext().getDefaultEmbeddedConnector() : Switchboard.getSwitchboard().index.fulltext().getEmbeddedConnector(WebgraphSchema.CORE_NAME);

        SolrCore core = connector.getCore();
        if (core == null) {
            hresponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "core not initialized");
            return;
        }
        
        hrequest.setAttribute("org.apache.solr.CoreContainer", core);
        String wt = mmsp.get(CommonParams.WT, "xml"); // maybe use /solr/select?q=*:*&start=0&rows=10&wt=exml            
        QueryResponseWriter responseWriter = RESPONSE_WRITER.get(wt); // check local response writer
        if (responseWriter == null) {
            // check default response writer
            responseWriter = SolrCore.DEFAULT_RESPONSE_WRITERS.get(wt);
            if (responseWriter == null) {
                hresponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Solr responsewriter not found for " + wt);
                return;
            }
        }        
        Map<String,String[]> map = mmsp.getMap(); // get modifiable parameter map
        // add default search field if missing (required by edismax)
        if (!map.containsKey(CommonParams.DF)) map.put (CommonParams.DF, new String[]{CollectionSchema.text_t.getSolrFieldName()});        
        // if this is a call to YaCys special search formats, enhance the query with field assignments
        if ((responseWriter instanceof YJsonResponseWriter || responseWriter instanceof OpensearchResponseWriter) && "true".equals(mmsp.get("hl", "true"))) {
            // add options for snippet generation
            if (!map.containsKey("hl.q")) map.put("hl.q",new String[]{mmsp.get("q")});
            if (!map.containsKey("hl.fl")) map.put("hl.fl",new String[]{CollectionSchema.h1_txt.getSolrFieldName() + "," + CollectionSchema.h2_txt.getSolrFieldName() + "," + CollectionSchema.text_t.getSolrFieldName()});
            if (!map.containsKey("hl.alternateField")) map.put("hl.alternateField",new String[]{CollectionSchema.description_txt.getSolrFieldName()});
            if (!map.containsKey("hl.simple.pre")) map.put("hl.simple.pre",new String[]{"<b>"});
            if (!map.containsKey("hl.simple.post")) map.put("hl.simple.post",new String[]{"</b>"});
            if (!map.containsKey("hl.fragsize")) map.put("hl.fragsize",new String[]{Integer.toString(SearchEvent.SNIPPET_MAX_LENGTH)});
        }
        SolrQueryRequest req = connector.request(mmsp);
        SolrQueryResponse rsp = connector.query(req);

        // prepare response
        hresponse.setHeader("Cache-Control", "no-cache");
        HttpCacheHeaderUtil.checkHttpCachingVeto(rsp, hresponse, reqMethod);

        // check error
        if (rsp.getException() != null) {
            sendError(hresponse, rsp.getException());
            return;
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
            Writer out = new FastWriter(new OutputStreamWriter(response.getOutputStream(), UTF8.charset));
            responseWriter.write(out, req, rsp);
            out.flush();
        }
        
        // log result
        Object rv = rsp.getValues().get("response");
        int matches = 0;
        if (rv != null && rv instanceof ResultContext) {
            matches = ((ResultContext) rv).docs.matches();
        } else if (rv != null && rv instanceof SolrDocumentList) {
            matches = (int) ((SolrDocumentList) rv).getNumFound();
        }
        AccessTracker.addToDump(mmsp.get("q"), Integer.toString(matches));
        ConcurrentLog.info("SOLR Query", "results: " + matches + ", for query:" + req.getParamString());
        req.close();

        SolrRequestInfo.clearRequestInfo();               
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
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Can not find: " + req.getRequestURI());
        }
    }

}
