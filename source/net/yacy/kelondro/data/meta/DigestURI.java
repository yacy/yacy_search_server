// yacyURL.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 13.07.2006 on http://yacy.net
//
// $LastChangedDate: 2009-10-10 01:22:22 +0200 (Sa, 10 Okt 2009) $
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.data.meta;

// this class exist to provide a system-wide normal form representation of urls,
// and to prevent that java.net.URL usage causes DNS queries which are used in java.net.

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.util.Domains;
import net.yacy.kelondro.util.Punycode;
import net.yacy.kelondro.util.Punycode.PunycodeException;


public class DigestURI implements Serializable {
    
    private static final long serialVersionUID = -1173233022912141884L;
    public  static final int TLD_any_zone_filter = 255; // from TLD zones can be filtered during search; this is the catch-all filter
    private static final Pattern backPathPattern = Pattern.compile("(/[^/]+(?<!/\\.{1,2})/)[.]{2}(?=/|$)|/\\.(?=/)|/(?=/)");
    private static final Pattern patternDot = Pattern.compile("\\.");
    private static final Pattern patternSlash = Pattern.compile("/");
    private static final Pattern patternAmp = Pattern.compile("&");
    private static final Pattern patternMail = Pattern.compile("^[a-z]+:.*?");
    
    // class variables
    private String protocol, host, userInfo, path, quest, ref, hash;
    private int port;
    
    public static String domhash(final String host) {
        String h = host;
        if (!h.startsWith("http://")) h = "http://" + h;
        DigestURI url = null;
        try {
            url = new DigestURI(h, null);
        } catch (MalformedURLException e) {
            Log.logException(e);
            return null;
        }
        return (url == null) ? null : url.hash().substring(6);
    }
    
    public DigestURI(final File file) throws MalformedURLException {
        this("file", "", -1, file.getAbsolutePath());
    }

    public DigestURI(final String url) throws MalformedURLException {
        this(url, null);
    }
    
    public DigestURI(final String url, final String hash) throws MalformedURLException {
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
            url = "http://" + url;
            p = 4;
        }
        this.protocol = url.substring(0, p).toLowerCase().trim();
        if (url.length() < p + 4) throw new MalformedURLException("URL not parseable: '" + url + "'");
        if (!this.protocol.equals("file") && url.substring(p + 1, p + 3).equals("//")) {
            // identify host, userInfo and file for http and ftp protocol
            final int q = url.indexOf('/', p + 3);
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
                host = url.substring(p + 3, q).trim();
                if ((r = host.indexOf('@')) < 0) {
                    userInfo = null;
                } else {
                    userInfo = host.substring(0, r);
                    host = host.substring(r + 1);
                }
                path = url.substring(q);
            }
            if (host.length() < 4 && !protocol.equals("file")) throw new MalformedURLException("host too short: '" + host + "'");
            if (host.indexOf('&') >= 0) throw new MalformedURLException("invalid '&' in host");
            path = resolveBackpath(path);
            identPort(url, (protocol.equals("http") ? 80 : ((protocol.equals("https")) ? 443 : ((protocol.equals("ftp")) ? 21 : -1))));
            identRef();
            identQuest();
            escape();
        } else {
            // this is not a http or ftp url
            if (protocol.equals("mailto")) {
                // parse email url
                final int q = url.indexOf('@', p + 3);
                if (q < 0) {
                    throw new MalformedURLException("wrong email address: " + url);
                }
                userInfo = url.substring(p + 1, q);
                host = url.substring(q + 1);
                path = null;
                port = -1;
                quest = null;
                ref = null;
            } if (protocol.equals("file")) {
                // parse file url
                String h = url.substring(p + 1);
                if (h.startsWith("//")) {
                    // host may be given, but may be also empty
                    final int q = h.indexOf('/', 2);
                    if (q <= 0) {
                        // no host given
                        host = null;
                        path = h.substring(2);
                    } else {
                        host = h.substring(2, q);
                        if (host.length() == 0 || host.equals("localhost")) host = null;
                        h = h.substring(q);
                        char c = h.charAt(2);
                        if (c == ':' || c == '|')
                            path = h.substring(1);
                        else
                            path = h;
                    }
                } else {
                    host = null;
                    if (h.length() > 0 && h.charAt(0) == '/') {
                        char c = h.charAt(2);
                        if (c == ':' || c == '|')
                            path = h.substring(1);
                        else
                            path = h;
                    } else {
                        char c = h.charAt(1);
                        if (c == ':' || c == '|')
                            path = h;
                        else
                            path = "/" + h;
                    }
                }
                userInfo = null;
                port = -1;
                quest = null;
                ref = null;
            } else {
                throw new MalformedURLException("unknown protocol: " + url);
            }
        }
        
        // handle international domains
        if (!Punycode.isBasic(host)) try {
            final String[] domainParts = patternDot.split(host, 0);
            StringBuilder buffer = new StringBuilder();
            // encode each domainpart seperately
            for(int i=0; i<domainParts.length; i++) {
            final String part = domainParts[i];
            if(!Punycode.isBasic(part)) {
                buffer.append("xn--" + Punycode.encode(part));
            } else {
                buffer.append(part);
            }
            if(i != domainParts.length-1) {
                buffer.append('.');
            }
            }
            host = buffer.toString();
        } catch (final PunycodeException e) {}
    }

    public static DigestURI newURL(final String baseURL, final String relPath) throws MalformedURLException {
        if ((baseURL == null) ||
            (relPath.startsWith("http://")) ||
            (relPath.startsWith("https://")) ||
            (relPath.startsWith("ftp://")) ||
            (relPath.startsWith("file://")) ||
            (relPath.startsWith("smb://")) /*||
            relPath.contains(":") && patternMail.matcher(relPath.toLowerCase()).find()*/) {
            return new DigestURI(relPath, null);
        }
        return new DigestURI(new DigestURI(baseURL, null), relPath);
    }
    
    public static DigestURI newURL(final DigestURI baseURL, final String relPath) throws MalformedURLException {
        if ((baseURL == null) ||
            (relPath.startsWith("http://")) ||
            (relPath.startsWith("https://")) ||
            (relPath.startsWith("ftp://")) ||
            (relPath.startsWith("file://")) ||
            (relPath.startsWith("smb://")) /*||
            relPath.contains(":") && patternMail.matcher(relPath.toLowerCase()).find()*/) {
            return new DigestURI(relPath, null);
        }
        return new DigestURI(baseURL, relPath);
    }
    
    private DigestURI(final DigestURI baseURL, String relPath) throws MalformedURLException {
        if (baseURL == null) throw new MalformedURLException("base URL is null");
        if (relPath == null) throw new MalformedURLException("relPath is null");

        this.hash = null;
        this.protocol = baseURL.protocol;
        this.host = baseURL.host;
        this.port = baseURL.port;
        this.userInfo = baseURL.userInfo;
        if (relPath.startsWith("//")) {
            // a "network-path reference" as defined in rfc2396 denotes
            // a relative path that uses the protocol from the base url
            relPath = baseURL.protocol + ":" + relPath;
        }
        if (relPath.toLowerCase().startsWith("javascript:")) {
            this.path = baseURL.path;
        } else if (
                (relPath.startsWith("http://")) ||
                (relPath.startsWith("https://")) ||
                (relPath.startsWith("ftp://")) ||
                (relPath.startsWith("file://")) ||
                (relPath.startsWith("smb://"))) {
            this.path = baseURL.path;
        } else if (relPath.contains(":") && patternMail.matcher(relPath.toLowerCase()).find()) { // discards also any unknown protocol from previous if
            throw new MalformedURLException("relative path malformed: " + relPath);
        } else if (relPath.length() > 0 && relPath.charAt(0) == '/') {
            this.path = relPath;
        } else if (baseURL.path.endsWith("/")) {
            if (relPath.length() > 0 && (relPath.charAt(0) == '#' || relPath.charAt(0) == '?')) {
                throw new MalformedURLException("relative path malformed: " + relPath);
            }
            this.path = baseURL.path + relPath;
        } else {
            if (relPath.length() > 0 && (relPath.charAt(0) == '#' || relPath.charAt(0) == '?')) {
                this.path = baseURL.path + relPath;
            } else {
                final int q = baseURL.path.lastIndexOf('/');
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
    
    public DigestURI(final String protocol, final String host, final int port, final String path) throws MalformedURLException {
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
    public String resolveBackpath(final String path) {
        String p = path;

        /* original version by [MC]
        int p;
        while ((p = path.indexOf("/..")) >= 0) {
            String head = path.substring(0, p);
            int q = head.lastIndexOf('/');
            if (q < 0) throw new MalformedURLException("backpath cannot be resolved in path = " + path);
            path = head.substring(0, q) + path.substring(p + 3);
        }*/
        
        /* by [MT] */
        if (p.length() == 0 || p.charAt(0) != '/') { p = "/" + p; }

        final Matcher matcher = backPathPattern.matcher(p);
        while (matcher.find()) {
            p = matcher.replaceAll("");
            matcher.reset(p);
        }
        
        return p.equals("")?"/":p;
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
        final String[] pathp = patternSlash.split(path, -1);
        StringBuilder ptmp = new StringBuilder(path.length() + 10);
        for (int i = 0; i < pathp.length; i++) {
            ptmp.append('/');
            ptmp.append(escape(pathp[i]));
        }
        path = ptmp.substring((ptmp.length() > 0) ? 1 : 0);
    }
    
    private void escapeRef() {
        ref = escape(ref).toString();
    }
    
    private void escapeQuest() {
        final String[] questp = patternAmp.split(quest, -1);
        StringBuilder qtmp = new StringBuilder(quest.length() + 10);
        for (int i = 0; i < questp.length; i++) {
            if (questp[i].indexOf('=') != -1) {
                qtmp.append('&');
                qtmp.append(escape(questp[i].substring(0, questp[i].indexOf('='))));
                qtmp.append('=');
                qtmp.append(escape(questp[i].substring(questp[i].indexOf('=') + 1)));
            } else {
                qtmp.append('&');
                qtmp.append(escape(questp[i]));
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
    public static StringBuilder escape(final String s) {
        final int len = s.length();
        final StringBuilder sbuf = new StringBuilder(len + 10);
        for (int i = 0; i < len; i++) {
            final int ch = s.charAt(i);
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
            } else if (ch == '/') {                 // reserved, but may appear in post part where it should not be replaced
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
        return sbuf;
    }

    // from: http://www.w3.org/International/unescape.java
    public static String unescape(final String s) {
        final int l  = s.length();
        final StringBuilder sbuf = new StringBuilder(l);
        int ch = -1;
        int b, sumb = 0;
        for (int i = 0, more = -1; i < l; i++) {
            /* Get next byte b from URL segment s */
            switch (ch = s.charAt(i)) {
                case '%':
                    if (i + 2 < l) {
                        ch = s.charAt(++i);
                        int hb = (Character.isDigit ((char) ch) ? ch - '0' : 10 + Character.toLowerCase((char) ch) - 'a') & 0xF;
                        ch = s.charAt(++i);
                        int lb = (Character.isDigit ((char) ch) ? ch - '0' : 10 + Character.toLowerCase ((char) ch) - 'a') & 0xF;
                        b = (hb << 4) | lb;
                    } else {
                        b = ch;
                    }
                    break;
                case '+':
                    b = ' ';
                    break;
                default:
                    b = ch;
            }
            /* Decode byte b as UTF-8, sumb collects incomplete chars */
            if ((b & 0xc0) == 0x80) {               // 10xxxxxx (continuation byte)
                sumb = (sumb << 6) | (b & 0x3f);    // Add 6 bits to sumb
                if (--more == 0) sbuf.append((char) sumb); // Add char to sbuf
            } else if ((b & 0x80) == 0x00) {        // 0xxxxxxx (yields 7 bits)
                sbuf.append((char) b);              // Store in sbuf
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

    private void identPort(final String inputURL, final int dflt) throws MalformedURLException {
        // identify ref in file
        final int r = this.host.indexOf(':');
        if (r < 0) {
            this.port = dflt;
        } else {            
            try {
                final String portStr = this.host.substring(r + 1);
                if (portStr.trim().length() > 0) this.port = Integer.parseInt(portStr);
                else this.port =  -1;               
                this.host = this.host.substring(0, r);
            } catch (final NumberFormatException e) {
                throw new MalformedURLException("wrong port in host fragment '" + this.host + "' of input url '" + inputURL + "'");
            }
        }
    }
    
    private void identRef() {
        // identify ref in file
        final int r = path.indexOf('#');
        if (r < 0) {
            this.ref = null;
        } else {
            this.ref = path.substring(r + 1);
            this.path = path.substring(0, r);
        }
    }
    
    private void identQuest() {
        // identify quest in file
        final int r = path.indexOf('?');
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
    
    public String getFile(final boolean includeReference) {
        // this is the path plus quest plus ref
        // if there is no quest and no ref the result is identical to getPath
        // this is defined according to http://java.sun.com/j2se/1.4.2/docs/api/java/net/URL.html#getFile()
        if (quest != null) return ((includeReference) && (ref != null)) ? path + "?" + quest + "#" + ref : path + "?" + quest;
        return ((includeReference) && (ref != null)) ? path + "#" + ref : path;
    }
    
    public String getFileName() {
        // this is a method not defined in any sun api
        // it returns the last portion of a path without any reference
        final int p = path.lastIndexOf('/');
        if (p < 0) return path;
        if (p == path.length() - 1) return ""; // no file name, this is a path to a directory
        return path.substring(p + 1); // the 'real' file name
    }

    public String getFileExtension() {
        String name = getFileName();
        int p = name.lastIndexOf('.');
        if (p < 0) return "";
        return name.substring(p + 1);
    }
    
    public String getPath() {
        return path;
    }

    /**
     * return the file object to a local file
     * this patches also 'strange' windows file paths
     * @return the file as absolute path
     */
    public File getLocalFile() {
        char c = path.charAt(1);
        if (c == ':') return new File(path.replace('/', '\\'));
        if (c == '|') return new File(path.charAt(0) + ":" + path.substring(2).replace('/', '\\'));
        c = path.charAt(2);
        if (c == ':' || c == '|') return new File(path.charAt(1) + ":" + path.substring(3).replace('/', '\\'));
        return new File(path);
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

    public void removeRef() {
        ref = null;
    }
    
    public String getUserInfo() {
        return userInfo;
    }

    public String getQuery() {
        return quest;
    }

    @Override
    public String toString() {
        return toNormalform(false, true);
    }
    
    public String toNormalform(final boolean stripReference, final boolean stripAmp) {
        String result = toNormalform(!stripReference); 
        if (stripAmp) {
            result = result.replaceAll("&amp;", "&");
        }
        return result;
    }
    
    private String toNormalform(final boolean includeReference) {
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
        } else if (this.protocol.equals("file")) {
            defaultPort = true;
        }
        final String path = this.getFile(includeReference);
        
        if (defaultPort) {
            return
              this.protocol + ":" +
              ((this.getHost() == null) ? "" : "//" + ((this.userInfo != null) ? (this.userInfo + "@") : ("")) + this.getHost().toLowerCase()) +
              path;
        }
        return this.protocol + "://" +
               ((this.userInfo != null) ? (this.userInfo + "@") : ("")) +
               this.getHost().toLowerCase() + ((defaultPort) ? ("") : (":" + this.port)) + path;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return this.hash().hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof DigestURI)) return false;
        DigestURI other = (DigestURI) obj;
        return this.toString().equals(other.toString());
    }

    public int compareTo(final Object h) {
        assert (h instanceof DigestURI);
        return this.toString().compareTo(((DigestURI) h).toString());
    }
    
    public boolean isPOST() {
        return (this.quest != null) && (this.quest.length() > 0);
    }

    public final boolean isCGI() {
        final String ls = unescape(path.toLowerCase());
        return ls.indexOf(".cgi") >= 0 ||
               ls.indexOf(".exe") >= 0;
    }
    
    public final boolean isIndividual() {
        final String ls = unescape(path.toLowerCase());
        int pos;
        return
               ((pos = ls.indexOf("sid")) > 0 &&
                (ls.charAt(--pos) == '?' || ls.charAt(pos) == '&' || ls.charAt(pos) == ';') &&
                (pos += 5) < ls.length() &&
                (ls.charAt(pos) != '&' && ls.charAt(--pos) == '=')
                ) ||

               ((pos = ls.indexOf("sessionid")) > 0 &&
                (pos += 10) < ls.length() &&
                (ls.charAt(pos) != '&' &&
                 (ls.charAt(--pos) == '=' || ls.charAt(pos) == '/'))
                ) ||

               ((pos = ls.indexOf("phpsessid")) > 0 &&
                (pos += 10) < ls.length() &&
                (ls.charAt(pos) != '&' &&
                 (ls.charAt(--pos) == '=' || ls.charAt(pos) == '/')));
    }


    // static methods from plasmaURL

    public static final int flagTypeID(final String hash) {
        return (Base64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 32) >> 5;
    }

    public static final int flagTLDID(final String hash) {
        return (Base64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 28) >> 2;
    }

    public static final int flagLengthID(final String hash) {
        return (Base64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 3);
    }

    public final String hash() {
        // in case that the object was initialized without a known url hash, compute it now
        synchronized (this) {
            if (this.hash == null) this.hash = urlHashComputation();
        }
        return this.hash;
    }

    private final String urlHashComputation() {
        // the url hash computation needs a DNS lookup to check if the addresses domain is local
        // that causes that this method may be very slow
        
        assert this.hash == null; // should only be called if the hash was not computed before

        final int id = Domains.getDomainID(this.host); // id=7: tld is local
        final boolean isHTTP = this.protocol.equals("http");
        int p = (host == null) ? -1 : this.host.lastIndexOf('.');
        String dom = (p > 0) ? dom = host.substring(0, p) : "";
        p = dom.lastIndexOf('.'); // locate subdomain
        String subdom = "";
        if (p > 0) {
            subdom = dom.substring(0, p);
            dom = dom.substring(p + 1);
        }
        
        // find rootpath
        int rootpathStart = 0;
        int rootpathEnd = this.path.length() - 1;
        if (this.path.length() > 0 && this.path.charAt(0) == '/')
            rootpathStart = 1;
        if (this.path.endsWith("/"))
            rootpathEnd = this.path.length() - 2;
        p = this.path.indexOf('/', rootpathStart);
        String rootpath = "";
        if (p > 0 && p < rootpathEnd) {
            rootpath = path.substring(rootpathStart, p);
        }

        // we collected enough information to compute the fragments that are
        // basis for hashes
        final int l = dom.length();
        final int domlengthKey = (l <= 8) ? 0 : (l <= 12) ? 1 : (l <= 16) ? 2 : 3;
        final byte flagbyte = (byte) (((isHTTP) ? 0 : 32) | (id << 2) | domlengthKey);

        // combine the attributes
        final StringBuilder hash = new StringBuilder(12);
        // form the 'local' part of the hash
        String normalform = toNormalform(true, true);
        String b64l = Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(normalform));
        if (b64l.length() < 5) return null;
        hash.append(b64l.substring(0, 5)); // 5 chars
        hash.append(subdomPortPath(subdom, port, rootpath)); // 1 char
        // form the 'global' part of the hash
        hash.append(hosthash5(this.protocol, host, port)); // 5 chars
        hash.append(Base64Order.enhancedCoder.encodeByte(flagbyte)); // 1 char

        // return result hash
        return hash.toString();
    }
    
    private static char subdomPortPath(final String subdom, final int port, final String rootpath) {
        return Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(subdom + ":" + port + ":" + rootpath)).charAt(0);
    }

    private static final char rootURLFlag0 = subdomPortPath("", 80, "");
    private static final char rootURLFlag1 = subdomPortPath("www", 80, "");

    public static final boolean probablyRootURL(final String urlHash) {
        return (urlHash.charAt(5) == rootURLFlag0) || (urlHash.charAt(5) == rootURLFlag1);
    }

    private static final String hosthash5(final String protocol, final String host, final int port) {
        return Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(protocol + ((host == null) ? "" : (":" + host + ":" + port)))).substring(0, 5);
    }
    
    /**
     * compute a 6-byte hash fragment that can be used to identify the domain of the url
     * @param protocol
     * @param host
     * @param port
     * @return 6 bytes base64 encoded String representing the domain of the url
     */
    public static final String hosthash6(final String protocol, final String host, final int port) {
        final StringBuilder hash = new StringBuilder(12);
        final int id = Domains.getDomainID(host); // id=7: tld is local
        int p = host.lastIndexOf('.');
        String dom = (p > 0) ? dom = host.substring(0, p) : "";
        p = dom.lastIndexOf('.');
        if (p > 0) dom = dom.substring(p + 1);
        final int l = dom.length();
        final int domlengthKey = (l <= 8) ? 0 : (l <= 12) ? 1 : (l <= 16) ? 2 : 3;
        final byte flagbyte = (byte) (((protocol.equals("http")) ? 0 : 32) | (id << 2) | domlengthKey);
        hash.append(hosthash5(protocol, host, port)); // 5 chars
        hash.append(Base64Order.enhancedCoder.encodeByte(flagbyte)); // 1 char

        // return result hash
        return hash.toString();
    }

    public static final String hosthash6(final String host) {
        return hosthash6("http", host, 80);
    }
    
    private static String[] testTLDs = new String[] { "com", "net", "org", "uk", "fr", "de", "es", "it" };

    public static final DigestURI probablyWordURL(final String urlHash, final TreeSet<String> words) {
        final Iterator<String> wi = words.iterator();
        String word;
        while (wi.hasNext()) {
            word = wi.next();
            if ((word == null) || (word.length() == 0)) continue;
            final String pattern = urlHash.substring(6, 11);
            for (int i = 0; i < testTLDs.length; i++) {
                if (pattern.equals(hosthash5("http", "www." + word.toLowerCase() + "." + testTLDs[i], 80)))
                    try {
                        return new DigestURI("http://www." + word.toLowerCase() + "." + testTLDs[i], null);
                    } catch (final MalformedURLException e) {
                        return null;
                    }
            }
        }
        return null;
    }

    public static final boolean isWordRootURL(final String givenURLHash, final TreeSet<String> words) {
        if (!(probablyRootURL(givenURLHash))) return false;
        final DigestURI wordURL = probablyWordURL(givenURLHash, words);
        if (wordURL == null) return false;
        if (wordURL.hash().equals(givenURLHash)) return true;
        return false;
    }

    public static final int domLengthEstimation(final String urlHash) {
        // generates an estimation of the original domain length
        assert (urlHash != null);
        assert (urlHash.length() == 12) : "urlhash = " + urlHash;
        final int flagbyte = Base64Order.enhancedCoder.decodeByte(urlHash.charAt(11));
        final int domLengthKey = flagbyte & 3;
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

    public static int domLengthNormalized(final String urlHash) {
        return domLengthEstimation(urlHash) << 8 / 20;
    }

    public static final int domDomain(final String urlHash) {
        // returns the ID of the domain of the domain
        assert (urlHash != null);
        assert (urlHash.length() == 12 || urlHash.length() == 6) : "urlhash = " + urlHash;
        return (Base64Order.enhancedCoder.decodeByte(urlHash.charAt((urlHash.length() == 12) ? 11 : 5)) & 28) >> 2;
    }


    public static boolean isDomDomain(final String urlHash, final int id) {
        return domDomain(urlHash) == id;
    }
    
    public static boolean matchesAnyDomDomain(final String urlHash, final int idset) {
        // this is a boolean matching on a set of domDomains
        return (domDomain(urlHash) | idset) != 0;
    }

    // checks for local/global IP range and local IP
    public final boolean isLocal() {
        if (this.hash == null) {
            if (this.host.startsWith("127.") || this.host.equals("localhost") || this.host.startsWith("0:0:0:0:0:0:0:1")) return true;
            synchronized (this) {
                if (this.hash == null) this.hash = urlHashComputation();
            }
        }
        //if (domDomain(this.hash) != 7) System.out.println("*** DEBUG - not local: " + this.toNormalform(true, false));
        return domDomain(this.hash) == 7;
    }

    public static final boolean isLocal(final String urlhash) {
        return domDomain(urlhash) == 7;
    }

    // language calculation
    public final String language() {
        String language = "en";
        if (host == null) return language;
        final int pos = host.lastIndexOf('.');
        if (pos > 0 && host.length() - pos == 3) language = host.substring(pos + 1).toLowerCase();
        if (language.equals("uk")) language = "en";
        return language;
    }

    private static final String splitrex = " |/|\\(|\\)|-|\\:|_|\\.|,|\\?|!|'|" + '"';
    public static final Pattern splitpattern = Pattern.compile(splitrex);
    public static String[] urlComps(String normalizedURL) {
        final int p = normalizedURL.indexOf("//");
        if (p > 0) normalizedURL = normalizedURL.substring(p + 2);
        return splitpattern.split(normalizedURL.toLowerCase()); // word components of the url
    }

    public static void main(final String[] args) {
        final String[][] test = new String[][]{
          new String[]{null, "file://C:WINDOWS\\CMD0.EXE"},
          new String[]{null, "file:/bin/yacy1"}, // file://<host>/<path> may have many '/' if the host is omitted and the path starts with '/'
          new String[]{null, "file:///bin/yacy2"}, // file://<host>/<path> may have many '/' if the host is omitted and the path starts with '/'
          new String[]{null, "file:C:WINDOWS\\CMD.EXE"},
          new String[]{null, "file:///C:WINDOWS\\CMD1.EXE"},
          new String[]{null, "file:///C|WINDOWS\\CMD2.EXE"},
          new String[]{null, "http://www.anomic.de/test/"},
          new String[]{null, "http://www.anomic.de/"},
          new String[]{null, "http://www.anomic.de"},
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
          new String[]{null, "mailto:bob@web.com"},
          new String[]{"http://www.anomic.de/home", "mailto:bob@web.com"},
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
        DigestURI aURL, aURL1;
        java.net.URL jURL;
        for (int i = 0; i < test.length; i++) {
            environment = test[i][0];
            url = test[i][1];
            try {aURL = DigestURI.newURL(environment, url);} catch (final MalformedURLException e) {Log.logException(e); aURL = null;}
            if (aURL != null) System.out.println("normalized: " + aURL.toNormalform(true, true));
            if (environment == null) {
                try {jURL = new java.net.URL(url);} catch (final MalformedURLException e) {jURL = null;}
            } else {
                try {jURL = new java.net.URL(new java.net.URL(environment), url);} catch (final MalformedURLException e) {jURL = null;}
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
                aURL1 = new DigestURI(aURL.toNormalform(false, true), null);
                if (!(aURL1.toNormalform(false, true).equals(aURL.toNormalform(false, true)))) {
                    System.out.println("no stability for url:");
                    System.out.println("aURL0=" + aURL.toString());
                    System.out.println("aURL1=" + aURL1.toString());
                }
            } catch (final MalformedURLException e) {
                System.out.println("no stability for url:");
                System.out.println("aURL0=" + aURL.toString());
                System.out.println("aURL1 cannot be computed:" + e.getMessage());
            }
        }
    }
}
