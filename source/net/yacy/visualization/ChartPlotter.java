// ChartPlotter.java
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 26.10.2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.visualization;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import net.yacy.cora.util.ConcurrentLog;


public class ChartPlotter extends RasterPlotter {

    public static final int DIMENSION_RIGHT  = 0;
    public static final int DIMENSION_TOP    = 1;
    public static final int DIMENSION_LEFT   = 2;
    public static final int DIMENSION_BOTTOM = 3;
    public static final int DIMENSION_ANOT0  = 4;
    public static final int DIMENSION_ANOT1  = 5;
    public static final int DIMENSION_ANOT2  = 6;
    public static final int DIMENSION_ANOT3  = 7;

    private final int leftborder;
    private final int rightborder;
    private final int topborder;
    private final int bottomborder;
    private final int[] scales = new int[]{0,0,0,0,0,0,0,0};
    private final int[] pixels = new int[]{0,0,0,0,0,0,0,0};
    private final int[] offsets = new int[]{0,0,0,0,0,0,0,0};
    private final String[] colnames = new String[]{"FFFFFF","FFFFFF","FFFFFF","FFFFFF","FFFFFF","FFFFFF","FFFFFF","FFFFFF"};
    private final String[] colscale = new String[]{null,null,null,null,null,null,null,null};
    private final String[] tablenames = new String[]{"","","","","","","",""};

    public ChartPlotter(final int width, final int height, final String backgroundColor, final String foregroundColor, final String lightColor,
                      final int leftborder, final int rightborder, final int topborder, final int bottomborder,
                      final String name, final String subline) {
        super(width, height, RasterPlotter.DrawMode.MODE_REPLACE, backgroundColor);
        this.leftborder = leftborder;
        this.rightborder = rightborder;
        this.topborder = topborder;
        this.bottomborder = bottomborder;
        //this.name = name;
        //this.backgroundColor = backgroundColor;
        //this.foregroundColor = foregroundColor;
        if (name != null) {
            if (foregroundColor != null) this.setColor(Long.parseLong(foregroundColor, 16));
            PrintTool.print(this, width / 2 - name.length() * 3, 6, 0, name, -1);
        }
        if (subline != null) {
            if (lightColor != null) this.setColor(Long.parseLong(lightColor, 16));
            PrintTool.print(this, width / 2 - subline.length() * 3, 14, 0, subline, -1);
        }
    }

    public void declareDimension(final int dimensionType, final int scale, final int pixelperscale, final int offset, final String colorNaming, final String colorScale, final String name) {
        if ((dimensionType == DIMENSION_LEFT) || (dimensionType == DIMENSION_RIGHT)) {
            drawVerticalScale((dimensionType == DIMENSION_LEFT), scale, pixelperscale, offset, colorNaming, colorScale, name);
        }
        if ((dimensionType == DIMENSION_TOP) || (dimensionType == DIMENSION_BOTTOM)) {
            drawHorizontalScale((dimensionType == DIMENSION_TOP), scale, pixelperscale, offset, colorNaming, colorScale, name);
        }
        this.scales[dimensionType] = scale;
        this.pixels[dimensionType] = pixelperscale;
        this.offsets[dimensionType] = offset;
        this.colnames[dimensionType] = colorNaming;
        this.colscale[dimensionType] = colorScale;
        this.tablenames[dimensionType] = name;
    }

    public void chartDot(final int dimension_x, final int dimension_y, final int coord_x, final int coord_y, final int dotsize, final String anot, final int anotAngle) {
        final int x = (coord_x - this.offsets[dimension_x]) * this.pixels[dimension_x] / this.scales[dimension_x];
        final int y = (coord_y - this.offsets[dimension_y]) * this.pixels[dimension_y] / this.scales[dimension_y];
        if (dotsize == 1) plot(this.leftborder + x, this.height - this.bottomborder - y, 100);
                      else dot(this.leftborder + x, this.height - this.bottomborder - y, dotsize, true, 100);
        if (anot != null) PrintTool.print(this, this.leftborder + x + dotsize + 2 + ((anotAngle == 315) ? -9 : 0), this.height - this.bottomborder - y + ((anotAngle == 315) ? -3 : 0), anotAngle, anot, (anotAngle == 0) ? (anot.length() * 6 + x > this.width ? 1 : -1) : ((anotAngle == 315) ? 1 : 0));
    }

    public void chartLine(final int dimension_x, final int dimension_y, final int coord_x1, final int coord_y1, final int coord_x2, final int coord_y2) {
        final int x1 = (coord_x1 - this.offsets[dimension_x]) * this.pixels[dimension_x] / this.scales[dimension_x];
        final int y1 = (coord_y1 - this.offsets[dimension_y]) * this.pixels[dimension_y] / this.scales[dimension_y];
        final int x2 = (coord_x2 - this.offsets[dimension_x]) * this.pixels[dimension_x] / this.scales[dimension_x];
        final int y2 = (coord_y2 - this.offsets[dimension_y]) * this.pixels[dimension_y] / this.scales[dimension_y];
        line(this.leftborder + x1, this.height - this.bottomborder - y1, this.leftborder + x2, this.height - this.bottomborder - y2, 100);
    }

    private void drawHorizontalScale(final boolean top, final int scale, final int pixelperscale, final int offset, final String colorNaming, final String colorScale, final String name) {
        final int y = (top) ? this.topborder : this.height - this.bottomborder;
        int x = this.leftborder;
        int s = offset;
        Long colorScale_l = colorScale == null ? null : Long.parseLong(colorScale, 16);
        Long colorNaming_l = colorNaming == null ? null : Long.parseLong(colorNaming, 16);
        while (x < this.width - this.rightborder) {
            if ((colorScale != null) && (x > this.leftborder) && (x < (this.width - this.rightborder))) {
                setColor(colorScale_l);
                line(x, this.topborder, x, this.height - this.bottomborder, 100);
            }
            setColor(colorNaming_l);
            line(x, y - 3, x, y + 3, 100);
            PrintTool.print(this, x, (top) ? y - 3 : y + 9, 0, Integer.toString(s), -1);
            x += pixelperscale;
            s += scale;
        }
        setColor(colorNaming_l);
        PrintTool.print(this, this.width - this.rightborder, (top) ? y - 9 : y + 15, 0, name, 1);
        line(this.leftborder - 4, y, this.width - this.rightborder + 4, y, 100);
    }

    private void drawVerticalScale(final boolean left, final int scale, final int pixelperscale, final int offset, final String colorNaming, final String colorScale, final String name) {
        assert pixelperscale > 0;
        assert scale > 0;
        final int x = (left) ? this.leftborder : this.width - this.rightborder;
        int y = this.height - this.bottomborder;
        int s = offset;
        String s1;
        int s1max = 0;
        Long colorScale_l = colorScale == null ? null : Long.parseLong(colorScale, 16);
        Long colorNaming_l = colorNaming == null ? null : Long.parseLong(colorNaming, 16);
        while (y > this.topborder) {
            if ((colorScale != null) && (y > this.topborder) && (y < (this.height - this.bottomborder))) {
                setColor(colorScale_l);
                line(this.leftborder, y, this.width - this.rightborder, y, 100);
            }
            setColor(colorNaming_l);
            line(x - 3, y, x + 3, y, 100);
            s1 = (s >= 1000000 && s % 10000 == 0) ? Integer.toString(s / 1000000) + "M" : (s >= 1000 && s % 1000 == 0) ? Integer.toString(s / 1000) + "K" : Integer.toString(s);
            if (s1.length() > s1max) s1max = s1.length();
            PrintTool.print(this, (left) ? this.leftborder - 4 : this.width - this.rightborder + 4, y, 0, s1, (left) ? 1 : -1);
            y -= pixelperscale;
            s += scale;
        }
        setColor(colorNaming_l);
        PrintTool.print(this, (left) ? x - s1max * 6 - 6 : x + s1max * 6 + 9, this.topborder, 90, name, 1);
        line(x, this.topborder - 4, x, this.height - this.bottomborder + 4, 100);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final String bg = "FFFFFF";
        final String fg = "000000";
        final String scale = "CCCCCC";
        final String green = "008800";
        final String blue = "0000FF";
        final ChartPlotter ip = new ChartPlotter(660, 240, bg, fg, fg, 30, 30, 20, 20, "PEER PERFORMANCE GRAPH: PAGES/MINUTE and USED MEMORY", "");
        ip.declareDimension(DIMENSION_BOTTOM, 60, 60, -600, fg, scale, "TIME/SECONDS");
        //ip.declareDimension(DIMENSION_TOP, 10, 40, "000000", null, "count");
        ip.declareDimension(DIMENSION_LEFT, 50, 40, 0, green, scale , "PPM [PAGES/MINUTE]");
        ip.declareDimension(DIMENSION_RIGHT, 100, 20, 0, blue, scale, "MEMORY/MEGABYTE");
        ip.setColor(Long.parseLong(green, 16));
        ip.chartDot(DIMENSION_BOTTOM, DIMENSION_LEFT, -160, 100, 5, null, 0);
        ip.chartLine(DIMENSION_BOTTOM, DIMENSION_LEFT, -160, 100, -130, 200);
        ip.setColor(Long.parseLong(blue, 16));
        ip.chartDot(DIMENSION_BOTTOM, DIMENSION_RIGHT, -50, 300, 2, null, 0);
        ip.chartLine(DIMENSION_BOTTOM, DIMENSION_RIGHT, -80, 100, -50, 300);
        //ip.print(100, 100, 0, "TEXT", true);
        //ip.print(100, 100, 0, "1234", false);
        //ip.print(100, 100, 90, "TEXT", true);
        //ip.print(100, 100, 90, "1234", false);
        final File file = new File("/Users/admin/Desktop/testimage.png");
        try {
            final FileOutputStream fos = new FileOutputStream(file);
            fos.write(RasterPlotter.exportImage(ip.getImage(), "png").getBytes());
            //ImageIO.write(ip.getImage(), "png", fos);
            fos.close();
        } catch (final IOException e) {}
        ConcurrentLog.shutdown();
    }

}
