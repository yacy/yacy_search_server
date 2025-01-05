/**
 *  DCEntryTest
 *  part of YaCy
 *  Copyright 2017 by reger24; https://github.com/reger24
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
package net.yacy.document.content;

import java.util.HashMap;
import java.util.Map;
import net.yacy.cora.lod.vocabulary.DublinCore;
import org.junit.Test;
import static org.junit.Assert.*;


public class DCEntryTest {

    /**
     * Test of getLanguage method, of class DCEntry for ISO639-2 3-char language
     * codes as input to test convert of 3-char to the interal used ISO639-1
     * 2-char code.
     */
    @Test
    public void testGetLanguage_ISO639_2() {
        Map<String, String> testmap = new HashMap<String, String>();

        // key=ISO639-2 (3 char language code), value= corresponding ISO639-1 (2 char language code)
        testmap.put("ger", "de");   testmap.put("deu", "de");
        testmap.put("eng", "en");
        testmap.put("rus", "ru");
        testmap.put("jpn", "ja");
        testmap.put("ita", "it");
        testmap.put("por", "pt");
        testmap.put("pol", "pl");
        testmap.put("spa", "es");
        testmap.put("ukr", "uk");
        testmap.put("chi", "zh");   testmap.put("zho", "zh");
        testmap.put("fre", "fr");   testmap.put("fra", "fr");
        testmap.put("eus", "eu");   testmap.put("baq", "eu");
        testmap.put("gre", "el");   testmap.put("ell", "el");
        // some additional languages to test icu.ULocale of .getLanguage()
        testmap.put("ara", "ar");
        testmap.put("ces", "cs");
        testmap.put("nld", "nl");
        testmap.put("tur", "tr");

        for (String testlang : testmap.keySet()) {
            DCEntry dce = new DCEntry();
            // set a 3-char ISO639-2/ISO639-3
            dce.getMap().put(DublinCore.Language.getURIref(), new String[]{testlang});

            String expectedresult = testmap.get(testlang);
            String lng = dce.getLanguage();

            assertEquals("convert language code=" + testlang, expectedresult, lng);
        }
    }

}
