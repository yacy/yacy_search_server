package net.yacy.cora.federate.solr.responsewriter;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;

import net.yacy.cora.util.CommonPattern;
import net.yacy.search.schema.CollectionSchema;

/**
 * this writer is supposed to be used to generate iframes. It generates links for the /api/snapshot.jpg servlet.
 */
public class SnapshotImagesReponseWriter implements QueryResponseWriter, SolrjResponseWriter  {

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
	 * Compute the root path as a relative URL prefix from the original servlet
	 * request URI if provided in the Solr request context, otherwise return a
	 * regular "/". Using relative URLs when possible makes deployment behind a
	 * reverse proxy more reliable and convenient as no URL rewriting is needed.
	 * 
	 * @param request the Solr request.
	 * @return the root context path to use as a prefix for resources URLs such as
	 *         stylesheets
	 */
	private String getRootPath(final SolrQueryRequest request) {
		String rootPath = "/";
		if (request != null) {
			final Map<Object, Object> context = request.getContext();
			if (context != null) {
				final Object requestUriObj = context.get("requestURI");
				if (requestUriObj instanceof String) {
					String servletRequestUri = (String) requestUriObj;
					if (servletRequestUri.startsWith("/")) {
						servletRequestUri = servletRequestUri.substring(1);

						final String[] pathParts = CommonPattern.SLASH.split(servletRequestUri);
						if (pathParts.length > 1) {
							final StringBuilder sb = new StringBuilder();
							for (int i = 1; i < pathParts.length; i++) {
								sb.append("../");
							}
							rootPath = sb.toString();
						}
					}
				}
			}
		}
		return rootPath;
	}
    
    /**
     * Append the response HTML head to the writer.
     * @param writer an open output writer. Must not be null.
     * @param request the Solr request
     * @throws IOException when a write error occurred
     */
	private void writeHtmlHead(final Writer writer, final SolrQueryRequest request) throws IOException {
		final String rootPath = getRootPath(request);
		
		writer.write("<!DOCTYPE html>\n");
		writer.write("<html lang=\"en\">");
		writer.write("<head>\n");
		writer.write("<meta charset=\"UTF-8\">");
		writer.write("<title>Documents snapshots</title>\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"all\" href=\"" + rootPath + "env/base.css\" />\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"" + rootPath + "env/style.css\" />\n");
		writer.write("</head>\n");
	}

    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
            final NamedList<?> values = rsp.getValues();
            assert values.get("responseHeader") != null;
            assert values.get("response") != null;

            writeHtmlHead(writer, request);
            writer.write("<body id=\"SnapshotImagesReponseWriter\">\n");
            final SolrParams originalParams = request.getOriginalParams();
            
            final int width = originalParams != null ? originalParams.getInt("width", DEFAULT_WIDTH) : DEFAULT_WIDTH;
            final int height = originalParams != null ? originalParams.getInt("height", DEFAULT_HEIGTH) : DEFAULT_HEIGTH;
            
            final DocList response = ((ResultContext) values.get("response")).getDocList();
            final int sz = response.size();
            if (sz > 0) {
                final SolrIndexSearcher searcher = request.getSearcher();
                final DocIterator iterator = response.iterator();
                while (iterator.hasNext()) {
                    final int id = iterator.nextDoc();
                    final Document doc = searcher.doc(id, DEFAULT_FIELD_LIST);
                    final IndexableField docId = doc.getField(CollectionSchema.id.getSolrFieldName());
                    final IndexableField docSku = doc.getField(CollectionSchema.sku.getSolrFieldName());
                    if(docId != null && docSku != null) {
                    	writeDoc(writer, width, height, docId.stringValue(), docSku.stringValue());
                    }
                }
            }
            
            writer.write("</body></html>\n");
        
    }

    /**
     * Process a document and append its representation to the output writer.
     * @param writer an open ouput writer. Must not be null.
     * @param width the width of the snapshot image to render
     * @param height the height of the snapshot image to render
     * @param docId the document id (URL hash).
	 * @param docUrl the document URL.
     * @throws IOException when a write error occurred
     */
	private void writeDoc(final Writer writer, final int width, final int height, final String docId, final String docUrl)
			throws IOException {
		if(docId != null && docUrl != null) {
			writer.write("<a href=\"");
			writer.write(docUrl);
			writer.write("\" class=\"forceNoExternalIcon\"><img width=\"");
			writer.write(String.valueOf(width));
			writer.write("\" height=\"");
			writer.write(String.valueOf(height));
			writer.write("\" src=\"/api/snapshot.jpg?urlhash=");
			writer.write(docId);
			writer.write("&amp;width=");
			writer.write(String.valueOf(width));
			writer.write("&amp;height=");
			writer.write(String.valueOf(height));
			writer.write("\" alt=\"");
			writer.write(docUrl);
			writer.write("\"></a>\n");
		}
	}
    
	@Override
	public void write(final Writer writer, final SolrQueryRequest request, final String coreName,
			final QueryResponse rsp) throws IOException {

		writeHtmlHead(writer, request);
		writer.write("<body id=\"SnapshotImagesReponseWriter\">\n");
		final SolrParams originalParams = request.getOriginalParams();

        final int width = originalParams != null ? originalParams.getInt("width", DEFAULT_WIDTH) : DEFAULT_WIDTH;
        final int height = originalParams != null ? originalParams.getInt("height", DEFAULT_HEIGTH) : DEFAULT_HEIGTH;

		final SolrDocumentList docList = rsp.getResults();
		final int sz = docList.size();
		if (sz > 0) {
			final Iterator<SolrDocument> iterator = docList.iterator();
			while (iterator.hasNext()) {
				final SolrDocument doc = iterator.next();
				final Object docId = doc.getFieldValue(CollectionSchema.id.getSolrFieldName());
				final Object docSku = doc.getFieldValue(CollectionSchema.sku.getSolrFieldName());
				if (docId != null && docSku != null) {
					writeDoc(writer, width, height, docId.toString(), docSku.toString());
				}
			}
		}

		writer.write("</body></html>\n");
	}

}
