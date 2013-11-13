// RasterPlotter.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.09.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;


public class RasterPlotter {

    public static final ConcurrentLog log = new ConcurrentLog("RasterPlotter");

    public static final double PI180 = Math.PI / 180.0d;
    public static final double PI4 = Math.PI / 4.0d;
    public static final double PI2 = Math.PI / 2.0d;
    public static final double PI32 = PI2 * 3.0d;
    public static final double TL = Math.sqrt(3) / 2;

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
    private         BufferedImage  image;
    private         WritableRaster grid;
    private         int            defaultColR, defaultColG, defaultColB;
    private final   long           backgroundCol;
    private         DrawMode       defaultMode;
    private byte[]  frame;

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
            // we need our own frame buffer to get a very, very fast transformation to png because we can omit the PixedGrabber, which is up to 800 times slower
            // see: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4835595
            this.frame = new byte[width * height * 3];
            DataBuffer videoBuffer = new DataBufferByte(frame, frame.length);
            ComponentSampleModel sampleModel = new ComponentSampleModel(DataBuffer.TYPE_BYTE, width, height, 3, width * 3, new int[] {0, 1, 2});
            this.grid = Raster.createWritableRaster(sampleModel, videoBuffer, null);
            ColorModel colorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), null, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
            this.image = new BufferedImage(colorModel, this.grid, false, null);
        } catch (final OutOfMemoryError e) {
            this.frame = null;
            try {
                this.image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);
            } catch (final OutOfMemoryError ee) {
                try {
                    this.image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
                } catch (final OutOfMemoryError eee) {
                    this.image = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
                }
            }
            this.grid = this.image.getRaster();
        }
        clear();
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

        if (this.frame == null) {
            final Graphics2D gr = this.image.createGraphics();
            Color c = new Color(bgR, bgG, bgB);
            gr.setBackground(c);
            gr.clearRect(0, 0, this.width, this.height);
            gr.setColor(c);
            gr.fillRect(0, 0, this.width, this.height);
        } else {
            int p = 0;
            for (int i = 0; i < width; i++) {
                this.frame[p++] = (byte) bgR;
                this.frame[p++] = (byte) bgG;
                this.frame[p++] = (byte) bgB;
            }
            final int rw = width * 3;
            for (int i = 1; i < height; i++) {
                System.arraycopy(this.frame, 0, this.frame, i * rw, rw);
            }
        }
    }

    public void setDrawMode(final DrawMode drawMode) {
        this.defaultMode = drawMode;
    }

    public BufferedImage getImage() {
        return this.image;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
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
    
    public int[] getPixel(final int x, final int y) {
        return getPixel(x, y, new int[3]);
    }

    public int[] getPixel(final int x, final int y, int[] c) {
        if (this.frame == null) return this.grid.getPixel(x, y, c);
        int cell = (this.width * y + x) * 3;
        c[0] = this.frame[cell++] & 0xff;
        c[1] = this.frame[cell++] & 0xff;
        c[2] = this.frame[cell++] & 0xff;
        return c;
    }
    
    public void setPixel(final int x, final int y, int[] c) {
        if (this.frame == null) {
            this.grid.setPixel(x, y, c);
            return;
        }
        int cell = (this.width * y + x) * 3;
        this.frame[cell++] = (byte) c[0];
        this.frame[cell++] = (byte) c[1];
        this.frame[cell++] = (byte) c[2];
    }

    public void setColor(final long c) {
    	if (this.defaultMode == DrawMode.MODE_SUB) {
            final int r = (int) (c >> 16);
            final int g = (int) ((c >> 8) & 0xff);
            final int b = (int) (c & 0xff);
            this.defaultColR = (g + b) >>> 1; // / 2;
            this.defaultColG = (r + b) >>> 1; // / 2;
            this.defaultColB = (r + g) >>> 1; // / 2;
    	} else {
            this.defaultColR = (int) (c >> 16);
            this.defaultColG = (int) ((c >> 8) & 0xff);
            this.defaultColB = (int) (c & 0xff);
    	}
    }

    public void plot(final int x, final int y) {
    	plot(x, y, 100);
    }

    public void plot(final int x, final int y, final int intensity) {
        if ((x < 0) || (x >= this.width)) return;
        if ((y < 0) || (y >= this.height)) return;
        try {
        if (this.defaultMode == DrawMode.MODE_REPLACE) {
            if (intensity == 100) synchronized (this.cc) {
                this.cc[0] = this.defaultColR;
                this.cc[1] = this.defaultColG;
                this.cc[2] = this.defaultColB;
                setPixel(x, y, this.cc);
            } else synchronized (this.cc) {
                final int[] c = getPixel(x, y, this.cc);
                c[0] = (intensity * this.defaultColR + (100 - intensity) * c[0]) / 100;
                c[1] = (intensity * this.defaultColG + (100 - intensity) * c[1]) / 100;
                c[2] = (intensity * this.defaultColB + (100 - intensity) * c[2]) / 100;
                setPixel(x, y, c);
            }
        } else if (this.defaultMode == DrawMode.MODE_ADD) synchronized (this.cc) {
            int[] c = null;
            try {
                c = getPixel(x, y, this.cc);
            }   catch (final ArrayIndexOutOfBoundsException e) {
                // catch "Coordinate out of bounds"
                return;
            }
            if (intensity == 100) {
                c[0] = (0xff & c[0]) + this.defaultColR; if (this.cc[0] > 255) this.cc[0] = 255;
                c[1] = (0xff & c[1]) + this.defaultColG; if (this.cc[1] > 255) this.cc[1] = 255;
                c[2] = (0xff & c[2]) + this.defaultColB; if (this.cc[2] > 255) this.cc[2] = 255;
            } else {
                c[0] = (0xff & c[0]) + (intensity * this.defaultColR / 100); if (this.cc[0] > 255) this.cc[0] = 255;
                c[1] = (0xff & c[1]) + (intensity * this.defaultColG / 100); if (this.cc[1] > 255) this.cc[1] = 255;
                c[2] = (0xff & c[2]) + (intensity * this.defaultColB / 100); if (this.cc[2] > 255) this.cc[2] = 255;
            }
            setPixel(x, y, c);
        } else if (this.defaultMode == DrawMode.MODE_SUB) synchronized (this.cc) {
            int[] c = null;
            try {
                c = getPixel(x, y, this.cc);
            }   catch (final ArrayIndexOutOfBoundsException e) {
                // catch "Coordinate out of bounds"
                return;
            }
            if (intensity == 100) {
                c[0] = (0xff & c[0]) - this.defaultColR; if (this.cc[0] < 0) this.cc[0] = 0;
                c[1] = (0xff & c[1]) - this.defaultColG; if (this.cc[1] < 0) this.cc[1] = 0;
                c[2] = (0xff & c[2]) - this.defaultColB; if (this.cc[2] < 0) this.cc[2] = 0;
            } else {
                c[0] = (0xff & c[0]) - (intensity * this.defaultColR / 100); if (this.cc[0] < 0) this.cc[0] = 0;
                c[1] = (0xff & c[1]) - (intensity * this.defaultColG / 100); if (this.cc[1] < 0) this.cc[1] = 0;
                c[2] = (0xff & c[2]) - (intensity * this.defaultColB / 100); if (this.cc[2] < 0) this.cc[2] = 0;
            }
            setPixel(x, y, c);
        }
        } catch (final ArrayIndexOutOfBoundsException e) {
            log.warn(e.getMessage() + ": x = " + x + ", y = " + y);
        } // may appear when pixel coordinate is out of bounds
    }

    public void line(final int Ax, final int Ay, final int Bx, final int By, final int intensity) {
        line(Ax, Ay, Bx, By, null, intensity, null, -1, -1, -1, -1, false);
    }

    /**
     * draw a line using Bresenham's line drawing algorithm.
     * The line will be plotted together with dots on it, if wanted.
     * @param Ax
     * @param Ay
     * @param Bx
     * @param By
     * @param colorLine
     * @param intensityLine
     * @param colorDot
     * @param intensityDot
     * @param dotDist
     * @param dotPos
     * @param dotRadius
     * @param dotFilled
     */
    public void line(
            int Ax, int Ay, final int Bx, final int By,
            final Long colorLine, final int intensityLine,
            final Long colorDot, final int intensityDot, final int dotDist, final int dotPos, final int dotRadius, final boolean dotFilled
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
                    else if (dotRadius > 0) dot(Ax, Ay, dotRadius, dotFilled, intensityDot);
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
                    else if (dotRadius > 0) dot(Ax, Ay, dotRadius, dotFilled, intensityDot);
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

    /**
     * draw a line with a dot at the end
     * @param x0 start point
     * @param y0 start point
     * @param x1 end point
     * @param y1 end point
     * @param radius radius of the dot
     * @param padding the distance of the dot border to the end point
     * @param lineColor the color of the line
     * @param dotColor the color of the dot
     */
    public void lineDot(final int x0, final int y0, final int x1, final int y1, final int radius, final int padding, final long lineColor, final long dotColor) {
        final double dx = x1 - x0;                          // distance of points, x component
        final double dy = y1 - y0;                          // distance of points, y component
        final double angle = Math.atan2(dy, dx);            // the angle of the line between the points
        final double d = Math.sqrt((dx * dx + dy * dy));    // the distance between the points (Pythagoras)
        final double ddotcenter = d - radius - padding;     // distance from {x0, y0} to dot center near {x1, y1}
        final double ddotborder = ddotcenter - radius;      // distance to point {x3, y3} at border of dot center at {x2, y2}
        final double xn = Math.cos(angle);                  // normalized vector component x
        final double yn = Math.sin(angle);                  // normalized vector component y
        final int x2 = x0 + ((int) (ddotcenter * xn));      // dot center, x component
        final int y2 = y0 + ((int) (ddotcenter * yn));      // dot center, y component
        final int x3 = x0 + ((int) (ddotborder * xn));      // dot border, x component
        final int y3 = y0 + ((int) (ddotborder * yn));      // dot border, y component
        setColor(lineColor); line(x0, y0, x3, y3, 100);     // draw line from {x0, y0} to dot border
        setColor(dotColor); dot(x2, y2, radius, true, 100); // draw dot at {x2, y2}
    }

    /**
     * draw a line with an arrow at the end
     * @param x0 start point
     * @param y0 start point
     * @param x1 end point
     * @param y1 end point
     * @param sidelength the side length of the arrow tip (all 3 sides are equal)
     * @param padding the distance of the arrow tip to the end point
     * @param lineColor the color of the line
     * @param arrowColor the color of the arrow tip
     */
    public void lineArrow(final int x0, final int y0, final int x1, final int y1, final int sidelength, final int padding, final long lineColor, final long arrowColor) {
        final double dx = x1 - x0;                          // distance of points, x component
        final double dy = y1 - y0;                          // distance of points, y component
        final double angle = Math.atan2(dy, dx);            // the angle of the line between the points
        final double d = Math.sqrt((dx * dx + dy * dy));    // the distance between the points (Pythagoras)
        final double arrowtip = d - padding;                // the distance from {x0, y0} to the arrow tip
        final double arrowlength = TL * sidelength;         // the length of the arrow (distance from base to tip)
        final double arrowbase = arrowtip - arrowlength;    // the distance from {x0, y0} to the arrow base
        final double xn = Math.cos(angle);                  // normalized vector component x
        final double yn = Math.sin(angle);                  // normalized vector component y
        final int xt = x0 + ((int) (arrowtip * xn));        // arrow tip point component x
        final int yt = y0 + ((int) (arrowtip * yn));        // arrow tip point component y
        final double xb = x0 + arrowbase * xn;              // arrow base point component x
        final double yb = y0 + arrowbase * yn;              // arrow base point component y
        final double sl2 = sidelength / 2.0;                // half of the side length
        final double xk = sl2 * Math.cos(angle + PI2);      // point at 90 degree on arrow direction to left side, vector component x
        final double yk = sl2 * Math.sin(angle + PI2);      // point at 90 degree on arrow direction to left side, vector component y
        final int x2 = (int) (xb + xk);
        final int y2 = (int) (yb + yk);
        final int x3 = (int) (xb - xk);
        final int y3 = (int) (yb - yk);
        setColor(lineColor);
        line(x0, y0, (int) xb, (int) yb, 100);              // draw line from {x0, y0} to arrow base
        setColor(arrowColor);
        line(x2, y2, x3, y3, 100); // base line
        line(x2, y2, xt, yt, 100); // left line
        line(x3, y3, xt, yt, 100); // right line
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

    /**
     * draw a portion of a line from the center of a circle
     * @param cx center of circle, x
     * @param cy center of circle, y
     * @param innerRadius inner radius of line
     * @param outerRadius outer radius of line
     * @param angle angle within the circle for the line
     * @param in direction, if true: inward. This is the moving direction of dots, if dotRadius is alternated from 0 to 360
     * @param colorLine the color of the line
     * @param colorDot the color of the dot
     * @param dotDist the distance of two dots
     * @param dotPos the start position of the first dot
     * @param dotRadius the radius of the dot
     * @param dotFilled if true: dot is filled.
     */
    public void arcLine(final int cx, final int cy, final int innerRadius, final int outerRadius, final double angle, final boolean in,
            final Long colorLine, final Long colorDot, final int dotDist, final int dotPos, final int dotRadius, final boolean dotFilled) {
        final double a = PI180 * angle;
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

    public void arcDot(final int cx, final int cy, final int arcRadius, final double angle, final int dotRadius) {
        final double a = PI180 * angle;
        final int x = cx + (int) (arcRadius * Math.cos(a));
        final int y = cy - (int) (arcRadius * Math.sin(a));
        dot(x, y, dotRadius, true, 100);
    }

    /**
     * draw a connecting line between two points o a circle
     * @param cx center of circle, x
     * @param cy center of circle, y
     * @param arcRadius radius of circle
     * @param angle1 position of dot 1 on circle
     * @param angle2 position of dot 2 on circle
     * @param in direction of dots on line; in=true means: inwards
     * @param colorLine
     * @param intensityLine
     * @param colorDot
     * @param intensityDot
     * @param dotDist
     * @param dotPos
     * @param dotRadius
     * @param dotFilled
     */
    public void arcConnect(final int cx, final int cy, final int arcRadius, final double angle1, final double angle2, final boolean in,
            final Long colorLine, final int intensityLine,
            final Long colorDot, final int intensityDot, final int dotDist, final int dotPos, final int dotRadius, final boolean dotFilled,
            final String message, final Long colorMessage) {
        final double a1 = PI180 * angle1;
        final double a2 = PI180 * angle2;
        // find positions of points
        final int x1 = cx + (int) (arcRadius * Math.cos(a1));
        final int y1 = cy - (int) (arcRadius * Math.sin(a1));
        final int x2 = cx + (int) (arcRadius * Math.cos(a2));
        final int y2 = cy - (int) (arcRadius * Math.sin(a2));
        // draw the line
        if (in) {
            line(x1, y1, x2, y2,
                colorLine, intensityLine,
                colorDot, intensityDot, dotDist, dotPos, dotRadius, dotFilled);
        } else {
            line(x2, y2, x1, y1,
                colorLine, intensityLine,
                colorDot, intensityDot, dotDist, dotPos, dotRadius, dotFilled);
        }
        // draw a name on the line
        if (message != null && message.length() > 0) {
            this.setColor(colorMessage);
            int xm = (x1 + 5 * x2) / 6;
            int ym = (y1 + 5 * y2) / 6;
            if (ym < cy) ym += 6; else ym -=6;
            if (xm < cx) xm += 6; else xm -=6;
            if (xm > cx) xm -= 6 * message.length();
            PrintTool.print(this, xm, ym, 0, message.toUpperCase(), -1);
        }
    }

    public void arcArc(final int cx, final int cy, final int arcRadius, final double angle,
            final int innerRadius, final int outerRadius, final int intensity) {
        final double a = PI180 * angle;
        final int x = cx + (int) (arcRadius * Math.cos(a));
        final int y = cy - (int) (arcRadius * Math.sin(a));
        arc(x, y, innerRadius, outerRadius, intensity);
    }

    public void arcArc(final int cx, final int cy, final int arcRadius, final double angle,
            final int innerRadius, final int outerRadius, final int fromArc, final int toArc) {
        final double a = PI180 * angle;
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
        final int heightTgt = this.height;
        final int widthTgt  = this.width;

        int rgb;
        for (int i = 0; i < heightSrc; i++) {
            for (int j = 0; j < widthSrc; j++) {
                // pixel in legal area?
                if (j + x >= 0 && i + y >= 0 && i + y < heightTgt && j + x < widthTgt) {
                    rgb = bitmap.getRGB(j, i);
                    if (rgb != transRGB) {
                        this.image.setRGB(j + x, i + y, rgb);
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
            final int imageWidth = this.image.getWidth();
            final int imageHeight = this.image.getHeight();

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
                filter(x - 1, y - 1, x + bitmapWidth, y + bitmapHeight, filter, this.image.getRGB(transX + x, transY + y));
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
        final int lox = Math.min(Math.max(Math.min(ulx, lrx), 0), this.width - 1);
        final int loy = Math.min(Math.max(Math.min(uly, lry), 0), this.height -1);
        final int rux = Math.min(Math.max(Math.max(ulx, lrx), 0), this.width - 1);
        final int ruy = Math.min(Math.max(Math.max(uly, lry), 0), this.height -1);

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
                        rgb = this.image.getRGB(i - 1, j);
                        border = (rgb == bgcolor);
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }
                    if (j > loy) {
                        rgb = this.image.getRGB(i, j - 1);
                        border = border || (rgb == bgcolor);
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }
                    if (i < this.width - 1) {
                        rgb = this.image.getRGB(i + 1, j);
                        border = border || (rgb == bgcolor);
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }
                    if (i < this.height - 1) {
                        rgb = this.image.getRGB(i, j + 1);
                        border = border || (rgb == bgcolor);
                        rgbR += rgb >> 16 & 0xff;
                        rgbG += rgb >> 8 & 0xff;
                        rgbB += rgb & 0xff;
                        numberOfNeighbours++;
                    }

                }

                rgb = this.image.getRGB(i, j);

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

    public BufferedImage toIndexed() {
        Set<Integer> colors = new TreeSet<Integer>();
        int[] c = new int[3];
        for (int y = this.getHeight() - 1; y >= 0; y--) {
            for (int x = this.getWidth() - 1; x >= 0; x--) {
                c = getPixel(x, y, c);
                colors.add((c[0]<<16)|(c[1]<<8)|c[2]);
            }
        }
        int[] cmap = new int[colors.size()];
        int i = 0;
        for (Integer cc: colors) {
            cmap[i++] = cc.intValue();
            if (i > 255) break;
        }
     
        int bitCount = 1;
        while ((colors.size() - 1) >> bitCount != 0) bitCount *= 2;
     
        IndexColorModel cm = new IndexColorModel(bitCount, colors.size(), cmap, 0, DataBuffer.TYPE_BYTE, null);
        
        /*
        byte [] data = null;
        int bytesPerRow = this.getWidth()/8 + (this.getWidth()%8!=0?1:0);
        data = new byte[this.getHeight() * bytesPerRow];  
        DataBuffer db = new DataBufferByte(data, data.length);  
        WritableRaster wr = Raster.createPackedRaster(db, this.getWidth(), this.getHeight(), 1, null);  
        BufferedImage dest = new BufferedImage(cm, wr, false, null);  
        */
        
        BufferedImage dest = new BufferedImage(this.getWidth(), this.getHeight(), cm.getPixelSize() < 8 ? BufferedImage.TYPE_BYTE_BINARY : BufferedImage.TYPE_BYTE_INDEXED, cm);
        dest.createGraphics().drawImage(this.getImage(), 0, 0, null);
        return dest;
    }
    
    public static BufferedImage convertToIndexed(BufferedImage src) {
        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_INDEXED);
        dest.createGraphics().drawImage(src,0,0, null);
        return dest;
    }

    public static ByteBuffer exportImage(final BufferedImage image, final String targetExt) {
    	// generate an byte array from the given image
    	final ByteBuffer baos = new ByteBuffer();
    	ImageIO.setUseCache(false); // because we write into ram here
    	try {
    		ImageIO.write(image, targetExt, baos);
    		return baos;
    	} catch (final IOException e) {
    		// should not happen
    	    ConcurrentLog.logException(e);
    		return null;
    	}
    }
    
    public ByteBuffer exportPng() {
        try {
            final ByteBuffer baos = new ByteBuffer();
            byte[] pngbytes = pngEncode(1);
            if (pngbytes == null) return null;
            baos.write(pngbytes);
            baos.flush();
            baos.close();
            return baos;
        } catch (final IOException e) {
            // should not happen
            ConcurrentLog.logException(e);
            return null;
        }
    }
    
    /**
     * save the image to a file
     * @param file the storage file
     * @param type the file type, may be i.e. 'png' or 'gif'
     * @throws IOException
     */
    public void save(File file, String type) throws IOException {
        final FileOutputStream fos = new FileOutputStream(file);
        ImageIO.write(this.image, type, fos);
        fos.close();
    }

    /**
     * show the image as JFrame on desktop
     */
    public void show() {
        JLabel label = new JLabel(new ImageIcon(this.image));
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(label);
        f.pack();
        f.setVisible(true);
    }
    
    /*
     * The following code was transformed from a library, coded by J. David Eisenberg, version 1.5, 19 Oct 2003 (C) LGPL
     * This code was very strongly transformed into the following very short method for an ultra-fast png generation.
     * These changes had been made 23.10.2012 by [MC] to the original code:
     * For the integration into YaCy this class was adopted to YaCy graphics by Michael Christen:
     * - removed alpha encoding
     * - removed not used code
     * - inlined static values
     * - inlined all methods that had been called only once
     * - moved class objects which appear after all refactoring only within a single method into this method
     * - removed a giant number of useless (obvious things) comments and empty lines to increase readability (!)
     * - new order of data computation: first compute the size of compressed deflater output,
     *   then assign an exact-sized byte[] which makes resizing afterwards superfluous
     * - after all enhancements all class objects were removed; result is just one short static method
     * - made objects final where possible
     * - removed the PixelGrabber call and replaced it with a call to this.frame which is just a byte[]
     * - added more speed woodoo like a buffer around the deflater which makes this much faster
     */

    private static final byte IHDR[] = {73, 72, 68, 82};
    private static final byte IDAT[] = {73, 68, 65, 84};
    private static final byte IEND[] = {73, 69, 78, 68};

    public final byte[] pngEncode(final int compressionLevel) throws IOException {
        if (this.frame == null) return exportImage(this.getImage(), "png").getBytes();
        final int width = image.getWidth(null);
        final int height = image.getHeight(null);
        
        final Deflater scrunch = new Deflater(compressionLevel);
        ByteBuffer outBytes = new ByteBuffer(1024);
        final OutputStream compBytes = new BufferedOutputStream(new DeflaterOutputStream(outBytes, scrunch));
        int i = 0;
        for (int row = 0; row < height; row++) {
            compBytes.write(0);
            // this replaces the whole PixelGrabber process which makes it probably more than 800x faster. See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4835595
            compBytes.write(frame, i, 3 * width);
            i += 3 * width;
        }
        compBytes.close();
        scrunch.finish();

        // finally write the result of the concurrent calculation into an DeflaterOutputStream to compress the png
        final int nCompressed = outBytes.length();
        final byte[] pngBytes = new byte[nCompressed + 57]; // yes thats the exact size, not too less, not too much. No resizing needed.
        int bytePos = writeBytes(pngBytes, new byte[]{-119, 80, 78, 71, 13, 10, 26, 10}, 0);
        final int startPos = bytePos = writeInt4(pngBytes, 13, bytePos);
        bytePos = writeBytes(pngBytes, IHDR, bytePos);
        bytePos = writeInt4(pngBytes, width, bytePos);
        bytePos = writeInt4(pngBytes, height, bytePos);
        bytePos = writeBytes(pngBytes, new byte[]{8, 2, 0, 0, 0}, bytePos);
        final CRC32 crc = new CRC32();
        crc.reset();
        crc.update(pngBytes, startPos, bytePos - startPos);
        bytePos = writeInt4(pngBytes, (int) crc.getValue(), bytePos);
        crc.reset();
        bytePos = writeInt4(pngBytes, nCompressed, bytePos);
        bytePos = writeBytes(pngBytes, IDAT, bytePos);
        crc.update(IDAT);
        outBytes.copyTo(pngBytes, bytePos);
        outBytes.close();
        outBytes = null;
        crc.update(pngBytes, bytePos, nCompressed);
        bytePos += nCompressed;
        bytePos = writeInt4(pngBytes, (int) crc.getValue(), bytePos);
        bytePos = writeInt4(pngBytes, 0, bytePos);
        bytePos = writeBytes(pngBytes, IEND, bytePos);
        crc.reset();
        crc.update(IEND);
        bytePos = writeInt4(pngBytes, (int) crc.getValue(), bytePos);
        return pngBytes;
    }
    
    private final static int writeInt4(final byte[] target, final int n, final int offset) {
        return writeBytes(target, new byte[]{(byte) ((n >> 24) & 0xff), (byte) ((n >> 16) & 0xff), (byte) ((n >> 8) & 0xff), (byte) (n & 0xff)}, offset);
    }

    private final static int writeBytes(final byte[] target, final byte[] data, final int offset) {
        System.arraycopy(data, 0, target, offset, data.length);
        return offset + data.length;
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
        ConcurrentLog.shutdown();
        
        // open file automatically, works only on Mac OS X
        /*
        Process p = null;
        try {p = Runtime.getRuntime().exec(new String[] {"/usr/bin/osascript", "-e", "open \"" + args[0] + "\""});} catch (final java.io.IOException e) {Log.logException(e);}
        try {p.waitFor();} catch (final InterruptedException e) {Log.logException(e);}
        */
    }


}
