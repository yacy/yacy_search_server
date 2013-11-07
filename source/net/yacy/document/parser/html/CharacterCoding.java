// CharacterCoding.java
// ----------------------------------
// (C) 22.10.2008 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2008
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser.html;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Contains methods to convert between Unicode and XML/HTML encoding.
 */
public final class CharacterCoding {

    /** Ampersand pattern */
    public final static Pattern ampPattern = Pattern.compile(Pattern.quote("&amp;"));
    /** Ampersand character in unicode encoding. */
    private static final char AMP_UNICODE = "\u0026".charAt(0);
    /** Ampersand character in HTML encoding. */
    private static final String AMP_HTML = "&amp;";
    /** Space character in HTML encoding. */
    private static final String SPACE_HTML = "&nbsp;";

    /** Special characters which have to be mapped for XML. */
    private static final String[] MAPPING4XML = {
        "\"", "&quot;",      //quotation mark
        "\u003C", "&lt;",    //less than
        "\u003E", "&gt;",    //greater than
    };

    /** Special characters which have to be mapped for HTML. */
    private static final String[] MAPPING4HTML = {
        "\\",     "&#092;",  // Backslash
        "\u005E", "&#094;",  // Caret

        "\u0060", "&#096;",  // Accent Grave `
        "\u007B", "&#123;",  // {
        "\u007C", "&#124;",  // |
        "\u007D", "&#125;",  // }
        "\u007E", "&#126;",  // ~

        "\u0082", "&#130;",
        "\u0083", "&#131;",
        "\u0084", "&#132;",
        "\u0085", "&#133;",
        "\u0086", "&#134;",
        "\u0087", "&#135;",
        "\u0088", "&#136;",
        "\u0089", "&#137;",
        "\u008A", "&#138;",
        "\u008B", "&#139;",
        "\u008C", "&#140;",
        "\u008D", "&#141;",
        "\u008E", "&#142;",

        "\u0091", "&#145;",
        "\u0092", "&#146;",
        "\u0093", "&#147;",
        "\u0094", "&#148;",
        "\u0095", "&#149;",
        "\u0096", "&#150;",
        "\u0097", "&#151;",
        "\u0098", "&#152;",
        "\u0099", "&#153;",
        "\u009A", "&#154;",
        "\u009B", "&#155;",
        "\u009C", "&#156;",
        "\u009D", "&#157;",
        "\u009E", "&#158;",
        "\u009F", "&#159;",

        "\u00A1", "&iexcl;",    //inverted (spanish) exclamation mark
        "\u00A2", "&cent;",     //cent
        "\u00A3", "&pound;",    //pound
        "\u00A4", "&curren;",   //currency
        "\u00A5", "&yen;",      //yen
        "\u00A6", "&brvbar;",   //broken vertical bar
        "\u00A7", "&sect;",     //section sign
        "\u00A8", "&uml;",      //diaeresis (umlaut)
        "\u00A9", "&copy;",     //copyright sign
        "\u00AA", "&ordf;",     //feminine ordinal indicator
        "\u00AB", "&laquo;",    //left-pointing double angle quotation mark
        "\u00AC", "&not;",      //not sign
        "\u00AD", "&shy;",      //soft hyphen
        "\u00AE", "&reg;",      //registered sign
        "\u00AF", "&macr;",     //macron
        "\u00B0", "&deg;",      //degree sign
        "\u00B1", "&plusmn;",   //plus-minus sign
        "\u00B2", "&sup2;",     //superscript two
        "\u00B3", "&sup3;",     //superscript three
        "\u00B4", "&acute;",    //acute accent
        "\u00B5", "&micro;",    //micro sign
        "\u00B6", "&para;",     //paragraph sign
        "\u00B7", "&middot;",   //middle dot
        "\u00B8", "&cedil;",    //cedilla
        "\u00B9", "&sup1;",     //superscript one
        "\u00BA", "&ordm;",     //masculine ordinal indicator
        "\u00BB", "&raquo;",    //right-pointing double angle quotation mark
        "\u00BC", "&frac14;",   //fraction 1/4
        "\u00BD", "&frac12;",   //fraction 1/2
        "\u00BE", "&frac34;",   //fraction 3/4
        "\u00BF", "&iquest;",   //inverted (spanisch) questionmark
        "\u00C0", "&Agrave;",
        "\u00C1", "&Aacute;",
        "\u00C2", "&Acirc;",
        "\u00C3", "&Atilde;",
        "\u00C4", "&Auml;",
        "\u00C5", "&Aring;",
        "\u00C6", "&AElig;",
        "\u00C7", "&Ccedil;",
        "\u00C8", "&Egrave;",
        "\u00C9", "&Eacute;",
        "\u00CA", "&Ecirc;",
        "\u00CB", "&Euml;",
        "\u00CC", "&Igrave;",
        "\u00CD", "&Iacute;",
        "\u00CE", "&Icirc;",
        "\u00CF", "&Iuml;",
        "\u00D0", "&ETH;",
        "\u00D1", "&Ntilde;",
        "\u00D2", "&Ograve;",
        "\u00D3", "&Oacute;",
        "\u00D4", "&Ocirc;",
        "\u00D5", "&Otilde;",
        "\u00D6", "&Ouml;",
        "\u00D7", "&times;",
        "\u00D8", "&Oslash;",
        "\u00D9", "&Ugrave;",
        "\u00DA", "&Uacute;",
        "\u00DB", "&Ucirc;",
        "\u00DC", "&Uuml;",
        "\u00DD", "&Yacute;",
        "\u00DE", "&THORN;",
        "\u00DF", "&szlig;",
        "\u00E0", "&agrave;",
        "\u00E1", "&aacute;",
        "\u00E2", "&acirc;",
        "\u00E3", "&atilde;",
        "\u00E4", "&auml;",
        "\u00E5", "&aring;",
        "\u00E6", "&aelig;",
        "\u00E7", "&ccedil;",
        "\u00E8", "&egrave;",
        "\u00E9", "&eacute;",
        "\u00EA", "&ecirc;",
        "\u00EB", "&euml;",
        "\u00EC", "&igrave;",
        "\u00ED", "&iacute;",
        "\u00EE", "&icirc;",
        "\u00EF", "&iuml;",
        "\u00F0", "&eth;",
        "\u00F1", "&ntilde;",
        "\u00F2", "&ograve;",
        "\u00F3", "&oacute;",
        "\u00F4", "&ocirc;",
        "\u00F5", "&otilde;",
        "\u00F6", "&ouml;",
        "\u00F7", "&divide;",
        "\u00F8", "&oslash;",
        "\u00F9", "&ugrave;",
        "\u00FA", "&uacute;",
        "\u00FB", "&ucirc;",
        "\u00FC", "&uuml;",
        "\u00FD", "&yacute;",
        "\u00FE", "&thorn;",
        "\u00FF", "&yuml;"
    };

    /** Mapping for XML to unicode. */
    private static final Map<String, Character> HTML2UNICODE4XML =
            new HashMap<String, Character>(MAPPING4XML.length * 2);
    /** Mapping for HTML to unicode. */
    private static final Map<String, Character> HTML2UNICODE4HTML =
            new HashMap<String, Character>(MAPPING4HTML.length * 2);
    /** Mapping for unicode to XML. */
    private static final Map<Character, String> UNICODE2HTML4XML =
            new HashMap<Character, String>(MAPPING4XML.length * 2);
    /** Mapping for unicode to HTML. */
    private static final Map<Character, String> UNICODE2HTML4HTML =
            new HashMap<Character, String>(MAPPING4HTML.length * 2);
    static {
        Character c;
        for (int i = 0; i < MAPPING4HTML.length; i += 2) {
            c = Character.valueOf(MAPPING4HTML[i].charAt(0));
            HTML2UNICODE4HTML.put(MAPPING4HTML[i + 1], c);
            UNICODE2HTML4HTML.put(c, MAPPING4HTML[i + 1]);
        }
        for (int i = 0; i < MAPPING4XML.length; i += 2) {
            c = Character.valueOf(MAPPING4XML[i].charAt(0));
            HTML2UNICODE4XML.put(MAPPING4XML[i + 1], c);
            UNICODE2HTML4XML.put(c, MAPPING4XML[i + 1]);
        }
    }

    /** Private constructor to avoid instantiation of utility
     * class with only static methods.
     */
    private CharacterCoding() { }

    /**
     * Replaces characters which have special representation in XML.
     * @see #MAPPING4XML
     * @param text text with character to replace
     * @param amp true if ampersands shall be replaced, else false
     * @return text with replaced characters
     */
    public static String unicode2xml(final String text, final boolean amp) {
        return unicode2html(text, amp, false);
    }

    /**
     * Replaces characters which have special representation in HTML.
     * @see #MAPPING4HTML
     * @param text text with character to replace
     * @param amp true if ampersands shall be replaced, else false
     * @return text with replaced characters
     */
    public static String unicode2html(final String text, final boolean amp) {
        return unicode2html(text, amp, true);
    }

    /**
     * Replaces characters which have special representation in HTML or XML.
     * @param text text with character to replace
     * @param amp true if ampersands shall be replaced, else false
     * @param html true if characters shall be replaced for embedding in
     * HTML, false for XML (far more characters are replaced for HTML,
     * compare {@link #MAPPING4HTML} with {@link #MAPPING4XML}
     * @return text with replaced characters
     */
    private static String unicode2html(
            final String text, final boolean amp, final boolean html) {
        if (text == null) return null;
        final StringBuilder sb = new StringBuilder(text.length() * 12 / 10);
        int textpos = 0;
        String r;
        char c;
        while (textpos < text.length()) {
            // find a (forward) mapping
            c = text.charAt(textpos);
            if (amp &&  c == AMP_UNICODE) {
                sb.append(AMP_HTML);
                textpos++;
                continue;
            }
            if ((r = UNICODE2HTML4XML.get(c)) != null) {
                sb.append(r);
                textpos++;
                continue;
            }
            if (html && (r = UNICODE2HTML4HTML.get(c)) != null) {
                sb.append(r);
                textpos++;
                continue;
            }
            sb.append(c);
            textpos++;
        }
        return sb.toString();
    }
    
    /**
     * Replaces HTML-encoded characters with unicode representation.
     * @param text text with character to replace
     * @return text with replaced characters
     */
    public static String html2unicode(String text) {
        if (text == null) return null;
        text = ampPattern.matcher(text).replaceAll("&"); // sometimes a double-replacement is necessary.
        int p = 0, p1, q;
        final StringBuilder sb = new StringBuilder(text.length());
        String s;
        Character r;
        while (p < text.length()) {
            p1 = text.indexOf('&', p);
            if (p1 < 0) {
                sb.append(text, p, text.length());
                break;
            }
            sb.append(text, p, p1);
            p = p1;
            if (p >= text.length()) {
                break;
            }
            q = text.indexOf(';', p);
            if (q < 0) {
                // if there is now no semicolon, then this will also fail when another ampersand is found afterwards
                // we are finished here
                sb.append(text, p, text.length());
                break;
            }
            s = text.substring(p, q + 1);
            p = q + 1;
            if (s.equals(AMP_HTML)) {
                sb.append(AMP_UNICODE);
                continue;
            }
            if (s.equals(SPACE_HTML)) {
                sb.append(" ");
                continue;
            }
            if ((r = HTML2UNICODE4XML.get(s)) != null) {
                sb.append(r.charValue());
                continue;
            }
            if ((r = HTML2UNICODE4HTML.get(s)) != null) {
                sb.append(r);
                continue;
            }
            if (s.charAt(1) == '#') {
                if (s.charAt(2) == 'x' || s.charAt(2) == 'X') {
                    sb.append(new char[] {(char) Integer.parseInt(s.substring(3, s.length() - 1), 16)});
                    continue;
                }
                String ucs = s.substring(2, s.length() - 1);
                try {
                    int uc = Integer.parseInt(ucs);
                    sb.append(new char[] {(char) uc});
                } catch (final NumberFormatException e) { }
                continue;
            }
            // the entity is unknown, skip it
        }
        return sb.toString();
    }

    /**
     * Test method. Ignore it if you don't need it.
     * @param args will be ignored
     */
    public static void main(final String[] args) {
        final String text =
                "Test-Text mit & um zyklische &uuml; &amp; Ersetzungen auszuschliessen";
        final String txet = unicode2html(text, true);
        System.out.println(txet);
        System.out.println(html2unicode(txet));
        if (html2unicode(txet).equals(text)) {
            System.out.println("correct");
        }

        final String text2 = "encodeUnicode2xml: & \" < >";
        System.out.println(text2);
        System.out.println(unicode2xml(text2, true));

        final String text3 = "space&nbsp;t&auml;st";
        System.out.println(text3);
        System.out.println(html2unicode(text3));
    }
}
