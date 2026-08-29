/**
 *  fitsParser.java
 *  Copyright 2026 by YaCy Developers
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 */

package net.yacy.document.parser.images;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCard;
import nom.tam.fits.ImageHDU;
import nom.tam.util.Cursor;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.DateDetection;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.util.FileUtils;

/**
 * Parser for astrophotography FITS (Flexible Image Transport System) files.
 * Extracts astronomical headers for full-text search and generates 8-bit previews
 * with auto-stretching for dark linear raw frames.
 */
public class fitsParser extends AbstractParser implements Parser {

    public fitsParser() {
        super("FITS Image Parser");
        SUPPORTED_EXTENSIONS.add("fits");
        SUPPORTED_EXTENSIONS.add("fit");
        SUPPORTED_EXTENSIONS.add("fts");

        SUPPORTED_MIME_TYPES.add("image/fits");
        SUPPORTED_MIME_TYPES.add("image/x-fits");
        SUPPORTED_MIME_TYPES.add("application/fits");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper,
            final int timezoneOffset,
            final InputStream source) throws Parser.Failure, InterruptedException {

        byte[] b;
        try {
            b = FileUtils.read(source);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            throw new Parser.Failure(e.getMessage(), location);
        }

        if (b.length < 80) {
            throw new Parser.Failure("File is too small to be a FITS document", location);
        }

        String headerStart = new String(b, 0, Math.min(b.length, 30), StandardCharsets.US_ASCII);
        if (!headerStart.startsWith("SIMPLE  ")) {
            throw new Parser.Failure("File does not start with FITS header SIMPLE key", location);
        }

        String title = null;
        String observer = null;
        String telescope = null;
        String instrument = null;
        String filter = null;
        String exposure = null;
        String dateObs = null;
        String ra = null;
        String dec = null;
        String bayerPat = null;
        String gain = null;
        String siteLat = null;
        String siteLong = null;

        final StringBuilder infoBuilder = new StringBuilder();
        final List<String> descriptions = new ArrayList<String>();
        final Set<String> keywordsSet = new HashSet<String>();

        int imgWidth = -1;
        int imgHeight = -1;
        BufferedImage previewImg = null;

        try {
            Fits fits = new Fits(new ByteArrayInputStream(b));
            BasicHDU<?>[] hdus = fits.read();
            if (hdus != null) {
                for (int hduIdx = 0; hduIdx < hdus.length; hduIdx++) {
                    BasicHDU<?> hdu = hdus[hduIdx];
                    Header header = hdu.getHeader();
                    if (header != null) {
                        Cursor<String, HeaderCard> iterator = header.iterator();
                        while (iterator.hasNext()) {
                            HeaderCard card = iterator.next();
                            String key = card.getKey();
                            if (key == null || key.trim().isEmpty() || key.equalsIgnoreCase("COMMENT") || key.equalsIgnoreCase("HISTORY")) {
                                continue;
                            }
                            String val = card.getValue();
                            if (val == null) val = "";
                            val = val.trim();

                            infoBuilder.append(key).append(": ").append(val).append(" .\n");

                            if (key.equalsIgnoreCase("OBJECT") && !val.isEmpty()) {
                                if (title == null) title = val;
                            } else if (key.equalsIgnoreCase("OBSERVER") && !val.isEmpty()) {
                                if (observer == null) observer = val;
                            } else if (key.equalsIgnoreCase("TELESCOP") && !val.isEmpty()) {
                                if (telescope == null) telescope = val;
                                keywordsSet.add("Telescope: " + val);
                            } else if ((key.equalsIgnoreCase("INSTRUME") || key.equalsIgnoreCase("CAMERA")) && !val.isEmpty()) {
                                if (instrument == null) instrument = val;
                                keywordsSet.add("Instrument: " + val);
                            } else if (key.equalsIgnoreCase("FILTER") && !val.isEmpty()) {
                                filter = val;
                                keywordsSet.add("Filter: " + val);
                            } else if ((key.equalsIgnoreCase("EXPOSURE") || key.equalsIgnoreCase("EXPTIME")) && !val.isEmpty()) {
                                exposure = val;
                                keywordsSet.add("Exposure: " + val + "s");
                            } else if (key.equalsIgnoreCase("DATE-OBS") && !val.isEmpty()) {
                                dateObs = val;
                            } else if ((key.equalsIgnoreCase("RA") || key.equalsIgnoreCase("OBJCTRA")) && !val.isEmpty()) {
                                ra = val;
                            } else if ((key.equalsIgnoreCase("DEC") || key.equalsIgnoreCase("OBJCTDEC")) && !val.isEmpty()) {
                                dec = val;
                            } else if (key.equalsIgnoreCase("BAYERPAT") && !val.isEmpty()) {
                                bayerPat = val;
                                keywordsSet.add("Bayer: " + val);
                            } else if (key.equalsIgnoreCase("GAIN") && !val.isEmpty()) {
                                gain = val;
                                keywordsSet.add("Gain: " + val);
                            } else if ((key.equalsIgnoreCase("SITELAT") || key.equalsIgnoreCase("LATITUDE") || key.equalsIgnoreCase("GPS_LAT") || key.equalsIgnoreCase("OBS-LAT") || key.equalsIgnoreCase("GEO-LAT") || key.equalsIgnoreCase("SITE-LAT")) && !val.isEmpty()) {
                                siteLat = val;
                            } else if ((key.equalsIgnoreCase("SITELONG") || key.equalsIgnoreCase("SITELON") || key.equalsIgnoreCase("LONGITUDE") || key.equalsIgnoreCase("GPS_LON") || key.equalsIgnoreCase("GPS_LONG") || key.equalsIgnoreCase("OBS-LONG") || key.equalsIgnoreCase("OBS-LON") || key.equalsIgnoreCase("GEO-LON") || key.equalsIgnoreCase("SITE-LON")) && !val.isEmpty()) {
                                siteLong = val;
                            }
                        }
                    }

                    if (previewImg == null && hdu instanceof ImageHDU) {
                        ImageHDU imageHDU = (ImageHDU) hdu;
                        int[] dims = imageHDU.getAxes();
                        if (dims != null && dims.length >= 2) {
                            int width = dims[dims.length - 1]; // NAXIS1
                            int height = dims[dims.length - 2]; // NAXIS2
                            if (width > 0 && height > 0) {
                                imgWidth = width;
                                imgHeight = height;
                                previewImg = renderPreviewImage(imageHDU, width, height, header);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            ConcurrentLog.logException(t);
        }

        String filename = location.getFileName();
        if (title == null || title.isEmpty()) {
            title = MultiProtocolURL.unescape(filename);
        }

        String author = observer;
        if (author == null || author.isEmpty()) author = telescope;
        if (author == null || author.isEmpty()) author = instrument;
        if (author == null) author = "";

        if (telescope != null && !telescope.isEmpty()) descriptions.add("Telescope: " + telescope);
        if (instrument != null && !instrument.isEmpty()) descriptions.add("Instrument/Camera: " + instrument);
        if (filter != null && !filter.isEmpty()) descriptions.add("Filter: " + filter);
        if (exposure != null && !exposure.isEmpty()) descriptions.add("Exposure: " + exposure + " s");
        if (ra != null && dec != null) descriptions.add("Coordinates: RA " + ra + ", DEC " + dec);

        double latVal = parseCoordinate(siteLat);
        double lonVal = parseCoordinate(siteLong);
        if (siteLat != null && siteLong != null) {
            descriptions.add("GPS Location: Lat " + siteLat + ", Lon " + siteLong);
            keywordsSet.add("GPS: " + siteLat + " " + siteLong);
        }

        Date pubDate = null;
        if (dateObs != null) {
            pubDate = DateDetection.parseLine(dateObs, 0);
        }
        if (pubDate == null) {
            pubDate = new Date();
        }

        final HashSet<String> languages = new HashSet<String>();
        final LinkedHashMap<DigestURL, ImageEntry> images = new LinkedHashMap<>();

        if (imgWidth <= 0 && previewImg != null) {
            imgWidth = previewImg.getWidth();
            imgHeight = previewImg.getHeight();
        }

        images.put(location, new ImageEntry(location, "", imgWidth, imgHeight, -1));

        String[] keywordsArray = keywordsSet.toArray(new String[0]);

        return new Document[]{new Document(
                location,
                mimeType,
                StandardCharsets.UTF_8.name(),
                this,
                languages,
                keywordsArray,
                singleList(title),
                author,
                location.getHost(),
                new String[]{},
                descriptions,
                lonVal, latVal,
                infoBuilder.toString(),
                null,
                null,
                images,
                false,
                pubDate)};
    }

    /**
     * Parses latitude or longitude coordinate strings into decimal degrees.
     * Supports decimal formats ("50.1234", "-8.5432") and sexagesimal formats ("+50 07 24.2", "50:07:24", "-08 32 15").
     */
    private double parseCoordinate(String val) {
        if (val == null || val.trim().isEmpty()) return 0.0;
        val = val.trim().replaceAll("['\"]", "");
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            String[] parts = val.split("[:\\s]+");
            if (parts.length >= 2) {
                try {
                    double deg = Double.parseDouble(parts[0]);
                    double min = Double.parseDouble(parts[1]);
                    double sec = parts.length >= 3 ? Double.parseDouble(parts[2]) : 0.0;
                    double sign = (deg < 0 || parts[0].startsWith("-")) ? -1.0 : 1.0;
                    return sign * (Math.abs(deg) + min / 60.0 + sec / 3600.0);
                } catch (NumberFormatException ex) {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }

    /**
     * Render 8-bit preview image from FITS ImageHDU using smart auto-stretching.
     */
    private BufferedImage renderPreviewImage(ImageHDU imageHDU, int width, int height, Header header) {
        try {
            Object rawData = imageHDU.getData().getData();
            if (rawData == null) return null;

            double bscale = 1.0;
            double bzero = 0.0;
            if (header != null) {
                bscale = header.getDoubleValue("BSCALE", 1.0);
                bzero = header.getDoubleValue("BZERO", 0.0);
            }

            double[] pixelValues = extractFlattenedPixels(rawData, width * height, bscale, bzero);
            if (pixelValues == null || pixelValues.length == 0) return null;

            boolean isPreStretched = checkIfPreStretched(pixelValues, header);

            int totalPixels = pixelValues.length;
            double[] displayPixels = new double[totalPixels];

            if (isPreStretched) {
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (double val : pixelValues) {
                    if (!Double.isNaN(val)) {
                        if (val < min) min = val;
                        if (val > max) max = val;
                    }
                }
                double range = (max - min > 1e-6) ? (max - min) : 1.0;
                for (int i = 0; i < totalPixels; i++) {
                    double norm = (pixelValues[i] - min) / range;
                    displayPixels[i] = Math.min(255.0, Math.max(0.0, norm * 255.0));
                }
            } else {
                double[] sorted = pixelValues.clone();
                Arrays.sort(sorted);

                int lowIdx = (int) (totalPixels * 0.005);
                int highIdx = (int) (totalPixels * 0.995);
                if (highIdx >= totalPixels) highIdx = totalPixels - 1;

                double pLow = sorted[lowIdx];
                double pHigh = sorted[highIdx];
                if (pHigh <= pLow) {
                    pHigh = sorted[totalPixels - 1];
                    pLow = sorted[0];
                }
                double range = (pHigh - pLow > 1e-6) ? (pHigh - pLow) : 1.0;

                for (int i = 0; i < totalPixels; i++) {
                    double v = pixelValues[i];
                    if (Double.isNaN(v)) v = pLow;
                    if (v < pLow) v = pLow;
                    if (v > pHigh) v = pHigh;

                    double norm = (v - pLow) / range;
                    double stretched = Math.pow(norm, 0.45);
                    displayPixels[i] = Math.min(255.0, Math.max(0.0, stretched * 255.0));
                }
            }

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            int idx = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (idx < displayPixels.length) {
                        int gray = (int) displayPixels[idx++];
                        int rgb = (gray << 16) | (gray << 8) | gray;
                        image.setRGB(x, y, rgb);
                    }
                }
            }
            return image;
        } catch (Throwable t) {
            ConcurrentLog.logException(t);
            return null;
        }
    }

    /**
     * Check if the image array appears to be non-linear / pre-stretched.
     */
    private boolean checkIfPreStretched(double[] pixels, Header header) {
        if (header != null) {
            String stretchStr = header.getStringValue("STRETCH");
            if (stretchStr != null && !stretchStr.isEmpty()) return true;
        }
        if (pixels == null || pixels.length < 100) return false;

        int step = Math.max(1, pixels.length / 1000);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;
        int count = 0;

        for (int i = 0; i < pixels.length; i += step) {
            double v = pixels[i];
            if (!Double.isNaN(v)) {
                if (v < min) min = v;
                if (v > max) max = v;
                sum += v;
                count++;
            }
        }
        if (count == 0) return false;
        double mean = sum / count;

        if (min >= 0.0 && max <= 1.0 && mean > 0.15 && mean < 0.85) return true;
        if (min >= 0.0 && max <= 255.0 && mean > 40.0 && mean < 200.0) return true;

        return false;
    }

    private double[] extractFlattenedPixels(Object rawData, int expectedLength, double bscale, double bzero) {
        double[] result = new double[expectedLength];
        int idx = 0;
        idx = flattenArray(rawData, result, idx, bscale, bzero);
        return result;
    }

    private int flattenArray(Object obj, double[] dest, int idx, double bscale, double bzero) {
        if (obj == null || idx >= dest.length) return idx;

        if (obj instanceof short[][]) {
            short[][] arr = (short[][]) obj;
            for (short[] row : arr) {
                for (short val : row) {
                    if (idx < dest.length) dest[idx++] = (val & 0xFFFF) * bscale + bzero;
                }
            }
        } else if (obj instanceof int[][]) {
            int[][] arr = (int[][]) obj;
            for (int[] row : arr) {
                for (int val : row) {
                    if (idx < dest.length) dest[idx++] = val * bscale + bzero;
                }
            }
        } else if (obj instanceof float[][]) {
            float[][] arr = (float[][]) obj;
            for (float[] row : arr) {
                for (float val : row) {
                    if (idx < dest.length) dest[idx++] = val * bscale + bzero;
                }
            }
        } else if (obj instanceof double[][]) {
            double[][] arr = (double[][]) obj;
            for (double[] row : arr) {
                for (double val : row) {
                    if (idx < dest.length) dest[idx++] = val * bscale + bzero;
                }
            }
        } else if (obj instanceof byte[][]) {
            byte[][] arr = (byte[][]) obj;
            for (byte[] row : arr) {
                for (byte val : row) {
                    if (idx < dest.length) dest[idx++] = (val & 0xFF) * bscale + bzero;
                }
            }
        } else if (obj instanceof Object[]) {
            Object[] arr = (Object[]) obj;
            for (Object sub : arr) {
                idx = flattenArray(sub, dest, idx, bscale, bzero);
            }
        }
        return idx;
    }

    public static void main(final String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java net.yacy.document.parser.images.fitsParser <path-to-fits>");
            return;
        }
        final File image = new File(args[0]);
        final fitsParser parser = new fitsParser();
        AnchorURL uri;
        FileInputStream inStream = null;
        try {
            uri = new AnchorURL("http://localhost/" + image.getName());
            inStream = new FileInputStream(image);
            final Document[] document = parser.parse(uri, "image/fits", StandardCharsets.UTF_8.name(), new VocabularyScraper(), 0, inStream);
            if (document != null && document.length > 0) {
                System.out.println("Parsed Document Title: " + document[0].dc_title());
                System.out.println("Parsed Author: " + document[0].dc_creator());
                System.out.println("Parsed Keywords: " + document[0].dc_subject());
                System.out.println("Parsed Descriptions: " + Arrays.toString(document[0].dc_description()));
                System.out.println("Content Snippet: " + document[0].getTextString().substring(0, Math.min(200, document[0].getTextString().length())));
            }
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            ConcurrentLog.shutdown();
        }
    }
}
