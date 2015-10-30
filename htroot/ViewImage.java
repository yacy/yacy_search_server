
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

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.storage.ConcurrentARC;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.URLLicense;
import net.yacy.document.ImageParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class ViewImage {

	private static Map<String, Image> iconcache = new ConcurrentARC<String, Image>(1000,
			Math.max(10, Math.min(32, WorkflowProcessor.availableCPU * 2)));
	private static String defaulticon = "htroot/env/grafics/dfltfvcn.ico";
	private static byte[] defaulticonb;

	static {
		try {
			defaulticonb = FileUtils.read(new File(defaulticon));
		} catch (final IOException e) {
		}
	}

	public static Object respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

		final Switchboard sb = (Switchboard) env;

		// the url to the image can be either submitted with an url in clear
		// text, or using a license key
		// if the url is given as clear text, the user must be authorized as
		// admin
		// the license can be used also from non-authorized users

		String urlString = post.get("url", "");
		final String urlLicense = post.get("code", "");
		String ext = header.get("EXT", null);
		final boolean auth = Domains.isLocalhost(header.get(HeaderFramework.CONNECTION_PROP_CLIENTIP, ""))
				|| sb.verifyAuthentication(header); // handle access rights

		DigestURL url = null;
		if ((urlString.length() > 0) && (auth))
			try {
				url = new DigestURL(urlString);
			} catch (final MalformedURLException e1) {
				url = null;
			}

		if ((url == null) && (urlLicense.length() > 0)) {
			urlString = URLLicense.releaseLicense(urlLicense);
			try {
				url = new DigestURL(urlString);
			} catch (final MalformedURLException e1) {
				url = null;
				urlString = null;
			}
		}

		if (urlString == null) {
			return null;
		}

		// get the image as stream
		if (MemoryControl.shortStatus()) {
			iconcache.clear();
		}
		EncodedImage encodedImage = null;
		Image image = iconcache.get(urlString);
		if (image != null) {
			encodedImage = new EncodedImage(image, ext, post.getBoolean("isStatic"));
		} else {
			byte[] resourceb = null;
			if (url != null)
				try {
					String agentName = post.get("agentName", auth ? ClientIdentification.yacyIntranetCrawlerAgentName
							: ClientIdentification.yacyInternetCrawlerAgentName);
					ClientIdentification.Agent agent = ClientIdentification.getAgent(agentName);
					resourceb = sb.loader.loadContent(sb.loader.request(url, false, true), CacheStrategy.IFEXIST,
							BlacklistType.SEARCH, agent);
				} catch (final IOException e) {
					ConcurrentLog.fine("ViewImage", "cannot load: " + e.getMessage());
				}
			boolean okToCache = true;
			if (resourceb == null) {
				if (urlString.endsWith(".ico")) {
					// load default favicon dfltfvcn.ico
					// Should not do this here : we can be displaying search
					// image result of '.ico' type and do not want to display a
					// default
					if (defaulticonb == null)
						try {
							resourceb = FileUtils.read(new File(sb.getAppPath(), defaulticon));
							okToCache = false;
						} catch (final IOException e) {
							return null;
						}
					else {
						resourceb = defaulticonb;
						okToCache = false;
					}
				} else {
					return null;
				}
			}

			String urlExt = MultiProtocolURL.getFileExtension(url.getFileName());
			if (ext != null && ext.equalsIgnoreCase(urlExt) && isBrowserRendered(urlExt)) {
				return new ByteArrayInputStream(resourceb);
			}

			// read image
			encodedImage = parseAndScale(post, auth, urlString, ext, okToCache, resourceb);
		}

		return encodedImage;
	}
	
	/**
	 * @param formatName
	 *            informal file format name. For example : "png".
	 * @return true when image format is rendered by browser and not by
	 *         ViewImage internals
	 */
	public static boolean isBrowserRendered(String formatName) {
		/*
		 * gif images are not loaded because of an animated gif bug within jvm
		 * which sends java into an endless loop with high CPU
		 */
		/*
		 * svg images not supported by jdk, but by most browser, deliver just
		 * content (without crop/scale)
		 */
		return ("gif".equalsIgnoreCase(formatName) || "svg".equalsIgnoreCase(formatName));
	}

	/**
	 * Process resourceb byte array to try to produce an Image instance
	 * eventually scaled and cropped depending on post parameters
	 * 
	 * @param post
	 *            request post parameters. Must not be null.
	 * @param auth
	 *            true when access rigths are OK.
	 * @param urlString
	 *            image source URL. Must not be null.
	 * @param ext
	 *            image file extension. May be null.
	 * @param okToCache
	 *            true when image can be cached
	 * @param resourceb
	 *            byte array. Must not be null.
	 * @return an Image instance when parsing is OK, or null.
	 */
	protected static EncodedImage parseAndScale(serverObjects post, boolean auth, String urlString, String ext,
			boolean okToCache, byte[] resourceb) {
		EncodedImage encodedImage = null;

		Image image = ImageParser.parse(urlString, resourceb);

		if (image != null) {
			int maxwidth = post.getInt("maxwidth", 0);
			int maxheight = post.getInt("maxheight", 0);
			final boolean quadratic = post.containsKey("quadratic");
			boolean isStatic = post.getBoolean("isStatic");
			if (!auth || maxwidth != 0 || maxheight != 0) {

				// find original size
				int h = image.getHeight(null);
				int w = image.getWidth(null);

				// in case of not-authorized access shrink the image to
				// prevent
				// copyright problems, so that images are not larger than
				// thumbnails
				Dimension maxDimensions = calculateMaxDimensions(auth, w, h, maxwidth, maxheight);

				// if a quadratic flag is set, we cut the image out to be in
				// quadratic shape
				if (quadratic && w != h) {
					image = makeSquare(image, h, w);
					h = image.getHeight(null);
					w = image.getWidth(null);
				}

				Dimension finalDimensions = calculateDimensions(w, h, maxDimensions);

				if (w != finalDimensions.width && h != finalDimensions.height) {
					image = scale(finalDimensions.width, finalDimensions.height, image);

				}

				if ((finalDimensions.width == 16) && (finalDimensions.height == 16) && okToCache) {
					// this might be a favicon, store image to cache for
					// faster
					// re-load later on
					iconcache.put(urlString, image);
				}
			}
			encodedImage = new EncodedImage(image, ext, isStatic);
		}
		return encodedImage;
	}

	/**
	 * Calculate image dimensions from image original dimensions, max
	 * dimensions, and target dimensions.
	 * 
	 * @return dimensions to render image
	 */
	protected static Dimension calculateDimensions(final int originWidth, final int originHeight, final Dimension max) {
		int resultWidth;
		int resultHeight;
		if (max.width < originWidth || max.height < originHeight) {
			// scale image
			final double hs = (originWidth <= max.width) ? 1.0 : ((double) max.width) / ((double) originWidth);
			final double vs = (originHeight <= max.height) ? 1.0 : ((double) max.height) / ((double) originHeight);
			final double scale = Math.min(hs, vs);
			// if (!auth) scale = Math.min(scale, 0.6); // this is for copyright
			// purpose
			if (scale < 1.0) {
				resultWidth = Math.max(1, (int) (originWidth * scale));
				resultHeight = Math.max(1, (int) (originHeight * scale));
			} else {
				resultWidth = Math.max(1, originWidth);
				resultHeight = Math.max(1, originHeight);
			}

		} else {
			// do not scale
			resultWidth = originWidth;
			resultHeight = originHeight;
		}
		return new Dimension(resultWidth, resultHeight);
	}

	/**
	 * Calculate image maximum dimentions from original and specified maximum
	 * dimensions
	 * 
	 * @param auth
	 *            true when acces rigths are OK.
	 * @return maximum dimensions to render image
	 */
	protected static Dimension calculateMaxDimensions(final boolean auth, final int originWidth, final int originHeight,
			final int maxWidth, final int maxHeight) {
		int resultWidth;
		int resultHeight;
		// in case of not-authorized access shrink the image to prevent
		// copyright problems, so that images are not larger than thumbnails
		if (auth) {
			resultWidth = (maxWidth == 0) ? originWidth : maxWidth;
			resultHeight = (maxHeight == 0) ? originHeight : maxHeight;
		} else if ((originWidth > 16) || (originHeight > 16)) {
			resultWidth = Math.min(96, originWidth);
			resultHeight = Math.min(96, originHeight);
		} else {
			resultWidth = 16;
			resultHeight = 16;
		}
		return new Dimension(resultWidth, resultHeight);
	}

	/**
	 * Scale image to specified dimensions
	 * 
	 * @param width
	 *            target width
	 * @param height
	 *            target height
	 * @param image
	 *            image to scale. Must not be null.
	 * @return a scaled image
	 */
	protected static Image scale(final int width, final int height, Image image) {
		// compute scaled image
		final Image scaled = image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
		final MediaTracker mediaTracker = new MediaTracker(new Container());
		mediaTracker.addImage(scaled, 0);
		try {
			mediaTracker.waitForID(0);
		} catch (final InterruptedException e) {
		}

		// make a BufferedImage out of that
		final BufferedImage i = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		try {
			i.createGraphics().drawImage(scaled, 0, 0, width, height, null);
			image = i;
			// check outcome
			final Raster raster = i.getData();
			int[] pixel = new int[3];
			pixel = raster.getPixel(0, 0, pixel);
			if (pixel[0] != 0 || pixel[1] != 0 || pixel[2] != 0)
				image = i;
		} catch (final Exception e) {
			// java.lang.ClassCastException: [I cannot be cast to [B
		}
		return image;
	}
	
	/**
	 * Crop image to make a square
	 * 
	 * @param image
	 *            image to crop
	 * @param h
	 * @param w
	 * @return
	 */
	protected static Image makeSquare(Image image, final int h, final int w) {
		if (w > h) {
			final BufferedImage dst = new BufferedImage(h, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = dst.createGraphics();
			final int offset = (w - h) / 2;
			g.drawImage(image, 0, 0, h - 1, h - 1, offset, 0, h + offset, h - 1, null);
			g.dispose();
			image = dst;
		} else {
			final BufferedImage dst = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = dst.createGraphics();
			final int offset = (h - w) / 2;
			g.drawImage(image, 0, 0, w - 1, w - 1, 0, offset, w - 1, w + offset, null);
			g.dispose();
			image = dst;
		}
		return image;
	}

}
