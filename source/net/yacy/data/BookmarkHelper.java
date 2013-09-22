// BookmarkHelper.java
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// Methods from this file has been originally contributed by Alexander Schier
// and had been refactored by Michael Christen for better a method structure 30.01.2010
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

package net.yacy.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.AnchorURL;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.data.BookmarksDB.Bookmark;
import net.yacy.data.BookmarksDB.Tag;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.util.FileUtils;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class BookmarkHelper {

    public static String cleanTagsString(String tagsString) {

        // get rid of heading, trailing and double commas since they are useless
        while (tagsString.length() > 0 && tagsString.charAt(0) == ',') {
            tagsString = tagsString.substring(1);
        }
        while (tagsString.endsWith(",")) {
            tagsString = tagsString.substring(0,tagsString.length() -1);
        }
        while (tagsString.contains(",,")){
            tagsString = tagsString.replaceAll(",,", ",");
        }
        // get rid of double and trailing slashes
        while (tagsString.endsWith("/")){
            tagsString = tagsString.substring(0, tagsString.length() -1);
        }
        while (tagsString.contains("/,")){
            tagsString = tagsString.replaceAll("/,", ",");
        }
        while (tagsString.contains("//")){
            tagsString = tagsString.replaceAll("//", "/");
        }
        // space characters following a comma are removed
        tagsString = tagsString.replaceAll(",\\s+", ",");

        return tagsString;
    }


    /**
     * returns an object of type String that contains a tagHash
     * @param tagName an object of type String with the name of the tag.
     *        tagName is converted to lower case before hash is generated!
     */
    public static String tagHash(final String tagName){
        return ASCII.String(Word.word2hash(tagName.toLowerCase()));
    }
    /*
    private static String tagHash(final String tagName, final String user){
        return UTF8.String(Word.word2hash(user+":"+tagName.toLowerCase()));
    }
    */



    // --------------------------------------
    // bookmarksDB's Import/Export functions
    // --------------------------------------

    public static int importFromBookmarks(final BookmarksDB db, final DigestURL baseURL, final String input, final String tag, final boolean importPublic){
        try {
            // convert string to input stream
            final ByteArrayInputStream byteIn = new ByteArrayInputStream(UTF8.getBytes(input));
            final InputStreamReader reader = new InputStreamReader(byteIn,"UTF-8");

            // import stream
            return importFromBookmarks(db, baseURL, reader, tag, importPublic);
        } catch (final UnsupportedEncodingException e) {
            return 0;
        }
    }

    private static int importFromBookmarks(final BookmarksDB db, final DigestURL baseURL, final InputStreamReader input, final String tag, final boolean importPublic){

        int importCount = 0;

        Collection<AnchorURL> links = new ArrayList<AnchorURL>();
        String title;
        Bookmark bm;
        final Set<String> tags=ListManager.string2set(tag); //this allow multiple default tags
        try {
            //load the links
            final ContentScraper scraper = new ContentScraper(baseURL, 10000);
            //OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
            final Writer writer = new TransformerWriter(null, null, scraper, null, false);
            FileUtils.copy(input,writer);
            writer.close();
            links = scraper.getAnchors();
        } catch (final IOException e) { ConcurrentLog.warn("BOOKMARKS", "error during load of links: "+ e.getClass() +" "+ e.getMessage());}
        for (final AnchorURL url: links) {
            title = url.getNameProperty();
            ConcurrentLog.info("BOOKMARKS", "links.get(url)");
            if ("".equals(title)) {//cannot be displayed
                title = url.toString();
            }
            bm = db.new Bookmark(url);
            bm.setProperty(Bookmark.BOOKMARK_TITLE, title);
            bm.setTags(tags);
            bm.setPublic(importPublic);
            db.saveBookmark(bm);

            importCount++;
        }

        return importCount;
    }


    public static int importFromXML(final BookmarksDB db, final String input, final boolean importPublic){
        // convert string to input stream
        final ByteArrayInputStream byteIn = new ByteArrayInputStream(UTF8.getBytes(input));

        // import stream
        return importFromXML(db, byteIn,importPublic);
    }

    private static int importFromXML(final BookmarksDB db, final InputStream input, final boolean importPublic){
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(false);
        try {
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.parse(input);
            return parseXMLimport(db, doc, importPublic);
        } catch (final ParserConfigurationException e) {
        } catch (final SAXException e) {
        } catch (final IOException e) {
        }
        return 0;

    }

    private static int parseXMLimport(final BookmarksDB db, final Node doc, final boolean importPublic){
        int importCount = 0;
        if ("post".equals(doc.getNodeName())) {
            final NamedNodeMap attributes = doc.getAttributes();
            final String url = attributes.getNamedItem("href").getNodeValue();
            if("".equals(url)){
                return 0;
            }
            Bookmark bm;
            try {
                bm = db.new Bookmark(url);
            } catch (final MalformedURLException e1) {
                return 0;
            }
            String tagsString = "";
            String title = "";
            String description = "";
            String time = "";
            if(attributes.getNamedItem("tag") != null){
                tagsString = attributes.getNamedItem("tag").getNodeValue();
            }
            if(attributes.getNamedItem("description") != null){
                title = attributes.getNamedItem("description").getNodeValue();
            }
            if(attributes.getNamedItem("extended") != null){
                description = attributes.getNamedItem("extended").getNodeValue();
            }
            if(attributes.getNamedItem("time") != null){
                time = attributes.getNamedItem("time").getNodeValue();
            }
            Set<String> tags = new HashSet<String>();

            if (title != null) {
                bm.setProperty(Bookmark.BOOKMARK_TITLE, title);
            }
            if (tagsString != null) {
                tags = ListManager.string2set(tagsString.replace(' ', ','));
            }
            bm.setTags(tags, true);
            if(time != null){

                Date parsedDate = null;
                try {
                    parsedDate = ISO8601Formatter.FORMATTER.parse(time);
                } catch (final ParseException e) {
                    parsedDate = new Date();
                }
                bm.setTimeStamp(parsedDate.getTime());
            }
            if(description!=null){
                bm.setProperty(Bookmark.BOOKMARK_DESCRIPTION, description);
            }
            bm.setPublic(importPublic);
            db.saveBookmark(bm);

            importCount++;
        }
        final NodeList children=doc.getChildNodes();
        if(children != null){
            for (int i=0; i<children.getLength(); i++) {
                importCount += parseXMLimport(db, children.item(i), importPublic);
            }
        }

        return importCount;
    }

    public static Iterator<String> getFolderList(final String root, final Iterator<Tag> tagIterator) {

        final Set<String> folders = new TreeSet<String>();
        String path = "";
        Tag tag;

        while (tagIterator.hasNext()) {
            tag=tagIterator.next();
            if (tag.getFriendlyName().startsWith(("/".equals(root) ? root : root+"/"))) {
                path = tag.getFriendlyName();
                path = BookmarkHelper.cleanTagsString(path);
                while(path.length() > 0 && !path.equals(root)){
                    folders.add(path);
                    path = path.replaceAll("(/.[^/]*$)", "");   // create missing folders in path
                }
            }
        }
        if (!"/".equals(root)) { folders.add(root); }
        folders.add("\uffff");
        return folders.iterator();
    }
}
