/**
 *  PixelTrendMatrixPlotter
 *  Draws a matrix of miniature trend charts on a retro CRT grid.
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

public class PixelTrendMatrixPlotter extends RasterPlotter {

    private final int rows;
    private final int cols;
    private final double[][] data;
    private final Random random;

    public PixelTrendMatrixPlotter(final int width, final int height, final int rows, final int cols) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000012);
        this.rows = rows;
        this.cols = cols;
        this.random = new Random(0xBADC0DE);
        this.data = new double[rows * cols][16];
        for (int i = 0; i < data.length; i++) {
            double value = random.nextDouble();
            for (int j = 0; j < data[i].length; j++) {
                value += (random.nextDouble() - 0.5) * 0.25;
                value = Math.min(Math.max(value, 0.05), 0.95);
                data[i][j] = value;
            }
        }
    }

    public void renderMatrix(final double wobble) {
        clear();
        drawGrid();
        final int cellWidth = getWidth() / cols;
        final int cellHeight = getHeight() / rows;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                final int index = row * cols + col;
                final int x0 = col * cellWidth;
                final int y0 = row * cellHeight;
                drawCell(x0, y0, cellWidth, cellHeight, data[index], (index + wobble) % 1.0);
            }
        }
    }

    private void drawGrid() {
        setColor(0x001A33);
        for (int y = 0; y < getHeight(); y += 8) line(0, y, getWidth(), y, 15);
        for (int x = 0; x < getWidth(); x += 8) line(x, 0, x, getHeight(), 15);
    }

    private void drawCell(final int x0, final int y0, final int width, final int height, final double[] values, final double wobble) {
        setColor(0x002C44);
        for (int y = y0; y < y0 + height; y++) line(x0, y, x0 + width, y, 20);
        final int padding = 6;
        final int innerWidth = width - 2 * padding;
        final int innerHeight = height - 2 * padding - 14;
        final int chartLeft = x0 + padding;
        final int chartTop = y0 + padding + 12;
        final long baselineColor = 0x003F6F + ((int) (wobble * 4) << 16);
        setColor(baselineColor);
        line(chartLeft, chartTop + innerHeight / 2, chartLeft + innerWidth, chartTop + innerHeight / 2, 35);
        setColor(0x00FFAA);
        final int points = values.length;
        int prevX = chartLeft;
        int prevY = chartTop + (int) (innerHeight * (1.0 - values[0]));
        for (int i = 1; i < points; i++) {
            final int x = chartLeft + i * innerWidth / (points - 1);
            final int y = chartTop + (int) (innerHeight * (1.0 - values[i])) + (int) (Math.sin((wobble + i) * 0.6) * 2);
            line(prevX, prevY, x, y, 70);
            prevX = x;
            prevY = y;
        }
        setColor(0x00FFFF);
        for (int i = 0; i < points; i += 3) {
            final int x = chartLeft + i * innerWidth / (points - 1);
            final int y = chartTop + (int) (innerHeight * (1.0 - values[i]));
            dot(x, y, 2, true, 80);
        }
        setColor(0x66FFDD);
        PrintTool.print(this, x0 + width / 2, y0 + 10, 0, labelForCell(values), 0, 70);
    }

    private String labelForCell(final double[] values) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        final int trend = (int) ((values[values.length - 1] - values[0]) * 100);
        return String.format("%+d%%  [%02d:%02d]", trend, (int) (min * 100), (int) (max * 100));
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final PixelTrendMatrixPlotter plotter = new PixelTrendMatrixPlotter(640, 400, 3, 4);
        for (int frame = 0; frame < 8; frame++) {
            plotter.renderMatrix(frame / 8.0d);
            final File out = new File(dir, String.format("trend_matrix_%02d.png", frame));
            try {
                plotter.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Failed to save matrix frame", e);
            }
        }
    }
}
