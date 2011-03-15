// DigestURI.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 13.07.2006 on http://yacy.net
//
// $LastChangedDate$
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

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.Domains;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Digest;
import net.yacy.kelondro.util.ByteArray;

/**
 * URI-object providing YaCy-hash computation
 * 
 * Hashes for URIs are split in several parts
 * For URIs pointing to resources not globally available,
 * the domainhash-part gets one reserved value
 */
public class DigestURI extends MultiProtocolURI implements Serializable {
    
    private static final long serialVersionUID = -1173233022912141885L;
    public  static final int TLD_any_zone_filter = 255; // from TLD zones can be filtered during search; this is the catch-all filter
    
    // class variables
    private byte[] hash;
    
    /**
     * Shortcut, calculate hash for shorted url/hostname
     * @param host
     * @return
     */
    public static String domhash(final String host) {
        String h = host;
        if (!h.startsWith("http://")) h = "http://" + h;
        DigestURI url = null;
        try {
            url = new DigestURI(h);
        } catch (MalformedURLException e) {
            Log.logException(e);
            return null;
        }
        return (url == null) ? null : UTF8.String(url.hash(), 6, 6);
    }
    
    /**
     * DigestURI from File
     */
    public DigestURI(final File file) throws MalformedURLException {
        this("file", "", -1, file.getAbsolutePath());
    }

    /**
     * DigestURI from URI string
     */
    public DigestURI(final String url) throws MalformedURLException {
        this(url, null);
    }
    
    /**
     * DigestURI from URI string, hash is already calculated
     * @param url 
     * @param hash already calculated hash for url
     * @throws MalformedURLException
     */
    public DigestURI(final String url, final byte[] hash) throws MalformedURLException {
        super(url);
        this.hash = hash;
    }
    
    /**
     * DigestURI from general URI
     * @param baseURL
     */
    public DigestURI(final MultiProtocolURI baseURL) {
        super(baseURL);
        this.hash = null;
    }
    
    /**
     * DigestURI from general URI, hash already calculated
     * @param baseURL
     * @param hash
     */
    public DigestURI(final MultiProtocolURI baseURL, final byte[] hash) {
        super(baseURL);
        this.hash = hash;
    }
    
    public DigestURI(final MultiProtocolURI baseURL, String relPath) throws MalformedURLException {
        super(baseURL, relPath);
        this.hash = null;
    }
    
    public DigestURI(final String protocol, final String host, final int port, final String path) throws MalformedURLException {
        super(protocol, host, port, path);
        this.hash = null;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return ByteArray.hashCode(this.hash());
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
            // we check the this.hash value twice to avoid synchronization where possible
            synchronized (this.protocol) {
                if (this.hash == null) this.hash = urlHashComputation();
            }
        }
        return this.hash;
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

        final int id = Domains.getDomainID(host); // id=7: tld is local
        final boolean isHTTP = isHTTP();
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
        final StringBuilder hashs = new StringBuilder(12);
        assert hashs.length() == 0;
        // form the 'local' part of the hash
        String normalform = toNormalform(true, true, false, true);
        String b64l = Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(normalform));
        if (b64l.length() < 5) return null;
        hashs.append(b64l.substring(0, 5)); // 5 chars
        assert hashs.length() == 5;
        hashs.append(subdomPortPath(subdom, port, rootpath)); // 1 char
        assert hashs.length() == 6;
        // form the 'global' part of the hash
        hashs.append(hosthash5(this.protocol, host, port)); // 5 chars
        assert hashs.length() == 11;
        hashs.append(Base64Order.enhancedCoder.encodeByte(flagbyte)); // 1 char
        assert hashs.length() == 12;

        // return result hash
        byte[] b = UTF8.getBytes(hashs.toString());
        assert b.length == 12;
        return b;
    }
    
    private static char subdomPortPath(final String subdom, final int port, final String rootpath) {
        StringBuilder sb = new StringBuilder(subdom.length() + rootpath.length() + 8);
        sb.append(subdom).append(':').append(Integer.toString(port)).append(':').append(rootpath);
        return Base64Order.enhancedCoder.encode(Digest.encodeMD5Raw(sb.toString())).charAt(0);
    }

    private static final char rootURLFlag0 = subdomPortPath("", 80, "");
    private static final char rootURLFlag1 = subdomPortPath("www", 80, "");

    public static final boolean probablyRootURL(String urlHash) {
    	char c = urlHash.charAt(5);
        return c == rootURLFlag0 || c == rootURLFlag1;
    }

    public static final boolean probablyRootURL(final byte[] urlHash) {
    	char c = (char) urlHash[5];
        return c == rootURLFlag0 || c == rootURLFlag1;
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
    
    //private static String[] testTLDs = new String[] { "com", "net", "org", "uk", "fr", "de", "es", "it" };

    public static final int domLengthEstimation(final byte[] urlHashBytes) {
        // generates an estimation of the original domain length
        assert (urlHashBytes != null);
        assert (urlHashBytes.length == 12) : "urlhash = " + UTF8.String(urlHashBytes);
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
        }
        return 20;
    }

    public static int domLengthNormalized(final byte[] urlHashBytes) {
        return domLengthEstimation(urlHashBytes) << 8 / 20;
    }

    public static final int domDomain(final byte[] urlHash) {
        // returns the ID of the domain of the domain
        assert (urlHash != null);
        assert (urlHash.length == 12 || urlHash.length == 6) : "urlhash = " + UTF8.String(urlHash);
        return (Base64Order.enhancedCoder.decodeByte(urlHash[(urlHash.length == 12) ? 11 : 5]) & 28) >> 2;
    }


    public static boolean isDomDomain(final byte[] urlHash, final int id) {
        return domDomain(urlHash) == id;
    }

    /**
     *  checks for local/global IP range and local IP
     */
    @Override
    public final boolean isLocal() {
        if (this.isFile()) return true;
        if (this.hash == null) synchronized (this.protocol) {
            // this is synchronized because another thread may also call the same method in between
            // that is the reason that this.hash is checked again
            if (this.hash == null) this.hash = urlHashComputation();
        }
        return domDomain(this.hash) == 7;
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
