/**
 *  NetworkSkylineGraph
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

package net.yacy.peers.graphics;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.feed.Hit;
import net.yacy.cora.document.feed.RSSMessage;
import net.yacy.cora.federate.yacy.Distribution;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.EventChannel;
import net.yacy.peers.Seed;
import net.yacy.peers.SeedDB;
import net.yacy.visualization.PrintTool;
import net.yacy.visualization.RasterPlotter;
import net.yacy.visualization.SkylinePlotter;
import net.yacy.visualization.SkylinePlotter.SkylineBeam;
import net.yacy.visualization.SkylinePlotter.SkylineConfig;
import net.yacy.visualization.SkylinePlotter.SkylineObject;

/**
 * Alternative skyline themed visualization of the YaCy network state.
 */
public final class NetworkSkylineGraph {

    private static final double DOUBLE_LONG_MAX_VALUE = Long.MAX_VALUE;

    private static final long SKYLINE_BACKGROUND = 0x000011L;
    private static final long GRID_COLOR = 0x00C0FFL;

    private static final long COL_ACTIVE_BUILDING = 0x0066E6L;
    private static final long COL_PASSIVE_BUILDING = 0x2B4A80L;
    private static final long COL_POTENTIAL_BUILDING = 0x245C3DL;
    private static final long COL_SELF = 0xFF3355L;
    private static final long COL_RUNWAY = 0x103030L;

    private static final long COL_ACTIVE_TEXT = 0x77CCFFL;
    private static final long COL_PASSIVE_TEXT = 0x9EB8DCL;
    private static final long COL_POTENTIAL_TEXT = 0x7FD7ABL;
    private static final long COL_SELF_TEXT = 0xFFD6E0L;

    private static final long COL_BEAM_IN = 0x4CFF88L;
    private static final long COL_BEAM_OUT = 0xFF7766L;

    private static final int BASE_PASSIVE_LIMIT = 60;
    private static final int BASE_POTENTIAL_LIMIT = 240;

    private NetworkSkylineGraph() { }

    private static final class Placement {
        final double x;
        final double z;
        final double base;
        final double height;

        Placement(final double x, final double z, final double base, final double height) {
            this.x = x;
            this.z = z;
            this.base = base;
            this.height = height;
        }

        double top() {
            return this.base + this.height;
        }
    }

    private enum PeerTier {
        ACTIVE(COL_ACTIVE_BUILDING, COL_ACTIVE_TEXT, 0.75d, 0.20d, 6),
        PASSIVE(COL_PASSIVE_BUILDING, COL_PASSIVE_TEXT, 0.40d, 0.12d, 0),
        POTENTIAL(COL_POTENTIAL_BUILDING, COL_POTENTIAL_TEXT, 0.25d, 0.08d, 8),
        SELF(COL_SELF, COL_SELF_TEXT, 0.90d, 0.30d, 0);

        final long color;
        final long textColor;
        final double bobFactor;
        final double pulseFactor;
        final int patternModulo;

        PeerTier(final long color, final long textColor, final double bobFactor, final double pulseFactor, final int patternModulo) {
            this.color = color;
            this.textColor = textColor;
            this.bobFactor = bobFactor;
            this.pulseFactor = pulseFactor;
            this.patternModulo = patternModulo;
        }
    }

    public static SkylinePlotter getNetworkSkylinePicture(final SeedDB seedDB,
                                                          final int width,
                                                          final int height,
                                                          final int passiveLimit,
                                                          final int potentialLimit,
                                                          final int maxCount,
                                                          final int coronaAngle,
                                                          final long communicationTimeout,
                                                          final String networkName,
                                                          final String networkTitle,
                                                          final long backgroundColor,
                                                          final int cyc) {
        final SkylineConfig config = new SkylineConfig();
        config.width = width;
        config.height = height;
        config.backgroundColor = backgroundColor != 0 ? backgroundColor : SKYLINE_BACKGROUND;
        config.gridColor = GRID_COLOR;
        config.gridIntensity = 40;
        config.gridPulse = 10;
        config.gridStepX = 28.0d;
        config.gridStepZ = 36.0d;
        config.fieldWidth = 460.0d;
        config.fieldDepth = 680.0d;
        config.focalLength = 580.0d;
        config.cameraDepth = 150.0d;
        config.groundLevel = 220.0d;
        config.horizonY = height / 3;

        final SkylinePlotter skyline = new SkylinePlotter(config);
        skyline.clearScene();

        final Map<String, Placement> placements = new HashMap<String, Placement>();
        final Set<String> seen = new HashSet<String>();
        if (seedDB != null && seedDB.mySeed() != null && seedDB.mySeed().hash != null) {
            seen.add(seedDB.mySeed().hash);
        }

        // optional runway to anchor the scene
        skyline.addBox(0.0d, config.fieldDepth - 60.0d,
                config.fieldWidth * 1.4d,
                90.0d,
                -20.0d,
                16.0d,
                COL_RUNWAY)
                .edges(RasterPlotter.lighten(COL_RUNWAY, 1.4d))
                .intensity(55)
                .dotted(4)
                .label("DATAPLANE", -30, 22)
                .labelIntensity(60)
                .labelColor(RasterPlotter.lighten(COL_RUNWAY, 2.4d));

        int totalCount = 0;
        if (seedDB != null) {
            totalCount += plotPeers(skyline, placements, seen,
                    seedDB.seedsConnected(true, false, null, 0.0f),
                    maxCount, PeerTier.ACTIVE, 150.0d, 90.0d, passiveLimit, false);

            totalCount += plotPeers(skyline, placements, seen,
                    seedDB.seedsSortedDisconnected(false, Seed.LASTSEEN),
                    maxCount, PeerTier.PASSIVE, 220.0d, 110.0d, passiveLimit > 0 ? passiveLimit : BASE_PASSIVE_LIMIT, true);

            totalCount += plotPeers(skyline, placements, seen,
                    seedDB.seedsSortedPotential(false, Seed.LASTSEEN),
                    maxCount, PeerTier.POTENTIAL, 310.0d, 130.0d, potentialLimit > 0 ? potentialLimit : BASE_POTENTIAL_LIMIT, true);

            final Seed self = seedDB.mySeed();
            if (self != null) {
                final Placement placement = plotSelfPeer(skyline, placements, seen, self);
                if (placement != null) {
                    totalCount++;
                }
            }

            addCommunicationBeams(skyline, placements, seedDB, communicationTimeout);
        }

        final int frameIndex = (((cyc % 360) + 360) % 360) / 45;
        skyline.renderFrame(frameIndex);

        final long headlineColor = RasterPlotter.lighten(config.gridColor, 1.2d);
        skyline.setColor(headlineColor);
        final String name = networkName == null ? "UNSPECIFIED" : networkName.toUpperCase();
        final String title = networkTitle == null ? "" : networkTitle.toUpperCase();
        PrintTool.print(skyline, 2, 6, 0, "YACY SKYLINE '" + name + "'", -1, 100);
        PrintTool.print(skyline, 2, 14, 0, title, -1, 80);
        PrintTool.print(skyline, width - 2, 6, 0,
                "SNAPSHOT " + new Date().toString().toUpperCase(), 1, 80);
        PrintTool.print(skyline, width - 2, 14, 0,
                "RENDERED " + totalCount + " PEERS", 1, 80);

        return skyline;
    }

    private static int plotPeers(final SkylinePlotter skyline,
                                 final Map<String, Placement> placements,
                                 final Set<String> seen,
                                 final Iterator<Seed> iterator,
                                 final int maxCount,
                                 final PeerTier tier,
                                 final double baseZ,
                                 final double baseWidth,
                                 final int timeLimitMinutes,
                                 final boolean limitLastSeen) {
        if (iterator == null) return 0;
        int count = 0;
        while (iterator.hasNext() && count < maxCount) {
            final Seed seed = iterator.next();
            if (seed == null) {
                ConcurrentLog.warn("NetworkSkylineGraph", tier + " seed == null");
                continue;
            }
            final String hash = seed.hash;
            if (hash == null || hash.length() == 0) continue;
            if (hash.startsWith("AD")) continue;
            if (!seen.add(hash)) continue;

            if (limitLastSeen) {
                final long minutes = Math.abs((System.currentTimeMillis() - seed.getLastSeenUTC()) / 60000L);
                if (minutes > timeLimitMinutes) continue;
            }

            final Placement placement = createPlacement(skyline, seed, tier, baseZ, baseWidth);
            placements.put(hash, placement);
            count++;
        }
        return count;
    }

    private static Placement createPlacement(final SkylinePlotter skyline,
                                              final Seed seed,
                                              final PeerTier tier,
                                              final double zBase,
                                              final double footprint) {
        final SkylineConfig config = skyline.getConfig();
        final long hashValue = Distribution.horizontalDHTPosition(ASCII.getBytes(seed.hash));
        final double normalized = hashValue / DOUBLE_LONG_MAX_VALUE;
        final double jitter = ((seed.hash.hashCode() & 0x1FF) - 256) * 0.12d;
        final double x = (normalized - 0.5d) * config.fieldWidth * 0.9d + jitter;

        final double depthJitter = ((seed.hash.hashCode() >> 9) & 0x3F) - 32;
        final double z = zBase + depthJitter;

        final long links = Math.max(1L, seed.getLinkCount());
        final double ppm = Math.max(0, seed.getPPM());
        final double qpm = Math.max(0.0d, seed.getQPM());
        final double height = computeHeight(links, ppm, qpm, tier);
        final double baseHeight = Math.max(-4.0d, Math.min(30.0d, Math.log10(links + 1.0d) * 4.0d));

        final long color = tier.color;
        final long accent = RasterPlotter.lighten(color, 1.35d);
        final long edges = RasterPlotter.lighten(color, 1.6d);
        final SkylineObject object = skyline.addBox(x, z, footprint, footprint * 0.7d, baseHeight, height, color);
        final String name = safePeerName(seed);
        final int labelOffsetX = -Math.max(10, name.length() * 3);
        final int labelOffsetY = (int) (-height / 4.0d) - 10;
        object.accent(accent)
                .edges(edges)
                .intensity(80)
                .bob(Math.min(18.0d, ppm * tier.bobFactor / 18.0d))
                .pulse(Math.min(16.0d, qpm * tier.pulseFactor))
                .dotted(tier.patternModulo)
                .patternPhase(Math.abs(seed.hash.hashCode() % 360))
                .animationPhase((seed.hash.hashCode() & 0x1FF) % 360)
                .label(name, labelOffsetX, labelOffsetY)
                .labelColor(tier.textColor)
                .labelIntensity(90);
        return new Placement(x, z, baseHeight, height);
    }

    private static Placement plotSelfPeer(final SkylinePlotter skyline,
                                          final Map<String, Placement> placements,
                                          final Set<String> seen,
                                          final Seed self) {
        final String hash = self.hash;
        if (hash == null || hash.length() == 0) return null;
        seen.add(hash);

        final SkylineConfig config = skyline.getConfig();
        final long hashValue = Distribution.horizontalDHTPosition(ASCII.getBytes(hash));
        final double normalized = hashValue / DOUBLE_LONG_MAX_VALUE;
        final double x = (normalized - 0.5d) * config.fieldWidth * 0.6d;
        final double z = 120.0d;
        final double diameter = 140.0d;
        final double baseHeight = 8.0d;
        final SkylineObject sphere = skyline.addSphere(x, z, diameter, baseHeight, COL_SELF);
        final String name = safePeerName(self);
        sphere.accent(RasterPlotter.lighten(COL_SELF, 1.4d))
                .intensity(90)
                .bob(20.0d)
                .pulse(14.0d)
                .animationPhase(180.0d)
                .label(name, -Math.max(36, name.length() * 3), -30)
                .labelColor(COL_SELF_TEXT);
        final Placement placement = new Placement(x, z, baseHeight, diameter);
        placements.put(hash, placement);
        return placement;
    }

    private static void addCommunicationBeams(final SkylinePlotter skyline,
                                              final Map<String, Placement> placements,
                                              final SeedDB seedDB,
                                              final long communicationTimeout) {
        if (communicationTimeout < 0 || seedDB == null) return;
        final Seed self = seedDB.mySeed();
        if (self == null) return;
        final Placement selfPlacement = placements.get(self.hash);
        if (selfPlacement == null) return;

        final Date horizon = new Date(System.currentTimeMillis() - communicationTimeout);
        addChannelBeams(skyline, placements, seedDB, selfPlacement, horizon, EventChannel.DHTRECEIVE, COL_BEAM_IN, false);
        addChannelBeams(skyline, placements, seedDB, selfPlacement, horizon, EventChannel.DHTSEND, COL_BEAM_OUT, true);
    }

    private static void addChannelBeams(final SkylinePlotter skyline,
                                        final Map<String, Placement> placements,
                                        final SeedDB seedDB,
                                        final Placement selfPlacement,
                                        final Date horizon,
                                        final EventChannel channel,
                                        final long color,
                                        final boolean outgoing) {
        final Iterable<RSSMessage> events = EventChannel.channels(channel);
        if (events == null) return;
        for (final Hit event : events) {
            if (event == null) continue;
            if (event.getPubDate() == null || event.getPubDate().before(horizon)) continue;
            final String link = event.getLink();
            if (link == null) continue;
            final Seed other = seedDB.get(link);
            if (other == null) continue;
            final Placement otherPlacement = placements.get(other.hash);
            if (otherPlacement == null) continue;
            final Placement source = outgoing ? selfPlacement : otherPlacement;
            final Placement target = outgoing ? otherPlacement : selfPlacement;
            final SkylineBeam beam = skyline.addBeam(source.x, source.top() + 6.0d, source.z,
                    target.x, target.top() + 6.0d, target.z, color);
            beam.intensity(88)
                    .pulse(12.0d)
                    .dotted(6)
                    .patternPhase(Math.abs(link.hashCode()) % 360)
                    .animationPhase((link.hashCode() & 0x1FF) % 360);
        }
    }

    private static double computeHeight(final long links, final double ppm, final double qpm, final PeerTier tier) {
        final double linkFactor = Math.min(180.0d, Math.log10(links + 1.0d) * 22.0d);
        final double crawlFactor = Math.min(60.0d, ppm * tier.bobFactor / 8.0d);
        final double queryFactor = Math.min(48.0d, qpm * tier.pulseFactor * 1.5d);
        return 40.0d + linkFactor + crawlFactor + queryFactor;
    }

    private static String safePeerName(final Seed seed) {
        if (seed == null) return "UNKNOWN";
        final String name = seed.getName();
        if (name == null || name.length() == 0) return "UNKNOWN";
        return name.toUpperCase();
    }
}
