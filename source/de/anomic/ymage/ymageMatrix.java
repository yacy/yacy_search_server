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
    
    public static final byte FILTER_ANTIALIASING = 0;
    public static final byte FILTER_BLUR = 1;
    public static final byte FILTER_INVERT = 2;
    
    protected int            width, height;
    private final   BufferedImage  image;
    private final   WritableRaster grid;
    private final   int[]          defaultCol;
    private final   long           backgroundCol;
    private final   byte           defaultMode;
    
    public ymageMatrix(final int width, final int height, final byte drawMode, final String backgroundColor) {
        this(width, height, drawMode, Long.parseLong(backgroundColor, 16));
    }
    
    public ymageMatrix(final int width, final int height, final byte drawMode, final long backgroundColor) {
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
        final Graphics2D gr = image.createGraphics();
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
    
    public void setColor(final long c) {
    	if (this.defaultMode == MODE_SUB) {
    		final int r = (int) (c >> 16);
            final int g = (int) ((c >> 8) & 0xff);
            final int b = (int) (c & 0xff);
            defaultCol[0] = (g + b) / 2;
            defaultCol[1] = (r + b) / 2;
            defaultCol[2] = (r + g) / 2;
    	} else {
    		defaultCol[0] = (int) (c >> 16);
            defaultCol[1] = (int) ((c >> 8) & 0xff);
            defaultCol[2] = (int) (c & 0xff);
    	}
        
    }
    
    public void setColor(final String s) {
        setColor(Long.parseLong(s, 16));
    }

    public void plot(final int x, final int y) {
    	plot (x, y, 100);
    }
    
    private final int[] cc = new int[3];
    
    public void plot(final int x, final int y, final int intensity) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        synchronized (cc) {
        	if (this.defaultMode == MODE_REPLACE) {
        		if (intensity == 100) {
        			cc[0] = defaultCol[0];
            		cc[1] = defaultCol[1];
            		cc[2] = defaultCol[2];
        		} else {
        			int[] c = new int[3];
            		c = grid.getPixel(x, y, c);
        			cc[0] = (intensity * defaultCol[0] + (100 - intensity) * c[0]) / 100;
        			cc[1] = (intensity * defaultCol[1] + (100 - intensity) * c[1]) / 100;
        			cc[2] = (intensity * defaultCol[2] + (100 - intensity) * c[2]) / 100;
        		}
        	} else if (this.defaultMode == MODE_ADD) {
        		int[] c = new int[3];
        		c = grid.getPixel(x, y, c);
        		if (intensity == 100) {
        			cc[0] = (0xff & c[0]) + defaultCol[0]; if (cc[0] > 255) cc[0] = 255;
        			cc[1] = (0xff & c[1]) + defaultCol[1]; if (cc[1] > 255) cc[1] = 255;
        			cc[2] = (0xff & c[2]) + defaultCol[2]; if (cc[2] > 255) cc[2] = 255;
        		} else {
        			cc[0] = (0xff & c[0]) + (intensity * defaultCol[0] / 100); if (cc[0] > 255) cc[0] = 255;
        			cc[1] = (0xff & c[1]) + (intensity * defaultCol[1] / 100); if (cc[1] > 255) cc[1] = 255;
        			cc[2] = (0xff & c[2]) + (intensity * defaultCol[2] / 100); if (cc[2] > 255) cc[2] = 255;
        		}
        	} else if (this.defaultMode == MODE_SUB) {
        		int[] c = new int[3];
        		c = grid.getPixel(x, y, c);
        		if (intensity == 100) {
        			cc[0] = (0xff & c[0]) - defaultCol[0]; if (cc[0] < 0) cc[0] = 0;
        			cc[1] = (0xff & c[1]) - defaultCol[1]; if (cc[1] < 0) cc[1] = 0;
        			cc[2] = (0xff & c[2]) - defaultCol[2]; if (cc[2] < 0) cc[2] = 0;
        		} else {
        			cc[0] = (0xff & c[0]) - (intensity * defaultCol[0] / 100); if (cc[0] < 0) cc[0] = 0;
        			cc[1] = (0xff & c[1]) - (intensity * defaultCol[1] / 100); if (cc[1] < 0) cc[1] = 0;
        			cc[2] = (0xff & c[2]) - (intensity * defaultCol[2] / 100); if (cc[2] < 0) cc[2] = 0;
        		}
        	}
        	grid.setPixel(x, y, cc);
        }
    }
    
    public void line(int Ax, int Ay, final int Bx, final int By) {
        // Bresenham's line drawing algorithm
        int dX = Math.abs(Bx-Ax);
        int dY = Math.abs(By-Ay);
        int Xincr, Yincr;
        if (Ax > Bx) Xincr=-1; else Xincr=1;
        if (Ay > By) Yincr=-1; else Yincr=1;
        if (dX >= dY) {
            final int dPr  = dY<<1;
            final int dPru = dPr - (dX<<1);
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
            final int dPr  = dX<<1;
            final int dPru = dPr - (dY<<1);
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
        //System.out.println("CONTROL angle = " + angle);
        //System.out.println("CONTROL x1 = " + x1 + ", x1calc = " + ((x0 + ((int) ra * Math.cos(angle)))));
        //System.out.println("CONTROL y1 = " + y1 + ", y1calc = " + ((y0 - ((int) ra * Math.sin(angle)))));
        // the points are on a circle with radius rb and rc
        final int x2 = x0 + ((int) (rb * Math.cos(angle)));
        final int y2 = y0 - ((int) (rb * Math.sin(angle)));
        final int x3 = x0 + ((int) (rc * Math.cos(angle)));
        final int y3 = y0 - ((int) (rc * Math.sin(angle)));
        setColor(lineColor);
        line(x0, y0, x3, y3);
        setColor(dotColor);
        dot(x2, y2, radius, true);
    }
    
    public int[] getColor(final int x, final int y) {
        final int[] c = new int[3];
        return grid.getPixel(x, y, c);
    }

    public void dot(final int x, final int y, final int radius, final boolean filled) {
        if (filled) {
            for (int r = radius; r >= 0; r--) ymageToolCircle.circle(this, x, y, r);
        } else {
            ymageToolCircle.circle(this, x, y, radius);
        }
    }
    
    public void arc(final int x, final int y, final int innerRadius, final int outerRadius, final int fromArc, final int toArc) {
        for (int r = innerRadius; r <= outerRadius; r++) ymageToolCircle.circle(this, x, y, r, fromArc, toArc);
    }

    public void arcLine(final int cx, final int cy, final int innerRadius, final int outerRadius, final int angle) {
        final int xi = cx + (int) (innerRadius * Math.cos(Math.PI * angle / 180));
        final int yi = cy - (int) (innerRadius * Math.sin(Math.PI * angle / 180));
        final int xo = cx + (int) (outerRadius * Math.cos(Math.PI * angle / 180));
        final int yo = cy - (int) (outerRadius * Math.sin(Math.PI * angle / 180));
        line(xi, yi, xo, yo);
    }
    
    public void arcDot(final int cx, final int cy, final int arcRadius, final int angle, final int dotRadius) {
        final int x = cx + (int) (arcRadius * Math.cos(Math.PI * angle / 180));
        final int y = cy - (int) (arcRadius * Math.sin(Math.PI * angle / 180));
        dot(x, y, dotRadius, true);
    }
    
    public void arcArc(final int cx, final int cy, final int arcRadius, final int angle, final int innerRadius, final int outerRadius, final int fromArc, final int toArc) {
        final int x = cx + (int) (arcRadius * Math.cos(Math.PI * angle / 180));
        final int y = cy - (int) (arcRadius * Math.sin(Math.PI * angle / 180));
        arc(x, y, innerRadius, outerRadius, fromArc, toArc);
    }
    
    /**
     * inserts an image into the ymageMatrix
     * @param bitmap the bitmap to be inserted
     * @param x the x value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @param y the y value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @author Marc Nause
     */
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y) {
        insertBitmap(bitmap, x, y, -1);
    }

    /**
     * inserts an image into the ymageMatrix
     * @param bitmap the bitmap to be inserted
     * @param x the x value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @param y the y value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @param filter chooses filter 
     * @author Marc Nause
     */
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y, final byte filter) {
        insertBitmap(bitmap, x, y, -1, filter);
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
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y, final int xx, final int yy) {
        insertBitmap(bitmap, x, y, bitmap.getRGB(xx, yy));
    }    

    /**
     * inserts an image into the ymageMatrix where all pixels that have the same RGB value as the
     * pixel at (xx, yy) are transparent
     * @param bitmap the bitmap to be inserted
     * @param x the x value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @param y the y value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @param xx the x value of the pixel that determines which color is transparent
     * @param yy the y value of the pixel that determines which color is transparent
     * @param filter chooses filter 
     * @author Marc Nause
     */
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y, final int xx, final int yy, final byte filter) {
        insertBitmap(bitmap, x, y, bitmap.getRGB(xx, yy), filter);
    }        
    
    /**
     * inserts an image into the ymageMatrix where all pixels that have a special RGB value
     * pixel at (xx, yy) are transparent
     * @param bitmap the bitmap to be inserted
     * @param x the x value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @param y the y value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @param rgb the RGB value that will be transparent
     * @author Marc Nause
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
                if (j+x >= 0 && i+y >= 0 && i+y < heightTgt && j+x < widthTgt) {
                    rgb = bitmap.getRGB(j, i);
                    if (rgb != transRGB) {
                        image.setRGB(j+x, i+y, rgb);
                    }
                }
            }
        }
    }
    
    /**
     * inserts an image into the ymageMatrix where all pixels that have a special RGB value
     * pixel at (xx, yy) are transparent
     * @param bitmap the bitmap to be inserted
     * @param x the x value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @param y the y value of the upper left coordinate in the ymageMatrix where the bitmap will be placed
     * @param rgb the RGB value that will be transparent
     * @param filter chooses filter 
     * @author Marc Nause
     */
    public void insertBitmap(final BufferedImage bitmap, final int x, final int y, final int transRGB, final byte filter) {
        insertBitmap(bitmap, x, y, transRGB);

        final int bitmapWidth = bitmap.getWidth();
        final int bitmapHeight = bitmap.getHeight();        
        
        if (filter == FILTER_ANTIALIASING) {
            
            int transX = -1;
            int transY = -1;
            final int imageWidth = image.getWidth();
            final int imageHeight = image.getHeight();
            
            // find first pixel in bitmap that equals transRGB
            // and also lies in area of image that will be covered by bitmap
            int i = 0;
            int j = 0;
            final boolean found = false;
            while ((i < bitmapWidth) && (i + x < imageWidth) && !found) {
                while ((j < bitmapHeight) && (j + y < imageHeight) && !found) {
                    if (bitmap.getRGB(i, j) == transRGB) {
                        transX = i;
                        transY = j;
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
     * antialiasing filter for a square part of the ymageMatrix
     * @param lox x value for left upper coordinate
     * @param loy y value for left upper coordinate
     * @param rux x value for right lower coordinate
     * @param ruy y value for right lower coordinate
     * @param rgb color of background
     * @author Marc Nause
     */
    public void antialiasing(final int lox, final int loy, final int rux, final int ruy, final int bgcolor) {
        filter(lox, loy, rux, ruy, FILTER_ANTIALIASING, bgcolor);
    }

    /**
     * blur filter for a square part of the ymageMatrix
     * @param lox x value for left upper coordinate
     * @param loy y value for left upper coordinate
     * @param rux x value for right lower coordinate
     * @param ruy y value for right lower coordinate
     * @author Marc Nause
     */
    public void blur(final int lox, final int loy, final int rux, final int ruy) {
        filter(lox, loy, rux, ruy, FILTER_BLUR, -1);
    }

    /**
     * invert filter for a square part of the ymageMatrix
     * @param lox x value for left upper coordinate
     * @param loy y value for left upper coordinate
     * @param rux x value for right lower coordinate
     * @param ruy y value for right lower coordinate
     * @author Marc Nause
     */
    public void invert(final int lox, final int loy, final int rux, final int ruy) {
        filter(lox, loy, rux, ruy, FILTER_INVERT, -1);
    }
    
    /**
     * filter for a square part of the ymageMatrix
     * @param lox x value for left upper coordinate
     * @param loy y value for left upper coordinate
     * @param rux x value for right lower coordinate
     * @param ruy y value for right lower coordinate
     * @param filter chooses filter 
     * @author Marc Nause
     */
    private void filter(int lox, int loy, int rux, int ruy, final byte filter, final int bgcolor) {

        // taking care that all values are legal
        if (lox < 0) { lox = 0; }
        if (loy < 0) { loy = 0; }
        if (rux < 0) { rux = 0; }
        if (ruy < 0) { ruy = 0; }
        if (lox > width) { lox = width - 1; }
        if (loy > height){ loy = height - 1; }
        if (rux > width) { rux = width - 1; }
        if (ruy > height){ ruy = height - 1; }        
        if (lox > rux) {
            final int tmp = lox;
            lox = rux;
            rux = tmp;
        }
        if (loy > ruy) {
            final int tmp = loy;
            loy = ruy;
            ruy = tmp;
        }
  
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
                
                if (filter == FILTER_ANTIALIASING || filter == FILTER_BLUR) {
                    // taking samples from neighbours of pixel
                    if (i > lox) {
                        rgb = image.getRGB(i - 1, j);
                        if (rgb == bgcolor) {
                            border = true;
                        }
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }
                    if (j > loy) {
                        rgb = image.getRGB(i, j - 1);
                        if (rgb == bgcolor) {
                            border = true;
                        }
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }
                    if (i < width - 1) {
                        rgb = image.getRGB(i + 1, j);
                        if (rgb == bgcolor) {
                            border = true;
                        }
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }
                    if (i < height - 1) {
                        rgb = image.getRGB(i, j + 1);
                        if (rgb == bgcolor) {
                            border = true;
                        }
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
                if ((filter == FILTER_ANTIALIASING && border) || (filter == FILTER_BLUR)) {
                    rgbR += (rgb >> 16 & 0xff);
                    rgbG += (rgb >> 8 & 0xff);
                    rgbB += (rgb & 0xff);
                    numberOfNeighbours++;                    
                    border = false;
                } 
                // set to value of pixel => keep value
                else if (filter == FILTER_ANTIALIASING) {
                    rgbR = (rgb >> 16 & 0xff);
                    rgbG = (rgb >> 8 & 0xff);
                    rgbB = (rgb & 0xff);
                    numberOfNeighbours = 1;
                }
                // set value of pixel to inverted value (using XOR)
                else if (filter == FILTER_INVERT) {
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
    
    public static void demoPaint(final ymageMatrix m) {
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
    	public sbbBuffer(final int width, final int height) {
    		this.buffer = new serverByteBuffer();
    		this.access = System.currentTimeMillis();
    		this.pixel = width * height;
    	}
    	public boolean enoughSize(final int width, final int height) {
    		return this.pixel >= width * height;
    	}
    	public boolean olderThan(final long timeout) {
    		return System.currentTimeMillis() - this.access > timeout;
    	}
    }
    private static final ArrayList<sbbBuffer> sbbPool = new ArrayList<sbbBuffer>();
    private static serverByteBuffer sbbFromPool(final int width, final int height, final long timeout) {
    	// returns an Image object from the image pool
    	// if the pooled Image was created recently (before timeout), it is not used
    	synchronized (sbbPool) {
    		sbbBuffer b;
    		for (int i = 0; i < sbbPool.size(); i++) {
    			b = sbbPool.get(i);
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
    
    public static serverByteBuffer exportImage(final BufferedImage image, final String targetExt) {
    	// generate an byte array from the given image
    	//serverByteBuffer baos = new serverByteBuffer();
    	final serverByteBuffer baos = sbbFromPool(image.getWidth(), image.getHeight(), 1000);
    	try {
    		ImageIO.write(image, targetExt, baos);
    		return baos;
    	} catch (final IOException e) {
    		// should not happen
    		e.printStackTrace();
    		return null;
    	}
    }
    
    public static void main(final String[] args) {
        // go into headless awt mode
        System.setProperty("java.awt.headless", "true");
        
        final ymageMatrix m = new ymageMatrix(200, 300, MODE_SUB, "FFFFFF");
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
