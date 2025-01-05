package net.yacy.data.wiki;

import org.junit.Test;

import static org.junit.Assert.*;


public class WikiCodeTest {

    /**
     * test geo location metadata convert
     */
    @Test
    public void testProcessMetadataCoordinates() {
        String[] testmeta = new String[]{
            "{{coordinate|NS=52.205944|EW=0.117593|region=GB-CAM|type=landmark}}",  // decimal  N-E location
            "{{coordinate|NS=43/50/29/N|EW=73/23/17/W|type=landmark|region=US-NY}}", // N-W location

            "{{Coordinate |text=DMS |NS=50/7/49/N |EW=6/8/09/E |type=landmark |region=BE-WLG |name=Monument des trois Frontières}}",
            "{{Coordinate |text=DMS |NS= 49.047169|EW=7.899148|region=DE-RP |type=landmark |name=Europadenkmal (Rheinland-Pfalz)}}",
            "{{Coordinate |text=DMS |NS= 49.047169|EW=7.899148|region=DE-RP |type=landmark |name={{de}}Europadenkmal (Rheinland-Pfalz)}}",// with nested language template

            "{{coordinate|NS=0.00000|EW=0.117593}}", // testing equator coord
            "{{coordinate|NS=-10.00000|EW=-10.10000}}", // testing S-E location
            "{{coordinate|NS=12a5|EW=-10.10000}}" // testing malformed coordinates value

        };
        WikiCode wc = new WikiCode();
        for (int i = 0; i < testmeta.length; i++) {
            String result = wc.transform("http://wiki:8080",testmeta[i]);
            System.out.println(testmeta[i] + " --> " + result);
            // simply check if replacement took place, if no coordinate recognized original string is just html encoded
            assertFalse(result.contains("#123;")); // simple check - result not containing char code for "{",
            assertFalse(result.contains("#125;")); // simple check - result not containing char code for "}"
        }
    }
    
    /**
     * Test multi-line template inclusion processing
     */
    @Test
    public void testTransformMultilineTemplateInclusion() {
    	String wikitext = "{{Infobox|Example\n"
    			+ "<!-- *** Name section *** -->\n" 
    			+ "| name = Example\n"
    			+ "| category = [[Infobox Examples|Example]]\n" 
    			+ "<!-- *** Website *** -->\n"
    			+ "| website = {{URL|http://example.com}}\n" 
    			+ "}}";
    	WikiCode wc = new WikiCode();
    	String result = wc.transform("http://wiki:8080", wikitext);
    	System.out.println(wikitext + " --> " + result);
        assertFalse(result.contains("#123;")); // simple check - result not containing char code for "{",
        assertFalse(result.contains("#125;")); // simple check - result not containing char code for "}"
    }
    
    /**
     * Test single line template inclusion processing
     */
    @Test
    public void testProcessMetadataTransclusion() {
        final String[] wikitexts = new String[]{
        		"{{Like}}", // most simple template inclusion
        		"{{Stochastic processes}}", // page name including space
        		"{{:Stochastic processes}}", // page inclusion with implicit namespace
        		"{{WP:Assume good faith}}", // page inclusion from Wikipedia namespace
        		"{{Pagename|parameter1|parameter2|parameter3}}", // with unnamed parameters
        		"{{Pagename|parameter1=value1|parameter2=value2|parameter3=value3}}", // with named parameters
        		"{{Template|This is the title text|This is a custom warning line}}", // with parameters including spaces
        		"{{Special:Recentchangeslinked/General}}", // subpage inclusion
        		"{{Template1}} text {{Template2}} {{Template3|parameter value1|param2}}", // multiple templates on the same line
        		"{{Template|[[Page]]}}", // with link parameter
        		"{{Template|parameter1={{en}}value1|parameter2}}", // nested template inclusion
        		"{{Template|parameter1={{en|param1|param2=val2}}value1}}", // nested template with parameters inclusion
        		"{{Template", // Multi-line template inclusion beginning
        		"simple text  {{Template", // Multi-line template inclusion beginning with text before
        		"{{Template|parameter1={{en}} value1", // Multi-line template inclusion beginning with nested tag
        		"{{Template|parameter1={{subTemplate", // Multi-line nested template inclusion
        		"|parameter", // Multi-line template inclusion unnamed parameter line
        		"|parameter=value", // Multi-line template inclusion named parameter line
        		"|parameter={{subTemplate|param1|param2}}value", // Multi-line template inclusion with nested template inclusion
        		"|[[Page]]", // Multi-line template inclusion with unnamed link parameter
        		"|parameter=[[Page]]", // Multi-line template inclusion with named link parameter
        		"}}", // Multi-line template inclusion closing
        		"|lastParameter}}", // Multi-line template inclusion closing with unnamed parameter
        		"|lastParameter=value}}", // Multi-line template inclusion closing with named parameter
        		"|lastParameter={{en}}value}}", // Multi-line template inclusion closing with nested tag
        		"}}}}" // Multi-line nested template inclusion closing
        };
                
        for (String wikitext : wikitexts) {
            String result = WikiCode.processMetadata(wikitext);
            System.out.println(wikitext + " --> " + result);
            // simply check if replacement took place
            assertFalse(result.contains("{"));
            assertFalse(result.contains("|"));
            assertFalse(result.contains("="));
            assertFalse(result.contains("}"));
        }
        
        final String[] wikitextsNotToModify = new String[]{
        		"", // empty string
        		"Simple text",
        		"<pre>Simple preformatted text</pre>",
        		"[[Page]]", // link
        		"{|", // table start
        		"|-", // new table line
        		"||", // table cell divider
        		"|}", // table end
        };
        for (String wikitext : wikitextsNotToModify) {
            assertEquals("Text sould not have been modified", wikitext, WikiCode.processMetadata(wikitext));
        }
    }

    /**
     * test header wiki markup
     */
    @Test
    public void testProcessLineOfWikiCode() {
        String[] hdrTeststr = new String[]{ // ok test header
            "== Header ==", "==Header=="};

        String[] nohdrTeststr = new String[]{ // wrong test header
            "Text of = Header, false = wrong", "One=Two"};

        WikiCode wc = new WikiCode();

        for (String s : hdrTeststr) { // test ok header
            String erg = wc.transform("8090", s);
            assertTrue("<h2> tag expected:"+erg, erg.contains("<h2>"));
        }
        for (String s : nohdrTeststr) { // test wrong header
            String erg = wc.transform("8090", s);
            assertFalse("no header tag expected:"+erg, erg.contains("<h1>"));
        }
    }
    
    /**
     * Test internal link markup processing
     */
    @Test
    public void testInternalLink() {
        WikiCode wc = new WikiCode();
        
        /* Link to another wiki article */
        String result = wc.transform("http://wiki:8080", "[[article]]");
        assertTrue(result.contains("<a"));
        assertTrue(result.contains("href=\"Wiki.html?page=article\""));
        
        /* Renamed link */
        result = wc.transform("http://wiki:8080", "[[article|renamed article]]");
        assertTrue(result.contains("<a"));
        assertTrue(result.contains("href=\"Wiki.html?page=article\""));
        assertTrue(result.contains(">renamed article<"));
        
        /* Multiple links on the same line */
        result = wc.transform("http://wiki:8080", "[[article1]] [[article2]]");
        assertTrue(result.contains("<a"));
        assertTrue(result.contains("href=\"Wiki.html?page=article1\""));
        assertTrue(result.contains("href=\"Wiki.html?page=article2\""));
    }
    
    /**
     * Test external link markup processing
     */
    @Test
    public void testExternalLink() {
        WikiCode wc = new WikiCode();
        
        /* Unamed link */
        String result = wc.transform("http://wiki:8080", "[https://yacy.net]");
        assertTrue(result.contains("<a"));
        assertTrue(result.contains("href=\"https://yacy.net\""));
        
        /* Named link */
        result = wc.transform("http://wiki:8080", "[https://yacy.net YaCy]");
        assertTrue(result.contains("<a"));
        assertTrue(result.contains("href=\"https://yacy.net\""));
        assertTrue(result.contains(">YaCy<"));
        
        /* Lua Script array parameter : should not crash the transform process */
        result = wc.transform("http://wiki:8080", "'[[[[2,1],[4,3],[6,5],[2,1]],[[12,11],[14,13],[16,15],[12,11]]]]'");
    }
}
