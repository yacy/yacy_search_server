// httpCache.java
// -----------------------
// part of YaCy
// SPDX-FileCopyrightText: 2004 Michael Peter Christen <mc@yacy.net)>
// SPDX-License-Identifier: GPL-2.0-or-later
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
import java.util.concurrent.atomic.AtomicLong;

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

	/** Default size in bytes of the backend buffer (buffered bytes before writing to the the file system) */
	protected static final int DEFAULT_BACKEND_BUFFER_SIZE = 1024 * 1024 * 2;
	
	/** Default size in bytes of the compressor buffer (buffered bytes before compressing and sending to the backend ) */
	protected static final int DEFAULT_COMPRESSOR_BUFFER_SIZE = 6 * 1024 * 1024;
	
	/** Default size in bytes of the response header data base buffer (buffered bytes before writing to the file system) */
	protected static final int DEFAULT_RESPONSE_HEADER_BUFFER_SIZE = 2048;
	
	
    private static final String RESPONSE_HEADER_DB_NAME = "responseHeader.heap";
    private static final String FILE_DB_NAME = "file.array";

    private static MapHeap responseHeaderDB = null;
    private static Compressor fileDB = null;
    private static ArrayStack fileDBunbuffered = null;

    private static volatile long maxCacheSize = Long.MAX_VALUE;
    
    /** Total number of requests for cached response since last start/initialization or cache clear */
    private static AtomicLong totalRequests = new AtomicLong(0);
    
    /** Total number of cache hits since last start/initialization or cache clear */
    private static AtomicLong hits = new AtomicLong(0);
    
    private static File cachePath = null;
    private static String prefix;
    public static final ConcurrentLog log = new ConcurrentLog("HTCACHE");

    /**
     * @param htCachePath folder path for the cache
     * @param peerSalt peer identifier
     * @param cacheSizeMax maximum cache size in bytes
     * @param lockTimeout maximum time (in milliseconds) to acquire a synchronization lock on store() and getContent()
     * @param compressionLevel the compression level : supported values ranging from 0 - no compression, to 9 - best compression
     */
    public static void init(final File htCachePath, final String peerSalt, final long cacheSizeMax, final long lockTimeout, final int compressionLevel) {

        cachePath = htCachePath;
        maxCacheSize = cacheSizeMax;
        prefix = peerSalt;
        totalRequests.set(0);
        hits.set(0);

        // set/make cache path
        if (!htCachePath.exists()) {
            htCachePath.mkdirs();
        }

        // open the response header database
        final File dbfile = new File(cachePath, RESPONSE_HEADER_DB_NAME);
        try {
            responseHeaderDB = new MapHeap(dbfile, Word.commonHashLength, Base64Order.enhancedCoder, DEFAULT_RESPONSE_HEADER_BUFFER_SIZE, 100, ' ');
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            // try a healing
            if (dbfile.exists()) {
                dbfile.delete();
                try {
                    responseHeaderDB = new MapHeap(dbfile, Word.commonHashLength, Base64Order.enhancedCoder, DEFAULT_RESPONSE_HEADER_BUFFER_SIZE, 100, ' ');
                } catch (final IOException ee) {
                    ConcurrentLog.logException(e);
                }
            }
        }
        // open the cache file
        try {
            fileDBunbuffered = new ArrayStack(new File(cachePath, FILE_DB_NAME), prefix, Base64Order.enhancedCoder, 12, DEFAULT_BACKEND_BUFFER_SIZE, false, true);
            fileDBunbuffered.setMaxSize(maxCacheSize);
            fileDB = new Compressor(fileDBunbuffered, DEFAULT_COMPRESSOR_BUFFER_SIZE, lockTimeout, compressionLevel);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            // try a healing
            if (cachePath.exists()) {
                cachePath.delete();
                try {
                    fileDBunbuffered = new ArrayStack(new File(cachePath, FILE_DB_NAME), prefix, Base64Order.enhancedCoder, 12, DEFAULT_BACKEND_BUFFER_SIZE, false, true);
                    fileDBunbuffered.setMaxSize(maxCacheSize);
                    fileDB = new Compressor(fileDBunbuffered, DEFAULT_COMPRESSOR_BUFFER_SIZE, lockTimeout, compressionLevel);
                } catch (final IOException ee) {
                    ConcurrentLog.logException(e);
                }
            }
        }
        ConcurrentLog.info("Cache", "initialized cache database responseHeaderDB.size() = " + (responseHeaderDB == null ? "NULL" : responseHeaderDB.size()) + ", fileDB.size() = " + (fileDB == null ? "NULL" : fileDB.size()));

        // clean up the responseHeaderDB which cannot be cleaned the same way as the cache files.
        // We do this as a concurrent job only once after start-up silently
        if (responseHeaderDB.size() != fileDB.size()) {
            ConcurrentLog.warn("Cache", "file and metadata size is not equal, starting a cleanup thread...");
            Thread startupCleanup = new Thread("Cache startupCleanup") {
                @Override
                public void run() {
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
        /* Clear statistics */
        totalRequests.set(0);
        hits.set(0);
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
     * Warning : even when the cache is empty, 
     * the actual cache size may not be zero because heap files still containing zeros after deletions
     * @return the current actual cache size stored on disk
     */
    public static long getActualCacheSize() {
        return fileDBunbuffered.length();
    }
    
    /**
     * @return the current actual number of cached documents stored on disk
     */
    public static long getActualCacheDocCount() {
        return fileDBunbuffered.size();
    }
    
    /**
     * Set the new content compression level 
     * @param newCompressionLevel the new compression level. Supported values between 0 (no compression) and 9 (best compression)
     */
    public static void setCompressionLevel(final int newCompressionLevel) {
    	fileDB.setCompressionLevel(newCompressionLevel);
    }
    
    /**
     * Set the new synchronization lock timeout.
     * @param lockTimeout the new synchronization lock timeout (in milliseconds).
     */
    public static void setLockTimeout(final long lockTimeout) {
    	fileDB.setLockTimeout(lockTimeout);
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
        if (responseHeader == null) throw new IOException("Cache.store of url " + url.toNormalform(false) + " not possible: responseHeader == null");
        if (responseHeader.getXRobotsTag().contains("noarchive")) return; // don't cache, see http://noarchive.net/
        if (file == null) throw new IOException("Cache.store of url " + url.toNormalform(false) + " not possible: file == null");
        log.info("storing content of url " + url.toNormalform(false) + ", " + file.length + " bytes");

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
    	totalRequests.incrementAndGet();
        boolean headerExists;
        boolean fileExists;
        //synchronized (responseHeaderDB) {
            headerExists = responseHeaderDB.containsKey(urlhash);
            fileExists = fileDB.containsKey(urlhash);
        //}
        if (headerExists && fileExists) {
        	hits.incrementAndGet();
        	return true;
        }
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
    	totalRequests.incrementAndGet();
        // loading data from database
        Map<String, String> hdb = null;
        try {
            hdb = responseHeaderDB.get(hash);
        } catch (final IOException e) {
            return null;
        } catch (final SpaceExceededException e) {
            return null;
        }
        if (hdb == null) {
        	return null;
        }

        hits.incrementAndGet();
        return new ResponseHeader(hdb);
    }


    /**
     * Returns the content of a cached resource as byte[]
     * @param url the requested resource
     * @return the resource content as byte[]. If no data
     * is available or the cached file is not readable, <code>null</code>
     * is returned.
     */
    public static byte[] getContent(final byte[] hash) {
    	totalRequests.incrementAndGet();
        // load the url as resource from the cache
        try {
            final byte[] b = fileDB.get(hash);
            if (b == null) {
            	return null;
            }
            hits.incrementAndGet();
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
    	totalRequests.incrementAndGet();
        // load the url as resource from the cache
        try {
            boolean result = fileDB.containsKey(hash);
            if(result) {
            	hits.incrementAndGet();
            }
            return result;
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
    
    /**
     * @return the total number of requests for cache content since last start/initialization or cache clear
     */
    public static long getTotalRequests() {
		return totalRequests.get();
	}
    
    /**
     * @return the total number of cache hits (cached response found) since last start/initialization or cache clear
     */
    public static long getHits() {
		return hits.get();
	}
    
    /**
     * @return the hit rate (proportion of hits over total requests)
     */
    public static double getHitRate() {
    	final long total = totalRequests.get();
    	return total > 0 ? ((Cache.getHits() / ((double) total))) : 0.0 ;
    }
}
