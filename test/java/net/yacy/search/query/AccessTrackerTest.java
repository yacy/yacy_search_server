package net.yacy.search.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.htroot.AccessTracker_p;
import net.yacy.search.EventTracker.Event;
import net.yacy.server.serverObjects;

/** Query log fixtures contain synthetic searches only. */
public class AccessTrackerTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withZone(ZoneOffset.UTC);

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private String record(final int second, final String count, final String query) {
        return FORMAT.format(START.plusSeconds(second)) + " " + count + " qs " + query;
    }

    private File log(final String contents) throws Exception {
        final File file = this.temporary.newFile();
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private List<Event> read(final File file, final long fromMillis, final long toMillis) {
        return AccessTracker.readLog(file, Date.from(START.plusMillis(fromMillis)),
                Date.from(START.plusMillis(toMillis)));
    }

    private List<String> payloads(final List<Event> events) {
        final List<String> payloads = new ArrayList<>();
        for (final Event event : events) {
            payloads.add(event.payload.toString());
        }
        return payloads;
    }

    @Test
    public void includesLastRecordAndSingleRecord() throws Exception {
        final File single = log(record(0, "1", "one") + "\n");
        assertEquals(Arrays.asList("qs one"), payloads(read(single, -1000, 1000)));
        final File multiple = log(record(0, "1", "one") + "\n" + record(60, "2", "two") + "\n");
        assertEquals(Arrays.asList("qs one", "qs two"), payloads(read(multiple, -1000, 61000)));
    }

    @Test
    public void honorsInclusiveFromAndExclusiveToBetweenRecords() throws Exception {
        final StringBuilder contents = new StringBuilder();
        for (int i = 0; i < 64; i++) contents.append(record(i * 60, "1", "entry-" + i)).append('\n');
        final File file = log(contents.toString());
        final List<Event> events = read(file, 630000, 1230000);
        assertEquals(10, events.size());
        assertEquals("qs entry-11", events.get(0).payload);
        assertEquals("qs entry-20", events.get(9).payload);
        assertEquals(Arrays.asList("qs entry-10"), payloads(read(file, 600000, 660000)));
        assertTrue(read(file, -2000, -1000).isEmpty());
        assertTrue(read(file, 4000000, 5000000).isEmpty());
        assertTrue(read(file, 1000, 1000).isEmpty());
        assertTrue(read(file, 2000, 1000).isEmpty());
    }

    @Test
    public void keepsAllRecordsSharingBoundaryTimestamp() throws Exception {
        final File file = log(record(0, "1", "before") + "\n"
                + record(1, "1", "first") + "\n" + record(1, "2", "second") + "\n"
                + record(1, "3", "third") + "\n" + record(2, "1", "after") + "\n");
        assertEquals(Arrays.asList("qs first", "qs second", "qs third"), payloads(read(file, 1000, 2000)));
    }

    @Test(timeout = 10000)
    public void toleratesLegacyMultilineRecordAtEverySeekPosition() throws Exception {
        for (int position = 0; position <= 64; position++) {
            final List<String> rows = new ArrayList<>();
            final List<String> expected = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                rows.add(record(i, "1", "synthetic-" + i));
                expected.add("qs synthetic-" + i);
            }
            // Space at offset 14 fooled the old header guard; "xx" is not a count.
            rows.add(position, "abcdefghijklmn xx sq synthetic-continuation\n\nshort");
            final File file = log(String.join("\n", rows) + "\n");
            final byte[] before = Files.readAllBytes(file.toPath());
            assertEquals("insertion " + position, expected, payloads(read(file, -1000, 65000)));
            org.junit.Assert.assertArrayEquals(before, Files.readAllBytes(file.toPath()));
        }
    }

    @Test
    public void rejectsHeaderLikeContinuationWithoutThrowing() throws Exception {
        final List<String> rows = new ArrayList<>();
        for (int i = 0; i < 64; i++) rows.add(record(i * 60, "1", "synthetic-record-" + i));
        rows.add(2, "abcdefghijklmn xx sq synthetic-continuation");
        assertEquals(64, read(log(String.join("\n", rows) + "\n"), -1000, 86400000).size());
    }

    @Test
    public void skipsInvalidCountsAndDates() throws Exception {
        final StringBuilder contents = new StringBuilder(record(0, "0", "zero") + "\n");
        for (final String count : Arrays.asList("", "x", "xx", "-1", "+1", "100000", "2147483648")) {
            contents.append(record(1, count, "invalid-count")).append('\n');
        }
        for (final String date : Arrays.asList("abcdefghijklmn", "20260230000000", "20261301000000",
                "20260101240000", "20260101006000", "20260101000060")) {
            contents.append(date).append(" 1 qs invalid-date\n");
        }
        contents.append(record(2, "99999", "maximum-existing-count")).append('\n');
        final List<Event> events = read(log(contents.toString()), -1000, 3000);
        assertEquals(Arrays.asList("qs zero", "qs maximum-existing-count"), payloads(events));
        assertEquals(0, events.get(0).count);
        assertEquals(99999, events.get(1).count);
    }

    @Test
    public void supportsEmptyMalformedAndTruncatedFiles() throws Exception {
        assertTrue(read(log(""), -1000, 1000).isEmpty());
        assertTrue(read(log("\nshort\r\nabcdefghijklmn xx sq broken\n2026010"), -1000, 1000).isEmpty());
        assertEquals(Arrays.asList("qs complete"), payloads(read(log(record(0, "1", "complete")
                + "\n202601010000"), -1000, 1000)));
        assertEquals(Arrays.asList("qs no-newline"), payloads(read(log(record(0, "1", "no-newline")), -1000, 1000)));
    }

    @Test
    public void preservesUtf8TabsAndSupportsLegacyLineEndings() throws Exception {
        final String text = "caf\u00e9 \u043b \ud83d\udca1\tdata\u2028more";
        final File file = log(record(0, "1", text) + "\r\n" + record(1, "2", "second") + "\r"
                + "abcdefghijklmn xx sq continuation\r" + record(2, "3", "third") + "\n");
        assertEquals(Arrays.asList("qs " + text, "qs second", "qs third"), payloads(read(file, -1000, 3000)));
        assertEquals(Arrays.asList("qs second"), payloads(read(file, 1000, 2000)));
    }

    @Test(timeout = 20000)
    public void rangesAgreeWithSequentialReferenceAcrossMalformedLines() throws Exception {
        final StringBuilder contents = new StringBuilder();
        final Random random = new Random(317L);
        for (int i = 0; i < 200; i++) {
            contents.append("\nabcdefghijklmn xx sq continuation-").append(i).append('\n');
            contents.append(record(i / 3, "1", "entry-" + i)).append('\n');
        }
        contents.append("truncated");
        final File file = log(contents.toString());
        for (int run = 0; run < 100; run++) {
            final long from = random.nextInt(75000) - 5000;
            final long to = from + random.nextInt(20000);
            final List<String> expected = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                final long time = (i / 3) * 1000L;
                if (time >= from && time < to) expected.add("qs entry-" + i);
            }
            assertEquals("range " + from + " to " + to, expected, payloads(read(file, from, to)));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void logsOnePhysicalLineWithoutChangingOriginalQuery() throws Exception {
        final Field bufferField = AccessTracker.class.getDeclaredField("log");
        bufferField.setAccessible(true);
        final List<String> buffer = (List<String>) bufferField.get(null);
        final Field dumpTimeField = AccessTracker.class.getDeclaredField("lastLogDump");
        dumpTimeField.setAccessible(true);
        synchronized (buffer) {
            final List<String> saved = new ArrayList<>(buffer);
            final long savedTime = dumpTimeField.getLong(null);
            try {
                buffer.clear();
                dumpTimeField.setLong(null, System.currentTimeMillis());
                final String query = "alpha\r\nbeta\ngamma\rdelta caf\u00e9";
                AccessTracker.addToDump(query, 2, Date.from(START), "sq");
                assertEquals(1, buffer.size());
                assertEquals("20260101000000 2 sq alpha beta gamma delta caf\u00e9", buffer.get(0));
                assertEquals("alpha\r\nbeta\ngamma\rdelta caf\u00e9", query);
                assertFalse(buffer.get(0).contains("\n"));
                assertFalse(buffer.get(0).contains("\r"));
            } finally {
                buffer.clear();
                buffer.addAll(saved);
                dumpTimeField.setLong(null, savedTime);
            }
        }
    }

    @Test
    public void localSearchPageRetainsStatisticsWithMalformedHistory() throws Exception {
        final String before = FORMAT.format(Instant.now().minusSeconds(60));
        final String after = FORMAT.format(Instant.now().minusSeconds(30));
        final File history = log(before + " 1 qs synthetic-before\n"
                + "abcdefghijklmn xx sq synthetic-continuation\n"
                + after + " 2 qs synthetic-after\n");
        final File saved = AccessTracker.getDumpFile();
        try {
            AccessTracker.setDumpFile(history);
            final serverObjects post = new serverObjects();
            post.put("page", "2");
            final serverObjects response = AccessTracker_p.respond(new RequestHeader(), post, null);
            assertEquals("2", response.get("page"));
            assertEquals("1", response.get("page_nav-topics"));
            assertEquals("2", response.get("page_nav-topics_count"));
        } finally {
            AccessTracker.setDumpFile(saved);
        }
    }
}
