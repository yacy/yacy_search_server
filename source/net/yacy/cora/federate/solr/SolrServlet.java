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
import java.util.Iterator;
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

import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.XMLResponseWriter;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.servlet.SolrRequestParsers;
import org.apache.solr.servlet.cache.HttpCacheHeaderUtil;
import org.apache.solr.servlet.cache.Method;
import org.apache.solr.util.FastWriter;


public class SolrServlet implements Filter {

    private static final QueryResponseWriter responseWriter = new XMLResponseWriter();
    private static EmbeddedSolrConnector connector;

    public SolrServlet() {
    }

    public static void initCore(EmbeddedSolrConnector c) {
        connector = c;
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
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
        SolrQueryRequest req = null;

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
        
        Writer out = null;
        try {
            SolrCore core = connector.getCore();
            if (core == null) {
                throw new UnsupportedOperationException("core not initialized");
            }

            // prepare request to solr
            hrequest.setAttribute("org.apache.solr.CoreContainer", core);
            MultiMapSolrParams mmsp = SolrRequestParsers.parseQueryString(hrequest.getQueryString());
            req = connector.request(mmsp);

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
            out = new FastWriter(new OutputStreamWriter(response.getOutputStream(), UTF8.charset));

            //debug
            @SuppressWarnings("unchecked")
            Iterator<Map.Entry<String, Object>> ie = rsp.getValues().iterator();
            Map.Entry<String, Object> e;
            while (ie.hasNext()) {
                e = ie.next();
                System.out.println("Field: " + e.getKey() + ", value: " + e.getValue().getClass().getName());
                //Field: responseHeader, value: org.apache.solr.common.util.SimpleOrderedMap
                //Field: response, value: org.apache.solr.search.DocSlice
                if (e.getValue() instanceof DocList) {
                    DocList ids = (DocList) e.getValue();
                    SolrIndexSearcher searcher = req.getSearcher();
                    DocIterator iterator = ids.iterator();
                    int sz = ids.size();
                    for (int i = 0; i < sz; i++) {
                        int id = iterator.nextDoc();
                        searcher.doc(id);
                    }
                }
            }

            responseWriter.write(out, req, rsp);
            out.flush();
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
