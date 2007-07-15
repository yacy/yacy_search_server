package de.anomic.ymage;

import java.awt.Container;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;


public class ymageImageParser {

    public static final Image parse(String filename, byte[] source) {
        MediaTracker mediaTracker = new MediaTracker(new Container()); 
        Image image;
        if (((filename.endsWith(".ico")) || (filename.endsWith(".bmp"))) && (ymageBMPParser.isBMP(source))) {
            // parse image with BMP parser
            ymageBMPParser bmpparser;
            bmpparser = new ymageBMPParser(source);
            image = bmpparser.getImage();
            if (image == null) return null;
        } else if ((filename.endsWith(".ico")) && (ymageICOParser.isICO(source))) {
            // parse image with ICO parser
            ymageICOParser icoparser;
            icoparser = new ymageICOParser(source);
            image = icoparser.getImage(0);
            if (image == null) return null;
        } else {
            // awt can handle jpg, bmp and gif formats, try it
            image = Toolkit.getDefaultToolkit().createImage(source);
        }
        
        int handle = image.hashCode();
        mediaTracker.addImage(image, handle); 
        try {mediaTracker.waitForID(handle);} catch (InterruptedException e) {} 
        
        return image;
    }
    
}
