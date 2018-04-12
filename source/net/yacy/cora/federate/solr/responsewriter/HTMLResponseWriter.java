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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
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
import org.apache.solr.search.ReturnFields;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SolrReturnFields;

import net.yacy.cora.federate.solr.SolrType;
import net.yacy.cora.lod.vocabulary.DublinCore;
import net.yacy.search.schema.CollectionSchema;
import net.yacy.search.schema.WebgraphSchema;

/**
 * Solr response writer producing an HTML representation.
 */
public class HTMLResponseWriter implements QueryResponseWriter, SolrjResponseWriter {

    public static final Pattern dqp = Pattern.compile("\"");
    
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
    
    /**
     * Append YaCy JavaScript license information to writer
     * @param writer must be non null
     * @throws IOException when a write error occurred
     */
	private void writeJSLicence(final Writer writer) throws IOException {
		writer.write("<script>");
		writer.write("/*");
		writer.write("@licstart  The following is the entire license notice for the");
		writer.write("JavaScript code in this page.");
		writer.write("");
		writer.write("Copyright (C) 2013-2015 by Michael Peter Christen and reger");
		writer.write("");
		writer.write("The JavaScript code in this page is free software: you can redistribute it and/or");
		writer.write("modify it under the terms of the GNU General Public License");
		writer.write("as published by the Free Software Foundation; either version 2");
		writer.write("of the License, or (at your option) any later version.");
		writer.write("");
		writer.write("This program is distributed in the hope that it will be useful,");
		writer.write("but WITHOUT ANY WARRANTY; without even the implied warranty of");
		writer.write("MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the");
		writer.write("GNU General Public License for more details.");
		writer.write("");
		writer.write("You should have received a copy of the GNU General Public License");
		writer.write("along with this program; if not, write to the Free Software");
		writer.write("Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.");
		writer.write("");
		writer.write("@licend  The above is the entire license notice");
		writer.write("for the JavaScript code in this page.");
		writer.write("*/");
		writer.write("</script>");
	}

    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
        NamedList<?> values = rsp.getValues();
        assert values.get("responseHeader") != null;
        assert values.get("response") != null;

        writeHtmlHead(writer);

        NamedList<Object> paramsList = request.getOriginalParams().toNamedList();
        paramsList.remove("wt");
        
        final String coreName = request.getCore().getName();
        
        String xmlquery = dqp.matcher("../solr/select?" + SolrParams.toSolrParams(paramsList).toString() + "&core=" + coreName).replaceAll("%22");

        DocList response = ((ResultContext) values.get("response")).getDocList();
        final int sz = response.size();
        if (sz > 0) {
            SolrIndexSearcher searcher = request.getSearcher();
            DocIterator iterator = response.iterator();
            IndexSchema schema = request.getSchema();

            int id = iterator.nextDoc();
            Document doc = searcher.doc(id);
            LinkedHashMap<String, String> tdoc = translateDoc(schema, doc, rsp.getReturnFields());

			String title = doc.get(CollectionSchema.title.getSolrFieldName()); // title is multivalued, after translation fieldname could be in tdoc. "title_0" ..., so get it from doc
            writeTitle(writer, coreName, sz, title);
            
            writer.write("<div id=\"api\"><a href=\"" + xmlquery + "\"><img src=\"../env/grafics/api.png\" width=\"60\" height=\"40\" alt=\"API\" /></a>\n");
            writer.write("<span>This search result can also be retrieved as XML. Click the API icon to see this page as XML.</span></div>\n");

            writeDoc(writer, tdoc, coreName, rsp.getReturnFields());

            while (iterator.hasNext()) {
                id = iterator.nextDoc();
                doc = searcher.doc(id);
                tdoc = translateDoc(schema, doc, rsp.getReturnFields());

                writeDoc(writer, tdoc, coreName, rsp.getReturnFields());
            }
        } else {
            writer.write("<title>No Document Found</title>\n</head><body>\n");
            writer.write("<div class='alert alert-info'>No documents found</div>\n");
        }
        
        writer.write("</body></html>\n");
    }
    
	@Override
	public void write(final Writer writer, final SolrQueryRequest request, final String coreName, final QueryResponse rsp) throws IOException {
		writeHtmlHead(writer);

		final SolrParams originalParams = request.getOriginalParams();
		final NamedList<Object> paramsList = originalParams.toNamedList();
		paramsList.remove("wt");

		final String xmlquery = dqp
				.matcher("../solr/select?" + SolrParams.toSolrParams(paramsList).toString() + "&core=" + coreName)
				.replaceAll("%22");

		final SolrDocumentList docsList = rsp.getResults();
		final int sz = docsList.size();
		if (sz > 0) {
			final Iterator<SolrDocument> iterator = docsList.iterator();

			SolrDocument doc = iterator.next();
			final ReturnFields fieldsToReturn = request != null ? new SolrReturnFields(request) : new SolrReturnFields();

			final Object titleValue = doc.getFirstValue(CollectionSchema.title.getSolrFieldName());
			final String firstDocTitle = formatValue(titleValue);
			writeTitle(writer, coreName, sz, firstDocTitle);

			writer.write("<div id=\"api\"><a href=\"" + xmlquery
					+ "\"><img src=\"../env/grafics/api.png\" width=\"60\" height=\"40\" alt=\"API\" /></a>\n");
			writer.write(
					"<span>This search result can also be retrieved as XML. Click the API icon to see this page as XML.</span></div>\n");

			writeDoc(writer, translateDoc(doc, fieldsToReturn), coreName, fieldsToReturn);

			while (iterator.hasNext()) {
				doc = iterator.next();

				writeDoc(writer, translateDoc(doc, fieldsToReturn), coreName, fieldsToReturn);
			}
		} else {
			writer.write("<title>No Document Found</title>\n</head><body>\n");
			writer.write("<div class='alert alert-info'>No documents found</div>\n");
		}

		writer.write("</body></html>\n");
	}

    /**
     * Appends the response HTML title to the writer, close the head section and starts the body section.
     * @param writer must not be null.
     * @param coreName the name of the requested Solr core
     * @param responseSize the number of documents in the Solr response
     * @param firstDocTitle the eventual title of the first document of the result set
     * @throws IOException when a write error occurred
     */
	private void writeTitle(final Writer writer, final String coreName, final int responseSize, final String firstDocTitle)
			throws IOException {
		if(CollectionSchema.CORE_NAME.equals(coreName)) {
			if (responseSize == 1) {
				writer.write("<title>");
				writer.write(firstDocTitle == null ? "" : firstDocTitle);
				writer.write("</title>\n</head><body>\n");
			} else {
				writer.write("<title>Documents List</title>\n</head><body>\n");
			}
		} else if(WebgraphSchema.CORE_NAME.equals(coreName)) {
			writer.write("<title>Links list</title>\n</head><body>\n");
		} else {
			writer.write("<title>Solr documents List</title>\n</head><body>\n");
		}
	}

    /**
     * Append the response HTML head beginning to the writer.
     * @param writer must not be null
     * @throws IOException when a write error occurred
     */
	private void writeHtmlHead(final Writer writer) throws IOException {
		writer.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n");
        //writer.write("<!--\n");
        //writer.write("this is a XHTML+RDFa file. It contains RDF annotations with dublin core properties\n");
        //writer.write("you can validate it with http://validator.w3.org/\n");
        //writer.write("-->\n");
        writer.write("<html xmlns=\"http://www.w3.org/1999/xhtml\"\n");
        writer.write("      xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n");
        writer.write("      xmlns:dc=\"" + DublinCore.NAMESPACE + "\"\n");
        writer.write("      xmlns:foaf=\"http://xmlns.com/foaf/0.1/\">\n");
        writer.write("<head profile=\"http://www.w3.org/2003/g/data-view\">\n");
        writer.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
        this.writeJSLicence(writer);
        //writer.write("<link rel=\"transformation\" href=\"http://www-sop.inria.fr/acacia/soft/RDFa2RDFXML.xsl\"/>\n");

        writer.write("<!-- Bootstrap core CSS -->\n");
        writer.write("<link href=\"../env/bootstrap/css/bootstrap.min.css\" rel=\"stylesheet\">\n");
        writer.write("<link href=\"../env/bootstrap/css/bootstrap-switch.min.css\" rel=\"stylesheet\">\n");
        //writer.write("<script src=\"../env/bootstrap/js/jquery.min.js\"></script>\n");
        //writer.write("<script src=\"../env/bootstrap/js/bootstrap.min.js\"></script>\n");
        //writer.write("<script src=\"../env/bootstrap/js/bootstrap-switch.min.js\"></script>\n");
        writer.write("<!-- Custom styles for this template, i.e. navigation (move this to base.css) -->\n");
        writer.write("<link href=\"../env/bootstrap-base.css\" rel=\"stylesheet\">\n");
        //writer.write("<!-- HTML5 shim and Respond.js IE8 support of HTML5 elements and media queries -->\n");
        //writer.write("<!--[if lt IE 9]>\n");
        //writer.write("  <script src=\"../env/bootstrap/js/html5shiv.js\"></script>\n");
        //writer.write("  <script src=\"../env/bootstrap/js/respond.min.js\"></script>\n");
        //writer.write("<![endif]-->\n");
        writer.write("<!-- old css styles -->\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"all\" href=\"../env/base.css\" />\n");
        writer.write("<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"../env/style.css\" />\n");
        writer.write("<!--[if lt IE 6]>\n");
        writer.write(" <link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"../env/oldie.css\" />\n");
        writer.write("<![endif]-->\n");
        writer.write("<!--[if lte IE 6.0]>\n");
        writer.write(" <link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"../env/ie6.css\" />\n");
        writer.write("<![endif]-->\n");
        writer.write("<!--[if lte IE 7.0]>\n");
        writer.write(" <link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"../env/ie7.css\" />\n");
        writer.write("<![endif]-->\n");
        writer.write("<!-- (C), Architecture: Michael Peter Christen; Contact: mc <at> yacy.net -->\n");
	}
    


    /**
     * Append an html representation of the document fields to the writer.
     * @param writer an open output writer. Must not be null.
     * @param tdoc the documents fields, mapped from field names to values. Must not be null.
     * @param coreName the Solr core name.
     * @param returnFields the eventual fields return configuration, allowing for example to
	 *            rename fields with a pseudo in the result. May be null.
     * @throws IOException when a write error occurred.
     */
	private static final void writeDoc(final Writer writer, final LinkedHashMap<String, String> tdoc, final String coreName, final ReturnFields returnFields) throws IOException {
    	String title;
        if(CollectionSchema.CORE_NAME.equals(coreName)) {
        	title = tdoc.get(CollectionSchema.title.getSolrFieldName());
            if (title == null) {
            	title = "";
            }
        } else {
        	title = "";
        }
		
        writer.write("<form name=\"yacydoc" + title + "\" method=\"post\" action=\"#\" enctype=\"multipart/form-data\" accept-charset=\"UTF-8\">\n");
        writer.write("<fieldset>\n");
        
        // add a link to re-crawl this url (in case it is a remote metadata only entry)
        if(CollectionSchema.CORE_NAME.equals(coreName)) {
        	String sku = tdoc.get(CollectionSchema.sku.getSolrFieldName());
        	if(sku != null) {
        		final String jsc= "javascript:w = window.open('../QuickCrawlLink_p.html?indexText=on&indexMedia=on&crawlingQ=on&followFrames=on&obeyHtmlRobotsNoindex=on&obeyHtmlRobotsNofollow=off&xdstopw=on&title=" + URLEncoder.encode(title, StandardCharsets.UTF_8.name()) + "&url='+escape('"+sku+"'),'_blank','height=250,width=600,resizable=yes,scrollbar=no,directory=no,menubar=no,location=no');w.focus();";
        		writer.write("<div class='btn btn-default btn-sm' style='float:right' onclick=\""+jsc+"\">re-crawl url</div>\n");
        	}
        	
            writer.write("<h1 property=\"" + DublinCore.Title.getURIref()+ "\">" + title + "</h1>\n");
        }

        writer.write("<dl>\n");
        /* Fields may be renamed in the ouput result, using aliases in the 'fl' parameter 
         * (see https://lucene.apache.org/solr/guide/6_6/common-query-parameters.html#CommonQueryParameters-FieldNameAliases) */
		final Map<String, String> fieldRenamings = returnFields == null ? Collections.emptyMap()
				: returnFields.getFieldRenames();
        for (Map.Entry<String, String> entry: tdoc.entrySet()) {
        	if(returnFields != null && !returnFields.wantsField(entry.getKey())) {
        		continue;
        	}
            writer.write("<dt>");
        	writer.write(fieldRenamings.getOrDefault(entry.getKey(), entry.getKey()));
            writer.write("</dt><dd>");
            if (entry.getKey().equals("sku")) {
                writer.write("<a href=\"" + entry.getValue() + "\">" + entry.getValue() + "</a>");
            } else {
                XML.escapeAttributeValue(entry.getValue(), writer);
            }
            writer.write("</dd>\n");
        }
        writer.write("</dl>\n");
        writer.write("</fieldset>\n");
        writer.write("</form>\n");
    }
    
	/**
	 * Translate an indexed document in a map of field names to field values.
	 * 
	 * @param schema
	 *            the schema of the indexed document. Must not be null.
	 * @param doc
	 *            the indexed document. Must not be null.
	 * @param returnFields
	 *            the eventual fields return configuration, allowing for example to
	 *            restrict the actually returned fields. May be null.
	 * @return a map of field names to field values
	 */
    private static final LinkedHashMap<String, String> translateDoc(final IndexSchema schema, final Document doc, final ReturnFields returnFields) {
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
            if(returnFields == null || returnFields.wantsField(fieldName)) {
            	SchemaField sf = schema.getFieldOrNull(fieldName);
            	if (sf == null) {
            		sf = new SchemaField(fieldName, new TextField());
            	}
            	FieldType type = sf.getType();
            
            	if (fidx1 + 1 == fidx2) {
            		if (sf.multiValued()) {
            			String sv = value.stringValue();
            			kv.put(fieldName, field2string(type, sv));
            		} else {
            			kv.put(fieldName, field2string(type, value.stringValue()));
            		}
            	} else {
            		int c = 0;
            		for (int i = fidx1; i < fidx2; i++) {
            			String sv = fields.get(i).stringValue();
            			kv.put(fieldName + "_" + c++, field2string(type, sv));
            		}
            	}
            }
            
            fidx1 = fidx2;
        }
        return kv;
    }
    
	/**
	 * Translate an indexed document in a map of field names to field values.
	 * 
	 * @param doc
	 *            the indexed document. Must not be null.
	 * @param returnFields
	 *            the eventual fields return configuration, allowing for example to
	 *            restrict the actually returned fields. May be null.
	 * @return a map of field names to field values
	 */
	private static final LinkedHashMap<String, String> translateDoc(final SolrDocument doc,
			final ReturnFields returnFields) {
		LinkedHashMap<String, String> kv = new LinkedHashMap<String, String>();
		for (final Entry<String, Object> entry : doc) {
			String fieldName = entry.getKey();

			if (returnFields == null || returnFields.wantsField(fieldName)) {
				Object value = entry.getValue();

				if (value instanceof Collection<?>) {
					Collection<?> values = (Collection<?>) value;
					if(values.size() > 1) {
						int c = 0;
						for (final Object singleValue : values) {
							if (singleValue != null) {
								kv.put(fieldName + "_" + c++, formatValue(singleValue));
							}
						}
					} else if(values.size() == 1) {
						Object singleValue = values.iterator().next();
						if(singleValue != null) {
							kv.put(fieldName, formatValue(singleValue));		
						}
					}
				} else if (value != null) {
					kv.put(fieldName, formatValue(value));
				}
			}

		}
		return kv;
	}
    
    /**
     * Translate an indexed document in a map of field names to field values.
     * @param schema the schema of the indexed document. Must not be null.
     * @param doc the indexed document. Must not be null.
     * @return a map of field names to field values
     */
    public static final LinkedHashMap<String, String> translateDoc(final IndexSchema schema, final Document doc) {
        return translateDoc(schema, doc, null);
    }
    
    /**
     * Format a solr field single value
     * @param value the value
     * @return a String representation of the value
     */
    private static String formatValue(final Object value) {
        if (value instanceof Date) {
            return ((Date)value).toInstant().toString();
        } else if(value != null) {
        	return value.toString();
        }
        return null;
    }

    /**
     * Reformat a solr field value string depending on the field type
     * @param type the Solr field type. Must not be null.
     * @param value the value
     * @return a value eventually reformatted
     */
    private static String field2string(final FieldType type, final String value) {
        String typeName = type.getTypeName();
        if (SolrType.bool.printName().equals(typeName)) {
            return "F".equals(value) ? "false" : "true";
        } else if (SolrType.date.printName().equals(typeName)) {
            return new Date(Long.parseLong(value)).toInstant().toString();
        }
        return value;
    }

    // XML.escapeCharData(val, writer);
}
