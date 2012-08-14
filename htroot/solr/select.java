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

import javax.servlet.ServletException;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.solr.EnhancedXMLResponseWriter;
import net.yacy.cora.services.federated.solr.OpensearchResponseWriter;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.index.YaCySchema;
import net.yacy.search.solr.EmbeddedSolrConnector;
import net.yacy.search.solr.SolrServlet;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.FastWriter;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.XSLTResponseWriter;

import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

// try
// http://localhost:8090/solr/select?q=*:*&start=0&rows=10&indent=on

/**
 *
 * http://wiki.apache.org/solr/SolrQuerySyntax
 *
 */
public class select {

    private static SolrServlet solrServlet = new SolrServlet();
    private final static Map<String, QueryResponseWriter> RESPONSE_WRITER = new HashMap<String, QueryResponseWriter>();
    static {
        try {solrServlet.init(null);} catch (ServletException e) {}
        RESPONSE_WRITER.putAll(SolrCore.DEFAULT_RESPONSE_WRITERS);
        XSLTResponseWriter xsltWriter = new XSLTResponseWriter();
        @SuppressWarnings("rawtypes")
        NamedList initArgs = new NamedList();
        xsltWriter.init(initArgs);
        RESPONSE_WRITER.put("xslt", xsltWriter); // try i.e. http://localhost:8090/solr/select?q=*:*&start=0&rows=10&wt=xslt&tr=json.xsl
        RESPONSE_WRITER.put("exml", new EnhancedXMLResponseWriter());
        RESPONSE_WRITER.put("rss", new OpensearchResponseWriter()); //try http://localhost:8090/solr/select?wt=rss&q=olympia
    }

    /**
     * get the right mime type for this streamed result page
     * @param header
     * @param post
     * @param env
     * @return
     */
    public static String mime(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        String wt = post.get(CommonParams.WT, "xml");
        if (wt == null || wt.length() == 0 || "xml".equals(wt) || "exml".equals(wt)) return "text/xml";
        if ("xslt".equals(wt)) {
            String tr = post.get("tr","");
            if (tr.indexOf("json") >= 0) return "application/json";
        }
        if ("rss".equals(wt)) return "application/rss+xml";
        if ("json".equals(wt)) return "application/json";
        if ("python".equals(wt)) return "text/html";
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
    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env, final OutputStream out) {

        // this uses the methods in the jetty servlet environment and can be removed if jetty in implemented
        Switchboard sb = (Switchboard) env;

        // check if user is allowed to search (can be switched in /ConfigPortal.html)
        final boolean searchAllowed = sb.getConfigBool("publicSearchpage", true) || sb.verifyAuthentication(header);
        if (!searchAllowed) return null;

        // check post
        if (post == null) return null;

        // rename post fields according to result style
        if (!post.containsKey(CommonParams.Q)) post.put(CommonParams.Q, post.remove("query")); // sru patch
        if (!post.containsKey(CommonParams.START)) post.put(CommonParams.START, post.remove("startRecord")); // sru patch
        if (!post.containsKey(CommonParams.ROWS)) post.put(CommonParams.ROWS, post.remove("maximumRecords")); // sru patch

        // check if all required post fields are there
        if (!post.containsKey(CommonParams.DF)) post.put(CommonParams.DF, YaCySchema.text_t.name()); // set default field to all fields
        if (!post.containsKey(CommonParams.START)) post.put(CommonParams.START, "0"); // set default start item
        if (!post.containsKey(CommonParams.ROWS)) post.put(CommonParams.ROWS, "10"); // set default number of search results

        // get the embedded connector
        EmbeddedSolrConnector connector = (EmbeddedSolrConnector) sb.index.getLocalSolr();
        if (connector == null) return null;

        // do the solr request
        SolrQueryRequest req = connector.request(post.toSolrParams());
        SolrQueryResponse response = null;
        Exception e = null;
        try {response = connector.query(req);} catch (SolrException ee) {e = ee;}
        if (response != null) e = response.getException();
        if (e != null) {
            Log.logException(e);
            return null;
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

        // write the result directly to the output stream
        Writer ow = new FastWriter(new OutputStreamWriter(out, UTF8.charset));
        try {
            responseWriter.write(ow, req, response);
            ow.flush();
        } catch (IOException e1) {
        } finally {
            req.close();
            try {ow.close();} catch (IOException e1) {}
        }

        return null;
    }
}
