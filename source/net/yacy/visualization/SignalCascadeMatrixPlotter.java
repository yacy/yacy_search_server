/**
 *  SignalCascadeMatrixPlotter
 *  Vertical cascaded bar HUD for decorative telemetry.
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

public class SignalCascadeMatrixPlotter extends RasterPlotter {

    private final int columns;
    private final int rows;
    private final double[][] amplitudes;
    private final Random random;

    public SignalCascadeMatrixPlotter(final int width, final int height, final int columns, final int rows) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000010);
        this.columns = columns;
        this.rows = rows;
        this.random = new Random(0xCAFEBABE);
        this.amplitudes = new double[columns][rows];
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows; r++) {
                amplitudes[c][r] = random.nextDouble();
            }
        }
    }

    public void renderFrame() {
        clear();
        drawBackground();
        final int columnWidth = getWidth() / columns;
        for (int c = 0; c < columns; c++) {
            updateColumn(c);
            drawColumn(c * columnWidth, columnWidth - 6, amplitudes[c]);
        }
        drawOverlay();
    }

    private void drawBackground() {
        setColor(0x001A2A);
        for (int x = 0; x < getWidth(); x += 10) line(x, 0, x, getHeight(), 12);
        for (int y = 0; y < getHeight(); y += 6) line(0, y, getWidth(), y, 10);
    }

    private void updateColumn(final int column) {
        for (int r = rows - 1; r > 0; r--) {
            amplitudes[column][r] = amplitudes[column][r - 1];
        }
        final double prev = amplitudes[column][0];
        double next = prev + (random.nextDouble() - 0.5) * 0.4;
        next = Math.max(0.05, Math.min(0.95, next));
        amplitudes[column][0] = next;
    }

    private void drawColumn(final int left, final int width, final double[] values) {
        final int barHeight = getHeight() / rows;
        for (int r = 0; r < rows; r++) {
            final double value = values[r];
            final int height = (int) (value * barHeight);
            final int y = getHeight() - (r + 1) * barHeight;
            final long color = value > 0.7 ? 0xFF5577 : value > 0.4 ? 0x00FFAA : 0x55CCFF;
            fillBar(left, y, width, height, color, r);
        }
    }

    private void fillBar(final int left, final int bottom, final int width, final int height, final long color, final int row) {
        final int top = bottom + (row % 2 == 0 ? 2 : 0);
        for (int i = 0; i < height; i += 3) {
            final int y = bottom - i;
            if (y < 0) break;
            setColor(darken(color, 0.4 + (i % 9) * 0.03));
            line(left + 2, y, left + width - 2, y, 65);
        }
        setColor(lighten(color, 1.5d));
        line(left + 2, top, left + width - 2, top, 85);
        line(left + 2, bottom - height, left + width - 2, bottom - height, 30);
    }

    private void drawOverlay() {
        setColor(0x66FFFF);
        PrintTool.print(this, 6, 14, 0, "SIGNAL CASCADE", -1, 80);
        setColor(0x00FFAA);
        PrintTool.print(this, getWidth() - 6, 14, 0, "CHANNELS:" + columns, 1, 70);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final SignalCascadeMatrixPlotter plotter = new SignalCascadeMatrixPlotter(480, 360, 10, 40);
        for (int frame = 0; frame < 8; frame++) {
            plotter.renderFrame();
            final File out = new File(dir, String.format("cascade_matrix_%02d.png", frame));
            try {
                plotter.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Could not save cascade frame", e);
            }
        }
    }
}
