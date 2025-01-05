// LoaderDispatcher.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.10.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.repository;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.solr.FailCategory;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.FTPLoader;
import net.yacy.crawler.retrieval.FileLoader;
import net.yacy.crawler.retrieval.HTTPLoader;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.retrieval.Response;
import net.yacy.crawler.retrieval.SMBLoader;
import net.yacy.crawler.retrieval.StreamResponse;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.TextParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;

public final class LoaderDispatcher {

    private final static int accessTimeMaxsize = 1000;
    private final static ConcurrentLog log = new ConcurrentLog("LOADER");
    private static final ConcurrentHashMap<String, Long> accessTime = new ConcurrentHashMap<String, Long>(); // to protect targets from DDoS

    private final Switchboard sb;
    private final HashSet<String> supportedProtocols;
    private final HTTPLoader httpLoader;
    private final FTPLoader ftpLoader;
    private final SMBLoader smbLoader;
    private final FileLoader fileLoader;
    private final ConcurrentHashMap<DigestURL, Semaphore> loaderSteering; // a map that delivers a 'finish' semaphore for urls

    public LoaderDispatcher(final Switchboard sb) {
        this.sb = sb;
        this.supportedProtocols = new HashSet<String>(Arrays.asList(new String[]{"http","https","ftp","smb","file"}));

        // initiate loader objects
        this.httpLoader = new HTTPLoader(sb, LoaderDispatcher.log);
        this.ftpLoader = new FTPLoader(sb, LoaderDispatcher.log);
        this.smbLoader = new SMBLoader(sb, LoaderDispatcher.log);
        this.fileLoader = new FileLoader(sb, LoaderDispatcher.log);
        this.loaderSteering = new ConcurrentHashMap<DigestURL, Semaphore>();
    }

    public boolean isSupportedProtocol(final String protocol) {
        if ((protocol == null) || (protocol.isEmpty())) return false;
        return this.supportedProtocols.contains(protocol.trim().toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    public HashSet<String> getSupportedProtocols() {
        return (HashSet<String>) this.supportedProtocols.clone();
    }

    /**
     * generate a request object
     * @param url the target url
     * @param forText shows that this was a for-text crawling request
     * @param global shows that this was a global crawling request
     * @return the request object
     */
    public Request request(
            final DigestURL url,
            final boolean forText,
            final boolean global
                    ) {
        CrawlProfile profile =
                (forText) ?
                    ((global) ?
                        this.sb.crawler.defaultTextSnippetGlobalProfile :
                        this.sb.crawler.defaultTextSnippetLocalProfile)
                    :
                    ((global) ?
                        this.sb.crawler.defaultMediaSnippetGlobalProfile :
                        this.sb.crawler.defaultMediaSnippetLocalProfile);
        return new Request(
                ASCII.getBytes(this.sb.peers.mySeed().hash),
                    url,
                    null,
                    "",
                    new Date(),
                    profile.handle(),
                    0,
                    profile.timezoneOffset());
    }

    public void load(final DigestURL url, final CacheStrategy cacheStratgy, final int maxFileSize, final File targetFile, BlacklistType blacklistType, ClientIdentification.Agent agent) throws IOException {

        final byte[] b = load(request(url, false, true), cacheStratgy, maxFileSize, blacklistType, agent).getContent();
        if (b == null) throw new IOException("load == null");
        final File tmp = new File(targetFile.getAbsolutePath() + ".tmp");

        // transaction-safe writing
        final File parent = targetFile.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        FileUtils.copy(b, tmp);
        tmp.renameTo(targetFile);
    }

    public Response load(final Request request, final CacheStrategy cacheStrategy, final BlacklistType blacklistType, ClientIdentification.Agent agent) throws IOException {
    	return load(request, cacheStrategy, protocolMaxFileSize(request.url()), blacklistType, agent);
    }

    /**
     * loads a resource from cache or web/ftp/smb/file
     * on concurrent execution waits max 5 sec for the prev. loader to fill the cache (except for CacheStrategy.NOCACHE)
     *
     * @param request the request essentials
     * @param cacheStrategy strategy according to NOCACHE, IFFRESH, IFEXIST, CACHEONLY
     * @param maxFileSize
     * @param blacklistType
     * @param agent
     * @return the loaded entity in a Response object
     * @throws IOException
     */
    public Response load(final Request request, final CacheStrategy cacheStrategy, final int maxFileSize, final BlacklistType blacklistType, ClientIdentification.Agent agent) throws IOException {
        Semaphore check = this.loaderSteering.get(request.url());
        if (check != null && cacheStrategy != CacheStrategy.NOCACHE) {
            // a loading process is going on for that url
            //ConcurrentLog.info("LoaderDispatcher", "waiting for " + request.url().toNormalform(true));
            long t = System.currentTimeMillis();
            try { check.tryAcquire(5, TimeUnit.SECONDS);} catch (final InterruptedException e) {}
            ConcurrentLog.info("LoaderDispatcher", "waited " + (System.currentTimeMillis() - t) + " ms for " + request.url().toNormalform(true));
            // now the process may have terminated and we run a normal loading
            // which may be successful faster because of a cache hit
        }

        this.loaderSteering.put(request.url(), new Semaphore(0));
        try {
            final Response response = loadInternal(request, cacheStrategy, maxFileSize, blacklistType, agent);
            // finally block cleans up loaderSteering and semaphore
            return response;
        } catch (final IOException e) {
        	/* Do not wrap an IOException in an unnecessary supplementary IOException */
            throw e;
        } catch (final Throwable e) {
            throw new IOException(e);
        } finally {
            // release the semaphore anyway
            check = this.loaderSteering.remove(request.url()); // = next caller goes directly to loadInternal (is ok we just wanted to fill cash)
            if (check != null) check.release(1000); // don't block any other
        }
    }

    /**
     * load a resource from the web, from ftp, from smb or a file
     * @param request the request essentials
     * @param cacheStratgy strategy according to NOCACHE, IFFRESH, IFEXIST, CACHEONLY
     * @return the loaded entity in a Response object
     * @throws IOException
     */
    private Response loadInternal(final Request request, CacheStrategy cacheStrategy, final int maxFileSize, final BlacklistType blacklistType, ClientIdentification.Agent agent) throws IOException {
        // get the protocol of the next URL
        final DigestURL url = request.url();
        if (url.isFile() || url.isSMB()) cacheStrategy = CacheStrategy.NOCACHE; // load just from the file system
        final String protocol = url.getProtocol();
        final String host = url.getHost();
        final CrawlProfile crawlProfile = request.profileHandle() == null ? null : this.sb.crawler.get(UTF8.getBytes(request.profileHandle()));

        // check if url is in blacklist
        if (blacklistType != null && host != null && Switchboard.urlBlacklist.isListed(blacklistType, host.toLowerCase(Locale.ROOT), url.getFile())) {
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), crawlProfile, FailCategory.FINAL_LOAD_CONTEXT, "url in blacklist", -1);
            throw new IOException("DISPATCHER Rejecting URL '" + request.url().toString() + "'. URL is in blacklist.$");
        }

        // check if we have the page in the cache
        Response response = loadFromCache(request, cacheStrategy, agent, url, crawlProfile);
        if(response != null) {
        	return response;
        }

        // check case where we want results from the cache exclusively, and never from the Internet (offline mode)
        if (cacheStrategy == CacheStrategy.CACHEONLY) {
            // we had a chance to get the content from the cache .. its over. We don't have it.
            throw new IOException("cache only strategy");
        }

        // now forget about the cache, nothing there. Try to load the content from the Internet

        // check access time: this is a double-check (we checked possibly already in the balancer)
        // to make sure that we don't DoS the target by mistake
        checkAccessTime(agent, url);

        // now it's for sure that we will access the target. Remember the access time
        if (host != null) {
            if (accessTime.size() > accessTimeMaxsize) accessTime.clear(); // prevent a memory leak here
            accessTime.put(host, System.currentTimeMillis());
        }

        // load resource from the internet
        if (protocol.equals("http") || protocol.equals("https")) {
            response = this.httpLoader.load(request, crawlProfile, maxFileSize, blacklistType, agent);
        } else if (protocol.equals("ftp")) {
            response = this.ftpLoader.load(request, true);
        } else if (protocol.equals("smb")) {
            response = this.smbLoader.load(request, true);
        } else if (protocol.equals("file")) {
            response = this.fileLoader.load(request, true);
        } else {
            throw new IOException("Unsupported protocol '" + protocol + "' in url " + url);
        }
        if (response == null) {
            throw new IOException("no response (NULL) for url " + url);
        }
        if (response.getContent() == null) {
            throw new IOException("empty response (code " + response.getStatus() + ") for url " + url.toNormalform(true));
        }

        // we got something. Now check if we want to store that to the cache
        // first check looks if we want to store the content to the cache
        if (crawlProfile == null || !crawlProfile.storeHTCache()) {
            // no caching wanted. Thats ok, do not write any message
            return response;
        }
        // second check tells us if the protocol tells us something about caching
        final String storeError = response.shallStoreCacheForCrawler();
        if (storeError == null) {
            try {
            	/* Important : we associate here the loaded content with the URL response.url().
            	 * On eventual redirection(s), response.url() provides the last redirection location.
            	 * If instead we associated content with the initial url (beginning of the redirection(s) chain),
            	 * the parsers would then have a wrong base URL when following links with relative URLs. */
                Cache.store(response.url(), response.getResponseHeader(), response.getContent());
            } catch (final IOException e) {
                LoaderDispatcher.log.warn("cannot write " + response.url() + " to Cache (3): " + e.getMessage(), e);
            }
        } else {
            LoaderDispatcher.log.warn("cannot write " + response.url() + " to Cache (4): " + storeError);
        }
        return response;
    }

    /**
     * Try loading requested resource from cache according to cache strategy
     * @param request request to resource
     * @param cacheStrategy cache strategy to use
     * @param agent agent identifier
     * @param url resource url
     * @param crawlProfile crawl profile
     * @return a Response instance when resource could be loaded from cache, or null.
     * @throws IOException when an error occured
     */
	private Response loadFromCache(final Request request, CacheStrategy cacheStrategy, ClientIdentification.Agent agent,
			final DigestURL url, final CrawlProfile crawlProfile) throws IOException {
		Response response = null;
		if (cacheStrategy != CacheStrategy.NOCACHE && crawlProfile != null) {
            // we have passed a first test if caching is allowed
            // now see if there is a cache entry

            final ResponseHeader cachedResponse = (url.isLocal()) ? null : Cache.getResponseHeader(url.hash());
            if (cachedResponse != null && Cache.hasContent(url.hash())) {
                // yes we have the content

                // create request header values and a response object because we need that
                // in case that we want to return the cached content in the next step
                final RequestHeader requestHeader = new RequestHeader();
                requestHeader.put(HeaderFramework.USER_AGENT, agent.userAgent);
                String refererURL = null;
                if (request.referrerhash() != null) refererURL = this.sb.getURL(request.referrerhash());
                if (refererURL != null) requestHeader.put(RequestHeader.REFERER, refererURL);
                response = new Response(
                        request,
                        requestHeader,
                        cachedResponse,
                        crawlProfile,
                        true,
                        null);

                // check which caching strategy shall be used
                if (cacheStrategy == CacheStrategy.IFEXIST || cacheStrategy == CacheStrategy.CACHEONLY) {
                    // well, just take the cache and don't care about freshness of the content
                    final byte[] content = Cache.getContent(url.hash());
                    if (content != null) {
                        LoaderDispatcher.log.info("cache hit/useall for: " + url.toNormalform(true));
                        response.setContent(content);
                        return response;
                    }
                }

                // now the cacheStrategy must be CACHE_STRATEGY_IFFRESH, that means we should do a proxy freshness test
                //assert cacheStrategy == CacheStrategy.IFFRESH : "cacheStrategy = " + cacheStrategy;
                if (response.isFreshForProxy()) {
                    final byte[] content = Cache.getContent(url.hash());
                    if (content != null) {
                        LoaderDispatcher.log.info("cache hit/fresh for: " + url.toNormalform(true));
                        response.setContent(content);
                        return response;
                    }
                }
                LoaderDispatcher.log.info("cache hit/stale for: " + url.toNormalform(true));
                /* Cached content can not be used : we return a null response to ensure callers will detect no cache response is available */
                response = null;
            } else if (cachedResponse != null) {
                LoaderDispatcher.log.warn("HTCACHE contained response header, but not content for url " + url.toNormalform(true));
            }
        }
		return response;
	}

    /**
     * Open an InputStream on a resource from the web, from ftp, from smb or a file
     * @param request the request essentials
     * @param cacheStratgy strategy according to NOCACHE, IFFRESH, IFEXIST, CACHEONLY
     * @return an open ImageInputStream. Don't forget to close it once used!
     * @throws IOException when url is malformed, blacklisted, or CacheStrategy is CACHEONLY and content is unavailable
     */
    private StreamResponse openInputStreamInternal(final Request request, CacheStrategy cacheStrategy, final int maxFileSize, final BlacklistType blacklistType, ClientIdentification.Agent agent) throws IOException {
        // get the protocol of the next URL
        final DigestURL url = request.url();
		if (url.isFile() || url.isSMB()) {
			cacheStrategy = CacheStrategy.NOCACHE; // load just from the file
													// system
		}
        final String protocol = url.getProtocol();
        final String host = url.getHost();
        final CrawlProfile crawlProfile = request.profileHandle() == null ? null : this.sb.crawler.get(UTF8.getBytes(request.profileHandle()));

        // check if url is in blacklist
        if (blacklistType != null && host != null && Switchboard.urlBlacklist.isListed(blacklistType, host.toLowerCase(Locale.ROOT), url.getFile())) {
            this.sb.crawlQueues.errorURL.push(request.url(), request.depth(), crawlProfile, FailCategory.FINAL_LOAD_CONTEXT, "url in blacklist", -1);
            throw new IOException("DISPATCHER Rejecting URL '" + request.url().toString() + "'. URL is in blacklist.$");
        }

        // check if we have the page in the cache
        Response cachedResponse = loadFromCache(request, cacheStrategy, agent, url, crawlProfile);
		if (cachedResponse != null) {
			return new StreamResponse(cachedResponse, new ByteArrayInputStream(cachedResponse.getContent()));
		}

        // check case where we want results from the cache exclusively, and never from the Internet (offline mode)
        if (cacheStrategy == CacheStrategy.CACHEONLY) {
            // we had a chance to get the content from the cache .. its over. We don't have it.
            throw new IOException("cache only strategy");
        }

        // now forget about the cache, nothing there. Try to load the content from the Internet

        // check access time: this is a double-check (we checked possibly already in the balancer)
        // to make sure that we don't DoS the target by mistake
		checkAccessTime(agent, url);

        // now it's for sure that we will access the target. Remember the access time
        if (host != null) {
            if (accessTime.size() > accessTimeMaxsize) accessTime.clear(); // prevent a memory leak here
            accessTime.put(host, System.currentTimeMillis());
        }

        // load resource from the internet
        StreamResponse response;
        if (protocol.equals("http") || protocol.equals("https")) {
        	response = this.httpLoader.openInputStream(request, crawlProfile, 2, maxFileSize, blacklistType, agent);
        } else if (protocol.equals("ftp")) {
        	response = this.ftpLoader.openInputStream(request, true);
        } else if (protocol.equals("smb")) {
            response = this.smbLoader.openInputStream(request, true);
        } else if (protocol.equals("file")) {
            response = this.fileLoader.openInputStream(request, true, maxFileSize);
        } else {
            throw new IOException("Unsupported protocol '" + protocol + "' in url " + url);
        }

        return response;
    }


    /**
     * Check access time: this is a double-check (we checked possibly already in the balancer)
     * to make sure that we don't DoS the target by mistake
     * @param agent agent identifier
     * @param url target url
     */
	private void checkAccessTime(ClientIdentification.Agent agent, final DigestURL url) {
		if (!url.isLocal()) {
			String host = url.getHost();
			final Long lastAccess = accessTime.get(host);
			long wait = 0;
			if (lastAccess != null)
				wait = Math.max(0, agent.minimumDelta + lastAccess.longValue() - System.currentTimeMillis());
			if (wait > 0) {
				// force a sleep here. Instead just sleep we clean up the
				// accessTime map
				final long untilTime = System.currentTimeMillis() + wait;
				cleanupAccessTimeTable(untilTime);
				if (System.currentTimeMillis() < untilTime) {
					long frcdslp = untilTime - System.currentTimeMillis();
					LoaderDispatcher.log.info("Forcing sleep of " + frcdslp + " ms for host " + host);
					try {
						Thread.sleep(frcdslp);
					} catch (final InterruptedException ee) {
					}
				}
			}
		}
	}

	/**
	 * @param url the URL of a resource to load
	 * @return the crawler configured maximum size allowed to load for the protocol of the URL
	 */
    public int protocolMaxFileSize(final DigestURL url) {
    	if (url.isHTTP() || url.isHTTPS()) {
    		return this.sb.getConfigInt("crawler.http.maxFileSize", HTTPLoader.DEFAULT_MAXFILESIZE);
    	}
    	if (url.isFTP()) {
    		return this.sb.getConfigInt("crawler.ftp.maxFileSize", (int) FTPLoader.DEFAULT_MAXFILESIZE);
    	}
    	if (url.isSMB()) {
    		return this.sb.getConfigInt("crawler.smb.maxFileSize", (int) SMBLoader.DEFAULT_MAXFILESIZE);
    	}
    	if(url.isFile()) {
    		return this.sb.getConfigInt("crawler.file.maxFileSize", FileLoader.DEFAULT_MAXFILESIZE);
    	}
    	return Integer.MAX_VALUE;
    }

    /**
     * load the url as byte[] content from the web or the cache
     * @param request
     * @param cacheStrategy
     * @param timeout
     * @return the content as {@link byte[]}
     * @throws IOException
     */
    public byte[] loadContent(final Request request, final CacheStrategy cacheStrategy, BlacklistType blacklistType, final ClientIdentification.Agent agent) throws IOException {
        // try to download the resource using the loader
        final Response entry = load(request, cacheStrategy, blacklistType, agent);
        if (entry == null) return null; // not found in web

        // read resource body (if it is there)
        return entry.getContent();
    }

    /**
     * Open the URL as an InputStream from the web or the cache
     * @param request must be not null
     * @param cacheStrategy cache strategy to use
     * @param blacklistType black list
     * @param agent agent identification for HTTP requests
     * @param maxFileSize max file size to load. -1 means no limit.
     * @return a response with full meta data and embedding on open input stream on content. Don't forget to close the stream.
     * @throws IOException when url is malformed or blacklisted
     */
	public StreamResponse openInputStream(final Request request, final CacheStrategy cacheStrategy,
			BlacklistType blacklistType, final ClientIdentification.Agent agent, final int maxFileSize) throws IOException {
		StreamResponse response;

		Semaphore check = this.loaderSteering.get(request.url());
		if (check != null && cacheStrategy != CacheStrategy.NOCACHE) {
			// a loading process is going on for that url
			long t = System.currentTimeMillis();
			try {
				check.tryAcquire(5, TimeUnit.SECONDS);
			} catch (final InterruptedException e) {
			}
			ConcurrentLog.info("LoaderDispatcher",
					"waited " + (System.currentTimeMillis() - t) + " ms for " + request.url().toNormalform(true));
			// now the process may have terminated and we run a normal loading
			// which may be successful faster because of a cache hit
		}

		this.loaderSteering.put(request.url(), new Semaphore(0));
		try {
			response = openInputStreamInternal(request, cacheStrategy, maxFileSize, blacklistType, agent);
		} catch(IOException ioe) {
			/* Do not re encapsulate any eventual IOException in an IOException */
			throw ioe;
		} catch (final Throwable e) {
			throw new IOException(e);
		} finally {
			// release the semaphore anyway
			check = this.loaderSteering.remove(request.url());
			if (check != null) {
				check.release(1000); // don't block any other
			}
		}

		return response;
	}

    /**
     * Open the URL as an InputStream from the web or the cache. Apply the default per protocol configured maximum file size limit.
     * @param request must be not null
     * @param cacheStrategy cache strategy to use
     * @param blacklistType black list
     * @param agent agent identification for HTTP requests
     * @return a response with full meta data and embedding on open input stream on content. Don't forget to close the stream.
     * @throws IOException when url is malformed or blacklisted
     */
	public StreamResponse openInputStream(final Request request, final CacheStrategy cacheStrategy,
			BlacklistType blacklistType, final ClientIdentification.Agent agent) throws IOException {
		final int maxFileSize = protocolMaxFileSize(request.url());
		return this.openInputStream(request, cacheStrategy, blacklistType, agent, maxFileSize);
	}

    public Document[] loadDocuments(final Request request, final CacheStrategy cacheStrategy, final int maxFileSize, BlacklistType blacklistType, final ClientIdentification.Agent agent) throws IOException, Parser.Failure {

        // load resource
        final Response response = load(request, cacheStrategy, maxFileSize, blacklistType, agent);
        final DigestURL url = request.url();
        if (response == null) throw new IOException("no Response for url " + url);

        // if it is still not available, report an error
        if (response.getContent() == null || response.getResponseHeader() == null) throw new IOException("no Content available for url " + url);

        // parse resource
        Document[] documents = response.parse();

        String x_robots_tag = response.getResponseHeader().getXRobotsTag();
        if (x_robots_tag.indexOf("noindex",0) >= 0) {
            for (Document d: documents) d.setIndexingDenied(true);
        }

        return documents;
    }

    public Document loadDocument(final DigestURL location, final CacheStrategy cachePolicy, BlacklistType blacklistType, final ClientIdentification.Agent agent) throws IOException {
        // load resource
        Request request = request(location, true, false);
        final Response response = this.load(request, cachePolicy, blacklistType, agent);
        final DigestURL url = request.url();
        if (response == null) throw new IOException("no Response for url " + url);

        // if it is still not available, report an error
        if (response.getContent() == null || response.getResponseHeader() == null) throw new IOException("no Content available for url " + url);

        // parse resource
        try {
            Document[] documents = response.parse();
            Document merged = Document.mergeDocuments(location, response.getMimeType(), documents);

            String x_robots_tag = response.getResponseHeader().getXRobotsTag();
            if (x_robots_tag.indexOf("noindex",0) >= 0) merged.setIndexingDenied(true);

            return merged;
        } catch(final Parser.Failure e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Similar to the loadDocument method, but streaming the resource content when possible instead of fully loading it in memory.
     * @param location URL of the resource to load
     * @param cachePolicy cache policy strategy
     * @param blacklistType blacklist to use
     * @param agent user agent identifier
     * @return on parsed document or null when an error occurred while parsing
     * @throws IOException when the content can not be fetched or no parser support it
     */
    public Document loadDocumentAsStream(final DigestURL location, final CacheStrategy cachePolicy,
    		final BlacklistType blacklistType, final ClientIdentification.Agent agent) throws IOException {
        // load resource
        Request request = request(location, true, false);
        final StreamResponse streamResponse = this.openInputStream(request, cachePolicy, blacklistType, agent);
        final Response response = streamResponse.getResponse();
        final DigestURL url = request.url();
        if (response == null) throw new IOException("no Response for url " + url);

        // if it is still not available, report an error
        if (streamResponse.getContentStream() == null || response.getResponseHeader() == null) {
        	throw new IOException("no Content available for url " + url);
        }

        // parse resource
        try {
            Document[] documents = streamResponse.parse();
            Document merged = Document.mergeDocuments(location, response.getMimeType(), documents);

            String x_robots_tag = response.getResponseHeader().getXRobotsTag();
            if (x_robots_tag.indexOf("noindex",0) >= 0) {
            	merged.setIndexingDenied(true);
            }

            return merged;
        } catch(final Parser.Failure e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
	 * Similar to the loadDocument method, but streaming the resource content
	 * when possible instead of fully loading it in memory.<br>
	 * Also try to limit the parser processing with a maximum total number of
	 * links detection (anchors, images links, media links...) or a maximum
	 * amount of content bytes to parse.<br>
	 * Limits apply only when the available parsers for the resource media type
	 * support parsing within limits (see
	 * {@link Parser#isParseWithLimitsSupported()}. When available parsers do
	 * not support parsing within limits, an exception is thrown when
	 * content size is beyond maxBytes.
	 *
	 * @param location
	 *            URL of the resource to load
	 * @param cachePolicy
	 *            cache policy strategy
	 * @param blacklistType
	 *            blacklist to use
	 * @param agent
	 *            user agent identifier
	 * @param maxLinks
	 *            the maximum total number of links to parse and add to the
	 *            result document
	 * @param maxBytes
	 *            the maximum number of content bytes to process
	 * @return on parsed document or null when an error occurred while parsing
	 * @throws IOException
	 *             when the content can not be fetched or no parser support it
	 */
    public Document loadDocumentAsLimitedStream(final DigestURL location, final CacheStrategy cachePolicy,
    		final BlacklistType blacklistType, final ClientIdentification.Agent agent, final int maxLinks, final long maxBytes) throws IOException {
        // load resource
        Request request = request(location, true, false);
        final StreamResponse streamResponse = this.openInputStream(request, cachePolicy, blacklistType, agent, -1);
        final Response response = streamResponse.getResponse();
        final DigestURL url = request.url();
        if (response == null) throw new IOException("no Response for url " + url);

        // if it is still not available, report an error
        if (streamResponse.getContentStream() == null || response.getResponseHeader() == null) {
        	throw new IOException("no Content available for url " + url);
        }

        // parse resource
        try {
            Document[] documents = streamResponse.parseWithLimits(maxLinks, maxBytes);
            Document merged = Document.mergeDocuments(location, response.getMimeType(), documents);

            String x_robots_tag = response.getResponseHeader().getXRobotsTag();
            if (x_robots_tag.indexOf("noindex",0) >= 0) {
            	merged.setIndexingDenied(true);
            }

            return merged;
        } catch(final Parser.Failure e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * load all links from a resource
     * @param url the url that shall be loaded
     * @param cacheStrategy the cache strategy
     * @return a map from URLs to the anchor texts of the urls
     * @throws IOException
     */
    public final Map<AnchorURL, String> loadLinks(
    		final DigestURL url,
    		final CacheStrategy cacheStrategy,
    		BlacklistType blacklistType,
    		final ClientIdentification.Agent agent,
    		final int timezoneOffset) throws IOException {
        final Response response = load(request(url, true, false), cacheStrategy, Integer.MAX_VALUE, blacklistType, agent);
        if (response == null) throw new IOException("response == null");
        final ResponseHeader responseHeader = response.getResponseHeader();
        if (response.getContent() == null) throw new IOException("resource == null");
        if (responseHeader == null) throw new IOException("responseHeader == null");

        Document[] documents = null;
        final String supportError = TextParser.supports(url, responseHeader.getContentType());
        if (supportError != null) throw new IOException("no parser support: " + supportError);
        try {
            documents = TextParser.parseSource(
                    url,
                    responseHeader.getContentType(),
                    responseHeader.getCharacterEncoding(),
                    response.profile().defaultValency(),
                    response.profile().valencySwitchTagNames(),
                    response.profile().scraper(),
                    timezoneOffset,
                    response.depth(),
                    response.getContent());
            if (documents == null) throw new IOException("document == null");
        } catch (final Exception e) {
            throw new IOException("parser error: " + e.getMessage());
        }

        return Document.getHyperlinks(documents, true);
    }

    public synchronized static void cleanupAccessTimeTable(final long timeout) {
    	final Iterator<Map.Entry<String, Long>> i = accessTime.entrySet().iterator();
        Map.Entry<String, Long> e;
        while (i.hasNext()) {
            e = i.next();
            if (System.currentTimeMillis() > timeout) break;
            if (System.currentTimeMillis() - e.getValue().longValue() > 1000) i.remove();
        }
    }

    public void loadIfNotExistBackground(final DigestURL url, final File cache, final int maxFileSize, BlacklistType blacklistType, final ClientIdentification.Agent agent) {
        new Loader(url, cache, maxFileSize, CacheStrategy.IFEXIST, blacklistType, agent).start();
    }

    public void loadIfNotExistBackground(final DigestURL url, final int maxFileSize, BlacklistType blacklistType, final ClientIdentification.Agent agent) {
        new Loader(url, null, maxFileSize, CacheStrategy.IFEXIST, blacklistType, agent).start();
    }

    private class Loader extends Thread {

        private final DigestURL url;
        private final File cache;
        private final int maxFileSize;
        private final CacheStrategy cacheStrategy;
        private final BlacklistType blacklistType;
        private final ClientIdentification.Agent agent;

        public Loader(final DigestURL url, final File cache, final int maxFileSize, final CacheStrategy cacheStrategy, BlacklistType blacklistType, final ClientIdentification.Agent agent) {
        	super("LoaderDispatcher.Loader");
            this.url = url;
            this.cache = cache;
            this.maxFileSize = maxFileSize;
            this.cacheStrategy = cacheStrategy;
            this.blacklistType = blacklistType;
            this.agent = agent;
        }

        @Override
        public void run() {
            if (this.cache != null && this.cache.exists()) return;
            try {
                // load from the net
                final Response response = load(request(this.url, false, true), this.cacheStrategy, this.maxFileSize, this.blacklistType, this.agent);
                final byte[] b = response.getContent();
                if (this.cache != null) FileUtils.copy(b, this.cache);
            } catch (final MalformedURLException e) {} catch (final IOException e) {}
        }
    }
}
