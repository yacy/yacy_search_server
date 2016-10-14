// ImageViewerPerfTest.java
// -----------------------
// part of YaCy
// Copyright 2016 by luccioman; https://github.com/luccioman
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.server.serverObjects;



/**
 * Test to measure image render performance by 
 * 
 * @author luc
 *
 */
public class ImageViewerPerfTest extends ImageViewerTest {

	/** Default minimum measurement time */
	private static final int DEFAULT_MIN_MEASURE_TIME = 10;

	/** Minimum measurement time */
	private int minMeasureTime;

	/**
	 * @param args
	 *            main parameters : args[7] may contain minimum measurement time
	 *            in secondes. Default : 10.
	 */
	public ImageViewerPerfTest(String args[]) {
		this.minMeasureTime = getMinMeasurementTime(args);
	}

	/**
	 * 
	 * @param args
	 *            main parameters : args[7] may contain minimum measurement time
	 *            in secondes. Default : 10.
	 * @return extension to use for encoding
	 */
	protected int getMinMeasurementTime(String args[]) {
		int time;
		if (args != null && args.length > 7) {
			time = Integer.parseInt(args[7]);
		} else {
			time = DEFAULT_MIN_MEASURE_TIME;
		}
		return time;
	}

	/**
	 * Process inFile image, update processedFiles list and failures map, and
	 * append measurements to results_perfs.txt. All parameters must not be
	 * null.
	 * 
	 * @param ext
	 *            output encoding image format
	 * @param outDir
	 *            output directory
	 * @param post
	 *             post parameters
	 * @param failures
	 *            map failed file urls to eventual exception
	 * @param inFile
	 *            file image to process
	 * @throws IOException
	 *             when an read/write error occured
	 */
	@Override
	protected void processFile(String ext, File outDir, serverObjects post, Map<String, Throwable> failures,
			File inFile) throws IOException {
		/* Delete eventual previous result file */
		System.out
				.println("Measuring  render with file : " + inFile.getAbsolutePath() + " encoded To : " + ext);
		File outFile = new File(outDir, inFile.getName() + "." + ext);
		if (outFile.exists()) {
			outFile.delete();
		}

		String urlString = inFile.getAbsolutePath();
		EncodedImage img = null;
		Exception error = null;
		long beginTime = System.nanoTime(), time, minTime = Long.MAX_VALUE, maxTime = 0, meanTime = 0, totalTime = 0;
		int step = 0;
		for (step = 0; (totalTime / 1000000000) < this.minMeasureTime; step++) {
			beginTime = System.nanoTime();
			ImageInputStream inStream = ImageIO.createImageInputStream(inFile);
			try {
				img = this.VIEWER.parseAndScale(post, true, new DigestURL(urlString), ext, inStream);
			} catch (Exception e) {
				error = e;
			}
			time = System.nanoTime() - beginTime;
			minTime = Math.min(minTime, time);
			maxTime = Math.max(maxTime, time);
			totalTime += time;
		}
		if (step > 0) {
			meanTime = totalTime / step;
		} else {
			meanTime = totalTime;
		}
		PrintWriter resultsWriter = new PrintWriter(new FileWriter(new File(outDir, "results_perfs.txt"), true));
		try {
			writeMessage("Measured  render with file : " + inFile.getAbsolutePath() + " encoded To : " + ext,
					resultsWriter);
			if(img == null) {
				writeMessage("Image could not be rendered! Measurement show time needed to read and parse image data until error detection.", resultsWriter);
			}
			writeMessage("Render total time (ms) : " + (totalTime) / 1000000 + " on " + step + " steps.",
					resultsWriter);
			writeMessage("Render mean time (ms) : " + (meanTime) / 1000000, resultsWriter);
			writeMessage("Render min time (ms) : " + (minTime) / 1000000, resultsWriter);
			writeMessage("Render max time (ms) : " + (maxTime) / 1000000, resultsWriter);
		} finally {
			resultsWriter.close();
		}

		if (img == null) {
			failures.put(urlString, error);
		} else {
			FileOutputStream outFileStream = null;
			try {
				outFileStream = new FileOutputStream(outFile);
				img.getImage().writeTo(outFileStream);
			} finally {
				if (outFileStream != null) {
					outFileStream.close();
				}
				img.getImage().close();
			}
		}
	}

	/**
	 * Test image(s) (default : classpath resource folder /viewImageTest/test/)
	 * are parsed and rendered again and again until specified time (default :
	 * 10 seconds) elapsed. Then rendered image is written to outDir for visual
	 * check and measured statistics are displayed.
	 * 
	 * @param args
	 *            may be empty or contain parameters to override defaults :
	 *            <ul>
	 *            <li>args[0] : input image file URL or folder containing image
	 *            files URL. Default : classpath resource /viewImageTest/test/
	 *            </li>
	 *            <li>args[1] : output format name (for example : "jpg") for
	 *            rendered image. Defaut : "png".</li>
	 *            <li>args[2] : ouput folder URL. Default :
	 *            "[system tmp dir]/Test".</li>
	 *            <li>args[3] : max width (in pixels) for rendered image. May be
	 *            set to zero to specify no max width. Default : no value.</li>
	 *            <li>args[4] : max height (in pixels) for rendered image. May
	 *            be set to zero to specify no max height. Default : no value.
	 *            </li>
	 *            <li>args[5] : set to "quadratic" to render square output
	 *            image. May be set to any string to specify no quadratic shape.
	 *            Default : false.</li>
	 *            <li>args[6] : set to "recursive" to process recursively sub
	 *            folders. Default : false.</li>
	 *            <li>args[7] : minimum measurement time in secondes. Default :
	 *            10.</li>
	 *            </ul>
	 * @throws IOException
	 *             when a read/write error occured
	 */
	public static void main(String args[]) throws IOException {
		ImageViewerPerfTest test = new ImageViewerPerfTest(args);
		File inFile = test.getInputURL(args);
		String ext = test.getEncodingExt(args);
		File outDir = test.getOuputDir(args);
		boolean recursive = test.isRecursive(args);
		serverObjects post = test.makePostParams(args);
		outDir.mkdirs();

		File[] inFiles;
		if (inFile.isFile()) {
			inFiles = new File[1];
			inFiles[0] = inFile;
			System.out.println(
					"Measuring  render with file : " + inFile.getAbsolutePath() + " encoded To : " + ext);
		} else if (inFile.isDirectory()) {
			inFiles = inFile.listFiles();
			System.out.println("Measuring  render with files in folder : " + inFile.getAbsolutePath()
					+ " encoded To : " + ext);
		} else {
			inFiles = new File[0];
		}
		if (inFiles.length == 0) {
			throw new IllegalArgumentException(inFile.getAbsolutePath() + " is not a valid file or folder url.");
		}

		System.out.println("Rendered images will be written in dir : " + outDir.getAbsolutePath());

		List<File> processedFiles = new ArrayList<File>();
		Map<String, Throwable> failures = new TreeMap<>();
		try {
			long time = System.nanoTime();
			test.processFiles(ext, recursive, outDir, post, inFiles, processedFiles, failures);
			time = System.nanoTime() - time;
			test.displayResults(processedFiles, failures, time, outDir);
		} finally {
			ConcurrentLog.shutdown();
		}

	}

}
