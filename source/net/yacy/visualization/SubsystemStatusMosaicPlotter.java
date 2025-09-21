/**
 *  SubsystemStatusMosaicPlotter
 *  Animated mosaic of subsystem indicators for decorative HUD backdrops.
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

public class SubsystemStatusMosaicPlotter extends RasterPlotter {

    private final int columns;
    private final int rows;
    private final double[][] phases;
    private final Random random;

    public SubsystemStatusMosaicPlotter(final int width, final int height, final int columns, final int rows) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000015);
        this.columns = columns;
        this.rows = rows;
        this.phases = new double[rows][columns];
        this.random = new Random(0x1ACEB00C);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                phases[r][c] = random.nextDouble() * Math.PI * 2;
            }
        }
    }

    public void renderFrame(final int frame) {
        clear();
        drawBackground();
        final int cellWidth = getWidth() / columns;
        final int cellHeight = getHeight() / rows;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                drawCell(r, c, frame, c * cellWidth, r * cellHeight, cellWidth, cellHeight);
            }
        }
    }

    private void drawBackground() {
        setColor(0x001020);
        for (int y = 0; y < getHeight(); y += 6) line(0, y, getWidth(), y, 12);
    }

    private void drawCell(final int row, final int col, final int frame, final int x, final int y, final int width, final int height) {
        final double phase = phases[row][col] + frame * 0.2 + col * 0.1;
        final long color = colorForCell(row, col);
        setColor(darken(color, 0.5d));
        for (int yy = y + 2; yy < y + height - 2; yy++) line(x + 2, yy, x + width - 2, yy, 25);
        setColor(color);
        line(x + 2, y + 2, x + width - 2, y + 2, 70);
        line(x + 2, y + height - 2, x + width - 2, y + height - 2, 40);
        line(x + 2, y + 2, x + 2, y + height - 2, 40);
        line(x + width - 2, y + 2, x + width - 2, y + height - 2, 40);
        final int inset = 6;
        switch ((row + col) % 4) {
            case 0: drawSpinner(x + inset, y + inset, width - inset * 2, height - inset * 2, phase); break;
            case 1: drawHistogram(x + inset, y + inset, width - inset * 2, height - inset * 2, phase); break;
            case 2: drawWaveform(x + inset, y + inset, width - inset * 2, height - inset * 2, phase); break;
            default: drawPulseDot(x + inset, y + inset, width - inset * 2, height - inset * 2, phase); break;
        }
    }

    private void drawSpinner(final int x, final int y, final int width, final int height, final double phase) {
        final int cx = x + width / 2;
        final int cy = y + height / 2;
        final int radius = Math.min(width, height) / 2 - 2;
        setColor(0x00FFAA);
        for (int spoke = 0; spoke < 4; spoke++) {
            final double angle = phase * 40 + spoke * 90;
            final int sx = cx + (int) (Math.cos(Math.toRadians(angle)) * radius);
            final int sy = cy - (int) (Math.sin(Math.toRadians(angle)) * radius);
            line(cx, cy, sx, sy, 70 - spoke * 5);
        }
    }

    private void drawHistogram(final int x, final int y, final int width, final int height, final double phase) {
        final int bars = Math.max(3, width / 6);
        for (int b = 0; b < bars; b++) {
            final double value = 0.5 + Math.sin(phase + b * 0.6) * 0.45;
            final int barHeight = (int) (value * height);
            final int left = x + b * (width / bars);
            setColor(0x66FFEE);
            for (int yy = y + height - barHeight; yy < y + height; yy += 2) line(left + 1, yy, left + (width / bars) - 2, yy, 60);
        }
    }

    private void drawWaveform(final int x, final int y, final int width, final int height, final double phase) {
        setColor(0xFF66CC);
        int prevX = x;
        int prevY = y + height / 2;
        for (int i = 0; i <= width; i++) {
            final double t = i / (double) width;
            final int yy = y + height / 2 - (int) (Math.sin(phase * 3 + t * Math.PI * 2) * height / 2.2);
            line(prevX, prevY, x + i, yy, 70);
            prevX = x + i;
            prevY = yy;
        }
    }

    private void drawPulseDot(final int x, final int y, final int width, final int height, final double phase) {
        final int cx = x + width / 2;
        final int cy = y + height / 2;
        final int radius = (int) (Math.abs(Math.sin(phase)) * Math.min(width, height) / 2);
        setColor(0x00DDFF);
        dot(cx, cy, radius + 1, true, 80);
        setColor(0x66FFAA);
        dot(cx, cy, radius / 2 + 1, true, 90);
    }

    private long colorForCell(final int row, final int col) {
        final int base = (row * 37 + col * 53) & 0xFF;
        final int r = 40 + (base & 0x3F);
        final int g = 120 + ((base >> 2) & 0x3F);
        final int b = 160 + ((base >> 4) & 0x3F);
        return (r << 16) | (g << 8) | b;
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final SubsystemStatusMosaicPlotter plotter = new SubsystemStatusMosaicPlotter(640, 360, 8, 4);
        for (int frame = 0; frame < 8; frame++) {
            plotter.renderFrame(frame);
            final File out = new File(dir, String.format("status_mosaic_%02d.png", frame));
            try {
                plotter.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Unable to save mosaic frame", e);
            }
        }
    }
}
