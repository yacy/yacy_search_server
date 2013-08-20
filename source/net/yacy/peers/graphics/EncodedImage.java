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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.yacy.cora.util.ByteBuffer;
import net.yacy.visualization.AnimationGIF;
import net.yacy.visualization.RasterPlotter;

public class EncodedImage {
    private ByteBuffer image;
    private String extension;

    public EncodedImage(final RasterPlotter sourceImage, final String targetExt) {
        this.image = "png".equals(targetExt) ? sourceImage.exportPng() : RasterPlotter.exportImage(sourceImage.getImage(), targetExt);
        this.extension = targetExt;
    }
    
    public EncodedImage(final AnimationGIF sourceImage) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(sourceImage.get());
            bos.close();
        } catch (final IOException e) {
        }
        this.image = new ByteBuffer(bos.toByteArray());
        this.extension = "gif";
    }
    
    public ByteBuffer getImage() {
        return this.image;
    }
    
    public String getExtension() {
        return this.extension;
    }
}
