// httpCache.java
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

package de.anomic.http.client;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import de.anomic.crawler.retrieval.Response;
import de.anomic.document.Classification;
import de.anomic.http.metadata.ResponseHeader;
import de.anomic.kelondro.blob.ArrayStack;
import de.anomic.kelondro.blob.Compressor;
import de.anomic.kelondro.blob.Heap;
import de.anomic.kelondro.blob.MapView;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public final class Cache {
    
    private static final String RESPONSE_HEADER_DB_NAME = "responseHeader.heap";
    private static final String FILE_DB_NAME = "file.array";

    private static MapView responseHeaderDB = null;
    private static Compressor fileDB = null;
    private static ArrayStack fileDBunbuffered = null;
    
    private static long maxCacheSize = 0l;
    private static File cachePath = null;
    private static String prefix;
    private static final Log log = new Log("HTCACHE");
    
    public static void init(final File htCachePath, String peerSalt, final long CacheSizeMax) {
        
        cachePath = htCachePath;
        maxCacheSize = CacheSizeMax;
        prefix = peerSalt;

        // set/make cache path
        if (!htCachePath.exists()) {
            htCachePath.mkdirs();
        }

        // open the response header database
        final File dbfile = new File(cachePath, RESPONSE_HEADER_DB_NAME);
        Heap blob = null;
        try {
            blob = new Heap(dbfile, yacySeedDB.commonHashLength, Base64Order.enhancedCoder, 1024 * 1024);
        } catch (final IOException e) {
            e.printStackTrace();
        }
        responseHeaderDB = new MapView(blob, 500, '_');
        try {
            fileDBunbuffered = new ArrayStack(new File(cachePath, FILE_DB_NAME), prefix, 12, Base64Order.enhancedCoder, 1024 * 1024 * 2);
            fileDBunbuffered.setMaxSize(maxCacheSize);
            fileDB = new Compressor(fileDBunbuffered, 2 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method changes the HTCache size.<br>
     * @param the new cache size in bytes
     */
    public static void setCacheSize(final long newCacheSize) {
        maxCacheSize = newCacheSize;
        fileDBunbuffered.setMaxSize(maxCacheSize);
    }
    
    public static void close() {
        responseHeaderDB.close();
        fileDB.close(true);
    }

    public static boolean isPicture(final String mimeType) {
        if (mimeType == null) return false;
        return mimeType.toUpperCase().startsWith("IMAGE");
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
        
        return Classification.isMediaExtension(urlString);
    }


    // Store to Cache
    public static void storeMetadata(
            final ResponseHeader responseHeader,
            Response metadata
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
        }
    }

    
    public static void storeFile(yacyURL url, byte[] file) {
        try {
            fileDB.put(url.hash().getBytes("UTF-8"), file);
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
    public static ResponseHeader loadResponseHeader(final yacyURL url) {    
        
        // loading data from database
        Map<String, String> hdb;
        try {
            hdb = responseHeaderDB.get(url.hash());
        } catch (final IOException e) {
            return null;
        }
        if (hdb == null) return null;
        
        return new ResponseHeader(null, hdb);
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
            return fileDB.get(url.hash().getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static long getResourceContentLength(final yacyURL url) {
        // load the url as resource from the cache
        try {
            return fileDB.length(url.hash().getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static void deleteFromCache(yacyURL url) throws IOException {
        responseHeaderDB.remove(url.hash());
        fileDB.remove(url.hash().getBytes("UTF-8"));
    }
}
