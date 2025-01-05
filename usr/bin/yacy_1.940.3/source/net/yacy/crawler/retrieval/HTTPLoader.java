// HTTPLoader.java
// ---------------
// SPDX-FileCopyrightText: 2004 Michael Peter Christen <mc@yacy.net)>
// SPDX-License-Identifier: GPL-2.0-or-later
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.HTTPInputStream;
import net.yacy.cora.util.StrictLimitInputStream;
import net.yacy.crawler.CrawlSwitchboard;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.data.Latency;
import net.yacy.kelondro.io.ByteCount;
import net.yacy.kelondro.util.Formatter;
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
        // load fulltext of html page
        Latency.updateBeforeLoad(entry.url());
        final long start = System.currentTimeMillis();
        final Response doc = load(entry, profile, DEFAULT_CRAWLING_RETRY_COUNT, maxFileSize, blacklistType, agent);
        Latency.updateAfterLoad(entry.url(), System.currentTimeMillis() - start);
        return doc;
    }

    /**
     * Open an input stream on a requested HTTP resource. When the resource content size is small
     * (lower than {@link Response#CRAWLER_MAX_SIZE_TO_CACHE}, fully load it and use a ByteArrayInputStream instance.
     * @param request
     * @param profile crawl profile
     * @param retryCount remaining redirect retries count
     * @param maxFileSize max file size to load. -1 means no limit.
     * @param blacklistType blacklist type to use
     * @param agent agent identifier
     * @return a response with full meta data and embedding on open input stream on content. Don't forget to close the stream.
     * @throws IOException when an error occurred
     */
    public StreamResponse openInputStream(
            final Request request, CrawlProfile profile, final int retryCount,
            final int maxFileSize, final BlacklistType blacklistType, final ClientIdentification.Agent agent
        ) throws IOException {
        if (retryCount < 0) {
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, "retry counter exceeded", -1);
            throw new IOException( "retry counter exceeded for URL " + request.url().toString() + ". Processing aborted.$");
        }
        DigestURL url = request.url();

        final String host = url.getHost();
        if (host == null || host.length() < 2) {
            throw new IOException("host is not well-formed: '" + host + "'");
        }
        final String path = url.getFile();
        int port = url.getPort();
        final boolean ssl = url.getProtocol().equals("https");
        if (port < 0)
            port = (ssl) ? 443 : 80;

        // check if url is in blacklist
        final String hostlow = host.toLowerCase(Locale.ROOT);
        if (blacklistType != null && Switchboard.urlBlacklist.isListed(blacklistType, hostlow, path)) {
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.FINAL_LOAD_CONTEXT,
                    "url in blacklist", -1);
            throw new IOException("CRAWLER Rejecting URL '" + request.url().toString() + "'. URL is in blacklist.$");
        }

        // resolve yacy and yacyh domains
        final AlternativeDomainNames yacyResolver = this.sb.peers;
        if (yacyResolver != null) {
            final String yAddress = yacyResolver.resolve(host);
            if (yAddress != null) {
                url = new DigestURL(url.getProtocol() + "://" + yAddress + path);
            }
        }

        // create a request header
        final RequestHeader requestHeader = createRequestheader(request, agent);

        // HTTP-Client
        try (final HTTPClient client = new HTTPClient(agent)) {
            client.setRedirecting(false); // we want to handle redirection
                                            // ourselves, so we don't index pages
                                            // twice
            client.setTimout(this.socketTimeout);
            client.setHeader(requestHeader.entrySet());

            // send request
            client.GET(url, false);
            final StatusLine statusline = client.getHttpResponse().getStatusLine();
            final int statusCode = statusline.getStatusCode();
            final ResponseHeader responseHeader = new ResponseHeader(statusCode, client.getHttpResponse().getAllHeaders());
            String requestURLString = request.url().toNormalform(true);

            // check redirection
            if (statusCode > 299 && statusCode < 310) {
                // client.close(); // explicit close caused: warning: [try] explicit call to close() on an auto-closeable resource

                final DigestURL redirectionUrl = extractRedirectURL(request, profile, url, statusline, responseHeader, requestURLString);

                if (this.sb.getConfigBool(SwitchboardConstants.CRAWLER_FOLLOW_REDIRECTS, true)) {
                    // we have two use cases here: loading from a crawl or just
                    // loading the url. Check this:
                    if (profile != null && !CrawlSwitchboard.DEFAULT_PROFILES.contains(profile.name())) {
                        // put redirect url on the crawler queue to repeat a
                        // double-check
                        /* We have to clone the request instance and not to modify directly its URL,
                         * otherwise the stackCrawl() function would reject it, because detecting it as already in the activeWorkerEntries */
                        Request redirectedRequest = new Request(request.initiator(),
                                redirectionUrl,
                                request.referrerhash(),
                                request.name(),
                                request.appdate(),
                                request.profileHandle(),
                                request.depth(),
                                request.timezoneOffset());
                        String rejectReason = this.sb.crawlStacker.stackCrawl(redirectedRequest);
                        if(rejectReason != null) {
                            throw new IOException("CRAWLER Redirect of URL=" + requestURLString + " aborted. Reason : " + rejectReason);
                        }
                        // in the end we must throw an exception (even if this is
                        // not an error, just to abort the current process
                        throw new IOException("CRAWLER Redirect of URL=" + requestURLString + " to "
                                + redirectionUrl.toNormalform(false) + " placed on crawler queue for double-check");
                    }

                    // if we are already doing a shutdown we don't need to retry
                    // crawling
                    if (Thread.currentThread().isInterrupted()) {
                        this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile,
                                FailCategory.FINAL_LOAD_CONTEXT, "server shutdown", statusCode);
                        throw new IOException(
                                "CRAWLER Redirect of URL=" + requestURLString + " aborted because of server shutdown.$");
                    }

                    // check if the redirected URL is the same as the requested URL
                    // this shortcuts a time-out using retryCount
                    if (redirectionUrl.equals(url)) {
                        this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.TEMPORARY_NETWORK_FAILURE, "redirect to same url", -1);
                        throw new IOException( "retry counter exceeded for URL " + request.url().toString() + ". Processing aborted.$");
                    }

                    // retry crawling with new url
                    request.redirectURL(redirectionUrl);
                    return openInputStream(request, profile, retryCount - 1, maxFileSize, blacklistType, agent);
                }
                // we don't want to follow redirects
                this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.FINAL_PROCESS_CONTEXT, "redirection not wanted", statusCode);
                throw new IOException("REJECTED UNWANTED REDIRECTION '" + statusline + "' for URL '" + requestURLString + "'$");
            } else if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION) {
                // the transfer is ok

                /*
                 * When content is not large (less than Response.CRAWLER_MAX_SIZE_TO_CACHE), we have better cache it if cache is enabled and url is not local
                 */
                long contentLength = client.getHttpResponse().getEntity().getContentLength();
                InputStream contentStream;
                if (profile != null && profile.storeHTCache() && contentLength > 0 && contentLength < (Response.CRAWLER_MAX_SIZE_TO_CACHE) && !url.isLocal()) {
                    byte[] content = null;
                    try {
                        content = HTTPClient.getByteArray(client.getHttpResponse().getEntity(), maxFileSize);
                        Cache.store(url, responseHeader, content);
                    } catch (final IOException e) {
                        this.log.warn("cannot write " + url + " to Cache (3): " + e.getMessage(), e);
                    } finally {
                        // client.close(); // explicit close caused: warning: [try] explicit call to close() on an auto-closeable resource
                    }

                    contentStream = new ByteArrayInputStream(content);
                } else {
                    /*
                     * Content length may already be known now : check it before opening a stream
                     */
                    if (maxFileSize >= 0 && contentLength > maxFileSize) {
                        throw new IOException("Content to download exceed maximum value of " + maxFileSize + " bytes");
                    }
                    /*
                     * Create a HTTPInputStream delegating to
                     * client.getContentstream(). Close method will ensure client is
                     * properly closed.
                     */
                    contentStream = new HTTPInputStream(client);
                    /* Anticipated content length may not be already known or incorrect : let's apply now the same eventual content size restriction as when loading in a byte array */
                    if(maxFileSize >= 0) {
                        contentStream = new StrictLimitInputStream(contentStream, maxFileSize,
                                "Content to download exceed maximum value of " + Formatter.bytesToString(maxFileSize));
                    }
                }

                return new StreamResponse(new Response(request, requestHeader, responseHeader, profile, false, null), contentStream);
            } else {
                // client.close(); // explicit close caused: warning: [try] explicit call to close() on an auto-closeable resource
                // if the response has not the right response type then reject file
                this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile,
                        FailCategory.TEMPORARY_NETWORK_FAILURE, "wrong http status code", statusCode);
                throw new IOException("REJECTED WRONG STATUS TYPE '" + statusline
                        + "' for URL '" + requestURLString + "'$");
            }
        }
    }

    /**
     * Extract redirect URL from response header. Status code is supposed to be between 299 and 310. Parameters must not be null.
     * @return redirect URL
     * @throws IOException when an error occured
     */
    private DigestURL extractRedirectURL(final Request request, CrawlProfile profile, DigestURL url,
            final StatusLine statusline, final ResponseHeader responseHeader, String requestURLString)
                    throws IOException {
        // read redirection URL
        String redirectionUrlString = responseHeader.get(HeaderFramework.LOCATION);
        redirectionUrlString = redirectionUrlString == null ? "" : redirectionUrlString.trim();

        if (redirectionUrlString.isEmpty()) {
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile,
                    FailCategory.TEMPORARY_NETWORK_FAILURE,
                    "no redirection url provided, field '" + HeaderFramework.LOCATION + "' is empty", statusline.getStatusCode());
            throw new IOException("REJECTED EMTPY REDIRECTION '" + statusline
                    + "' for URL '" + requestURLString + "'$");
        }

        // normalize URL
        final DigestURL redirectionUrl = DigestURL.newURL(request.url(), redirectionUrlString);

        // restart crawling with new url
        this.log.info("CRAWLER Redirection detected ('" + statusline + "') for URL "
                + requestURLString);
        this.log.info("CRAWLER ..Redirecting request to: " + redirectionUrl.toNormalform(false));

        this.sb.webStructure.generateCitationReference(url, redirectionUrl);

        if (this.sb.getConfigBool(SwitchboardConstants.CRAWLER_RECORD_REDIRECTS, true)) {
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile,
                    FailCategory.FINAL_REDIRECT_RULE, "redirect to " + redirectionUrlString, statusline.getStatusCode());
        }
        return redirectionUrl;
    }

    /**
     * Create request header for loading content.
     * @param request search request
     * @param agent agent identification information
     * @return a request header
     * @throws IOException when an error occured
     */
    private RequestHeader createRequestheader(final Request request, final ClientIdentification.Agent agent)
            throws IOException {
        final RequestHeader requestHeader = new RequestHeader();
        requestHeader.put(HeaderFramework.USER_AGENT, agent.userAgent);
        if (request.referrerhash() != null) {
                    String refererURL = this.sb.getURL(request.referrerhash());
                    if (refererURL != null) {
                        requestHeader.put(RequestHeader.REFERER, refererURL);
                    }
        }

        requestHeader.put(HeaderFramework.ACCEPT, this.sb.getConfig("crawler.http.accept", DEFAULT_ACCEPT));
        requestHeader.put(HeaderFramework.ACCEPT_LANGUAGE,
                this.sb.getConfig("crawler.http.acceptLanguage", DEFAULT_LANGUAGE));
        requestHeader.put(HeaderFramework.ACCEPT_CHARSET,
                this.sb.getConfig("crawler.http.acceptCharset", DEFAULT_CHARSET));
        requestHeader.put(HeaderFramework.ACCEPT_ENCODING,
                this.sb.getConfig("crawler.http.acceptEncoding", DEFAULT_ENCODING));
        return requestHeader;
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
        final String hostlow = host.toLowerCase(Locale.ROOT);
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
        final RequestHeader requestHeader = createRequestheader(request, agent);

        // HTTP-Client
        try (final HTTPClient client = new HTTPClient(agent)) {
            client.setRedirecting(false); // we want to handle redirection ourselves, so we don't index pages twice
            client.setTimout(this.socketTimeout);
            client.setHeader(requestHeader.entrySet());

            // send request
            final byte[] responseBody = client.GETbytes(url, this.sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"), this.sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""), maxFileSize, false);
            final int statusCode = client.getHttpResponse().getStatusLine().getStatusCode();
            final ResponseHeader responseHeader = new ResponseHeader(statusCode, client.getHttpResponse().getAllHeaders());
            String requestURLString = request.url().toNormalform(true);

            // check redirection
            if (statusCode > 299 && statusCode < 310) {

                final DigestURL redirectionUrl = extractRedirectURL(request, profile, url, client.getHttpResponse().getStatusLine(),
                        responseHeader, requestURLString);

                if (this.sb.getConfigBool(SwitchboardConstants.CRAWLER_FOLLOW_REDIRECTS, true)) {
                    // we have two use cases here: loading from a crawl or just loading the url. Check this:
                    if (profile != null && !CrawlSwitchboard.DEFAULT_PROFILES.contains(profile.name())) {
                        // put redirect url on the crawler queue to repeat a double-check
                        /* We have to clone the request instance and not to modify directly its URL,
                         * otherwise the stackCrawl() function would reject it, because detecting it as already in the activeWorkerEntries */
                        Request redirectedRequest = new Request(request.initiator(),
                                redirectionUrl,
                                request.referrerhash(),
                                request.name(),
                                request.appdate(),
                                request.profileHandle(),
                                request.depth(),
                                request.timezoneOffset());
                        String rejectReason = this.sb.crawlStacker.stackCrawl(redirectedRequest);
                        // in the end we must throw an exception (even if this is not an error, just to abort the current process
                        if(rejectReason != null) {
                            throw new IOException("CRAWLER Redirect of URL=" + requestURLString + " aborted. Reason : " + rejectReason);
                        }
                        throw new IOException("CRAWLER Redirect of URL=" + requestURLString + " to " + redirectionUrl.toNormalform(false) + " placed on crawler queue for double-check");
                    }

                    // if we are already doing a shutdown we don't need to retry crawling
                    if (Thread.currentThread().isInterrupted()) {
                        this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), profile, FailCategory.FINAL_LOAD_CONTEXT, "server shutdown", statusCode);
                        throw new IOException("CRAWLER Redirect of URL=" + requestURLString + " aborted because of server shutdown.$");
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
        final String hostlow = host.toLowerCase(Locale.ROOT);
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

        try (final HTTPClient client = new HTTPClient(agent)) {
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
        }
        return response;
    }

}
