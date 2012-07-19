import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.servlet.ServletException;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.logging.Log;
import net.yacy.search.Switchboard;
import net.yacy.search.solr.EmbeddedSolrConnector;
import net.yacy.search.solr.SolrServlet;

import org.apache.solr.common.util.FastWriter;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.XMLResponseWriter;

import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class select {

    private static SolrServlet solrServlet = new SolrServlet();
    private static final QueryResponseWriter responseWriter = new XMLResponseWriter();

    static {
        try {solrServlet.init(null);} catch (ServletException e) {}
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
    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env, final OutputStream out) {

        // this uses the methods in the jetty servlet environment and can be removed if jetty in implemented
        Switchboard sb = (Switchboard) env;
        EmbeddedSolrConnector connector = (EmbeddedSolrConnector) sb.index.getLocalSolr();
        if (connector == null) return null;
        if (post == null) return null;
        if (!post.containsKey("df")) post.put("df", "text_t"); // set default field to all fields
        SolrQueryRequest req = connector.request(post.toSolrParams());
        SolrQueryResponse response = connector.query(req);
        Exception e = response.getException();
        if (e != null) {
            Log.logException(e);
            return null;
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
