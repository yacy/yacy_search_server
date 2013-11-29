/**
 *  select
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 12.08.2012 at http://yacy.net
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.federate.solr.Ranking;
import net.yacy.cora.federate.solr.connector.EmbeddedSolrConnector;
import net.yacy.cora.federate.solr.responsewriter.EnhancedXMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.GSAResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.GrepHTMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.HTMLResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.YJsonResponseWriter;
import net.yacy.cora.federate.solr.responsewriter.OpensearchResponseWriter;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.AccessTracker;
import net.yacy.search.query.QueryGoal;
import net.yacy.search.query.QueryModifier;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.response.BinaryResponseWriter;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.XSLTResponseWriter;
import org.apache.solr.util.FastWriter;


// try
// http://localhost:8090/solr/select?q=*:*&start=0&rows=10&indent=on

/**
 * this is a standard solr search result formatter as defined in
 * http://wiki.apache.org/solr/SolrQuerySyntax
 */
public class select {

    private final static Map<String, QueryResponseWriter> RESPONSE_WRITER = new HashMap<String, QueryResponseWriter>();
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

    /**
     * get the right mime type for this streamed result page
     * @param header
     * @param post
     * @param env
     * @return
     */
    public static String mime(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        String wt = post == null ? "xml" : post.get(CommonParams.WT, "xml");
        if (wt == null || wt.length() == 0 || "xml".equals(wt) || "exml".equals(wt)) return "text/xml";
        if ("xslt".equals(wt)) {
            String tr = post == null ? "" : post.get("tr","");
            if (tr.indexOf("json") >= 0) return "application/json";
        }
        if ("rss".equals(wt)) return "application/rss+xml";
        if ("exml".equals(wt)) return "application/rss+xml";
        if ("json".equals(wt)) return "application/json";
        if ("yjson".equals(wt)) return "application/json";
        if ("html".equals(wt) || "grephtml".equals(wt) || "python".equals(wt)) return "text/html";
        if ("php".equals(wt) || "phps".equals(wt)) return "application/x-httpd-php";
        if ("ruby".equals(wt)) return "text/html";
        if ("raw".equals(wt)) return "application/octet-stream";
        if ("javabin".equals(wt)) return "application/octet-stream";
        if ("csv".equals(wt)) return "text/csv";
        return "text/xml";
    }

    /**
     * a query to solr, for documentation of parameters see:
     * http://lucene.apache.org/solr/api-3_6_0/doc-files/tutorial.html
     * and
     * http://wiki.apache.org/solr/SolrQuerySyntax
     * @param header
     * @param post
     * @param env
     * @param out
     * @return
     */
    public static serverObjects respond(final RequestHeader header, serverObjects post, final serverSwitch env, final OutputStream out) {

        // this uses the methods in the jetty servlet environment and can be removed if jetty in implemented
        Switchboard sb = (Switchboard) env;

        // remember the peer contact for peer statistics
        final String clientip = header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, "<unknown>"); // read an artificial header addendum
        final String userAgent = header.get(HeaderFramework.USER_AGENT, "<unknown>");
        sb.peers.peerActions.setUserAgent(clientip, userAgent);

        // check if user is allowed to search (can be switched in /ConfigPortal.html)
        boolean authenticated = sb.adminAuthenticated(header) >= 2;
        final boolean searchAllowed = authenticated || sb.getConfigBool("publicSearchpage", true);
        if (!searchAllowed) return null;

        // check post
        if (post == null) {post = new serverObjects(); post.put(CommonParams.Q, ""); post.put(CommonParams.ROWS, "0");}
        if (post.size() > 100) {
            ConcurrentLog.warn("select", "rejected bad-formed search request with " + post.size() + " properties from " + header.refererHost());
            return null; // prevent the worst hacks here...
        }
        sb.intermissionAllThreads(3000); // tell all threads to do nothing for a specific time
        
        // get the ranking profile id
        int profileNr = post.getInt("profileNr", 0);
        
        // rename post fields according to result style
        if (!post.containsKey(CommonParams.Q) && post.containsKey("query")) {
            String querystring = post.get("query", "");
            post.remove("query");
            QueryModifier modifier = new QueryModifier();
            querystring = modifier.parse(querystring);
            modifier.apply(post);
            QueryGoal qg = new QueryGoal(querystring, querystring);
            StringBuilder solrQ = qg.collectionTextQueryString(sb.index.fulltext().getDefaultConfiguration(), profileNr, false);
            post.put(CommonParams.Q, solrQ.toString()); // sru patch
        }
        String q = post.get(CommonParams.Q, "");
        if (!post.containsKey(CommonParams.START)) post.put(CommonParams.START, post.remove("startRecord", 0)); // sru patch
        if (!post.containsKey(CommonParams.ROWS)) post.put(CommonParams.ROWS, post.remove("maximumRecords", 10)); // sru patch
        post.put(CommonParams.ROWS, Math.min(post.getInt(CommonParams.ROWS, 10), (authenticated) ? 100000000 : 100));
        
        // set ranking according to profile number if ranking attributes are not given in the request
        if (!post.containsKey("sort") && !post.containsKey("bq") && !post.containsKey("bf") && !post.containsKey("boost")) {
            if (!post.containsKey("defType")) post.put("defType", "edismax");        
            Ranking ranking = sb.index.fulltext().getDefaultConfiguration().getRanking(profileNr);
            String bq = ranking.getBoostQuery();
            String bf = ranking.getBoostFunction();
            if (bq.length() > 0) post.put("bq", bq);
            if (bf.length() > 0) post.put("boost", bf); // a boost function extension, see http://wiki.apache.org/solr/ExtendedDisMax#bf_.28Boost_Function.2C_additive.29
        }
        
        // get a response writer for the result
        String wt = post.get(CommonParams.WT, "xml"); // maybe use /solr/select?q=*:*&start=0&rows=10&wt=exml
        QueryResponseWriter responseWriter = RESPONSE_WRITER.get(wt);
        if (responseWriter == null) return null;
        if (responseWriter instanceof OpensearchResponseWriter) {
            // set the title every time, it is possible that it has changed
            final String promoteSearchPageGreeting =
                            (env.getConfigBool(SwitchboardConstants.GREETING_NETWORK_NAME, false)) ? env.getConfig(
                                "network.unit.description",
                                "") : env.getConfig(SwitchboardConstants.GREETING, "");
            ((OpensearchResponseWriter) responseWriter).setTitle(promoteSearchPageGreeting);
        }
        
        // if this is a call to YaCys special search formats, enhance the query with field assignments
        if ((responseWriter instanceof YJsonResponseWriter || responseWriter instanceof OpensearchResponseWriter) && "true".equals(post.get("hl", "true"))) {
            // add options for snippet generation
            if (!post.containsKey("hl.q")) post.put("hl.q", q);
            if (!post.containsKey("hl.fl")) post.put("hl.fl", CollectionSchema.h1_txt.getSolrFieldName() + "," + CollectionSchema.h2_txt.getSolrFieldName() + "," + CollectionSchema.text_t.getSolrFieldName());
            if (!post.containsKey("hl.alternateField")) post.put("hl.alternateField", CollectionSchema.description_txt.getSolrFieldName());
            if (!post.containsKey("hl.simple.pre")) post.put("hl.simple.pre", "<b>");
            if (!post.containsKey("hl.simple.post")) post.put("hl.simple.post", "</b>");
            if (!post.containsKey("hl.fragsize")) post.put("hl.fragsize", Integer.toString(SearchEvent.SNIPPET_MAX_LENGTH));
        }

        // get the embedded connector
        boolean defaultConnector = post == null || post.get("core", CollectionSchema.CORE_NAME).equals(CollectionSchema.CORE_NAME);
        EmbeddedSolrConnector connector = defaultConnector ? sb.index.fulltext().getDefaultEmbeddedConnector() : sb.index.fulltext().getEmbeddedConnector(WebgraphSchema.CORE_NAME);
        if (connector == null) return null;

        // do the solr request, generate facets if we use a special YaCy format
        SolrParams params = post.toSolrParams(/*responseWriter instanceof JsonResponseWriter ? new YaCySchema[]{YaCySchema.host_s, YaCySchema.url_file_ext_s, YaCySchema.url_protocol_s} :*/ null);
        SolrQueryRequest req = connector.request(params);
        Writer ow = null;
        SolrQueryResponse response = null;
        try {
	        response = connector.query(req);
	        if (response != null) {
	        	Exception e = response.getException();
	        	if (e != null) {
	        		ConcurrentLog.logException(e);
	        	} else {
		        	
			        // write the result directly to the output stream
		        	if (responseWriter instanceof BinaryResponseWriter) {
			            ((BinaryResponseWriter) responseWriter).write(out, req, response);
			        } else {
			            ow = new FastWriter(new OutputStreamWriter(out, UTF8.charset));
			            responseWriter.write(ow, req, response);
		                ow.flush();
			        }
	        	}
	        }
        } catch (final SolrException e) {
        	ConcurrentLog.logException(e);
        } catch (final IOException e1) {
        } finally {
        	req.close();
            SolrRequestInfo.clearRequestInfo();
            if (ow != null) try {ow.close();} catch (final IOException e1) {}
        }
        if (response == null) return null;

        // log result
        Object rv = response.getValues().get("response");
        int matches = 0;
        if (rv != null && rv instanceof ResultContext) {
            matches = ((ResultContext) rv).docs.matches();
        } else if (rv != null && rv instanceof SolrDocumentList) {
            matches = (int) ((SolrDocumentList) rv).getNumFound();
        }
        AccessTracker.addToDump(q, Integer.toString(matches));
        ConcurrentLog.info("SOLR Query", "results: " + matches + ", for query:" + post.toString());

        return null;
    }
}
