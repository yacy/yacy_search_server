// ymageCaptcha.java
// -----------------------
// part of YaCy
// (C) by Marc Nause
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

package net.yacy.visualization;

import java.util.Random;

import javax.imageio.ImageIO;

import net.yacy.cora.util.ConcurrentLog;


public class Captcha extends RasterPlotter {

    public Captcha(final int width, final int height, final DrawMode displayMode, final String code) {
        super(width, height, displayMode, "FFFFFF");
        this.create(code);
    }

    private void create(final String code){

        final Random random = new Random();
        final int chars = code.length();

        int x;
        int y;
        int ub = 0;
        final int widthPerChar = width/chars;
        final int pixels = width * height;


        //printing code
        for(int i=0;i<chars;i++){
            y = random.nextInt(height/2) + (height/4);
            setColor(((random.nextInt(128)+64)<<16) + ((random.nextInt(128)+64)<<8) + random.nextInt(128)+64);
            PrintTool.print(this, widthPerChar*i+random.nextInt(widthPerChar/2) , y , 0, code.substring(i,i+1), -1);
        }

        //adding some noise

        //random pixels
        ub = pixels/100;
        for(int i=0;i<ub;i++){
            setColor(((random.nextInt(128)+64)<<16) + ((random.nextInt(128)+64)<<8) + random.nextInt(128)+64);
            x = random.nextInt(width);
            y = random.nextInt(height);
            plot(x, y, 100);
        }

        //random lines
        ub = pixels/1000;
        for(int i=0;i<ub;i++){
            setColor(((random.nextInt(128)+64)<<16) + ((random.nextInt(128)+64)<<8) + random.nextInt(128)+64);
            x = random.nextInt(width);
            y = random.nextInt(height);
            line(x, y, x + random.nextInt(5), y + random.nextInt(5), 100);
        }

    }

    public static void main(final String[] args) {
        // go into headless awt mode
        System.setProperty("java.awt.headless", "true");

        final Captcha m = new Captcha(200, 70, RasterPlotter.DrawMode.MODE_REPLACE, args[1]);
        try {
            ImageIO.write(m.getImage(), "png", new java.io.File(args[0]));
        } catch (final java.io.IOException e) {
            ConcurrentLog.logException(e);
        }

    }

}