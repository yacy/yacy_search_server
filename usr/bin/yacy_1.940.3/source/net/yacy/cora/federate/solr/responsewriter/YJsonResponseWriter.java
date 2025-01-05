/**
 *  JsonResponseWriter
 *  Copyright 2012 by Michael Peter Christen
 *  First released 10.09.2012 at https://yacy.net
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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;
import org.json.JSONObject;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.solr.responsewriter.OpensearchResponseWriter.ResHead;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.Response;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

/**
 * write the opensearch result in YaCys special way to include as much as in opensearch is included.
 * This will also include YaCy facets.
 * 
 * example:
 * http://localhost:8090/solr/select?hl=false&wt=yjson&facet=true&facet.mincount=1&facet.field=host_s&facet.field=url_file_ext_s&facet.field=url_protocol_s&facet.field=author_sxt&facet.field=collection_sxt&start=0&rows=10&query=www
 */
public class YJsonResponseWriter implements QueryResponseWriter, SolrjResponseWriter {

    // define a list of simple YaCySchema -> json Token matchings
    private static final Map<String, String> field2tag = new HashMap<>();
    static {
        field2tag.put(CollectionSchema.url_protocol_s.getSolrFieldName(), "protocol");
        field2tag.put(CollectionSchema.host_s.getSolrFieldName(), "host");
        field2tag.put(CollectionSchema.url_file_ext_s.getSolrFieldName(), "ext");
    }
     
    private String title;

    public YJsonResponseWriter() {
        super();
    }

    public void setTitle(String searchPageTitle) {
        this.title = searchPageTitle;
    }

    @Override
    public String getContentType(final SolrQueryRequest request, final SolrQueryResponse response) {
        return "application/json; charset=UTF-8";
    }

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList n) {
    }
    
    @Override
    public void write(final Writer writer, final SolrQueryRequest request, final SolrQueryResponse rsp) throws IOException {
        
        final NamedList<?> values = rsp.getValues();
        
        final Object responseObj = rsp.getResponse();
        
        write(writer, request, values, responseObj);
    }
    
    @Override
	public void write(Writer writer, SolrQueryRequest request, String coreName, QueryResponse rsp) throws IOException {
        
        final NamedList<Object> values = rsp.getResponse();
        
        final SolrDocumentList documents = rsp.getResults();
        	
        write(writer, request, values, documents);
	}

	/**
	 * Append to the writer the YaCy json representation of the Solr results.
	 * @param writer  an open output writer. Must not be null.
	 * @param request the initial Solr request. Must not be null.
	 * @param values  the response values. Must not be null.
	 * @param responseObj     the Solr response header.
	 * @throws IOException when a write error occurred
	 */
	private void write(final Writer writer, final SolrQueryRequest request, final NamedList<?> values,
			final Object responseObj) throws IOException {

        
        assert values.get("response") != null;

        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> facetCounts = (SimpleOrderedMap<Object>) values.get("facet_counts");
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> facetFields = facetCounts == null || facetCounts.size() == 0 ? null : (SimpleOrderedMap<Object>) facetCounts.get("facet_fields");
        
		final Object highlightingObj = values.get("highlighting");
		final Map<String, Collection<String>> snippets = highlightingObj instanceof NamedList
				? OpensearchResponseWriter.snippetsFromHighlighting((NamedList<?>) highlightingObj)
				: new HashMap<>();

        // parse response header
        final ResHead resHead = new ResHead();
        resHead.rows = request.getOriginalParams().getLong("rows", -1);
        
        String jsonp = request.getParams().get("callback"); // check for JSONP
        if (jsonp != null) {
            writer.write(jsonp.toCharArray());
            writer.write("([".toCharArray());
        }
        
        if(responseObj instanceof ResultContext){
        	/* Regular response object */
        	final DocList documents = ((ResultContext)responseObj).getDocList();
        	
            resHead.offset = documents.offset(); // equal to 'start' Solr param
            resHead.numFound = documents.matches();
            
            writeHeader(writer, resHead);
            
            writeDocs(writer, documents, request, snippets);
        } else if(responseObj instanceof SolrDocumentList) {
			/*
			 * The response object can be a SolrDocumentList when the response is partial,
			 * for example when the allowed processing time has been exceeded
			 */
        	final SolrDocumentList documents = ((SolrDocumentList)responseObj);
        	
            resHead.offset = documents.getStart(); // equal to 'start' Solr param
            resHead.numFound = documents.getNumFound();
            
            writeHeader(writer, resHead);
            
            writeDocs(writer, documents, snippets);
        } else {
        	throw new IOException("Unable to process Solr response format");
        }

        writer.write("],\n".toCharArray());
        
        writer.write("\"navigation\":[\n");

        // the facets can be created with the options &facet=true&facet.mincount=1&facet.field=host_s&facet.field=url_file_ext_s&facet.field=url_protocol_s&facet.field=author_sxt
        @SuppressWarnings("unchecked")
        NamedList<Integer> domains = facetFields == null ? null : (NamedList<Integer>) facetFields.get(CollectionSchema.host_s.getSolrFieldName());
        @SuppressWarnings("unchecked")
        NamedList<Integer> filetypes = facetFields == null ? null : (NamedList<Integer>) facetFields.get(CollectionSchema.url_file_ext_s.getSolrFieldName());
        @SuppressWarnings("unchecked")
        NamedList<Integer> protocols = facetFields == null ? null : (NamedList<Integer>) facetFields.get(CollectionSchema.url_protocol_s.getSolrFieldName());
        @SuppressWarnings("unchecked")
        NamedList<Integer> authors = facetFields == null ? null : (NamedList<Integer>) facetFields.get(CollectionSchema.author_sxt.getSolrFieldName());
        @SuppressWarnings("unchecked")
        NamedList<Integer> collections = facetFields == null ? null : (NamedList<Integer>) facetFields.get(CollectionSchema.collection_sxt.getSolrFieldName());

        int facetcount = 0;
        if (domains != null) {
            writer.write(facetcount > 0 ? ",\n" : "\n");
            writer.write("{\"facetname\":\"domains\",\"displayname\":\"Provider\",\"type\":\"String\",\"min\":\"0\",\"max\":\"0\",\"mean\":\"0\",\"elements\":[\n".toCharArray());
            for (int i = 0; i < domains.size(); i++) {
                facetEntry(writer, "site", domains.getName(i), Integer.toString(domains.getVal(i)));
                if (i < domains.size() - 1) writer.write(',');
                writer.write("\n");
            }
            writer.write("]}".toCharArray());
            facetcount++;
        }
        if (filetypes != null) {
            writer.write(facetcount > 0 ? ",\n" : "\n");
            writer.write("{\"facetname\":\"filetypes\",\"displayname\":\"Filetypes\",\"type\":\"String\",\"min\":\"0\",\"max\":\"0\",\"mean\":\"0\",\"elements\":[\n".toCharArray());
            List<Map.Entry<String, Integer>> l = new ArrayList<>();
            for (Map.Entry<String, Integer> e: filetypes) {
                if (e.getKey().length() <= 6) l.add(e);
                if (l.size() >= 16) break;
            }
            for (int i = 0; i < l.size(); i++) {
                Map.Entry<String, Integer> e = l.get(i);
                facetEntry(writer, "filetype", e.getKey(), Integer.toString(e.getValue()));
                if (i < l.size() - 1) writer.write(',');
                writer.write("\n");
            }
            writer.write("]}".toCharArray());
            facetcount++;
        }
        if (protocols != null) {
            writer.write(facetcount > 0 ? ",\n" : "\n");
            writer.write("{\"facetname\":\"protocols\",\"displayname\":\"Protocol\",\"type\":\"String\",\"min\":\"0\",\"max\":\"0\",\"mean\":\"0\",\"elements\":[\n".toCharArray());
            for (int i = 0; i < protocols.size(); i++) {
                facetEntry(writer, "protocol", protocols.getName(i), Integer.toString(protocols.getVal(i)));
                if (i < protocols.size() - 1) writer.write(',');
                writer.write("\n");
            }
            writer.write("]}".toCharArray());
            facetcount++;
        }
        if (authors != null) {
            writer.write(facetcount > 0 ? ",\n" : "\n");
            writer.write("{\"facetname\":\"authors\",\"displayname\":\"Authors\",\"type\":\"String\",\"min\":\"0\",\"max\":\"0\",\"mean\":\"0\",\"elements\":[\n".toCharArray());
            for (int i = 0; i < authors.size(); i++) {
                facetEntry(writer, "author", authors.getName(i), Integer.toString(authors.getVal(i)));
                if (i < authors.size() - 1) writer.write(',');
                writer.write("\n");
            }
            writer.write("]}".toCharArray());
            facetcount++;
        }
        if (collections != null) {
            writer.write(facetcount > 0 ? ",\n" : "\n");
            writer.write("{\"facetname\":\"collections\",\"displayname\":\"Collections\",\"type\":\"String\",\"min\":\"0\",\"max\":\"0\",\"mean\":\"0\",\"elements\":[\n".toCharArray());
            for (int i = 0; i < collections.size(); i++) {
                facetEntry(writer, "collection", collections.getName(i), Integer.toString(collections.getVal(i)));
                if (i < collections.size() - 1) writer.write(',');
                writer.write("\n");
            }
            writer.write("]}".toCharArray());
            facetcount++;
        }
        writer.write("\n]}]}\n".toCharArray());
        
        if (jsonp != null) {
            writer.write("])".toCharArray());
        }
    }
	
	/**
	 * Append to the writer the header of the YaCy json representation.
	 * @param writer an open output writer. Must not be null.
	 * @param resHead the calculated results head. Must not be null.
	 * @throws IOException when an unexpected error occurred while writing
	 */
	private void writeHeader(final Writer writer, final ResHead resHead)
			throws IOException {
        writer.write(("{\"channels\": [{\n").toCharArray());
        solitaireTag(writer, "totalResults", Long.toString(resHead.numFound));
        solitaireTag(writer, "startIndex", Long.toString(resHead.offset));
        solitaireTag(writer, "itemsPerPage", Long.toString(resHead.rows));
        solitaireTag(writer, "title", this.title);
        solitaireTag(writer, "description", "Search Result");
        writer.write("\"items\": [\n".toCharArray());
	}
	
	/**
	 * Append to the writer the OpenSearch RSS representation of Solr documents.
	 * 
	 * @param writer        an open output writer. Must not be null.
	 * @param documents     the documents to render. Must not be null.
	 * @param snippets      Solr computed text snippets (highlighting).
	 * @throws IOException when an unexpected error occurred while writing
	 */
	private void writeDocs(final Writer writer, final DocList documents, final SolrQueryRequest request, 
			final Map<String, Collection<String>> snippets) throws IOException {
        final SolrIndexSearcher searcher = request.getSearcher();
        final DocIterator iterator = documents.iterator();
        int writtenDocs = 0;
        while(iterator.hasNext()) {
        	if(writtenDocs > 0) {
        		writer.write(",\n".toCharArray());
        	}
            try {
            	writer.write("{\n".toCharArray());
            	int id = iterator.nextDoc();
            	Document doc = searcher.doc(id, OpensearchResponseWriter.SOLR_FIELDS);
            	MultiProtocolURL url = null;
            	String urlhash = null;
            	List<String> descriptions = new ArrayList<>();
            	String docTitle = "";
            	StringBuilder path = new StringBuilder(80);
            	List<Object> imagesProtocolObjs = new ArrayList<>();
            	List<String> imagesStubs = new ArrayList<>();
        	
            	for (final IndexableField value : doc.getFields()) {
            		String fieldName = value.name();

            		// apply generic matching rule
            		String stag = field2tag.get(fieldName);
            		if (stag != null) {
            			solitaireTag(writer, stag, value.stringValue());
            			continue;
            		}
            		
            		// some special handling here
            		if (CollectionSchema.sku.getSolrFieldName().equals(fieldName)) {
            			url = writeLink(writer, value.stringValue());
            			continue;
            		}
            		if (CollectionSchema.title.getSolrFieldName().equals(fieldName)) {
            			docTitle = value.stringValue();
            			continue;
            		}
            		if (CollectionSchema.description_txt.getSolrFieldName().equals(fieldName)) {
            			String description = value.stringValue();
            			descriptions.add(description);
            			continue;
            		}
            		if (CollectionSchema.id.getSolrFieldName().equals(fieldName)) {
            			urlhash = value.stringValue();
                    	solitaireTag(writer, "guid", urlhash);
                    	continue;
            		}
            		if (CollectionSchema.url_paths_sxt.getSolrFieldName().equals(fieldName)) {
            			path.append('/').append(value.stringValue());
                    	continue;
            		}
            		if (CollectionSchema.last_modified.getSolrFieldName().equals(fieldName)) {
            			Date d = new Date(Long.parseLong(value.stringValue()));
            			solitaireTag(writer, "pubDate", HeaderFramework.formatRFC1123(d));
            			continue;
            		}
            		if (CollectionSchema.size_i.getSolrFieldName().equals(fieldName)) {
            			int size = value.stringValue() != null && value.stringValue().length() > 0 ? Integer.parseInt(value.stringValue()) : -1;
            			writeSize(writer, size);
            			continue;
            		}
            		if (CollectionSchema.images_protocol_sxt.getSolrFieldName().equals(fieldName)) {
            			imagesProtocolObjs.add(value.stringValue());
            			continue;
            		}
            		if (CollectionSchema.images_urlstub_sxt.getSolrFieldName().equals(fieldName)) {
            			imagesStubs.add(value.stringValue());
            			continue;
            		}

            		//missing: "code","faviconCode"
            	}

            	writeDocEnd(writer, snippets, url, urlhash, descriptions, docTitle, path, imagesProtocolObjs,
						imagesStubs);
            } catch (final Exception ee) {
                ConcurrentLog.logException(ee);
                writer.write("\"description\":\"\"\n}\n");
            }
            writtenDocs++;
        }
	}

	/**
	 * Append to the writer the YaCy json representation of Solr documents.
	 * 
	 * @param writer        an open output writer. Must not be null.
	 * @param documents     the documents to render. Must not be null.
	 * @param snippets      snippets Solr computed text snippets (highlighting).
	 * @throws IOException when an unexpected error occurred while writing
	 */
	private void writeDocs(final Writer writer, final SolrDocumentList documents,
			final Map<String, Collection<String>> snippets) throws IOException {
		int writtenDocs = 0;
        for (final SolrDocument doc : documents) {
        	if(writtenDocs > 0) {
        		writer.write(",\n".toCharArray());
        	}
            try {
            	writer.write("{\n".toCharArray());
            	MultiProtocolURL url = null;
            	String urlhash = null;
            	List<String> descriptions = new ArrayList<>();
            	String docTitle = "";
            	StringBuilder path = new StringBuilder(80);
            	List<Object> imagesProtocolObjs = new ArrayList<>();
            	List<String> imagesStubs = new ArrayList<>();
        	
            	for (final Entry<String, Object> fieldEntry : doc) {
            		final String fieldName = fieldEntry.getKey();
            		final Object value = fieldEntry.getValue();
        		
            		if(value == null) {
            			continue;
            		}

            		// apply generic matching rule
            		String stag = field2tag.get(fieldName);
            		if (stag != null) {
            			solitaireTag(writer, stag, value.toString());
            			continue;
            		}
            		// some special handling here
            		if (CollectionSchema.sku.getSolrFieldName().equals(fieldName)) {
            			url = writeLink(writer, value.toString());
            			continue;
            		}
            		
            		if (CollectionSchema.title.getSolrFieldName().equals(fieldName)) {
            			if(value instanceof Iterable<?>) {
            				/* Handle multivalued field */
            				for(final Object valueItem : (Iterable<?>)value) {
            					docTitle = valueItem.toString();
            				}
            			} else {
            				docTitle = value.toString();
            			}
            			continue;
            		}
            		
            		if (CollectionSchema.description_txt.getSolrFieldName().equals(fieldName)) {
            			if(value instanceof Iterable<?>) {
            				/* Handle multivalued field */
            				for(final Object valueItem : (Iterable<?>)value) {
            					final String description = valueItem.toString();
            					descriptions.add(description);
            				}
            			} else {
            				final String description = value.toString();
            				descriptions.add(description);
            			}
            			continue;
            		}
            		
            		if (CollectionSchema.id.getSolrFieldName().equals(fieldName)) {
            			urlhash = value.toString();
            			solitaireTag(writer, "guid", urlhash);
            			continue;
            		}
            		
            		if (CollectionSchema.url_paths_sxt.getSolrFieldName().equals(fieldName)) {
            			if(value instanceof Iterable<?>) {
            				/* Handle multivalued field */
            				for(final Object valueItem : (Iterable<?>)value) {
            					path.append('/').append(valueItem.toString());
            				}
            			} else {
            				path.append('/').append(value.toString());
            			}
            			continue;
            		}
            		
            		if (CollectionSchema.last_modified.getSolrFieldName().equals(fieldName)  && value instanceof Date) {
            			solitaireTag(writer, "pubDate", HeaderFramework.formatRFC1123((Date)value));
            			continue;
            		}
            		
            		if (CollectionSchema.size_i.getSolrFieldName().equals(fieldName) && value instanceof Integer) {
            			writeSize(writer, ((Integer)value).intValue());
            			continue;
            		}
            		
            		if (CollectionSchema.images_protocol_sxt.getSolrFieldName().equals(fieldName)) {
            			if(value instanceof Iterable<?>) {
            				/* Handle multivalued field */
            				for(final Object valueItem : (Iterable<?>)value) {
            					imagesProtocolObjs.add(valueItem.toString());
            				}
            			} else {
            				imagesProtocolObjs.add(value.toString());
            			}
            			continue;
            		}
            		
            		if (CollectionSchema.images_urlstub_sxt.getSolrFieldName().equals(fieldName)) {
            			if(value instanceof Iterable<?>) {
            				/* Handle multivalued field */
            				for(final Object valueItem : (Iterable<?>)value) {
            					imagesStubs.add(valueItem.toString());
            				}
            			} else {
            				imagesStubs.add(value.toString());
            			}
            			continue;
            		}

            		//missing: "code","faviconCode"
            	}

            	writeDocEnd(writer, snippets, url, urlhash, descriptions, docTitle, path, imagesProtocolObjs,
						imagesStubs);
            } catch (final Exception ee) {
                ConcurrentLog.logException(ee);
                writer.write("\"description\":\"\"\n}\n");
            }
            writtenDocs++;
        }
	}

	/**
	 * Append information about the Solr document size to the writer
	 * @param writer an open output writer. Must not be null.
	 * @param size the size of the indexed document
	 * @throws IOException when an unexpected error occurred while writing
	 */
	private void writeSize(final Writer writer, int size) throws IOException {
		int sizekb = size / 1024;
		int sizemb = sizekb / 1024;
		solitaireTag(writer, "size", Integer.toString(size));
		solitaireTag(writer, "sizename", sizemb > 0 ? (Integer.toString(sizemb) + " mbyte") : sizekb > 0 ? (Integer.toString(sizekb) + " kbyte") : (Integer.toString(size) + " byte"));
	}
	
	/**
	 * Append information about the Solr document URL to the writer
	 * @param writer an open output writer. Must no be null.
	 * @param sku the Solr document URL as a String.
	 * @return a MultiProtocolURL instance built from the URL string, or null when the URL string is malformed.
	 * @throws IOException when an unexpected error occurred while writing
	 */
	private MultiProtocolURL writeLink(final Writer writer, final String sku)
			throws IOException {
		MultiProtocolURL url;
        try {
            url = new MultiProtocolURL(sku);
            String filename = url.getFileName();
            solitaireTag(writer, "link", sku);
            solitaireTag(writer, "file", filename);
        } catch (final MalformedURLException e) {
        	url = null;
        }
        return url;
	}
	
	/**
	 * Append to the writer the end of the YaCy json representation of the Solr
	 * document.
	 */
	private void writeDocEnd(final Writer writer, final Map<String, Collection<String>> snippets,
			final MultiProtocolURL url, final String urlhash, final List<String> descriptions, final String docTitle, final StringBuilder path,
			final List<Object> imagesProtocolObjs, final List<String> imagesStubs) throws IOException {
		if (Math.min(imagesProtocolObjs.size(), imagesStubs.size()) > 0) {
			List<String> imagesProtocols = CollectionConfiguration.indexedList2protocolList(imagesProtocolObjs, imagesStubs.size());
			String imageurl = imagesProtocols.get(0) + "://" + imagesStubs.get(0);
			solitaireTag(writer, "image", imageurl);
		} else {
			if (url != null && Response.docTypeExt(MultiProtocolURL.getFileExtension(url.getFile()).toLowerCase(Locale.ROOT)) == Response.DT_IMAGE) {
				solitaireTag(writer, "image", url.toNormalform(true));
			}
		}
         
		// compute snippet from texts            
		solitaireTag(writer, "path", path.toString());
		solitaireTag(writer, "title", docTitle.length() == 0 ? path.toString() : docTitle.replaceAll("\"", "'"));
		Collection<String> snippet = urlhash == null ? null : snippets.get(urlhash);
		if (snippet == null) {snippet = new LinkedHashSet<>(); snippet.addAll(descriptions);}
		OpensearchResponseWriter.removeSubsumedTitle(snippet, docTitle);
		String snippetstring = snippet == null || snippet.size() == 0 ? (descriptions.size() > 0 ? descriptions.get(0) : "") : OpensearchResponseWriter.getLargestSnippet(snippet);
		if (snippetstring != null && snippetstring.length() > 140) {
			snippetstring = snippetstring.substring(0, 140);
			int sp = snippetstring.lastIndexOf(' ');
			if (sp >= 0) snippetstring = snippetstring.substring(0, sp) + " ..."; else snippetstring = snippetstring + "...";
		}
		writer.write("\"description\":"); writer.write(JSONObject.quote(snippetstring)); writer.write("\n}\n");
	}

    public static void solitaireTag(final Writer writer, final String tagname, String value) throws IOException {
        if (value == null) return;
        writer.write('"'); writer.write(tagname); writer.write("\":"); writer.write(JSONObject.quote(value)); writer.write(','); writer.write('\n');
    }

    private static void facetEntry(final Writer writer, String modifier, String propname, final String value) throws IOException {
        modifier = modifier.replaceAll("\"", "'").trim();
        propname = propname.replaceAll("\"", "'").trim();
        writer.write("{\"name\":"); writer.write(JSONObject.quote(propname));
        writer.write(",\"count\":"); writer.write(JSONObject.quote(value.replaceAll("\"", "'").trim())); 
        writer.write(",\"modifier\":"); writer.write(JSONObject.quote(modifier+"%3A"+propname));
        writer.write("}");
    }
}
/**
{
  "channels": [{
    "title": "YaCy P2P-Search for uni-mainz",
    "description": "Search for uni-mainz",
    "link": "http://localhost:8090/yacysearch.html?query=uni-mainz&amp;resource=local&amp;contentdom=text&amp;verify=-UNRESOLVED_PATTERN-",
    "image": {
      "url": "http://localhost:8090/env/grafics/yacy.gif",
      "title": "Search for uni-mainz",
      "link": "http://localhost:8090/yacysearch.html?query=uni-mainz&amp;resource=local&amp;contentdom=text&amp;verify=-UNRESOLVED_PATTERN-"
    },
    "totalResults": "1986",
    "startIndex": "0",
    "itemsPerPage": "10",
    "searchTerms": "uni-mainz",
    "items": [
    {
      "title": "From dark matter to school experiments: Physicists meet in Mainz",
      "link": "http://www.phmi.uni-mainz.de/5305.php",
      "code": "",
      "description": "",
      "pubDate": "Mon, 10 Sep 2012 10:25:36 +0000",
      "size": "15927",
      "sizename": "15 kbyte",
      "guid": "7NYsT4NwCWgB",
      "faviconCode": "d6ce1c0b",
      "host": "www.phmi.uni-mainz.de",
      "path": "/5305.php",
      "file": "/5305.php",
      "urlhash": "7NYsT4NwCWgB",
      "ranking": "6983282"
    }
    ,
    ..
  }],
"navigation": [
{
  "facetname": "filetypes",
  "displayname": "Filetype",
  "type": "String",
  "min": "0",
  "max": "0",
  "mean": "0",
  "elements": [
    {"name": "php", "count": "8", "modifier": "filetype%3Aphp", "url": "/yacysearch.json?query=uni-mainz+filetype%3Aphp&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "html", "count": "1", "modifier": "filetype%3Ahtml", "url": "/yacysearch.json?query=uni-mainz+filetype%3Ahtml&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"}
  ]
},
{
  "facetname": "protocols",
  "displayname": "Protocol",
  "type": "String",
  "min": "0",
  "max": "0",
  "mean": "0",
  "elements": [
    {"name": "http", "count": "13", "modifier": "%2Fhttp", "url": "/yacysearch.json?query=uni-mainz+%2Fhttp&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "https", "count": "1", "modifier": "%2Fhttps", "url": "/yacysearch.json?query=uni-mainz+%2Fhttps&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"}
  ]
},
{
  "facetname": "domains",
  "displayname": "Domains",
  "type": "String",
  "min": "0",
  "max": "0",
  "mean": "0",
  "elements": [
    {"name": "www.geo.uni-frankfurt.de", "count": "1", "modifier": "site%3Awww.geo.uni-frankfurt.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.geo.uni-frankfurt.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.info.jogustine.uni-mainz.de", "count": "1", "modifier": "site%3Awww.info.jogustine.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.info.jogustine.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.fb09.uni-mainz.de", "count": "1", "modifier": "site%3Awww.fb09.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.fb09.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.studgen.uni-mainz.de", "count": "1", "modifier": "site%3Awww.studgen.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.studgen.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "twitter.com", "count": "1", "modifier": "site%3Atwitter.com", "url": "/yacysearch.json?query=uni-mainz+site%3Atwitter.com&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.theaterwissenschaft.uni-mainz.de", "count": "1", "modifier": "site%3Awww.theaterwissenschaft.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.theaterwissenschaft.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.uni-mainz.de", "count": "1", "modifier": "site%3Awww.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.fb06.uni-mainz.de", "count": "1", "modifier": "site%3Awww.fb06.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.fb06.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.familienservice.uni-mainz.de", "count": "1", "modifier": "site%3Awww.familienservice.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.familienservice.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.zdv.uni-mainz.de", "count": "1", "modifier": "site%3Awww.zdv.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.zdv.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "zope.verwaltung.uni-mainz.de", "count": "1", "modifier": "site%3Azope.verwaltung.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Azope.verwaltung.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.geo.uni-mainz.de", "count": "1", "modifier": "site%3Awww.geo.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.geo.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.bio.uni-mainz.de", "count": "1", "modifier": "site%3Awww.bio.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.bio.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "www.phmi.uni-mainz.de", "count": "1", "modifier": "site%3Awww.phmi.uni-mainz.de", "url": "/yacysearch.json?query=uni-mainz+site%3Awww.phmi.uni-mainz.de&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"}
  ]
},
{
  "facetname": "topics",
  "displayname": "Topics",
  "type": "String",
  "min": "0",
  "max": "0",
  "mean": "0",
  "elements": [
    {"name": "des", "count": "3", "modifier": "des", "url": "/yacysearch.json?query=uni-mainz+des&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "auf", "count": "2", "modifier": "auf", "url": "/yacysearch.json?query=uni-mainz+auf&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "willkommen", "count": "2", "modifier": "willkommen", "url": "/yacysearch.json?query=uni-mainz+willkommen&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "biologie", "count": "1", "modifier": "biologie", "url": "/yacysearch.json?query=uni-mainz+biologie&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "johannes", "count": "1", "modifier": "johannes", "url": "/yacysearch.json?query=uni-mainz+johannes&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "weiterleitung", "count": "1", "modifier": "weiterleitung", "url": "/yacysearch.json?query=uni-mainz+weiterleitung&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "archiv", "count": "1", "modifier": "archiv", "url": "/yacysearch.json?query=uni-mainz+archiv&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "entdecken", "count": "1", "modifier": "entdecken", "url": "/yacysearch.json?query=uni-mainz+entdecken&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "gutenberg", "count": "1", "modifier": "gutenberg", "url": "/yacysearch.json?query=uni-mainz+gutenberg&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "heimverzeichnis", "count": "1", "modifier": "heimverzeichnis", "url": "/yacysearch.json?query=uni-mainz+heimverzeichnis&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "school", "count": "1", "modifier": "school", "url": "/yacysearch.json?query=uni-mainz+school&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"},
    {"name": "vernetzung", "count": "1", "modifier": "vernetzung", "url": "/yacysearch.json?query=uni-mainz+vernetzung&amp;maximumRecords=10&amp;resource=local&amp;verify=iffresh&amp;nav=hosts,authors,namespace,topics,filetype,protocol&amp;urlmaskfilter=.*&amp;prefermaskfilter=&amp;cat=href&amp;constraint=&amp;contentdom=text&amp;former=uni-mainz&amp;startRecord=0"}
  ]
}
],
"totalResults": "1986"
}]
}
*/
