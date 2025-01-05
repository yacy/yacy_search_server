/**
 *  WordReferenceVarsTest
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
package net.yacy.kelondro.data.word;

import java.net.MalformedURLException;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.crawler.retrieval.Response;
import net.yacy.kelondro.util.Bitfield;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for WordReferenceVars class.
 */
public class WordReferenceVarsTest {

    /**
     * Test of min method, of class WordReferenceVars.
     *
     * @author reger24
     */
    @Test
    public void testMin() throws MalformedURLException {

        // testing posintext and distance calculation
        int minposintext = 5; // minposintext for test
        int maxposintext = 30; // maxposintext for test

        DigestURL url = new DigestURL("http://test.org/test.html");
        // create a WordReference template with posintext = minposintext = 5
        final WordReferenceRow ientry = new WordReferenceRow(
                url.hash(), 20, 3, 2,
                1, 1,
                System.currentTimeMillis(), System.currentTimeMillis(),
                UTF8.getBytes("en"), Response.DT_TEXT,
                0, 0);
        Word word = new Word(minposintext, 1, 100);
        word.flags = new Bitfield(4);
        ientry.setWord(word);

        WordReferenceVars wvMin = new WordReferenceVars(ientry, true);
        // create a other reference
        WordReferenceVars wvOther = wvMin.clone();
        
        word.posInText = maxposintext;
        ientry.setWord(word);
        WordReferenceVars wvMax = new WordReferenceVars(ientry, true);

        wvMin.addPosition(10); // add position for distance testing
        wvMax.addPosition(maxposintext); // add position for distance testing
        wvOther.addPosition(maxposintext); // add position (max) for distance testing

        // test min for posintext and distance
        wvMin.min(wvOther);
        assertEquals("min posintext", minposintext, wvMin.posintext());
        assertEquals("min distance", 5, wvMin.distance());

        wvMin.min(wvOther); // test repeated call doesn't change result
        assertEquals("min posintext (repeat)", minposintext, wvMin.posintext());
        assertEquals("min distance (repeat)", 5, wvMin.distance());

        // test max for posintext and distance
        wvMax.max(wvOther);
        assertEquals("max posintext", maxposintext, wvMax.posintext());
        assertEquals("max distance", maxposintext - minposintext, wvMax.distance());

        wvMax.max(wvOther); // test repeated calls don't change result
        wvMax.max(wvOther);
        assertEquals("max posintext (repeat)", maxposintext, wvMax.posintext());
        assertEquals("max distance (repeat)", maxposintext - minposintext, wvMax.distance());

        // reverse test
        wvOther.max(wvMax);
        assertEquals("max posintext (reverse)", maxposintext, wvOther.posintext());
        assertEquals("max distance (repeat)", maxposintext - minposintext, wvOther.distance());

    }

}
