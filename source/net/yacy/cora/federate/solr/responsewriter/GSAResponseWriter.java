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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.util.CommonPattern;
import net.yacy.peers.operation.yacyVersion;
import net.yacy.search.Switchboard;
import net.yacy.search.schema.CollectionSchema;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
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

/**
 * implementation of a GSA search result.
 * example: GET /gsa/searchresult?q=chicken+teriyaki&output=xml&client=test&site=test&sort=date:D:S:d1
 * for a xml reference, see https://developers.google.com/search-appliance/documentation/614/xml_reference
 */
public class GSAResponseWriter implements QueryResponseWriter {

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

    // define a list of simple YaCySchema -> RSS Token matchings
    private static final Map<String, String> field2tag = new HashMap<String, String>();

    // pre-select a set of YaCy schema fields for the solr searcher which should cause a better caching
    private static final CollectionSchema[] extrafields = new CollectionSchema[]{
        CollectionSchema.id, CollectionSchema.sku, CollectionSchema.title, CollectionSchema.description_txt,
        CollectionSchema.last_modified, CollectionSchema.load_date_dt, CollectionSchema.size_i, 
        CollectionSchema.language_s, CollectionSchema.collection_sxt
    };
    
    private static final Set<String> SOLR_FIELDS = new HashSet<String>();
    static {
        field2tag.put(CollectionSchema.language_s.getSolrFieldName(), GSAToken.LANG.name());
        SOLR_FIELDS.addAll(field2tag.keySet());
        for (CollectionSchema field: extrafields) SOLR_FIELDS.add(field.getSolrFieldName());
    }

    private static class ResHead {
        public int offset, rows, numFound;
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

    public GSAResponseWriter() {
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
        assert rsp.getValues().get("responseHeader") != null;
        assert rsp.getValues().get("response") != null;

        long start = System.currentTimeMillis();

        SimpleOrderedMap<Object> responseHeader = (SimpleOrderedMap<Object>) rsp.getResponseHeader();
        DocList response = ((ResultContext) rsp.getValues().get("response")).docs;
        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> highlighting = (SimpleOrderedMap<Object>) rsp.getValues().get("highlighting");
        Map<String, LinkedHashSet<String>> snippets = OpensearchResponseWriter.highlighting(highlighting);
        Map<Object,Object> context = request.getContext();

        // parse response header
        ResHead resHead = new ResHead();
        NamedList<?> val0 = (NamedList<?>) responseHeader.get("params");
        resHead.rows = Integer.parseInt((String) val0.get("rows"));
        resHead.offset = response.offset(); // equal to 'start'
        resHead.numFound = response.matches();
        //resHead.df = (String) val0.get("df");
        //resHead.q = (String) val0.get("q");
        //resHead.wt = (String) val0.get("wt");
        //resHead.status = (Integer) responseHeader.get("status");
        //resHead.QTime = (Integer) responseHeader.get("QTime");
        //resHead.maxScore = response.maxScore();

        // write header
        writer.write(XML_START);
        String query = request.getParams().get("originalQuery");
        String site  = getContextString(context, "site", "");
        String sort  = getContextString(context, "sort", "");
        String client  = getContextString(context, "client", "");
        String ip  = getContextString(context, "ip", "");
        String access  = getContextString(context, "access", "");
        String entqr  = getContextString(context, "entqr", "");
        OpensearchResponseWriter.solitaireTag(writer, "TM", Long.toString(System.currentTimeMillis() - start));
        OpensearchResponseWriter.solitaireTag(writer, "Q", query);
        paramTag(writer, "sort", sort);
        paramTag(writer, "output", "xml_no_dtd");
        paramTag(writer, "ie", "UTF-8");
        paramTag(writer, "oe", "UTF-8");
        paramTag(writer, "client", client);
        paramTag(writer, "q", query);
        paramTag(writer, "site", site);
        paramTag(writer, "start", Integer.toString(resHead.offset));
        paramTag(writer, "num", Integer.toString(resHead.rows));
        paramTag(writer, "ip", ip);
        paramTag(writer, "access", access); // p - search only public content, s - search only secure content, a - search all content, both public and secure
        paramTag(writer, "entqr", entqr); // query expansion policy; (entqr=1) -- Uses only the search appliance's synonym file, (entqr=1) -- Uses only the search appliance's synonym file, (entqr=3) -- Uses both standard and local synonym files.

        // body introduction
        final int responseCount = response.size();
        writer.write("<RES SN=\"" + (resHead.offset + 1) + "\" EN=\"" + (resHead.offset + responseCount) + "\">"); writer.write(lb); // The index (1-based) of the first and last search result returned in this result set.
        writer.write("<M>" + resHead.numFound + "</M>"); writer.write(lb); // The estimated total number of results for the search.
        writer.write("<FI/>"); writer.write(lb); // Indicates that document filtering was performed during this search.
        int nextStart = resHead.offset + responseCount;
        int nextNum = Math.min(resHead.numFound - nextStart, responseCount < resHead.rows ? 0 : resHead.rows);
        int prevStart = resHead.offset - resHead.rows;
        if (prevStart >= 0 || nextNum > 0) {
            writer.write("<NB>");
            if (prevStart >= 0) {
                writer.write("<PU>");
                XML.escapeCharData("/gsa/search?q=" + request.getParams().get("q") + "&site=" + site +
                         "&lr=&ie=UTF-8&oe=UTF-8&output=xml_no_dtd&client=" + client + "&access=" + access +
                         "&sort=" + sort + "&start=" + prevStart + "&sa=N", writer); // a relative URL pointing to the NEXT results page.
                writer.write("</PU>");
            }
            if (nextNum > 0) {
                writer.write("<NU>");
                XML.escapeCharData("/gsa/search?q=" + request.getParams().get("q") + "&site=" + site +
                         "&lr=&ie=UTF-8&oe=UTF-8&output=xml_no_dtd&client=" + client + "&access=" + access +
                         "&sort=" + sort + "&start=" + nextStart + "&num=" + nextNum + "&sa=N", writer); // a relative URL pointing to the NEXT results page.
                writer.write("</NU>");
            }
            writer.write("</NB>");
        }
        writer.write(lb);

        // parse body
        SolrIndexSearcher searcher = request.getSearcher();
        DocIterator iterator = response.iterator();
        String urlhash = null;
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
            //List<String> texts = new ArrayList<String>();
            List<String> descriptions = new ArrayList<String>();
            List<String> collections = new ArrayList<String>();
            int size = 0;
            boolean title_written = false; // the solr index may contain several; we take only the first which should be the visible tag in <title></title>
            String title = null;
            for (IndexableField value: fields) {
                String fieldName = value.name();

                // apply generic matching rule
                String stag = field2tag.get(fieldName);
                if (stag != null) {
                    OpensearchResponseWriter.solitaireTag(writer, stag, value.stringValue());
                    continue;
                }

                // if the rule is not generic, use the specific here
                if (CollectionSchema.id.getSolrFieldName().equals(fieldName)) {
                    urlhash = value.stringValue();
                    continue;
                }
                if (CollectionSchema.sku.getSolrFieldName().equals(fieldName)) {
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.U.name(), value.stringValue());
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.UE.name(), value.stringValue());
                    continue;
                }
                if (CollectionSchema.title.getSolrFieldName().equals(fieldName) && !title_written) {
                    title = value.stringValue();
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.T.name(), highlight(title, query));
                    //texts.add(value.stringValue());
                    title_written = true;
                    continue;
                }
                if (CollectionSchema.description_txt.getSolrFieldName().equals(fieldName)) {
                    descriptions.add(value.stringValue());
                    //texts.adds(description);
                    continue;
                }
                if (CollectionSchema.last_modified.getSolrFieldName().equals(fieldName)) {
                    Date d = new Date(Long.parseLong(value.stringValue()));
                    writer.write("<FS NAME=\"date\" VALUE=\"" + HeaderFramework.formatGSAFS(d) + "\"/>\n");
                    //OpensearchResponseWriter.solitaireTag(writer, GSAToken.CACHE_LAST_MODIFIED.getSolrFieldName(), HeaderFramework.formatRFC1123(d));
                    //texts.add(value.stringValue());
                    continue;
                }
                if (CollectionSchema.load_date_dt.getSolrFieldName().equals(fieldName)) {
                    Date d = new Date(Long.parseLong(value.stringValue()));
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.CRAWLDATE.name(), HeaderFramework.formatRFC1123(d));
                    //texts.add(value.stringValue());
                    continue;
                }
                if (CollectionSchema.size_i.getSolrFieldName().equals(fieldName)) {
                    size = value.stringValue() != null && value.stringValue().length() > 0 ? Integer.parseInt(value.stringValue()) : -1;
                    continue;
                }
                if (CollectionSchema.collection_sxt.getSolrFieldName().equals(fieldName)) {
                    collections.add(value.stringValue());
                    continue;
                }
                //System.out.println("superfluous field: " + fieldName + ": " + value.stringValue()); // this can be avoided setting the enableLazyFieldLoading = false in solrconfig.xml
            }
            // compute snippet from texts
            LinkedHashSet<String> snippet = urlhash == null ? null : snippets.get(urlhash);
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
        writer.write("</RES>"); writer.write(lb);
        writer.write(XML_STOP);
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
}