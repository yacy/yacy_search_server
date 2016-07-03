// metadataImageParser.java
// (C) 2014 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.09.2014 on http://yacy.net
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

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.GpsDirectory;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;


/**
 * Image parser base on drewnoakes.com metadata-extractor which supports
 * metadata extraction from bmp, gif, jpeg, png, psd, tiff
 * All discovered metadata are added to the parsed document
 *
 * http://www.drewnoakes.com/drewnoakes.com/code/exif/
 *
 * (in difference to genericImageParser javax ImageIO is not used,
 * to support tiff parsing also if not supported by ImageIO)
 */
public class metadataImageParser extends AbstractParser implements Parser {

    public metadataImageParser() {
        super("Metadata Image Parser");
        
        SUPPORTED_EXTENSIONS.add("tif");
        SUPPORTED_EXTENSIONS.add("psd");
        // only used for ext/mime not covered by genericImageParser's default
        //SUPPORTED_EXTENSIONS.add("gif");
        //SUPPORTED_EXTENSIONS.add("jpg");
        //SUPPORTED_EXTENSIONS.add("jpeg");
        //SUPPORTED_EXTENSIONS.add("png");

        SUPPORTED_MIME_TYPES.add("image/tiff");
        SUPPORTED_MIME_TYPES.add("image/vnd.adobe.photoshop");
        SUPPORTED_MIME_TYPES.add("image/x-photoshop");
        //SUPPORTED_MIME_TYPES.add("image/gif");
        //SUPPORTED_MIME_TYPES.add("image/jpeg");
        //SUPPORTED_MIME_TYPES.add("image/png");
    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source) throws Parser.Failure, InterruptedException {

        String title = null;
        String author = null;
        String keywords = null;
        List<String> descriptions = new ArrayList<String>();
        double gpslat = 0;
        double gpslon = 0;
        StringBuilder imgInfotxt = new StringBuilder();

        try {
            final Metadata metadata = ImageMetadataReader.readMetadata(new BufferedInputStream(source));

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
                            imgInfotxt.append(tag.getTagName() + ": " + tag.getDescription() + " .\n");
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
            description = props.get("Caption/Abstract");
            if (description != null && description.length() > 0) descriptions.add("Abstract: " + description);
            description = props.get("Country/Primary Location");
            if (description != null && description.length() > 0) descriptions.add("Location: " + description);
            description = props.get("Province/State");
            if (description != null && description.length() > 0) descriptions.add("State: " + description);
            description = props.get("Copyright Notice");
            if (description != null && description.length() > 0) descriptions.add("Copyright: " + description);

        } catch (ImageProcessingException e) {
            throw new Parser.Failure("could not extract image meta data", location);
        } catch (IOException ex) {
            throw new Parser.Failure("IO-Error reading", location);
        }

        if (title == null || title.isEmpty()) {
            title = MultiProtocolURL.unescape(location.getFileName());
        }

        return new Document[]{new Document(
            location,
            mimeType,
            charset,
            this,
            new HashSet<String>(0), // languages
            keywords == null ? new String[]{} : keywords.split(keywords.indexOf(',') > 0 ? "," : " "), // keywords
            singleList(title), // title
            author == null ? null : author, // author
            location.getHost(), // Publisher
            null, // sections
            descriptions, // description
            gpslon, gpslat, //  location
            imgInfotxt.toString(), // content text
            null, // anchors
            null, // rss
            null, // images
            false,
            new Date())}; // images
    }
}
