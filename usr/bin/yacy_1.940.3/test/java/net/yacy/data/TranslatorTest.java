package net.yacy.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class TranslatorTest {

    /**
     * Test of translate method, of class Translator.
     */
    @Test
    public void testTranslate() {
        // test that translator respects word bondaries  ( e.g. key=bug not translate "mybugfix"
        Translator t = new Translator();
        final Map<String, String> translationTable = new HashMap<String, String>();
        translationTable.put("MIST", "Nebel"); // key upper case just to easy identify it in test strings
        translationTable.put(">MIST", ">Nebel");
        translationTable.put("BY", "bei");
        translationTable.put(">BY", ">bei");
        translationTable.put("BY<", "bei<");
        translationTable.put(">BY<", ">bei<");

        // source test text, expected not to be translated
        Set<String> noChange = new HashSet<String>();
        noChange.add("MISTer wong ");
        noChange.add("make no MISTake");
        noChange.add("value=\"MISTake\" ");
        noChange.add("<b>MISTral</b>");
        noChange.add("value=\"#[MISTake]#\" ");
        noChange.add(" optiMIST ");
        noChange.add("goodBY.");
        noChange.add(" BYte");
        noChange.add("<label>BYte</label>");
        //noChange.add(" BY_BY "); // this translates

        // source test text, to be translated
        Set<String> doChange = new HashSet<String>();
        doChange.add("Queen of the MIST ");
        doChange.add("value=\"#[MIST]#\" ");
        doChange.add("text#[MIST]#text ");
        doChange.add("MIST in the forrest");
        doChange.add("MIST\nin the forrest");
        doChange.add("<label>BY</label>");

        String result;
        for (String stringToExamine : noChange) {
            StringBuilder source = new StringBuilder(stringToExamine);
            result = t.translate(source, translationTable);
            assertEquals(result, stringToExamine);
        }

        for (String stringToExamine : doChange) {
            StringBuilder source = new StringBuilder(stringToExamine);
            result = t.translate(source, translationTable);
            assertNotEquals(result, stringToExamine);
        }
    }

}
