/**
 *  ImageViewer
 *  Copyright 2016 by luccioman; https://github.com/luccioman
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

package net.yacy.visualization;

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
import java.net.MalformedURLException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.retrieval.StreamResponse;
import net.yacy.data.InvalidURLLicenceException;
import net.yacy.data.URLLicense;
import net.yacy.data.UserDB;
import net.yacy.http.servlets.TemplateMissingParameterException;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;

/**
 * Provides methods for image or favicon viewing in YaCy servlets. 
 * @author luc
 *
 */
public class ImageViewer {

	/**
	 * Try to get image URL from parameters.
	 * @param post post parameters. Must not be null.
	 * @param auth true when current user is authenticated
	 * @return DigestURL instance
	 * @throws MalformedURLException when url is malformed
	 * @throws TemplateMissingParameterException when urlString or urlLicense is missing (the one needed depends on auth)
	 */
	public DigestURL parseURL(final serverObjects post, final boolean auth)
			throws MalformedURLException {
		final String urlString = post.get("url", "");
		final String urlLicense = post.get("code", "");
		DigestURL url;
		if(auth) {
			/* Authenticated user : rely on url parameter*/
			if (urlString.length() > 0) {
				url = new DigestURL(urlString);
			} else {
				throw new TemplateMissingParameterException("missing required url parameter");
			}
		} else {
			/* Non authenticated user : rely on urlLicense parameter */
			if((urlLicense.length() > 0)) {
				String licensedURL = URLLicense.releaseLicense(urlLicense);
				if (licensedURL != null) {
					url = new DigestURL(licensedURL);
				} else { // license is gone (e.g. released/remove in prev calls)
					ConcurrentLog.fine("ImageViewer", "image urlLicense not found key=" + urlLicense);
					/* Caller is responsible for handling this with appropriate HTTP status code */
					throw new InvalidURLLicenceException();
				}
			} else {
				throw new TemplateMissingParameterException("missing required code parameter");
			}			
		}
		return url;
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
	 *             when a read/write error occurred. 
	 */
	public InputStream openInputStream(final serverObjects post, final LoaderDispatcher loader,
			final boolean auth, DigestURL url) throws IOException {
		InputStream inStream = null;
		if (url != null) {
			try {
				String agentName = post.get("agentName", auth ? ClientIdentification.yacyIntranetCrawlerAgentName
						: ClientIdentification.yacyInternetCrawlerAgentName);
				ClientIdentification.Agent agent = ClientIdentification.getAgent(agentName);
				/* We do not apply here the crawler max file size limit, 
				 * as the purpose of this stream is not to be parsed and indexed but to be directly rendered */
				final StreamResponse response = loader.openInputStream(loader.request(url, false, true), CacheStrategy.IFEXIST,
						BlacklistType.SEARCH, agent, -1);
				inStream = response.getContentStream();
			} catch (final IOException e) {
				/** No need to log full stack trace (in most cases resource is not available because of a network error) */
				ConcurrentLog.fine("ImageViewer", "cannot load image. URL : " + url.toNormalform(true));
				throw e;
			}
		}
		if (inStream == null) {
			throw new IOException("Input stream could no be open");
		}
		return inStream;
	}
	
	/**
	 * Check the request header to decide whether full image viewing is allowed for a given request.
	 * @param header request header. When null, false is returned.
	 * @param sb switchboard instance.
	 * @return true when full image view is allowed for this request
	 */
	public static boolean hasFullViewingRights(final RequestHeader header, final Switchboard sb) {
		boolean extendedSearchRights = false;
		if(sb != null && header != null) {
			final boolean adminAuthenticated = sb.verifyAuthentication(header);
			if (adminAuthenticated) {
				extendedSearchRights = true;
			} else {
				final UserDB.Entry user = sb.userDB != null ? sb.userDB.getUser(header) : null;
				if (user != null) {
					extendedSearchRights = user.hasRight(UserDB.AccessRight.EXTENDED_SEARCH_RIGHT);
				}
			}
		}
		return header != null && (extendedSearchRights || Domains.isLocalhost(header.getRemoteAddr()));
	}

	/**
	 * @param formatName
	 *            informal file format name. For example : "png".
	 * @return true when image format will be rendered by browser and not by a YaCy service
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
	 * @param url
	 *            image source URL. Must not be null.
	 * @param ext
	 *            target image file format. May be null.
	 * @param imageInStream
	 *            open stream on image content. Must not be null.
	 * @return an EncodedImage instance.
	 * @throws IOException
	 *             when image could not be parsed or encoded to specified format.
	 */
	public EncodedImage parseAndScale(serverObjects post, boolean auth, DigestURL url, String ext,
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
			String urlString = url.toNormalform(false);
			String errorMessage = "Image format (" + MultiProtocolURL.getFileExtension(urlString) + ") is not supported.";
			ConcurrentLog.fine("ImageViewer", errorMessage + "Image URL : " + urlString);
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
				ConcurrentLog.fine("ImageViewer", errorMessage + ". Image URL : " + url.toNormalform(false));
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
	private BufferedImage readImage(ImageReader reader) throws IOException {
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
	private byte[] readRawImage(ImageInputStream inStream) throws IOException {
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
	protected Dimension calculateDimensions(final int originWidth, final int originHeight, final Dimension max) {
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
	protected Dimension calculateMaxDimensions(final boolean auth, final int originWidth, final int originHeight,
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
	public BufferedImage scale(final int width, final int height, final BufferedImage image) {
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

			ConcurrentLog.fine("ImageViewer", "Image could not be scaled");
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
	public Rectangle getMaxSquare(final int h, final int w) {
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
	public BufferedImage makeSquare(BufferedImage image) {
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
