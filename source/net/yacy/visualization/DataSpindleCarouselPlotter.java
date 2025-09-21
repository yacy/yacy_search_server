/**
 *  DataSpindleCarouselPlotter
 *  Renders stacked rotating rings as a data spindle HUD element.
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

public class DataSpindleCarouselPlotter extends RasterPlotter {

    private final int centerX;
    private final int centerY;
    private final int[] ringRadii;
    private final long[] ringColors;

    public DataSpindleCarouselPlotter(final int width, final int height) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000014);
        this.centerX = width / 2;
        this.centerY = height / 2;
        this.ringRadii = new int[]{40, 70, 100, 130};
        this.ringColors = new long[]{0x00FFCC, 0x66FF99, 0xFF66CC, 0x66AAFF};
    }

    public void renderFrame(final int frame) {
        clear();
        drawBackground();
        for (int i = 0; i < ringRadii.length; i++) {
            drawRing(i, frame);
        }
        drawCenter(frame);
        drawOverlay(frame);
    }

    private void drawBackground() {
        setColor(0x001A2C);
        for (int y = 0; y < getHeight(); y += 6) line(0, y, getWidth(), y, 12);
    }

    private void drawRing(final int index, final int frame) {
        final int radius = ringRadii[index];
        final long color = ringColors[index];
        final double rotation = frame * (index + 1) * 7.0d;
        setColor(darken(color, 0.4d));
        arc(centerX, centerY, radius - 4, radius + 4, 40);
        setColor(color);
        for (int spoke = 0; spoke < 6; spoke++) {
            final double angle = rotation + spoke * 60.0d;
            arcLine(centerX, centerY, radius - 6, radius + 6, angle, true, null, null, -1, -1, -1, false);
            final int dotX = centerX + (int) (Math.cos(Math.toRadians(angle)) * radius);
            final int dotY = centerY - (int) (Math.sin(Math.toRadians(angle)) * radius);
            dot(dotX, dotY, 4, true, 85);
        }
        setColor(lighten(color, 1.3d));
        arc(centerX, centerY, radius - 1, radius + 1, 90);
    }

    private void drawCenter(final int frame) {
        final long color = 0x00EEFF;
        setColor(color);
        dot(centerX, centerY, 12, true, 90);
        final double angle = frame * 9.0d;
        arcLine(centerX, centerY, 12, 40, angle, true, null, null, -1, -1, -1, false);
        arcLine(centerX, centerY, 12, 40, angle + 120, true, null, null, -1, -1, -1, false);
        arcLine(centerX, centerY, 12, 40, angle + 240, true, null, null, -1, -1, -1, false);
    }

    private void drawOverlay(final int frame) {
        setColor(0x66FFFF);
        PrintTool.print5(this, 6, 12, 0, "DATA SPINDLE", -1, 80);
        setColor(0x00FFAA);
        PrintTool.print5(this, 6, 24, 0, String.format("IDX:%02d", frame % 32), -1, 70);
        setColor(0x00CCFF);
        PrintTool.print5(this, getWidth() - 6, 12, 0, "RPM" + (frame * 37 % 1000), 1, 70);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final DataSpindleCarouselPlotter plotter = new DataSpindleCarouselPlotter(400, 400);
        for (int frame = 0; frame < 8; frame++) {
            plotter.renderFrame(frame);
            final File out = new File(dir, String.format("data_spindle_%02d.png", frame));
            try {
                plotter.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Failed to save spindle frame", e);
            }
        }
    }
}
