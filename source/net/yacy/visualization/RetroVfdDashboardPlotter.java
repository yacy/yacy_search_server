/**
 *  RetroVfdDashboardPlotter
 *  Draws teal-on-black virtual fluorescent display gauges and counters.
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

public class RetroVfdDashboardPlotter extends RasterPlotter {

    private final int centerX;
    private final int centerY;

    public RetroVfdDashboardPlotter(final int width, final int height) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000008);
        this.centerX = width / 2;
        this.centerY = height / 2 + 20;
    }

    public void renderFrame(final double sweepFraction) {
        clear();
        drawBackground();
        drawGauge("THROUGHPUT", centerX - 140, centerY, 110, sweepFraction, 0x00F0D0, 0.78);
        drawGauge("LATENCY", centerX + 140, centerY, 110, 1.0 - sweepFraction * 0.6, 0x00C0FF, 0.45);
        drawCounters(sweepFraction);
        drawStatusLights(sweepFraction);
    }

    private void drawBackground() {
        setColor(0x001820);
        for (int y = 0; y < getHeight(); y += 6) {
            line(0, y, getWidth(), y, 20);
        }
        setColor(0x003040);
        line(0, centerY + 120, getWidth(), centerY + 120, 35);
        line(0, centerY - 140, getWidth(), centerY - 140, 25);
    }

    private void drawGauge(final String label, final int cx, final int cy, final int radius, final double fraction, final long color, final double labelOffset) {
        final int inner = radius - 28;
        final int outer = radius;
        setColor(darken(color, 0.3));
        arc(cx, cy, inner - 6, outer + 6, 18);
        setColor(darken(color, 0.5));
        arc(cx, cy, inner - 2, inner, 30);
        setColor(color);
        final int start = 210;
        final int sweep = (int) Math.round(300 * fraction);
        arc(cx, cy, inner, outer, start, start + sweep);
        for (int tick = 0; tick <= 10; tick++) {
            final int angle = start + tick * 30;
            final int intensity = tick % 5 == 0 ? 70 : 40;
            setColor(darken(color, tick % 5 == 0 ? 0.4 : 0.6));
            arcLine(cx, cy, inner - 6, inner + 6, angle, true, null, null, -1, -1, -1, false);
            if (tick % 5 == 0) {
                final int tx = cx + (int) (Math.cos(Math.toRadians(angle)) * (inner - 20));
                final int ty = cy - (int) (Math.sin(Math.toRadians(angle)) * (inner - 20));
                setColor(color);
                PrintTool.print(this, tx, ty, 0, Integer.toString(tick), -1, intensity);
            }
        }
        setColor(color);
        final int needleAngle = start + sweep;
        arcLine(cx, cy, 0, inner, needleAngle, true, null, null, -1, -1, -1, false);
        dot(cx, cy, 6, true, 80);
        PrintTool.print(this, cx, (int) (cy + labelOffset * radius), 0, label, 0, 75);
    }

    private void drawCounters(final double fraction) {
        final long color = 0x00FFD8;
        setColor(color);
        final int baseY = centerY + 150;
        PrintTool.print(this, centerX - 150, baseY, 0, "REQ/S", -1, 70);
        PrintTool.print(this, centerX + 150, baseY, 0, "ERROR%", -1, 70);
        setColor(0x003030);
        fillRectangle(centerX - 200, baseY - 4, centerX - 40, baseY + 20);
        fillRectangle(centerX + 40, baseY - 4, centerX + 200, baseY + 20);
        final String left = sevenSegmentNumber(4300 + (int) (fraction * 1200));
        final String right = sevenSegmentNumber((int) (fraction * 240));
        setColor(color);
        PrintTool.print(this, centerX - 40, baseY + 8, 0, left, -1, 100);
        PrintTool.print(this, centerX + 200, baseY + 8, 0, right, -1, 100);
    }

    private void fillRectangle(final int x1, final int y1, final int x2, final int y2) {
        for (int y = y1; y <= y2; y++) line(x1, y, x2, y, 18);
    }

    private void drawStatusLights(final double fraction) {
        final int top = centerY - 200;
        final String[] labels = {"NET", "CPU", "IO", "CACHE"};
        final long[] colors = {0x00FFCC, 0xFF8844, 0x66FF99, 0xFFEE44};
        for (int i = 0; i < labels.length; i++) {
            final int x = 80 + i * 90;
            final int intensity = (int) ((Math.sin(fraction * Math.PI * 2 + i) + 1.0) * 30) + 40;
            setColor(darken(colors[i], 0.4));
            dot(x, top, 12, true, intensity);
            dot(x, top, 6, true, intensity + 10);
            setColor(colors[i]);
            PrintTool.print(this, x, top - 20, 0, labels[i], 0, 60);
        }
    }

    private String sevenSegmentNumber(final int value) {
        final String s = String.format("%04d", Math.abs(value));
        return s;
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final RetroVfdDashboardPlotter plotter = new RetroVfdDashboardPlotter(640, 420);
        for (int frame = 0; frame < 8; frame++) {
            final double fraction = frame / 7.0d;
            plotter.renderFrame(fraction);
            final File out = new File(dir, String.format("vfd_dashboard_%02d.png", frame));
            try {
                plotter.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Failed to store frame", e);
            }
        }
    }
}
