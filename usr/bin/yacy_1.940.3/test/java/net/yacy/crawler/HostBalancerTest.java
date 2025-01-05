package net.yacy.crawler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;

import org.junit.Test;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.robots.RobotsTxt;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.blob.ArrayStack;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.RowHandleSet;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.SwitchboardConstants;

public class HostBalancerTest {

    private static final File QUEUES_ROOT = new File("test/DATA/INDEX/QUEUES");
    private static final File DATA_DIR = new File("test/DATA");
    
    private static final boolean EXCEED_134217727 = true;
    private static final int ON_DEMAND_LIMIT = 1000;
    
    /**
     * Test of reopen existing HostBalancer cache to test/demonstrate issue with
     * HostQueue for file: protocol
     */
    @Test
    public void testReopen() throws IOException, SpaceExceededException, InterruptedException {
        String hostDir = "C:\\filedirectory";

        // prepare one urls for push test
        String urlstr = "file:///" + hostDir;
        DigestURL url = new DigestURL(urlstr);
        Request req = new Request(url, null);

        FileUtils.deletedelete(QUEUES_ROOT); // start clean test

        HostBalancer hb = new HostBalancer(QUEUES_ROOT, ON_DEMAND_LIMIT, EXCEED_134217727, false);
        hb.clear();

        Thread.sleep(100);
        assertEquals("After clear", 0, hb.size());

        WorkTables wt = new WorkTables(DATA_DIR);
        RobotsTxt rob = new RobotsTxt(wt, null, 10);

        String res = hb.push(req, null, rob); // push url
        assertNull(res); // should have no error text
        assertTrue(hb.has(url.hash())); // check existence
        assertEquals("first push of one url", 1, hb.size()); // expected size=1

        res = hb.push(req, null, rob); // push same url (should be rejected = double occurence)
        assertNotNull(res); // should state double occurrence
        assertTrue(hb.has(url.hash()));
        assertEquals("second push of same url", 1, hb.size());

        hb.close(); // close

        Thread.sleep(200); // wait a bit for file operation

        hb = new HostBalancer(QUEUES_ROOT, ON_DEMAND_LIMIT, EXCEED_134217727, false); // reopen balancer

        assertEquals("size after reopen (with one existing url)", 1, hb.size()); // expect size=1 from previous push
        assertTrue("check existance of pushed url", hb.has(url.hash())); // check url exists (it fails as after reopen internal queue.hosthash is wrong)

        res = hb.push(req, null, rob); // push same url as before (should be rejected, but isn't due to hosthash mismatch afte reopen)
        assertNotNull("should state double occurence", res);
        assertEquals("first push of same url after reopen", 1, hb.size()); // should stay size=1
        assertTrue("check existance of pushed url", hb.has(url.hash()));

        res = hb.push(req, null, rob);
        assertNotNull("should state double occurence", res);
        assertTrue("check existance of pushed url", hb.has(url.hash()));
        assertEquals("second push of same url after reopen", 1, hb.size()); // double check, should stay size=1

        // list all urls in hostbalancer
        Iterator<Request> it = hb.iterator();
        while (it.hasNext()) {
            Request rres = it.next();
            System.out.println(rres.toString());
        }
        hb.close();

    }
    
	/**
	 * A test task performing some operations to be profiled on the HostBalancer. To
	 * run concurrently.
	 * 
	 */
	private static class ProfilingTask extends Thread {
		
		private static final CrawlProfile CRAWL_PROFILE = new CrawlProfile(
				CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT, CrawlProfile.MATCH_ALL_STRING, // crawlerUrlMustMatch
				CrawlProfile.MATCH_NEVER_STRING, // crawlerUrlMustNotMatch
				CrawlProfile.MATCH_ALL_STRING, // crawlerIpMustMatch
				CrawlProfile.MATCH_NEVER_STRING, // crawlerIpMustNotMatch
				CrawlProfile.MATCH_NEVER_STRING, // crawlerCountryMustMatch
				CrawlProfile.MATCH_NEVER_STRING, // crawlerNoDepthLimitMatch
				CrawlProfile.MATCH_ALL_STRING, // indexUrlMustMatch
				CrawlProfile.MATCH_NEVER_STRING, // indexUrlMustNotMatch
				CrawlProfile.MATCH_ALL_STRING, // indexContentMustMatch
				CrawlProfile.MATCH_NEVER_STRING, // indexContentMustNotMatch
				0, false, CrawlProfile.getRecrawlDate(CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT_RECRAWL_CYCLE),
				-1, true, true, true, false, // crawlingQ, followFrames, obeyHtmlRobotsNoindex, obeyHtmlRobotsNofollow,
				true, true, true, false, -1, false, true, CrawlProfile.MATCH_NEVER_STRING, CacheStrategy.IFEXIST,
				"robot_" + CrawlSwitchboard.CRAWL_PROFILE_SNIPPET_GLOBAL_TEXT,
				ClientIdentification.yacyIntranetCrawlerAgentName, null, null, 0);
		
		/** RobotsTxt instance */
		private final RobotsTxt robots;
		
		/** The HostBalancer instance target */
		private final HostBalancer balancer;

		/** The test URLs for this task */
		private final List<DigestURL> urls;

		/** Number of steps to run */
		private final int maxSteps;

		/** Number of steps effectively run */
		private int steps;

		/** Sleep time (in milliseconds) between each operation */
		private final long sleepTime;
		
		/** Number of HostBalancer.push() failures */
		private int pushFailures;

		/** Total time spent (in nanoseconds) on the HostBalancer.push() operation */
		private long pushTime;

		/** Maximum time spent (in nanoseconds)on the HostBalancer.push() operation */
		private long maxPushTime;

		/** Total time spent (in nanoseconds) on the HostBalancer.has() operation */
		private long hasTime;

		/** Maximum time spent (in nanoseconds) on the HostBalancer.has() operation */
		private long maxHasTime;

		/** Total time spent (in nanoseconds) on the HostBalancer.remove() operation */
		private long removeTime;

		/** Maximum time spent (in nanoseconds) on the HostBalancer.remove() operation */
		private long maxRemoveTime;

		/**
		 * @param balancer the HostBalancer instance to be tested
		 * @param urls
		 *            the test URLs
		 * @param steps
		 *            number of loops
		 * @param sleepTime
		 *            sleep time (in milliseconds) between each operation
		 */
		public ProfilingTask(final HostBalancer balancer, final RobotsTxt robots, final List<DigestURL> urls, final int steps, final long sleepTime) {
			this.balancer = balancer;
			this.robots = robots;
			this.urls = urls;
			this.maxSteps = steps;
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
				this.pushTime = 0;
				this.maxPushTime = 0;
				this.hasTime = 0;
				this.maxHasTime = 0;
				this.maxRemoveTime = 0;
				this.removeTime = 0;
				this.steps = 0;
				long time;
				while (this.steps < this.maxSteps) {
					int processedURLs = 0;
					/* Run the same steps for each test URL */
					for (final DigestURL url : urls) {
						if (this.steps >= this.maxSteps) {
							break;
						}
						final byte[] urlHash = url.hash();
						final Request req = new Request(ASCII.getBytes("testPeer"), url, null, "", new Date(),
								CRAWL_PROFILE.handle(), 0, CRAWL_PROFILE.timezoneOffset());
						

						/* Measure push() */
						time = System.nanoTime();
						try {
							if(this.balancer.push(req, CRAWL_PROFILE, this.robots) != null) {
								this.pushFailures++;
							}
						} catch (final SpaceExceededException e) {
							this.pushFailures++;
						}
						time = (System.nanoTime() - time);
						
						this.pushTime += time;
						this.maxPushTime = Math.max(time, this.maxPushTime);

						sleep();

						/* Measure get() */
						time = System.nanoTime();
						this.balancer.has(urlHash);
						time = (System.nanoTime() - time);
						
						this.hasTime += time;
						this.maxHasTime = Math.max(time, this.maxHasTime);

						sleep();

						this.steps++;
						processedURLs++;
					}

					/* Now delete each previously inserted URL */
					for (int i = 0; i < processedURLs; i++) {
						DigestURL url = urls.get(i);
						byte[] urlHash = url.hash();
			            final HandleSet urlHashes = new RowHandleSet(Word.commonHashLength, Base64Order.enhancedCoder, 1);
			            try {
							urlHashes.put(urlHash);
							
							/* Measure remove() operation */
							time = System.nanoTime();
							this.balancer.remove(urlHashes);
							time = (System.nanoTime() - time);
							
							this.removeTime += time;
							this.maxRemoveTime = Math.max(time, this.maxRemoveTime);
						} catch (final SpaceExceededException e) {
							// should not happen
							e.printStackTrace();
						}

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

		public int getPushFailures() {
			return this.pushFailures;
		}

		public long getPushTime() {
			return this.pushTime;
		}

		public long getMaxPushTime() {
			return this.maxPushTime;
		}

		public long getHasTime() {
			return this.hasTime;
		}

		public long getMaxHasTime() {
			return this.maxHasTime;
		}

		public long getRemoveTime() {
			return this.removeTime;
		}

		public long getMaxRemoveTime() {
			return this.maxRemoveTime;
		}


	}

	/**
	 * Run a stress test on the HostBalancer
	 * 
	 * @param args
	 *            main arguments
	 * @throws IOException
	 *             when a error occurred
	 */
	public static void main(final String args[]) throws IOException {
		System.out.println("Stress test on HostBalancer");

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
		final int steps = 100;
		/* Number of test URLs in each task */
		final int urlsPerThread = 5;
		/* Sleep time between each measured operation on the balancer */
		final long sleepTime = 0;
		
		final RobotsTxt robots = new RobotsTxt(new WorkTables(DATA_DIR), null,
				SwitchboardConstants.ROBOTS_TXT_THREADS_ACTIVE_MAX_DEFAULT);
		
        FileUtils.deletedelete(QUEUES_ROOT);

        final HostBalancer hb = new HostBalancer(QUEUES_ROOT, ON_DEMAND_LIMIT, EXCEED_134217727, false);
        hb.clear();

		System.out.println("HostBalancer initialized with persistent queues folder " + QUEUES_ROOT);

		try {
			System.out.println("Starting " + threads + " threads ...");
			long time = System.nanoTime();
			final List<ProfilingTask> tasks = new ArrayList<>();
			for (int count = 0; count < threads; count++) {
				final List<DigestURL> urls = new ArrayList<>();
				for (int i = 0; i < urlsPerThread; i++) {
					/* We use here local test URLs to prevent running RobotsTxt internals */
					urls.add(new DigestURL("http://localhost/" + i + "/" + count));
				}
				final ProfilingTask thread = new ProfilingTask(hb, robots, urls, steps, sleepTime);
				thread.start();
				tasks.add(thread);
			}
			/* Wait for tasks termination */
			for (final ProfilingTask task : tasks) {
				try {
					task.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			/*
			 * Check consistency : balancer cache should be empty when all tasks have
			 * terminated without error
			 */
			final int depthCacheSize = HostBalancer.depthCache.size();
			if(depthCacheSize > 0) {
				System.out.println("Depth cache is not empty!!! Actual URLs count : " + depthCacheSize);
			}

			System.out.println("All threads terminated in " + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - time)
					+ "s. Computing statistics...");
			long pushTime = 0;
			long maxPushTime = 0;
			long hasTime = 0;
			long maxHasTime = 0;
			int pushFailures = 0;
			long removeTime = 0;
			long maxRemoveTime = 0;
			long totalSteps = 0;
			for (final ProfilingTask task : tasks) {
				pushTime += task.getPushTime();
				maxPushTime = Math.max(task.getMaxPushTime(), maxPushTime);
				hasTime += task.getHasTime();
				maxHasTime = Math.max(task.getMaxHasTime(), maxHasTime);
				pushFailures += task.getPushFailures();
				removeTime += task.getRemoveTime();
				maxRemoveTime = Math.max(task.getMaxRemoveTime(), maxRemoveTime);
				totalSteps += task.getSteps();
			}
			System.out.println("HostBalancer.push() total time (ms) : " + TimeUnit.NANOSECONDS.toMillis(pushTime));
			System.out.println("HostBalancer.push() maximum time (ms) : " + TimeUnit.NANOSECONDS.toMillis(maxPushTime));
			System.out
					.println("HostBalancer.push() mean time (ms) : " + TimeUnit.NANOSECONDS.toMillis(pushTime / totalSteps));
			System.out
					.println("HostBalancer.push() failures : " + pushFailures);
			System.out.println("");
			System.out.println("HostBalancer.has() total time (ms) : " + TimeUnit.NANOSECONDS.toMillis(hasTime));
			System.out.println(
					"HostBalancer.has() maximum time (ms) : " + TimeUnit.NANOSECONDS.toMillis(maxHasTime));
			System.out.println("HostBalancer.has() mean time (ms) : "
					+ TimeUnit.NANOSECONDS.toMillis(hasTime / totalSteps));
			System.out.println("");
			System.out.println("HostBalancer.remove() total time (ms) : " + TimeUnit.NANOSECONDS.toMillis(removeTime));
			System.out.println("HostBalancer.remove() maximum time (ms) : " + TimeUnit.NANOSECONDS.toMillis(maxRemoveTime));
			System.out.println(
					"HostBalancer.remove() mean time (ms) : " + TimeUnit.NANOSECONDS.toMillis(removeTime / totalSteps));
		} finally {
			try {
				hb.close();
			} finally {
				/* Shutdown running threads */
				ArrayStack.shutdownDeleteService();
				
				robots.close();
				
				try {
					Domains.close();
				} finally {
					ConcurrentLog.shutdown();
				}
			}
		}

	}

}
