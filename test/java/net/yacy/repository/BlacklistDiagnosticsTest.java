package net.yacy.repository;

import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.BlacklistDiagnostics.Match;
import net.yacy.repository.BlacklistDiagnostics.Report;
import net.yacy.kelondro.util.SetTools;
import net.yacy.cora.document.id.MultiProtocolURL;

public class BlacklistDiagnosticsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private Path file(final String name, final String... lines) throws Exception {
        final Path file = this.temporary.getRoot().toPath().resolve(name);
        Files.write(file, Arrays.asList(lines), Charset.defaultCharset());
        return file;
    }

    private Report find(final Map<BlacklistType, Set<String>> active, final String rule) {
        return BlacklistDiagnostics.findSources(this.temporary.getRoot(), active,
                Collections.singletonMap(rule, EnumSet.of(BlacklistType.CRAWLER, BlacklistType.SEARCH)));
    }

    @Test public void keepsCopiesAndPurposesSeparateAcrossFiles() throws Exception {
        file("a.black", "example.org/.*", "example.org/.*");
        file("b.black", "example.org/.*");
        file("inactive.black", "example.org/.*");
        final Map<BlacklistType, Set<String>> active = new EnumMap<>(BlacklistType.class);
        active.put(BlacklistType.CRAWLER, new HashSet<>(Arrays.asList("a.black", "b.black")));
        active.put(BlacklistType.SEARCH, Collections.singleton("b.black"));
        final Report report = find(active, "example.org/.*");
        assertEquals(2, report.matches.size());
        assertEquals("a.black", report.matches.get(0).filename);
        assertEquals(EnumSet.of(BlacklistType.CRAWLER), report.matches.get(0).types);
        assertEquals("b.black", report.matches.get(1).filename);
        assertEquals(EnumSet.of(BlacklistType.CRAWLER, BlacklistType.SEARCH), report.matches.get(1).types);
    }

    @Test public void retainsExactSourceDespiteNormalization() throws Exception {
        final String original = "EXAMPLE.org/caf\u00e9\\?q=a+b";
        file("a.black", original);
        final Report report = find(Collections.singletonMap(BlacklistType.CRAWLER, Collections.singleton("a.black")),
                "example.org/caf%C3%A9\\?q=a+b");
        assertEquals(original, report.matches.get(0).entry);
        assertTrue(report.matches.get(0).editable());
        assertEquals("", report.matches.get(1).filename); // Search has no attributed active source.
    }

    @Test public void normalizesLikeTheExistingFileLoader() throws Exception {
        final String[] lines = {"EXAMPLE.org/*", "example.org", " example.org / News/.* ",
                "example.org/caf\u00e9", "example.org/\\x25", "/missing-host", "# comment", ""};
        final Path source = file("a.black", lines);
        final Set<String> expected = new HashSet<>();
        SetTools.loadMapMultiValsPerKey(source.toString(), "/").forEach((host, paths) -> paths.forEach(path ->
                expected.add(host + "/" + ("*".equals(path) ? ".*" : MultiProtocolURL.escapePathPattern(path)))));
        final Set<String> actual = new HashSet<>();
        for (final String line : lines) {
            final String normalized = BlacklistDiagnostics.loadedRule(line);
            if (normalized != null) actual.add(normalized);
        }
        assertEquals(expected, actual);
        assertNull(BlacklistDiagnostics.loadedRule("example.org/a?*"));
    }

    @Test public void distinguishesSourceSpellingsOfTheSameLoadedPattern() throws Exception {
        file("a.black", "example.org/*", "example.org/.*", "example.org");
        final Report report = find(Collections.singletonMap(BlacklistType.CRAWLER, Collections.singleton("a.black")), "example.org/.*");
        assertEquals(4, report.matches.size()); // Three source entries plus unattributed Search.
        assertFalse(report.matches.get(0).editable()); // Native editor cannot round-trip a bare host.
        assertTrue(report.matches.get(1).editable());
    }

    @Test public void missingAndUnsafeFilesAreNotAttributedOrCreated() throws Exception {
        final Report report = find(Collections.singletonMap(BlacklistType.CRAWLER,
                new HashSet<>(Arrays.asList("missing.black", "../outside.black"))), "example.org/.*");
        assertEquals(2, report.unavailableFiles.size());
        assertEquals(1, report.matches.size());
        assertEquals("", report.matches.get(0).filename);
        assertFalse(Files.exists(this.temporary.getRoot().toPath().resolve("missing.black")));
    }

    @Test public void missingRuleNeverGetsGuessedFromActiveFileName() throws Exception {
        file("a.black", "other.org/.*", "# example.org/.*");
        final Report report = find(Collections.singletonMap(BlacklistType.CRAWLER, Collections.singleton("a.black")), "example.org/.*");
        assertEquals(1, report.matches.size());
        assertEquals("", report.matches.get(0).filename);
        assertFalse(report.matches.get(0).editable());
    }

    @Test public void fileRevisionChangesWhenContentsChange() throws Exception {
        file("a.black", "example.org/.*");
        final Map<BlacklistType, Set<String>> active = Collections.singletonMap(BlacklistType.CRAWLER, Collections.singleton("a.black"));
        final Match first = find(active, "example.org/.*").matches.get(0);
        file("a.black", "example.org/.*", "other.org/.*");
        assertNotEquals(first.revision, find(active, "example.org/.*").matches.get(0).revision);
    }

    @Test public void deletesOnlyExactEntryInSelectedFilePreservingOtherBytes() throws Exception {
        final String original = "# keep\r\nexample.org/*\r\nexample.org/.*\nexample.org/*\r\nother.org/.*";
        final Path a = file("a.black");
        Files.write(a, original.getBytes(Charset.defaultCharset()));
        final Path b = file("b.black", "example.org/*");
        final byte[] bBefore = Files.readAllBytes(b);
        final Report report = find(Collections.singletonMap(BlacklistType.CRAWLER,
                new HashSet<>(Arrays.asList("a.black", "b.black"))), "example.org/.*");
        final Match match = report.matches.stream().filter(m -> m.filename.equals("a.black")
                && m.entry.equals("example.org/*")).findFirst().get();
        BlacklistDiagnostics.deleteEntry(this.temporary.getRoot(), match);
        assertEquals("# keep\r\nexample.org/.*\nother.org/.*",
                new String(Files.readAllBytes(a), Charset.defaultCharset()));
        assertArrayEquals(bBefore, Files.readAllBytes(b));
        assertThrows(java.io.IOException.class, () -> BlacklistDiagnostics.deleteEntry(this.temporary.getRoot(), match));
    }

    @Test public void rejectsStaleSelectionsWithoutOverwritingNewData() throws Exception {
        final Path a = file("a.black", "example.org/.*");
        final Map<BlacklistType, Set<String>> active = Collections.singletonMap(BlacklistType.CRAWLER, Collections.singleton("a.black"));
        final Match before = find(active, "example.org/.*").matches.get(0);
        file("a.black", "example.org/.*", "new.org/.*");
        final byte[] changed = Files.readAllBytes(a);
        final Report report = find(active, "example.org/.*");
        assertThrows(IllegalArgumentException.class, () -> BlacklistDiagnostics.requireMatch(
                report, before.filename, before.entry, before.revision));
        assertThrows(java.io.IOException.class, () -> BlacklistDiagnostics.deleteEntry(this.temporary.getRoot(), before));
        assertArrayEquals(changed, Files.readAllBytes(a));
        assertThrows(IllegalArgumentException.class, () -> BlacklistDiagnostics.requireMatch(
                report, "../a.black", before.entry, before.revision));
    }

    @Test public void refusesSymlinkSources() throws Exception {
        final Path source = file("source.black", "example.org/.*");
        Files.createSymbolicLink(this.temporary.getRoot().toPath().resolve("link.black"), source);
        final Report report = find(Collections.singletonMap(BlacklistType.CRAWLER,
                Collections.singleton("link.black")), "example.org/.*");
        assertTrue(report.unavailableFiles.contains("link.black"));
        assertFalse(report.matches.get(0).editable());
    }
}
