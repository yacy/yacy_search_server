
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
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

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
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.LoaderDispatcher;
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
	 *             consistent across browsers than a response with zero content
	 *             bytes.
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
                    if (urlString != null) {
                        url = new DigestURL(urlString);
                    } else { // license is gone (e.g. released/remove in prev calls)
                        ConcurrentLog.fine("ViewImage", "image urlLicense not found key=" + urlLicense);
                        return null; //TODO: maybe favicon accessed again, check iconcache
                    }
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

			ImageInputStream imageInStream = null;
			InputStream inStream = null;
			try {
				String urlExt = MultiProtocolURL.getFileExtension(url.getFileName());
				if (ext != null && ext.equalsIgnoreCase(urlExt) && isBrowserRendered(urlExt)) {
					return openInputStream(post, sb.loader, auth, url);
				}
				/*
				 * When opening a file, the most efficient is to open
				 * ImageInputStream directly on file
				 */
				if (url.isFile()) {
					imageInStream = ImageIO.createImageInputStream(url.getFSFile());
				} else {
					inStream = openInputStream(post, sb.loader, auth, url);
					imageInStream = ImageIO.createImageInputStream(inStream);
				}
				// read image
				encodedImage = parseAndScale(post, auth, urlString, ext, imageInStream);
			} catch(Exception e) {
				/* Exceptions are not propagated here : many error causes are possible, network errors, 
				 * incorrect or unsupported format, bad ImageIO plugin...
				 * Instead return an empty EncodedImage. Caller is responsible for handling this correctly */
				encodedImage = new EncodedImage(new byte[0], ext, true);
			} finally {
				/*
				 * imageInStream.close() method doesn't close source input
				 * stream
				 */
				if (inStream != null) {
					try {
						inStream.close();
					} catch (IOException ignored) {
					}
				}
			}
		}

		return encodedImage;
	}

	/**
	 * Open input stream on image url using provided loader. All parameters must
	 * not be null.
	 * 
	 * @param post
	 *            post parameters.
	 * @param loader.
	 *            Resources loader.
	 * @param auth
	 *            true when user has credentials to load full images.
	 * @param url
	 *            image url.
	 * @return an open input stream instance (don't forget to close it).
	 * @throws IOException
	 *             when a read/write error occured. 
	 */
	private static InputStream openInputStream(final serverObjects post, final LoaderDispatcher loader,
			final boolean auth, DigestURL url) throws IOException {
		InputStream inStream = null;
		if (url != null) {
			try {
				String agentName = post.get("agentName", auth ? ClientIdentification.yacyIntranetCrawlerAgentName
						: ClientIdentification.yacyInternetCrawlerAgentName);
				ClientIdentification.Agent agent = ClientIdentification.getAgent(agentName);
				inStream = loader.openInputStream(loader.request(url, false, true), CacheStrategy.IFEXIST,
						BlacklistType.SEARCH, agent);
			} catch (final IOException e) {
				/** No need to log full stack trace (in most cases resource is not available because of a network error) */
				ConcurrentLog.fine("ViewImage", "cannot load image. URL : " + url);
				throw e;
			}
		}
		if (inStream == null) {
			throw new IOException("Input stream could no be open");
		}
		return inStream;
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
	 * Process source image to try to produce an EncodedImage instance
	 * eventually scaled and clipped depending on post parameters. When
	 * processed, imageInStream is closed.
	 * 
	 * @param post
	 *            request post parameters. Must not be null.
	 * @param auth
	 *            true when access rigths are OK.
	 * @param urlString
	 *            image source URL as String. Must not be null.
	 * @param ext
	 *            target image file format. May be null.
	 * @param imageInStream
	 *            open stream on image content. Must not be null.
	 * @return an EncodedImage instance.
	 * @throws IOException
	 *             when image could not be parsed or encoded to specified format.
	 */
	protected static EncodedImage parseAndScale(serverObjects post, boolean auth, String urlString, String ext,
			ImageInputStream imageInStream) throws IOException {
		EncodedImage encodedImage;

		// BufferedImage image = ImageIO.read(imageInStream);
		Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInStream);
		if (!readers.hasNext()) {
			try {
				/* When no reader can be found, we have to close the stream */
				imageInStream.close();
			} catch (IOException ignoredException) {
			}
			String errorMessage = "Image format (" + ext + ") is not supported.";
			ConcurrentLog.fine("ViewImage", errorMessage + "Image URL : " + urlString);
			/*
			 * Throw an exception, wich will end in a HTTP 500 response, better
			 * handled by browsers than an empty image
			 */
			throw new IOException(errorMessage);
		}
		ImageReader reader = readers.next();
		reader.setInput(imageInStream, true, true);

		int maxwidth = post.getInt("maxwidth", 0);
		int maxheight = post.getInt("maxheight", 0);
		final boolean quadratic = post.containsKey("quadratic");
		boolean isStatic = post.getBoolean("isStatic");
		BufferedImage image = null;
		boolean returnRaw = true;
		if (!auth || maxwidth != 0 || maxheight != 0) {

			// find original size
			final int originWidth = reader.getWidth(0);
			final int originHeigth = reader.getHeight(0);

			// in case of not-authorized access shrink the image to
			// prevent
			// copyright problems, so that images are not larger than
			// thumbnails
			Dimension maxDimensions = calculateMaxDimensions(auth, originWidth, originHeigth, maxwidth, maxheight);

			// if a quadratic flag is set, we cut the image out to be in
			// quadratic shape
			int w = originWidth;
			int h = originHeigth;
			if (quadratic && originWidth != originHeigth) {
				Rectangle square = getMaxSquare(originHeigth, originWidth);
				h = square.height;
				w = square.width;
			}

			Dimension finalDimensions = calculateDimensions(w, h, maxDimensions);

			if (originWidth != finalDimensions.width || originHeigth != finalDimensions.height) {
				returnRaw = false;
				image = readImage(reader);
				if (quadratic && originWidth != originHeigth) {
					image = makeSquare(image);
				}
				image = scale(finalDimensions.width, finalDimensions.height, image);
			}
			if (finalDimensions.width == 16 && finalDimensions.height == 16) {
				// this might be a favicon, store image to cache for
				// faster
				// re-load later on
				if (image == null) {
					returnRaw = false;
					image = readImage(reader);
				}
				iconcache.put(urlString, image);
			}
		}
		/* Image do not need to be scaled or cropped */
		if (returnRaw) {
			if (!reader.getFormatName().equalsIgnoreCase(ext) || imageInStream.getFlushedPosition() != 0) {
				/*
				 * image parsing and reencoding is only needed when source image
				 * and target formats differ, or when first bytes have been discarded
				 */
				returnRaw = false;
				image = readImage(reader);
			}
		}
		if (returnRaw) {
			byte[] imageData = readRawImage(imageInStream);
			encodedImage = new EncodedImage(imageData, ext, isStatic);
		} else {
			/*
			 * An error can still occur when transcoding from buffered image to
			 * target ext : in that case EncodedImage.getImage() is empty.
			 */
			encodedImage = new EncodedImage(image, ext, isStatic);
			if (encodedImage.getImage().length() == 0) {
				String errorMessage = "Image could not be encoded to format : " + ext;
				ConcurrentLog.fine("ViewImage", errorMessage + ". Image URL : " + urlString);
				throw new IOException(errorMessage);
			}
		}

		return encodedImage;
	}

	/**
	 * Read image using specified reader and close ImageInputStream source.
	 * Input must have bean set before using
	 * {@link ImageReader#setInput(Object)}
	 * 
	 * @param reader
	 *            image reader. Must not be null.
	 * @return buffered image
	 * @throws IOException
	 *             when an error occured
	 */
	private static BufferedImage readImage(ImageReader reader) throws IOException {
		BufferedImage image;
		try {
			image = reader.read(0);
		} finally {
			reader.dispose();
			Object input = reader.getInput();
			if (input instanceof ImageInputStream) {
				try {
					((ImageInputStream) input).close();
				} catch (IOException ignoredException) {
				}
			}
		}
		return image;
	}

	/**
	 * Read image data without parsing.
	 * 
	 * @param inStream
	 *            image source. Must not be null. First bytes must not have been marked discarded ({@link ImageInputStream#getFlushedPosition()} must be zero)
	 * @return image data as bytes
	 * @throws IOException
	 *             when a read/write error occured.
	 */
	private static byte[] readRawImage(ImageInputStream inStream) throws IOException {
		byte[] buffer = new byte[4096];
		int l = 0;
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		inStream.seek(0);
		try {
			while ((l = inStream.read(buffer)) >= 0) {
				outStream.write(buffer, 0, l);
			}
			return outStream.toByteArray();
		} finally {
			try {
				inStream.close();
			} catch (IOException ignored) {
			}
		}
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
	protected static BufferedImage scale(final int width, final int height, final BufferedImage image) {
		// compute scaled image
		Image scaled = image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
		final MediaTracker mediaTracker = new MediaTracker(new Container());
		mediaTracker.addImage(scaled, 0);
		try {
			mediaTracker.waitForID(0);
		} catch (final InterruptedException e) {
		}

		// make a BufferedImage out of that
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		try {
			result.createGraphics().drawImage(scaled, 0, 0, width, height, null);
			// check outcome
			final Raster raster = result.getData();
			int[] pixel = new int[raster.getSampleModel().getNumBands()];
			pixel = raster.getPixel(0, 0, pixel);
		} catch (final Exception e) {
			/*
			 * Exception may be caused by source image color model : try now to
			 * convert to RGB before scaling
			 */
			try {
				BufferedImage converted = EncodedImage.convertToRGB(image);
				scaled = converted.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
				mediaTracker.addImage(scaled, 1);
				try {
					mediaTracker.waitForID(1);
				} catch (final InterruptedException e2) {
				}
				result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				result.createGraphics().drawImage(scaled, 0, 0, width, height, null);

				// check outcome
				final Raster raster = result.getData();
				int[] pixel = new int[result.getSampleModel().getNumBands()];
				pixel = raster.getPixel(0, 0, pixel);
			} catch (Exception e2) {
				result = image;
			}

			ConcurrentLog.fine("ViewImage", "Image could not be scaled");
		}
		return result;
	}

	/**
	 * 
	 * @param h
	 *            image height
	 * @param w
	 *            image width
	 * @return max square area fitting inside dimensions
	 */
	protected static Rectangle getMaxSquare(final int h, final int w) {
		Rectangle square;
		if (w > h) {
			final int offset = (w - h) / 2;
			square = new Rectangle(offset, 0, h, h);
		} else {
			final int offset = (h - w) / 2;
			square = new Rectangle(0, offset, w, w);
		}
		return square;
	}

	/**
	 * Crop image to make a square
	 * 
	 * @param image
	 *            image to crop
	 * @return
	 */
	protected static BufferedImage makeSquare(BufferedImage image) {
		final int w = image.getWidth();
		final int h = image.getHeight();
		if (w > h) {
			final BufferedImage dst = new BufferedImage(h, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = dst.createGraphics();
			final int offset = (w - h) / 2;
			try {
				g.drawImage(image, 0, 0, h - 1, h - 1, offset, 0, h + offset, h - 1, null);
			} finally {
				g.dispose();
			}
			image = dst;
		} else {
			final BufferedImage dst = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = dst.createGraphics();
			final int offset = (h - w) / 2;
			try {
				g.drawImage(image, 0, 0, w - 1, w - 1, 0, offset, w - 1, w + offset, null);
			} finally {
				g.dispose();
			}
			image = dst;
		}
		return image;
	}

}
