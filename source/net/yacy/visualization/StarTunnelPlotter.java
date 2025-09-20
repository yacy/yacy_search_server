/**
 *  StarTunnelPlotter
 *  Creates an 80s style star tunnel animation reminiscent of hyperspace jumps.
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

public class StarTunnelPlotter extends RasterPlotter {

    private static final class Star {
        double x;
        double y;
        double z;
    }

    private final Star[] stars;
    private final Random random;
    private final double speed;
    private final double focalLength;
    private final int centerX;
    private final int centerY;

    public StarTunnelPlotter(final int width, final int height, final int starCount) {
        super(width, height, DrawMode.MODE_REPLACE, 0x000010);
        this.random = new Random(0xCADCADE);
        this.stars = new Star[starCount];
        this.speed = 12.0d;
        this.focalLength = Math.min(width, height) * 1.2d;
        this.centerX = width / 2;
        this.centerY = height / 2;
        for (int i = 0; i < starCount; i++) {
            this.stars[i] = randomStar();
        }
    }

    private Star randomStar() {
        final Star s = new Star();
        s.z = random.nextDouble() * 400 + 40;
        final double angle = random.nextDouble() * 2 * Math.PI;
        final double radius = Math.sqrt(random.nextDouble()) * 220;
        s.x = Math.cos(angle) * radius;
        s.y = Math.sin(angle) * radius;
        return s;
    }

    public void renderFrame() {
        clear();
        drawGrid();
        setColor(0x66FFFF);
        for (final Star star : stars) {
            star.z -= speed;
            if (star.z < 20) {
                final Star replacement = randomStar();
                star.x = replacement.x;
                star.y = replacement.y;
                star.z = replacement.z + 400;
            }
            final double scale = focalLength / star.z;
            final int x = centerX + (int) Math.round(star.x * scale);
            final int y = centerY + (int) Math.round(star.y * scale);
            if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) continue;
            final int intensity = Math.min(100, (int) (120 - star.z / 4));
            final int radius = Math.max(1, (int) (6 - star.z / 80));
            dot(x, y, radius, true, intensity);
        }
        drawOverlay();
    }

    private void drawGrid() {
        setColor(0x002244);
        for (int i = 0; i < 40; i++) {
            final int offset = i * 12;
            final int intensity = 20 + (i % 8) * 4;
            final int y = getHeight() - offset;
            if (y < 0) break;
            line(0, y, getWidth(), y, intensity);
        }
        setColor(0x003366);
        for (int a = -80; a <= 80; a += 10) {
            final double rad = Math.toRadians(a);
            final int x1 = centerX;
            final int y1 = centerY;
            final int x2 = centerX + (int) (Math.cos(rad) * getWidth());
            final int y2 = centerY + (int) (Math.sin(rad) * getHeight());
            line(x1, y1, x2, y2, 28);
        }
        setColor(0x004477);
        dot(centerX, centerY, 12, true, 30);
    }

    private void drawOverlay() {
        setColor(0x00FFCC);
        PrintTool.print(this, 4, 12, 0, "STAR TUNNEL", -1, 90);
        setColor(0x66FFFF);
        PrintTool.print(this, 4, 22, 0, "HYPERSPACE VECTOR", -1, 70);
        setColor(0x00AACC);
        PrintTool.print(this, getWidth() - 4, getHeight() - 6, 0, "SPEED:" + (int) speed * stars.length, 1, 70);
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");
        final File target = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!target.exists()) target.mkdirs();
        final StarTunnelPlotter tunnel = new StarTunnelPlotter(480, 360, 160);
        for (int frame = 0; frame < 8; frame++) {
            tunnel.renderFrame();
            final File out = new File(target, String.format("star_tunnel_%02d.png", frame));
            try {
                tunnel.save(out, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Failed to store tunnel frame", e);
            }
        }
    }
}
