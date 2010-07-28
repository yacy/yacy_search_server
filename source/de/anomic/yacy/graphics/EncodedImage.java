package de.anomic.yacy.graphics;

import java.awt.image.BufferedImage;

import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.visualization.RasterPlotter;

public class EncodedImage {
    private ByteBuffer image;
    private String extension;
    
    public EncodedImage(final BufferedImage sourceImage, final String targetExt) {
        this.image = RasterPlotter.exportImage(sourceImage, targetExt);
        this.extension = targetExt;
    }
    
    public ByteBuffer getImage() {
        return this.image;
    }
    
    public String getExtension() {
        return this.extension;
    }
}
