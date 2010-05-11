// htmlParser.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.07.2009 on http://yacy.net
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

package net.yacy.document.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashSet;
import java.util.Set;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ScraperInputStream;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.util.FileUtils;


public class htmlParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("htm");
        SUPPORTED_EXTENSIONS.add("html");
        SUPPORTED_EXTENSIONS.add("shtml");
        SUPPORTED_EXTENSIONS.add("xhtml");
        SUPPORTED_EXTENSIONS.add("php");
        SUPPORTED_EXTENSIONS.add("php3");
        SUPPORTED_EXTENSIONS.add("php4");
        SUPPORTED_EXTENSIONS.add("php5");
        SUPPORTED_EXTENSIONS.add("cfm");
        SUPPORTED_EXTENSIONS.add("asp");
        SUPPORTED_EXTENSIONS.add("aspx");
        SUPPORTED_EXTENSIONS.add("txt");
        SUPPORTED_EXTENSIONS.add("jsp");
        SUPPORTED_EXTENSIONS.add("pl");
        SUPPORTED_EXTENSIONS.add("py");
        SUPPORTED_MIME_TYPES.add("text/html");
        SUPPORTED_MIME_TYPES.add("text/xhtml+xml");
        SUPPORTED_MIME_TYPES.add("application/xhtml+xml");
        SUPPORTED_MIME_TYPES.add("application/x-httpd-php");
        SUPPORTED_MIME_TYPES.add("text/plain");
        SUPPORTED_MIME_TYPES.add("text/sgml");
        SUPPORTED_MIME_TYPES.add("text/csv");
    }
    
    public htmlParser() {
        super("HTML Parser"); 
    }
    
    @Override
    public Document parse(
            final DigestURI location, 
            final String mimeType, 
            final String documentCharset, 
            final InputStream sourceStream) throws ParserException, InterruptedException {
        
        // make a scraper and transformer
        final ScraperInputStream htmlFilter = new ScraperInputStream(sourceStream,documentCharset,location,null,false);
        String charset = null;
        try {
            charset = htmlFilter.detectCharset();
        } catch (IOException e1) {
            throw new ParserException("Charset error:" + e1.getMessage(), location);
        }
        if (charset == null) {
            charset = documentCharset;
        } else {
            charset = patchCharsetEncoding(charset);
        }
        
        if (!documentCharset.equalsIgnoreCase(charset)) {
            theLogger.logInfo("Charset transformation needed from '" + documentCharset + "' to '" + charset + "' for URL = " + location.toNormalform(true, true));
        }
        
        Charset c;
        try {
            c = Charset.forName(charset);
        } catch (IllegalCharsetNameException e) {
            c = Charset.defaultCharset();
        } catch (UnsupportedCharsetException e) {
            c = Charset.defaultCharset();
        }
        
        // parsing the content
        final ContentScraper scraper = new ContentScraper(location);        
        final TransformerWriter writer = new TransformerWriter(null,null,scraper,null,false);
        try {
            FileUtils.copy(htmlFilter, writer, c);
            writer.close();
        } catch (IOException e) {
            throw new ParserException("IO error:" + e.getMessage(), location);
        }
        //OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);            
        //serverFileUtils.copy(sourceFile, hfos);
        //hfos.close();
        if (writer.binarySuspect()) {
            final String errorMsg = "Binary data found in resource";
            theLogger.logSevere("Unable to parse '" + location + "'. " + errorMsg);
            throw new ParserException(errorMsg,location);    
        }
        return transformScraper(location, mimeType, documentCharset, scraper);
    }

    private static Document transformScraper(final DigestURI location, final String mimeType, final String charSet, final ContentScraper scraper) {
        final String[] sections = new String[scraper.getHeadlines(1).length + scraper.getHeadlines(2).length + scraper.getHeadlines(3).length + scraper.getHeadlines(4).length];
        int p = 0;
        for (int i = 1; i <= 4; i++) for (int j = 0; j < scraper.getHeadlines(i).length; j++) sections[p++] = scraper.getHeadlines(i)[j];
        final Document ppd =  new Document(
                location,
                mimeType,
                charSet,
                scraper.getContentLanguages(),
                scraper.getKeywords(),
                scraper.getTitle(),
                scraper.getAuthor(),
                scraper.getPublisher(),
                sections,
                scraper.getDescription(),
                scraper.getText(),
                scraper.getAnchors(),
                scraper.getImages(),
                scraper.indexingDenied());
        //scraper.close();            
        ppd.setFavicon(scraper.getFavicon());
        return ppd;
    }


    /**
     * some html authors use wrong encoding names, either because they don't know exactly what they
     * are doing or they produce a type. Many times, the upper/downcase scheme of the name is fuzzy
     * This method patches wrong encoding names. The correct names are taken from
     * http://www.iana.org/assignments/character-sets
     * @param encoding
     * @return patched encoding name
     */
    public static String patchCharsetEncoding(String encoding) {
        
        // return the system default encoding
        if ((encoding == null) || (encoding.length() < 3)) return Charset.defaultCharset().name();
        
        // trim encoding string
        encoding = encoding.trim();

        // fix upper/lowercase
        encoding = encoding.toUpperCase();
        if (encoding.startsWith("SHIFT")) return "Shift_JIS";
        if (encoding.startsWith("BIG")) return "Big5";
        // all other names but such with "windows" use uppercase
        if (encoding.startsWith("WINDOWS")) encoding = "windows" + encoding.substring(7);
        if (encoding.startsWith("MACINTOSH")) encoding = "MacRoman";
        
        // fix wrong fill characters
        encoding = encoding.replaceAll("_", "-");

        if (encoding.matches("GB[_-]?2312([-_]80)?")) return "GB2312";
        if (encoding.matches(".*UTF[-_]?8.*")) return "UTF-8";
        if (encoding.startsWith("US")) return "US-ASCII";
        if (encoding.startsWith("KOI")) return "KOI8-R";
        
        // patch missing '-'
        if (encoding.startsWith("windows") && encoding.length() > 7) {
            final char c = encoding.charAt(7);
            if ((c >= '0') && (c <= '9')) {
                encoding = "windows-" + encoding.substring(7);
            }
        }
        
        if (encoding.startsWith("ISO")) {
            // patch typos
            if (encoding.length() > 3) {
                final char c = encoding.charAt(3);
                if ((c >= '0') && (c <= '9')) {
                    encoding = "ISO-" + encoding.substring(3);
                }
            }
            if (encoding.length() > 8) {
                final char c = encoding.charAt(8);
                if ((c >= '0') && (c <= '9')) {
                    encoding = encoding.substring(0, 8) + "-" + encoding.substring(8);           
                } 
            }
        }
        
        // patch wrong name
        if (encoding.startsWith("ISO-8559")) {
            // popular typo
            encoding = "ISO-8859" + encoding.substring(8);
        }

        // converting cp\d{4} -> windows-\d{4}
        if (encoding.matches("CP([_-])?125[0-8]")) {
            final char c = encoding.charAt(2);
            if ((c >= '0') && (c <= '9')) {
                encoding = "windows-" + encoding.substring(2);
            } else {
                encoding = "windows" + encoding.substring(2);
            }
        }

        return encoding;
    }

    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
    
    public boolean indexingDenied() {
        return false;
    }
}
