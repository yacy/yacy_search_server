package net.yacy.kelondro.index;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.HeapWriter;
import net.yacy.kelondro.data.citation.CitationReference;
import net.yacy.kelondro.data.citation.CitationReferenceFactory;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceContainerArray;

public class RowSetMergeTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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
    public void orderedMergeAccumulatorPreservesSegmentOrderAcrossAllInputs() throws SpaceExceededException {
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

    @Test
    public void orderedMergeKeepsAllDuplicatesFromTheEarliestSegment() throws SpaceExceededException {
        final Row row = new Row("byte[] key-4, byte[] payload-4", NaturalOrder.naturalOrder);
        final RowSet first = new RowSet(row);
        first.add(row("0001", "s000"));
        first.add(row("0002", "s001"));
        first.add(row("0002", "s002"));

        final RowSet second = new RowSet(row);
        second.add(row("0002", "s100"));
        second.add(row("0003", "s101"));

        final RowSet third = new RowSet(row);
        third.add(row("0002", "s200"));

        final RowSet.OrderedMergeAccumulator accumulator = new RowSet.OrderedMergeAccumulator(row);
        accumulator.add(first);
        accumulator.add(second);
        accumulator.add(third);
        final RowSet merged = accumulator.finish();

        assertEquals(4, merged.size());
        assertEquals(merged.size() * row.objectsize, merged.chunkcache.length);
        assertArrayEquals("s001".getBytes(StandardCharsets.US_ASCII),
                merged.get(1, true).getColBytes(1, false));
        assertArrayEquals("s002".getBytes(StandardCharsets.US_ASCII),
                merged.get(2, true).getColBytes(1, false));
        assertArrayEquals("s101".getBytes(StandardCharsets.US_ASCII),
                merged.get(3, true).getColBytes(1, false));
    }

    @Test
    public void orderedMergeCanCombineDiskSegmentsAndRamOverlayInOnePass() throws SpaceExceededException {
        final Row row = new Row("byte[] key-4, byte[] payload-4", NaturalOrder.naturalOrder);
        final RowSet firstDiskSegment = new RowSet(row);
        firstDiskSegment.add(row("0001", "d000"));
        firstDiskSegment.add(row("0003", "d001"));

        final RowSet secondDiskSegment = new RowSet(row);
        secondDiskSegment.add(row("0002", "d100"));
        secondDiskSegment.add(row("0003", "d101"));
        secondDiskSegment.add(row("0004", "d102"));

        final RowSet ramOverlay = new RowSet(row);
        ramOverlay.add(row("0003", "ram3"));
        ramOverlay.add(row("0004", "ram4"));
        ramOverlay.add(row("0005", "ram5"));

        final RowSet expected = firstDiskSegment.merge(secondDiskSegment).merge(ramOverlay);
        final RowSet.OrderedMergeAccumulator accumulator = new RowSet.OrderedMergeAccumulator(row);
        accumulator.add(firstDiskSegment);
        accumulator.add(secondDiskSegment);
        accumulator.add(ramOverlay);
        final RowSet merged = accumulator.finish();

        assertEquals(expected.size(), merged.size());
        assertEquals(merged.size() * row.objectsize, merged.chunkcache.length);
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i, true).bytes(), merged.get(i, true).bytes());
        }
    }

    @Test
    public void referenceArrayMergesRamOverlayWithDiskSegmentsInOnePass() throws Exception {
        final CitationReferenceFactory factory = new CitationReferenceFactory();
        final byte[] termHash = ASCII.getBytes("TERM00000001");
        final ReferenceContainer<CitationReference> firstDisk = references(factory, termHash,
                "000000000001", "000000000003");
        final ReferenceContainer<CitationReference> secondDisk = references(factory, termHash,
                "000000000002", "000000000003", "000000000004");
        final ReferenceContainer<CitationReference> ramOverlay = references(factory, termHash,
                "000000000003", "000000000004", "000000000005");
        final File directory = this.temporaryFolder.newFolder("reference-container-array");
        final ReferenceContainerArray<CitationReference> array = new ReferenceContainerArray<>(
                directory, "rwi", factory, Word.commonHashOrder, termHash.length);
        try {
            writeAndMount(array, termHash, firstDisk);
            Thread.sleep(5L); // ensure distinct time based BLOB names
            writeAndMount(array, termHash, secondDisk);

            final ReferenceContainer<CitationReference> previousDiskResult = array.get(termHash);
            final ReferenceContainer<CitationReference> expected = previousDiskResult.merge(ramOverlay);
            final ReferenceContainer<CitationReference> actual = array.get(termHash, ramOverlay);

            assertEquals(expected.size(), actual.size());
            assertEquals(actual.size() * actual.row().objectsize, actual.chunkcache.length);
            for (int i = 0; i < expected.size(); i++) {
                assertArrayEquals(expected.get(i, true).bytes(), actual.get(i, true).bytes());
            }
        } finally {
            array.close();
        }
    }

    private static ReferenceContainer<CitationReference> references(
            final CitationReferenceFactory factory, final byte[] termHash, final String... urlHashes)
            throws SpaceExceededException {
        final ReferenceContainer<CitationReference> container = new ReferenceContainer<>(factory, termHash);
        long modified = 1_700_000_000_000L;
        for (final String urlHash : urlHashes) {
            container.add(new CitationReference(ASCII.getBytes(urlHash), modified++));
        }
        return container;
    }

    private static void writeAndMount(final ReferenceContainerArray<CitationReference> array,
            final byte[] termHash, final ReferenceContainer<CitationReference> container) throws Exception {
        final File blob = array.newContainerBLOBFile();
        final File temporary = new File(blob.getParentFile(), blob.getName() + ".prt");
        final HeapWriter writer = new HeapWriter(temporary, blob, termHash.length,
                Word.commonHashOrder, 4096);
        writer.add(termHash, container.exportCollection());
        writer.close(true);
        array.mountBLOBFile(blob);
    }

    private static byte[] row(final String key, final String payload) {
        final byte[] bytes = new byte[8];
        System.arraycopy(key.getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy(payload.getBytes(StandardCharsets.US_ASCII), 0, bytes, 4, 4);
        return bytes;
    }
}
