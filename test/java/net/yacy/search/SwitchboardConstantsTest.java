package net.yacy.search;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SwitchboardConstantsTest {

    @Test
    public void testMinChunkSizeConstantMatchesConfigKey() {
        assertEquals("indexDistribution.minChunkSize", SwitchboardConstants.INDEX_DIST_CHUNK_SIZE_MIN);
    }

    @Test
    public void testMaxChunkSizeConstantMatchesConfigKey() {
        assertEquals("indexDistribution.maxChunkSize", SwitchboardConstants.INDEX_DIST_CHUNK_SIZE_MAX);
    }

    @Test
    public void testStartChunkSizeConstantMatchesConfigKey() {
        assertEquals("indexDistribution.startChunkSize", SwitchboardConstants.INDEX_DIST_CHUNK_SIZE_START);
    }
}
