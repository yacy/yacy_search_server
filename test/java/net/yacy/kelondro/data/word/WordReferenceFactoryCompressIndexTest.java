package net.yacy.kelondro.data.word;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.kelondro.rwi.ReferenceContainer;

public class WordReferenceFactoryCompressIndexTest {

    @Test
    public void compressIndexKeepsUrlHashEncoding() throws Exception {
        final DigestURL url = new DigestURL("https://example.org/path/item.html");
        final byte[] urlHash = url.hash();
        final WordReferenceFactory factory = new WordReferenceFactory();
        final ReferenceContainer<WordReference> container = new ReferenceContainer<>(factory, Word.word2hash("term"));
        container.add(new WordReferenceRow(urlHash, url.toNormalform(true).length(), 3, 1, 1, 1,
                System.currentTimeMillis(), System.currentTimeMillis(), null, 't', 0, 0));

        final String expected = "{" + ASCII.String(urlHash, 6, 6) + ":" + ASCII.String(urlHash, 0, 6) + "}";
        assertEquals(expected, WordReferenceFactory.compressIndex(container, null, Long.MAX_VALUE).toString());
    }
}
