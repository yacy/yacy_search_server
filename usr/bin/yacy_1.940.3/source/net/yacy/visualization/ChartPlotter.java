/**
 *  ChartPlotter
 *  Copyright 2005 by Michael Christen
 *  First released 26.10.2005 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

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
    private final Long[] colnames = new Long[]{0xFFFFFFl,0xFFFFFFl,0xFFFFFFl,0xFFFFFFl,0xFFFFFFl,0xFFFFFFl,0xFFFFFFl,0xFFFFFFl};
    private final Long[] colscale = new Long[]{null,null,null,null,null,null,null,null};
    private final String[] tablenames = new String[]{"","","","","","","",""};

    public ChartPlotter(final int width, final int height, final Long backgroundColor, final Long foregroundColor, final Long lightColor,
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
            if (foregroundColor != null) this.setColor(foregroundColor);
            PrintTool.print(this, width / 2 - name.length() * 3, 6, 0, name, -1, 100);
        }
        if (subline != null) {
            if (lightColor != null) this.setColor(lightColor);
            PrintTool.print(this, width / 2 - subline.length() * 3, 14, 0, subline, -1, 100);
        }
    }

    /**
     * assign a metric to a graph. The dimension can be left or right (the measure at the side-border of the graph)
     * @param dimensionType
     * @param scale is the measure (the number) to be printed on the border per pixelscale
     * @param pixelperscale is the number of pixel points per scale
     * @param offset is an offset on the 'scale' number
     * @param colorNaming the colour of the legend for the scale
     * @param colorScale the colour of the line drawing for the vertical scale
     * @param name printed on the vertical bar
     */
    public void declareDimension(final int dimensionType, final int scale, final int pixelperscale, final int offset, final Long colorNaming, final Long colorScale, final String name) {
        this.scales[dimensionType] = Math.max(1, scale);
        this.pixels[dimensionType] = pixelperscale;
        this.offsets[dimensionType] = offset;
        this.colnames[dimensionType] = colorNaming;
        this.colscale[dimensionType] = colorScale;
        this.tablenames[dimensionType] = name;
        if ((dimensionType == DIMENSION_LEFT) || (dimensionType == DIMENSION_RIGHT)) {
            drawVerticalScale((dimensionType == DIMENSION_LEFT), scale, pixelperscale, offset, colorNaming, colorScale, name);
        }
        if ((dimensionType == DIMENSION_TOP) || (dimensionType == DIMENSION_BOTTOM)) {
            drawHorizontalScale((dimensionType == DIMENSION_TOP), scale, pixelperscale, offset, colorNaming, colorScale, name);
        }
    }

    public void chartDot(final int dimension_x, final int dimension_y, final float coord_x, final int coord_y, final int dotsize, final String anot, final int anotAngle) {
        final int x = (int) ((coord_x - this.offsets[dimension_x]) * this.pixels[dimension_x] / this.scales[dimension_x]);
        assert this.scales[dimension_y] != 0;
        final int y = (int)((long)(coord_y - this.offsets[dimension_y]) * (long)(this.pixels[dimension_y]) / (this.scales[dimension_y]));
        if (dotsize == 1) plot(this.leftborder + x, this.height - this.bottomborder - y, 100);
                      else dot(this.leftborder + x, this.height - this.bottomborder - y, dotsize, true, 100);
        if (anot != null) PrintTool.print(this, this.leftborder + x + dotsize + 2 + ((anotAngle == 315) ? -9 : 0), this.height - this.bottomborder - y + ((anotAngle == 315) ? -3 : 0), anotAngle, anot, (anotAngle == 0) ? (anot.length() * 6 + x > this.width ? 1 : -1) : ((anotAngle == 315) ? 1 : 0), 100);
    }

    public void chartLine(final int dimension_x, final int dimension_y, final float coord_x1, final int coord_y1, final float coord_x2, final int coord_y2) {
        final int x1 = (int) ((coord_x1 - this.offsets[dimension_x]) * this.pixels[dimension_x] / this.scales[dimension_x]);
        final int y1 = (int)((long)(coord_y1 - this.offsets[dimension_y]) * (long)(this.pixels[dimension_y]) / this.scales[dimension_y]);
        final int x2 = (int) ((coord_x2 - this.offsets[dimension_x]) * this.pixels[dimension_x] / this.scales[dimension_x]);
        final int y2 = (int)((long)(coord_y2 - this.offsets[dimension_y]) * (long)(this.pixels[dimension_y]) / this.scales[dimension_y]);
        line(this.leftborder + x1, this.height - this.bottomborder - y1, this.leftborder + x2, this.height - this.bottomborder - y2, 100);
    }

    /**
     * draw a horizontal scale border
     * @param top - if true, this the top-horizontal scale, otherwise it is at the bottom
     * @param scale is the measure (the number) to be printed on the vertical border per pixelscale
     * @param pixelperscale is the number of vertical pixel points per scale
     * @param offset is an offset on the 'scale' number
     * @param colorNaming the colour of the legend for the scale
     * @param colorScale the colour of the line drawing for the vertical scale
     * @param name printed on the vertical bar
     */
    private void drawHorizontalScale(final boolean top, final int scale, final int pixelperscale, final int offset, final Long colorNaming, final Long colorScale, final String name) {
        final int y = (top) ? this.topborder : this.height - this.bottomborder;
        int x = this.leftborder;
        int s = offset;
        while (x < this.width - this.rightborder) {
            if ((colorScale != null) && (x > this.leftborder) && (x < (this.width - this.rightborder))) {
                setColor(colorScale);
                line(x, this.topborder, x, this.height - this.bottomborder, 100);
            }
            setColor(colorNaming);
            line(x, y - 3, x, y + 3, 100);
            PrintTool.print(this, x, (top) ? y - 3 : y + 9, 0, Integer.toString(s), -1, 80);
            x += pixelperscale;
            s += scale;
        }
        setColor(colorNaming);
        PrintTool.print(this, this.width - this.rightborder, (top) ? y - 9 : y + 15, 0, name, 1, 80);
        line(this.leftborder - 4, y, this.width - this.rightborder + 4, y, 100);
    }

    /**
     * draw the vertical scale of the graph
     * @param left if true this is the vertical bar on the left, otherwise it is the one on the right
     * @param scale is the measure (the number) to be printed on the vertical border per pixelscale
     * @param pixelperscale is the number of vertical pixel points per scale
     * @param offset is an offset on the 'scale' number
     * @param colorNaming the colour of the legend for the scale
     * @param colorScale the colour of the line drawing for the vertical scale
     * @param name printed on the vertical bar
     */
    private void drawVerticalScale(final boolean left, final int scale, final int pixelperscale, final int offset, final Long colorNaming, final Long colorScale, final String name) {
        assert pixelperscale > 0;
        assert scale > 0;
        if (pixelperscale <= 0) return; // this would not meet the termination condition in the while loop
        final int x = (left) ? this.leftborder : this.width - this.rightborder;
        int y = this.height - this.bottomborder;
        int s = offset;
        String s1;
        int s1max = 0;
        while (y > this.topborder) {
            if ((colorScale != null) && (y > this.topborder) && (y < (this.height - this.bottomborder))) {
                setColor(colorScale);
                line(this.leftborder, y, this.width - this.rightborder, y, 100);
            }
            setColor(colorNaming);
            line(x - 3, y, x + 3, y, 100);
            s1 = (s >= 1000000 && s % 1000000 == 0) ? Integer.toString(s / 1000000) + "M" : (s >= 1000 && s % 1000 == 0) ? Integer.toString(s / 1000) + "K" : Integer.toString(s);
            if (s1.length() > s1max) s1max = s1.length();
            PrintTool.print(this, (left) ? this.leftborder - 4 : this.width - this.rightborder + 4, y, 0, s1, (left) ? 1 : -1, 80);
            y -= pixelperscale;
            s += scale;
        }
        setColor(colorNaming);
        PrintTool.print(this, (left) ? Math.max(6, x - s1max * 6 - 6) : x + s1max * 6 + 9, this.height - this.bottomborder, 90, name, -1, 80);
        line(x, this.topborder - 4, x, this.height - this.bottomborder + 4, 100);
    }

    /**
     * Write a test chart to a temporary file testimage.png
     */
    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        
        final long bg = 0xFFFFFF;
        final long fg = 0x000000;
        final long scale = 0xCCCCCC;
        final long green = 0x008800;
        final long blue = 0x0000FF;
        final ChartPlotter ip = new ChartPlotter(660, 240, bg, fg, fg, 30, 30, 20, 20, "PEER PERFORMANCE GRAPH: PAGES/MINUTE and USED MEMORY", "");
        ip.declareDimension(DIMENSION_BOTTOM, 60, 60, -600, fg, scale, "TIME/SECONDS");
        ip.declareDimension(DIMENSION_LEFT, 50, 40, 0, green, scale , "PPM [PAGES/MINUTE]");
        ip.declareDimension(DIMENSION_RIGHT, 100, 20, 0, blue, scale, "MEMORY/MEGABYTE");
        
        /* Draw an ascending line of 10 plots */
        ip.setColor(green);
        final int width = 600, maxPPM = 240;
        int steps = 10, x = - width;
        int ppm = (int)(maxPPM * 0.1);
        int ppmStep = (int)((maxPPM * 0.9) / steps);
        for(int step = 0; step < steps; step++) {
        	ip.chartDot(DIMENSION_BOTTOM, DIMENSION_LEFT, x, ppm, 5, null, 0);
        	ip.chartLine(DIMENSION_BOTTOM, DIMENSION_LEFT, x, ppm, x + (width / steps), ppm + ppmStep);
        	ppm += ppmStep;
        	x += (width / steps);
        }
        
        /* Draw a descending line of 20 plots */
        ip.setColor(blue);
        steps = 20;
        final int maxMBytes = 800;
        int mBytes = (int)(maxMBytes * 0.8);
        int mBytesStep = (int)((maxMBytes * 0.6) / steps);
        x = - width;
        for(int step = 0; step < steps; step++) {
        	ip.chartDot(DIMENSION_BOTTOM, DIMENSION_RIGHT, x, mBytes, 5, null, 0);
        	ip.chartLine(DIMENSION_BOTTOM, DIMENSION_RIGHT, x, mBytes, x + (width / steps), mBytes - mBytesStep);
        	mBytes -= mBytesStep;
        	x += (width / steps);
        }
        final File file = new File(System.getProperty("java.io.tmpdir"),"testimage.png");
        try (
        	/* Automatically closed by this try-with-resources statement */
            final FileOutputStream fos = new FileOutputStream(file);
        ) {
            fos.write(RasterPlotter.exportImage(ip.getImage(), "png").getBytes());
            System.out.println("CharPlotter test file written at " + file);
        } catch (final IOException e) {
        	e.printStackTrace();
        }
        ConcurrentLog.shutdown();
    }

}
