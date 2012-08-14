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

package net.yacy.cora.services.federated.solr;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.lod.vocabulary.DublinCore;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.search.index.YaCySchema;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.SolrIndexSearcher;

/**
 * implementation of a GSA search result.
 * example: GET /gsa/searchresult?q=chicken+teriyaki&output=xml&client=test&site=test&sort=date:D:S:d1
 * for a xml reference, see https://developers.google.com/search-appliance/documentation/68/xml_reference
 */
public class GSAResponseWriter implements QueryResponseWriter {

    private static final char lb = '\n';
    private enum GSAToken {
        CACHE_LAST_MODIFIED, // Date that the document was crawled, as specified in the Date HTTP header when the document was crawled for this index.
        CRAWLDATE,  // An optional element that shows the date when the page was crawled. It is shown only for pages that have been crawled within the past two days.
        U,          // The URL of the search result.
        UE,         // The URL-encoded version of the URL that is in the U parameter.
        T,          // The title of the search result.
        RK,         // Provides a ranking number used internally by the search appliance.
        ENT_SOURCE, // Identifies the application ID (serial number) of the search appliance that contributes to a result. Example: <ENT_SOURCE>S5-KUB000F0ADETLA</ENT_SOURCE>
        FS,         // Additional details about the search result.
        S,          // The snippet for the search result. Query terms appear in bold in the results. Line breaks are included for proper text wrapping.
        LANG,       // Indicates the language of the search result. The LANG element contains a two-letter language code.
        HAS;        // Encapsulates special features that are included for this search result.
    }


    private static final char[] XML_START = (
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<GSP VER=\"3.2\">\n").toCharArray();
    private static final char[] XML_STOP = "</GSP>\n".toCharArray();

    // define a list of simple YaCySchema -> RSS Token matchings
    private static final Map<String, String> field2tag = new HashMap<String, String>();

    // pre-select a set of YaCy schema fields for the solr searcher which should cause a better caching
    private static final YaCySchema[] extrafields = new YaCySchema[]{
        YaCySchema.id, YaCySchema.title, YaCySchema.description, YaCySchema.text_t,
        YaCySchema.h1_txt, YaCySchema.h2_txt, YaCySchema.h3_txt, YaCySchema.h4_txt, YaCySchema.h5_txt, YaCySchema.h6_txt,
        };
    private static final Set<String> SOLR_FIELDS = new HashSet<String>();
    static {
        field2tag.put(YaCySchema.language_txt.name(), GSAToken.LANG.name());
        SOLR_FIELDS.addAll(field2tag.keySet());
        for (YaCySchema field: extrafields) SOLR_FIELDS.add(field.name());
    }

    private static class ResHead {
        public int offset, rows, numFound;
        //public int status, QTime;
        //public String df, q, wt;
        //public float maxScore;
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

        @SuppressWarnings("unchecked")
        SimpleOrderedMap<Object> responseHeader = (SimpleOrderedMap<Object>) rsp.getResponseHeader();
        DocSlice response = (DocSlice) rsp.getValues().get("response");

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
        paramTag(writer, "start", Integer.toString(resHead.offset));
        paramTag(writer, "num", Integer.toString(resHead.rows));

        // parse body
        final int responseCount = response.size();
        SolrIndexSearcher searcher = request.getSearcher();
        DocIterator iterator = response.iterator();
        for (int i = 0; i < responseCount; i++) {
            OpensearchResponseWriter.openTag(writer, "R");
            int id = iterator.nextDoc();
            Document doc = searcher.doc(id, SOLR_FIELDS);
            List<Fieldable> fields = doc.getFields();
            int fieldc = fields.size();
            List<String> texts = new ArrayList<String>();
            String description = "";
            for (int j = 0; j < fieldc; j++) {
                Fieldable value = fields.get(j);
                String fieldName = value.name();

                // apply generic matching rule
                String stag = field2tag.get(fieldName);
                if (stag != null) {
                    OpensearchResponseWriter.solitaireTag(writer, stag, value.stringValue());
                    continue;
                }

/*
<RK></RK>
<FS NAME="date" VALUE=""/>
<S></S>
<HAS><L/><C SZ="7k" CID="XN-uikfmLv0J" ENC="UTF-8"/></HAS>
*/

                // if the rule is not generic, use the specific here
                if (YaCySchema.sku.name().equals(fieldName)) {
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.U.name(), CharacterCoding.unicode2xml(value.stringValue(), true));
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.UE.name(), CharacterCoding.unicode2html(value.stringValue(), true));
                    continue;
                }
                if (YaCySchema.title.name().equals(fieldName)) {
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.T.name(), CharacterCoding.unicode2xml(value.stringValue(), true));
                    texts.add(value.stringValue());
                    continue;
                }
                if (YaCySchema.description.name().equals(fieldName)) {
                    description = value.stringValue();
                    OpensearchResponseWriter.solitaireTag(writer, DublinCore.Description.getURIref(), CharacterCoding.unicode2xml(description, true));
                    texts.add(description);
                    continue;
                }
                if (YaCySchema.last_modified.name().equals(fieldName)) {
                    Date d = new Date(Long.parseLong(value.stringValue()));
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.CACHE_LAST_MODIFIED.name(), HeaderFramework.formatRFC1123(d));
                    texts.add(value.stringValue());
                    continue;
                }
                if (YaCySchema.load_date_dt.name().equals(fieldName)) {
                    Date d = new Date(Long.parseLong(value.stringValue()));
                    OpensearchResponseWriter.solitaireTag(writer, GSAToken.CRAWLDATE.name(), HeaderFramework.formatRFC1123(d));
                    texts.add(value.stringValue());
                    continue;
                }
                if (YaCySchema.text_t.name().equals(fieldName)) {
                    texts.add(value.stringValue());
                    continue;
                }
                if (YaCySchema.h1_txt.name().equals(fieldName) || YaCySchema.h2_txt.name().equals(fieldName) ||
                    YaCySchema.h3_txt.name().equals(fieldName) || YaCySchema.h4_txt.name().equals(fieldName) ||
                    YaCySchema.h5_txt.name().equals(fieldName) || YaCySchema.h6_txt.name().equals(fieldName)) {
                    // because these are multi-valued fields, there can be several of each
                    texts.add(value.stringValue());
                    continue;
                }
            }
            // compute snippet from texts
            OpensearchResponseWriter.solitaireTag(writer, RSSMessage.Token.description.name(), CharacterCoding.unicode2xml(description, true));
            OpensearchResponseWriter.solitaireTag(writer, GSAToken.ENT_SOURCE.name(), "YaCy");
            OpensearchResponseWriter.closeTag(writer, "R");
        }

        writer.write(XML_STOP);
    }


    public static void paramTag(final Writer writer, final String tagname, String value) throws IOException {
        if (value == null || value.length() == 0) return;
        writer.write("<PARAM name=\"");
        writer.write(tagname);
        writer.write("\" value=\"");
        writer.write(value);
        writer.write("\" original_value=\"");
        writer.write(value);
        writer.write("\"/>"); writer.write(lb);
    }
}

/*
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<GSP VER="3.2">
<TM>0.053898</TM>
<Q>pdf</Q>
<PARAM name="sort" value="date:D:L:d1" original_value="date:D:L:d1"/>
<PARAM name="output" value="xml_no_dtd" original_value="xml_no_dtd"/>
<PARAM name="ie" value="UTF-8" original_value="UTF-8"/>
<PARAM name="oe" value="UTF-8" original_value="UTF-8"/>
<PARAM name="client" value="" original_value=""/>
<PARAM name="q" value="pdf" original_value="pdf"/>
<PARAM name="site" value="" original_value=""/>
<PARAM name="start" value="0" original_value="0"/>
<PARAM name="num" value="10" original_value="10"/>
<PARAM name="ip" value="" original_value=""/>
<PARAM name="access" value="p" original_value="p"/>
<PARAM name="entqr" value="3" original_value="3"/>
<PARAM name="entqrm" value="0" original_value="0"/>
<RES SN="1" EN="10">
<M>296</M>
<NB>
<NU></NU>
</NB>

<R N="1">
<U></U>
<UE></UE>
<T></T>
<RK></RK>
<ENT_SOURCE></ENT_SOURCE>
<FS NAME="date" VALUE=""/>
<S></S>
<LANG>de</LANG>
<HAS><L/><C SZ="7k" CID="XN-uikfmLv0J" ENC="UTF-8"/></HAS>
</R>
<R N="2"></R>
</RES>
</GSP>
*/