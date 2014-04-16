/**
 *  CollectionSchema
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

package net.yacy.search.schema;

import java.util.Date;
import java.util.List;

import net.yacy.cora.federate.solr.SchemaDeclaration;
import net.yacy.cora.federate.solr.SolrType;

import org.apache.solr.common.SolrInputDocument;

public enum CollectionSchema implements SchemaDeclaration {
    
    // mandatory
    id(SolrType.string, true, true, false, false, false, "primary key of document, the URL hash **mandatory field**"),
    sku(SolrType.string, true, true, false, true, true, "url of document"), // a 'sku' is a stock-keeping unit, a unique identifier and a default field in unmodified solr.
    //sku(SolrType.text_en_splitting_tight, true, true, false, true, true, "url of document"), // a 'sku' is a stock-keeping unit, a unique identifier and a default field in unmodified solr.
    last_modified(SolrType.date, true, true, false, false, false, "last-modified from http header"),
    content_type(SolrType.string, true, true, true, false, false, "mime-type of document"),
    title(SolrType.text_general, true, true, true, false, true, "content of title tag"),
    title_exact_signature_l(SolrType.num_long, true, true, false, false, false, "the 64 bit hash of the org.apache.solr.update.processor.Lookup3Signature of title, used to compute title_unique_b"),
    title_unique_b(SolrType.bool, true, true, false, false, false, "flag shows if title is unique in the whole index; if yes and another document appears with same title, the unique-flag is set to false"),
    host_id_s(SolrType.string, true, true, false, false, false, "id of the host, a 6-byte hash that is part of the document id"),// String hosthash();
    md5_s(SolrType.string, true, true, false, false, false, "the md5 of the raw source"),// String md5();
    exact_signature_l(SolrType.num_long, true, true, false, false, false, "the 64 bit hash of the org.apache.solr.update.processor.Lookup3Signature of text_t"),
    exact_signature_unique_b(SolrType.bool, true, true, false, false, false, "flag shows if exact_signature_l is unique at the time of document creation, used for double-check during search"),
    exact_signature_copycount_i(SolrType.num_integer, true, true, false, false, false, "counter for the number of documents which are not unique (== count of not-unique-flagged documents + 1)"),
    fuzzy_signature_l(SolrType.num_long, true, true, false, false, false, "64 bit of the Lookup3Signature from EnhancedTextProfileSignature of text_t"),
    fuzzy_signature_text_t(SolrType.text_general, true, true, false, false, true, "intermediate data produced in EnhancedTextProfileSignature: a list of word frequencies"),
    fuzzy_signature_unique_b(SolrType.bool, true, true, false, false, false, "flag shows if fuzzy_signature_l is unique at the time of document creation, used for double-check during search"),
    fuzzy_signature_copycount_i(SolrType.num_integer, true, true, false, false, false, "counter for the number of documents which are not unique (== count of not-unique-flagged documents + 1)"),
    size_i(SolrType.num_integer, true, true, false, false, false, "the size of the raw source"),// int size();
    failreason_s(SolrType.string, true, true, false, false, false, "fail reason if a page was not loaded. if the page was loaded then this field is empty"),
    failtype_s(SolrType.string, true, true, false, false, false, "fail type if a page was not loaded. This field is either empty, 'excl' or 'fail'"),
    httpstatus_i(SolrType.num_integer, true, true, false, false, false, "html status return code (i.e. \"200\" for ok), -1 if not loaded"),
    httpstatus_redirect_s(SolrType.num_integer, true, true, false, false, false, "html status return code (i.e. \"200\" for ok), -1 if not loaded"),
    references_i(SolrType.num_integer, true, true, false, false, false, "number of unique http references, should be equal to references_internal_i + references_external_i"),
    references_internal_i(SolrType.num_integer, true, true, false, false, false, "number of unique http references from same host to referenced url"),
    references_external_i(SolrType.num_integer, true, true, false, false, false, "number of unique http references from external hosts"),
    references_exthosts_i(SolrType.num_integer, true, true, false, false, false, "number of external hosts which provide http references"),
    crawldepth_i(SolrType.num_integer, true, true, false, false, false, "crawl depth of web page according to the number of steps that the crawler did to get to this document; if the crawl was started at a root document, then this is equal to the clickdepth"),
    process_sxt(SolrType.string, true, true, true, false, false, "needed (post-)processing steps on this metadata set"),
    harvestkey_s(SolrType.string, true, true, false, false, false, "key from a harvest process (i.e. the crawl profile hash key) which is needed for near-realtime postprocessing. This shall be deleted as soon as postprocessing has been terminated."),
    
    // optional but recommended, part of index distribution
    load_date_dt(SolrType.date, true, true, false, false, false, "time when resource was loaded"),
    fresh_date_dt(SolrType.date, true, true, false, false, false, "date until resource shall be considered as fresh"),
    referrer_id_s(SolrType.string, true, true, false, false, false, "id of the referrer to this document, discovered during crawling"),// byte[] referrerHash();
    publisher_t(SolrType.text_general, true, true, false, false, true, "the name of the publisher of the document"),// String dc_publisher();
    language_s(SolrType.string, true, true, false, false, false, "the language used in the document"),// byte[] language();
    audiolinkscount_i(SolrType.num_integer, true, true, false, false, false, "number of links to audio resources"),// int laudio();
    videolinkscount_i(SolrType.num_integer, true, true, false, false, false, "number of links to video resources"),// int lvideo();
    applinkscount_i(SolrType.num_integer, true, true, false, false, false, "number of links to application resources"),// int lapp();

    // optional but recommended
    coordinate_p(SolrType.location, true, true, false, false, false, "point in degrees of latitude,longitude as declared in WSG84"),
    coordinate_p_0_coordinate(SolrType.coordinate, true, true, false, false, false, "automatically created subfield, (latitude)"),
    coordinate_p_1_coordinate(SolrType.coordinate, true, true, false, false, false, "automatically created subfield, (longitude)"),
    ip_s(SolrType.string, true, true, false, false, false, "ip of host of url (after DNS lookup)"),
    author(SolrType.text_general, true, true, false, false, true, "content of author-tag"),
    author_sxt(SolrType.string, true, true, true, false, false, "content of author-tag as copy-field from author. This is used for facet generation"),
    description_txt(SolrType.text_general, true, true, true, false, true, "content of description-tag(s)"),
    description_exact_signature_l(SolrType.num_long, true, true, false, false, false, "the 64 bit hash of the org.apache.solr.update.processor.Lookup3Signature of description, used to compute description_unique_b"),
    description_unique_b(SolrType.bool, true, true, false, false, false, "flag shows if description is unique in the whole index; if yes and another document appears with same description, the unique-flag is set to false"),
    keywords(SolrType.text_general, true, true, false, false, true, "content of keywords tag; words are separated by space"),
    charset_s(SolrType.string, true, true, false, false, false, "character encoding"),
    wordcount_i(SolrType.num_integer, true, true, false, false, false, "number of words in visible area"),
    linkscount_i(SolrType.num_integer, true, true, false, false, false, "number of all outgoing links; including linksnofollowcount_i"),
    linksnofollowcount_i(SolrType.num_integer, true, true, false, false, false, "number of all outgoing inks with nofollow tag"),
    inboundlinkscount_i(SolrType.num_integer, true, true, false, false, false, "number of outgoing inbound (to same domain) links; including inboundlinksnofollowcount_i"),
    inboundlinksnofollowcount_i(SolrType.num_integer, true, true, false, false, false, "number of outgoing inbound (to same domain) links with nofollow tag"),
    outboundlinkscount_i(SolrType.num_integer, true, true, false, false, false, "number of outgoing outbound (to other domain) links, including outboundlinksnofollowcount_i"),
    outboundlinksnofollowcount_i(SolrType.num_integer, true, true, false, false, false, "number of outgoing outbound (to other domain) links with nofollow tag"),
    imagescount_i(SolrType.num_integer, true, true, false, false, false, "number of images"),
    responsetime_i(SolrType.num_integer, true, true, false, false, false, "response time of target server in milliseconds"),
    text_t(SolrType.text_general, true, true, false, false, true, "all visible text"),
    synonyms_sxt(SolrType.string, true, true, true, false, true, "additional synonyms to the words in the text"),
    h1_txt(SolrType.text_general, true, true, true, false, true, "h1 header"),
    h2_txt(SolrType.text_general, true, true, true, false, true, "h2 header"),
    h3_txt(SolrType.text_general, true, true, true, false, true, "h3 header"),
    h4_txt(SolrType.text_general, true, true, true, false, true, "h4 header"),
    h5_txt(SolrType.text_general, true, true, true, false, true, "h5 header"),
    h6_txt(SolrType.text_general, true, true, true, false, true, "h6 header"),

    // optional values, not part of standard YaCy handling (but useful for external applications)
    collection_sxt(SolrType.string, true, true, true, false, false, "tags that are attached to crawls/index generation to separate the search result into user-defined subsets"),
    csscount_i(SolrType.num_integer, true, true, false, false, false, "number of entries in css_tag_txt and css_url_txt"),
    css_tag_sxt(SolrType.string, true, true, true, false, false, "full css tag with normalized url"),
    css_url_sxt(SolrType.string, true, true, true, false, false, "normalized urls within a css tag"),
    scripts_sxt(SolrType.string, true, true, true, false, false, "normalized urls within a scripts tag"),
    scriptscount_i(SolrType.num_integer, true, true, false, false, false, "number of entries in scripts_sxt"),
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
    robots_i(SolrType.num_integer, true, true, false, false, false, "content of <meta name=\"robots\" content=#content#> tag and the \"X-Robots-Tag\" HTTP property"),
    metagenerator_t(SolrType.text_general, true, true, false, false, false, "content of <meta name=\"generator\" content=#content#> tag"),
    inboundlinks_protocol_sxt(SolrType.string, true, true, true, false, false, "internal links, only the protocol"),
    inboundlinks_urlstub_sxt(SolrType.string, true, true, true, false, true, "internal links, the url only without the protocol"),
    inboundlinks_anchortext_txt(SolrType.text_general, true, true, true, false, true, "internal links, the visible anchor text"),
    outboundlinks_protocol_sxt(SolrType.string, true, true, true, false, false, "external links, only the protocol"),
    outboundlinks_urlstub_sxt(SolrType.string, true, true, true, false, true, "external links, the url only without the protocol"),
    outboundlinks_anchortext_txt(SolrType.text_general, true, true, true, false, true, "external links, the visible anchor text"),
    
    images_text_t(SolrType.text_general, true, true, false, false, true, "all text/words appearing in image alt texts or the tokenized url"),
    images_urlstub_sxt(SolrType.string, true, true, true, false, true, "all image links without the protocol and '://'"),
    images_protocol_sxt(SolrType.string, true, true, true, false, false, "all image link protocols"),
    images_alt_sxt(SolrType.string, true, true, true, false, true, "all image link alt tag"), // no need to index this; don't turn it into a txt field; use images_text_t instead
    images_height_val(SolrType.num_integer, true, true, true, false, false, "size of images:height"),
    images_width_val(SolrType.num_integer, true, true, true, false, false, "size of images:width"),
    images_pixel_val(SolrType.num_integer, true, true, true, false, false, "size of images as number of pixels (easier for a search restriction than with and height)"),
    images_withalt_i(SolrType.num_integer, true, true, false, false, false, "number of image links with alt tag"),
    htags_i(SolrType.num_integer, true, true, false, false, false, "binary pattern for the existance of h1..h6 headlines"),
    canonical_s(SolrType.string, true, true, false, false, false, "url inside the canonical link element"),
    canonical_equal_sku_b(SolrType.bool, true, true, false, false, false, "flag shows if the url in canonical_t is equal to sku"),
    refresh_s(SolrType.string, true, true, false, false, false, "link from the url property inside the refresh link element"),
    li_txt(SolrType.text_general, true, true, true, false, true, "all texts in <li> tags"),
    licount_i(SolrType.num_integer, true, true, false, false, false, "number of <li> tags"),
    bold_txt(SolrType.text_general, true, true, true, false, true, "all texts inside of <b> or <strong> tags. no doubles. listed in the order of number of occurrences in decreasing order"),
    boldcount_i(SolrType.num_integer, true, true, false, false, false, "total number of occurrences of <b> or <strong>"),
    italic_txt(SolrType.text_general, true, true, true, false, true, "all texts inside of <i> tags. no doubles. listed in the order of number of occurrences in decreasing order"),
    italiccount_i(SolrType.num_integer, true, true, false, false, false, "total number of occurrences of <i>"),
    underline_txt(SolrType.text_general, true, true, true, false, true, "all texts inside of <u> tags. no doubles. listed in the order of number of occurrences in decreasing order"),
    underlinecount_i(SolrType.num_integer, true, true, false, false, false, "total number of occurrences of <u>"),
    flash_b(SolrType.bool, true, true, false, false, false, "flag that shows if a swf file is linked"),
    frames_sxt(SolrType.string, true, true, true, false, false, "list of all links to frames"),
    framesscount_i(SolrType.num_integer, true, true, false, false, false, "number of frames_txt"),
    iframes_sxt(SolrType.string, true, true, true, false, false, "list of all links to iframes"),
    iframesscount_i(SolrType.num_integer, true, true, false, false, false, "number of iframes_txt"),

    hreflang_url_sxt(SolrType.string, true, true, true, false, false, "url of the hreflang link tag, see http://support.google.com/webmasters/bin/answer.py?hl=de&answer=189077"),
    hreflang_cc_sxt(SolrType.string, true, true, true, false, false, "country code of the hreflang link tag, see http://support.google.com/webmasters/bin/answer.py?hl=de&answer=189077"),
    navigation_url_sxt(SolrType.string, true, true, true, false, false, "page navigation url, see http://googlewebmastercentral.blogspot.de/2011/09/pagination-with-relnext-and-relprev.html"),
    navigation_type_sxt(SolrType.string, true, true, true, false, false, "page navigation rel property value, can contain one of {top,up,next,prev,first,last}"),
    publisher_url_s(SolrType.string, true, true, false, false, false, "publisher url as defined in http://support.google.com/plus/answer/1713826?hl=de"),
    
    url_protocol_s(SolrType.string, true, true, false, false, false, "the protocol of the url"),
    url_file_name_s(SolrType.string, true, true, false, false, true, "the file name (which is the string after the last '/' and before the query part from '?' on) without the file extension"),
    url_file_name_tokens_t(SolrType.text_general, true, true, false, false, true, "tokens generated from url_file_name_s which can be used for better matching and result boosting"),
    url_file_ext_s(SolrType.string, true, true, false, false, true, "the file name extension"),
    url_paths_sxt(SolrType.string, true, true, true, false, true, "all path elements in the url hpath (see: http://www.ietf.org/rfc/rfc1738.txt) without the file name"),
    url_parameter_i(SolrType.num_integer, true, true, false, false, false, "number of key-value pairs in search part of the url"),
    url_parameter_key_sxt(SolrType.string, true, true, true, false, false, "the keys from key-value pairs in the search part of the url"),
    url_parameter_value_sxt(SolrType.string, true, true, true, false, false, "the values from key-value pairs in the search part of the url"),
    url_chars_i(SolrType.num_integer, true, true, false, false, false, "number of all characters in the url == length of sku field"),

    host_s(SolrType.string, true, true, false, false, true, "host of the url"),
    host_dnc_s(SolrType.string, true, true, false, false, true, "the Domain Class Name, either the TLD or a combination of ccSLD+TLD if a ccSLD is used."),
    host_organization_s(SolrType.string, true, true, false, false, true, "either the second level domain or, if a ccSLD is used, the third level domain"),
    host_organizationdnc_s(SolrType.string, true, true, false, false, true, "the organization and dnc concatenated with '.'"),
    host_subdomain_s(SolrType.string, true, true, false, false, true, "the remaining part of the host without organizationdnc"),
    host_extent_i(SolrType.num_integer, true, true, false, false, false, "number of documents from the same host; can be used to measure references_internal_i for likelihood computation"),

    title_count_i(SolrType.num_integer, true, true, false, false, false, "number of titles (counting the 'title' field) in the document"),
    title_chars_val(SolrType.num_integer, true, true, true, false, false, "number of characters for each title"),
    title_words_val(SolrType.num_integer, true, true, true, false, false, "number of words in each title"),

    description_count_i(SolrType.num_integer, true, true, false, false, false, "number of descriptions in the document. Its not counting the 'description' field since there is only one. But it counts the number of descriptions that appear in the document (if any)"),
    description_chars_val(SolrType.num_integer, true, true, true, false, false, "number of characters for each description"),
    description_words_val(SolrType.num_integer, true, true, true, false, false, "number of words in each description"),

    h1_i(SolrType.num_integer, true, true, false, false, false, "number of h1 header lines"),
    h2_i(SolrType.num_integer, true, true, false, false, false, "number of h2 header lines"),
    h3_i(SolrType.num_integer, true, true, false, false, false, "number of h3 header lines"),
    h4_i(SolrType.num_integer, true, true, false, false, false, "number of h4 header lines"),
    h5_i(SolrType.num_integer, true, true, false, false, false, "number of h5 header lines"),
    h6_i(SolrType.num_integer, true, true, false, false, false, "number of h6 header lines"),

    schema_org_breadcrumb_i(SolrType.num_integer, true, true, false, false, false, "number of itemprop=\"breadcrumb\" appearances in div tags"),
    opengraph_title_t(SolrType.text_general, true, true, false, false, true, "Open Graph Metadata from og:title metadata field, see http://ogp.me/ns#"),
    opengraph_type_s(SolrType.text_general, true, true, false, false, false, "Open Graph Metadata from og:type metadata field, see http://ogp.me/ns#"),
    opengraph_url_s(SolrType.text_general, true, true, false, false, false, "Open Graph Metadata from og:url metadata field, see http://ogp.me/ns#"),
    opengraph_image_s(SolrType.text_general, true, true, false, false, false, "Open Graph Metadata from og:image metadata field, see http://ogp.me/ns#"),
    
    // link structure for ranking
    cr_host_count_i(SolrType.num_integer, true, true, false, false, false, "the number of documents within a single host"),
    cr_host_chance_d(SolrType.num_double, true, true, false, false, false, "the chance to click on this page when randomly clicking on links within on one host"),
    cr_host_norm_i(SolrType.num_integer, true, true, false, false, false, "normalization of chance: 0 for lower halve of cr_host_count_i urls, 1 for 1/2 of the remaining and so on. the maximum number is 10"),
    
    // custom rating; values to influence the ranking in combination with boost rules
    rating_i(SolrType.num_integer, true, true, false, false, false, "custom rating; to be set with external rating information"),
    
    // special values; can only be used if '_val' type is defined in schema file; this is not standard
    bold_val(SolrType.num_integer, true, true, true, false, false, "number of occurrences of texts in bold_txt"),
    italic_val(SolrType.num_integer, true, true, true, false, false, "number of occurrences of texts in italic_txt"),
    underline_val(SolrType.num_integer, true, true, true, false, false, "number of occurrences of texts in underline_txt"),
    ext_cms_txt(SolrType.text_general, true, true, true, false, false, "names of cms attributes; if several are recognized then they are listen in decreasing order of number of matching criterias"),
    ext_cms_val(SolrType.num_integer, true, true, true, false, false, "number of attributes that count for a specific cms in ext_cms_txt"),
    ext_ads_txt(SolrType.text_general, true, true, true, false, false, "names of ad-servers/ad-services"),
    ext_ads_val(SolrType.num_integer, true, true, true, false, false, "number of attributes counts in ext_ads_txt"),
    ext_community_txt(SolrType.text_general, true, true, true, false, false, "names of recognized community functions"),
    ext_community_val(SolrType.num_integer, true, true, true, false, false, "number of attribute counts in attr_community"),
    ext_maps_txt(SolrType.text_general, true, true, true, false, false, "names of map services"),
    ext_maps_val(SolrType.num_integer, true, true, true, false, false, "number of attribute counts in ext_maps_txt"),
    ext_tracker_txt(SolrType.text_general, true, true, true, false, false, "names of tracker server"),
    ext_tracker_val(SolrType.num_integer, true, true, true, false, false, "number of attribute counts in ext_tracker_txt"),
    ext_title_txt(SolrType.text_general, true, true, true, false, false, "names matching title expressions"),
    ext_title_val(SolrType.num_integer, true, true, true, false, false, "number of matching title expressions");

    public final static String CORE_NAME = "collection1"; // this was the default core name up to Solr 4.4.0. This default name was stored in CoreContainer.DEFAULT_DEFAULT_CORE_NAME but was removed in Solr 4.5.0
    
    public final static String VOCABULARY_PREFIX = "vocabulary_";
    public final static String VOCABULARY_SUFFIX = "_sxt";
    
    private String solrFieldName = null; // solr field name in custom solr schema, defaults to solcell schema field name (= same as this.name() )
    private final SolrType type;
    private final boolean indexed, stored, searchable, multiValued, omitNorms;
    private String comment;

    private CollectionSchema(final SolrType type, final boolean indexed, final boolean stored, final boolean multiValued, final boolean omitNorms, final boolean searchable, final String comment) {
        this.type = type;
        this.indexed = indexed;
        this.stored = stored;
        this.multiValued = multiValued;
        this.omitNorms = omitNorms;
        this.searchable = searchable;
        this.comment = comment;
        // verify our naming scheme
        String name = this.name();
        int p = name.indexOf('_');
        if (p > 0) {
            String ext = name.substring(p + 1);
            assert !ext.equals("i") || (type == SolrType.num_integer && !multiValued) : name;
            assert !ext.equals("l") || (type == SolrType.num_long && !multiValued) : name;
            assert !ext.equals("b") || (type == SolrType.bool && !multiValued) : name;
            assert !ext.equals("s") || (type == SolrType.string && !multiValued) : name;
            assert !ext.equals("sxt") || (type == SolrType.string && multiValued) : name;
            assert !ext.equals("dt") || (type == SolrType.date && !multiValued) : name;
            assert !ext.equals("t") || (type == SolrType.text_general && !multiValued) : name;
            assert !ext.equals("coordinate") || (type == SolrType.coordinate && !multiValued) : name;
            assert !ext.equals("txt") || (type == SolrType.text_general && multiValued) : name;
            assert !ext.equals("val") || (type == SolrType.num_integer && multiValued) : name;
            assert !ext.equals("d") || (type == SolrType.num_double && !multiValued) : name;
        }
        assert type.appropriateName(this) : "bad configuration: " + this.name();
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
    @Override
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
    public final boolean isSearchable() {
        return this.searchable;
    }

    @Override
    public final String getComment() {
        return this.comment;
    }

    @Override
    public final void add(final SolrInputDocument doc, final String value) {
        assert !this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    @Override
    public final void add(final SolrInputDocument doc, final Date value) {
        assert !this.isMultiValued();
        assert this.type == SolrType.date;
        doc.setField(this.getSolrFieldName(), value);
    }

    @Override
    public final void add(final SolrInputDocument doc, final int value) {
        assert !this.isMultiValued();
        assert this.type == SolrType.num_integer;
        doc.setField(this.getSolrFieldName(), value);
    }

    @Override
    public final void add(final SolrInputDocument doc, final long value) {
        assert !this.isMultiValued();
        assert this.type == SolrType.num_long;
        doc.setField(this.getSolrFieldName(), value);
    }

    @Override
    public final void add(final SolrInputDocument doc, final String[] value) {
        assert this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    @Override
    public final void add(final SolrInputDocument doc, final Integer[] value) {
        assert this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    @Override
    public final void add(final SolrInputDocument doc, final List<?> value) {
        assert this.isMultiValued();
        if (value == null || value.size() == 0) {
            if (this.type == SolrType.num_integer) {
                doc.setField(this.getSolrFieldName(), new Integer[0]);
            } else if (this.type == SolrType.string || this.type == SolrType.text_general) {
                doc.setField(this.getSolrFieldName(), new String[0]);
            } else {
                assert false : "ADD(1): type is " + this.type.name();
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
            assert false : "ADD(2): type is " + this.type.name();
            doc.setField(this.getSolrFieldName(), value.toArray(new Object[value.size()]));
        }
    }

    @Override
    public final void add(final SolrInputDocument doc, final float value) {
        assert !this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    @Override
    public final void add(final SolrInputDocument doc, final double value) {
        assert !this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

    @Override
    public final void add(final SolrInputDocument doc, final boolean value) {
        assert !this.isMultiValued();
        doc.setField(this.getSolrFieldName(), value);
    }

}

