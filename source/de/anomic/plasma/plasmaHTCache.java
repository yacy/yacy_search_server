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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.anomic.http.httpResponseHeader;
import de.anomic.index.indexDocumentMetadata;
import de.anomic.kelondro.kelondroBLOB;
import de.anomic.kelondro.kelondroBLOBArray;
import de.anomic.kelondro.kelondroBLOBBuffer;
import de.anomic.kelondro.kelondroBLOBHeap;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroMap;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;

public final class plasmaHTCache {
    
    public static final String RESPONSE_HEADER_DB_NAME = "responseHeader.heap";
    public static final String FILE_DB_NAME = "file.array";
    
    private static final int stackLimit = 150; // if we exceed that limit, we do not check idle
    public  static final long oneday = 1000L * 60L * 60L * 24L; // milliseconds of a day

    private static kelondroMap responseHeaderDB = null;
    private static kelondroBLOBBuffer fileDB = null;
    
    private static final ConcurrentLinkedQueue<indexDocumentMetadata> cacheStack = new ConcurrentLinkedQueue<indexDocumentMetadata>();
    public static long maxCacheSize = 0l;
    public static File cachePath = null;
    public static final serverLog log = new serverLog("HTCACHE");
    

    // URL attributes
    public static final int UA_LOCAL    =  0; // URL was crawled locally
    public static final int UA_TILDE    =  1; // tilde appears in URL
    public static final int UA_REDIRECT =  2; // The URL is a redirection
    
    // local flag attributes
    public static final char LT_LOCAL   = 'L';
    public static final char LT_GLOBAL  = 'G';

    
    public static void init(final File htCachePath, final long CacheSizeMax) {
        
        cachePath = htCachePath;
        maxCacheSize = CacheSizeMax;

        // reset old HTCache ?
        String[] list = cachePath.list();
        if (list != null) {
            File object;
            for (int i = list.length - 1; i >= 0; i--) {
                object = new File(cachePath, list[i]);

                if (object.getName().equals("http") ||
                    object.getName().equals("yacy") ||
                    object.getName().equals("https") ||
                    object.getName().equals("ftp")) {
                    deleteOldHTCache(cachePath);
                }
            }
        }

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
        openDB();
    }

    static void resetDB() {
        log.logFine("reset responseHeader DB with "+ responseHeaderDB.size() +" entries");
        if (responseHeaderDB != null) responseHeaderDB.close();
        final File dbfile = new File(cachePath, RESPONSE_HEADER_DB_NAME);
        if (dbfile.exists()) dbfile.delete();
        openDB();
    }
    
    private static void openDB() {
        // open the response header database
        final File dbfile = new File(cachePath, RESPONSE_HEADER_DB_NAME);
        kelondroBLOB blob = null;
        try {
            blob = new kelondroBLOBHeap(dbfile, yacySeedDB.commonHashLength, kelondroBase64Order.enhancedCoder);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        responseHeaderDB = new kelondroMap(blob, 500);
        try {
            kelondroBLOBArray fileDBunbuffered = new kelondroBLOBArray(new File(cachePath, FILE_DB_NAME), 12, kelondroBase64Order.enhancedCoder, kelondroBLOBArray.oneMonth, kelondroBLOBArray.oneGigabyte);
            fileDB = new kelondroBLOBBuffer(fileDBunbuffered, 1024 * 1024, true);
            fileDB.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public static int responseHeaderDBSize() {
        return responseHeaderDB.size();
    }
    
    public static long fileDBSize() {
        return fileDB.length();
    }
    
    public static void push(final indexDocumentMetadata entry) {
        cacheStack.add(entry);
    }

    public static indexDocumentMetadata pop() {
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
        long l = fileDB.length();
        return (l >= maxCacheSize) ? 0 : maxCacheSize - l;
    }
    
    public static void close() {
        responseHeaderDB.close();
        fileDB.close();
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


    // Store to Cache
    
    public static void storeMetadata(
            final httpResponseHeader responseHeader,
            indexDocumentMetadata metadata
    ) {
        if (responseHeader != null) try {
            // store the response header into the header database
            final HashMap<String, String> hm = new HashMap<String, String>();
            hm.putAll(responseHeader);
            hm.put("@@URL", metadata.url().toNormalform(false, false));
            hm.put("@@DEPTH", Integer.toString(metadata.depth()));
            responseHeaderDB.put(metadata.urlHash(), hm);
        } catch (final Exception e) {
            log.logWarning("could not write ResourceInfo: "
                    + e.getClass() + ": " + e.getMessage());
            resetDB();
        }
    }

    
    public static void storeFile(yacyURL url, byte[] file) {
        try {
            fileDB.put(url.hash().getBytes(), file);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
    public static httpResponseHeader loadResponseHeader(final yacyURL url) throws IllegalAccessException {    
        
        // loading data from database
        Map<String, String> hdb;
        try {
            hdb = responseHeaderDB.get(url.hash());
        } catch (final IOException e) {
            return null;
        }
        if (hdb == null) return null;
        
        return new httpResponseHeader(null, hdb);
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
        byte[] b = getResourceContent(url);
        if (b == null) return null;
        return new ByteArrayInputStream(b);
    }
    
    public static byte[] getResourceContent(final yacyURL url) {
        // load the url as resource from the cache
        try {
            return fileDB.get(url.hash().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static long getResourceContentLength(final yacyURL url) {
        // load the url as resource from the cache
        try {
            return fileDB.length(url.hash().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static void deleteFromCache(yacyURL url) throws IOException {
        responseHeaderDB.remove(url.hash());
        fileDB.remove(url.hash().getBytes());
    }
}
