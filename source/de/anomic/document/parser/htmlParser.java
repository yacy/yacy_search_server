package de.anomic.document.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Hashtable;

import de.anomic.document.AbstractParser;
import de.anomic.document.Document;
import de.anomic.document.Parser;
import de.anomic.document.ParserException;
import de.anomic.document.parser.html.ContentScraper;
import de.anomic.document.parser.html.ScraperInputStream;
import de.anomic.document.parser.html.TransformerWriter;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.yacy.yacyURL;

public class htmlParser extends AbstractParser implements Parser {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();  
    static { 
        SUPPORTED_MIME_TYPES.put("application/xhtml+xml","htm,html,xhtml,php,asp");
        SUPPORTED_MIME_TYPES.put("text/html","htm,html,xhtml,php,asp");
        SUPPORTED_MIME_TYPES.put("text/plain","htm,html,xhtml,php,asp,txt");
        SUPPORTED_MIME_TYPES.put("text/sgml","htm,html,xhtml,php,asp,xml");
    }
    
    public htmlParser() {
        super();
        this.parserName = "streaming html parser"; 
    }
    
    @Override
    public Document parse(
            final yacyURL location, 
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

    private static Document transformScraper(final yacyURL location, final String mimeType, final String charSet, final ContentScraper scraper) {
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
                sections,
                scraper.getDescription(),
                scraper.getText(),
                scraper.getAnchors(),
                scraper.getImages());
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
    
    public Hashtable<String, String> getSupportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
}
