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

import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.tools.ImagePainter;

import java.awt.Graphics2D; 
import java.awt.Color; 
import java.awt.image.BufferedImage; 
import java.awt.image.WritableRaster; 
import javax.imageio.ImageIO; 
import java.io.File; 
import java.io.IOException; 
import java.io.FileOutputStream; 
import java.io.ByteArrayOutputStream; 
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class imagetest {
    

    public static BufferedImage respond(httpHeader header, serverObjects post, serverSwitch env) {
        
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
        
        ImagePainter img = new ImagePainter(300, 200);
        img.draw(3, 5, 277, 170, "AA1122");
        
        //g.drawImage(img, BufferedImageOp
        return bi;
    }
    
}
