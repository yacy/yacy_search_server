// GenerateSourceMasterXliff.java
// ---------------------------
// Copyright 2026 by YaCy contributors
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.utils.translation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.SentenceReader;

/**
 * Generate a source-based XLIFF master candidate by extracting conservative UI
 * text candidates directly from htroot source files.
 *
 * This tool does not use existing *.lng files as input. It is intended to build
 * and review a source-of-truth candidate before checking individual languages
 * for completeness.
 */
public class GenerateSourceMasterXliff {

    private static final List<String> SOURCE_EXTENSIONS = Arrays.asList(".html", ".template", ".inc");

    private static final Set<String> TEXT_ATTRIBUTES = new LinkedHashSet<>(
            Arrays.asList("alt", "aria-label", "placeholder", "title", "value"));

    private static final Pattern IGNORED_BLOCK_PATTERN = Pattern.compile(
            "(?is)<(script|style|pre|code|textarea)\\b[^>]*>.*?</\\1>");

    private static final Pattern HTML_COMMENT_PATTERN = Pattern.compile("(?s)<!--.*?-->");

    private static final Pattern TAG_PATTERN = Pattern.compile("(?s)<[^>]+>");

    private static final Pattern BLOCK_TAG_PATTERN = Pattern.compile(
            "(?is)</?(?:html|head|body|title|div|p|table|thead|tbody|tfoot|tr|td|th|ul|ol|li|form|fieldset|legend|dl|dt|dd|select|option|button|input|h[1-6]|section|article|nav|header|footer|hr)\\b[^>]*>");

    private static final Pattern LINK_TAG_PATTERN = Pattern.compile("(?is)</?a\\b[^>]*>");

    private static final Pattern UNPRESERVED_TAG_PATTERN = Pattern.compile(
            "(?is)<(?!/?(?:abbr|b|strong|em|i|br)\\b)[^>]+>");

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(?i)\\b([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");

    private static final Pattern TEMPLATE_BOUNDARY_PATTERN = Pattern.compile(
            "#\\([^)]*\\)#|#\\(/[^)]*\\)#|#\\{[^}]+\\}#|#\\{/[^}]+\\}#|#%[^%]+%#|::");

    private static final Pattern HTML_SPACE_ENTITY_ONLY_PATTERN = Pattern.compile(
            "(?i)(?:&nbsp;|&#160;|&ensp;|&emsp;|\\s)+");

    private static final Pattern HTML_SPACE_ENTITY_EDGE_PATTERN = Pattern.compile(
            "(?i)^(?:&nbsp;|&#160;|&ensp;|&emsp;|\\s)+|(?:&nbsp;|&#160;|&ensp;|&emsp;|\\s)+$");

    private static final Pattern FORMAT_WRAPPER_PATTERN = Pattern.compile(
            "(?is)^<(strong|b|em|i)>(.*)</\\1>(?:<br\\s*/?>)?$");

    private static final Pattern HTML_LINE_BREAK_EDGE_PATTERN = Pattern.compile(
            "(?is)^(?:<br\\s*/?>\\s*)+|(?:\\s*<br\\s*/?>)+$");

    private static final Pattern HTML_TAG_ONLY_PATTERN = Pattern.compile("(?is)</?[a-z][^>]*>");

    /**
     * @param args runtime optional arguments<br/>
     *            <ul>
     *            <li>args[0] : source folder path (htroot/ as default)</li>
     *            <li>args[1] : output xliff master file path
     *            (source-master.lng.xlf as default)</li>
     *            </ul>
     * @throws IOException when a read/write error occurred
     */
    public static void main(final String args[]) throws IOException {
        try {
            final File sourceFolder = args.length > 0 && args[0] != null ? new File(args[0]) : new File("htroot");
            if (!sourceFolder.isDirectory()) {
                System.err.println(sourceFolder.getPath() + " is not a directory");
                return;
            }

            final File masterXlf = args.length > 1 && args[1] != null ? new File(args[1]) : new File("source-master.lng.xlf");

            System.out.println("Extracting source text from folder " + sourceFolder.getAbsolutePath());
            System.out.println((masterXlf.exists() ? "Replacing" : "Generating") + " source master xliff file at "
                    + masterXlf.getAbsolutePath());

            final ExtractionResult extractionResult = extractSourceMaster(sourceFolder.toPath());
            final Map<String, Map<String, String>> sourceMaster = extractionResult.sourceMaster;
            final VerificationResult verificationResult = verifySourceMaster(sourceFolder.toPath(), sourceMaster);
            if (!verificationResult.isOk()) {
                throw new IOException("Source master verification failed: " + verificationResult);
            }
            new TranslatorXliff().saveAsXliff(null, masterXlf, sourceMaster);

            int unitCount = 0;
            for (Map<String, String> entries : sourceMaster.values()) {
                unitCount += entries.size();
            }
            System.out.println("Extracted " + unitCount + " source text candidates from " + sourceMaster.size() + " files.");
            System.out.println("Rejected " + extractionResult.rejectedCandidates
                    + " extracted candidates without a runtime boundary match.");
            System.out.println("Verified " + verificationResult.verifiedKeys
                    + " source text candidates in " + verificationResult.verifiedFiles + " files.");
        } finally {
            ConcurrentLog.shutdown();
        }
    }

    static ExtractionResult extractSourceMaster(final Path sourceRoot) throws IOException {
        final ExtractionResult result = new ExtractionResult();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(GenerateSourceMasterXliff::hasSupportedExtension)
                    .forEach(path -> extractFile(sourceRoot, path, result.sourceMaster, result));
        }
        return result;
    }

    private static boolean hasSupportedExtension(final Path path) {
        final String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : SOURCE_EXTENSIONS) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static void extractFile(final Path sourceRoot, final Path sourceFile,
            final Map<String, Map<String, String>> sourceMaster, final ExtractionResult result) {
        try {
            final String content = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
            final LinkedHashSet<String> candidates = extractCandidates(relativeSourcePath(sourceRoot, sourceFile), content);
            if (!candidates.isEmpty()) {
                final String relativePath = relativeSourcePath(sourceRoot, sourceFile);
                final Map<String, String> entries = new LinkedHashMap<>();
                for (String candidate : candidates) {
                    if (hasRuntimeBoundaryMatch(content, candidate)) {
                        entries.put(candidate, null);
                    } else {
                        result.rejectedCandidates++;
                    }
                }
                if (!entries.isEmpty()) {
                    sourceMaster.put(relativePath, entries);
                }
            }
        } catch (IOException e) {
            ConcurrentLog.warn("TRANSLATOR", "Could not extract source text from " + sourceFile + ": " + e.getMessage());
        }
    }

    private static String relativeSourcePath(final Path sourceRoot, final Path sourceFile) {
        return sourceRoot.relativize(sourceFile).toString().replace(File.separatorChar, '/');
    }

    static VerificationResult verifySourceMaster(final Path sourceRoot,
            final Map<String, Map<String, String>> sourceMaster) throws IOException {
        final VerificationResult result = new VerificationResult();
        for (Map.Entry<String, Map<String, String>> fileEntry : sourceMaster.entrySet()) {
            result.verifiedFiles++;
            final Path sourceFile = sourceRoot.resolve(fileEntry.getKey());
            if (!Files.isRegularFile(sourceFile)) {
                result.missingFiles++;
                continue;
            }

            final String content = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);
            for (String key : fileEntry.getValue().keySet()) {
                if (key == null || key.isEmpty()) {
                    result.emptyKeys++;
                    continue;
                }
                if (content.indexOf(key) < 0) {
                    result.missingRawMatches++;
                    continue;
                }
                if (!hasRuntimeBoundaryMatch(content, key)) {
                    result.missingRuntimeBoundaryMatches++;
                    continue;
                }
                result.verifiedKeys++;
            }
        }
        return result;
    }

    private static boolean hasRuntimeBoundaryMatch(final String content, final String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }

        int index = content.indexOf(key);
        while (index >= 0) {
            if (hasRuntimeBoundaries(content, key, index)) {
                return true;
            }
            index = content.indexOf(key, index + key.length());
        }
        return false;
    }

    private static boolean hasRuntimeBoundaries(final String content, final String key, final int index) {
        boolean boundary = index + key.length() >= content.length();

        if (!boundary) {
            final char c = content.charAt(index + key.length() - 1);
            final char lc = content.charAt(index + key.length());
            boundary |= SentenceReader.punctuation(c) || SentenceReader.invisible(c);
            boundary |= SentenceReader.punctuation(lc) || SentenceReader.invisible(lc);
        }

        if (boundary && index > 0) {
            final char c = content.charAt(index - 1);
            boundary = SentenceReader.punctuation(c) || SentenceReader.invisible(c);
            final char fc = content.charAt(index);
            boundary |= SentenceReader.punctuation(fc) || SentenceReader.invisible(fc);
        }
        return boundary;
    }

    static LinkedHashSet<String> extractCandidates(final String content) {
        return extractCandidates(null, content);
    }

    static LinkedHashSet<String> extractCandidates(final String sourcePath, final String content) {
        final LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String withoutIgnoredBlocks = HTML_COMMENT_PATTERN.matcher(
                IGNORED_BLOCK_PATTERN.matcher(content).replaceAll("")).replaceAll("");
        if ("jslicense.html".equals(sourcePath)) {
            withoutIgnoredBlocks = withoutIgnoredBlocks.replaceAll("(?is)<td\\b[^>]*>.*?</td>", "");
        }

        Matcher tagMatcher = TAG_PATTERN.matcher(withoutIgnoredBlocks);
        while (tagMatcher.find()) {
            addAttributeCandidates(tagMatcher.group(), candidates);
        }

        final String textSource = UNPRESERVED_TAG_PATTERN.matcher(
                LINK_TAG_PATTERN.matcher(BLOCK_TAG_PATTERN.matcher(withoutIgnoredBlocks).replaceAll("\n")).replaceAll(""))
                .replaceAll("\n");
        addTextCandidates(textSource, candidates);
        return candidates;
    }

    private static void addAttributeCandidates(final String tag, final Set<String> candidates) {
        final String lowerTag = tag.toLowerCase(Locale.ROOT);
        if (lowerTag.contains("type=\"hidden\"") || lowerTag.contains("type='hidden'")) {
            return;
        }

        final Matcher attributeMatcher = ATTRIBUTE_PATTERN.matcher(tag);
        while (attributeMatcher.find()) {
            final String attributeName = attributeMatcher.group(1).toLowerCase(Locale.ROOT);
            if (TEXT_ATTRIBUTES.contains(attributeName)) {
                if ("value".equals(attributeName) && !isButtonValueAttribute(lowerTag)) {
                    continue;
                }
                final String value = attributeMatcher.group(3) != null ? attributeMatcher.group(3) : attributeMatcher.group(4);
                final String quote = attributeMatcher.group(3) != null ? "\"" : "'";
                addCandidate(quote + value + quote, candidates);
            }
        }
    }

    private static boolean isButtonValueAttribute(final String lowerTag) {
        return lowerTag.startsWith("<button") || lowerTag.contains("type=\"submit\"") || lowerTag.contains("type='submit'")
                || lowerTag.contains("type=\"button\"") || lowerTag.contains("type='button'")
                || lowerTag.contains("type=\"reset\"") || lowerTag.contains("type='reset'");
    }

    private static void addTextCandidates(final String text, final Set<String> candidates) {
        final String[] templateParts = TEMPLATE_BOUNDARY_PATTERN.split(text);
        for (String templatePart : templateParts) {
            final String[] lines = templatePart.split("\\r?\\n");
            for (String line : lines) {
                addCandidate(line, candidates);
            }
        }
    }

    private static void addCandidate(final String rawCandidate, final Set<String> candidates) {
        if (rawCandidate == null) {
            return;
        }

        final String candidate = normalizeFormattingWrapper(trimHtmlLineBreaks(
                trimHtmlSpaceEntities(trimInvisible(rawCandidate))));
        if (!isUsefulCandidate(candidate)) {
            return;
        }
        candidates.add(candidate);
    }

    private static String trimInvisible(final String text) {
        int start = 0;
        int end = text.length();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static String trimHtmlSpaceEntities(final String text) {
        return HTML_SPACE_ENTITY_EDGE_PATTERN.matcher(text).replaceAll("");
    }

    private static String trimHtmlLineBreaks(final String text) {
        return HTML_LINE_BREAK_EDGE_PATTERN.matcher(text).replaceAll("");
    }

    private static String normalizeFormattingWrapper(final String text) {
        final Matcher matcher = FORMAT_WRAPPER_PATTERN.matcher(text);
        if (matcher.matches()) {
            return trimHtmlSpaceEntities(trimInvisible(matcher.group(2)));
        }
        return text;
    }

    private static boolean isUsefulCandidate(final String candidate) {
        if (candidate.length() < 2) {
            return false;
        }
        if (HTML_SPACE_ENTITY_ONLY_PATTERN.matcher(candidate).matches()) {
            return false;
        }
        if (HTML_TAG_ONLY_PATTERN.matcher(candidate).matches()) {
            return false;
        }
        if (candidate.indexOf("==") >= 0) {
            return false;
        }
        if (candidate.endsWith("=")) {
            return false;
        }
        if (candidate.startsWith("#")) {
            return false;
        }
        if (candidate.startsWith("//") || candidate.startsWith("/*") || candidate.startsWith("*")) {
            return false;
        }
        if (candidate.startsWith(":") || candidate.startsWith(".") || candidate.startsWith("-")) {
            return false;
        }
        if (candidate.indexOf("#[") >= 0 || candidate.indexOf("#(") >= 0 || candidate.indexOf("#{") >= 0
                || candidate.indexOf("#%") >= 0) {
            return false;
        }

        boolean hasLetter = false;
        for (int i = 0; i < candidate.length(); i++) {
            if (Character.isLetter(candidate.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        return hasLetter;
    }

    static final class ExtractionResult {

        private final Map<String, Map<String, String>> sourceMaster = new TreeMap<>();

        private int rejectedCandidates;
    }

    static final class VerificationResult {

        private int verifiedFiles;

        private int verifiedKeys;

        private int missingFiles;

        private int emptyKeys;

        private int missingRawMatches;

        private int missingRuntimeBoundaryMatches;

        private boolean isOk() {
            return this.missingFiles == 0 && this.emptyKeys == 0 && this.missingRawMatches == 0
                    && this.missingRuntimeBoundaryMatches == 0;
        }

        @Override
        public String toString() {
            return "verifiedFiles=" + this.verifiedFiles + ", verifiedKeys=" + this.verifiedKeys
                    + ", missingFiles=" + this.missingFiles + ", emptyKeys=" + this.emptyKeys
                    + ", missingRawMatches=" + this.missingRawMatches
                    + ", missingRuntimeBoundaryMatches=" + this.missingRuntimeBoundaryMatches;
        }
    }
}
