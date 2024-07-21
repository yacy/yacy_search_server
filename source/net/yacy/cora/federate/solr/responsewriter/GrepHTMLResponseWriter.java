/**
 *  GrepHTMLResponseWriter
 *  Copyright 2013 by Michael Peter Christen
 *  First released 09.06.2013 at https://yacy.net
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

package net.yacy.cora.federate.solr.responsewriter;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.XML;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.ReturnFields;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SolrReturnFields;

import net.yacy.document.SentenceReader;
import net.yacy.search.schema.CollectionSchema;

/**
 * this response writer shows a list of documents with the lines containing matches
 * of the search request in 'grep-style', which means it is like doing a grep on a set
 * of files. Within the result list, the document is splitted into the sentences of the
 * text part and each sentence is shown as separate line. grep attributes can be used to
 * show leading and trainling lines.
 */
public class GrepHTMLResponseWriter implements QueryResponseWriter, SolrjResponseWriter {

    private static final Set<String> DEFAULT_FIELD_LIST = new HashSet<>();
    private static final Pattern dqp = Pattern.compile("\"");
    static {
        DEFAULT_FIELD_LIST.add(CollectionSchema.id.getSolrFieldName());
        DEFAULT_FIELD_LIST.add(CollectionSchema.sku.getSolrFieldName());
        DEFAULT_FIELD_LIST.add(CollectionSchema.title.getSolrFieldName());
        DEFAULT_FIELD_LIST.add(CollectionSchema.text_t.getSolrFieldName());
    }
    
    @Override
    public String getContentType(final SolrQueryRequest request, final SolrQueryResponse response) {
        return "text/html";
    }

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList n) {
    }

    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
        writeHtmlHead(writer);
        
        final SolrParams params = request.getOriginalParams();
        
        final String query = getQueryParam(params);
        final String grep = getGrepParam(params, query);

        
        final Object responseObj = rsp.getResponse();
        
        if(responseObj instanceof SolrDocumentList) {
			/*
			 * The response object can be a SolrDocumentList when the response is partial,
			 * for example when the allowed processing time has been exceeded
			 */
        	final SolrDocumentList docList = ((SolrDocumentList)responseObj);
        	
            writeSolrDocumentList(writer, params, query, grep, docList);
        	
        } else if(responseObj instanceof ResultContext) {
        	/* Regular response object */
        	final DocList documents = ((ResultContext)responseObj).getDocList();
        	
            final int sz = documents.size();
            if (sz > 0) {
                final SolrIndexSearcher searcher = request.getSearcher();
                final DocIterator iterator = documents.iterator();
                final IndexSchema schema = request.getSchema();
                writeTitleAndHeadeing(writer, grep, query);
                writeApiLink(writer, params);
                for (int i = 0; i < sz; i++) {
                    int id = iterator.nextDoc();
                    final Document doc = searcher.doc(id, DEFAULT_FIELD_LIST);
                    final LinkedHashMap<String, String> tdoc = HTMLResponseWriter.translateDoc(schema, doc);
                    final String sku = tdoc.get(CollectionSchema.sku.getSolrFieldName());
                    final String title = tdoc.get(CollectionSchema.title.getSolrFieldName());
                    final String text = tdoc.get(CollectionSchema.text_t.getSolrFieldName());

                    final ArrayList<String> sentences = extractSentences(title, text);
                    writeDoc(writer, sku, sentences, grep);
                }
            } else {
                writer.write("<title>No Document Found</title>\n</head><body>\n");
            }
        } else {
        	writer.write("<title>Unable to process Solr response</title>\n</head><body>\n");
        }
        
        writer.write("</body></html>\n");
    }

    /**
     * Process the solr documents list and append a representation to the output writer.
     * @param writer an open output writer. Must not be null.
     * @param params the original Solr parameters
     * @param query the query parameter value
     * @param grep the grep parameter value
     * @param docList the solr documents list
     * @throws IOException when a write error occurred
     */
	private void writeSolrDocumentList(final Writer writer, final SolrParams params, final String query,
			final String grep, final SolrDocumentList docList) throws IOException {
		if (docList == null || docList.isEmpty()) {
		    writer.write("<title>No Document Found</title>\n</head><body>\n");
		} else {
		    writeTitleAndHeadeing(writer, grep, query);
		    writeApiLink(writer, params);
		    
		    final ReturnFields fieldsToReturn = new SolrReturnFields();
		    for (final SolrDocument doc : docList) {
		        final LinkedHashMap<String, String> tdoc = HTMLResponseWriter.translateDoc(doc, fieldsToReturn);
		        final String sku = tdoc.get(CollectionSchema.sku.getSolrFieldName());
		        final String title = tdoc.get(CollectionSchema.title.getSolrFieldName());
		        final String text = tdoc.get(CollectionSchema.text_t.getSolrFieldName());

		        final ArrayList<String> sentences = extractSentences(title, text);
		        writeDoc(writer, sku, sentences, grep);
		    }
		}
	}

    /**
     * Write the html header beginning
     * @param writer an open output writer
     * @throws IOException when a write error occurred
     */
	private void writeHtmlHead(final Writer writer) throws IOException {
		writer.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n");
        writer.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"all\" href=\"../env/base.css\" />\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"../env/style.css\" />\n");
	}

	/**
	 * @param params the original request parameters. Must not be null.
	 * @param query the query parameter value
	 * @return the grep parameter value
	 */
	private String getGrepParam(final SolrParams params, String query) {
		String grep = params.get("grep");
        if (grep == null) {
        	if(query.length() > 0) {
        		grep = query;
        	} else {
        		grep = "";
        	}
        }
        if (grep.length() > 0) {
            if (grep.charAt(0) == '"') {
            	grep = grep.substring(1);
            }
            if (grep.charAt(grep.length() - 1) == '"') {
            	grep = grep.substring(0, grep.length() - 1);
            }
        }
		return grep;
	}

	/**
	 * @param params the original request parameters. Must not be null.
	 * @return the query parameter value
	 */
	private String getQueryParam(final SolrParams params) {
		final String q = params.get(CommonParams.Q, "");
        String query = "";
        int p = q.indexOf(':');
        if (p >= 0) {
            int r = q.charAt(p + 1) == '"' ? q.indexOf(p + 2, '"') : q.indexOf(' ');
            if (r < 0) {
            	r = q.length();
            }
            query = q.substring(p + 1, r);
            if (query.length() > 0) {
                if (query.charAt(0) == '"') {
                	query = query.substring(1);
                }
                if (query.charAt(query.length() - 1) == '"') {
                	query = query.substring(0, query.length() - 1);
                }
            }
        }
		return query;
	}

    /**
     * Append the response title and level 1 html heading
     * @param writer an open output writer. Must not be null.
     * @param grep the grep phrase
     * @param query the search query
     * @throws IOException when a write error occurred
     */
	private void writeTitleAndHeadeing(final Writer writer, final String grep, final String query) throws IOException {
		final String h1 = "Document Grep for query \"" + query + "\" and grep phrase \"" + grep + "\"";
		writer.write("<title>" + h1 + "</title>\n</head><body>\n<h1>" + h1 + "</h1>\n");
	}

	/**
	 * Append a link to the related Solr api
	 * @param writer an open output writer. Must not be null.
	 * @param solrParams the original request parameters. Must not be null.
	 * @throws IOException when a write error occurred
	 */
	private void writeApiLink(final Writer writer, final SolrParams solrParams) throws IOException {
        final NamedList<Object> paramsList = solrParams.toNamedList();
        paramsList.remove("wt");
        @SuppressWarnings("deprecation")
        String xmlquery = dqp.matcher("select?" + SolrParams.toSolrParams(paramsList).toString()).replaceAll("%22");
        
		writer.write("<div id=\"api\"><a href=\"" + xmlquery + "\"><img src=\"../env/grafics/api.png\" width=\"60\" height=\"40\" alt=\"API\" /></a>\n");
		writer.write("<span>This search result can also be retrieved as XML. Click the API icon to see an example call to the search rss API.</span></div>\n");
	}

	/**
	 * @param title
	 * @param text
	 * @return a list of sentences extracted from the given document text and title 
	 */
	private ArrayList<String> extractSentences(final String title, final String text) {
		final ArrayList<String> sentences = new ArrayList<>();
		if (title != null) {
			sentences.add(title);
		}
		if(text != null) {
			final SentenceReader sr = new SentenceReader(text);
			StringBuilder line;
			while (sr.hasNext()) {
				line = sr.next();
				if (line.length() > 0) {
					sentences.add(line.toString());
				}
			}
		}
		return sentences;
	}
    
    @Override
    public void write(Writer writer, SolrQueryRequest request, String coreName, QueryResponse rsp) throws IOException {
        writeHtmlHead(writer);
        
        final SolrParams params = request.getOriginalParams();
        
        final String query = getQueryParam(params);
        final String grep = getGrepParam(params, query);

        writeSolrDocumentList(writer, params, query, grep, rsp.getResults());
        	
        
        writer.write("</body></html>\n");
    	
    }

    private static final void writeDoc(Writer writer, String url, ArrayList<String> sentences, String grep) throws IOException {
        writer.write("<form name=\"yacydoc" + url + "\" method=\"post\" action=\"#\" enctype=\"multipart/form-data\" accept-charset=\"UTF-8\">\n");
        writer.write("<fieldset>\n");
        writer.write("<h1><a href=\"" + url + "\">" + url + "</a></h1>\n");
        writer.write("<dl>\n");
        int c = 0;
        for (String line: sentences) {
            if (grep != null && grep.length() > 0 && line.indexOf(grep) < 0) continue;
            writer.write("<dt>");
            if (c++ == 0) {
                if (grep == null || grep.length() == 0) writer.write("all lines in document"); else {writer.write("matches for grep phrase \"");writer.write(grep);writer.write("\"");}
            }
            writer.write("</dt>");
            writedd(writer, line, grep);
        }
        writer.write("</dl>\n");
        writer.write("</fieldset>\n");
        writer.write("</form>\n");
    }
    
    private static void writedd(Writer writer, String line, String grep) throws IOException {
        writer.write("<dd><a href=\"select?q=text_t:%22");
        XML.escapeAttributeValue(line, writer);
        writer.write("%22&rows=100&grep=%22");
        XML.escapeAttributeValue(grep, writer);
        writer.write("%22&wt=grephtml\">");
        XML.escapeAttributeValue(line, writer);
        writer.write("</a></dd>\n");
    }

}
