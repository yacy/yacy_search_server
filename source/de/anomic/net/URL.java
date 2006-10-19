// URL.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 13.07.2006 on http://www.anomic.de
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
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

package de.anomic.net;

// this class exsist to provide a system-wide normal form representation of urls,
// and to prevent that java.net.URL usage causes DNS queries which are used in java.net.

import java.io.File;
import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URL {

    private String protocol, host, userInfo, path, quest, ref;
    private int port;
    
    public URL(String url) throws MalformedURLException {
        if (url == null) throw new MalformedURLException("url string is null");
        parseURLString(url);
    }
    
    public void parseURLString(String url) throws MalformedURLException {
        // identify protocol
        int p = url.indexOf(':');
        if (p < 0) throw new MalformedURLException("protocol is not given in '" + url + "'");
        this.protocol = url.substring(0, p).toLowerCase().trim();

        if (url.substring(p + 1, p + 3).equals("//")) {
            // identify host, userInfo and file for http and ftp protocol
            int q = url.indexOf('/', p + 3);
            int r;
            if (q < 0) {
                if ((r = url.indexOf('@', p + 3)) < 0) {
                    host = url.substring(p + 3);
                    userInfo = null;
                } else {
                    host = url.substring(r + 1);
                    userInfo = url.substring(p + 3, r);
                }
                path = "/";
            } else {
                host = url.substring(p + 3, q);
                if ((r = host.indexOf('@')) < 0) {
                    userInfo = null;
                } else {
                    userInfo = host.substring(0, r);
                    host = host.substring(r + 1);
                }
                path = url.substring(q);
            }
            
            path = resolveBackpath(path);
            identPort(url);
            identRef();
            identQuest();
            escape();
        } else {
            // this is not a http or ftp url
            if (protocol.equals("mailto")) {
                // parse email url
                int q = url.indexOf('@', p + 3);
                if (q < 0) {
                    throw new MalformedURLException("wrong email address: " + url);
                } else {
                    userInfo = url.substring(p + 1, q);
                    host = url.substring(q + 1);
                    path = null;
                    port = -1;
                    quest = null;
                    ref = null;
                }
            } else {
                throw new MalformedURLException("unknown protocol: " + url);
            }
        }
    }

    public URL(File file) throws MalformedURLException {
        this("file", "", -1, file.getAbsolutePath());
    }

    public URL(URL baseURL, String relPath) throws MalformedURLException {
        if (baseURL == null) throw new MalformedURLException("base URL is null");
        int p = relPath.indexOf(':');
        String relprotocol = (p < 0) ? null : relPath.substring(0, p).toLowerCase();
        if (relprotocol != null) {
            if ("http.https.ftp.mailto".indexOf(relprotocol) >= 0) {
                parseURLString(relPath);
            } else {
                throw new MalformedURLException("unknown protocol: " + relprotocol);
            }
        } else {
            this.protocol = baseURL.protocol;
            this.host = baseURL.host;
            this.port = baseURL.port;
            this.userInfo = baseURL.userInfo;
            if (relPath.toLowerCase().startsWith("javascript:")) {
                this.path = baseURL.path;
            } else if (relPath.startsWith("/")) {
                this.path = relPath;
            } else if (baseURL.path.endsWith("/")) {
                if (relPath.startsWith("#") || relPath.startsWith("?")) {
                    throw new MalformedURLException("relative path malformed: " + relPath);
                } else {
                    this.path = baseURL.path + relPath;
                }
            } else {
                if (relPath.startsWith("#") || relPath.startsWith("?")) {
                    this.path = baseURL.path + relPath;
                } else {
                    int q = baseURL.path.lastIndexOf('/');
                    if (q < 0) {
                        this.path = relPath;
                    } else {
                        this.path = baseURL.path.substring(0, q + 1) + relPath;
                    }
                }
            }
            this.quest = baseURL.quest;
            this.ref = baseURL.ref;

            path = resolveBackpath(path);
            identRef();
            identQuest();
            escape();
        }
    }
    
    public URL(String protocol, String host, int port, String path) throws MalformedURLException {
        if (protocol == null) throw new MalformedURLException("protocol is null");
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.path = path;
        identRef();
        identQuest();
        escape();
    }

    //  resolve '..'
    private String resolveBackpath(String path) /* throws MalformedURLException */ {
        /* original version by [MC]
        int p;
        while ((p = path.indexOf("/..")) >= 0) {
            String head = path.substring(0, p);
            int q = head.lastIndexOf('/');
            if (q < 0) throw new MalformedURLException("backpath cannot be resolved in path = " + path);
            path = head.substring(0, q) + path.substring(p + 3);
        }*/
        
        /* by [MT] */
        if (path.length() == 0 || path.charAt(0) != '/') { path = "/" + path; }

        Pattern pathPattern = Pattern.compile("(/[^/\\.]+/)[.]{2}(?=/)|/\\.(?=/)|/(?=/)");
        Matcher matcher = pathPattern.matcher(path);
        while (matcher.find()) {
            path = matcher.replaceAll("");
            matcher.reset(path);
        }
        
        /* another version at http://www.yacy-forum.de/viewtopic.php?p=26871#26871 */
        
        return path;
    }
    
    /**
     * Escapes the following parts of the url, this object already contains:
     * <ul>
     * <li>path: see {@link: escape(String)}</li>
     * <li>ref: same as above</li>
     * <li>quest: same as above without the ampersand ("&amp;") and the equals symbol</li>
     * </ul>
     */
    private void escape() {
        if (path != null && path.indexOf('%') == -1) escapePath();
        if (quest != null && quest.indexOf('%') == -1) escapeQuest();
        if (ref != null && ref.indexOf('%') == -1) escapeRef();
    }
    
    private void escapePath() {
        String[] pathp = path.split("/", -1);
        String ptmp = "";
        for (int i = 0; i < pathp.length; i++) {
            ptmp += "/" + escape(pathp[i]);
        }
        path = ptmp.substring((ptmp.length() > 0) ? 1 : 0);
    }
    
    private void escapeRef() {
        ref = escape(ref);
    }
    
    private void escapeQuest() {
        String[] questp = quest.split("&", -1);
        String qtmp = "";
        for (int i = 0; i < questp.length; i++) {
            if (questp[i].indexOf('=') != -1) {
                qtmp += "&" + escape(questp[i].substring(0, questp[i].indexOf('=')));
                qtmp += "=" + escape(questp[i].substring(questp[i].indexOf('=') + 1));
            } else {
                qtmp += "&" + escape(questp[i]);
            }
        }
        quest = qtmp.substring((qtmp.length() > 0) ? 1 : 0);
    }
    
    final static String[] hex = {
        "%00", "%01", "%02", "%03", "%04", "%05", "%06", "%07",
        "%08", "%09", "%0a", "%0b", "%0c", "%0d", "%0e", "%0f",
        "%10", "%11", "%12", "%13", "%14", "%15", "%16", "%17",
        "%18", "%19", "%1a", "%1b", "%1c", "%1d", "%1e", "%1f",
        "%20", "%21", "%22", "%23", "%24", "%25", "%26", "%27",
        "%28", "%29", "%2a", "%2b", "%2c", "%2d", "%2e", "%2f",
        "%30", "%31", "%32", "%33", "%34", "%35", "%36", "%37",
        "%38", "%39", "%3a", "%3b", "%3c", "%3d", "%3e", "%3f",
        "%40", "%41", "%42", "%43", "%44", "%45", "%46", "%47",
        "%48", "%49", "%4a", "%4b", "%4c", "%4d", "%4e", "%4f",
        "%50", "%51", "%52", "%53", "%54", "%55", "%56", "%57",
        "%58", "%59", "%5a", "%5b", "%5c", "%5d", "%5e", "%5f",
        "%60", "%61", "%62", "%63", "%64", "%65", "%66", "%67",
        "%68", "%69", "%6a", "%6b", "%6c", "%6d", "%6e", "%6f",
        "%70", "%71", "%72", "%73", "%74", "%75", "%76", "%77",
        "%78", "%79", "%7a", "%7b", "%7c", "%7d", "%7e", "%7f",
        "%80", "%81", "%82", "%83", "%84", "%85", "%86", "%87",
        "%88", "%89", "%8a", "%8b", "%8c", "%8d", "%8e", "%8f",
        "%90", "%91", "%92", "%93", "%94", "%95", "%96", "%97",
        "%98", "%99", "%9a", "%9b", "%9c", "%9d", "%9e", "%9f",
        "%a0", "%a1", "%a2", "%a3", "%a4", "%a5", "%a6", "%a7",
        "%a8", "%a9", "%aa", "%ab", "%ac", "%ad", "%ae", "%af",
        "%b0", "%b1", "%b2", "%b3", "%b4", "%b5", "%b6", "%b7",
        "%b8", "%b9", "%ba", "%bb", "%bc", "%bd", "%be", "%bf",
        "%c0", "%c1", "%c2", "%c3", "%c4", "%c5", "%c6", "%c7",
        "%c8", "%c9", "%ca", "%cb", "%cc", "%cd", "%ce", "%cf",
        "%d0", "%d1", "%d2", "%d3", "%d4", "%d5", "%d6", "%d7",
        "%d8", "%d9", "%da", "%db", "%dc", "%dd", "%de", "%df",
        "%e0", "%e1", "%e2", "%e3", "%e4", "%e5", "%e6", "%e7",
        "%e8", "%e9", "%ea", "%eb", "%ec", "%ed", "%ee", "%ef",
        "%f0", "%f1", "%f2", "%f3", "%f4", "%f5", "%f6", "%f7",
        "%f8", "%f9", "%fa", "%fb", "%fc", "%fd", "%fe", "%ff"
    };
    
    /**
     * Encode a string to the "x-www-form-urlencoded" form, enhanced
     * with the UTF-8-in-URL proposal. This is what happens:
     *
     * <ul>
     * <li>The ASCII characters 'a' through 'z', 'A' through 'Z',
     *     and '0' through '9' remain the same.
     *
     * <li>The unreserved characters - _ . ! ~ * ' ( ) remain the same.
     *
     * <li>All other ASCII characters are converted into the
     *     3-character string "%xy", where xy is
     *     the two-digit hexadecimal representation of the character
     *     code
     *
     * <li>All non-ASCII characters are encoded in two steps: first
     *     to a sequence of 2 or 3 bytes, using the UTF-8 algorithm;
     *     secondly each of these bytes is encoded as "%xx".
     * </ul>
     *
     * @param s The string to be encoded
     * @return The encoded string
     */
    // from: http://www.w3.org/International/URLUTF8Encoder.java
    public static String escape(String s)
    {
        StringBuffer sbuf = new StringBuffer();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            int ch = s.charAt(i);
            if ('A' <= ch && ch <= 'Z') {           // 'A'..'Z'
                sbuf.append((char)ch);
            } else if ('a' <= ch && ch <= 'z') {    // 'a'..'z'
                sbuf.append((char)ch);
            } else if ('0' <= ch && ch <= '9') {    // '0'..'9'
                sbuf.append((char)ch);
            } else if (ch == ' ') {                 // space
                sbuf.append("%20");
            } else if (ch == '-' || ch == '_'       // unreserved
                    || ch == '.' || ch == '!'
                    || ch == '~' || ch == '*'
                    || ch == '\'' || ch == '('
                    || ch == ')') {
                sbuf.append((char)ch);
            } else if (ch <= 0x007f) {              // other ASCII
                sbuf.append(hex[ch]);
            } else if (ch <= 0x07FF) {              // non-ASCII <= 0x7FF
                sbuf.append(hex[0xc0 | (ch >> 6)]);
                sbuf.append(hex[0x80 | (ch & 0x3F)]);
            } else {                                // 0x7FF < ch <= 0xFFFF
                sbuf.append(hex[0xe0 | (ch >> 12)]);
                sbuf.append(hex[0x80 | ((ch >> 6) & 0x3F)]);
                sbuf.append(hex[0x80 | (ch & 0x3F)]);
            }
        }
        return sbuf.toString();
    }
    
    // from: http://www.w3.org/International/unescape.java
    public static String unescape(String s) {
        StringBuffer sbuf = new StringBuffer();
        int l  = s.length();
        int ch = -1;
        int b, sumb = 0;
        for (int i = 0, more = -1; i < l; i++) {
            /* Get next byte b from URL segment s */
            switch (ch = s.charAt(i)) {
                case '%':
                    ch = s.charAt(++i) ;
                    int hb = (Character.isDigit ((char) ch) ? ch - '0' : 10 + Character.toLowerCase((char) ch) - 'a') & 0xF;
                    ch = s.charAt(++i) ;
                    int lb = (Character.isDigit ((char) ch) ? ch - '0' : 10 + Character.toLowerCase ((char) ch) - 'a') & 0xF;
                    b = (hb << 4) | lb;
                    break;
                case '+':
                    b = ' ';
                    break;
                default:
                    b = ch;
            }
            /* Decode byte b as UTF-8, sumb collects incomplete chars */
            if ((b & 0xc0) == 0x80) {               // 10xxxxxx (continuation byte)
                sumb = (sumb << 6) | (b & 0x3f) ;   // Add 6 bits to sumb
                if (--more == 0) sbuf.append((char) sumb) ; // Add char to sbuf
            } else if ((b & 0x80) == 0x00) {        // 0xxxxxxx (yields 7 bits)
                sbuf.append((char) b) ;             // Store in sbuf
            } else if ((b & 0xe0) == 0xc0) {        // 110xxxxx (yields 5 bits)
                sumb = b & 0x1f;
                more = 1;                           // Expect 1 more byte
            } else if ((b & 0xf0) == 0xe0) {        // 1110xxxx (yields 4 bits)
                sumb = b & 0x0f;
                more = 2;                           // Expect 2 more bytes
            } else if ((b & 0xf8) == 0xf0) {        // 11110xxx (yields 3 bits)
                sumb = b & 0x07;
                more = 3;                           // Expect 3 more bytes
            } else if ((b & 0xfc) == 0xf8) {        // 111110xx (yields 2 bits)
                sumb = b & 0x03;
                more = 4;                           // Expect 4 more bytes
            } else /*if ((b & 0xfe) == 0xfc)*/ {    // 1111110x (yields 1 bit)
                sumb = b & 0x01;
                more = 5;                           // Expect 5 more bytes
            }
            /* We don't test if the UTF-8 encoding is well-formed */
        }
        return sbuf.toString();
    }
    
    private void identPort(String inputURL) throws MalformedURLException {
        // identify ref in file
        int r = host.indexOf(':');
        if (r < 0) {
            this.port = -1;
        } else {
            try {
                this.port = Integer.parseInt(host.substring(r + 1));
                this.host = host.substring(0, r);
            } catch (NumberFormatException e) {
                throw new MalformedURLException("wrong port in host fragment '" + this.host + "' of input url '" + inputURL + "'");
            }
        }
    }
    
    private void identRef() {
        // identify ref in file
        int r = path.indexOf('#');
        if (r < 0) {
            this.ref = null;
        } else {
            this.ref = path.substring(r + 1);
            this.path = path.substring(0, r);
        }
    }
    
    private void identQuest() {
        // identify quest in file
        int r = path.indexOf('?');
        if (r < 0) {
            this.quest = null;
        } else {
            this.quest = path.substring(r + 1);
            this.path = path.substring(0, r);
        }
    }
    
    public String getFile() {
        return getFile(true);
    }
    
    public String getFile(boolean includeReference) {
        // this is the path plus quest plus ref
        if (quest != null) return ((includeReference) && (ref != null)) ? path + "?" + quest + "#" + ref : path + "?" + quest;
        return ((includeReference) && (ref != null)) ? path + "#" + ref : path;
    }

    public String getPath() {
        return path;
    }

    public String getAuthority() {
        return ((port >= 0) && (host != null)) ? host + ":" + port : ((host != null) ? host : "");
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getRef() {
        return ref;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public String getQuery() {
        return quest;
    }

    public String toNormalform() {
        return toString(false);
    }
    
    public String toString() {
        return toString(true);
    }
    
    public String toString(boolean includeReference) {
        // generates a normal form of the URL
        boolean defaultPort = false;
        if (this.protocol.equals("mailto")) {
            return this.protocol + ":" + this.userInfo + "@" + this.host;
        } else if (this.protocol.equals("http")) {
            if (this.port < 0 || this.port == 80)  { defaultPort = true; }
        } else if (this.protocol.equals("ftp")) {
            if (this.port < 0 || this.port == 21)  { defaultPort = true; }
        } else if (this.protocol.equals("https")) {
            if (this.port < 0 || this.port == 443) { defaultPort = true; }
        }
        String path = resolveBackpath(this.getFile(includeReference));
        
        if (defaultPort) {
            return this.protocol + "://" +
                   ((this.userInfo != null) ? (this.userInfo + "@") : ("")) +
                   this.getHost().toLowerCase() + path;
        }
        return this.protocol + "://" +
               ((this.userInfo != null) ? (this.userInfo + "@") : ("")) +
               this.getHost().toLowerCase() + ((defaultPort) ? ("") : (":" + this.port)) + path;
    }
    
    public boolean equals(URL other) {
        return (((this.protocol == other.protocol) || (this.protocol.equals(other.protocol))) &&
                ((this.host     == other.host    ) || (this.host.equals(other.host))) &&
                ((this.userInfo == other.userInfo) || (this.userInfo.equals(other.userInfo))) &&
                ((this.path     == other.path    ) || (this.path.equals(other.path))) &&
                ((this.quest    == other.quest   ) || (this.quest.equals(other.quest))) &&
                ((this.ref      == other.ref     ) || (this.ref.equals(other.ref))) &&
                ((this.port     == other.port    )));
    }
    
    public int hashCode() {
        return this.toString().hashCode();
    }
    
    public int compareTo(Object h) {
        assert (h instanceof URL);
        return this.toString().compareTo(((URL) h).toString());
    }
    
    public static void main(String[] args) {
        String[][] test = new String[][]{
          new String[]{null, "http://www.anomic.de/home/test?x=1#home"},
          new String[]{null, "http://www.anomic.de/home/test?x=1"},
          new String[]{null, "http://www.anomic.de/home/test#home"},
          new String[]{null, "ftp://ftp.anomic.de/home/test#home"},
          new String[]{null, "http://www.anomic.de/home/../abc/"},
          new String[]{null, "mailto:abcdefg@nomailnomail.com"},
          new String[]{"http://www.anomic.de/home", "test"},
          new String[]{"http://www.anomic.de/home", "test/"},
          new String[]{"http://www.anomic.de/home/", "test"},
          new String[]{"http://www.anomic.de/home/", "test/"},
          new String[]{"http://www.anomic.de/home/index.html", "test.htm"},
          new String[]{"http://www.anomic.de/home/index.html", "http://www.yacy.net/test"},
          new String[]{"http://www.anomic.de/home/index.html", "ftp://ftp.yacy.net/test"},
          new String[]{"http://www.anomic.de/home/index.html", "../test"},
          new String[]{"http://www.anomic.de/home/index.html", "mailto:abcdefg@nomailnomail.com"},
          new String[]{null, "news:de.test"},
          new String[]{"http://www.anomic.de/home", "news:de.test"},
          new String[]{"http://www.anomic.de/home", "ftp://ftp.anomic.de/src"},
          new String[]{null, "ftp://ftp.delegate.org/"},
          new String[]{"http://www.anomic.de/home", "ftp://ftp.delegate.org/"},
          new String[]{null,"http://www.bla.org/bli bla blo"},
          new String[]{null,"http://www.blubb.org/bli bla/ blo blubb/bla.html"},
          new String[]{null,"http://california-press-release.com/30/Hendrick Chevrolet, the renowned car dealer for Chevrolet in Cary, North Carolina (NC) announces the arrival of 2007 Chevrolet Cobalt SS  Coupe For further information, call Hendrick Chevrolet on (800)-857-4909.php"},
          new String[]{"http://california-press-release.com","/30/Hendrick%20Chevrolet%2c%20the%20renowned%20car%20dealer%20for%20Chevrolet%20in%20Cary%2c%20North%20Carolina%20(NC)%20announces%20the%20arrival%20of%202007%20Chevrolet%20Cobalt%20SS%20%20Coupe%20For%20further%20information%2c%20call%20Hendrick%20Chevrolet%20on%20(800)-857-4909.php"},
          new String[]{null, "http://www.anomic.de/home/test?x=1&täst=xyß#höme"},
          new String[]{null, "http://www.anomic.de/home/test?x&test=#"}
        };
        String environment, url;
        de.anomic.net.URL aURL = null;
        java.net.URL jURL = null;
        for (int i = 0; i < test.length; i++) {
            environment = test[i][0];
            url = test[i][1];
            if (environment == null) {
                try {aURL = new de.anomic.net.URL(url);} catch (MalformedURLException e) {aURL = null;}
                try {jURL = new java.net.URL(url);} catch (MalformedURLException e) {jURL = null;}
            } else {
                try {aURL = new de.anomic.net.URL(new de.anomic.net.URL(environment), url);} catch (MalformedURLException e) {aURL = null;}
                try {jURL = new java.net.URL(new java.net.URL(environment), url);} catch (MalformedURLException e) {jURL = null;}
            }
            if (((aURL == null) && (jURL != null)) ||
                ((aURL != null) && (jURL == null)) ||
                ((aURL != null) && (jURL != null) && (!(jURL.toString().equals(aURL.toString()))))) {
                System.out.println("Difference for environment=" + environment + ", url=" + url + ":");
                System.out.println((jURL == null) ? "jURL rejected input" : "jURL=" + jURL.toString());
                System.out.println((aURL == null) ? "aURL rejected input" : "aURL=" + aURL.toString());
                System.out.println((aURL == null || unescape(aURL.toString()) == null) ? "aURL rejected input" : "back=" + unescape(aURL.toString()));
            }
        }
    }
}
