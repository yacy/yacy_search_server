// imagetest.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// Created 05.10.2005
//
// $LastChangedDate: 2005-09-29 02:24:09 +0200 (Thu, 29 Sep 2005) $
// $LastChangedRevision: 811 $
// $LastChangedBy: orbiter $
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

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.ymage.ymageMatrix;
import de.anomic.ymage.ymageToolPrint;

public class imagetest {
    
    public static ymageMatrix respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        /*
        BufferedImage bi = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB); 
        Graphics2D g = bi.createGraphics();
        g.setBackground(Color.white);
        g.clearRect(0, 0, 640, 400);
        
        g.setColor(new Color(200, 200, 0));
        g.drawRect(100, 50, 40, 30);
        
        g.setColor(new Color(0, 0, 200));
        try {
            Class[] pType    = {Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE};
            Object[] pParam = new Integer[]{new Integer(66), new Integer(55), new Integer(80), new Integer(80)};
            
            String com = "drawRect";
            Method m = g.getClass().getMethod(com, pType);
            Object result = m.invoke(g, pParam);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        
        WritableRaster r = bi.getRaster();
        for (int i = 20; i < 100; i++) r.setPixel(i, 30, new int[]{255, 0, 0});
        for (int i = 20; i < 100; i++) r.setPixel(i, 32, new int[]{0, 255, 0});
        for (int i = 20; i < 100; i++) r.setPixel(i, 34, new int[]{0, 0, 255});
        return bi;
        */
        ymageMatrix img = new ymageMatrix(800, 600, ymageMatrix.MODE_SUB, "FFFFFF");
        img.setColor(ymageMatrix.GREY);
        for (int y = 0; y < 600; y = y + 50) ymageToolPrint.print(img, 0, 6 + y, 0, "" + y, -1);
        for (int x = 0; x < 800; x = x + 50) ymageToolPrint.print(img, x, 6    , 0, "" + x, -1);
        img.setColor(ymageMatrix.RED);
        img.dot(550, 110, 90, true);
        img.setColor(ymageMatrix.GREEN);
        img.dot(480, 200, 90, true);
        img.setColor(ymageMatrix.BLUE);
        img.dot(620, 200, 90, true);
        img.setColor(ymageMatrix.RED);
        img.arc(300, 270, 30, 70, 0, 360);
        img.setColor("330000");
        img.arc(220, 110, 50, 90, 30, 110);
        img.arc(210, 120, 50, 90, 30, 110);
        img.setColor(ymageMatrix.GREY);
        ymageToolPrint.print(img, 50, 110, 0, "BROADCAST MESSAGE #772: NODE %882 GREY abcefghijklmnopqrstuvwxyz", -1);
        img.setColor(ymageMatrix.GREEN);
        ymageToolPrint.print(img, 50, 120, 0, "BROADCAST MESSAGE #772: NODE %882 GREEN abcefghijklmnopqrstuvwxyz", -1);
        for (long i = 0; i < 256; i++) {
            img.setColor(i);
            img.dot(10 + 14 * (int) (i / 16), 200 + 14 * (int) (i % 16), 6, true);
        }
        img.setColor("008000");
        img.dot(10 + 14 * 8, 200 + 14 * 8, 90, true);
        /*
        for (long r = 0; r < 256; r = r + 16) {
            for (long g = 0; g < 256; g = g + 16) {
                for (long b = 0; b < 256; b = b + 16) {
                    img.setColor(r << 16 + g << 8 + b);
                    img.dot((int) (10 + 48 * g + 12 * ((r / 16) / 12)), (int) (420 + 48 * b + 12 * ((r / 16) % 12)), 4, true);
                }
            }
        }*/
        img.setColor("0000A0");
        img.arc(550, 400, 40, 81, 0, 360);
        img.setColor("010100");
        for (int i = 0; i <= 360; i++) {
            img.arc(550, 400, 40, 41 + i/9, 0, i);
        }
        img.setColor(ymageMatrix.GREY);
        int angle;
        for (byte c = (byte) 'A'; c <= 'Z'; c++) {
            angle = (c - (byte) 'A') * 360 / ((byte) 'Z' - (byte) 'A');
            img.arcLine(550, 400, 81, 100, angle);
            ymageToolPrint.arcPrint(img, 550, 400, 100, angle, "ANGLE" + angle + ":" + (char) c);
        }
        return img;
        
    }
    
}
