/**
 *  ImageParser
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 29.6.2010 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.yacy.cora.util.ConcurrentLog;

public class ImageParser {

	/**
	 * @param filename source image file url
	 * @param source image content as bytes
	 * @return an Image instance parsed from image content bytes, or null if no parser can handle image format or an error occured
	 */
	public static final Image parse(final String filename, final byte[] source) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(new ByteArrayInputStream(source));
			/*
			 * With ImageIO.read, image is already loaded as a complete BufferedImage, no need to wait
			 * full loading with a MediaTracker
			 */
		} catch (IOException e) {
			if (ConcurrentLog.isFine("IMAGEPARSER")) {
				ConcurrentLog.fine("IMAGEPARSER", "IMAGEPARSER.parse : could not parse image " + filename, e);
			}
		}
		if (image == null) {
			if (ConcurrentLog.isFine("IMAGEPARSER")) {
				ConcurrentLog.fine("IMAGEPARSER", "IMAGEPARSER.parse : ImageIO failed for " + filename);
			}
			return null;
		}

		return image;
	}

}
