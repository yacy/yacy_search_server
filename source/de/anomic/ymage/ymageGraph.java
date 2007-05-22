package de.anomic.ymage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/* this class does not really draw graphes, it is only a container for graph coordinates.
 * all coordinates are given in a artificial corrdinate system, in the range from
 * -1 to +1. The lower left point of the graph has the coordinate -1, -1 and the upper
 * right is 1,1
 * 0,0 is the center of the graph
 */

public class ymageGraph {

    // a ymageGraph is a set of points and borders between the points
    // to reference the points, they must all have a nickname

    HashMap points;
    HashSet borders;
    double leftmost, rightmost, topmost, bottommost;
    
    public ymageGraph() {
        points = new HashMap();
        borders = new HashSet();
        leftmost = 1.0;
        rightmost = -1.0;
        topmost = -1.0;
        bottommost = 1.0;
    }
    
    public coordinate getPoint(String name) {
        return (coordinate) points.get(name);
    }
    
    public coordinate[] getBorder(String name) {
        int p = name.indexOf("$");
        if (p < 0) return null;
        coordinate from = getPoint(name.substring(0, p));
        coordinate to = getPoint(name.substring(p + 1));
        if ((from == null) || (to == null)) return null;
        return new coordinate[] {from, to};
    }
    
    public coordinate addPoint(String name, double x, double y, int layer) {
        coordinate newc = new coordinate(x, y, layer);
        coordinate oldc = (coordinate) points.put(name, newc);
        assert oldc == null; // all add shall be unique
        if (x > rightmost) rightmost = x;
        if (x < leftmost) leftmost = x;
        if (y > topmost) topmost = y;
        if (y < bottommost) bottommost = y;
        return newc;
    }
    
    public boolean hasBorder(String fromPoint, String toPoint) {
        return borders.contains(fromPoint + "-" + toPoint);
    }
    
    public void setBorder(String fromPoint, String toPoint) {
        coordinate from = (coordinate) points.get(fromPoint);
        coordinate to = (coordinate) points.get(toPoint);
        assert from != null;
        assert to != null;
        borders.add(fromPoint + "$" + toPoint);
    }

    public class coordinate {
        public double x, y;
        public int layer;
        public coordinate(double x, double y, int layer) {
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
        Iterator i = points.entrySet().iterator();
        Map.Entry entry;
        String name;
        coordinate c;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            name = (String) entry.getKey();
            c = (coordinate) entry.getValue();
            System.out.println("point(" + c.x + ", " + c.y + ", " + c.layer + ") [" + name + "]");
        }
        i = borders.iterator();
        while (i.hasNext()) {
            System.out.println("border(" + i.next() + ")");
        }
    }
    
    private static final long color_back = ymageMatrix.SUBTRACTIVE_WHITE;
    private static final long color_dot = 0x4444AA;
    private static final long color_line = 0x333333;
    private static final long color_text = ymageMatrix.SUBTRACTIVE_BLACK;
    
    public ymageMatrix draw(int width, int height, int leftborder, int rightborder, int topborder, int bottomborder) {
        ymageMatrix image = new ymageMatrix(width, height, color_back);
        double xfactor = (width - leftborder - rightborder) / (rightmost - leftmost);
        double yfactor = (height - topborder - bottomborder) / (topmost - bottommost);
        image.setMode(ymageMatrix.MODE_SUB);
        
        Iterator i = points.entrySet().iterator();
        Map.Entry entry;
        String name;
        coordinate c;
        int x, y;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            name = (String) entry.getKey();
            c = (coordinate) entry.getValue();
            x = (int) (leftborder + (c.x - leftmost) * xfactor);
            y = (int) (height - bottomborder - (c.y - bottommost) * yfactor);
            image.setColor(color_dot);
            image.dot(x, y, 5, true);
            image.setColor(color_text);
            ymageToolPrint.print(image, x, y + 10, 0, name.toUpperCase(), 0);
        }
        i = borders.iterator();
        coordinate[] border;
        image.setColor(color_line);
        while (i.hasNext()) {
            border = getBorder((String) i.next());
            if (border == null) continue;
            image.line(
                    (int) (leftborder + (border[0].x - leftmost) * xfactor),
                    (int) (height - bottomborder - (border[0].y - bottommost) * yfactor),
                    (int) (leftborder + (border[1].x - leftmost) * xfactor),
                    (int) (height - bottomborder - (border[1].y - bottommost) * yfactor));
        }
        return image;
    }
}
