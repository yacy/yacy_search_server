// pngParser.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 16.10.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-10-11 02:12:19 +0200 (So, 11 Okt 2009) $
// $LastChangedRevision: 6398 $
// $LastChangedBy: orbiter $
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
import java.awt.image.Raster;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.document.parser.html.ImageEntry;
import net.yacy.kelondro.data.meta.DigestURI;

public class genericImageParser extends AbstractParser implements Idiom {

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
        SUPPORTED_MIME_TYPES.add("image/png");
        SUPPORTED_MIME_TYPES.add("image/gif");
        SUPPORTED_MIME_TYPES.add("image/jpg");
    }
    
    public genericImageParser() {
        super("Generic Image Parser"); 
    }
    
    @Override
    public Document parse(
            final DigestURI location, 
            final String mimeType, 
            final String documentCharset, 
            final InputStream sourceStream) throws ParserException, InterruptedException {
        BufferedImage image = null;
        try {
            image = ImageIO.read(sourceStream);
        } catch (final EOFException e) {
            throw new ParserException(e.getMessage(), location);
        } catch (final IOException e) {
            throw new ParserException(e.getMessage(), location);
        }
        
        /*
        // scan the image
        int height = image.getHeight();
        int width = image.getWidth();
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
        String [] propNames = image.getPropertyNames();
        if (propNames == null) propNames = new String[0];
        StringBuilder sb = new StringBuilder(propNames.length * 80);
        for (String propName: propNames) {
            sb.append(propName).append(" = ").append(image.getProperty(propName)).append(" .\n");
        }
        
        final HashSet<String> languages = new HashSet<String>();
        final HashMap<DigestURI, String> anchors = new HashMap<DigestURI, String>();
        final HashMap<String, ImageEntry> images  = new HashMap<String, ImageEntry>();
        
         return new Document(
             location,
             mimeType,
             "UTF-8",
             languages,
             new String[]{}, // keywords
             "", // title
             "", // author
             new String[]{}, // sections
             "", // description
             sb.toString().getBytes(), // content text
             anchors, // anchors
             images); // images
    }
/*
 * Document(final DigestURI location, final String mimeType, final String charset, final Set<String> languages,
                    final String[] keywords, final String title, final String author,
                    final String[] sections, final String abstrct,
                    final Object text, final Map<DigestURI, String> anchors, final HashMap<String, ImageEntry> images) {(non-Javadoc)
 * @see net.yacy.document.Idiom#supportedMimeTypes()
 */
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
    
}
