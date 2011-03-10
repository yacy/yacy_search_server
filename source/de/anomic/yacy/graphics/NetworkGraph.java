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

package de.anomic.yacy.graphics;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Date;
import java.util.Iterator;

import net.yacy.cora.document.Hit;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.logging.Log;
import net.yacy.visualization.PrintTool;
import net.yacy.visualization.RasterPlotter;

import de.anomic.search.QueryParams;
import de.anomic.search.SearchEvent;
import de.anomic.search.SearchEventCache;
import de.anomic.search.Switchboard;
import de.anomic.search.SwitchboardConstants;
import de.anomic.yacy.yacyChannel;
import de.anomic.yacy.yacySearch;
import de.anomic.yacy.yacySeed;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.dht.FlatWordPartitionScheme;

public class NetworkGraph {

    private static int shortestName = 10;
    private static int longestName = 12;

    public  static final String COL_BACKGROUND     = "FFFFFF";
    private static final String COL_DHTCIRCLE      = "006020";
    private static final String COL_HEADLINE       = "FFFFFF";
    private static final String COL_ACTIVE_DOT     = "000044";
    private static final String COL_ACTIVE_LINE    = "335544";
    private static final String COL_ACTIVE_TEXT    = "66AA88";
    private static final String COL_PASSIVE_DOT    = "221111";
    private static final String COL_PASSIVE_LINE   = "443333";
    private static final String COL_PASSIVE_TEXT   = "663333";
    private static final String COL_POTENTIAL_DOT  = "002200";
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
    
    public static class CircleThreadPiece {
        private final String pieceName;
        private final Color color;
        private long execTime = 0;
        private float fraction = 0;
        
        public CircleThreadPiece(final String pieceName, final Color color) {
            this.pieceName = pieceName;
            this.color = color;
        }
        
        public int getAngle() { return Math.round(360f*this.fraction); }
        public int getFractionPercent() { return Math.round(100f*this.fraction); }
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

    private static RasterPlotter bannerPicture = null;
    private static BufferedImage logo = null;
    private static long          bannerPictureDate = 0;

    public static RasterPlotter getSearchEventPicture(final yacySeedDB seedDB, final String eventID, final int coronaangle) {
        final SearchEvent event = SearchEventCache.getEvent(eventID);
        if (event == null) return null;
        final yacySearch[] primarySearches = event.getPrimarySearchThreads();
        final yacySearch[] secondarySearches = event.getSecondarySearchThreads();
        if (primarySearches == null) return null; // this was a local search and there are no threads

        // get a copy of a recent network picture
        final RasterPlotter eventPicture = getNetworkPicture(seedDB, 120000, 640, 480, 300, 300, 1000, coronaangle, -1, Switchboard.getSwitchboard().getConfig(SwitchboardConstants.NETWORK_NAME, "unspecified"), Switchboard.getSwitchboard().getConfig("network.unit.description", "unspecified"), COL_BACKGROUND);
        //if (eventPicture instanceof ymageMatrix) eventPicture = (ymageMatrix) eventPicture; //new ymageMatrix((ymageMatrix) eventPicture);
        // TODO: fix cloning of ymageMatrix pictures
        
        // get dimensions
        final int cr = Math.min(eventPicture.getWidth(), eventPicture.getHeight()) / 5 - 20;
        final int cx = eventPicture.getWidth() / 2;
        final int cy = eventPicture.getHeight() / 2 + 20;

        int angle;

        // draw in the primary search peers
        for (int j = 0; j < primarySearches.length; j++) {
            if (primarySearches[j] == null) continue;
            eventPicture.setColor((primarySearches[j].isAlive()) ? RasterPlotter.RED : RasterPlotter.GREEN);
            angle = (int) (360.0 * (((double) FlatWordPartitionScheme.std.dhtPosition(UTF8.getBytes(primarySearches[j].target().hash), null)) / ((double) Long.MAX_VALUE)));
            eventPicture.arcLine(cx, cy, cr - 20, cr, angle, true, null, null, -1, -1, -1, false);
        }

        // draw in the secondary search peers
        if (secondarySearches != null) {
            for (int j = 0; j < secondarySearches.length; j++) {
                if (secondarySearches[j] == null) continue;
                eventPicture.setColor((secondarySearches[j].isAlive()) ? RasterPlotter.RED : RasterPlotter.GREEN);
                angle = (int) (360.0 * (((double) FlatWordPartitionScheme.std.dhtPosition(UTF8.getBytes(secondarySearches[j].target().hash), null)) / ((double) Long.MAX_VALUE)));
                eventPicture.arcLine(cx, cy, cr - 10, cr, angle - 1, true, null, null, -1, -1, -1, false);
                eventPicture.arcLine(cx, cy, cr - 10, cr, angle + 1, true, null, null, -1, -1, -1, false);
            }
        }
        
        // draw in the search target
        final QueryParams query = event.getQuery();
        final Iterator<byte[]> i = query.queryHashes.iterator();
        eventPicture.setColor(RasterPlotter.GREY);
        while (i.hasNext()) {
            long[] positions = seedDB.scheme.dhtPositions(i.next());
            for (int j = 0; j < positions.length; j++) {
                angle = (int) (360.0 * (((double) positions[j]) / ((double) Long.MAX_VALUE)));
                eventPicture.arcLine(cx, cy, cr - 20, cr, angle, true, null, null, -1, -1, -1, false);
            }
        }

        return eventPicture;
    }

    public static RasterPlotter getNetworkPicture(final yacySeedDB seedDB, final long maxAge, final int width, final int height, final int passiveLimit, final int potentialLimit, final int maxCount, final int coronaangle, final long communicationTimeout, final String networkName, final String networkTitle, final String bgcolor) {
        return drawNetworkPicture(seedDB, width, height, passiveLimit, potentialLimit, maxCount, coronaangle, communicationTimeout, networkName, networkTitle, bgcolor);
    }

    private static RasterPlotter drawNetworkPicture(
            final yacySeedDB seedDB, final int width, final int height,
            final int passiveLimit, final int potentialLimit,
            final int maxCount, final int coronaangle,
            final long communicationTimeout,
            final String networkName, final String networkTitle, final String bgcolor) {

        RasterPlotter networkPicture = new RasterPlotter(width, height, (bgcolor.equals("000000")) ? RasterPlotter.DrawMode.MODE_ADD : RasterPlotter.DrawMode.MODE_SUB, bgcolor);
        if (seedDB == null) return networkPicture; // no other peers known

        final int maxradius = Math.min(width, height) / 2;
        final int innerradius = maxradius * 4 / 10;
        int outerradius = maxradius - 20;

        // draw network circle
        networkPicture.setColor(COL_DHTCIRCLE);
        networkPicture.arc(width / 2, height / 2, innerradius - 20, innerradius + 20, 100);

        //System.out.println("Seed Maximum distance is       " + yacySeed.maxDHTDistance);
        //System.out.println("Seed Minimum distance is       " + yacySeed.minDHTNumber);

        yacySeed seed;
        long lastseen;

        // draw connected senior and principals
        int count = 0;
        int totalCount = 0;
        Iterator<yacySeed> e = seedDB.seedsConnected(true, false, null, (float) 0.0);
        while (e.hasNext() && count < maxCount) {
            seed = e.next();
            if (seed == null) {
                Log.logWarning("NetworkGraph", "connected seed == null");
                continue;
            }
            //Log.logInfo("NetworkGraph", "drawing peer " + seed.getName());
            drawNetworkPicturePeer(networkPicture, width / 2, height / 2, innerradius, outerradius, seed, COL_ACTIVE_DOT, COL_ACTIVE_LINE, COL_ACTIVE_TEXT, coronaangle);
            count++;
        }
        totalCount += count;

        // draw disconnected senior and principals that have been seen lately
        count = 0;
        e = seedDB.seedsSortedDisconnected(false, yacySeed.LASTSEEN);
        while (e.hasNext() && count < maxCount) {
            seed = e.next();
            if (seed == null) {
                Log.logWarning("NetworkGraph", "disconnected seed == null");
                continue;
            }
            lastseen = Math.abs((System.currentTimeMillis() - seed.getLastSeenUTC()) / 1000 / 60);
            if (lastseen > passiveLimit) {
                break; // we have enough, this list is sorted so we don't miss anything
            }
            drawNetworkPicturePeer(networkPicture, width / 2, height / 2, innerradius, outerradius, seed, COL_PASSIVE_DOT, COL_PASSIVE_LINE, COL_PASSIVE_TEXT, coronaangle);
            count++;
        }
        totalCount += count;

        // draw juniors that have been seen lately
        count = 0;
        e = seedDB.seedsSortedPotential(false, yacySeed.LASTSEEN);
        while (e.hasNext() && count < maxCount) {
            seed = e.next();
            if (seed == null) {
                Log.logWarning("NetworkGraph", "potential seed == null");
                continue;
            }
            lastseen = Math.abs((System.currentTimeMillis() - seed.getLastSeenUTC()) / 1000 / 60);
            if (lastseen > potentialLimit) {
                break; // we have enough, this list is sorted so we don't miss anything
            }
            drawNetworkPicturePeer(networkPicture, width / 2, height / 2, innerradius, outerradius, seed, COL_POTENTIAL_DOT, COL_POTENTIAL_LINE, COL_POTENTIAL_TEXT, coronaangle);
            count++;
        }
        totalCount += count;

        // draw my own peer
        drawNetworkPicturePeer(networkPicture, width / 2, height / 2, innerradius, outerradius, seedDB.mySeed(), COL_MYPEER_DOT, COL_MYPEER_LINE, COL_MYPEER_TEXT, coronaangle);

        // draw DHT activity
        if (communicationTimeout >= 0) {
            Date horizon = new Date(System.currentTimeMillis() - communicationTimeout);
            for (Hit event: yacyChannel.channels(yacyChannel.DHTRECEIVE)) {
                if (event == null || event.getPubDate() == null) continue;
                if (event.getPubDate().after(horizon)) {
                    //System.out.println("*** NETWORK-DHTRECEIVE: " + event.getLink());
                    drawNetworkPictureDHT(networkPicture, width / 2, height / 2, innerradius, seedDB.mySeed(), seedDB.get(event.getLink()), COL_DHTIN, coronaangle, false);
                }
            }
            for (Hit event: yacyChannel.channels(yacyChannel.DHTSEND)) {
                if (event == null || event.getPubDate() == null) continue;
                if (event.getPubDate().after(horizon)) {
                    //System.out.println("*** NETWORK-DHTSEND: " + event.getLink());
                    drawNetworkPictureDHT(networkPicture, width / 2, height / 2, innerradius, seedDB.mySeed(), seedDB.get(event.getLink()), COL_DHTOUT, coronaangle, true);
                }
            }
        }        
        
        // draw description
        networkPicture.setColor(COL_HEADLINE);
        PrintTool.print(networkPicture, 2, 6, 0, "YACY NETWORK '" + networkName.toUpperCase() + "'", -1);
        PrintTool.print(networkPicture, 2, 14, 0, networkTitle.toUpperCase(), -1);
        PrintTool.print(networkPicture, width - 2, 6, 0, "SNAPSHOT FROM " + new Date().toString().toUpperCase(), 1);
        PrintTool.print(networkPicture, width - 2, 14, 0, "DRAWING OF " + totalCount + " SELECTED PEERS", 1);
        
        return networkPicture;
    }

    private static void drawNetworkPictureDHT(final RasterPlotter img, final int centerX, final int centerY, final int innerradius, final yacySeed mySeed, final yacySeed otherSeed, final String colorLine, final int coronaangle, boolean out) {
        final int angleMy = (int) (360.0 * (((double) FlatWordPartitionScheme.std.dhtPosition(UTF8.getBytes(mySeed.hash), null)) / ((double) Long.MAX_VALUE)));
        final int angleOther = (int) (360.0 * (((double) FlatWordPartitionScheme.std.dhtPosition(UTF8.getBytes(otherSeed.hash), null)) / ((double) Long.MAX_VALUE)));
        // draw line
        img.arcLine(centerX, centerY, innerradius, innerradius - 20, angleMy, !out,
                colorLine, null, 12, (coronaangle < 0) ? -1 : coronaangle / 30, 2, true);
        img.arcLine(centerX, centerY, innerradius, innerradius - 20, angleOther, out,
                colorLine, null, 12, (coronaangle < 0) ? -1 : coronaangle / 30, 2, true);
        img.arcConnect(centerX, centerY, innerradius - 20, angleMy, angleOther, out,
                colorLine, 100, null, 100, 12, (coronaangle < 0) ? -1 : coronaangle / 30, 2, true);
    }
    
    private static void drawNetworkPicturePeer(
            final RasterPlotter img, final int centerX, final int centerY,
            final int innerradius, final int outerradius,
            final yacySeed seed,
            final String colorDot, final String colorLine, final String colorText,
            final int coronaangle) {
        final String name = seed.getName().toUpperCase() /*+ ":" + seed.hash + ":" + (((double) ((int) (100 * (((double) yacySeed.dhtPosition(seed.hash)) / ((double) yacySeed.maxDHTDistance))))) / 100.0)*/;
        if (name.length() < shortestName) shortestName = name.length();
        if (name.length() > longestName) longestName = name.length();
        final int angle = (int) (360.0 * (((double) FlatWordPartitionScheme.std.dhtPosition(UTF8.getBytes(seed.hash), null)) / ((double) Long.MAX_VALUE)));
        //System.out.println("Seed " + seed.hash + " has distance " + seed.dhtDistance() + ", angle = " + angle);
        int linelength = 20 + outerradius * (20 * (name.length() - shortestName) / (longestName - shortestName) + Math.abs(seed.hash.hashCode() % 20)) / 60;
        if (linelength > outerradius) linelength = outerradius;
        int dotsize = 4 + (int) (seed.getLinkCount() / 2000000L);
        if (dotsize > 18) dotsize = 18;
        // draw dot
        img.setColor(colorDot);
        img.arcDot(centerX, centerY, innerradius, angle, dotsize);
        // draw line to text
        img.arcLine(centerX, centerY, innerradius + 18, innerradius + linelength, angle, true, colorLine, "444444", 12, coronaangle / 30, 0, true);
        // draw text
        img.setColor(colorText);
        PrintTool.arcPrint(img, centerX, centerY, innerradius + linelength, angle, name);

        // draw corona around dot for crawling activity
        int ppmx = seed.getPPM() / 30;
        if (coronaangle >= 0 && ppmx > 0) {
            drawCorona(img, centerX, centerY, innerradius, angle, dotsize, ppmx, coronaangle, true, false, 24, 24, 24); // color = 0..63
        }
        
        // draw corona around dot for query activity
        int qphx = ((int) (seed.getQPM() * 4.0));
        if (coronaangle >= 0 && qphx > 0) {
            drawCorona(img, centerX, centerY, innerradius, angle, dotsize, qphx, coronaangle, false, true, 8, 62, 8); // color = 0..63
        }
    }
    
    private static void drawCorona(final RasterPlotter img, final int centerX, final int centerY, final int innerradius, int angle, int dotsize, int strength, int coronaangle, boolean inside, boolean split, int r, int g, int b) {
        double ca = Math.PI * 2.0 * ((double) coronaangle) / 360.0;
        if (strength > 4) strength = 4;
        // draw a wave around crawling peers
        double wave;
        final int waveradius = innerradius / 2;
        int segments = 72;
        for (int radius = 0; radius < waveradius; radius++) {
            wave = ((double) (waveradius - radius) * strength) * (1.0 + Math.sin(Math.PI * 16 * radius / waveradius + ((inside) ? ca : -ca))) / 2.0 / (double) waveradius;
            img.setColor(((((long) (r * wave)) & 0xff) << 16) | (((long) ((g * wave)) & 0xff) << 8) | ((((long) (b * wave))) & 0xff));
            if (split) {
                for (int i = 0; i < segments; i++) {
                    int a = (coronaangle + 360 * i) / segments;
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

    public static RasterPlotter getBannerPicture(final long maxAge, final int width, final int height, final String bgcolor, final String textcolor, final String bordercolor, final String name, final long links, final long words, final String type, final int ppm, final String network, final int peers, final long nlinks, final long nwords, final double nqph, final long nppm) {
        if ((bannerPicture == null) || ((System.currentTimeMillis() - bannerPictureDate) > maxAge)) {
            drawBannerPicture(width, height, bgcolor, textcolor, bordercolor, name, links, words, type, ppm, network, peers, nlinks, nwords, nqph, nppm, logo);
        }
        return bannerPicture;
    }    
    
    public static RasterPlotter getBannerPicture(final long maxAge, final int width, final int height, final String bgcolor, final String textcolor, final String bordercolor, final String name, final long links, final long words, final String type, final int ppm, final String network, final int peers, final long nlinks, final long nwords, final double nqph, final long nppm, final BufferedImage newLogo) {
        if ((bannerPicture == null) || ((System.currentTimeMillis() - bannerPictureDate) > maxAge)) {
            drawBannerPicture(width, height, bgcolor, textcolor, bordercolor, name, links, words, type, ppm, network, peers, nlinks, nwords, nqph, nppm, newLogo);
        }
        return bannerPicture;
    }

    private static void drawBannerPicture(final int width, final int height, final String bgcolor, final String textcolor, final String bordercolor, final String name, final long links, final long words, final String type, final int ppm, final String network, final int peers, final long nlinks, final long nwords, final double nqph, final long nppm, final BufferedImage newLogo) {

        final int exprlength = 19;
        logo = newLogo;
        bannerPicture = new RasterPlotter(width, height, RasterPlotter.DrawMode.MODE_REPLACE, bgcolor);

        // draw description
        bannerPicture.setColor(textcolor);
        PrintTool.print(bannerPicture, 100, 12, 0, "PEER:  " + addTrailingBlanks(name, exprlength), -1);
        PrintTool.print(bannerPicture, 100, 22, 0, "LINKS: " + addBlanksAndDots(links, exprlength), -1);
        PrintTool.print(bannerPicture, 100, 32, 0, "WORDS: " + addBlanksAndDots(words, exprlength), -1);
        PrintTool.print(bannerPicture, 100, 42, 0, "TYPE:  " + addTrailingBlanks(type, exprlength), -1);
        PrintTool.print(bannerPicture, 100, 52, 0, "SPEED: " + addTrailingBlanks(ppm + " PAGES/MINUTE", exprlength), -1);

        PrintTool.print(bannerPicture, 285, 12, 0, "NETWORK: " + addTrailingBlanks(network + " [" + peers + "]", exprlength), -1);
        PrintTool.print(bannerPicture, 285, 22, 0, "LINKS:   " + addBlanksAndDots(nlinks, exprlength), -1);
        PrintTool.print(bannerPicture, 285, 32, 0, "WORDS:   " + addBlanksAndDots(nwords, exprlength), -1);
        PrintTool.print(bannerPicture, 285, 42, 0, "QUERIES: " + addTrailingBlanks(nqph + " QUERIES/HOUR", exprlength), -1);
        PrintTool.print(bannerPicture, 285, 52, 0, "SPEED:   " + addTrailingBlanks(nppm + " PAGES/MINUTE", exprlength), -1);

        if (logo != null) {
            final int x = (100/2 - logo.getWidth()/2);
            final int y = (height/2 - logo.getHeight()/2);
            bannerPicture.insertBitmap(logo, x, y, 0, 0, RasterPlotter.FilterMode.FILTER_ANTIALIASING);
        }

        if (!bordercolor.equals("")) {
            bannerPicture.setColor(bordercolor);
            bannerPicture.line(0, 0, 0, height-1, 100);
            bannerPicture.line(0, 0, width-1, 0, 100);
            bannerPicture.line(width-1, 0, width-1, height-1, 100);
            bannerPicture.line(0, height-1, width-1, height-1, 100);
        }
        
        // set timestamp
         bannerPictureDate = System.currentTimeMillis();
    }
    
    public static boolean logoIsLoaded() {
        if (logo == null) {
            return false;
        }
        return true;
    }

    private static String addBlanksAndDots(final long input, final int length) {
        return addBlanksAndDots(Long.toString(input), length);
    }

    private static String addBlanksAndDots(String input, final int length) {
        input = addDots(input);
        input = addTrailingBlanks(input,length);
        return input;
    }

    private static String addDots(String word) {
        String tmp = "";
        int len = word.length();
        if (len > 3) {
            while(len > 3) {
                if(tmp.equals("")) {
                    tmp = word.substring(len-3,len);
                } else {
                    tmp = word.substring(len-3,len) + "." + tmp;
                }
                word = word.substring(0,len-3);
                len = word.length();
            }
            word = word + "." + tmp;
        }
        return word;
    }

    private static String addTrailingBlanks(String word, int length) {
        if (length > word.length()) {
            String blanks = "";
            length = length - word.length();
            int i = 0;
            while(i++ < length) {
                blanks += " ";
            }
            word = blanks + word;
        }
        return word;
    }

}