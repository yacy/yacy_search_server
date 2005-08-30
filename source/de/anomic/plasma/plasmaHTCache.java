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
import java.util.Map;
import java.util.TreeMap;
//import java.util.Calendar;
//import java.util.GregorianCalendar;
//import java.util.TimeZone;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpHeader;
import de.anomic.http.httpc;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroMap;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverInstantThread;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverDate;
import de.anomic.tools.enumerateFiles;

public final class plasmaHTCache {

    private static final int stackLimit = 150;  // if we exceed that limit, we do not check idle
    public  static final long oneday = 1000 * 60 * 60 * 24; // milliseconds of a day

    private kelondroMap responseHeaderDB = null;
    private final LinkedList cacheStack;
    private final TreeMap cacheAge; // a <date+hash, cache-path> - relation
    public  long currCacheSize;
    public  long maxCacheSize;
    public  final File cachePath;
    public  static serverLog log;

    public plasmaHTCache(File htCachePath, long maxCacheSize, int bufferkb) {
        // this.switchboard = switchboard;

        this.log = new serverLog("HTCACHE");
        this.cachePath = htCachePath;
        this.maxCacheSize = maxCacheSize;

        // we dont need check the path, because we have do that in plasmaSwitchboard.java - Borg-0300
/*      // set cache path
        if (!(htCachePath.exists())) {
            // make the cache path
            htCachePath.mkdir();
        }
        if (!(htCachePath.isDirectory())) {
            // if the cache does not exists or is a file and not a directory, panic
            System.out.println("the cache path " + htCachePath.toString() + " is not a directory or does not exists and cannot be created");
            System.exit(0);
        }*/

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

        // init cache age and size management
        cacheAge = new TreeMap();
        currCacheSize = 0;
        this.maxCacheSize = maxCacheSize;

        // start the cache startup thread
        // this will collect information about the current cache size and elements
        serverInstantThread.oneTimeJob(this, "cacheScan", log, 5000);
    }

    public int size() {
        synchronized (this.cacheStack) {
            return this.cacheStack.size();
        }        
    }

    public void push(Entry entry) {
        synchronized (this.cacheStack) {
            this.cacheStack.add(entry);
        }
    }

    public Entry pop() {
        synchronized (this.cacheStack) {
        if (this.cacheStack.size() > 0)
            return (Entry) this.cacheStack.removeFirst();
        else
            return null;
        }
    }

    public void storeHeader(String urlHash, httpHeader responseHeader) throws IOException {
        responseHeaderDB.set(urlHash, responseHeader);
    }

    private boolean deleteFile(File file) {
        if (file.exists()) {
            currCacheSize -= file.length();
            return file.delete();
        } else {
            return false;
        }
    }

    public boolean deleteFile(URL url) {
        return deleteFile(getCachePath(url));
    }

    public boolean writeFile(URL url, byte[] array) {
        if (array == null) return false;
        File file = getCachePath(url);
        try {
            deleteFile(file);
            file.getParentFile().mkdirs();
            serverFileUtils.write(array, file);
        } catch (FileNotFoundException e) {
            // this is the case of a "(Not a directory)" error, which should be prohibited
            // by the shallStoreCache() property. However, sometimes the error still occurs
            // In this case do nothing.
            log.logFailure("File storage failed (not a directory): " + e.getMessage());
            return false;
        } catch (IOException e) {
            log.logFailure("File storage failed (IO error): " + e.getMessage());
            return false;
        }
        writeFileAnnouncement(file);
        return true;
    }

    public void writeFileAnnouncement(File file) {
        synchronized (cacheAge) {
            if (file.exists()) {
                currCacheSize += file.length();
                cacheAge.put(ageString(file.lastModified(), file), file);
                cleanup();
            }
        }
    }

    private void cleanup() {
        // clean up cache to have enough space for next entries
        File f;
        while ((currCacheSize > maxCacheSize) && (cacheAge.size() > 0)) {
            f = (File) cacheAge.remove(cacheAge.firstKey());
            if ((f != null) && (f.exists())) {
                long size = f.length();
                //currCacheSize -= f.length();
                if (f.delete()) {
                    log.logInfo("DELETED OLD CACHE : " + f.toString());
                    currCacheSize -= size;
                    f = f.getParentFile();
                    if (f.isDirectory() && (f.list().length == 0)) {
                        // the directory has no files in it; delete it also
                        if (f.delete()) log.logInfo("DELETED EMPTY DIRECTORY : " + f.toString());
                    }
                }
            }
        }
    }

    public void close() throws IOException {
        responseHeaderDB.close();
    }

    private String ageString(long date, File f) {
        StringBuffer sb = new StringBuffer(32);
        String s = Long.toHexString(date);
        for (int i = s.length(); i < 16; i++) sb.append('0');
            sb.append(s);
            s = Integer.toHexString(f.hashCode());
            for (int i = s.length(); i < 8; i++) sb.append('0');
            sb.append(s);
        return sb.toString();
    }

    public void cacheScan() {
        //log.logSystem("STARTING CACHE SCANNING");
        kelondroMScoreCluster doms = new kelondroMScoreCluster();
        int c = 0;
        enumerateFiles ef = new enumerateFiles(cachePath, true, false, true, true);
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
        long ageHours = 0;
        try {
            ageHours = (System.currentTimeMillis() -
                            Long.parseLong(((String) cacheAge.firstKey()).substring(0, 16), 16)) / 3600000;
        } catch (NumberFormatException e) {
            //e.printStackTrace();
        }
        log.logConfig("CACHE SCANNED, CONTAINS " + c +
                      " FILES = " + currCacheSize/1048576 + "MB, OLDEST IS " + 
            ((ageHours < 24) ? (ageHours + " HOURS") : ((ageHours / 24) + " DAYS")) + " OLD");
        cleanup();

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
            log.logConfig("PRE-FILLED " + dom + "=" + ip);
            c++;
            doms.deleteScore(dom);
            // wait a short while to prevent that this looks like a DoS
            try {Thread.currentThread().sleep(100);} catch (InterruptedException e) {}
        }
        if (result.length() > 2) log.logConfig("PRE-FILLED DNS CACHE, FETCHED " + c +
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
        Map hdb = responseHeaderDB.get(urlHash);
        if (hdb == null) return null;
        return new httpHeader(null, hdb);
    }

    public boolean full() {
        return (cacheStack.size() > stackLimit);
    }

    public boolean empty() {
        return (cacheStack.size() == 0);
    }

    public static boolean isPicture(httpHeader response) {
        Object ct = response.get(httpHeader.CONTENT_TYPE);
        if (ct == null) return false;
        return ((String)ct).toUpperCase().startsWith("IMAGE");
    }

    public static boolean isText(httpHeader response) {
//      Object ct = response.get(httpHeader.CONTENT_TYPE);
//      if (ct == null) return false;
//      String t = ((String)ct).toLowerCase();
//      return ((t.startsWith("text")) || (t.equals("application/xhtml+xml")));
        return plasmaParser.supportedMimeTypesContains(response.mime());
    }

    public static boolean noIndexingURL(String urlString) {
        if (urlString == null) return false;
        urlString = urlString.toLowerCase();
//        return (
//                (urlString.endsWith(".gz")) ||
//                (urlString.endsWith(".msi")) ||
//                (urlString.endsWith(".doc")) ||
//                (urlString.endsWith(".zip")) ||
//                (urlString.endsWith(".tgz")) ||
//                (urlString.endsWith(".rar")) ||
//                (urlString.endsWith(".pdf")) ||
//                (urlString.endsWith(".ppt")) ||
//                (urlString.endsWith(".xls")) ||
//                (urlString.endsWith(".log")) ||
//                (urlString.endsWith(".java")) ||
//                (urlString.endsWith(".c")) ||
//                (urlString.endsWith(".p"))
//        );
        int idx = urlString.indexOf("?");
        if (idx > 0) urlString = urlString.substring(0,idx);

        idx = urlString.lastIndexOf(".");
        if (idx > 0) urlString = urlString.substring(idx+1);

        return plasmaParser.mediaExtContains(urlString);
    }

    /** 
     * this method creates from a given host and path a cache path
     * from a given host (which may also be an IPv4 - number, but not IPv6 or
     * a domain; all without leading 'http://') and a path (which must start
     * with a leading '/', and may also end in an '/') a path to a file
     * in the file system with root as given in cachePath is constructed
     * it will also be ensured, that the complete path exists; if necessary
     * that path will be generated
     * @return URL
     */
    public File getCachePath(URL url) {
        // System.out.println("DEBUG: getCachePath:  IN=" + url.toString());
        String remotePath = url.getPath();
        if (!(remotePath.startsWith("/"))) remotePath = "/" + remotePath;
        if (remotePath.endsWith("/")) remotePath = remotePath + "ndx";
        if (remotePath.indexOf('#') > 0) remotePath.substring(0, remotePath.indexOf('#'));
        remotePath = remotePath.replace('?', '_'); 
        remotePath = remotePath.replace('&', '_'); // yes this is not reversible, but that is not needed
        remotePath = remotePath.replace(':', '_'); // yes this is not reversible, but that is not needed
        int port = url.getPort();
        if (port < 0) port = 80;
        // System.out.println("DEBUG: getCachePath: OUT=" + url.getHost() + ((port == 80) ? "" : ("+" + port)) + remotePath);
        return new File(this.cachePath, url.getHost() + ((port == 80) ? "" : ("+" + port)) + remotePath);
    }

    /**
     * this is the reverse function to getCachePath: it constructs the url as string
     * from a given storage path
     */
    public static URL getURL(File cachePath, File f) {
        // System.out.println("DEBUG: getURL:  IN: Path=[" + cachePath + "]");
        // System.out.println("DEBUG: getURL:  IN: File=[" + f + "]");
        String s = f.toString().replace('\\', '/');
        String c = cachePath.toString().replace('\\', '/');
        int p = s.lastIndexOf(c);
        if (p >= 0) {
            s = s.substring(p + c.length());
            while (s.startsWith("/")) s = s.substring(1);
            if ((p = s.indexOf("+")) >= 0) {
                s = s.substring(0, p) + ":" + s.substring(p + 1);
/*          } else {
                p = s.indexOf("/");
                if (p < 0)
                    s = s + ":80/";
                else
                    s = s.substring(0, p) + ":80" + s.substring(p);*/
            }
            if (s.endsWith("ndx")) s = s.substring(0, s.length() - 3);
            // System.out.println("DEBUG: getURL: OUT=" + s);
	
            try {
/*              URL url = null;
                url = new URL("http://" + s);
                System.out.println("DEBUG: getURL: URL=" + url.toString());
                return url;//new URL("http://" + s); */
                return new URL("http://" + s);
            } catch (Exception e) {
                return null;
            }
        }
	    return null;
    }

    public byte[] loadResource(URL url) {
        // load the url as resource from the cache
        File f = getCachePath(url);
        if (f.exists()) try {
            return serverFileUtils.read(f);
        } catch (IOException e) {
            return null;
        } else {
            return null;
        }
    }

    public static boolean isPOST(String urlString) {
        return ((urlString.indexOf("?") >= 0) ||
                (urlString.indexOf("&") >= 0));
    }

    public static boolean isCGI(String urlString) {
        String ls = urlString.toLowerCase();
        return ((ls.indexOf(".cgi") >= 0) ||
                (ls.indexOf(".exe") >= 0) ||
                (ls.indexOf(";jsessionid=") >= 0) ||
                (ls.indexOf("sessionid/") >= 0) ||
                (ls.indexOf("phpsessid=") >= 0));
    }

    public Entry newEntry(Date initDate, int depth, URL url, String name,
                          httpHeader requestHeader,
                          String responseStatus, httpHeader responseHeader,
                          String initiator,
                          plasmaCrawlProfile.entry profile) {
        return new Entry(initDate, depth, url, name, requestHeader, responseStatus, responseHeader, initiator, profile);
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
    public String                   name;           // the name of the link, read as anchor from an <a>-tag
    public String                   nomalizedURLHash;
    public String                   nomalizedURLString;
    public int                      status;         // cache load/hit/stale etc status
    public Date                     lastModified;
    public char                     doctype;
    public String                   language;
    public plasmaCrawlProfile.entry profile;
    private String                  initiator;

    public Entry(Date initDate, int depth, URL url, String name,
                 httpHeader requestHeader,
                 String responseStatus, httpHeader responseHeader,
                 String initiator,
                 plasmaCrawlProfile.entry profile) {

        // normalize url - Borg-0300
        serverLog.logFine("PLASMA", "Entry: URL=" + url.toString());
        this.nomalizedURLString = htmlFilterContentScraper.urlNormalform(url);
        try {
            this.url            = new URL(nomalizedURLString);
        } catch (MalformedURLException e) {
            System.out.println("internal error at httpdProxyCache.Entry: " + e);
            System.exit(-1);
        }
        this.name           = name;
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

           lastModified = serverDate.correctedGMTDate();
        } else {
            lastModified = responseHeader.lastModified();
            if (lastModified == null) lastModified = serverDate.correctedGMTDate(); // does not exist in header
        }
        this.doctype = plasmaWordIndexEntry.docType(responseHeader.mime());
        if (this.doctype == plasmaWordIndexEntry.DT_UNKNOWN) this.doctype = plasmaWordIndexEntry.docType(url);
        this.language = plasmaWordIndexEntry.language(url);

        // to be defined later:
        this.cacheArray     = null;
    }
	
    public String name() {
        return name;
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
            return new URL((String) requestHeader.get(httpHeader.REFERER, ""));
        } catch (Exception e) {
            return null;
        }
    }

    /*
	public boolean update() {
	    return ((status == CACHE_FILL) || (status == CACHE_STALE_RELOAD_GOOD));
	}
	*/

    // the following three methods for cache read/write granting shall be as loose as possible
    // but also as strict as necessary to enable caching of most items
	
    public String shallStoreCacheForProxy() {
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
	    if ((requestHeader != null) && (requestHeader.containsKey(httpHeader.AUTHORIZATION))) return "personalized";
	    
	    // -ranges in request and response
	    // we do not cache partial content
	    if ((requestHeader != null) && (requestHeader.containsKey(httpHeader.RANGE))) return "partial";
	    if ((responseHeader != null) && (responseHeader.containsKey(httpHeader.CONTENT_RANGE))) return "partial";
	    
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
	    if ((responseHeader.containsKey(httpHeader.PRAGMA)) &&
		(((String) responseHeader.get(httpHeader.PRAGMA)).toUpperCase().equals("NO-CACHE"))) return "controlled_no_cache";
	    
	    // -expires in response
	    // we do not care about expires, because at the time this is called the data is
	    // obvious valid and that header info is used in the indexing later on
	    
	    // -cache-control in response
	    // the cache-control has many value options.
	    String cacheControl = (String) responseHeader.get(httpHeader.CACHE_CONTROL);
	    if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("MAX-AGE=")) {
                    // we need also the load date
                    Date date = responseHeader.date();
                    if (date == null) return "stale_no_date_given_in_response";
                    try {
                        long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                        if (serverDate.correctedGMTDate().getTime() - date.getTime() > ttl) {
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

        public boolean shallUseCacheForProxy() {
	    // decide upon header information if a specific file should be taken from the cache or not
	    
	    //System.out.println("SHALL READ CACHE: requestHeader = " + requestHeader.toString() + ", responseHeader = " + responseHeader.toString());
	    
	    // -CGI access in request
	    // CGI access makes the page very individual, and therefore not usable in caches
	    if (isPOST(nomalizedURLString)) return false;
	    if (isCGI(nomalizedURLString)) return false;
	    
	    // -authorization cases in request
	    if (requestHeader.containsKey(httpHeader.AUTHORIZATION)) return false;
	    
	    // -ranges in request
	    // we do not cache partial content
	    if ((requestHeader != null) && (requestHeader.containsKey(httpHeader.RANGE))) return false;
	    
	    //Date d1, d2;

	    // -if-modified-since in request
	    // The entity has to be transferred only if it has
	    // been modified since the date given by the If-Modified-Since header.
	    if (requestHeader.containsKey(httpHeader.IF_MODIFIED_SINCE)) {
		// checking this makes only sense if the cached response contains
		// a Last-Modified field. If the field does not exist, we go the safe way
		if (!(responseHeader.containsKey(httpHeader.LAST_MODIFIED))) return false;
		// parse date
                Date d1, d2;
		d2 = responseHeader.lastModified(); if (d2 == null) d2 = serverDate.correctedGMTDate();
		d1 = requestHeader.ifModifiedSince(); if (d1 == null) d1 = serverDate.correctedGMTDate();
		// finally, we shall treat the cache as stale if the modification time is after the if-.. time
		if (d2.after(d1)) return false;
	    }
	    
	    boolean isNotPicture = !isPicture(responseHeader);

	    // -cookies in request
	    // unfortunately, we should reload in case of a cookie
	    // but we think that pictures can still be considered as fresh
	    if ((requestHeader.containsKey(httpHeader.COOKIE)) && (isNotPicture)) return false;
	    
	    // -set-cookie in cached response
	    // this is a similar case as for COOKIE.
	    if ((responseHeader.containsKey(httpHeader.SET_COOKIE)) && (isNotPicture)) return false; // too strong
	    if ((responseHeader.containsKey(httpHeader.SET_COOKIE2)) && (isNotPicture)) return false; // too strong
	    
	    // -pragma in cached response
	    // logically, we would not need to care about no-cache pragmas in cached response headers,
	    // because they cannot exist since they are not written to the cache.
	    // So this IF should always fail..
	    if ((responseHeader.containsKey(httpHeader.PRAGMA)) &&
		(((String) responseHeader.get(httpHeader.PRAGMA)).toUpperCase().equals("NO-CACHE"))) return false;
	    
	    // calculate often needed values for freshness attributes
	    Date date           = responseHeader.date();
	    Date expires        = responseHeader.expires();
	    Date lastModified   = responseHeader.lastModified();
	    String cacheControl = (String) responseHeader.get(httpHeader.CACHE_CONTROL);
	    
	    
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
		//System.out.println("EXPIRES-TEST: expires=" + expires + ", NOW=" + serverDate.correctedGMTDate() + ", url=" + url);
		if (expires.before(serverDate.correctedGMTDate())) return false;
	    }
	    
	    // -lastModified in cached response
	    // we can apply a TTL (Time To Live)  heuristic here. We call the time delta between the last read
	    // of the file and the last modified date as the age of the file. If we consider the file as
	    // middel-aged then, the maximum TTL would be cache-creation plus age.
	    // This would be a TTL factor of 100% we want no more than 10% TTL, so that a 10 month old cache
	    // file may only be treated as fresh for one more month, not more.
	    if (lastModified != null) {
		if (date == null) date = serverDate.correctedGMTDate();
		long age = date.getTime() - lastModified.getTime();
		if (age < 0) return false;
		// TTL (Time-To-Live) is age/10 = (d2.getTime() - d1.getTime()) / 10
		// the actual living-time is serverDate.correctedGMTDate().getTime() - d2.getTime()
		// therefore the cache is stale, if serverDate.correctedGMTDate().getTime() - d2.getTime() > age/10
		if (serverDate.correctedGMTDate().getTime() - date.getTime() > age / 10) return false;
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
                        if (serverDate.correctedGMTDate().getTime() - date.getTime() > ttl) {
                            return false;
                        }
                    } catch (Exception e) {
                        return false;
                    }
                }
	    }
	    
	    return true;
	}
        
        
    }
    
    /*
    public static void main(String[] args) {
        //String[] s = TimeZone.getAvailableIDs();
        //for (int i = 0; i < s.length; i++) System.out.println("ZONE=" + s[i]);
        Calendar c = GregorianCalendar.getInstance();
        int zoneOffset = c.get(Calendar.ZONE_OFFSET)/(60*60*1000);
        int DSTOffset = c.get(Calendar.DST_OFFSET)/(60*60*1000);
        System.out.println("This Offset = " + (zoneOffset + DSTOffset));
        for (int i = 0; i < 12; i++) {
            c = new GregorianCalendar(TimeZone.getTimeZone("Etc/GMT-" + i));
            //c.setTimeZone(TimeZone.getTimeZone("Etc/GMT+0"));
            System.out.println("Zone offset: "+
                     c.get(Calendar.ZONE_OFFSET)/(60*60*1000));
            System.out.println(c.get(GregorianCalendar.HOUR) + ", " + c.getTime() + ", " + c.getTimeInMillis());
        }
    }
     **/
}
