import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.filefilter.FileFileFilter;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.server.serverObjects;

// ViewImageTest.java
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
 * Test rendering of one or more image files by ViewImage
 * 
 * @author luc
 *
 */
public class ViewImageTest {

	/** Default image */
	private static final String DEFAULT_IMG_RESOURCES = "/viewImageTest/test";

	/** Default output encoding format */
	private static final String DEFAULT_OUT_EXT = "png";

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
	 *            main parameters. first item may contain input file or folder
	 *            URL
	 * @return file or folder to be used : specified as first in args or default
	 *         one
	 */
	private static File getInputURL(String args[]) {
		String fileURL;
		if (args != null && args.length > 0) {
			fileURL = args[0];
		} else {
			URL defaultURL = ViewImageTest.class.getResource(DEFAULT_IMG_RESOURCES);
			if (defaultURL == null) {
				throw new IllegalArgumentException("File not found : " + DEFAULT_IMG_RESOURCES);
			}
			fileURL = defaultURL.getFile();
		}
		return new File(fileURL);
	}

	/**
	 * @param args
	 *            main parameters. args[2] main contains directory url for
	 *            rendered images files
	 * @return output directory to use
	 * @throws IllegalArgumentException
	 *             when args[2] is not set and default is not found
	 */
	private static File getOuputDir(String[] args) {
		File outDir;
		if (args.length > 2) {
			outDir = new File(args[2]);
		} else {
			String tmpDir = System.getProperty("java.io.tmpdir");
			if (tmpDir == null) {
				throw new IllegalArgumentException("No destination dir specified, and default not found");
			}
			outDir = new File(tmpDir + File.separator + ViewImageTest.class.getCanonicalName());
		}
		return outDir;
	}

	/**
	 * Build post parameters to use with ViewImage
	 * 
	 * @param args
	 *            main parameters : args[3] and args[4] may respectively contain
	 *            max width and max height
	 * @return a serverObjects instance
	 */
	private static serverObjects makePostParams(String args[]) {
		serverObjects post = new serverObjects();
		if (args != null && args.length > 3) {
			int maxWidth = Integer.parseInt(args[3]);
			post.put("maxwidth", String.valueOf(maxWidth));
		}

		if (args != null && args.length > 4) {
			int maxHeight = Integer.parseInt(args[4]);
			post.put("maxheight", String.valueOf(maxHeight));
		}

		return post;
	}

	/**
	 * 
	 * @param args
	 *            main parameters : fourth item may contain extension
	 * @return extension to use for encoding
	 */
	private static String getEncodingExt(String args[]) {
		String ext = DEFAULT_OUT_EXT;
		if (args != null && args.length > 3) {
			ext = args[3];
		}
		return ext;
	}

	/**
	 * Display detailed results. All parametrers required not to be null.
	 * 
	 * @param inFiles
	 *            input image files
	 * @param failures
	 *            map input file url which failed with eventual cause exception
	 */
	private static void displayResults(File[] inFiles, Map<String, Exception> failures) {
		if (failures.size() > 0) {
			if (failures.size() == inFiles.length) {
				System.out.println("No input files could be processed :");
			} else {
				System.out.println("Some input files could not be processed :");
			}
			for (Entry<String, Exception> entry : failures.entrySet()) {
				System.out.println(entry.getKey());
				if (entry.getValue() != null) {
					System.out.println("cause : " + entry.getValue());
				}
			}
		} else {
			System.out.println("All input files were successfully processed.");
		}
	}

	/**
	 * Test image(s) (default : JPEG_example_JPG_RIP_100.jpg) are parsed and
	 * rendered to an output foler. Result can then be checked with program of
	 * your choice.
	 * 
	 * @param args
	 *            may be empty or contain parameters to override defaults :
	 *            <ul>
	 *            <li>args[0] : input image file URL or folder containing image
	 *            files URL. Default :
	 *            viewImageTest/test/JPEG_example_JPG_RIP_100.jpg</li>
	 *            <li>args[1] : output format name (for example : "jpg") for
	 *            rendered image</li>
	 *            <li>args[2] : ouput folder URL</li>
	 *            <li>args[3] : max width (in pixels) for rendered image.
	 *            Default : no value.</li>
	 *            <li>args[4] : max height (in pixels) for rendered image.
	 *            Default : no value.</li>
	 *            </ul>
	 * @throws IOException
	 *             when a read/write error occured
	 */
	public static void main(String args[]) throws IOException {
		File inURL = getInputURL(args);
		String ext = getEncodingExt(args);
		File outDir = getOuputDir(args);
		serverObjects post = makePostParams(args);
		outDir.mkdirs();

		File[] inFiles;
		if (inURL.isFile()) {
			inFiles = new File[1];
			inFiles[0] = inURL;
			System.out.println("Testing ViewImage rendering with input file : " + inURL.getAbsolutePath()
					+ " encoded To : " + ext);
		} else if (inURL.isDirectory()) {
			FileFilter filter = FileFileFilter.FILE;
			inFiles = inURL.listFiles(filter);
			System.out.println("Testing ViewImage rendering with input files in folder : " + inURL.getAbsolutePath()
					+ " encoded To : " + ext);
		} else {
			inFiles = new File[0];
		}
		if (inFiles.length == 0) {
			throw new IllegalArgumentException(inURL.getAbsolutePath() + " is not a valid file or folder url.");
		}
		System.out.println("Rendered images will be written in dir : " + outDir.getAbsolutePath());

		Map<String, Exception> failures = new HashMap<String, Exception>();
		try {
			for (File inFile : inFiles) {
				/* Delete eventual previous result file */
				File outFile = new File(outDir, inFile.getName() + "." + ext);
				if (outFile.exists()) {
					outFile.delete();
				}

				byte[] resourceb = getBytes(inFile);
				String urlString = inFile.getAbsolutePath();
				EncodedImage img = null;
				Exception error = null;
				try {
					img = ViewImage.parseAndScale(post, true, urlString, ext, false, resourceb);
				} catch (Exception e) {
					error = e;
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
			displayResults(inFiles, failures);
		} finally {
			ConcurrentLog.shutdown();
		}

	}

}
