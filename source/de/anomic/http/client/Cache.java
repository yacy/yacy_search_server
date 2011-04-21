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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.kelondro.blob.ArrayStack;
import net.yacy.kelondro.blob.Compressor;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;


public final class Cache {
    
    private static final String RESPONSE_HEADER_DB_NAME = "responseHeader.heap";
    private static final String FILE_DB_NAME = "file.array";

    private static Map<byte[], Map<String, String>> responseHeaderDB = null;
    private static Compressor fileDB = null;
    private static ArrayStack fileDBunbuffered = null;
    
    private static long maxCacheSize = Long.MAX_VALUE;
    private static File cachePath = null;
    private static String prefix;
    public static final Log log = new Log("HTCACHE");
    
    public static void init(final File htCachePath, final String peerSalt, final long CacheSizeMax) {
        
        cachePath = htCachePath;
        maxCacheSize = CacheSizeMax;
        prefix = peerSalt;

        // set/make cache path
        if (!htCachePath.exists()) {
            htCachePath.mkdirs();
        }

        // open the response header database
        final File dbfile = new File(cachePath, RESPONSE_HEADER_DB_NAME);
        try {
            responseHeaderDB = new MapHeap(dbfile, Word.commonHashLength, Base64Order.enhancedCoder, 1024 * 1024, 100, ' ');
        } catch (final IOException e) {
            Log.logException(e);
        }
        try {
            fileDBunbuffered = new ArrayStack(new File(cachePath, FILE_DB_NAME), prefix, 12, Base64Order.enhancedCoder, 1024 * 1024 * 2, false);
            fileDBunbuffered.setMaxSize(maxCacheSize);
            fileDB = new Compressor(fileDBunbuffered, 2 * 1024 * 1024);
        } catch (IOException e) {
            Log.logException(e);
        }
    }
    
    /**
     * clear the cache
     */
    public static void clear() {
        responseHeaderDB.clear();
        try {
            fileDB.clear();
        } catch (IOException e) {
            Log.logException(e);
        }
        try {
            fileDBunbuffered.clear();
        } catch (IOException e) {
            Log.logException(e);
        }
    }

    /**
     * This method changes the HTCache size.<br>
     * @param the new cache size in bytes
     */
    public static void setMaxCacheSize(final long newCacheSize) {
        maxCacheSize = newCacheSize;
        fileDBunbuffered.setMaxSize(maxCacheSize);
    }
    
    /**
     * get the current actual cache size
     * @return
     */
    public static long getActualCacheSize() {
        return fileDBunbuffered.length();
    }
    
    /**
     * close the databases
     */
    public static void close() {
        if (responseHeaderDB instanceof MapHeap) {
            ((MapHeap) responseHeaderDB).close();
        }
        fileDB.close(true);
    }
    
    public static void store(final DigestURI url, final ResponseHeader responseHeader, final byte[] file) throws IOException {
        if (responseHeader == null) throw new IOException("Cache.store of url " + url.toString() + " not possible: responseHeader == null");
        if (file == null) throw new IOException("Cache.store of url " + url.toString() + " not possible: file == null");
        log.logInfo("storing content of url " + url.toString() + ", " + file.length + " bytes");

        // store the file
        try {
            fileDB.insert(url.hash(), file);
        } catch (UnsupportedEncodingException e) {
            throw new IOException("Cache.store: cannot write to fileDB (1): " + e.getMessage());
        } catch (IOException e) {
            throw new IOException("Cache.store: cannot write to fileDB (2): " + e.getMessage());
        }
        
        // store the response header into the header database
        final HashMap<String, String> hm = new HashMap<String, String>();
        hm.putAll(responseHeader);
        hm.put("@@URL", url.toNormalform(true, false));
        try {
            if (responseHeaderDB instanceof MapHeap) {
                ((MapHeap) responseHeaderDB).insert(url.hash(), hm);
            } else {
                responseHeaderDB.put(url.hash(), hm);
            }
        } catch (Exception e) {
            throw new IOException("Cache.store: cannot write to headerDB: " + e.getMessage());
        }
        if (log.isFine()) log.logFine("stored in cache: " + url.toNormalform(true, false));
    }
    
    /**
     * check if the responseHeaderDB and the fileDB has an entry for the given url
     * @param url the url of the resource
     * @return true if the content of the url is in the cache, false otherwise
     */
    public static boolean has(final DigestURI url) {
        boolean headerExists;
        boolean fileExists;
        //synchronized (responseHeaderDB) {
            headerExists = responseHeaderDB.containsKey(url.hash());
            fileExists = fileDB.containsKey(url.hash());
        //}
        if (headerExists && fileExists) return true;
        if (!headerExists && !fileExists) return false;
        // if not both is there then we do a clean-up
        if (headerExists) try {
            log.logWarning("header but not content of url " + url.toString() + " in cache; cleaned up");
            if (responseHeaderDB instanceof MapHeap) {
                ((MapHeap) responseHeaderDB).delete(url.hash());
            } else {
                responseHeaderDB.remove(url.hash());
            }
        } catch (IOException e) {}
        if (fileExists) try {
            log.logWarning("content but not header of url " + url.toString() + " in cache; cleaned up");
            fileDB.delete(url.hash());
        } catch (IOException e) {}
        return false;
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
    public static ResponseHeader getResponseHeader(final byte[] hash) {    
        
        // loading data from database
        Map<String, String> hdb;
        hdb = responseHeaderDB.get(hash);
        if (hdb == null) return null;
        
        return new ResponseHeader(null, hdb);
    }
    
    
    /**
     * Returns the content of a cached resource as byte[]
     * @param url the requested resource
     * @return the resource content as byte[]. If no data
     * is available or the cached file is not readable, <code>null</code>
     * is returned.
     * @throws IOException 
     */
    public static byte[] getContent(final byte[] hash) {
        // load the url as resource from the cache
        try {
            byte[] b = fileDB.get(hash);
            if (b == null) return null;
            return b;
        } catch (UnsupportedEncodingException e) {
            Log.logException(e);
            return null;
        } catch (IOException e) {
            Log.logException(e);
            return null;
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            return null;
        } catch (OutOfMemoryError e) {
            Log.logException(e);
            return null;
        }
    }
    
    /**
     * removed response header and cached content from the database
     * @param url
     * @throws IOException
     */
    public static void delete(final DigestURI url) throws IOException {
        if (responseHeaderDB instanceof MapHeap) {
            ((MapHeap) responseHeaderDB).delete(url.hash());
        } else {
            responseHeaderDB.remove(url.hash());
        }
        fileDB.delete(url.hash());
    }
}
