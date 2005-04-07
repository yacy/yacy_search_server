// plasmaIndexEntry.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 01.06.2004
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

import java.net.*;
import java.util.*;
import de.anomic.yacy.*;
import de.anomic.server.*;

public class plasmaWordIndexEntry {
	
    // an wordEntry can be filled in either of two ways:
    // by the discrete values of the entry
    // or by the encoded entry-string
    
    // the size of a word hash
    public static final int wordHashLength   = yacySeedDB.commonHashLength; // 12

    // the size of the index entry attributes
    public static final int attrSpaceShort   = 12;
    public static final int attrSpaceLong    = 18;

    // the associated hash
    private String urlHash;
    
    // discrete values
    private int    count;       // words in file
    private int    posintext;   // first position of the word in text as number of word; 0=unknown or irrelevant position
    private int    posinphrase; // position within a phrase of the word
    private int    posofphrase; // position of the phrase in the text as count of sentences; 0=unknown; 1=path; 2=keywords; 3=headline; >4: in text
    private int    age;         // calculated by using last-modified
    private int    quality;     // result of a heuristic on the source file
    private String language;    // essentially the country code (the TLD as heuristic), two letters lowercase only
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
    public static final char DT_UNKNOWN = 'u';

    // local flag attributes
    public static final char LT_LOCAL   = 'L';
    public static final char LT_GLOBAL  = 'G';

    // encoded discrete values
    private String code;
    
    // create a word hash
    public static String word2hash(String word) {
	return serverCodings.encodeMD5B64(word.toLowerCase(), true).substring(0, wordHashLength);
    }

    // doctype calculation
    public static char docType(String path) {
	    char doctype = DT_UNKNOWN;
	    if (path.endsWith(".gif")) doctype = DT_IMAGE;
	    if (path.endsWith(".jpg")) doctype = DT_IMAGE;
	    if (path.endsWith(".jpeg")) doctype = DT_IMAGE;
	    if (path.endsWith(".png")) doctype = DT_IMAGE;
	    if (path.endsWith(".html")) doctype = DT_HTML;
	    if (path.endsWith(".txt")) doctype = DT_TEXT;
	    if (path.endsWith(".doc")) doctype = DT_DOC;
	    if (path.endsWith(".rtf")) doctype = DT_DOC;
	    if (path.endsWith(".pdf")) doctype = DT_PDFPS;
	    if (path.endsWith(".ps")) doctype = DT_PDFPS;
	    if (path.endsWith(".avi")) doctype = DT_MOVIE;
	    if (path.endsWith(".mov")) doctype = DT_MOVIE;
	    if (path.endsWith(".qt")) doctype = DT_MOVIE;
	    if (path.endsWith(".mpg")) doctype = DT_MOVIE;
            if (path.endsWith(".md5")) doctype = DT_SHARE;
	    if (path.endsWith(".mpeg")) doctype = DT_MOVIE;
	    if (path.endsWith(".asf")) doctype = DT_FLASH;
	    return doctype;
    }

    // language calculation
    public static String language(URL url) {
	String host = url.getHost();
	int pos = host.lastIndexOf(".");
	String language = "uk";
	if ((pos > 0) && (host.length() - pos == 3)) language = host.substring(pos + 1).toLowerCase();
	return language;
    }

    // the class instantiation can only be done by a plasmaStore method
    // therefore they are all public
    public plasmaWordIndexEntry(String urlHash, int count, int posintext, int posinphrase, int posofphraseint, int virtualage, int quality, String language, char doctype, boolean local) {

	// ** hier fehlt noch als Attribut: <Wortposition im Text>, damit 'nearby' getrackt werden kann **

	if ((language == null) || (language.length() != plasmaCrawlLURL.urlLanguageLength)) language = "uk";
	this.urlHash = urlHash;
	this.count = count;
        this.posintext = posintext;
        this.posinphrase = posinphrase;
        this.posofphrase = posofphrase;
	this.age = virtualage;
	this.quality = quality;
	this.language = language;
	this.doctype = doctype;
	this.code = null;
	this.localflag = (local) ? LT_LOCAL : LT_GLOBAL;
    }
    
    public plasmaWordIndexEntry(String urlHash, String code) {
        // the code is not parsed but used later on
        this.urlHash = urlHash;
        this.count = 0;
        this.posintext = 0;
        this.posinphrase = 0;
        this.posofphrase = 0;
        this.age = 0;
        this.quality = 0;
        this.language = "uk";
        this.doctype = 'u';
        this.code = code;
        this.localflag = LT_LOCAL;
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
       this.count = (int) serverCodings.enhancedCoder.decodeBase64Long(pr.getProperty("c", "A"));
       this.posintext = (int) serverCodings.enhancedCoder.decodeBase64Long(pr.getProperty("t", "__"));
       this.posinphrase = (int) serverCodings.enhancedCoder.decodeBase64Long(pr.getProperty("r", "__"));
       this.posofphrase = (int) serverCodings.enhancedCoder.decodeBase64Long(pr.getProperty("o", "__"));
       this.age = (int) serverCodings.enhancedCoder.decodeBase64Long(pr.getProperty("a", "A"));
       this.quality = (int) serverCodings.enhancedCoder.decodeBase64Long(pr.getProperty("q", "__"));
       this.language = pr.getProperty("l", "uk");
       this.doctype = pr.getProperty("d", "u").charAt(0);
       this.localflag = pr.getProperty("f", ""+LT_LOCAL).charAt(0);
       // clear code
       this.code = null;
    }

   private String b64save(long x, int l) {
	try {
	    return serverCodings.enhancedCoder.encodeBase64Long(x, l);
	} catch (Exception e) {
	    // if x does not fit into l
	    return "________".substring(0, l);
	}
    }
    
    public String toEncodedForm(boolean longAttr) {
	if (code == null) {
            String shortAttr =
                b64save(quality, plasmaCrawlLURL.urlQualityLength) +
		b64save(age, 3) +
		b64save(count, 2) +
		language +
		doctype +
		localflag; // 3 + 3 + 2 + 2 + 1 + 1 = 12 bytes
            if (longAttr)
              return 
		shortAttr +
                b64save(posintext, 2) +
                b64save(posinphrase, 2) +
                b64save(posofphrase, 2);
	    // 12 + 3 + 2 + 2 + 1 + 1 = 12 bytes
            else
              return shortAttr;
		
	} else {
	    return code;
	}
    }
    
    public String toExternalForm() {
	if (code == null) {
            return "{" +
                "h=" + urlHash +
                ",q=" + b64save(quality, plasmaCrawlLURL.urlQualityLength) +
		",a=" + b64save(age, 3) +
		",c=" + b64save(count, 2) +
		",l=" + language +
		",d=" + doctype +
		",f=" + localflag +
                ",t=" + b64save(posintext, 2) +
                ",r=" + b64save(posinphrase, 2) +
                ",o=" + b64save(posofphrase, 2) +
                "}";
	} else {
	    return "{" +
                "h=" + urlHash +
                ",q=" + code.substring(0, 3) +
		",a=" + code.substring(3, 6) +
		",c=" + code.substring(6, 8) +
		",l=" + code.substring(8, 10) +
		",d=" + code.charAt(10) +
		",f=" + code.charAt(11) +
                ((code.length() > 12) ? (
                ",t=" + code.substring(12, 14) +
                ",r=" + code.substring(14, 16) +
                ",o=" + code.substring(16, 18)
                ) : "") +
                "}";
	}
    }
        
    public String getUrlHash() {
	return urlHash;
    }
    
    public int getQuality() {
	if (code == null) return quality;
	else return (int) serverCodings.enhancedCoder.decodeBase64Long(code.substring(0, 3));
    }

    public int getVirtualAge() {
	if (code == null) return age;
	else return (int) serverCodings.enhancedCoder.decodeBase64Long(code.substring(3, 6));
    }
    
    public int getCount() {
	if (code == null) return count;
	else return (int) serverCodings.enhancedCoder.decodeBase64Long(code.substring(6, 8));
    }

    public int posintext() {
        if (code == null) return posintext;
        if (code.length() >= 14)
            return (int) serverCodings.enhancedCoder.decodeBase64Long(code.substring(12, 14));
        else
            return 0;
    }

    public int posinphrase() {
        if (code == null) return posinphrase;
        if (code.length() >= 15)
            return (int) serverCodings.enhancedCoder.decodeBase64Long(code.substring(14, 16));
        else
            return 0;
    }

    public int posofphrase() {
        if (code == null) return posofphrase;
        if (code.length() >= 16)
            return (int) serverCodings.enhancedCoder.decodeBase64Long(code.substring(16, 18));
        else
            return 0;
    }
    
    public String getLanguage() {
	if (code == null) return language;
	else return code.substring(8, 10);
    }

    public char getType() {
	if (code == null) return doctype;
	else return code.charAt(10);
    }

    public boolean isLocal() {
        if (code == null) return localflag == LT_LOCAL;
	else return code.charAt(11) == LT_LOCAL;
    }

    public static void main(String[] args) {
	// outputs the word hash to a given word
	if (args.length != 1) System.exit(0);
	System.out.println("WORDHASH: " + word2hash(args[0]));
    }
    
}
