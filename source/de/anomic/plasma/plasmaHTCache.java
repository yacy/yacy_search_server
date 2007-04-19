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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.StringBuffer;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.anomic.http.httpc;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaURL;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMScoreCluster;
import de.anomic.kelondro.kelondroMapObjects;
import de.anomic.net.URL;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.ResourceInfoFactory;
import de.anomic.server.serverCodings;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverInstantThread;
import de.anomic.server.serverSystem;
import de.anomic.server.serverThread;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.enumerateFiles;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;

public final class plasmaHTCache {

    private static final int stackLimit = 150; // if we exceed that limit, we do not check idle
    public  static final long oneday = 1000 * 60 * 60 * 24; // milliseconds of a day

    kelondroMapObjects responseHeaderDB = null;
    private final LinkedList cacheStack;
    private final Map cacheAge; // a <date+hash, cache-path> - relation
    public long curCacheSize;
    public long maxCacheSize;
    public final File cachePath;
    public final serverLog log;
    public static final HashSet filesInUse = new HashSet(); // can we delete this file
    public String cacheLayout;
    public boolean cacheMigration;

    private ResourceInfoFactory objFactory;
    private serverThread cacheScanThread;

    public plasmaHTCache(File htCachePath, long maxCacheSize, long preloadTime, String cacheLayout, boolean cacheMigration) {
        // this.switchboard = switchboard;

        this.log = new serverLog("HTCACHE");
        this.cachePath = htCachePath;
        this.cacheLayout = cacheLayout;
        this.cacheMigration = cacheMigration;
        
        // create the object factory
        this.objFactory = new ResourceInfoFactory();

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
        openResponseHeaderDB(preloadTime);

        // init stack
        this.cacheStack = new LinkedList();

        // init cache age and size management
        this.cacheAge = Collections.synchronizedMap(new TreeMap());
        this.curCacheSize = 0;
        this.maxCacheSize = maxCacheSize;

        // start the cache startup thread
        // this will collect information about the current cache size and elements
        this.cacheScanThread = serverInstantThread.oneTimeJob(this, "cacheScan", this.log, 120000);
    }

    private void resetResponseHeaderDB() {
        if (this.responseHeaderDB != null) this.responseHeaderDB.close();
        File dbfile = new File(this.cachePath, "responseHeader.db");
        if (dbfile.exists()) dbfile.delete();
        openResponseHeaderDB(0);
    }
    
    private void openResponseHeaderDB(long preloadTime) {
        // open the response header database
        File dbfile = new File(this.cachePath, "responseHeader.db");
        this.responseHeaderDB = new kelondroMapObjects(new kelondroDyn(dbfile, true, true, preloadTime, yacySeedDB.commonHashLength, 150, '#', true, false, true), 500);
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

    /**
     * This method changes the HTCache size.<br>
     * @param the new cache size in bytes
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

    public boolean writeResourceContent(URL url, byte[] array) {
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

    private long lastcleanup = System.currentTimeMillis();
    public void writeFileAnnouncement(File file) {
        synchronized (this.cacheAge) {
            if (file.exists()) {
                this.curCacheSize += file.length();
                if (System.currentTimeMillis() - lastcleanup > 300000) {
                    // call the cleanup job only every 5 minutes
                    cleanup();
                    lastcleanup = System.currentTimeMillis();
                }
                this.cacheAge.put(ageString(file.lastModified(), file), file);
            }
        }
    }

    public boolean deleteFile(URL url) {
        return deleteURLfromCache("", url, "FROM");
    }

    private boolean deleteURLfromCache(String key, URL url, String msg) {
        if (deleteFileandDirs(key, getCachePath(url), msg)) {
            try {
                // As the file is gone, the entry in responseHeader.db is not needed anymore
                this.log.logFinest("Trying to remove responseHeader from URL: " + url.toString());
                this.responseHeaderDB.remove(plasmaURL.urlHash(url));
            } catch (IOException e) {
                resetResponseHeaderDB();
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

    private boolean deleteFileandDirs(String key, File obj, String msg) {
        if (deleteFile(obj)) {
            this.log.logInfo("DELETED " + msg + " CACHE [" + key + "]: " + obj.toString());
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
        File file;
        synchronized (cacheAge) {
            Iterator iter = this.cacheAge.entrySet().iterator();
            Map.Entry entry;
            while (iter.hasNext() && this.curCacheSize >= newCacheSize) {
                if (Thread.currentThread().isInterrupted()) return;
                entry = (Map.Entry) iter.next();
                String key = (String) entry.getKey();
                file = (File) entry.getValue();
                long t = Long.parseLong(key.substring(0, 16), 16);
                if (System.currentTimeMillis() - t < 300000) break; // files must have been at least 5 minutes in the cache before they are deleted
                if (file != null) {
                    if (filesInUse.contains(file)) continue;
                    this.log.logFinest("Trying to delete [" + key + "] = old file: " + file.toString());
                    if (deleteFileandDirs(key, file, "OLD")) {
                        try {
                            // As the file is gone, the entry in responseHeader.db is not needed anymore
                            String urlHash = getHash(file);
                            if (urlHash != null) {
                                this.log.logFinest("Trying to remove responseHeader for URLhash: " + urlHash);
                                this.responseHeaderDB.remove(urlHash);
                            } else {
                                URL url = getURL(file);
                                if (url != null) {
                                    this.log.logFinest("Trying to remove responseHeader for URL: " + url.toString());
                                    this.responseHeaderDB.remove(plasmaURL.urlHash(url));
                                }
                            }
                        } catch (IOException e) {
                            this.log.logInfo("IOExeption removing response header from DB: " + e.getMessage(), e);
                        }
                    }
                }
                iter.remove();
            }
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
        // closing cache scan if still running
        if ((this.cacheScanThread != null) && (this.cacheScanThread.isAlive())) {
            this.cacheScanThread.terminate(true);
        }
        
        // closing DB
        this.responseHeaderDB.close();
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
        int fileCount = 0;
        enumerateFiles fileEnum = new enumerateFiles(this.cachePath, true, false, true, true);
        File dbfile = new File(this.cachePath, "responseHeader.db");
        while (fileEnum.hasMoreElements()) {
            if (Thread.currentThread().isInterrupted()) return;
            fileCount++;
            File nextFile = (File) fileEnum.nextElement();
            long nextFileModDate = nextFile.lastModified();
            //System.out.println("Cache: " + dom(f));
            doms.incScore(dom(nextFile));
            this.curCacheSize += nextFile.length();
            if (!dbfile.equals(nextFile)) this.cacheAge.put(ageString(nextFileModDate, nextFile), nextFile);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                return;
            }
        }
        //System.out.println("%" + (String) cacheAge.firstKey() + "=" + cacheAge.get(cacheAge.firstKey()));
        long ageHours = 0;
        if (!this.cacheAge.isEmpty()) {
            Iterator i = this.cacheAge.keySet().iterator();
            if (i.hasNext()) try {
                ageHours = (System.currentTimeMillis() - Long.parseLong(((String) i.next()).substring(0, 16), 16)) / 3600000;
            } catch (NumberFormatException e) {
                ageHours = 0;
            } else {
                ageHours = 0;
            }
        }
        this.log.logConfig("CACHE SCANNED, CONTAINS " + fileCount +
                      " FILES = " + this.curCacheSize/1048576 + "MB, OLDEST IS " + 
            ((ageHours < 24) ? (ageHours + " HOURS") : ((ageHours / 24) + " DAYS")) + " OLD");
        cleanup();

        log.logConfig("STARTING DNS PREFETCH");
        // start to prefetch IPs from DNS
        String dom;
        long start = System.currentTimeMillis();
        String result = "";
        fileCount = 0;
        while ((doms.size() > 0) && (fileCount < 50) && ((System.currentTimeMillis() - start) < 60000)) {
            if (Thread.currentThread().isInterrupted()) return;
            dom = (String) doms.getMaxObject();
            InetAddress ip = httpc.dnsResolve(dom);
            if (ip == null) continue;
            result += ", " + dom + "=" + ip.getHostAddress();
            this.log.logConfig("PRE-FILLED " + dom + "=" + ip.getHostAddress());
            fileCount++;
            doms.deleteScore(dom);
            // wait a short while to prevent that this looks like a DoS
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
        if (result.length() > 2) this.log.logConfig("PRE-FILLED DNS CACHE, FETCHED " + fileCount +
                                               " ADDRESSES: " + result.substring(2));
    }

    private String dom(File f) {
        String s = f.toString().substring(this.cachePath.toString().length() + 1);
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
        int e = s.indexOf("!");
        if ((e > 0) && (e < p)) p = e; // strip port
        return prefix + s.substring(0, p);
    }

    /**
     * Returns an object containing metadata about a cached resource
     * @param url the url of the resource
     * @return an {@link IResourceInfo info object}  
     * @throws Exception of the info object could not be created, e.g. if the protocol is not supported
     */
    public IResourceInfo loadResourceInfo(URL url) throws Exception {    
        
        // getting the URL hash
        String urlHash = plasmaURL.urlHash(url.toNormalform());
        
        // loading data from database
        Map hdb = this.responseHeaderDB.getMap(urlHash);
        if (hdb == null) return null;
        
        // generate the cached object
        IResourceInfo cachedObj = this.objFactory.buildResourceInfoObj(url, hdb);
        return cachedObj;
    }
    
    public ResourceInfoFactory getResourceInfoFactory() {
        return this.objFactory;
    }

    public boolean full() {
        return (this.cacheStack.size() > stackLimit);
    }

    public boolean empty() {
        return (this.cacheStack.size() == 0);
    }

    public static boolean isPicture(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.toUpperCase().startsWith("IMAGE");
    }

    public static boolean isText(String mimeType) {
//      Object ct = response.get(httpHeader.CONTENT_TYPE);
//      if (ct == null) return false;
//      String t = ((String)ct).toLowerCase();
//      return ((t.startsWith("text")) || (t.equals("application/xhtml+xml")));
        return plasmaParser.supportedMimeTypesContains(mimeType);
    }

    public static boolean noIndexingURL(String urlString) {
        if (urlString == null) return false;
        urlString = urlString.toLowerCase();
        
        //http://www.yacy.net/getimage.php?image.png
        
        int idx = urlString.indexOf("?");
        if (idx > 0) urlString = urlString.substring(0,idx);

        //http://www.yacy.net/getimage.php
        
        idx = urlString.lastIndexOf(".");
        if (idx > 0) urlString = urlString.substring(idx+1);

        //php
        
        return plasmaParser.mediaExtContains(urlString);
    }

    /**
     * This function moves an old cached object (if it exists) to the new position
     */
    private void moveCachedObject(File oldpath, File newpath) {
        try {
            if (oldpath.exists() && oldpath.isFile() && (!newpath.exists())) {
                long d = oldpath.lastModified();
                newpath.getParentFile().mkdirs();
                if (oldpath.renameTo(newpath)) {
                    cacheAge.put(ageString(d, newpath), newpath);
                    File obj = oldpath.getParentFile();
                    while ((!(obj.equals(this.cachePath))) && (obj.isDirectory()) && (obj.list().length == 0)) {
                        if (obj.delete()) this.log.logFine("DELETED EMPTY DIRECTORY : " + obj.toString());
                        obj = obj.getParentFile();
                    }
                }
            }
        } catch (Exception e) {
            log.logFine("moveCachedObject('" + oldpath.toString() + "','" +
                        newpath.toString() + "')", e);
        }
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
        String extention = null;
        int d = path.lastIndexOf(".");
        int s = path.lastIndexOf("/");
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
        StringBuffer fileName = new StringBuffer();
        fileName.append(protocol).append('/').append(host);
        if (port >= 0) {
            fileName.append('!').append(port);
        }

        // generate cache path according to storage method
        if (cacheLayout.equals("tree")) {
            File FileTree = treeFile(fileName, "tree", path);
            if (cacheMigration) {
                moveCachedObject(hashFile(fileName, "hash", extention, url), FileTree);
                moveCachedObject(hashFile(fileName, null, extention, url), FileTree); // temporary migration
                moveCachedObject(treeFile(fileName, null, path), FileTree);           // temporary migration
            }
            return FileTree;
        }
        if (cacheLayout.equals("hash")) {
            File FileFlat = hashFile(fileName, "hash", extention, url);
            if (cacheMigration) {
                moveCachedObject(treeFile(fileName, "tree", path), FileFlat);
                moveCachedObject(treeFile(fileName, null, path), FileFlat);           // temporary migration
                moveCachedObject(hashFile(fileName, null, extention, url), FileFlat); // temporary migration
            }
            return FileFlat;
        }
        return null;
    }

    private File treeFile(StringBuffer fileName, String prefix, String path) {
        StringBuffer f = new StringBuffer(fileName.length() + 30);
        f.append(fileName);
        if (prefix != null) f.append('/').append(prefix);
        f.append(path);
        return new File(this.cachePath, f.toString());
    }
    
    private File hashFile(StringBuffer fileName, String prefix, String extention, URL url) {
        String hexHash = yacySeed.b64Hash2hexHash(plasmaURL.urlHash(url));
        StringBuffer f = new StringBuffer(fileName.length() + 30);
        f.append(fileName);
        if (prefix != null) f.append('/').append(prefix);
        f.append('/').append(hexHash.substring(0,2)).append('/').append(hexHash.substring(2,4)).append('/').append(hexHash);
        if (extention != null) fileName.append(extention);
        return new File(this.cachePath, f.toString());
    }
    
    
    /**
     * This is a helper funktion that extracts the Hash from the filename
     */
    public static String getHash(final File f) {
        if ((!f.isFile()) || (f.getPath().indexOf("hash") < 0)) return null;
        String hexHash = f.getName().substring(0,18);
        if (hexHash.indexOf('.') >= 0) return null;
        try {
            String hash = kelondroBase64Order.enhancedCoder.encode(serverCodings.decodeHex(hexHash));
            if (hash.length() == yacySeedDB.commonHashLength) return hash;
            return null;
        } catch (Exception e) {
            //log.logWarning("getHash: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * this is the reverse function to getCachePath: it constructs the url as string
     * from a given storage path
     */
    public URL getURL(final File f) {
//      this.log.logFinest("plasmaHTCache: getURL:  IN: Path=[" + cachePath + "] File=[" + f + "]");
        final String urlHash = getHash(f);
        if (urlHash != null) {
            URL url = null;
            // try the urlPool
            try {
                url = plasmaSwitchboard.getSwitchboard().getURL(urlHash);
            } catch (Exception e) {
                log.logWarning("getURL(" + urlHash + "): " /*+ e.getMessage()*/, e);
                url = null;
            }
            if (url != null) return url;
            // try responseHeaderDB
            Map hdb;
            hdb = this.responseHeaderDB.getMap(urlHash);
            if (hdb != null) {
                Object origRequestLine = hdb.get(httpHeader.X_YACY_ORIGINAL_REQUEST_LINE);
                if ((origRequestLine != null)&&(origRequestLine instanceof String)) {
                    int i = ((String)origRequestLine).indexOf(" ");
                    if (i >= 0) {
                        String s = ((String)origRequestLine).substring(i).trim();
                        i = s.indexOf(" ");
                        try {
                            url = new URL((i<0) ? s : s.substring(0,i));
                        } catch (final Exception e) {
                            url = null;
                        }
                    }
                }
            }
            if (url != null) return url;
        }
        // If we can't get the correct URL, it seems to be a treeed file
        String c = cachePath.toString().replace('\\', '/');
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
                return new URL(protocol + host + path);
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
    public InputStream getResourceContentStream(URL url) {
        // load the url as resource from the cache
        File f = getCachePath(url);
        if (f.exists() && f.canRead()) try {
            return new BufferedInputStream(new FileInputStream(f));
        } catch (IOException e) {
            this.log.logSevere("Unable to create a BufferedInputStream from file " + f,e);
            return null;
        }
        return null;        
    }
    
    public long getResourceContentLength(URL url) {
        // load the url as resource from the cache
        File f = getCachePath(url);
        if (f.exists() && f.canRead()) {
            return f.length();
        } 
        return 0;           
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

    public Entry newEntry(
            Date initDate, 
            int depth, 
            URL url, 
            String name,
            //httpHeader requestHeader,
            String responseStatus, 
            //httpHeader responseHeader,
            IResourceInfo docInfo,            
            String initiator,
            plasmaCrawlProfile.entry profile
    ) {
        return new Entry(
                initDate, 
                depth, 
                url, 
                name, 
                //requestHeader, 
                responseStatus, 
                //responseHeader,
                docInfo,
                initiator, 
                profile
        );
    }

    public final class Entry {

    // the class objects
    private Date                     initDate;       // the date when the request happened; will be used as a key
    private int                      depth;          // the depth of prefetching
//    private httpHeader               requestHeader;  // we carry also the header to prevent too many file system access
//    private httpHeader               responseHeader; // we carry also the header to prevent too many file system access
    private String                   responseStatus;    
    private File                     cacheFile;      // the cache file
    private byte[]                   cacheArray;     // or the cache as byte-array
    private URL                      url;
    private String                   name;           // the name of the link, read as anchor from an <a>-tag
    private String                   nomalizedURLHash;
    private String                   nomalizedURLString;
    //private int                      status;         // cache load/hit/stale etc status
    private Date                     lastModified;
    private char                     doctype;
    private String                   language;
    private plasmaCrawlProfile.entry profile;
    private String                   initiator;
    
    /**
     * protocolspecific information about the resource 
     */
    private IResourceInfo              resInfo;

    protected Object clone() throws CloneNotSupportedException {
        return new Entry(
                this.initDate,
                this.depth,
                this.url,
                this.name,
                //this.requestHeader,
                this.responseStatus,
                //this.responseHeader,
                this.resInfo,
                this.initiator,
                this.profile
        );
    }

    public Entry(Date initDate, 
            int depth, 
            URL url, 
            String name,
            //httpHeader requestHeader,
            String responseStatus,
            //httpHeader responseHeader,
            IResourceInfo resourceInfo,            
            String initiator,
            plasmaCrawlProfile.entry profile
    ) {
        if (resourceInfo == null){
            System.out.println("Content information object is null. " + url);
            System.exit(0);            
        }
        this.resInfo = resourceInfo;
        
        
        // normalize url
        this.nomalizedURLString = url.toNormalform();

        try {
            this.url            = new URL(this.nomalizedURLString);
        } catch (MalformedURLException e) {
            System.out.println("internal error at httpdProxyCache.Entry: " + e);
            System.exit(-1);
        }
        this.name             = name;
        this.cacheFile        = getCachePath(this.url);
        this.nomalizedURLHash = plasmaURL.urlHash(this.nomalizedURLString);

       // assigned:
        this.initDate       = initDate;
        this.depth          = depth;
        //this.requestHeader  = requestHeader;
        this.responseStatus = responseStatus;
        //this.responseHeader = responseHeader;
        this.profile        = profile;
        this.initiator      = (initiator == null) ? null : ((initiator.length() == 0) ? null : initiator);

        // getting the last modified date
        this.lastModified = resourceInfo.getModificationDate();
        
        // getting the doctype
        this.doctype = plasmaURL.docType(resourceInfo.getMimeType());
        if (this.doctype == plasmaURL.DT_UNKNOWN) this.doctype = plasmaURL.docType(url);
        this.language = plasmaURL.language(url);

        // to be defined later:
        this.cacheArray     = null;
    }

    public String name() {
        return this.name;
    }
    
    public URL url() {
        return this.url;
    }
    
    public String urlHash() {
        return this.nomalizedURLHash;
    }
    
    public Date lastModified() {
        return this.lastModified;
    }
    
    public String language() {
        return this.language;
    }
    
    public plasmaCrawlProfile.entry profile() {
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
    
    public URL referrerURL() {
        return (this.resInfo==null)?null:this.resInfo.getRefererUrl();
    }

    public File cacheFile() {
        return this.cacheFile;
    }
    
    public void setCacheArray(byte[] data) {
        this.cacheArray = data;
    }
    
    public byte[] cacheArray() {
        return this.cacheArray;
    }
    
//    public httpHeader requestHeader() {
//        return this.requestHeader;
//    }
    
//    public httpHeader responseHeader() {
//        return this.responseHeader;        
//    }
    
    public IResourceInfo getDocumentInfo() {
        return this.resInfo;
    }
    
    public boolean writeResourceInfo() throws IOException {
        assert(this.nomalizedURLHash != null) : "URL Hash is null";
        if (this.resInfo == null) return false;
        try {
            plasmaHTCache.this.responseHeaderDB.set(this.nomalizedURLHash, this.resInfo.getMap());
        } catch (Exception e) {
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
//        if (!(this.responseStatus.startsWith("200") ||
//              this.responseStatus.startsWith("203"))) { return "bad_status_" + this.responseStatus.substring(0,3); }

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
//      System.out.println("SHALL READ CACHE: requestHeader = " + requestHeader.toString() + ", responseHeader = " + responseHeader.toString());

        // -CGI access in request
        // CGI access makes the page very individual, and therefore not usable in caches
        if (isPOST(this.nomalizedURLString)) { return false; }
        if (isCGI(this.nomalizedURLString)) { return false; }
        
        if (this.resInfo != null) {
            return this.resInfo.shallUseCacheForProxy();
        }
        
        return true;
    }

    } // class Entry
}
