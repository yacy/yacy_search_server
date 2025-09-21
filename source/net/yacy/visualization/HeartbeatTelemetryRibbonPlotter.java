/**
 *  HeartbeatTelemetryRibbonPlotter
 *  Decorative ECG-style ribbon for HUD overlays.
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

public class HeartbeatTelemetryRibbonPlotter extends RasterPlotter {

    private final int ribbonHeight = 80;
    private final int baseline;

    public HeartbeatTelemetryRibbonPlotter(final int width, final int height) {
        super(width, height, DrawMode.MODE_REPLACE, 0x00000C);
        this.baseline = height / 2;
    }

    public void renderFrame(final int frame) {
        clear();
        drawBackground();
        drawRibbon(frame);
        drawMarkers(frame);
        drawOverlay(frame);
    }

    private void drawBackground() {
        setColor(0x001020);
        for (int y = 0; y < getHeight(); y += 4) line(0, y, getWidth(), y, 12);
    }

    private void drawRibbon(final int frame) {
        final int left = 20;
        final int right = getWidth() - 20;
        final int amplitude = ribbonHeight / 2;
        setColor(0x00334C);
        line(left, baseline, right, baseline, 40);
        setColor(0x00FFAA);
        int prevX = left;
        int prevY = baseline;
        for (int x = left; x <= right; x++) {
            final double t = (x - left) / (double) (right - left);
            final double wave = heartbeatWave(t, frame);
            final int y = baseline - (int) (wave * amplitude);
            line(prevX, prevY, x, y, 80);
            prevX = x;
            prevY = y;
        }
    }

    private double heartbeatWave(final double t, final int frame) {
        final double phase = t * 4.0 + frame * 0.1;
        double wave = Math.sin(phase * Math.PI * 2) * 0.2;
        final double beat = (phase + frame * 0.02) % 1.0;
        if (beat < 0.04) wave += Math.sin(beat * Math.PI / 0.04) * 1.2;
        if (beat > 0.04 && beat < 0.08) wave -= Math.sin((beat - 0.04) * Math.PI / 0.04) * 0.6;
        return Math.max(-1.0, Math.min(1.0, wave));
    }

    private void drawMarkers(final int frame) {
        setColor(0x00BBFF);
        for (int x = 20; x < getWidth(); x += 60) {
            line(x, baseline - ribbonHeight / 2, x, baseline + ribbonHeight / 2, 20);
        }
        setColor(0x66FFFF);
        PrintTool.print(this, 20, baseline - ribbonHeight / 2 - 12, 0, "SYNC", -1, 70);
    }

    private void drawOverlay(final int frame) {
        setColor(0x66FFEE);
        PrintTool.print(this, 6, 12, 0, "CARDIAC TELEMETRY", -1, 80);
        setColor(0x00FFAA);
        PrintTool.print(this, getWidth() - 6, 12, 0, "BPM" + (frame * 3 % 90 + 60), 1, 70);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final HeartbeatTelemetryRibbonPlotter plotter = new HeartbeatTelemetryRibbonPlotter(640, 240);
        for (int frame = 0; frame < 8; frame++) {
            plotter.renderFrame(frame);
            final File out = new File(dir, String.format("telemetry_ribbon_%02d.png", frame));
            try {
                plotter.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Failed to save telemetry frame", e);
            }
        }
    }
}
