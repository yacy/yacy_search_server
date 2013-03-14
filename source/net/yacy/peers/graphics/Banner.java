package net.yacy.peers.graphics;

import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import net.yacy.visualization.PrintTool;
import net.yacy.visualization.RasterPlotter;

/**
 * Creates banner which displays dtat about peer and network.
 */
public final class Banner {

    /** Private constructor to avoid instantiation of utility class. */
    private Banner() { }

    /** Always use dot as decimal separator since
     * banner text is always in English.
     */
    private static final DecimalFormatSymbols DFS =
            new DecimalFormatSymbols();
    static {
        DFS.setDecimalSeparator('.');
    }

    private static RasterPlotter bannerPicture = null;
    private static BufferedImage logo = null;
    private static long bannerPictureDate = 0;

    /**
     * Creates new banner image if max age has been reached,
     * else returns cached version.
     * @param data data to display
     * @param maxAge age in ms since 01.01.1970
     * @return banner image
     */
    public static RasterPlotter getBannerPicture(
            final BannerData data, final long maxAge) {
        if ((bannerPicture == null)
                || ((System.currentTimeMillis() - bannerPictureDate) > maxAge)) {
            drawBannerPicture(data, logo);
        }
        return bannerPicture;
    }

    /**
     * Creates new banner image if max age has been reached,
     * else returns cached version.
     * @param data data to display
     * @param maxAge age in ms since 01.01.1970
     * @param newLogo logo to display
     * @return banner image
     */
    public static RasterPlotter getBannerPicture(
            final BannerData data,
            final long maxAge,
            final BufferedImage newLogo) {
        if ((bannerPicture == null)
                || ((System.currentTimeMillis() - bannerPictureDate) > maxAge)) {
            drawBannerPicture(data, newLogo);
        }
        return bannerPicture;
    }

    private static void drawBannerPicture(
            final BannerData data, final BufferedImage newLogo) {

        final int exprlength = 16;
        logo = newLogo;
        bannerPicture =
                new RasterPlotter(
                        data.getWidth(),
                        data.getHeight(),
                        RasterPlotter.DrawMode.MODE_REPLACE,
                        data.getBgcolor());

        final int height = data.getHeight();
        final int width = data.getWidth();

        // draw description
        bannerPicture.setColor(Long.parseLong(data.getTextcolor(), 16));
        PrintTool.print(bannerPicture, 100, 12, 0, "PEER NAME:" + addTrailingBlanks(data.getName(), exprlength), -1);
        PrintTool.print(bannerPicture, 100, 22, 0, "DOCUMENTS:" + addBlanksAndDots(data.getLinks(), exprlength), -1);
        PrintTool.print(bannerPicture, 100, 32, 0, "DHT WORDS:" + addBlanksAndDots(data.getWords(), exprlength), -1);
        PrintTool.print(bannerPicture, 100, 42, 0, "TYPE:     " + addTrailingBlanks(data.getType(), exprlength), -1);
        PrintTool.print(bannerPicture, 100, 52, 0, "SPEED:    " + addTrailingBlanks(data.getPpm() + " PAGES/MINUTE", exprlength), -1);

        PrintTool.print(bannerPicture, 290, 12, 0, "NETWORK:  " + addTrailingBlanks(data.getNetwork() + " [" + data.getPeers() + "]", exprlength), -1);
        PrintTool.print(bannerPicture, 290, 22, 0, "LINKS:    " + addBlanksAndDots(data.getNlinks(), exprlength), -1);
        PrintTool.print(bannerPicture, 290, 32, 0, "WORDS:    " + addBlanksAndDots(data.getNwords(), exprlength), -1);
        PrintTool.print(bannerPicture, 290, 42, 0, "QUERIES:  " + addTrailingBlanks(formatQpm(data.getNqph()) + " QUERIES/HOUR", exprlength), -1);
        PrintTool.print(bannerPicture, 290, 52, 0, "SPEED:    " + addTrailingBlanks(data.getNppm() + " PAGES/MINUTE", exprlength), -1);

        if (logo != null) {
            final int x = (100 / 2 - logo.getWidth() / 2);
            final int y = (height / 2 - logo.getHeight() / 2);
            bannerPicture.insertBitmap(logo, x, y, 0, 0, RasterPlotter.FilterMode.FILTER_ANTIALIASING);
        }

        final String bordercolor = data.getBordercolor();
        if (bordercolor != null && !bordercolor.isEmpty()) {
            bannerPicture.setColor(Long.parseLong(bordercolor, 16));
            bannerPicture.line(0, 0, 0, height - 1, 100);
            bannerPicture.line(0, 0, width - 1, 0, 100);
            bannerPicture.line(width - 1, 0, width - 1, height - 1, 100);
            bannerPicture.line(0, height - 1, width - 1, height - 1, 100);
        }

        // set timestamp
         bannerPictureDate = System.currentTimeMillis();
    }

    private static String addBlanksAndDots(final long input, final int length) {
        return addBlanksAndDots(Long.toString(input), length);
    }

    private static String addBlanksAndDots(String input, final int length) {
        input = addDots(input);
        input = addTrailingBlanks(input,length);
        return input;
    }

    private static String addDots(String word) {
        String tmp = "";
        int len = word.length();
        if (len > 3) {
            while (len > 3) {
                if (tmp.equals("")) {
                    tmp = word.substring(len - 3,len);
                } else {
                    tmp = word.substring(len - 3,len) + "." + tmp;
                }
                word = word.substring(0,len - 3);
                len = word.length();
            }
            word = word + "." + tmp;
        }
        return word;
    }

    private static String addTrailingBlanks(String word, int length) {
        if (length > word.length()) {
            String blanks = "";
            length = length - word.length();
            int i = 0;
            while (i++ < length) {
                blanks += " ";
            }
            word = blanks + word;
        }
        return word;
    }

    private static String formatQpm(final double qpm) {
        final String ret;

        if (qpm < 10) {
            ret = new DecimalFormat("0.00",  DFS).format(qpm);
        } else {
            ret = Long.toString(Math.round(qpm));
        }

        return ret;
    }

    /**
     * Tells if a logo has been set.
     * @return true if logo has been set, else false
     */
    public static boolean logoIsLoaded() {
        if (logo == null) {
            return false;
        }
        return true;
    }

}
