package net.yacy.crawler.focused;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;

public class FocusedNoveltyStateTest {

    @Test
    public void tracksHostsAndPathPrefixesAcrossRestart() throws Exception {
        final java.io.File state = Files.createTempDirectory("focused-novelty-").toFile();
        final FocusedNoveltyState first = new FocusedNoveltyState(state, "astronomy");
        assertTrue(first.observe(new DigestURL("https://www.research.example/papers/one/page.html"), 2).newHost());
        final FocusedNoveltyState.Observation second = first.observe(
                new DigestURL("https://research.example/observations/two/page.html"), 2);
        assertFalse(second.newHost());
        assertTrue(second.newPath());
        first.close();

        final FocusedNoveltyState reloaded = new FocusedNoveltyState(state, "astronomy");
        final FocusedNoveltyState.Observation known = reloaded.observe(
                new DigestURL("https://research.example/papers/one/other.html"), 2);
        assertFalse(known.newHost());
        assertFalse(known.newPath());
        reloaded.close();
    }
}
