/**
 *  HttpHeader
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 16.12.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
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


package net.yacy.cora.lod.vocabulary;

import java.util.Set;

import net.yacy.cora.lod.Literal;
import net.yacy.cora.lod.Vocabulary;

public enum HttpHeader implements Vocabulary {

    //The following properties may appear in nodes of type Request:

    accept, // representing an Accept header,
    acceptCharset, // representing an Accept-Charset header,
    acceptEncoding, // representing an Accept-Encoding header,
    acceptLanguage, // representing an Accept-Language header,
    authorization, // representing an Authorization header,
    expect, // representing an Expect header,
    from, // representing a From header,
    host, // representing a Host header,
    ifMatch, // representing an If-Match header,
    ifModifiedSince, // representing an If-Modified-Since header,
    ifNoneMatch, // representing an If-None-Match header,
    ifRange, // representing an If-Range header,
    ifUnmodifiedSince, // representing an If-Unmodified-Since header,
    maxForwards, // representing a Max-Forwards header,
    proxyAuthorization, // representing a Proxy-Authorization header,
    range, // representing a Range header,
    referer, // representing a Referer header,
    te, // representing a TE header,
    userAgent, // representing a User-Agent header.

    //The following properties may appear in nodes of type Response:
    acceptRanges, // representing a Accept-Ranges header,
    age, // representing an Age header,
    etag, // representing an ETag header,
    location, // representing a Location header,
    proxyAuthenticate, // representing a Proxy-Authenticate header,
    retryAfter, // representing a Retry-After header,
    server, // representing a Server header,
    vary, // representing a Vary header,
    wwwAuthenticate, // representing a WWW-Authenticate header.

    //The following properties may appear in nodes of type Request or Response:
    allow, // representing an Allow header,
    cacheControl, // representing a Cache-Control header,
    connection, // representing a Connection header,
    contentEncoding, // representing a Content-Encoding header,
    contentLanguage, // representing a Content-Language header,
    contentLength, // representing a Content-Length header,
    contentLocation, // representing a Content-Location header,
    contentMD5, // representing a Content-MD5 header,
    contentRange, // representing a Content-Range header,
    contentType, // representing a Content-Type header,
    date, // representing a Date header,
    expires, // representing an Expires header,
    lastModified, // representing a Last-Modified header,
    pragma, // representing a Pragma header,
    trailer, // representing a Trailer header,
    transferEncoding, // representing a Transfer-Encoding header,
    upgrade, // representing an Upgrade header,
    via, // representing a Via header,
    warning; // representing a Warning header.

    public final static String NAMESPACE = "http://www.w3.org/1999/xx/http#";
    public final static String PREFIX = "http";

    private final String predicate;

    private HttpHeader() {
        this.predicate = NAMESPACE +  this.name();
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getNamespacePrefix() {
        return PREFIX;
    }

    @Override
    public Set<Literal> getLiterals() {
        return null;
    }

    @Override
    public String getPredicate() {
        return this.predicate;
    }

    @Override
    public String getURIref() {
        return PREFIX + ':' + this.name();
    }
}
