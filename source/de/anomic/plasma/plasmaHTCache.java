// plasmaHTCache.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 12.02.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
   Class documentation:
   This class has two purposes:
   1. provide a object that carries path and header information
      that shall be used as objects within a scheduler's stack
   2. static methods for a cache control and cache aging
    the class shall also be used to do a cache-cleaning and index creation
*/

package de.anomic.plasma;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.LinkedList;
import java.util.TreeMap;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroMap;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverInstantThread;
import de.anomic.server.serverLog;
import de.anomic.tools.enumerateFiles;

public final class plasmaHTCache {

    private static final int stackLimit = 150;  // if we exceed that limit, we do not check idle
    private static final long idleDelay = 2000; // 2 seconds no hits until we think that we idle
    private static final long oneday = 1000 * 60 * 60 * 24; // milliseconds of a day
    
    private final plasmaSwitchboard switchboard;
    private kelondroMap responseHeaderDB = null;
    private final LinkedList cacheStack;
    private final TreeMap cacheAge; // a <date+hash, cache-path> - relation
    public  long currCacheSize;
    public  long maxCacheSize;
    private long lastAcc;
    private final File cachePath;
    public  static serverLog log;

    public static final int CACHE_UNFILLED          = 0; // default case without assignment
    public static final int CACHE_FILL              = 1; // this means: update == true
    public static final int CACHE_HIT               = 2; // the best case: reading from Cache
    public static final int CACHE_STALE_NO_RELOAD   = 3; // this shall be treated as a rare case that should not appear
    public static final int CACHE_STALE_RELOAD_GOOD = 4; // this means: update == true
    public static final int CACHE_STALE_RELOAD_BAD  = 5; // this updates only the responseHeader, not the content
    public static final int CACHE_PASSING           = 6; // does not touch cache, just passing

    public plasmaHTCache(plasmaSwitchboard switchboard, int bufferkb) {
	this.switchboard = switchboard;
        
        int loglevel = Integer.parseInt(switchboard.getConfig("plasmaLoglevel", "2"));
        this.log = new serverLog("HTCACHE", loglevel);
        
	// set cache path
	cachePath = new File(switchboard.getRootPath(),switchboard.getConfig("proxyCache","HTCACHE"));
	if (!(cachePath.exists())) {
	    // make the cache path
	    cachePath.mkdir();
	}
	if (!(cachePath.isDirectory())) {
	    // if the cache does not exists or is a file and not a directory, panic
	    System.out.println("the cache path " + cachePath.toString() + " is not a directory or does not exists and cannot be created");
	    System.exit(0);
	}

	// open the response header database
	File dbfile = new File(cachePath, "responseHeader.db");
	try {
            if (dbfile.exists())
		responseHeaderDB = new kelondroMap(new kelondroDyn(dbfile, bufferkb * 0x400));
	    else
		responseHeaderDB = new kelondroMap(new kelondroDyn(dbfile, bufferkb * 0x400, plasmaCrawlLURL.urlHashLength, 150));
	} catch (IOException e) {
	    System.out.println("the request header database could not be opened: " + e.getMessage());
	    System.exit(0);
	}

	// init stack
	cacheStack = new LinkedList();

	// init idle check
	lastAcc = System.currentTimeMillis();

	// init cache age and size management
	cacheAge = new TreeMap();
	currCacheSize = 0;
	maxCacheSize = Long.parseLong(switchboard.getConfig("proxyCacheSize", "2")); // this is megabyte
	maxCacheSize = maxCacheSize * 1024 * 1024; // now it's the number of bytes

	// start the cache startup thread
	// this will collect information about the current cache size and elements
	serverInstantThread.oneTimeJob(this, "cacheScan", log, 5000);
    }
    
    public void close() throws IOException {
        responseHeaderDB.close();
    }
    
    private String ageString(long date, File f) {
	String s = Integer.toHexString(f.hashCode());
	while (s.length() < 8) s = "0" + s;
	s = Long.toHexString(date) + s;
	while (s.length() < 24) s = "0" + s;
	return s;
    }
    
	public void cacheScan() {
	    //log.logSystem("STARTING CACHE SCANNING");
            kelondroMScoreCluster doms = new kelondroMScoreCluster();
	    int c = 0;
	    enumerateFiles ef = new enumerateFiles(cachePath, true, false, true);
	    File f;
	    while (ef.hasMoreElements()) {
		c++;
		f = (File) ef.nextElement();
		long d = f.lastModified();
                //System.out.println("Cache: " + dom(f));
                doms.incScore(dom(f));
		currCacheSize += f.length();
		cacheAge.put(ageString(d, f), f);
	    }
	    //System.out.println("%" + (String) cacheAge.firstKey() + "=" + cacheAge.get(cacheAge.firstKey()));
            long ageHours = (System.currentTimeMillis() -
				 Long.parseLong(((String) cacheAge.firstKey()).substring(0, 16), 16)) / 3600000;
            log.logSystem("CACHE SCANNED, CONTAINS " + c +
				   " FILES = " + currCacheSize/1048576 + "MB, OLDEST IS " + 
				   ((ageHours < 24) ? (ageHours + " HOURS") : ((ageHours / 24) + " DAYS")) +
				   " OLD");

            // start to prefetch ip's from dns                       
            String dom;
            long start = System.currentTimeMillis();
            String ip, result = "";
            c = 0;
            while ((doms.size() > 0) && (c < 50) && ((System.currentTimeMillis() - start) < 60000)) {
                dom = (String) doms.getMaxObject();
                ip = httpc.dnsResolve(dom);
                if (ip == null) break;
                result += ", " + dom + "=" + ip;
                log.logSystem("PRE-FILLED " + dom + "=" + ip);
                c++;
                doms.deleteScore(dom);
                // wait a short while to prevent that this looks like a DoS
                try {Thread.currentThread().sleep(100);} catch (InterruptedException e) {}
            }
            if (result.length() > 2) log.logSystem("PRE-FILLED DNS CACHE, FETCHED " + c +
				   " ADDRESSES: " + result.substring(2));
	}

        private String dom(File f) {
            String s = f.toString().substring(cachePath.toString().length() + 1);
            int p = s.indexOf("/");
            if (p < 0) p = s.indexOf("\\");
            if (p < 0) return null;
            return s.substring(0, p);
        }
    
    public httpHeader getCachedResponse(String urlHash) throws IOException {
        httpHeader header = new httpHeader(null, responseHeaderDB.get(urlHash));
        //System.out.println("DEBUG: getCachedResponse hash=" + urlHash + ", header=" + header.toString());
        return header;
    }

    public boolean idle() {
	return (System.currentTimeMillis() > (idleDelay + lastAcc));
    }
    
    public boolean full() {
	return (cacheStack.size() > stackLimit);
    }

    public boolean empty() {
	return (cacheStack.size() == 0);
    }

    synchronized public void stackProcess(Entry entry) throws IOException {
	lastAcc = System.currentTimeMillis();
	if (full())
	    process(entry);
	else
	    cacheStack.add(entry);
    }
	
    synchronized public void stackProcess(Entry entry, byte[] cacheArray) throws IOException {
	lastAcc = System.currentTimeMillis();
	entry.cacheArray = cacheArray;
	if (full())
	    process(entry);
	else
	    cacheStack.add(entry);
    }

    public int size() {
        return cacheStack.size();
    }
    
    synchronized public void process(Entry entry) throws IOException {

        if (entry == null) return;
        
	// store response header
	if ((entry.status == CACHE_FILL) ||
	    (entry.status == CACHE_STALE_RELOAD_GOOD) ||
	    (entry.status == CACHE_STALE_RELOAD_BAD)) {
	    responseHeaderDB.set(entry.nomalizedURLHash, entry.responseHeader);
	}

	// work off unwritten files and undone parsing
	String storeError = null;
	if (((entry.status == CACHE_FILL) || (entry.status == CACHE_STALE_RELOAD_GOOD)) &&
	    ((storeError = entry.shallStoreCache()) == null)) {

	    // write file if not written yet
	    if (entry.cacheArray != null) try {
		if (entry.cacheFile.exists()) {
                    currCacheSize -= entry.cacheFile.length();
                    entry.cacheFile.delete();
                }
		entry.cacheFile.getParentFile().mkdirs();
		log.logInfo("WRITE FILE (" + entry.cacheArray.length + " bytes) " + entry.cacheFile);
                serverFileUtils.write(entry.cacheArray, entry.cacheFile);
		log.logDebug("AFTER WRITE cacheArray = " + entry.cacheFile + ": " + ((entry.cacheArray == null) ? "empty" : "full"));
		//entry.cacheArray = null;
	    } catch (FileNotFoundException e) {
		// this is the case of a "(Not a directory)" error, which should be prohibited
		// by the shallStoreCache() property. However, sometimes the error still occurs
		// In this case do nothing.
                log.logError("File storage failed: " + e.getMessage());
	    }
	    
	    // update statistics
	    currCacheSize += entry.cacheFile.length();
	    cacheAge.put(ageString(entry.cacheFile.lastModified(), entry.cacheFile), entry.cacheFile);

	    // enqueue in switchboard
	    switchboard.enQueue(entry);
	} else if (entry.status == CACHE_PASSING) {
	    // even if the file should not be stored in the cache, it can be used to be indexed
	    if (storeError != null) log.logDebug("NOT STORED " + entry.cacheFile + ":" + storeError);

	    // enqueue in switchboard
	    switchboard.enQueue(entry);
	}

	// write log

	    switch (entry.status) {
	    case CACHE_UNFILLED:
		log.logInfo("CACHE UNFILLED: " + entry.cacheFile); break;
	    case CACHE_FILL:
		log.logInfo("CACHE FILL: " + entry.cacheFile +
			    ((entry.cacheArray == null) ? "" : " (cacheArray is filled)") +
			    ((entry.scraper    == null) ? "" : " (scraper is filled)"));
			    break;
	    case CACHE_HIT:
		log.logInfo("CACHE HIT: " + entry.cacheFile); break;
	    case CACHE_STALE_NO_RELOAD:
		log.logInfo("CACHE STALE, NO RELOAD: " + entry.cacheFile); break;
	    case CACHE_STALE_RELOAD_GOOD:
		log.logInfo("CACHE STALE, NECESSARY RELOAD: " + entry.cacheFile); break;
	    case CACHE_STALE_RELOAD_BAD:
		log.logInfo("CACHE STALE, SUPERFLUOUS RELOAD: " + entry.cacheFile); break;
	    case CACHE_PASSING:
		log.logInfo("PASSING: " + entry.cacheFile); break;
	    default:
		log.logInfo("CACHE STATE UNKNOWN: " + entry.cacheFile); break;
	    }
    }
    

    public boolean job() {
        if (empty()) return false;
        try {
            File f;
            int workoff;
            workoff = 1 + cacheStack.size() / 10;
            // we want to work off always 10 % to prevent that we collaps
            while ((workoff-- > 0) && (!(empty()))) {
                process((Entry) cacheStack.removeFirst());
            }
            
            // loop until we are not idle or nothing more to do
            while ((!empty()) && (idle())) {
                // work off stack and store entries to file system
                process((Entry) cacheStack.removeFirst());
                
                // clean up cache to have enough space for next entries
                while (currCacheSize > maxCacheSize) {
                    f = (File) cacheAge.remove(cacheAge.firstKey());
                    if (f.exists()) {
                        currCacheSize -= f.length();
                        f.delete();
                        log.logInfo("DELETED OLD CACHE : " + f.toString());
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("The proxy cache manager has died because of an IO-problem: " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(-1);
        }
        return true;
    }

    public static boolean isPicture(httpHeader response) {
	Object ct = response.get("Content-Type");
	if (ct == null) return false;
	return ((String)ct).toUpperCase().startsWith("IMAGE");
    }
    
    public static boolean isText(httpHeader response) {
	Object ct = response.get("Content-Type");
	if (ct == null) return false;
	return ((String)ct).toUpperCase().startsWith("TEXT");
    }

    public static boolean noIndexingURL(String urlString) {
	if (urlString == null) return false;
        urlString = urlString.toLowerCase();
        return (
            (urlString.endsWith(".gz")) ||
            (urlString.endsWith(".msi")) ||
            (urlString.endsWith(".doc")) ||
            (urlString.endsWith(".zip")) ||
            (urlString.endsWith(".tgz")) ||
            (urlString.endsWith(".rar")) ||
            (urlString.endsWith(".pdf")) ||
            (urlString.endsWith(".ppt")) ||
            (urlString.endsWith(".xls")) ||
            (urlString.endsWith(".log")) ||
            (urlString.endsWith(".java")) ||
            (urlString.endsWith(".c")) ||
            (urlString.endsWith(".p"))
            );
    }
        
    // this method creates from a given host and path a cache path
    public File getCachePath(URL url) {
	// from a given host (which may also be an IPv4 - number, but not IPv6 or
	// a domain; all without leading 'http://') and a path (which must start
	// with a leading '/', and may also end in an '/') a path to a file
	// in the file system with root as given in cachePath is constructed
	// it will also be ensured, that the complete path exists; if necessary
	// that path will be generated
        //System.out.println("DEBUG: getCachedPath=" + url.toString());
	String remotePath = url.getPath();
	if (!(remotePath.startsWith("/"))) remotePath = "/" + remotePath;
	if (remotePath.endsWith("/")) remotePath = remotePath + "ndx";
        if (remotePath.indexOf('#') > 0) remotePath.substring(0, remotePath.indexOf('#'));
        remotePath = remotePath.replace('?', '_'); remotePath = remotePath.replace('&', '_'); // yes this is not reversible, but that is not needed
	int port = url.getPort();
	if (port < 0) port = 80;
	return new File(this.cachePath, url.getHost() + ((port == 80) ? "" : ("+" + port)) + remotePath);
    }

    public static URL getURL(File cachePath, File f) {
	// this is the reverse function to getCachePath: it constructs the url as string
	// from a given storage path
	String s = f.toString().replace('\\', '/');
	String c = cachePath.toString().replace('\\', '/');    
        //System.out.println("DEBUG: getURL for c=" + c + ", s=" + s);
	int p = s.lastIndexOf(c);
	if (p >= 0) {
	    s = s.substring(p + c.length());
	    while (s.startsWith("/")) s = s.substring(1);
	    if ((p = s.indexOf("+")) >= 0) {
                s = s.substring(0, p) + ":" + s.substring(p + 1);
            } else {
                p = s.indexOf("/");
                if (p < 0)
                    s = s + ":80/";
                else
                    s = s.substring(0, p) + ":80" + s.substring(p);
            }
	    if (s.endsWith("ndx")) s = s.substring(0, s.length() - 3);
            //System.out.println("DEBUG: getURL url=" + s);
            try {
                return new URL("http://" + s);
            } catch (Exception e) {
                return null;
            }
	}
	return null;
    }
    
    public static boolean isPOST(String urlString) {
	return ((urlString.indexOf("?") >= 0) ||
		(urlString.indexOf("&") >= 0));
    }

    public static boolean isCGI(String urlString) {
	return ((urlString.toLowerCase().indexOf(".cgi") >= 0) ||
		(urlString.toLowerCase().indexOf(".exe") >= 0));
    }

    public Entry newEntry(Date initDate, int depth, URL url,
			  httpHeader requestHeader,
			  String responseStatus, httpHeader responseHeader,
                          String initiator,
                          plasmaCrawlProfile.entry profile) {
        //System.out.println("NEW ENTRY: " + url.toString()); // DEBUG
	return new Entry(initDate, depth, url, requestHeader, responseStatus, responseHeader, initiator, profile);
    }

    public final class Entry {

	// the class objects
	public Date                     initDate;       // the date when the request happened; will be used as a key
	public int                      depth;          // the depth of prefetching
	public httpHeader               requestHeader;  // we carry also the header to prevent too many file system access
	public String                   responseStatus;
	public httpHeader               responseHeader; // we carry also the header to prevent too many file system access
	public File                     cacheFile;      // the cache file
	public byte[]                   cacheArray;     // or the cache as byte-array
	public URL                      url;
	public String                   nomalizedURLHash;
	public String                   nomalizedURLString;
	public int                      status;         // cache load/hit/stale etc status
	public Date                     lastModified;
	public char                     doctype;
	public String                   language;
        public plasmaCrawlProfile.entry profile;
        private String                  initiator;
        public htmlFilterContentScraper scraper;

	
	public Entry(Date initDate, int depth, URL url,
		     httpHeader requestHeader,
		     String responseStatus, httpHeader responseHeader,
                     String initiator,
                     plasmaCrawlProfile.entry profile) {

            // normalize url
            this.nomalizedURLString = htmlFilterContentScraper.urlNormalform(url);
            try {
                this.url            = new URL(nomalizedURLString);
            } catch (MalformedURLException e) {
                System.out.println("internal error at httpdProxyCache.Entry: " + e);
                System.exit(-1);
            }
	    this.cacheFile      = getCachePath(this.url);
	    this.nomalizedURLHash        = plasmaCrawlLURL.urlHash(nomalizedURLString);
	                 
	    // assigned:
	    this.initDate       = initDate;
	    this.depth          = depth;
	    this.requestHeader  = requestHeader;
	    this.responseStatus = responseStatus;
	    this.responseHeader = responseHeader;
	    this.profile        = profile;
            this.initiator      = (initiator == null) ? null : ((initiator.length() == 0) ? null: initiator);

            // calculated:
	    if (responseHeader == null) {
		try {
		    throw new RuntimeException("RESPONSE HEADER = NULL");
		} catch (Exception e) {
		    System.out.println("RESPONSE HEADER = NULL in " + url);
		    e.printStackTrace();
		    System.exit(0);
		}

		lastModified = new Date();
	    } else {
		lastModified = responseHeader.lastModified();
                if (lastModified == null) lastModified = new Date(); // does not exist in header
	    }
	    this.doctype = plasmaWordIndexEntry.docType(nomalizedURLString);
	    this.language = plasmaWordIndexEntry.language(url);

	    // to be defined later:
	    this.cacheArray     = null;
	    this.status         = CACHE_UNFILLED;
            this.scraper        = null;
	}
	
        public String initiator() {
            return initiator;
        }
        public boolean proxy() {
            return initiator() == null;
        }
	public long size() {
	    if (cacheArray == null) return 0; else return cacheArray.length;
	}

        public URL referrerURL() {
            if (requestHeader == null) return null;
            try {
                return new URL((String) requestHeader.get("Referer", ""));
            } catch (Exception e) {
                return null;
            }
        }
        
	public boolean update() {
	    return ((status == CACHE_FILL) || (status == CACHE_STALE_RELOAD_GOOD));
	}
	

	// the following three methods for cache read/write granting shall be as loose as possible
	// but also as strict as necessary to enable caching of most items
	
	public String shallStoreCache() {
            // returns NULL if the answer is TRUE
            // in case of FALSE, the reason as String is returned
	    
            // check profile
            if (!(profile.storeHTCache())) return "storage_not_wanted";
            
	    // decide upon header information if a specific file should be stored to the cache or not
	    // if the storage was requested by prefetching, the request map is null
	    
	    // check status code
	    if (!((responseStatus.startsWith("200")) || (responseStatus.startsWith("203")))) return "bad_status_" + responseStatus.substring(0,3);
	    
	    // check storage location
	    // sometimes a file name is equal to a path name in the same directory;
	    // or sometimes a file name is equal a directory name created earlier;
	    // we cannot match that here in the cache file path and therefore omit writing into the cache
	    if ((cacheFile.getParentFile().isFile()) || (cacheFile.isDirectory())) return "path_ambiguous";
	    if (cacheFile.toString().indexOf("..") >= 0) return "path_dangerous";
            
	    // -CGI access in request
	    // CGI access makes the page very individual, and therefore not usable in caches
	    if ((isPOST(nomalizedURLString)) && (!(profile.crawlingQ()))) return "dynamic_post";
            if (isCGI(nomalizedURLString)) return "dynamic_cgi";
	    
	    // -authorization cases in request
	    // authorization makes pages very individual, and therefore we cannot use the
	    // content in the cache
	    if ((requestHeader != null) && (requestHeader.containsKey("AUTHORIZATION"))) return "personalized";
	    
	    // -ranges in request and response
	    // we do not cache partial content
	    if ((requestHeader != null) && (requestHeader.containsKey("RANGE"))) return "partial";
	    if ((responseHeader != null) && (responseHeader.containsKey("CONTENT-RANGE"))) return "partial";
	    
	    // -if-modified-since in request
	    // we do not care about if-modified-since, because this case only occurres if the
	    // cache file does not exist, and we need as much info as possible for the indexing
	    
	    // -cookies in request
	    // we do not care about cookies, because that would prevent loading more pages
	    // from one domain once a request resulted in a client-side stored cookie
	    
	    // -set-cookie in response
	    // we do not care about cookies in responses, because that info comes along
	    // any/many pages from a server and does not express the validity of the page
	    // in modes of life-time/expiration or individuality
	    
	    // -pragma in response
	    // if we have a pragma non-cache, we don't cache. usually if this is wanted from
	    // the server, it makes sense
	    if ((responseHeader.containsKey("PRAGMA")) &&
		(((String) responseHeader.get("Pragma")).toUpperCase().equals("NO-CACHE"))) return "controlled_no_cache";
	    
	    // -expires in response
	    // we do not care about expires, because at the time this is called the data is
	    // obvious valid and that header info is used in the indexing later on
	    
	    // -cache-control in response
	    // the cache-control has many value options.
	    String cacheControl = (String) responseHeader.get("Cache-Control");
	    if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("MAX-AGE=")) {
                    // we need also the load date
                    Date date = responseHeader.date();
                    if (date == null) return "stale_no_date_given_in_response";
                    try {
                        long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                        if ((new Date()).getTime() - date.getTime() > ttl) {
                            //System.out.println("***not indexed because cache-control");
                            return "stale_expired";
                        }
                    } catch (Exception e) {
                        return "stale_error_" + e.getMessage() + ")";
                    }
                }
	    }

	    
	    return null;
	}

        public boolean shallUseCache() {
	    // decide upon header information if a specific file should be taken from the cache or not
	    
	    //System.out.println("SHALL READ CACHE: requestHeader = " + requestHeader.toString() + ", responseHeader = " + responseHeader.toString());
	    
	    // -CGI access in request
	    // CGI access makes the page very individual, and therefore not usable in caches
	    if (isPOST(nomalizedURLString)) return false;
	    if (isCGI(nomalizedURLString)) return false;
	    
	    // -authorization cases in request
	    if (requestHeader.containsKey("AUTHORIZATION")) return false;
	    
	    // -ranges in request
	    // we do not cache partial content
	    if ((requestHeader != null) && (requestHeader.containsKey("RANGE"))) return false;
	    
	    //Date d1, d2;

	    // -if-modified-since in request
	    // The entity has to be transferred only if it has
	    // been modified since the date given by the If-Modified-Since header.
	    if (requestHeader.containsKey("IF-MODIFIED-SINCE")) {
		// checking this makes only sense if the cached response contains
		// a Last-Modified field. If the field does not exist, we go the safe way
		if (!(responseHeader.containsKey("Last-Modified"))) return false;
		// parse date
                Date d1, d2;
		d2 = responseHeader.lastModified(); if (d2 == null) d2 = new Date();
		d1 = requestHeader.ifModifiedSince(); if (d1 == null) d1 = new Date();
		// finally, we shall treat the cache as stale if the modification time is after the if-.. time
		if (d2.after(d1)) return false;
	    }
	    
	    boolean isNotPicture = !isPicture(responseHeader);

	    // -cookies in request
	    // unfortunately, we should reload in case of a cookie
	    // but we think that pictures can still be considered as fresh
	    if ((requestHeader.containsKey("COOKIE")) && (isNotPicture)) return false;
	    
	    // -set-cookie in cached response
	    // this is a similar case as for COOKIE.
	    if ((responseHeader.containsKey("SET-COOKIE")) && (isNotPicture)) return false; // too strong
	    if ((responseHeader.containsKey("SET-COOKIE2")) && (isNotPicture)) return false; // too strong
	    
	    // -pragma in cached response
	    // logically, we would not need to care about no-cache pragmas in cached response headers,
	    // because they cannot exist since they are not written to the cache.
	    // So this IF should always fail..
	    if ((responseHeader.containsKey("PRAGMA")) &&
		(((String) responseHeader.get("Pragma")).toUpperCase().equals("NO-CACHE"))) return false;
	    
	    // calculate often needed values for freshness attributes
	    Date date           = responseHeader.date();
	    Date expires        = responseHeader.expires();
	    Date lastModified   = responseHeader.lastModified();
	    String cacheControl = (String) responseHeader.get("Cache-Control");
	    
	    
	    // see for documentation also:
	    // http://www.web-caching.com/cacheability.html
	    // http://vancouver-webpages.com/CacheNow/

	    // look for freshnes information
	    // if we don't have any freshnes indication, we treat the file as stale.
	    // no handle for freshness control:
	    if ((expires == null) && (cacheControl == null) && (lastModified == null)) return false;
	    
	    // -expires in cached response
	    // the expires value gives us a very easy hint when the cache is stale
	    if (expires != null) {
		Date yesterday = new Date((new Date()).getTime() - oneday);
		if (expires.before(yesterday)) return false;
	    }
	    
	    // -lastModified in cached response
	    // we can apply a TTL (Time To Live)  heuristic here. We call the time delta between the last read
	    // of the file and the last modified date as the age of the file. If we consider the file as
	    // middel-aged then, the maximum TTL would be cache-creation plus age.
	    // This would be a TTL factor of 100% we want no more than 10% TTL, so that a 10 month old cache
	    // file may only be treated as fresh for one more month, not more.
	    if (lastModified != null) {
		if (date == null) date = new Date();
		long age = date.getTime() - lastModified.getTime();
		if (age < 0) return false;
		// TTL (Time-To-Live) is age/10 = (d2.getTime() - d1.getTime()) / 10
		// the actual living-time is new Date().getTime() - d2.getTime()
		// therefore the cache is stale, if Date().getTime() - d2.getTime() > age/10
		if ((new Date()).getTime() - date.getTime() > age / 10) return false;
	    }
	    
 	    // -cache-control in cached response
	    // the cache-control has many value options.
	    if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("PUBLIC")) {
                    // ok, do nothing
                } else if ((cacheControl.startsWith("PRIVATE")) ||
                           (cacheControl.startsWith("NO-CACHE")) ||
                           (cacheControl.startsWith("NO-STORE"))) {
                    // easy case
                    return false;
                } else if (cacheControl.startsWith("MAX-AGE=")) {
                    // we need also the load date
                    if (date == null) return false;
                    try {
                        long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                        if ((new Date()).getTime() - date.getTime() > ttl) {
                            return false;
                        }
                    } catch (Exception e) {
                        return false;
                    }
                }
	    }
	    
	    return true;
	}
        
        	
	public String shallIndexCacheForProxy() {
	    // decide upon header information if a specific file should be indexed
	    // this method returns null if the answer is 'YES'!
	    // if the answer is 'NO' (do not index), it returns a string with the reason
	    // to reject the crawling demand in clear text
	    
            // check profile
            if (!(profile.localIndexing())) return "Indexing_Not_Allowed";
            
	    // -CGI access in request
	    // CGI access makes the page very individual, and therefore not usable in caches
	     if ((isPOST(nomalizedURLString)) && (!(profile.crawlingQ()))) return "Dynamic_(POST)";
             if ((isCGI(nomalizedURLString)) && (!(profile.crawlingQ()))) return "Dynamic_(CGI)";
	    
	    // -authorization cases in request
	    // we checked that in shallStoreCache
	    
	    // -ranges in request
	    // we checked that in shallStoreCache
	    
	    // a picture cannot be indexed
	    if (isPicture(responseHeader)) return "Media_Content_(Picture)";
	    if (!(isText(responseHeader))) return "Media_Content_(not_text)";
	    if (noIndexingURL(nomalizedURLString)) return "Media_Content_(forbidden)";

	    
	    // -if-modified-since in request
	    // if the page is fresh at the very moment we can index it
	    if ((requestHeader != null) &&
                (requestHeader.containsKey("IF-MODIFIED-SINCE")) &&
                (responseHeader.containsKey("Last-Modified"))) {
		// parse date
                Date d1, d2;
		d2 = responseHeader.lastModified(); if (d2 == null) d2 = new Date();
		d1 = requestHeader.ifModifiedSince(); if (d1 == null) d1 = new Date();
		// finally, we shall treat the cache as stale if the modification time is after the if-.. time
		if (d2.after(d1)) {
		    //System.out.println("***not indexed because if-modified-since");
		    return "Stale_(Last-Modified>Modified-Since)";
		}
	    }
	    
	    // -cookies in request
	    // unfortunately, we cannot index pages which have been requested with a cookie
	    // because the returned content may be special for the client
	    if ((requestHeader != null) && (requestHeader.containsKey("COOKIE"))) {
		//System.out.println("***not indexed because cookie");
		return "Dynamic_(Requested_With_Cookie)";
	    }

	    // -set-cookie in response
	    // the set-cookie from the server does not indicate that the content is special
	    // thus we do not care about it here for indexing

	    // -pragma in cached response
	    if ((responseHeader.containsKey("PRAGMA")) &&
		(((String) responseHeader.get("Pragma")).toUpperCase().equals("NO-CACHE"))) return "Denied_(pragma_no_cache)";
            
	    // see for documentation also:
	    // http://www.web-caching.com/cacheability.html
	    
	    // calculate often needed values for freshness attributes
	    Date date           = responseHeader.date();
	    Date expires        = responseHeader.expires();
	    Date lastModified   = responseHeader.lastModified();
	    String cacheControl = (String) responseHeader.get("Cache-Control");
	    
	    // look for freshnes information
	    
	    // -expires in cached response
	    // the expires value gives us a very easy hint when the cache is stale
	    // sometimes, the expires date is set to the past to prevent that a page is cached
	    // we use that information to see if we should index it
	    if (expires != null) {
		Date yesterday = new Date((new Date()).getTime() - oneday);
		if (expires.before(yesterday)) return "Stale_(Expired)";
	    }
	    
	    // -lastModified in cached response
	    // this information is too weak to use it to prevent indexing
	    // even if we can apply a TTL heuristic for cache usage
	    
 	    // -cache-control in cached response
	    // the cache-control has many value options.
	    if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                /* we have the following cases for cache-control:
                "public" -- can be indexed
                "private", "no-cache", "no-store" -- cannot be indexed
                "max-age=<delta-seconds>" -- stale/fresh dependent on date
                */
                if (cacheControl.startsWith("PUBLIC")) {
                    // ok, do nothing
                } else if ((cacheControl.startsWith("PRIVATE")) ||
                           (cacheControl.startsWith("NO-CACHE")) ||
                           (cacheControl.startsWith("NO-STORE"))) {
                    // easy case
                    return "Stale_(denied_by_cache-control=" + cacheControl+ ")";
                } else if (cacheControl.startsWith("MAX-AGE=")) {
                    // we need also the load date
                    if (date == null) return "Stale_(no_date_given_in_response)";
                    try {
                        long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                        if ((new Date()).getTime() - date.getTime() > ttl) {
                            //System.out.println("***not indexed because cache-control");
                            return "Stale_(expired_by_cache-control)";
                        }
                    } catch (Exception e) {
                        return "Error_(" + e.getMessage() + ")";
                    }
                }
	    }
	    
	    return null;
	}
        
        	
	public String shallIndexCacheForCrawler() {
	    // decide upon header information if a specific file should be indexed
	    // this method returns null if the answer is 'YES'!
	    // if the answer is 'NO' (do not index), it returns a string with the reason
	    // to reject the crawling demand in clear text
	    
            // check profile
            if (!(profile.localIndexing())) return "Indexing_Not_Allowed";
            
	    // -CGI access in request
	    // CGI access makes the page very individual, and therefore not usable in caches
	     if ((isPOST(nomalizedURLString)) && (!(profile.crawlingQ()))) return "Dynamic_(POST)";
             if ((isCGI(nomalizedURLString)) && (!(profile.crawlingQ()))) return "Dynamic_(CGI)";
	    
	    // -authorization cases in request
	    // we checked that in shallStoreCache
	    
	    // -ranges in request
	    // we checked that in shallStoreCache
	    
	    // a picture cannot be indexed
	    if (isPicture(responseHeader)) return "Media_Content_(Picture)";
	    if (!(isText(responseHeader))) return "Media_Content_(not_text)";
	    if (noIndexingURL(nomalizedURLString)) return "Media_Content_(forbidden)";

	    // -if-modified-since in request
	    // if the page is fresh at the very moment we can index it
            // -> this does not apply for the crawler
	    
	    // -cookies in request
	    // unfortunately, we cannot index pages which have been requested with a cookie
	    // because the returned content may be special for the client
            // -> this does not apply for a crawler

	    // -set-cookie in response
	    // the set-cookie from the server does not indicate that the content is special
	    // thus we do not care about it here for indexing
            // -> this does not apply for a crawler

	    // -pragma in cached response
            // -> in the crawler we ignore this
            
	    // look for freshnes information
	    
	    // -expires in cached response
	    // the expires value gives us a very easy hint when the cache is stale
	    // sometimes, the expires date is set to the past to prevent that a page is cached
	    // we use that information to see if we should index it
	    // -> this does not apply for a crawler
	    
	    // -lastModified in cached response
	    // this information is too weak to use it to prevent indexing
	    // even if we can apply a TTL heuristic for cache usage
	    
 	    // -cache-control in cached response
	    // the cache-control has many value options.
	    // -> in the crawler we ignore this
	    
	    return null;
	}
        
    }
    
}
