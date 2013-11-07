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

package net.yacy.crawler.data;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.ArrayStack;
import net.yacy.kelondro.blob.Compressor;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleSet;


public final class Cache {

    private static final String RESPONSE_HEADER_DB_NAME = "responseHeader.heap";
    private static final String FILE_DB_NAME = "file.array";

    private static MapHeap responseHeaderDB = null;
    private static Compressor fileDB = null;
    private static ArrayStack fileDBunbuffered = null;

    private static long maxCacheSize = Long.MAX_VALUE;
    private static File cachePath = null;
    private static String prefix;
    public static final ConcurrentLog log = new ConcurrentLog("HTCACHE");

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
            responseHeaderDB = new MapHeap(dbfile, Word.commonHashLength, Base64Order.enhancedCoder, 2048, 100, ' ');
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        // open the cache file
        try {
            fileDBunbuffered = new ArrayStack(new File(cachePath, FILE_DB_NAME), prefix, Base64Order.enhancedCoder, 12, 1024 * 1024 * 2, false, true);
            fileDBunbuffered.setMaxSize(maxCacheSize);
            fileDB = new Compressor(fileDBunbuffered, 6 * 1024 * 1024);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        ConcurrentLog.info("Cache", "initialized cache database responseHeaderDB.size() = " + responseHeaderDB.size() + ", fileDB.size() = " + fileDB.size());

        // clean up the responseHeaderDB which cannot be cleaned the same way as the cache files.
        // We do this as a concurrent job only once after start-up silently
        if (responseHeaderDB.size() != fileDB.size()) {
            ConcurrentLog.warn("Cache", "file and metadata size is not equal, starting a cleanup thread...");
            Thread startupCleanup = new Thread() {
                @Override
                public void run() {
                    Thread.currentThread().setName("Cache startupCleanup");
                    // enumerate the responseHeaderDB and find out all entries that are not inside the fileDBunbuffered
                    BlockingQueue<byte[]> q = responseHeaderDB.keyQueue(1000);
                    final HandleSet delkeys = new RowHandleSet(Word.commonHashLength, Base64Order.enhancedCoder, 1);
                    ConcurrentLog.info("Cache", "started cleanup thread to remove unused cache metadata");
                    try {
                        byte[] k;
                        while (((k = q.take()) != MapHeap.POISON_QUEUE_ENTRY)) {
                            if (!fileDB.containsKey(k)) try { delkeys.put(k); } catch (final SpaceExceededException e) { break; }
                        }
                    } catch (final InterruptedException e) {
                    } finally {
                        // delete the collected keys from the metadata
                        ConcurrentLog.info("Cache", "cleanup thread collected " + delkeys.size() + " unused metadata entries; now deleting them from the file...");
                        for (byte[] k: delkeys) {
                            try {
                                responseHeaderDB.delete(k);
                            } catch (final IOException e) {
                            }
                        }
                    }

                    ConcurrentLog.info("Cache", "running check to remove unused file cache data");
                    delkeys.clear();
                    for (byte[] k: fileDB) {
                        if (!responseHeaderDB.containsKey(k)) try { delkeys.put(k); } catch (final SpaceExceededException e) { break; }
                    }
                    ConcurrentLog.info("Cache", "cleanup thread collected " + delkeys.size() + " unused cache entries; now deleting them from the file...");
                    for (byte[] k: delkeys) {
                        try {
                            fileDB.delete(k);
                        } catch (final IOException e) {
                        }
                    }
                    ConcurrentLog.info("Cache", "terminated cleanup thread; responseHeaderDB.size() = " + responseHeaderDB.size() + ", fileDB.size() = " + fileDB.size());
                }
            };
            startupCleanup.start();
        }
    }

    public static void commit() {
    	fileDB.flushAll();
    }

    /**
     * clear the cache
     */
    public static void clear() {
        responseHeaderDB.clear();
        try {
            fileDB.clear();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        try {
            fileDBunbuffered.clear();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
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
     * get the current actual cache size
     * @return
     */
    public static long getActualCacheDocCount() {
        return fileDBunbuffered.size();
    }

    /**
     * close the databases
     */
    public static void close() {
        responseHeaderDB.close();
        fileDB.close(true);
    }

    public static void store(final DigestURL url, final ResponseHeader responseHeader, final byte[] file) throws IOException {
        if (maxCacheSize == 0) return;
        if (responseHeader == null) throw new IOException("Cache.store of url " + url.toString() + " not possible: responseHeader == null");
        if (file == null) throw new IOException("Cache.store of url " + url.toString() + " not possible: file == null");
        log.info("storing content of url " + url.toString() + ", " + file.length + " bytes");

        // store the file
        try {
            fileDB.insert(url.hash(), file);
        } catch (final UnsupportedEncodingException e) {
            throw new IOException("Cache.store: cannot write to fileDB (1): " + e.getMessage());
        } catch (final IOException e) {
            throw new IOException("Cache.store: cannot write to fileDB (2): " + e.getMessage());
        }

        // store the response header into the header database
        final HashMap<String, String> hm = new HashMap<String, String>();
        hm.putAll(responseHeader);
        hm.put("@@URL", url.toNormalform(true));
        try {
            responseHeaderDB.insert(url.hash(), hm);
        } catch (final Exception e) {
            fileDB.delete(url.hash());
            throw new IOException("Cache.store: cannot write to headerDB: " + e.getMessage());
        }
        if (log.isFine()) log.fine("stored in cache: " + url.toNormalform(true));
    }

    /**
     * check if the responseHeaderDB and the fileDB has an entry for the given url
     * @param url the url of the resource
     * @return true if the content of the url is in the cache, false otherwise
     */
    public static boolean has(final byte[] urlhash) {
        boolean headerExists;
        boolean fileExists;
        //synchronized (responseHeaderDB) {
            headerExists = responseHeaderDB.containsKey(urlhash);
            fileExists = fileDB.containsKey(urlhash);
        //}
        if (headerExists && fileExists) return true;
        if (!headerExists && !fileExists) return false;
        // if not both is there then we do a clean-up
        if (headerExists) try {
            log.warn("header but not content of urlhash " + ASCII.String(urlhash) + " in cache; cleaned up");
            responseHeaderDB.delete(urlhash);
        } catch (final IOException e) {}
        if (fileExists) try {
            //log.logWarning("content but not header of url " + url.toString() + " in cache; cleaned up");
            fileDB.delete(urlhash);
        } catch (final IOException e) {}
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
        Map<String, String> hdb = null;
        try {
            hdb = responseHeaderDB.get(hash);
        } catch (final IOException e) {
            return null;
        } catch (final SpaceExceededException e) {
            return null;
        }
        if (hdb == null) return null;

        return new ResponseHeader(null, hdb);
    }


    /**
     * Returns the content of a cached resource as byte[]
     * @param url the requested resource
     * @return the resource content as byte[]. If no data
     * is available or the cached file is not readable, <code>null</code>
     * is returned.
     */
    public static byte[] getContent(final byte[] hash) {
        // load the url as resource from the cache
        try {
            final byte[] b = fileDB.get(hash);
            if (b == null) return null;
            return b;
        } catch (final UnsupportedEncodingException e) {
            ConcurrentLog.logException(e);
            return null;
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return null;
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            return null;
        } catch (final OutOfMemoryError e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

    public static boolean hasContent(final byte[] hash) {
        // load the url as resource from the cache
        try {
            return fileDB.containsKey(hash);
        } catch (final OutOfMemoryError e) {
            ConcurrentLog.logException(e);
            return false;
        }
    }

    /**
     * removed response header and cached content from the database
     * @param url
     * @throws IOException
     */
    public static void delete(final byte[] hash) throws IOException {
        responseHeaderDB.delete(hash);
        fileDB.delete(hash);
    }
}
