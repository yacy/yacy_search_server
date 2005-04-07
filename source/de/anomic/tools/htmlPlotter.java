// htmlPlotter.java 
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

package de.anomic.tools;

import java.io.*;
import de.anomic.server.*;

public class htmlPlotter {
    
    private byte[][][] grid;
    int width, height;
    byte[] defaultCol;
    
    public htmlPlotter(int width, int height, String defaultCol) {
        this.width = width;
        this.height = height;
        this.defaultCol = parseCol(defaultCol);
        grid = new byte[width][height][3];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++) grid[x][y] = null;
    }
    
    private byte[] parseCol(String col) {
        byte[] c = new byte[3];
        c[0] = (byte) Integer.parseInt(col.substring(0,2), 16);
        c[1] = (byte) Integer.parseInt(col.substring(2,4), 16);
        c[2] = (byte) Integer.parseInt(col.substring(4,6), 16);
        return c;
    }
    
    private String genCol(byte[] c) {
        if (c == null) c = defaultCol;
        return hex2(c[0]) + hex2(c[1]) + hex2(c[2]);
    }
    
    private String hex2(byte b) {
        String h = Integer.toHexString(((int) b) & 0xff);
        if (h.length() == 1) return "0" + h; else return h;
    }
    
    public void plot(int x, int y, String col) {
        plot(x, y, parseCol(col));
    }
    
    public void plot(int x, int y, byte[] c) {
        if ((x < 0) || (x >= width)) return;
        if ((y < 0) || (y >= height)) return;
        grid[x][y] = c;
    }
    
    private boolean equalCol(byte[] a, byte[] b) {
        if ((a == null) && (b == null)) return true;
        if ((a == null) || (b == null)) return false;
        return ((a[0] == b[0]) && (a[1] == b[1]) && (a[2] == b[2]));
    }
    
    public String toHTML() {
        String s = "<table border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"" + width +"\">\n\r";
        byte[] col;
        int x, i, l;
        for (int y = height - 1; y >= 0; y--) {
            s += "<tr height=\"1\">";
            x = 0;
            while (x < width) {
                i = x + 1;
                col = grid[x][y];
                while ((i < width) && (equalCol(grid[i][y], col))) i++;
                l = i - x;
                x = i;
                if (l == 1)
                    s += "<td width=\"1\" bgcolor=\"#" + genCol(col) + "\"></td>";
                else
                    s += "<td width=\"1\" bgcolor=\"#" + genCol(col) + "\" rowspan=\"" + l + "\"></td>";
            }
            s += "</tr>\n\r";
        }
        s += "</table>\n\r";
        return s;
    }
    
    public void draw(int Ax, int Ay, int Bx, int By, String col) {
        // Bresenham's line drawing algorithm
        byte[] Color = parseCol(col);
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
                plot(Ax, Ay, Color);
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
                plot(Ax, Ay, Color);
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
    
    public static void main(String[] args) {
        htmlPlotter plotter = new htmlPlotter(200, 100, "FFFFFF");
        plotter.plot(0,0,"000000"); plotter.plot(33,50,"000000"); plotter.plot(36,50,"000000");
        //plotter.draw(10,10,170,88,"AAAAAA");
        try {
            serverFileUtils.write(("<html><body>" + plotter.toHTML() + "</body></html>").getBytes(), new File("D:\\bin\\test.html"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(plotter.toHTML());
    }
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