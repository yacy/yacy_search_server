// indexURLEntry.java 
// (C) 2004, 2005, 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2004 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
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

/* 
   This class defines the structures of an index entry for URLs
*/

package de.anomic.index;

import java.util.Properties;

import de.anomic.index.indexEntry;
import de.anomic.index.indexEntryAttribute;
import de.anomic.index.indexAbstractEntry;
import de.anomic.index.indexURL;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRow.Entry;
import de.anomic.plasma.plasmaWordIndex;

public final class indexURLEntry extends indexAbstractEntry implements Cloneable, indexEntry {

    
    // the class instantiation can only be done by a plasmaStore method
    // therefore they are all public
    public indexURLEntry(String  urlHash,
                                int     urlLength,    // byte-length of complete URL
                                int     urlComps,     // number of path components
                                int     titleLength,  // length of description/length (longer are better?)
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
                                int     outlinksSame, // outlinks to same domain
                                int     outlinksOther,// outlinks to other domain
                                boolean local         //*flag shows that this index was generated locally; othervise its from a remote peer
                               ) {

        // more needed attributes:
        // - boolean: appearance attributes: title, appears in header, anchor-descr, image-tag etc
        // - boolean: URL attributes
        
    if ((language == null) || (language.length() != indexURL.urlLanguageLength)) language = "uk";
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
        this.localflag = (local) ? indexEntryAttribute.LT_LOCAL : indexEntryAttribute.LT_GLOBAL;
    }
    
    public indexURLEntry(String urlHash, String code) {
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
    
    public indexURLEntry(String external) {
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
       this.localflag = pr.getProperty("f", ""+indexEntryAttribute.LT_LOCAL).charAt(0);
    }
    
    public Object clone() {
        return new indexURLEntry(this.toPropertyForm());
    }
    
    public static int encodedStringFormLength() {
        // the size of the index entry attributes when encoded to string
        return 24;
    }
    
    public String toEncodedStringForm() {
       // attention: this integrates NOT the URL hash into the encoding
       // if you need a complete dump, use toExternalForm()
       StringBuffer buf = new StringBuffer(encodedStringFormLength());
       
       buf.append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.quality, indexURL.urlQualityLength))
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
    
    public static int encodedByteArrayFormLength() {
        // the size of the index entry attributes when encoded to string
        return encodedStringFormLength();
    }
    
    public byte[] toEncodedByteArrayForm() {
        return toEncodedStringForm().getBytes();
    }

    public Entry toKelondroEntry() {
        kelondroRow.Entry entry = indexURLEntryNew.urlEntryRow.newEntry(toEncodedByteArrayForm());
        return entry;
    }
    
    public String toPropertyForm() {
       StringBuffer str = new StringBuffer(61);
       
       str.append("{")
           .append( "h=").append(this.urlHash)
           .append(",q=").append(kelondroBase64Order.enhancedCoder.encodeLongSmart(this.quality, indexURL.urlQualityLength))
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
    
    public static void main(String[] args) {
        // outputs the word hash to a given word
        if (args.length != 1) System.exit(0);
        System.out.println("WORDHASH: " + indexEntryAttribute.word2hash(args[0]));
    }
   
}
