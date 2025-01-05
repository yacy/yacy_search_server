
// ViewFavicon.java
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

package net.yacy.htroot;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.ImageViewer;

/**
 * Extends ViewImage behavior : add a specific favicon cache and use of a
 * default image on loading error.
 *
 * @author luc
 *
 */
public class ViewFavicon {

	/** Single instance of ImageViewer */
	private static final ImageViewer VIEWER = new ImageViewer();

	/** Icons cache encoded as png */
	private static Map<String, byte[]> pngIconCache = new ConcurrentARC<String, byte[]>(1000,
			Math.max(10, Math.min(32, WorkflowProcessor.availableCPU * 2)));

	/** Default icon local file */
	private static final String defaulticon = "htroot/env/grafics/dfltfvcn.ico";

	/**
	 * Default icon encoded as png : we use a bvte array as it is thread-safe
	 * instead of a ByteBuffer in EncodedImage
	 */
	private static byte[] defaultPNGEncodedIcon = null;

	/**
	 * Try parsing image from post "url" parameter (authenticated users) or from
	 * "code" parameter (non authenticated users). When image could be parsed,
	 * try encoding to target format specified by header "EXT". When any error
	 * occurs, return default icon.
	 *
	 * @param header
	 *            request header
	 * @param post
	 *            post parameters
	 * @param env
	 *            Switchboard instance
	 * @return an {@link EncodedImage} instance encoded in format specified in
	 *         post, or an InputStream pointing to original image data.
	 */
	public static Object respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

		final Switchboard sb = (Switchboard) env;
		final String ext = header.get(HeaderFramework.CONNECTION_PROP_EXT, null);
		final boolean isPNGTarget = "png".equalsIgnoreCase(ext);

		ImageInputStream imageInStream = null;
		InputStream inStream = null;
		byte[] resultBytes = null;
		try {
			/* Clear icon cache when running out of memory */
			if (MemoryControl.shortStatus()) {
				pngIconCache.clear();
			}

			final boolean auth = ImageViewer.hasFullViewingRights(header, sb); // handle access rights

			final DigestURL url = VIEWER.parseURL(post, auth);

			final String normalizedURL = url.toNormalform(false);

			if (isPNGTarget) {
				resultBytes = pngIconCache.get(normalizedURL);
			}
			/* Icon is not already in cache */
			if (resultBytes == null) {
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
				final EncodedImage encodedIcon = VIEWER.parseAndScale(post, auth, url, ext, imageInStream);

				if (encodedIcon != null && !encodedIcon.getImage().isEmpty()) {
					resultBytes = encodedIcon.getImage().getBytes();
					if (isPNGTarget && encodedIcon.getImage().length() <= 10240) {
						/* Only store in cache icon images below 10KB, png encoded */
						pngIconCache.put(normalizedURL, resultBytes);
					}
				}
			}
		} catch (final IOException e) {
			ConcurrentLog.fine("ViewFavicon", "Error loading favicon, default one wille be used : " + e);
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
			if (resultBytes == null) {
				/*
				 * I any error occured when loading icon, return default one
				 */
				if (ext == null || isPNGTarget) {
					/* Load default icon only once */
					if (defaultPNGEncodedIcon == null) {
						defaultPNGEncodedIcon = loadDefaultIcon(post, sb, ext);
					}
					resultBytes = defaultPNGEncodedIcon;
				} else {
					resultBytes = loadDefaultIcon(post, sb, ext);
				}
			}

		}

		return new ByteArrayInputStream(resultBytes);
	}

	/**
	 * Load default icon and encode it to ext format
	 *
	 * @param post
	 *            post parameters
	 * @param sb
	 *            Switchboard instance
	 * @param ext
	 *            target image format
	 * @return icon encoded bytes, empty if and exception occured when loading
	 *         or rendering
	 */
	private static byte[] loadDefaultIcon(final serverObjects post, final Switchboard sb, final String ext) {
		byte[] resultBytes;
		byte[] defaultBytes = new byte[0];
		try {
			defaultBytes = FileUtils.read(new File(sb.getAppPath(), defaulticon));
		} catch (final IOException initicon) {
			defaultBytes = new byte[0];
		} finally {
			resultBytes = new EncodedImage(defaultBytes, ext, post.getBoolean("isStatic")).getImage().getBytes();
		}
		return resultBytes;
	}

}
