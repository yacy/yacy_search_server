package net.yacy.cora.federate.solr.responsewriter;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;

import net.yacy.search.schema.CollectionSchema;

/**
 * this writer is supposed to be used to generate iframes. It generates links for the /api/snapshot.jpg servlet.
 */
public class SnapshotImagesReponseWriter implements QueryResponseWriter, EmbeddedSolrResponseWriter  {

    private static final Set<String> DEFAULT_FIELD_LIST = new HashSet<>();
    
    static {
        DEFAULT_FIELD_LIST.add(CollectionSchema.id.getSolrFieldName());
        DEFAULT_FIELD_LIST.add(CollectionSchema.sku.getSolrFieldName());
    }
    
    /** Default width for each snapshot image */
    private static final int DEFAULT_WIDTH = 256;
    
    /** Default height for each snapshot image */
    private static final int DEFAULT_HEIGTH = 256;
    
    public SnapshotImagesReponseWriter() {
        super();
    }
    
    @Override
    public String getContentType(SolrQueryRequest arg0, SolrQueryResponse arg1) {
        return "text/html";
    }

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList n) {
    }
    
    /**
     * Append the response HTML head to the writer.
     * @param writer an open output writer. Must not be null.
     * @throws IOException when a write error occurred
     */
	private void writeHtmlHead(final Writer writer) throws IOException {
		writer.write("<!DOCTYPE html>\n");
		writer.write("<html lang=\"en\">");
		writer.write("<head>\n");
		writer.write("<meta charset=\"UTF-8\">");
		writer.write("<title>Documents snapshots</title>\n");
		writer.write("</head>\n");
	}

    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
            NamedList<?> values = rsp.getValues();
            assert values.get("responseHeader") != null;
            assert values.get("response") != null;

            writeHtmlHead(writer);
            writer.write("<body>\n");
            final SolrParams originalParams = request.getOriginalParams();
            
            final int width = originalParams.getInt("width", DEFAULT_WIDTH);
            final int heigth = originalParams.getInt("height", DEFAULT_HEIGTH);
            
            DocList response = ((ResultContext) values.get("response")).getDocList();
            final int sz = response.size();
            if (sz > 0) {
                SolrIndexSearcher searcher = request.getSearcher();
                DocIterator iterator = response.iterator();
                while (iterator.hasNext()) {
                    int id = iterator.nextDoc();
                    Document doc = searcher.doc(id, DEFAULT_FIELD_LIST);
                    String urlhash = doc.getField(CollectionSchema.id.getSolrFieldName()).stringValue();
                    String url = doc.getField(CollectionSchema.sku.getSolrFieldName()).stringValue();
                    writer.write("<a href=\"");
                    writer.write(url);
                    writer.write("\"><img src=\"/api/snapshot.jpg?urlhash=");
                    writer.write(urlhash);
                    writer.write("&amp;width=");
                    writer.write(String.valueOf(width));
                    writer.write("&amp;height=");
                    writer.write(String.valueOf(heigth));
                    writer.write("\" alt=\"");
                    writer.write(url);
                    writer.write("\"></a>\n");
                }
            }
            
            writer.write("</body></html>\n");
        
    }

}
