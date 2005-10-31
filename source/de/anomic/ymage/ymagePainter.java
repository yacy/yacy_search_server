// ymagePainter.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 31.10.2005
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

package de.anomic.ymage;


public interface ymagePainter {
    
    public Object clone();
    
    public int getWidth();
    
    public int getHeight();
    
    public void setColor(String s);
    
    public void setColor(long c);
    
    public void setMode(byte m);
    
    public void plot(int x, int y);
    
    public void line(int Ax, int Ay, int Bx, int By);
    
    public void circle(int xc, int yc, int radius);
    
    public void circle(int xc, int yc, int radius, int fromArc, int toArc);
    
    public void dot(int x, int y, int radius, boolean filled);
    
    public void arc(int x, int y, int innerRadius, int outerRadius, int fromArc, int toArc);
    
    public void print(int x, int y, int angle, String message, boolean alignLeft);
    
    public void arcPrint(int cx, int cy, int radius, int angle, String message);
    
    public void arcLine(int cx, int cy, int innerRadius, int outerRadius, int angle);
    
    public void arcDot(int cx, int cy, int arcRadius, int angle, int dotRadius);
    
    public void arcArc(int cx, int cy, int arcRadius, int angle, int innerRadius, int outerRadius, int fromArc, int toArc);

}
