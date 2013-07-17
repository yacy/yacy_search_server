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
import java.awt.Toolkit;

import net.yacy.document.parser.images.bmpParser;
import net.yacy.document.parser.images.icoParser;

public class ImageParser {

    public static final Image parse(final String filename, final byte[] source) {
        final MediaTracker mediaTracker = new MediaTracker(new Container());
        Image image;
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
                image = null;
            }
            if (image == null) return null;
        } else {
            // awt can handle jpg, png and gif formats, try it
            image = Toolkit.getDefaultToolkit().createImage(source);
            /*
            try {
                ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
                image = ImageIO.read(new ByteArrayInputStream(source));
            } catch (final IOException e) {
                Image i = Toolkit.getDefaultToolkit().createImage(source);
                mediaTracker.addImage(i, 0);
                try {mediaTracker.waitForID(0);} catch (final InterruptedException ee) {}

                int width = i.getWidth(null); if (width < 0) width = 96; // bad hack
                int height = i.getHeight(null); if (height < 0) height = 96; // bad hack
                image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                image.createGraphics().drawImage(i, 0, 0, width, height, null);
            }
             */
        }

        final int handle = image.hashCode();
        mediaTracker.addImage(image, handle);
        try {mediaTracker.waitForID(handle);} catch (final InterruptedException e) {}

        return image;
    }

}
