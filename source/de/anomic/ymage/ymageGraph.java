// ymageGraph.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 22.05.2007 on http://yacy.net
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

package de.anomic.ymage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/* this class is a container for graph coordinates and it can draw such coordinates into a graph
 * all coordinates are given in a artificial coordinate system, in the range from
 * -1 to +1. The lower left point of the graph has the coordinate -1, -1 and the upper
 * right is 1,1
 * 0,0 is the center of the graph
 */

public class ymageGraph {

    // a ymageGraph is a set of points and borders between the points
    // to reference the points, they must all have a nickname

    HashMap<String, coordinate> points;
    HashSet<String> borders;
    double leftmost, rightmost, topmost, bottommost;
    
    public ymageGraph() {
        points = new HashMap<String, coordinate>();
        borders = new HashSet<String>();
        leftmost = 1.0;
        rightmost = -1.0;
        topmost = -1.0;
        bottommost = 1.0;
    }
    
    public coordinate getPoint(final String name) {
        return points.get(name);
    }
    
    public coordinate[] getBorder(final String name) {
        final int p = name.indexOf("$");
        if (p < 0) return null;
        final coordinate from = getPoint(name.substring(0, p));
        final coordinate to = getPoint(name.substring(p + 1));
        if ((from == null) || (to == null)) return null;
        return new coordinate[] {from, to};
    }
    
    public coordinate addPoint(final String name, final double x, final double y, final int layer) {
        final coordinate newc = new coordinate(x, y, layer);
        final coordinate oldc = points.put(name, newc);
        assert oldc == null; // all add shall be unique
        if (x > rightmost) rightmost = x;
        if (x < leftmost) leftmost = x;
        if (y > topmost) topmost = y;
        if (y < bottommost) bottommost = y;
        return newc;
    }
    
    public boolean hasBorder(final String fromPoint, final String toPoint) {
        return borders.contains(fromPoint + "-" + toPoint);
    }
    
    public void setBorder(final String fromPoint, final String toPoint) {
        final coordinate from = points.get(fromPoint);
        final coordinate to = points.get(toPoint);
        assert from != null;
        assert to != null;
        borders.add(fromPoint + "$" + toPoint);
    }

    public class coordinate {
        public double x, y;
        public int layer;
        public coordinate(final double x, final double y, final int layer) {
            assert x >= -1;
            assert x <=  1;
            assert y >= -1;
            assert y <=  1;
            this.x = x;
            this.y = y;
            this.layer = layer;
        }
    }
    
    public void print() {
        // for debug purpose: print out all coordinates
        final Iterator<Map.Entry<String, coordinate>> i = points.entrySet().iterator();
        Map.Entry<String, coordinate> entry;
        String name;
        coordinate c;
        while (i.hasNext()) {
            entry = i.next();
            name = entry.getKey();
            c = entry.getValue();
            System.out.println("point(" + c.x + ", " + c.y + ", " + c.layer + ") [" + name + "]");
        }
        final Iterator<String> j = borders.iterator();
        while (j.hasNext()) {
            System.out.println("border(" + j.next() + ")");
        }
    }
    
    public  static final long color_back = 0xFFFFFF;
    public  static final long color_text = 0xAAAAAA;
    private static final long color_dot = 0x11CC11;
    private static final long color_line = 0x333333;
    private static final long color_lineend = 0x666666;
    
    public ymageMatrix draw(final int width, final int height, final int leftborder, final int rightborder, final int topborder, final int bottomborder) {
        final ymageMatrix image = new ymageMatrix(width, height, ymageMatrix.MODE_SUB, color_back);
        final double xfactor = ((rightmost - leftmost) == 0.0) ? 0.0 : (width - leftborder - rightborder) / (rightmost - leftmost);
        final double yfactor = ((topmost - bottommost) == 0.0) ? 0.0 : (height - topborder - bottomborder) / (topmost - bottommost);
        
        // draw dots and names
        final Iterator<Map.Entry<String, coordinate>> i = points.entrySet().iterator();
        Map.Entry<String, coordinate> entry;
        String name;
        coordinate c;
        int x, y;
        while (i.hasNext()) {
            entry = i.next();
            name = entry.getKey();
            c = entry.getValue();
            x = (xfactor == 0.0) ? width / 2 : (int) (leftborder + (c.x - leftmost) * xfactor);
            y = (yfactor == 0.0) ? height / 2 : (int) (height - bottomborder - (c.y - bottommost) * yfactor);
            image.setColor(color_dot);
            image.dot(x, y, 6, true);
            image.setColor(color_text);
            ymageToolPrint.print(image, x, y + 10, 0, name.toUpperCase(), 0);
        }
        
        // draw lines
        final Iterator<String> j = borders.iterator();
        coordinate[] border;
        image.setColor(color_line);
        int x0, x1, y0, y1;
        while (j.hasNext()) {
            border = getBorder(j.next());
            if (border == null) continue;
            if (xfactor == 0.0) {
                x0 = width / 2;
                x1 = width / 2;
            } else {
                x0 = (int) (leftborder + (border[0].x - leftmost) * xfactor);
                x1 = (int) (leftborder + (border[1].x - leftmost) * xfactor);
            }
            if (yfactor == 0.0) {
                y0 = height / 2;
                y1 = height / 2;
            } else {
                y0 = (int) (height - bottomborder - (border[0].y - bottommost) * yfactor);
                y1 = (int) (height - bottomborder - (border[1].y - bottommost) * yfactor);
            }
            // draw the line, with the dot at the beginning of the line
            image.lineDot(x1, y1, x0, y0, 3, 4, color_line, color_lineend);
        }
        return image;
    }
    
}
