import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

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
	protected byte[] getBytes(File testFile) throws IOException {
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
	protected File getInputURL(String args[]) {
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
	protected File getOuputDir(String[] args) {
		File outDir;
		if (args.length > 2) {
			outDir = new File(args[2]);
		} else {
			String tmpDir = System.getProperty("java.io.tmpdir");
			if (tmpDir == null) {
				throw new IllegalArgumentException("No destination dir specified, and default not found");
			}
			outDir = new File(tmpDir + File.separator + this.getClass().getCanonicalName());
		}
		return outDir;
	}

	/**
	 * Build post parameters to use with ViewImage
	 * 
	 * @param args
	 *            main parameters : args[3] and args[4] may respectively contain
	 *            max width and max height. Set it to zero so there is no max
	 *            width and no max height when processing. args[5] may be set to
	 *            "quadratic" to render output images as squares.
	 * @return a serverObjects instance
	 */
	protected serverObjects makePostParams(String args[]) {
		serverObjects post = new serverObjects();
		if (args != null && args.length > 3) {
			int maxWidth = Integer.parseInt(args[3]);
			post.put("maxwidth", String.valueOf(maxWidth));
		}

		if (args != null && args.length > 4) {
			int maxHeight = Integer.parseInt(args[4]);
			post.put("maxheight", String.valueOf(maxHeight));
		}

		boolean quadratic = isQuadratic(args);
		if (quadratic) {
			post.put("quadratic", "");
		}

		return post;
	}

	/**
	 * 
	 * @param args
	 *            main parameters : second item may contain extension
	 * @return extension to use for encoding
	 */
	protected String getEncodingExt(String args[]) {
		String ext = DEFAULT_OUT_EXT;
		if (args != null && args.length > 1) {
			ext = args[1];
		}
		return ext;
	}

	/**
	 * 
	 * @param args
	 *            main parameters. args[5] may be set to "quadratic"
	 * @return true when image are supposed to be rendered as squares.
	 */
	protected boolean isQuadratic(String args[]) {
		boolean recursive = false;
		if (args != null && args.length > 5) {
			recursive = "quadratic".equals(args[5]);
		}
		return recursive;
	}

	/**
	 * 
	 * @param args
	 *            main parameters. args[6] may be set to "recursive"
	 * @return true when folders are supposed to processed recursively
	 */
	protected boolean isRecursive(String args[]) {
		boolean recursive = false;
		if (args != null && args.length > 6) {
			recursive = "recursive".equals(args[6]);
		}
		return recursive;
	}

	/**
	 * Write same message to both system standard output and to outWriter.
	 * 
	 * @param message
	 *            message to write
	 * @param outWriter
	 *            PrintWriter writer. Must not be null.
	 * @throws IOException
	 *             in case of write error
	 */
	protected void writeMessage(String message, PrintWriter outWriter) throws IOException {
		System.out.println(message);
		outWriter.println(message);
	}

	/**
	 * Display detailed results and produce a results.txt file in outDir. All
	 * parametrers required not to be null.
	 * 
	 * @param processedFiles
	 *            all processed image files
	 * @param failures
	 *            map input file url which failed with eventual cause exception
	 * @param time
	 *            total processing time in nanoseconds
	 * @param outDir
	 *            directory to write results file
	 * @throws IOException
	 *             when a write error occured writing the results file
	 */
	protected void displayResults(List<File> processedFiles, Map<String, Exception> failures, long time, File outDir)
			throws IOException {
		PrintWriter resultsWriter = new PrintWriter(new FileWriter(new File(outDir, "results.txt")));
		try {
			writeMessage(processedFiles.size() + " files processed in " + (time / 1000000) + " ms", resultsWriter);
			if (failures.size() > 0) {
				if (failures.size() == processedFiles.size()) {
					writeMessage("No input files could be processed :", resultsWriter);
				} else {
					writeMessage("Some input files could not be processed :", resultsWriter);
				}
				for (Entry<String, Exception> entry : failures.entrySet()) {
					writeMessage(entry.getKey(), resultsWriter);
					if (entry.getValue() != null) {
						writeMessage("cause : " + entry.getValue(), resultsWriter);
					}
				}
			} else {
				if (processedFiles.size() > 0) {
					writeMessage("All input files were successfully processed.", resultsWriter);
				} else {
					writeMessage("No input file was provided.", resultsWriter);
				}
			}
		} finally {
			resultsWriter.close();
		}
	}

	/**
	 * Process inFiles and update processedFiles list and failures map. All
	 * parameters must not be null.
	 * 
	 * @param ext
	 *            output encoding image format
	 * @param recursive
	 *            when true, also process inFiles directories
	 * @param outDir
	 *            output directory
	 * @param post
	 *            ViewImage post parameters
	 * @param inFiles
	 *            files or directories to process
	 * @param processedFiles
	 *            list of processed files
	 * @param failures
	 *            map failed file urls to eventual exception
	 * @throws IOException
	 *             when an read/write error occured
	 */
	protected void processFiles(String ext, boolean recursive, File outDir, serverObjects post, File[] inFiles,
			List<File> processedFiles, Map<String, Exception> failures) throws IOException {
		for (File inFile : inFiles) {
			if (inFile.isDirectory()) {
				if (recursive) {
					File subDir = new File(outDir, inFile.getName());
					subDir.mkdirs();
					processFiles(ext, recursive, subDir, post, inFile.listFiles(), processedFiles, failures);
				}
			} else {
				processedFiles.add(inFile);
				processFile(ext, outDir, post, failures, inFile);
			}
		}
	}

	/**
	 * Process inFile image and update processedFiles list and failures map. All
	 * parameters must not be null.
	 * @param ext output encoding image format
	 * @param outDir output directory
	 * @param post ViewImage post parameters
	 * @param failures map failed file urls to eventual exception
	 * @param inFile file image to process
	 * @throws IOException when an read/write error occured
	 */
	protected void processFile(String ext, File outDir, serverObjects post, Map<String, Exception> failures, File inFile)
			throws IOException {
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

	/**
	 * Test image(s) (default : classpath resource folder /viewImageTest/test/) are parsed and
	 * rendered to an output foler. Result can then be checked with program of
	 * your choice.
	 * 
	 * @param args
	 *            may be empty or contain parameters to override defaults :
	 *            <ul>
	 *            <li>args[0] : input image file URL or folder containing image
	 *            files URL. Default : classpath resource
	 *            /viewImageTest/test/</li>
	 *            <li>args[1] : output format name (for example : "jpg") for
	 *            rendered image. Defaut : "png".</li>
	 *            <li>args[2] : ouput folder URL. Default :
	 *            "[system tmp dir]/ViewImageTest".</li>
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
	 *            </ul>
	 * @throws IOException
	 *             when a read/write error occured
	 */
	public static void main(String args[]) throws IOException {
		ViewImageTest test = new ViewImageTest();
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
			System.out.println("Testing ViewImage rendering with input file : " + inFile.getAbsolutePath()
					+ " encoded To : " + ext);
		} else if (inFile.isDirectory()) {
			inFiles = inFile.listFiles();
			System.out.println("Testing ViewImage rendering with input files in folder : " + inFile.getAbsolutePath()
					+ " encoded To : " + ext);
		} else {
			inFiles = new File[0];
		}
		if (inFiles.length == 0) {
			throw new IllegalArgumentException(inFile.getAbsolutePath() + " is not a valid file or folder url.");
		}

		System.out.println("Rendered images will be written in dir : " + outDir.getAbsolutePath());

		List<File> processedFiles = new ArrayList<File>();
		Map<String, Exception> failures = new TreeMap<>();
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
