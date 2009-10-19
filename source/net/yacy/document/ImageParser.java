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
            bmpParser bmpparser;
            bmpparser = new bmpParser(source);
            image = bmpparser.getImage();
            if (image == null) return null;
        } else if ((filename.endsWith(".ico")) && (icoParser.isICO(source))) {
            // parse image with ICO parser
            icoParser icoparser;
            icoparser = new icoParser(source);
            image = icoparser.getImage(0);
            if (image == null) return null;
        } else {
            // awt can handle jpg, png and gif formats, try it
            image = Toolkit.getDefaultToolkit().createImage(source);
        }
        
        final int handle = image.hashCode();
        mediaTracker.addImage(image, handle); 
        try {mediaTracker.waitForID(handle);} catch (final InterruptedException e) {} 
        
        return image;
    }
    
}
