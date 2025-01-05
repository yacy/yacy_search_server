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
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.document.ImageParser;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.OS;

/**
 * Convert html to an copy on disk-image in a other file format
 * currently (pdf and/or jpg)
 */
public class Html2Image {

    // Mac
    /**
     * Path to wkhtmltopdf executable on Mac OS when installed using
     * wkhtmltox-n.n.n.macos-cocoa.pkg from https://wkhtmltopdf.org/downloads.html.
     * This can also be a path on Debian or another Gnu/Linux distribution.
     */
    private final static File wkhtmltopdfMac = new File("/usr/local/bin/wkhtmltopdf");

    // to install imagemagick, download from http://cactuslab.com/imagemagick/assets/ImageMagick-6.8.9-9.pkg.zip
    // the convert command from imagemagick needs ghostscript, if not present on older macs, download a version of gs from http://pages.uoregon.edu/koch/

    private final static File convertMac1 = new File("/opt/local/bin/convert");
    private final static File convertMac2 = new File("/opt/ImageMagick/bin/convert");

    /* Debian packages to install: apt-get install wkhtmltopdf imagemagick xvfb ghostscript
     The imagemagick policy at /etc should also be checked :
     if it contains a line such as <policy domain="coder" rights="none" pattern="PDF" /> it must be edited with rights="read" at minimum
     */
    private final static File wkhtmltopdfDebian = new File("/usr/bin/wkhtmltopdf"); // there is no wkhtmltoimage, use convert to create images
    private final static File convertDebian = new File("/usr/bin/convert");

    /**
     * Path to wkhtmltopdf executable on Windows, when installed with default
     * settings using wkhtmltox-n.n.n.msvc2015-win64.exe from
     * https://wkhtmltopdf.org/downloads.html
     */
    private static final File WKHTMLTOPDF_WINDOWS = new File("C:\\Program Files\\wkhtmltopdf\\bin\\wkhtmltopdf.exe");

    /**
     * Path to wkhtmltopdf executable on Windows, when installed with default
     * settings using wkhtmltox-n.n.n.msvc2015-win32.exe from
     * https://wkhtmltopdf.org/downloads.html
     */
    private static final File WKHTMLTOPDF_WINDOWS_X86 = new File(
            "C:\\Program Files (x86)\\wkhtmltopdf\\bin\\wkhtmltopdf.exe");

    /** Command to use when wkhtmltopdf is included in the system Path */
    private static final String WKHTMLTOPDF_COMMAND = "wkhtmltopdf";

    /** Command to use when imagemagick convert is included in the system Path */
    private static final String CONVERT_COMMAND = "convert";

    private static boolean usexvfb = false;

    /**
     * @return when the wkhtmltopdf command is detected as available in the system
     */
    public static boolean wkhtmltopdfAvailable() {
        /* Check wkhtmltopdf common installation paths and system Path */
        return wkhtmltopdfExecutable() != null || wkhtmltopdfAvailableInPath();
    }

    /**
     * @return a wkhtmltopdf executable file when one can be found, null otherwise
     */
    private static File wkhtmltopdfExecutable() {
        File executable = null;
        if(OS.isWindows) {
            if(WKHTMLTOPDF_WINDOWS.exists()) {
                executable = WKHTMLTOPDF_WINDOWS;
            } else if(WKHTMLTOPDF_WINDOWS_X86.exists()) {
                executable = WKHTMLTOPDF_WINDOWS_X86;
            }
        } else {
            if(wkhtmltopdfMac.exists()) {
                executable = wkhtmltopdfMac;
            } else if(wkhtmltopdfDebian.exists()) {
                executable = wkhtmltopdfDebian;
            }
        }
        return executable;
    }

    /**
     * @return true when wkhtmltopdf is available in system path
     */
    private static boolean wkhtmltopdfAvailableInPath() {
        boolean available = false;
        try {
            @SuppressWarnings("deprecation")
            final Process p = Runtime.getRuntime().exec(WKHTMLTOPDF_COMMAND + " -V");
            available = p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (final IOException e) {
            ConcurrentLog.fine("Html2Image", "wkhtmltopdf is not included in system path.");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve thread interrupted state
        }
        return available;
    }

    /**
     * @return a imagemagick convert executable file when one can be found, null otherwise
     */
    private static File convertExecutable() {
        File executable = null;
        if(!OS.isWindows) {
            if(convertMac1.exists()) {
                executable = convertMac1;
            } else if(convertMac2.exists()) {
                executable = convertMac2;
            } else if(convertDebian.exists()) {
                executable = convertDebian;
            }
        }
        return executable;
    }

    /**
     * @return when the imagemagick convert command is detected as available in the system
     */
    public static boolean convertAvailable() {
        /* Check convert common installation paths and system Path */
        return convertExecutable() != null || convertAvailableInPath();
    }

    /**
     * @return when imagemagick convert is available in system path
     */
    private static boolean convertAvailableInPath() {
        boolean available = false;
        if(!OS.isWindows) { // on MS Windows convert is a system tool to convert volumes from FAT to NTFS
            try {
                @SuppressWarnings("deprecation")
                final Process p = Runtime.getRuntime().exec(CONVERT_COMMAND + " -version");
                available = p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (final IOException e) {
                ConcurrentLog.fine("Html2Image", "convert is not included in system path.");
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve thread interrupted state
            }
        }
        return available;
    }

    /**
     * Run the wkhtmltopdf external tool to fetch and render to PDF a web resource.
     * wKhtmltopdf may be called multiple times with various parameters flavors in
     * case of failure.
     *
     * @param url         the URL of a web resource to fetch, render and convert to
     *                    a pdf file. Must not be null.
     * @param proxy       the eventual proxy address to use. Can be null. Must be of
     *                    the form http://host:port; use YaCy here as proxy which is
     *                    mostly http://localhost:8090
     * @param destination the destination PDF file that should be written. Must not
     *                    be null.
     * @param maxSeconds  the maximum time in seconds to wait for each wkhtmltopdf
     *                    call termination. Beyond this limit the process is killed.
     * @return true when the destination file was successfully written
     */
    public static boolean writeWkhtmltopdf(final String url, final String proxy, final String userAgent, final String acceptLanguage, final File destination, final long maxSeconds) {
        boolean success = false;
        for (final boolean ignoreErrors: new boolean[]{false, true}) {
            success = writeWkhtmltopdfInternal(url, proxy, destination, userAgent, acceptLanguage, ignoreErrors, maxSeconds);
            if (success) break;
            if (!success && proxy != null) {
                ConcurrentLog.warn("Html2Image", "trying to load without proxy: " + url);
                success = writeWkhtmltopdfInternal(url, null, destination, userAgent, acceptLanguage, ignoreErrors, maxSeconds);
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

    /**
     * Run wkhtmltopdf in a separate process to fetch and render to PDF a web
     * resource.
     *
     * @param url          the URL of a web resource to fetch, render and convert to
     *                     a pdf file. Must not be null.
     * @param proxy        the eventual proxy address to use. Can be null.
     * @param destination  the destination PDF file that should be written. Must not
     *                     be null.
     * @param userAgent    TODO: implement
     * @param acceptLanguage TODO: implement
     * @param ignoreErrors when true wkhtmltopdf is instructed to ignore load errors
     * @param maxSeconds   the maximum time in seconds to wait for the wkhtmltopdf
     *                     dedicated process termination. Beyond this limit the
     *                     process is killed.
     * @return true when the destination file was successfully written
     */
    private static boolean writeWkhtmltopdfInternal(final String url, final String proxy, final File destination,
            final String userAgent, final String acceptLanguage, final boolean ignoreErrors, final long maxSeconds) {
        final String wkhtmltopdfCmd;
        final File wkhtmltopdf = wkhtmltopdfExecutable();
        if(wkhtmltopdf != null) {
            wkhtmltopdfCmd = wkhtmltopdf.getAbsolutePath();
        } else if(wkhtmltopdfAvailableInPath()) {
            wkhtmltopdfCmd = WKHTMLTOPDF_COMMAND;
        } else {
            ConcurrentLog.warn("Html2Pdf", "Unable to locate wkhtmltopdf executable on this system!");
            return false;
        }
        String commandline =
                wkhtmltopdfCmd + " -q --title '" + url + "' " +
                        //acceptLanguage == null ? "" : "--custom-header 'Accept-Language' '" + acceptLanguage + "' " +
                        //(userAgent == null ? "" : "--custom-header \"User-Agent\" \"" + userAgent + "\" --custom-header-propagation ") +
                        (proxy == null ? "" : "--proxy " + proxy + " ") +
                        (ignoreErrors ? (OS.isMacArchitecture ? "--load-error-handling ignore " : "--ignore-load-errors ") : "") + // some versions do not have that flag and fail if attempting to use it...
                        //"--footer-font-name 'Courier' --footer-font-size 9 --footer-left [webpage] --footer-right [date]/[time]([page]/[topage]) " +
                        "--footer-left [webpage] --footer-right '[date]/[time]([page]/[topage])' --footer-font-size 7 " +
                        url + " " + destination.getAbsolutePath();
        try {
            ConcurrentLog.info("Html2Pdf", "creating pdf from url " + url + " with command: " + commandline);
            if (!usexvfb && execWkhtmlToPdf(proxy, destination, commandline, maxSeconds)) {
                return true;
            }
            // if this fails, we should try to wrap the X server with a virtual screen using xvfb, this works on headless servers
            commandline = "xvfb-run -a " + commandline;
            return execWkhtmlToPdf(proxy, destination, commandline, maxSeconds);
        } catch (final IOException e) {
            ConcurrentLog.warn("Html2Pdf", "exception while creation of pdf with command: " + commandline, e);
            return false;
        }
    }

    /**
     * Run a wkhtmltopdf commandline in a separate process.
     *
     * @param proxy       the eventual proxy address to use. Can be null.
     * @param destination the destination PDF file that should be written. Must not
     *                    be null.
     * @param commandline the wkhtmltopdf command line to execute. Must not be null.
     * @param maxSeconds  the maximum time in seconds to wait for the process
     *                    termination. Beyond this limit the process is killed.
     * @return true when the destination file was successfully written
     * @throws IOException when an unexpected error occurred
     */
    private static boolean execWkhtmlToPdf(final String proxy, final File destination, final String commandline, final long maxSeconds) throws IOException {
        @SuppressWarnings("deprecation")
        final Process p = Runtime.getRuntime().exec(commandline);

        try {
            p.waitFor(maxSeconds, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            p.destroyForcibly();
            ConcurrentLog.warn("Html2Pdf", "Interrupted creation of pdf. Killing the process started with command : " + commandline);
            Thread.currentThread().interrupt(); // Keep the thread interrupted state
            return false;
        }
        if(p.isAlive()) {
            ConcurrentLog.warn("Html2Pdf", "Creation of pdf did not terminate within " + maxSeconds + " seconds. Killing the process started with command : " + commandline);
            p.destroyForcibly();
            return false;
        }
        if (p.exitValue() == 0 && destination.exists()) {
            return true;
        }
        final List<String> messages = OS.readStreams(p);
        ConcurrentLog.warn("Html2Image", "failed to create pdf " + (proxy == null ? "" : "using proxy " + proxy) + " with command : " + commandline);
        for (final String message : messages) {
            ConcurrentLog.warn("Html2Image", ">> " + message);
        }
        return false;
    }

    /**
     * Convert a pdf (first page) to an image. Proper values are i.e. width = 1024, height = 1024, density = 300, quality = 75
     * using internal pdf library or external command line tool on linux or mac
     * @param pdf input pdf file. Must not be null.
     * @param image output image file. Must not be null, and should end with ".jpg" or ".png".
     * @param width output width in pixels
     * @param height output height in pixels
     * @param density (dpi)
     * @param quality JPEG/PNG compression level
     * @return true when the ouput image file was successfully written.
     */
    public static boolean pdf2image(final File pdf, final File image, final int width, final int height, final int density, final int quality) {
        /* Deduce the ouput image format from the file extension */
        String imageFormat = MultiProtocolURL.getFileExtension(image.getName());
        if(imageFormat.isEmpty()) {
            /* Use JPEG as a default fallback */
            imageFormat = "jpg";
        }
        String convertCmd = null;
        final File convert = convertExecutable();
        if(convert != null) {
            convertCmd = convert.getAbsolutePath();
        } else if(convertAvailableInPath()) {
            convertCmd = CONVERT_COMMAND;
        } else {
            ConcurrentLog.info("Html2Image", "Unable to locate convert executable on this system!");
        }

        // convert pdf to jpg using internal pdfbox capability
        if (convertCmd == null) {
            try (final PDDocument pdoc = Loader.loadPDF(pdf);) {

                final BufferedImage bi = new PDFRenderer(pdoc).renderImageWithDPI(0, density, ImageType.RGB);

                return ImageIO.write(bi, imageFormat, image);

            } catch (final IOException ex) {
                ConcurrentLog.warn("Html2Image", "Failed to create image with pdfbox"
                        + (ex.getMessage() != null ? " : " + ex.getMessage() : ""));
                return false;
            }
        }

        // convert using external command line utility
        try {
            // i.e. convert -density 300 -trim yacy.pdf[0] -trim -resize 1024x -crop x1024+0+0 -quality 75% yacy-convert-300.jpg
            // note: both -trim are necessary, otherwise it is trimmed only on one side. The [0] selects the first page of the pdf
            final String command = convertCmd + " -alpha remove -density " + density + " -trim " + pdf.getAbsolutePath() + "[0] -trim -resize " + width + "x -crop x" + height + "+0+0 -quality " + quality + "% " + image.getAbsolutePath();
            List<String> message = OS.execSynchronous(new String[] {
                    convertCmd,
                    "-alpha", "remove", "-density", Integer.toString(density),
                    "-trim", pdf.getAbsolutePath() + "[0]", "-trim",
                    "-resize" + Integer.toString(width) + "x",
                    "-crop x" + Integer.toString(height) + "+0+0",
                    "-quality", Integer.toString(quality) + "%",
                    image.getAbsolutePath()
            });
            if (image.exists()) return true;
            ConcurrentLog.warn("Html2Image", "failed to create image with command: " + command);
            for (final String m: message) ConcurrentLog.warn("Html2Image", ">> " + m);

            // another try for mac: use Image Events using AppleScript in osacript commands...
            // the following command overwrites a pdf with an png, so we must make a copy first
            if (!OS.isMacArchitecture) return false;
            final File pngFile = new File(pdf.getAbsolutePath() + ".tmp.pdf");
            org.apache.commons.io.FileUtils.copyFile(pdf, pngFile);
            final String[] commandx = {"osascript",
                    "-e", "set ImgFile to \"" + pngFile.getAbsolutePath() + "\"",
                    "-e", "tell application \"Image Events\"",
                    "-e", "set Img to open file ImgFile",
                    "-e", "save Img as PNG",
                    "-e", "end tell"};
            //ConcurrentLog.warn("Html2Image", "failed to create image with command: " + commandx);
            message = OS.execSynchronous(commandx);
            for (final String m: message) ConcurrentLog.warn("Html2Image", ">> " + m);
            // now we must read and convert this file to the target format with the target size 1024x1024
            try {
                final File newPngFile = new File(pngFile.getAbsolutePath() + ".png");
                pngFile.renameTo(newPngFile);
                final Image img = ImageParser.parse(pngFile.getAbsolutePath(), FileUtils.read(newPngFile));
                if(img == null) {
                    /* Should not happen. If so, ImageParser.parse() should already have logged about the error */
                    return false;
                }
                final Image scaled = img.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING);
                final MediaTracker mediaTracker = new MediaTracker(new Container());
                mediaTracker.addImage(scaled, 0);
                try {mediaTracker.waitForID(0);} catch (final InterruptedException e) {}
                // finally write the image
                final BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                bi.createGraphics().drawImage(scaled, 0, 0, width, height, null);
                ImageIO.write(bi, imageFormat, image);
                newPngFile.delete();
                return image.exists();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                return false;
            }
        } catch (final IOException e) {
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
    public static void writeSwingImage(final String url, final Dimension size, final File destination) throws IOException {

        // set up a pane for rendering
        final JEditorPane htmlPane = new JEditorPane();
        htmlPane.setSize(size);
        htmlPane.setEditable(false);
        final HTMLEditorKit kit = new HTMLEditorKit() {

            private static final long serialVersionUID = 1L;

            @Override
            public Document createDefaultDocument() {
                final HTMLDocument doc = (HTMLDocument) super.createDefaultDocument();
                doc.setAsynchronousLoadPriority(-1);
                return doc;
            }

            @Override
            public ViewFactory getViewFactory() {
                return new HTMLFactory() {
                    @Override
                    public View create(final Element elem) {
                        final View view = super.create(elem);
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
            public void propertyChange(final PropertyChangeEvent evt) {
            }
        });

        // load the page
        try {
            htmlPane.setPage(url);
        } catch (final IOException e) {
            e.printStackTrace();
        }

        // render the page
        final Dimension prefSize = htmlPane.getPreferredSize();
        final BufferedImage img = new BufferedImage(prefSize.width, htmlPane.getPreferredSize().height, BufferedImage.TYPE_INT_ARGB);
        final Graphics graphics = img.getGraphics();
        htmlPane.setSize(prefSize);
        htmlPane.paint(graphics);
        ImageIO.write(img, destination.getName().endsWith("jpg") ? "jpg" : "png", destination);
    }

    /**
     * Test PDF or image snapshot generation for a given URL.
     * @param args main arguments list:
     * <ol>
     * 	<li>Source remote URL (required)</li>
     * 	<li>Target local file path (required)</li>
     * 	<li>Snapshot generation method identifier (optional) :
     * 		<ul>
     * 			<li>"wkhtmltopdf" (default): generate a PDF snapshot using external wkhtmltopdf tool.</li>
     * 			<li>"swing" : use JRE provided Swing to generate a jpg or png image snapshot.</li>
     * 		</ul>
     * 	</li>
     * </ol>
     */
    public static void main(final String[] args) {
        final String usageMessage = "Usage : java " + Html2Image.class.getName()
                + " <url> <target-file[.pdf|.jpg|.png]> [wkhtmltopdf|swing]";
        int exitStatus = 0;
        try {
            if (args.length < 2) {
                System.out.println("Missing required parameter(s).");
                System.out.println(usageMessage);
                exitStatus = 1;
                return;
            }
            final String targetPath = args[1];
            if (args.length < 3 || "wkhtmltopdf".equals(args[2])) {
                if(Html2Image.wkhtmltopdfAvailable()) {
                    final File targetPdfFile;
                    if(targetPath.endsWith(".jpg") || targetPath.endsWith(".png")) {
                        targetPdfFile = new File(targetPath.substring(0, targetPath.length() - 4) + ".pdf");
                    } else if(targetPath.endsWith(".pdf")) {
                        targetPdfFile = new File(targetPath);
                    } else {
                        System.out.println("Unsupported output format");
                        System.out.println(usageMessage);
                        exitStatus = 1;
                        return;
                    }
                    if(Html2Image.writeWkhtmltopdf(args[0], null, ClientIdentification.yacyInternetCrawlerAgent.userAgent,
                            "en-us,en;q=0.5", targetPdfFile, 30)) {
                        if(targetPath.endsWith(".jpg") || targetPath.endsWith(".png")) {
                            if(Html2Image.pdf2image(targetPdfFile, new File(targetPath), 1024, 1024, 300, 75)) {
                                ConcurrentLog.info("Html2Image", "wrote " + targetPath + " converted from " + targetPdfFile);
                            } else {
                                exitStatus = 1;
                                return;
                            }
                        }
                    } else {
                        exitStatus = 1;
                        return;
                    }
                } else {
                    System.out.println("Unable to locate wkhtmltopdf executable on this system!");
                    exitStatus = 1;
                    return;
                }
            } else if ("swing".equals(args[2])) {
                if(targetPath.endsWith(".pdf")) {
                    System.out.println("Pdf output format is not supported with swing method.");
                    exitStatus = 1;
                    return;
                }
                if(!targetPath.endsWith(".jpg") && !targetPath.endsWith(".png")) {
                    System.out.println("Unsupported output format");
                    System.out.println(usageMessage);
                    exitStatus = 1;
                    return;
                }

                try {
                    Html2Image.writeSwingImage(args[0], new Dimension(1200, 2000), new File(targetPath));
                } catch (final IOException e) {
                    e.printStackTrace();
                    exitStatus = 1;
                    return;
                }
            } else {
                System.out.println("Unknown method : please specify either wkhtmltopdf or swing.");
                exitStatus = 1;
                return;
            }
        } finally {
            /* Shutdown running threads */
            Domains.close();
            try {
                HTTPClient.closeConnectionManager();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt(); // restore interrupted state
            }
            ConcurrentLog.shutdown();
            if(exitStatus != 0) {
                System.exit(exitStatus);
            }
        }
    }

}
