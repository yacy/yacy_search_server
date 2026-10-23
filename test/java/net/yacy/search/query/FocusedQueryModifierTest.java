package net.yacy.search.query;

import org.junit.Assert;
import org.junit.Test;

/** Tests for the generic focused-policy search modifiers. */
public class FocusedQueryModifierTest {

    @Test
    public void parsesPolicyAndRelevance() {
        final QueryModifier modifier = new QueryModifier(0);
        final String query = modifier.parse("policy:fiction relevance:42 library");

        Assert.assertEquals("library", query);
        Assert.assertEquals("fiction", modifier.policy);
        Assert.assertEquals(Integer.valueOf(42), modifier.policyRelevance);
    }

    @Test
    public void policyOnlyQueryBecomesCatchall() {
        final QueryModifier modifier = new QueryModifier(0);

        Assert.assertEquals("*", modifier.parse("policy:fiction"));
        Assert.assertEquals("fiction", modifier.policy);
    }

    @Test
    public void invalidRelevanceRemainsOrdinaryQueryText() {
        final QueryModifier modifier = new QueryModifier(0);

        Assert.assertEquals("relevance:unknown library", modifier.parse("relevance:unknown library"));
        Assert.assertNull(modifier.policyRelevance);
    }
}
