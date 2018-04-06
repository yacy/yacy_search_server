package net.yacy.cora.federate.solr.responsewriter;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import net.yacy.search.schema.CollectionSchema;

import org.apache.lucene.document.Document;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;

/**
 * this writer is supposed to be used to generate iframes. It generates links for the /api/snapshot.jpg servlet.
 */
public class SnapshotImagesReponseWriter implements QueryResponseWriter, EmbeddedSolrResponseWriter  {

    private static final Set<String> DEFAULT_FIELD_LIST = new HashSet<>();
    
    static {
        DEFAULT_FIELD_LIST.add(CollectionSchema.id.getSolrFieldName());
        DEFAULT_FIELD_LIST.add(CollectionSchema.sku.getSolrFieldName());
    }
    
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

    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
            NamedList<?> values = rsp.getValues();
            assert values.get("responseHeader") != null;
            assert values.get("response") != null;

            writer.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
            writer.write("<head></head><body>\n");
            NamedList<Object> paramsList = request.getOriginalParams().toNamedList();
            paramsList.remove("wt");
            
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
                    writer.write("<a href=\"" + url + "\"><img src=\"/api/snapshot.jpg?urlhash=" + urlhash + "&amp;width=256&amp;height=256\" alt=\"" + url + "\"></a>\n");
                }
            }
            
            writer.write("</body></html>\n");
        
    }

}
