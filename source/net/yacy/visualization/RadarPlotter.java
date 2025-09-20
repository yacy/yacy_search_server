/**
 *  NeonVectorRadarPlotter
 *  Generates a retro radar style wireframe animation.
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

public class RadarPlotter extends RasterPlotter {

    private final int centerX;
    private final int centerY;
    private final int innerRadius;
    private final int outerRadius;
    private final double[] blipAngles;
    private final int[] blipRadius;
    private final long[] blipColors;

    public RadarPlotter(final int width, final int height) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000018);
        this.centerX = width / 2;
        this.centerY = height / 2;
        this.innerRadius = Math.min(width, height) / 6;
        this.outerRadius = Math.min(width, height) / 2 - 20;
        this.blipAngles = new double[]{18, 46, 102, 153, 214, 276, 320};
        this.blipRadius = new int[]{innerRadius + 30, innerRadius + 70, innerRadius + 110, innerRadius + 160, innerRadius + 120, innerRadius + 50, innerRadius + 90};
        this.blipColors = new long[]{0x44FFAA, 0x66FFEE, 0xFF88AA, 0x88AAFF, 0xFFFF66, 0xFF3366, 0x99FF99};
    }

    public void renderFrame(final int frameIndex) {
        clear();
        final double sweepAngle = (frameIndex % 8) * 45.0d;
        drawRadarGrid(sweepAngle);
        drawSweepBeam(sweepAngle);
        drawBlips(sweepAngle);
        drawOverlay();
    }

    private void drawRadarGrid(final double sweepAngle) {
        setColor(0x003366);
        for (int r = innerRadius; r <= outerRadius; r += 30) {
            arc(centerX, centerY, r - 1, r, 25);
        }
        setColor(0x004488);
        for (int a = 0; a < 360; a += 15) {
            arcLine(centerX, centerY, innerRadius - 10, outerRadius, a, true, null, null, -1, -1, -1, false);
        }
        setColor(0x001122);
        arc(centerX, centerY, innerRadius - 15, innerRadius - 8, 18);
        arc(centerX, centerY, outerRadius + 5, outerRadius + 8, 30);

        final double pulse = 10 + 8 * Math.sin(Math.toRadians(sweepAngle));
        setColor(0x00AAFF);
        arc(centerX, centerY, innerRadius + 5, (int) (innerRadius + 5 + pulse), 35);
    }

    private void drawSweepBeam(final double sweepAngle) {
        final int beamWidth = 4;
        final long[] colors = new long[]{0x00FFCC, 0x00BBAA, 0x008877, 0x004433};
        for (int i = 0; i < beamWidth; i++) {
            final double angle = sweepAngle - i * 2.5d;
            setColor(colors[i]);
            arcLine(centerX, centerY, innerRadius - 4, outerRadius + 5, angle, true, null, null, -1, -1, -1, false);
        }
    }

    private void drawBlips(final double sweepAngle) {
        for (int i = 0; i < blipAngles.length; i++) {
            final double angle = blipAngles[i];
            final int radius = blipRadius[i];
            final double diff = Math.abs(((sweepAngle - angle + 540) % 360) - 180);
            final int intensity = diff < 12 ? 90 : diff < 24 ? 60 : 35;
            final long color = blipColors[i];
            setColor(color);
            final int x = centerX + (int) Math.round(Math.cos(Math.toRadians(angle)) * radius);
            final int y = centerY - (int) Math.round(Math.sin(Math.toRadians(angle)) * radius);
            dot(x, y, diff < 10 ? 6 : 4, true, intensity);
            if (diff < 18) {
                setColor(lighten(color, 1.4d));
                dot(x, y, 2, true, 100);
            }
        }
    }

    private void drawOverlay() {
        setColor(0x006699);
        PrintTool.print(this, 4, 12, 0, "NEON VECTOR RADAR", -1, 80);
        setColor(0x00FFFF);
        PrintTool.print(this, 4, 24, 0, "SCAN ACTIVE", -1, 70);
        setColor(0x004466);
        PrintTool.print(this, getWidth() - 4, 12, 0, "RANGE:" + outerRadius + "px", 1, 70);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File target = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!target.exists()) target.mkdirs();
        final RadarPlotter radar = new RadarPlotter(480, 480);
        for (int frame = 0; frame < 8; frame++) {
            radar.renderFrame(frame);
            final File out = new File(target, String.format("neon_radar_%02d.png", frame));
            try {
                radar.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Failed to save radar frame", e);
            }
        }
    }
}
