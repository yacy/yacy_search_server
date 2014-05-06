/**
 *  search
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 30.10.2013 at http://yacy.net
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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.responsewriter.GSAResponseWriter;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.UserDB;
import net.yacy.search.Switchboard;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.server.serverObjects;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.FastWriter;


/**
 * This is a gsa result formatter for solr search results.
 * The result format is implemented according to
 * https://developers.google.com/search-appliance/documentation/614/xml_reference
 */
public class GSAsearchServlet extends HttpServlet {

    private static final long serialVersionUID = 7835985518515673885L;
    
    private final static GSAResponseWriter responseWriter = new GSAResponseWriter();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType(QueryResponseWriter.CONTENT_TYPE_XML_UTF8);
        response.setStatus(HttpServletResponse.SC_OK);
        respond(request, Switchboard.getSwitchboard(), response.getOutputStream());
    }
    
    // ------------------------------------------
    /**
     * from here copy of htroot/gsa/gsasearchresult.java
     * with modification to use HttpServletRequest instead of (yacy) RequestHeader
     */
    
 
    public static void respond(final HttpServletRequest header, final Switchboard sb, final OutputStream out) {

        // remember the peer contact for peer statistics
        String clientip = header.getHeader(HeaderFramework.CONNECTION_PROP_CLIENTIP);
        if (clientip == null) clientip = "<unknown>"; // read an artificial header addendum
        String userAgent = header.getHeader(HeaderFramework.USER_AGENT);
        if (userAgent == null) userAgent = "<unknown>";
        sb.peers.peerActions.setUserAgent(clientip, userAgent);

        // --- handled by Servlet securityHandler
        // check if user is allowed to search (can be switched in /ConfigPortal.html)
        boolean authenticated = header.isUserInRole(UserDB.AccessRight.ADMIN_RIGHT.toString()); //sb.adminAuthenticated(header) >= 2;
        // final boolean searchAllowed = authenticated || sb.getConfigBool(SwitchboardConstants.PUBLIC_SEARCHPAGE, true);
        // if (!searchAllowed) return null;

        // create post
        serverObjects post = new serverObjects();
        post.put("q", ""); post.put("num", "0");
        // convert servletrequest parameter to old style serverObjects map
        Map<String, String[]> map = header.getParameterMap();
        Iterator<Map.Entry<String, String[]>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String[]> param = it.next();
            post.put(param.getKey(), param.getValue()); // hint: post.put uses String[] for String value anyways
        }     

        ConcurrentLog.info("GSA Query", post.toString());
        sb.intermissionAllThreads(3000); // tell all threads to do nothing for a specific time
        
        // rename post fields according to result style
        //post.put(CommonParams.Q, post.remove("q")); // same as solr
        //post.put(CommonParams.START, post.remove("start")); // same as solr
        //post.put(, post.remove("client"));//required, example: myfrontend
        //post.put(, post.remove("output"));//required, example: xml,xml_no_dtd
        String originalQuery = post.get(CommonParams.Q, "");
        post.put("originalQuery", originalQuery);
        
        // get a solr query string
        QueryGoal qg = new QueryGoal(originalQuery);
        StringBuilder solrQ = qg.collectionTextQueryString(sb.index.fulltext().getDefaultConfiguration(), 0, false);
        post.put("defType", "edismax");
        post.put(CommonParams.Q, solrQ.toString());
        post.put(CommonParams.ROWS, post.remove("num"));
        post.put(CommonParams.ROWS, Math.min(post.getInt(CommonParams.ROWS, 10), (authenticated) ? 100000000 : 100));
        
        // set ranking
        if (post.containsKey("sort")) {
            // if a gsa-style sort attribute is given, use this to set the solr sort attribute
            GSAResponseWriter.Sort sort = new GSAResponseWriter.Sort(post.get(CommonParams.SORT, ""));
            String sorts = sort.toSolr();
            if (sorts == null) {
                post.remove(CommonParams.SORT);
            } else {
                post.put(CommonParams.SORT, sorts);
            }
        } else {
            // if no such sort attribute is given, use the ranking as configured for YaCy
            Ranking ranking = sb.index.fulltext().getDefaultConfiguration().getRanking(0);
            String bq = ranking.getBoostQuery();
            String bf = ranking.getBoostFunction();
            if (bq.length() > 0) post.put("bq", bq);
            if (bf.length() > 0) post.put("boost", bf); // a boost function extension, see http://wiki.apache.org/solr/ExtendedDisMax#bf_.28Boost_Function.2C_additive.29
        }
        String daterange[] = post.remove("daterange");
        if (daterange != null) {
            String origfq = post.get(CommonParams.FQ);
            String datefq = "";
            for (String dr: daterange) {
                String from_to[] = dr.endsWith("..") ? new String[]{dr.substring(0, dr.length() - 2), ""} : dr.startsWith("..") ? new String[]{"", dr.substring(2)} : dr.split("\\.\\.");
                if (from_to.length != 2) continue;
                Date from = HeaderFramework.parseGSAFS(from_to[0]);
                if (from == null) from = new Date(0);
                Date to = HeaderFramework.parseGSAFS(from_to[1]);
                if (to == null) to = new Date();
                to.setTime(to.getTime() + 24L * 60L * 60L * 1000L); // we add a day because the day is inclusive
                String z = CollectionSchema.last_modified.getSolrFieldName() + ":[" + ISO8601Formatter.FORMATTER.format(from) + " TO " + ISO8601Formatter.FORMATTER.format(to) + "]";
                datefq = datefq.length() == 0 ? z : " OR " + z;
            }
            if (datefq.length() > 0) post.put(CommonParams.FQ, origfq == null || origfq.length() == 0 ? datefq : "(" + origfq + ") AND (" + datefq + ")");
        }
        post.put(CommonParams.FL,
                CollectionSchema.content_type.getSolrFieldName() + ',' +
                CollectionSchema.id.getSolrFieldName() + ',' +
                CollectionSchema.sku.getSolrFieldName() + ',' +
                CollectionSchema.title.getSolrFieldName() + ',' +
                CollectionSchema.description_txt.getSolrFieldName() + ',' +
                CollectionSchema.load_date_dt.getSolrFieldName() + ',' +
                CollectionSchema.last_modified.getSolrFieldName() + ',' +
                CollectionSchema.size_i.getSolrFieldName());
        post.put("hl", "true");
        post.put("hl.q", originalQuery);
        post.put("hl.fl", CollectionSchema.description_txt + "," + CollectionSchema.h4_txt.getSolrFieldName() + "," + CollectionSchema.h3_txt.getSolrFieldName() + "," + CollectionSchema.h2_txt.getSolrFieldName() + "," + CollectionSchema.h1_txt.getSolrFieldName() + "," + CollectionSchema.text_t.getSolrFieldName());
        post.put("hl.alternateField", CollectionSchema.description_txt.getSolrFieldName());
        post.put("hl.simple.pre", "<b>");
        post.put("hl.simple.post", "</b>");
        post.put("hl.fragsize", Integer.toString(SearchEvent.SNIPPET_MAX_LENGTH));
        
        //String[] access = post.remove("access");
        //String[] entqr = post.remove("entqr");

        // add sites operator
        String[] site = post.remove("site"); // example: col1|col2
        if (site != null && site[0].length() > 0) {
            String origfq = post.get(CommonParams.FQ);
            String sitefq = QueryModifier.parseCollectionExpression(site[0]);
            post.put(CommonParams.FQ, origfq == null || origfq.length() == 0 ? sitefq : "(" + origfq + ") AND (" + sitefq + ")");
        }
        
        // get the embedded connector
        EmbeddedSolrConnector connector = sb.index.fulltext().getDefaultEmbeddedConnector();
        if (connector == null) return;

        // do the solr request
        SolrQueryRequest req = connector.request(post.toSolrParams(null));
        SolrQueryResponse response = null;
        Exception e = null;
        try {response = connector.query(req);} catch (final SolrException ee) {e = ee;}
        if (response != null) e = response.getException();
        if (e != null) {
            ConcurrentLog.logException(e);
            if (req != null) req.close();
            SolrRequestInfo.clearRequestInfo();
            return;
        }

        // set some context for the writer
        /*
        Map<Object,Object> context = req.getContext();
        context.put("ip", header.get("CLIENTIP", ""));
        context.put("client", "vsm_frontent");
        context.put("sort", sort.sort);
        context.put("site", site == null ? "" : site);
        context.put("access", access == null ? "p" : access[0]);
        context.put("entqr", entqr == null ? "3" : entqr[0]);
        */
        
        // write the result directly to the output stream
        Writer ow = new FastWriter(new OutputStreamWriter(out, UTF8.charset));
        try {
            responseWriter.write(ow, req, response);
            ow.flush();
        } catch (final IOException e1) {
        } finally {
            req.close();
            SolrRequestInfo.clearRequestInfo();
            try {ow.close();} catch (final IOException e1) {}
        }

        // log result
        Object rv = response.getValues().get("response");
        int matches = 0;
        if (rv != null && rv instanceof ResultContext) {
            matches = ((ResultContext) rv).docs.matches();
        } else if (rv != null && rv instanceof SolrDocumentList) {
            matches = (int) ((SolrDocumentList) rv).getNumFound();
        }
        AccessTracker.addToDump(originalQuery, Integer.toString(matches));
        ConcurrentLog.info("GSA Query", "results: " + matches + ", for query:" + post.toString());
    }
}
