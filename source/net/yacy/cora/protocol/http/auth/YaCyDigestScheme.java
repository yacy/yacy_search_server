// YaCyDigestScheme.java
// Copyright 2017 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.cora.protocol.http.auth;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.auth.UnsupportedDigestAlgorithmException;
import org.apache.http.message.BasicHeaderValueFormatter;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.BufferedHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.EncodingUtils;

/**
 * Overrides the Apache DigestScheme to support credentials with YaCy encoded
 * passwords instead of clear-text ones. This implementation is directly derived
 * from the {@link DigestScheme} from Apache HTTPClient 4.5.3.
 */
public class YaCyDigestScheme extends DigestScheme {

    private static final long serialVersionUID = 3883908186234566916L;

    /**
     * Hexa values used when creating 32 character long digest in HTTP DigestScheme
     * in case of authentication.
     *
     * @see #encode(byte[])
     */
    private static final char[] HEXADECIMAL = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
        'e', 'f'
    };

    /** Whether the digest authentication process is complete */
    private boolean complete;

    private static final int QOP_UNKNOWN = -1;
    private static final int QOP_MISSING = 0;
    private static final int QOP_AUTH_INT = 1;
    private static final int QOP_AUTH = 2;

    private String lastNonce;
    private long nounceCount;
    private String cnonce;
    private String a2;

    /**
     * @since 4.3
     */
    public YaCyDigestScheme(final Charset credentialsCharset) {
        super(credentialsCharset);
        this.complete = false;
    }

    public YaCyDigestScheme() {
        this(Consts.ASCII);
    }

    /**
	 * Produces a digest authorization string for the given set of
	 * {@link Credentials}, method name and URI. The credentials password is
	 * supposed to be a YaCy encoded password (see adminAccountBase64MD5
	 * property in yacy.conf) and not a clear-text value.
	 *
	 * @param credentials
	 *            A set of credentials to be used for authentication
	 * @param request
	 *            The request being authenticated
	 *
	 * @throws org.apache.http.auth.InvalidCredentialsException
	 *             if authentication credentials are not valid or not applicable
	 *             for this authentication scheme
	 * @throws AuthenticationException
	 *             if authorization string cannot be generated due to an
	 *             authentication failure
	 *
	 * @return a digest authorization string
	 */
    @Override
    public Header authenticate(
            final Credentials credentials,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {

        Args.notNull(credentials, "Credentials");
        Args.notNull(request, "HTTP request");
        if (getParameter("realm") == null) {
            throw new AuthenticationException("missing realm in challenge");
        }
        if (getParameter("nonce") == null) {
            throw new AuthenticationException("missing nonce in challenge");
        }
        // Add method name and request-URI to the parameter map
        getParameters().put("methodname", request.getRequestLine().getMethod());
        getParameters().put("uri", request.getRequestLine().getUri());
        if (getParameter("charset") == null) {
            getParameters().put("charset", StandardCharsets.US_ASCII.name()); // default according to https://hc.apache.org/httpcomponents-client-4.2.x/tutorial/html/authentication.html
        }
        return createDigestHeader(credentials, request);
    }

    private static MessageDigest createMessageDigest(
            final String digAlg) throws UnsupportedDigestAlgorithmException {
        try {
            return MessageDigest.getInstance(digAlg);
        } catch (final Exception e) {
            throw new UnsupportedDigestAlgorithmException(
              "Unsupported algorithm in HTTP Digest authentication: "
               + digAlg);
        }
    }

    /**
     * Creates digest-response header as defined in RFC2617.
     *
     * @param credentials User credentials
     *
     * @return The digest-response as String.
     */
    private Header createDigestHeader(
            final Credentials credentials,
            final HttpRequest request) throws AuthenticationException {
        final String uri = getParameter("uri");
        final String realm = getParameter("realm");
        final String nonce = getParameter("nonce");
        final String opaque = getParameter("opaque");
        final String method = getParameter("methodname");
        String algorithm = getParameter("algorithm");
        // If an algorithm is not specified, default to MD5.
        if (algorithm == null) {
            algorithm = "MD5";
        }

        final Set<String> qopset = new HashSet<String>(8);
        int qop = QOP_UNKNOWN;
        final String qoplist = getParameter("qop");
        if (qoplist != null) {
            final StringTokenizer tok = new StringTokenizer(qoplist, ",");
            while (tok.hasMoreTokens()) {
                final String variant = tok.nextToken().trim();
                qopset.add(variant.toLowerCase(Locale.ROOT));
            }
            if (request instanceof HttpEntityEnclosingRequest && qopset.contains("auth-int")) {
                qop = QOP_AUTH_INT;
            } else if (qopset.contains("auth")) {
                qop = QOP_AUTH;
            }
        } else {
            qop = QOP_MISSING;
        }

        if (qop == QOP_UNKNOWN) {
            throw new AuthenticationException("None of the qop methods is supported: " + qoplist);
        }

        String charset = getParameter("charset");
        if (charset == null) {
            charset = "ISO-8859-1";
        }

        String digAlg = algorithm;
        if (digAlg.equalsIgnoreCase("MD5-sess")) {
            digAlg = "MD5";
        }

        final MessageDigest digester;
        try {
            digester = createMessageDigest(digAlg);
        } catch (final UnsupportedDigestAlgorithmException ex) {
            throw new AuthenticationException("Unsuppported digest algorithm: " + digAlg);
        }

        final String uname = credentials.getUserPrincipal().getName();
        /* Modification for YaCy Digest authentication using the encoded password from yacy.conf */
        String pwd = credentials.getPassword();
        if(pwd.startsWith("MD5:")) {
        	pwd = pwd.substring("MD5:".length());
        }

        if (nonce.equals(this.lastNonce)) {
            nounceCount++;
        } else {
            nounceCount = 1;
            cnonce = null;
            lastNonce = nonce;
        }
        final StringBuilder sb = new StringBuilder(256);
        final Formatter formatter = new Formatter(sb, Locale.US);
        formatter.format("%08x", Long.valueOf(nounceCount));
        formatter.close();
        final String nc = sb.toString();

        if (cnonce == null) {
            cnonce = createCnonce();
        }

        a2 = null;
        // 3.2.2.2: Calculating digest
        String hasha1 ;
        if (algorithm.equalsIgnoreCase("MD5-sess")) {
            // H( unq(username-value) ":" unq(realm-value) ":" passwd )
            //      ":" unq(nonce-value)
            //      ":" unq(cnonce-value)

            // calculated one per session
            sb.setLength(0);
            /* Modification for YaCy Digest Authentication : the pwd value is already the result of MD5(userName:realm:password)
            sb.append(uname).append(':').append(realm).append(':').append(pwd);
            final String checksum = encode(digester.digest(EncodingUtils.getBytes(sb.toString(), charset)));
            sb.setLength(0);
            sb.append(checksum).append(':').append(nonce).append(':').append(cnonce);
            a1 = sb.toString();*/
            sb.append(pwd).append(':').append(nonce).append(':').append(cnonce);
            hasha1 = encode(digester.digest(EncodingUtils.getBytes(sb.toString(), charset)));
        } else {
            // unq(username-value) ":" unq(realm-value) ":" passwd
            /* Modification for YaCy Digest Authentication : the pwd value is already the result of MD5(userName:realm:password)
            sb.setLength(0);
            sb.append(uname).append(':').append(realm).append(':').append(pwd);
            a1 = sb.toString();*/
            hasha1 = pwd;
        }
        /*Modification for YaCy Digest Authentication
        final String hasha1 = encode(digester.digest(EncodingUtils.getBytes(a1, charset)));*/

        if (qop == QOP_AUTH) {
            // Method ":" digest-uri-value
            a2 = method + ':' + uri;
        } else if (qop == QOP_AUTH_INT) {
            // Method ":" digest-uri-value ":" H(entity-body)
            HttpEntity entity = null;
            if (request instanceof HttpEntityEnclosingRequest) {
                entity = ((HttpEntityEnclosingRequest) request).getEntity();
            }
            if (entity != null && !entity.isRepeatable()) {
                // If the entity is not repeatable, try falling back onto QOP_AUTH
                if (qopset.contains("auth")) {
                    qop = QOP_AUTH;
                    a2 = method + ':' + uri;
                } else {
                    throw new AuthenticationException("Qop auth-int cannot be used with " +
                            "a non-repeatable entity");
                }
            } else {
                final HttpEntityDigester entityDigester = new HttpEntityDigester(digester);
                try {
                    if (entity != null) {
                        entity.writeTo(entityDigester);
                    }
                    entityDigester.close();
                } catch (final IOException ex) {
                    throw new AuthenticationException("I/O error reading entity content", ex);
                }
                a2 = method + ':' + uri + ':' + encode(entityDigester.getDigest());
            }
        } else {
            a2 = method + ':' + uri;
        }

        final String hasha2 = encode(digester.digest(EncodingUtils.getBytes(a2, charset)));

        // 3.2.2.1

        final String digestValue;
        if (qop == QOP_MISSING) {
            sb.setLength(0);
            sb.append(hasha1).append(':').append(nonce).append(':').append(hasha2);
            digestValue = sb.toString();
        } else {
            sb.setLength(0);
            sb.append(hasha1).append(':').append(nonce).append(':').append(nc).append(':')
                .append(cnonce).append(':').append(qop == QOP_AUTH_INT ? "auth-int" : "auth")
                .append(':').append(hasha2);
            digestValue = sb.toString();
        }

        final String digest = encode(digester.digest(EncodingUtils.getAsciiBytes(digestValue)));

        final CharArrayBuffer buffer = new CharArrayBuffer(128);
        if (isProxy()) {
            buffer.append(AUTH.PROXY_AUTH_RESP);
        } else {
            buffer.append(AUTH.WWW_AUTH_RESP);
        }
        buffer.append(": Digest ");

        final List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>(20);
        params.add(new BasicNameValuePair("username", uname));
        params.add(new BasicNameValuePair("realm", realm));
        params.add(new BasicNameValuePair("nonce", nonce));
        params.add(new BasicNameValuePair("uri", uri));
        params.add(new BasicNameValuePair("response", digest));

        if (qop != QOP_MISSING) {
            params.add(new BasicNameValuePair("qop", qop == QOP_AUTH_INT ? "auth-int" : "auth"));
            params.add(new BasicNameValuePair("nc", nc));
            params.add(new BasicNameValuePair("cnonce", cnonce));
        }
        // algorithm cannot be null here
        params.add(new BasicNameValuePair("algorithm", algorithm));
        if (opaque != null) {
            params.add(new BasicNameValuePair("opaque", opaque));
        }

        for (int i = 0; i < params.size(); i++) {
            final BasicNameValuePair param = params.get(i);
            if (i > 0) {
                buffer.append(", ");
            }
            final String name = param.getName();
            final boolean noQuotes = ("nc".equals(name) || "qop".equals(name)
                    || "algorithm".equals(name));
            BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(buffer, param, !noQuotes);
        }
        return new BufferedHeader(buffer);
    }

    /**
     * Encodes the 128 bit (16 bytes) MD5 digest into a 32 characters long
     * <CODE>String</CODE> according to RFC 2617.
     *
     * @param binaryData array containing the digest
     * @return encoded MD5, or <CODE>null</CODE> if encoding failed
     */
    static String encode(final byte[] binaryData) {
        final int n = binaryData.length;
        final char[] buffer = new char[n * 2];
        for (int i = 0; i < n; i++) {
            final int low = (binaryData[i] & 0x0f);
            final int high = ((binaryData[i] & 0xf0) >> 4);
            buffer[i * 2] = HEXADECIMAL[high];
            buffer[(i * 2) + 1] = HEXADECIMAL[low];
        }

        return new String(buffer);
    }


    /**
     * Creates a random cnonce value based on the current time.
     *
     * @return The cnonce value as String.
     */
    public static String createCnonce() {
        final SecureRandom rnd = new SecureRandom();
        final byte[] tmp = new byte[8];
        rnd.nextBytes(tmp);
        return encode(tmp);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("DIGEST [complete=").append(complete)
                .append(", nonce=").append(lastNonce)
                .append(", nc=").append(nounceCount)
                .append("]");
        return builder.toString();
    }

}
