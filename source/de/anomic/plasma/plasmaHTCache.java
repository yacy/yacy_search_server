// plasmaHTCache.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.http.httpc;
import de.anomic.http.httpHeader;
import de.anomic.index.indexEntryAttribute;
import de.anomic.index.indexURL;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMap;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.server.logging.serverLog;
import de.anomic.server.serverDate;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverInstantThread;
import de.anomic.server.serverSystem;
import de.anomic.tools.enumerateFiles;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import de.anomic.net.URL;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TreeMap;

public final class plasmaHTCache {

    private static final int stackLimit = 150; // if we exceed that limit, we do not check idle
    public  static final long oneday = 1000 * 60 * 60 * 24; // milliseconds of a day

    private kelondroMap responseHeaderDB = null;
    private final LinkedList cacheStack;
    private final TreeMap cacheAge; // a <date+hash, cache-path> - relation
    public long curCacheSize;
    public long maxCacheSize;
    public final File cachePath;
    public final serverLog log;
    public static final HashSet filesInUse = new HashSet(); // can we delete this file

    public plasmaHTCache(File htCachePath, long maxCacheSize, int bufferkb, long preloadTime) {
        // this.switchboard = switchboard;

        this.log = new serverLog("HTCACHE");
        this.cachePath = htCachePath;

        // reset old HTCache ?
        String[] list = this.cachePath.list();
        if (list != null) {
            File object;
            for (int i = list.length - 1; i >= 0; i--) {
                object = new File(this.cachePath, list[i]);

                if (!object.isDirectory()) { continue; }

                if (!object.getName().equals("http") &&
                    !object.getName().equals("yacy") &&
                    !object.getName().equals("https") &&
                    !object.getName().equals("ftp")) {
                    deleteOldHTCache(this.cachePath);
                    break;

                }
            }
        }
        File testpath = new File(this.cachePath, "/http/");
        list = testpath.list();
        if (list != null) {
            File object;
            for (int i = list.length - 1; i >= 0; i--) {
                object = new File(testpath, list[i]);

                if (!object.isDirectory()) { continue; }

                if (!object.getName().equals("ip") &&
                    !object.getName().equals("other") &&
                    !object.getName().equals("www")) {
                    deleteOldHTCache(this.cachePath);
                    break;
                }
            }
        }
        testpath = null;


        // set/make cache path
        if (!htCachePath.exists()) {
            htCachePath.mkdirs();
        }
        if (!htCachePath.isDirectory()) {
            // if the cache does not exists or is a file and not a directory, panic
            this.log.logSevere("the cache path " + htCachePath.toString() + " is not a directory or does not exists and cannot be created");
            System.exit(0);
        }

        // open the response header database
        File dbfile = new File(this.cachePath, "responseHeader.db");
        try {
            if (dbfile.exists())
                this.responseHeaderDB = new kelondroMap(new kelondroDyn(dbfile, bufferkb * 0x400, preloadTime, '#'));
            else
                this.responseHeaderDB = new kelondroMap(new kelondroDyn(dbfile, bufferkb * 0x400, preloadTime, indexURL.urlHashLength, 150, '#', false));
        } catch (IOException e) {
            this.log.logSevere("the request header database could not be opened: " + e.getMessage());
            System.exit(0);
        }

        // init stack
        this.cacheStack = new LinkedList();

        // init cache age and size management
        this.cacheAge = new TreeMap();
        this.curCacheSize = 0;
        this.maxCacheSize = maxCacheSize;

        // start the cache startup thread
        // this will collect information about the current cache size and elements
        serverInstantThread.oneTimeJob(this, "cacheScan", this.log, 120000);
    }

    private void deleteOldHTCache(File directory) {
        String[] list = directory.list();
        if (list != null) {
            File object;
            for (int i = list.length - 1; i >= 0; i--) {
                object = new File(directory, list[i]);
                if (object.isFile()) {
                    object.delete();
                } else {
                    deleteOldHTCache(object);
                }
            }
        }
        directory.delete();
    }

    public int size() {
        synchronized (this.cacheStack) {
            return this.cacheStack.size();
        }
    }

    public int dbSize() {
        return this.responseHeaderDB.size();
    }

    public int dbCacheChunkSize() {
        return this.responseHeaderDB.cacheNodeChunkSize();
    }

    public int[] dbCacheStatus() {
        return this.responseHeaderDB.cacheNodeStatus();
    }

    public String[] dbCacheObjectStatus() {
        return this.responseHeaderDB.cacheObjectStatus();
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
        return null;
        }
    }

    public void storeHeader(String urlHash, httpHeader responseHeader) throws IOException {
        this.responseHeaderDB.set(urlHash, responseHeader);
    }

    /**
     * This method changes the HTCache size.<br>
     * @param new cache size in bytes
     */
    public void setCacheSize(long newCacheSize) {
        this.maxCacheSize = newCacheSize;
    }

    /**
     * This method returns the free HTCache size.<br>
     * @return the cache size in bytes
     */
    public long getFreeSize() {
        return (this.curCacheSize >= this.maxCacheSize) ? 0 : this.maxCacheSize - this.curCacheSize;
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
            this.log.logSevere("File storage failed (not a directory): " + e.getMessage());
            return false;
        } catch (IOException e) {
            this.log.logSevere("File storage failed (IO error): " + e.getMessage());
            return false;
        }
        writeFileAnnouncement(file);
        return true;
    }

    public void writeFileAnnouncement(File file) {
        synchronized (this.cacheAge) {
            if (file.exists()) {
                this.curCacheSize += file.length();
                this.cacheAge.put(ageString(file.lastModified(), file), file);
                cleanup();
            }
        }
    }

    public boolean deleteFile(URL url) {
        return deleteURLfromCache(url, "FROM");
    }

    private boolean deleteURLfromCache(URL url, String msg) {
        if (deleteFileandDirs(getCachePath(url), msg)) {
            try {
                // As the file is gone, the entry in responseHeader.db is not needed anymore
                this.log.logFinest("Trying to remove responseHeader from URL: " + url.toString());
                this.responseHeaderDB.remove(indexURL.urlHash(url));
            } catch (IOException e) {
                this.log.logInfo("IOExeption removing response header from DB: " + e.getMessage(), e);
            }
           return true;
       }
        return false;
    }

    private boolean deleteFile(File obj) {
        if (obj.exists() && !filesInUse.contains(obj)) {
            long size = obj.length();
            if (obj.delete()) {
                this.curCacheSize -= size;
                return true;
            }
        }
       return false;
    }

    private boolean deleteFileandDirs (File obj, String msg) {
        if (deleteFile(obj)) {
            this.log.logInfo("DELETED " + msg + " CACHE : " + obj.toString());
            obj = obj.getParentFile();
            // If the has been emptied, remove it
            // Loop as long as we produce empty driectoriers, but stop at HTCACHE
            while ((!(obj.equals(this.cachePath))) && (obj.isDirectory()) && (obj.list().length == 0)) {
                if (obj.delete()) this.log.logFine("DELETED EMPTY DIRECTORY : " + obj.toString());
                obj = obj.getParentFile();
            }
            return true;
         }
        return false;
    }

    private void cleanupDoIt(long newCacheSize) {
        File obj;
        Iterator iter = this.cacheAge.keySet().iterator();
        while (iter.hasNext() && this.curCacheSize >= newCacheSize) {
            Object key = iter.next();
            obj = (File) this.cacheAge.get(key);
            if (obj != null) {
                if (filesInUse.contains(obj)) continue;
                this.log.logFinest("Trying to delete old file: " + obj.toString());
                if (deleteFileandDirs (obj, "OLD")) {
                    try {
                        // As the file is gone, the entry in responseHeader.db is not needed anymore
                        this.log.logFinest("Trying to remove responseHeader for URL: " +
                            getURL(this.cachePath ,obj).toString());
                        this.responseHeaderDB.remove(indexURL.urlHash(getURL(this.cachePath ,obj)));
                    } catch (IOException e) {
                        this.log.logInfo("IOExeption removing response header from DB: " +
                            e.getMessage(), e);
                    }
                }
            }
            iter.remove();
        }
    }

    private void cleanup() {
        // clean up cache to have 4% (enough) space for next entries
        if (this.cacheAge.size() > 0 &&
            this.curCacheSize >= this.maxCacheSize &&
            this.maxCacheSize > 0) {
            cleanupDoIt(this.maxCacheSize - (this.maxCacheSize / 100) * 4);
        }
    }

    public void close() {
        try {this.responseHeaderDB.close();} catch (IOException e) {}
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
        log.logConfig("STARTING HTCACHE SCANNING");
        kelondroMScoreCluster doms = new kelondroMScoreCluster();
        int c = 0;
        enumerateFiles ef = new enumerateFiles(this.cachePath, true, false, true, true);
        File f;
        while (ef.hasMoreElements()) {
            c++;
            f = (File) ef.nextElement();
            long d = f.lastModified();
            //System.out.println("Cache: " + dom(f));
            doms.incScore(dom(f));
            this.curCacheSize += f.length();
            this.cacheAge.put(ageString(d, f), f);
            try {Thread.sleep(10);} catch (InterruptedException e) {}
        }
        //System.out.println("%" + (String) cacheAge.firstKey() + "=" + cacheAge.get(cacheAge.firstKey()));
        long ageHours = 0;
        try {
            ageHours = (System.currentTimeMillis() -
                            Long.parseLong(((String) this.cacheAge.firstKey()).substring(0, 16), 16)) / 3600000;
        } catch (NumberFormatException e) {
            //e.printStackTrace();
        }
        this.log.logConfig("CACHE SCANNED, CONTAINS " + c +
                      " FILES = " + this.curCacheSize/1048576 + "MB, OLDEST IS " + 
            ((ageHours < 24) ? (ageHours + " HOURS") : ((ageHours / 24) + " DAYS")) + " OLD");
        cleanup();

        log.logConfig("STARTING DNS PREFETCH");
        // start to prefetch IPs from DNS
        String dom;
        long start = System.currentTimeMillis();
        String result = "";
        c = 0;
        while ((doms.size() > 0) && (c < 50) && ((System.currentTimeMillis() - start) < 60000)) {
            dom = (String) doms.getMaxObject();
            InetAddress ip = httpc.dnsResolve(dom);
            if (ip == null) continue;
            result += ", " + dom + "=" + ip.getHostAddress();
            this.log.logConfig("PRE-FILLED " + dom + "=" + ip.getHostAddress());
            c++;
            doms.deleteScore(dom);
            // wait a short while to prevent that this looks like a DoS
            try {Thread.sleep(100);} catch (InterruptedException e) {}
        }
        if (result.length() > 2) this.log.logConfig("PRE-FILLED DNS CACHE, FETCHED " + c +
                                               " ADDRESSES: " + result.substring(2));
    }

    private String dom(File f) {
        String s = f.toString().substring(this.cachePath.toString().length() + 1);
        int p = s.indexOf("/");
        if (p < 0) p = s.indexOf("\\");
        if (p < 0) return null;
        return s.substring(0, p);
    }

    public httpHeader getCachedResponse(String urlHash) throws IOException {
        Map hdb = this.responseHeaderDB.get(urlHash);
        if (hdb == null) return null;
        return new httpHeader(null, hdb);
    }

    public boolean full() {
        return (this.cacheStack.size() > stackLimit);
    }

    public boolean empty() {
        return (this.cacheStack.size() == 0);
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

    private String replaceRegex(String input, String regex, String replacement) {
        if (input == null) { return ""; }
        if (input.length() > 0) {
            final Pattern searchPattern = Pattern.compile(regex);
            final Matcher matcher = searchPattern.matcher(input);
            while (matcher.find()) {
                input = matcher.replaceAll(replacement);
                matcher.reset(input);
            }
        }
        return input;
    }

    /**
     * this method creates from a given host and path a cache path
     * from a given host (which may also be an IPv4 - number, but not IPv6 or
     * a domain; all without leading 'http://') and a path (which must start
     * with a leading '/', and may also end in an '/') a path to a file
     * in the file system with root as given in cachePath is constructed
     * it will also be ensured, that the complete path exists; if necessary
     * that path will be generated
     * @return new File
     */
    public File getCachePath(final URL url) {
//      this.log.logFinest("plasmaHTCache: getCachePath:  IN=" + url.toString());

        // peer.yacy || www.peer.yacy  = http/yacy/peer
        // protocol://www.doamin.net   = protocol/www/domain.net
        // protocol://other.doamin.net = protocol/other/other.domain.net
        // protocol://xxx.xxx.xxx.xxx  = protocol/ip/xxx.xxx.xxx.xxx

        String host = url.getHost().toLowerCase();

        String path = url.getPath();
        final String query = url.getQuery();
        if (!path.startsWith("/")) { path = "/" + path; }
        if (path.endsWith("/") && query == null) { path = path + "ndx"; }

        // yes this is not reversible, but that is not needed
        path = replaceRegex(path, "/\\.\\./", "/!!/");
        path = replaceRegex(path, "(\"|\\\\|\\*|\\?|:|<|>|\\|+)", "_"); // hier wird kein '/' gefiltert
        path = path.concat(replaceRegex(query, "(\"|\\\\|\\*|\\?|/|:|<|>|\\|+)", "_"));

        // only set NO default ports
        int port = url.getPort();
        String protocol = url.getProtocol();
        if (port >= 0) {
            if ((port ==  80 && protocol.equals("http" )) ||
                (port == 443 && protocol.equals("https")) ||
                (port ==  21 && protocol.equals("ftp"  ))) {
                 port = -1;
            }
        }
        if (host.endsWith(".yacy")) {
            host = host.substring(0, host.length() - 5);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            protocol = "yacy";
        } else if (host.startsWith("www.")) {
            host = "www/" + host.substring(4);
        } else if (host.matches("\\d{2,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            host = "ip/" + host;
        } else {
            host = "other/" + host;
        }
        if (port < 0) {
            return new File(this.cachePath, protocol + "/" + host + path);
        } else {
            return new File(this.cachePath, protocol + "/" + host + "!" + port + path);
        }
    }

    /**
     * this is the reverse function to getCachePath: it constructs the url as string
     * from a given storage path
     */
    public static URL getURL(final File cachePath, final File f) {
//      this.log.logFinest("plasmaHTCache: getURL:  IN: Path=[" + cachePath + "] File=[" + f + "]");
        final String c = cachePath.toString().replace('\\', '/');
        String path = f.toString().replace('\\', '/');

        if (path.endsWith("ndx")) { path = path.substring(0, path.length() - 3); }

        int pos = path.lastIndexOf(c);
        if (pos == 0) {
            path = path.substring(pos + c.length());
            while (path.startsWith("/")) { path = path.substring(1); }

            pos = path.indexOf("!");
            if (pos >= 0) {
                path = path.substring(0, pos) + ":" + path.substring(pos + 1);
            }

            String protocol = "http://";
            String host = "";
            if (path.startsWith("yacy/")) {
                path = path.substring(5);

                pos = path.indexOf("/");
                if (pos > 0) {
                    host = path.substring(0, pos);
                    path = path.substring(pos);
                } else {
                    host = path;
                    path = "";
                }
                pos = host.indexOf(":");
                if (pos > 0) {
                    host = host.substring(0, pos) + ".yacy" + host.substring(pos);
                } else {
                    host = host + ".yacy";
                }

            } else {
                if (path.startsWith("http/")) {
                    path = path.substring(5);
                } else if (path.startsWith("https/")) {
                    protocol = "https://";
                    path = path.substring(6);
                } else if (path.startsWith("ftp/")) {
                    protocol = "ftp://";
                    path = path.substring(4);
                } else {
                    return null;
                }
                if (path.startsWith("www/")) {
                    path = path.substring(4);
                    host = "www.";
                } else if (path.startsWith("other/")) {
                    path = path.substring(6);
                } else if (path.startsWith("ip/")) {
                    path = path.substring(3);
                }
                pos = path.indexOf("/");
                if (pos > 0) {
                    host = host + path.substring(0, pos);
                    path = path.substring(pos);
                } else {
                    host = host + path;
                    path = "";
                }
            }

            if (!path.equals("")) {
                final Pattern pathPattern = Pattern.compile("/!!/");
                final Matcher matcher = pathPattern.matcher(path);
                while (matcher.find()) {
                    path = matcher.replaceAll("/\\.\\./");
                    matcher.reset(path);
                }
            }

//          this.log.logFinest("plasmaHTCache: getURL: OUT=" + s);
            try {
                return new URL(protocol + host + path);
            } catch (final Exception e) {
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
        }
        return null;
    }

    public static boolean isPOST(String urlString) {
        return (urlString.indexOf("?") >= 0 ||
                urlString.indexOf("&") >= 0);
    }

    public static boolean isCGI(String urlString) {
        String ls = urlString.toLowerCase();
        return ((ls.indexOf(".cgi") >= 0) ||
                (ls.indexOf(".exe") >= 0) ||
                (ls.indexOf(";jsessionid=") >= 0) ||
                (ls.indexOf("sessionid/") >= 0) ||
                (ls.indexOf("phpsessid=") >= 0) ||
                (ls.indexOf("search.php?sid=") >= 0) ||
                (ls.indexOf("memberlist.php?sid=") >= 0));
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

    protected Object clone() throws CloneNotSupportedException {
        return new Entry(
                this.initDate,
                this.depth,
                this.url,
                this.name,
                this.requestHeader,
                this.responseStatus,
                this.responseHeader,
                this.initiator,
                this.profile
        );
    }

    public Entry(Date initDate, int depth, URL url, String name,
                 httpHeader requestHeader,
                 String responseStatus, httpHeader responseHeader,
                 String initiator,
                 plasmaCrawlProfile.entry profile) {

        // normalize url
//      serverLog.logFine("PLASMA", "Entry: URL=" + url.toString());
        this.nomalizedURLString = htmlFilterContentScraper.urlNormalform(url);

        try {
            this.url            = new URL(this.nomalizedURLString);
        } catch (MalformedURLException e) {
            System.out.println("internal error at httpdProxyCache.Entry: " + e);
            System.exit(-1);
        }
        this.name             = name;
        this.cacheFile        = getCachePath(this.url);
        this.nomalizedURLHash = indexURL.urlHash(this.nomalizedURLString);

       // assigned:
        this.initDate       = initDate;
        this.depth          = depth;
        this.requestHeader  = requestHeader;
        this.responseStatus = responseStatus;
        this.responseHeader = responseHeader;
        this.profile        = profile;
        this.initiator      = (initiator == null) ? null : ((initiator.length() == 0) ? null : initiator);

        // calculated:
        if (responseHeader == null) {
           try {
               throw new RuntimeException("RESPONSE HEADER = NULL");
           } catch (Exception e) {
               System.out.println("RESPONSE HEADER = NULL in " + url);
               e.printStackTrace();
               System.exit(0);
           }

            this.lastModified = new Date(serverDate.correctedUTCTime());
        } else {
            this.lastModified = responseHeader.lastModified();
            if (this.lastModified == null) this.lastModified = new Date(serverDate.correctedUTCTime()); // does not exist in header
        }
        this.doctype = indexEntryAttribute.docType(responseHeader.mime());
        if (this.doctype == indexEntryAttribute.DT_UNKNOWN) this.doctype = indexEntryAttribute.docType(url);
        this.language = indexEntryAttribute.language(url);

        // to be defined later:
        this.cacheArray     = null;
    }

    public String name() {
        return this.name;
    }
    public String initiator() {
        return this.initiator;
    }
    public boolean proxy() {
        return initiator() == null;
    }
    public long size() {
        if (this.cacheArray == null) return 0;
        return this.cacheArray.length;
    }

    public URL referrerURL() {
        if (this.requestHeader == null) return null;
        try {
            return new URL((String) this.requestHeader.get(httpHeader.REFERER, ""));
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

        // check profile (disabled: we will check this in the plasmaSwitchboard)
        //if (!this.profile.storeHTCache()) { return "storage_not_wanted"; }

        // decide upon header information if a specific file should be stored to the cache or not
        // if the storage was requested by prefetching, the request map is null

        // check status code
        if (!(this.responseStatus.startsWith("200") ||
              this.responseStatus.startsWith("203"))) { return "bad_status_" + this.responseStatus.substring(0,3); }

        // check storage location
        // sometimes a file name is equal to a path name in the same directory;
        // or sometimes a file name is equal a directory name created earlier;
        // we cannot match that here in the cache file path and therefore omit writing into the cache
        if (this.cacheFile.getParentFile().isFile() || this.cacheFile.isDirectory()) { return "path_ambiguous"; }
        if (this.cacheFile.toString().indexOf("..") >= 0) { return "path_dangerous"; }
        if (this.cacheFile.getAbsolutePath().length() > serverSystem.maxPathLength) { return "path too long"; }

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable in caches
        if (isPOST(this.nomalizedURLString) && !this.profile.crawlingQ()) { return "dynamic_post"; }
        if (isCGI(this.nomalizedURLString)) { return "dynamic_cgi"; }

        if (this.requestHeader != null) {
            // -authorization cases in request
            // authorization makes pages very individual, and therefore we cannot use the
            // content in the cache
            if (this.requestHeader.containsKey(httpHeader.AUTHORIZATION)) { return "personalized"; }
            // -ranges in request and response
            // we do not cache partial content
            if (this.requestHeader.containsKey(httpHeader.RANGE)) { return "partial"; }
        }
        // -ranges in request and response
        // we do not cache partial content
        if (this.responseHeader != null && this.responseHeader.containsKey(httpHeader.CONTENT_RANGE)) { return "partial"; }

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
        String cacheControl = (String) this.responseHeader.get(httpHeader.PRAGMA);
        if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return "controlled_no_cache"; }

        // -expires in response
        // we do not care about expires, because at the time this is called the data is
        // obvious valid and that header info is used in the indexing later on

        // -cache-control in response
        // the cache-control has many value options.
        cacheControl = (String) this.responseHeader.get(httpHeader.CACHE_CONTROL);
        if (cacheControl != null) {
            cacheControl = cacheControl.trim().toUpperCase();
            if (cacheControl.startsWith("MAX-AGE=")) {
                // we need also the load date
                Date date = this.responseHeader.date();
                if (date == null) return "stale_no_date_given_in_response";
                try {
                    long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                    if (serverDate.correctedUTCTime() - date.getTime() > ttl) {
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

    /**
     * decide upon header information if a specific file should be taken from the cache or not
     * @return
     */
    public boolean shallUseCacheForProxy() {
//      System.out.println("SHALL READ CACHE: requestHeader = " + requestHeader.toString() + ", responseHeader = " + responseHeader.toString());

        String cacheControl;
        if (this.requestHeader != null) {
            // -authorization cases in request
            if (this.requestHeader.containsKey(httpHeader.AUTHORIZATION)) { return false; }

            // -ranges in request
            // we do not cache partial content
            if (this.requestHeader.containsKey(httpHeader.RANGE)) { return false; }

            // if the client requests a un-cached copy of the resource ...
            cacheControl = (String) this.requestHeader.get(httpHeader.PRAGMA);
            if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return false; }

            cacheControl = (String) this.requestHeader.get(httpHeader.CACHE_CONTROL);
            if (cacheControl != null) {
                cacheControl = cacheControl.trim().toUpperCase();
                if (cacheControl.startsWith("NO-CACHE") || cacheControl.startsWith("MAX-AGE=0")) { return false; }
            }
        }

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable in caches
        if (isPOST(this.nomalizedURLString)) { return false; }
        if (isCGI(this.nomalizedURLString)) { return false; }

        // -if-modified-since in request
        // The entity has to be transferred only if it has
        // been modified since the date given by the If-Modified-Since header.
        if (this.requestHeader.containsKey(httpHeader.IF_MODIFIED_SINCE)) {
            // checking this makes only sense if the cached response contains
            // a Last-Modified field. If the field does not exist, we go the safe way
            if (!this.responseHeader.containsKey(httpHeader.LAST_MODIFIED)) { return false; }
            // parse date
            Date d1, d2;
            d2 = this.responseHeader.lastModified(); if (d2 == null) { d2 = new Date(serverDate.correctedUTCTime()); }
            d1 = this.requestHeader.ifModifiedSince(); if (d1 == null) { d1 = new Date(serverDate.correctedUTCTime()); }
            // finally, we shall treat the cache as stale if the modification time is after the if-.. time
            if (d2.after(d1)) { return false; }
        }

        if (!isPicture(this.responseHeader)) {
            // -cookies in request
            // unfortunately, we should reload in case of a cookie
            // but we think that pictures can still be considered as fresh
            // -set-cookie in cached response
            // this is a similar case as for COOKIE.
            if (this.requestHeader.containsKey(httpHeader.COOKIE) ||
                this.responseHeader.containsKey(httpHeader.SET_COOKIE) ||
                this.responseHeader.containsKey(httpHeader.SET_COOKIE2)) {
                return false; // too strong
            }
        }

        // -pragma in cached response
        // logically, we would not need to care about no-cache pragmas in cached response headers,
        // because they cannot exist since they are not written to the cache.
        // So this IF should always fail..
        cacheControl = (String) this.responseHeader.get(httpHeader.PRAGMA); 
        if (cacheControl != null && cacheControl.trim().toUpperCase().equals("NO-CACHE")) { return false; }

        // see for documentation also:
        // http://www.web-caching.com/cacheability.html
        // http://vancouver-webpages.com/CacheNow/

        // look for freshnes information
        // if we don't have any freshnes indication, we treat the file as stale.
        // no handle for freshness control:

        // -expires in cached response
        // the expires value gives us a very easy hint when the cache is stale
        Date expires = this.responseHeader.expires();
        if (expires != null) {
//          System.out.println("EXPIRES-TEST: expires=" + expires + ", NOW=" + serverDate.correctedGMTDate() + ", url=" + url);
            if (expires.before(new Date(serverDate.correctedUTCTime()))) { return false; }
        }
        Date lastModified = this.responseHeader.lastModified();
        cacheControl = (String) this.responseHeader.get(httpHeader.CACHE_CONTROL);
        if (cacheControl == null && lastModified == null && expires == null) { return false; }

        // -lastModified in cached response
        // we can apply a TTL (Time To Live)  heuristic here. We call the time delta between the last read
        // of the file and the last modified date as the age of the file. If we consider the file as
        // middel-aged then, the maximum TTL would be cache-creation plus age.
        // This would be a TTL factor of 100% we want no more than 10% TTL, so that a 10 month old cache
        // file may only be treated as fresh for one more month, not more.
        Date date = this.responseHeader.date();
        if (lastModified != null) {
            if (date == null) { date = new Date(serverDate.correctedUTCTime()); }
            long age = date.getTime() - lastModified.getTime();
            if (age < 0) { return false; }
            // TTL (Time-To-Live) is age/10 = (d2.getTime() - d1.getTime()) / 10
            // the actual living-time is serverDate.correctedGMTDate().getTime() - d2.getTime()
            // therefore the cache is stale, if serverDate.correctedGMTDate().getTime() - d2.getTime() > age/10
            if (serverDate.correctedUTCTime() - date.getTime() > age / 10) { return false; }
        }

        // -cache-control in cached response
        // the cache-control has many value options.
        if (cacheControl != null) {
            cacheControl = cacheControl.trim().toUpperCase();
            if (cacheControl.startsWith("PRIVATE") ||
                cacheControl.startsWith("NO-CACHE") ||
                cacheControl.startsWith("NO-STORE")) {
                // easy case
                return false;
//          } else if (cacheControl.startsWith("PUBLIC")) {
//              // ok, do nothing
            } else if (cacheControl.startsWith("MAX-AGE=")) {
                // we need also the load date
                if (date == null) { return false; }
                try {
                    final long ttl = 1000 * Long.parseLong(cacheControl.substring(8)); // milliseconds to live
                    if (serverDate.correctedUTCTime() - date.getTime() > ttl) {
                        return false;
                    }
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return true;
    }

    } // class Entry

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
