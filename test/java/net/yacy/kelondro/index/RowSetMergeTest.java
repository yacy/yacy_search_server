package net.yacy.kelondro.index;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.SpaceExceededException;

public class RowSetMergeTest {

    @Test
    public void mergeAllocatesCapacityForDeduplicatedOutput() throws SpaceExceededException {
        final Row row = new Row("byte[] key-4, byte[] payload-4", NaturalOrder.naturalOrder);
        final RowSet left = new RowSet(row);
        left.add(row("0001", "left"));
        left.add(row("0002", "left"));
        left.add(row("0004", "left"));

        final RowSet right = new RowSet(row);
        right.add(row("0002", "rght"));
        right.add(row("0003", "rght"));

        final int maximumOutputSize = left.size() + right.size();
        final RowSet merged = left.merge(right);

        assertEquals(4, merged.size());
        assertEquals((maximumOutputSize - 1) * row.objectsize, merged.chunkcache.length);
        assertTrue(merged.has("0001".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(merged.has("0002".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(merged.has("0003".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(merged.has("0004".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(merged.has("0005".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void mergeKeepsMaximumCapacityWhenInputsDoNotOverlap() throws SpaceExceededException {
        final Row row = new Row("byte[] key-4, byte[] payload-4", NaturalOrder.naturalOrder);
        final RowSet left = new RowSet(row);
        left.add(row("0001", "left"));
        left.add(row("0002", "left"));

        final RowSet right = new RowSet(row);
        right.add(row("0003", "rght"));
        right.add(row("0004", "rght"));

        final int maximumOutputSize = left.size() + right.size();
        final RowSet merged = left.merge(right);

        assertEquals(maximumOutputSize, merged.size());
        assertEquals(maximumOutputSize * row.objectsize, merged.chunkcache.length);
        assertTrue(merged.has("0001".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(merged.has("0002".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(merged.has("0003".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(merged.has("0004".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void orderedMergeAccumulatorPreservesSegmentOrderAcrossBalancedRuns() throws SpaceExceededException {
        final Row row = new Row("byte[] key-4, byte[] payload-4", NaturalOrder.naturalOrder);
        final RowSet.OrderedMergeAccumulator accumulator = new RowSet.OrderedMergeAccumulator(row);
        final RowSet[] segments = new RowSet[7];
        for (int i = 0; i < segments.length; i++) segments[i] = new RowSet(row);

        segments[0].add(row("0001", "s000"));
        segments[0].add(row("0007", "s000"));
        segments[1].add(row("0002", "s001"));
        segments[1].add(row("0007", "s001"));
        segments[2].add(row("0003", "s002"));
        segments[2].add(row("0007", "s002"));
        segments[3].add(row("0004", "s003"));
        segments[3].add(row("0007", "s003"));
        segments[4].add(row("0005", "s004"));
        segments[4].add(row("0007", "s004"));
        segments[5].add(row("0006", "s005"));
        segments[5].add(row("0007", "s005"));
        segments[6].add(row("0008", "s006"));
        segments[6].add(row("0007", "s006"));

        for (final RowSet segment : segments) accumulator.add(segment);
        final RowSet merged = accumulator.finish();

        assertEquals(8, merged.size());
        assertEquals(8 * row.objectsize, merged.chunkcache.length);
        for (int i = 1; i <= 8; i++) {
            assertTrue(merged.has(String.format("%04d", i).getBytes(StandardCharsets.US_ASCII)));
        }
        assertArrayEquals("s000".getBytes(StandardCharsets.US_ASCII),
                merged.get("0007".getBytes(StandardCharsets.US_ASCII), true).getColBytes(1, false));
    }

    private static byte[] row(final String key, final String payload) {
        final byte[] bytes = new byte[8];
        System.arraycopy(key.getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy(payload.getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 4);
        return bytes;
    }
}
