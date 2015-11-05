
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
import java.io.IOException;
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

	/**
	 * Try parsing image from post "url" parameter or from "code" parameter.
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
	 *         post, or an InputStream pointing to original image data
	 * @throws IOException
	 *             when specified url is malformed, or a read/write error
	 *             occured, or input or target image format is not supported.
	 *             Sould end in a HTTP 500 error whose processing is more
	 *             consistent across browsers than a response with zero
	 *             content bytes.
	 */
	public static Object respond(final RequestHeader header, final serverObjects post, final serverSwitch env)
			throws IOException {

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
		if ((urlString.length() > 0) && (auth)) {
			url = new DigestURL(urlString);
		}

		if ((url == null) && (urlLicense.length() > 0)) {
			urlString = URLLicense.releaseLicense(urlLicense);
			url = new DigestURL(urlString);
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
					throw e;
				}
			boolean okToCache = true;
			if (resourceb == null) {
				/*
				 * Throw an exception, wich will end in a HTTP 500 response,
				 * better handled by browsers than an empty image
				 */
				throw new IOException("Image could not be loaded.");
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
	 * Process resourceb byte array to try to produce an EncodedImage instance
	 * eventually scaled and cropped depending on post parameters.
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
	 * @return an EncodedImage instance.
	 * @throws IOException
	 *             when image could not be parsed or encoded to specified format
	 */
	protected static EncodedImage parseAndScale(serverObjects post, boolean auth, String urlString, String ext,
			boolean okToCache, byte[] resourceb) throws IOException {
		EncodedImage encodedImage = null;

		Image image = ImageParser.parse(urlString, resourceb);
		if (image == null) {
			/*
			 * Throw an exception, wich will end in a HTTP 500 response, better
			 * handled by browsers than an empty image
			 */
			throw new IOException("Image format is not supported.");
		}

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
			/*
			 * An error can still occur when transcoding from buffered image to
			 * target ext : in that case return null
			 */
			encodedImage = new EncodedImage(image, ext, isStatic);
			if (encodedImage.getImage().length() == 0) {
				throw new IOException("Image could not be encoded to format : " + ext);
			}
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
