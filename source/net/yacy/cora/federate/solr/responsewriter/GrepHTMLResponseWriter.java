/**
 *  GrepHTMLResponseWriter
 *  Copyright 2013 by Michael Peter Christen
 *  First released 09.06.2013 at http://yacy.net
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

import net.yacy.document.SentenceReader;
import net.yacy.search.schema.CollectionSchema;

import org.apache.lucene.document.Document;
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
import org.apache.solr.search.SolrIndexSearcher;

/**
 * this response writer shows a list of documents with the lines containing matches
 * of the search request in 'grep-style', which means it is like doing a grep on a set
 * of files. Within the result list, the document is splitted into the sentences of the
 * text part and each sentence is shown as separate line. grep attributes can be used to
 * show leading and trainling lines.
 */
public class GrepHTMLResponseWriter implements QueryResponseWriter {

    private static final Set<String> DEFAULT_FIELD_LIST = new HashSet<String>();
    private static final Pattern dqp = Pattern.compile("\"");
    static {
        DEFAULT_FIELD_LIST.add(CollectionSchema.id.getSolrFieldName());
        DEFAULT_FIELD_LIST.add(CollectionSchema.sku.getSolrFieldName());
        DEFAULT_FIELD_LIST.add(CollectionSchema.title.getSolrFieldName());
        DEFAULT_FIELD_LIST.add(CollectionSchema.text_t.getSolrFieldName());
    }
    
    public GrepHTMLResponseWriter() {
        super();
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
        NamedList<?> values = rsp.getValues();
        assert values.get("responseHeader") != null;
        assert values.get("response") != null;

        writer.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"all\" href=\"/env/base.css\" />\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"/env/style.css\" />\n");
        SolrParams params = request.getOriginalParams();
        String grep = params.get("grep");
        String query = "";
        String q = params.get("q"); if (q == null) q = "";
        int p = q.indexOf(':');
        if (p >= 0) {
            int r = q.charAt(p + 1) == '"' ? q.indexOf(p + 2, '"') : q.indexOf(' ');
            if (r < 0) r = q.length();
            query = q.substring(p + 1, r);
            if (query.length() > 0) {
                if (query.charAt(0) == '"') query = query.substring(1);
                if (query.charAt(query.length() - 1) == '"') query = query.substring(0, query.length() - 1);
            }
        }
        if (grep == null && query.length() > 0) grep = query;
        if (grep.length() > 0) {
            if (grep.charAt(0) == '"') grep = grep.substring(1);
            if (grep.charAt(grep.length() - 1) == '"') grep = grep.substring(0, grep.length() - 1);
        }
        NamedList<Object> paramsList = params.toNamedList();
        paramsList.remove("wt");
        String xmlquery = dqp.matcher("/solr/select?" + SolrParams.toSolrParams(paramsList).toString()).replaceAll("%22");
        
        DocList response = ((ResultContext) values.get("response")).docs;
        final int sz = response.size();
        if (sz > 0) {
            SolrIndexSearcher searcher = request.getSearcher();
            DocIterator iterator = response.iterator();
            IndexSchema schema = request.getSchema();
            String h1 = "Document Grep for query \"" + query + "\" and grep phrase \"" + grep + "\"";
            writer.write("<title>" + h1 + "</title>\n</head><body>\n<h1>" + h1 + "</h1>\n");
            writer.write("<div id=\"api\"><a href=\"" + xmlquery + "\"><img src=\"../env/grafics/api.png\" width=\"60\" height=\"40\" alt=\"API\" /></a>\n");
            writer.write("<span>This search result can also be retrieved as XML. Click the API icon to see an example call to the search rss API.</span></div>\n");
            for (int i = 0; i < sz; i++) {
                int id = iterator.nextDoc();
                Document doc = searcher.doc(id, DEFAULT_FIELD_LIST);
                LinkedHashMap<String, String> tdoc = HTMLResponseWriter.translateDoc(schema, doc);
                String sku = tdoc.get(CollectionSchema.sku.getSolrFieldName());
                String title = tdoc.get(CollectionSchema.title.getSolrFieldName());
                String text = tdoc.get(CollectionSchema.text_t.getSolrFieldName());

                ArrayList<String> sentences = new ArrayList<String>();
                if (title != null) sentences.add(title);
                SentenceReader sr = new SentenceReader(text);
                StringBuilder line;
                while (sr.hasNext()) {
                    line = sr.next();
                    if (line.length() > 0) sentences.add(line.toString());
                }
                writeDoc(writer, sku, sentences, grep);
            }
        } else {
            writer.write("<title>No Document Found</title>\n</head><body>\n");
        }
        
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
        writer.write("<dd><a href=\"/solr/select?q=text_t:%22");
        XML.escapeAttributeValue(line, writer);
        writer.write("%22&rows=100&grep=%22");
        XML.escapeAttributeValue(grep, writer);
        writer.write("%22&wt=grephtml\">");
        XML.escapeAttributeValue(line, writer);
        writer.write("</a></dd>\n");
    }

}
