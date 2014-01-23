/**
 *  SolrServlet
 *  Copyright 2014 by Michael Peter Christen
 *  First released 23.01.2014 at http://yacy.net
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
import java.io.Writer;
import java.nio.charset.Charset;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.BinaryQueryResponseWriter;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.servlet.SolrRequestParsers;
import org.apache.solr.servlet.cache.Method;
import org.apache.solr.util.FastWriter;

public class SolrServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    
    @Override
    public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException {

        HttpServletRequest hrequest = (HttpServletRequest) request;

        final Method reqMethod = Method.getMethod(hrequest.getMethod());
        
        // get the embedded connector
        String requestURI = hrequest.getRequestURI();
        MultiMapSolrParams mmsp = SolrRequestParsers.parseQueryString(hrequest.getQueryString());
        boolean defaultConnector = (requestURI.startsWith("/solr/" + WebgraphSchema.CORE_NAME)) ? false : requestURI.startsWith("/solr/" + CollectionSchema.CORE_NAME) || mmsp.get("core", CollectionSchema.CORE_NAME).equals(CollectionSchema.CORE_NAME);
        mmsp.getMap().remove("core");
        Switchboard sb = Switchboard.getSwitchboard();
        EmbeddedSolrConnector connector = defaultConnector ? sb.index.fulltext().getDefaultEmbeddedConnector() : sb.index.fulltext().getEmbeddedConnector(WebgraphSchema.CORE_NAME);
        if (connector == null) throw new ServletException("no core");

        SolrQueryResponse solrRsp = new SolrQueryResponse();
        try {
            SolrQueryRequest solrReq = connector.request(mmsp); // SolrRequestParsers.DEFAULT.parse(null, hrequest.getServletPath(), hrequest);
            solrReq.getContext().put("webapp", hrequest.getContextPath());
            SolrRequestHandler handler = sb.index.fulltext().getEmbeddedInstance().getCoreContainer().getMultiCoreHandler();
            connector.getCore().execute( handler, solrReq, solrRsp );
            
            // write response header 
            QueryResponseWriter responseWriter = connector.getCore().getQueryResponseWriter(solrReq);

            final String ct = responseWriter.getContentType(solrReq, solrRsp);
            if (null != ct) response.setContentType(ct); 

            if (Method.HEAD != reqMethod) {
              if (responseWriter instanceof BinaryQueryResponseWriter) {
                BinaryQueryResponseWriter binWriter = (BinaryQueryResponseWriter) responseWriter;
                binWriter.write(response.getOutputStream(), solrReq, solrRsp);
              } else {
                String charset = ContentStreamBase.getCharsetFromContentType(ct);
                Writer out = (charset == null || charset.equalsIgnoreCase("UTF-8"))
                  ? new OutputStreamWriter(response.getOutputStream(), UTF8)
                  : new OutputStreamWriter(response.getOutputStream(), charset);
                out = new FastWriter(out);
                responseWriter.write(out, solrReq, solrRsp);
                out.flush();
              }
            }

            ConcurrentLog.info("luke", solrRsp.getValues().toString());

        } catch (Exception e) {
            ConcurrentLog.logException(e);
        }
        
    }

}
