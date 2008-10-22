// htmlFilterCharacterCoding.java
// ----------------------------------
// (C) 22.10.2008 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2008
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

package de.anomic.htmlFilter;

import java.util.HashMap;

public class htmlFilterCharacterCoding {

    private static final char amp_unicode = "\u0026".charAt(0);
    private static final String amp_html = "&amp;";
    
    private static final String[] mapping4xml = {
        "\"","&quot;",      //quotation mark
        "\u003C","&lt;",    //less than
        "\u003E","&gt;",    //greater than
    };
    
    private static final String[] mapping4html = {
        "\\",    "&#092;",  // Backslash
        "\u005E","&#094;",  // Caret

        "\u0060","&#096;",  // Accent Grave `
        "\u007B","&#123;",  // {
        "\u007C","&#124;",  // |
        "\u007D","&#125;",  // }
        "\u007E","&#126;",  // ~

        "\u0082","&#130;",
        "\u0083","&#131;",
        "\u0084","&#132;",
        "\u0085","&#133;",
        "\u0086","&#134;",
        "\u0087","&#135;",
        "\u0088","&#136;",
        "\u0089","&#137;",
        "\u008A","&#138;",
        "\u008B","&#139;",
        "\u008C","&#140;",
        "\u008D","&#141;",
        "\u008E","&#142;",

        "\u0091","&#145;",
        "\u0092","&#146;",
        "\u0093","&#147;",
        "\u0094","&#148;",
        "\u0095","&#149;",
        "\u0096","&#150;",
        "\u0097","&#151;",
        "\u0098","&#152;",
        "\u0099","&#153;",
        "\u009A","&#154;",
        "\u009B","&#155;",
        "\u009C","&#156;",
        "\u009D","&#157;",
        "\u009E","&#158;",
        "\u009F","&#159;",

        "\u00A1","&iexcl;",    //inverted (spanish) exclamation mark
        "\u00A2","&cent;",     //cent
        "\u00A3","&pound;",    //pound
        "\u00A4","&curren;",   //currency
        "\u00A5","&yen;",      //yen
        "\u00A6","&brvbar;",   //broken vertical bar
        "\u00A7","&sect;",     //section sign
        "\u00A8","&uml;",      //diaeresis (umlaut)
        "\u00A9","&copy;",     //copyright sign
        "\u00AA","&ordf;",     //feminine ordinal indicator
        "\u00AB","&laquo;",    //left-pointing double angle quotation mark
        "\u00AC","&not;",      //not sign
        "\u00AD","&shy;",      //soft hyphen
        "\u00AE","&reg;",      //registered sign
        "\u00AF","&macr;",     //macron
        "\u00B0","&deg;",      //degree sign
        "\u00B1","&plusmn;",   //plus-minus sign
        "\u00B2","&sup2;",     //superscript two
        "\u00B3","&sup3;",     //superscript three
        "\u00B4","&acute;",    //acute accent
        "\u00B5","&micro;",    //micro sign
        "\u00B6","&para;",     //paragraph sign
        "\u00B7","&middot;",   //middle dot
        "\u00B8","&cedil;",    //cedilla
        "\u00B9","&sup1;",     //superscript one
        "\u00BA","&ordm;",     //masculine ordinal indicator
        "\u00BB","&raquo;",    //right-pointing double angle quotation mark
        "\u00BC","&frac14;",   //fraction 1/4
        "\u00BD","&frac12;",   //fraction 1/2
        "\u00BE","&frac34;",   //fraction 3/4
        "\u00BF","&iquest;",   //inverted (spanisch) questionmark
        "\u00C0","&Agrave;",
        "\u00C1","&Aacute;",
        "\u00C2","&Acirc;",
        "\u00C3","&Atilde;",
        "\u00C4","&Auml;",
        "\u00C5","&Aring;",
        "\u00C6","&AElig;",
        "\u00C7","&Ccedil;",
        "\u00C8","&Egrave;",
        "\u00C9","&Eacute;",
        "\u00CA","&Ecirc;",
        "\u00CB","&Euml;",
        "\u00CC","&Igrave;",
        "\u00CD","&Iacute;",
        "\u00CE","&Icirc;",
        "\u00CF","&Iuml;",
        "\u00D0","&ETH;",
        "\u00D1","&Ntilde;",
        "\u00D2","&Ograve;",
        "\u00D3","&Oacute;",
        "\u00D4","&Ocirc;",
        "\u00D5","&Otilde;",
        "\u00D6","&Ouml;",
        "\u00D7","&times;",
        "\u00D8","&Oslash;",
        "\u00D9","&Ugrave;",
        "\u00DA","&Uacute;",
        "\u00DB","&Ucirc;",
        "\u00DC","&Uuml;",
        "\u00DD","&Yacute;",
        "\u00DE","&THORN;",
        "\u00DF","&szlig;",
        "\u00E0","&agrave;",
        "\u00E1","&aacute;",
        "\u00E2","&acirc;",
        "\u00E3","&atilde;",
        "\u00E4","&auml;",
        "\u00E5","&aring;",
        "\u00E6","&aelig;",
        "\u00E7","&ccedil;",
        "\u00E8","&egrave;",
        "\u00E9","&eacute;",
        "\u00EA","&ecirc;",
        "\u00EB","&euml;",
        "\u00EC","&igrave;",
        "\u00ED","&iacute;",
        "\u00EE","&icirc;",
        "\u00EF","&iuml;",
        "\u00F0","&eth;",
        "\u00F1","&ntilde;",
        "\u00F2","&ograve;",
        "\u00F3","&oacute;",
        "\u00F4","&ocirc;",
        "\u00F5","&otilde;",
        "\u00F6","&ouml;",
        "\u00F7","&divide;",
        "\u00F8","&oslash;",
        "\u00F9","&ugrave;",
        "\u00FA","&uacute;",
        "\u00FB","&ucirc;",
        "\u00FC","&uuml;",
        "\u00FD","&yacute;",
        "\u00FE","&thorn;",
        "\u00FF","&yuml;"
    };
    
    private final static HashMap<String, Character> html2unicode4xml = new HashMap<String, Character>();
    private final static HashMap<String, Character> html2unicode4html = new HashMap<String, Character>();
    private final static HashMap<Character, String> unicode2html4xml = new HashMap<Character, String>();
    private final static HashMap<Character, String> unicode2html4html = new HashMap<Character, String>();
    static {
        Character c;
        for (int i = 0; i < mapping4html.length; i += 2) {
            c = new Character(mapping4html[i].charAt(0));
            html2unicode4html.put(mapping4html[i + 1], c);
            unicode2html4html.put(c, mapping4html[i + 1]);
        }
        for (int i = 0; i < mapping4xml.length; i += 2) {
            c = new Character(mapping4xml[i].charAt(0));
            html2unicode4xml.put(mapping4xml[i + 1], c);
            unicode2html4xml.put(c, mapping4xml[i + 1]);
        }
    }
    
    public static String unicode2xml(final String text, boolean amp) {
        return unicode2html(text, amp, false);
    }
    
    public static String unicode2html(final String text, boolean amp) {
        return unicode2html(text, amp, true);
    }
    
    private static String unicode2html(final String text, boolean amp, boolean html) {
        if (text == null) return null;
        final StringBuffer sb = new StringBuffer(text.length() * 12 / 10);
        int textpos = 0;
        String r;
        char c;
        while (textpos < text.length()) {
            // find a (forward) mapping
            c = text.charAt(textpos);
            if (amp &&  c == amp_unicode) {
                sb.append(amp_html);
                textpos++;
                continue;
            }
            if ((r = unicode2html4xml.get(c)) != null) {
                sb.append(r);
                textpos++;
                continue;
            }
            if (html && (r = unicode2html4html.get(c)) != null) {
                sb.append(r);
                textpos++;
                continue;
            }
            sb.append(c);
            textpos++;
        }
        return sb.toString();
    }
    
    public static String html2unicode(final String text) {
        if (text == null) return null;
        int p = 0, p1, q;
        final StringBuffer sb = new StringBuffer(text.length());
        String s;
        Character r;
        while (p < text.length()) {
            p1 = text.indexOf('&', p);
            if (p1 < 0) p1 = text.length();
            sb.append(text.subSequence(p, p1));
            p = p1;
            if (p >= text.length()) break;
            q = text.indexOf(';', p);
            if (q < 0) {
                p++;
                continue;
            }
            s = text.substring(p, q + 1);
            p = q + 1;
            if (s.equals(amp_html)) {
                sb.append(amp_unicode);
                continue;
            }
            if ((r = html2unicode4xml.get(s)) != null) {
                sb.append(r.charValue());
                continue;
            }
            if ((r = html2unicode4html.get(s)) != null) {
                sb.append(r);
                continue;
            }
            // the entity is unknown, skip it
        }
        return new String(sb);
    }

    public static void main(final String[] args) {
        final String text = "Test-Text mit & um zyklische &uuml; &amp; Ersetzungen auszuschliessen";
        final String txet = unicode2html(text, true);
        System.out.println(txet);
        System.out.println(html2unicode(txet));
        if (html2unicode(txet).equals(text)) System.out.println("correct");
        
        final String text2 = "encodeUnicode2xml: & \" < >";
        System.out.println(text2);
        System.out.println(unicode2xml(text2, true));
    }
}
