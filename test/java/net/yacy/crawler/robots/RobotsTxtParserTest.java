package net.yacy.crawler.robots;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class RobotsTxtParserTest {

    @Test
    public void testSpecificAgentTakesPriorityOverWildcard() {
        String robotsTxt =
            "User-agent: yacybot\n" +
            "Allow: /\n" +
            "\n" +
            "User-agent: *\n" +
            "Disallow: /\n";

        RobotsTxtParser parser = new RobotsTxtParser(
            new String[]{"yacybot"},
            robotsTxt.getBytes(StandardCharsets.UTF_8));

        assertTrue("denyList should be empty for yacybot", parser.denyList().isEmpty());
        assertTrue("allowList should contain /", parser.allowList().contains("/"));
    }

    @Test
    public void testWildcardAppliesWhenNoSpecificAgentMatch() {
        String robotsTxt =
            "User-agent: googlebot\n" +
            "Allow: /\n" +
            "\n" +
            "User-agent: *\n" +
            "Disallow: /\n";

        RobotsTxtParser parser = new RobotsTxtParser(
            new String[]{"yacybot"},
            robotsTxt.getBytes(StandardCharsets.UTF_8));

        assertTrue("denyList should contain / from wildcard", parser.denyList().contains("/"));
        assertTrue("allowList should be empty", parser.allowList().isEmpty());
    }

    @Test
    public void testCrawlDelayAppliesOnlyToMatchedBlock() {
        String robotsTxt =
            "User-agent: yacybot\n" +
            "Crawl-delay: 0\n" +
            "\n" +
            "User-agent: *\n" +
            "Crawl-delay: 60\n" +
            "Disallow: /\n";

        RobotsTxtParser parser = new RobotsTxtParser(
            new String[]{"yacybot"},
            robotsTxt.getBytes(StandardCharsets.UTF_8));

        assertEquals("yacybot crawl delay should be 0ms", 0L, parser.crawlDelayMillis());
    }

    @Test
    public void testWildcardCrawlDelayAppliesWhenNoSpecificMatch() {
        String robotsTxt =
            "User-agent: googlebot\n" +
            "Crawl-delay: 0\n" +
            "\n" +
            "User-agent: *\n" +
            "Crawl-delay: 5\n";

        RobotsTxtParser parser = new RobotsTxtParser(
            new String[]{"yacybot"},
            robotsTxt.getBytes(StandardCharsets.UTF_8));

        assertEquals("wildcard crawl delay should apply: 5000ms", 5000L, parser.crawlDelayMillis());
    }
}
