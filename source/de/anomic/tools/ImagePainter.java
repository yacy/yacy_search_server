// ImagePainter.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 16.09.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
 This Class implements some convenience-methods to support drawing of statistical Data
 It is not intended to replace existing awt-funktions even if it looks so
 This class provides some drawing methods that creates transparency effects that
 are not available in awt.
 */

package de.anomic.tools;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

public class ImagePainter {
    
    public static final long TRANSPARENT = -1;
    public static final long WHITE = 0xFFFFFF;
    public static final long RED   = 0xFF0000;
    public static final long GREEN = 0x00FF00;
    public static final long BLUE  = 0x0000FF;
    public static final long BLACK = 0;
    
    public static final byte MODE_REPLACE = 0;
    public static final byte MODE_REVERSE = 1;
    public static final byte MODE_MIX = 2;
    
    private long[] grid; // one-dimensional arrays are much faster than two-dimensional
    private int width, height;
    private long defaultCol;
    private byte defaultMode;
    
    public ImagePainter(int width, int height, long backgroundColor) {
        this.width = width;
        this.height = height;
        this.defaultCol = BLACK;
        this.defaultMode = MODE_REPLACE;
        grid = new long[width*height];
        for (int n= width * height - 1; n >= 0; n--) grid[n] = backgroundColor;
    }
    
    private long colNum(String col) {
        return Long.parseLong(col, 16);
        //return Integer.parseInt(col.substring(0,2), 16) << 16 | Integer.parseInt(col.substring(2,4), 16) << 8 | Integer.parseInt(col.substring(4,6), 16);
    }
    
    private String colStr(long c) {
        String s = Long.toHexString(c);
        while (s.length() < 6) s = "0" + s;
        return s;
    }
    
    private void setColor(long c) {
        defaultCol = c;
    }
    
    private void setMode(byte m) {
        defaultMode = m;
    }
    
    private void plot(int x, int y) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        if (defaultMode == MODE_REPLACE) {
            grid[x + y * width] = defaultCol;
        } else if (defaultMode == MODE_MIX) {
            int n = x + y * width;
            long c = grid[n];
            int r = ((int) ((c >> 16) + (defaultCol >> 16))) & 0xff;
            int g = ((int) (((c >> 8) & 0xff) + ((defaultCol >> 8) & 0xff))) & 0xff;
            int b = ((int) ((c & 0xff) + (defaultCol & 0xff))) & 0xff;
            grid[n] = r << 16 | g << 8 | b;
        }
    }
    
    private void line(int Ax, int Ay, int Bx, int By) {
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
    
    private void circle(int xc, int yc, int radius) {
        int xp = xc + radius;
        int yp = yc;
        int xn, yn;
        for (int i = 359; i >= 0; i--) {
            xn = xc + (int) (radius * Math.cos(Math.PI * 2 * i / 360));
            yn = yc + (int) (radius * Math.sin(Math.PI * 2 * i / 360));
            line(xp, yp, xn, yn);
            xp = xn;
            yp = yn;
        }
    }
    
    public void dot(int x, int y, int radius, boolean filled, long color, byte mode) {
        setColor(color);
        setMode(mode);
        if (filled) {
            for (int i = radius; i >= 0; i--) circle(x, y, i);
        } else {
            circle(x, y, radius);
        }
    }
    
    
    public BufferedImage toImage() {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); 
        Graphics2D g = bi.createGraphics();
        g.setBackground(Color.white);
        g.clearRect(0, 0, width, height);

        WritableRaster r = bi.getRaster();
        long c;
        for (int i = width - 1; i >= 0; i--) {
            for (int j = height - 1; j >= 0; j--) {
                c = grid[i + j * width];
                if (c >= 0) r.setPixel(i, j, new int[]{(int) (c >> 16), (int) ((c >> 8) & 0xff), (int) (c & 0xff)});
            }
        }
        
        return bi;
    }
    
    /*
    public String toHTML() {
        String s = "<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"" + width +"\">\n\r";
        long c;
        int x, i, l;
        for (int y = height - 1; y >= 0; y--) {
            s += "<tr height=\"1\">";
            x = 0;
            while (x < width) {
                i = x + 1;
                c = grid[x + y * width];
                while ((i < width) && (grid[i * width + y] == c)) i++;
                l = i - x;
                x = i;
                if (l == 1)
                    s += "<td width=\"1\" bgcolor=\"#" + colStr(c) + "\"></td>";
                else
                    s += "<td width=\"1\" bgcolor=\"#" + colStr(c) + "\" rowspan=\"" + l + "\"></td>";
            }
            s += "</tr>\n\r";
        }
        s += "</table>\n\r";
        return s;
    }
    */
}

/*
/============================================================================
// b r e s l i n e . c    
//
// VERSION 1: draws only from one end and calculates both x and y.
//
// Programmer:  Kenny Hoff
// Date:        10/25/95
// Purpose:     To implement the Bresenham's line drawing algorithm for all
//              slopes and line directions (using minimal routines). 
//============================================================================

#include <stdlib.h>

// EXTERNALLY DEFINED FRAMEBUFFER AND FRAMEBUFFER DIMENSIONS (WIDTH))
extern unsigned char far* FrameBuffer;
extern int WIDTH; 
#define SetPixel(x,y,c) FrameBuffer[y*WIDTH+x]=c;

//============================================================================
// Fills the intermediate points along a line between the two given endpoints
// using Bresenham's line drawing algorithm. NOTE: this routine does no clipping
// so the coordinate values must be within the FrameBuffer bounds.
// NOTE: USE (Ax,Ay) as the starting point (values that are incremented)
//============================================================================
void BresLine(int Ax, int Ay, int Bx, int By, unsigned char Color)
{
	//------------------------------------------------------------------------
	// INITIALIZE THE COMPONENTS OF THE ALGORITHM THAT ARE NOT AFFECTED BY THE
	// SLOPE OR DIRECTION OF THE LINE
	//------------------------------------------------------------------------
	int dX = abs(Bx-Ax);	// store the change in X and Y of the line endpoints
	int dY = abs(By-Ay);
	
	//------------------------------------------------------------------------
	// DETERMINE "DIRECTIONS" TO INCREMENT X AND Y (REGARDLESS OF DECISION)
	//------------------------------------------------------------------------
	int Xincr, Yincr;
	if (Ax > Bx) { Xincr=-1; } else { Xincr=1; }	// which direction in X?
	if (Ay > By) { Yincr=-1; } else { Yincr=1; }	// which direction in Y?
	
	//------------------------------------------------------------------------
	// DETERMINE INDEPENDENT VARIABLE (ONE THAT ALWAYS INCREMENTS BY 1 (OR -1) )
	// AND INITIATE APPROPRIATE LINE DRAWING ROUTINE (BASED ON FIRST OCTANT
	// ALWAYS). THE X AND Y'S MAY BE FLIPPED IF Y IS THE INDEPENDENT VARIABLE.
	//------------------------------------------------------------------------
	if (dX >= dY)	// if X is the independent variable
	{           
		int dPr 	= dY<<1;           // amount to increment decision if right is chosen (always)
		int dPru 	= dPr - (d><<1);   // amount to increment decision if up is chosen
		int P 		= dPr - dX;  // decision variable start value

		for (; dX>=0; dX--)            // process each point in the line one at a time (just use dX)
		{
			SetPixel(Ax, Ay, Color); // plot the pixel
			if (P > 0)               // is the pixel going right AND up?
			{ 
				Ax+=Xincr;	       // increment independent variable
				Ay+=Yincr;         // increment dependent variable
				P+=dPru;           // increment decision (for up)
			}
			else                     // is the pixel just going right?
			{
				Ax+=Xincr;         // increment independent variable
				P+=dPr;            // increment decision (for right)
			}
		}		
	}
	else              // if Y is the independent variable
	{
		int dPr 	= dX<<1;           // amount to increment decision if right is chosen (always)
		int dPru 	= dPr - (d><<1);   // amount to increment decision if up is chosen
		int P 		= dPr - dY;  // decision variable start value

		for (; dY>=0; dY--)            // process each point in the line one at a time (just use dY)
		{
			SetPixel(Ax, Ay, Color); // plot the pixel
			if (P > 0)               // is the pixel going up AND right?
			{ 
				Ax+=Xincr;         // increment dependent variable
				Ay+=Yincr;         // increment independent variable
				P+=dPru;           // increment decision (for up)
			}
			else                     // is the pixel just going up?
			{
				Ay+=Yincr;         // increment independent variable
				P+=dPr;            // increment decision (for right)
			}
		}		
	}		
}

*/