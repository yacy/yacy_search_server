// CacheTest.java
// ---------------------------
// Copyright 2017 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
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

package net.yacy.crawler.data;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.zip.Deflater;

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.ResponseHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.blob.ArrayStack;

/**
 * Unit tests for the {@link Cache} class, and stress test as main function.
 * 
 * @author luc
 *
 */
public class CacheTest {

	/**
	 * Simple test content. Given its reduced size, it is likely to stay in
	 * cache buffers and not written on disk
	 */
	private static final String TEXT_CONTENT = "Simple text content";

	/**
	 * Run before each unit test
	 */
	@Before
	public void setUp() {
		Cache.init(new File(System.getProperty("java.io.tmpdir") + File.separator + "testCache"), "peerSalt",
				Math.max(Math.max(TEXT_CONTENT.getBytes(StandardCharsets.UTF_8).length * 10,
						Cache.DEFAULT_COMPRESSOR_BUFFER_SIZE * 2), Cache.DEFAULT_BACKEND_BUFFER_SIZE * 2),
				2000, Deflater.BEST_COMPRESSION);
		Cache.clear();
	}

	/**
	 * Run after each unit test
	 */
	@After
	public void tearDown() {
		Cache.clear();
		Cache.close();
	}

	/**
	 * @return random bytes likely to be flushed to the file system when
	 *         inserted into cache
	 */
	private static byte[] generateFlushableContent() {
		/* Set the size higher than the cache backend buffers */
		final byte[] content = new byte[Math.max(Cache.DEFAULT_COMPRESSOR_BUFFER_SIZE + 1,
				Cache.DEFAULT_BACKEND_BUFFER_SIZE + 1)];
		/* Fill the content with random bytes so they can not be compressed */
		new Random().nextBytes(content);
		return content;
	}

	@Test
	public void testGetContent() throws MalformedURLException, IOException {
		final ResponseHeader okResponse = new ResponseHeader(HttpStatus.SC_OK);
		DigestURL url = new DigestURL("https://yacy.net");
		byte[] urlHash = url.hash();
		byte[] fileContent = TEXT_CONTENT.getBytes(StandardCharsets.UTF_8);

		/* content not in cache */
		assertNull(Cache.getContent(urlHash));

		/* Store without committing */
		Cache.store(url, okResponse, fileContent);

		/* content now in cache */
		assertArrayEquals(fileContent, Cache.getContent(urlHash));

		/* Commit */
		Cache.commit();

		/* content is still in cache */
		assertArrayEquals(fileContent, Cache.getContent(urlHash));

		/* update the existing entry */
		fileContent = "abcdef".getBytes(StandardCharsets.UTF_8);
		Cache.store(url, okResponse, fileContent);

		/* content has been updated */
		assertArrayEquals(fileContent, Cache.getContent(urlHash));

		/*
		 * Store an entry larger than backend buffer sizes, likely to be flushed
		 * to the file system
		 */
		url = new DigestURL("https://json.org");
		urlHash = url.hash();
		fileContent = generateFlushableContent();
		Cache.store(new DigestURL("https://json.org"), okResponse, fileContent);
		Cache.commit();
		/* content is in cache */
		assertArrayEquals(fileContent, Cache.getContent(urlHash));
	}

	@Test
	public void testDelete() throws MalformedURLException, IOException {
		final ResponseHeader okResponse = new ResponseHeader(HttpStatus.SC_OK);
		final DigestURL url = new DigestURL("https://yacy.net");
		final byte[] urlHash = url.hash();
		final byte[] fileContent = TEXT_CONTENT.getBytes(StandardCharsets.UTF_8);

		/* try deleting an entry not in cache */
		Cache.delete(urlHash);
		assertFalse(Cache.has(urlHash));
		assertFalse(Cache.hasContent(urlHash));

		/* Store without committing */
		Cache.store(url, okResponse, fileContent);
		assertTrue(Cache.has(urlHash));
		assertTrue(Cache.hasContent(urlHash));

		/* delete entry in cache */
		Cache.delete(urlHash);
		assertFalse(Cache.has(urlHash));
		assertFalse(Cache.hasContent(urlHash));

		/* Store and commit */
		Cache.store(url, okResponse, fileContent);
		Cache.commit();
		assertTrue(Cache.has(urlHash));
		assertTrue(Cache.hasContent(urlHash));

		/* delete entry in cache */
		Cache.delete(urlHash);
		assertFalse(Cache.has(urlHash));
		assertFalse(Cache.hasContent(urlHash));

		/*
		 * Store an entry larger than backend buffer sizes, likely to be flushed
		 * to the file system
		 */
		Cache.store(url, okResponse, generateFlushableContent());
		Cache.commit();
		assertTrue(Cache.has(urlHash));
		assertTrue(Cache.hasContent(urlHash));

		/* delete entry in cache */
		Cache.delete(urlHash);
		assertFalse(Cache.has(urlHash));
		assertFalse(Cache.hasContent(urlHash));
	}

	@Test
	public void testClear() throws MalformedURLException, IOException {
		final ResponseHeader okResponse = new ResponseHeader(HttpStatus.SC_OK);
		final DigestURL url = new DigestURL("https://yacy.net");
		final byte[] urlHash = url.hash();
		byte[] fileContent = TEXT_CONTENT.getBytes(StandardCharsets.UTF_8);

		/* Store without committing */
		Cache.store(url, okResponse, fileContent);
		assertTrue(Cache.has(urlHash));
		assertTrue(Cache.hasContent(urlHash));

		/* Clear the cache */
		Cache.clear();
		assertFalse(Cache.has(urlHash));
		assertFalse(Cache.hasContent(urlHash));
		assertEquals(0, Cache.getActualCacheDocCount());
		assertEquals(0, Cache.getActualCacheSize());

		/* Clear the cache even if already empty */
		Cache.clear();
		assertEquals(0, Cache.getActualCacheDocCount());
		assertEquals(0, Cache.getActualCacheSize());

		/* Store and commit */
		Cache.store(url, okResponse, fileContent);
		Cache.commit();
		assertTrue(Cache.has(urlHash));
		assertTrue(Cache.hasContent(urlHash));

		/* Clear the cache */
		Cache.clear();
		assertFalse(Cache.has(urlHash));
		assertFalse(Cache.hasContent(urlHash));
		assertEquals(0, Cache.getActualCacheDocCount());
		assertEquals(0, Cache.getActualCacheSize());

		/*
		 * Store an entry larger than backend buffer sizes, likely to be flushed
		 * to the file system
		 */
		fileContent = generateFlushableContent();
		Cache.store(url, okResponse, fileContent);
		Cache.commit();
		assertTrue(Cache.has(urlHash));
		assertTrue(Cache.hasContent(urlHash));
		assertNotEquals(0, Cache.getActualCacheDocCount());
		assertNotEquals(0, Cache.getActualCacheSize());

		/* Clear the cache */
		Cache.clear();
		assertFalse(Cache.has(urlHash));
		assertFalse(Cache.hasContent(urlHash));
		assertEquals(0, Cache.getActualCacheDocCount());
		assertEquals(0, Cache.getActualCacheSize());
	}

	/**
	 * A test task performing some accesses to the cache. To be run
	 * concurrently.
	 * 
	 * @author luc
	 *
	 */
	private static class CacheAccessTask extends Thread {

		/** The test URLs for this task */
		private final List<DigestURL> urls;

		/** Number of steps to run */
		private final int maxSteps;

		/** Number of steps effectively run */
		private int steps;

		/** Test content size in bytes */
		private final int contentSize;

		/** Sleep time (in milliseconds) between each cache operations */
		private final long sleepTime;

		/** Number of Cache.store() failures */
		private int storeFailures;

		/** Total time spent (in nanoseconds) on the Cache.store operation */
		private long storeTime;

		/** Maximum time spent (in nanoseconds)on the Cache.store operation */
		private long maxStoreTime;

		/**
		 * Total time spent (in nanoseconds) on the Cache.getContent operation
		 */
		private long getContentTime;

		/**
		 * Maximum time spent (in nanoseconds) on the Cache.getContent operation
		 */
		private long maxGetContentTime;

		/** Total time spent (in nanoseconds) on the Cache.store operation */
		private long deleteTime;

		/** Maximum time spent (in nanoseconds) on the Cache.store operation */
		private long maxDeleteTime;

		/**
		 * @param urls
		 *            the test URLs
		 * @param steps
		 *            number of loops
		 * @param contentSize
		 *            size (in bytes) of the test content to generate and use
		 * @param sleepTime
		 *            sleep time (in milliseconds) between each cache operations
		 */
		public CacheAccessTask(final List<DigestURL> urls, final int steps, final int contentSize,
				final long sleepTime) {
			this.urls = urls;
			this.maxSteps = steps;
			this.contentSize = contentSize;
			this.sleepTime = sleepTime;
		}

		private void sleep() {
			if (this.sleepTime > 0) {
				try {
					Thread.sleep(this.sleepTime);
				} catch (InterruptedException ignored) {
				}
			}
		}

		@Override
		public void run() {
			try {
				final ResponseHeader okResponse = new ResponseHeader(HttpStatus.SC_OK);
				final byte[] fileContent = new byte[contentSize];
				new Random().nextBytes(fileContent);

				this.storeTime = 0;
				this.maxStoreTime = 0;
				this.storeFailures = 0;
				this.getContentTime = 0;
				this.maxGetContentTime = 0;
				this.maxDeleteTime = 0;
				this.deleteTime = 0;
				this.steps = 0;
				long time;
				while (this.steps < this.maxSteps) {
					int processedURLs = 0;
					/* Run the same steps for each test URL */
					for (DigestURL url : urls) {
						if (this.steps >= this.maxSteps) {
							break;
						}
						byte[] urlHash = url.hash();

						/* measure store */
						time = System.nanoTime();
						try {
							Cache.store(url, okResponse, fileContent);
						} catch (final IOException e) {
							this.storeFailures++;
						}
						time = (System.nanoTime() - time);
						this.storeTime += time;
						this.maxStoreTime = Math.max(time, this.maxStoreTime);

						sleep();

						/* Measure content retrieval */
						time = System.nanoTime();
						Cache.getContent(urlHash);
						time = (System.nanoTime() - time);
						this.getContentTime += time;
						this.maxGetContentTime = Math.max(time, this.maxGetContentTime);

						sleep();

						/*
						 * Run some cache functions without measurement, to
						 * produce some concurrent accesses
						 */
						Cache.has(urlHash);

						sleep();

						Cache.hasContent(urlHash);

						sleep();

						Cache.getResponseHeader(urlHash);

						sleep();

						this.steps++;
						processedURLs++;
					}

					Cache.commit();

					/* Now delete each previously inserted URL */
					for (int i = 0; i < processedURLs; i++) {
						DigestURL url = urls.get(i);
						byte[] urlHash = url.hash();

						/* Measure delete operation */
						time = System.nanoTime();
						Cache.delete(urlHash);
						time = (System.nanoTime() - time);
						this.deleteTime += time;
						this.maxDeleteTime = Math.max(time, this.maxDeleteTime);

						sleep();
					}
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public int getSteps() {
			return this.steps;
		}

		public long getStoreTime() {
			return this.storeTime;
		}

		public long getMaxStoreTime() {
			return this.maxStoreTime;
		}

		public long getGetContentTime() {
			return this.getContentTime;
		}

		public long getMaxGetContentTime() {
			return this.maxGetContentTime;
		}

		public long getDeleteTime() {
			return this.deleteTime;
		}

		public long getMaxDeleteTime() {
			return this.maxDeleteTime;
		}

		public int getStoreFailures() {
			return this.storeFailures;
		}

	}

	/**
	 * Run a stress test on the Cache
	 * 
	 * @param args
	 *            main arguments
	 * @throws IOException
	 *             when a error occurred
	 */
	public static void main(final String args[]) throws IOException {
		System.out.println("Stress test on Cache");

		/*
		 * Set the root log level to WARNING to prevent filling the console with
		 * too many information log messages
		 */
		LogManager.getLogManager()
				.readConfiguration(new ByteArrayInputStream(".level=WARNING".getBytes(StandardCharsets.ISO_8859_1)));
		
		/* Main control parameters. Modify values for different scenarios. */

		/* Number of concurrent test tasks */
		final int threads = 50;
		/* Number of steps in each task */
		final int steps = 10;
		/* Number of test URLs in each task */
		final int urlsPerThread = 5;
		/* Size of the generated test content */
		final int contentSize = Math.max(Cache.DEFAULT_COMPRESSOR_BUFFER_SIZE + 1,
				Cache.DEFAULT_BACKEND_BUFFER_SIZE + 1) / urlsPerThread;
		/* Cache maximum size */
		final long cacheMaxSize = Math.min(1024 * 1024 * 1024, ((long) contentSize) * 10 * urlsPerThread);
		/* Sleep time between each cache operation */
		final long sleepTime = 0;
		/* Maximum waiting time (in ms) for acquiring a synchronization lock */
		final long lockTimeout = 2000;
		/* The backend compression level */
		final int compressionLevel = Deflater.BEST_COMPRESSION;

		Cache.init(new File(System.getProperty("java.io.tmpdir") + File.separator + "yacyTestCache"), "peerSalt",
				cacheMaxSize, lockTimeout, compressionLevel);
		Cache.clear();
		System.out.println("Cache initialized with a maximum size of " + cacheMaxSize + " bytes.");

		try {
			System.out.println("Starting " + threads + " threads ...");
			long time = System.nanoTime();
			List<CacheAccessTask> tasks = new ArrayList<>();
			for (int count = 0; count < threads; count++) {
				List<DigestURL> urls = new ArrayList<>();
				for (int i = 0; i < urlsPerThread; i++) {
					urls.add(new DigestURL("https://yacy.net/" + i + "/" + count));
				}
				CacheAccessTask thread = new CacheAccessTask(urls, steps, contentSize, sleepTime);
				thread.start();
				tasks.add(thread);
			}
			/* Wait for tasks termination */
			for (CacheAccessTask task : tasks) {
				try {
					task.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			/*
			 * Check consistency : cache should be empty when all tasks have
			 * terminated without error
			 */
			Cache.commit();
			long docCount = Cache.getActualCacheDocCount();
			if (docCount > 0) {
				System.out.println("Cache is not empty!!! Actual documents count : " + docCount);
			}

			System.out.println("All threads terminated in " + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - time)
					+ "s. Computing statistics...");
			long storeTime = 0;
			long maxStoreTime = 0;
			long getContentTime = 0;
			long maxGetContentTime = 0;
			int storeFailures = 0;
			long deleteTime = 0;
			long maxDeleteTime = 0;
			long totalSteps = 0;
			for (CacheAccessTask task : tasks) {
				storeTime += task.getStoreTime();
				maxStoreTime = Math.max(task.getMaxStoreTime(), maxStoreTime);
				getContentTime += task.getGetContentTime();
				maxGetContentTime = Math.max(task.getMaxGetContentTime(), maxGetContentTime);
				storeFailures += task.getStoreFailures();
				deleteTime += task.getDeleteTime();
				maxDeleteTime = Math.max(task.getMaxDeleteTime(), maxDeleteTime);
				totalSteps += task.getSteps();
			}
			System.out.println("Cache.store() total time (ms) : " + TimeUnit.NANOSECONDS.toMillis(storeTime));
			System.out.println("Cache.store() maximum time (ms) : " + TimeUnit.NANOSECONDS.toMillis(maxStoreTime));
			System.out
					.println("Cache.store() mean time (ms) : " + TimeUnit.NANOSECONDS.toMillis(storeTime / totalSteps));
			System.out
					.println("Cache.store() failures : " + storeFailures);
			System.out.println("");
			System.out.println("Cache.getContent() total time (ms) : " + TimeUnit.NANOSECONDS.toMillis(getContentTime));
			System.out.println(
					"Cache.getContent() maximum time (ms) : " + TimeUnit.NANOSECONDS.toMillis(maxGetContentTime));
			System.out.println("Cache.getContent() mean time (ms) : "
					+ TimeUnit.NANOSECONDS.toMillis(getContentTime / totalSteps));
			System.out.println("Cache hits : " + Cache.getHits() + " total requests : "
					+ Cache.getTotalRequests() + " ( hit rate : "
					+ NumberFormat.getPercentInstance().format(Cache.getHitRate()) + " )");
			System.out.println("");
			System.out.println("Cache.delete() total time (ms) : " + TimeUnit.NANOSECONDS.toMillis(deleteTime));
			System.out.println("Cache.delete() maximum time (ms) : " + TimeUnit.NANOSECONDS.toMillis(maxDeleteTime));
			System.out.println(
					"Cache.delete() mean time (ms) : " + TimeUnit.NANOSECONDS.toMillis(deleteTime / totalSteps));
		} finally {
			try {
				Cache.close();
			} finally {
				/* Shutdown running threads */
				ArrayStack.shutdownDeleteService();
				try {
					Domains.close();
				} finally {
					ConcurrentLog.shutdown();
				}
			}
		}

	}

}
