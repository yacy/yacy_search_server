// plasmaHTCache.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

/*
   Class documentation:
   This class has two purposes:
   1. provide a object that carries path and header information
      that shall be used as objects within a scheduler's stack
   2. static methods for a cache control and cache aging
    the class shall also be used to do a cache-cleaning and index creation
*/

package de.anomic.plasma;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.crawler.CrawlProfile;
import de.anomic.http.httpHeader;
import de.anomic.kelondro.kelondroBLOB;
import de.anomic.kelondro.kelondroBLOBHeap;
import de.anomic.kelondro.kelondroBLOBTree;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroMap;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.ResourceInfoFactory;
import de.anomic.plasma.cache.UnsupportedProtocolException;
import de.anomic.server.serverCodings;
import de.anomic.server.serverDomains;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverInstantBusyThread;
import de.anomic.server.serverSystem;
import de.anomic.server.serverThread;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.enumerateFiles;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public final class plasmaHTCache {
    
    public static final String DB_NAME = "responseHeader.heap";
    
    private static final int stackLimit = 150; // if we exceed that limit, we do not check idle
    public  static final long oneday = 1000 * 60 * 60 * 24; // milliseconds of a day

    private static kelondroMap responseHeaderDB = null;
    private static final ConcurrentLinkedQueue<Entry> cacheStack = new ConcurrentLinkedQueue<Entry>();
    private static final ConcurrentHashMap<String, File> cacheAge = new ConcurrentHashMap<String, File>(); // a <date+hash, cache-path> - relation
    public static long curCacheSize = 0;
    public static long maxCacheSize = 0l;
    public static File cachePath = null;
    public static final serverLog log = new serverLog("HTCACHE");
    private static long lastcleanup = System.currentTimeMillis();
    
    private static ResourceInfoFactory objFactory = new ResourceInfoFactory();
    private static serverThread cacheScanThread = null;

    // doctypes:
    public static final char DT_PDFPS   = 'p';
    public static final char DT_TEXT    = 't';
    public static final char DT_HTML    = 'h';
    public static final char DT_DOC     = 'd';
    public static final char DT_IMAGE   = 'i';
    public static final char DT_MOVIE   = 'm';
    public static final char DT_FLASH   = 'f';
    public static final char DT_SHARE   = 's';
    public static final char DT_AUDIO   = 'a';
    public static final char DT_BINARY  = 'b';
    public static final char DT_UNKNOWN = 'u';

    // URL attributes
    public static final int UA_LOCAL    =  0; // URL was crawled locally
    public static final int UA_TILDE    =  1; // tilde appears in URL
    public static final int UA_REDIRECT =  2; // The URL is a redirection
    
    // local flag attributes
    public static final char LT_LOCAL   = 'L';
    public static final char LT_GLOBAL  = 'G';

    // doctype calculation
    public static char docType(final yacyURL url) {
        final String path = url.getPath().toLowerCase();
        // serverLog.logFinest("PLASMA", "docType URL=" + path);
        char doctype = DT_UNKNOWN;
        if (path.endsWith(".gif"))       { doctype = DT_IMAGE; }
        else if (path.endsWith(".ico"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".bmp"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpg"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpeg")) { doctype = DT_IMAGE; }
        else if (path.endsWith(".png"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".html")) { doctype = DT_HTML;  }
        else if (path.endsWith(".txt"))  { doctype = DT_TEXT;  }
        else if (path.endsWith(".doc"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".rtf"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".pdf"))  { doctype = DT_PDFPS; }
        else if (path.endsWith(".ps"))   { doctype = DT_PDFPS; }
        else if (path.endsWith(".avi"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".mov"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".qt"))   { doctype = DT_MOVIE; }
        else if (path.endsWith(".mpg"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".md5"))  { doctype = DT_SHARE; }
        else if (path.endsWith(".mpeg")) { doctype = DT_MOVIE; }
        else if (path.endsWith(".asf"))  { doctype = DT_FLASH; }
        return doctype;
    }

    public static char docType(final String mime) {
        // serverLog.logFinest("PLASMA", "docType mime=" + mime);
        char doctype = DT_UNKNOWN;
        if (mime == null) doctype = DT_UNKNOWN;
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.endsWith("/gif")) doctype = DT_IMAGE;
        else if (mime.endsWith("/jpeg")) doctype = DT_IMAGE;
        else if (mime.endsWith("/png")) doctype = DT_IMAGE;
        else if (mime.endsWith("/html")) doctype = DT_HTML;
        else if (mime.endsWith("/rtf")) doctype = DT_DOC;
        else if (mime.endsWith("/pdf")) doctype = DT_PDFPS;
        else if (mime.endsWith("/octet-stream")) doctype = DT_BINARY;
        else if (mime.endsWith("/x-shockwave-flash")) doctype = DT_FLASH;
        else if (mime.endsWith("/msword")) doctype = DT_DOC;
        else if (mime.endsWith("/mspowerpoint")) doctype = DT_DOC;
        else if (mime.endsWith("/postscript")) doctype = DT_PDFPS;
        else if (mime.startsWith("text/")) doctype = DT_TEXT;
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.startsWith("audio/")) doctype = DT_AUDIO;
        else if (mime.startsWith("video/")) doctype = DT_MOVIE;
        //bz2     = application/x-bzip2
        //dvi     = application/x-dvi
        //gz      = application/gzip
        //hqx     = application/mac-binhex40
        //lha     = application/x-lzh
        //lzh     = application/x-lzh
        //pac     = application/x-ns-proxy-autoconfig
        //php     = application/x-httpd-php
        //phtml   = application/x-httpd-php
        //rss     = application/xml
        //tar     = application/tar
        //tex     = application/x-tex
        //tgz     = application/tar
        //torrent = application/x-bittorrent
        //xhtml   = application/xhtml+xml
        //xla     = application/msexcel
        //xls     = application/msexcel
        //xsl     = application/xml
        //xml     = application/xml
        //Z       = application/x-compress
        //zip     = application/zip
        return doctype;
    }
    
    public static void init(final File htCachePath, final long CacheSizeMax) {
        
        cachePath = htCachePath;
        maxCacheSize = CacheSizeMax;
        

        // reset old HTCache ?
        String[] list = cachePath.list();
        if (list != null) {
            File object;
            for (int i = list.length - 1; i >= 0; i--) {
                object = new File(cachePath, list[i]);

                if (!object.isDirectory()) { continue; }

                if (!object.getName().equals("http") &&
                    !object.getName().equals("yacy") &&
                    !object.getName().equals("https") &&
                    !object.getName().equals("ftp")) {
                    deleteOldHTCache(cachePath);
                    break;

                }
            }
        }
        File testpath = new File(cachePath, "/http/");
        list = testpath.list();
        if (list != null) {
            File object;
            for (int i = list.length - 1; i >= 0; i--) {
                object = new File(testpath, list[i]);

                if (!object.isDirectory()) { continue; }

                if (!object.getName().equals("ip") &&
                    !object.getName().equals("other") &&
                    !object.getName().equals("www")) {
                    deleteOldHTCache(cachePath);
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
            log.logSevere("the cache path " + htCachePath.toString() + " is not a directory or does not exists and cannot be created");
            System.exit(0);
        }

        // open the response header database
        openResponseHeaderDB();

        // start the cache startup thread
        // this will collect information about the current cache size and elements
        try {
            cacheScanThread = serverInstantBusyThread.oneTimeJob(Class.forName("de.anomic.plasma.plasmaHTCache"), "cacheScan", log, 120000);
        } catch (final ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    static void resetResponseHeaderDB() {
        if (responseHeaderDB != null) responseHeaderDB.close();
        final File dbfile = new File(cachePath, DB_NAME);
        if (dbfile.exists()) dbfile.delete();
        openResponseHeaderDB();
    }
    
    private static void openResponseHeaderDB() {
        // open the response header database
        final File dbfile = new File(cachePath, DB_NAME);
        kelondroBLOB blob = null;
        if (DB_NAME.endsWith("heap")) {
            try {
                blob = new kelondroBLOBHeap(dbfile, yacySeedDB.commonHashLength, kelondroBase64Order.enhancedCoder);
            } catch (final IOException e) {
                e.printStackTrace();
            }
        } else {
            blob = new kelondroBLOBTree(dbfile, true, true, yacySeedDB.commonHashLength, 150, '#', kelondroBase64Order.enhancedCoder, false, false, true);
        }
        responseHeaderDB = new kelondroMap(blob, 500);
    }
    
    private static void deleteOldHTCache(final File directory) {
        final String[] list = directory.list();
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

    public static int size() {
        return cacheStack.size();
    }

    public static int dbSize() {
        return responseHeaderDB.size();
    }
    
    public static void push(final Entry entry) {
        cacheStack.add(entry);
    }

    public static Entry pop() {
        return cacheStack.poll();
    }

    /**
     * This method changes the HTCache size.<br>
     * @param the new cache size in bytes
     */
    public static void setCacheSize(final long newCacheSize) {
        maxCacheSize = newCacheSize;
    }

    /**
     * This method returns the free HTCache size.<br>
     * @return the cache size in bytes
     */
    public static long getFreeSize() {
        return (curCacheSize >= maxCacheSize) ? 0 : maxCacheSize - curCacheSize;
    }

    public static boolean writeResourceContent(final yacyURL url, final byte[] array) {
        if (array == null) return false;
        final File file = getCachePath(url);
        try {
            deleteFile(file);
            file.getParentFile().mkdirs();
            serverFileUtils.copy(array, file);
        } catch (final FileNotFoundException e) {
            // this is the case of a "(Not a directory)" error, which should be prohibited
            // by the shallStoreCache() property. However, sometimes the error still occurs
            // In this case do nothing.
            log.logSevere("File storage failed (not a directory): " + e.getMessage());
            return false;
        } catch (final IOException e) {
            log.logSevere("File storage failed (IO error): " + e.getMessage());
            return false;
        }
        writeFileAnnouncement(file);
        return true;
    }

    public static void writeFileAnnouncement(final File file) {
        if (file.exists()) {
            curCacheSize += file.length();
            if (System.currentTimeMillis() - lastcleanup > 300000) {
                // call the cleanup job only every 5 minutes
                cleanup();
                lastcleanup = System.currentTimeMillis();
            }
            cacheAge.put(ageString(file.lastModified(), file), file);
        }
    }
    
    public static boolean deleteURLfromCache(final yacyURL url) {
        if (deleteFileandDirs(getCachePath(url), "FROM")) {
            try {
                // As the file is gone, the entry in responseHeader.db is not needed anymore
                if (log.isFinest()) log.logFinest("Trying to remove responseHeader from URL: " + url.toNormalform(false, true));
                responseHeaderDB.remove(url.hash());
            } catch (final IOException e) {
                resetResponseHeaderDB();
                log.logInfo("IOExeption removing response header from DB: " + e.getMessage(), e);
            }
           return true;
       }
        return false;
    }

    private static boolean deleteFile(final File obj) {
        if (obj.exists()) {
            final long size = obj.length();
            if (obj.delete()) {
                curCacheSize -= size;
                return true;
            }
        }
       return false;
    }

    private static boolean deleteFileandDirs(File path, final String msg) {
        if (deleteFile(path)) {
            log.logInfo("DELETED " + msg + " CACHE: " + path.toString());
            path = path.getParentFile(); // returns null if the path does not have a parent
            // If the has been emptied, remove it
            // Loop as long as we produce empty directories, but stop at HTCACHE
            while (path != null && !(path.equals(cachePath)) && path.isDirectory() && path.list().length == 0) { // NPE
                if (path.delete()) if (log.isFine()) log.logFine("DELETED EMPTY DIRECTORY : " + path.toString());
                path = path.getParentFile(); // returns null if the path does not have a parent
            }
            return true;
         }
        return false;
    }

    private static void cleanupDoIt(final long newCacheSize) {
        File file;
        final Iterator<Map.Entry<String, File>> iter = cacheAge.entrySet().iterator();
        Map.Entry<String, File> entry;
        while (iter.hasNext() && curCacheSize >= newCacheSize) {
            if (Thread.currentThread().isInterrupted()) return;
            entry = iter.next();
            final String key = entry.getKey();
            file = entry.getValue();
            final long t = Long.parseLong(key.substring(0, 16), 16);
            if (System.currentTimeMillis() - t < 300000) break; // files must have been at least 5 minutes in the cache before they are deleted
            if (file != null) {
                if (log.isFinest()) log.logFinest("Trying to delete [" + key + "] = old file: " + file.toString());
                // This needs to be called *before* the file is deleted
                final String urlHash = getHash(file);
                if (deleteFileandDirs(file, "OLD")) {
                    try {
                        // As the file is gone, the entry in responseHeader.db is not needed anymore
                        if (urlHash != null) {
                            if (log.isFinest()) log.logFinest("Trying to remove responseHeader for URLhash: " + urlHash);
                            responseHeaderDB.remove(urlHash);
                        } else {
                            final yacyURL url = getURL(file);
                            if (url != null) {
                                if (log.isFinest()) log.logFinest("Trying to remove responseHeader for URL: " + url.toNormalform(false, true));
                                responseHeaderDB.remove(url.hash());
                            }
                        }
                    } catch (final IOException e) {
                        log.logInfo("IOExeption removing response header from DB: " + e.getMessage(), e);
                    }
                }
            }
            iter.remove();
        }
    }

    private static void cleanup() {
        // clean up cache to have 4% (enough) space for next entries
        if (cacheAge.size() > 0 &&
            curCacheSize >= maxCacheSize &&
            maxCacheSize > 0) {
            cleanupDoIt(maxCacheSize - (maxCacheSize / 100) * 4);
        }
    }

    public static void close() {
        // closing cache scan if still running
        if ((cacheScanThread != null) && (cacheScanThread.isAlive())) {
            cacheScanThread.terminate(true);
        }
        
        // closing DB
        responseHeaderDB.close();
    }

    private static String ageString(final long date, final File f) {
        final StringBuffer sb = new StringBuffer(32);
        String s = Long.toHexString(date);
        for (int i = s.length(); i < 16; i++) sb.append('0');
            sb.append(s);
            s = Integer.toHexString(f.hashCode());
            for (int i = s.length(); i < 8; i++) sb.append('0');
            sb.append(s);
        return sb.toString();
    }

    public static void cacheScan() {
        log.logConfig("STARTING HTCACHE SCANNING");
        final kelondroMScoreCluster<String> doms = new kelondroMScoreCluster<String>();
        int fileCount = 0;
        final enumerateFiles fileEnum = new enumerateFiles(cachePath, true, false, true, true);
        final File dbfile = new File(cachePath, "responseHeader.db");
        while (fileEnum.hasMoreElements()) {
            if (Thread.currentThread().isInterrupted()) return;
            fileCount++;
            final File nextFile = fileEnum.nextElement();
            final long nextFileModDate = nextFile.lastModified();
            //System.out.println("Cache: " + dom(f));
            doms.incScore(dom(nextFile));
            curCacheSize += nextFile.length();
            if (!dbfile.equals(nextFile)) cacheAge.put(ageString(nextFileModDate, nextFile), nextFile);
            try {
                Thread.sleep(10);
            } catch (final InterruptedException e) {
                return;
            }
        }
        //System.out.println("%" + (String) cacheAge.firstKey() + "=" + cacheAge.get(cacheAge.firstKey()));
        long ageHours = 0;
        if (!cacheAge.isEmpty()) {
            final Iterator<String> i = cacheAge.keySet().iterator();
            if (i.hasNext()) try {
                ageHours = (System.currentTimeMillis() - Long.parseLong(i.next().substring(0, 16), 16)) / 3600000;
            } catch (final NumberFormatException e) {
                ageHours = 0;
            } else {
                ageHours = 0;
            }
        }
        log.logConfig("CACHE SCANNED, CONTAINS " + fileCount +
                      " FILES = " + curCacheSize/1048576 + "MB, OLDEST IS " + 
            ((ageHours < 24) ? (ageHours + " HOURS") : ((ageHours / 24) + " DAYS")) + " OLD");
        cleanup();

        log.logConfig("STARTING DNS PREFETCH");
        // start to prefetch IPs from DNS
        String dom;
        final long start = System.currentTimeMillis();
        String result = "";
        fileCount = 0;
        while ((doms.size() > 0) && (fileCount < 50) && ((System.currentTimeMillis() - start) < 60000)) {
            if (Thread.currentThread().isInterrupted()) return;
            dom = doms.getMaxObject();
            final InetAddress ip = serverDomains.dnsResolve(dom);
            if (ip == null) continue;
            result += ", " + dom + "=" + ip.getHostAddress();
            log.logConfig("PRE-FILLED " + dom + "=" + ip.getHostAddress());
            fileCount++;
            doms.deleteScore(dom);
            // wait a short while to prevent that this looks like a DoS
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                return;
            }
        }
        if (result.length() > 2) log.logConfig("PRE-FILLED DNS CACHE, FETCHED " + fileCount +
                                               " ADDRESSES: " + result.substring(2));
    }

    private static String dom(final File f) {
        String s = f.toString().substring(cachePath.toString().length() + 1);
        int p = s.indexOf("/");
        if (p < 0) p = s.indexOf("\\");
        if (p < 0) return null;
        // remove the protokoll
        s = s.substring(p + 1);
        p = s.indexOf("/");
        if (p < 0) p = s.indexOf("\\");
        if (p < 0) return null;
        String prefix = new String("");
        if (s.startsWith("www")) prefix = new String("www.");
        // remove the www|other|ip directory
        s = s.substring(p + 1);
        p = s.indexOf("/");
        if (p < 0) p = s.indexOf("\\");
        if (p < 0) return null;
        final int e = s.indexOf("!");
        if ((e > 0) && (e < p)) p = e; // strip port
        return prefix + s.substring(0, p);
    }

    /**
     * Returns an object containing metadata about a cached resource
     * @param url the {@link URL} of the resource
     * @return an {@link IResourceInfo info object}
     * @throws <b>IllegalAccessException</b> if the {@link SecurityManager} doesn't allow instantiation
     * of the info object with the given protocol
     * @throws <b>UnsupportedProtocolException</b> if the protocol is not supported and therefore the
     * info object couldn't be created
     */
    public static IResourceInfo loadResourceInfo(final yacyURL url) throws UnsupportedProtocolException, IllegalAccessException {    
        
        // loading data from database
        Map<String, String> hdb;
        try {
            hdb = responseHeaderDB.get(url.hash());
        } catch (final IOException e) {
            return null;
        }
        if (hdb == null) return null;
        
        // generate the cached object
        final IResourceInfo cachedObj = objFactory.buildResourceInfoObj(url, hdb);
        return cachedObj;
    }
    
    public static ResourceInfoFactory getResourceInfoFactory() {
        return objFactory;
    }

    public static boolean full() {
        return (cacheStack.size() > stackLimit);
    }

    public static boolean empty() {
        return (cacheStack.size() == 0);
    }

    public static boolean isPicture(final String mimeType) {
        if (mimeType == null) return false;
        return mimeType.toUpperCase().startsWith("IMAGE");
    }

    public static boolean isText(final String mimeType) {
//      Object ct = response.get(httpHeader.CONTENT_TYPE);
//      if (ct == null) return false;
//      String t = ((String)ct).toLowerCase();
//      return ((t.startsWith("text")) || (t.equals("application/xhtml+xml")));
        return plasmaParser.supportedMimeTypesContains(mimeType);
    }

    public static boolean noIndexingURL(final yacyURL url) {
        if (url == null) return false;
        String urlString = url.toString().toLowerCase();
        
        //http://www.yacy.net/getimage.php?image.png
        
        int idx = urlString.indexOf("?");
        if (idx > 0) urlString = urlString.substring(0,idx);

        //http://www.yacy.net/getimage.php
        
        idx = urlString.lastIndexOf(".");
        if (idx > 0) urlString = urlString.substring(idx+1);

        //php
        
        return plasmaParser.mediaExtContains(urlString);
    }

    private static String replaceRegex(String input, final String regex, final String replacement) {
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
    public static File getCachePath(final yacyURL url) {
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
        String extention = null;
        final int d = path.lastIndexOf(".");
        final int s = path.lastIndexOf("/");
        if ((d >= 0) && (d > s)) {
            extention = path.substring(d);
        } else if (path.endsWith("/ndx")) {
            extention = new String (".html"); // Just a wild guess
        }
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
        final StringBuffer fileName = new StringBuffer();
        fileName.append(protocol).append('/').append(host);
        if (port >= 0) {
            fileName.append('!').append(port);
        }

        // generate cache path
        final File FileFlat = hashFile(fileName, "hash", extention, url.hash());
        return FileFlat;
    }
    
    private static File hashFile(final StringBuffer fileName, final String prefix, final String extention, final String urlhash) {
        final String hexHash = yacySeed.b64Hash2hexHash(urlhash);
        final StringBuffer f = new StringBuffer(fileName.length() + 30);
        f.append(fileName);
        if (prefix != null) f.append('/').append(prefix);
        f.append('/').append(hexHash.substring(0,2)).append('/').append(hexHash.substring(2,4)).append('/').append(hexHash);
        if (extention != null) fileName.append(extention);
        return new File(cachePath, f.toString());
    }
    
    /**
     * This is a helper function that extracts the Hash from the filename
     */
    public static String getHash(final File f) {
        if ((!f.isFile()) || (f.getPath().indexOf("hash") < 0)) return null;
        final String name = f.getName();
        if (name.length() < 18) return null; // not a hash file name
        final String hexHash = name.substring(0,18);
        if (hexHash.indexOf('.') >= 0) return null;
        try {
            final String hash = kelondroBase64Order.enhancedCoder.encode(serverCodings.decodeHex(hexHash));
            if (hash.length() == yacySeedDB.commonHashLength) return hash;
            return null;
        } catch (final Exception e) {
            //log.logWarning("getHash: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * this is the reverse function to getCachePath: it constructs the url as string
     * from a given storage path
     */
    public static yacyURL getURL(final File f) {
//      this.log.logFinest("plasmaHTCache: getURL:  IN: Path=[" + cachePath + "] File=[" + f + "]");
        final String urlHash = getHash(f);
        if (urlHash != null) {
            yacyURL url = null;
            // try the urlPool
            try {
                url = plasmaSwitchboard.getSwitchboard().getURL(urlHash);
            } catch (final Exception e) {
                log.logWarning("getURL(" + urlHash + "): " /*+ e.getMessage()*/, e);
                url = null;
            }
            if (url != null) return url;
            // try responseHeaderDB
            Map<String, String> hdb;
            try {
                hdb = responseHeaderDB.get(urlHash);
            } catch (final IOException e1) {
                hdb = null;
            }
            if (hdb != null) {
                final Object origRequestLine = hdb.get(httpHeader.X_YACY_ORIGINAL_REQUEST_LINE);
                if ((origRequestLine != null)&&(origRequestLine instanceof String)) {
                    int i = ((String)origRequestLine).indexOf(" ");
                    if (i >= 0) {
                        final String s = ((String)origRequestLine).substring(i).trim();
                        i = s.indexOf(" ");
                        try {
                            url = new yacyURL((i<0) ? s : s.substring(0,i), urlHash);
                        } catch (final Exception e) {
                            url = null;
                        }
                    }
                }
            }
            if (url != null) return url;
        }
        // If we can't get the correct URL, it seems to be a treeed file
        final String c = cachePath.toString().replace('\\', '/');
        String path = f.toString().replace('\\', '/');
        int pos;
        if ((pos = path.indexOf("/tree")) >= 0) path = path.substring(0, pos) + path.substring(pos + 5);
        
        if (path.endsWith("ndx")) { path = path.substring(0, path.length() - 3); }
        
        if ((pos = path.lastIndexOf(c)) == 0) {
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
                return new yacyURL(protocol + host + path, null);
            } catch (final Exception e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Returns the content of a cached resource as {@link InputStream}
     * @param url the requested resource
     * @return the resource content as {@link InputStream}. In no data
     * is available or the cached file is not readable, <code>null</code>
     * is returned.
     */
    public static InputStream getResourceContentStream(final yacyURL url) {
        // load the url as resource from the cache
        final File f = getCachePath(url);
        if (f.exists() && f.canRead()) try {
            return new BufferedInputStream(new FileInputStream(f));
        } catch (final IOException e) {
            log.logSevere("Unable to create a BufferedInputStream from file " + f,e);
            return null;
        }
        return null;        
    }
    
    public static long getResourceContentLength(final yacyURL url) {
        // load the url as resource from the cache
        final File f = getCachePath(url);
        if (f.exists() && f.canRead()) {
            return f.length();
        } 
        return 0;           
    }

    public static Entry newEntry(
            final Date initDate, 
            final int depth, 
            final yacyURL url,
            final String name,
            final String responseStatus,
            final IResourceInfo docInfo,            
            final String initiator,
            final CrawlProfile.entry profile
    ) {
        final Entry entry = new Entry(
                initDate, 
                depth, 
                url,
                name,
                responseStatus,
                docInfo,
                initiator, 
                profile
        );
        entry.writeResourceInfo();
        return entry;
    }

    /**
     * @return the responseHeaderDB
     */
    static kelondroMap getResponseHeaderDB() {
        return responseHeaderDB;
    }

    public final static class Entry {

    // the class objects
    private final Date                     initDate;       // the date when the request happened; will be used as a key
    private final int                      depth;          // the depth of prefetching
    private final String                   responseStatus;    
    private final File                     cacheFile;      // the cache file
    private byte[]                   cacheArray;     // or the cache as byte-array
    private final yacyURL                  url;
    private final String                   name;           // the name of the link, read as anchor from an <a>-tag
    private final Date                     lastModified;
    private char                     doctype;
    private final String                   language;
    private final CrawlProfile.entry profile;
    private final String                   initiator;
    
    /**
     * protocolspecific information about the resource 
     */
    private final IResourceInfo            resInfo;

    protected Entry clone() throws CloneNotSupportedException {
        return new Entry(
                this.initDate,
                this.depth,
                this.url,
                this.name,
                this.responseStatus,
                this.resInfo,
                this.initiator,
                this.profile
        );
    }

    @SuppressWarnings("null")
    public Entry(final Date initDate, 
            final int depth, 
            final yacyURL url,
            final String name,
            final String responseStatus,
            final IResourceInfo resourceInfo,            
            final String initiator,
            final CrawlProfile.entry profile
    ) {
        if (resourceInfo == null){
            System.out.println("Content information object is null. " + url);
            System.exit(0);            
        }
        this.resInfo = resourceInfo;
        this.url              = url;
        this.name             = name;
        this.cacheFile        = getCachePath(this.url);
        
        // assigned:
        this.initDate       = initDate;
        this.depth          = depth;
        this.responseStatus = responseStatus;
        this.profile        = profile;
        this.initiator      = (initiator == null) ? null : ((initiator.length() == 0) ? null : initiator);

        // getting the last modified date
        this.lastModified = resourceInfo.getModificationDate();
        
        // getting the doctype
        this.doctype = docType(resourceInfo.getMimeType());
        if (this.doctype == DT_UNKNOWN) this.doctype = docType(url);
        this.language = yacyURL.language(url);

        // to be defined later:
        this.cacheArray     = null;
    }

    public String name() {
        // the anchor name; can be either the text inside the anchor tag or the page description after loading of the page
        return this.name;
    }
    
    public yacyURL url() {
        return this.url;
    }
    
    public String urlHash() {
        return this.url.hash();
    }
    
    public Date lastModified() {
        return this.lastModified;
    }
    
    public String language() {
        return this.language;
    }
    
    public CrawlProfile.entry profile() {
        return this.profile;
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

    public int depth() {
        return this.depth;
    }
    
    public yacyURL referrerURL() {
        return (this.resInfo == null) ? null : this.resInfo.getRefererUrl();
    }

    public File cacheFile() {
        return this.cacheFile;
    }
    
    public void setCacheArray(final byte[] data) {
        this.cacheArray = data;
    }
    
    public byte[] cacheArray() {
        return this.cacheArray;
    }
    
    public IResourceInfo getDocumentInfo() {
        return this.resInfo;
    }
    
    boolean writeResourceInfo() {
        if (this.resInfo == null) return false;
        try {
            final HashMap<String, String> hm = new HashMap<String, String>();
            hm.putAll(this.resInfo.getMap());
            hm.put("@@URL", this.url.toNormalform(false, false));
            hm.put("@@DEPTH", Integer.toString(this.depth));
            if (this.initiator != null) hm.put("@@INITIATOR", this.initiator);
            getResponseHeaderDB().put(this.url.hash(), hm);
        } catch (final Exception e) {
            resetResponseHeaderDB();
            return false;
        }
        return true;
    }    
    
    public String getMimeType() {
        return (this.resInfo == null) ? null : this.resInfo.getMimeType();
    }
    
    public Date ifModifiedSince() {
        return (this.resInfo == null) ? null : this.resInfo.ifModifiedSince();
    }
    
    public boolean requestWithCookie() {
        return (this.resInfo == null) ? false : this.resInfo.requestWithCookie();
    }
    
    public boolean requestProhibitsIndexing() {
        return (this.resInfo == null) ? false : this.resInfo.requestProhibitsIndexing();
    }
    
    /*
    public boolean update() {
        return ((status == CACHE_FILL) || (status == CACHE_STALE_RELOAD_GOOD));
    }
    */

    // the following three methods for cache read/write granting shall be as loose as possible
    // but also as strict as necessary to enable caching of most items

    /**
     * @return NULL if the answer is TRUE, in case of FALSE, the reason as String is returned
     */
    public String shallStoreCacheForProxy() {

        // check profile (disabled: we will check this in the plasmaSwitchboard)
        //if (!this.profile.storeHTCache()) { return "storage_not_wanted"; }

        // decide upon header information if a specific file should be stored to the cache or not
        // if the storage was requested by prefetching, the request map is null

        // check status code
        if ((this.resInfo != null) && (!this.resInfo.validResponseStatus(this.responseStatus))) {
            return "bad_status_" + this.responseStatus.substring(0,3);
        }
        
        // check storage location
        // sometimes a file name is equal to a path name in the same directory;
        // or sometimes a file name is equal a directory name created earlier;
        // we cannot match that here in the cache file path and therefore omit writing into the cache
        if (this.cacheFile.getParentFile().isFile() || this.cacheFile.isDirectory()) { return "path_ambiguous"; }
        if (this.cacheFile.toString().indexOf("..") >= 0) { return "path_dangerous"; }
        if (this.cacheFile.getAbsolutePath().length() > serverSystem.maxPathLength) { return "path too long"; }

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable in caches
        if (this.url.isPOST() && !this.profile.crawlingQ()) { return "dynamic_post"; }
        if (this.url.isCGI()) { return "dynamic_cgi"; }

        if (this.resInfo != null) {
            return this.resInfo.shallStoreCacheForProxy();
        }
        
        return null;
    }

    /**
     * decide upon header information if a specific file should be taken from the cache or not
     * @return whether the file should be taken from the cache
     */
    public boolean shallUseCacheForProxy() {

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable in caches
        if (this.url.isPOST()) { return false; }
        if (this.url.isCGI()) { return false; }
        
        if (this.resInfo != null) {
            return this.resInfo.shallUseCacheForProxy();
        }
        
        return true;
    }

    } // class Entry
}
