/**
 *  WireframeBarPrismPlotter
 *  Renders clustered bar charts as neon wireframe prisms.
 *  Copyright 2025 by Michael Peter Christen
 *  First released 20.09.2025 at https://yacy.net
 *
 *  This file is part of YaCy.
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
import java.io.IOException;

public class WireframeBarPrismPlotter extends RasterPlotter {

    private final double[][] values;
    private final long[] colors;
    private final int originX;
    private final int originY;
    private final int barWidth;
    private final int depth;

    public WireframeBarPrismPlotter(final int width, final int height, final double[][] values) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000014);
        this.values = values;
        this.colors = new long[]{0x00FFFF, 0xFF66CC, 0x66FF99};
        this.originX = 120;
        this.originY = height - 80;
        this.barWidth = 40;
        this.depth = 28;
    }

    public void renderScene() {
        clear();
        drawAxes();
        for (int series = 0; series < values.length; series++) {
            for (int index = 0; index < values[series].length; index++) {
                drawBar(series, index, values[series][index]);
            }
        }
        drawLegend();
    }

    private void drawAxes() {
        setColor(0x00324C);
        line(originX - 60, originY, getWidth() - 40, originY, 40);
        line(originX, originY + 20, originX + depth * 6, originY - depth * 6, 30);
        line(originX, originY + 20, originX, originY - 240, 35);
        setColor(0x005A80);
        for (int i = 0; i <= 5; i++) {
            int y = originY - i * 40;
            line(originX, y, getWidth() - 60, y - depth * 2, 20);
            PrintTool.print5(this, originX - 70, y, 0, String.format("%3d", i * 20), -1, 60);
        }
    }

    private void drawBar(final int series, final int index, final double value) {
        final long baseColor = colors[series % colors.length];
        final int x = originX + index * (barWidth + 20) + series * (barWidth + 4);
        final int height = (int) Math.round(value * 160);
        final int frontLeftX = x;
        final int frontRightX = x + barWidth;
        final int frontBottomY = originY;
        final int frontTopY = originY - height;
        final int backLeftX = frontLeftX + depth;
        final int backRightX = frontRightX + depth;
        final int backTopY = frontTopY - depth / 2;
        final int backBottomY = frontBottomY - depth / 2;

        // top face
        setColor(lighten(baseColor, 1.5d));
        fillPolygon(
                new int[]{frontLeftX, frontRightX, backRightX, backLeftX},
                new int[]{frontTopY, frontTopY, backTopY, backTopY},
                lighten(baseColor, 1.2d),
                60);

        // side faces (wireframe only)
        setColor(baseColor);
        line(frontLeftX, frontTopY, frontRightX, frontTopY, 90);
        line(frontLeftX, frontBottomY, frontRightX, frontBottomY, 50);
        line(frontLeftX, frontTopY, frontLeftX, frontBottomY, 70);
        line(frontRightX, frontTopY, frontRightX, frontBottomY, 70);
        // back edges
        line(backLeftX, backTopY, backRightX, backTopY, 60);
        line(backLeftX, backTopY, backLeftX, backBottomY, 50);
        line(backRightX, backTopY, backRightX, backBottomY, 50);
        // connecting edges
        line(frontLeftX, frontTopY, backLeftX, backTopY, 65);
        line(frontRightX, frontTopY, backRightX, backTopY, 65);
        line(frontLeftX, frontBottomY, backLeftX, backBottomY, 40);
        line(frontRightX, frontBottomY, backRightX, backBottomY, 40);

        // neon fill stripes
        setColor(darken(baseColor, 0.6d));
        for (int stripe = 0; stripe < height; stripe += 6) {
            final int y = frontTopY + stripe;
            line(frontLeftX + 2, y, frontRightX - 2, y, 35);
        }
        // label
        setColor(lighten(baseColor, 1.3d));
        final String label = String.format("S%d-%d", series + 1, index + 1);
        PrintTool.print5(this, frontLeftX + barWidth / 2, originY + 14 + series * 12, 0, label, 0, 60);
    }

    private void drawLegend() {
        final int x = getWidth() - 140;
        final int y = 60;
        for (int i = 0; i < colors.length; i++) {
            setColor(colors[i]);
            dot(x, y + i * 18, 6, true, 90);
            PrintTool.print5(this, x + 10, y + i * 18 + 4, 0, "SERIES " + (i + 1), -1, 70);
        }
        setColor(0x00EEFF);
        PrintTool.print5(this, x, y - 16, 0, "DATASET", -1, 80);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File outDir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!outDir.exists()) outDir.mkdirs();
        final double[][] values = {
                {0.4, 0.7, 0.5, 0.8},
                {0.5, 0.6, 0.65, 0.45},
                {0.3, 0.55, 0.75, 0.6}
        };
        final WireframeBarPrismPlotter plotter = new WireframeBarPrismPlotter(680, 420, values);
        plotter.renderScene();
        final File out = new File(outDir, "wireframe_bar_prism.png");
        try {
            plotter.save(out, "png");
        } catch (final IOException e) {
            throw new RuntimeException("Failed to save prism chart", e);
        }
    }
}
