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
import java.util.List;
import java.util.Set;

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
    private static final Set<String> DEFAULT_FIELD_LIST = null;

    private final String title;

    private static class ResHead {
        public int offset, rows, numFound;
        //public int status, QTime;
        //public String df, q, wt;
        //public float maxScore;
    }

    public OpensearchResponseWriter(String searchPageTitle) {
        super();
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
        writer.write(XML_START);

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
        startTagOpen(writer, "channel");
        solitaireTag(writer, "opensearch:totalResults", Integer.toString(resHead.numFound));
        solitaireTag(writer, "opensearch:startIndex", Integer.toString(resHead.offset));
        solitaireTag(writer, "opensearch:itemsPerPage", Integer.toString(resHead.rows));
        solitaireTag(writer, "title", this.title);
        //solitaireTag(writer, "description", "");
        //solitaireTag(writer, "link", "");
        //solitaireTag(writer, "image", "");

        // parse body
        final int responseCount = response.size();
        SolrIndexSearcher searcher = request.getSearcher();
        DocIterator iterator = response.iterator();
        for (int i = 0; i < responseCount; i++) {
            startTagOpen(writer, "item");
            int id = iterator.nextDoc();
            Document doc = searcher.doc(id, DEFAULT_FIELD_LIST);
            List<Fieldable> fields = doc.getFields();
            int fieldc = fields.size();
            List<String> texts = new ArrayList<String>();
            String description = "";
            for (int j = 0; j < fieldc; j++) {
                Fieldable f1 = fields.get(j);
                String fieldName = f1.name();
                if ("id".equals(fieldName)) {writer.write("<guid isPermaLink=\"false\">"); writer.write(f1.stringValue()); writer.write("</guid>"); writer.write(lb); continue;}
                if ("sku".equals(fieldName)) {solitaireTag(writer, "link", f1.stringValue()); continue;}
                if ("title".equals(fieldName)) {solitaireTag(writer, "title", f1.stringValue()); texts.add(f1.stringValue()); continue;}
                if ("last_modified".equals(fieldName)) {solitaireTag(writer, "pubDate", f1.stringValue()); continue;}
                if ("".equals(fieldName)) {solitaireTag(writer, "dc:publisher", f1.stringValue()); continue;}
                if ("".equals(fieldName)) {solitaireTag(writer, "dc:creator", f1.stringValue()); continue;}
                if ("description".equals(fieldName)) {description = f1.stringValue(); solitaireTag(writer, "dc:subject", description); texts.add(description); continue;}
                if ("text_t".equals(fieldName)) {texts.add(f1.stringValue()); continue;}
                if ("h1_txt".equals(fieldName) || "h2_txt".equals(fieldName) || "h3_txt".equals(fieldName) ||
                    "h4_txt".equals(fieldName) || "h5_txt".equals(fieldName) || "h6_txt".equals(fieldName)) {texts.add(f1.stringValue()); continue;}
            }
            // compute snippet from texts
            solitaireTag(writer, "description", description);
            closeTag(writer, "item");
        }

        closeTag(writer, "channel");
        writer.write(XML_STOP);
    }

    public static void startTagOpen(final Writer writer, final String tag) throws IOException {
        writer.write('<'); writer.write(tag); writer.write('>'); writer.write(lb);
    }

    public static void closeTag(final Writer writer, final String tag) throws IOException {
        writer.write("</"); writer.write(tag); writer.write('>'); writer.write(lb);
    }

    public static void solitaireTag(final Writer writer, final String tagname, String value) throws IOException {
        writer.write("<"); writer.write(tagname); writer.write('>');
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