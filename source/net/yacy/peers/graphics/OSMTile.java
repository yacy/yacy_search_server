/**
 *  OSMTile
 *  Copyright 2008 by Michael Peter Christen
 *  First released 12.02.2008 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.federate.yacy.CacheStrategy;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.Cache;
import net.yacy.crawler.retrieval.Response;
import net.yacy.search.Switchboard;
import net.yacy.visualization.RasterPlotter;

public class OSMTile {

    // helper methods to load map images from openstreetmap.org

    /**
     * generate a image according to a given coordinate of a middle tile
     * and a width and height of tile numbers. The tile number width and height must
     * always be impair since the given tile must be always in the middle
     * @param t the middle tile
     * @param width number of tiles
     * @param height number of tiles
     * @return the image
     */
    public static RasterPlotter getCombinedTiles(final tileCoordinates t, int width, int height) {
        final int w = (width - 1) / 2;
        width = w * 2 + 1;
        final int h = (height - 1) / 2;
        height = h * 2 + 1;
        final RasterPlotter m = new RasterPlotter(256 * width, 256 * height, RasterPlotter.DrawMode.MODE_REPLACE, "FFFFFF");
        final List<Place> tileLoader = new ArrayList<Place>();
        Place place;
        // start tile loading concurrently
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                place = new Place(m, t.xtile - w + i, t.ytile - h + j, 256 * i, 256 * j, t.zoom);
                place.start();
                tileLoader.add(place);
                if (t.zoom >= 17) try {Thread.sleep(100);} catch (final InterruptedException e) {} // be nice with tile server for uncached tiles
            }
        }
        // wait until all tiles are loaded
        for (final Place p: tileLoader) try { p.join(); } catch (final InterruptedException e) {}
        return m;
    }

    static class Place extends Thread {
        RasterPlotter m;
        int xt, yt, xc, yc, z;
        public Place(final RasterPlotter m, final int xt, final int yt, final int xc, final int yc, final int z) {
            this.m = m; this.xt = xt; this.yt = yt; this.xc = xc; this.yc = yc; this.z = z;
        }
        @Override
        public void run() {
            final tileCoordinates t = new tileCoordinates(this.xt, this.yt, this.z);
            BufferedImage bi = null;
            for (int i = 0; i < 5; i++) {
                bi = getSingleTile(t, i);
                if (bi != null) {
                    this.m.insertBitmap(bi, this.xc, this.yc);
                    return;
                }
                // don't DoS OSM when trying again
                try {Thread.sleep(300 + 100 * i);} catch (final InterruptedException e) {}
            }
        }
    }

    public static BufferedImage getSingleTile(final tileCoordinates tile, final int retry) {
        DigestURL tileURL;
        try {
            tileURL = new DigestURL(tile.url(retry));
        } catch (final MalformedURLException e) {
            return null;
        }
        //System.out.println("*** DEBUG: fetching OSM tile: " + tileURL.toNormalform(true, true));
        byte[] tileb = Cache.getContent(tileURL.hash());
        if (tileb == null) {
            // download resource using the crawler and keep resource in memory if possible
            Response entry = null;
            try {
                entry = Switchboard.getSwitchboard().loader.load(Switchboard.getSwitchboard().loader.request(tileURL, false, false), CacheStrategy.IFEXIST, Integer.MAX_VALUE, null, ClientIdentification.yacyInternetCrawlerAgent);
            } catch (final IOException e) {
                ConcurrentLog.warn("OSMTile", "cannot load: " + e.getMessage());
                return null;
            }
            tileb = entry.getContent();
        }
        try {
            ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
            return ImageIO.read(new ByteArrayInputStream(tileb));
        } catch (final EOFException e) {
            return null;
        } catch (final IOException e) {
            return null;
        }
    }

    public static class tileCoordinates {

        int xtile, ytile, zoom;

        public tileCoordinates(final double lat, final double lon, final int zoom) {
            // see http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
            this.zoom = zoom;
            this.xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
            this.ytile = (int) Math.floor((1 - Math.log(Math.tan(lat * RasterPlotter.PI180) + 1 / Math.cos(lat * RasterPlotter.PI180)) / Math.PI) / 2 * (1 << zoom));
        }

        public tileCoordinates(final int xtile, final int ytile, final int zoom) {
            this.zoom = zoom;
            this.xtile = xtile;
            this.ytile = ytile;
        }

        public String url(final int retry) {
            // see http://wiki.openstreetmap.org/wiki/Public_Domain_Map
            final int hash = (this.xtile + 7 * this.ytile + 13 * this.zoom + retry) % 4;
            final String host = (hash == 3) ? "tile.openstreetmap.org" : ((char) ('a' + hash)) + ".tile.openstreetmap.org";
            final String url = "http://" + host + "/" + this.zoom + "/" + this.xtile + "/" + this.ytile + ".png";
            //System.out.println("OSM URL = " + url);
            return url;
        }

    }
}
