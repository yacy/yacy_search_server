/**
 *  EncodedImage
 *  Copyright 2010 by Michael Christen
 *  First released 28.07.2010 at http://yacy.net
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

import java.awt.Image;
import java.awt.image.BufferedImage;
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
     * set an encoded image; prefer this over methods with Image-source objects because png generation is faster when done from RasterPlotter sources
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
     * set an encoded image from a buffered image
     * @param sourceImage the image
     * @param targetExt the target extension of the image when converted into a file
     * @param isStatic shall be true if the image will never change, false if not
     */
    public EncodedImage(final BufferedImage bi, final String targetExt, final boolean isStatic) {
        this.extension = targetExt;
        this.image = RasterPlotter.exportImage(bi, targetExt);
        this.isStatic = isStatic;
        
    }
    
    /**
     * set an encoded image from a buffered image
     * @param sourceImage the image
     * @param targetExt the target extension of the image when converted into a file
     * @param isStatic shall be true if the image will never change, false if not
     */
    public EncodedImage(final Image i, final String targetExt, final boolean isStatic) {
        this.extension = targetExt;
        this.isStatic = isStatic;
        
        // generate an byte array from the generated image
        int width = i.getWidth(null);
        if (width < 0) {
            width = 96; // bad hack
        }
        int height = i.getHeight(null);
        if (height < 0) {
            height = 96; // bad hack
        }
        final BufferedImage sourceImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        sourceImage.createGraphics().drawImage(i, 0, 0, width, height, null);
        this.image = RasterPlotter.exportImage(sourceImage, targetExt);
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
     * get the encoded image
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
}
