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
import net.yacy.search.solr.EmbeddedSolrConnector;
import net.yacy.search.solr.SolrServlet;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.FastWriter;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;

import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

// try
// http://localhost:8090/solr/select?q=*:*&start=0&rows=10&indent=on

public class select {

    private static SolrServlet solrServlet = new SolrServlet();
    private final static Map<String, QueryResponseWriter> RESPONSE_WRITER = new HashMap<String, QueryResponseWriter>();
    static {
        try {solrServlet.init(null);} catch (ServletException e) {}
        RESPONSE_WRITER.putAll(SolrCore.DEFAULT_RESPONSE_WRITERS);
        RESPONSE_WRITER.put("exml", new EnhancedXMLResponseWriter());
        RESPONSE_WRITER.put("rss", new OpensearchResponseWriter()); //try http://localhost:8090/solr/select?wt=rss&q=olympia
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

        // get the embedded connector
        EmbeddedSolrConnector connector = (EmbeddedSolrConnector) sb.index.getLocalSolr();
        if (connector == null) return null;

        // check post
        if (post == null) return null;

        // rename post fields according to result style
        if (!post.containsKey("q")) post.put("q", post.remove("query")); // sru patch
        if (!post.containsKey("start")) post.put("start", post.remove("startRecord")); // sru patch
        if (!post.containsKey("rows")) post.put("rows", post.remove("maximumRecords")); // sru patch

        // check if all required post fields are there
        if (!post.containsKey("df")) post.put("df", "text_t"); // set default field to all fields
        if (!post.containsKey("start")) post.put("start", "0"); // set default start item
        if (!post.containsKey("rows")) post.put("rows", "10"); // set default number of search results

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
        String wt = post.get("wt", "xml"); // maybe use /solr/select?q=*:*&start=0&rows=10&wt=exml
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
