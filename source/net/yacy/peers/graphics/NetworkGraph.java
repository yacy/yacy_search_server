// NetworkGraph.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 08.10.2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.peers.graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.feed.Hit;
import net.yacy.cora.federate.yacy.Distribution;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.peers.EventChannel;
import net.yacy.peers.RemoteSearch;
import net.yacy.peers.Seed;
import net.yacy.peers.SeedDB;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.SearchEvent;
import net.yacy.search.query.SearchEventCache;
import net.yacy.visualization.PrintTool;
import net.yacy.visualization.RasterPlotter;

public class NetworkGraph {

    private final static double DOUBLE_LONG_MAX_VALUE = Long.MAX_VALUE;

    private static int shortestName = 10;
    private static int longestName = 30;

    public  static final String COL_BACKGROUND     = "FFFFFF";
    public  static final String COL_DHTCIRCLE      = "004018";
    private static final String COL_HEADLINE       = "FFFFFF";
    private static final String COL_ACTIVE_DOT     = "000040";
    private static final String COL_ACTIVE_LINE    = "113322";
    private static final String COL_ACTIVE_TEXT    = "226644";
    private static final String COL_PASSIVE_DOT    = "201010";
    private static final String COL_PASSIVE_LINE   = "443333";
    private static final String COL_PASSIVE_TEXT   = "663333";
    private static final String COL_POTENTIAL_DOT  = "002000";
    private static final String COL_POTENTIAL_LINE = "224422";
    private static final String COL_POTENTIAL_TEXT = "336633";
    private static final String COL_MYPEER_DOT     = "FF0000";
    private static final String COL_MYPEER_LINE    = "FFAAAA";
    private static final String COL_MYPEER_TEXT    = "FFCCCC";
    private static final String COL_DHTOUT         = "440000";
    private static final String COL_DHTIN          = "008800";

    private static final String COL_BORDER         = "000000";
    private static final String COL_NORMAL_TEXT    = "000000";
    private static final String COL_LOAD_BG        = "F7F7F7";

    /** Private constructor to avoid instantiation of utility class. */
    private NetworkGraph() { }

    public static class CircleThreadPiece {
        private final String pieceName;
        private final Color color;
        private long execTime = 0;
        private float fraction = 0;

        public CircleThreadPiece(final String pieceName, final Color color) {
            this.pieceName = pieceName;
            this.color = color;
        }

        public int getAngle() { return Math.round(360.0f * this.fraction); }
        public int getFractionPercent() { return Math.round(100.0f * this.fraction); }
        public Color getColor() { return this.color; }
        public long getExecTime() { return this.execTime; }
        public String getPieceName() { return this.pieceName; }

        public void addExecTime(final long execTime) { this.execTime += execTime; }
        public void reset() {
            this.execTime = 0;
            this.fraction = 0;
        }
        public void setExecTime(final long execTime) { this.execTime = execTime; }
        public void setFraction(final long totalBusyTime) {
            this.fraction = (float)this.execTime / (float)totalBusyTime;
        }
    }

    private static final int     LEGEND_BOX_SIZE = 10;

    private static BufferedImage peerloadPicture = null;
    private static long          peerloadPictureDate = 0;

    public static RasterPlotter getSearchEventPicture(final SeedDB seedDB, final String eventID, final int coronaangle, final int cyc) {
        final SearchEvent event = SearchEventCache.getEvent(eventID);
        if (event == null) return null;
        final List<RemoteSearch> primarySearches = event.getPrimarySearchThreads();
        //final Thread[] secondarySearches = event.getSecondarySearchThreads();
        if (primarySearches == null) return null; // this was a local search and there are no threads

        // get a copy of a recent network picture
        final RasterPlotter eventPicture = getNetworkPicture(seedDB, 640, 480, 300, 300, 9000, coronaangle, -1, Switchboard.getSwitchboard().getConfig(SwitchboardConstants.NETWORK_NAME, "unspecified"), Switchboard.getSwitchboard().getConfig("network.unit.description", "unspecified"), COL_BACKGROUND, cyc);
        //if (eventPicture instanceof ymageMatrix) eventPicture = (ymageMatrix) eventPicture; //new ymageMatrix((ymageMatrix) eventPicture);
        // TODO: fix cloning of ymageMatrix pictures

        // get dimensions
        final int cr = Math.min(eventPicture.getWidth(), eventPicture.getHeight()) / 5 - 20;
        final int cx = eventPicture.getWidth() / 2;
        final int cy = eventPicture.getHeight() / 2 + 20;

        double angle;

        // draw in the primary search peers
        for (final RemoteSearch primarySearche : primarySearches) {
            if (primarySearche == null) continue;
            eventPicture.setColor((primarySearche.isAlive()) ? RasterPlotter.RED : RasterPlotter.GREEN);
            angle = cyc + (360.0d * ((Distribution.horizontalDHTPosition(UTF8.getBytes(primarySearche.target().hash))) / DOUBLE_LONG_MAX_VALUE));
            eventPicture.arcLine(cx, cy, cr - 20, cr, angle, true, null, null, -1, -1, -1, false);
        }

        // draw in the secondary search peers
        /*
        if (secondarySearches != null) {
            for (final Thread secondarySearche : secondarySearches) {
                if (secondarySearche == null) continue;
                eventPicture.setColor((secondarySearche.isAlive()) ? RasterPlotter.RED : RasterPlotter.GREEN);
                angle = cyc + (360.0d * ((FlatWordPartitionScheme.std.dhtPosition(UTF8.getBytes(secondarySearche.target().hash), null)) / DOUBLE_LONG_MAX_VALUE));
                eventPicture.arcLine(cx, cy, cr - 10, cr, angle - 1.0, true, null, null, -1, -1, -1, false);
                eventPicture.arcLine(cx, cy, cr - 10, cr, angle + 1.0, true, null, null, -1, -1, -1, false);
            }
        }
        */

        // draw in the search target
        final Iterator<byte[]> i = event.query.getQueryGoal().getIncludeHashes().iterator();
        eventPicture.setColor(RasterPlotter.GREY);
        while (i.hasNext()) {
            byte[] wordHash = i.next();
            for (int verticalPosition = 0; verticalPosition < seedDB.scheme.verticalPartitions(); verticalPosition++) {
                long position = seedDB.scheme.verticalDHTPosition(wordHash, verticalPosition);
                angle = cyc + (360.0d * (position / DOUBLE_LONG_MAX_VALUE));
                eventPicture.arcLine(cx, cy, cr - 20, cr, angle, true, null, null, -1, -1, -1, false);
            }
        }

        return eventPicture;
    }

    public static RasterPlotter getNetworkPicture(final SeedDB seedDB, final int width, final int height, final int passiveLimit, final int potentialLimit, final int maxCount, final int coronaangle, final long communicationTimeout, final String networkName, final String networkTitle, final String bgcolor, final int cyc) {
        return drawNetworkPicture(seedDB, width, height, passiveLimit, potentialLimit, maxCount, coronaangle, communicationTimeout, networkName, networkTitle, bgcolor, cyc);
    }

    private static RasterPlotter drawNetworkPicture(
            final SeedDB seedDB, final int width, final int height,
            final int passiveLimit, final int potentialLimit,
            final int maxCount, final int coronaangle,
            final long communicationTimeout,
            final String networkName, final String networkTitle, final String color_back,
            final int cyc) {

        final RasterPlotter.DrawMode drawMode = (RasterPlotter.darkColor(color_back)) ? RasterPlotter.DrawMode.MODE_ADD : RasterPlotter.DrawMode.MODE_SUB;
        final RasterPlotter networkPicture = new RasterPlotter(width, height, drawMode, color_back);
        if (seedDB == null) return networkPicture; // no other peers known

        final int maxradius = Math.min(width / 2, height * 3 / 5);
        final int innerradius = maxradius * 4 / 10;
        final int outerradius = maxradius - 20;

        // draw network circle
        networkPicture.setColor(Long.parseLong(COL_DHTCIRCLE, 16));
        networkPicture.arc(width / 2, height / 2, innerradius - 20, innerradius + 20, 100);

        //System.out.println("Seed Maximum distance is       " + yacySeed.maxDHTDistance);
        //System.out.println("Seed Minimum distance is       " + yacySeed.minDHTNumber);

        Seed seed;
        long lastseen;

        // draw connected senior and principals
        int count = 0;
        int totalCount = 0;
        Iterator<Seed> e = seedDB.seedsConnected(true, false, null, (float) 0.0);
        while (e.hasNext() && count < maxCount) {
            seed = e.next();
            if (seed == null) {
                ConcurrentLog.warn("NetworkGraph", "connected seed == null");
                continue;
            }
            if (seed.hash.startsWith("AD")) {//temporary patch
                continue;
            }
            //Log.logInfo("NetworkGraph", "drawing peer " + seed.getName());
            new drawNetworkPicturePeerJob(networkPicture, width / 2, height / 2, innerradius, outerradius, seed, COL_ACTIVE_DOT, COL_ACTIVE_LINE, COL_ACTIVE_TEXT, coronaangle, cyc).draw();
            count++;
        }
        totalCount += count;

        // draw disconnected senior and principals that have been seen lately
        count = 0;
        e = seedDB.seedsSortedDisconnected(false, Seed.LASTSEEN);
        while (e.hasNext() && count < maxCount) {
            seed = e.next();
            if (seed == null) {
                ConcurrentLog.warn("NetworkGraph", "disconnected seed == null");
                continue;
            }
            lastseen = Math.abs((System.currentTimeMillis() - seed.getLastSeenUTC()) / 1000 / 60);
            if (lastseen > passiveLimit) {
                break; // we have enough, this list is sorted so we don't miss anything
            }
            new drawNetworkPicturePeerJob(networkPicture, width / 2, height / 2, innerradius, outerradius, seed, COL_PASSIVE_DOT, COL_PASSIVE_LINE, COL_PASSIVE_TEXT, coronaangle, cyc).draw();
            count++;
        }
        totalCount += count;

        // draw juniors that have been seen lately
        count = 0;
        e = seedDB.seedsSortedPotential(false, Seed.LASTSEEN);
        while (e.hasNext() && count < maxCount) {
            seed = e.next();
            if (seed == null) {
                ConcurrentLog.warn("NetworkGraph", "potential seed == null");
                continue;
            }
            lastseen = Math.abs((System.currentTimeMillis() - seed.getLastSeenUTC()) / 1000 / 60);
            if (lastseen > potentialLimit) {
                break; // we have enough, this list is sorted so we don't miss anything
            }
            new drawNetworkPicturePeerJob(networkPicture, width / 2, height / 2, innerradius, outerradius, seed, COL_POTENTIAL_DOT, COL_POTENTIAL_LINE, COL_POTENTIAL_TEXT, coronaangle, cyc).draw();
            count++;
        }
        totalCount += count;

        // draw my own peer
        new drawNetworkPicturePeerJob(networkPicture, width / 2, height / 2, innerradius, outerradius, seedDB.mySeed(), COL_MYPEER_DOT, COL_MYPEER_LINE, COL_MYPEER_TEXT, coronaangle, cyc).draw();

        // signal termination
        //for (@SuppressWarnings("unused") final Thread t: drawThreads) try { drawQueue.put(poison); } catch (final InterruptedException ee) {}

        // draw DHT activity
        if (communicationTimeout >= 0) {
            final Date horizon = new Date(System.currentTimeMillis() - communicationTimeout);
            for (final Hit event: EventChannel.channels(EventChannel.DHTRECEIVE)) {
                if (event == null) break;
                if (event.getPubDate() == null) continue;
                if (event.getPubDate().after(horizon)) {
                    //System.out.println("*** NETWORK-DHTRECEIVE: " + event.getLink());
                    drawNetworkPictureDHT(networkPicture, width / 2, height / 2, innerradius, seedDB.mySeed(), seedDB.get(event.getLink()), COL_DHTIN, coronaangle, false, cyc);
                }
            }
            for (final Hit event: EventChannel.channels(EventChannel.DHTSEND)) {
                if (event == null || event.getPubDate() == null) continue;
                if (event.getPubDate().after(horizon)) {
                    //System.out.println("*** NETWORK-DHTSEND: " + event.getLink());
                    drawNetworkPictureDHT(networkPicture, width / 2, height / 2, innerradius, seedDB.mySeed(), seedDB.get(event.getLink()), COL_DHTOUT, coronaangle, true, cyc);
                }
            }
        }

        // draw description
        networkPicture.setColor(Long.parseLong(COL_HEADLINE, 16));
        PrintTool.print(networkPicture, 2, 6, 0, "YACY NETWORK '" + networkName.toUpperCase() + "'", -1);
        PrintTool.print(networkPicture, 2, 14, 0, networkTitle.toUpperCase(), -1);
        PrintTool.print(networkPicture, width - 2, 6, 0, "SNAPSHOT FROM " + new Date().toString().toUpperCase(), 1);
        PrintTool.print(networkPicture, width - 2, 14, 0, "DRAWING OF " + totalCount + " SELECTED PEERS", 1);

        // wait for draw termination
        //for (final Thread t: drawThreads) try { t.join(); } catch (final InterruptedException ee) {}

        return networkPicture;
    }

    private static void drawNetworkPictureDHT(final RasterPlotter img, final int centerX, final int centerY, final int innerradius, final Seed mySeed, final Seed otherSeed, final String colorLine, final int coronaangle, final boolean out, final int cyc) {
        // find positions (== angle) of the two peers
        final int angleMy = cyc + (int) (360.0d * Distribution.horizontalDHTPosition(ASCII.getBytes(mySeed.hash)) / DOUBLE_LONG_MAX_VALUE);
        final int angleOther = cyc + (int) (360.0d * Distribution.horizontalDHTPosition(ASCII.getBytes(otherSeed.hash)) / DOUBLE_LONG_MAX_VALUE);
        Long colorLine_l = Long.parseLong(colorLine, 16);
        // paint the line from my peer to the inner border of the network circle
        img.arcLine(centerX, centerY, innerradius, innerradius - 20, angleMy, !out, colorLine_l, null, 12, (coronaangle < 0) ? -1 : coronaangle / 30, 2, true);
        // paint the line from the other peer to the inner border of the network circle
        img.arcLine(centerX, centerY, innerradius, innerradius - 20, angleOther, out, colorLine_l, null, 12, (coronaangle < 0) ? -1 : coronaangle / 30, 2, true);
        // paint a line between the two inner border points of my peer and the other peer
        img.arcConnect(centerX, centerY, innerradius - 20, angleMy, angleOther, out, colorLine_l, 100, null, 100, 12, (coronaangle < 0) ? -1 : coronaangle / 30, 2, true, otherSeed.getName(), colorLine_l);
    }

    private static class drawNetworkPicturePeerJob {

        private final RasterPlotter img;
        private final int centerX, centerY, innerradius, outerradius, coronaangle;
        private final Seed seed;
        private final String colorDot, colorLine, colorText;
        private final double cyc;
        //public drawNetworkPicturePeerJob() {} // used to produce a poison pill
        public drawNetworkPicturePeerJob(
                        final RasterPlotter img, final int centerX, final int centerY,
                        final int innerradius, final int outerradius,
                        final Seed seed,
                        final String colorDot, final String colorLine, final String colorText,
                        final int coronaangle,
                        final double cyc) {
            this.img = img;
            this.centerX = centerX;
            this.centerY = centerY;
            this.innerradius = innerradius;
            this.outerradius = outerradius;
            this.coronaangle = coronaangle;
            this.seed = seed;
            this.colorDot = colorDot;
            this.colorLine = colorLine;
            this.colorText = colorText;
            this.cyc = cyc;
        }
        public void draw() {
            final String name = this.seed.getName().toUpperCase() /*+ ":" + seed.hash + ":" + (((double) ((int) (100 * (((double) yacySeed.dhtPosition(seed.hash)) / ((double) yacySeed.maxDHTDistance))))) / 100.0)*/;
            if (name.length() < shortestName) shortestName = name.length();
            if (name.length() > longestName) longestName = name.length();
            final double angle = this.cyc + (360.0d * Distribution.horizontalDHTPosition(ASCII.getBytes(this.seed.hash)) / DOUBLE_LONG_MAX_VALUE);
            //System.out.println("Seed " + seed.hash + " has distance " + seed.dhtDistance() + ", angle = " + angle);
            int linelength = 20 + this.outerradius * (20 * (name.length() - shortestName) / (longestName - shortestName) + Math.abs(this.seed.hash.hashCode() % 20)) / 80;
            if (linelength > this.outerradius) linelength = this.outerradius;
            int dotsize = 2 + (int) (this.seed.getLinkCount() / 2000000L);
            if (this.colorDot.equals(COL_MYPEER_DOT)) dotsize = dotsize + 4;
            if (dotsize > 18) dotsize = 18;
            // draw dot
            this.img.setColor(Long.parseLong(this.colorDot, 16));
            this.img.arcDot(this.centerX, this.centerY, this.innerradius, angle, dotsize);
            // draw line to text
            this.img.arcLine(this.centerX, this.centerY, this.innerradius + 18, this.innerradius + linelength, angle, true, Long.parseLong(this.colorLine, 16), Long.parseLong("444444", 16), 12, this.coronaangle / 30, 0, true);
            // draw text
            this.img.setColor(Long.parseLong(this.colorText, 16));
            PrintTool.arcPrint(this.img, this.centerX, this.centerY, this.innerradius + linelength, angle, name);

            // draw corona around dot for crawling activity
            final int ppmx = Math.min(this.seed.getPPM() / 20, 10);
            if (this.coronaangle >= 0 && ppmx > 0) {
                drawCorona(this.img, this.centerX, this.centerY, this.innerradius, this.innerradius * 2 / 5, angle, dotsize, ppmx, this.coronaangle, true, false, 2, 2, 2); // color = 0..63
            }

            // draw corona around dot for query activity
            int qphx = Math.min((int) (this.seed.getQPM() * 15.0f), 8);
            if (this.coronaangle >= 0 && qphx > 0) {
                drawCorona(this.img, this.centerX, this.centerY, this.innerradius, this.innerradius / 2, angle, dotsize, qphx, this.coronaangle, false, true, 10, 60, 10); // color = 0..63
            }
        }
    }

    private static void drawCorona(final RasterPlotter img, final int centerX, final int centerY, final int innerradius, final int waveradius, final double angle, final int dotsize, int strength, final int coronaangle, final boolean inside, final boolean split, final int r, final int g, final int b) {
        final double ca = Math.PI * 2.0d * coronaangle / 360.0d;
        if (strength > 20) strength = 20;
        // draw a wave around crawling peers
        double wave;
        final int segments = 72;
        for (int radius = 0; radius < waveradius; radius++) {
            wave = ((double) (waveradius - radius) * strength) * (1.0 + Math.sin(Math.PI * 16 * radius / waveradius + ((inside) ? ca : -ca))) / 2.0 / waveradius;
            img.setColor(((((long) (r * wave)) & 0xff) << 16) | (((long) ((g * wave)) & 0xff) << 8) | ((((long) (b * wave))) & 0xff));
            if (split) {
                for (int i = 0; i < segments; i++) {
                    final int a = (coronaangle + 360 * i) / segments;
                    img.arcArc(centerX, centerY, innerradius, angle, dotsize + radius, dotsize + radius, a, a + 180/segments);
                }
            } else {
                img.arcArc(centerX, centerY, innerradius, angle, dotsize + radius, dotsize + radius, 100);
            }
        }
    }

    public static BufferedImage getPeerLoadPicture(final long maxAge, final int width, final int height, final CircleThreadPiece[] pieces, final CircleThreadPiece fillRest) {
        if ((peerloadPicture == null) || ((System.currentTimeMillis() - peerloadPictureDate) > maxAge)) {
            drawPeerLoadPicture(width, height, pieces, fillRest);
        }
        return peerloadPicture;
    }

    private static void drawPeerLoadPicture(final int width, final int height, final CircleThreadPiece[] pieces, final CircleThreadPiece fillRest) {
    	//prepare image
    	peerloadPicture = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = peerloadPicture.createGraphics();
        g.setBackground(Color.decode("0x"+COL_LOAD_BG));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.clearRect(0,0,width,height);

        final int circ_w = Math.min(width,height)-20; //width of the circle (r*2)
        final int circ_x = width-circ_w-10;           //x-coordinate of circle-left
        final int circ_y = 10;                        //y-coordinate of circle-top
        int curr_angle = 0;                       //remember current angle

        int i;
        for (i=0; i<pieces.length; i++) {
            // draw the piece
            g.setColor(pieces[i].getColor());
            g.fillArc(circ_x, circ_y, circ_w, circ_w, curr_angle, pieces[i].getAngle());
            curr_angle += pieces[i].getAngle();

            // draw it's legend line
            drawLegendLine(g, 5, height - 5 - 15 * i, pieces[i].getPieceName()+" ("+pieces[i].getFractionPercent()+" %)", pieces[i].getColor());
        }

        // fill the rest
        g.setColor(fillRest.getColor());
        //FIXME: better method to avoid gaps on rounding-differences?
        g.fillArc(circ_x, circ_y, circ_w, circ_w, curr_angle, 360 - curr_angle);
        drawLegendLine(g, 5, height - 5 - 15 * i, fillRest.getPieceName()+" ("+fillRest.getFractionPercent()+" %)", fillRest.getColor());

        //draw border around the circle
        g.setColor(Color.decode("0x"+COL_BORDER));
        g.drawArc(circ_x, circ_y, circ_w, circ_w, 0, 360);

        peerloadPictureDate = System.currentTimeMillis();
    }

    private static void drawLegendLine(final Graphics2D g, final int x, final int y, final String caption, final Color item_color) {
    	g.setColor(item_color);
    	g.fillRect(x, y-LEGEND_BOX_SIZE, LEGEND_BOX_SIZE, LEGEND_BOX_SIZE);
    	g.setColor(Color.decode("0x"+COL_BORDER));
    	g.drawRect(x, y-LEGEND_BOX_SIZE, LEGEND_BOX_SIZE, LEGEND_BOX_SIZE);

    	g.setColor(Color.decode("0x"+COL_NORMAL_TEXT));
    	g.drawChars(caption.toCharArray(), 0, caption.length(), x+LEGEND_BOX_SIZE+5,y);
    }

}