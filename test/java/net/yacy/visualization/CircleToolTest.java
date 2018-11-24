// CircleToolTest.java
// Copyright 2018 by luccioman; https://github.com/luccioman
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

package net.yacy.visualization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bouncycastle.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

import net.yacy.peers.graphics.EncodedImage;

/**
 * Unit tests for the {@link CircleTool} class.
 */
public class CircleToolTest {

	/**
	 * Check circle function consistency when run on multiple concurrent threads.
	 *
	 * @throws Exception when an unexpected error occurred
	 */
	@Test
	public void testCircleConcurrentConsistency() throws Exception {
		final int concurrency = 20;
		final int side = 600;
		final String ext = "png";

		final Callable<byte[]> drawTask = () -> {
			final RasterPlotter raster = new RasterPlotter(side, side, RasterPlotter.DrawMode.MODE_SUB, "FFFFFF");
			for (int radius = side / 4; radius < side / 2; radius++) {
				raster.setColor(RasterPlotter.GREEN);
				CircleTool.circle(raster, side / 2, side / 2, radius, 100);
				raster.setColor(RasterPlotter.RED);
				CircleTool.circle(raster, side / 2, side / 2, radius, 0, 45);
			}

			final EncodedImage image = new EncodedImage(raster, ext, true);
			return image.getImage().getBytes();
		};

		/* Generate a reference image without concurrency */
		CircleTool.clearcache();
		final byte[] refImageBytes = drawTask.call();

		/* Write the reference image to the file system to enable manual visual check */
		final Path outputPath = Paths.get(System.getProperty("java.io.tmpdir", ""), "CircleToolTest." + ext);
		try {
			Files.write(outputPath, refImageBytes);
			System.out.println("Wrote CircleTool.circle() test image to file " + outputPath.toAbsolutePath());
		} catch (final IOException e) {
			/*
			 * Even if output file writing failed we do not make the test fail as this is
			 * not the purpose of the test
			 */
			e.printStackTrace();
		}

		/*
		 * Generate the same image multiple times in concurrent threads without initial
		 * cache
		 */
		CircleTool.clearcache();
		final ExecutorService executor = Executors.newFixedThreadPool(concurrency);
		final ArrayList<Future<byte[]>> futures = new ArrayList<>();
		for (int i = 0; i < concurrency; i++) {
			futures.add(executor.submit(drawTask));
		}
		try {
			for (final Future<byte[]> future : futures) {
				/* Check that all concurrently generated images are equal to the reference */
				final byte[] imageBytes = future.get();
				if (!Arrays.areEqual(refImageBytes, imageBytes)) {
					/* Write the image in error to file system to enable manual visual check */
					final Path errOutputPath = Paths.get(System.getProperty("java.io.tmpdir", ""),
							"CircleToolTestError." + ext);
					try {
						Files.write(errOutputPath, imageBytes);
						System.out.println(
								"Wrote CircleTool.circle() error image to file " + errOutputPath.toAbsolutePath());
					} catch (final IOException e) {
						e.printStackTrace();
					}
					Assert.fail();
				}
			}
		} finally {
			executor.shutdown();
		}
	}

}
