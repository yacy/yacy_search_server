package net.yacy.peers.graphics;

import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

import net.yacy.visualization.PrintTool;
import net.yacy.visualization.RasterPlotter;

/**
 * Creates banner which displays data about peer and network.
 */
public final class Banner {

    private static final String QUERIES_HOUR = " QUERIES/HOUR";
    private static final String PAGES_MINUTE = " PAGES/MINUTE";
    private static final String QUERIES = "QUERIES:";
    private static final String WORDS = "WORDS:";
    private static final String LINKS = "LINKS:";
    private static final String NETWORK = "NETWORK:";
    private static final String SPEED = "SPEED:";
    private static final String TYPE = "TYPE:";
    private static final String DHT_WORDS = "DHT WORDS:";
    private static final String DOCUMENTS = "DOCUMENTS:";
    private static final String PEER_NAME = "PEER:";
    private static final int EXPR_LEN = 26;

    /** Private constructor to avoid instantiation of utility class. */
    private Banner() { }

    /** Always use dot as decimal separator since
     * banner text is always in English.
     */
    private static final DecimalFormatSymbols DFS =
                    new DecimalFormatSymbols();
    static {
        DFS.setDecimalSeparator('.');
        DFS.setGroupingSeparator(',');
    }

    private static final NumberFormat QPM_FORMAT = new DecimalFormat("0.00",  DFS);
    private static final NumberFormat LARGE_NUMBER_FORMAT = new DecimalFormat("#,###", DFS);

    private static RasterPlotter bannerPicture = null;
    private static BufferedImage logo = null;
    private static long bannerPictureDate = 0;

    /**
     * Creates new banner image if max age has been reached, else returns cached version.
     * @param data data to display
     * @param maxAge age in ms since 01.01.1970
     * @return banner image
     */
    public static RasterPlotter getBannerPicture( final BannerData data, final long maxAge) {

        if (bannerPicture == null || bannerOutdated(maxAge)) {
            drawBannerPicture(data, logo);
        }
        return bannerPicture;
    }

    /**
     * Creates new banner image if max age has been reached, else returns cached version.
     * @param data data to display
     * @param maxAge age in ms since 01.01.1970
     * @param newLogo logo to display
     * @return banner image
     */
    public static RasterPlotter getBannerPicture(
                    final BannerData data,
                    final long maxAge,
                    final BufferedImage newLogo) {
        if (bannerPicture == null || bannerOutdated(maxAge)) {
            drawBannerPicture(data, newLogo);
        }
        return bannerPicture;
    }

    private static boolean bannerOutdated(final long maxAge) {
        return (System.currentTimeMillis() - bannerPictureDate) > maxAge;
    }

    private static void drawBannerPicture(final BannerData data, final BufferedImage newLogo) {

        logo = newLogo;
        bannerPicture =
                        new RasterPlotter(
                                        data.getWidth(),
                                        data.getHeight(),
                                        RasterPlotter.DrawMode.MODE_REPLACE,
                                        data.getBgcolor());

        // draw description
        bannerPicture.setColor(data.getTextcolor());
        PrintTool.print(bannerPicture, 100, 12, 0, PEER_NAME + addBlanks(data.getName(), PEER_NAME.length()), -1);
        PrintTool.print(bannerPicture, 100, 22, 0, DOCUMENTS + addBlanksAndDots(data.getLinks(), DOCUMENTS.length()), -1);
        PrintTool.print(bannerPicture, 100, 32, 0, DHT_WORDS + addBlanksAndDots(data.getWords(), DHT_WORDS.length()), -1);
        PrintTool.print(bannerPicture, 100, 42, 0, TYPE + addBlanks(data.getType(), TYPE.length()), -1);
        PrintTool.print(bannerPicture, 100, 52, 0, SPEED + addBlanks(data.getPpm() + PAGES_MINUTE, SPEED.length()), -1);

        PrintTool.print(bannerPicture, 290, 12, 0, NETWORK + addBlanks(data.getNetwork() + " [" + data.getPeers() + "]", NETWORK.length()), -1);
        PrintTool.print(bannerPicture, 290, 22, 0, LINKS + addBlanksAndDots(data.getNlinks(), LINKS.length()), -1);
        PrintTool.print(bannerPicture, 290, 32, 0, WORDS + addBlanksAndDots(data.getNwords(), WORDS.length()), -1);
        PrintTool.print(bannerPicture, 290, 42, 0, QUERIES + addBlanks(formatQpm(data.getNqph()) + QUERIES_HOUR, QUERIES.length()), -1);
        PrintTool.print(bannerPicture, 290, 52, 0, SPEED + addBlanks(data.getNppm() + PAGES_MINUTE, SPEED.length()), -1);

        final int height = data.getHeight();
        final int width = data.getWidth();

        if (logo != null) {
            final int x = (100 / 2 - logo.getWidth() / 2);
            final int y = (height / 2 - logo.getHeight() / 2);
            bannerPicture.insertBitmap(logo, x, y, 0, 0, RasterPlotter.FilterMode.FILTER_ANTIALIASING);
        }

        bannerPicture.setColor(data.getBordercolor());
        bannerPicture.line(0, 0, 0, height - 1, 100);
        bannerPicture.line(0, 0, width - 1, 0, 100);
        bannerPicture.line(width - 1, 0, width - 1, height - 1, 100);
        bannerPicture.line(0, height - 1, width - 1, height - 1, 100);

        // set timestamp
        bannerPictureDate = System.currentTimeMillis();
    }

    private static String addBlanksAndDots(final long input, final int length) {
        return addBlanks(LARGE_NUMBER_FORMAT.format(input), length);
    }

    private static String addBlanks(final String word, final int offset) {
        return String.format(String.format("%%%ds", (EXPR_LEN - offset)), word);
    }

    private static String formatQpm(final double qpm) {
        return (qpm < 10) ? QPM_FORMAT.format(qpm) : Long.toString(Math.round(qpm));
    }

    /**
     * Tells if a logo has been set.
     * @return true if logo has been set, else false
     */
    public static boolean logoIsLoaded() {
        return logo != null;
    }

}
