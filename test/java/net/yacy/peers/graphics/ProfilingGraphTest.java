// ProfilingGraphTest.java
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

package net.yacy.peers.graphics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.EventTracker;
import net.yacy.visualization.RasterPlotter;

/**
 * Unit tests for the {@link ProfilingGraph} class
 */
public class ProfilingGraphTest {

	/**
	 * Generate a performance graph and write it to a temporary file for manual
	 * viasual checking.<br/>
	 * Note : it is not an automated JUnit test function as it is time dependant.
	 *
	 * @throws IOException
	 *             when a read/write exception occurred
	 * @throws InterruptedException
	 *             when interrupted before termination
	 */
	public static void main(final String args[]) throws IOException, InterruptedException {
		long time = System.currentTimeMillis();
		final long beginTime = time;
		long prevTime = time;

		/* Feed the event tracker with test values */
		final int steps = 100;

		/* Ascending memory usage from 500MB to 16GB */
		long bytes = 500L * 1024L * 1024L;
		final long bytesStep = (16L * 1204L * 1204L * 1204L) / steps;

		/* Descending words, from max integer value to zero.
		 * (events values are stored as long, but currently the actual maximum possible value ford WORDCACHE is an Integer.MAX_VALUE) */
		long words = Integer.MAX_VALUE;
		final long wordsStep = words / steps;

		for (int step = 0; step < steps; step++) {
			if ((step % 30) == 0) {
				/* Stable PPRM and peer ping values */
				EventTracker.update(EventTracker.EClass.PPM, Long.valueOf(500), false);
				EventTracker.update(EventTracker.EClass.PEERPING,
						new ProfilingGraph.EventPing("localPeerName", "aaaa", true, 1536), false);
			}
			EventTracker.update(EventTracker.EClass.WORDCACHE, Long.valueOf(words), false);
			EventTracker.update(EventTracker.EClass.MEMORY, Long.valueOf(bytes), false);
			time = System.currentTimeMillis();
			/* Ensure each test event is separated at least from 1ms */
			while (time == prevTime) {
				Thread.sleep(1);
				time = System.currentTimeMillis();
			}
			prevTime = time;

			bytes += bytesStep;
			words -= wordsStep;
		}

		long timeRange = (time - beginTime) * 2;

		/* Parameters likely to be encountered on the PerformanceGraph calling class */
		final int indexSizeCache = 865749;
		final int rwiCount = 512378;
		final int rwiBufferCount = 6754;
		final RasterPlotter graph = ProfilingGraph.performanceGraph(660, 240,
				indexSizeCache + " URLS / " + rwiCount + " WORDS IN INDEX / " + rwiBufferCount + " WORDS IN CACHE",
				(int) timeRange, TimeUnit.MILLISECONDS, true, true);

		/* Now write the result to a temporary file for visual checking */
		final File outputFile = new File(System.getProperty("java.io.tmpdir"), "testPerformanceGraph.png");
		try (
				/* Automatically closed by this try-with-resources statement */
				final FileOutputStream fos = new FileOutputStream(outputFile);) {
			fos.write(RasterPlotter.exportImage(graph.getImage(), "png").getBytes());
			System.out.println("Performance graph writtent to file " + outputFile);
		} finally {
			ConcurrentLog.shutdown();
		}
	}

}
