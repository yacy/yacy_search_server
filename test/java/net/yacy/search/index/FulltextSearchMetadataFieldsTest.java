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
    public void regularRwiSearchOmitsOnlyFullTextAndKeepsDynamicFields() {
        final String dynamicField = "vocabulary_example_sxt";
        final String[] fields = Fulltext.searchMetadataFields(false,
                Collections.singleton(dynamicField));
        final Set<String> fieldSet = new HashSet<>(Arrays.asList(fields));

        assertFalse(fieldSet.contains(CollectionSchema.text_t.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.id.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.sku.getSolrFieldName()));
        assertTrue(fieldSet.contains(CollectionSchema.synonyms_sxt.getSolrFieldName()));
        assertTrue(fieldSet.contains(dynamicField));
    }

    @Test
    public void fullTextSnippetRequestIncludesFullText() {
        final String[] fields = Fulltext.searchMetadataFields(true, Collections.<String>emptySet());

        assertTrue(Arrays.asList(fields).contains(CollectionSchema.text_t.getSolrFieldName()));
    }

    @Test
    public void additionalFieldsCannotReenableFullTextForRegularSearch() {
        final String[] fields = Fulltext.searchMetadataFields(false,
                Collections.singleton(CollectionSchema.text_t.getSolrFieldName()));

        assertFalse(Arrays.asList(fields).contains(CollectionSchema.text_t.getSolrFieldName()));
    }
}
