// ymageCaptcha.java
// -----------------------
// part of YaCy
// (C) by Marc Nause
//
// $LastChangedDate: 2007-04-03 22:56:00 +0100 (Di, 04 Apr 2007) $
// $LastChangedRevision: 3542 $
// $LastChangedBy: low012 $
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

package de.anomic.ymage;

import java.util.Random;
import javax.imageio.ImageIO;

public class ymageCaptcha extends ymageMatrix {

    public ymageCaptcha(int width, int height, String code) {
        super(width, height, SUBTRACTIVE_WHITE);
        this.create(code);
    }

    private void create(String code){

        Random random = new Random();

        int width = this.getWidth();
        int height = this.getHeight();
        int chars = code.length();

        int x;
        int y;
        int ub = 0;
        int widthPerChar = (int)(width/chars);
        int pixels = width * height;


        //printing code
        for(int i=0;i<chars;i++){
            y = random.nextInt((int)(height/2)) + (int)(height/4);
            setColor(((random.nextInt(128)+64)<<16) + ((random.nextInt(128)+64)<<8) + random.nextInt(128)+64);
            ymageToolPrint.print(this, widthPerChar*i+random.nextInt((int)(widthPerChar/2)) , y , 0, code.substring(i,i+1), true);
        }

        //adding some noise

        //random pixels
        ub = (int)(pixels/100);
        for(int i=0;i<ub;i++){
            setColor(((random.nextInt(128)+64)<<16) + ((random.nextInt(128)+64)<<8) + random.nextInt(128)+64);
            x = random.nextInt(width);
            y = random.nextInt(height);
            plot(x, y);
        }

        //random lines
        ub = (int)(pixels/1000);
        for(int i=0;i<ub;i++){
            setColor(((random.nextInt(128)+64)<<16) + ((random.nextInt(128)+64)<<8) + random.nextInt(128)+64);
            x = random.nextInt(width);
            y = random.nextInt(height);
            line(x, y, x + random.nextInt(5), y + random.nextInt(5));
        }

    }

    public static void main(String[] args) {
        // go into headless awt mode
        System.setProperty("java.awt.headless", "true");

        ymageCaptcha m = new ymageCaptcha(200, 70, args[1]);
        try {
            ImageIO.write(m.getImage(), "png", new java.io.File(args[0]));
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

    }

}