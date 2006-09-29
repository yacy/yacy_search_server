// plasmaGrafics.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;

import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySearch;
import de.anomic.yacy.yacySeed;
import de.anomic.ymage.ymageMatrix;
import de.anomic.ymage.ymageMatrixPainter;
import de.anomic.ymage.ymagePainter;

public class plasmaGrafics {

    private static int shortestName = 10;
    private static int longestName = 12;

    private static ymagePainter networkPicture = null;
    private static long         networkPictureDate = 0;

    public static ymagePainter getSearchEventPicture() {
        if (plasmaSearchEvent.lastEvent == null) return null;
        yacySearch[] primarySearches = plasmaSearchEvent.lastEvent.getPrimarySearchThreads();
        yacySearch[] secondarySearches = plasmaSearchEvent.lastEvent.getSecondarySearchThreads();
        if (primarySearches == null) return null; // this was a local search and there are no threads

        // get a copy of a recent network picture
        ymagePainter eventPicture = getNetworkPicture(120000);
        if (eventPicture instanceof ymageMatrixPainter) eventPicture = new ymageMatrixPainter((ymageMatrix) eventPicture);

        // get dimensions
        int cr = Math.min(eventPicture.getWidth(), eventPicture.getHeight()) / 5 - 20;
        int cx = eventPicture.getWidth() / 2;
        int cy = eventPicture.getHeight() / 2;

        String hash;
        int angle;

        // draw in the primary search peers
        for (int j = 0; j < primarySearches.length; j++) {
            eventPicture.setColor((primarySearches[j].isAlive()) ? ymageMatrix.ADDITIVE_RED : ymageMatrix.ADDITIVE_GREEN);
            hash = primarySearches[j].target().hash;
            angle = (int) ((long) 360 * (yacySeed.dhtPosition(hash) / (yacySeed.maxDHTDistance / (long) 10000)) / (long) 10000);
            eventPicture.arcLine(cx, cy, cr - 20, cr, angle);
        }

        // draw in the secondary search peers
        if (secondarySearches != null) {
            for (int j = 0; j < secondarySearches.length; j++) {
                eventPicture.setColor((secondarySearches[j].isAlive()) ? ymageMatrix.ADDITIVE_RED : ymageMatrix.ADDITIVE_GREEN);
                hash = secondarySearches[j].target().hash;
                angle = (int) ((long) 360 * (yacySeed.dhtPosition(hash) / (yacySeed.maxDHTDistance / (long) 10000)) / (long) 10000);
                eventPicture.arcLine(cx, cy, cr - 10, cr, angle - 1);
                eventPicture.arcLine(cx, cy, cr - 10, cr, angle + 1);
            }
        }
        
        // draw in the search target
        plasmaSearchQuery query = plasmaSearchEvent.lastEvent.getQuery();
        Iterator i = query.queryHashes.iterator();
        eventPicture.setMode(ymageMatrix.MODE_ADD);
        eventPicture.setColor(ymageMatrix.ADDITIVE_BLACK);
        while (i.hasNext()) {
            hash = (String) i.next();
            angle = (int) ((long) 360 * (yacySeed.dhtPosition(hash) / (yacySeed.maxDHTDistance / (long) 10000)) / (long) 10000);
            eventPicture.arcLine(cx, cy, cr - 20, cr, angle);
        }

        return eventPicture;
    }

    public static ymagePainter getNetworkPicture(long maxAge) {
        return getNetworkPicture(maxAge, 640, 480, 300, 300, 1000, true);
    }

    public static ymagePainter getNetworkPicture(long maxAge, int width, int height, int passiveLimit, int potentialLimit, int maxCount, boolean corona) {
        if ((networkPicture == null) || ((System.currentTimeMillis() - networkPictureDate) > maxAge)) {
            drawNetworkPicture(width, height, passiveLimit, potentialLimit, maxCount, corona);
        }
        return networkPicture;
    }

    private static void drawNetworkPicture(int width, int height, int passiveLimit, int potentialLimit, int maxCount, boolean corona) {

        int innerradius = Math.min(width, height) / 5;
        int outerradius = innerradius + innerradius * yacyCore.seedDB.sizeConnected() / 100;
        if (outerradius > innerradius * 2) outerradius = innerradius * 2;

        if (yacyCore.seedDB == null) return; // no other peers known

        networkPicture = new ymageMatrixPainter(width, height, "000010");
        networkPicture.setMode(ymageMatrix.MODE_ADD);

        // draw network circle
        networkPicture.setColor("008020");
        networkPicture.arc(width / 2, height / 2, innerradius - 20, innerradius + 20, 0, 360);

        //System.out.println("Seed Maximum distance is       " + yacySeed.maxDHTDistance);
        //System.out.println("Seed Minimum distance is       " + yacySeed.minDHTNumber);

        yacySeed seed;
        long lastseen;

        // draw connected senior and principals
        int count = 0;
        int totalCount = 0;
        Enumeration e = yacyCore.seedDB.seedsConnected(true, false, null);
        while (e.hasMoreElements() && count < maxCount) {
            seed = (yacySeed) e.nextElement();
            if (seed != null) {
                drawNetworkPicturePeer(networkPicture, width / 2, height / 2, innerradius, outerradius, seed, "000040", "608860", "B0FFB0", corona);
                count++;
            }
        }
        totalCount += count;

        // draw disconnected senior and principals that have been seen lately
        count = 0;
        e = yacyCore.seedDB.seedsSortedDisconnected(false, yacySeed.LASTSEEN);
        while (e.hasMoreElements() && count < maxCount) {
            seed = (yacySeed) e.nextElement();
            if (seed != null) {
                lastseen = Math.abs((System.currentTimeMillis() - seed.getLastSeenTime()) / 1000 / 60);
                if (lastseen > passiveLimit) break; // we have enough, this list is sorted so we don't miss anything
                drawNetworkPicturePeer(networkPicture, width / 2, height / 2, innerradius, outerradius, seed, "101010", "401000", "802000", corona);
                count++;
            }
        }
        totalCount += count;

        // draw juniors that have been seen lately
        count = 0;
        e = yacyCore.seedDB.seedsSortedPotential(false, yacySeed.LASTSEEN);
        while (e.hasMoreElements() && count < maxCount) {
            seed = (yacySeed) e.nextElement();
            if (seed != null) {
                lastseen = Math.abs((System.currentTimeMillis() - seed.getLastSeenTime()) / 1000 / 60);
                if (lastseen > potentialLimit) break; // we have enough, this list is sorted so we don't miss anything
                drawNetworkPicturePeer(networkPicture, width / 2, height / 2, innerradius, outerradius, seed, "202000", "505000", "A0A000", corona);
                count++;
            }
        }
        totalCount += count;

        // draw my own peer
        drawNetworkPicturePeer(networkPicture, width / 2, height / 2, innerradius, outerradius, yacyCore.seedDB.mySeed, "800000", "AAAAAA", "FFFFFF", corona);

        // draw description
        networkPicture.setColor("FFFFFF");
        networkPicture.setMode(ymageMatrix.MODE_ADD);
        networkPicture.print(2, 8, 0, "THE YACY NETWORK", true);
        networkPicture.print(2, 16, 0, "DRAWING OF " + totalCount + " SELECTED PEERS", true);
        networkPicture.print(width - 2, 8, 0, "SNAPSHOT FROM " + new Date().toString().toUpperCase(), false);

        // set timestamp
        networkPictureDate = System.currentTimeMillis();
    }

    private static void drawNetworkPicturePeer(ymagePainter img, int x, int y, int innerradius, int outerradius, yacySeed seed, String colorDot, String colorLine, String colorText, boolean corona) {
        String name = seed.getName().toUpperCase();
        if (name.length() < shortestName) shortestName = name.length();
        if (name.length() > longestName) longestName = name.length();
        int angle = (int) ((long) 360 * (seed.dhtPosition() / (yacySeed.maxDHTDistance / (long) 10000)) / (long) 10000);
        //System.out.println("Seed " + seed.hash + " has distance " + seed.dhtDistance() + ", angle = " + angle);
        int linelength = 20 + outerradius * (20 * (name.length() - shortestName) / (longestName - shortestName) + (Math.abs(seed.hash.hashCode()) % 20)) / 60;
        if (linelength > outerradius) linelength = outerradius;
        int dotsize = 6 + 2 * (int) (seed.getLinkCount() / 500000L);
        if (dotsize > 18) dotsize = 18;
        img.setMode(ymageMatrix.MODE_ADD);
        // draw dot
        img.setColor(colorDot);
        img.arcDot(x, y, innerradius, angle, dotsize);
        // draw line to text
        img.setColor(colorLine);
        img.arcLine(x, y, innerradius + 18, innerradius + linelength, angle);
        // draw text
        img.setColor(colorText);
        img.arcPrint(x, y, innerradius + linelength, angle, name);

        // draw corona around dot for crawling activity
        int ppm10 = seed.getPPM() / 10;
        if ((corona) && (ppm10 > 0)) {
            if (ppm10 > 3) ppm10 = 3;
            // draw a wave around crawling peers
            long strength;
            img.setMode(ymageMatrix.MODE_SUB);
            img.setColor("303030");
            img.arcArc(x, y, innerradius, angle, dotsize + 1, dotsize + 1, 0, 360);
            int waveradius = innerradius / 2;
            for (int r = 0; r < waveradius; r++) {
                strength = (waveradius - r) * (long) (0x08 * ppm10 * (1.0 + Math.sin(Math.PI * 16 * r / waveradius))) / waveradius;
                //System.out.println("r = " + r + ", Strength = " + strength);
                img.setColor((strength << 16) | (strength << 8) | strength);
                img.arcArc(x, y, innerradius, angle, dotsize + r, dotsize + r, 0, 360);
            }
        }
    }

}