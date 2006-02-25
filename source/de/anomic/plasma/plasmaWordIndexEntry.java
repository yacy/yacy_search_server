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

public final class plasmaWordIndexEntry implements Cloneable {

    // an wordEntry can be filled in either of two ways:
    // by the discrete values of the entry
    // or by the encoded entry-string

    // the size of a word hash
    public static final int wordHashLength   = yacySeedDB.commonHashLength; // 12
    public static final int  urlHashLength   = yacySeedDB.commonHashLength; // 12

    // the size of the index entry attributes
    public static final int attrSpace = 24;

    // the associated hash
    private final String urlHash;

    // discrete values
    private int    hitcount;    // number of this words in file
    private int    wordcount;   // number of all words in the file
    private int    phrasecount; // number of all phrases in the file
    private int    posintext;   // first position of the word in text as number of word; 0=unknown or irrelevant position
    private int    posinphrase; // position within a phrase of the word
    private int    posofphrase; // position of the phrase in the text as count of sentences; 0=unknown; 1=path; 2=keywords; 3=headline; >4: in text
    private int    worddistance;// distance between the words, only used if the index is artificial (from a conjunction)
    private long   lastModified;// calculated by using last-modified
    private int    quality;     // result of a heuristic on the source file
    private byte[] language;    // essentially the country code (the TLD as heuristic), two letters lowercase only
    private char   doctype;     // type of source
    private char   localflag;   // indicates if the index was created locally

    // doctypes:
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

    // appearance locations: (used for flags)
    public static final int AP_TITLE     =  0; // title tag from html header
    public static final int AP_H1        =  1; // h1-tag
    public static final int AP_H2        =  2; // h2-tag
    public static final int AP_H3        =  3; // h3-tag
    public static final int AP_H4        =  4; // h4-tag
    public static final int AP_H5        =  5; // h5-tag
    public static final int AP_H6        =  6; // h6-tag
    public static final int AP_TEXT      =  7; // word appears in text (used to check validation of other appearances against spam)
    public static final int AP_DOM       =  8; // word inside an url: in Domain
    public static final int AP_PATH      =  9; // word inside an url: in path
    public static final int AP_IMG       = 10; // tag inside image references
    public static final int AP_ANCHOR    = 11; // anchor description
    public static final int AP_BOLD      = 12; // may be interpreted as emphasized
    public static final int AP_ITALICS   = 13; // may be interpreted as emphasized
    public static final int AP_WEAK      = 14; // for Text that is small or bareley visible
    public static final int AP_INVISIBLE = 15; // good for spam detection
    public static final int AP_TAG       = 16; // for tagged indexeing (i.e. using mp3 tags)
    public static final int AP_AUTHOR    = 17; // word appears in author name
    public static final int AP_OPUS      = 18; // word appears in name of opus, which may be an album name (in mp3 tags)
    public static final int AP_TRACK     = 19; // word appears in track name (i.e. in mp3 tags)
    
    // URL attributes
    public static final int UA_LOCAL    =  0; // URL was crawled locally
    public static final int UA_TILDE    =  1; // tilde appears in URL
    public static final int UA_REDIRECT =  2; // The URL is a redirection
    
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
    public plasmaWordIndexEntry(String  urlHash,
                                int     urlLength,    // byte-length of complete URL
                                int     urlComps,     // number of path components
                                int     hitcount,     //*how often appears this word in the text
                                int     wordcount,    //*total number of words
                                int     phrasecount,  //*total number of phrases
                                int     posintext,    //*position of word in all words
                                int     posinphrase,  //*position of word in its phrase
                                int     posofphrase,  //*number of the phrase where word appears
                                int     distance,     //*word distance; this is 0 by default, and set to the difference of posintext from two indexes if these are combined (simultanous search). If stored, this shows that the result was obtained by remote search
                                int     sizeOfPage,   // # of bytes of the page
                                long    lastmodified, //*last-modified time of the document where word appears
                                long    updatetime,   // update time; this is needed to compute a TTL for the word, so it can be removed easily if the TTL is short
                                int     quality,      //*the entropy value
                                String  language,     //*(guessed) language of document
                                char    doctype,      //*type of document
                                boolean local         //*flag shows that this index was generated locally; othervise its from a remote peer
                               ) {

        // more needed attributes:
        // - boolean: appearance attributes: title, appears in header, anchor-descr, image-tag etc
        // - boolean: URL attributes
        // - int: length of description tag / title tag (longer are better)
        // - int: # of outlinks to same domain
        // - int: # of outlinks to outside domain
        // - int: # of keywords
        
    if ((language == null) || (language.length() != plasmaURL.urlLanguageLength)) language = "uk";
        this.urlHash = urlHash;
        this.hitcount = hitcount;
        this.wordcount = wordcount;
        this.phrasecount = phrasecount;
        this.posintext = posintext;
        this.posinphrase = posinphrase;
        this.posofphrase = posofphrase;
        this.worddistance = distance;
        this.lastModified = lastmodified;
        this.quality = quality;
        this.language = language.getBytes();
        this.doctype = doctype;
        this.localflag = (local) ? LT_LOCAL : LT_GLOBAL;
    }
    
    public plasmaWordIndexEntry(String urlHash, String code) {
        // the code is not parsed but used later on
        this.urlHash = urlHash;
        this.hitcount = (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(6, 8));
        this.lastModified = plasmaWordIndex.reverseMicroDateDays((int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(3, 6)));
        this.quality = (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(0, 3));
        this.language = code.substring(8, 10).getBytes();
        this.doctype = code.charAt(10);
        this.localflag = code.charAt(11);
        this.posintext = (code.length() >= 14) ? (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(12, 14)) : 0;
        this.posinphrase = (code.length() >= 15) ? (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(14, 16)) : 0;
        this.posofphrase = (code.length() >= 17) ? (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(16, 18)) : 0;
        this.worddistance = (code.length() >= 19) ? (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(18, 20)) : 0;
        this.wordcount = (code.length() >= 21) ? (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(20, 22)) : 0;
        this.phrasecount = (code.length() >= 23) ? (int) kelondroBase64Order.enhancedCoder.decodeLong(code.substring(22, 24)) : 0;
        if (hitcount == 0) hitcount = 1;
        if (wordcount == 0) wordcount = 1000;
        if (phrasecount == 0) phrasecount = 100;
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
       this.hitcount = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("c", "A"));
       this.wordcount = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("w", "__"));
       this.phrasecount = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("p", "__"));
       this.posintext = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("t", "__"));
       this.posinphrase = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("r", "__"));
       this.posofphrase = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("o", "__"));
       this.worddistance = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("i", "__"));
       this.lastModified = plasmaWordIndex.reverseMicroDateDays((int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("a", "A")));
       this.quality = (int) kelondroBase64Order.enhancedCoder.decodeLong(pr.getProperty("q", "__"));
       this.language = pr.getProperty("l", "uk").getBytes();
       this.doctype = pr.getProperty("d", "u").charAt(0);
       this.localflag = pr.getProperty("f", ""+LT_LOCAL).charAt(0);
    }
    
    public Object clone() {
        return new plasmaWordIndexEntry(this.toExternalForm());
    }
    
    public String toEncodedForm() {
       // attention: this integrates NOT the URL hash into the encoding
       // if you need a complete dump, use toExternalForm()
       StringBuffer buf = new StringBuffer(attrSpace);
       
       buf.append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.quality, plasmaURL.urlQualityLength))
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(plasmaWordIndex.microDateDays(this.lastModified), 3))
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.hitcount, 2))
          .append(new String(this.language))
          .append(this.doctype)
          .append(this.localflag)
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posintext, 2))
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posinphrase, 2))
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posofphrase, 2))
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.worddistance, 2))
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.wordcount, 2))
          .append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.phrasecount, 2)); // 3+3+2+2+1+1+2+2+2+2+2+2= 24 bytes
    
       return buf.toString();
    }
    
    public String toExternalForm() {
       StringBuffer str = new StringBuffer(61);
       
       str.append("{")
           .append( "h=").append(this.urlHash)
           .append(",q=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.quality, plasmaURL.urlQualityLength))
           .append(",a=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(plasmaWordIndex.microDateDays(this.lastModified), 3))
           .append(",c=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.hitcount, 2))
           .append(",l=").append(new String(this.language))
           .append(",d=").append(this.doctype)
           .append(",f=").append(this.localflag)
           .append(",t=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posintext, 2))
           .append(",r=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posinphrase, 2))
           .append(",o=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.posofphrase, 2))
           .append(",i=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.worddistance, 2))
           .append(",w=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.wordcount, 2))
           .append(",p=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.phrasecount, 2))
       .append("}");
       
       return str.toString();
    }
    
    public void combineDistance(plasmaWordIndexEntry oe) {
        this.worddistance = this.worddistance + oe.worddistance + Math.abs(this.posintext - oe.posintext);
        this.posintext = Math.min(this.posintext, oe.posintext);
        if (this.posofphrase != oe.posofphrase) this.posinphrase = 0; // (unknown)
        this.posofphrase = Math.min(this.posofphrase, oe.posofphrase);
        this.wordcount = (this.wordcount + oe.wordcount) / 2;
    }
    
    public void min(plasmaWordIndexEntry other) {
        if (this.hitcount > other.hitcount) this.hitcount = other.hitcount;
        if (this.wordcount > other.wordcount) this.wordcount = other.wordcount;
        if (this.phrasecount > other.phrasecount) this.phrasecount = other.phrasecount;
        if (this.posintext > other.posintext) this.posintext = other.posintext;
        if (this.posinphrase > other.posinphrase) this.posinphrase = other.posinphrase;
        if (this.posofphrase > other.posofphrase) this.posofphrase = other.posofphrase;
        if (this.worddistance > other.worddistance) this.worddistance = other.worddistance;
        if (this.lastModified > other.lastModified) this.lastModified = other.lastModified;
        if (this.quality > other.quality) this.quality = other.quality;
    }
    
    public void max(plasmaWordIndexEntry other) {
        if (this.hitcount < other.hitcount) this.hitcount = other.hitcount;
        if (this.wordcount < other.wordcount) this.wordcount = other.wordcount;
        if (this.phrasecount < other.phrasecount) this.phrasecount = other.phrasecount;
        if (this.posintext < other.posintext) this.posintext = other.posintext;
        if (this.posinphrase < other.posinphrase) this.posinphrase = other.posinphrase;
        if (this.posofphrase < other.posofphrase) this.posofphrase = other.posofphrase;
        if (this.worddistance < other.worddistance) this.worddistance = other.worddistance;
        if (this.lastModified < other.lastModified) this.lastModified = other.lastModified;
        if (this.quality < other.quality) this.quality = other.quality;
    }
    
    public void normalize(plasmaWordIndexEntry min, plasmaWordIndexEntry max) {
        this.hitcount     = (this.hitcount     == 0) ? 0 : 1 + 255 * (this.hitcount     - min.hitcount    ) / (1 + max.hitcount     - min.hitcount);
        this.wordcount    = (this.wordcount    == 0) ? 0 : 1 + 255 * (this.wordcount    - min.wordcount   ) / (1 + max.wordcount    - min.wordcount);
        this.phrasecount  = (this.phrasecount  == 0) ? 0 : 1 + 255 * (this.phrasecount  - min.phrasecount ) / (1 + max.phrasecount  - min.phrasecount);
        this.posintext    = (this.posintext    == 0) ? 0 : 1 + 255 * (this.posintext    - min.posintext   ) / (1 + max.posintext    - min.posintext);
        this.posinphrase  = (this.posinphrase  == 0) ? 0 : 1 + 255 * (this.posinphrase  - min.posinphrase ) / (1 + max.posinphrase  - min.posinphrase);
        this.posofphrase  = (this.posofphrase  == 0) ? 0 : 1 + 255 * (this.posofphrase  - min.posofphrase ) / (1 + max.posofphrase  - min.posofphrase);
        this.worddistance = (this.worddistance == 0) ? 0 : 1 + 255 * (this.worddistance - min.worddistance) / (1 + max.worddistance - min.worddistance);
        this.lastModified = (this.lastModified == 0) ? 0 : 1 + 255 * (this.lastModified - min.lastModified) / (1 + max.lastModified - min.lastModified);
        this.quality      = (this.quality      == 0) ? 0 : 1 + 255 * (this.quality      - min.quality     ) / (1 + max.quality      - min.quality);
    }
    
    public plasmaWordIndexEntry generateNormalized(plasmaWordIndexEntry min, plasmaWordIndexEntry max) {
        plasmaWordIndexEntry e = (plasmaWordIndexEntry) this.clone();
        e.normalize(min, max);
        return e;
    }
    
    public String getUrlHash() { return urlHash; }
    public int getQuality() { return quality; }
    public int getVirtualAge() { return plasmaWordIndex.microDateDays(lastModified); }
    public long getLastModified() { return lastModified; }
    public int hitcount() { return hitcount; }
    public int posintext() { return posintext; }
    public int posinphrase() { return posinphrase; }
    public int posofphrase() { return posofphrase; }
    public int worddistance() { return worddistance; }
    public int wordcount() { return wordcount; }
    public int phrasecount() { return phrasecount; }
    public String getLanguage() { return new String(language); }
    public char getType() { return doctype; }
    public boolean isLocal() { return localflag == LT_LOCAL; }
    
    public int domlengthNormalized() {
        return 255 * plasmaURL.domLengthEstimation(this.urlHash) / 30;
    }

    public static void main(String[] args) {
        // outputs the word hash to a given word
        if (args.length != 1) System.exit(0);
        System.out.println("WORDHASH: " + word2hash(args[0]));
    }
   
}