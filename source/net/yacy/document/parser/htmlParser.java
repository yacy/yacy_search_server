/**
 *  htmlParser.java
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 09.07.2009 at http://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.regex.Pattern;

import com.ibm.icu.text.CharsetDetector;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.document.parser.html.ContentScraper;
import net.yacy.document.parser.html.ScraperInputStream;
import net.yacy.document.parser.html.TransformerWriter;
import net.yacy.kelondro.util.FileUtils;


public class htmlParser extends AbstractParser implements Parser {

    private static final Pattern patternUnderline = Pattern.compile("_");

    public htmlParser() {
        super("HTML Parser"); 
        SUPPORTED_EXTENSIONS.add("htm");
        SUPPORTED_EXTENSIONS.add("html");
        SUPPORTED_EXTENSIONS.add("phtml");
        SUPPORTED_EXTENSIONS.add("shtml");
        SUPPORTED_EXTENSIONS.add("xhtml");
        SUPPORTED_EXTENSIONS.add("php");
        SUPPORTED_EXTENSIONS.add("php3");
        SUPPORTED_EXTENSIONS.add("php4");
        SUPPORTED_EXTENSIONS.add("php5");
        SUPPORTED_EXTENSIONS.add("cfm");
        SUPPORTED_EXTENSIONS.add("asp");
        SUPPORTED_EXTENSIONS.add("aspx");
        SUPPORTED_EXTENSIONS.add("tex");
        SUPPORTED_EXTENSIONS.add("txt");
        //SUPPORTED_EXTENSIONS.add("js");
        SUPPORTED_EXTENSIONS.add("jsp");
        SUPPORTED_EXTENSIONS.add("mf");
        SUPPORTED_EXTENSIONS.add("pl");
        SUPPORTED_EXTENSIONS.add("py");
        SUPPORTED_MIME_TYPES.add("text/html");
        SUPPORTED_MIME_TYPES.add("text/xhtml+xml");
        SUPPORTED_MIME_TYPES.add("application/xhtml+xml");
        SUPPORTED_MIME_TYPES.add("application/x-httpd-php");
        SUPPORTED_MIME_TYPES.add("application/x-tex");
        SUPPORTED_MIME_TYPES.add("text/plain");
        SUPPORTED_MIME_TYPES.add("text/sgml");
        SUPPORTED_MIME_TYPES.add("text/csv");
    }
    
    public static ContentScraper parseToScraper(
            final MultiProtocolURI location, 
            final String documentCharset, 
            InputStream sourceStream) throws Parser.Failure, IOException {
        
        // make a scraper
        String charset = null;

        // ah, we are lucky, we got a character-encoding via HTTP-header
        if (documentCharset != null) {
            charset = patchCharsetEncoding(documentCharset);
        }
        
        // nothing found: try to find a meta-tag
        if (charset == null) {
            try {
                final ScraperInputStream htmlFilter = new ScraperInputStream(sourceStream,documentCharset,location,null,false);
                sourceStream = htmlFilter;
                charset = htmlFilter.detectCharset();
            } catch (IOException e1) {
                throw new Parser.Failure("Charset error:" + e1.getMessage(), location);
            }
        }

        // the author didn't tell us the encoding, try the mozilla-heuristic
        if (charset == null) {
        	CharsetDetector det = new CharsetDetector();
        	det.enableInputFilter(true);
        	InputStream detStream = new BufferedInputStream(sourceStream);
        	det.setText(detStream);
        	charset = det.detect().getName();
        	sourceStream = detStream;
        }
        
        // wtf? still nothing, just take system-standard
        if (charset == null) {
            charset = Charset.defaultCharset().name();
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
            FileUtils.copy(sourceStream, writer, c);
            writer.close();
        } catch (IOException e) {
            throw new Parser.Failure("IO error:" + e.getMessage(), location);
        }
        //OutputStream hfos = new htmlFilterOutputStream(null, scraper, null, false);            
        //serverFileUtils.copy(sourceFile, hfos);
        //hfos.close();
        if (writer.binarySuspect()) {
            final String errorMsg = "Binary data found in resource";
            throw new Parser.Failure(errorMsg, location);    
        }
        return scraper;
    }

    public Document[] parse(
            final MultiProtocolURI location, 
            final String mimeType, 
            final String documentCharset, 
            final InputStream sourceStream) throws Parser.Failure, InterruptedException {
        
        try {
			return transformScraper(location, mimeType, documentCharset, parseToScraper(location, documentCharset, sourceStream));
		} catch (IOException e) {
			throw new Parser.Failure("IOException in htmlParser: " + e.getMessage(), location);
		}
    }

    private static Document[] transformScraper(final MultiProtocolURI location, final String mimeType, final String charSet, final ContentScraper scraper) {
        final String[] sections = new String[
                 scraper.getHeadlines(1).length +
                 scraper.getHeadlines(2).length +
                 scraper.getHeadlines(3).length +
                 scraper.getHeadlines(4).length +
                 scraper.getHeadlines(5).length +
                 scraper.getHeadlines(6).length];
        int p = 0;
        for (int i = 1; i <= 6; i++) {
            for (final String headline : scraper.getHeadlines(i)) {
                sections[p++] = headline;
            }
        }
        final Document[] ppds = new Document[]{new Document(
                location,
                mimeType,
                charSet,
                scraper,
                scraper.getContentLanguages(),
                scraper.getKeywords(),
                scraper.getTitle(),
                scraper.getAuthor(),
                scraper.getPublisher(),
                sections,
                scraper.getDescription(),
                scraper.getLon(), scraper.getLat(),
                scraper.getText(),
                scraper.getAnchors(),
                scraper.getRSS(),
                scraper.getImages(),
                scraper.indexingDenied())};
        //scraper.close();            
        for (final Document ppd: ppds) {
            ppd.setFavicon(scraper.getFavicon());
        }
        return ppds;
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
        
        // do nothing with null
        if ((encoding == null) || (encoding.length() < 3)) return null;
        
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
        encoding = patternUnderline.matcher(encoding).replaceAll("-");

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
 
    public static void main(String[] args) {
        // test parsing of a url
        MultiProtocolURI url;
        try {
            url = new MultiProtocolURI(args[0]);
            byte[] content = url.get(ClientIdentification.getUserAgent(), 3000);
            Document[] document = new htmlParser().parse(url, "text/html", null, new ByteArrayInputStream(content));
            String title = document[0].dc_title();
            System.out.println(title);
            System.out.println(CharacterCoding.unicode2html(title, false));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Parser.Failure e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
}
