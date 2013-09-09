// imagetest.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 05.10.2005
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

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;
import net.yacy.visualization.PrintTool;
import net.yacy.visualization.RasterPlotter;

public class imagetest {

    public static RasterPlotter respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch env) {

        final RasterPlotter img = new RasterPlotter(800, 600, RasterPlotter.DrawMode.MODE_SUB, "FFFFFF");
        img.setColor(RasterPlotter.GREY);
        for (int y = 0; y < 600; y = y + 50) PrintTool.print(img, 0, 6 + y, 0, Integer.toString(y), -1);
        for (int x = 0; x < 800; x = x + 50) PrintTool.print(img, x, 6    , 0, Integer.toString(x), -1);
        img.setColor(RasterPlotter.RED);
        img.dot(550, 110, 90, true, 100);
        img.setColor(RasterPlotter.GREEN);
        img.dot(480, 200, 90, true, 100);
        img.setColor(RasterPlotter.BLUE);
        img.dot(620, 200, 90, true, 100);
        img.setColor(RasterPlotter.RED);
        img.arc(300, 270, 30, 70, 100);
        img.setColor(Long.parseLong("330000", 16));
        img.arc(220, 110, 50, 90, 30, 110);
        img.arc(210, 120, 50, 90, 30, 110);
        img.setColor(RasterPlotter.GREY);
        PrintTool.print(img, 50, 110, 0, "BROADCAST MESSAGE #772: NODE %882 GREY abcefghijklmnopqrstuvwxyz", -1);
        img.setColor(RasterPlotter.GREEN);
        PrintTool.print(img, 50, 120, 0, "BROADCAST MESSAGE #772: NODE %882 GREEN abcefghijklmnopqrstuvwxyz", -1);
        for (long i = 0; i < 256; i++) {
            img.setColor(i);
            img.dot(10 + 14 * (int) (i / 16), 200 + 14 * (int) (i % 16), 6, true, 100);
        }
        img.setColor(Long.parseLong("008000", 16));
        img.dot(10 + 14 * 8, 200 + 14 * 8, 90, true, 100);
        /*
        for (long r = 0; r < 256; r = r + 16) {
            for (long g = 0; g < 256; g = g + 16) {
                for (long b = 0; b < 256; b = b + 16) {
                    img.setColor(r << 16 + g << 8 + b);
                    img.dot((int) (10 + 48 * g + 12 * ((r / 16) / 12)), (int) (420 + 48 * b + 12 * ((r / 16) % 12)), 4, true);
                }
            }
        }*/
        img.setColor(Long.parseLong("0000A0", 16));
        img.arc(550, 400, 40, 81, 100);
        img.setColor(Long.parseLong("010100", 16));
        for (int i = 0; i <= 360; i++) {
            img.arc(550, 400, 40, 41 + i/9, 0, i);
        }
        img.setColor(RasterPlotter.GREY);
        int angle;
        for (byte c = (byte) 'A'; c <= 'Z'; c++) {
            angle = (c - (byte) 'A') * 360 / ((byte) 'Z' - (byte) 'A');
            img.arcLine(550, 400, 81, 100, angle, true, null, null, -1, -1, -1, false);
            PrintTool.arcPrint(img, 550, 400, 100, angle, "ANGLE" + angle + ":" + (char) c);
        }
        return img;

    }

}
