/**
 *  WebgraphSchema
 *  Copyright 2011 by Michael Peter Christen
 *  First released 19.02.2013 at http://yacy.net
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

public enum WebgraphSchema implements SchemaDeclaration {
    
    // index organisation
    id(SolrType.string, true, true, false, false, false, "primary key of document, a combination of <source-url-hash><target-url-hash><four-digit-hex-counter> (28 characters)"),
    last_modified(SolrType.date, true, true, false, false, false, "last-modified from http header"),
    load_date_dt(SolrType.date, true, true, false, false, false, "time when resource was loaded"),
    collection_sxt(SolrType.string, true, true, true, false, false, "tags that are attached to crawls/index generation to separate the search result into user-defined subsets"),
    process_sxt(SolrType.string, true, true, true, false, false, "needed (post-)processing steps on this metadata set."),
    harvestkey_s(SolrType.string, true, true, false, false, false, "key from a harvest process (i.e. the crawl profile hash key) which is needed for near-realtime postprocessing. This shall be deleted as soon as postprocessing has been terminated."),
    
    // source information
    source_id_s(SolrType.string, true, true, false, false, false, "primary key of document, the URL hash (source)"),
    source_protocol_s(SolrType.string, true, true, false, false, false, "the protocol of the url (source)"),
    source_urlstub_s(SolrType.string, true, true, false, false, false, "the url without the protocol (source)"),
    source_file_name_s(SolrType.string, true, true, false, false, false, "the file name without the extension (source)"),
    source_file_ext_s(SolrType.string, true, true, false, false, false, "the file name extension (source)"),
    source_chars_i(SolrType.num_integer, true, true, false, false, false, "number of all characters in the url (source)"),
    source_path_s(SolrType.string, true, true, false, false, false, "path of the url (source)"),
    source_path_folders_count_i(SolrType.num_integer, true, true, false, false, false, "count of all path elements in the url (source)"),
    source_path_folders_sxt(SolrType.string, true, true, true, false, false, "all path elements in the url without the file name (source)"),
    source_parameter_count_i(SolrType.num_integer, true, true, false, false, false, "number of key-value pairs in search part of the url (source)"),
    source_parameter_key_sxt(SolrType.string, true, true, true, false, false, "the keys from key-value pairs in the search part of the url (source)"),
    source_parameter_value_sxt(SolrType.string, true, true, true, false, false, "the values from key-value pairs in the search part of the url (source)"),
    source_crawldepth_i(SolrType.num_integer, true, true, false, false, false, "depth of web page according to number of clicks from the 'main' page, which is the page that appears if only the host is entered as url (source)"),
    source_cr_host_norm_i(SolrType.num_integer, true, true, false, false, false, "copy of the citation rank norm value from the source link"),

    source_host_s(SolrType.string, true, true, false, false, false, "host of the url (source)"),
    source_host_id_s(SolrType.string, true, true, false, false, false, "id of the host (source)"),
    source_host_dnc_s(SolrType.string, true, true, false, false, false, "the Domain Class Name, either the TLD or a combination of ccSLD+TLD if a ccSLD is used (source)"),
    source_host_organization_s(SolrType.string, true, true, false, false, false, "either the second level domain or, if a ccSLD is used, the third level domain"),
    source_host_organizationdnc_s(SolrType.string, true, true, false, false, false, "the organization and dnc concatenated with '.' (source)"),
    source_host_subdomain_s(SolrType.string, true, true, false, false, false, "the remaining part of the host without organizationdnc (source)"),

    // information in the source about the target
    target_linktext_t(SolrType.text_general, true, true, false, false, true, "the text content of the a-tag (in source, but pointing to a target)"),
    target_linktext_charcount_i(SolrType.num_integer, true, true, false, false, false, "the length of the a-tag content text as number of characters (in source, but pointing to a target)"),
    target_linktext_wordcount_i(SolrType.num_integer, true, true, false, false, false, "the length of the a-tag content text as number of words (in source, but pointing to a target)"),
    target_alt_t(SolrType.text_general, true, true, false, false, true, "if the link is an image link, this contains the alt tag if the image is also liked as img link (in source, but pointing to a target)"),
    target_alt_charcount_i(SolrType.num_integer, true, true, false, false, false, "the length of the a-tag content text as number of characters (in source, but pointing to a target)"),
    target_alt_wordcount_i(SolrType.num_integer, true, true, false, false, false, "the length of the a-tag content text as number of words (in source, but pointing to a target)"),
    target_name_t(SolrType.text_general, true, true, false, false, true, "the name property of the a-tag (in source, but pointing to a target)"),
    target_rel_s(SolrType.string, true, true, false, false, false, "the rel property of the a-tag (in source, but pointing to a target)"),
    target_relflags_i(SolrType.num_integer, true, true, false, false, false, "the rel property of the a-tag, coded binary (in source, but pointing to a target)"),
    
    // target information
    target_id_s(SolrType.string, true, true, false, false, false, "primary key of document, the URL hash (target)"),
    target_order_i(SolrType.num_integer, true, true, false, false, false, "order number of target url, a count from first to last URL on the source page (target)"),
    target_protocol_s(SolrType.string, true, true, false, false, false, "the protocol of the url (target)"),
    target_urlstub_s(SolrType.string, true, true, false, false, false, "the url without the protocol (target)"),
    target_file_name_s(SolrType.string, true, true, false, false, false, "the file name without the extension (target)"),
    target_file_ext_s(SolrType.string, true, true, false, false, true, "the file name extension (target)"),
    target_chars_i(SolrType.num_integer, true, true, false, false, false, "number of all characters in the url (target)"),
    target_path_s(SolrType.string, true, true, false, false, false, "path of the url (target)"),
    target_path_folders_count_i(SolrType.num_integer, true, true, false, false, false, "count of all path elements in the url (target)"),
    target_path_folders_sxt(SolrType.string, true, true, true, false, true, "all path elements in the url without the file name (target)"),
    target_parameter_count_i(SolrType.num_integer, true, true, false, false, false, "number of key-value pairs in search part of the url (target)"),
    target_parameter_key_sxt(SolrType.string, true, true, true, false, false, "the keys from key-value pairs in the search part of the url (target)"),
    target_parameter_value_sxt(SolrType.string, true, true, true, false, true, "the values from key-value pairs in the search part of the url (target)"),
    target_crawldepth_i(SolrType.num_integer, true, true, false, false, false, "depth of web page according to number of clicks from the 'main' page, which is the page that appears if only the host is entered as url (target)"),
    target_cr_host_norm_i(SolrType.num_integer, true, true, false, false, false, "copy of the citation rank norm value from the target link; this is only filled if the target host is identical to the source host"),

    target_host_s(SolrType.string, true, true, false, false, true, "host of the url (target)"),
    target_host_id_s(SolrType.string, true, true, false, false, false, "id of the host (target)"),
    target_host_dnc_s(SolrType.string, true, true, false, false, true, "the Domain Class Name, either the TLD or a combination of ccSLD+TLD if a ccSLD is used (target)"),
    target_host_organization_s(SolrType.string, true, true, false, false, true, "either the second level domain or, if a ccSLD is used, the third level domain (target)"),
    target_host_organizationdnc_s(SolrType.string, true, true, false, false, true, "the organization and dnc concatenated with '.' (target)"),
    target_host_subdomain_s(SolrType.string, true, true, false, false, true, "the remaining part of the host without organizationdnc (target)"),
   
    target_inbound_b(SolrType.bool, true, true, false, false, false, "flag shows if the target host is equal to the source host");
    
    public final static String CORE_NAME = "webgraph";
    
    public final static String VOCABULARY_PREFIX = "vocabulary_";
    public final static String VOCABULARY_SUFFIX = "_sxt";
    
    private String solrFieldName = null; // solr field name in custom solr schema
    private final SolrType type;
    private final boolean indexed, stored, multiValued, omitNorms, searchable;
    private String comment;


    private WebgraphSchema(final SolrType type, final boolean indexed, final boolean stored, final boolean multiValued, final boolean omitNorms, final boolean searchable, final String comment) {
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

