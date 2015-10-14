import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import net.yacy.peers.graphics.EncodedImage;
import net.yacy.server.serverObjects;

// ViewImageJPGPerfTest.java
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
 * Test to measure JPG render performance by ViewImage
 * 
 * @author luc
 *
 */
public class ViewImageJPGPerfTest {

	private static final String TEST_IMG_RESOURCE = "/viewImageTest/test/JPEG_example_JPG_RIP_100.jpg";

	private static byte[] getTestImageBytes() throws IOException {
		InputStream inStream = ViewImageJPGPerfTest.class.getResourceAsStream(TEST_IMG_RESOURCE);
		if (inStream == null) {
			throw new FileNotFoundException(TEST_IMG_RESOURCE);
		}
		byte[] res = new byte[inStream.available()];
		try {
			inStream.read(res);
		} finally {
			inStream.close();
		}
		return res;
	}
	// TODO pour info dernier test : 11167ms

	/**
	 * Test image (JPEG_example_JPG_RIP_100.jpg) is scaled, and cropped.
	 * 
	 * @throws IOException
	 */
	public static void main(String args[]) throws IOException {
		byte[] resourceb = getTestImageBytes();
		serverObjects post = new serverObjects();
		/* Scale test image to 1/10 */
		post.put("maxwidth", "31");
		post.put("maxheight", "23");
		/* Make it square */
		post.put("quadratic", "");

		String urlString = ViewImageJPGPerfTest.class.getResource(TEST_IMG_RESOURCE).getFile();

		long beginTime = System.nanoTime();
		for (int step = 0; step < 500; step++) {
			EncodedImage img = ViewImage.parseAndScale(post, true, urlString, "jpg", false, resourceb);
			if (img == null) {
				throw new IOException("Image render failed");
			}
		}
		long endTime = System.nanoTime();
		System.out.println("Render time(ms) : " + (endTime - beginTime) / 1000000);

	}

}
