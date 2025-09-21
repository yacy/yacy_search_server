/**
 *  CrtHeatmapConsolePlotter
 *  Displays a heatmap with CRT-style scanlines and neon legends.
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
import java.util.Random;

public class CrtHeatmapConsolePlotter extends RasterPlotter {

    private final double[][] values;
    private final String[] rowLabels;
    private final String[] colLabels;

    public CrtHeatmapConsolePlotter(final int width, final int height, final double[][] values, final String[] rowLabels, final String[] colLabels) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000010);
        this.values = values;
        this.rowLabels = rowLabels;
        this.colLabels = colLabels;
    }

    public void render() {
        clear();
        drawScanlines();
        drawHeatmap();
        drawLegend();
    }

    private void drawScanlines() {
        setColor(0x001020);
        for (int y = 0; y < getHeight(); y += 3) line(0, y, getWidth(), y, 18);
    }

    private void drawHeatmap() {
        final int rows = values.length;
        final int cols = values[0].length;
        final int padding = 50;
        final int cellWidth = (getWidth() - padding * 2) / cols;
        final int cellHeight = (getHeight() - padding * 2) / rows;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                final double v = values[r][c];
                final long color = colorForValue(v);
                final int x = padding + c * cellWidth;
                final int y = padding + r * cellHeight;
                fillCell(x, y, cellWidth - 2, cellHeight - 2, color, v);
            }
        }
        // grid
        setColor(0x002F4F);
        for (int r = 0; r <= rows; r++) {
            final int y = padding + r * cellHeight;
            line(padding, y, padding + cols * cellWidth, y, 30);
        }
        for (int c = 0; c <= cols; c++) {
            final int x = padding + c * cellWidth;
            line(x, padding, x, padding + rows * cellHeight, 30);
        }
        // labels
        setColor(0x00FFDD);
        for (int r = 0; r < rowLabels.length; r++) {
            PrintTool.print5(this, padding - 8, padding + r * cellHeight + cellHeight / 2, 0, rowLabels[r], 1, 65);
        }
        for (int c = 0; c < colLabels.length; c++) {
            PrintTool.print5(this, padding + c * cellWidth + cellWidth / 2, padding - 12, 0, colLabels[c], 0, 65);
        }
    }

    private long colorForValue(final double value) {
        final double clamped = Math.max(0.0, Math.min(1.0, value));
        final int r = (int) (clamped * 255);
        final int g = (int) ((1.0 - Math.abs(clamped - 0.5) * 2) * 220);
        final int b = (int) ((1.0 - clamped) * 255);
        return (r << 16) | (g << 8) | b;
    }

    private void fillCell(final int x, final int y, final int width, final int height, final long color, final double value) {
        final long base = darken(color, 0.4d);
        for (int yy = 0; yy < height; yy++) {
            final double lineFactor = 0.8 + 0.2 * Math.sin(yy / 3.0);
            setColor(lighten(base, lineFactor));
            line(x, y + yy, x + width, y + yy, 60);
        }
        setColor(lighten(color, 1.6d));
        line(x, y, x + width, y, 80);
        line(x, y + height, x + width, y + height, 40);
        line(x, y, x, y + height, 40);
        line(x + width, y, x + width, y + height, 40);
        setColor(0x001010);
        PrintTool.print5(this, x + width / 2, y + height / 2, 0, String.format("%3.0f", value * 100), 0, 55);
    }

    private void drawLegend() {
        final int x = getWidth() - 60;
        final int y = 50;
        setColor(0x00FFCC);
        PrintTool.print5(this, x, y - 16, 90, "TEMP", -1, 70);
        for (int i = 0; i <= 8; i++) {
            final double v = i / 8.0;
            final long color = colorForValue(v);
            setColor(color);
            line(x - 12, y + i * 12, x, y + i * 12, 40);
            setColor(0x00EEFF);
            PrintTool.print5(this, x - 16, y + i * 12, 0, String.format("%2d", (int) (v * 100)), 1, 50);
        }
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File outDir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!outDir.exists()) outDir.mkdirs();
        final int rows = 6;
        final int cols = 8;
        final double[][] values = new double[rows][cols];
        final Random random = new Random(0xC0FFEE);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                values[r][c] = random.nextDouble();
            }
        }
        final String[] rowLabels = {"NORTH", "NE", "EAST", "SE", "SOUTH", "CORE"};
        final String[] colLabels = {"00", "03", "06", "09", "12", "15", "18", "21"};
        final CrtHeatmapConsolePlotter plotter = new CrtHeatmapConsolePlotter(720, 420, values, rowLabels, colLabels);
        plotter.render();
        final File out = new File(outDir, "crt_heatmap.png");
        try {
            plotter.save(out, "png");
        } catch (final IOException e) {
            throw new RuntimeException("Failed to save heatmap", e);
        }
    }
}
