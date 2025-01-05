/**
 *  OpensearchResponseWriter
 *  Copyright 2012 by Michael Peter Christen
 *  First released 06.08.2012 at https://yacy.net
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.common.util.XML;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;

import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.lod.vocabulary.DublinCore;
import net.yacy.cora.lod.vocabulary.Geo;
import net.yacy.cora.lod.vocabulary.YaCyMetadata;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.crawler.retrieval.Response;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

/**
 * Solr response writer producing an OpenSearch representation in RSS 2.0 format.
 * @see <a href="https://github.com/dewitt/opensearch/blob/master/opensearch-1-1-draft-6.md#example-of-opensearch-response-elements-in-rss-20">Example of OpenSearch response elements in RSS 2.0</a>
 */
public class OpensearchResponseWriter implements QueryResponseWriter, SolrjResponseWriter {

    // define a list of simple YaCySchema -> RSS Token matchings
    private static final Map<String, String> field2tag = new HashMap<>();

    // pre-select a set of YaCy schema fields for the solr searcher which should cause a better caching
    private static final CollectionSchema[] extrafields = new CollectionSchema[]{
        CollectionSchema.id, CollectionSchema.title, CollectionSchema.description_txt, CollectionSchema.text_t,
        CollectionSchema.h1_txt, CollectionSchema.h2_txt, CollectionSchema.h3_txt, CollectionSchema.h4_txt, CollectionSchema.h5_txt, CollectionSchema.h6_txt,
        };
    static final Set<String> SOLR_FIELDS = new HashSet<>();
    static {
        field2tag.put(CollectionSchema.coordinate_p.getSolrFieldName() + "_0_coordinate", Geo.Lat.getURIref());
        field2tag.put(CollectionSchema.coordinate_p.getSolrFieldName() + "_1_coordinate", Geo.Long.getURIref());
        field2tag.put(CollectionSchema.publisher_t.getSolrFieldName(), DublinCore.Publisher.getURIref());
        field2tag.put(CollectionSchema.author.getSolrFieldName(), DublinCore.Creator.getURIref());
        SOLR_FIELDS.addAll(field2tag.keySet());
        for (CollectionSchema field: extrafields) SOLR_FIELDS.add(field.getSolrFieldName());
    }

    private String title;

    public static class ResHead {
        public long offset, rows, numFound;
        //public int status, QTime;
        //public String df, q, wt;
        //public float maxScore;
    }

    public OpensearchResponseWriter() {
        super();
    }

    public void setTitle(String searchPageTitle) {
        this.title = searchPageTitle;
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
        
        final NamedList<?> values = rsp.getValues();
        
        final Object responseObj = rsp.getResponse();
        
        assert values.get("response") != null;

        write(writer, request, values, responseObj);
    }
    
    @Override
	public void write(Writer writer, SolrQueryRequest request, String coreName, QueryResponse rsp) throws IOException {
        
        final NamedList<Object> values = rsp.getResponse();
        
        final SolrDocumentList documents = rsp.getResults();
        	
        write(writer, request, values, documents);
	}
    
	/**
	 * Append to the writer the OpenSearch RSS representation of the Solr results.
	 * @param writer  an open output writer. Must not be null.
	 * @param request the initial Solr request. Must not be null.
	 * @param values  the response values. Must not be null.
	 * @param rsp     the Solr response header.
	 * @throws IOException when a write error occurred
	 */
	private void write(final Writer writer, final SolrQueryRequest request, final NamedList<?> values,
			final Object responseObj) throws IOException {
        final ResHead resHead = new ResHead();
        resHead.rows = request.getOriginalParams().getLong("rows", -1);
        
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> facetCounts = (SimpleOrderedMap<Object>) values.get("facet_counts");
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> facetFields = facetCounts == null || facetCounts.size() == 0 ? null : (SimpleOrderedMap<Object>) facetCounts.get("facet_fields");
        
		final Object highlightingObj = values.get("highlighting");
		final Map<String, Collection<String>> snippets = highlightingObj instanceof NamedList
				? OpensearchResponseWriter.snippetsFromHighlighting((NamedList<?>) highlightingObj)
				: new HashMap<>();
        
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

        openTag(writer, "yacy:navigation");
        
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
        
        if (domains != null) {
            openTag(writer, "yacy:facet name=\"domains\" displayname=\"Domains\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\"");
            for (Map.Entry<String, Integer> entry: domains) facetEntry(writer, "site", entry.getKey(), Integer.toString(entry.getValue()));
            closeTag(writer, "yacy:facet");
        }
        if (filetypes != null) {
            openTag(writer, "yacy:facet name=\"filetypes\" displayname=\"Filetypes\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\"");
            for (Map.Entry<String, Integer> entry: filetypes) facetEntry(writer, "filetype", entry.getKey(), Integer.toString(entry.getValue()));
            closeTag(writer, "yacy:facet");
        }
        if (protocols != null) {
            openTag(writer, "yacy:facet name=\"protocols\" displayname=\"Protocols\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\"");
            for (Map.Entry<String, Integer> entry: protocols) facetEntry(writer, "protocol", entry.getKey(), Integer.toString(entry.getValue()));
            closeTag(writer, "yacy:facet");
        }
        if (authors != null) {
            openTag(writer, "yacy:facet name=\"authors\" displayname=\"Authors\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\"");
            for (Map.Entry<String, Integer> entry: authors) facetEntry(writer, "author", entry.getKey(), Integer.toString(entry.getValue()));
            closeTag(writer, "yacy:facet");
        }
        if (collections != null) {
            openTag(writer, "yacy:facet name=\"collections\" displayname=\"Collections\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\"");
            for (Map.Entry<String, Integer> entry: collections) facetEntry(writer, "collection", entry.getKey(), Integer.toString(entry.getValue()));
            closeTag(writer, "yacy:facet");
        }
        closeTag(writer, "yacy:navigation");
        
        closeTag(writer, "channel");
        writer.write("</rss>\n".toCharArray());
    }
    
	/**
	 * Append to the writer the header of the OpenSearch RSS representation.
	 * @param writer an open output writer. Must not be null.
	 * @param resHead the calculated results head. Must not be null.
	 * @throws IOException when an unexpected error occurred while writing
	 */
	private void writeHeader(final Writer writer, final ResHead resHead)
			throws IOException {
        // write header
        writer.write((
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<?xml-stylesheet type='text/xsl' href='/yacysearch.xsl' version='1.0'?>\n" +
                        "<rss version=\"2.0\"\n" +
                        "    xmlns:yacy=\"http://www.yacy.net/\"\n" +
                        "    xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\"\n" +
                        "    xmlns:media=\"http://search.yahoo.com/mrss/\"\n" +
                        "    xmlns:atom=\"http://www.w3.org/2005/Atom\"\n" +
                        "    xmlns:dc=\"" + DublinCore.NAMESPACE + "\"\n" +
                        "    xmlns:geo=\"" + Geo.NAMESPACE + "\"\n" +
                        ">\n").toCharArray());
        openTag(writer, "channel");
        solitaireTag(writer, "opensearch:totalResults", Long.toString(resHead.numFound));
        solitaireTag(writer, "opensearch:startIndex", Long.toString(resHead.offset));
        solitaireTag(writer, "opensearch:itemsPerPage", Long.toString(resHead.rows));
        solitaireTag(writer, RSSMessage.Token.title.name(), this.title);
        writer.write("<atom:link rel=\"search\" href=\"/opensearchdescription.xml\" type=\"application/opensearchdescription+xml\"/>");
        solitaireTag(writer, "description", "Search Result");
	}

	/**
	 * Append to the writer the OpenSearch RSS representation of Solr documents.
	 * 
	 * @param writer    an open output writer. Must not be null.
	 * @param documents the documents to render. Must not be null.
	 * @param snippets  snippets Solr computed text snippets (highlighting).
	 * @throws IOException when an unexpected error occurred while writing
	 */
	private void writeDocs(final Writer writer, final SolrDocumentList documents,
			final Map<String, Collection<String>> snippets) throws IOException {
		// parse body
        String urlhash = null;
        MultiProtocolURL url = null;
        for (SolrDocument doc: documents) {
            openTag(writer, "item");
            List<String> texts = new ArrayList<>();
            List<String> descriptions = new ArrayList<>();
            String docTitle = "";
            List<Object> images_protocol_obj = new ArrayList<>();
        	List<String> images_stub = new ArrayList<>();
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
                
                // take apart the url
                if (CollectionSchema.sku.getSolrFieldName().equals(fieldName)) {
                    url = writeLink(writer, value.toString());
                    continue;
                }
                
                // if the rule is not generic, use the specific here
                if (CollectionSchema.id.getSolrFieldName().equals(fieldName)) {
                    urlhash = value.toString();
                    solitaireTag(writer, RSSMessage.Token.guid.name(), urlhash, "isPermaLink=\"false\"");
                    continue;
                }
                if (CollectionSchema.title.getSolrFieldName().equals(fieldName)) {
                	if(value instanceof Iterable<?>) {
                        /* Handle multivalued field */
                		for(final Object valueItem : (Iterable<?>)value) {
                            docTitle = valueItem.toString();
                            texts.add(docTitle);                			
                		}
                	} else {
                        docTitle = value.toString();
                        texts.add(docTitle);        		
                	}
                    continue;
                }
                if (CollectionSchema.last_modified.getSolrFieldName().equals(fieldName) && value instanceof Date) {
                    solitaireTag(writer, RSSMessage.Token.pubDate.name(), HeaderFramework.formatRFC1123((Date)value));
                    continue;
                }
                if (CollectionSchema.description_txt.getSolrFieldName().equals(fieldName)) {
                	if(value instanceof Iterable<?>) {
                        /* Handle multivalued field */
                		for(final Object valueItem : (Iterable<?>)value) {
                            final String description = valueItem.toString();
                            descriptions.add(description);
                            texts.add(description);
                            solitaireTag(writer, DublinCore.Description.getURIref(), description);                			
                		}
                	} else {
                        final String description = value.toString();
                        descriptions.add(description);
                        texts.add(description);
                        solitaireTag(writer, DublinCore.Description.getURIref(), description);                		
                	}

                    continue;
                }
                if (CollectionSchema.text_t.getSolrFieldName().equals(fieldName)) {
                    texts.add(value.toString());
                    continue;
                }
                if (CollectionSchema.size_i.getSolrFieldName().equals(fieldName) && value instanceof Integer) {
                    int size = ((Integer)value).intValue();
                    solitaireTag(writer, YaCyMetadata.size.getURIref(), Integer.toString(size));
                    solitaireTag(writer, YaCyMetadata.sizename.getURIref(), RSSMessage.sizename(size));
                    continue;
                }
                if (CollectionSchema.h1_txt.getSolrFieldName().equals(fieldName) || CollectionSchema.h2_txt.getSolrFieldName().equals(fieldName) ||
                    CollectionSchema.h3_txt.getSolrFieldName().equals(fieldName) || CollectionSchema.h4_txt.getSolrFieldName().equals(fieldName) ||
                    CollectionSchema.h5_txt.getSolrFieldName().equals(fieldName) || CollectionSchema.h6_txt.getSolrFieldName().equals(fieldName)) {
                	if(value instanceof Iterable<?>) {
                        // because these are multi-valued fields, there can be several of each
                		for(final Object valueItem : (Iterable<?>)value) {
                			texts.add(valueItem.toString());                			
                		}
                	} else {
                		texts.add(value.toString());       		
                	}
                    continue;
                }
                if (CollectionSchema.images_protocol_sxt.getSolrFieldName().equals(fieldName)) {
                	if(value instanceof Iterable<?>) {
                        /* Handle multivalued field */
                		for(final Object valueItem : (Iterable<?>)value) {
                        	images_protocol_obj.add(valueItem.toString());            			
                		}
                	} else {
                    	images_protocol_obj.add(value.toString());       		
                	}
                    continue;
                }
                if (CollectionSchema.images_urlstub_sxt.getSolrFieldName().equals(fieldName)) {
                	if(value instanceof Iterable<?>) {
                        /* Handle multivalued field */
                		for(final Object valueItem : (Iterable<?>)value) {
                        	images_stub.add(valueItem.toString());            			
                		}
                	} else {
                    	images_stub.add(value.toString());       		
                	}
                    continue;
                }
            }
			
			final Object keywordsObj = doc.get(CollectionSchema.keywords.getSolrFieldName());
			final String keywords = (keywordsObj instanceof String) ? (String)keywordsObj : null;
            
            writeDocEnd(writer, snippets, urlhash, url, keywords, texts, descriptions, docTitle, images_protocol_obj,
					images_stub);
        }
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
		// parse body
        SolrIndexSearcher searcher = request.getSearcher();
        String urlhash = null;
        MultiProtocolURL url = null;
        final DocIterator iterator = documents.iterator();
        while(iterator.hasNext()) {
            openTag(writer, "item");
            int id = iterator.nextDoc();
            Document doc = searcher.doc(id, SOLR_FIELDS);
            List<String> texts = new ArrayList<>();
            List<String> descriptions = new ArrayList<>();
            String docTitle = "";
            List<Object> images_protocol_obj = new ArrayList<>();
        	List<String> images_stub = new ArrayList<>();
            for (final IndexableField value : doc.getFields()) {
                String fieldName = value.name();

                // apply generic matching rule
                String stag = field2tag.get(fieldName);
                if (stag != null) {
                    solitaireTag(writer, stag, value.stringValue());
                    continue;
                }
                
                // take apart the url
                if (CollectionSchema.sku.getSolrFieldName().equals(fieldName)) {
                	url = writeLink(writer, value.stringValue());
                    continue;
                }
                
                // if the rule is not generic, use the specific here
                if (CollectionSchema.id.getSolrFieldName().equals(fieldName)) {
                    urlhash = value.stringValue();
                    solitaireTag(writer, RSSMessage.Token.guid.name(), urlhash, "isPermaLink=\"false\"");
                    continue;
                }
                if (CollectionSchema.title.getSolrFieldName().equals(fieldName)) {
                    docTitle = value.stringValue();
                    texts.add(docTitle);
                    continue;
                }
                if (CollectionSchema.last_modified.getSolrFieldName().equals(fieldName)) {
                    Date d = new Date(Long.parseLong(value.stringValue()));
                    solitaireTag(writer, RSSMessage.Token.pubDate.name(), HeaderFramework.formatRFC1123(d));
                    continue;
                }
                if (CollectionSchema.description_txt.getSolrFieldName().equals(fieldName)) {
                    String description = value.stringValue();
                    descriptions.add(description);
                    solitaireTag(writer, DublinCore.Description.getURIref(), description);
                    texts.add(description);
                    continue;
                }
                if (CollectionSchema.text_t.getSolrFieldName().equals(fieldName)) {
                    texts.add(value.stringValue());
                    continue;
                }
                if (CollectionSchema.size_i.getSolrFieldName().equals(fieldName)) {
                    int size = value.numericValue().intValue();
                    solitaireTag(writer, YaCyMetadata.size.getURIref(), Integer.toString(size));
                    solitaireTag(writer, YaCyMetadata.sizename.getURIref(), RSSMessage.sizename(size));
                    continue;
                }
                if (CollectionSchema.h1_txt.getSolrFieldName().equals(fieldName) || CollectionSchema.h2_txt.getSolrFieldName().equals(fieldName) ||
                    CollectionSchema.h3_txt.getSolrFieldName().equals(fieldName) || CollectionSchema.h4_txt.getSolrFieldName().equals(fieldName) ||
                    CollectionSchema.h5_txt.getSolrFieldName().equals(fieldName) || CollectionSchema.h6_txt.getSolrFieldName().equals(fieldName)) {
                    // because these are multi-valued fields, there can be several of each
                    texts.add(value.stringValue());
                    continue;
                }
                if (CollectionSchema.images_protocol_sxt.getSolrFieldName().equals(fieldName)) {
                	images_protocol_obj.add(value.stringValue());
                    continue;
                }
                if (CollectionSchema.images_urlstub_sxt.getSolrFieldName().equals(fieldName)) {
                	images_stub.add(value.stringValue());
                    continue;
                }
            }
            
			final Object keywordsObj = doc.get(CollectionSchema.keywords.getSolrFieldName());
			final String keywords = (keywordsObj instanceof String) ? (String)keywordsObj : null;
            
            writeDocEnd(writer, snippets, urlhash, url, keywords, texts, descriptions, docTitle, images_protocol_obj,
					images_stub);
        }
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
		solitaireTag(writer, RSSMessage.Token.link.name(), sku);
		MultiProtocolURL url; 
		try {
		    url = new MultiProtocolURL(sku);
		    solitaireTag(writer, YaCyMetadata.host.getURIref(), url.getHost());
		    solitaireTag(writer, YaCyMetadata.path.getURIref(), url.getPath());
		    solitaireTag(writer, YaCyMetadata.file.getURIref(), url.getFileName());
		} catch (final MalformedURLException e) {
			url = null;
		}
		return url;
	}

	/**
	 * Append to the writer the end of the RSS OpenSearch representation of the Solr
	 * document.
	 */
	private void writeDocEnd(final Writer writer, final Map<String, Collection<String>> snippets, final String urlhash,
			final MultiProtocolURL url, final String keywords, final List<String> texts, final List<String> descriptions, final String docTitle,
			final List<Object> imagesProtocolObjs, final List<String> imagesStubs) throws IOException {
		if (Math.min(imagesProtocolObjs.size(), imagesStubs.size()) > 0) {
			List<String> imagesProtocols = CollectionConfiguration.indexedList2protocolList(imagesProtocolObjs, imagesStubs.size());
			String imageurl = imagesProtocols.get(0) + "://" + imagesStubs.get(0);
		     writer.write("<media:content medium=\"image\" url=\"");
		     XML.escapeCharData(imageurl, writer); writer.write("\"/>\n");
		} else {
			if (url != null && Response.docTypeExt(MultiProtocolURL.getFileExtension(url.getFile()).toLowerCase(Locale.ROOT)) == Response.DT_IMAGE) {
				writer.write("<media:content medium=\"image\" url=\"");
		        XML.escapeCharData(url.toNormalform(true), writer); writer.write("\"/>\n");
			}
		}
		
		// compute snippet from texts
		solitaireTag(writer, RSSMessage.Token.title.name(), docTitle.length() == 0 ? (texts.size() == 0 ? "" : texts.get(0)) : docTitle);
		Collection<String> snippet = urlhash == null ? null : snippets.get(urlhash);
		String tagname = RSSMessage.Token.description.name();
		if (snippet == null || snippet.size() == 0) {
		    writer.write("<"); writer.write(tagname); writer.write('>');
		    for (String d: descriptions) {
		        XML.escapeCharData(d, writer);
		    }
		    writer.write("</"); writer.write(tagname); writer.write(">\n");
		} else {
		    removeSubsumedTitle(snippet, docTitle);
		    solitaireTag(writer, tagname, getLargestSnippet(snippet)); // snippet may be size=0
		}

		if(keywords != null) {
			solitaireTag(writer, DublinCore.Subject.getURIref(), keywords);
		}
		
		closeTag(writer, "item");
	}
	
    
	/**
	 * produce snippets from solr (they call that 'highlighting')
	 * 
	 * @param sorlHighlighting highlighting from Solr
	 * @return a map from urlhashes to a list of snippets for that url
	 */
	public static Map<String, Collection<String>> snippetsFromHighlighting(final NamedList<?> sorlHighlighting) {
		final Map<String, Collection<String>> snippets = new HashMap<>();
		if (sorlHighlighting == null) {
			return snippets;
		}
		for (final Entry<String, ?> highlightingEntry : sorlHighlighting) {
			final String urlHash = highlightingEntry.getKey();
			final Object highlights = highlightingEntry.getValue();
			if (highlights instanceof SimpleOrderedMap) {
				final LinkedHashSet<String> urlSnippets = new LinkedHashSet<>();
				for (final Entry<String, ?> entry : (SimpleOrderedMap<?>) highlights) {
					final Object texts = entry.getValue();
					if (texts instanceof String[]) {
						Collections.addAll(urlSnippets, (String[]) texts);
					}
				}
				snippets.put(urlHash, urlSnippets);
			}
		}
		return snippets;
	}
    
    final static Pattern keymarks = Pattern.compile("<b>|</b>");
    
    public static void removeSubsumedTitle(Collection<String> snippets, String title) {
        if (title == null || title.length() == 0 || snippets == null || snippets.size() == 0) return;
        snippets.remove(title);
        String tlc = title.toLowerCase();
        Iterator<String> i = snippets.iterator();
        while (i.hasNext()) {
            String s = i.next().toLowerCase();
            s = keymarks.matcher(s).replaceAll("");
            if (tlc.toLowerCase().indexOf(s) >= 0 || s.toLowerCase().indexOf(tlc) >= 0) i.remove();
        }
        return;
    }

    /**
     * @param snippets snippets collection eventually empty
     * @return the largest snippet containing at least a space character among the list, or null
     */
    public static String getLargestSnippet(final Collection<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
        	return null;
        }
        String l = null;
        for (final String s: snippets) {
			if ((l == null || s.length() > l.length()) && s.length() > 1 && s.indexOf(' ', 1) > 0) {
				l = s;
			}
        }
        if(l != null) {
        	l = l.replaceAll("\"", "'");
        }
        return l;
    }
    
    public static void openTag(final Writer writer, final String tag) throws IOException {
        writer.write('<'); writer.write(tag); writer.write(">\n");
    }

    public static void closeTag(final Writer writer, final String tag) throws IOException {
        writer.write("</"); writer.write(tag); writer.write(">\n");
    }

    public static void solitaireTag(final Writer writer, final String tagname, String value) throws IOException {
        if (value == null || value.length() == 0) return;
        writer.write("<"); writer.write(tagname); writer.write('>');
        XML.escapeCharData(value, writer);
        writer.write("</"); writer.write(tagname); writer.write(">\n");
    }

    public static void solitaireTag(final Writer writer, final String tagname, String value, String attr) throws IOException {
        if (value == null || value.length() == 0) return;
        writer.write("<"); writer.write(tagname);
        if (attr.charAt(0) != ' ') writer.write(' ');
        writer.write(attr);
        writer.write('>');
        writer.write(value);
        writer.write("</"); writer.write(tagname); writer.write(">\n");
    }

    private static void facetEntry(final Writer writer, final String modifier, final String propname, String value) throws IOException {
        writer.write("<yacy:element name=\""); XML.escapeCharData(propname, writer);
        writer.write("\" count=\""); XML.escapeCharData(value, writer);
        writer.write("\" modifier=\""); writer.write(modifier); writer.write("%3A"); XML.escapeCharData(propname, writer);
        writer.write("\" />\n");
    }

}
