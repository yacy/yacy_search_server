package net.yacy.htroot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.Test;
import org.jsoup.Jsoup;

import net.yacy.server.http.TemplateEngine;
import net.yacy.server.serverObjects;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.BlacklistDiagnostics;

public class BlacklistTestPageTest {
    private BlacklistDiagnostics.Report report(final Map<String, Set<String>> rules) {
        final BlacklistDiagnostics.Report report = new BlacklistDiagnostics.Report();
        for (final Map.Entry<String, Set<String>> rule : rules.entrySet()) {
            final Set<BlacklistType> types = EnumSet.noneOf(BlacklistType.class);
            for (final BlacklistType type : BlacklistType.values()) {
                if (rule.getValue().contains(BlacklistTest_p.purpose(type))) types.add(type);
            }
            report.matches.add(new BlacklistDiagnostics.Match(rule.getKey(), "test.black", rule.getKey(), "revision", types));
        }
        return report;
    }
    private String render(final serverObjects prop) throws Exception {
        final String template = new String(Files.readAllBytes(Paths.get("htroot/BlacklistTest_p.html")),
                StandardCharsets.UTF_8).replaceAll("#%[^%]+%#", "");
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        TemplateEngine.writeTemplate("BlacklistTest_p.html",
                new ByteArrayInputStream(template.getBytes(StandardCharsets.UTF_8)), out, prop);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    @Test
    public void rendersAllRulesAndCombinedPurposesInTheActualTemplate() throws Exception {
        final serverObjects prop = new serverObjects();
        prop.put("testlist", 1);
        final Map<String, Set<String>> rules = new TreeMap<>();
        rules.put("*.example.org/.*", new TreeSet<>(Arrays.asList("Crawling", "Search")));
        rules.put("example\\.org/news/.*", Collections.singleton("DHT"));
        BlacklistTest_p.putMatchingRules(prop, report(rules));
        assertEquals("2", prop.get("testlist_matchdetails_rows"));
        final String html = render(prop);
        assertTrue(html.contains("Matching rule entries (2)"));
        assertTrue(html.contains("test.black"));
        assertTrue(html.contains("<code>*.example.org/.*</code>"));
        assertEquals("example\\.org/news/.*", Jsoup.parse(html).select("tbody code").get(1).text());
        assertTrue(html.contains("Crawling, Search"));
        assertFalse(html.contains("No matching active rules."));
    }

    @Test
    public void escapesRuleTextInsteadOfRenderingMarkup() throws Exception {
        final serverObjects prop = new serverObjects();
        prop.put("testlist", 1);
        BlacklistTest_p.putMatchingRules(prop, report(Collections.singletonMap("example.org/<script>&\".*",
                Collections.singleton("Search"))));
        final String html = render(prop);
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&amp;"));
        assertTrue(html.contains("&quot;"));
    }

    @Test
    public void preservesEncodedPathsPlusSignsAndBackslashes() throws Exception {
        final serverObjects prop = new serverObjects();
        prop.put("testlist", 1);
        final String rule = "example\\.org/caf%C3%A9\\?q=a+b&x=1";
        BlacklistTest_p.putMatchingRules(prop, report(Collections.singletonMap(rule, Collections.singleton("Search"))));
        assertEquals(rule, Jsoup.parse(render(prop)).select("tbody code").first().text());
    }

    @Test
    public void distinguishesCacheOnlyBlockFromActiveMatch() throws Exception {
        final serverObjects prop = new serverObjects();
        prop.put("testlist", 1);
        prop.put("testlist_cachedonly", 1);
        prop.putHTML("testlist_cachedonly_types", "Crawling, Search");
        BlacklistTest_p.putMatchingRules(prop, report(Collections.emptyMap()));
        final String html = render(prop);
        assertTrue(html.contains("No matching active rules."));
        assertTrue(html.contains("The block for Crawling, Search is cached; no current rule matched."));
        assertFalse(html.contains("Matching rule entries ("));
    }

    @Test
    public void initialPageAndInvalidUrlDoNotShowMatchResults() throws Exception {
        final serverObjects initial = BlacklistTest_p.respond(null, null, null);
        assertEquals("http://", initial.get("url"));
        assertFalse(render(initial).contains("Matching rule entries ("));
        final serverObjects post = new serverObjects();
        post.put("testList", "Test");
        post.put("testurl", "http://[");
        final serverObjects invalid = BlacklistTest_p.respond(null, post, null);
        assertEquals("2", invalid.get("testlist"));
        assertTrue(render(invalid).contains("The tested URL was not valid."));
        assertFalse(render(invalid).contains("Matching rule entries ("));
    }

    @Test public void actionsKeepSourceTextAndFilesSeparateAndFormsUnnested() throws Exception {
        final BlacklistDiagnostics.Report report = new BlacklistDiagnostics.Report();
        final String entry = "EXAMPLE.org/caf%C3%A9\\?x=a+b&y=\"yes\"";
        final String filename = "list&name.black";
        report.matches.add(new BlacklistDiagnostics.Match("example.org/.*", filename, entry,
                "revision", EnumSet.of(BlacklistType.SEARCH)));
        final serverObjects prop = new serverObjects();
        prop.put("testlist", 1);
        BlacklistTest_p.putMatchingRules(prop, report, "https://example.org/?x=a+b", "test-token");
        final org.jsoup.nodes.Document page = Jsoup.parse(render(prop));
        assertEquals(filename, page.select("tbody td").get(1).text());
        final org.jsoup.nodes.Element edit = page.select("form.blacklistEdit").first();
        assertEquals("_blank", edit.attr("target"));
        assertEquals("Blacklist_p.html", edit.attr("action"));
        assertEquals(entry, edit.select("input[name=selectedEntry.0]").val());
        assertEquals(filename, edit.select("input[name=currentBlacklist]").val());
        assertEquals("post", edit.attr("method"));
        assertEquals(3, page.select("form").size());
        assertEquals(0, page.select("form form").size());
        assertEquals("prepareDelete", page.select("button[name=ruleAction]").val());
        assertEquals("test-token", page.select("input[name=transactionToken]").val());
    }

    @Test public void confirmationRequiresConsentAndCancelDoesNotSubmit() throws Exception {
        final serverObjects prop = new serverObjects();
        BlacklistTest_p.putConfirmation(prop, new BlacklistDiagnostics.Match("example.org/.*", "a.black",
                "example.org/.*", "revision", EnumSet.of(BlacklistType.SEARCH)),
                "http://example.org/", "token", "BlacklistTest_p.html?testList=Test&testurl=http%3A%2F%2Fexample.org");
        final org.jsoup.nodes.Document page = Jsoup.parse(render(prop));
        assertTrue(page.select("input[name=consent]").hasAttr("required"));
        assertFalse(page.select("input[name=consent]").hasAttr("checked"));
        assertEquals("checkbox", page.select("input[name=consent]").attr("type"));
        assertEquals("confirmDelete", page.select("button[name=ruleAction]").val());
        assertTrue(page.select("#blacklistConfirmation a").attr("href").contains("testurl=http%3A"));
        assertThrows(IllegalArgumentException.class, () -> BlacklistTest_p.requireConfirmation(""));
        BlacklistTest_p.requireConfirmation("delete");
    }

    @Test public void actionGuardsRejectGetAndMissingOrWrongTokens() {
        assertThrows(IllegalArgumentException.class, () -> BlacklistTest_p.requirePost("GET", "token", "token"));
        assertThrows(IllegalArgumentException.class, () -> BlacklistTest_p.requirePost("POST", "", "token"));
        assertThrows(IllegalArgumentException.class, () -> BlacklistTest_p.requirePost("POST", "wrong", "token"));
        assertThrows(IllegalArgumentException.class, () -> BlacklistTest_p.requirePost("POST", "", ""));
        BlacklistTest_p.requirePost("POST", "token", "token");
    }
}
