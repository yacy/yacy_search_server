// yacyURL.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 13.07.2006 on http://yacy.net
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

package de.anomic.yacy;

// this class exsist to provide a system-wide normal form representation of urls,
// and to prevent that java.net.URL usage causes DNS queries which are used in java.net.

import java.io.File;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDomains;
import de.anomic.tools.Punycode;
import de.anomic.tools.Punycode.PunycodeException;

public class yacyURL {
    
    public static final int TLD_any_zone_filter = 255; // from TLD zones can be filtered during search; this is the catch-all filter
    
    public static String dummyHash;
    static {
        // create a dummy hash
        dummyHash = "";
        for (int i = 0; i < yacySeedDB.commonHashLength; i++) dummyHash += "-";
    }
    
    // class variables
    private String protocol, host, userInfo, path, quest, ref, hash;
    private int port;
    
    public yacyURL(String url, String hash) throws MalformedURLException {
        if (url == null) throw new MalformedURLException("url string is null");
        
        parseURLString(url);
        this.hash = hash;
    }
    
    private void parseURLString(String url) throws MalformedURLException {
        // identify protocol
        assert (url != null);
        url = url.trim();
        int p = url.indexOf(':');
        if (p < 0) {
            if (url.startsWith("www.")) {
                url = "http://" + url;
                p = 4;
            } else {
                throw new MalformedURLException("protocol is not given in '" + url + "'");
            }
        }
        this.protocol = url.substring(0, p).toLowerCase().trim();
        if (url.length() < p + 4) throw new MalformedURLException("URL not parseable: '" + url + "'");
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
            identPort(url, (protocol.equals("http") ? 80 : ((protocol.equals("https")) ? 443 : ((protocol.equals("ftp")) ? 21 : -1))));
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
        
        // handle international domains
        if (!Punycode.isBasic(host)) try {
            int d1 = host.lastIndexOf('.');
            if (d1 >= 0) {
                String tld = host.substring(d1 + 1);
                String dom = host.substring(0, d1);
                int d0 = dom.lastIndexOf('.');
                if (d0 >= 0) {
                    host = dom.substring(0, d0) + ".xn--" + Punycode.encode(dom.substring(d0 + 1)) + "." + tld;
                } else {
                    host = "xn--" + Punycode.encode(dom) + "." + tld;
                }
            }
        } catch (PunycodeException e) {}
    }

    public yacyURL(File file) throws MalformedURLException {
        this("file", "", -1, file.getAbsolutePath());
    }

    public static yacyURL newURL(String baseURL, String relPath) throws MalformedURLException {
        if ((baseURL == null) ||
            (relPath.startsWith("http://")) ||
            (relPath.startsWith("https://")) ||
            (relPath.startsWith("ftp://")) ||
            (relPath.startsWith("file://")) ||
            (relPath.startsWith("smb://"))) {
            return new yacyURL(relPath, null);
        } else {
            return new yacyURL(new yacyURL(baseURL, null), relPath);
        }
    }
    
    public static yacyURL newURL(yacyURL baseURL, String relPath) throws MalformedURLException {
        if ((baseURL == null) ||
            (relPath.startsWith("http://")) ||
            (relPath.startsWith("https://")) ||
            (relPath.startsWith("ftp://")) ||
            (relPath.startsWith("file://")) ||
            (relPath.startsWith("smb://"))) {
            return new yacyURL(relPath, null);
        } else {
            return new yacyURL(baseURL, relPath);
        }
    }
    
    private yacyURL(yacyURL baseURL, String relPath) throws MalformedURLException {
        if (baseURL == null) throw new MalformedURLException("base URL is null");
        if (relPath == null) throw new MalformedURLException("relPath is null");

        this.hash = null;
        this.protocol = baseURL.protocol;
        this.host = baseURL.host;
        this.port = baseURL.port;
        this.userInfo = baseURL.userInfo;
        if (relPath.toLowerCase().startsWith("javascript:")) {
            this.path = baseURL.path;
        } else if (
                (relPath.startsWith("http://")) ||
                (relPath.startsWith("https://")) ||
                (relPath.startsWith("ftp://")) ||
                (relPath.startsWith("file://")) ||
                (relPath.startsWith("smb://"))) {
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
    
    public yacyURL(String protocol, String host, int port, String path) throws MalformedURLException {
        if (protocol == null) throw new MalformedURLException("protocol is null");
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.path = path;
        this.hash = null;
        identRef();
        identQuest();
        escape();
    }

    //  resolve '..'
    String resolveBackpath(String path) /* throws MalformedURLException */ {
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

        Pattern pathPattern = Pattern.compile("(/[^/]+(?<!/\\.{1,2})/)[.]{2}(?=/|$)|/\\.(?=/)|/(?=/)");
        Matcher matcher = pathPattern.matcher(path);
        while (matcher.find()) {
            path = matcher.replaceAll("");
            matcher.reset(path);
        }
        
        return path.equals("")?"/":path;
    }
    
    /**
     * Escapes the following parts of the url, this object already contains:
     * <ul>
     * <li>path: see {@link #escape(String)}</li>
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
    
    private final static String[] hex = {
        "%00", "%01", "%02", "%03", "%04", "%05", "%06", "%07",
        "%08", "%09", "%0A", "%0B", "%0C", "%0D", "%0E", "%0F",
        "%10", "%11", "%12", "%13", "%14", "%15", "%16", "%17",
        "%18", "%19", "%1A", "%1B", "%1C", "%1D", "%1E", "%1F",
        "%20", "%21", "%22", "%23", "%24", "%25", "%26", "%27",
        "%28", "%29", "%2A", "%2B", "%2C", "%2D", "%2E", "%2F",
        "%30", "%31", "%32", "%33", "%34", "%35", "%36", "%37",
        "%38", "%39", "%3A", "%3B", "%3C", "%3D", "%3E", "%3F",
        "%40", "%41", "%42", "%43", "%44", "%45", "%46", "%47",
        "%48", "%49", "%4A", "%4B", "%4C", "%4D", "%4E", "%4F",
        "%50", "%51", "%52", "%53", "%54", "%55", "%56", "%57",
        "%58", "%59", "%5A", "%5B", "%5C", "%5D", "%5E", "%5F",
        "%60", "%61", "%62", "%63", "%64", "%65", "%66", "%67",
        "%68", "%69", "%6A", "%6B", "%6C", "%6D", "%6E", "%6F",
        "%70", "%71", "%72", "%73", "%74", "%75", "%76", "%77",
        "%78", "%79", "%7A", "%7B", "%7C", "%7D", "%7E", "%7F",
        "%80", "%81", "%82", "%83", "%84", "%85", "%86", "%87",
        "%88", "%89", "%8A", "%8B", "%8C", "%8D", "%8E", "%8F",
        "%90", "%91", "%92", "%93", "%94", "%95", "%96", "%97",
        "%98", "%99", "%9A", "%9B", "%9C", "%9D", "%9E", "%9F",
        "%A0", "%A1", "%A2", "%A3", "%A4", "%A5", "%A6", "%A7",
        "%A8", "%A9", "%AA", "%AB", "%AC", "%AD", "%AE", "%AF",
        "%B0", "%B1", "%B2", "%B3", "%B4", "%B5", "%B6", "%B7",
        "%B8", "%B9", "%BA", "%BB", "%BC", "%BD", "%BE", "%BF",
        "%C0", "%C1", "%C2", "%C3", "%C4", "%C5", "%C6", "%C7",
        "%C8", "%C9", "%CA", "%CB", "%CC", "%CD", "%CE", "%CF",
        "%D0", "%D1", "%D2", "%D3", "%D4", "%D5", "%D6", "%D7",
        "%D8", "%D9", "%DA", "%DB", "%DC", "%DD", "%DE", "%DF",
        "%E0", "%E1", "%E2", "%E3", "%E4", "%E5", "%E6", "%E7",
        "%E8", "%E9", "%EA", "%EB", "%EC", "%ED", "%EE", "%EF",
        "%F0", "%F1", "%F2", "%F3", "%F4", "%F5", "%F6", "%F7",
        "%F8", "%F9", "%FA", "%FB", "%FC", "%FD", "%FE", "%FF"
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
            } else if (ch == '&' || ch == ':'       // unreserved
                    || ch == '-' || ch == '_'
                    || ch == '.' || ch == '!'
                    || ch == '~' || ch == '*'
                    || ch == '\'' || ch == '('
                    || ch == ')' || ch == ';') {
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
    
    private void identPort(String inputURL, int dflt) throws MalformedURLException {
        // identify ref in file
        int r = this.host.indexOf(':');
        if (r < 0) {
            this.port = dflt;
        } else {            
            try {
                String portStr = this.host.substring(r + 1);
                if (portStr.trim().length() > 0) this.port = Integer.parseInt(portStr);
                else this.port =  -1;               
                this.host = this.host.substring(0, r);
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
        // if there is no quest and no ref the result is identical to getPath
        // this is defined according to http://java.sun.com/j2se/1.4.2/docs/api/java/net/URL.html#getFile()
        if (quest != null) return ((includeReference) && (ref != null)) ? path + "?" + quest + "#" + ref : path + "?" + quest;
        return ((includeReference) && (ref != null)) ? path + "#" + ref : path;
    }
    
    public String getFileName() {
        // this is a method not defined in any sun api
        // it returns the last portion of a path without any reference
        int p = path.lastIndexOf('/');
        if (p < 0) return path;
        if (p == path.length() - 1) return ""; // no file name, this is a path to a directory
        return path.substring(p + 1); // the 'real' file name
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

    public String toString() {
        return toNormalform(false, true);
    }
    
    public String toNormalform(boolean stripReference, boolean stripAmp) {
        if (stripAmp)
            return toNormalform(!stripReference).replaceAll("&amp;", "&");
        else
            return toNormalform(!stripReference);
    }
    
    private String toNormalform(boolean includeReference) {
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
    
    public boolean equals(yacyURL other) {
        return (((this.protocol == other.protocol) || (this.protocol.equals(other.protocol))) &&
                ((this.host     == other.host    ) || (this.host.equals(other.host))) &&
                ((this.userInfo == other.userInfo) || (this.userInfo.equals(other.userInfo))) &&
                ((this.path     == other.path    ) || (this.path.equals(other.path))) &&
                ((this.quest    == other.quest   ) || (this.quest.equals(other.quest))) &&
                ((this.ref      == other.ref     ) || (this.ref.equals(other.ref))) &&
                ((this.port     == other.port    )));
    }
    
    /**
     * hash code computation for yacyURL: please don't mix this up with the YaCy-Hash
     * this hash here is only used by hashing data structures, like a HashMap
     * We do not use tha yacy hash here, because this needs the computation of a DNS
     * which is very time-intensive
     */
    public int hashCode() {
        return this.toNormalform(true, false).hashCode();
    }
    
    public int compareTo(Object h) {
        assert (h instanceof yacyURL);
        return this.toString().compareTo(((yacyURL) h).toString());
    }
    
    public boolean isPOST() {
    	return (this.quest != null) && (this.quest.length() > 0);
    }

    public boolean isCGI() {
        String ls = path.toLowerCase();
        return ((ls.indexOf(".cgi") >= 0) ||
                (ls.indexOf(".exe") >= 0) ||
                (ls.indexOf(";jsessionid=") >= 0) ||
                (ls.indexOf("sessionid/") >= 0) ||
                (ls.indexOf("phpsessid=") >= 0) ||
                (ls.indexOf("search.php?sid=") >= 0) ||
                (ls.indexOf("memberlist.php?sid=") >= 0));
    }
    
    // static methods from plasmaURL

    public static final int flagTypeID(String hash) {
        return (kelondroBase64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 32) >> 5;
    }

    public static final int flagTLDID(String hash) {
        return (kelondroBase64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 28) >> 2;
    }

    public static final int flagLengthID(String hash) {
        return (kelondroBase64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 3);
    }

    public final String hash() {
        // in case that the object was initialized without a known url hash, compute it now
        if (this.hash == null) this.hash = urlHashComputation();
        return this.hash;
    }

    private final String urlHashComputation() {
        // the url hash computation needs a DNS lookup to check if the addresses domain is local
        // that causes that this method may be very slow
        
        assert this.hash == null; // should only be called if the hash was not computed bevore

        int id = serverDomains.getDomainID(this.host); // id=7: tld is local
        boolean isHTTP = this.protocol.equals("http");
        int p = this.host.lastIndexOf('.');
        String dom = (p > 0) ? dom = host.substring(0, p) : "";
        p = dom.lastIndexOf('.'); // locate subdomain
        String subdom = "";
        if (p > 0) {
            subdom = dom.substring(0, p);
            dom = dom.substring(p + 1);
        }
        
        // find rootpath
        String pathx = new String(this.path);
        if (pathx.startsWith("/"))
            pathx = pathx.substring(1);
        if (pathx.endsWith("/"))
            pathx = pathx.substring(0, pathx.length() - 1);
        p = pathx.indexOf('/');
        String rootpath = "";
        if (p > 0) {
            rootpath = pathx.substring(0, p);
        }

        // we collected enough information to compute the fragments that are
        // basis for hashes
        int l = dom.length();
        int domlengthKey = (l <= 8) ? 0 : (l <= 12) ? 1 : (l <= 16) ? 2 : 3;
        byte flagbyte = (byte) (((isHTTP) ? 0 : 32) | (id << 2) | domlengthKey);

        // combine the attributes
        StringBuffer hash = new StringBuffer(12);
        // form the 'local' part of the hash
        hash.append(kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(toNormalform(true, true))).substring(0, 5)); // 5 chars
        hash.append(subdomPortPath(subdom, port, rootpath)); // 1 char
        // form the 'global' part of the hash
        hash.append(protocolHostPort(this.protocol, host, port)); // 5 chars
        hash.append(kelondroBase64Order.enhancedCoder.encodeByte(flagbyte)); // 1 char

        // return result hash
        return new String(hash);
    }

    private static char subdomPortPath(String subdom, int port, String rootpath) {
        return kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(subdom + ":" + port + ":" + rootpath)).charAt(0);
    }

    private static final char rootURLFlag0 = subdomPortPath("", 80, "");
    private static final char rootURLFlag1 = subdomPortPath("www", 80, "");

    public static final boolean probablyRootURL(String urlHash) {
        return (urlHash.charAt(5) == rootURLFlag0) || (urlHash.charAt(5) == rootURLFlag1);
    }

    private static String protocolHostPort(String protocol, String host, int port) {
        return kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(protocol + ":" + host + ":" + port)).substring(0, 5);
    }

    private static String[] testTLDs = new String[] { "com", "net", "org", "uk", "fr", "de", "es", "it" };

    public static final yacyURL probablyWordURL(String urlHash, TreeSet<String> words) {
        Iterator<String> wi = words.iterator();
        String word;
        while (wi.hasNext()) {
            word = wi.next();
            if ((word == null) || (word.length() == 0)) continue;
            String pattern = urlHash.substring(6, 11);
            for (int i = 0; i < testTLDs.length; i++) {
                if (pattern.equals(protocolHostPort("http", "www." + word.toLowerCase() + "." + testTLDs[i], 80)))
                    try {
                        return new yacyURL("http://www." + word.toLowerCase() + "." + testTLDs[i], null);
                    } catch (MalformedURLException e) {
                        return null;
                    }
            }
        }
        return null;
    }

    public static final boolean isWordRootURL(String givenURLHash, TreeSet<String> words) {
        if (!(probablyRootURL(givenURLHash))) return false;
        yacyURL wordURL = probablyWordURL(givenURLHash, words);
        if (wordURL == null) return false;
        if (wordURL.hash().equals(givenURLHash)) return true;
        return false;
    }

    public static final int domLengthEstimation(String urlHash) {
        // generates an estimation of the original domain length
        assert (urlHash != null);
        assert (urlHash.length() == 12) : "urlhash = " + urlHash;
        int flagbyte = kelondroBase64Order.enhancedCoder.decodeByte(urlHash.charAt(11));
        int domLengthKey = flagbyte & 3;
        switch (domLengthKey) {
        case 0:
            return 4;
        case 1:
            return 10;
        case 2:
            return 14;
        case 3:
            return 20;
        }
        return 20;
    }

    public static int domLengthNormalized(String urlHash) {
        return domLengthEstimation(urlHash) << 8 / 20;
    }

    public static final int domDomain(String urlHash) {
        // returns the ID of the domain of the domain
        assert (urlHash != null);
        assert (urlHash.length() == 12) : "urlhash = " + urlHash;
        return (kelondroBase64Order.enhancedCoder.decodeByte(urlHash.charAt(11)) & 28) >> 2;
    }

    public static boolean isDomDomain(String urlHash, int id) {
        return domDomain(urlHash) == id;
    }
    
    public static boolean matchesAnyDomDomain(String urlHash, int idset) {
        // this is a boolean matching on a set of domDomains
        return (domDomain(urlHash) | idset) != 0;
    }
    
    // checks for local/global IP range and local IP
    public boolean isLocal() {
        if (this.hash == null) synchronized (this) {this.hash = urlHashComputation();}
        return domDomain(this.hash) == 7;
    }
    
    // language calculation
    public static String language(yacyURL url) {
        String language = "uk";
        String host = url.getHost();
        int pos = host.lastIndexOf(".");
        if ((pos > 0) && (host.length() - pos == 3)) language = host.substring(pos + 1).toLowerCase();
        return language;
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
          new String[]{"http://www.anomic.de","mailto:yacy@weltherrschaft.org"},
          new String[]{"http://www.anomic.de","javascipt:temp"},
          new String[]{null,"http://yacy-websuche.de/wiki/index.php?title=De:IntroInformationFreedom&action=history"},
          new String[]{null, "http://diskusjion.no/index.php?s=5bad5f431a106d9a8355429b81bb0ca5&showuser=23585"},
          new String[]{null, "http://diskusjion.no/index.php?s=5bad5f431a106d9a8355429b81bb0ca5&amp;showuser=23585"}
          };
        String environment, url;
        yacyURL aURL, aURL1;
        java.net.URL jURL;
        for (int i = 0; i < test.length; i++) {
            environment = test[i][0];
            url = test[i][1];
            try {aURL = yacyURL.newURL(environment, url);} catch (MalformedURLException e) {aURL = null;}
            if (environment == null) {
                try {jURL = new java.net.URL(url);} catch (MalformedURLException e) {jURL = null;}
            } else {
                try {jURL = new java.net.URL(new java.net.URL(environment), url);} catch (MalformedURLException e) {jURL = null;}
            }
            
            // check equality to java.net.URL
            if (((aURL == null) && (jURL != null)) ||
                ((aURL != null) && (jURL == null)) ||
                ((aURL != null) && (jURL != null) && (!(jURL.toString().equals(aURL.toString()))))) {
                System.out.println("Difference for environment=" + environment + ", url=" + url + ":");
                System.out.println((jURL == null) ? "jURL rejected input" : "jURL=" + jURL.toString());
                System.out.println((aURL == null) ? "aURL rejected input" : "aURL=" + aURL.toString());
            }
            
            // check stability: the normalform of the normalform must be equal to the normalform
            if (aURL != null) try {
                aURL1 = new yacyURL(aURL.toNormalform(false, true), null);
                if (!(aURL1.toNormalform(false, true).equals(aURL.toNormalform(false, true)))) {
                    System.out.println("no stability for url:");
                    System.out.println("aURL0=" + aURL.toString());
                    System.out.println("aURL1=" + aURL1.toString());
                }
            } catch (MalformedURLException e) {
                System.out.println("no stability for url:");
                System.out.println("aURL0=" + aURL.toString());
                System.out.println("aURL1 cannot be computed:" + e.getMessage());
            }
        }
    }
}
