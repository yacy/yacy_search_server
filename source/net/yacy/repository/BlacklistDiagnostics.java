package net.yacy.repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.repository.Blacklist.BlacklistType;

/** On-demand source attribution for administrator diagnostics, not search/crawl matching. */
public final class BlacklistDiagnostics {
    private BlacklistDiagnostics() { }

    public static final class Match {
        public final String rule;
        public final String filename;
        public final String entry;
        public final String revision;
        public final Set<BlacklistType> types;

        public Match(final String rule, final String filename, final String entry,
                final String revision, final Set<BlacklistType> types) {
            this.rule = rule;
            this.filename = filename;
            this.entry = entry;
            this.revision = revision;
            this.types = Collections.unmodifiableSet(EnumSet.copyOf(types));
        }

        public boolean editable() {
            // The native editor/remover requires an explicit host/path entry.
            return !this.filename.isEmpty() && this.entry.indexOf('/') > 0;
        }
    }

    public static final class Report {
        public final List<Match> matches = new ArrayList<>();
        public final Set<String> unavailableFiles = new TreeSet<>();
    }

    public static Path listFile(final File root, final String filename) throws IOException {
        if (filename == null || filename.isEmpty() || !filename.endsWith(".black")
                || filename.contains("/") || filename.contains("\\")) {
            throw new IOException("Invalid blacklist filename");
        }
        final Path file = root.toPath().resolve(filename);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Blacklist is missing or is not a regular file");
        }
        return file;
    }

    public static Match requireMatch(final Report report, final String filename,
            final String entry, final String revision) {
        for (final Match match : report.matches) {
            if (match.editable() && match.filename.equals(filename) && match.entry.equals(entry)
                    && match.revision.equals(revision)) return match;
        }
        throw new IllegalArgumentException("The blacklist entry changed or no longer matches. Test the URL again.");
    }

    private static String revision(final byte[] contents) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(contents));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Delete only this spelling in this file; leave other rules and line endings intact. */
    public static synchronized void deleteEntry(final File root, final Match match) throws IOException {
        final Path file = listFile(root, match.filename);
        final byte[] original = Files.readAllBytes(file);
        if (!match.editable() || !revision(original).equals(match.revision)) {
            throw new IOException("The blacklist changed. Test the URL again before deleting.");
        }
        final Charset charset = Charset.defaultCharset();
        final String text = new String(original, charset);
        if (!Arrays.equals(original, text.getBytes(charset))) {
            throw new IOException("Blacklist encoding cannot be safely preserved. Use the main editor.");
        }
        final StringBuilder remaining = new StringBuilder(text.length());
        boolean found = false;
        int start = 0;
        while (start < text.length()) {
            int end = start;
            while (end < text.length() && text.charAt(end) != '\r' && text.charAt(end) != '\n') end++;
            int next = end;
            if (next < text.length() && text.charAt(next) == '\r') next++;
            if (next < text.length() && text.charAt(next) == '\n') next++;
            if (text.substring(start, end).equals(match.entry)) found = true;
            else remaining.append(text, start, next);
            start = next;
        }
        if (!found) throw new IOException("The selected entry is no longer present.");
        final Path temporary = Files.createTempFile(file.getParent(), ".blacklist-delete-", ".tmp");
        try {
            Files.write(temporary, remaining.toString().getBytes(charset));
            if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(file));
            }
            if (!revision(Files.readAllBytes(listFile(root, match.filename))).equals(match.revision)) {
                throw new IOException("The blacklist changed during deletion. Test the URL again.");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Match SetTools.loadMapMultiValsPerKey and Blacklist.loadList normalization. */
    static String loadedRule(final String source) {
        String line = source.trim();
        if (line.isEmpty() || line.startsWith("#")) return null;
        int slash = line.indexOf('/');
        if (slash <= 0) {
            line += "/.*";
            slash = line.length() - 3;
        }
        // Use the loader's locale behavior, including for host regex text.
        final String host = line.substring(0, slash).trim().toLowerCase();
        final String path = line.substring(slash + 1).trim();
        if (path.indexOf("?*") > 0) return null;
        return host + "/" + ("*".equals(path) ? ".*" : MultiProtocolURL.escapePathPattern(path));
    }

    public static Report findSources(final File root,
            final Map<BlacklistType, Set<String>> activeFiles,
            final Map<String, Set<BlacklistType>> rules) {
        final Report report = new Report();
        if (rules.isEmpty()) return report;
        final Map<String, Set<BlacklistType>> fileTypes = new TreeMap<>();
        for (final Map.Entry<BlacklistType, Set<String>> type : activeFiles.entrySet()) {
            for (final String file : type.getValue()) {
                fileTypes.computeIfAbsent(file, key -> EnumSet.noneOf(BlacklistType.class)).add(type.getKey());
            }
        }
        final Map<String, Set<BlacklistType>> covered = new TreeMap<>();
        for (final Map.Entry<String, Set<BlacklistType>> file : fileTypes.entrySet()) {
            final Map<String, String> entries = new TreeMap<>();
            final MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (final NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new DigestInputStream(
                    Files.newInputStream(listFile(root, file.getKey())), digest), Charset.defaultCharset()))) {
                String entry;
                while ((entry = reader.readLine()) != null) {
                    final String rule = loadedRule(entry);
                    if (rule != null && rules.containsKey(rule)) entries.put(entry, rule);
                }
            } catch (final IOException e) {
                report.unavailableFiles.add(file.getKey());
                continue;
            }
            final String revision = Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
            for (final Map.Entry<String, String> entry : entries.entrySet()) {
                final Set<BlacklistType> types = EnumSet.copyOf(rules.get(entry.getValue()));
                types.retainAll(file.getValue());
                if (types.isEmpty()) continue;
                report.matches.add(new Match(entry.getValue(), file.getKey(), entry.getKey(), revision, types));
                covered.computeIfAbsent(entry.getValue(), key -> EnumSet.noneOf(BlacklistType.class)).addAll(types);
            }
        }
        for (final Map.Entry<String, Set<BlacklistType>> rule : rules.entrySet()) {
            final Set<BlacklistType> missing = EnumSet.copyOf(rule.getValue());
            missing.removeAll(covered.getOrDefault(rule.getKey(), Collections.emptySet()));
            if (!missing.isEmpty()) report.matches.add(new Match(rule.getKey(), "", "", "", missing));
        }
        return report;
    }
}
