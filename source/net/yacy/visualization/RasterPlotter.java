// RasterPlotter.java 
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.09.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.visualization;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.ByteBuffer;


public class RasterPlotter {
    
    public static final double PI180 = Math.PI / 180.0d;
    
    // colors regarding RGB Color Model
    public static final long RED    = 0xFF0000;
    public static final long GREEN  = 0x00FF00;
    public static final long BLUE   = 0x0000FF;
    public static final long GREY   = 0x888888;
    
    public static enum DrawMode {
        MODE_REPLACE, MODE_ADD, MODE_SUB;
    }
    
    public static enum FilterMode {
        FILTER_ANTIALIASING, FILTER_BLUR, FILTER_INVERT;
    }
    
    protected final int            width, height;
    private final   int[]          cc;
    private   BufferedImage  image;
    private final   WritableRaster grid;
    private         int            defaultColR, defaultColG, defaultColB;
    private final   long           backgroundCol;
    private         DrawMode       defaultMode;
    
    public RasterPlotter(final int width, final int height, final DrawMode drawMode, final String backgroundColor) {
        this(width, height, drawMode, Long.parseLong(backgroundColor, 16));
    }
    
    public RasterPlotter(final int width, final int height, final DrawMode drawMode, final long backgroundColor) {
        this.cc = new int[3];
        this.width = width;
        this.height = height;
        this.backgroundCol = backgroundColor;
        this.defaultColR = 0xFF;
        this.defaultColG = 0xFF;
        this.defaultColB = 0xFF;
        this.defaultMode = drawMode;
        try {
            this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        } catch (OutOfMemoryError e) {
            this.image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            //throw new RuntimeException(RasterPlotter.class.getSimpleName() + ": not enough memory (" + MemoryControl.available() + ") available");
        }
        this.clear();
        this.grid = image.getRaster();
    }

    /**
     * Deletes all pixels of image and sets them to previously defined
     * background color.
     */
    public final void clear() {
    	// fill grid with background color
        final int bgR = (int) (this.backgroundCol >> 16);
        final int bgG = (int) ((this.backgroundCol >> 8) & 0xff);
        final int bgB = (int) (this.backgroundCol & 0xff);

        final Graphics2D gr = image.createGraphics();
        gr.setBackground(new Color(bgR, bgG, bgB));
        gr.clearRect(0, 0, width, height);
    }
    
    public void setDrawMode(final DrawMode drawMode) {
        this.defaultMode = drawMode;
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
    
    public static boolean darkColor(final String s) {
        return darkColor(Long.parseLong(s, 16));
    }
    
    public static boolean darkColor(final long c) {
        final int r = (int) (c >> 16);
        final int g = (int) ((c >> 8) & 0xff);
        final int b = (int) (c & 0xff);
        return r + g + b < 384;
    }
    
    public void setColor(final long c) {
    	if (this.defaultMode == DrawMode.MODE_SUB) {
            final int r = (int) (c >> 16);
            final int g = (int) ((c >> 8) & 0xff);
            final int b = (int) (c & 0xff);
            defaultColR = (g + b) >>> 1; // / 2;
            defaultColG = (r + b) >>> 1; // / 2;
            defaultColB = (r + g) >>> 1; // / 2;
    	} else {
            defaultColR = (int) (c >> 16);
            defaultColG = (int) ((c >> 8) & 0xff);
            defaultColB = (int) (c & 0xff);
    	}
        
    }
    
    public void setColor(final String s) {
        setColor(Long.parseLong(s, 16));
    }

    public void plot(final int x, final int y) {
    	plot(x, y, 100);
    }

    public void plot(final int x, final int y, final int intensity) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        if (this.defaultMode == DrawMode.MODE_REPLACE) {
            if (intensity == 100) synchronized (cc) {
                cc[0] = defaultColR;
                cc[1] = defaultColG;
                cc[2] = defaultColB;
                grid.setPixel(x, y, cc);
            } else synchronized (cc) {
                final int[] c = grid.getPixel(x, y, cc);
                c[0] = (intensity * defaultColR + (100 - intensity) * c[0]) / 100;
                c[1] = (intensity * defaultColG + (100 - intensity) * c[1]) / 100;
                c[2] = (intensity * defaultColB + (100 - intensity) * c[2]) / 100;
                grid.setPixel(x, y, c);
            }
        } else if (this.defaultMode == DrawMode.MODE_ADD) synchronized (cc) {
            final int[] c = grid.getPixel(x, y, cc);
            if (intensity == 100) {
                c[0] = (0xff & c[0]) + defaultColR; if (cc[0] > 255) cc[0] = 255;
                c[1] = (0xff & c[1]) + defaultColG; if (cc[1] > 255) cc[1] = 255;
                c[2] = (0xff & c[2]) + defaultColB; if (cc[2] > 255) cc[2] = 255;
            } else {
                c[0] = (0xff & c[0]) + (intensity * defaultColR / 100); if (cc[0] > 255) cc[0] = 255;
                c[1] = (0xff & c[1]) + (intensity * defaultColG / 100); if (cc[1] > 255) cc[1] = 255;
                c[2] = (0xff & c[2]) + (intensity * defaultColB / 100); if (cc[2] > 255) cc[2] = 255;
            }
            grid.setPixel(x, y, c);
        } else if (this.defaultMode == DrawMode.MODE_SUB) synchronized (cc) {
            final int[] c = grid.getPixel(x, y, cc);
            if (intensity == 100) {
                c[0] = (0xff & c[0]) - defaultColR; if (cc[0] < 0) cc[0] = 0;
                c[1] = (0xff & c[1]) - defaultColG; if (cc[1] < 0) cc[1] = 0;
                c[2] = (0xff & c[2]) - defaultColB; if (cc[2] < 0) cc[2] = 0;
            } else {
                c[0] = (0xff & c[0]) - (intensity * defaultColR / 100); if (cc[0] < 0) cc[0] = 0;
                c[1] = (0xff & c[1]) - (intensity * defaultColG / 100); if (cc[1] < 0) cc[1] = 0;
                c[2] = (0xff & c[2]) - (intensity * defaultColB / 100); if (cc[2] < 0) cc[2] = 0;
            }
            grid.setPixel(x, y, c);
        }
    }

    public void line(int Ax, int Ay, final int Bx, final int By, final int intensity) {
        line(Ax, Ay, Bx, By, null, intensity, null, -1, -1, -1, -1, false);
    }
    
    public void line(
            int Ax, int Ay, final int Bx, final int By,
            final String colorLine, final int intensityLine,
            final String colorDot, final int intensityDot, final int dotDist, final int dotPos, final int dotRadius, final boolean dotFilled
            ) {
        // Bresenham's line drawing algorithm
        int dX = Math.abs(Bx-Ax);
        int dY = Math.abs(By-Ay);
        final int Xincr = (Ax > Bx) ? -1 : 1;
        final int Yincr = (Ay > By) ? -1 : 1;
        int dotc = 0;
        if (dX >= dY) {
            final int dPr  = dY<<1;
            final int dPru = dPr - (dX<<1);
            int P = dPr - dX;
            for (; dX >= 0; dX--) {
                if (colorLine != null) this.setColor(colorLine);
                plot(Ax, Ay, intensityLine);
                if (dotc == dotPos) {
                    if (colorDot != null) this.setColor(colorDot);
                    if (dotRadius == 0) this.plot(Ax, Ay, intensityDot);
                    else if (dotRadius > 0) this.dot(Ax, Ay, dotRadius, dotFilled, intensityDot);
                }
                dotc++;
                if (dotc == dotDist) dotc = 0;
                if (P > 0) {
                    Ax += Xincr;
                    Ay += Yincr;
                    P += dPru;
                } else {
                    Ax += Xincr;
                    P += dPr;
                }
            }
        } else {
            final int dPr  = dX<<1;
            final int dPru = dPr - (dY<<1);
            int P = dPr - dY;
            for (; dY >= 0; dY--) {
                if (colorLine != null) this.setColor(colorLine);
                plot(Ax, Ay, intensityLine);
                if (dotc == dotPos) {
                    if (colorDot != null) this.setColor(colorDot);
                    if (dotRadius == 0) this.plot(Ax, Ay, intensityDot);
                    else if (dotRadius > 0) this.dot(Ax, Ay, dotRadius, dotFilled, intensityDot);
                }
                dotc++;
                if (dotc == dotDist) dotc = 0;
                if (P > 0) {
                    Ax += Xincr;
                    Ay += Yincr;
                    P += dPru;
                } else {
                    Ay += Yincr;
                    P += dPr;
                }
            }
        }
    }
    
    public void lineDot(final int x0, final int y0, final int x1, final int y1, final int radius, final int distance, final String lineColor, final String dotColor) {
        lineDot(x0, y0, x1, y1, radius, distance, Long.parseLong(lineColor, 16), Long.parseLong(dotColor, 16));
    }
    
    public void lineDot(final int x0, final int y0, final int x1, final int y1, final int radius, final int distance, final long lineColor, final long dotColor) {
        // draw a line with a dot at the end.
        // the radius value is the radius of the dot
        // the distance value is the distance of the dot border to the endpoint
        
        // compute first the angle of the line between the points
        final double angle = (x1 - x0 > 0) ? Math.atan(((double) (y0 - y1)) / ((double) (x1 - x0))) : Math.PI - Math.atan(((double) (y0 - y1)) / ((double) (x0 - x1)));
        // now find two more points in between
        // first calculate the radius' of the points
        final double ra = Math.sqrt(((x0 - x1) * (x0 - x1) + (y0 - y1) * (y0 - y1))); // from a known point x1, y1
        final double rb = ra - radius - distance;
        final double rc = rb - radius;
        // the points are on a circle with radius rb and rc
        final int x2 = x0 + ((int) (rb * Math.cos(angle)));
        final int y2 = y0 - ((int) (rb * Math.sin(angle)));
        final int x3 = x0 + ((int) (rc * Math.cos(angle)));
        final int y3 = y0 - ((int) (rc * Math.sin(angle)));
        setColor(lineColor);
        line(x0, y0, x3, y3, 100);
        setColor(dotColor);
        dot(x2, y2, radius, true, 100);
    }
    
    public int[] getColor(final int x, final int y) {
        final int[] c = new int[3];
        return grid.getPixel(x, y, c);
    }

    public void dot(final int x, final int y, final int radius, final boolean filled, final int intensity) {
        if (filled) {
            for (int r = radius; r >= 0; r--) {
                CircleTool.circle(this, x, y, r, intensity);
            }
        } else {
            CircleTool.circle(this, x, y, radius, intensity);
        }
    }
    
    public void arc(final int x, final int y, final int innerRadius, final int outerRadius, final int intensity) {
        for (int r = innerRadius; r <= outerRadius; r++) {
            CircleTool.circle(this, x, y, r, intensity);
        }
    }
    
    public void arc(final int x, final int y, final int innerRadius, final int outerRadius, final int fromArc, final int toArc) {
        for (int r = innerRadius; r <= outerRadius; r++) {
            CircleTool.circle(this, x, y, r, fromArc, toArc);
        }
    }

    public void arcLine(final int cx, final int cy, final int innerRadius, final int outerRadius, final int angle, final boolean in,
            final String colorLine, final String colorDot, final int dotDist, final int dotPos, final int dotRadius, final boolean dotFilled) {
        final double a = PI180 * ((double) angle);
        final double cosa = Math.cos(a);
        final double sina = Math.sin(a);
        final int xi = cx + (int) (innerRadius * cosa);
        final int yi = cy - (int) (innerRadius * sina);
        final int xo = cx + (int) (outerRadius * cosa);
        final int yo = cy - (int) (outerRadius * sina);
        //line(xi, yi, xo, yo, 100);
        if (in) {
            line(
                    xo, yo, xi, yi, 
                    colorLine, 100,
                    colorDot, 100, dotDist, dotPos, dotRadius, dotFilled
                   );
        } else {
            line(
                    xi, yi, xo, yo,
                    colorLine, 100,
                    colorDot, 100, dotDist, dotPos, dotRadius, dotFilled
                   );
        }
    }
    
    public void arcDot(final int cx, final int cy, final int arcRadius, final int angle, final int dotRadius) {
        final double a = PI180 * ((double) angle);
        final int x = cx + (int) (arcRadius * Math.cos(a));
        final int y = cy - (int) (arcRadius * Math.sin(a));
        dot(x, y, dotRadius, true, 100);
    }
    
    public void arcConnect(final int cx, final int cy, final int arcRadius, final int angle1, final int angle2, final boolean in,
            final String colorLine, final int intensityLine,
            final String colorDot, final int intensityDot, final int dotDist, final int dotPos, final int dotRadius, final boolean dotFilled) {
        final double a1 = PI180 * ((double) angle1);
        final double a2 = PI180 * ((double) angle2);
        final int x1 = cx + (int) (arcRadius * Math.cos(a1));
        final int y1 = cy - (int) (arcRadius * Math.sin(a1));
        final int x2 = cx + (int) (arcRadius * Math.cos(a2));
        final int y2 = cy - (int) (arcRadius * Math.sin(a2));
        if (in) {
            line(x1, y1, x2, y2,
                colorLine, intensityLine,
                colorDot, intensityDot, dotDist, dotPos, dotRadius, dotFilled);
        } else {
            line(x2, y2, x1, y1, 
                colorLine, intensityLine,
                colorDot, intensityDot, dotDist, dotPos, dotRadius, dotFilled);
        }
    }
    
    public void arcArc(final int cx, final int cy, final int arcRadius, final int angle,
            final int innerRadius, final int outerRadius, final int intensity) {
        final double a = PI180 * ((double) angle);
        final int x = cx + (int) (arcRadius * Math.cos(a));
        final int y = cy - (int) (arcRadius * Math.sin(a));
        arc(x, y, innerRadius, outerRadius, intensity);
    }
    
    public void arcArc(final int cx, final int cy, final int arcRadius, final int angle,
            final int innerRadius, final int outerRadius, final int fromArc, final int toArc) {
        final double a = PI180 * ((double) angle);
        final int x = cx + (int) (arcRadius * Math.cos(a));
        final int y = cy - (int) (arcRadius * Math.sin(a));
        arc(x, y, innerRadius, outerRadius, fromArc, toArc);
    }
    
    /**
     * inserts image
     * @param bitmap bitmap to be inserted
     * @param x x-value of upper left coordinate where bitmap will be placed
     * @param y y-value of upper left coordinate where bitmap will be placed
     */
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y) {
        insertBitmap(bitmap, x, y, -1);
    }

    /**
     * inserts image
     * @param bitmap bitmap to be inserted
     * @param x x-value of upper left coordinate where bitmap will be placed
     * @param y y-value of upper left coordinate where bitmap will be placed
     * @param filter chooses filter
     */
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y, final FilterMode filter) {
        insertBitmap(bitmap, x, y, -1, filter);
    }    
    
    /**
     * inserts image where all pixels which have the same RGB value as the
     * pixel at (xx, yy) are transparent
     * @param bitmap bitmap to be inserted
     * @param x x-value of upper left coordinate where bitmap will be placed
     * @param y y-value of upper left coordinate where bitmap will be placed
     * @param xx x-value of pixel that determines which color is transparent
     * @param yy y-value of pixel that determines which color is transparent
     */
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y, final int xx, final int yy) {
        insertBitmap(bitmap, x, y, bitmap.getRGB(xx, yy));
    }    

    /**
     * inserts image  where all pixels that have the same RGB value as the
     * pixel at (xx, yy) are transparent
     * @param bitmap bitmap to be inserted
     * @param x x-value of upper left coordinate where bitmap will be placed
     * @param y y-value of upper left coordinate where bitmap will be placed
     * @param xx x-value of pixel that determines which color is transparent
     * @param yy y-value of pixel that determines which color is transparent
     * @param filter filter to be applied
     */
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y, final int xx, final int yy, final FilterMode filter) {
        insertBitmap(bitmap, x, y, bitmap.getRGB(xx, yy), filter);
    }        
    
    /**
     * inserts image  where all pixels that have the same RGB value as the
     * pixel at (xx, yy) are transparent
     * @param bitmap bitmap to be inserted
     * @param x x-value of upper left coordinate where bitmap will be placed
     * @param y y-value of upper left coordinate where bitmap will be placed
     * @param rgb RGB value which will be transparent
     */      
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y, final int transRGB) {
        final int heightSrc = bitmap.getHeight();
        final int widthSrc  = bitmap.getWidth();
        final int heightTgt = height;
        final int widthTgt  = width;

        int rgb;
        for (int i = 0; i < heightSrc; i++) {
            for (int j = 0; j < widthSrc; j++) {
                // pixel in legal area?
                if (j + x >= 0 && i + y >= 0 && i + y < heightTgt && j + x < widthTgt) {
                    rgb = bitmap.getRGB(j, i);
                    if (rgb != transRGB) {
                        image.setRGB(j + x, i + y, rgb);
                    }
                }
            }
        }
    }
    
    /**
     * inserts image where all pixels that have a special RGB value
     * pixel at (xx, yy) are transparent
     * @param bitmap bitmap to be inserted
     * @param x x-value of upper left coordinate where bitmap will be placed
     * @param y y-value of upper left coordinate where bitmap will be placed
     * @param rgb RGB value which will be transparent
     * @param filter filter to be applied
     */
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y, final int transRGB, final FilterMode filter) {
        insertBitmap(bitmap, x, y, transRGB);

        final int bitmapWidth = bitmap.getWidth();
        final int bitmapHeight = bitmap.getHeight();        
        
        if (filter == FilterMode.FILTER_ANTIALIASING) {
            
            int transX = -1;
            int transY = -1;
            final int imageWidth = image.getWidth();
            final int imageHeight = image.getHeight();
            
            // find first pixel in bitmap that equals transRGB
            // and also lies in area of image that will be covered by bitmap
            int i = 0;
            int j = 0;
            boolean found = false;
            while ((i < bitmapWidth) && (i + x < imageWidth) && !found) {
                while ((j < bitmapHeight) && (j + y < imageHeight) && !found) {
                    if (bitmap.getRGB(i, j) == transRGB) {
                        transX = i;
                        transY = j;
                        found = true;
                    }
                    j++;
                }
                i++;
            }

            // if there is a transparent pixel in the bitmap that covers an area
            // of the image, the fiter will be used. If no such pixel has been found that
            // means that there either is no transparent pixel in the bitmap or part
            // of the bitmap that covers part of tha image is not within the borders of
            // the image (i.e. bitmap is larger than image)
            if (transX != -1) {
                filter(x - 1, y - 1, x + bitmapWidth, y + bitmapHeight, filter, image.getRGB(transX + x, transY + y));
            }

        } else {
            filter(x - 1, y - 1, x + bitmapWidth, y + bitmapHeight, filter, -1);
        }

    }
    
    /**
     * antialiasing filter for a square part of the image
     * @param ulx x-value for upper left coordinate
     * @param uly y-value for upper left coordinate
     * @param lrx x-value for lower right coordinate
     * @param lry y-value for lower right coordinate
     * @param rgb color of background
     */
    public void antialiasing(final int ulx, final int uly, final int lrx, final int lry, final int bgcolor) {
        filter(ulx, uly, lrx, lry, FilterMode.FILTER_ANTIALIASING, bgcolor);
    }

    /**
     * blur filter for a square part of the image
     * @param ulx x-value for upper left coordinate
     * @param uly y-value for upper left coordinate
     * @param lrx x-value for lower right coordinate
     * @param lry y-value for lower right coordinate
     */
    public void blur(final int ulx, final int uly, final int lrx, final int lry) {
        filter(ulx, uly, lrx, lry, FilterMode.FILTER_BLUR, -1);
    }

    /**
     * invert filter for a square part of the image
     * @param ulx x-value for upper left coordinate
     * @param uly y-value for upper left coordinate
     * @param lrx x-value for lower right coordinate
     * @param lry y-value for lower right coordinate
     */
    public void invert(final int ulx, final int uly, final int lrx, final int lry) {
        filter(ulx, uly, lrx, lry, FilterMode.FILTER_INVERT, -1);
    }
    
    /**
     * filter for a square part of the ymageMatrix
     * @param ulx x-value for upper left coordinate
     * @param uly y-value for upper left coordinate
     * @param lrx x-value for lower right coordinate
     * @param lry y-value for lower right coordinate
     * @param filter chooses filter 
     */
    private void filter(final int ulx, final int uly, final int lrx, final int lry, final FilterMode filter, final int bgcolor) {

        // taking care that all values are legal
        final int lox = Math.min(Math.max(Math.min(ulx, lrx), 0), width - 1);
        final int loy = Math.min(Math.max(Math.min(uly, lry), 0), height -1);
        final int rux = Math.min(Math.max(Math.max(ulx, lrx), 0), width - 1);
        final int ruy = Math.min(Math.max(Math.max(uly, lry), 0), height -1);
  
        int numberOfNeighbours = 0;
        int rgbR = 0;
        int rgbG = 0;
        int rgbB = 0;
        int rgb = 0;
        final int width2 = rux - lox + 1;
        final int height2 = ruy - loy + 1;
        boolean border = false;
        final BufferedImage image2 = new BufferedImage(width2, height2, BufferedImage.TYPE_INT_RGB);

        for (int i = lox; i < rux + 1; i++) {
            for (int j = loy; j < ruy + 1; j++) {

                numberOfNeighbours = 0;
                rgbR = 0;
                rgbG = 0;
                rgbB = 0;

                if (filter == FilterMode.FILTER_ANTIALIASING || filter == FilterMode.FILTER_BLUR) {
                    // taking samples from neighbouring pixel
                    if (i > lox) {
                        rgb = image.getRGB(i - 1, j);
                        border = (rgb == bgcolor);
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }
                    if (j > loy) {
                        rgb = image.getRGB(i, j - 1);
                        border = border || (rgb == bgcolor);
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }
                    if (i < width - 1) {
                        rgb = image.getRGB(i + 1, j);
                        border = border || (rgb == bgcolor);
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }
                    if (i < height - 1) {
                        rgb = image.getRGB(i, j + 1);
                        border = border || (rgb == bgcolor);
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }

                }

                rgb = image.getRGB(i, j);
                
                // add value of pixel
                // in case filter is used for antialiasing this will only be done if
                // the pixel is on the edge to the background color
                if (filter == FilterMode.FILTER_ANTIALIASING && border || filter == FilterMode.FILTER_BLUR) {
                    rgbR += (rgb >> 16 & 0xff);
                    rgbG += (rgb >> 8 & 0xff);
                    rgbB += (rgb & 0xff);
                    numberOfNeighbours++;                    
                    border = false;
                } 
                // set to value of pixel => keep value
                else if (filter == FilterMode.FILTER_ANTIALIASING) {
                    rgbR = (rgb >> 16 & 0xff);
                    rgbG = (rgb >> 8 & 0xff);
                    rgbB = (rgb & 0xff);
                    numberOfNeighbours = 1;
                }
                // set value of pixel to inverted value (using XOR)
                else if (filter == FilterMode.FILTER_INVERT) {
                    rgb = rgb ^ 0xffffff;
                    rgbR = (rgb >> 16 & 0xff);
                    rgbG = (rgb >> 8 & 0xff);
                    rgbB = (rgb & 0xff);
                    numberOfNeighbours = 1;
                }

                // calculating the average
                rgbR = (rgbR / numberOfNeighbours);
                rgbG = (rgbG / numberOfNeighbours);
                rgbB = (rgbB / numberOfNeighbours);
                
                rgb = (rgbR << 16) | (rgbG << 8) | rgbB;
                
                image2.setRGB(i-lox, j-loy, rgb);
            }
        }
        
        // insert new version of area into image
        insertBitmap(image2, lox, loy);
    }
    
    public static void demoPaint(final RasterPlotter m) {
        m.setColor(GREY);
        m.line(0,  70, 100,  70, 100); PrintTool.print(m, 0,  65, 0, "Grey", -1);
        m.line(65, 0,   65, 300, 100);
        m.setColor(RED);
        m.line(0,  90, 100,  90, 100); PrintTool.print(m, 0,  85, 0, "Red", -1);
        m.line(70, 0,   70, 300, 100);
        m.setColor(GREEN);
        m.line(0, 110, 100, 110, 100); PrintTool.print(m, 0, 105, 0, "Green", -1);
        m.line(75, 0,   75, 300, 100);
        m.setColor(BLUE);
        m.line(0, 130, 100, 130, 100); PrintTool.print(m, 0, 125, 0, "Blue", -1);
        m.line(80, 0,   80, 300, 100);
    }
    
    public static ByteBuffer exportImage(final BufferedImage image, final String targetExt) {
    	// generate an byte array from the given image
    	//serverByteBuffer baos = new serverByteBuffer();
    	final ByteBuffer baos = new ByteBuffer();
    	ImageIO.setUseCache(false);
    	try {
    		ImageIO.write(image, targetExt, baos);
    		return baos;
    	} catch (final IOException e) {
    		// should not happen
    	    Log.logException(e);
    		return null;
    	}
    }
    
    public static void main(final String[] args) {
        // go into headless awt mode
        System.setProperty("java.awt.headless", "true");
        
        final RasterPlotter m = new RasterPlotter(200, 300, DrawMode.MODE_SUB, "FFFFFF");
        demoPaint(m);
        final File file = new File("/Users/admin/Desktop/testimage.png");
        try {
            final FileOutputStream fos = new FileOutputStream(file);
            ImageIO.write(m.getImage(), "png", fos);
            fos.close();
        } catch (final IOException e) {}
        
        // open file automatically, works only on Mac OS X
        /*
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] {"/usr/bin/osascript", "-e", "open \"" + args[0] + "\""});
        } catch (java.io.IOException e) {
            Log.logException(e);
        }
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Log.logException(e);
        }
        */
    }
    
    
}
