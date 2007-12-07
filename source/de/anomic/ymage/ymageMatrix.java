// ymageMatrix.java 
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.09.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

/*
 This Class implements some convenience-methods to support drawing of statistical Data
 It is not intended to replace existing awt-funktions even if it looks so
 This class provides some drawing methods that creates transparency effects that
 are not available in awt.
 */

package de.anomic.ymage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import de.anomic.server.serverByteBuffer;
import de.anomic.server.serverMemory;

public class ymageMatrix {
    
    // colors regarding RGB Color Model
    public static final long RED    = 0xFF0000;
    public static final long GREEN  = 0x00FF00;
    public static final long BLUE   = 0x0000FF;
    public static final long GREY   = 0x888888;
    
    public static final byte MODE_REPLACE = 0;
    public static final byte MODE_ADD = 1;
    public static final byte MODE_SUB = 2;
    
    protected int            width, height;
    private   BufferedImage  image;
    private   WritableRaster grid;
    private   int[]          defaultCol;
    private   long           backgroundCol;
    private   byte           defaultMode;
    
    public ymageMatrix(int width, int height, byte drawMode, String backgroundColor) {
        this(width, height, drawMode, Long.parseLong(backgroundColor, 16));
    }
    
    public ymageMatrix(int width, int height, byte drawMode, long backgroundColor) {
        if (!(serverMemory.request(1024 * 1024 + 3 * width * height, false))) throw new RuntimeException("ymage: not enough memory (" + serverMemory.available() + ") available");
        this.width = width;
        this.height = height;
        this.backgroundCol = backgroundColor;
        this.defaultCol = new int[]{0xFF, 0xFF, 0xFF};
        this.defaultMode = drawMode;
        
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        //this.image = imageFromPool(width, height, 1000);
        this.clear();
        this.grid = image.getRaster();
    }
    
    public void clear() {
    	// fill grid with background color
        int bgR, bgG, bgB;
        /*if (drawMode == MODE_SUB) {
            bgR = (int) (0xFF - (this.backgroundCol >> 16));
            bgG = (int) (0xFF - ((this.backgroundCol >> 8) & 0xff));
            bgB = (int) (0xFF - (this.backgroundCol & 0xff));
        } else {*/
            bgR = (int) (this.backgroundCol >> 16);
            bgG = (int) ((this.backgroundCol >> 8) & 0xff);
            bgB = (int) (this.backgroundCol & 0xff);
        //}
        Graphics2D gr = image.createGraphics();
        gr.setBackground(new Color(bgR, bgG, bgB));
        gr.clearRect(0, 0, width, height);
        /*
        int[] c = new int[]{bgR, bgG, bgB};
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                grid.setPixel(i, j, c);
            }
        }
        */
    }
    
    public BufferedImage getImage() {
        return this.image;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public void setColor(long c) {
    	if (this.defaultMode == MODE_SUB) {
    		int r = (int) (c >> 16);
            int g = (int) ((c >> 8) & 0xff);
            int b = (int) (c & 0xff);
            defaultCol[0] = (g + b) / 2;
            defaultCol[1] = (r + b) / 2;
            defaultCol[2] = (r + g) / 2;
    	} else {
    		defaultCol[0] = (int) (c >> 16);
            defaultCol[1] = (int) ((c >> 8) & 0xff);
            defaultCol[2] = (int) (c & 0xff);
    	}
        
    }
    
    public void setColor(String s) {
        setColor(Long.parseLong(s, 16));
    }

    public void plot(int x, int y) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        //int n = 3 * (x + y * width);
        if (this.defaultMode == MODE_REPLACE) {
            grid.setPixel(x, y, defaultCol);
        } else if (this.defaultMode == MODE_ADD) {
            int[] c = new int[3];
            c = grid.getPixel(x, y, c);
            int r = (0xff & c[0]) + defaultCol[0]; if (r > 255) r = 255;
            int g = (0xff & c[1]) + defaultCol[1]; if (g > 255) g = 255;
            int b = (0xff & c[2]) + defaultCol[2]; if (b > 255) b = 255;
            grid.setPixel(x, y, new int[]{r, g, b});
        } else if (this.defaultMode == MODE_SUB) {
            int[] c = new int[3];
            c = grid.getPixel(x, y, c);
            int r = (0xff & c[0]) - defaultCol[0]; if (r < 0) r = 0;
            int g = (0xff & c[1]) - defaultCol[1]; if (g < 0) g = 0;
            int b = (0xff & c[2]) - defaultCol[2]; if (b < 0) b = 0;
            grid.setPixel(x, y, new int[]{r, g, b});
        }
    }
    
    public void line(int Ax, int Ay, int Bx, int By) {
        // Bresenham's line drawing algorithm
        int dX = Math.abs(Bx-Ax);
        int dY = Math.abs(By-Ay);
        int Xincr, Yincr;
        if (Ax > Bx) Xincr=-1; else Xincr=1;
        if (Ay > By) Yincr=-1; else Yincr=1;
        if (dX >= dY) {
            int dPr  = dY<<1;
            int dPru = dPr - (dX<<1);
            int P    = dPr - dX;
            for (; dX>=0; dX--) {
                plot(Ax, Ay);
                if (P > 0) {
                    Ax+=Xincr;
                    Ay+=Yincr;
                    P+=dPru;
                } else {
                    Ax+=Xincr;
                    P+=dPr;
                }
            }
        } else {
            int dPr  = dX<<1;
            int dPru = dPr - (dY<<1);
            int P    = dPr - dY;
            for (; dY>=0; dY--) {
                plot(Ax, Ay);
                if (P > 0) {
                    Ax+=Xincr;
                    Ay+=Yincr;
                    P+=dPru;
                } else {
                    Ay+=Yincr;
                    P+=dPr;
                }
            }
        }
    }
    
    public void lineDot(int x0, int y0, int x1, int y1, int radius, int distance, long lineColor, long dotColor) {
        // draw a line with a dot at the end.
        // the radius value is the radius of the dot
        // the distance value is the distance of the dot border to the endpoint
        
        // compute first the angle of the line between the points
        double angle = (x1 - x0 > 0) ? Math.atan(((double) (y0 - y1)) / ((double) (x1 - x0))) : Math.PI - Math.atan(((double) (y0 - y1)) / ((double) (x0 - x1)));
        // now find two more points in between
        // first calculate the radius' of the points
        double ra = Math.sqrt((double) ((x0 - x1) * (x0 - x1) + (y0 - y1) * (y0 - y1))); // from a known point x1, y1
        double rb = ra - radius - distance;
        double rc = rb - radius;
        //System.out.println("CONTROL angle = " + angle);
        //System.out.println("CONTROL x1 = " + x1 + ", x1calc = " + ((x0 + ((int) ra * Math.cos(angle)))));
        //System.out.println("CONTROL y1 = " + y1 + ", y1calc = " + ((y0 - ((int) ra * Math.sin(angle)))));
        // the points are on a circle with radius rb and rc
        int x2 = x0 + ((int) (rb * Math.cos(angle)));
        int y2 = y0 - ((int) (rb * Math.sin(angle)));
        int x3 = x0 + ((int) (rc * Math.cos(angle)));
        int y3 = y0 - ((int) (rc * Math.sin(angle)));
        setColor(lineColor);
        line(x0, y0, x3, y3);
        setColor(dotColor);
        dot(x2, y2, radius, true);
    }
    
    public int[] getColor(int x, int y) {
        int[] c = new int[3];
        return grid.getPixel(x, y, c);
    }

    public void dot(int x, int y, int radius, boolean filled) {
        if (filled) {
            for (int r = radius; r >= 0; r--) ymageToolCircle.circle(this, x, y, r);
        } else {
            ymageToolCircle.circle(this, x, y, radius);
        }
    }
    
    public void arc(int x, int y, int innerRadius, int outerRadius, int fromArc, int toArc) {
        for (int r = innerRadius; r <= outerRadius; r++) ymageToolCircle.circle(this, x, y, r, fromArc, toArc);
    }

    public void arcLine(int cx, int cy, int innerRadius, int outerRadius, int angle) {
        int xi = cx + (int) (innerRadius * Math.cos(Math.PI * angle / 180));
        int yi = cy - (int) (innerRadius * Math.sin(Math.PI * angle / 180));
        int xo = cx + (int) (outerRadius * Math.cos(Math.PI * angle / 180));
        int yo = cy - (int) (outerRadius * Math.sin(Math.PI * angle / 180));
        line(xi, yi, xo, yo);
    }
    
    public void arcDot(int cx, int cy, int arcRadius, int angle, int dotRadius) {
        int x = cx + (int) (arcRadius * Math.cos(Math.PI * angle / 180));
        int y = cy - (int) (arcRadius * Math.sin(Math.PI * angle / 180));
        dot(x, y, dotRadius, true);
    }
    
    public void arcArc(int cx, int cy, int arcRadius, int angle, int innerRadius, int outerRadius, int fromArc, int toArc) {
        int x = cx + (int) (arcRadius * Math.cos(Math.PI * angle / 180));
        int y = cy - (int) (arcRadius * Math.sin(Math.PI * angle / 180));
        arc(x, y, innerRadius, outerRadius, fromArc, toArc);
    }
    
    /**
     *  inserts an image into the ymageMatrix
     *  @param bitmap the bitmap to be inserted
     *  @param x the x value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     *  @param y the y value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     *
     *  @author Marc Nause
     */
    public void insertBitmap(BufferedImage bitmap, int x, int y) {
        insertBitmap(bitmap, x, y, -1);
    }
    
    /**
     *  inserts an image into the ymageMatrix where all pixels that have the same RGB value as the
     *  pixel at (xx, yy) are transparent
     *  @param bitmap the bitmap to be inserted
     *  @param x the x value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     *  @param y the y value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     *  @param xx the x value of the pixel that determines which color is transparent
     *  @param yy the y value of the pixel that determines which color is transparent
     *  @author Marc Nause
     */
    public void insertBitmap(BufferedImage bitmap, int x, int y, int xx, int yy) {
        insertBitmap(bitmap, x, y, bitmap.getRGB(xx, yy));
    }    

    /**
     *  inserts an image into the ymageMatrix where all pixels that have a special RGB value
     *  pixel at (xx, yy) are transparent
     *  @param bitmap the bitmap to be inserted
     *  @param x the x value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     *  @param y the y value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     *  @param rgb the RGB value that will be transparent
     *  @author Marc Nause
     */      
    public void insertBitmap(BufferedImage bitmap, int x, int y, int transRGB) {
        int heightSrc = bitmap.getHeight();
        int widthSrc  = bitmap.getWidth();
        int heightTgt = image.getHeight();
        int widthTgt  = image.getWidth();

        int rgb;
        for (int i = 0; i < heightSrc; i++) {
            for (int j = 0; j < widthSrc; j++) {
                // pixel in legal area?
                if (j+x >= 0 && i+y >= 0 && i+y < heightTgt && j+x < widthTgt) {
                    rgb = bitmap.getRGB(j, i);
                    if (rgb != transRGB) {
                        image.setRGB(j+x, i+y, rgb);
                    }
                }
            }
        }
    }      
    
    public static void demoPaint(ymageMatrix m) {
        m.setColor(GREY);
        m.line(0,  70, 100,  70); ymageToolPrint.print(m, 0,  65, 0, "Grey", -1);
        m.line(65, 0,   65, 300);
        m.setColor(RED);
        m.line(0,  90, 100,  90); ymageToolPrint.print(m, 0,  85, 0, "Red", -1);
        m.line(70, 0,   70, 300);
        m.setColor(GREEN);
        m.line(0, 110, 100, 110); ymageToolPrint.print(m, 0, 105, 0, "Green", -1);
        m.line(75, 0,   75, 300);
        m.setColor(BLUE);
        m.line(0, 130, 100, 130); ymageToolPrint.print(m, 0, 125, 0, "Blue", -1);
        m.line(80, 0,   80, 300);
    }
/*
    private static class imageBuffer {
    	protected BufferedImage  image;
    	protected long           access;
    	public imageBuffer(BufferedImage image) {
    		this.image = image;
    		this.access = System.currentTimeMillis();
    	}
    	public boolean sameSize(int width, int height) {
    		return (this.image.getWidth() == width) && (this.image.getHeight() == height);
    	}
    	public boolean olderThan(long timeout) {
    		return System.currentTimeMillis() - this.access > timeout;
    	}
    }
    private static final ArrayList imagePool = new ArrayList();
    private static BufferedImage imageFromPool(int width, int height, long timeout) {
    	// returns an Image object from the image pool
    	// if the pooled Image was created recently (before timeout), it is not used
    	synchronized (imagePool) {
    		imageBuffer buffer;
    		for (int i = 0; i < imagePool.size(); i++) {
    			buffer = (imageBuffer) imagePool.get(i);
    			if ((buffer.sameSize(width, height)) && (buffer.olderThan(timeout))) {
    				// use this buffer
    				System.out.println("### using imageBuffer from pool " + i);
    				buffer.access = System.currentTimeMillis();
    				return buffer.image;
    			}
    		}
    		// no buffered image found, create a new one
    		buffer = new imageBuffer(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
    		imagePool.add(buffer);
    		return buffer.image;
    	}
    }
*/  
    private static class sbbBuffer {
    	protected serverByteBuffer buffer;
    	protected int              pixel;
    	protected long             access;
    	public sbbBuffer(int width, int height) {
    		this.buffer = new serverByteBuffer();
    		this.access = System.currentTimeMillis();
    		this.pixel = width * height;
    	}
    	public boolean enoughSize(int width, int height) {
    		return this.pixel >= width * height;
    	}
    	public boolean olderThan(long timeout) {
    		return System.currentTimeMillis() - this.access > timeout;
    	}
    }
    private static final ArrayList sbbPool = new ArrayList();
    private static serverByteBuffer sbbFromPool(int width, int height, long timeout) {
    	// returns an Image object from the image pool
    	// if the pooled Image was created recently (before timeout), it is not used
    	synchronized (sbbPool) {
    		sbbBuffer b;
    		for (int i = 0; i < sbbPool.size(); i++) {
    			b = (sbbBuffer) sbbPool.get(i);
    			if ((b.enoughSize(width, height)) && (b.olderThan(timeout))) {
    				// use this buffer
    				b.access = System.currentTimeMillis();
    				b.buffer.clear(); // this makes only sense if the byteBuffer keeps its buffer
    				return b.buffer;
    			}
    		}
    		// no buffered image found, create a new one
    		b = new sbbBuffer(width, height);
    		sbbPool.add(b);
    		return b.buffer;
    	}
    }
    
    public static serverByteBuffer exportImage(BufferedImage image, String targetExt) {
    	// generate an byte array from the given image
    	//serverByteBuffer baos = new serverByteBuffer();
    	serverByteBuffer baos = sbbFromPool(image.getWidth(), image.getHeight(), 1000);
    	try {
    		ImageIO.write(image, targetExt, baos);
    		return baos;
    	} catch (IOException e) {
    		// should not happen
    		e.printStackTrace();
    		return null;
    	}
    }
    
    public static void main(String[] args) {
        // go into headless awt mode
        System.setProperty("java.awt.headless", "true");
        
        ymageMatrix m = new ymageMatrix(200, 300, MODE_SUB, "FFFFFF");
        demoPaint(m);
        File file = new File("/Users/admin/Desktop/testimage.png");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ImageIO.write(m.getImage(), "png", fos);
            fos.close();
        } catch (IOException e) {}
        
        // open file automatically, works only on Mac OS X
        /*
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] {"/usr/bin/osascript", "-e", "open \"" + args[0] + "\""});
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */
    }
    
    
}
