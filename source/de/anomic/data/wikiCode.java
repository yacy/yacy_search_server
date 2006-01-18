// wikiCode.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Alexander Schier
// last major change: 09.08.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// Contains contributions from Alexander Schier [AS]
// Franz Brausse [FB] and Marc Nause [MN]

package de.anomic.data;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.yacy.yacyCore;

/** This class provides methods to handle texts that have been posted in the yacyWiki. */
public class wikiCode {
    private String numListLevel="";
    private String ListLevel="";
    private String defListLevel="";
    private plasmaSwitchboard sb;
    private boolean escape = false;           //needed for escape
    private boolean escaped = false;          //needed for <pre> not getting in the way
    private boolean escapeSpan = false;       //needed for escape symbols [= and =] spanning over several lines 
    private boolean preformatted = false;     //needed for preformatted text
    private boolean preformattedSpan = false; //needed for <pre> and </pre> spanning over several lines
    private boolean replaced = false;         //indicates if method replaceHTML has been used with line already
    private int preindented = 0;              //needed for indented <pre>s
    private int escindented = 0;              //needed for indented [=s

    /** Constructor of the class wikiCode */
    public wikiCode(plasmaSwitchboard switchboard){
        sb=switchboard;
    }

    public String transform(String content){
        try {
            return transform(content.getBytes("UTF-8"), sb);
        } catch (UnsupportedEncodingException e) {
            return transform(content.getBytes(), sb);
        }
    }

    public String transform(byte[] content){
        return transform(content, sb);
    }

    public String transform(byte[] content, plasmaSwitchboard switchboard) {
        ByteArrayInputStream bais = new ByteArrayInputStream(content);
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(bais,
                    "UTF-8"));
            String line;
            StringBuffer out = new StringBuffer(content.length);
            try {
                while ((line = br.readLine()) != null) {
                    out.append(transformLine(line, switchboard)).append(
                            serverCore.crlfString);
                }
                return out.toString();
            } catch (UnsupportedEncodingException e1) {
                // can not happen
                return null;
            }
        } catch (IOException e) {
            return "internal error: " + e.getMessage();
        }
    }

    //The following method has been submitted by [FB] (added and a few changes by MN)
    /** Replaces special characters from a string. Otherwise they might cause ugly output on some systems.
      * This code is also important to avoid XSS attacks.
      *
      * @param text a string that possibly contains special characters
      * @return the string with all special characters encoded so they will look right on every system
      */
    public static String replaceHTML(String text) {
        if (text==null) { return null; }
        for (int x=0;x<=htmlentities.length-1;x=x+2) {
            int p=0;
            while ((p=text.indexOf(htmlentities[x],p))>=0) {
                text=text.substring(0,p)+htmlentities[x+1]+text.substring(p+htmlentities[x].length());
                p+=htmlentities[x+1].length();
            }
        }
        return text;
    }

    //This array contains codes (see http://mindprod.com/jgloss/unicode.html for details) and
    //patterns that will be replaced. To add new codes or patterns, just put them at the end
    //of the list.
    public static String[] htmlentities={
        // Ampersands _have_ to be replaced first. If they were replaced later,
        // other replaced characters containing ampersands would get messed up.
        "\u0026","&amp;",      //ampersand
        "\"","&quot;",         //quotation mark
        "\u003C","&lt;",       //less than
        "\u003E","&gt;",       //greater than
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
        "\u00FF","&yuml;",
        "(C)","&copy;"
    };
    //end contrib [FB] and [MN]


    /** Replaces wiki tags with HTML tags.
      *
      * @param result a line of text
      * @param switchboard
      *
      * @result the line of text with HTML tags instead of wiki tags
      */
    public String transformLine(String result, plasmaSwitchboard switchboard) {
        // transform page
        int p0, p1;
        boolean defList = false;    //needed for definition lists

        if (!replaced) {
            result = replaceHTML(result);
            replaced = true;
        }

        //check if line contains any escape symbol or tag for preformatted text 
        //or if we are in an esacpe sequence already or if we are in a preformated text
        //if that's the case the program will continue further below 
        //(see code for [= and =] and <pre> and </pre>) [MN]
        if((result.indexOf("[=")<0)&&(result.indexOf("=]")<0)&&(!escapeSpan)&&
           (result.indexOf("&lt;pre&gt;")<0)&&(result.indexOf("&lt;/pre&gt;")<0)&&(!preformattedSpan)){

            // format lines
            if (result.startsWith(" ")) result = "<tt>" + result + "</tt>";
            if (result.startsWith("----")) result = "<hr>";

            // citings contributed by [MN]
            if(result.startsWith(":")){
                String head = "";
                String tail = "";
                while(result.startsWith(":")){
                    head = head + "<blockquote>";
                    tail = tail + "</blockquote>";
                    result = result.substring(1);
                }
            result = head + result + tail;
        }
        // end contrib [MN]	

        // format headers
        if ((p0 = result.indexOf("====")) >= 0) {
            p1 = result.indexOf("====", p0 + 4);
            if (p1 >= 0) result = result.substring(0, p0) + "<h4>" +
                                  result.substring(p0 + 4, p1) + "</h4>" +
                                  result.substring(p1 + 4);
        }
        if ((p0 = result.indexOf("===")) >= 0) {
            p1 = result.indexOf("===", p0 + 3);
            if (p1 >= 0) result = result.substring(0, p0) + "<h3>" +
                                  result.substring(p0 + 3, p1) + "</h3>" +
                                  result.substring(p1 + 3);
        }
        if ((p0 = result.indexOf("==")) >= 0) {
            p1 = result.indexOf("==", p0 + 2);
            if (p1 >= 0) result = result.substring(0, p0) + "<h2>" +
                                  result.substring(p0 + 2, p1) + "</h2>" +
                                  result.substring(p1 + 2);
        }

        if ((p0 = result.indexOf("'''''")) >= 0) {
            p1 = result.indexOf("'''''", p0 + 5);
            if (p1 >= 0) result = result.substring(0, p0) + "<b><i>" +
                                  result.substring(p0 + 5, p1) + "</i></b>" +
                                  result.substring(p1 + 5);
        }
        if ((p0 = result.indexOf("'''")) >= 0) {
            p1 = result.indexOf("'''", p0 + 3);
            if (p1 >= 0) result = result.substring(0, p0) + "<b>" +
                                  result.substring(p0 + 3, p1) + "</b>" +
                                  result.substring(p1 + 3);
        }
        if ((p0 = result.indexOf("''")) >= 0) {
            p1 = result.indexOf("''", p0 + 2);
            if (p1 >= 0) result = result.substring(0, p0) + "<i>" +
                                  result.substring(p0 + 2, p1) + "</i>" +
                                  result.substring(p1 + 2);
        }

        //* unorderd Lists contributed by [AS]
        //** Sublist
        if(result.startsWith(ListLevel + "*")){ //more stars
            p0 = result.indexOf(ListLevel);
            p1 = result.length();
            result = "<ul>" + serverCore.crlfString +
                "<li>" +
            result.substring(ListLevel.length() + 1, p1) +
                "</li>";
            ListLevel += "*";
        }else if(ListLevel.length() > 0 && result.startsWith(ListLevel)){ //equal number of stars
            p0 = result.indexOf(ListLevel);
            p1 = result.length();
            result = "<li>" +
                result.substring(ListLevel.length(), p1) +
                "</li>";
        }else if(ListLevel.length() > 0){ //less stars
            int i = ListLevel.length();
            String tmp = "";

            while(! result.startsWith(ListLevel.substring(0,i)) ){
                tmp += "</ul>";
                i--;
            }
            ListLevel = ListLevel.substring(0,i);
            p0 = ListLevel.length();
            p1 = result.length();

            if(ListLevel.length() > 0){
                result = tmp +
                    "<li>" +
                    result.substring(p0, p1) +
                    "</li>";
            }else{
                result = tmp + result.substring(p0, p1);
            }
        }


        //# sorted Lists contributed by [AS]
        //## Sublist
        if(result.startsWith(numListLevel + "#")){ //more #
            p0 = result.indexOf(numListLevel);
            p1 = result.length();
            result = "<ol>" + serverCore.crlfString +
                "<li>" +
                result.substring(numListLevel.length() + 1, p1) +
                "</li>";
            numListLevel += "#";
        }else if(numListLevel.length() > 0 && result.startsWith(numListLevel)){ //equal number of #
            p0 = result.indexOf(numListLevel);
            p1 = result.length();
            result = "<li>" +
                result.substring(numListLevel.length(), p1) +
                "</li>";
        }else if(numListLevel.length() > 0){ //less #
            int i = numListLevel.length();
            String tmp = "";

            while(! result.startsWith(numListLevel.substring(0,i)) ){
                tmp += "</ol>";
                i--;
            }
            numListLevel = numListLevel.substring(0,i);
            p0 = numListLevel.length();
            p1 = result.length();

            if(numListLevel.length() > 0){
                result = tmp +
                     "<li>" +
                     result.substring(p0, p1) +
                     "</li>";
            }else{
                result = tmp + result.substring(p0, p1);
            }
        }
        // end contrib [AS]

        //* definition Lists contributed by [MN] based on unordered list code by [AS]
        if(result.startsWith(defListLevel + ";")){ //more semicolons
            String dt = ""; 
            String dd = "";
            p0 = result.indexOf(defListLevel);
            p1 = result.length();
            String resultCopy = result.substring(defListLevel.length() + 1, p1);
            if((p0 = resultCopy.indexOf(":")) > 0){
                dt = resultCopy.substring(0,p0);
                dd = resultCopy.substring(p0+1);
                result = "<dl>" + "<dt>" + dt + "</dt>" + "<dd>" + dd;
                defList = true;
            }
            defListLevel += ";";
        }else if(defListLevel.length() > 0 && result.startsWith(defListLevel)){ //equal number of semicolons
            String dt = ""; 
            String dd = "";
            p0 = result.indexOf(defListLevel);
            p1 = result.length();
            String resultCopy = result.substring(defListLevel.length(), p1);
            if((p0 = resultCopy.indexOf(":")) > 0){
                dt = resultCopy.substring(0,p0);
                dd = resultCopy.substring(p0+1);
                result = "<dt>" + dt + "</dt>" + "<dd>" + dd;
                defList = true;
            }
        }else if(defListLevel.length() > 0){ //less semicolons
            String dt = ""; 
            String dd = "";
            int i = defListLevel.length();
            String tmp = "";
            while(! result.startsWith(defListLevel.substring(0,i)) ){
                tmp += "</dd></dl>";
                i--;
            }
            defListLevel = defListLevel.substring(0,i);
            p0 = defListLevel.length();
            p1 = result.length();
            if(defListLevel.length() > 0){
                String resultCopy = result.substring(p0, p1);
                if((p0 = resultCopy.indexOf(":")) > 0){
                    dt = resultCopy.substring(0,p0);
                    dd = resultCopy.substring(p0+1);
                    result = tmp + "<dt>" + dt + "</dt>" + "<dd>" + dd;
                    defList = true;
                }

                }else{
                result = tmp + result.substring(p0, p1);
                }
            }
            // end contrib [MN]	


            // create links
            String kl, kv, alt, align;
            int p;
            // internal links and images
            while ((p0 = result.indexOf("[[")) >= 0) {
                p1 = result.indexOf("]]", p0 + 2);
                if (p1 <= p0) break;
                kl = result.substring(p0 + 2, p1);

                // this is the part of the code that's responsible for images
                // contributed by [MN]
                if (kl.startsWith("Image:")) {
                    alt = "";
                    align = "";
                    kv = "";
                    kl = kl.substring(6);

                    // are there any arguments for the image?
                    if ((p = kl.indexOf("|")) > 0) {
                        kv = kl.substring(p + 1);
                        kl = kl.substring(0, p);

                        // if there are 2 arguments, write them into ALIGN and ALT
                        if ((p = kv.indexOf("|")) > 0) {
                            align = " align=\"" + kv.substring(0, p) + "\"";
                            alt = " alt=\"" + kv.substring(p + 1) + "\"";
                        }

                        // if there is just one, put it into ALT
                        else
                            alt = " alt=\"" + kv + "\"";
                    }

                    // replace incomplete URLs and make them point to http://peerip:port/...
                    // with this feature you can access an image in DATA/HTDOCS/share/yacy.gif
                    // using the wikicode [[Image:share/yacy.gif]]
                    // or an image DATA/HTDOCS/grafics/kaskelix.jpg with [[Image:grafics/kaskelix.jpg]]
                    // you are free to use other sub-paths of DATA/HTDOCS
                    if (!((kl.indexOf("://"))>=0)) {
                        kl = "http://" + yacyCore.seedDB.mySeed.getAddress().trim() + "/" + kl;
                    }

                    result = result.substring(0, p0) + "<img src=\"" + kl + "\"" + align.trim() + alt.trim() + ">" + result.substring(p1 + 2);
                }
                // end contrib [MN]

                // if it's no image, it might be an internal link
                else {
                    if ((p = kl.indexOf("|")) > 0) {
                        kv = kl.substring(p + 1);
                        kl = kl.substring(0, p);
                    } else {
                        kv = kl;
                    }
                    if (switchboard.wikiDB.read(kl) != null)
                        result = result.substring(0, p0) + "<a class=\"known\" href=\"Wiki.html?page=" + kl + "\">" + kv + "</a>" + result.substring(p1 + 2);
                    else
                        result = result.substring(0, p0) + "<a class=\"unknown\" href=\"Wiki.html?page=" + kl + "&edit=Edit\">" + kv + "</a>" + result.substring(p1 + 2);
                }
            }

            // external links
            while ((p0 = result.indexOf("[")) >= 0) {
                p1 = result.indexOf("]", p0 + 1);
                if (p1 <= p0) break;
                kl = result.substring(p0 + 1, p1);
                if ((p = kl.indexOf(" ")) > 0) {
                    kv = kl.substring(p + 1);
                    kl = kl.substring(0, p);
                }
                // No text for the link? -> <a href="http://www.url.com/">http://www.url.com/</a>
                else {
                    kv = kl;
                }
                // replace incomplete URLs and make them point to http://peerip:port/...
                // with this feature you can access a file at DATA/HTDOCS/share/page.html
                // using the wikicode [share/page.html]
                // or a file DATA/HTDOCS/www/page.html with [www/page.html]
                // you are free to use other sub-paths of DATA/HTDOCS
                if (!((kl.indexOf("://"))>=0)) {
                    kl = "http://" + yacyCore.seedDB.mySeed.getAddress().trim() + "/" + kl;
                }
                result = result.substring(0, p0) + "<a class=\"extern\" href=\"" + kl + "\">" + kv + "</a>" + result.substring(p1 + 1);
            }
        }

    //escape code ([=...=]) contributed by [MN]
    //both [= and =] in the same line
    else if(((p0 = result.indexOf("[="))>=0)&&((p1 = result.indexOf("=]"))>0)&&(!(preformatted))){
        //if(p0 < p1){
        String escapeText = result.substring(p0+2,p1);

        //BUGS TO BE FIXED: [=[=text=]=]  does not work properly:
        //[=[= undx=]x=] should resolve as [= undx=]x, but resolves as [= undxx=]
        //ALSO [=[= und =]=]   [= und =] leads to an exception
        //
        //handlicg cases where the text inside [= and =] also contains
        //[= and =]. Example: [=[=...=]=]
        //if(escapeText)

        //else{
            result = transformLine(result.substring(0,p0).replaceAll("!esc!", "!esc!!")+"!esc!txt!"+result.substring(p1+2).replaceAll("!esc!", "!esc!!"), switchboard);
            result = result.replaceAll("!esc!txt!", escapeText);
        result = result.replaceAll("!esc!!", "!esc!");
        //}
        //}
    }

    //start [=
    else if(((p0 = result.indexOf("[="))>=0)&&(!escapeSpan)&&(!preformatted)){
        escape = true;    //prevent surplus line breaks
        escaped = true;   //prevents <pre> being parsed
        String bq = "";   //gets filled with <blockquote>s as needed
        String escapeText = result.substring(p0+2);
        //taking care of indented lines
        while(result.substring(escindented,p0).startsWith(":")){
            escindented++;
            bq = bq + "<blockquote>";
        }
        result = transformLine(result.substring(escindented,p0).replaceAll("!esc!", "!esc!!")+"!esc!txt!", switchboard);
        result = bq + result.replaceAll("!esc!txt!", escapeText);
        escape = false;
        escapeSpan = true;
    }

    //end =]
    else if(((p0 = result.indexOf("=]"))>=0)&&(escapeSpan)&&(!preformatted)){
        escapeSpan = false;
        String bq = ""; //gets filled with </blockquote>s as neede
        String escapeText = result.substring(0,p0);
        //taking care of indented lines
        while(escindented > 0){
            bq = bq + "</blockquote>";
            escindented--;
        }
        result = transformLine("!esc!txt!"+result.substring(p0+2).replaceAll("!esc!", "!esc!!"), switchboard);
        result = result.replaceAll("!esc!txt!", escapeText) + bq;
        escaped = false;
    }
    //end contrib [MN]

    //preformatted code (<pre>...</pre>) contributed by [MN]
    //implementation very similar to escape code (see above)
    //both <pre> and </pre> in the same line
    else if(((p0 = result.indexOf("&lt;pre&gt;"))>=0)&&((p1 = result.indexOf("&lt;/pre&gt;"))>0)&&(!(escaped))){
        //if(p0 < p1){
            String preformattedText = "<pre style=\"border:dotted;border-width:thin\">"+result.substring(p0+11,p1)+"</pre>";
            result = transformLine(result.substring(0,p0).replaceAll("!pre!", "!pre!!")+"!pre!txt!"+result.substring(p1+12).replaceAll("!pre!", "!pre!!"), switchboard);
            result = result.replaceAll("!pre!txt!", preformattedText);
        result = result.replaceAll("!pre!!", "!pre!");
        //}
    }

    //start <pre>
    else if(((p0 = result.indexOf("&lt;pre&gt;"))>=0)&&(!preformattedSpan)&&(!escaped)){
        preformatted = true;    //prevent surplus line breaks
        String bq ="";  //gets filled with <blockquote>s as needed
        String preformattedText = "<pre style=\"border:dotted;border-width:thin\">"+result.substring(p0+11);
        //taking care of indented lines
        while(result.substring(preindented,p0).startsWith(":")){
            preindented++;
            bq = bq + "<blockquote>";
        }
        result = transformLine(result.substring(preindented,p0).replaceAll("!pre!", "!pre!!")+"!pre!txt!", switchboard);
        result = bq + result.replaceAll("!pre!txt!", preformattedText);
        result = result.replaceAll("!pre!!", "!pre!");
        preformattedSpan = true;
    }

    //end </pre>
    else if(((p0 = result.indexOf("&lt;/pre&gt;"))>=0)&&(preformattedSpan)&&(!escaped)){
        preformattedSpan = false;
        String bq = ""; //gets filled with </blockquote>s as needed
        String preformattedText = result.substring(0,p0)+"</pre>";
            //taking care of indented lines
        while (preindented > 0){
            bq = bq + "</blockquote>";
            preindented--;
        }
        result = transformLine("!pre!txt!"+result.substring(p0+12).replaceAll("!pre!", "!pre!!"), switchboard);
        result = result.replaceAll("!pre!txt!", preformattedText) + bq;
        result = result.replaceAll("!pre!!", "!pre!");
        preformatted = false;
    }
    //end contrib [MN]	

    replaced = false;
    if ((result.endsWith("</li>"))||(defList)||(escape)||(preformatted)) return result;
    return result + "<br>";
    }

    /*
      nice to have:
      || tables
    */

}
