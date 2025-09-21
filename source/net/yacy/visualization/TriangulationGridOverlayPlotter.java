/**
 *  TriangulationGridOverlayPlotter
 *  Produces a translucent triangulation HUD overlay for decorative maps.
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

public class TriangulationGridOverlayPlotter extends RasterPlotter {

    private final int centerX;
    private final int centerY;

    public TriangulationGridOverlayPlotter(final int width, final int height) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000018);
        this.centerX = width / 2;
        this.centerY = height / 2;
    }

    public void renderFrame(final int frame) {
        clear();
        drawBaseMap();
        drawTriangulation(frame);
        drawCrosshair(frame);
        drawReadouts(frame);
    }

    private void drawBaseMap() {
        setColor(0x001822);
        for (int y = 0; y < getHeight(); y += 8) line(0, y, getWidth(), y, 12);
        for (int x = 0; x < getWidth(); x += 10) line(x, 0, x, getHeight(), 12);
        setColor(0x002A38);
        for (int i = -6; i <= 6; i++) {
            line(centerX + i * 40, 0, centerX + i * 40, getHeight(), 18);
            line(0, centerY + i * 40, getWidth(), centerY + i * 40, 18);
        }
    }

    private void drawTriangulation(final int frame) {
        final int sectors = 6;
        final int radius = Math.min(getWidth(), getHeight()) / 2 - 40;
        final double baseAngle = frame * 6.0d;
        for (int i = 0; i < sectors; i++) {
            final double angle = baseAngle + i * (360.0 / sectors);
            final int x = centerX + (int) (Math.cos(Math.toRadians(angle)) * radius);
            final int y = centerY - (int) (Math.sin(Math.toRadians(angle)) * radius);
            setColor(0x00FFAA);
            line(centerX, centerY, x, y, 50);
            setColor(0x0066FF);
            arcLine(centerX, centerY, radius - 20, radius, angle + 6, true, null, null, -1, -1, -1, false);
        }
        // triangular sectors
        setColor(0x0044AA);
        for (int i = 0; i < sectors; i++) {
            final double angle1 = baseAngle + i * (360.0 / sectors);
            final double angle2 = baseAngle + (i + 1) * (360.0 / sectors);
            final int x1 = centerX + (int) (Math.cos(Math.toRadians(angle1)) * radius / 1.2);
            final int y1 = centerY - (int) (Math.sin(Math.toRadians(angle1)) * radius / 1.2);
            final int x2 = centerX + (int) (Math.cos(Math.toRadians(angle2)) * radius / 1.2);
            final int y2 = centerY - (int) (Math.sin(Math.toRadians(angle2)) * radius / 1.2);
            final int[] xs = {centerX, x1, x2};
            final int[] ys = {centerY, y1, y2};
            fillPolygon(xs, ys, 0x001E33, 25);
            setColor(0x0066FF);
            line(centerX, centerY, x1, y1, 35);
            line(centerX, centerY, x2, y2, 35);
            line(x1, y1, x2, y2, 20);
        }
    }

    private void drawCrosshair(final int frame) {
        final int pulse = (int) (10 + 5 * Math.sin(Math.toRadians(frame * 15)));
        setColor(0x00FFFF);
        line(centerX - 80, centerY, centerX + 80, centerY, 50);
        line(centerX, centerY - 80, centerX, centerY + 80, 50);
        dot(centerX, centerY, pulse, false, 40);
        dot(centerX, centerY, 4, true, 90);
    }

    private void drawReadouts(final int frame) {
        final int left = 20;
        final int top = 20;
        setColor(0x66FFFF);
        PrintTool.print5(this, left, top, 0, "TRIANGULATION", -1, 80);
        setColor(0x00FFAA);
        PrintTool.print5(this, left, top + 12, 0, String.format("Bearing:%03d", (frame * 6) % 360), -1, 70);
        PrintTool.print5(this, left, top + 24, 0, String.format("Phase:%02d", frame % 8), -1, 60);
        setColor(0x00BBFF);
        PrintTool.print5(this, getWidth() - 6, getHeight() - 10, 0, "COORD" + (frame % 5), 1, 60);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final TriangulationGridOverlayPlotter plotter = new TriangulationGridOverlayPlotter(480, 360);
        for (int frame = 0; frame < 8; frame++) {
            plotter.renderFrame(frame);
            final File out = new File(dir, String.format("triangulation_overlay_%02d.png", frame));
            try {
                plotter.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Failed to save triangulation frame", e);
            }
        }
    }
}
