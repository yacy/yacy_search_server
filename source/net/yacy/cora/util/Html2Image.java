/**
 *  Html2Image
 *  Copyright 2014 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 26.11.2014 on http://yacy.net
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

package net.yacy.cora.util;

import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;

import net.yacy.kelondro.util.OS;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;

public class Html2Image {
    
    // Mac
    // to install wkhtmltopdf, download wkhtmltox-0.12.1_osx-cocoa-x86-64.pkg from http://wkhtmltopdf.org/downloads.html
    // to install imagemagick, download from http://cactuslab.com/imagemagick/assets/ImageMagick-6.8.9-9.pkg.zip
    private final static File wkhtmltopdfMac = new File("/usr/local/bin/wkhtmltopdf");
    private final static File convertMac = new File("/opt/local/bin/convert");
    
    // debian
    // to install: apt-get install wkhtmltopdf imagemagick xvfb
    private final static File wkhtmltopdfDebian = new File("/usr/bin/wkhtmltopdf"); // there is no wkhtmltoimage, use convert to create images
    private final static File convertDebian = new File("/usr/bin/convert");

    private static boolean usexvfb = false;

    public static boolean wkhtmltopdfAvailable() {
        return wkhtmltopdfMac.exists() || wkhtmltopdfDebian.exists();
    }
    
    public static boolean convertAvailable() {
        return convertMac.exists() || convertDebian.exists();
    }
    
    /**
     * write a pdf of a web page
     * @param url
     * @param proxy must be of the form http://host:port; use YaCy here as proxy which is mostly http://localhost:8090
     * @param destination
     * @return
     */
    public static boolean writeWkhtmltopdf(String url, String proxy, File destination) {
        final File wkhtmltopdf = wkhtmltopdfMac.exists() ? wkhtmltopdfMac : wkhtmltopdfDebian;
        String commandline = wkhtmltopdf.getAbsolutePath() + " --title " + url + (proxy == null ? " " : " --proxy " + proxy + " ") + "--ignore-load-errors " + url + " " + destination.getAbsolutePath();
        try {
            if (!usexvfb) {
                OS.execSynchronous(commandline);
                if (destination.exists()) return true;
                ConcurrentLog.warn("Html2Image", "failed to create pdf with command: " + commandline);
            }
            // if this fails, we should try to wrap the X server with a virtual screen using xvfb, this works on headless servers
            commandline = "xvfb-run -a -s \"-screen 0 640x480x16\" " + commandline;
            OS.execSynchronous(commandline);
            if (destination.exists()) {usexvfb = true; return true;}
            ConcurrentLog.warn("Html2Image", "failed to create pdf with command: " + commandline);
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            ConcurrentLog.warn("Html2Image", "exception while creation of pdf with command: " + commandline);
            return false;
        }
    }

    /**
     * convert a pdf to an image. proper values are i.e. width = 1024, height = 1024, density = 300, quality = 75
     * @param pdf
     * @param image
     * @param width
     * @param height
     * @param density
     * @param quality
     * @return
     */
    public static boolean pdf2image(File pdf, File image, int width, int height, int density, int quality) {
        final File convert = convertMac.exists() ? convertMac : convertDebian;
        
        try {
            // i.e. convert -density 300 -trim yacy.pdf[0] -trim -resize 1024x -crop x1024+0+0 -quality 75% yacy-convert-300.jpg
            // note: both -trim are necessary, otherwise it is trimmed only on one side. The [0] selects the first page of the pdf
            OS.execSynchronous(convert.getAbsolutePath() + " -density " + density + " -trim " + pdf.getAbsolutePath() + "[0] -trim -resize " + width + "x -crop x" + height + "+0+0 -quality " + quality + "% " + image.getAbsolutePath());
            return image.exists();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * render a html page with a JEditorPane, which can do html up to html v 3.2. No CSS supported!
     * @param url
     * @param size
     * @throws IOException 
     */
    public static void writeSwingImage(String url, Dimension size, File destination) throws IOException {
        
        // set up a pane for rendering
        final JEditorPane htmlPane = new JEditorPane();
        htmlPane.setSize(size);
        htmlPane.setEditable(false);
        final HTMLEditorKit kit = new HTMLEditorKit() {

            private static final long serialVersionUID = 1L;

            @Override
            public Document createDefaultDocument() {
                HTMLDocument doc = (HTMLDocument) super.createDefaultDocument();
                doc.setAsynchronousLoadPriority(-1);
                return doc;
            }

            @Override
            public ViewFactory getViewFactory() {
                return new HTMLFactory() {
                    @Override
                    public View create(Element elem) {
                        View view = super.create(elem);
                        if (view instanceof ImageView) {
                            ((ImageView) view).setLoadsSynchronously(true);
                        }
                        return view;
                    }
                };
            }
        };
        htmlPane.setEditorKitForContentType("text/html", kit);
        htmlPane.setContentType("text/html");
        htmlPane.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
            }
        });
        
        // load the page
        try {
            htmlPane.setPage(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // render the page
        Dimension prefSize = htmlPane.getPreferredSize();
        BufferedImage img = new BufferedImage(prefSize.width, htmlPane.getPreferredSize().height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = img.getGraphics();
        htmlPane.setSize(prefSize);
        htmlPane.paint(graphics);
        ImageIO.write(img, destination.getName().endsWith("jpg") ? "jpg" : "png", destination);
    }
    
    public static void main(String[] args) {
        try {
            Html2Image.writeSwingImage(args[0], new Dimension(1200, 2000), new File(args[1]));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
