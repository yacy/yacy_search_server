/**
 *  OpensearchResponseWriter
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

package net.yacy.cora.services.federated.solr;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.RSSMessage;
import net.yacy.cora.lod.vocabulary.DublinCore;
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

public class OpensearchResponseWriter implements QueryResponseWriter {

    private static final char lb = '\n';
    private static final char[] XML_START = (
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<?xml-stylesheet type='text/xsl' href='/yacysearch.xsl' version='1.0'?>\n" +
                    "<rss version=\"2.0\"\n" +
                    "    xmlns:yacy=\"http://www.yacy.net/\"\n" +
                    "    xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\"\n" +
                    "    xmlns:media=\"http://search.yahoo.com/mrss/\"\n" +
                    "    xmlns:atom=\"http://www.w3.org/2005/Atom\"\n" +
                    "    xmlns:dc=\"http://purl.org/dc/elements/1.1/\"\n" +
                    "    xmlns:geo=\"http://www.w3.org/2003/01/geo/wgs84_pos#\"\n" +
                    ">\n").toCharArray();
    private static final char[] XML_STOP = "</rss>\n".toCharArray();

    // define a list of simple YaCySchema -> RSS Token matchings
    private static final Map<String, String> field2tag = new HashMap<String, String>();

    // pre-select a set of YaCy schema fields for the solr searcher which should cause a better caching
    private static final YaCySchema[] extrafields = new YaCySchema[]{
        YaCySchema.id, YaCySchema.title, YaCySchema.description, YaCySchema.text_t,
        YaCySchema.h1_txt, YaCySchema.h2_txt, YaCySchema.h3_txt, YaCySchema.h4_txt, YaCySchema.h5_txt, YaCySchema.h6_txt,
        };
    private static final Set<String> SOLR_FIELDS = new HashSet<String>();
    static {
        field2tag.put(YaCySchema.sku.name(), RSSMessage.Token.link.name());
        field2tag.put(YaCySchema.last_modified.name(), RSSMessage.Token.pubDate.name());
        field2tag.put(YaCySchema.publisher_t.name(), DublinCore.Publisher.getURIref());
        field2tag.put(YaCySchema.author.name(), DublinCore.Creator.getURIref());
        SOLR_FIELDS.addAll(field2tag.keySet());
        for (YaCySchema field: extrafields) SOLR_FIELDS.add(field.name());
    }

    private String title;

    private static class ResHead {
        public int offset, rows, numFound;
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
        openTag(writer, "channel");
        solitaireTag(writer, "opensearch:totalResults", Integer.toString(resHead.numFound));
        solitaireTag(writer, "opensearch:startIndex", Integer.toString(resHead.offset));
        solitaireTag(writer, "opensearch:itemsPerPage", Integer.toString(resHead.rows));
        solitaireTag(writer, RSSMessage.Token.title.name(), this.title);
        //solitaireTag(writer, "description", "");
        //solitaireTag(writer, "link", "");
        //solitaireTag(writer, "image", "");

        // parse body
        final int responseCount = response.size();
        SolrIndexSearcher searcher = request.getSearcher();
        DocIterator iterator = response.iterator();
        for (int i = 0; i < responseCount; i++) {
            openTag(writer, "item");
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
                    solitaireTag(writer, stag, value.stringValue());
                    continue;
                }

                // if the rule is not generic, use the specific here
                if (YaCySchema.id.name().equals(fieldName)) {
                    solitaireTag(writer, RSSMessage.Token.guid.name(), value.stringValue(), "isPermaLink=\"false\"");
                    continue;
                }
                if (YaCySchema.title.name().equals(fieldName)) {
                    solitaireTag(writer, RSSMessage.Token.title.name(), value.stringValue());
                    texts.add(value.stringValue());
                    continue;
                }
                if (YaCySchema.description.name().equals(fieldName)) {
                    description = value.stringValue();
                    solitaireTag(writer, DublinCore.Description.getURIref(), description);
                    texts.add(description);
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
            solitaireTag(writer, RSSMessage.Token.description.name(), description);
            closeTag(writer, "item");
        }

        closeTag(writer, "channel");
        writer.write(XML_STOP);
    }

    public static void openTag(final Writer writer, final String tag) throws IOException {
        writer.write('<'); writer.write(tag); writer.write('>'); writer.write(lb);
    }

    public static void closeTag(final Writer writer, final String tag) throws IOException {
        writer.write("</"); writer.write(tag); writer.write('>'); writer.write(lb);
    }

    public static void solitaireTag(final Writer writer, final String tagname, String value) throws IOException {
        if (value == null || value.length() == 0) return;
        writer.write("<"); writer.write(tagname); writer.write('>');
        writer.write(value);
        writer.write("</"); writer.write(tagname); writer.write('>'); writer.write(lb);
    }

    public static void solitaireTag(final Writer writer, final String tagname, String value, String attr) throws IOException {
        if (value == null || value.length() == 0) return;
        writer.write("<"); writer.write(tagname);
        if (attr.charAt(0) != ' ') writer.write(' ');
        writer.write(attr);
        writer.write('>');
        writer.write(value);
        writer.write("</"); writer.write(tagname); writer.write('>'); writer.write(lb);
    }

}

/*
    <!-- YaCy Search Engine; http://yacy.net -->
    <channel>
        <title>#[promoteSearchPageGreeting]#</title>
        <description>Search for #[rss_query]#</description>
        <link>#[searchBaseURL]#?query=#[rss_queryenc]#&amp;resource=#[resource]#&amp;contentdom=#[contentdom]#&amp;verify=#[verify]#</link>
        <image>
            <url>#[rssYacyImageURL]#</url>
            <title>Search for #[rss_query]#</title>
            <link>#[searchBaseURL]#?query=#[rss_queryenc]#&amp;resource=#[resource]#&amp;contentdom=#[contentdom]#&amp;verify=#[verify]#</link>
        </image>
        <opensearch:totalResults>#[num-results_totalcount]#</opensearch:totalResults>
        <opensearch:startIndex>#[num-results_offset]#</opensearch:startIndex>
        <opensearch:itemsPerPage>#[num-results_itemsPerPage]#</opensearch:itemsPerPage>
        <atom:link rel=\"search\" href=\"http://#[thisaddress]#/opensearchdescription.xml\" type=\"application/opensearchdescription+xml\"/>
        <opensearch:Query role=\"request\" searchTerms=\"#[rss_queryenc]#\" />


<!-- results -->
<item>
<title>#[title-xml]#</title>
<link>#[link]#</link>
<description>#[description-xml]#</description>
<pubDate>#[date822]#</pubDate>
<dc:publisher><![CDATA[#[publisher]#]]></dc:publisher>
<dc:creator><![CDATA[#[creator]#]]></dc:creator>
<dc:subject><![CDATA[#[subject]#]]></dc:subject>
<yacy:size>#[size]#</yacy:size>
<yacy:sizename>#[sizename]#</yacy:sizename>
<yacy:host>#[host]#</yacy:host>
<yacy:path>#[path]#</yacy:path>
<yacy:file>#[file]#</yacy:file>
<guid isPermaLink=\"false\">#[urlhash]#</guid>
#(loc)#::<geo:lat>#[lat]#</geo:lat><geo:long>#[lon]#</geo:long>#(/loc)#
</item>::
#(item)#::<item>
<title>#[name]#</title>
<link>#[source-xml]#</link>
<description></description>
<pubDate></pubDate>
<guid isPermaLink=\"false\">#[urlhash]#</guid>
<yacy:host>#[sourcedom]#</yacy:host>
<media:group>
<media:content
  url=\"#[href]#\"
  fileSize=\"#[fileSize]#\"
  type=\"#[mimetype]#\"
  medium=\"image\"
  isDefault=\"true\"
  expression=\"full\"
  height=\"#[width]#\"
  width=\"#[height]#\" />
<media:content
  url=\"#[hrefCache]#\"
  fileSize=\"#[fileSize]#\"
  type=\"#[mimetype]#\"
  medium=\"image\"
  isDefault=\"false\"
  expression=\"full\"
  height=\"#[width]#\"
  width=\"#[height]#\" />
<media:content
  url=\"/ViewImage.png?maxwidth=96&amp;maxheight=96&amp;code=#[code]#\"
  fileSize=\"#[fileSize]#\"
  type=\"#[mimetype]#\"
  medium=\"image\"
  isDefault=\"false\"
  expression=\"sample\"
  height=\"96\"
  width=\"96\" />
</media:group>
</item>

<!-- footer -->
<yacy:navigation>

#(nav-domains)#::
<yacy:facet name=\"domains\" displayname=\"Domains\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\">
#{element}#
<yacy:element name=\"#[name]#\" count=\"#[count]#\" modifier=\"#[modifier]#\" />
#{/element}#
</yacy:facet>
#(/nav-domains)#

#(nav-namespace)#::
<yacy:facet name=\"namespace\" displayname=\"Namespace\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\">
#{element}#
<yacy:element name=\"#[name]#\" count=\"#[count]#\" modifier=\"#[modifier]#\" />
#{/element}#
</yacy:facet>
#(/nav-namespace)#

#(nav-authors)#::
<yacy:facet name=\"authors\" displayname=\"Authors\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\">
#{element}#
<yacy:element name=\"#[name]#\" count=\"#[count]#\" modifier=\"#[modifier]#\" />
#{/element}#
</yacy:facet>
#(/nav-authors)#

#(nav-filetype)#::
<yacy:facet name=\"filetypes\" displayname=\"Filetypes\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\">
#{element}#
<yacy:element name=\"#[name]#\" count=\"#[count]#\" modifier=\"#[modifier]#\" />
#{/element}#
</yacy:facet>
#(/nav-filetype)#

#(nav-protocol)#::
<yacy:facet name=\"protocols\" displayname=\"Protocols\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\">
#{element}#
<yacy:element name=\"#[name]#\" count=\"#[count]#\" modifier=\"#[modifier]#\" />
#{/element}#
</yacy:facet>
#(/nav-protocol)#

#(nav-topics)#::
<yacy:facet name=\"topics\" displayname=\"Topics\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\">
#{element}#
<yacy:element name=\"#[name]#\" count=\"#[count]#\" modifier=\"#[modifier]#\" />
#{/element}#
</yacy:facet>
#(/nav-topics)#

#{nav-vocabulary}#
<yacy:facet name=\"#[navname]#\" displayname=\"#[navname]#\" type=\"String\" min=\"0\" max=\"0\" mean=\"0\">
#{element}#
<yacy:element name=\"#[name]#\" count=\"#[count]#\" modifier=\"#[modifier]#\" />
#{/element}#
</yacy:facet>
#{/nav-vocabulary}#

</yacy:navigation>

<opensearch:totalResults>#[num-results_totalcount]#</opensearch:totalResults>
</channel>
 */