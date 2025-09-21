/**
 *  DotMatrixWaterfallPlotter
 *  Emulates a retro dot-matrix printer style waterfall chart.
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

public class DotMatrixWaterfallPlotter extends RasterPlotter {

    private final double[] steps;
    private final String[] labels;
    private final long positiveColor = 0x66FF99;
    private final long negativeColor = 0xFF7777;
    private final long totalColor = 0x00FFFF;

    public DotMatrixWaterfallPlotter(final int width, final int height, final double[] steps, final String[] labels) {
        super(width, height, DrawMode.MODE_REPLACE, 0x040404);
        this.steps = steps;
        this.labels = labels;
    }

    public void render() {
        clear();
        drawPaperFeed();
        double cumulative = 0.0d;
        final int baseX = 80;
        final int baseline = getHeight() - 80;
        final int stepWidth = (getWidth() - baseX - 40) / steps.length;
        for (int i = 0; i < steps.length; i++) {
            final double step = steps[i];
            final boolean isTotal = (i == steps.length - 1);
            final long color = isTotal ? totalColor : step >= 0 ? positiveColor : negativeColor;
            final double previous = cumulative;
            cumulative += step;
            final double top = Math.max(previous, cumulative);
            final double bottom = Math.min(previous, cumulative);
            final int left = baseX + i * stepWidth;
            final int right = left + stepWidth - 20;
            fillDots(left, right, baseline - (int) (top * 3), baseline - (int) (bottom * 3), color, isTotal);
            drawStepLabel(left, baseline, labels[i], step, color);
            if (i < steps.length - 1) {
                setColor(0x003333);
                line(right + 4, baseline - (int) (cumulative * 3), right + stepWidth - 4, baseline - (int) (cumulative * 3), 25);
            }
        }
        drawAxes();
    }

    private void drawPaperFeed() {
        setColor(0x1A1A1A);
        for (int y = 0; y < getHeight(); y += 16) {
            line(0, y, getWidth(), y, 18);
        }
        setColor(0x202020);
        for (int x = 0; x < getWidth(); x += 12) {
            dot(x, 12, 1, true, 40);
            dot(x, getHeight() - 12, 1, true, 40);
        }
    }

    private void fillDots(final int left, final int right, final int top, final int bottom, final long color, final boolean heavy) {
        final int spacing = heavy ? 3 : 4;
        for (int y = top; y <= bottom; y += spacing) {
            for (int x = left; x <= right; x += spacing) {
                setColor(color);
                dot(x, y, heavy ? 2 : 1, true, heavy ? 90 : 70);
            }
        }
        // border
        setColor(darken(color, 0.5d));
        line(left, top, right, top, 60);
        line(left, bottom, right, bottom, 60);
        line(left, top, left, bottom, 60);
        line(right, top, right, bottom, 60);
    }

    private void drawStepLabel(final int left, final int baseline, final String label, final double value, final long color) {
        setColor(color);
        PrintTool.print5(this, left + 2, baseline + 14, 0, label, -1, 70);
        PrintTool.print5(this, left + 2, baseline + 24, 0, String.format("%+.1f", value), -1, 60);
    }

    private void drawAxes() {
        final int baseline = getHeight() - 80;
        setColor(0x005555);
        line(60, baseline, getWidth() - 20, baseline, 40);
        line(60, baseline, 60, 60, 40);
        for (int i = 0; i <= 6; i++) {
            int y = baseline - i * 30;
            line(60, y, getWidth() - 20, y, 20);
            PrintTool.print5(this, 42, y, 0, String.format("%+d", i * 10), -1, 50);
        }
        setColor(0x00FFFF);
        PrintTool.print5(this, getWidth() / 2, 30, 0, "WATERFALL LEDGER", 0, 85);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File dir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!dir.exists()) dir.mkdirs();
        final double[] steps = {40, 18, -12, 22, -15, 30};
        final String[] labels = {"BASE", "SALES", "RETURNS", "SERVICE", "LOSS", "TOTAL"};
        final DotMatrixWaterfallPlotter plotter = new DotMatrixWaterfallPlotter(640, 360, steps, labels);
        plotter.render();
        final File out = new File(dir, "dot_matrix_waterfall.png");
        try {
            plotter.save(out, "png");
        } catch (final IOException e) {
            throw new RuntimeException("Unable to write waterfall image", e);
        }
    }
}
