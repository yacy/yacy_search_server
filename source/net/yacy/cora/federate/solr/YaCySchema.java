/**
 *  YaCySchema
 *  Copyright 2011 by Michael Peter Christen
 *  First released 14.04.2011 at http://yacy.net
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

package net.yacy.cora.federate.solr;

import java.util.Date;
import java.util.List;


import org.apache.solr.common.SolrInputDocument;

public enum YaCySchema implements Schema {
    
    // mandatory
    id(SolrType.string, true, true, false, "primary key of document, the URL hash **mandatory field**"),
    sku(SolrType.text_en_splitting_tight, true, true, false, true, "url of document"),
    last_modified(SolrType.date, true, true, false, "last-modified from http header"),
    content_type(SolrType.string, true, true, true, "mime-type of document"),
    title(SolrType.text_general, true, true, true, "content of title tag"),
    host_id_s(SolrType.string, true, true, false, "id of the host, a 6-byte hash that is part of the document id"),// String hosthash();
    md5_s(SolrType.string, true, true, false, "the md5 of the raw source"),// String md5();
    exact_signature_l(SolrType.num_long, true, true, false, "the 64 bit hash of the org.apache.solr.update.processor.Lookup3Signature of text_t"),
    exact_signature_unique_b(SolrType.bool, true, true, false, "flag shows if exact_signature_l is unique at the time of document creation, used for double-check during search"),
    fuzzy_signature_l(SolrType.num_long, true, true, false, "64 bit of the Lookup3Signature from EnhancedTextProfileSignature of text_t"),
    fuzzy_signature_text_t(SolrType.text_general, true, true, false, "intermediate data produced in EnhancedTextProfileSignature: a list of word frequencies"),
    fuzzy_signature_unique_b(SolrType.bool, true, true, false, "flag shows if fuzzy_signature_l is unique at the time of document creation, used for double-check during search"),
    size_i(SolrType.num_integer, true, true, false, "the size of the raw source"),// int size();
    process_s(SolrType.string, true, true, false, "index creation comment"),
    failreason_t(SolrType.text_general, true, true, false, "fail reason if a page was not loaded. if the page was loaded then this field is empty"),
    failtype_s(SolrType.string, true, true, false, "fail type if a page was not loaded. This field is either empty, 'excl' or 'fail'"),
    httpstatus_i(SolrType.num_integer, true, true, false, "html status return code (i.e. \"200\" for ok), -1 if not loaded"),
    httpstatus_redirect_s(SolrType.num_integer, true, true, false, "html status return code (i.e. \"200\" for ok), -1 if not loaded"),
    references_i(SolrType.num_integer, true, true, false, "number of unique http references; used for ranking"),
    clickdepth_i(SolrType.num_integer, true, true, false, "depth of web page according to number of clicks from the 'main' page, which is the page that appears if only the host is entered as url"),

    // optional but recommended, part of index distribution
    load_date_dt(SolrType.date, true, true, false, "time when resource was loaded"),
    fresh_date_dt(SolrType.date, true, true, false, "date until resource shall be considered as fresh"),
    referrer_id_txt(SolrType.string, true, true, true, "ids of referrer to this document"),// byte[] referrerHash();
    publisher_t(SolrType.text_general, true, true, false, "the name of the publisher of the document"),// String dc_publisher();
    language_s(SolrType.string, true, true, false, "the language used in the document"),// byte[] language();
    audiolinkscount_i(SolrType.num_integer, true, true, false, "number of links to audio resources"),// int laudio();
    videolinkscount_i(SolrType.num_integer, true, true, false, "number of links to video resources"),// int lvideo();
    applinkscount_i(SolrType.num_integer, true, true, false, "number of links to application resources"),// int lapp();

    // optional but recommended
    coordinate_p(SolrType.location, true, true, false, "point in degrees of latitude,longitude as declared in WSG84"),
    ip_s(SolrType.string, true, true, false, "ip of host of url (after DNS lookup)"),
    author(SolrType.text_general, true, true, false, "content of author-tag"),
    description(SolrType.text_general, true, true, false, "content of description-tag"),
    keywords(SolrType.text_general, true, true, false, "content of keywords tag; words are separated by space"),
    charset_s(SolrType.string, true, true, false, "character encoding"),
    wordcount_i(SolrType.num_integer, true, true, false, "number of words in visible area"),
    inboundlinkscount_i(SolrType.num_integer, true, true, false, "total number of inbound links"),
    inboundlinksnofollowcount_i(SolrType.num_integer, true, true, false, "number of inbound links with nofollow tag"),
    outboundlinkscount_i(SolrType.num_integer, true, true, false, "external number of inbound links"),
    outboundlinksnofollowcount_i(SolrType.num_integer, true, true, false, "number of external links with nofollow tag"),
    imagescount_i(SolrType.num_integer, true, true, false, "number of images"),
    responsetime_i(SolrType.num_integer, true, true, false, "response time of target server in milliseconds"),
    text_t(SolrType.text_general, true, true, false, "all visible text"),
    synonyms_sxt(SolrType.string, true, true, true, "additional synonyms to the words in the text"),
    h1_txt(SolrType.text_general, true, true, true, "h1 header"),
    h2_txt(SolrType.text_general, true, true, true, "h2 header"),
    h3_txt(SolrType.text_general, true, true, true, "h3 header"),
    h4_txt(SolrType.text_general, true, true, true, "h4 header"),
    h5_txt(SolrType.text_general, true, true, true, "h5 header"),
    h6_txt(SolrType.text_general, true, true, true, "h6 header"),

    // optional values, not part of standard YaCy handling (but useful for external applications)
    collection_sxt(SolrType.string, true, true, true, "tags that are attached to crawls/index generation to separate the search result into user-defined subsets"),
    csscount_i(SolrType.num_integer, true, true, false, "number of entries in css_tag_txt and css_url_txt"),
    css_tag_txt(SolrType.text_general, true, true, true, "full css tag with normalized url"),
    css_url_txt(SolrType.text_general, true, true, true, "normalized urls within a css tag"),
    scripts_txt(SolrType.text_general, true, true, true, "normalized urls within a scripts tag"),
    scriptscount_i(SolrType.num_integer, true, true, false, "number of entries in scripts_txt"),
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
    robots_i(SolrType.num_integer, true, true, false, "content of <meta name=\"robots\" content=#content#> tag and the \"X-Robots-Tag\" HTTP property"),
    metagenerator_t(SolrType.text_general, true, true, false, "content of <meta name=\"generator\" content=#content#> tag"),
    inboundlinks_tag_txt(SolrType.text_general, true, true, true, "internal links, normalized (absolute URLs), as <a> - tag with anchor text and nofollow"),
    inboundlinks_protocol_sxt(SolrType.string, true, true, true, "internal links, only the protocol"),
    inboundlinks_urlstub_txt(SolrType.text_general, true, true, true, "internal links, the url only without the protocol"),
    inboundlinks_name_txt(SolrType.text_general, true, true, true, "internal links, the name property of the a-tag"),
    inboundlinks_rel_sxt(SolrType.string, true, true, true, "internal links, the rel property of the a-tag"),
    inboundlinks_relflags_val(SolrType.num_integer, true, true, true, "internal links, the rel property of the a-tag, coded binary"),
    inboundlinks_text_txt(SolrType.text_general, true, true, true, "internal links, the text content of the a-tag"),
    inboundlinks_text_chars_val(SolrType.num_integer, true, true, true, "internal links, the length of the a-tag as number of characters"),
    inboundlinks_text_words_val(SolrType.num_integer, true, true, true, "internal links, the length of the a-tag as number of words"),
    inboundlinks_alttag_txt(SolrType.text_general, true, true, true, "if the link is an image link, this contains the alt tag if the image is also liked as img link"),
    outboundlinks_tag_txt(SolrType.text_general, true, true, true, "external links, normalized (absolute URLs), as <a> - tag with anchor text and nofollow"),
    outboundlinks_protocol_sxt(SolrType.string, true, true, true, "external links, only the protocol"),
    outboundlinks_urlstub_txt(SolrType.text_general, true, true, true, "external links, the url only without the protocol"),
    outboundlinks_name_txt(SolrType.text_general, true, true, true, "external links, the name property of the a-tag"),
    outboundlinks_rel_sxt(SolrType.string, true, true, true, "external links, the rel property of the a-tag"),
    outboundlinks_relflags_val(SolrType.num_integer, true, true, true, "external links, the rel property of the a-tag, coded binary"),
    outboundlinks_text_txt(SolrType.text_general, true, true, true, "external links, the text content of the a-tag"),
    outboundlinks_text_chars_val(SolrType.num_integer, true, true, true, "external links, the length of the a-tag as number of characters"),
    outboundlinks_text_words_val(SolrType.num_integer, true, true, true, "external links, the length of the a-tag as number of words"),
    outboundlinks_alttag_txt(SolrType.text_general, true, true, true, "if the link is an image link, this contains the alt tag if the image is also liked as img link"),
    images_tag_txt(SolrType.text_general, true, true, true, " all image tags, encoded as <img> tag inclusive alt- and title property"),
    images_urlstub_txt(SolrType.text_general, true, true, true, "all image links without the protocol and '://'"),
    images_protocol_sxt(SolrType.text_general, true, true, true, "all image link protocols"),
    images_alt_txt(SolrType.text_general, true, true, true, "all image link alt tag"),
    images_withalt_i(SolrType.num_integer, true, true, false, "number of image links with alt tag"),
    htags_i(SolrType.num_integer, true, true, false, "binary pattern for the existance of h1..h6 headlines"),
    canonical_t(SolrType.text_general, true, true, false, "url inside the canonical link element"),
    refresh_s(SolrType.string, true, true, false, "link from the url property inside the refresh link element"),
    li_txt(SolrType.text_general, true, true, true, "all texts in <li> tags"),
    licount_i(SolrType.num_integer, true, true, false, "number of <li> tags"),
    bold_txt(SolrType.text_general, true, true, true, "all texts inside of <b> or <strong> tags. no doubles. listed in the order of number of occurrences in decreasing order"),
    boldcount_i(SolrType.num_integer, true, true, false, "total number of occurrences of <b> or <strong>"),
    italic_txt(SolrType.text_general, true, true, true, "all texts inside of <i> tags. no doubles. listed in the order of number of occurrences in decreasing order"),
    italiccount_i(SolrType.num_integer, true, true, false, "total number of occurrences of <i>"),
    underline_txt(SolrType.text_general, true, true, true, "all texts inside of <u> tags. no doubles. listed in the order of number of occurrences in decreasing order"),
    underlinecount_i(SolrType.num_integer, true, true, false, "total number of occurrences of <u>"),
    flash_b(SolrType.bool, true, true, false, "flag that shows if a swf file is linked"),
    frames_txt(SolrType.text_general, true, true, true, "list of all links to frames"),
    framesscount_i(SolrType.num_integer, true, true, false, "number of frames_txt"),
    iframes_txt(SolrType.text_general, true, true, true, "list of all links to iframes"),
    iframesscount_i(SolrType.num_integer, true, true, false, "number of iframes_txt"),

    url_protocol_s(SolrType.string, true, true, false, "the protocol of the url"),
    url_paths_sxt(SolrType.string, true, true, true, "all path elements in the url"),
    url_file_ext_s(SolrType.string, true, true, false, "the file name extension"),
    url_parameter_i(SolrType.num_integer, true, true, false, "number of key-value pairs in search part of the url"),
    url_parameter_key_sxt(SolrType.string, true, true, true, "the keys from key-value pairs in the search part of the url"),
    url_parameter_value_sxt(SolrType.string, true, true, true, "the values from key-value pairs in the search part of the url"),
    url_chars_i(SolrType.num_integer, true, true, false, "number of all characters in the url == length of sku field"),

    host_s(SolrType.string, true, true, false, "host of the url"),
    host_dnc_s(SolrType.string, true, true, false, "the Domain Class Name, either the TLD or a combination of ccSLD+TLD if a ccSLD is used."),
    host_organization_s(SolrType.string, true, true, false, "either the second level domain or, if a ccSLD is used, the third level domain"),
    host_organizationdnc_s(SolrType.string, true, true, false, "the organization and dnc concatenated with '.'"),
    host_subdomain_s(SolrType.string, true, true, false, "the remaining part of the host without organizationdnc"),

    title_count_i(SolrType.num_integer, true, true, false, "number of titles (counting the 'title' field) in the document"),
    title_chars_val(SolrType.num_integer, true, true, true, "number of characters for each title"),
    title_words_val(SolrType.num_integer, true, true, true, "number of words in each title"),

    description_count_i(SolrType.num_integer, true, true, false, "number of descriptions in the document. Its not counting the 'description' field since there is only one. But it counts the number of descriptions that appear in the document (if any)"),
    description_chars_val(SolrType.num_integer, true, true, true, "number of characters for each description"),
    description_words_val(SolrType.num_integer, true, true, true, "number of words in each description"),

    h1_i(SolrType.num_integer, true, true, false, "number of h1 header lines"),
    h2_i(SolrType.num_integer, true, true, false, "number of h2 header lines"),
    h3_i(SolrType.num_integer, true, true, false, "number of h3 header lines"),
    h4_i(SolrType.num_integer, true, true, false, "number of h4 header lines"),
    h5_i(SolrType.num_integer, true, true, false, "number of h5 header lines"),
    h6_i(SolrType.num_integer, true, true, false, "number of h6 header lines"),

    schema_org_breadcrumb_i(SolrType.num_integer, true, true, false, "number of itemprop=\"breadcrumb\" appearances in div tags"),
    opengraph_title_t(SolrType.text_general, true, true, false, "Open Graph Metadata from og:title metadata field, see http://ogp.me/ns#"),
    opengraph_type_s(SolrType.text_general, true, true, false, "Open Graph Metadata from og:type metadata field, see http://ogp.me/ns#"),
    opengraph_url_s(SolrType.text_general, true, true, false, "Open Graph Metadata from og:url metadata field, see http://ogp.me/ns#"),
    opengraph_image_s(SolrType.text_general, true, true, false, "Open Graph Metadata from og:image metadata field, see http://ogp.me/ns#"),
    
    // special values; can only be used if '_val' type is defined in schema file; this is not standard
    bold_val(SolrType.num_integer, true, true, true, "number of occurrences of texts in bold_txt"),
    italic_val(SolrType.num_integer, true, true, true, "number of occurrences of texts in italic_txt"),
    underline_val(SolrType.num_integer, true, true, true, "number of occurrences of texts in underline_txt"),
    ext_cms_txt(SolrType.text_general, true, true, true, "names of cms attributes; if several are recognized then they are listen in decreasing order of number of matching criterias"),
    ext_cms_val(SolrType.num_integer, true, true, true, "number of attributes that count for a specific cms in ext_cms_txt"),
    ext_ads_txt(SolrType.text_general, true, true, true, "names of ad-servers/ad-services"),
    ext_ads_val(SolrType.num_integer, true, true, true, "number of attributes counts in ext_ads_txt"),
    ext_community_txt(SolrType.text_general, true, true, true, "names of recognized community functions"),
    ext_community_val(SolrType.num_integer, true, true, true, "number of attribute counts in attr_community"),
    ext_maps_txt(SolrType.text_general, true, true, true, "names of map services"),
    ext_maps_val(SolrType.num_integer, true, true, true, "number of attribute counts in ext_maps_txt"),
    ext_tracker_txt(SolrType.text_general, true, true, true, "names of tracker server"),
    ext_tracker_val(SolrType.num_integer, true, true, true, "number of attribute counts in ext_tracker_txt"),
    ext_title_txt(SolrType.text_general, true, true, true, "names matching title expressions"),
    ext_title_val(SolrType.num_integer, true, true, true, "number of matching title expressions");

    public final static String VOCABULARY_PREFIX = "vocabulary_";
    public final static String VOCABULARY_SUFFIX = "_sxt";
    
    private String solrFieldName = null; // solr field name in custom solr schema, defaults to solcell schema field name (= same as this.name() )
    private final SolrType type;
    private final boolean indexed, stored;
    private boolean multiValued, omitNorms;
    private String comment;

    private YaCySchema(final SolrType type, final boolean indexed, final boolean stored, final boolean multiValued, final String comment) {
        this.type = type;
        this.indexed = indexed;
        this.stored = stored;
        this.multiValued = multiValued;
        this.omitNorms = false;
        this.comment = comment;
        assert type.appropriateName(this.name(), this.multiValued) : "bad configuration: " + this.name();
    }

    private YaCySchema(final SolrType type, final boolean indexed, final boolean stored, final boolean multiValued, final boolean omitNorms, final String comment) {
        this(type, indexed, stored, multiValued, comment);
        this.omitNorms = omitNorms;
        assert type.appropriateName(this.name(), this.multiValued) : "bad configuration: " + this.name();
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
        assert !this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final Date value) {
        assert !this.isMultiValued();
        assert this.type == SolrType.date;
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final int value) {
        assert !this.isMultiValued();
        assert this.type == SolrType.num_integer;
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final long value) {
        assert !this.isMultiValued();
        assert this.type == SolrType.num_long;
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final String[] value) {
        assert this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final Integer[] value) {
        assert this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final List<?> value) {
        assert this.isMultiValued();
        if (value == null || value.size() == 0) {
            if (this.type == SolrType.num_integer) {
                doc.setField(this.getSolrFieldName(), new Integer[0]);
            } else if (this.type == SolrType.string) {
                doc.setField(this.getSolrFieldName(), new String[0]);
            } else {
                assert false;
                doc.setField(this.getSolrFieldName(), new Object[0]);
            }
            return;
        }
        if (this.type == SolrType.num_integer) {
            assert (value.iterator().next() instanceof Integer);
            doc.setField(this.getSolrFieldName(), value.toArray(new Integer[value.size()]));
        } else if (this.type == SolrType.string || this.type == SolrType.text_general) {
            assert (value.iterator().next() instanceof String);
            doc.setField(this.getSolrFieldName(), value.toArray(new String[value.size()]));
        } else {
            assert false : "ADD: type is " + this.type.name();
            doc.setField(this.getSolrFieldName(), value.toArray(new Object[value.size()]));
        }
    }

    public final void add(final SolrInputDocument doc, final float value) {
        assert !this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final double value) {
        assert !this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    public final void add(final SolrInputDocument doc, final boolean value) {
        assert !this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

}

