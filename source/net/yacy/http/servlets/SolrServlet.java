/**
 *  SolrServlet
 *  Copyright 2014 by Michael Peter Christen
 *  First released 23.01.2014 at https://yacy.net
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

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.UpdateRequestHandler;
import org.apache.solr.handler.admin.LukeRequestHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.QueryResponseWriterUtil;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.servlet.ResponseUtils;
import org.apache.solr.servlet.SolrRequestParsers;
import org.apache.solr.servlet.cache.Method;

import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

public class SolrServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	public void service(ServletRequest request, ServletResponse response) throws IOException, ServletException {

    	if (!Domains.isIntranet(request.getRemoteAddr())) {
    		((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN,
                    "SolrServlet use not granted for IP " + request.getRemoteAddr());
    	}

		HttpServletRequest hrequest = (HttpServletRequest) request;

		final Method reqMethod = Method.getMethod(hrequest.getMethod());

		// get the embedded connector
		String requestURI = hrequest.getRequestURI();
		MultiMapSolrParams mmsp = SolrRequestParsers.parseQueryString(hrequest.getQueryString());
		boolean defaultConnector = (requestURI.startsWith("/solr/" + WebgraphSchema.CORE_NAME)) ? false
				: requestURI.startsWith("/solr/" + CollectionSchema.CORE_NAME)
						|| mmsp.get("core", CollectionSchema.CORE_NAME).equals(CollectionSchema.CORE_NAME);
		mmsp.getMap().remove("core");
		Switchboard sb = Switchboard.getSwitchboard();
		EmbeddedSolrConnector connector = defaultConnector ? sb.index.fulltext().getDefaultEmbeddedConnector()
				: sb.index.fulltext().getEmbeddedConnector(WebgraphSchema.CORE_NAME);

		if (connector == null)
			throw new ServletException("no core");

		SolrQueryResponse solrRsp = new SolrQueryResponse();
		SolrQueryRequest solrReq = null;
		try {
			solrReq = SolrRequestParsers.DEFAULT.parse(connector.getCore(), hrequest.getServletPath(), hrequest);
			solrReq.getContext().put("webapp", hrequest.getContextPath());
			SolrRequestHandler handler;
			if ("/solr/collection1/update".equals(hrequest.getServletPath())
					|| "/solr/webgraph/update".equals(hrequest.getServletPath())) {
				handler = new UpdateRequestHandler();
			} else {
				handler = new LukeRequestHandler();
			}
			handler.init(new NamedList<Object>());

			SolrRequestInfo.setRequestInfo(new SolrRequestInfo(solrReq, solrRsp));
			connector.getCore().execute(handler, solrReq, solrRsp);
			Iterator<Map.Entry<String, String>> headers = solrRsp.httpHeaders();
			while (headers.hasNext()) {
				Map.Entry<String, String> entry = headers.next();
				((HttpServletResponse) response).addHeader(entry.getKey(), entry.getValue());
			}

			// write response header
			QueryResponseWriter responseWriter = connector.getCore().getQueryResponseWriter(solrReq);
			writeResponse(solrReq, solrRsp, (HttpServletResponse) response, responseWriter, reqMethod);
		} catch (Exception e) {
			ConcurrentLog.logException(e);
		} finally {
			if (solrReq != null)
				solrReq.close();
			SolrRequestInfo.clearRequestInfo();
		}

	}

	private void writeResponse(SolrQueryRequest solrReq, SolrQueryResponse solrRsp, HttpServletResponse response, QueryResponseWriter responseWriter,
			Method reqMethod) throws IOException {
		try {
			Object invalidStates = solrReq.getContext().get(CloudSolrClient.STATE_VERSION);
			// This is the last item added to the response and the client would expect it
			// that way.
			// If that assumption is changed , it would fail. This is done to avoid an O(n)
			// scan on
			// the response for each request
			if (invalidStates != null)
				solrRsp.add(CloudSolrClient.STATE_VERSION, invalidStates);
			// Now write it out
			final String ct = responseWriter.getContentType(solrReq, solrRsp);
			// don't call setContentType on null
			if (null != ct)
				response.setContentType(ct);

			if (solrRsp.getException() != null) {
				@SuppressWarnings("rawtypes")
				NamedList info = new SimpleOrderedMap();
				@SuppressWarnings("unchecked")
				int code = ResponseUtils.getErrorInfo(solrRsp.getException(), info, null);
				solrRsp.add("error", info);
				response.setStatus(code);
			}

			if (Method.HEAD != reqMethod) {
				OutputStream out = response.getOutputStream();
				QueryResponseWriterUtil.writeQueryResponse(out, responseWriter, solrReq, solrRsp, ct);
			}
			// else http HEAD request, nothing to write out, waited this long just to get
			// ContentType
		} catch (EOFException e) {
			ConcurrentLog.info("SolrServlet", "Unable to write response, client closed connection or we are shutting down", e);
		}
	}
}
