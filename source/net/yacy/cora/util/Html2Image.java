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

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;

import net.yacy.document.ImageParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.OS;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Convert html to an copy on disk-image in a other file format
 * currently (pdf and/or jpg)
 */
public class Html2Image {
    
    // Mac
    // to install wkhtmltopdf, download wkhtmltox-0.12.1_osx-cocoa-x86-64.pkg from http://wkhtmltopdf.org/downloads.html
    // to install imagemagick, download from http://cactuslab.com/imagemagick/assets/ImageMagick-6.8.9-9.pkg.zip
    // the convert command from imagemagick needs ghostscript, if not present on older macs, download a version of gs from http://pages.uoregon.edu/koch/
    private final static File wkhtmltopdfMac = new File("/usr/local/bin/wkhtmltopdf");  // sometimes this is also the path on debian
    private final static File convertMac1 = new File("/opt/local/bin/convert");
    private final static File convertMac2 = new File("/opt/ImageMagick/bin/convert");
    
    // debian
    // to install: apt-get install wkhtmltopdf imagemagick xvfb
    private final static File wkhtmltopdfDebian = new File("/usr/bin/wkhtmltopdf"); // there is no wkhtmltoimage, use convert to create images
    private final static File convertDebian = new File("/usr/bin/convert");

    private static boolean usexvfb = false;

    public static boolean wkhtmltopdfAvailable() {
        return wkhtmltopdfMac.exists() || wkhtmltopdfDebian.exists();
    }
    
    public static boolean convertAvailable() {
        return convertMac1.exists() || convertMac2.exists() || convertDebian.exists();
    }
    
    /**
     * write a pdf of a web page
     * @param url
     * @param proxy must be of the form http://host:port; use YaCy here as proxy which is mostly http://localhost:8090
     * @param destination
     * @return
     */
    public static boolean writeWkhtmltopdf(String url, String proxy, String userAgent, final String acceptLanguage, File destination) {
        boolean success = false;
        for (boolean ignoreErrors: new boolean[]{false, true}) {
            success = writeWkhtmltopdfInternal(url, proxy, destination, userAgent, acceptLanguage, ignoreErrors);
            if (success) break;
            if (!success && proxy != null) {
                ConcurrentLog.warn("Html2Image", "trying to load without proxy: " + url);
                success = writeWkhtmltopdfInternal(url, null, destination, userAgent, acceptLanguage, ignoreErrors);
                if (success) break;
            }
        }
        if (success) {
            ConcurrentLog.info("Html2Image", "wrote " + destination.toString() + " for " + url);
        } else {
            ConcurrentLog.warn("Html2Image", "could not generate snapshot for " + url);
        }
        return success;
    }
    
    private static boolean writeWkhtmltopdfInternal(final String url, final String proxy, final File destination, final String userAgent, final String acceptLanguage, final boolean ignoreErrors) {
        final File wkhtmltopdf = wkhtmltopdfMac.exists() ? wkhtmltopdfMac : wkhtmltopdfDebian;
        String commandline =
                wkhtmltopdf.getAbsolutePath() + " -q --title '" + url + "' " +
                //acceptLanguage == null ? "" : "--custom-header 'Accept-Language' '" + acceptLanguage + "' " + 
                //(userAgent == null ? "" : "--custom-header \"User-Agent\" \"" + userAgent + "\" --custom-header-propagation ") + 
                (proxy == null ? "" : "--proxy " + proxy + " ") +
                (ignoreErrors ? (OS.isMacArchitecture ? "--load-error-handling ignore " : "--ignore-load-errors ") : "") + // some versions do not have that flag and fail if attempting to use it...
                //"--footer-font-name 'Courier' --footer-font-size 9 --footer-left [webpage] --footer-right [date]/[time]([page]/[topage]) " +
                "--footer-left [webpage] --footer-right '[date]/[time]([page]/[topage])' --footer-font-size 7 " +
                url + " " + destination.getAbsolutePath();
        try {
            ConcurrentLog.info("Html2Pdf", "creating pdf from url " + url + " with command: " + commandline); 
            List<String> message;
            if (!usexvfb) {
                message = OS.execSynchronous(commandline);
                if (destination.exists()) return true;
                ConcurrentLog.warn("Html2Image", "failed to create pdf " + (proxy == null ? "" : "using proxy " + proxy) + " with command: " + commandline);
                for (String m: message) ConcurrentLog.warn("Html2Image", ">> " + m);
            }
            // if this fails, we should try to wrap the X server with a virtual screen using xvfb, this works on headless servers
            commandline = "xvfb-run -a " + commandline;
            message = OS.execSynchronous(commandline);
            if (destination.exists()) {usexvfb = true; return true;}
            ConcurrentLog.warn("Html2Pdf", "failed to create pdf " + (proxy == null ? "" : "using proxy " + proxy) + " and xvfb with command: " + commandline);
            for (String m: message) ConcurrentLog.warn("Html2Image", ">> " + m);
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            ConcurrentLog.warn("Html2Pdf", "exception while creation of pdf with command: " + commandline);
            return false;
        }
    }
    
    /**
     * convert a pdf (first page) to an image. proper values are i.e. width = 1024, height = 1024, density = 300, quality = 75
     * using internal pdf library or external command line tool on linux or mac
     * @param pdf input pdf file
     * @param image output jpg file
     * @param width
     * @param height
     * @param density (dpi)
     * @param quality
     * @return
     */
    public static boolean pdf2image(File pdf, File image, int width, int height, int density, int quality) {
        final File convert = convertMac1.exists() ? convertMac1 : convertMac2.exists() ? convertMac2 : convertDebian;

        // convert pdf to jpg using internal pdfbox capability
        if (OS.isWindows || !convert.exists()) {
            try {
                PDDocument pdoc = PDDocument.load(pdf);
                BufferedImage bi = new PDFRenderer(pdoc).renderImageWithDPI(0, density, ImageType.RGB);

                return ImageIO.write(bi, "jpg", image);

            } catch (IOException ex) { }
        }

        // convert on mac or linux using external command line utility
        try {
            // i.e. convert -density 300 -trim yacy.pdf[0] -trim -resize 1024x -crop x1024+0+0 -quality 75% yacy-convert-300.jpg
            // note: both -trim are necessary, otherwise it is trimmed only on one side. The [0] selects the first page of the pdf
            String command = convert.getAbsolutePath() + " -density " + density + " -trim " + pdf.getAbsolutePath() + "[0] -trim -resize " + width + "x -crop x" + height + "+0+0 -quality " + quality + "% " + image.getAbsolutePath();
            List<String> message = OS.execSynchronous(command);
            if (image.exists()) return true;
            ConcurrentLog.warn("Html2Image", "failed to create image with command: " + command);
            for (String m: message) ConcurrentLog.warn("Html2Image", ">> " + m);
            
            // another try for mac: use Image Events using AppleScript in osacript commands...
            // the following command overwrites a pdf with an png, so we must make a copy first
            if (!OS.isMacArchitecture) return false;
            File pngFile = new File(pdf.getAbsolutePath() + ".tmp.pdf");
            org.apache.commons.io.FileUtils.copyFile(pdf, pngFile);
            String[] commandx = {"osascript",
                    "-e", "set ImgFile to \"" + pngFile.getAbsolutePath() + "\"",
                    "-e", "tell application \"Image Events\"",
                    "-e", "set Img to open file ImgFile",
                    "-e", "save Img as PNG",
                    "-e", "end tell"};
            //ConcurrentLog.warn("Html2Image", "failed to create image with command: " + commandx);
            message = OS.execSynchronous(commandx);
            for (String m: message) ConcurrentLog.warn("Html2Image", ">> " + m);
            // now we must read and convert this file to a jpg with the target size 1024x1024
            try {
                File newPngFile = new File(pngFile.getAbsolutePath() + ".png");
                pngFile.renameTo(newPngFile);
                Image img = ImageParser.parse(pngFile.getAbsolutePath(), FileUtils.read(newPngFile));
                final Image scaled = img.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
                final MediaTracker mediaTracker = new MediaTracker(new Container());
                mediaTracker.addImage(scaled, 0);
                try {mediaTracker.waitForID(0);} catch (final InterruptedException e) {}
                // finally write the image
                final BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                bi.createGraphics().drawImage(scaled, 0, 0, width, height, null);
                ImageIO.write(bi, "jpg", image);
                newPngFile.delete();
                return image.exists();
            } catch (IOException e) {
                ConcurrentLog.logException(e);
                return false;
            }
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
