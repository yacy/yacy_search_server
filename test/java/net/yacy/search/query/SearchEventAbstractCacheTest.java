package net.yacy.search.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.data.word.WordReferenceRow;
import net.yacy.kelondro.rwi.ReferenceContainer;

public class SearchEventAbstractCacheTest {

    @Test
    public void compressesOnlyRequestedAbstractsAndReusesThem() throws Exception {
        final Map<byte[], ReferenceContainer<WordReference>> containers = new TreeMap<>(Base64Order.enhancedCoder);
        final byte[] firstTerm = Word.word2hash("first");
        final byte[] secondTerm = Word.word2hash("second");
        final WordReferenceFactory factory = new WordReferenceFactory();
        final DigestURL url = new DigestURL("https://example.org/path/item.html");
        final byte[] urlHash = url.hash();
        containers.put(firstTerm, container(factory, firstTerm, urlHash, url));
        containers.put(secondTerm, container(factory, secondTerm, urlHash, url));

        final SearchEventAbstractCache abstracts = new SearchEventAbstractCache(true, () -> containers);
        assertEquals(0, abstracts.size());

        final String expected = "{" + ASCII.String(urlHash, 6, 6) + ":" + ASCII.String(urlHash, 0, 6) + "}";
        assertEquals(expected, abstracts.get(firstTerm));
        assertEquals(1, abstracts.size());
        assertEquals(expected, abstracts.get(firstTerm));
        assertEquals(1, abstracts.size());

        assertEquals(expected, abstracts.get(secondTerm));
        assertEquals(2, abstracts.size());
    }

    @Test
    public void disabledOrClearedCacheDoesNotMaterializeAbstracts() throws Exception {
        final Map<byte[], ReferenceContainer<WordReference>> containers = new TreeMap<>(Base64Order.enhancedCoder);
        final byte[] term = Word.word2hash("term");
        final WordReferenceFactory factory = new WordReferenceFactory();
        final DigestURL url = new DigestURL("https://example.org/path/item.html");
        containers.put(term, container(factory, term, url.hash(), url));

        final AtomicInteger sourceReads = new AtomicInteger();
        final SearchEventAbstractCache disabled = new SearchEventAbstractCache(false, () -> {
            sourceReads.incrementAndGet();
            return containers;
        });
        assertNull(disabled.get(term));
        assertEquals(0, disabled.size());
        assertEquals(0, sourceReads.get());

        final SearchEventAbstractCache cleared = new SearchEventAbstractCache(true, () -> {
            sourceReads.incrementAndGet();
            return containers;
        });
        cleared.clear();
        assertNull(cleared.get(term));
        assertEquals(0, cleared.size());
        assertEquals(0, sourceReads.get());
    }

    private static ReferenceContainer<WordReference> container(final WordReferenceFactory factory,
            final byte[] term, final byte[] urlHash, final DigestURL url) throws Exception {
        final ReferenceContainer<WordReference> container = new ReferenceContainer<>(factory, term);
        final long now = System.currentTimeMillis();
        container.add(new WordReferenceRow(urlHash, url.toNormalform(true).length(), 3, 1, 1, 1,
                now, now, null, 't', 0, 0));
        return container;
    }
}
