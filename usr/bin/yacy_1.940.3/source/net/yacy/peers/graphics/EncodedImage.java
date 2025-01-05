/**
 *  EncodedImage
 *  Copyright 2010 by Michael Christen
 *  First released 28.07.2010 at https://yacy.net
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

package net.yacy.peers.graphics;

import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.yacy.cora.util.ByteBuffer;
import net.yacy.visualization.AnimationGIF;
import net.yacy.visualization.RasterPlotter;

public class EncodedImage {
    private ByteBuffer image;
    private String extension;
    private boolean isStatic;
    
    /**
     * Instanciates an encoded image with raw image data. 
     * Image ByteBuffer will be empty when encoding format is not supported.
     * @param imageData the image data encode in format specified. Must not be null.
     * @param format the image format of imageData. Must not be null.
     * @param isStatic shall be true if the image will never change, false if not
     * @throws IllegalArgumentException when imageData or format parameter is null
     */
    public EncodedImage(final byte[] imageData, final String format, final boolean isStatic) {
    	if(imageData == null) {
    		throw new IllegalArgumentException("imageData parameter is null");
    	}
    	if(format == null) {
    		throw new IllegalArgumentException("format parameter is null");
    	}
        this.image = new ByteBuffer(imageData);
        this.extension = format;
        this.isStatic = isStatic;
    }

    /**
     * set an encoded image; prefer this over methods with Image-source objects because png generation is faster when done from RasterPlotter sources. 
     * Image ByteBuffer will be empty when encoding format is not supported.
     * @param sourceImage the image
     * @param targetExt the target extension of the image when converted into a file
     * @param isStatic shall be true if the image will never change, false if not
     */
    public EncodedImage(final RasterPlotter sourceImage, final String targetExt, final boolean isStatic) {
        this.image = "png".equals(targetExt) ? sourceImage.exportPng() : RasterPlotter.exportImage(sourceImage.getImage(), targetExt);
        this.extension = targetExt;
        this.isStatic = isStatic;
    }
    
    /**
     * set an encoded image from a buffered image. Image ByteBuffer will be empty when encoding format is not supported.
     * @param sourceImage the image
     * @param targetExt the target extension of the image when converted into a file
     * @param isStatic shall be true if the image will never change, false if not
     */
    public EncodedImage(final BufferedImage bi, final String targetExt, final boolean isStatic) {
        this.extension = targetExt;
        this.image = RasterPlotter.exportImage(bi, targetExt);
        if(this.image == null || this.image.length() == 0) {
			/*
			 * Buffered image rendering to targetExt format might fail because
			 * no image writer support source image color model. Let's try
			 * converting source image to RGB before rendering
			 */
        	BufferedImage converted = convertToRGB(bi);
            this.image = RasterPlotter.exportImage(converted, targetExt);
        }
        this.isStatic = isStatic;
        
    }
    
    /**
     * set an encoded image from an animated GIF. The target extension will be "gif"
     * @param sourceImage the image
     * @param isStatic shall be true if the image will never change, false if not
     */
    public EncodedImage(final AnimationGIF sourceImage, final boolean isStatic) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(sourceImage.get());
            bos.close();
        } catch (final IOException e) {
        }
        this.image = new ByteBuffer(bos.toByteArray());
        this.extension = "gif";
        this.isStatic = isStatic;
    }
    
    /**
     * get the encoded image data (empty when encoding format is not supported)
     * @return the bytes of the image encoded into the target extension format
     */
    public ByteBuffer getImage() {
        return this.image;
    }
    
    /**
     * get the extension of the image
     * @return the target extension of the encoded image
     */
    public String getExtension() {
        return this.extension;
    }
    
    /**
     * get information if the information changes in the future or not if it does not change, it is static
     * @return true if the image will never change, false if not
     */
    public boolean isStatic() {
        return this.isStatic;
    }
    
	/**
	 * If source source image colorspace is not RGB or ARGB, convert it to RGB or ARGB when alpha channel is present.
	 * @param image source image. Must not be null.
	 * @return converted image or source image when already RGB.
	 */
	public static BufferedImage convertToRGB(BufferedImage image) {
		BufferedImage converted = image;
		if(image.getType() != BufferedImage.TYPE_INT_RGB && image.getType() != BufferedImage.TYPE_INT_ARGB) {
			int targetType;
			if(image.getColorModel() != null && image.getColorModel().hasAlpha()) {
				targetType = BufferedImage.TYPE_INT_ARGB;
			} else {
				targetType = BufferedImage.TYPE_INT_RGB;
			}
			BufferedImage target = new BufferedImage(image.getWidth(), image.getHeight(), targetType);
			ColorConvertOp convertOP = new ColorConvertOp(null);
			converted = convertOP.filter(image, target);
		}
		return converted;
	}
}
