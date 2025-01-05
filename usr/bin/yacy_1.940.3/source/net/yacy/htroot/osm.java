/**
 *  osm
 *  Copyright 2008 by Michael Peter Christen
 *  First released 13.02.2011 at https://yacy.net
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

package net.yacy.htroot;

import net.yacy.cora.protocol.HeaderFramework;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.peers.graphics.EncodedImage;
import net.yacy.peers.graphics.OSMTile;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.PrintTool;
import net.yacy.visualization.RasterPlotter;
import net.yacy.visualization.RasterPlotter.DrawMode;

public class osm {

    public static EncodedImage respond(final RequestHeader header, final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

        int zoom = 10;
        double lat = 50.11670d;
        double lon = 8.68333d;
        int width = 3;
        int height = 3;

        if (post != null) {
            zoom = post.getInt("zoom", zoom);
            lat = post.getDouble("lat", lat);
            lon = post.getDouble("lon", lon);
            width = post.getInt("width", width);
            height = post.getInt("height", height);
        }

        final OSMTile.tileCoordinates coord = new OSMTile.tileCoordinates(lat, lon, zoom);
        final RasterPlotter map = OSMTile.getCombinedTiles(coord, width, height);
        map.setDrawMode(DrawMode.MODE_SUB);
        map.setColor(0xffffff);
        /*
         * copyright notice on OSM Tiles
         * According to http://www.openstreetmap.org/copyright/ the (C) of the map tiles is (CC BY-SA)
         * while the OpenStreetMap raw data is licensed with (ODbL) http://opendatacommons.org/licenses/odbl/
         * Map tiles shall be underlined with the statement "(C) OpenStreetMap contributors". In our 5-dot character
         * set the lowercase letters do not look good, so we use uppercase only.
         * The (C) symbol is not available in our font, so we use the letters (C) instead.
         */
        PrintTool.print(map, map.getWidth() - 6, map.getHeight() - 6, 0, "(C) OPENSTREETMAP CONTRIBUTORS", 1, 80);
        return new EncodedImage(map, header.get(HeaderFramework.CONNECTION_PROP_EXT, null), true);
   }

}