/**
 *  AccessPicture_p
 *  Copyright 2011 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First released 18.02.2010 at http://yacy.net
 *
 *  $LastChangedDate: 2010-06-16 17:11:21 +0200 (Mi, 16 Jun 2010) $
 *  $LastChangedRevision: 6922 $
 *  $LastChangedBy: orbiter $
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

import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import net.yacy.cora.protocol.ConnectionInfo;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;
import net.yacy.server.serverAccessTracker;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.HexGridPlotter;
import net.yacy.visualization.PrintTool;
import net.yacy.visualization.RasterPlotter;

public class AccessPicture_p {

    private static int[] times = new int[]{60000, 50000, 40000, 30000, 20000, 10000, 1000};

    public static RasterPlotter respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;

        String color_text    = "AAAAAA";
        String color_back    = "FFFFFF";
        String color_grid    = "333333";
        String color_dot     = "33CC33";
        String color_line    = "555555";

        int width = 1024;
        int height = 576;
        int cellsize = 18;
        boolean corona = false;
        int coronaangle = 0;

        if (post != null) {
            width         = post.getInt("width", 1024);
            height        = post.getInt("height", 576);
            cellsize      = post.getInt("cellsize", cellsize);
            color_text    = post.get("colortext",   color_text);
            color_back    = post.get("colorback",   color_back);
            color_grid    = post.get("colorgrid",   color_grid);
            color_dot     = post.get("colordot",    color_dot);
            color_line    = post.get("colorline",   color_line);
            corona        = !post.containsKey("corona") || post.getBoolean("corona");
            coronaangle   = (corona) ? post.getInt("coronaangle", 0) : -1;
        }
        if (coronaangle < 0) corona = false;

        // too small values lead to an error, too big to huge CPU/memory consumption, resulting in possible DOS.
        if (width < 32 ) width = 32;
        if (width > 10000) width = 10000;
        if (height < 24) height = 24;
        if (height > 10000) height = 10000;

        final RasterPlotter.DrawMode drawMode = (RasterPlotter.darkColor(color_back)) ? RasterPlotter.DrawMode.MODE_ADD : RasterPlotter.DrawMode.MODE_SUB;
        final HexGridPlotter picture = new HexGridPlotter(width, height, drawMode, color_back, cellsize);
        picture.drawGrid(color_grid);

        // calculate dimensions for left and right column
        final int gridLeft = 0;
        int gridRight = picture.gridWidth() - 2;
        if ((gridRight & 1) == 0) gridRight--;

        // draw home peer
        final int centerx = (picture.gridWidth() >> 1) - 1;
        final int centery = picture.gridHeight() >> 1;
        long color_dot_l = Long.parseLong(color_dot, 16);
        long color_text_l = Long.parseLong(color_text, 16);
        long color_line_l = Long.parseLong(color_line, 16);
        long color_grid_l = Long.parseLong(color_grid, 16);
        picture.setColor(color_dot_l);
        picture.gridDot(centerx, centery, 5, true, 100);
        if (corona) {
            for (int i = 0; i < 6; i++) {
                picture.gridDot(centerx, centery, 50, i * 60 + coronaangle / 6, i * 60 + 30 + coronaangle / 6);
            }
        } else {
            picture.gridDot(centerx, centery, 50, false, 100);
        }
        //picture.gridDot(centerx, centery, 31, false);
        picture.setColor(color_text_l);
        picture.gridPrint(centerx, centery, 5, "THIS YACY PEER", "\"" + sb.peers.myName().toUpperCase() + "\"", 0);

        // left column: collect data for access from outside
        final int verticalSlots = (picture.gridHeight() >> 1) - 1;
        final String[] hosts = new String[verticalSlots];
        final int[] time = new int[verticalSlots];
        final int[] count = new int[verticalSlots];
        for (int i = 0; i < verticalSlots; i++) {hosts[i] = null; time[i] = 0; count[i] = 0;}

        String host;
        int c, h;
        for (final int time2 : times) {
            final Iterator<String> i = serverAccessTracker.accessHosts();
            try {
                while (i.hasNext()) {
                    host = i.next();
                    c = serverAccessTracker.latestAccessCount(host, time2);
                    if (c > 0) {
                        h = (Math.abs(host.hashCode())) % hosts.length;
                        hosts[h] = host;
                        count[h] = c;
                        time[h] = time2;
                    }
                }
            } catch (final ConcurrentModificationException e) {
                // we don't want to synchronize this
                ConcurrentLog.logException(e);
            }
        }

        // draw left column: access from outside
        for (int i = 0; i < hosts.length; i++) {
            if (hosts[i] != null) {
                picture.setColor(color_dot_l);
                picture.gridDot(gridLeft, i * 2 + 1, 7, false, 100);
                picture.gridDot(gridLeft, i * 2 + 1, 8, false, 100);
                picture.setColor(color_text_l);
                picture.gridPrint(gridLeft, i * 2 + 1, 8, hosts[i].toUpperCase(), "COUNT = " + count[i] + ", TIME > " + ((time[i] >= 60000) ? ((time[i] / 60000) + " MINUTES") : ((time[i] / 1000) + " SECONDS")), -1);
                if (corona) {
                    picture.gridLine((centerx - gridLeft) / 2 - 2, i * 2 + 1, gridLeft, i * 2 + 1,
                            color_line, 100, "AAAAAA", 100, 12, 11 - coronaangle / 30, 0, true);
                    picture.gridLine(centerx, centery, (centerx - gridLeft) / 2 - 2, i * 2 + 1,
                            color_line, 100, "AAAAAA", 100, 12, 11 - coronaangle / 30, 0, true);
                } else {
                    picture.setColor(color_line_l);
                    picture.gridLine(gridLeft, i * 2 + 1, (centerx - gridLeft) / 2, i * 2 + 1);
                    picture.gridLine(centerx, centery, (centerx - gridLeft) / 2, i * 2 + 1);
                }
            }
        }

        // right column: collect data for access to outside
        for (int i = 0; i < verticalSlots; i++) {hosts[i] = null; time[i] = 0; count[i] = 0;}
        final Set<ConnectionInfo> allConnections = ConnectionInfo.getAllConnections();
        c = 0;
        synchronized (allConnections) {
            for (final ConnectionInfo conInfo: allConnections) {
                host = conInfo.getTargetHost();
                h = (Math.abs(host.hashCode())) % hosts.length;
                hosts[h] = host + " - " + conInfo.getCommand();
                count[h] = (int) conInfo.getUpbytes();
                time[h] = (int) conInfo.getLifetime();
            }
        }

        // draw right column: access to outside
        for (int i = 0; i < hosts.length; i++) {
            if (hosts[i] != null) {
                picture.setColor(color_dot_l);
                picture.gridDot(gridRight, i * 2 + 1, 7, false, 100);
                picture.gridDot(gridRight, i * 2 + 1, 8, false, 100);
                picture.setColor(color_text_l);
                picture.gridPrint(gridRight, i * 2 + 1, 8, hosts[i].toUpperCase(), count[i] + " BYTES, " + time[i] + " MS DUE", 1);
                if (corona) {
                    picture.gridLine(gridRight, i * 2 + 1, centerx + (gridRight - centerx) / 2 + 2, i * 2 + 1,
                            color_line, 100, "AAAAAA", 100, 12, 11 - coronaangle / 30, 0, true);
                    picture.gridLine(centerx, centery, centerx + (gridRight - centerx) / 2 + 2, i * 2 + 1,
                            color_line, 100, "AAAAAA", 100, 12, coronaangle / 30, 0, true);
                } else {
                    picture.setColor(color_line_l);
                    picture.gridLine(gridRight, i * 2 + 1, centerx + (gridRight - centerx) / 2, i * 2 + 1);
                    picture.gridLine(centerx, centery, centerx + (gridRight - centerx) / 2, i * 2 + 1);
                }
            }
        }

        // print headline
        picture.setColor(color_text_l);
        PrintTool.print(picture, 2, 6, 0, "YACY NODE ACCESS GRID", -1);
        PrintTool.print(picture, width - 2, 6, 0, "SNAPSHOT FROM " + new Date().toString().toUpperCase(), 1);

        // print legend
        picture.setColor(color_grid_l);
        picture.gridLine(gridLeft, 0, centerx - 3, 0);
        picture.gridLine(gridLeft, 0, gridLeft, picture.gridHeight() - 1);
        picture.gridLine(centerx - 3, 0, centerx - 3, picture.gridHeight() - 1);
        picture.setColor(color_dot_l);
        picture.gridLine(gridLeft, picture.gridHeight() - 1, centerx - 3, picture.gridHeight() - 1);
        picture.gridPrint(gridLeft, picture.gridHeight() - 1, 8, "", "INCOMING CONNECTIONS", -1);

        picture.setColor(color_grid_l);
        picture.gridLine(centerx + 3, 0, gridRight, 0);
        picture.gridLine(centerx + 3, 0, centerx + 3, picture.gridHeight() - 1);
        picture.gridLine(gridRight, 0, gridRight, picture.gridHeight() - 1);
        picture.setColor(color_dot_l);
        picture.gridLine(centerx + 3, picture.gridHeight() - 1, gridRight, picture.gridHeight() - 1);
        picture.gridPrint(gridRight, picture.gridHeight() - 1, 8, "", "OUTGOING CONNECTIONS", 1);

        return picture;

    }
}
