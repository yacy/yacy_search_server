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

package net.yacy.crawler.retrieval;

import java.io.IOException;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.Latency;
import net.yacy.kelondro.io.ByteCount;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.http.AlternativeDomainNames;

public final class HTTPLoader {

    private static final String DEFAULT_ENCODING = "gzip,deflate";
    private static final String DEFAULT_LANGUAGE = "en-us,en;q=0.5";
    private static final String DEFAULT_CHARSET = "ISO-8859-1,utf-8;q=0.7,*;q=0.7";
    public  static final String DEFAULT_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    public  static final int    DEFAULT_MAXFILESIZE = 1024 * 1024 * 10;
    public  static final int    DEFAULT_CRAWLING_RETRY_COUNT = 5;

    /**
     * The socket timeout that should be used
     */
    private final int socketTimeout;
    private final Switchboard sb;
    private final ConcurrentLog log;

    public HTTPLoader(final Switchboard sb, final ConcurrentLog theLog) {
        this.sb = sb;
        this.log = theLog;

        // refreshing timeout value
        this.socketTimeout = (int) sb.getConfigLong("crawler.clientTimeout", 30000);
    }

    public Response load(final Request entry, CrawlProfile profile, final int maxFileSize, final BlacklistType blacklistType, final ClientIdentification.Agent agent) throws IOException {
        Latency.updateBeforeLoad(entry.url());
        final long start = System.currentTimeMillis();
        final Response doc = load(entry, profile, DEFAULT_CRAWLING_RETRY_COUNT, maxFileSize, blacklistType, agent);
        Latency.updateAfterLoad(entry.url(), System.currentTimeMillis() - start);
        return doc;
    }

    private Response load(final Request request, CrawlProfile profile, final int retryCount, final int maxFileSize, final BlacklistType blacklistType, final ClientIdentification.Agent agent) throws IOException {

        if (retryCount < 0) {
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, "retry counter exceeded", -1);
            throw new IOException("retry counter exceeded for URL " + request.url().toString() + ". Processing aborted.$");
        }

        DigestURL url = request.url();

        final String host = url.getHost();
        if (host == null || host.length() < 2) throw new IOException("host is not well-formed: '" + host + "'");
        final String path = url.getFile();
        int port = url.getPort();
        final boolean ssl = url.getProtocol().equals("https");
        if (port < 0) port = (ssl) ? 443 : 80;

        // check if url is in blacklist
        final String hostlow = host.toLowerCase();
        if (blacklistType != null && Switchboard.urlBlacklist.isListed(blacklistType, hostlow, path)) {
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.FINAL_LOAD_CONTEXT, "url in blacklist", -1);
            throw new IOException("CRAWLER Rejecting URL '" + request.url().toString() + "'. URL is in blacklist.$");
        }

        // resolve yacy and yacyh domains
        final AlternativeDomainNames yacyResolver = this.sb.peers;
        if(yacyResolver != null) {
        	final String yAddress = yacyResolver.resolve(host);
        	if(yAddress != null) {
        		url = new DigestURL(url.getProtocol() + "://" + yAddress + path);
        	}
        }

        // take a file from the net
        Response response = null;

        // create a request header
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(HeaderFramework.USER_AGENT, agent.userAgent);
        DigestURL refererURL = null;
        if (request.referrerhash() != null) refererURL = this.sb.getURL(request.referrerhash());
        if (refererURL != null) requestHeader.put(RequestHeader.REFERER, refererURL.toNormalform(true));
        requestHeader.put(HeaderFramework.ACCEPT, this.sb.getConfig("crawler.http.accept", DEFAULT_ACCEPT));
        requestHeader.put(HeaderFramework.ACCEPT_LANGUAGE, this.sb.getConfig("crawler.http.acceptLanguage", DEFAULT_LANGUAGE));
        requestHeader.put(HeaderFramework.ACCEPT_CHARSET, this.sb.getConfig("crawler.http.acceptCharset", DEFAULT_CHARSET));
        requestHeader.put(HeaderFramework.ACCEPT_ENCODING, this.sb.getConfig("crawler.http.acceptEncoding", DEFAULT_ENCODING));

        // HTTP-Client
        final HTTPClient client = new HTTPClient(agent);
        client.setRedirecting(false); // we want to handle redirection ourselves, so we don't index pages twice
        client.setTimout(this.socketTimeout);
        client.setHeader(requestHeader.entrySet());

        // send request
    	final byte[] responseBody = client.GETbytes(url, sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"), sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""), maxFileSize, false);
        final int statusCode = client.getHttpResponse().getStatusLine().getStatusCode();
    	final ResponseHeader responseHeader = new ResponseHeader(statusCode, client.getHttpResponse().getAllHeaders());
        String requestURLString = request.url().toNormalform(true);

        // check redirection
    	if (statusCode > 299 && statusCode < 310) {

    	    // read redirection URL
            String redirectionUrlString = responseHeader.get(HeaderFramework.LOCATION);
            redirectionUrlString = redirectionUrlString == null ? "" : redirectionUrlString.trim();

            if (redirectionUrlString.isEmpty()) {
                this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, "no redirection url provided, field '" + HeaderFramework.LOCATION + "' is empty", statusCode);
                throw new IOException("REJECTED EMTPY REDIRECTION '" + client.getHttpResponse().getStatusLine() + "' for URL '" + requestURLString + "'$");
            }

            // normalize URL
            final DigestURL redirectionUrl = DigestURL.newURL(request.url(), redirectionUrlString);

            // restart crawling with new url
            this.log.info("CRAWLER Redirection detected ('" + client.getHttpResponse().getStatusLine() + "') for URL " + requestURLString);
            this.log.info("CRAWLER ..Redirecting request to: " + redirectionUrl);

            this.sb.webStructure.generateCitationReference(url, redirectionUrl);
            
            if (this.sb.getConfigBool(SwitchboardConstants.CRAWLER_RECORD_REDIRECTS, true)) {
                this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.FINAL_REDIRECT_RULE, "redirect to " + redirectionUrlString, statusCode);
            }

    	    if (this.sb.getConfigBool(SwitchboardConstants.CRAWLER_FOLLOW_REDIRECTS, true)) {
                // if we are already doing a shutdown we don't need to retry crawling
                if (Thread.currentThread().isInterrupted()) {
                    this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.FINAL_LOAD_CONTEXT, "server shutdown", statusCode);
                    throw new IOException("CRAWLER Retry of URL=" + requestURLString + " aborted because of server shutdown.$");
                }

                // retry crawling with new url
                request.redirectURL(redirectionUrl);
                return load(request, profile, retryCount - 1, maxFileSize, blacklistType, agent);
    	    }
            // we don't want to follow redirects
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.FINAL_PROCESS_CONTEXT, "redirection not wanted", statusCode);
            throw new IOException("REJECTED UNWANTED REDIRECTION '" + client.getHttpResponse().getStatusLine() + "' for URL '" + requestURLString + "'$");
        } else if (responseBody == null) {
    	    // no response, reject file
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, "no response body", statusCode);
            throw new IOException("REJECTED EMPTY RESPONSE BODY '" + client.getHttpResponse().getStatusLine() + "' for URL '" + requestURLString + "'$");
    	} else if (statusCode == 200 || statusCode == 203) {
            // the transfer is ok

            // we write the new cache entry to file system directly
            final long contentLength = responseBody.length;
            ByteCount.addAccountCount(ByteCount.CRAWLER, contentLength);

            // check length again in case it was not possible to get the length before loading
            if (maxFileSize >= 0 && contentLength > maxFileSize) {
            	this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.FINAL_PROCESS_CONTEXT, "file size limit exceeded", statusCode);
            	throw new IOException("REJECTED URL " + request.url() + " because file size '" + contentLength + "' exceeds max filesize limit of " + maxFileSize + " bytes. (GET)$");
            }

            // create a new cache entry
            response = new Response(
                    request,
                    requestHeader,
                    responseHeader,
                    profile,
                    false,
                    responseBody
            );

            return response;
    	} else {
            // if the response has not the right response type then reject file
        	this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, "wrong http status code", statusCode);
            throw new IOException("REJECTED WRONG STATUS TYPE '" + client.getHttpResponse().getStatusLine() + "' for URL '" + requestURLString + "'$");
        }
    }

    public static Response load(final Request request, ClientIdentification.Agent agent) throws IOException {
        return load(request, agent, 3);
    }

    private static Response load(final Request request, ClientIdentification.Agent agent, final int retryCount) throws IOException {

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
        if (Switchboard.urlBlacklist != null && Switchboard.urlBlacklist.isListed(BlacklistType.CRAWLER, hostlow, path)) {
            throw new IOException("CRAWLER Rejecting URL '" + request.url().toString() + "'. URL is in blacklist.");
        }

        // take a file from the net
        Response response = null;

        // create a request header
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(HeaderFramework.USER_AGENT, agent.userAgent);
        requestHeader.put(HeaderFramework.ACCEPT_LANGUAGE, DEFAULT_LANGUAGE);
        requestHeader.put(HeaderFramework.ACCEPT_CHARSET, DEFAULT_CHARSET);
        requestHeader.put(HeaderFramework.ACCEPT_ENCODING, DEFAULT_ENCODING);

        final HTTPClient client = new HTTPClient(agent);
        client.setTimout(20000);
        client.setHeader(requestHeader.entrySet());
        	final byte[] responseBody = client.GETbytes(request.url(), null, null, false);
            final int code = client.getHttpResponse().getStatusLine().getStatusCode();
        	final ResponseHeader header = new ResponseHeader(code, client.getHttpResponse().getAllHeaders());
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
                        null,
                        false,
                        responseBody
                );

                return response;
            } else if (code > 299 && code < 310) {
                if (header.containsKey(HeaderFramework.LOCATION)) {
                    // getting redirection URL
                	String redirectionUrlString = header.get(HeaderFramework.LOCATION);
                    redirectionUrlString = redirectionUrlString.trim();

                    if (redirectionUrlString.isEmpty()) {
                        throw new IOException("CRAWLER Redirection of URL=" + request.url().toString() + " aborted. Location header is empty.");
                    }

                    // normalizing URL
                    final DigestURL redirectionUrl = DigestURL.newURL(request.url(), redirectionUrlString);


                    // if we are already doing a shutdown we don't need to retry crawling
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("CRAWLER Retry of URL=" + request.url().toString() + " aborted because of server shutdown.");
                    }

                    // retry crawling with new url
                    request.redirectURL(redirectionUrl);
                    return load(request, agent, retryCount - 1);
                }
            } else {
                // if the response has not the right response type then reject file
            	throw new IOException("REJECTED WRONG STATUS TYPE '" + client.getHttpResponse().getStatusLine() + "' for URL " + request.url().toString());
            }
        return response;
    }

}
