import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.server.serverObjects;

// ViewImagePerfTest.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// created 03.04.2006
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

/**
 * Test to measure image render performance by ViewImage
 * 
 * @author luc
 *
 */
public class ViewImagePerfTest {

	/** Default image */
	private static final String DEFAULT_IMG_RESOURCE = "/viewImageTest/test/JPEG_example_JPG_RIP_100.jpg";

	/** Default render max width (JPEG_example_JPG_RIP_100.jpg width / 10) */
	private static final int DEFAULT_MAX_WIDTH = 31;

	/** Default render max height (JPEG_example_JPG_RIP_100.jpg height / 10) */
	private static final int DEFAULT_MAX_HEIGHT = 23;

	/** Default encoding format */
	private static final String DEFAUL_EXT = "png";

	/**
	 * @param testFile
	 *            file to load
	 * @return testFile content as a bytes array
	 * @throws IOException
	 *             when an error occured while loading
	 */
	private static byte[] getBytes(File testFile) throws IOException {
		InputStream inStream = new FileInputStream(testFile);
		byte[] res = new byte[inStream.available()];
		try {
			inStream.read(res);
		} finally {
			inStream.close();
		}
		return res;
	}

	/**
	 * @param args
	 *            first item may contain file URL
	 * @return file to be used : specified as first in args or default one
	 */
	private static File getTestFile(String args[]) {
		String fileURL;
		if (args != null && args.length > 0) {
			fileURL = args[0];
		} else {
			URL defaultURL = ViewImagePerfTest.class.getResource(DEFAULT_IMG_RESOURCE);
			if (defaultURL == null) {
				throw new IllegalArgumentException("File not found : " + DEFAULT_IMG_RESOURCE);
			}
			fileURL = defaultURL.getFile();
		}
		return new File(fileURL);
	}

	/**
	 * Build post parameters to use with ViewImage
	 * 
	 * @param args
	 *            main parameters : second and third items may respectively
	 *            contain max width and max height
	 * @return a serverObjects instance
	 */
	private static serverObjects makePostParams(String args[]) {
		serverObjects post = new serverObjects();
		int maxWidth = DEFAULT_MAX_WIDTH;
		if (args != null && args.length > 1) {
			maxWidth = Integer.parseInt(args[1]);
		}
		post.put("maxwidth", String.valueOf(maxWidth));

		int maxHeight = DEFAULT_MAX_HEIGHT;
		if (args != null && args.length > 2) {
			maxHeight = Integer.parseInt(args[2]);
		}
		post.put("maxheight", String.valueOf(maxHeight));
		/* Make it square by default */
		post.put("quadratic", "");
		return post;
	}

	/**
	 * 
	 * @param args
	 *            main parameters : fourth item may contain extension
	 * @return extension to use for encoding
	 */
	private static String getEncodingExt(String args[]) {
		String ext = DEFAUL_EXT;
		if (args != null && args.length > 3) {
			ext = args[3];
		}
		return ext;
	}

	/**
	 * Test image is parsed and rendered again and again until 20 seconds
	 * elapsed. Then measured statistics are displayed.
	 * 
	 * @param args
	 *            may be empty or contain parameters to override defaults :
	 *            <ul>
	 *            <li>args[0] : input image file URL. Default :
	 *            viewImageTest/test/JPEG_example_JPG_RIP_100.jpg</li>
	 *            <li>args[1] : max width (in pixels) for rendered image.
	 *            Default : default image width divided by 10.</li>
	 *            <li>args[2] : max height (in pixels) for rendered image.
	 *            Default : default image height divided by 10.</li>
	 *            <li>args[3] : output format name. Default : "png".</li>
	 *            </ul>
	 * @throws IOException
	 *             when a read/write error occured
	 */
	public static void main(String args[]) throws IOException {
		File imgFile = getTestFile(args);
		byte[] resourceb = getBytes(imgFile);
		String ext = getEncodingExt(args);
		serverObjects post = makePostParams(args);

		String urlString = imgFile.getAbsolutePath();

		System.out.println("Measuring ViewImage render with file : " + urlString + " encoded To : " + ext);
		try {
			/* Max test total time (s) */
			int maxTotalTime = 20;
			long beginTime, time, minTime = Long.MAX_VALUE, maxTime = 0, meanTime = 0, totalTime = 0;
			int step = 0;
			for (step = 0; (totalTime / 1000000000) < maxTotalTime; step++) {
				beginTime = System.nanoTime();
				EncodedImage img = ViewImage.parseAndScale(post, true, urlString, ext, false, resourceb);
				time = System.nanoTime() - beginTime;
				minTime = Math.min(minTime, time);
				maxTime = Math.max(maxTime, time);
				totalTime += time;
				if (img == null) {
					throw new IOException("Image render failed");
				}
			}
			meanTime = totalTime / step;
			System.out.println("Render total time (ms) : " + (totalTime) / 1000000 + " on " + step + " steps.");
			System.out.println("Render mean time (ms) : " + (meanTime) / 1000000);
			System.out.println("Render min time (ms) : " + (minTime) / 1000000);
			System.out.println("Render max time (ms) : " + (maxTime) / 1000000);
		} finally {
			ConcurrentLog.shutdown();
		}

	}

}
