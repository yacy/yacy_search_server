package net.yacy.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;

public class BlacklistMatchingRulesTest {
    private final Map<String, Set<Pattern>> matchable = new HashMap<>();
    private final Map<String, Set<Pattern>> regex = new HashMap<>();

    private void add(final String host, final String... paths) {
        final Map<String, Set<Pattern>> rules = Blacklist.isMatchable(host) ? this.matchable : this.regex;
        final Set<Pattern> patterns = rules.computeIfAbsent(host, key -> new HashSet<>());
        for (final String path : paths) {
            patterns.add(Pattern.compile(path, Pattern.CASE_INSENSITIVE));
        }
    }

    private Set<String> matches(final String host, final String path) {
        final Set<String> matches = Blacklist.getMatchingRules(host, path, this.matchable, this.regex);
        assertEquals("Diagnostics must agree with the uncached engine",
                Blacklist.isListed(host, path, this.matchable, this.regex), !matches.isEmpty());
        return matches;
    }

    @Test
    public void reportsEveryMatchingHostAndPathInsteadOfFirstHit() {
        add("shop.example.org", "products/.*", "products/widget", "other/.*");
        add("*.example.org", "products/.*");
        add("shop\\..*", "products/widget");
        add("(.*)", "products/.*");
        assertEquals(new TreeSet<>(Arrays.asList("shop.example.org/products/.*",
                "shop.example.org/products/widget", "*.example.org/products/.*",
                "shop\\..*/products/widget", "(.*)/products/.*")),
                matches("shop.example.org", "/products/widget"));
    }

    @Test
    public void preservesEnginePrefixSuffixAndWildcardSemantics() {
        add("shop.example.org", ".*");
        add("shop.*", ".*");
        add("shop", ".*");
        add("*.example.org", ".*");
        add("example.org", ".*");
        assertEquals(new TreeSet<>(Arrays.asList("shop.example.org/.*", "shop.*/.*",
                "shop/.*", "*.example.org/.*", "example.org/.*")),
                matches("shop.example.org", "/index.html"));
    }

    @Test
    public void wildcardSubdomainDoesNotMatchRootOrUnrelatedSuffix() {
        add("*.example.org", ".*");
        assertTrue(matches("example.org", "/").isEmpty());
        assertTrue(matches("notexample.org", "/").isEmpty());
        assertFalse(matches("a.b.example.org", "/").isEmpty());
    }

    @Test
    public void respectsPathCaseFlagsFullMatchAndSingleLeadingSlash() {
        add("example.org", "News/.*");
        assertEquals(Collections.singleton("example.org/News/.*"), matches("example.org", "/news/item"));
        assertEquals(Collections.singleton("example.org/News/.*"), matches("example.org", "NEWS/item"));
        assertTrue(matches("example.org", "/archive/news/item").isEmpty());
        assertTrue(matches("example.org", "//news/item").isEmpty());
    }

    @Test
    public void testsEncodedPathAndQueryUsingTheSameUrlComponents() throws Exception {
        add("example.org", "caf%C3%A9\\?q=one");
        final DigestURL url = new DigestURL("https://EXAMPLE.ORG/caf%C3%A9?q=one");
        assertEquals(Collections.singleton("example.org/caf%C3%A9\\?q=one"),
                matches(url.getHost().toLowerCase(Locale.ROOT), url.getFile()));
    }

    @Test
    public void deduplicatesEqualPatternsAndKeepsStableSortOrder() {
        add("example.org", "z.*", ".*", ".*");
        final Set<String> result = matches("example.org", "/zzz");
        assertEquals(Arrays.asList("example.org/.*", "example.org/z.*"), Arrays.asList(result.toArray(new String[0])));
        assertEquals("Diagnostic must not change active pattern sets", 3, this.matchable.get("example.org").size());
    }

    @Test
    public void invalidRegexHostIsIgnoredAsInNormalMatching() {
        add("[", ".*");
        add("example\\.org", "blocked");
        assertEquals(Collections.singleton("example\\.org/blocked"), matches("example.org", "/blocked"));
        assertTrue(matches("example.org", "/allowed").isEmpty());
    }

    @Test
    public void preservesDotStarHostClassificationRatherThanTreatingItAsRegex() {
        add(".*", ".*");
        assertTrue(matches("example.org", "/index.html").isEmpty());
    }

    @Test
    public void supportsEmptyRootPathAndEmptyLists() {
        assertTrue(matches("example.org", "").isEmpty());
        add("example.org", "");
        assertEquals(Collections.singleton("example.org/"), matches("example.org", ""));
        assertEquals(Collections.singleton("example.org/"), matches("example.org", "/"));
    }
}
