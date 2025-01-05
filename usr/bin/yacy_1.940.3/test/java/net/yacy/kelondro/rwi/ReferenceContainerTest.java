/**
 *  ReferenceContainerTest
 *  part of YaCy
 *  Copyright 2016 by reger24; https://github.com/reger24
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package net.yacy.kelondro.rwi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.crawler.retrieval.Response;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.data.word.WordReferenceFactory;
import net.yacy.kelondro.data.word.WordReferenceVars;
import net.yacy.kelondro.util.Bitfield;

/**
 * Unit tests for ReferenceContainer class.
 *
 * @author reger24
 */
public class ReferenceContainerTest {

    /**
     * Test of add method, of class ReferenceContainer. this also demonstrates a
     * issue with word.distance() used in ranking
     */
    @Test
    public void testAdd() throws Exception {
        ReferenceFactory<WordReference> wordReferenceFactory = new WordReferenceFactory();
        byte[] termHash = Word.word2hash("test");

		ReferenceContainer<WordReference> rc = new ReferenceContainer<WordReference>(wordReferenceFactory, termHash);

        // prepare a WordReference to be added to the container
        DigestURL url = new DigestURL("http://test.org/test.html");
        int urlComps = MultiProtocolURL.urlComps(url.toNormalform(true)).length;
        int urlLength = url.toNormalform(true).length();

        Queue<Integer> positions = new LinkedBlockingQueue<Integer>();
        positions.add(10);

        WordReferenceVars wentry = new WordReferenceVars(
                url.hash(),
                urlLength, // byte-length of complete URL
                urlComps, // number of path components
                0, // length of description/length (longer are better?)
                1, // how often appears this word in the text
                1, // total number of words
                1, // total number of phrases
                1, // first position of word in text
                positions, // positions of words that are joined into the reference
                1, // position of word in its phrase
                1, // number of the phrase where word appears
                0, // last-modified time of the document where word appears
                "en", // (guessed) language of document
                Response.DT_TEXT, // type of document
                0, // outlinks to same domain
                0, // outlinks to other domain
                new Bitfield(4), // attributes to the url and to the word according the url
                0.0d
        );

        rc.add(wentry); // add the ref

        assertTrue("size after add", rc.size() > 0);

        WordReference wc = rc.getReference(url.hash()); // retrieve the ref

        assertNotNull("getReference failed", wc);

        System.out.println("-----------------------------------------------------------");
        System.out.println("WordReference (word distance) before add to container:  " + wentry.distance());
        System.out.println("WordReference (word distance) after get from container: " + wc.distance());
        System.out.println("-----------------------------------------------------------");
        assertEquals("distance()", wentry.distance(), wc.distance());
    }

}
