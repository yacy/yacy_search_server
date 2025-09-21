/**
 *  SpectralAnalyzerStripPlotter
 *  Horizontal strip-style spectral analyzer for decorative HUDs.
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

public class SpectralAnalyzerStripPlotter extends RasterPlotter {

    private final double[][] bands;
    private final Random random;

    public SpectralAnalyzerStripPlotter(final int width, final int height, final int segments, final int bandsPerSegment) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000011);
        this.random = new Random(0x5EC7C0DE);
        this.bands = new double[segments][bandsPerSegment];
        for (int s = 0; s < segments; s++) {
            for (int b = 0; b < bandsPerSegment; b++) {
                bands[s][b] = random.nextDouble();
            }
        }
    }

    public void renderFrame() {
        clear();
        drawBackground();
        final int segments = bands.length;
        final int segmentWidth = getWidth() / segments;
        for (int s = 0; s < segments; s++) {
            updateSegment(s);
            drawSegment(s * segmentWidth, segmentWidth, bands[s]);
        }
        drawOverlay();
    }

    private void drawBackground() {
        setColor(0x001928);
        for (int y = 0; y < getHeight(); y += 4) line(0, y, getWidth(), y, 15);
    }

    private void updateSegment(final int segment) {
        final double[] values = bands[segment];
        for (int i = 0; i < values.length; i++) {
            double next = values[i] + (random.nextDouble() - 0.5) * 0.3;
            next = Math.max(0.05, Math.min(1.0, next));
            values[i] = next * 0.7 + values[i] * 0.3;
        }
    }

    private void drawSegment(final int left, final int width, final double[] values) {
        final int bottom = getHeight() - 24;
        final int top = 28;
        final int bandCount = values.length;
        final int bandWidth = Math.max(2, (width - 12) / bandCount);
        for (int i = 0; i < bandCount; i++) {
            final double value = values[i];
            final int height = (int) (value * (bottom - top));
            final int x = left + 6 + i * bandWidth;
            final long color = colorForBand(value, i);
            setColor(color);
            for (int y = bottom; y > bottom - height; y -= 2) line(x, y, x + bandWidth - 2, y, 75);
            setColor(lighten(color, 1.4d));
            line(x, bottom - height, x + bandWidth - 2, bottom - height, 90);
        }
        setColor(0x004F6F);
        line(left + 4, top, left + width - 4, top, 30);
        line(left + 4, bottom, left + width - 4, bottom, 30);
    }

    private long colorForBand(final double value, final int index) {
        final int hueShift = (index * 20) % 120;
        final double base = Math.min(1.0, value + hueShift / 160.0);
        final int r = (int) (base * 255);
        final int g = (int) ((0.5 + value / 2) * 180);
        final int b = (int) ((1.0 - base) * 180 + 60);
        return (r << 16) | (g << 8) | b;
    }

    private void drawOverlay() {
        setColor(0x66FFFF);
        PrintTool.print(this, 6, 12, 0, "SPECTRAL ANALYZER", -1, 80);
        setColor(0x00FFAA);
        PrintTool.print(this, 6, getHeight() - 10, 0, "HZ" + random.nextInt(8000), -1, 60);
        setColor(0x00BBFF);
        PrintTool.print(this, getWidth() - 6, 12, 0, "GAIN" + random.nextInt(30), 1, 70);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final SpectralAnalyzerStripPlotter plotter = new SpectralAnalyzerStripPlotter(640, 220, 12, 24);
        for (int frame = 0; frame < 8; frame++) {
            plotter.renderFrame();
            final File out = new File(dir, String.format("spectral_strip_%02d.png", frame));
            try {
                plotter.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Could not write spectral frame", e);
            }
        }
    }
}
