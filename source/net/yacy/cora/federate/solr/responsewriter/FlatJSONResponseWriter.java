/**
 *  FlatJSONResponseWriter
 *  Copyright 2017 by Michael Peter Christen
 *  First released 30.03.2017 at http://yacy.net
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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.common.SolrDocument;
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
import org.apache.solr.util.DateFormatUtil;
import org.json.simple.JSONArray;

import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.util.JSONObject;

public class FlatJSONResponseWriter implements QueryResponseWriter {

    private static final char lb = '\n';
    
    private boolean elasticsearchBulkRequest;
    
    public FlatJSONResponseWriter(boolean elasticsearchBulkRequest) {
        this.elasticsearchBulkRequest = elasticsearchBulkRequest;
    }
    
    @Override
    public String getContentType(SolrQueryRequest arg0, SolrQueryResponse arg1) {
        return "application/json; charset=UTF-8";
    }

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList arg0) {
    }

    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
        NamedList<?> values = rsp.getValues();
        DocList response = ((ResultContext) values.get("response")).docs;
        writeDocs(writer, request, response);
    }

    private static final void writeDocs(final Writer writer, final SolrQueryRequest request, final DocList response) throws IOException {
        boolean includeScore = false;
        final int sz = response.size();
        SolrIndexSearcher searcher = request.getSearcher();
        DocIterator iterator = response.iterator();
        includeScore = includeScore && response.hasScores();
        IndexSchema schema = request.getSchema();
        for (int i = 0; i < sz; i++) {
            int id = iterator.nextDoc();
            Document doc = searcher.doc(id);
            writeDoc(writer, schema, null, doc.getFields(), (includeScore ? iterator.score() : 0.0f), includeScore);
        }
    }

    private static final void writeDoc(final Writer writer, final IndexSchema schema, final String name, final List<IndexableField> fields, final float score, final boolean includeScore) throws IOException {
        JSONObject json = new JSONObject(true);
        
        int sz = fields.size();
        int fidx1 = 0, fidx2 = 0;
        while (fidx1 < sz) {
            IndexableField value = fields.get(fidx1);
            String fieldName = value.name();
            fidx2 = fidx1 + 1;
            while (fidx2 < sz && fieldName.equals(fields.get(fidx2).name())) {
                fidx2++;
            }
            SchemaField sf = schema == null ? null : schema.getFieldOrNull(fieldName);
            if (sf == null) {
                sf = new SchemaField(fieldName, new TextField());
            }
            FieldType type = sf.getType();
            if (fidx1 + 1 == fidx2) {
                if (sf.multiValued()) {
                    JSONArray a = new JSONArray();
                    json.put(fieldName, a);
                    JSONObject j = new JSONObject();
                    String sv = value.stringValue();
                    setValue(j, type.getTypeName(), "x", sv); //sf.write(this, null, f1);
                    a.add(j.get("x"));
                } else {
                    setValue(json, type.getTypeName(), value.name(), value.stringValue());
                }
            } else {
                JSONArray a = new JSONArray();
                json.put(fieldName, a);
                for (int i = fidx1; i < fidx2; i++) {
                    String sv = fields.get(i).stringValue();
                    JSONObject j = new JSONObject();
                    setValue(j, type.getTypeName(), "x", sv); //sf.write(this, null, f1);
                    a.add(j.get("x"));
                }
            }
            fidx1 = fidx2;
        }
        writer.write(json.toString());
        writer.write(lb);
    }

    private static void setValue(final JSONObject json, final String typeName, final String name, final String value) {
        if (typeName.equals(SolrType.text_general.printName()) ||
            typeName.equals(SolrType.string.printName()) ||
            typeName.equals(SolrType.text_en_splitting_tight.printName())) {
            json.put(name, value);
        } else if (typeName.equals(SolrType.bool.printName())) {
            json.put(name, "F".equals(value) ? false : true);
        } else if (typeName.equals(SolrType.num_integer.printName())) {
            json.put(name, Long.parseLong(value));
        } else if (typeName.equals(SolrType.num_long.printName())) {
            json.put(name, Long.parseLong(value));
        } else if (typeName.equals(SolrType.date.printName())) {
            json.put(name, DateFormatUtil.formatExternal(new Date(Long.parseLong(value))));
        } else if (typeName.equals(SolrType.num_float.printName())) {
            json.put(name, Double.parseDouble(value));
        } else if (typeName.equals(SolrType.num_double.printName())) {
            json.put(name, Double.parseDouble(value));
        }
    }
    
    public static final void writeDoc(final Writer writer, final SolrDocument doc) throws IOException {
        JSONObject json = new JSONObject(true);
        final Map<String, Object> fields = doc.getFieldValueMap();
        for (String key: fields.keySet()) {
            if (key == null)  continue;
            Object value = doc.get(key);
            if (value == null) {
            } else if (value instanceof Collection<?>) {
                JSONArray a = new JSONArray();
                json.put(key, a);
                for (Object o: ((Collection<?>) value)) {
                    a.add(o);
                }
            } else {
                json.put(key, value);
            }
        }
        writer.write(json.toString());
        writer.write(lb);
    }
}
