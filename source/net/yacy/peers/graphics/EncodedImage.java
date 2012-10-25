package net.yacy.peers.graphics;

import net.yacy.kelondro.util.ByteBuffer;
import net.yacy.visualization.RasterPlotter;

public class EncodedImage {
    private ByteBuffer image;
    private String extension;
    
    public EncodedImage(final RasterPlotter sourceImage, final String targetExt) {
        this.image = "png".equals(targetExt) ? sourceImage.exportPng() : RasterPlotter.exportImage(sourceImage.getImage(), targetExt);
        this.extension = targetExt;
    }
    
    public ByteBuffer getImage() {
        return this.image;
    }
    
    public String getExtension() {
        return this.extension;
    }
}
