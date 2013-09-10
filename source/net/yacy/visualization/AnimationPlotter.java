/**
 *  AnimationPlotter
 *  Copyright 2013 by Michael Christen
 *  First released 9.9.2010 at http://yacy.net
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

package net.yacy.visualization;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

public class AnimationPlotter {
    
    public static class Frame {
        BufferedImage image;
        int delayMillis;
        public Frame(BufferedImage image, int delayMillis) {
            this.image = image;
            this.delayMillis = delayMillis;
        }
    }
    
    private final List<Frame> frames;
    
    
    public AnimationPlotter() {
        this.frames = new ArrayList<Frame>();
    }
    
    public void addFrame(final BufferedImage image, final int delayMillis) {
        this.frames.add(new Frame(image, delayMillis));
    }
    

    public void save(final File path, final String filestub, final String type) throws IOException {
        assert path.isDirectory();
        for (int i = 0; i < this.frames.size(); i++) {
            Frame frame = this.frames.get(i);
            File file = new File(path, filestub + "_" + intformat(i) + '.' + type);
            final FileOutputStream fos = new FileOutputStream(file);
            ImageIO.write(frame.image, type, fos);
            fos.close();
        }
    }
    
    private String intformat(final int i) {
        String n = Integer.toString(i);
        while (n.length() < 6) n = '0' + n;
        return n;
    }

    /**
     * show the images as stream of JFrame on desktop
     */
    public void show() {
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
        JLabel label = null;
        while (true) {
            for (int i = 0; i < this.frames.size(); i++) {
                Frame frame = this.frames.get(i);
                if (label == null) {
                    label = new JLabel(new ImageIcon(frame.image));
                    f.getContentPane().add(label);
                    f.pack();
                } else {
                    label.getGraphics().drawImage(frame.image,0,0, label);
                }
                try {Thread.sleep(frame.delayMillis);} catch (InterruptedException e) {}
            }
        }
    }
}
