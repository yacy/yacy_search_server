// genericImageParser.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.10.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package net.yacy.document.parser.images;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.document.parser.images.bmpParser.IMAGEMAP;
import net.yacy.kelondro.util.FileUtils;

import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.imaging.jpeg.JpegSegmentReader;
import com.drew.lang.ByteArrayReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifReader;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.iptc.IptcReader;

public class genericImageParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("png");
        SUPPORTED_EXTENSIONS.add("gif");
        SUPPORTED_EXTENSIONS.add("jpg");
        SUPPORTED_EXTENSIONS.add("jpeg");
        SUPPORTED_EXTENSIONS.add("jpe");
        SUPPORTED_EXTENSIONS.add("bmp");
        SUPPORTED_EXTENSIONS.add("tif");
        SUPPORTED_EXTENSIONS.add("tiff");
        SUPPORTED_MIME_TYPES.add("image/png");
        SUPPORTED_MIME_TYPES.add("image/gif");
        SUPPORTED_MIME_TYPES.add("image/jpeg");
        SUPPORTED_MIME_TYPES.add("image/jpg"); // this is in fact a 'wrong' mime type. We leave it here because that is a common error that occurs in the internet frequently
        SUPPORTED_MIME_TYPES.add("image/bmp");
    }

    public genericImageParser() {
        super("Generic Image Parser");
    }

    @Override
    public Document[] parse(
            final AnchorURL location,
            final String mimeType,
            final String documentCharset,
            final InputStream sourceStream) throws Parser.Failure, InterruptedException {

        ImageInfo ii = null;
        String title = null;
        String author = null;
        String keywords = null;
        List<String> descriptions = new ArrayList<String>();
        String filename = location.getFileName();
        String ext = MultiProtocolURL.getFileExtension(filename);
        double gpslat = 0;
        double gpslon = 0;        
        if (mimeType.equals("image/bmp") || ext.equals("bmp")) {
            byte[] b;
            try {
                b = FileUtils.read(sourceStream);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                throw new Parser.Failure(e.getMessage(), location);
            }
            final IMAGEMAP imap = bmpParser.parse(b);
            ii = parseJavaImage(location, imap.getImage());
        } else if (mimeType.equals("image/jpeg") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("jpe")) {
            // use the exif parser from
            // http://www.drewnoakes.com/drewnoakes.com/code/exif/
            // javadoc is at: http://www.drewnoakes.com/drewnoakes.com/code/exif/javadoc/
            // a tutorial is at: http://www.drewnoakes.com/drewnoakes.com/code/exif/sampleUsage.html
            byte[] b;
            try {
                b = FileUtils.read(sourceStream);
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                throw new Parser.Failure(e.getMessage(), location);
            }

            ii = parseJavaImage(location, new ByteArrayInputStream(b));

            JpegSegmentReader segmentReader;
            try {
                /**/
                segmentReader = new JpegSegmentReader(b);

                final byte[] exifSegment = segmentReader.readSegment(JpegSegmentReader.SEGMENT_APP1);
                final byte[] iptcSegment = segmentReader.readSegment(JpegSegmentReader.SEGMENT_APPD);
                final Metadata metadata = new Metadata();
                if (exifSegment != null) new ExifReader().extract(new ByteArrayReader(exifSegment),metadata);
                if (iptcSegment != null) new IptcReader().extract(new ByteArrayReader(iptcSegment),metadata);
                /**/
                // alternative to above: to read all included jpeg tags and metadata 
                // final Metadata metadata = JpegMetadataReader.readMetadata(new ByteArrayInputStream(b));                
                
                final Iterator<Directory> directories = metadata.getDirectories().iterator();
                final HashMap<String, String> props = new HashMap<String, String>();
                while (directories.hasNext()) {
                    final Directory directory = directories.next();
                    if (directory instanceof GpsDirectory) { // extracting GPS location                    
                        GeoLocation geoloc = ((GpsDirectory) directory).getGeoLocation();
                        if (geoloc != null) {
                            gpslat = geoloc.getLatitude();
                            gpslon = geoloc.getLongitude();
                        }
                    } else {
                        final Iterator<Tag> tags = directory.getTags().iterator();
                        while (tags.hasNext()) {
                            final Tag tag = tags.next();
                            if (!tag.getTagName().startsWith("Unknown")) { // filter out returned TagName of "Unknown tag"
                                props.put(tag.getTagName(), tag.getDescription());
                                ii.info.append(tag.getTagName() + ": " + tag.getDescription() + " .\n");
                            }
                        }
                    }
                }
                title = props.get("Image Description");
                if (title == null || title.isEmpty()) title = props.get("Headline");
                if (title == null || title.isEmpty()) title = props.get("Object Name");

                author = props.get("Artist");
                if (author == null || author.isEmpty()) author = props.get("Writer/Editor");
                if (author == null || author.isEmpty()) author = props.get("By-line");
                if (author == null || author.isEmpty()) author = props.get("Credit");
                if (author == null || author.isEmpty()) author = props.get("Make");

                keywords = props.get("Keywords");
                if (keywords == null || keywords.isEmpty()) keywords = props.get("Category");
                if (keywords == null || keywords.isEmpty()) keywords = props.get("Supplemental Category(s)");

                String description;
                description = props.get("Caption/Abstract"); if (description != null && description.length() > 0) descriptions.add("Abstract: " + description);
                description = props.get("Country/Primary Location"); if (description != null && description.length() > 0) descriptions.add("Location: " + description);
                description = props.get("Province/State"); if (description != null && description.length() > 0) descriptions.add("State: " + description);
                description = props.get("Copyright Notice"); if (description != null && description.length() > 0) descriptions.add("Copyright: " + description);
                
            } catch (final JpegProcessingException e) {
                //Log.logException(e);
                // just ignore
            }
        } else {
            ii = parseJavaImage(location, sourceStream);
        }

        final HashSet<String> languages = new HashSet<String>();
        final List<AnchorURL> anchors = new ArrayList<AnchorURL>();
        final LinkedHashMap<AnchorURL, ImageEntry> images  = new LinkedHashMap<AnchorURL, ImageEntry>();
        // add this image to the map of images
        final String infoString = ii.info.toString();
        images.put(ii.location, new ImageEntry(location, "", ii.width, ii.height, -1));

        if (title == null || title.isEmpty()) title = MultiProtocolURL.unescape(filename);

        return new Document[]{new Document(
             location,
             mimeType,
             "UTF-8",
             this,
             languages,
             keywords == null ? new String[]{} : keywords.split(keywords.indexOf(',') > 0 ? "," : " "), // keywords
             singleList(title), // title
             author == null ? "" : author, // author
             location.getHost(), // Publisher
             new String[]{}, // sections
             descriptions, // description
             gpslon, gpslat, //  location
             infoString, // content text
             anchors, // anchors
             null,
             images,
             false,
             new Date())}; // images
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }

    @Override
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    public static ImageInfo parseJavaImage(
                            final AnchorURL location,
                            final InputStream sourceStream) throws Parser.Failure {
        BufferedImage image = null;
        try {
            ImageIO.setUseCache(false); // do not write a cache to disc; keep in RAM
            image = ImageIO.read(sourceStream);
        } catch (final Throwable e) {
            //Log.logException(e);
            throw new Parser.Failure(e.getMessage(), location);
        }
        if (image == null) throw new Parser.Failure("ImageIO returned NULL", location);
        return parseJavaImage(location, image);
    }

    public static ImageInfo parseJavaImage(
                            final AnchorURL location,
                            final BufferedImage image) {
        final ImageInfo ii = new ImageInfo(location);
        ii.image = image;

        // scan the image
        ii.height = ii.image.getHeight();
        ii.width = ii.image.getWidth();
        /*
        Raster raster = image.getData();
        int[] pixel = raster.getPixel(0, 0, (int[])null);
        long[] average = new long[pixel.length];
        for (int i = 0; i < average.length; i++) average[i] = 0L;
        int pc = 0;
        for (int x = width / 4; x < 3 * width / 4; x = x + 2) {
            for (int y = height / 4; y < 3 * height / 4; y = y + 2) {
                pixel = raster.getPixel(x, y, pixel);
                for (int i = 0; i < average.length; i++) average[i] += pixel[i];
                pc++;
            }
        }
        */
        // get image properties
        String [] propNames = ii.image.getPropertyNames();
        if (propNames == null) propNames = new String[0];
        ii.info.append("\n");
        for (final String propName: propNames) {
            ii.info.append(propName).append(" = ").append(ii.image.getProperty(propName)).append(" .\n");
        }
        // append also properties that we measured
        ii.info.append("width").append(": ").append(Integer.toString(ii.width)).append(" .\n");
        ii.info.append("height").append(": ").append(Integer.toString(ii.height)).append(" .\n");

        return ii;
    }

    public static class ImageInfo {
        public AnchorURL location;
        public BufferedImage image;
        public StringBuilder info;
        public int height;
        public int width;
        public ImageInfo(final AnchorURL location) {
            this.location = location;
            this.image = null;
            this.info = new StringBuilder();
            this.height = -1;
            this.width = -1;
        }
    }



    public static void main(final String[] args) {
        final File image = new File(args[0]);
        final genericImageParser parser = new genericImageParser();
        AnchorURL uri;
        try {
            uri = new AnchorURL("http://localhost/" + image.getName());
            final Document[] document = parser.parse(uri, "image/" + MultiProtocolURL.getFileExtension(uri.getFileName()), "UTF-8", new FileInputStream(image));
            System.out.println(document[0].toString());
        } catch (final MalformedURLException e) {
            e.printStackTrace();
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        } catch (final Parser.Failure e) {
            e.printStackTrace();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }

}
