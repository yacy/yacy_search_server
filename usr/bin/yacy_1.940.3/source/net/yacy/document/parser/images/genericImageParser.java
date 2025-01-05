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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.GpsDirectory;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.util.FileUtils;

/**
 * Parser for images, bmp and jpeg and all supported by the Java Image I/O API
 * by default java ImageIO supports bmp, gif, jpg, jpeg, png, wbmp (tif if jai-imageio is in classpath/registered)
 * http://download.java.net/media/jai-imageio/javadoc/1.1/overview-summary.html
 */
public class genericImageParser extends AbstractParser implements Parser {

    public genericImageParser() {
        super("Generic Image Parser");

        SUPPORTED_EXTENSIONS.add("jpe"); // not listed in ImageIO extension but sometimes uses for jpeg
        SUPPORTED_MIME_TYPES.add("image/jpg"); // this is in fact a 'wrong' mime type. We leave it here because that is a common error that occurs in the internet frequently

        try {
            SUPPORTED_EXTENSIONS.addAll(Arrays.asList(ImageIO.getReaderFileSuffixes()));
            SUPPORTED_MIME_TYPES.addAll(Arrays.asList(ImageIO.getReaderMIMETypes()));
        } catch (NoSuchMethodError e) {
            ConcurrentLog.logException(e);
        }
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source) throws Parser.Failure, InterruptedException {

        ImageInfo ii = null;
        String title = null;
        String author = null;
        String keywords = null;
        List<String> descriptions = new ArrayList<String>();
        String filename = location.getFileName();
        String ext = MultiProtocolURL.getFileExtension(filename);
        double gpslat = 0;
        double gpslon = 0;        
        if (mimeType.equals("image/jpeg") || ext.equals("jpg") || ext.equals("jpeg") || ext.equals("jpe")) {
            // use the exif parser from
            // http://www.drewnoakes.com/drewnoakes.com/code/exif/
            // javadoc is at: http://www.drewnoakes.com/drewnoakes.com/code/exif/javadoc/
            // a tutorial is at: http://www.drewnoakes.com/drewnoakes.com/code/exif/sampleUsage.html
            byte[] b;
            try {
                b = FileUtils.read(source);
                // check jpeg file signature (magic number FF D8 FF)
                if (b.length < 3
                        || (b[0] != (byte) 0xFF) // cast to signed byte (-1)
                        || (b[1] != (byte) 0xD8) //cast to signed byte (-40)
                        || (b[2] != (byte) 0xFF)) {
                    throw new Parser.Failure("File has no jpeg signature", location);
                }
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                throw new Parser.Failure(e.getMessage(), location);
            }

            ii = parseJavaImage(location, new ByteArrayInputStream(b));

            try {
                final Metadata metadata = JpegMetadataReader.readMetadata(new ByteArrayInputStream(b));                   
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
                
            } catch (final Throwable e) {
                //Log.logException(e);
                // just ignore
            }
        } else {
            ii = parseJavaImage(location, source);
        }

        final HashSet<String> languages = new HashSet<String>();
        final LinkedHashMap<DigestURL, ImageEntry> images  = new LinkedHashMap<>();
        // add this image to the map of images
        final String infoString = ii.info.toString();
        images.put(ii.location, new ImageEntry(ii.location, "", ii.width, ii.height, -1));

        if (title == null || title.isEmpty()) title = MultiProtocolURL.unescape(filename);

        return new Document[]{new Document(
             location,
             mimeType,
             StandardCharsets.UTF_8.name(),
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
             null, // anchors
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

    private ImageInfo parseJavaImage(
                            final DigestURL location,
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

    private ImageInfo parseJavaImage(
                            final DigestURL location,
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

    private class ImageInfo {
        public DigestURL location;
        public BufferedImage image;
        public StringBuilder info;
        public int height;
        public int width;
        public ImageInfo(final DigestURL location) {
            this.location = location;
            this.image = null;
            this.info = new StringBuilder();
            this.height = -1;
            this.width = -1;
        }
    }



    public static void main(final String[] args) {
        // list support file extension by java ImageIO
        String names[] = ImageIO.getReaderFileSuffixes();
        System.out.print("supported file extension:");
        for (int i = 0; i < names.length; ++i) {
            System.out.print(" " + names[i]);
        }
        System.out.println();

        // list supported mime types of java ImageIO
        String mime[] = ImageIO.getReaderMIMETypes();
        System.out.print("supported mime types:    ");
        for (int i = 0; i < mime.length; ++i) {
            System.out.print(" " + mime[i]);
        }
        System.out.println();

        final File image = new File(args[0]);
        final genericImageParser parser = new genericImageParser();
        AnchorURL uri;
        FileInputStream inStream = null;
        try {
            uri = new AnchorURL("http://localhost/" + image.getName());
            inStream = new FileInputStream(image);
            final Document[] document = parser.parse(uri, "image/" + MultiProtocolURL.getFileExtension(uri.getFileName()), StandardCharsets.UTF_8.name(), new VocabularyScraper(), 0, inStream);
            System.out.println(document[0].toString());
        } catch (final MalformedURLException e) {
            e.printStackTrace();
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        } catch (final Parser.Failure e) {
            e.printStackTrace();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        } finally {
        	if(inStream != null) {
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
