
// ViewImage.java
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

package net.yacy.htroot;

import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.http.servlets.TemplateMissingParameterException;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.ImageViewer;

public class ViewImage {

	/** Single instance of ImageViewer */
	private static final ImageViewer VIEWER = new ImageViewer();

	/**
	 * Try parsing image from post "url" parameter (authenticated users) or from "code" parameter (non authenticated users).
	 * When image format is not supported, return directly image data. When
	 * image could be parsed, try encoding to target format specified by header
	 * "EXT".
	 *
	 * @param header
	 *            request header
	 * @param post
	 *            post parameters
	 * @param env
	 *            environment
	 * @return an {@link EncodedImage} instance encoded in format specified in
	 *         post, or an InputStream pointing to original image data.
	 *         Return and EncodedImage with empty data when image format is not supported,
	 *         a read/write or any other error occured while loading resource.
	 * @throws IOException
	 *             when specified url is malformed.
	 *             Sould end in a HTTP 500 error whose processing is more
	 *             consistent across browsers than a response with zero content
	 *             bytes.
	 * @throws TemplateMissingParameterException when one required parameter is missing
	 */
	public static Object respond(final RequestHeader header, final serverObjects post, final serverSwitch env)
			throws IOException {

		final Switchboard sb = (Switchboard) env;

		if(post == null) {
			throw new TemplateMissingParameterException("please fill at least url or code parameter");
		}

		final String ext = header.get(HeaderFramework.CONNECTION_PROP_EXT, null);
		final boolean auth = ImageViewer.hasFullViewingRights(header, sb); // handle access rights

		final DigestURL url = VIEWER.parseURL(post, auth);

		// get the image as stream
		EncodedImage encodedImage;

		ImageInputStream imageInStream = null;
		InputStream inStream = null;
		try {
			final String urlExt = MultiProtocolURL.getFileExtension(url.getFileName());
			if (ext != null && ext.equalsIgnoreCase(urlExt) && ImageViewer.isBrowserRendered(urlExt)) {
				return VIEWER.openInputStream(post, sb.loader, auth, url);
			}
			/*
			 * When opening a file, the most efficient is to open
			 * ImageInputStream directly on file
			 */
			if (url.isFile()) {
				imageInStream = ImageIO.createImageInputStream(url.getFSFile());
			} else {
				inStream = VIEWER.openInputStream(post, sb.loader, auth, url);
				imageInStream = ImageIO.createImageInputStream(inStream);
			}
			// read image
			encodedImage = VIEWER.parseAndScale(post, auth, url, ext, imageInStream);
		} catch (final Exception e) {
			/*
			 * Exceptions are not propagated here : many error causes are
			 * possible, network errors, incorrect or unsupported format, bad
			 * ImageIO plugin... Instead return an empty EncodedImage. Caller is
			 * responsible for handling this correctly (500 status code
			 * response)
			 */
			encodedImage = new EncodedImage(new byte[0], ext, post.getBoolean("isStatic"));
		} finally {
			/*
			 * imageInStream.close() method doesn't close source input stream
			 */
			if (inStream != null) {
				try {
					inStream.close();
				} catch (final IOException ignored) {
				}
			}
		}

		return encodedImage;
	}



}
