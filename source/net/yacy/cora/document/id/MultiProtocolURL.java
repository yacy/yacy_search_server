/**
 *  MultiProtocolURI
 *  Copyright 2010 by Michael Peter Christen
 *  First released 25.5.2010 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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


package net.yacy.cora.document.id;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import net.yacy.cora.document.analysis.Classification;
import net.yacy.cora.document.analysis.Classification.ContentDomain;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.Punycode.PunycodeException;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.TimeoutRequest;
import net.yacy.cora.protocol.ftp.FTPClient;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.parser.html.CharacterCoding;

/**
 * MultiProtocolURI provides a URL object for multiple protocols like http, https, ftp, smb and file
 *
 */
public class MultiProtocolURL implements Serializable, Comparable<MultiProtocolURL> {

    public static final MultiProtocolURL POISON = new MultiProtocolURL(); // poison pill for concurrent link generators

    private static final long serialVersionUID = -1173233022912141884L;
    private static final long SMB_TIMEOUT = 5000;

    public  static final int TLD_any_zone_filter = 255; // from TLD zones can be filtered during search; this is the catch-all filter
    private static final Pattern backPathPattern = Pattern.compile("(/[^/]+(?<!/\\.{1,2})/)[.]{2}(?=/|$)|/\\.(?=/)|/(?=/)");
    private static final Pattern patternMail = Pattern.compile("^[a-z]+:.*?");
    //private static final Pattern patternSpace = Pattern.compile("%20");

    // session id handling
    private static final Object PRESENT = new Object();
    private static final ConcurrentHashMap<String, Object> sessionIDnames = new ConcurrentHashMap<String, Object>();

    public static final void initSessionIDNames(final Set<String> idNames) {
        for (String s: idNames) {
            if (s == null) continue;
            s = s.trim();
            if (!s.isEmpty()) sessionIDnames.put(s, PRESENT);
        }
    }

    // class variables
    protected final String protocol, userInfo;
    protected       String host, path, searchpart, anchor;
    protected       int port;
    protected       InetAddress hostAddress;
    protected       ContentDomain contentDomain;

    /**
     * initialization of a MultiProtocolURI to produce poison pills for concurrent blocking queues
     */
    public MultiProtocolURL()  {
        this.protocol = null;
        this.host = null;
        this.hostAddress = null;
        this.userInfo = null;
        this.path = null;
        this.searchpart = null;
        this.anchor = null;
        this.contentDomain = null;
        this.port = -1;
    }

    public MultiProtocolURL(final File file) throws MalformedURLException {
        this("file", "", -1, file.getAbsolutePath());
    }

    protected MultiProtocolURL(final MultiProtocolURL url) {
        this.protocol = url.protocol;
        this.host = url.host;
        this.hostAddress = null;
        this.userInfo = url.userInfo;
        this.path = url.path;
        this.searchpart = url.searchpart;
        this.anchor = url.anchor;
        this.contentDomain = null;
        this.port = url.port;
    }

    public MultiProtocolURL(String url) throws MalformedURLException {
        if (url == null) throw new MalformedURLException("url string is null");

        this.hostAddress = null;
        this.contentDomain = null;

        // identify protocol
        assert (url != null);
        url = url.trim();
        url = UTF8.decodeURL(url); // normalization here
        //url = patternSpace.matcher(url).replaceAll(" ");
        if (url.startsWith("//")) {
            // patch for urls starting with "//" which can be found in the wild
            url = "http:" + url;
        }
        if (url.startsWith("\\\\")) {
            url = "smb://" + CommonPattern.BACKSLASH.matcher(url.substring(2)).replaceAll("/");
        }

        if (url.length() > 1 && url.charAt(1) == ':') {
            // maybe a DOS drive path
            url = "file://" + url;
        }

        if (url.length() > 0 && url.charAt(0) == '/') {
            // maybe a unix/linux absolute path
            url = "file://" + url;
        }

        int p = url.indexOf(':');
        if (p < 0) {
            url = "http://" + url;
            p = 4;
        }
        this.protocol = url.substring(0, p).toLowerCase().trim().intern();
        if (url.length() < p + 4) throw new MalformedURLException("URL not parseable: '" + url + "'");
        if (!this.protocol.equals("file") && url.substring(p + 1, p + 3).equals("//")) {
            // identify host, userInfo and file for http and ftp protocol
            int q = url.indexOf('/', p + 3);
            if (q < 0) q = url.indexOf("?", p + 3); // check for www.test.com?searchpart
            int r;
            if (q < 0) {
                if ((r = url.indexOf('@', p + 3)) < 0) {
                    this.host = url.substring(p + 3).intern();
                    this.userInfo = null;
                } else {
                    this.host = url.substring(r + 1).intern();
                    this.userInfo = url.substring(p + 3, r);
                }
                this.path = "/";
            } else {
                this.host = url.substring(p + 3, q).trim().intern();
                if ((r = this.host.indexOf('@')) < 0) {
                    this.userInfo = null;
                } else {
                    this.userInfo = this.host.substring(0, r);
                    this.host = this.host.substring(r + 1).intern();
                }
                this.path = url.substring(q); // may result in "?searchpart" (resolveBackpath prepends a "/" )
            }
            if (this.host.length() < 4 && !this.protocol.equals("file")) throw new MalformedURLException("host too short: '" + this.host + "', url = " + url);
            if (this.host.indexOf('&') >= 0) throw new MalformedURLException("invalid '&' in host");
            this.path = resolveBackpath(this.path); // adds "/" if missing
            identPort(url, (isHTTP() ? 80 : (isHTTPS() ? 443 : (isFTP() ? 21 : (isSMB() ? 445 : -1)))));
            if (this.port < 0) { // none of known protocols (above) = unknown
                throw new MalformedURLException("unknown protocol: " + url);
            }
            identAnchor();
            identSearchpart();
            escape();
        } else {
            // this is not a http or ftp url
            if (this.protocol.equals("mailto")) {
                // parse email url
                final int q = url.indexOf('@', p + 3);
                if (q < 0) {
                    throw new MalformedURLException("wrong email address: " + url);
                }
                this.userInfo = url.substring(p + 1, q);
                this.host = url.substring(q + 1);
                this.path = null;
                this.port = -1;
                this.searchpart = null;
                this.anchor = null;
            } else if (this.protocol.equals("file")) {
                // parse file url
                final String h = url.substring(p + 1);
                if (h.startsWith("//")) {
                    // no host given
                    this.host = null;
                    this.path = h.substring(2);
                } else {
                    this.host = null;
                    if (h.length() > 0 && h.charAt(0) == '/') {
                        final char c = h.charAt(2);
                        if (c == ':' || c == '|')
                            this.path = h.substring(1);
                        else
                            this.path = h;
                    } else {
                        final char c = h.charAt(1);
                        if (c == ':' || c == '|')
                            this.path = h;
                        else
                            this.path = "/" + h;
                    }
                }
                this.userInfo = null;
                this.port = -1;
                this.searchpart = null;
                this.anchor = null;
            } else {
                throw new MalformedURLException("unknown protocol: " + url);
            }
        }

        // handle international domains
        if (!Punycode.isBasic(this.host)) try {
            this.host = toPunycode(this.host);
        } catch (final PunycodeException e) {}
    }

    public static String toPunycode(final String host) throws PunycodeException {
        final String[] domainParts = CommonPattern.DOT.split(host, 0);
        final StringBuilder buffer = new StringBuilder(80);
        // encode each domain-part separately
        for(int i = 0; i < domainParts.length; i++) {
            final String part = domainParts[i];
            if (!Punycode.isBasic(part)) {
                buffer.append("xn--").append(Punycode.encode(part));
            } else {
                buffer.append(part);
            }
            if (i != domainParts.length-1) {
                buffer.append('.');
            }
        }
        return buffer.toString();
    }

    public static final boolean isHTTP(final String s) { return s.startsWith("http://"); }
    public static final boolean isHTTPS(final String s) { return s.startsWith("https://"); }
    public static final boolean isFTP(final String s) { return s.startsWith("ftp://"); }
    public static final boolean isFile(final String s) { return s.startsWith("file://"); }
    public static final boolean isSMB(final String s) { return s.startsWith("smb://") || s.startsWith("\\\\"); }

    public final boolean isHTTP()  { return this.protocol.equals("http"); }
    public final boolean isHTTPS() { return this.protocol.equals("https"); }
    public final boolean isFTP()   { return this.protocol.equals("ftp"); }
    public final boolean isFile()  { return this.protocol.equals("file"); }
    public final boolean isSMB()   { return this.protocol.equals("smb"); }

    /**
     * Get the content domain of a document according to the extension.
     * This can produce wrong results because the extension is a weak hint for the content domain.
     * If possible, use the mime type, call Classification.getContentDomainFromMime()
     * @return the content domain which classifies the content type
     */
    public final ContentDomain getContentDomainFromExt() {
        if (this.contentDomain == null) {
            this.contentDomain = Classification.getContentDomainFromExt(getFileExtension(this.getFileName()));
        }
        return this.contentDomain;
    }

    public static MultiProtocolURL newURL(final String baseURL, String relPath) throws MalformedURLException {
        if (relPath.startsWith("//")) {
            // patch for urls starting with "//" which can be found in the wild
            relPath = "http:" + relPath;
        }
        if ((baseURL == null) ||
            isHTTP(relPath) ||
            isHTTPS(relPath) ||
            isFTP(relPath) ||
            isFile(relPath) ||
            isSMB(relPath)/*||
            relPath.contains(":") && patternMail.matcher(relPath.toLowerCase()).find()*/) {
            return new MultiProtocolURL(relPath);
        }
        return new MultiProtocolURL(new MultiProtocolURL(baseURL), relPath);
    }

    public static MultiProtocolURL newURL(final MultiProtocolURL baseURL, String relPath) throws MalformedURLException {
        if (relPath.startsWith("//")) {
            // patch for urls starting with "//" which can be found in the wild
            relPath = (baseURL == null) ? "http:" + relPath : baseURL.getProtocol() + ":" + relPath;
        }
        if ((baseURL == null) ||
            isHTTP(relPath) ||
            isHTTPS(relPath) ||
            isFTP(relPath) ||
            isFile(relPath) ||
            isSMB(relPath)/*||
            relPath.contains(":") && patternMail.matcher(relPath.toLowerCase()).find()*/) {
            return new MultiProtocolURL(relPath);
        }
        return new MultiProtocolURL(baseURL, relPath);
    }

    public MultiProtocolURL(final MultiProtocolURL baseURL, String relPath) throws MalformedURLException {
        if (baseURL == null) throw new MalformedURLException("base URL is null");
        if (relPath == null) throw new MalformedURLException("relPath is null");

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
                isHTTP(relPath) ||
                isHTTPS(relPath) ||
                isFTP(relPath) ||
                isFile(relPath) ||
                isSMB(relPath)) {
            this.path = baseURL.path;
        } else if (relPath.contains(":") && patternMail.matcher(relPath.toLowerCase()).find()) { // discards also any unknown protocol from previous if
            throw new MalformedURLException("relative path malformed: " + relPath);
        } else if (relPath.length() > 0 && relPath.charAt(0) == '/') {
            this.path = relPath;
        } else if (baseURL.path.endsWith("/")) {
            if (relPath.length() > 0 && (relPath.charAt(0) == '#' || relPath.charAt(0) == '?')) {
                throw new MalformedURLException("relative path malformed: " + relPath);
            }
            if (relPath.startsWith("/")) this.path = baseURL.path + relPath.substring(1); else this.path = baseURL.path + relPath;
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
        this.searchpart = baseURL.searchpart;
        this.anchor = baseURL.anchor;

        this.path = resolveBackpath(this.path);
        identAnchor();
        identSearchpart();
        escape();
    }

    public MultiProtocolURL(final String protocol, String host, final int port, final String path) throws MalformedURLException {
        if (protocol == null) throw new MalformedURLException("protocol is null");
        if (host.indexOf(':') >= 0 && host.charAt(0) != '[') host = '[' + host + ']'; // IPv6 host must be enclosed in square brackets
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.path = path;
        this.searchpart = null;
        this.userInfo = null;
        this.anchor = null;
        identAnchor();
        identSearchpart();
        escape();
    }

    //  resolve '..'
    private static final String resolveBackpath(final String path) {
        String p = path;
        if (p.isEmpty() || p.charAt(0) != '/') { p = "/" + p; }
        final Matcher qm = CommonPattern.QUESTION.matcher(p); // do not resolve backpaths in the post values
        final int end = qm.find() ? qm.start() : p.length();
        final Matcher matcher = backPathPattern.matcher(p);
        while (matcher.find()) {
            if (matcher.start() > end) break;
            p = matcher.replaceAll("");
            matcher.reset(p);
        }
        return p.equals("") ? "/" : p;
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
        if (this.path != null && this.path.indexOf('%') == -1) escapePath();
        if (this.searchpart != null && this.searchpart.indexOf('%') == -1) escapeSearchpart();
        if (this.anchor != null && this.anchor.indexOf('%') == -1) escapeAnchor();
    }

    private void escapePath() {
        final String[] pathp = CommonPattern.SLASH.split(this.path, -1);
        final StringBuilder ptmp = new StringBuilder(this.path.length() + 10);
        for (final String element : pathp) {
            ptmp.append('/');
            ptmp.append(escape(element));
        }
        this.path = ptmp.substring((ptmp.length() > 0) ? 1 : 0);
    }

    private void escapeAnchor() {
        this.anchor = escape(this.anchor).toString();
    }

    private void escapeSearchpart() {
        final String[] questp = CommonPattern.AMP.split(this.searchpart, -1);
        final StringBuilder qtmp = new StringBuilder(this.searchpart.length() + 10);
        for (final String element : questp) {
            if (element.indexOf('=') != -1) {
                qtmp.append('&');
                qtmp.append(escape(element.substring(0, element.indexOf('='))));
                qtmp.append('=');
                qtmp.append(escape(element.substring(element.indexOf('=') + 1)));
            } else {
                qtmp.append('&');
                qtmp.append(escape(element));
            }
        }
        this.searchpart = qtmp.substring((qtmp.length() > 0) ? 1 : 0);
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
                        final int hb = (Character.isDigit ((char) ch) ? ch - '0' : 10 + Character.toLowerCase((char) ch) - 'a') & 0xF;
                        ch = s.charAt(++i);
                        final int lb = (Character.isDigit ((char) ch) ? ch - '0' : 10 + Character.toLowerCase ((char) ch) - 'a') & 0xF;
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
        if (this.host == null) {
            this.port = dflt;
            return;
        }
        int pss = 0;
        int ip6 = this.host.indexOf('[');
        if (ip6 >= 0 && ((ip6 = this.host.indexOf("]", ip6)) > 0)) {
            pss = ip6 + 1;
        }
        final int r = this.host.indexOf(":", pss);
        if (r < 0) {
            this.port = dflt;
        } else {
            try {
                final String portStr = this.host.substring(r + 1);
                if (portStr.trim().length() > 0) this.port = Integer.parseInt(portStr);
                else this.port =  dflt;
                this.host = this.host.substring(0, r);
            } catch (final NumberFormatException e) {
                throw new MalformedURLException("wrong port in host fragment '" + this.host + "' of input url '" + inputURL + "'");
            }
        }
    }

    private void identAnchor() {
        // identify ref in file
        final int r = this.path.indexOf('#');
        if (r < 0) {
            this.anchor = null;
        } else {
            this.anchor = this.path.substring(r + 1);
            this.path = this.path.substring(0, r);
        }
    }

    private void identSearchpart() {
        // identify quest in file
        final int r = this.path.indexOf('?');
        if (r < 0) {
            this.searchpart = null;
        } else {
            this.searchpart = this.path.substring(r + 1);
            // strip &amp;
            Matcher matcher = CharacterCoding.ampPattern.matcher(this.searchpart);
            while (matcher.find()) {
                this.searchpart = matcher.replaceAll("&");
                matcher.reset(this.searchpart);
            }
            this.path = this.path.substring(0, r);
        }
    }

    /**
     * get the hpath plus search field plus anchor.
     * see http://www.ietf.org/rfc/rfc1738.txt for naming.
     * if there is no search and no anchor the result is identical to getPath
     * this is defined according to http://docs.oracle.com/javase/1.4.2/docs/api/java/net/URL.html#getFile()
     * @return
     */
    public String getFile() {
        return getFile(false, false);
    }

    /**
     * get the hpath plus search field plus anchor (if wanted)
     * see http://www.ietf.org/rfc/rfc1738.txt for naming.
     * if there is no search and no anchor the result is identical to getPath
     * this is defined according to http://docs.oracle.com/javase/1.4.2/docs/api/java/net/URL.html#getFile()
     * @param excludeAnchor
     * @param removeSessionID
     * @return
     */
    public String getFile(final boolean excludeAnchor, final boolean removeSessionID) {
        if (this.searchpart == null) {
            if (excludeAnchor || this.anchor == null) return this.path;
            final StringBuilder sb = new StringBuilder(120);
            sb.append(this.path);
            sb.append('#');
            sb.append(this.anchor);
            return sb.toString();
        }
        String q = this.searchpart;
        if (removeSessionID) {
            for (final String sid: sessionIDnames.keySet()) {
                if (q.toLowerCase().startsWith(sid.toLowerCase() + "=")) {
                    final int p = q.indexOf('&');
                    if (p < 0) {
                        if (excludeAnchor || this.anchor == null) return this.path;
                        final StringBuilder sb = new StringBuilder(120);
                        sb.append(this.path);
                        sb.append('#');
                        sb.append(this.anchor);
                        return sb.toString();
                    }
                    q = q.substring(p + 1);
                    continue;
                }
                final int p = q.toLowerCase().indexOf("&" + sid.toLowerCase() + "=",0);
                if (p < 0) continue;
                final int p1 = q.indexOf('&', p+1);
                if (p1 < 0) {
                    q = q.substring(0, p);
                } else {
                    q = q.substring(0, p) + q.substring(p1);
                }
            }
        }
        final StringBuilder sb = new StringBuilder(120);
        sb.append(this.path);
        sb.append('?');
        sb.append(q);
        if (excludeAnchor || this.anchor == null) return sb.toString();
        sb.append('#');
        sb.append(this.anchor);
        return sb.toString();
    }

    public String getFileName() {
        // this is a method not defined in any sun api
        // it returns the last portion of a path without any reference
        final int p = this.path.lastIndexOf('/');
        if (p < 0) return this.path;
        if (p == this.path.length() - 1) return ""; // no file name, this is a path to a directory
        return this.path.substring(p + 1); // the 'real' file name
    }

    public static String getFileExtension(final String fileName) {
        final int p = fileName.lastIndexOf('.');
        if (p < 0) return "";
        return fileName.substring(p + 1).toLowerCase();
    }

    public String getPath() {
        return this.path;
    }

    public String[] getPaths() {
        String s = this.path == null ? "" : this.path.charAt(0) == '/' ? this.path.substring(1) : this.path;
        int p = s.lastIndexOf('/');
        if (p < 0) return new String[0];
        s = s.substring(0, p); // the paths do not contain the last part, which is considered as the getFileName() part.
        String[] paths = CommonPattern.SLASH.split(s);
        return paths;
    }

    /**
     * return the file object to a local file
     * this patches also 'strange' windows file paths
     * @return the file as absolute path
     */
    public File getLocalFile() {
        char c = this.path.charAt(1);
        if (c == ':') return new File(this.path.replace('/', '\\'));
        if (c == '|') return new File(this.path.charAt(0) + ":" + this.path.substring(2).replace('/', '\\'));
        c = this.path.charAt(2);
        if (c == ':' || c == '|') return new File(this.path.charAt(1) + ":" + this.path.substring(3).replace('/', '\\'));
        return new File(this.path);
    }

    public String getAuthority() {
        return ((this.port >= 0) && (this.host != null)) ? this.host + ":" + this.port : ((this.host != null) ? this.host : "");
    }

    public String getHost() {
        if (this.host == null) return null;
        if (this.host.length() > 0 && this.host.charAt(0) == '[') {
            int p = this.host.indexOf(']');
            if (p < 0) return this.host;
            return this.host.substring(1, p);
        }
        return this.host;
    }
    
    public String getOrganization() {
        String dnc = Domains.getDNC(host);
        String subdomOrga = host.length() - dnc.length() <= 0 ? "" : host.substring(0, host.length() - dnc.length() - 1);
        int p = subdomOrga.lastIndexOf('.');
        String orga = (p < 0) ? subdomOrga : subdomOrga.substring(p + 1);
        return orga;
    }

    public String getTLD() {
        if (this.host == null) return "";
        int p = this.host.lastIndexOf('.');
        if (p < 0) return "";
        return this.host.substring(p + 1);
    }

    public InetAddress getInetAddress() {
        if (this.hostAddress != null) return this.hostAddress;
        if (this.host == null) return null; // this may happen for file:// urls
        this.hostAddress = Domains.dnsResolve(this.host.toLowerCase());
        return this.hostAddress;
    }

    public int getPort() {
        return this.port;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public String getRef() {
        return this.anchor;
    }

    public void removeRef() {
        this.anchor = null;
    }

    /**
     * the userInfo is the authentication part in front of the host; separated by '@'
     * @return a string like '<user>:<password>' or just '<user>'
     */
    public String getUserInfo() {
        return this.userInfo;
    }

    public String getSearchpart() {
        return this.searchpart;
    }

    public Map<String, String> getSearchpartMap() {
        if (this.searchpart == null) return null;
        this.searchpart = this.searchpart.replaceAll("&amp;", "&");
        String[] parts = CommonPattern.AMP.split(this.searchpart);
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (String part: parts) {
            int p = part.indexOf('=');
            if (p > 0) map.put(part.substring(0, p), part.substring(p + 1)); else map.put(part, "");
        }
        return map;
    }

    @Override
    public String toString() {
        return toNormalform(false);
    }

    public String toTokens() {
        return toTokens(unescape(this.toNormalform(true)));
    }

    /**
     * create word tokens for parser. Find CamelCases and separate these words
     * resulting words are not ordered by appearance, but all
     * @return
     */
    public static String toTokens(final String s) {
        // remove all non-character & non-number
        final StringBuilder sb = new StringBuilder(s.length());
        char c;
        for (int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            if ((c >= '0' && c <='9') || (c >= 'a' && c <='z') || (c >= 'A' && c <='Z')) sb.append(c); else sb.append(' ');
        }

        String t = sb.toString();

        // remove all double-spaces
        int p;
        while ((p = t.indexOf("  ",0)) >= 0) t = t.substring(0, p) + t.substring(p + 1);

        // split the string into tokens and add all camel-case splitting
        final String[] u = CommonPattern.SPACE.split(t);
        final Set<String> token = new LinkedHashSet<String>();
        for (final String r: u) token.add(r);
        for (final String r: u) token.addAll(parseCamelCase(r));

        // construct a String again
        sb.setLength(0);
        for (final String v: token) if (v.length() > 1) sb.append(v).append(' ');
        return sb.length() == 0 ? "" : sb.substring(0, sb.length() - 1);
    }

    public static enum CharType { low, high, number; }

    private static Set<String> parseCamelCase(String s) {
        final Set<String> token = new LinkedHashSet<String>();
        if (s.isEmpty()) return token;
        int p = 0;
        CharType type = charType(s.charAt(0)), nct = type;
        while (p < s.length()) {
            // search for first appearance of an character that is a upper-case
            while (p < s.length() && (nct = charType(s.charAt(p))) == type) p++;
            if (p >= s.length()) { token.add(s); break; }
            if (nct == CharType.low) {
                type = CharType.low;
                p++; continue;
            }

            // the char type has changed
            token.add(s.substring(0, p));
            s = s.substring(p);
            p = 0;
            type = nct;
        }
        token.add(s);
        return token;
    }

    private static CharType charType(final char c) {
        if (c >= 'a' && c <= 'z') return CharType.low;
        if (c >= '0' && c <= '9') return CharType.number;
        return CharType.high;
    }

    public String toNormalform(final boolean excludeAnchor) {
        return toNormalform(excludeAnchor, false);
    }

    public String toNormalform(final boolean excludeAnchor, final boolean removeSessionID) {
        // generates a normal form of the URL
        boolean defaultPort = false;
        if (this.protocol.equals("mailto")) {
            return this.protocol + ":" + this.userInfo + "@" + this.host;
        } else if (isHTTP()) {
            if (this.port < 0 || this.port == 80)  { defaultPort = true; }
        } else if (isHTTPS()) {
            if (this.port < 0 || this.port == 443) { defaultPort = true; }
        } else if (isFTP()) {
            if (this.port < 0 || this.port == 21)  { defaultPort = true; }
        } else if (isSMB()) {
            if (this.port < 0 || this.port == 445)  { defaultPort = true; }
        } else if (isFile()) {
            defaultPort = true;
        }
        String urlPath = this.getFile(excludeAnchor, removeSessionID);
        String h = getHost();
        final StringBuilder u = new StringBuilder(20 + urlPath.length() + ((h == null) ? 0 : h.length()));
        u.append(this.protocol);
        u.append("://");
        if (h != null) {
            if (this.userInfo != null && !(this.isFTP() && this.userInfo.startsWith(FTPClient.ANONYMOUS))) {
                u.append(this.userInfo);
                u.append("@");
            }
            u.append(h.toLowerCase());
        }
        if (!defaultPort) {
            u.append(":");
            u.append(this.port);
        }
        u.append(urlPath);
        String result = u.toString();
        
        return result;
    }

    @Override
    public int hashCode() {
        return
            (this.protocol == null ? 0 : this.protocol.hashCode() >> 2) +
            (this.host == null ? 0 : this.host.hashCode() >> 2) +
            (this.userInfo == null ? 0 : this.userInfo.hashCode() >> 2) +
            (this.path == null ? 0 : this.path.hashCode() >> 2) +
            (this.searchpart == null ? 0 : this.searchpart.hashCode() >> 2) +
            this.port;
        //return this.toNormalform(true).hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof MultiProtocolURL)) return false;
        final MultiProtocolURL other = (MultiProtocolURL) obj;

        return
          ((this.protocol == null && other.protocol == null) || (this.protocol != null && other.protocol != null && this.protocol.equals(other.protocol))) &&
          ((this.host == null && other.host == null) || (this.host != null && other.host != null && this.host.equals(other.host))) &&
          ((this.userInfo == null && other.userInfo == null) || (this.userInfo != null && other.userInfo != null && this.userInfo.equals(other.userInfo))) &&
          ((this.path == null && other.path == null) || (this.path != null && other.path != null && this.path.equals(other.path))) &&
          ((this.searchpart == null && other.searchpart == null) || (this.searchpart != null && other.searchpart != null && this.searchpart.equals(other.searchpart))) &&
          this.port == other.port;
    }

    @Override
    public int compareTo(final MultiProtocolURL h) {
        int c;
        if (this.protocol != null && h.protocol != null && (c = this.protocol.compareTo(h.protocol)) != 0) return c;
        if (this.host != null && h.host != null && (c = this.host.compareTo(h.host)) != 0) return c;
        if (this.userInfo != null && h.userInfo != null && (c = this.userInfo.compareTo(h.userInfo)) != 0) return c;
        if (this.path != null && h.path != null && (c = this.path.compareTo(h.path)) != 0) return c;
        if (this.searchpart != null && h.searchpart != null && (c = this.searchpart.compareTo(h.searchpart)) != 0) return c;
        return toNormalform(true).compareTo(h.toNormalform(true));
    }

    public boolean isPOST() {
        return (this.searchpart != null) && (this.searchpart.length() > 0);
    }

    public static final boolean isCGI(final String extension) {
        return extension != null && extension.length() > 0 && "cgi.exe".indexOf(extension.toLowerCase()) >= 0;
    }

    public static final boolean isImage(final String extension) {
        return extension != null && extension.length() > 0 && "png.gif.jpg.jpeg.tif.tiff.ico".indexOf(extension.toLowerCase()) >= 0;
    }

    public final boolean isIndividual() {
        final String q = unescape(this.path.toLowerCase());
        for (final String sid: sessionIDnames.keySet()) {
            if (q.startsWith(sid.toLowerCase() + "=")) return true;
            final int p = q.indexOf("&" + sid.toLowerCase() + "=",0);
            if (p >= 0) return true;
        }
        int pos;
        return
               ((pos = q.indexOf("sid",0)) > 0 &&
                (q.charAt(--pos) == '?' || q.charAt(pos) == '&' || q.charAt(pos) == ';') &&
                (pos += 5) < q.length() &&
                (q.charAt(pos) != '&' && q.charAt(--pos) == '=')
                ) ||

               ((pos = q.indexOf("sessionid",0)) > 0 &&
                (pos += 10) < q.length() &&
                (q.charAt(pos) != '&' &&
                 (q.charAt(--pos) == '=' || q.charAt(pos) == '/'))
                ) ||

               ((pos = q.indexOf("phpsessid",0)) > 0 &&
                (pos += 10) < q.length() &&
                (q.charAt(pos) != '&' &&
                 (q.charAt(--pos) == '=' || q.charAt(pos) == '/')));
    }

    // checks for local/global IP range and local IP
    public boolean isLocal() {
        return this.isFile() || this.isSMB() || Domains.isLocal(this.host, this.hostAddress);
    }

    // language calculation
    //modified by copperdust; Ukraine, 2012
    public final String language() {
        String language = "en";
        if (this.host == null) return language;
        final int pos = this.host.lastIndexOf('.');
        String host_tld = this.host.substring(pos + 1).toLowerCase();
        if (pos == 0) return language;
        int length = this.host.length() - pos - 1;
        switch (length) {
	        case 2:
	        	char firstletter = host_tld.charAt(0);
	        	switch (firstletter) {//speed-up
	        	case 'a':
	        		if (host_tld.equals("au")) {//Australia /91,000,000
			        	language = "en";//australian english; eng; eng; ause
			        } else if (host_tld.equals("at")) {//Austria /23,000,000
			        	language = "de";//german; ger (deu); deu
			        } else if (host_tld.equals("ar")) {//Argentina /10,700,000
			        	language = "es";//spanish
			        } else if (host_tld.equals("ae")) {//United Arab Emirates /3,310,000
			        	language = "ar";//arabic
			        } else if (host_tld.equals("am")) {//Armenia /2,080,000
			        	language = "hy";//armenian; arm (hye); hye
			        } else if (host_tld.equals("ac")) {//Ascension Island /2,060,000
			        	language = "en";//english
			        } else if (host_tld.equals("az")) {//Azerbaijan /1,340,000
			        	language = "az";//azerbaijani; aze; aze (azj, azb)
			        } else if (host_tld.equals("ag")) {//Antigua and Barbuda /1,310,000
			        	language = "en";//english
			        } else if (host_tld.equals("as")) {//American Samoa /1,220,000
			        	language = "en";//english
			        } else if (host_tld.equals("al")) {//Albania /389,000
			        	language = "sq";//albanian; alb (sqi); sqi
	        		} else if (host_tld.equals("ad")) {//Andorra /321,000
			        	language = "ca";//catalan; cat
			        } else if (host_tld.equals("ao")) {//Angola /153,000
			        	language = "pt";//portuguese
			        } else if (host_tld.equals("ai")) {//Anguilla /149,000
			        	language = "en";//english
			        } else if (host_tld.equals("af")) {//Afghanistan /101,000
			        	language = "ps";//pashto; pus
			        } else if (host_tld.equals("an")) {//Netherlands Antilles /78,100
			        	language = "nl";//dutch
			        } else if (host_tld.equals("aq")) {//Antarctica /36,000
			        	language = "en";//can be any
			        } else if (host_tld.equals("aw")) {//Aruba /34,400
			        	language = "nl";//dutch
			        } else if (host_tld.equals("ax")) {//Aland Islands /28
			        	language = "sv";//swedish
			        }
	        		break;
				case 'b':
					if (host_tld.equals("br")) {//Brazil /25,800,000
						language = "pt";//portuguese
			        } else if (host_tld.equals("be")) {//Belgium /25,100,000
			        	language = "nl";//dutch
			        } else if (host_tld.equals("bg")) {//Bulgaria /3,480,000
			        	language = "bg";//bulgarian; bul
			        } else if (host_tld.equals("bz")) {//Belize /2,790,000
			        	language = "en";//english
					} else if (host_tld.equals("ba")) {//Bosnia and Herzegovina /2,760,000
			        	language = "sh";//serbo-croatian
			        } else if (host_tld.equals("by")) {//Belarus /2,540,000
			        	language = "be";//belarusian; bel
			        } else if (host_tld.equals("bo")) {//Bolivia /1,590,000
			        	language = "es";//spanish; spa
			        	//language = "qu";//quechua; que
			        	//language = "ay";//aymara; aym (ayr)
			        	//und viele andere (indian)
			        } else if (host_tld.equals("bd")) {//Bangladesh /342,000
			        	language = "bn";//bengali; ben
			        } else if (host_tld.equals("bw")) {//Botswana /244,000
			        	//language = "en";//english
			        	language = "tn";//tswana; tsn
			        } else if (host_tld.equals("bh")) {//Bahrain /241,000
			        	language = "ar";//arabic
			        } else if (host_tld.equals("bf")) {//Burkina Faso /239,000
			        	language = "fr";//french
			        } else if (host_tld.equals("bm")) {//Bermuda /238,000
			        	language = "en";//english
			        } else if (host_tld.equals("bn")) {//Brunei Darussalam /157,000
			        	language = "ms";//malay; msa/mhp
			        } else if (host_tld.equals("bb")) {//Barbados /131,000
			        	language = "en";//english
			        } else if (host_tld.equals("bt")) {//Bhutan /123,000
			        	language = "dz";//dzongkha; dzo
			        } else if (host_tld.equals("bi")) {//Burundi /60,600
			        	language = "rn";//kirundi; run
			        } else if (host_tld.equals("bs")) {//Bahamas /37,700
			        	language = "en";//english
			        } else if (host_tld.equals("bj")) {//Benin /36,200
			        	language = "fr";//french; fra (fre); fra
			        } else if (host_tld.equals("bv")) {//Bouvet Island /55
			        	language = "no";//norwegian; nor (nob/nno)
			        }
				    break;
				case 'c':
			        if (host_tld.equals("ca")) {//Canada /165,000,000
			        	language = "en";//english
			        	//language = "fr";//french
			        } else if (host_tld.equals("ch")) {//Switzerland /62,100,000
			        	language = "de";//german; gsw
			        } else if (host_tld.equals("cn")) {//People's Republic of China /26,700,000
			        	language = "zh";//chinese; 	chi (zho); cmn - Mandarin (Modern Standard Mandarin)
			        } else if (host_tld.equals("cz")) {//Czech Republic /18,800,000
			        	language = "cs";//czech; cze (ces); ces
			        } else if (host_tld.equals("cl")) {//Chile /18,500,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("co")) {//Colombia /4,270,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("cc")) {//Cocos (Keeling) Islands /4,050,000
			        	language = "en";//english
			        } else if (host_tld.equals("cr")) {//Costa Rica /2,060,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("cy")) {//Cyprus /2,500,000
			        	language = "el";//greek; gre (ell); ell
			        } else if (host_tld.equals("cu")) {//Cuba /2,040,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("cx")) {//Christmas Island /1,830,000
			        	language = "en";//english
			        } else if (host_tld.equals("cd")) {//Democratic Republic of the Congo /475,000
			        	language = "fr";//french
			        } else if (host_tld.equals("cg")) {//Republic of the Congo /193,000
			        	language = "fr";//french
			        } else if (host_tld.equals("cm")) {//Cameroon /119,000
			        	//language = "fr";//french
			        	language = "en";//english
			        } else if (host_tld.equals("ci")) {//Cote d'Ivoire /95,200
			        	language = "fr";//french
			        } else if (host_tld.equals("cv")) {//Cape Verde /81,900
			        	language = "pt";//portuguese; por
			        } else if (host_tld.equals("ck")) {//Cook Islands /43,300
			        	language = "en";//english
			        	//language = "";//cook islands maori; rar (pnh, rkh)
			        } else if (host_tld.equals("cf")) {//Central African Republic /703
			        	language = "sg";//sango; sag; 92% could speak
			        	//language = "fr";//french; fra (fre); fra; 22,5% could speak, but maybe inet users prefer this
			        }
				    break;
				case 'd':
					if (host_tld.equals("dk")) {//Denmark /19,700,000
			        	language = "da";//danish; dan
			        } else if (host_tld.equals("do")) {//Dominican Republic /1,510,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("dz")) {//Algeria /326,000
			        	language = "ar";//arabic; ara; arq
			        } else if (host_tld.equals("dj")) {//Djibouti /150,000
			        	language = "ar";//arabic; ara; 94% are muslims, so arabic is primary
			        	//language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("dm")) {//Dominica /30,100
			        	language = "en";//english
			        }
				    break;
				case 'e':
					if (host_tld.equals("ee")) {//Estonia /6,790,000
			        	language = "et";//estonian; est; est (ekk)
			        } else if (host_tld.equals("eg")) {//Egypt /2,990,000
			        	language = "ar";//modern standard arabic; ara; arb
			        	//language = "ar";//egyptian arabic; ara; arz
			        } else if (host_tld.equals("ec")) {//Ecuador /2,580,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("et")) {//Ethiopia /142,000
			        	language = "am";//amharic; amh
			        } else if (host_tld.equals("eu")) {//European Union /45,100
			        	language = "en";//english (what can be else)
			        } else if (host_tld.equals("er")) {//Eritrea /15,800
			        	language = "ti";//tigrinya; tir
			        }
				    break;
				case 'f':
					if (host_tld.equals("fr")) {//France /96,700,000
				        language = "fr";//french; fre (fra); fra
					} else if (host_tld.equals("fi")) {//Finland /28,100,000
			        	language = "fi";//finnish; fin (92%)
					} else if (host_tld.equals("fm")) {//Federated States of Micronesia /4,580,000
			        	language = "en";//english
			        	//all native at regional level
			        } else if (host_tld.equals("fo")) {//Faroe Islands /623,000
			        	language = "fo";//faroese; fao
			        } else if (host_tld.equals("fj")) {//Fiji /466,000
			        	language = "fj";//fijian; fij
			        	//also english, fiji hindi etc
			        } else if (host_tld.equals("fk")) {//Falkland Islands /10,500
			        	language = "en";//english
			        }
				    break;
				case 'g':
					if (host_tld.equals("gr")) {//Greece /13,500,000
			        	language = "el";//greek; gre (ell); ell
			        } else if (host_tld.equals("ge")) {//Georgia /2,480,000
			        	language = "ka";//georgian; geo (kat); kat
			        } else if (host_tld.equals("gt")) {//Guatemala /904,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("gs")) {//South Georgia and the South Sandwich Islands /772,000
			        	language = "en";//english
			        } else if (host_tld.equals("gl")) {//Greenland /526,000
			        	language = "kl";//greenlandic; kal
			        } else if (host_tld.equals("gg")) {//Guernsey /322,000
			        	language = "en";//english
			        } else if (host_tld.equals("gi")) {//Gibraltar /193,000
			        	language = "en";//english
			        } else if (host_tld.equals("gh")) {//Ghana /107,000
			        	language = "en";//english
			        } else if (host_tld.equals("gy")) {//Guyana /68,700
			        	language = "en";//english
			        } else if (host_tld.equals("gm")) {//Gambia /59,300
			        	language = "en";//english
			        } else if (host_tld.equals("gn")) {//Guinea /18,700
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("ga")) {//Gabon /17,900
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("gd")) {//Grenada /13,600
			        	language = "en";//english
			        } else if (host_tld.equals("gu")) {//Guam /12,800
			        	//language = "ch";//chamorro; cha (looks like young generation don't want to use)
			        	language = "en";//english
			        } else if (host_tld.equals("gq")) {//Equatorial Guinea /1,450
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("gp")) {//Guadeloupe /980
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("gf")) {//French Guiana /926
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("gb")) {//United Kingdom of Great Britain and Northern Ireland (currently->uk) /186
			        	language = "en";//english
			        } else if (host_tld.equals("gw")) {//Guinea-Bissau /26
			        	language = "pt";//portuguese; por
			        }
				    break;
				case 'h':
					if (host_tld.equals("hu")) {//Hungary /18,500,000
			        	language = "hu";//hungarian; hun
			        } else if (host_tld.equals("hk")) {//Hong Kong /9,510,000
			        	language = "zh";//chinese; chi (zho, cmn)
			        	//also english
			        } else if (host_tld.equals("hr")) {//Croatia /6,080,000
			        	language = "hr";//croatian; hrv
			        } else if (host_tld.equals("hn")) {//Honduras /628,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("hm")) {//Heard and McDonald Islands /194,000
			        	language = "en";//english
			        } else if (host_tld.equals("ht")) {//Haiti /17,700
			        	language = "fr";//french; fre (fra); fra
			        	//language = "ht";//haitian creole; hat
			        }
				    break;
				case 'i':
					if (host_tld.equals("it")) {//Italy /55,200,000
			        	language = "it";//italian; ita
			        } else if (host_tld.equals("il")) {//Israel /17,800,000
			        	language = "he";//hebrew; heb
			        } else if (host_tld.equals("ie")) {//Republic of Ireland + Northern Ireland /17,000,000
			        	language = "ga";//irish; gle
			        	//language = "en";//english
			        } else if (host_tld.equals("in")) {//India /9,330,000
			        	language = "hi";//hindi; hin
			        } else if (language.equals("is")) {//Iceland /5,310,000
			        	language = "is";//icelandic; ice (isl); isl
			        } else if (host_tld.equals("ir")) {//Islamic Republic of Iran /2,940,000
			        	language = "fa";//persian; per (fas); pes
			        } else if (host_tld.equals("im")) {//Isle of Man /276,000
			        	language = "en";//english
			        	//language = "gv";//manx; glv (was dead, currently only slogans etc basically)
			        } else if (host_tld.equals("io")) {//British Indian Ocean Territory /108,000
			        	language = "en";//english
			        } else if (host_tld.equals("iq")) {//Iraq /133
			        	language = "ar";//arabic; ara; acm
			        	//language = "ku";//kurdish; kur
			        }
				    break;
				case 'j':
					if (host_tld.equals("jp")) {//Japan /139,000,000
			        	language = "ja";//japanese; jpn
			        } else if (host_tld.equals("jo")) {//Jordan /601,000
			        	language = "ar";//jordanian arabic; ara; ajp
			        	//language = "en";//english (businness)
			        } else if (host_tld.equals("jm")) {//Jamaica /290,000
			        	language = "en";//english
			        } else if (host_tld.equals("je")) {//Jersey /202,000
			        	language = "en";//english
			        }
				    break;
				case 'k':
					if (host_tld.equals("kr")) {//Republic of Korea /13,700,000
			        	language = "ko";//korean; kor
			        } else if (host_tld.equals("kz")) {//Kazakhstan /2,680,000
			        	language = "kk";//kazakh; kaz
			        	//language = "ru";//russian; rus (de-facto is widely used than native language)
			        } else if (host_tld.equals("kg")) {//Kyrgyzstan /1,440,000
			        	language = "ky";//kyrgyz; kir
			        	//language = "ru";//russian; rus (perhaps this one here is widely used)
			        } else if (host_tld.equals("ki")) {//Kiribati /427,000
			        	//language = "";//kiribati; gil (this one must be used, but don't have ISO 639-1) (!)
			        	language = "en";//english
			        	//here also can be other languages: .de.ki = deutsch
			        } else if (host_tld.equals("kw")) {//Kuwait /356,000
			        	language = "ar";//arabic; ara
			        } else if (host_tld.equals("ke")) {//Kenya /301,000
			        	language = "sw";//swahili; swa; swh
			        	//language = "en";//english
			        } else if (host_tld.equals("kh")) {//Cambodia /262,000
			        	language = "km";//khmer; khm
			        } else if (host_tld.equals("ky")) {//Cayman Islands /172,000
			        	language = "en";//english
			        } else if (host_tld.equals("kn")) {//Saint Kitts and Nevis /9,830
			        	language = "en";//english
			        } else if (host_tld.equals("km")) {//Comoros /533
			        	//Comorian dialects ISO 639-3: zdj, wni, swb, wlc - must be used here
			        	language = "ar";//arabic; ara
			        	//language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("kp")) {//Democratic People's Republic of Korea /122
			        	language = "ko";//korean; kor
			        }
				    break;
				case 'l':
					if (host_tld.equals("lv")) {//Latvia /6,970,000
			        	language = "lv";//latvian; lav;	lvs
			        } else if (host_tld.equals("lt")) {//Lithuania /6,040,000
			        	language = "lt";//lithuanian; lit
			        } else if (host_tld.equals("lu")) {//Luxembourg /4,940,000
			        	language = "lb";//luxembourgish; ltz (West Central German language familie; official 1984)
			        	//wide spoken, but not business or media
			        	//language = "fr";//french; fre (fra); fra (business)
			        	//language = "de";//german; ger (deu); ltz (media)
			        } else if (host_tld.equals("li")) {//Liechtenstein /3,990,000
			        	language = "de";//german; ger (deu); deu
			        } else if (host_tld.equals("lb")) {//Lebanon /1,890,000
			        	language = "ar";//arabic; ara
			        } else if (host_tld.equals("lk")) {//Sri Lanka /1,770,000
			        	language = "si";//sinhala; sin
			        	//language = "ta";//tamil; tam
			        } else if (host_tld.equals("la")) {//Laos (Lao People���s Democratic Republic) /932,000
			        	language = "lo";//lao; lao
			        } else if (host_tld.equals("ly")) {//Libya /388,000
			        	language = "ar";//libyan arabic; ara; ayl
			        } else if (host_tld.equals("lc")) {//Saint Lucia /86,400
			        	language = "en";//english
			        	//language = "";//french creole; acf (ISO 639-3)
			        	//ISO 639-1 is missed + not official, but this is 95% speaking language - must be first (!)
			        } else if (host_tld.equals("ls")) {//Lesotho /81,900
			        	language = "st";//sotho; sot (97%)
			        	//language = "en";//english
			        } else if (host_tld.equals("lr")) {//Liberia /588
			        	language = "en";//english
			        }
				    break;
				case 'm':
					if (host_tld.equals("mx")) {//Mexico /13,700,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("my")) {//Malaysia /4,610,000
			        	language = "en";//english (business)
			        	//language = "";//malaysian; zsm, zlm (maybe must be used here, but no ISO 639-1,2)
			        } else if (host_tld.equals("md")) {//Moldova /3,230,000
			        	language = "ro";//romanian; rum (ron); ron
			        } else if (host_tld.equals("ma")) {//Morocco /3,030,000
			        	language = "ar";//moroccan arabic; ara; ary
			        	//language = "fr";//french; fre (fra); fra
			        	//language = "";//amazigh (berber); ber; tzm (no ISO 639-1 code)
			        } else if (host_tld.equals("mk")) {//Republic of Macedonia /2,980,000
			        	language = "mk";//macedonian; mac (mkd); mkd
			        } else if (host_tld.equals("ms")) {//Montserrat /2,160,000
			        	language = "en";//english
			        } else if (host_tld.equals("mt")) {//Malta /1,650,000
			        	language = "mt";//maltese; mlt
			        	//100% speak Maltese, 88% English, 66% Italian
			        	//(but about 75-80% of sites have default english, support of maltese have ~50% of sites)
			        } else if (host_tld.equals("mo")) {//Macau /1,310,000
			        	language = "zh";//chinese; 	chi (zho); yue (cantonese)
			        } else if (host_tld.equals("mn")) {//Mongolia /1,160,000
			        	language = "mn";//Mongolian; mon; mon: khk
			        } else if (host_tld.equals("mp")) {//Northern Mariana Islands /861,000
			        	language = "en";//english
			        	//language = "ch";//chamorro; cha
			        	//language = "";//carolinian; ISO 639-3: cal (no ISO 639-1)
			        } else if (host_tld.equals("mu")) {//Mauritius /651,000
			        	language = "fr";//french; fre (fra); fra, mfe (predominant on media)
			        	//language = "en";//english (goverment)
			        } else if (host_tld.equals("mm")) {//Myanmar /367,000
			        	language = "my";//burmese; bur (mya); mya
			        } else if (host_tld.equals("mc")) {//Monaco /307,000
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("me")) {//Montenegro /?
			        	language = "sh";//montenegrin (~serbo-croatian, near serbian); scr, scc; hbs (macrolanguage): srp (serbian)
			        } else if (host_tld.equals("mz")) {//Mozambique /288,000
			        	language = "pt";//portuguese; por
			        	//language = "";//makhuwa; vmw (ISO 639-3)
			        } else if (host_tld.equals("mg")) {//Madagascar /255,000
			        	language = "mg";//malagasy; mlg (mlg); mlg (macrolanguage): plt
			        	//language = "fr";//french; fre (fra); fra
			        	//malagasy is native language, but elite want to french
			        } else if (host_tld.equals("mr")) {//Mauritania /210,000
			        	language = "ar";//arabic; ara; mey
			        	//language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("mv")) {//Maldives /125,000
			        	language = "dv";//dhivehi; div
			        	//English is used widely in commerce and increasingly in government schools.
			        } else if (host_tld.equals("mw")) {//Malawi /87,000
			        	//language = "ny";//chewa; nya
			        	language = "en";//english (founded sites in english only, include goverment)
			        } else if (host_tld.equals("ml")) {//Mali /73,500
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("mq")) {//Martinique /19,000
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("mh")) {//Marshall Islands /53
			        	language = "mh";//marshallese; mah
			        	//language = "en";//english
			        }
				    break;
				case 'n':
			        if (host_tld.equals("no")) {//Norway /32,300,000
			        	language = "no";//norwegian; nor (nob/nno)
			        } else if (host_tld.equals("nz")) {//New Zealand /18,500,000
			        	language = "en";//english
			        	//language = "mi";//maori; mao (mri); mri (4.2%)
			        } else if (host_tld.equals("nu")) {//Niue /5,100,000
			        	language = "en";//english
			        	//language = "";//niuean; niu (no ISO 639-1) (97.4% of native, but most are bilingual in English)
			        } else if (host_tld.equals("ni")) {//Nicaragua /4,240,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("np")) {//Nepal /1,910,000
			        	language = "ne";//nepali; nep
			        }if (host_tld.equals("na")) {//Namibia /1,650,000
			        	language = "af";//afrikaans; afr
			        	//language = "de";//German; ger (deu); deu
			        	//language = "ng";//ndonga (ovambo); kua (ndo); ndo
			        	//language = "en";//english
			        	//Official is English.
			        	//Northern majority of Namibians speak Oshiwambo as first language,
			        	//whereas the most widely understood and spoken Afrikaans.
			        	//Younger generation most widely understood English and Afrikaans.
			        	//Afrikaans is spoken by 60% of the WHITE community, German is spoken by 32%,
			        	//English is spoken by 7% and Portuguese by 1%.
			        } else if (host_tld.equals("nr")) {//Nauru /466,000
			        	//language = "na";//Nauruan; nau (50% - 66% at home)
			        	language = "en";//english (goverment + business, also .co.nr is free so here can be any)
			        } else if (host_tld.equals("nc")) {//New Caledonia /265,000
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("ne")) {//Niger /151,000
			        	language = "fr";//french; fre (fra); fra (official and elite)
			        	//language = "ha";//hausa; hau (50%)
			        } else if (host_tld.equals("ng")) {//Nigeria /101,000
			        	language = "en";//english
			        } else if (host_tld.equals("nf")) {//Norfolk Island /54,900
			        	language = "en";//english
			        }
				    break;
				case 'o':
					if (host_tld.equals("om")) {//Oman /204,000
			        	language = "ar";//omani arabic; ara; acx
			        	//language = "en";//english (education and science is ar/en, but people speak mostly arabic)
			        }
				    break;
				case 'p':
					if (host_tld.equals("pl")) {//Poland /20,100,000
			        	language = "pl";//polish; pol
			        } else if (host_tld.equals("pt")) {//Portugal /9,100,000
			        	language = "pt";//portuguese; por
			        } else if (host_tld.equals("ph")) {//Philippines /4,080,000
			        	language = "tl";//filipino; fil
			        	//language = "en";//english
			        } else if (host_tld.equals("pk")) {//Pakistan /3,180,000
			        	language = "ur";//urdu; urd (lingua franca and national language)
			        	//language = "en";//english (official language and used in business, government, and legal contracts)
			        	//language = "";//pakistani english;6:pake
			        	//(sase: South-Asian-English, engs: English Spoken)
			        	//language = "pa";//punjabi; pan
			        	//language = "ps";//pashto; pus; pst, pbt
			        	//language = "sd";//sindhi; snd
			        	//also Saraiki skr (no 1,2) and Balochi bal; bal (bgp, bgn, bcc) (no 1)
			        } else if (host_tld.equals("pw")) {//Palau /3,010,000
			        	language = "en";//english
			        	//language = "";//palauan; pau (no ISO 639-1)
			        	//language = "tl";//tagalog; tgl
			        	//language = "ja";//japanese; jpn
			        } else if (host_tld.equals("pe")) {//Peru /2,740,000
			        	language = "es";//spanish; spa (83.9%)
			        	//language = "qu";//quechua; que (13.2%)
			        } else if (host_tld.equals("pr")) {//Puerto Rico /1,920,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("pa")) {//Panama /1,040,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("py")) {//Paraguay /962,000
			        	language = "gn";//guarani; grn; gug (90%)
			        	//language = "es";//spanish; spa (87%)
			        } else if (host_tld.equals("ps")) {//Palestinian territories /559,000
			        	language = "ar";//palestinian arabic; ara; ajp
			        } else if (host_tld.equals("pf")) {//French Polynesia /240,000
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("pg")) {//Papua New Guinea /211,000
			        	language = "en";//english (also pidgin Tok Pisin)
			        	//language = "ho";//hiri motu; hmo
			        } else if (host_tld.equals("pn")) {//Pitcairn Islands /80,900
			        	language = "en";//english/pitkern (english creole); pih (ISO 639-3)
			        	//language = "en";//english (second language in schools)
			        } else if (host_tld.equals("pm")) {//Saint-Pierre and Miquelon /184
			        	language = "fr";//french; fre (fra); fra
			        }
				    break;
				case 'q':
					if (host_tld.equals("qa")) {//Qatar /259,000
			        	language = "ar";//gulf arabic; ara; afb
			        }
				    break;
				case 'r':
					if (host_tld.equals("ru")) {//Russia /67,900,000
			        	language = "ru";//russian; rus
			        } else if (host_tld.equals("ro")) {//Romania /7,990,000
			        	language = "ro";//daco-romanian; rum (ron); ron
			        } else if (host_tld.equals("rs")) {//Serbia /?
			        	language = "sr";//serbian; srp
			        } else if (host_tld.equals("re")) {//Reunion /146,000
			        	language = "fr";//french; fre (fra); fra, rcf (Reunion Creole)
			        } else if (host_tld.equals("rw")) {//Rwanda /131,000
			        	language = "rw";//kinyarwanda; kin
			        	//language = "en";//english
			        	//language = "fr";//french; fre (fra); fra
			        	//language = "sw";//swahili; swa
			        }
				    break;
				case 's':
					if (host_tld.equals("se")) {//Sweden /39,000,000
			        	language = "sv";//swedish; swe
			        } else if (host_tld.equals("es")) {//Spain /31,000,000
			        	language = "es";//spanish; spa
			        } else if (host_tld.equals("sg")) {//Singapore /8,770,000
			        	language = "zh";//singaporean mandarin (chinese); chi (zho); cmn (49.9%)
			        	//language = "en";//english (business, government and medium of instruction in schools) (32.3%)
			        	//language = "ms";//malay; may (msa); msa, zsm ("national language") (12.2%)
			        	//language = "ta";//tamil; tam
			        } else if (host_tld.equals("sk")) {//Slovakia /8,040,000
			        	language = "sk";//slovak; slo (slk); slk
			        } else if (host_tld.equals("si")) {//Slovenia /4,420,000
			        	language = "sl";//slovene; slv
			        } else if (host_tld.equals("su")) {//Soviet Union /3,530,000
			        	language = "ru";//russian; rus
			        } else if (host_tld.equals("sa")) {//Saudi Arabia /2,770,000
			        	language = "ar";//gulf arabic; ara; afb
			        } else if (host_tld.equals("st")) {//Sao Tome and Principe /2,490,000
			        	language = "pt";//portuguese; por (95%)
			        	//language = "pt";//forro (creole); por; cri (85%)
			        	//language = "pt";//angolar (creole); cpp; aoa (3%)
			        	//language = "fr";//french; fre (fra); fra (Francophonie -> learns in schools)
			        } else if (host_tld.equals("sv")) {//El Salvador /1,320,000
			        	language = "es";//spanish; spa
			        	//language = "";//nahuatl; nah; nlv and others (no ISO 639-1)
			        	//language = "";//mayan; myn (no ISO 639-1,3)
			        	//language = "";//q'eqchi'; kek (no ISO 639-1,2)
			        } else if (host_tld.equals("sc")) {//Seychelles /949,000
			        	language = "en";//english
			        	//language = "fr";//french; fre (fra); fra
			        	//language = "fr";//seychellois creole; fre (fra); crs
			        } else if (host_tld.equals("sh")) {//Saint Helena /547,000
			        	language = "en";//english
			        } else if (host_tld.equals("sn")) {//Senegal /503,000
			        	language = "wo";//wolof; wol (80%)
			        	//language = "fr";//french; fre (fra); fra
			        	//(understood ~15%-20% of all males and ~1%-2% of all women, but official)
			        } else if (host_tld.equals("sr")) {//Suriname /242,000
			        	language = "nl";//dutch; dut (nld); nld (education, government, business and the media)
			        	//language = "en";//sranan (suriname creole); srn; srn
			        	//language = "bh";//bhojpuri (Surinamese Hindi is a dialect of Bhojpuri); bho
			        	//language = "jv";//javanese; jvn
			        } else if (host_tld.equals("sm")) {//San Marino /225,000
			        	language = "it";//italian; ita
			        } else if (host_tld.equals("sy")) {//Syria /115,000
			        	language = "ar";//syrian arabic; ara; apc, ajp
			        	//language = "ku";//kurmanji (kurdish); kur; kmr
			        } else if (host_tld.equals("sz")) {//Swaziland /81,500
			        	language = "ss";//swazi; ssw
			        	//language = "en";//english
			        } else if (host_tld.equals("sl")) {//Sierra Leone /13,800
			        	language = "en";//Sierra Leone Krio (english); eng; kri (97% spoken)
			        	//language = "en";//english (official)
			        } else if (host_tld.equals("sb")) {//Solomon Islands /11,800
			        	language = "en";//Pijin (Solomons Pidgin or Neo-Solomonic); cpe; pis
			        	//language = "en";//english (1���2%)
			        } else if (host_tld.equals("sd")) {//Sudan /11,700
			        	language = "ar";//sudanese arabic; ara; apd
			        	//language = "en";//english
			        	//english and arabic promoted by goverment (english for education and official)
			        } else if (host_tld.equals("so")) {//Somalia /512
			        	language = "so";//somali; som
			        	//language = "ar";//hadhrami arabic; ara; ayh
			        	//language = "en";//english
			        	//language = "it";//italian; ita
			        	//language = "sw";//bravanese (swahili); swa; swh
			        } else if (host_tld.equals("ss")) {//South Sudan /?
			        	language = "en";//english
			        	//language = "ar";//juba arabic; ara; pga
			        	//language = "";//dinka; din (no ISO 639-1)
			        	//English and Juba Arabic are the official languages, although Dinka is the most widely spoken
			        }
				    break;
				case 't':
					if (host_tld.equals("tw")) {//Republic of China (Taiwan) /14,000,000
			        	language = "zh";//chinese; 	chi (zho); cmn - Mandarin (Modern Standard Mandarin)
			        } else if (host_tld.equals("tr")) {//Turkey /8,310,000
			        	language = "tr";//turkish; tur
			        } else if (host_tld.equals("tv")) {//Tuvalu /7,170,000
			        	//used for TV, domain currently operated by dotTV, a VeriSign company
			        	//the Tuvalu government owns twenty percent of the company
			        	//language = "";//tuvaluan; tvl (no ISO 639-1) (close to Maori(mi), Tahitian(ty), Samoan(sm), Tongan(to))
			        	language = "en";//english
			        } else if (host_tld.equals("th")) {//Thailand /6,470,000
			        	language = "th";//thai; tha
			        } else if (host_tld.equals("tc")) {//Turks and Caicos Islands /2,610,000
			        	//language = "en";//english
			        	language = "en";//turks and caicos islands creole; eng; tch
			        } else if (host_tld.equals("to")) {//Tonga /2,490,000
			        	//Often used unofficially for Torrent, Toronto, or Tokyo
			        	language = "to";//tongan; ton
			        	//language = "en";//english
			        } else if (host_tld.equals("tk")) {//Tokelau /2,170,000
			        	//Also used as a free domain service to the public (so maybe english here)
			        	language = "to";//tokelauan; tvl/ton; tkl (no ISO 639-1,2)
			        	//to - has marked similarities to the Niuafo'ou language of Tonga
			        	//tvl - Tokelauan is a Polynesian language closely related to Tuvaluan
			        	//language = "en";//english (main language is Tokelauan, but English is also spoken)
			        } else if (host_tld.equals("tt")) {//Trinidad and Tobago /1,170,000
			        	language = "en";//trinidadian english (official)
			        	//language = "en";//trinidadian creole; eng; trf (main spoken)
			        	//language = "en";//tobagonian creole; eng; tgh (main spoken)
			        } else if (host_tld.equals("tn")) {//Tunisia /1,060,000
			        	language = "ar";//tunisian arabic; ara; aeb
			        } else if (host_tld.equals("tf")) {//French Southern and Antarctic Lands /777,000
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("tz")) {//Tanzania /405,000
			        	language = "sw";//swahili; swa; swh
			        	//language = "en";//english (Higher courts, higher education)
			        } else if (host_tld.equals("tj")) {//Tajikistan /153,000
			        	language = "tg";//tajik; tgk
			        	//language = "ru";//russian; rus (wide in businness)
			        } else if (host_tld.equals("tp")) {//East Timor /151,000
			        	language = "pt";//portuguese; por
			        	//language = "en";//english
			        } else if (host_tld.equals("tm")) {//Turkmenistan /136,000
			        	language = "tk";//turkmen; tuk
			        } else if (host_tld.equals("tg")) {//Togo /36,000
			        	language = "fr";//french; fre (fra); fra
			        } else if (host_tld.equals("tl")) {//East Timor (Timor-Leste) /18,100
			        	//language = "";//tetum; tet (no ISO 639-1)
			        	language = "id";//indonesian; ind
			        	//language = "pt";//portuguese; por (5% literally, 25-50% listeners)
			        	//language = "en";//english
			        } else if (host_tld.equals("td")) {//Chad /332
			        	language = "ar";//chadian arabic; ara; shu
			        	//language = "ar";//arabic; ara
			        	//language = "fr";//french; fre (fra); fra
			        }
				    break;
				case 'u':
					if (host_tld.equals("uk")) {//United Kingdom of Great Britain and Northern Ireland /473,000,000
			        	language = "en";//english
			        } else if (host_tld.equals("us")) {//United States of America /68,300,000
			        	language = "en";//english
			        } else if (host_tld.equals("ua")) {//Ukraine /6,820,000
			        	language = "uk";//ukrainian; ukr
			        } else if (host_tld.equals("uz")) {//Uzbekistan /2,610,000
			        	language = "uz";//uzbek; uzb
			        	//language = "ru";//russian; rus (14% native)
			        } else if (host_tld.equals("uy")) {//Uruguay /2,020,000
			        	language = "es";//spanish; spa
			        	//language = "en";//english
			        } else if (host_tld.equals("ug")) {//Uganda /337,000
			        	language = "sw";//swahili; swa; swc
			        	//language = "en";//english (also ugandan english)
			        	//language = "lg";//ganda; lug (not all territory)
			        }
				    break;
				case 'v':
					if (host_tld.equals("vu")) {//Vanuatu /5,050,000
			        	language = "en";//english (education)
			        	//language = "bi";//bislama; bis (creole language, used as pidgin)
			        	//language = "fr";//french; fre (fra); fra (education)
			        	//many native languages, but no-one primary
			        } else if (host_tld.equals("ve")) {//Venezuela /3,050,000
			        	language = "es";//spanish; spa
			        	//language = "en";//english
			        	//language = "it";//italian; ita
			        	//also many indigenous languages
			        } else if (host_tld.equals("vn")) {//Vietnam /2,490,000
			        	language = "vi";//vietnamese; vie
			        } else if (host_tld.equals("va")) {//Vatican City /852,000
			        	language = "it";//italian; ita
			        } else if (host_tld.equals("vg")) {//British Virgin Islands /882,000
			        	language = "en";//english
			        	//language = "en";//virgin islands creole english; eng; vic
			        } else if (host_tld.equals("vc")) {//Saint Vincent and the Grenadines /239,000
			        	language = "en";//english
			        	//language = "en";//vincentiancreole; eng; svc (home and friends)
			        	//language = "bh";//bhojpuri; bho (east indian language)
			        	//native indians 2% and no data about their language
			        } else if (host_tld.equals("vi")) {//United States Virgin Islands /202,000
			        	language = "en";//english
			        	//language = "en";//virgin islands creole english; eng; vic
			        	//language = "es";//spanish; spa
			        	//language = "fr";//french; fre (fra); fra
			        }
				    break;
				case 'w':
					if (host_tld.equals("ws")) {//Samoa /3,000,000
			        	language = "sm";//Samoan; smo (most people)
			        	//but maybe english from the world also (!)
			        } else if (host_tld.equals("wf")) {//Wallis and Futuna /30
				        	language = "fr";//french; fre (fra); fra
				        	//language = "";//wallisian; wls (no ISO 639-1,2)
				        	//language = "";//futunan; fud (no ISO 639-1,2)
				        	//could: wallisian+futunan=88.5%; french=78.2%
				        	//had no knowledge: wallisian|futunan=7.2%; french=17.3% (!)
			        }
				    break;
				case 'x':
				    break;
				case 'y':
					if (host_tld.equals("yu")) {//Yugoslavia /3,270,000
			        	language = "sh";//serbo-croatian; scr, scc; hbs (srp, hrv, bos)
			        } else if (host_tld.equals("ye")) {//Yemen /93,800
			        	language = "ar";//yemeni arabic; ara; ayh (hadhrami), ayn (aanaani), acq(ta'izzi-adeni)
			        } else if (host_tld.equals("yt")) {//Mayotte /34
			        	language = "fr";//french; fre (fra); fra (55% read/write)
			        	//language = "sw";//maore comorian; swa; swb (41% r/w)
			        	//language = "ar";//yemeni arabic; ara (33% r/w)
			        }
				    break;
				case 'z':
					if (host_tld.equals("za")) {//South Africa /16,400,000
			        	//language = "zu";//zulu; zul (23.8%)
			        	//language = "xh";//xhosa; xho (17.6%)
			        	language = "af";//afrikaans; afr (13.3%)
			        	//language = "en";//english; (8.2%, but language of commerce and science)
			        	//need research (!)
			        } else if (host_tld.equals("zw")) {//Zimbabwe /507,000
			        	language = "sn";//shona; sna (70%)
			        	//language = "nd";//ndebele; nde (20%)
			        	//language = "en"//english (2.5%, but traditionally used for official business)
			        } else if (host_tld.equals("zm")) {//Zambia /324,000
			        	language = "en";//english (official business and is the medium of instruction in schools)
			        	//language = "ny";//chewa; nya
			        }
				    break;
	        	}
	        	break;
	        case 3:
	        	if (host_tld.equals("cat")) {//Catalan linguistic and cultural community /22,479
		        	language = "ca";//catalan; cat
		        }
	        	break;
	        case 8:
	        	if (host_tld.equals("xn--p1ai")) {//Russia/Cyrillic /67,900,000*
		        	language = "ru";//russian; rus
		        } else if (host_tld.equals("xn--node")) {//Georgia/Georgian /2,480,000*
		        	language = "ka";//georgian; geo (kat); kat //Proposed
		        }
	        	break;
	        case 9:
	        	if (host_tld.equals("xn--j1amh")) {//Ukraine/Cyrillic /6,820,000*
		        	language = "uk";//ukrainian; ukr //Proposed
		        }
	        	break;
	        case 10:
	        	if (host_tld.equals("xn--fiqs8s")) {//China/Simplified Chinese /26,700,000*
		        	language = "zh";//chinese; 	chi (zho); cmn - Mandarin (Modern Standard Mandarin)
		        } else if (host_tld.equals("xn--fiqz9s")) {//China/Traditional Chinese /26,700,000*
		        	language = "zh";//chinese; 	chi (zho); cmn - Mandarin (Modern Standard Mandarin)
		        } else if (host_tld.equals("xn--o3cw4h")) {//Thailand/Thai script /6,470,000*
		        	language = "th";//thai; tha
		        } else if (host_tld.equals("xn--wgbh1c")) {//Egypt/Arabic /2,990,000*
		        	language = "ar";//modern standard arabic; ara; arb
		        } else if (host_tld.equals("xn--wgbl6a")) {//Qatar/Arabic /259,000*
		        	language = "ar";//gulf arabic; ara; afb
		        } else if (host_tld.equals("xn--90a3ac")) {//Serbia/Cyrillic /?
		        	language = "sr";//serbian; srp
		        } else if (host_tld.equals("xn--wgv71a")) {//Japan/Japanese /139,000,000*
		        	language = "ja";//japanese; jpn //Proposed
		        }
	        	break;
	        case 11:
	        	if (host_tld.equals("xn--kprw13d")) {//Taiwan/Simplified Chinese /14,000,000*
		        	language = "zh";//chinese; 	chi (zho); cmn - Mandarin (Modern Standard Mandarin)
		        } else if (host_tld.equals("xn--kpry57d")) {//Taiwan/Simplified Chinese /14,000,000*
		        	language = "zh";//chinese; 	chi (zho); cmn - Mandarin (Modern Standard Mandarin)
		        } else if (host_tld.equals("xn--j6w193g")) {//Hong Kong/Traditional Chinese /9,510,000*
		        	language = "zh";//chinese; chi (zho, cmn)
		        } else if (host_tld.equals("xn--h2brj9c")) {//India/Devanagari /9,330,000*
		        	language = "hi";//hindi; hin
		        } else if (host_tld.equals("xn--gecrj9c")) {//India/Gujarati /9,330,000*
		        	language = "gu";//gujarati; guj
		        	//also can be Kutchi and Hindi
		        } else if (host_tld.equals("xn--s9brj9c")) {//India/Gurmukhi /9,330,000*
		        	language = "pa";//punjabi; pan
		        } else if (host_tld.equals("xn--45brj9c")) {//India/Bengali /9,330,000*
		        	language = "bn";//bengali; ben
		        } else if (host_tld.equals("xn--pgbs0dh")) {//Tunisia/Arabic /1,060,000*
		        	language = "ar";//tunisian arabic; ara; aeb
		        } else if (host_tld.equals("xn--80ao21a")) {//Kazakhstan/Cyrillic /2,680,000*
		        	language = "kk";//kazakh; kaz //Proposed
		        }
	        	break;
	        case 12:
	        	if (host_tld.equals("xn--3e0b707e")) {//South Korea/Hangul /13,700,000*
		        	language = "ko";//korean; kor
		        } else if (host_tld.equals("xn--mgbtf8fl")) {//Syria/Arabic /115,000*
		        	language = "ar";//syrian arabic; ara; apc, ajp
		        } else if (host_tld.equals("xn--4dbrk0ce")) {//Israel/Hebrew /17,800,000*
		        	language = "he";//hebrew; heb //Proposed
		        } else if (host_tld.equals("xn--mgb9awbf")) {//Oman/Arabic /204,000
		        	language = "ar";//omani arabic; ara; acx //Proposed
		        } else if (host_tld.equals("xn--mgb2ddes")) {//Yemen/Arabic /93,800*
		        	language = "ar";//yemeni arabic; ara; ayh (hadhrami), ayn (aanaani), acq(ta'izzi-adeni) //Proposed
		        }
	        	break;
	        case 13:
	        	if (host_tld.equals("xn--fpcrj9c3d")) {//India/Telugu /9,330,000*
		        	language = "te";//telugu; tel
		        } else if (host_tld.equals("xn--yfro4i67o")) {//Singapore/Chinese /8,770,000*
		        	language = "zh";//singaporean mandarin (chinese); chi (zho); cmn
		        } else if (host_tld.equals("xn--fzc2c9e2c")) {//Sri Lanka/Sinhala language /1,770,000*
		        	language = "si";//sinhala; sin
		        } else if (host_tld.equals("xn--ygbi2ammx")) {//Palestinian Territory/Arabic /559,000*
		        	language = "ar";//palestinian arabic; ara; ajp
		        }
	        	break;
	        case 14:
	        	if (host_tld.equals("xn--mgbbh1a71e")) {//India/Urdu /9,330,000*
		        	language = "ur";//urdu; urd
		        } else if (host_tld.equals("xn--mgbaam7a8h")) {//United Arab Emirates/Arabic /3,310,000*
		        	language = "ar";//arabic
		        } else if (host_tld.equals("xn--mgbayh7gpa")) {//Jordan/Arabic /601,000*
		        	language = "ar";//jordanian arabic; ara; ajp
		        } else if (host_tld.equals("xn--mgbx4cd0ab")) {//Malaysia/Arabic(Jawi alphabet?) /4,610,000*
		        	language = "ar";//arabic //Proposed (why not malay?)
		        } else if (host_tld.equals("xn--54b7fta0cc")) {//Bangladesh/Bengali /342,000*
		        	language = "bn";//bengali; ben //Proposed
		        }
	        	break;
	        case 15:
	        	if (host_tld.equals("xn--mgbc0a9azcg")) {//Morocco/Arabic /3,030,000*
		        	language = "ar";//moroccan arabic; ara; ary
		        } else if (host_tld.equals("xn--mgba3a4f16a")) {//Iran/Persian /2,940,000*
		        	language = "fa";//persian; per (fas); pes
		        } else if (host_tld.equals("xn--lgbbat1ad8j")) {//Algeria/Arabic /326,000*
		        	language = "ar";//arabic; ara; arq
		        }
	        	break;
	        case 16:
	        	if (host_tld.equals("xn--xkc2al3hye2a")) {//Sri Lanka/Tamil /1,770,000*
		        	language = "ta";//tamil; tam
		        }
	        	break;
	        case 17:
	        	if (host_tld.equals("xn--xkc2dl3a5ee0h")) {//India/Tamil /9,330,000*
		        	language = "ta";//tamil; tam
		        	//Badaga (ISO 639-3:bfq), Irula (ISO 639-3:iru), Paniya (ISO 639-3:pcg)
		        } else if (host_tld.equals("xn--mgberp4a5d4ar")) {//Saudi Arabia/Arabic /2,770,000*
		        	language = "ar";//gulf arabic; ara; afb
		        } else if (host_tld.equals("xn--mgbai9azgqp6j")) {//Pakistan/Arabic /3,180,000*
		        	language = "ar";//arabic //Proposed (why not urdu?)
		        	//language = "ur";//urdu; urd (lingua franca and national language)
		        }
	        	break;
	        case 22:
	        	if (host_tld.equals("xn--clchc0ea0b2g2a9gcd")) {//Singapore/Tamil /8,770,000*
		        	language = "ta";//tamil; tam
		        }
		        //* - stats from ccTLD
	        	break;
	        default:
	        	break;
        }
        //6: ISO 639-6 Part 6: Alpha-4 - most of small languages from ISO 639-3 not exists.
        //ISO 639-2 languages included, but not all.
        return language;
    }

    // The MultiProtocolURI may be used to integrate File- and SMB accessed into one object
    // some extraction methods that generate File/SmbFile objects from the MultiProtocolURI

    /**
     * create a standard java URL.
     * Please call isHTTP(), isHTTPS() and isFTP() before using this class
     */
    public java.net.URL getURL() throws MalformedURLException {
        if (!(isHTTP() || isHTTPS() || isFTP())) throw new MalformedURLException();
        return new java.net.URL(this.toNormalform(false));
    }

    /**
     * create a standard java File.
     * Please call isFile() before using this class
     */
    public java.io.File getFSFile() throws MalformedURLException {
        if (!isFile()) throw new MalformedURLException();
        return new java.io.File(this.toNormalform(true).substring(7));
    }

    /**
     * create a smb File
     * Please call isSMB() before using this class
     * @throws MalformedURLException
     */
    public SmbFile getSmbFile() throws MalformedURLException {
        if (!isSMB()) throw new MalformedURLException();
        final String url = unescape(this.toNormalform(true));
        return new SmbFile(url);
    }

    // some methods that let the MultiProtocolURI look like a java.io.File object
    // to use these methods the object must be either of type isFile() or isSMB()

    public boolean exists() throws IOException {
        if (isFile()) return getFSFile().exists();
        if (isSMB()) try {
            return TimeoutRequest.exists(getSmbFile(), SMB_TIMEOUT);
        } catch (final SmbException e) {
            throw new IOException("SMB.exists SmbException (" + e.getMessage() + ") for " + toString());
        } catch (final MalformedURLException e) {
            throw new IOException("SMB.exists MalformedURLException (" + e.getMessage() + ") for " + toString());
        }
        return false;
    }

    public boolean canRead() throws IOException {
        if (isFile()) return getFSFile().canRead();
        if (isSMB()) try {
            return TimeoutRequest.canRead(getSmbFile(), SMB_TIMEOUT);
        } catch (final SmbException e) {
            throw new IOException("SMB.canRead SmbException (" + e.getMessage() + ") for " + toString());
        } catch (final MalformedURLException e) {
            throw new IOException("SMB.canRead MalformedURLException (" + e.getMessage() + ") for " + toString());
        }
        return false;
    }

    public boolean canWrite() throws IOException {
        if (isFile()) return getFSFile().canWrite();
        if (isSMB()) try {
            return TimeoutRequest.canWrite(getSmbFile(), SMB_TIMEOUT);
        } catch (final SmbException e) {
            throw new IOException("SMB.canWrite SmbException (" + e.getMessage() + ") for " + toString());
        } catch (final MalformedURLException e) {
            throw new IOException("SMB.canWrite MalformedURLException (" + e.getMessage() + ") for " + toString());
        }
        return false;
    }

    public boolean isHidden() throws IOException {
        if (isFile()) return getFSFile().isHidden();
        if (isSMB()) try {
            return TimeoutRequest.isHidden(getSmbFile(), SMB_TIMEOUT);
        } catch (final SmbException e) {
            throw new IOException("SMB.isHidden SmbException (" + e.getMessage() + ") for " + toString());
        } catch (final MalformedURLException e) {
            throw new IOException("SMB.isHidden MalformedURLException (" + e.getMessage() + ") for " + toString());
        }
        return false;
    }

    public boolean isDirectory() throws IOException {
        if (isFile()) return getFSFile().isDirectory();
        if (isSMB()) try {
            return TimeoutRequest.isDirectory(getSmbFile(), SMB_TIMEOUT);
        } catch (final SmbException e) {
            throw new IOException("SMB.isDirectory SmbException (" + e.getMessage() + ") for " + toString());
        } catch (final MalformedURLException e) {
            throw new IOException("SMB.isDirectory MalformedURLException (" + e.getMessage() + ") for " + toString());
        }
        return false;
    }

    public long length() {
        if (isFile()) try {
            return getFSFile().length();
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
            return -1;
        }
        if (isSMB()) try {
            return TimeoutRequest.length(getSmbFile(), SMB_TIMEOUT);
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
            return -1;
        }
        return -1;
    }

    public long lastModified() throws IOException {
        if (isFile()) return getFSFile().lastModified();
        if (isSMB()) try {
            return TimeoutRequest.lastModified(getSmbFile(), SMB_TIMEOUT);
        } catch (final SmbException e) {
            throw new IOException("SMB.lastModified SmbException (" + e.getMessage() + ") for " + toString());
        } catch (final MalformedURLException e) {
            throw new IOException("SMB.lastModified MalformedURLException (" + e.getMessage() + ") for " + toString());
        }
        return 0;
    }

    public String getName() throws IOException {
        if (isFile()) return getFSFile().getName();
        if (isSMB()) try {
            return getSmbFile().getName();
        } catch (final MalformedURLException e) {
            throw new IOException("SMB.getName MalformedURLException (" + e.getMessage() + ") for " + toString() );
        }
        if (isFTP()) {
            return this.getFileName();
        }
        return null;
    }

    public String[] list() throws IOException {
        if (isFile()) return getFSFile().list();
        if (isSMB()) try {
            final SmbFile sf = getSmbFile();
            if (!sf.isDirectory()) return null;
            try {
                return TimeoutRequest.list(sf, SMB_TIMEOUT);
            } catch (final SmbException e) {
                throw new IOException("SMB.list SmbException for " + sf.toString() + ": " + e.getMessage());
            }
        } catch (final MalformedURLException e) {
            throw new IOException("SMB.list MalformedURLException for " + toString() + ": " + e.getMessage());
        }
        return null;
    }

    public InputStream getInputStream(final ClientIdentification.Agent agent, final String username, final String pass) throws IOException {
        if (isFile()) return new BufferedInputStream(new FileInputStream(getFSFile()));
        if (isSMB()) return new BufferedInputStream(new SmbFileInputStream(getSmbFile()));
        if (isFTP()) {
            final FTPClient client = new FTPClient();
            client.open(this.host, this.port < 0 ? 21 : this.port);
            final byte[] b = client.get(this.path);
            client.CLOSE();
            return new ByteArrayInputStream(b);
        }
        if (isHTTP() || isHTTPS()) {
                final HTTPClient client = new HTTPClient(agent);
                client.setHost(getHost());
                return new ByteArrayInputStream(client.GETbytes(this, username, pass, false));
        }

        return null;
    }

    public byte[] get(final ClientIdentification.Agent agent, final String username, final String pass) throws IOException {
        if (isFile()) return read(new FileInputStream(getFSFile()));
        if (isSMB()) return read(new SmbFileInputStream(getSmbFile()));
        if (isFTP()) {
            final FTPClient client = new FTPClient();
            client.open(this.host, this.port < 0 ? 21 : this.port);
            final byte[] b = client.get(this.path);
            client.CLOSE();
            return b;
        }
        if (isHTTP() || isHTTPS()) {
                final HTTPClient client = new HTTPClient(agent);
                client.setHost(getHost());
                return client.GETbytes(this, username, pass, false);
        }

        return null;
    }

    public static byte[] read(final InputStream source) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] buffer = new byte[2048];
        int c;
        while ((c = source.read(buffer, 0, 2048)) > 0) baos.write(buffer, 0, c);
        baos.flush();
        baos.close();
        return baos.toByteArray();
    }

    public Locale getLocale() {
        if (this.hostAddress != null) {
            final Locale locale = Domains.getLocale(this.hostAddress);
            if (locale != null && locale.getCountry() != null && locale.getCountry().length() > 0) return locale;
        }
        /*
        if (this.hostAddress != null) {
            return Domains.getLocale(this.hostAddress);
        }
        */
        return Domains.getLocale(this.host);
    }

    //---------------------

    private static final String splitrex = " |/|\\(|\\)|-|\\:|_|\\.|,|\\?|!|'|" + '"';
    public static final Pattern splitpattern = Pattern.compile(splitrex);
    public static String[] urlComps(String normalizedURL) {
        final int p = normalizedURL.indexOf("//",0);
        if (p > 0) normalizedURL = normalizedURL.substring(p + 2);
        return splitpattern.split(normalizedURL.toLowerCase()); // word components of the url
    }
/*
    public static void main(final String[] args) {
        for (final String s: args) System.out.println(toTokens(s));
    }
*/
    public static void main(final String[] args) {
        final String[][] test = new String[][]{
          new String[]{null, "C:WINDOWS\\CMD0.EXE"},
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
          new String[]{null, "ftp://bob:builder@ftp.anomic.de/home/test.gif"},
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
          new String[]{null, "http://diskusjion.no/index.php?s=5bad5f431a106d9a8355429b81bb0ca5&amp;showuser=23585"},
          new String[]{null, "http://www.scc.kit.edu/publikationen/80.php?PHPSESSID=5f3624d3e1c33d4c086ab600d4d5f5a1"},
          new String[]{null, "smb://localhost/"},
          new String[]{null, "smb://localhost/repository"}, // paths must end with '/'
          new String[]{null, "smb://localhost/repository/"},
          new String[]{null, "\\\\localhost\\"}, // Windows-like notion of smb shares
          new String[]{null, "\\\\localhost\\repository"},
          new String[]{null, "\\\\localhost\\repository\\"},
          new String[]{null, "http://test.net/test1.htm?s=multiple&amp;a=amp&amp;b=in&amp;c=url"},
          new String[]{null, "http://test.net/test2.htm?s=multiple&amp;amp;amp;amp;a=amp"},
          new String[]{null, "http://validator.w3.org/check?uri=http://www.anomic.de/"}
          };
        //MultiProtocolURI.initSessionIDNames(FileUtils.loadList(new File("defaults/sessionid.names")));
        String environment, url;
        MultiProtocolURL aURL, aURL1;
        java.net.URL jURL;
        for (String[] element : test) {
            environment = element[0];
            url = element[1];
            try {aURL = MultiProtocolURL.newURL(environment, url);} catch (final MalformedURLException e) {e.printStackTrace(); aURL = null;}
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
                aURL1 = new MultiProtocolURL(aURL.toNormalform(false));
                if (!(aURL1.toNormalform(false).equals(aURL.toNormalform(false)))) {
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
