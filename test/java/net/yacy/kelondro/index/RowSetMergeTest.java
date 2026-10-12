package net.yacy.kelondro.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.SpaceExceededException;

public class RowSetMergeTest {

    @Test
    public void mergeUsesOnlyItsMaximumOutputCapacity() throws SpaceExceededException {
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
        assertEquals(maximumOutputSize * row.objectsize, merged.chunkcache.length);
        assertTrue(merged.has("0001".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(merged.has("0002".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(merged.has("0003".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(merged.has("0004".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(merged.has("0005".getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] row(final String key, final String payload) {
        final byte[] bytes = new byte[8];
        System.arraycopy(key.getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy(payload.getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 4);
        return bytes;
    }
}
