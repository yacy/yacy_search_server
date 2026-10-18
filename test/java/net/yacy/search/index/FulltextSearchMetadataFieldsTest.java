package net.yacy.search.index;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import net.yacy.search.schema.CollectionSchema;

public class FulltextSearchMetadataFieldsTest {

    @Test
    public void regularRwiSearchOmitsLargeExtractedTextAndKeepsResultFields() {
        final String dynamicField = "vocabulary_example_sxt";
        final String[] fields = Fulltext.searchMetadataFields(false,
                Collections.singleton(dynamicField));
        final Set<String> fieldSet = new HashSet<>(Arrays.asList(fields));

        assertFalse(fieldSet.contains(CollectionSchema.text_t.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.fuzzy_signature_text_t.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.inboundlinks_anchortext_txt.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.outboundlinks_anchortext_txt.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.images_text_t.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.article_txt.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.bold_txt.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.inboundlinks_urlstub_sxt.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.css_url_sxt.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.scripts_sxt.getSolrFieldName()));
        assertFalse(fieldSet.contains(CollectionSchema.url_paths_sxt.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.id.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.sku.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.title.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.h1_txt.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.description_txt.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.images_urlstub_sxt.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.synonyms_sxt.getSolrFieldName()));
        assertTrue(fieldSet.contains(dynamicField));
    }

    @Test
    public void fullTextSnippetRequestIncludesFullText() {
        final String[] fields = Fulltext.searchMetadataFields(true, Collections.<String>emptySet());

        assertTrue(Arrays.asList(fields).contains(CollectionSchema.text_t.getSolrFieldName()));
        assertFalse(Arrays.asList(fields).contains(CollectionSchema.fuzzy_signature_text_t.getSolrFieldName()));
        assertFalse(Arrays.asList(fields).contains(CollectionSchema.article_txt.getSolrFieldName()));
    }

    @Test
    public void additionalFieldsCannotReenableFullTextForRegularSearch() {
        final Set<String> additionalFields = new HashSet<>();
        additionalFields.add(CollectionSchema.text_t.getSolrFieldName());
        final String[] fields = Fulltext.searchMetadataFields(false, additionalFields);

        assertFalse(Arrays.asList(fields).contains(CollectionSchema.text_t.getSolrFieldName()));
    }

    @Test
    public void configuredNavigatorFieldsRemainAvailable() {
        final String[] fields = Fulltext.searchMetadataFields(false,
                Collections.singleton(CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName()));

        assertTrue(Arrays.asList(fields).contains(CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName()));
    }
}
