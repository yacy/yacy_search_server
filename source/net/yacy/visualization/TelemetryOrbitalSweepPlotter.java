/**
 *  TelemetryOrbitalSweepPlotter
 *  Decorative orbital HUD element with sweeping vectors.
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

public class TelemetryOrbitalSweepPlotter extends RasterPlotter {

    private final int centerX;
    private final int centerY;
    private final int baseRadius;
    private final double[] satelliteAngles;
    private final int[] satelliteRadii;

    public TelemetryOrbitalSweepPlotter(final int width, final int height) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000012);
        this.centerX = width / 2;
        this.centerY = height / 2;
        this.baseRadius = Math.min(width, height) / 3;
        this.satelliteAngles = new double[]{15, 80, 130, 210, 275, 330};
        this.satelliteRadii = new int[]{baseRadius - 20, baseRadius + 40, baseRadius + 90, baseRadius - 50, baseRadius + 60, baseRadius + 10};
    }

    public void renderFrame(final int frame) {
        clear();
        drawBackgroundGrid();
        drawOrbits(frame);
        drawSatellites(frame);
        drawOverlay(frame);
    }

    private void drawBackgroundGrid() {
        setColor(0x001A33);
        for (int r = 30; r <= baseRadius + 140; r += 30) arc(centerX, centerY, r - 3, r, 20);
        setColor(0x002A44);
        for (int a = 0; a < 360; a += 10) line(centerX, centerY, centerX + (int) (Math.cos(Math.toRadians(a)) * (baseRadius + 150)), centerY + (int) (-Math.sin(Math.toRadians(a)) * (baseRadius + 150)), 15);
    }

    private void drawOrbits(final int frame) {
        final double sweep = (frame % 8) * 45.0d;
        setColor(0x00FFCC);
        final int outer = baseRadius + 120;
        arc(centerX, centerY, outer - 2, outer + 2, 30);
        arcLine(centerX, centerY, 40, outer, sweep, true, null, null, -1, -1, -1, false);
        arcLine(centerX, centerY, 40, outer, sweep - 12, true, null, null, -1, -1, -1, false);
        setColor(0x00AAEE);
        for (int r = baseRadius - 20; r <= baseRadius + 90; r += 40) arc(centerX, centerY, r - 1, r + 1, 40);
    }

    private void drawSatellites(final int frame) {
        final double sweep = (frame % 8) * 45.0d;
        for (int i = 0; i < satelliteAngles.length; i++) {
            final double angle = satelliteAngles[i] + frame * (i + 1) * 2.5d;
            final int radius = satelliteRadii[i];
            final int x = centerX + (int) Math.round(Math.cos(Math.toRadians(angle)) * radius);
            final int y = centerY - (int) Math.round(Math.sin(Math.toRadians(angle)) * radius);
            final long color = i % 2 == 0 ? 0x66FFEE : 0xFF66CC;
            setColor(color);
            dot(x, y, 6, true, 90);
            setColor(lighten(color, 1.6d));
            dot(x, y, 2, true, 100);
            setColor(color);
            arcLine(centerX, centerY, radius - 12, radius, angle, true, null, null, -1, -1, -1, false);
        }
        setColor(0x00FFAA);
        final int pulseRadius = baseRadius + (int) (Math.sin(Math.toRadians(sweep * 3)) * 12);
        arc(centerX, centerY, pulseRadius - 1, pulseRadius + 1, 50);
    }

    private void drawOverlay(final int frame) {
        setColor(0x66FFFF);
        PrintTool.print5(this, 6, 12, 0, "ORBITAL TELEMETRY", -1, 80);
        PrintTool.print5(this, 6, 22, 0, String.format("SWEEP:%03d", (frame * 45) % 360), -1, 70);
        setColor(0x00CCFF);
        PrintTool.print5(this, getWidth() - 6, 12, 0, "ALT/KM", 1, 70);
        PrintTool.print5(this, getWidth() - 6, 22, 0, "VEL/MPS", 1, 70);
        final int statusY = getHeight() - 18;
        setColor(0x00FFAA);
        PrintTool.print5(this, 6, statusY, 0, "LOCK", -1, 70);
        setColor(0xFF66CC);
        PrintTool.print5(this, 70, statusY, 0, "TRACK", -1, 60);
        setColor(0x00DDFF);
        PrintTool.print5(this, getWidth() - 6, statusY, 0, "SIG" + (frame % 8), 1, 60);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final TelemetryOrbitalSweepPlotter plotter = new TelemetryOrbitalSweepPlotter(480, 480);
        for (int frame = 0; frame < 8; frame++) {
            plotter.renderFrame(frame);
            final File out = new File(dir, String.format("telemetry_orbit_%02d.png", frame));
            try {
                plotter.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Unable to save orbital frame", e);
            }
        }
    }
}
