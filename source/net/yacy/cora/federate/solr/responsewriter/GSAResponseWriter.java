/**
 *  GSAResponseWriter
 *  Copyright 2012 by Michael Peter Christen
 *  First released 14.08.2012 at http://yacy.net
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
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.XML;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.CommonPattern;
import net.yacy.http.servlets.GSAsearchServlet;
import net.yacy.peers.operation.yacyVersion;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;

/**
 * implementation of a GSA search result.
 * example: GET /gsa/searchresult?q=chicken+teriyaki&output=xml&client=test&site=test&sort=date:D:S:d1
 * for a xml reference, see https://developers.google.com/search-appliance/documentation/614/xml_reference
 */
public class GSAResponseWriter implements QueryResponseWriter, SolrjResponseWriter {

    private static String YaCyVer = null;
    private static final char lb = '\n';
    private enum GSAToken {
        CACHE_LAST_MODIFIED, // Date that the document was crawled, as specified in the Date HTTP header when the document was crawled for this index.
        CRAWLDATE,  // An optional element that shows the date when the page was crawled. It is shown only for pages that have been crawled within the past two days.
        U,          // The URL of the search result.
        UE,         // The URL-encoded version of the URL that is in the U parameter.
        GD,         // Contains the description of a KeyMatch result..
        T,          // The title of the search result.
        RK,         // Provides a ranking number used internally by the search appliance.
        ENT_SOURCE, // Identifies the application ID (serial number) of the search appliance that contributes to a result. Example: <ENT_SOURCE>S5-KUB000F0ADETLA</ENT_SOURCE>
        FS,         // Additional details about the search result.
        R,          // details of an individual search result.
        S,          // The snippet for the search result. Query terms appear in bold in the results. Line breaks are included for proper text wrapping.
        LANG,       // Indicates the language of the search result. The LANG element contains a two-letter language code.
        HAS;        // Encapsulates special features that are included for this search result.
    }


    private static final char[] XML_START = (
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<GSP VER=\"3.2\">\n<!-- This is a Google Search Appliance API result, provided by YaCy. See https://developers.google.com/search-appliance/documentation/614/xml_reference -->\n").toCharArray();
    private static final char[] XML_STOP = "</GSP>\n".toCharArray();

    // pre-select a set of YaCy schema fields for the solr searcher which should cause a better caching
    private static final CollectionSchema[] extrafields = new CollectionSchema[]{
        CollectionSchema.id, CollectionSchema.sku, CollectionSchema.title, CollectionSchema.description_txt,
        CollectionSchema.last_modified, CollectionSchema.load_date_dt, CollectionSchema.size_i, 
        CollectionSchema.language_s, CollectionSchema.collection_sxt
    };
    
    private static final Set<String> SOLR_FIELDS = new HashSet<>();
    static {
        
        SOLR_FIELDS.add(CollectionSchema.language_s.getSolrFieldName());
        for (CollectionSchema field: extrafields) SOLR_FIELDS.add(field.getSolrFieldName());
    }

    private static class ResHead {
        public long offset, numFound;
        public int rows;
        //public int status, QTime;
        //public String df, q, wt;
        //public float maxScore;
    }

    public static class Sort {
        public String sort = null, action = null, direction = null, mode = null, format = null;
        public Sort(String d) {
            this.sort = d;
            String[] s = CommonPattern.DOUBLEPOINT.split(d);
            if (s.length < 1) return;
            this.action = s[0]; // date
            this.direction = s.length > 1 ? s[1] : "D"; // A or D
            this.mode = s.length > 2 ? s[2] : "S"; // S, R, L
            this.format = s.length > 3 ? s[3] : "d1"; // d1
        }
        public String toSolr() {
            if (this.action != null && "date".equals(this.action)) {
                return CollectionSchema.last_modified.getSolrFieldName() + " " + (("D".equals(this.direction) ? "desc" : "asc"));
            }
            return null;
        }
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

        final long start = System.currentTimeMillis();

        final Object responseObj = rsp.getResponse();
        
        if(responseObj instanceof ResultContext) {
        	/* Regular response object */
        	
            final DocList documents = ((ResultContext) responseObj).getDocList();
            
    		final Object highlightingObj = rsp.getValues().get("highlighting");
    		final Map<String, Collection<String>> snippets = highlightingObj instanceof NamedList
    				? OpensearchResponseWriter.snippetsFromHighlighting((NamedList<?>) highlightingObj)
    				: new HashMap<>();

            // parse response header
            final ResHead resHead = new ResHead();
            resHead.rows = request.getParams().getInt(CommonParams.ROWS, 0);
            resHead.offset = documents.offset(); // equal to 'start'
            resHead.numFound = documents.matches();
            //resHead.df = (String) val0.get("df");
            //resHead.q = (String) val0.get("q");
            //resHead.wt = (String) val0.get("wt");
            //resHead.status = (Integer) responseHeader.get("status");
            //resHead.QTime = (Integer) responseHeader.get("QTime");
            //resHead.maxScore = response.maxScore();

            // write header
            writeHeader(writer, request, resHead, start);

            // body introduction
            writeBodyIntro(writer, request, resHead, documents.size());

            writeDocs(writer, request, documents, snippets, resHead);
            
            writer.write("</RES>"); writer.write(lb);
            writer.write(XML_STOP);        	
        } else if(responseObj instanceof SolrDocumentList) {
			/*
			 * The response object can be a SolrDocumentList when the response is partial,
			 * for example when the allowed processing time has been exceeded
			 */
        	final SolrDocumentList documents = (SolrDocumentList) responseObj;
        	
    		final Object highlightingObj = rsp.getValues().get("highlighting");
    		final Map<String, Collection<String>> snippets = highlightingObj instanceof NamedList
    				? OpensearchResponseWriter.snippetsFromHighlighting((NamedList<?>) highlightingObj)
    				: new HashMap<>();
        	
        	writeSolrDocumentList(writer, request, snippets, start, documents);
        } else {
        	throw new IOException("Unable to process Solr response format");
        }
    }
    
    @Override
    public void write(Writer writer, SolrQueryRequest request, String coreName, QueryResponse rsp) throws IOException {
        final long start = System.currentTimeMillis();
				
		writeSolrDocumentList(writer, request, snippetsFromHighlighting(rsp.getHighlighting()), start,
				rsp.getResults());
    }
    
	/**
	 * Produce snippets from Solr (they call that 'highlighting')
	 * 
	 * @param sorlHighlighting highlighting from Solr
	 * @return a map from urlhashes to a list of snippets for that url
	 */
	private Map<String, Collection<String>> snippetsFromHighlighting(
			final Map<String, Map<String, List<String>>> sorlHighlighting) {
		final Map<String, Collection<String>> snippets = new HashMap<>();
		if (sorlHighlighting == null) {
			return snippets;
		}
		for (final Entry<String, Map<String, List<String>>> highlightingEntry : sorlHighlighting.entrySet()) {
			final String urlHash = highlightingEntry.getKey();
			final Map<String, List<String>> highlights = highlightingEntry.getValue();
			final LinkedHashSet<String> urlSnippets = new LinkedHashSet<>();
			for (final List<String> texts : highlights.values()) {
				urlSnippets.addAll(texts);
			}
			snippets.put(urlHash, urlSnippets);
		}
		return snippets;
	}

	/**
	 * Append to the writer a representation of a list of Solr documents. All
	 * parameters are required and must not be null.
	 * 
	 * @param writer    an open output writer
	 * @param request   the Solr request
	 * @param snippets  the snippets computed from the Solr highlighting
	 * @param start     the results start index
	 * @param documents the Solr documents to process
	 * @throws IOException when a write error occurred
	 */
	private void writeSolrDocumentList(final Writer writer, final SolrQueryRequest request,
			final Map<String, Collection<String>> snippets, final long start, final SolrDocumentList documents)
			throws IOException {

        // parse response header
        final ResHead resHead = new ResHead();
        resHead.rows = request.getParams().getInt(CommonParams.ROWS, 0);
        resHead.offset = documents.getStart();
        resHead.numFound = documents.getNumFound();

        // write header
        writeHeader(writer, request, resHead, start);

        // body introduction
        writeBodyIntro(writer, request, resHead, documents.size());

        writeDocs(writer, documents, snippets, resHead, request.getParams().get("originalQuery"));
        
        writer.write("</RES>"); writer.write(lb);
        writer.write(XML_STOP);
	}
    
	/**
	 * Append the response header to the writer. All parameters are required and
	 * must not be null.
	 * 
	 * @param writer    an open output writer
	 * @param request   the Solr request
	 * @param resHead   results header information
	 * @param startTime this writer processing start time in milliseconds since
	 *                  Epoch
	 * @throws IOException when a write error occurred
	 */
	private void writeHeader(final Writer writer, final SolrQueryRequest request, final ResHead resHead,
			final long startTime) throws IOException {
    	final Map<Object,Object> context = request.getContext();
    	
        writer.write(XML_START);
        final String query = request.getParams().get("originalQuery");
        final String site  = getContextString(context, "site", "");
        final String sort  = getContextString(context, "sort", "");
        final String client  = getContextString(context, "client", "");
        final String ip  = getContextString(context, "ip", "");
        final String access  = getContextString(context, "access", "");
        final String entqr  = getContextString(context, "entqr", "");
        OpensearchResponseWriter.solitaireTag(writer, "TM", Long.toString(System.currentTimeMillis() - startTime));
        OpensearchResponseWriter.solitaireTag(writer, "Q", query);
        paramTag(writer, "sort", sort);
        paramTag(writer, "output", "xml_no_dtd");
        paramTag(writer, "ie", StandardCharsets.UTF_8.name());
        paramTag(writer, "oe", StandardCharsets.UTF_8.name());
        paramTag(writer, "client", client);
        paramTag(writer, "q", query);
        paramTag(writer, "site", site);
        paramTag(writer, "start", Long.toString(resHead.offset));
        paramTag(writer, "num", Integer.toString(resHead.rows));
        paramTag(writer, "ip", ip);
        paramTag(writer, "access", access); // p - search only public content, s - search only secure content, a - search all content, both public and secure
        paramTag(writer, "entqr", entqr); // query expansion policy; (entqr=1) -- Uses only the search appliance's synonym file, (entqr=1) -- Uses only the search appliance's synonym file, (entqr=3) -- Uses both standard and local synonym files.
    }
    
	/**
	 * Append the response body introduction to the writer. All parameters are
	 * required and must not be null.
	 * 
	 * @param writer        an open output writer
	 * @param resHead       results header information
	 * @param responseCount the number of result documents
	 * @throws IOException when a write error occurred
	 */
	private void writeBodyIntro(final Writer writer, final SolrQueryRequest request, final ResHead resHead,
			final int responseCount) throws IOException {
        final Map<Object,Object> context = request.getContext();
        final String site  = getContextString(context, "site", "");
        final String sort  = getContextString(context, "sort", "");
        final String client  = getContextString(context, "client", "");
        final String access  = getContextString(context, "access", "");
        writer.write("<RES SN=\"" + (resHead.offset + 1) + "\" EN=\"" + (resHead.offset + responseCount) + "\">"); writer.write(lb); // The index (1-based) of the first and last search result returned in this result set.
        writer.write("<M>" + resHead.numFound + "</M>"); writer.write(lb); // The estimated total number of results for the search.
        writer.write("<FI/>"); writer.write(lb); // Indicates that document filtering was performed during this search.
        long nextStart = resHead.offset + responseCount;
        long nextNum = Math.min(resHead.numFound - nextStart, responseCount < resHead.rows ? 0 : resHead.rows);
        long prevStart = resHead.offset - resHead.rows;
        if (prevStart >= 0 || nextNum > 0) {
            writer.write("<NB>");
            if (prevStart >= 0) {
                writer.write("<PU>");
                XML.escapeCharData("/gsa/search?q=" + request.getParams().get(CommonParams.Q) + "&site=" + site +
                         "&lr=&ie=UTF-8&oe=UTF-8&output=xml_no_dtd&client=" + client + "&access=" + access +
                         "&sort=" + sort + "&start=" + prevStart + "&sa=N", writer); // a relative URL pointing to the NEXT results page.
                writer.write("</PU>");
            }
            if (nextNum > 0) {
                writer.write("<NU>");
                XML.escapeCharData("/gsa/search?q=" + request.getParams().get(CommonParams.Q) + "&site=" + site +
                         "&lr=&ie=UTF-8&oe=UTF-8&output=xml_no_dtd&client=" + client + "&access=" + access +
                         "&sort=" + sort + "&start=" + nextStart + "&num=" + nextNum + "&sa=N", writer); // a relative URL pointing to the NEXT results page.
                writer.write("</NU>");
            }
            writer.write("</NB>");
        }
        writer.write(lb);
    }

	/**
	 * Append to the writer a representation of a list of Solr documents. All
	 * parameters are required and must not be null.
	 * 
	 * @param writer    an open output writer
	 * @param request   the Solr request
	 * @param documents the Solr documents to process
	 * @param snippets  the snippets computed from the Solr highlighting
	 * @param resHead       results header information
	 * @throws IOException when a write error occurred
	 */
	private void writeDocs(final Writer writer, final SolrQueryRequest request, final DocList documents,
			final Map<String, Collection<String>> snippets, final ResHead resHead)
			throws IOException {
		// parse body
		final String query = request.getParams().get("originalQuery");
        SolrIndexSearcher searcher = request.getSearcher();
        DocIterator iterator = documents.iterator();
        String urlhash = null;
        final int responseCount = documents.size();
        for (int i = 0; i < responseCount; i++) {
            int id = iterator.nextDoc();
            Document doc = searcher.doc(id, SOLR_FIELDS);
            List<IndexableField> fields = doc.getFields();

            // pre-scan the fields to get the mime-type            
            String mime = "";
            for (IndexableField value: fields) {
                String fieldName = value.name();
                if (CollectionSchema.content_type.getSolrFieldName().equals(fieldName)) {
                    mime = value.stringValue();
                    break;
                }
            }
            
            // write the R header for a search result
            writer.write("<R N=\"" + (resHead.offset + i + 1)  + "\"" + (i == 1 ? " L=\"2\"" : "")  + (mime != null && mime.length() > 0 ? " MIME=\"" + mime + "\"" : "") + ">"); writer.write(lb);
            List<String> descriptions = new ArrayList<>();
            List<String> collections = new ArrayList<>();
            int size = 0;
            boolean title_written = false; // the solr index may contain several; we take only the first which should be the visible tag in <title></title>
            String title = null;
            for (IndexableField value: fields) {
                String fieldName = value.name();

                if (CollectionSchema.language_s.getSolrFieldName().equals(fieldName)) {
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.LANG.name(), value.stringValue());
                } else if (CollectionSchema.id.getSolrFieldName().equals(fieldName)) {
                    urlhash = value.stringValue();
                } else if (CollectionSchema.sku.getSolrFieldName().equals(fieldName)) {
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.U.name(), value.stringValue());
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.UE.name(), value.stringValue());
                } else if (CollectionSchema.title.getSolrFieldName().equals(fieldName) && !title_written) {
                    title = value.stringValue();
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.T.name(), highlight(title, query));
                    title_written = true;
                } else if (CollectionSchema.description_txt.getSolrFieldName().equals(fieldName)) {
                    descriptions.add(value.stringValue());
                } else if (CollectionSchema.last_modified.getSolrFieldName().equals(fieldName)) {
                    Date d = new Date(Long.parseLong(value.stringValue()));
                    writer.write("<FS NAME=\"date\" VALUE=\"" + formatGSAFS(d) + "\"/>\n");
                } else if (CollectionSchema.load_date_dt.getSolrFieldName().equals(fieldName)) {
                    Date d = new Date(Long.parseLong(value.stringValue()));
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.CRAWLDATE.name(), HeaderFramework.formatRFC1123(d));
                } else if (CollectionSchema.size_i.getSolrFieldName().equals(fieldName)) {
                    size = value.stringValue() != null && value.stringValue().length() > 0 ? Integer.parseInt(value.stringValue()) : -1;
                } else if (CollectionSchema.collection_sxt.getSolrFieldName().equals(fieldName)) {
                    collections.add(value.stringValue());
                }
            }
            // compute snippet from texts
            Collection<String> snippet = urlhash == null ? null : snippets.get(urlhash);
            OpensearchResponseWriter.removeSubsumedTitle(snippet, title);
            OpensearchResponseWriter.solitaireTag(writer, GSAToken.S.name(), snippet == null || snippet.size() == 0 ? (descriptions.size() > 0 ? descriptions.get(0) : "") : OpensearchResponseWriter.getLargestSnippet(snippet));
            OpensearchResponseWriter.solitaireTag(writer, GSAToken.GD.name(), descriptions.size() > 0 ? descriptions.get(0) : "");
            String cols = collections.toString();
            if (collections.size() > 0) OpensearchResponseWriter.solitaireTag(writer, "COLS" /*SPECIAL!*/, collections.size() > 1 ? cols.substring(1, cols.length() - 1).replaceAll(" ", "") : collections.get(0));
            writer.write("<HAS><L/><C SZ=\""); writer.write(Integer.toString(size / 1024)); writer.write("k\" CID=\""); writer.write(urlhash); writer.write("\" ENC=\"UTF-8\"/></HAS>\n");
            if (YaCyVer == null) YaCyVer = yacyVersion.thisVersion().getName() + "/" + Switchboard.getSwitchboard().peers.mySeed().hash;
            OpensearchResponseWriter.solitaireTag(writer, GSAToken.ENT_SOURCE.name(), YaCyVer);
            OpensearchResponseWriter.closeTag(writer, "R");
        }
	}
	
	/**
	 * Append to the writer a representation of a list of Solr documents. All
	 * parameters are required and must not be null.
	 * 
	 * @param writer    an open output writer
	 * @param documents the Solr documents to process
	 * @param snippets  the snippets computed from the Solr highlighting
	 * @param resHead       results header information
	 * @param query the original search query
	 * @throws IOException when a write error occurred
	 */
	private void writeDocs(final Writer writer, final SolrDocumentList documents,
			final Map<String, Collection<String>> snippets, final ResHead resHead, final String query)
			throws IOException {
		// parse body
        String urlhash = null;
        int i = 0;
        for (final SolrDocument doc : documents) {

            // pre-scan the fields to get the mime-type
        	final Object contentTypeObj = doc.getFirstValue(CollectionSchema.content_type.getSolrFieldName());
        	final String mime = contentTypeObj != null ? contentTypeObj.toString() : "";
            
            // write the R header for a search result
            writer.write("<R N=\"" + (resHead.offset + i + 1)  + "\"" + (i == 1 ? " L=\"2\"" : "")  + (mime != null && mime.length() > 0 ? " MIME=\"" + mime + "\"" : "") + ">"); writer.write(lb);
            final List<String> descriptions = new ArrayList<>();
            final List<String> collections = new ArrayList<>();
            int size = 0;
            String title = null;
            for (final Entry<String, Object> field : doc.entrySet()) {
            	final String fieldName = field.getKey();
            	final Object value = field.getValue();
                
                if (CollectionSchema.language_s.getSolrFieldName().equals(fieldName)) {
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.LANG.name(), value.toString());
                } else if (CollectionSchema.id.getSolrFieldName().equals(fieldName)) {
                    urlhash = value.toString();
                } else if (CollectionSchema.sku.getSolrFieldName().equals(fieldName)) {
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.U.name(), value.toString());
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.UE.name(), value.toString());
                } else if (CollectionSchema.title.getSolrFieldName().equals(fieldName)) {
                  	if(value instanceof Iterable) {
                		for(final Object titleObj : (Iterable<?>)value) {
                			if(titleObj != null) {
                				/* get only the first title */
                				title = titleObj.toString();
                				break;
                			}
                		}
                	} else if(value != null) {
                		title = value.toString();
                	}
                  	if(title != null) {
                  		OpensearchResponseWriter.solitaireTag(writer, GSAToken.T.name(), highlight(title, query));
                  	}
                } else if (CollectionSchema.description_txt.getSolrFieldName().equals(fieldName)) {
                	if(value instanceof Iterable) {
                		for(final Object descriptionObj : (Iterable<?>)value) {
                			if(descriptionObj != null) {
                				descriptions.add(descriptionObj.toString());
                			}
                		}
                	} else if(value != null) {
                        descriptions.add(value.toString());                		
                	}
                } else if (CollectionSchema.last_modified.getSolrFieldName().equals(fieldName) && value instanceof Date) {
                    writer.write("<FS NAME=\"date\" VALUE=\"" + formatGSAFS((Date)value) + "\"/>\n");
                } else if (CollectionSchema.load_date_dt.getSolrFieldName().equals(fieldName) && value instanceof Date) {
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.CRAWLDATE.name(), HeaderFramework.formatRFC1123((Date)value));
                } else if (CollectionSchema.size_i.getSolrFieldName().equals(fieldName)) {
                    size = value instanceof Integer ? (Integer)value : -1;
                } else if (CollectionSchema.collection_sxt.getSolrFieldName().equals(fieldName)) { // handle collection
                	if(value instanceof Iterable) {
                		for(final Object collectionObj : (Iterable<?>)value) {
                			if(collectionObj != null) {
                				collections.add(collectionObj.toString());
                			}
                		}
                	} else if(value != null) {
                		collections.add(value.toString());                		
                	}
                }
            }
            // compute snippet from texts
            Collection<String> snippet = urlhash == null ? null : snippets.get(urlhash);
            OpensearchResponseWriter.removeSubsumedTitle(snippet, title);
            OpensearchResponseWriter.solitaireTag(writer, GSAToken.S.name(), snippet == null || snippet.size() == 0 ? (descriptions.size() > 0 ? descriptions.get(0) : "") : OpensearchResponseWriter.getLargestSnippet(snippet));
            OpensearchResponseWriter.solitaireTag(writer, GSAToken.GD.name(), descriptions.size() > 0 ? descriptions.get(0) : "");
            String cols = collections.toString();
            if (!collections.isEmpty()) {
            	OpensearchResponseWriter.solitaireTag(writer, "COLS" /*SPECIAL!*/, collections.size() > 1 ? cols.substring(1, cols.length() - 1).replaceAll(" ", "") : collections.get(0));
            }
            writer.write("<HAS><L/><C SZ=\""); writer.write(Integer.toString(size / 1024)); writer.write("k\" CID=\""); writer.write(urlhash); writer.write("\" ENC=\"UTF-8\"/></HAS>\n");
            if (YaCyVer == null) YaCyVer = yacyVersion.thisVersion().getName() + "/" + Switchboard.getSwitchboard().peers.mySeed().hash;
            OpensearchResponseWriter.solitaireTag(writer, GSAToken.ENT_SOURCE.name(), YaCyVer);
            OpensearchResponseWriter.closeTag(writer, "R");
            
            i++;
        }
	}

    private static String getContextString(Map<Object,Object> context, String key, String dflt) {
        Object v = context.get(key);
        if (v == null) return dflt;
        if (v instanceof String) return (String) v;
        if (v instanceof String[]) {
            String[] va = (String[]) v;
            return va.length == 0 ? dflt : va[0];
        }
        return dflt;
    }
    
    public static void paramTag(final Writer writer, final String tagname, String value) throws IOException {
        if (value == null || value.length() == 0) return;
        writer.write("<PARAM name=\"");
        writer.write(tagname);
        writer.write("\" value=\"");
        XML.escapeAttributeValue(value, writer);
        writer.write("\" original_value=\"");
        XML.escapeAttributeValue(value, writer);
        writer.write("\"/>"); writer.write(lb);
    }

    public static String highlight(String text, String query) {
        if (query != null) {
            String[] q = CommonPattern.SPACE.split(CommonPattern.PLUS.matcher(query.trim().toLowerCase()).replaceAll(" "));
            for (String s: q) {
                int p = text.toLowerCase().indexOf(s.toLowerCase());
                if (p < 0) continue;
                text = text.substring(0, p) + "<b>" + text.substring(p, p + s.length()) + "</b>" + text.substring(p + s.length());
            }
            return text.replaceAll(Pattern.quote("</b> <b>"), " ");
        } 
        return text;
    }

    /**
     * Format date for GSA (short form of ISO8601 date format)
     * @param date
     * @return datestring "yyyy-mm-dd"
     * @see ISO8601Formatter
     */
    public final String formatGSAFS(final Date date) {
        if (date == null) {
        	return "";
        }
		try {
			return GSAsearchServlet.FORMAT_GSAFS.format(date.toInstant());
		} catch (final DateTimeException e) {
			return "";
		}
    }

}