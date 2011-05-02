// HTTPLoader.java 
// ---------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 2006
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package de.anomic.crawler.retrieval;

import java.io.IOException;
import java.util.Date;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.io.ByteCount;
import net.yacy.kelondro.logging.Log;
import net.yacy.repository.Blacklist;

import de.anomic.crawler.CrawlProfile;
import de.anomic.crawler.Latency;
import de.anomic.http.server.AlternativeDomainNames;
import de.anomic.http.server.HTTPDemon;
import de.anomic.search.Segments;
import de.anomic.search.Switchboard;

public final class HTTPLoader {

    private static final String DEFAULT_ENCODING = "gzip,deflate";
    private static final String DEFAULT_LANGUAGE = "en-us,en;q=0.5";
    private static final String DEFAULT_CHARSET = "ISO-8859-1,utf-8;q=0.7,*;q=0.7";
    public  static final long   DEFAULT_MAXFILESIZE = 1024 * 1024 * 10;
    public  static final int    DEFAULT_CRAWLING_RETRY_COUNT = 5;
    
    /**
     * The socket timeout that should be used
     */
    private final int socketTimeout;
    private final Switchboard sb;
    private final Log log;
    
    public HTTPLoader(final Switchboard sb, final Log theLog) {
        this.sb = sb;
        this.log = theLog;
        
        // refreshing timeout value
        this.socketTimeout = (int) sb.getConfigLong("crawler.clientTimeout", 10000);
    }  
   
    public Response load(final Request entry, long maxFileSize, boolean checkBlacklist) throws IOException {
        long start = System.currentTimeMillis();
        Response doc = load(entry, DEFAULT_CRAWLING_RETRY_COUNT, maxFileSize, checkBlacklist);
        Latency.update(entry.url(), System.currentTimeMillis() - start);
        return doc;
    }
    
    private Response load(final Request request, final int retryCount, final long maxFileSize, final boolean checkBlacklist) throws IOException {

        if (retryCount < 0) {
            sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, "redirection counter exceeded", -1);
            throw new IOException("Redirection counter exceeded for URL " + request.url().toString() + ". Processing aborted.");
        }
        
        DigestURI url = request.url();
        
        final String host = url.getHost();
        if (host == null || host.length() < 2) throw new IOException("host is not well-formed: '" + host + "'");
        final String path = url.getFile();
        int port = url.getPort();
        final boolean ssl = url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
        
        // check if url is in blacklist
        final String hostlow = host.toLowerCase();
        if (checkBlacklist && Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, hostlow, path)) {
            sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, "url in blacklist", -1);
            throw new IOException("CRAWLER Rejecting URL '" + request.url().toString() + "'. URL is in blacklist.");
        }
        
        // resolve yacy and yacyh domains
        AlternativeDomainNames yacyResolver = HTTPDemon.getAlternativeResolver();
        if(yacyResolver != null) {
        	String yAddress = yacyResolver.resolve(host);
        	if(yAddress != null) {
        		url = new DigestURI(url.getProtocol() + "://" + yAddress + path);
        	}
        }
        
        // take a file from the net
        Response response = null;
        
        // create a request header
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(HeaderFramework.USER_AGENT, ClientIdentification.getUserAgent());
        DigestURI refererURL = null;
        if (request.referrerhash() != null) refererURL = sb.getURL(Segments.Process.LOCALCRAWLING, request.referrerhash());
        if (refererURL != null) requestHeader.put(RequestHeader.REFERER, refererURL.toNormalform(true, true));
        requestHeader.put(HeaderFramework.ACCEPT_LANGUAGE, sb.getConfig("crawler.http.acceptLanguage", DEFAULT_LANGUAGE));
        requestHeader.put(HeaderFramework.ACCEPT_CHARSET, sb.getConfig("crawler.http.acceptCharset", DEFAULT_CHARSET));
        requestHeader.put(HeaderFramework.ACCEPT_ENCODING, sb.getConfig("crawler.http.acceptEncoding", DEFAULT_ENCODING));

        // HTTP-Client
        final HTTPClient client = new HTTPClient();
        client.setRedirecting(false); // we want to handle redirection ourselves, so we don't index pages twice
        client.setTimout(socketTimeout);
        client.setHeader(requestHeader.entrySet());
            // send request
        	final byte[] responseBody = client.GETbytes(url, maxFileSize);
        	final ResponseHeader header = new ResponseHeader(client.getHttpResponse().getAllHeaders());
        	final int code = client.getHttpResponse().getStatusLine().getStatusCode();

        	if (code > 299 && code < 310) {
        		// redirection (content may be empty)
                if (header.containsKey(HeaderFramework.LOCATION)) {
                    // getting redirection URL
                	String redirectionUrlString = header.get(HeaderFramework.LOCATION);
                    redirectionUrlString = redirectionUrlString.trim();

                    if (redirectionUrlString.length() == 0) {
                        sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, "redirection header empy", code);
                        throw new IOException("CRAWLER Redirection of URL=" + request.url().toString() + " aborted. Location header is empty.");
                    }
                    
                    // normalizing URL
                    final DigestURI redirectionUrl = new DigestURI(MultiProtocolURI.newURL(request.url(), redirectionUrlString));

                    // restart crawling with new url
                    this.log.logInfo("CRAWLER Redirection detected ('" + client.getHttpResponse().getStatusLine() + "') for URL " + request.url().toString());
                    this.log.logInfo("CRAWLER ..Redirecting request to: " + redirectionUrl);

                    // if we are already doing a shutdown we don't need to retry crawling
                    if (Thread.currentThread().isInterrupted()) {
                        sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, "server shutdown", code);
                        throw new IOException("CRAWLER Retry of URL=" + request.url().toString() + " aborted because of server shutdown.");
                    }
                    
                    // check if the url was already indexed
                    final String dbname = sb.urlExists(Segments.Process.LOCALCRAWLING, redirectionUrl.hash());
                    if (dbname != null) {
                        sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, "redirection to double content", code);
                        throw new IOException("CRAWLER Redirection of URL=" + request.url().toString() + " ignored. The url appears already in db " + dbname);
                    }
                    
                    // retry crawling with new url
                    request.redirectURL(redirectionUrl);
                    return load(request, retryCount - 1, maxFileSize, checkBlacklist);
                } else {
                	// no redirection url provided
                    sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, "no redirection url provided", code);
                    throw new IOException("REJECTED EMTPY REDIRECTION '" + client.getHttpResponse().getStatusLine() + "' for URL " + request.url().toString());
                }
            } else if (responseBody == null) {
        	    // no response, reject file
                sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, "no response body", code);
                throw new IOException("REJECTED EMPTY RESPONSE BODY '" + client.getHttpResponse().getStatusLine() + "' for URL " + request.url().toString());
        	} else if (code == 200 || code == 203) {
                // the transfer is ok
                
                // we write the new cache entry to file system directly
                long contentLength = responseBody.length;
                ByteCount.addAccountCount(ByteCount.CRAWLER, contentLength);

                // check length again in case it was not possible to get the length before loading
                if (maxFileSize > 0 && contentLength > maxFileSize) {
                	sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, "file size limit exceeded", code);                    
                	throw new IOException("REJECTED URL " + request.url() + " because file size '" + contentLength + "' exceeds max filesize limit of " + maxFileSize + " bytes. (GET)");
                }

                // create a new cache entry
                final CrawlProfile profile = sb.crawler.getActive(request.profileHandle().getBytes());
                response = new Response(
                        request,
                        requestHeader,
                        header, 
                        Integer.toString(code),
                        profile,
                        responseBody
                );

                return response;
        	} else {
                // if the response has not the right response type then reject file
            	sb.crawlQueues.errorURL.push(request, sb.peers.mySeed().hash.getBytes(), new Date(), 1, "wrong http status code", code);
                throw new IOException("REJECTED WRONG STATUS TYPE '" + client.getHttpResponse().getStatusLine() + "' for URL " + request.url().toString());
            }
    }
    
    public static Response load(final Request request) throws IOException {
        return load(request, 3);
    }
    
    private static Response load(final Request request, int retryCount) throws IOException {

        if (retryCount < 0) {
            throw new IOException("Redirection counter exceeded for URL " + request.url().toString() + ". Processing aborted.");
        }
        
        final String host = request.url().getHost();
        if (host == null || host.length() < 2) throw new IOException("host is not well-formed: '" + host + "'");
        final String path = request.url().getFile();
        int port = request.url().getPort();
        final boolean ssl = request.url().getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;
        
        // check if url is in blacklist
        final String hostlow = host.toLowerCase();
        if (Switchboard.urlBlacklist != null && Switchboard.urlBlacklist.isListed(Blacklist.BLACKLIST_CRAWLER, hostlow, path)) {
            throw new IOException("CRAWLER Rejecting URL '" + request.url().toString() + "'. URL is in blacklist.");
        }
        
        // take a file from the net
        Response response = null;
        
        // create a request header
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(HeaderFramework.USER_AGENT, ClientIdentification.getUserAgent());
        requestHeader.put(HeaderFramework.ACCEPT_LANGUAGE, DEFAULT_LANGUAGE);
        requestHeader.put(HeaderFramework.ACCEPT_CHARSET, DEFAULT_CHARSET);
        requestHeader.put(HeaderFramework.ACCEPT_ENCODING, DEFAULT_ENCODING);

        final HTTPClient client = new HTTPClient();
        client.setTimout(20000);
        client.setHeader(requestHeader.entrySet());
        	final byte[] responseBody = client.GETbytes(request.url(), Long.MAX_VALUE);
        	final ResponseHeader header = new ResponseHeader(client.getHttpResponse().getAllHeaders());
        	final int code = client.getHttpResponse().getStatusLine().getStatusCode();
            // FIXME: 30*-handling (bottom) is never reached
            // we always get the final content because httpClient.followRedirects = true

        	if (responseBody != null && (code == 200 || code == 203)) {
                // the transfer is ok
        		
        		//statistics:
        		ByteCount.addAccountCount(ByteCount.CRAWLER, responseBody.length);
                
                // we write the new cache entry to file system directly

                // create a new cache entry
                response = new Response(
                        request,
                        requestHeader,
                        header, 
                        Integer.toString(code),
                        null,
                        responseBody
                );

                return response;
            } else if (code > 299 && code < 310) {
                if (header.containsKey(HeaderFramework.LOCATION)) {
                    // getting redirection URL
                	String redirectionUrlString = header.get(HeaderFramework.LOCATION);
                    redirectionUrlString = redirectionUrlString.trim();

                    if (redirectionUrlString.length() == 0) {
                        throw new IOException("CRAWLER Redirection of URL=" + request.url().toString() + " aborted. Location header is empty.");
                    }
                    
                    // normalizing URL
                    final DigestURI redirectionUrl = new DigestURI(MultiProtocolURI.newURL(request.url(), redirectionUrlString));

                    
                    // if we are already doing a shutdown we don't need to retry crawling
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("CRAWLER Retry of URL=" + request.url().toString() + " aborted because of server shutdown.");
                    }
                    
                    // retry crawling with new url
                    request.redirectURL(redirectionUrl);
                    return load(request, retryCount - 1);
                }
            } else {
                // if the response has not the right response type then reject file
            	throw new IOException("REJECTED WRONG STATUS TYPE '" + client.getHttpResponse().getStatusLine() + "' for URL " + request.url().toString());
            }
        return response;
    }
    
}
