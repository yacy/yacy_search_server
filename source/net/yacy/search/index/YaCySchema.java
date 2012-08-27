/**
 *  SolrField
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 22:05:04 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7654 $
 *  $LastChangedBy: orbiter $
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

package net.yacy.search.index;

import java.util.Date;
import java.util.List;

import net.yacy.cora.services.federated.solr.Schema;
import net.yacy.cora.services.federated.solr.SolrType;

import org.apache.solr.common.SolrInputDocument;

public enum YaCySchema implements Schema {

    // mandatory
    id(SolrType.string, true, true, "primary key of document, the URL hash **mandatory field**"),
    sku(SolrType.text_en_splitting_tight, true, true, false, true, "url of document"),
    last_modified(SolrType.date, true, true, "last-modified from http header"),
    content_type(SolrType.string, true, true, true, "mime-type of document"),
    title(SolrType.text_general, true, true, true, "content of title tag"),
    host_id_s(SolrType.string, true, true, "id of the host, a 6-byte hash that is part of the document id"),// String hosthash();
    md5_s(SolrType.string, true, true, "the md5 of the raw source"),// String md5();
    size_i(SolrType.integer, true, true, "the size of the raw source"),// int size();
    process_s(SolrType.string, true, true, "index creation comment"),
    failreason_t(SolrType.text_general, true, true, "fail reason if a page was not loaded. if the page was loaded then this field is empty"),
    httpstatus_i(SolrType.integer, true, true, "html status return code (i.e. \"200\" for ok), -1 if not loaded"),

    // optional but recommended, part of index distribution
    load_date_dt(SolrType.date, true, true, "time when resource was loaded"),
    fresh_date_dt(SolrType.date, true, true, "date until resource shall be considered as fresh"),
    referrer_id_txt(SolrType.string, true, true, true, "ids of referrer to this document"),// byte[] referrerHash();
    publisher_t(SolrType.text_general, true, true, "the name of the publisher of the document"),// String dc_publisher();
    language_txt(SolrType.string, true, true, "the language used in the document; starts with primary language"),// byte[] language();
    audiolinkscount_i(SolrType.integer, true, true, "number of links to audio resources"),// int laudio();
    videolinkscount_i(SolrType.integer, true, true, "number of links to video resources"),// int lvideo();
    applinkscount_i(SolrType.integer, true, true, "number of links to application resources"),// int lapp();

    // optional but recommended
    lon_coordinate(SolrType.tdouble, true, true, "longitude of location as declared in WSG84"), // deprecated
    lat_coordinate(SolrType.tdouble, true, true, "latitude of location as declared in WSG84"), // deprecated
    coordinate_p(SolrType.location, true, true, "point in degrees of latitude,longitude as declared in WSG84"),
    ip_s(SolrType.string, true, true, "ip of host of url (after DNS lookup)"),
    author(SolrType.text_general, true, true, "content of author-tag"),
    description(SolrType.text_general, true, true, "content of description-tag"),
    keywords(SolrType.text_general, true, true, "content of keywords tag; words are separated by space"),
    charset_s(SolrType.string, true, true, "character encoding"),
    wordcount_i(SolrType.integer, true, true, "number of words in visible area"),
    inboundlinkscount_i(SolrType.integer, true, true, "total number of inbound links"),
    inboundlinksnofollowcount_i(SolrType.integer, true, true, "number of inbound links with nofollow tag"),
    outboundlinkscount_i(SolrType.integer, true, true, "external number of inbound links"),
    outboundlinksnofollowcount_i(SolrType.integer, true, true, "number of external links with nofollow tag"),
    imagescount_i(SolrType.integer, true, true, "number of images"),
    responsetime_i(SolrType.integer, true, true, "response time of target server in milliseconds"),
    text_t(SolrType.text_general, true, true, "all visible text"),

    // optional values
    csscount_i(SolrType.integer, true, true, "number of entries in css_tag_txt and css_url_txt"),
    css_tag_txt(SolrType.text_general, true, true, true, "full css tag with normalized url"),
    css_url_txt(SolrType.text_general, true, true, true, "normalized urls within a css tag"),
    scripts_txt(SolrType.text_general, true, true, true, "normalized urls within a scripts tag"),
    scriptscount_i(SolrType.integer, true, true, "number of entries in scripts_txt"),
    // encoded as binary value into an integer:
    // bit  0: "all" contained in html header meta
    // bit  1: "index" contained in html header meta
    // bit  2: "noindex" contained in html header meta
    // bit  3: "nofollow" contained in html header meta
    // bit  8: "noarchive" contained in http header properties
    // bit  9: "nosnippet" contained in http header properties
    // bit 10: "noindex" contained in http header properties
    // bit 11: "nofollow" contained in http header properties
    // bit 12: "unavailable_after" contained in http header properties
    robots_i(SolrType.integer, true, true, "content of <meta name=\"robots\" content=#content#> tag and the \"X-Robots-Tag\" HTTP property"),
    metagenerator_t(SolrType.text_general, true, true, "content of <meta name=\"generator\" content=#content#> tag"),
    inboundlinks_tag_txt(SolrType.text_general, true, true, true, "internal links, normalized (absolute URLs), as <a> - tag with anchor text and nofollow"),
    inboundlinks_protocol_txt(SolrType.text_general, true, true, true, "internal links, only the protocol"),
    inboundlinks_urlstub_txt(SolrType.text_general, true, true, true, "internal links, the url only without the protocol"),
    inboundlinks_name_txt(SolrType.text_general, true, true, true, "internal links, the name property of the a-tag"),
    inboundlinks_rel_txt(SolrType.text_general, true, true, true, "internal links, the rel property of the a-tag"),
    inboundlinks_relflags_txt(SolrType.text_general, true, true, true, "internal links, the rel property of the a-tag, coded binary"),
    inboundlinks_text_txt(SolrType.text_general, true, true, true, "internal links, the text content of the a-tag"),
    outboundlinks_tag_txt(SolrType.text_general, true, true, true, "external links, normalized (absolute URLs), as <a> - tag with anchor text and nofollow"),
    outboundlinks_protocol_txt(SolrType.text_general, true, true, true, "external links, only the protocol"),
    outboundlinks_urlstub_txt(SolrType.text_general, true, true, true, "external links, the url only without the protocol"),
    outboundlinks_name_txt(SolrType.text_general, true, true, true, "external links, the name property of the a-tag"),
    outboundlinks_rel_txt(SolrType.text_general, true, true, true, "external links, the rel property of the a-tag"),
    outboundlinks_relflags_txt(SolrType.text_general, true, true, true, "external links, the rel property of the a-tag, coded binary"),
    outboundlinks_text_txt(SolrType.text_general, true, true, true, "external links, the text content of the a-tag"),
    images_tag_txt(SolrType.text_general, true, true, true, " all image tags, encoded as <img> tag inclusive alt- and title property"),
    images_urlstub_txt(SolrType.text_general, true, true, true, "all image links without the protocol and '://'"),
    images_protocol_txt(SolrType.text_general, true, true, true, "all image link protocols"),
    images_alt_txt(SolrType.text_general, true, true, true, "all image link alt tag"),
    h1_txt(SolrType.text_general, true, true, true, "h1 header"),
    h2_txt(SolrType.text_general, true, true, true, "h2 header"),
    h3_txt(SolrType.text_general, true, true, true, "h3 header"),
    h4_txt(SolrType.text_general, true, true, true, "h4 header"),
    h5_txt(SolrType.text_general, true, true, true, "h5 header"),
    h6_txt(SolrType.text_general, true, true, true, "h6 header"),
    htags_i(SolrType.integer, true, true, "binary pattern for the existance of h1..h6 headlines"),
    paths_txt(SolrType.text_general, true, true, true, "all path elements in the url"),
    host_s(SolrType.string, true, true, "host of the url"),
    canonical_s(SolrType.string, true, true, "url inside the canonical link element"),
    refresh_s(SolrType.string, true, true, "link from the url property inside the refresh link element"),
    li_txt(SolrType.text_general, true, true, true, "all texts in <li> tags"),
    licount_i(SolrType.integer, true, true, "number of <li> tags"),
    bold_txt(SolrType.text_general, true, true, true, "all texts inside of <b> or <strong> tags. no doubles. listed in the order of number of occurrences in decreasing order"),
    bold_val(SolrType.integer, true, true, true, "number of occurrences of texts in bold_txt"),
    boldcount_i(SolrType.integer, true, true, "total number of occurrences of <b> or <strong>"),
    italic_txt(SolrType.text_general, true, true, true, "all texts inside of <i> tags. no doubles. listed in the order of number of occurrences in decreasing order"),
    italic_val(SolrType.integer, true, true, true, "number of occurrences of texts in italic_txt"),
    italiccount_i(SolrType.integer, true, true, "total number of occurrences of <i>"),
    flash_b(SolrType.bool, true, true, "flag that shows if a swf file is linked"),
    frames_txt(SolrType.text_general, true, true, true, "list of all links to frames"),
    framesscount_i(SolrType.integer, true, true, "number of frames_txt"),
    iframes_txt(SolrType.text_general, true, true, true, "list of all links to iframes"),
    iframesscount_i(SolrType.integer, true, true, "number of iframes_txt"),
    ext_cms_txt(SolrType.text_general, true, true, true, "names of cms attributes; if several are recognized then they are listen in decreasing order of number of matching criterias"),
    ext_cms_val(SolrType.integer, true, true, true, "number of attributes that count for a specific cms in ext_cms_txt"),
    ext_ads_txt(SolrType.text_general, true, true, true, "names of ad-servers/ad-services"),
    ext_ads_val(SolrType.integer, true, true, true, "number of attributes counts in ext_ads_txt"),
    ext_community_txt(SolrType.text_general, true, true, true, "names of recognized community functions"),
    ext_community_val(SolrType.integer, true, true, true, "number of attribute counts in attr_community"),
    ext_maps_txt(SolrType.text_general, true, true, true, "names of map services"),
    ext_maps_val(SolrType.integer, true, true, true, "number of attribute counts in ext_maps_txt"),
    ext_tracker_txt(SolrType.text_general, true, true, true, "names of tracker server"),
    ext_tracker_val(SolrType.integer, true, true, true, "number of attribute counts in ext_tracker_txt"),
    ext_title_txt(SolrType.text_general, true, true, true, "names matching title expressions"),
    ext_title_val(SolrType.integer, true, true, true, "number of matching title expressions");

    private String solrFieldName = null; // solr field name in custom solr schema, defaults to solcell schema field name (= same as this.name() )
    private final SolrType type;
    private final boolean indexed, stored;
    private boolean multiValued, omitNorms;
    private String comment;

    private YaCySchema(final SolrType type, final boolean indexed, final boolean stored, final String comment) {
        this.type = type;
        this.indexed = indexed;
        this.stored = stored;
        this.multiValued = false;
        this.omitNorms = false;
        this.comment = comment;
    }

    private YaCySchema(final SolrType type, final boolean indexed, final boolean stored, final boolean multiValued, final String comment) {
        this(type, indexed, stored, comment);
        this.multiValued = multiValued;
    }

    private YaCySchema(final SolrType type, final boolean indexed, final boolean stored, final boolean multiValued, final boolean omitNorms, final String comment) {
        this(type, indexed, stored, multiValued, comment);
        this.omitNorms = omitNorms;
    }

    /**
     * Returns the YaCy default or (if available) custom field name for Solr
     * @return SolrFieldname String
     */
    @Override
    public final String getSolrFieldName() {
        return (this.solrFieldName == null ? this.name() : this.solrFieldName);
    }

    /**
     * Set a custom Solr field name (and converts it to lower case)
     * @param theValue = the field name
     */
    public final void setSolrFieldName(String theValue) {
        // make sure no empty string is assigned
        if ( (theValue != null) && (!theValue.isEmpty()) ) {
            this.solrFieldName = theValue.toLowerCase();
        } else {
            this.solrFieldName = null;
        }
    }

    @Override
    public final SolrType getType() {
        return this.type;
    }

    @Override
    public final boolean isIndexed() {
        return this.indexed;
    }

    @Override
    public final boolean isStored() {
        return this.stored;
    }

    @Override
    public final boolean isMultiValued() {
        return this.multiValued;
    }

    @Override
    public final boolean isOmitNorms() {
        return this.omitNorms;
    }

    @Override
    public final String getComment() {
        return this.comment;
    }

    public final void add(final SolrInputDocument doc, final String value) {
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final Date value) {
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final int value) {
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final long value) {
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final String[] value) {
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final List<String> value) {
        doc.setField(this.getSolrFieldName(), value.toArray(new String[value.size()]));
    }

    public final void add(final SolrInputDocument doc, final float value) {
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final double value) {
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final boolean value) {
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final String value, final float boost) {
        doc.setField(this.getSolrFieldName(), value, boost);
    }

}

