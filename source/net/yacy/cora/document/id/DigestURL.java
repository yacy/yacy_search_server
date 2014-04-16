/**
 *  DigestURL
 *  Copyright 2006 by Michael Peter Christen
 *  first published 13.07.2006 on http://yacy.net
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

// this class exist to provide a system-wide normal form representation of urls,
// and to prevent that java.net.URL usage causes DNS queries which are used in java.net.

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.Digest;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.CommonPattern;

/**
 * URI-object providing YaCy-hash computation
 *
 * Hashes for URIs are split in several parts
 * For URIs pointing to resources not globally available,
 * the domainhash-part gets one reserved value
 */
public class DigestURL extends MultiProtocolURL implements Serializable {

    public static final DigestURL POISON = new DigestURL(); // poison pill for concurrent link generators
    
    private static final long serialVersionUID = -1173233022912141885L;

    // class variables
    private byte[] hash;

    /**
     * Shortcut, calculate hash for shorted url/hostname
     * @param host
     * @param port
     * @return
     */
    public static String hosthash(final String host, final int port) throws MalformedURLException {
        String h = host;
        if (h.indexOf("//") < 0) {
            if (port == 80 || port == 8080 || port == 8090) h = "http://" + h;
            else if (port == 443) h = "https://" + h;
            else if (port == 21 || port == 2121) h = "ftp://" + h;
            else if (port > 999) h = "http://" + h + ":" + port;
            else h = "http://" + h;
        }
        DigestURL url = new DigestURL(h);
        return (url == null) ? null : ASCII.String(url.hash(), 6, 6);
    }

    /**
     * from a given list of hosts make a list of host hashes
     * the list is separated by comma
     * @param hostlist
     * @return list of host hashes without separation
     * @throws MalformedURLException 
     */
    public static String hosthashes(final String hostlist) throws MalformedURLException {
        String[] hs = CommonPattern.COMMA.split(hostlist);
        StringBuilder sb = new StringBuilder(hostlist.length());
        for (String h: hs) {
            if (h == null) continue;
            h = h.trim();
            if (h.isEmpty()) continue;
            h = hosthash(h, h.startsWith("ftp.") ? 21 : 80);
            if (h == null || h.length() != 6) continue;
            sb.append(h);
        }
        return sb.toString();
    }

    public static Set<String> hosthashess(String hosthashes) {
        if (hosthashes == null || hosthashes.isEmpty()) return null;
        HashSet<String> h = new HashSet<String>();
        assert hosthashes.length() % 6 == 0;
        for (int i = 0; i < hosthashes.length(); i = i + 6) {
            h.add(hosthashes.substring(i, i + 6));
        }
        return h;
    }

    /**
     * DigestURI to generate a poison pill
     */
    private DigestURL() {
        super();
        this.hash = null;
    }

    /**
     * DigestURI from File
     */
    public DigestURL(final File file) throws MalformedURLException {
        this("file", "", -1, file.getAbsolutePath());
    }

    /**
     * DigestURI from URI string
     */
    public DigestURL(final String url) throws MalformedURLException {
        super(url);
        this.hash = null;
    }

    /**
     * DigestURI from URI string, hash is already calculated
     * @param url
     * @param hash already calculated hash for url
     * @throws MalformedURLException
     */
    public DigestURL(final String url, final byte[] hash) throws MalformedURLException {
        super(url);
        this.hash = hash;
    }
    
    /**
     * DigestURI from general URI, hash already calculated
     * @param baseURL
     * @param hash
     */
    public DigestURL(final MultiProtocolURL baseURL, final byte[] hash) {
        super(baseURL);
        this.hash = hash;
    }

    public DigestURL(final MultiProtocolURL baseURL, final String relPath) throws MalformedURLException {
        super(baseURL, relPath);
        this.hash = null;
    }

    public DigestURL(final String protocol, final String host, final int port, final String path) throws MalformedURLException {
        super(protocol, host, port, path);
        this.hash = null;
    }

    public static DigestURL newURL(final DigestURL baseURL, String relPath) throws MalformedURLException {
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
            return new DigestURL(relPath);
        }
        return new DigestURL(baseURL, relPath);
    }
    
    private int hashCache = Integer.MIN_VALUE; // if this is used in a compare method many times, a cache is useful
    
    @Override
    public int hashCode() {
        if (this.hashCache == Integer.MIN_VALUE) {
            this.hashCache = ByteArray.hashCode(hash());
        }
        return this.hashCache;
    }

    public static final int flagTypeID(final String hash) {
        return (Base64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 32) >> 5;
    }

    public static final int flagTLDID(final String hash) {
        return (Base64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 28) >> 2;
    }

    public static final int flagLengthID(final String hash) {
        return (Base64Order.enhancedCoder.decodeByte(hash.charAt(11)) & 3);
    }

    /**
     * get YaCy-hash of URI
     * @return
     */
    public final byte[] hash() {
        // in case that the object was initialized without a known url hash, compute it now
        if (this.hash == null) {
            synchronized (this) {
                if (this.hash == null) this.hash = urlHashComputation();
            }
        }
        return this.hash;
    }

    public String hosthash() {
        return ASCII.String(this.hash(), 6, 6);
    }
    
    /**
     * calculated YaCy-Hash of this URI
     *
     * @note needs DNS lookup to check if the addresses domain is local
     * that causes that this method may be very slow
     *
     * @return hash
     */
    private final byte[] urlHashComputation() {
        // the url hash computation needs a DNS lookup to check if the addresses domain is local
        // that causes that this method may be very slow

        assert this.hash == null; // should only be called if the hash was not computed before

        final int id = Domains.getDomainID(this.host, this.hostAddress); // id=7: tld is local
        final boolean isHTTP = isHTTP();
        int p = (this.host == null) ? -1 : this.host.lastIndexOf('.');
        String dom = (p > 0) ? dom = this.host.substring(0, p) : "";
        p = dom.lastIndexOf('.'); // locate subdomain
        final String subdom;
        if (p <= 0) {
            subdom = "";
        } else {
            subdom = dom.substring(0, p);
            dom = dom.substring(p + 1);
        }

        // find rootpath
        int rootpathStart = 0;
        int rootpathEnd = this.path.length() - 1;
        if (!this.path.isEmpty() && this.path.charAt(0) == '/')
            rootpathStart = 1;
        if (this.path.endsWith("/"))
            rootpathEnd = this.path.length() - 2;
        p = this.path.indexOf('/', rootpathStart);
        String rootpath = "";
        if (p > 0 && p < rootpathEnd) {
            rootpath = this.path.substring(rootpathStart, p);
        }

        // we collected enough information to compute the fragments that are
        // basis for hashes
        final int l = dom.length();
        final int domlengthKey = (l <= 8) ? 0 : (l <= 12) ? 1 : (l <= 16) ? 2 : 3;
        final byte flagbyte = (byte) (((isHTTP) ? 0 : 32) | (id << 2) | domlengthKey);

        // combine the attributes
        final StringBuilder hashs = new StringBuilder(12);
        assert hashs.length() == 0;
        // form the 'local' part of the hash
        final String normalform = toNormalform(true, true);
        final String b64l = Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(normalform));
        if (b64l.length() < 5) return null;
        hashs.append(b64l.substring(0, 5)); // 5 chars
        assert hashs.length() == 5;
        hashs.append(subdomPortPath(subdom, this.port, rootpath)); // 1 char
        assert hashs.length() == 6;
        // form the 'global' part of the hash
        hashs.append(hosthash5(this.protocol, this.host, this.port)); // 5 chars
        assert hashs.length() == 11;
        hashs.append(Base64Order.enhancedCoder.encodeByte(flagbyte)); // 1 char
        assert hashs.length() == 12;

        // return result hash
        final byte[] b = ASCII.getBytes(hashs.toString());
        assert b.length == 12;
        return b;
    }

    private static char subdomPortPath(final String subdom, final int port, final String rootpath) {
        final StringBuilder sb = new StringBuilder(subdom.length() + rootpath.length() + 8);
        sb.append(subdom).append(':').append(Integer.toString(port)).append(':').append(rootpath);
        return Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(sb.toString())).charAt(0);
    }

    public final static Pattern rootPattern = Pattern.compile("/|/\\?|/index.htm(l?)|/index.php|/home.htm(l?)|/home.php|/default.htm(l?)|/default.php");

    private static final String hosthash5(final String protocol, final String host, final int port) {
        if (host == null) {
            return Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(protocol)).substring(0, 5);
        }
        final StringBuilder sb = new StringBuilder(host.length() + 15);
        sb.append(protocol).append(':').append(host).append(':').append(Integer.toString(port));
        return Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(sb.toString())).substring(0, 5);
    }

    /**
     * compute a 6-byte hash fragment that can be used to identify the domain of the url
     * @param protocol
     * @param host
     * @param port
     * @return 6 bytes base64 encoded String representing the domain of the url
     */
    private static final String hosthash6(final String protocol, final String host, final int port) {
        final StringBuilder hash = new StringBuilder(12);
        final int id = Domains.getDomainID(host, null); // id=7: tld is local
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

    //private static String[] testTLDs = new String[] { "com", "net", "org", "uk", "fr", "de", "es", "it" };

    public static final int domLengthEstimation(final byte[] urlHashBytes) {
        // generates an estimation of the original domain length
        assert (urlHashBytes != null);
        assert (urlHashBytes.length == 12) : "urlhash = " + ASCII.String(urlHashBytes);
        final int flagbyte = Base64Order.enhancedCoder.decodeByte(urlHashBytes[11]);
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
        default:
            return 20;
        }
    }

    public static int domLengthNormalized(final byte[] urlHashBytes) {
        return domLengthEstimation(urlHashBytes) << 8 / 20;
    }

    private static final int domDomain(final byte[] urlHash) {
        // returns the ID of the domain of the domain
        assert (urlHash != null);
        assert (urlHash.length == 12 || urlHash.length == 6) : "urlhash = " + ASCII.String(urlHash);
        return (Base64Order.enhancedCoder.decodeByte(urlHash[(urlHash.length == 12) ? 11 : 5]) & 28) >> 2;
    }

    /**
     *  checks for local/global IP range and local IP
     */
    @Override
    public final boolean isLocal() {
        if (this.isFile()) return true;
        return domDomain(hash()) == 7;
    }

    /**
     * checks, if hash is in local/global IP range
     * @param urlhash
     * @return
     */
    public static final boolean isLocal(final byte[] urlhash) {
        return domDomain(urlhash) == 7;
    }

}
