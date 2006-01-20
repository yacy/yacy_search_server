// plasmaIndexEntry.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/* 
   This class defines the structures of an index entry
*/

package de.anomic.plasma;

import java.net.URL;
import java.util.Properties;
import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.server.serverCodings;
import de.anomic.yacy.yacySeedDB;
// import de.anomic.server.logging.serverLog;

public final class plasmaWordIndexEntry {

    // an wordEntry can be filled in either of two ways:
    // by the discrete values of the entry
    // or by the encoded entry-string

    // the size of a word hash
    public static final int wordHashLength   = yacySeedDB.commonHashLength; // 12
    public static final int  urlHashLength   = yacySeedDB.commonHashLength; // 12

    // the size of the index entry attributes
    //public static final int attrSpaceShort   = 12;
    public static final int attrSpaceLong    = 18;

    // the associated hash
    private final String urlHash;

    // discrete values
    private int    count;       // words in file
    private int    posintext;   // first position of the word in text as number of word; 0=unknown or irrelevant position
    private int    posinphrase; // position within a phrase of the word
    private int    posofphrase; // position of the phrase in the text as count of sentences; 0=unknown; 1=path; 2=keywords; 3=headline; >4: in text
    private long   lastModified;// calculated by using last-modified
    private int    quality;     // result of a heuristic on the source file
    private byte[] language;    // essentially the country code (the TLD as heuristic), two letters lowercase only
    private char   doctype;     // type of source
    private char   localflag;   // indicates if the index was created locally

    // some doctypes:
    public static final char DT_PDFPS   = 'p';
    public static final char DT_TEXT    = 't';
    public static final char DT_HTML    = 'h';
    public static final char DT_DOC     = 'd';
    public static final char DT_IMAGE   = 'i';
    public static final char DT_MOVIE   = 'm';
    public static final char DT_FLASH   = 'f';
    public static final char DT_SHARE   = 's';
    public static final char DT_AUDIO   = 'a';
    public static final char DT_BINARY  = 'b';
    public static final char DT_UNKNOWN = 'u';

    // local flag attributes
    public static final char LT_LOCAL   = 'L';
    public static final char LT_GLOBAL  = 'G';

    // create a word hash
    public static String word2hash(String word) {
        return kelondroBase64Order.enhancedCoder.encode(serverCodings.encodeMD5Raw(word.toLowerCase())).substring(0, wordHashLength);
    }

    // doctype calculation
    public static char docType(URL url) {
        String path = htmlFilterContentScraper.urlNormalform(url);
        // serverLog.logFinest("PLASMA", "docType URL=" + path);
        char doctype = doctype = DT_UNKNOWN;
        if (path.endsWith(".gif"))       { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpg"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".jpeg")) { doctype = DT_IMAGE; }
        else if (path.endsWith(".png"))  { doctype = DT_IMAGE; }
        else if (path.endsWith(".html")) { doctype = DT_HTML;  }
        else if (path.endsWith(".txt"))  { doctype = DT_TEXT;  }
        else if (path.endsWith(".doc"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".rtf"))  { doctype = DT_DOC;   }
        else if (path.endsWith(".pdf"))  { doctype = DT_PDFPS; }
        else if (path.endsWith(".ps"))   { doctype = DT_PDFPS; }
        else if (path.endsWith(".avi"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".mov"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".qt"))   { doctype = DT_MOVIE; }
        else if (path.endsWith(".mpg"))  { doctype = DT_MOVIE; }
        else if (path.endsWith(".md5"))  { doctype = DT_SHARE; }
        else if (path.endsWith(".mpeg")) { doctype = DT_MOVIE; }
        else if (path.endsWith(".asf"))  { doctype = DT_FLASH; }
        return doctype;
    }

    public static char docType(String mime) {
        // serverLog.logFinest("PLASMA", "docType mime=" + mime);
        char doctype = DT_UNKNOWN;
        if (mime == null) doctype = DT_UNKNOWN;
        else if (mime.startsWith("image/")) doctype = DT_IMAGE;
/*      else if (mime.endsWith("/gif")) doctype = DT_IMAGE;
        else if (mime.endsWith("/jpeg")) doctype = DT_IMAGE;
        else if (mime.endsWith("/png")) doctype = DT_IMAGE; */
        else if (mime.endsWith("/html")) doctype = DT_HTML;
        else if (mime.endsWith("/rtf")) doctype = DT_DOC;
        else if (mime.endsWith("/pdf")) doctype = DT_PDFPS;
        else if (mime.endsWith("/octet-stream")) doctype = DT_BINARY;
        else if (mime.endsWith("/x-shockwave-flash")) doctype = DT_FLASH;
        else if (mime.endsWith("/msword")) doctype = DT_DOC;
        else if (mime.endsWith("/mspowerpoint")) doctype = DT_DOC;
        else if (mime.endsWith("/postscript")) doctype = DT_PDFPS;
        else if (mime.startsWith("text/")) doctype = DT_TEXT;
//      else if (mime.startsWith("image/")) doctype = DT_IMAGE;
        else if (mime.startsWith("audio/")) doctype = DT_AUDIO;
        else if (mime.startsWith("video/")) doctype = DT_MOVIE;
        //bz2     = application/x-bzip2
        //dvi     = application/x-dvi
        //gz      = application/gzip
        //hqx     = application/mac-binhex40
        //lha     = application/x-lzh
        //lzh     = application/x-lzh
        //pac     = application/x-ns-proxy-autoconfig
        //php     = application/x-httpd-php
        //phtml   = application/x-httpd-php
        //rss     = application/xml
        //tar     = application/tar
        //tex     = application/x-tex
        //tgz     = application/tar
        //torrent = application/x-bittorrent
        //xhtml   = application/xhtml+xml
        //xla     = application/msexcel
        //xls     = application/msexcel
        //xsl     = application/xml
        //xml     = application/xml
        //Z       = application/x-compress
        //zip     = application/zip
        return doctype;
    }

    // language calculation
    public static String language(URL url) {
        String language = "uk";
        String host = url.getHost();
        int pos = host.lastIndexOf(".");
        if ((pos > 0) && (host.length() - pos == 3)) language = host.substring(pos + 1).toLowerCase();
        return language;
    }

    // the class instantiation can only be done by a plasmaStore method
    // therefore they are all public
    public plasmaWordIndexEntry(String urlHash,
                                int count,        // how often appears this word in the text
                                int posintext,
                                int posinphrase,
                                int posofphrase,
                                long time,
                                int quality,
                                String language,
                                char doctype, 
                                boolean local) {

        // more needed attributes:
        // - int: length of text / total number of words
        // - int: length of text / total number of sentences
        // - long: update time; this is needed to compute a TTL for the word, so it can be removed easily if the TTL is short
        // - int: word distance; this is 0 by default, and set to the difference of posintext from two indexes if these are combined (simultanous search). If stored, this shows that the result was obtained by remote search
        // - char: category of appearance (header, title, section, text, anchor-descr, image-tag etc)
        // - boolean: appears in title, appears in header, appears in ....
        // - int: url-length (shorter are better)
        // - int: url-number of components / length of path
        // - int: length of description tag / title tag (longer are better)
        // - int: number of chapters
        
    if ((language == null) || (language.length() != plasmaURL.urlLanguageLength)) language = "uk";
        this.urlHash = urlHash;
        this.count = count;
        this.posintext = posintext;
        this.posinphrase = posinphrase;
        this.posofphrase = posofphrase;
        this.lastModified = time;
        this.quality = quality;
        this.language = language.getBytes();
        this.doctype = doctype;
        this.localflag = (local) ? LT_LOCAL : LT_GLOBAL;
    }
    
    public plasmaWordIndexEntry(String urlHash, String code) {
        // the code is not parsed but used later on
        this.urlHash = urlHash;
        this.count = (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(6, 8));
        this.posintext = (code.length() >= 14) ? (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(12, 14)) : 0;
        this.posinphrase = (code.length() >= 15) ? (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(14, 16)) : 0;
        this.posofphrase = (code.length() >= 16) ? (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(16, 18)) : 0;
        this.lastModified = plasmaWordIndex.reverseMicroDateDays((int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(3, 6)));
        this.quality = (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(0, 3));
        this.language = code.substring(8, 10).getBytes();
        this.doctype = code.charAt(10);
        this.localflag = code.charAt(11);
    }
    
    public plasmaWordIndexEntry(String external) {
       // parse external form
       String[] elts = external.substring(1, external.length() - 1).split(",");
       Properties pr = new Properties();
       int p;
       for (int i = 0; i < elts.length; i++) {
           pr.put(elts[i].substring(0, (p = elts[i].indexOf("="))), elts[i].substring(p + 1));
       }
       // set values
       this.urlHash = pr.getProperty("h", "");
       this.count = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("c", "A"));
       this.posintext = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("t", "__"));
       this.posinphrase = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("r", "__"));
       this.posofphrase = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("o", "__"));
       this.lastModified = plasmaWordIndex.reverseMicroDateDays((int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("a", "A")));
       this.quality = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("q", "__"));
       this.language = pr.getProperty("l", "uk").getBytes();
       this.doctype = pr.getProperty("d", "u").charAt(0);
       this.localflag = pr.getProperty("f", ""+LT_LOCAL).charAt(0);
    }
    
    public String toEncodedForm() {
       // attention: this integrates NOT the URL hash into the encoding
       // if you need a complete dump, use toExternalForm()
       StringBuffer buf = new StringBuffer(attrSpaceLong);
       
       buf.append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.quality, plasmaURL.urlQualityLength))
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(plasmaWordIndex.microDateDays(this.lastModified), 3))
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.count, 2))
          .append(new String(this.language))
          .append(this.doctype)
          .append(this.localflag); // 3 + 3 + 2 + 2 + 1 + 1 = 12 bytes
           
       
           buf.append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posintext, 2))
              .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posinphrase, 2))
              .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posofphrase, 2));
       
       return buf.toString();
   }
    
   public String toExternalForm() {
       StringBuffer str = new StringBuffer(61);
       
       str.append("{")
           .append("h=").append(this.urlHash)
           .append(",q=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.quality, plasmaURL.urlQualityLength))
           .append(",a=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(plasmaWordIndex.microDateDays(this.lastModified), 3))
           .append(",c=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.count, 2))
           .append(",l=").append(new String(this.language))
           .append(",d=").append(this.doctype)
           .append(",f=").append(this.localflag)
           .append(",t=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posintext, 2))
           .append(",r=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posinphrase, 2))
           .append(",o=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posofphrase, 2))
       .append("}");
       
       return str.toString();
   }
        
    public String getUrlHash() {
        return urlHash;
    }
    
    public int getQuality() {
        return quality;
    }

    public int getVirtualAge() {
        return plasmaWordIndex.microDateDays(lastModified);
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public int getCount() {
        return count;
    }

    public int posintext() {
        return posintext;
    }

    public int posinphrase() {
        return posinphrase;
    }

    public int posofphrase() {
        return posofphrase;
    }
    
    public String getLanguage() {
        return new String(language);
    }

    public char getType() {
        return doctype;
    }

    public boolean isLocal() {
        return localflag == LT_LOCAL;
    }

    public static void main(String[] args) {
        // outputs the word hash to a given word
        if (args.length != 1) System.exit(0);
        System.out.println("WORDHASH: " + word2hash(args[0]));
    }
   
}