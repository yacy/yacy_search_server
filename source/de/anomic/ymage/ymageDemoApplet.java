package de.anomic.ymage;

import java.applet.Applet;
import java.awt.Dimension;
import java.awt.Graphics;


public class ymageDemoApplet extends Applet implements Runnable {
    // can be run in eclipse with
    // Run -> Run As -> Java Applet
    
    // see http://www.javaworld.com/javaworld/jw-03-1996/jw-03-animation.html?page=3
    
    private static final long serialVersionUID = -8230253094143014406L;

    int delay;
    Thread animator;
    Dimension offDimension;
    ymageMatrix offGraphics;

    public void init() {
        final String str = getParameter("fps");
        final int fps = (str != null) ? Integer.parseInt(str) : 10;
        delay = (fps > 0) ? (1000 / fps) : 100;
    }

    public void start() {
        animator = new Thread(this);
        animator.start();
    }

    public void run() {
        while (Thread.currentThread() == animator) {
            final long time = System.currentTimeMillis();
            repaint();         
            try {
                Thread.sleep(delay - System.currentTimeMillis() + time);
            } catch (final InterruptedException e) {
                break;
            }
        }
    }

    public void stop() {
        animator = null;
    }

    public void update(final Graphics g) {
        final Dimension d = getSize();
        offGraphics = new ymageMatrix(d.width, d.height, ymageMatrix.MODE_REPLACE, "FFFFFF");
        paintFrame(offGraphics);
        g.drawImage(offGraphics.getImage(), 0, 0, null);
    }
    
    public void paint(final Graphics g) {
        if (offGraphics != null) {
            g.drawImage(offGraphics.getImage(), 0, 0, null);
        }
    }

    public void paintFrame(final ymageMatrix m) {
        ymageMatrix.demoPaint(m);
        final int y = (int) (System.currentTimeMillis() / 10 % 300);
        m.setColor(ymageMatrix.GREY);
        ymageToolPrint.print(m, 0, y, 0, "Hello World", -1);
    }
    
}
