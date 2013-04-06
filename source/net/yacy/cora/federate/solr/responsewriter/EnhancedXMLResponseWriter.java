/**
 *  FastXMLResponseWriter
 *  Copyright 2012 by Michael Peter Christen
 *  First released 06.08.2012 at http://yacy.net
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
import java.util.List;
import java.util.Set;

import net.yacy.cora.federate.solr.SolrType;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
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

public class EnhancedXMLResponseWriter implements QueryResponseWriter {

    private static final char lb = '\n';
    private static final char[] XML_START = "<?xml version = \"1.0\" encoding = \"UTF-8\"?>\n<response>\n".toCharArray();
    private static final char[] XML_STOP = "\n</response>\n".toCharArray();
    private static final Set<String> DEFAULT_FIELD_LIST = null;

    public EnhancedXMLResponseWriter() {
        super();
    }

    @Override
    public String getContentType(final SolrQueryRequest request, final SolrQueryResponse response) {
        return CONTENT_TYPE_XML_UTF8;
    }

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList n) {
    }

    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
        writer.write(XML_START);
        NamedList<?> values = rsp.getValues();
        
        assert values.get("responseHeader") != null;
        assert values.get("response") != null;

        SimpleOrderedMap<Object> responseHeader = (SimpleOrderedMap<Object>) rsp.getResponseHeader();
        DocList response = ((ResultContext) values.get("response")).docs;
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> highlighting = (SimpleOrderedMap<Object>) values.get("highlighting");
        writeProps(writer, "responseHeader", responseHeader); // this.writeVal("responseHeader", responseHeader);
        writeDocs(writer, request, response); // this.writeVal("response", response);
        writeProps(writer, "highlighting", highlighting);
        writer.write(XML_STOP);
    }

    private static void writeProps(final Writer writer, final String name, final NamedList<?> val) throws IOException {
        if (val == null) return;
        int sz = val.size();
        if (sz <= 0) startTagClose(writer, "lst", name); else startTagOpen(writer, "lst", name);
        Object v;
        for (int i = 0; i < sz; i++) {
            String n = val.getName(i);
            v = val.getVal(i);
            if (v instanceof Integer) writeTag(writer, "int", n, ((Integer) v).toString(), false);
            else if (v instanceof String) writeTag(writer, "str", n, (String) v, true);
            else if (v instanceof String[]) writeTag(writer, "str", n, (String[]) v, true);
            else if (v instanceof NamedList) writeProps(writer, n, (NamedList<?>) v);
        }
        if (sz > 0) {
            writer.write("</lst>");
            writer.write(lb);
        }
    }

    private static final void writeDocs(final Writer writer, final SolrQueryRequest request, final DocList response) throws IOException {
        boolean includeScore = false;
        final int sz = response.size();
        writer.write("<result");
        writeAttr(writer, "name", "response");
        writeAttr(writer, "numFound", Long.toString(response.matches()));
        writeAttr(writer, "start", Long.toString(response.offset()));
        if (includeScore) {
            writeAttr(writer, "maxScore", Float.toString(response.maxScore()));
        }
        if (sz == 0) {
            writer.write("/>");
            return;
        }
        writer.write('>'); writer.write(lb);
        SolrIndexSearcher searcher = request.getSearcher();
        DocIterator iterator = response.iterator();
        includeScore = includeScore && response.hasScores();
        IndexSchema schema = request.getSchema();
        for (int i = 0; i < sz; i++) {
            int id = iterator.nextDoc();
            Document doc = searcher.doc(id, DEFAULT_FIELD_LIST);
            writeDoc(writer, schema, null, doc, (includeScore ? iterator.score() : 0.0f), includeScore);
        }
        writer.write("</result>");
        writer.write(lb);
    }

    private static final void writeDoc(final Writer writer, final IndexSchema schema, final String name, final Document doc, final float score, final boolean includeScore) throws IOException {
        startTagOpen(writer, "doc", name);

        if (includeScore) {
            writeTag(writer, "float", "score", Float.toString(score), false);
        }

        List<IndexableField> fields = doc.getFields();
        int sz = fields.size();
        int fidx1 = 0, fidx2 = 0;
        while (fidx1 < sz) {
            IndexableField value = fields.get(fidx1);
            String fieldName = value.name();
            fidx2 = fidx1 + 1;
            while (fidx2 < sz && fieldName.equals(fields.get(fidx2).name())) {
                fidx2++;
            }
            SchemaField sf = schema.getFieldOrNull(fieldName);
            if (sf == null) {
                sf = new SchemaField(fieldName, new TextField());
            }
            FieldType type = sf.getType();
            if (fidx1 + 1 == fidx2) {
                if (sf.multiValued()) {
                    startTagOpen(writer, "arr", fieldName);
                    String sv = value.stringValue();
                    writeField(writer, type, null, sv); //sf.write(this, null, f1);
                    writer.write("</arr>");
                } else {
                    writeField(writer, type, value.name(), value.stringValue()); //sf.write(this, f1.name(), f1);
                }
            } else {
                startTagOpen(writer, "arr", fieldName);
                for (int i = fidx1; i < fidx2; i++) {
                    String sv = fields.get(i).stringValue();
                    writeField(writer, type, null, sv); //sf.write(this, null, (Fieldable)this.tlst.get(i));
                }
                writer.write("</arr>");
                writer.write(lb);
            }
            fidx1 = fidx2;
        }
        writer.write("</doc>");
        writer.write(lb);
    }

    @SuppressWarnings("deprecation")
    private static void writeField(final Writer writer, final FieldType type, final String name, final String value) throws IOException {
        String typeName = type.getTypeName();
        if (typeName.equals(SolrType.text_general.printName()) ||
            typeName.equals(SolrType.string.printName()) ||
            typeName.equals(SolrType.text_en_splitting_tight.printName())) {
            writeTag(writer, "str", name, value, true);
        } else if (typeName.equals(SolrType.bool.printName())) {
            writeTag(writer, "bool", name, "F".equals(value) ? "false" : "true", true);
        } else if (typeName.equals(SolrType.num_integer.printName())) {
            writeTag(writer, "int", name, value, true);
        } else if (typeName.equals(SolrType.num_long.printName())) {
            writeTag(writer, "long", name, value, true);
        } else if (typeName.equals(SolrType.date.printName())) {
            writeTag(writer, "date", name, org.apache.solr.schema.DateField.formatExternal(new Date(Long.parseLong(value))), true); // this is declared deprecated in solr 4.2.1 but is still used as done here
        } else if (typeName.equals(SolrType.num_float.printName())) {
            writeTag(writer, "float", name, value, true);
        } else if (typeName.equals(SolrType.num_double.printName())) {
            writeTag(writer, "double", name, value, true);
        }
    }

    private static void writeTag(final Writer writer, final String tag, final String nameAttr, final String val, final boolean escape) throws IOException {
        int contentLen = val.length();
        if (contentLen == 0) {
            startTagClose(writer, tag, nameAttr);
            return;
        }
        startTagOpen(writer, tag, nameAttr);
        if (escape) {
            XML.escapeCharData(val, writer);
        } else {
            writer.write(val, 0, contentLen);
        }
        writer.write("</"); writer.write(tag); writer.write('>'); writer.write(lb);
    }

    private static void writeTag(final Writer writer, final String tag, final String nameAttr, final String[] vals, final boolean escape) throws IOException {
        startTagOpen(writer, "arr", nameAttr);
        for (String val: vals) {
            writeTag(writer, tag, null, val, escape);
        }
        writer.write("</arr>"); writer.write(lb);
    }

    private static void startTagOpen(final Writer writer, final String tag, final String nameAttr) throws IOException {
        writer.write('<'); writer.write(tag);
        if (nameAttr != null) writeAttr(writer, "name", nameAttr);
        writer.write('>'); writer.write(lb);
    }

    private static void startTagClose(final Writer writer, final String tag, final String nameAttr) throws IOException {
        writer.write('<'); writer.write(tag);
        if (nameAttr != null) writeAttr(writer, "name", nameAttr);
        writer.write("/>"); writer.write(lb);
    }

    private static void writeAttr(final Writer writer, final String nameAttr, final String val) throws IOException {
        writer.write(' '); writer.write(nameAttr); writer.write("=\""); XML.escapeAttributeValue(val, writer); writer.write('"');
    }
}
