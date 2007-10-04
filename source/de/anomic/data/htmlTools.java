package de.anomic.data;

public class htmlTools {

    /** Replaces characters in a string with other entities according to HTML standards.
      * @param text a string that possibly contains special characters
      * @param includingAmpersand if <code>false</code> ampersands are not encoded
      * @return the string with all characters replaced by the corresponding character from array
      */
      //[FB], changes by [MN], re-implemented by [MC]
    public static String encodeUnicode2html(String text, boolean includingAmpersand) {
        if (text == null) return null;
        int spos = (includingAmpersand ? 0 : 2);
        int epos = mapping.length;

        return encode(text, mapping, spos, epos);
    }

    /**
     * Replaces special entities ampersand, quotation marks, and less than/graiter than
     * by the escaping entities allowed in XML documents.
     * 
     * @param text the original String
     * @return the encoded String
     */
    public static String encodeUnicode2xml(String text) {
        if (text == null) return null;
        int spos = 0;
        int epos = 8;

        return encode(text, mapping, spos, epos);
    }

    /**
     * Generic method that replaces occurences of special character entities defined in map 
     * array with their corresponding mapping.
     * @param text The String too process.
     * @param map  An array defining the entity mapping.
     * @param spos It is possible to use a subset of the map only. This parameter defines the
     *             starting point in the map array. 
     * @param epos The ending point, see above.
     * @return A copy of the original String with all entities defined in map replaced.
     */
    public static String encode(String text, final String[] map, int spos, int epos) {
        StringBuffer sb = new StringBuffer(text.length());
        search: while (spos < text.length()) {
            // find a (forward) mapping
            loop: for (int i = spos; i < epos; i += 2) {
                if (text.charAt(spos) != map[i].charAt(0)) continue loop;
                // found match
                sb.append(map[i + 1]);
                spos++;
                continue search;
            }
            // not found match
            sb.append(text.charAt(spos));
            spos++;
        }

        return sb.toString();
    }
    
    public static String decodeHtml2Unicode(String text) {
        if (text == null) return null;
        int pos = 0;
        StringBuffer sb = new StringBuffer(text.length());
        search: while (pos < text.length()) {
            // find a reverse mapping. TODO: replace matching with hashtable(s)
            loop: for (int i = 0; i < mapping.length; i += 2) {
                if (pos + mapping[i + 1].length() > text.length()) continue loop;
                for (int j = mapping[i + 1].length() - 1; j >= 0; j--) {
                    if (text.charAt(pos + j) != mapping[i + 1].charAt(j)) continue loop;
                }
                // found match
                sb.append(mapping[i]);
                pos = pos + mapping[i + 1].length();
                continue search;
            }
            // not found match
            sb.append(text.charAt(pos));
            pos++;
        }
        return new String(sb);
    }

    //This array contains codes (see http://mindprod.com/jgloss/unicode.html for details) 
    //that will be replaced. To add new codes or patterns, just put them at the end
    //of the list. Codes or patterns in this list can not be escaped with [= or <pre>
    private static final String[] mapping = {
        // Ampersands _have_ to be replaced first. If they were replaced later,
        // other replaced characters containing ampersands would get messed up.
        "\u0026","&amp;",      //ampersand
        "\"","&quot;",         //quotation mark
        "\u003C","&lt;",       //less than
        "\u003E","&gt;",       //greater than
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
    
    public static void main(String[] args) {
        String text = "Test-Text mit & um zyklische &uuml; &amp; Ersetzungen auszuschliessen";
        String txet = encodeUnicode2html(text, true);
        System.out.println(txet);
        System.out.println(decodeHtml2Unicode(txet));
        if (decodeHtml2Unicode(txet).equals(text)) System.out.println("correct");
    }
}
