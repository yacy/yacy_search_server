/**
 *  IsometricCityscapePlotter
 *  Synthesizes a pixelated skyline using a faux-isometric projection.
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

public class IsometricPlotter extends RasterPlotter {

    private static final int TILE_WIDTH = 24;
    private static final int TILE_HEIGHT = 12;
    private static final int BASELINE = 260;
    private static final int ORIGIN_X = 240;

    private final int[][] heightMap;
    private final long[] palette;

    public IsometricPlotter(final int width, final int height, final int[][] heightMap) {
        super(width, height, DrawMode.MODE_REPLACE, 0x00000C);
        this.heightMap = heightMap;
        this.palette = new long[]{0x1A2E59, 0x24407B, 0x33589B, 0x1F6C8F, 0x3F9FBF, 0x7FD3FF};
    }

    public void renderScene() {
        clear();
        drawBackgroundGrid();
        for (int y = 0; y < heightMap.length; y++) {
            for (int x = heightMap[y].length - 1; x >= 0; x--) {
                final int h = heightMap[y][x];
                if (h <= 0) continue;
                final long baseColor = palette[(x + y) % palette.length];
                drawBuilding(x, y, h, baseColor);
            }
        }
        drawForeground();
    }

    private void drawBackgroundGrid() {
        setColor(0x001133);
        for (int i = 0; i < 7; i++) {
            int y = BASELINE + i * 10;
            line(0, y, getWidth(), y, 12 + i * 2);
        }
        setColor(0x002244);
        for (int i = -6; i <= 6; i++) {
            final int x0 = ORIGIN_X + i * TILE_WIDTH / 2;
            line(x0, BASELINE, x0 + TILE_WIDTH * 6, BASELINE + TILE_HEIGHT * 6, 20);
            line(x0, BASELINE, x0 - TILE_WIDTH * 6, BASELINE + TILE_HEIGHT * 6, 20);
        }
    }

    private void drawBuilding(final int gridX, final int gridY, final int height, final long color) {
        final int baseX = ORIGIN_X + (gridX - gridY) * (TILE_WIDTH / 2);
        final int baseY = BASELINE + (gridX + gridY) * (TILE_HEIGHT / 2);
        final int topY = baseY - height;
        final int halfWidth = TILE_WIDTH / 2;
        final int halfHeight = TILE_HEIGHT / 2;

        // top face shading
        setColor(lighten(color, 1.3d));
        for (int i = 0; i <= halfHeight; i++) {
            int y = topY - halfHeight + i;
            int xLeft = baseX - halfWidth + i;
            int xRight = baseX + halfWidth - i;
            if (xLeft <= xRight) line(xLeft, y, xRight, y, 60);
        }
        for (int i = 0; i <= halfHeight; i++) {
            int y = topY + i;
            int xLeft = baseX - halfWidth + i;
            int xRight = baseX + halfWidth - i;
            if (xLeft <= xRight) line(xLeft, y, xRight, y, 60);
        }

        // left wall
        setColor(darken(color, 0.8d));
        for (int i = 0; i <= height; i++) {
            int y = topY + i;
            int xLeft = baseX - halfWidth;
            int xRight = baseX - halfWidth + halfHeight;
            line(xLeft, y, xRight, y + halfHeight, 45);
        }
        // right wall
        setColor(darken(color, 0.6d));
        for (int i = 0; i <= height; i++) {
            int y = topY + i;
            int xLeft = baseX + halfWidth - halfHeight;
            int xRight = baseX + halfWidth;
            line(xLeft, y + halfHeight, xRight, y, 50);
        }

        // outline edges
        setColor(lighten(color, 1.6d));
        line(baseX, topY - halfHeight, baseX + halfWidth, topY, 100);
        line(baseX, topY - halfHeight, baseX - halfWidth, topY, 100);
        line(baseX - halfWidth, topY, baseX - halfWidth, baseY, 80);
        line(baseX + halfWidth, topY, baseX + halfWidth, baseY, 80);
        line(baseX - halfWidth, baseY, baseX, baseY + halfHeight, 70);
        line(baseX + halfWidth, baseY, baseX, baseY + halfHeight, 70);

        // neon windows
        setColor(0xFFEE66);
        for (int row = 4; row < height; row += 6) {
            int y = topY + row;
            line(baseX - halfWidth / 2, y, baseX - halfWidth / 6, y, 85);
            line(baseX + halfWidth / 6, y, baseX + halfWidth / 2, y, 85);
        }
    }

    private void drawForeground() {
        setColor(0x00AAFF);
        PrintTool.print(this, 6, 12, 0, "ISOMETRIC CITYSCAPE", -1, 90);
        setColor(0x66FFFF);
        PrintTool.print(this, 6, 24, 0, "GRID SECTOR 7B", -1, 70);
        setColor(0x00CCAA);
        PrintTool.print(this, getWidth() - 6, getHeight() - 8, 0, "POPULATION: 1.2M", 1, 60);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File target = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!target.exists()) target.mkdirs();
        final int[][] heights = {
            {40, 60, 80, 70, 45},
            {20, 50, 110, 90, 40},
            {10, 30, 140, 120, 70},
            {0, 40, 90, 80, 30},
            {0, 10, 40, 30, 20}
        };
        final IsometricPlotter city = new IsometricPlotter(480, 320, heights);
        city.renderScene();
        final File out = new File(target, "isometric_city.png");
        try {
            city.save(out, "png");
        } catch (final IOException e) {
            throw new RuntimeException("Failed to save cityscape", e);
        }
    }
}
