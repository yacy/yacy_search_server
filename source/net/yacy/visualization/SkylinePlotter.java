/**
 *  SkylinePlotter
 *  Retro wireframe skyline renderer for YaCy visualizations.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A painter that renders a perspective wireframe skyline on top of a classic
 * horizon grid. Objects are placed on a two dimensional field, projected with a
 * simple pinhole camera model and depth sorted so nearer objects cover the
 * distant ones. The renderer focuses on the retro aesthetics of early 80s
 * vector displays while being compatible with the existing {@link RasterPlotter}
 * infrastructure.
 */
public class SkylinePlotter extends RasterPlotter {

    private final SkylineConfig config;
    private final List<SkylineObject> objects;
    private final List<SkylineBeam> beams;

    private final int centerX;
    private final int horizonY;

    /**
     * Create a new skyline renderer with the supplied configuration.
     *
     * @param config the configuration (must not be {@code null})
     */
    public SkylinePlotter(final SkylineConfig config) {
        super(config.width, config.height, DrawMode.MODE_REPLACE, config.backgroundColor);
        this.config = config.copy();
        this.objects = new ArrayList<SkylineObject>();
        this.beams = new ArrayList<SkylineBeam>();
        this.centerX = getWidth() / 2;
        this.horizonY = (config.horizonY >= 0) ? config.horizonY : getHeight() / 4;
    }

    public SkylineConfig getConfig() {
        return this.config.copy();
    }

    /**
     * Remove all currently registered skyline objects.
     */
    public void clearScene() {
        this.objects.clear();
        this.beams.clear();
    }
    
    /**
     * Types of supported 3D shapes.
     */
    public static enum ShapeType {
        BOX,
        SPHERE,
        PYRAMID,
        POLYLINE
    }

    /**
     * Configuration data class. All values are in world units unless stated
     * otherwise. Public fields are used here for simplicity; callers should
     * populate them before passing the configuration to the constructor.
     */
    public static class SkylineConfig {
        public int width;
        public int height;
        public long backgroundColor;
        public long gridColor = 0x00ccccL;
        public int gridIntensity = 35;
        public int gridPulse = 8;
        public double gridStepX = 30.0d;
        public double gridStepZ = 40.0d;
        public double gridNear = 6.0d;
        public double fieldWidth = 400.0d;
        public double fieldDepth = 600.0d;
        public double focalLength = 520.0d;
        public double cameraDepth = 160.0d;
        public double groundLevel = 220.0d;
        public double nearPlane = 4.0d;
        public int horizonY = -1;

        private SkylineConfig copy() {
            final SkylineConfig clone = new SkylineConfig();
            clone.width = this.width;
            clone.height = this.height;
            clone.backgroundColor = this.backgroundColor;
            clone.gridColor = this.gridColor;
            clone.gridIntensity = this.gridIntensity;
            clone.gridPulse = this.gridPulse;
            clone.gridStepX = this.gridStepX;
            clone.gridStepZ = this.gridStepZ;
            clone.gridNear = this.gridNear;
            clone.fieldWidth = this.fieldWidth;
            clone.fieldDepth = this.fieldDepth;
            clone.focalLength = this.focalLength;
            clone.cameraDepth = this.cameraDepth;
            clone.groundLevel = this.groundLevel;
            clone.nearPlane = this.nearPlane;
            clone.horizonY = this.horizonY;
            return clone;
        }
    }
    
    /**
     * A drawable object within the skyline scene. Instances are configured
     * through fluently chained setters for retro convenience.
     */
    public static class SkylineObject {
        private final ShapeType shape;
        private final double centerX;
        private final double centerZ;
        private final double sizeX;
        private final double sizeZ;
        private final double baseHeight;
        private final double objectHeight;
        private final long color;

        private Long accentColor;
        private Long edgeColor;
        private int intensity = 80;
        private double bobAmplitude = 0.0d;
        private double pulseAmplitude = 0.0d;
        private int patternModulo = 0;
        private double patternPhase = 0.0d;
        private String label;
        private int labelOffsetX = 0;
        private int labelOffsetY = -12;
        private int labelIntensity = 100;
        private Long labelColor;
        private double animationPhase = 0.0d;

        public SkylineObject(final ShapeType shape,
                               final double centerX,
                               final double centerZ,
                               final double sizeX,
                               final double sizeZ,
                               final double baseHeight,
                               final double objectHeight,
                               final long color) {
            this.shape = shape;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.baseHeight = baseHeight;
            this.objectHeight = objectHeight;
            this.color = color;
        }

        private double sortKey() {
            return this.centerZ + this.objectHeight;
        }

        public SkylineObject accent(final long accentColor) {
            this.accentColor = Long.valueOf(accentColor);
            return this;
        }

        public SkylineObject edges(final long edgeColor) {
            this.edgeColor = Long.valueOf(edgeColor);
            return this;
        }

        public SkylineObject intensity(final int intensity) {
            this.intensity = intensity;
            return this;
        }

        public SkylineObject bob(final double amplitude) {
            this.bobAmplitude = amplitude;
            return this;
        }

        public SkylineObject pulse(final double amplitude) {
            this.pulseAmplitude = amplitude;
            return this;
        }

        public SkylineObject dotted(final int modulo) {
            this.patternModulo = Math.max(0, modulo);
            return this;
        }

        public SkylineObject patternPhase(final double phaseDegrees) {
            this.patternPhase = phaseDegrees;
            return this;
        }

        public SkylineObject animationPhase(final double phaseDegrees) {
            this.animationPhase = phaseDegrees;
            return this;
        }

        public SkylineObject label(final String text) {
            this.label = text;
            return this;
        }

        public SkylineObject label(final String text, final int offsetX, final int offsetY) {
            this.label = text;
            this.labelOffsetX = offsetX;
            this.labelOffsetY = offsetY;
            return this;
        }

        public SkylineObject labelColor(final long color) {
            this.labelColor = Long.valueOf(color);
            return this;
        }

        public SkylineObject labelIntensity(final int intensity) {
            this.labelIntensity = intensity;
            return this;
        }
    }

    /**
     * A 2D projection of a 3D world coordinate.
     */
    public static final class Projection {
        private final int x;
        private final int y;
        private final double depth;

        public Projection(final int x, final int y, final double depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }

    /**
     * Definition of a connector beam between two points in world space.
     */
    public static final class SkylineBeam {
        private final double startX;
        private final double startY;
        private final double startZ;
        private final double endX;
        private final double endY;
        private final double endZ;
        private final long color;
        private int intensity = 80;
        private int patternModulo = 0;
        private double patternPhase = 0.0d;
        private double animationPhase = 0.0d;
        private double pulseAmplitude = 0.0d;

        private SkylineBeam(
                final double startX,
                final double startY,
                final double startZ,
                final double endX,
                final double endY,
                final double endZ,
                final long color
        ) {
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.endX = endX;
            this.endY = endY;
            this.endZ = endZ;
            this.color = color;
        }

        private double sortKey() {
            return (this.startZ + this.endZ) * 0.5d;
        }

        public SkylineBeam intensity(final int value) {
            this.intensity = value;
            return this;
        }

        public SkylineBeam dotted(final int modulo) {
            this.patternModulo = Math.max(0, modulo);
            return this;
        }

        public SkylineBeam patternPhase(final double phaseDegrees) {
            this.patternPhase = phaseDegrees;
            return this;
        }

        public SkylineBeam animationPhase(final double phaseDegrees) {
            this.animationPhase = phaseDegrees;
            return this;
        }

        public SkylineBeam pulse(final double amplitude) {
            this.pulseAmplitude = amplitude;
            return this;
        }
    }

    /**
     * Add a box shaped object to the scene.
     *
     * @param centerX world x coordinate of the box center
     * @param centerZ world z coordinate (depth) of the box center
     * @param width   width along x axis
     * @param depth   depth along z axis
     * @param base    height of the box bottom above the grid
     * @param height  height of the box itself
     * @param color   primary color
     * @return the created skyline object for further customization
     */
    public SkylineObject addBox(
            final double centerX,
            final double centerZ,
            final double width,
            final double depth,
            final double base,
            final double height,
            final long color
    ) {
        final SkylineObject object = new SkylineObject(ShapeType.BOX, centerX, centerZ, width, depth, base, height, color);
        this.objects.add(object);
        return object;
    }

    /**
     * Add a sphere to the scene.
     *
     * @param centerX world x coordinate of sphere center
     * @param centerZ world z coordinate of sphere center
     * @param diameter diameter of the sphere
     * @param base height of the sphere bottom above the grid
     * @param color primary color
     * @return the created skyline object
     */
    public SkylineObject addSphere(
            final double centerX,
            final double centerZ,
            final double diameter,
            final double base,
            final long color
    ) {
        final SkylineObject object = new SkylineObject(ShapeType.SPHERE, centerX, centerZ, diameter, diameter, base, diameter, color);
        this.objects.add(object);
        return object;
    }

    /**
     * Add a pyramid to the scene.
     *
     * @param centerX world x coordinate of pyramid center
     * @param centerZ world z coordinate of pyramid center
     * @param width width of the pyramid base along x axis
     * @param depth depth of the pyramid base along z axis
     * @param base base height above the grid
     * @param height height of the pyramid to the apex
     * @param color primary color
     * @return the created skyline object
     */
    public SkylineObject addPyramid(
            final double centerX,
            final double centerZ,
            final double width,
            final double depth,
            final double base,
            final double height,
            final long color
    ) {
        final SkylineObject object = new SkylineObject(ShapeType.PYRAMID, centerX, centerZ, width, depth, base, height, color);
        this.objects.add(object);
        return object;
    }

    /**
     * Add a light beam or connector between two points in world coordinates.
     *
     * @param startX x coordinate of the beam start
     * @param startY y coordinate (height above grid) of the beam start
     * @param startZ z coordinate (depth) of the beam start
     * @param endX x coordinate of the beam end
     * @param endY y coordinate (height above grid) of the beam end
     * @param endZ z coordinate (depth) of the beam end
     * @param color beam colour
     * @return newly created beam for additional configuration
     */
    public SkylineBeam addBeam(
            final double startX,
            final double startY,
            final double startZ,
            final double endX,
            final double endY,
            final double endZ,
            final long color
    ) {
        final SkylineBeam beam = new SkylineBeam(startX, startY, startZ, endX, endY, endZ, color);
        this.beams.add(beam);
        return beam;
    }

    /**
     * Render the skyline for a specific animation frame. YaCy uses 8 phase
     * frames (45 degrees step) for looping animations. The plotter supports that
     * convention but any integer value is allowed.
     *
     * @param animationFrame index of the animation step (0..n)
     */
    public void renderFrame(final int animationFrame) {
        clear();
        final double animationAngle = (animationFrame % 8) * 45.0d;

        drawHorizonGrid(animationAngle);

        if (!this.objects.isEmpty()) {
            final List<SkylineObject> sorted = new ArrayList<SkylineObject>(this.objects);
            Collections.sort(sorted, new Comparator<SkylineObject>() {
                @Override
                public int compare(final SkylineObject a, final SkylineObject b) {
                    return Double.compare(b.sortKey(), a.sortKey());
                }
            });

            for (final SkylineObject object : sorted) {
                drawObject(object, animationAngle);
            }
        }

        if (!this.beams.isEmpty()) {
            final List<SkylineBeam> sortedBeams = new ArrayList<SkylineBeam>(this.beams);
            Collections.sort(sortedBeams, new Comparator<SkylineBeam>() {
                @Override
                public int compare(final SkylineBeam a, final SkylineBeam b) {
                    return Double.compare(b.sortKey(), a.sortKey());
                }
            });
            for (final SkylineBeam beam : sortedBeams) {
                drawBeam(beam, animationAngle);
            }
        }
    }

    private void drawHorizonGrid(final double animationAngle) {
        setColor(this.config.gridColor);
        final int baseIntensity = clampIntensity(this.config.gridIntensity);
        final int modulation = (int) Math.round(Math.sin(Math.toRadians(animationAngle)) * this.config.gridPulse);
        final int intensity = clampIntensity(baseIntensity + modulation);

        final double halfWidth = this.config.fieldWidth * 0.5d;
        final double far = this.config.fieldDepth;
        final double near = Math.max(this.config.nearPlane, this.config.gridNear);

        final double scaleNear = this.config.focalLength / (near + this.config.cameraDepth);
        final double scaleFar = this.config.focalLength / (far + this.config.cameraDepth);

        final double minXNear = (0 - this.centerX) / scaleNear;
        final double maxXNear = ((getWidth() - 1) - this.centerX) / scaleNear;
        final double minXFar = (0 - this.centerX) / scaleFar;
        final double maxXFar = ((getWidth() - 1) - this.centerX) / scaleFar;

        final double minX = Math.min(Math.min(-halfWidth, minXNear), minXFar);
        final double maxX = Math.max(Math.max(halfWidth, maxXNear), maxXFar);

        final double startX = Math.floor(minX / this.config.gridStepX) * this.config.gridStepX;
        final double endX = Math.ceil(maxX / this.config.gridStepX) * this.config.gridStepX;

        for (double x = startX; x <= endX + 0.0001d; x += this.config.gridStepX) {
            drawLine3D(x, 0.0d, near, x, 0.0d, far, intensity, 0, 0.0d);
        }

        for (double z = near; z <= far + 0.0001d; z += this.config.gridStepZ) {
            drawLine3D(startX, 0.0d, z, endX, 0.0d, z, intensity, 0, 0.0d);
        }
    }

    private void drawObject(final SkylineObject object, final double animationAngle) {
        final double angle = (animationAngle + object.animationPhase) % 360.0d;
        final double bobOffset = (object.bobAmplitude == 0.0d) ? 0.0d : Math.sin(Math.toRadians(angle)) * object.bobAmplitude;
        final int baseIntensity = clampIntensity(object.intensity + (int) Math.round(Math.sin(Math.toRadians(angle)) * object.pulseAmplitude));
        final int edgeIntensity = clampIntensity(baseIntensity + 10);
        final int topIntensity = clampIntensity(baseIntensity + 8);
        final int sideIntensity = clampIntensity(baseIntensity - 8);
        final long mainColor = object.color;
        final long edgeColor = (object.edgeColor != null) ? object.edgeColor.longValue() : lighten(mainColor, 1.4d);
        final long highlightColor = (object.accentColor != null) ? object.accentColor.longValue() : lighten(mainColor, 1.2d);
        final long shadowColor = darken(mainColor, 0.75d);

        switch (object.shape) {
            case BOX:
                drawBox(object, bobOffset, mainColor, highlightColor, shadowColor, edgeColor, baseIntensity, topIntensity, sideIntensity, edgeIntensity, angle);
                break;
            case SPHERE:
                drawSphere(object, bobOffset, mainColor, highlightColor, edgeColor, baseIntensity, edgeIntensity, angle);
                break;
            case PYRAMID:
                drawPyramid(object, bobOffset, mainColor, highlightColor, shadowColor, edgeColor, baseIntensity, topIntensity, sideIntensity, edgeIntensity, angle);
                break;
            default:
                break;
        }
    }

    private void drawBox(
            final SkylineObject object,
            final double bobOffset,
            final long mainColor,
            final long highlightColor,
            final long shadowColor,
            final long edgeColor,
            final int baseIntensity,
            final int topIntensity,
            final int sideIntensity,
            final int edgeIntensity,
            final double animationAngle
    ) {
        final double cx = object.centerX;
        final double cz = object.centerZ;
        final double base = object.baseHeight + bobOffset;
        final double h = object.objectHeight;
        final double halfW = object.sizeX * 0.5d;
        final double halfD = object.sizeZ * 0.5d;
        final double top = base + h;

        final Projection topNW = project(cx - halfW, top, cz - halfD);
        final Projection topNE = project(cx + halfW, top, cz - halfD);
        final Projection topSE = project(cx + halfW, top, cz + halfD);
        final Projection topSW = project(cx - halfW, top, cz + halfD);

        final Projection baseNW = project(cx - halfW, base, cz - halfD);
        final Projection baseNE = project(cx + halfW, base, cz - halfD);
        final Projection baseSE = project(cx + halfW, base, cz + halfD);
        final Projection baseSW = project(cx - halfW, base, cz + halfD);

        fillPolygon(
                new int[]{topNW.x, topNE.x, topSE.x, topSW.x},
                new int[]{topNW.y, topNE.y, topSE.y, topSW.y},
                highlightColor,
                topIntensity);
        fillPolygon(
                new int[]{baseNW.x, baseNE.x, topNE.x, topNW.x},
                new int[]{baseNW.y, baseNE.y, topNE.y, topNW.y},
                mainColor,
                baseIntensity);
        fillPolygon(
                new int[]{baseNE.x, baseSE.x, topSE.x, topNE.x},
                new int[]{baseNE.y, baseSE.y, topSE.y, topNE.y},
                shadowColor,
                sideIntensity);

        final Projection[] outline = new Projection[]{baseNW, baseNE, baseSE, baseSW, baseNW, topNW, topNE, topSE, topSW, topNW, topSW, baseSW};
        setColor(edgeColor);
        for (int i = 0; i < outline.length - 1; i++) {
            drawLineWithPattern(object.patternModulo, object.patternPhase, outline[i], outline[i + 1], edgeIntensity, animationAngle);
        }

        drawLabel(object, topNE, animationAngle);
    }

    private void drawSphere(
            final SkylineObject object,
            final double bobOffset,
            final long mainColor,
            final long highlightColor,
            final long edgeColor,
            final int baseIntensity,
            final int edgeIntensity,
            final double animationAngle
    ) {
        final double radiusWorld = object.objectHeight * 0.5d;
        final double centerY = object.baseHeight + bobOffset + radiusWorld;

        final Projection center = project(object.centerX, centerY, object.centerZ);
        final Projection radiusX = project(object.centerX + radiusWorld, centerY, object.centerZ);
        final int radius = Math.max(1, Math.abs(radiusX.x - center.x));

        setColor(mainColor);
        dot(center.x, center.y, radius, true, baseIntensity);

        final int highlightRadius = Math.max(1, radius / 2);
        setColor(highlightColor);
        dot(center.x - highlightRadius / 2, center.y - highlightRadius / 2, highlightRadius, true, baseIntensity);

        setColor(edgeColor);
        CircleTool.circle(this, center.x, center.y, radius, edgeIntensity);

        drawLabel(object, new Projection(center.x, center.y - radius - 6, center.depth), animationAngle);
    }

    private void drawPyramid(
            final SkylineObject object,
            final double bobOffset,
            final long mainColor,
            final long highlightColor,
            final long shadowColor,
            final long edgeColor,
            final int baseIntensity,
            final int highlightIntensity,
            final int shadowIntensity,
            final int edgeIntensity,
            final double animationAngle
    ) {
        final double cx = object.centerX;
        final double cz = object.centerZ;
        final double base = object.baseHeight + bobOffset;
        final double h = object.objectHeight;
        final double halfW = object.sizeX * 0.5d;
        final double halfD = object.sizeZ * 0.5d;
        final double apexY = base + h;

        final Projection baseNW = project(cx - halfW, base, cz - halfD);
        final Projection baseNE = project(cx + halfW, base, cz - halfD);
        final Projection baseSE = project(cx + halfW, base, cz + halfD);
        final Projection baseSW = project(cx - halfW, base, cz + halfD);
        final Projection apex = project(cx, apexY, cz);

        fillPolygon(
                new int[]{baseNW.x, baseNE.x, apex.x},
                new int[]{baseNW.y, baseNE.y, apex.y},
                highlightColor,
                highlightIntensity);
        fillPolygon(
                new int[]{baseNE.x, baseSE.x, apex.x},
                new int[]{baseNE.y, baseSE.y, apex.y},
                mainColor,
                baseIntensity);
        fillPolygon(
                new int[]{baseSE.x, baseSW.x, apex.x},
                new int[]{baseSE.y, baseSW.y, apex.y},
                shadowColor,
                shadowIntensity);

        setColor(edgeColor);
        final Projection[] outline = new Projection[]{baseNW, baseNE, baseSE, baseSW, baseNW, apex, baseNE, apex, baseSE, apex, baseSW};
        for (int i = 0; i < outline.length - 1; i++) {
            drawLineWithPattern(object.patternModulo, object.patternPhase, outline[i], outline[i + 1], edgeIntensity, animationAngle);
        }

        drawLabel(object, apex, animationAngle);
    }

    private void drawLabel(final SkylineObject object, final Projection reference, final double animationAngle) {
        if (object.label == null || object.label.length() == 0) return;
        final long labelColor = (object.labelColor != null) ? object.labelColor.longValue()
                : (object.accentColor != null ? object.accentColor.longValue() : object.color);
        setColor(labelColor);
        final int targetX = reference.x + object.labelOffsetX;
        final int targetY = reference.y + object.labelOffsetY;
        PrintTool.print5(this, targetX, targetY, 0, object.label, -1, clampIntensity(object.labelIntensity));
    }

    private void drawLineWithPattern(final int patternModulo, final double patternPhase, final Projection start, final Projection end, final int intensity, final double animationAngle) {
        if (patternModulo <= 1) {
            line(start.x, start.y, end.x, end.y, intensity);
            return;
        }
        final int steps = Math.max(Math.abs(end.x - start.x), Math.abs(end.y - start.y));
        if (steps == 0) {
            plot(start.x, start.y, intensity);
            return;
        }
        final int offset = ((int) Math.round(patternModulo * ((patternPhase + animationAngle) % 360.0d) / 360.0d)) % patternModulo;
        for (int i = 0; i <= steps; i++) {
            final int patternIndex = (i + offset) % patternModulo;
            if (patternIndex < (patternModulo / 2)) {
                final double t = i / (double) steps;
                final int x = (int) Math.round(start.x + (end.x - start.x) * t);
                final int y = (int) Math.round(start.y + (end.y - start.y) * t);
                plot(x, y, intensity);
            }
        }
    }

    private void drawLine3D(final double x0, final double y0, final double z0, final double x1, final double y1, final double z1, final int intensity, final int patternModulo, final double patternPhase) {
        final Projection p0 = project(x0, y0, z0);
        final Projection p1 = project(x1, y1, z1);
        if (patternModulo <= 1) {
            line(p0.x, p0.y, p1.x, p1.y, intensity);
        } else {
            drawLineWithPattern(patternModulo, patternPhase, p0, p1, intensity, 0.0d);
        }
    }

    private void drawBeam(final SkylineBeam beam, final double animationAngle) {
        final double phaseAngle = (animationAngle + beam.animationPhase) % 360.0d;
        final int beamIntensity = clampIntensity(beam.intensity + (int) Math.round(Math.sin(Math.toRadians(phaseAngle)) * beam.pulseAmplitude));
        setColor(beam.color);
        final Projection start = project(beam.startX, beam.startY, beam.startZ);
        final Projection end = project(beam.endX, beam.endY, beam.endZ);
        drawLineWithPattern(beam.patternModulo, beam.patternPhase + phaseAngle, start, end, beamIntensity, phaseAngle);
    }

    private Projection project(final double x, final double y, final double z) {
        final double denom = Math.max(this.config.nearPlane, z + this.config.cameraDepth);
        final double scale = this.config.focalLength / denom;
        final int sx = this.centerX + (int) Math.round(x * scale);
        final int sy = this.horizonY + (int) Math.round((this.config.groundLevel - y) * scale);
        return new Projection(sx, sy, denom);
    }

    private int clampIntensity(final int value) {
        return (value < 0) ? 0 : (value > 100) ? 100 : value;
    }

    /**
     * Demo entry point generating eight animation frames illustrating the
     * skyline style. Accepts an optional output directory; defaults to the
     * current working directory.
     */
    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");

        final File targetDir = (args.length > 0) ? new File(args[0]) : new File(".");
        if (!targetDir.exists()) {
            if (!targetDir.mkdirs()) {
                throw new IllegalStateException("Cannot create output directory " + targetDir.getAbsolutePath());
            }
        } else if (!targetDir.isDirectory()) {
            throw new IllegalArgumentException("Output path is not a directory: " + targetDir.getAbsolutePath());
        }

        final SkylineConfig config = new SkylineConfig();
        config.width = 640;
        config.height = 360;
        config.backgroundColor = 0x000011L;
        config.gridColor = 0x00FFFFL;
        config.gridIntensity = 36;
        config.gridPulse = 12;
        config.gridStepX = 26.0d;
        config.gridStepZ = 36.0d;
        config.fieldWidth = 420.0d;
        config.fieldDepth = 640.0d;
        config.focalLength = 560.0d;
        config.cameraDepth = 140.0d;
        config.groundLevel = 210.0d;
        config.horizonY = 110;

        final SkylinePlotter skyline = new SkylinePlotter(config);

        skyline.addBox(-160, 220, 120, 110, 10, 130, 0x0077FFL)
                .accent(0x55FFFFL)
                .edges(0xFFFFFFL)
                .intensity(85)
                .bob(14.0d)
                .pulse(14.0d)
                .dotted(6)
                .patternPhase(90.0d)
                .label("DATA CORE", -40, -18);

        skyline.addBox(150, 260, 90, 90, 0, 80, 0x3A00AAL)
                .accent(0xAA66FFL)
                .edges(0xFFD6FFL)
                .intensity(75)
                .bob(6.0d)
                .pulse(10.0d)
                .dotted(8)
                .patternPhase(0.0d)
                .label("NODE CLUSTER", -44, -14);

        skyline.addSphere(40, 320, 120, 6, 0xFF3366L)
                .accent(0xFF88AAL)
                .intensity(70)
                .bob(18.0d)
                .pulse(16.0d)
                .animationPhase(180.0d)
                .label("ORBITAL CACHE", -52, -22);

        skyline.addPyramid(-20, 420, 150, 150, 0, 160, 0xFFAA33L)
                .accent(0xFFD27FL)
                .edges(0xFFE0A0L)
                .intensity(82)
                .pulse(8.0d)
                .animationPhase(45.0d)
                .label("SIGNAL SPIRE", -48, -20);

        skyline.addBox(0, 540, 420, 60, -18, 12, 0x004455L)
                .edges(0x33AADDL)
                .intensity(52)
                .dotted(4)
                .label("RUNWAY 12", -32, 18)
                .labelIntensity(70);

        for (int frame = 0; frame < 8; frame++) {
            skyline.renderFrame(frame);
            final File outFile = new File(targetDir, String.format("skyline_demo_%02d.png", frame));
            try {
                skyline.save(outFile, "png");
            } catch (final IOException e) {
                throw new RuntimeException("Failed to write demo frame " + outFile.getAbsolutePath(), e);
            }
        }
    }
}
