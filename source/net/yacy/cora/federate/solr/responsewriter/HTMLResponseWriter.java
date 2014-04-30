/**
 *  HTMLResponseWriter
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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.federate.solr.SolrType;
import net.yacy.search.schema.CollectionSchema;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.XML;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.TextField;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;

public class HTMLResponseWriter implements QueryResponseWriter {

    private static final Set<String> DEFAULT_FIELD_LIST = null;
    private static final Pattern dqp = Pattern.compile("\"");
    
    public HTMLResponseWriter() {
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

        writer.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
        //writer.write("<!--\n");
        //writer.write("this is a XHTML+RDFa file. It contains RDF annotations with dublin core properties\n");
        //writer.write("you can validate it with http://validator.w3.org/\n");
        //writer.write("-->\n");
        writer.write("<html xmlns=\"http://www.w3.org/1999/xhtml\"\n");
        writer.write("      xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n");
        writer.write("      xmlns:dc=\"http://purl.org/dc/elements/1.1/\"\n");
        writer.write("      xmlns:foaf=\"http://xmlns.com/foaf/0.1/\">\n");
        writer.write("<head profile=\"http://www.w3.org/2003/g/data-view\">\n");
        //writer.write("<link rel=\"transformation\" href=\"http://www-sop.inria.fr/acacia/soft/RDFa2RDFXML.xsl\"/>\n");

        writer.write("<!-- Bootstrap core CSS -->\n");
        writer.write("<link href=\"/env/bootstrap/css/bootstrap.min.css\" rel=\"stylesheet\">\n");
        writer.write("<link href=\"/env/bootstrap/css/bootstrap-switch.min.css\" rel=\"stylesheet\">\n");
        //writer.write("<script src=\"/env/bootstrap/js/jquery.min.js\"></script>\n");
        //writer.write("<script src=\"/env/bootstrap/js/bootstrap.min.js\"></script>\n");
        //writer.write("<script src=\"/env/bootstrap/js/docs.min.js\"></script>\n");
        //writer.write("<script src=\"/env/bootstrap/js/bootstrap-switch.min.js\"></script>\n");
        writer.write("<!-- Custom styles for this template, i.e. navigation (move this to base.css) -->\n");
        writer.write("<link href=\"/env/bootstrap-base.css\" rel=\"stylesheet\">\n");
        //writer.write("<!-- HTML5 shim and Respond.js IE8 support of HTML5 elements and media queries -->\n");
        //writer.write("<!--[if lt IE 9]>\n");
        //writer.write("  <script src=\"/env/bootstrap/js/html5shiv.js\"></script>\n");
        //writer.write("  <script src=\"/env/bootstrap/js/respond.min.js\"></script>\n");
        //writer.write("<![endif]-->\n");
        writer.write("<!-- old css styles -->\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"all\" href=\"/env/base.css\" />\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"/env/style.css\" />\n");
        writer.write("<!--[if lt IE 6]>\n");
        writer.write(" <link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"/env/oldie.css\" />\n");
        writer.write("<![endif]-->\n");
        writer.write("<!--[if lte IE 6.0]>\n");
        writer.write(" <link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"/env/ie6.css\" />\n");
        writer.write("<![endif]-->\n");
        writer.write("<!--[if lte IE 7.0]>\n");
        writer.write(" <link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"/env/ie7.css\" />\n");
        writer.write("<![endif]-->\n");
        writer.write("<!-- (C), Architecture: Michael Peter Christen; Contact: mc <at> yacy.net -->\n");

        NamedList<Object> paramsList = request.getOriginalParams().toNamedList();
        paramsList.remove("wt");
        String xmlquery = dqp.matcher("/solr/select?" + SolrParams.toSolrParams(paramsList).toString()).replaceAll("%22");

        DocList response = ((ResultContext) values.get("response")).docs;
        final int sz = response.size();
        if (sz > 0) {
            SolrIndexSearcher searcher = request.getSearcher();
            DocIterator iterator = response.iterator();
            IndexSchema schema = request.getSchema();

            int id = iterator.nextDoc();
            Document doc = searcher.doc(id, DEFAULT_FIELD_LIST);
            LinkedHashMap<String, String> tdoc = translateDoc(schema, doc);
            
            String title = tdoc.get(CollectionSchema.title.getSolrFieldName());
            if (sz == 1) {
                writer.write("<title>" + title + "</title>\n</head><body>\n");
            } else {
                writer.write("<title>Document List</title>\n</head><body>\n");
            }
            writer.write("<div id=\"api\"><a href=\"" + xmlquery + "\"><img src=\"../env/grafics/api.png\" width=\"60\" height=\"40\" alt=\"API\" /></a>\n");
            writer.write("<span>This search result can also be retrieved as XML. Click the API icon to see this page as XML.</span></div>\n");

            writeDoc(writer, tdoc, title);

            while (iterator.hasNext()) {
                id = iterator.nextDoc();
                doc = searcher.doc(id, DEFAULT_FIELD_LIST);
                tdoc = translateDoc(schema, doc);
                title = tdoc.get(CollectionSchema.title.getSolrFieldName());
                writeDoc(writer, tdoc, title);
            }
        } else {
            writer.write("<title>No Document Found</title>\n</head><body>\n");
        }
        
        writer.write("</body></html>\n");
    }

    private static final void writeDoc(Writer writer, LinkedHashMap<String, String> tdoc, String title) throws IOException {
        writer.write("<form name=\"yacydoc" + title + "\" method=\"post\" action=\"#\" enctype=\"multipart/form-data\" accept-charset=\"UTF-8\">\n");
        writer.write("<fieldset>\n");
        writer.write("<h1 property=\"dc:Title\">" + title + "</h1>\n");
        writer.write("<dl>\n");
        for (Map.Entry<String, String> entry: tdoc.entrySet()) {
            writer.write("<dt>");
            writer.write(entry.getKey());
            writer.write("</dt><dd>");
            XML.escapeAttributeValue(entry.getValue(), writer);
            writer.write("</dd>\n");
        }
        writer.write("</dl>\n");
        writer.write("</fieldset>\n");
        writer.write("</form>\n");
    }
    
    static final LinkedHashMap<String, String> translateDoc(final IndexSchema schema, final Document doc) {
        List<IndexableField> fields = doc.getFields();
        int sz = fields.size();
        int fidx1 = 0, fidx2 = 0;
        LinkedHashMap<String, String> kv = new LinkedHashMap<String, String>();
        while (fidx1 < sz) {
            IndexableField value = fields.get(fidx1);
            String fieldName = value.name();
            fidx2 = fidx1 + 1;
            while (fidx2 < sz && fieldName.equals(fields.get(fidx2).name())) {
                fidx2++;
            }
            SchemaField sf = schema.getFieldOrNull(fieldName);
            if (sf == null) sf = new SchemaField(fieldName, new TextField());
            FieldType type = sf.getType();
            
            if (fidx1 + 1 == fidx2) {
                if (sf.multiValued()) {
                    String sv = value.stringValue();
                    kv.put(fieldName, field2string(type, sv));
                } else {
                    kv.put(fieldName, field2string(type, value.stringValue()));
                }
            } else {
                for (int i = fidx1; i < fidx2; i++) {
                    String sv = fields.get(i).stringValue();
                    kv.put(fieldName + "_" + i, field2string(type, sv));
                }
            }
            
            fidx1 = fidx2;
        }
        return kv;
    }

    @SuppressWarnings("deprecation")
    private static String field2string(final FieldType type, final String value) {
        String typeName = type.getTypeName();
        if (typeName.equals(SolrType.bool.printName())) {
            return "F".equals(value) ? "false" : "true";
        } else if (typeName.equals(SolrType.date.printName())) {
            return org.apache.solr.schema.DateField.formatExternal(new Date(Long.parseLong(value))); // this is declared deprecated in solr 4.2.1 but is still used as done here
        }
        return value;
    }

    // XML.escapeCharData(val, writer);
}
