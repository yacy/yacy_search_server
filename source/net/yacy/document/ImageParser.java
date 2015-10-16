/**
 *  ImageParser
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 29.6.2010 at http://yacy.net
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

import java.awt.Container;
import java.awt.Image;
import java.awt.MediaTracker;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.parser.images.bmpParser;
import net.yacy.document.parser.images.icoParser;

public class ImageParser {

    public static final Image parse(final String filename, final byte[] source) {
        final MediaTracker mediaTracker = new MediaTracker(new Container());
        Image image = null;
        if (((filename.endsWith(".ico")) || (filename.endsWith(".bmp"))) && (bmpParser.isBMP(source))) {
            // parse image with BMP parser
            image = bmpParser.parse(source).getImage();
            if (image == null) return null;
        } else if ((filename.endsWith(".ico")) && (icoParser.isICO(source))) {
            // parse image with ICO parser
            icoParser icoparser;
            try {
                icoparser = new icoParser(source);
                image = icoparser.getImage(0);
            } catch (final Throwable e) {
    			if (ConcurrentLog.isFine("IMAGEPARSER")) {
    				ConcurrentLog.fine("IMAGEPARSER", "IMAGEPARSER.parse : could not parse image " + filename, e);
    			}
            }
            if (image == null) return null;
        } else {
    		try {
    			image = ImageIO.read(new ByteArrayInputStream(source));
    		} catch(IOException e) {
    			if (ConcurrentLog.isFine("IMAGEPARSER")) {
    				ConcurrentLog.fine("IMAGEPARSER", "IMAGEPARSER.parse : could not parse image " + filename, e);
    			}
    		}
        }

        final int handle = image.hashCode();
        mediaTracker.addImage(image, handle);
        try {
            mediaTracker.waitForID(handle);
            
            if (mediaTracker.isErrorID(handle)) { // true if status ERRORD during loading (happens on not supported formats too)
                mediaTracker.removeImage(image, handle);
                image = null; // return null to indicate source not handled
            }
        } catch (final InterruptedException e) {
            return null;
        }

        return image;
    }

}
