// indexURLEntryNew.java 
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 21.07.2006 on http://www.anomic.de
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


package de.anomic.index;

import de.anomic.kelondro.kelondroColumn;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRow.Entry;
import de.anomic.plasma.plasmaWordIndex;

public class indexURLEntry implements Cloneable, indexEntry {

    public static kelondroRow urlEntryRow = new kelondroRow(new kelondroColumn[]{
            new kelondroColumn("h", kelondroColumn.celltype_string,    kelondroColumn.encoder_bytes, indexURL.urlHashLength, "urlhash"),
            new kelondroColumn("q", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b64e, indexURL.urlQualityLength, "quality"),
            new kelondroColumn("a", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b64e, 3, "lastModified"),
            new kelondroColumn("c", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b64e, 2, "hitcount"),
            new kelondroColumn("l", kelondroColumn.celltype_string,    kelondroColumn.encoder_bytes, indexURL.urlLanguageLength, "language"),
            new kelondroColumn("d", kelondroColumn.celltype_binary,    kelondroColumn.encoder_bytes, 1, "doctype"),
            new kelondroColumn("f", kelondroColumn.celltype_binary,    kelondroColumn.encoder_bytes, 1, "localflag"),
            new kelondroColumn("t", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b64e, 2, "posintext"),
            new kelondroColumn("r", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b64e, 2, "posinphrase"),
            new kelondroColumn("o", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b64e, 2, "posofphrase"),
            new kelondroColumn("i", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b64e, 2, "worddistance"),
            new kelondroColumn("w", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b64e, 2, "wordcount"),
            new kelondroColumn("p", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b64e, 2, "phrasecount")
    });

    private static final int col_urlhash      =  0;
    private static final int col_quality      =  1;
    private static final int col_lastModified =  2;
    private static final int col_hitcount     =  3;
    private static final int col_language     =  4;
    private static final int col_doctype      =  5;
    private static final int col_localflag    =  6;
    private static final int col_posintext    =  7;
    private static final int col_posinphrase  =  8;
    private static final int col_posofphrase  =  9;
    private static final int col_worddistance = 10;
    private static final int col_wordcount    = 11;
    private static final int col_phrasecount  = 12;
    
    
    private kelondroRow.Entry entry;
    
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
            int     worddistance, //*word distance; this is 0 by default, and set to the difference of posintext from two indexes if these are combined (simultanous search). If stored, this shows that the result was obtained by remote search
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
        // - boolean: appearance attributes: title, appears in header, anchor-descr, image-tag, hervorhebungen, meta-tags, word in link etc
        // - boolean: URL attributes

        if ((language == null) || (language.length() != indexURL.urlLanguageLength)) language = "uk";
        this.entry = urlEntryRow.newEntry();
        this.entry.setCol(col_urlhash, urlHash, null);
        this.entry.setCol(col_quality, quality);
        this.entry.setCol(col_lastModified, lastmodified);
        this.entry.setCol(col_hitcount, hitcount);
        this.entry.setCol(col_language, language, null);
        this.entry.setCol(col_doctype, (byte) doctype);
        this.entry.setCol(col_localflag, (byte) ((local) ? indexEntryAttribute.LT_LOCAL : indexEntryAttribute.LT_GLOBAL));
        this.entry.setCol(col_posintext, posintext);
        this.entry.setCol(col_posinphrase, posinphrase);
        this.entry.setCol(col_posofphrase, posofphrase);
        this.entry.setCol(col_worddistance, worddistance);
        this.entry.setCol(col_wordcount, wordcount);
        this.entry.setCol(col_phrasecount, phrasecount);
        //System.out.println("DEBUG-NEWENTRY " + toPropertyForm());
    }

    public indexURLEntry(String urlHash, String code) {
        // the code is the external form of the row minus the leading urlHash entry
        this.entry = urlEntryRow.newEntry((urlHash + code).getBytes());
    }
    
    public indexURLEntry(String external) {
        this.entry = urlEntryRow.newEntry(external);
    }
    
    public indexURLEntry(byte[] row) {
        this.entry = urlEntryRow.newEntry(row);
    }
    
    public indexURLEntry(kelondroRow.Entry rentry) {
        // FIXME: see if cloning is necessary
        this.entry = rentry;
    }
    
    public Object clone() {
        byte[] b = new byte[urlEntryRow.objectsize()];
        System.arraycopy(entry.bytes(), 0, b, 0, urlEntryRow.objectsize());
        return new indexURLEntry(b);
    }
    
    public static int encodedByteArrayFormLength(boolean includingHeader) {
        // the size of the index entry attributes when encoded to string
        return (includingHeader) ? urlEntryRow.objectsize() : urlEntryRow.objectsize() - indexURL.urlHashLength;
    }
    
    public byte[] toEncodedByteArrayForm(boolean includeHash) {
        if (includeHash) return entry.bytes();
        byte[] b = new byte[urlEntryRow.objectsize() - indexURL.urlHashLength];
        System.arraycopy(entry.bytes(), indexURL.urlHashLength, b, 0, b.length);
        return b;
    }

   public String toPropertyForm(boolean displayFormat) {
        return entry.toPropertyForm(true, displayFormat, displayFormat);
    }

    public Entry toKelondroEntry() {
        return this.entry;
    }

    public String urlHash() {
        return this.entry.getColString(col_urlhash, null);
    }

    public int quality() {
        return (int) this.entry.getColLong(col_quality);
    }

    public int virtualAge() {
        return plasmaWordIndex.microDateDays(lastModified()); 
    }

    public long lastModified() {
        return (int) this.entry.getColLong(col_lastModified);
    }

    public int hitcount() {
        return (int) this.entry.getColLong(col_hitcount);
    }

    public int posintext() {
        return (int) this.entry.getColLong(col_posintext);
    }

    public int posinphrase() {
        return (int) this.entry.getColLong(col_posinphrase);
    }

    public int posofphrase() {
        return (int) this.entry.getColLong(col_posofphrase);
    }

    public int wordcount() {
        return (int) this.entry.getColLong(col_wordcount);
    }

    public int phrasecount() {
        return (int) this.entry.getColLong(col_phrasecount);
    }

    public String getLanguage() {
        return this.entry.getColString(col_language, null);
    }

    public char getType() {
        return (char) this.entry.getColByte(col_doctype);
    }

    public boolean isLocal() {
        return this.entry.getColByte(col_localflag) == indexEntryAttribute.LT_LOCAL;
    }
    
    public static indexURLEntry combineDistance(indexURLEntry ie1, indexEntry ie2) {
        // returns a modified entry of the first argument
        ie1.entry.setCol(col_worddistance, ie1.worddistance() + ie2.worddistance() + Math.abs(ie1.posintext() - ie2.posintext()));
        ie1.entry.setCol(col_posintext, Math.min(ie1.posintext(), ie2.posintext()));
        ie1.entry.setCol(col_posinphrase, (ie1.posofphrase() == ie2.posofphrase()) ? ie1.posofphrase() : 0 /*unknown*/);
        ie1.entry.setCol(col_posofphrase, Math.min(ie1.posofphrase(), ie2.posofphrase()));
        ie1.entry.setCol(col_wordcount, (ie1.wordcount() + ie2.wordcount()) / 2);
        return ie1;
    }
    
     public void combineDistance(indexEntry oe) {
        combineDistance(this, oe);
    }

    public int worddistance() {
        return (int) this.entry.getColLong(col_worddistance);
    }
    
    public static final void min(indexURLEntry t, indexEntry other) {
        if (t.hitcount() > other.hitcount()) t.entry.setCol(col_hitcount, other.hitcount());
        if (t.wordcount() > other.wordcount()) t.entry.setCol(col_wordcount, other.wordcount());
        if (t.phrasecount() > other.phrasecount()) t.entry.setCol(col_phrasecount, other.phrasecount());
        if (t.posintext() > other.posintext()) t.entry.setCol(col_posintext, other.posintext());
        if (t.posinphrase() > other.posinphrase()) t.entry.setCol(col_posinphrase, other.posinphrase());
        if (t.posofphrase() > other.posofphrase()) t.entry.setCol(col_posofphrase, other.posofphrase());
        if (t.worddistance() > other.worddistance()) t.entry.setCol(col_worddistance, other.worddistance());
        if (t.lastModified() > other.lastModified()) t.entry.setCol(col_lastModified, other.lastModified());
        if (t.quality() > other.quality()) t.entry.setCol(col_quality, other.quality());
    }
    
    public static final void max(indexURLEntry t, indexEntry other) {
        if (t.hitcount() < other.hitcount()) t.entry.setCol(col_hitcount, other.hitcount());
        if (t.wordcount() < other.wordcount()) t.entry.setCol(col_wordcount, other.wordcount());
        if (t.phrasecount() < other.phrasecount()) t.entry.setCol(col_phrasecount, other.phrasecount());
        if (t.posintext() < other.posintext()) t.entry.setCol(col_posintext, other.posintext());
        if (t.posinphrase() < other.posinphrase()) t.entry.setCol(col_posinphrase, other.posinphrase());
        if (t.posofphrase() < other.posofphrase()) t.entry.setCol(col_posofphrase, other.posofphrase());
        if (t.worddistance() < other.worddistance()) t.entry.setCol(col_worddistance, other.worddistance());
        if (t.lastModified() < other.lastModified()) t.entry.setCol(col_lastModified, other.lastModified());
        if (t.quality() < other.quality()) t.entry.setCol(col_quality, other.quality());
    }
    
    
    public void min(indexEntry other) {
        min(this, other);
    }

    public void max(indexEntry other) {
        max(this, other);
    }

    static void normalize(indexURLEntry t, indexEntry min, indexEntry max) {
        if (1 + max.worddistance() - min.worddistance() == 0) System.out.println("min = " + min.toPropertyForm(true) + "\nmax=" + max.toPropertyForm(true));
        //System.out.println("Normalize:\nentry = " + t.toPropertyForm(true));
        //System.out.println("min   = " + min.toPropertyForm(true));
        //System.out.println("max   = " + max.toPropertyForm(true));
        t.entry.setCol(col_hitcount     , (t.hitcount()     == 0) ? 0 : 1 + 255 * (t.hitcount()     - min.hitcount()    ) / (1 + max.hitcount()     - min.hitcount()));
        t.entry.setCol(col_wordcount    , (t.wordcount()    == 0) ? 0 : 1 + 255 * (t.wordcount()    - min.wordcount()   ) / (1 + max.wordcount()    - min.wordcount()));
        t.entry.setCol(col_phrasecount  , (t.phrasecount()  == 0) ? 0 : 1 + 255 * (t.phrasecount()  - min.phrasecount() ) / (1 + max.phrasecount()  - min.phrasecount()));
        t.entry.setCol(col_posintext    , (t.posintext()    == 0) ? 0 : 1 + 255 * (t.posintext()    - min.posintext()   ) / (1 + max.posintext()    - min.posintext()));
        t.entry.setCol(col_posinphrase  , (t.posinphrase()  == 0) ? 0 : 1 + 255 * (t.posinphrase()  - min.posinphrase() ) / (1 + max.posinphrase()  - min.posinphrase()));
        t.entry.setCol(col_posofphrase  , (t.posofphrase()  == 0) ? 0 : 1 + 255 * (t.posofphrase()  - min.posofphrase() ) / (1 + max.posofphrase()  - min.posofphrase()));
        t.entry.setCol(col_worddistance , (t.worddistance() == 0) ? 0 : 1 + 255 * (t.worddistance() - min.worddistance()) / (1 + max.worddistance() - min.worddistance())); // FIXME: hier gibts ein division by zero, was nur sein kann wenn die Normalisierung nicht geklappt hat.
        t.entry.setCol(col_lastModified , (t.lastModified() == 0) ? 0 : 1 + 255 * (t.lastModified() - min.lastModified()) / (1 + max.lastModified() - min.lastModified()));
        t.entry.setCol(col_quality      , (t.quality()      == 0) ? 0 : 1 + 255 * (t.quality()      - min.quality()     ) / (1 + max.quality()      - min.quality()));
        //System.out.println("out   = " + t.toPropertyForm(true));
    }
    
    public void normalize(indexEntry min, indexEntry max) {
        normalize(this, min, max);
    }

    public indexEntry generateNormalized(indexEntry min, indexEntry max) {
        indexURLEntry e = (indexURLEntry) this.clone();
        e.normalize(min, max);
        return e;
    }
    
    public boolean isNewer(indexEntry other) {
        if (other == null) return true;
        if (this.lastModified() > other.lastModified()) return true;
        if (this.lastModified() == other.lastModified()) {
            if (this.quality() > other.quality()) return true;
        }
        return false;
    }
 
    public boolean isOlder(indexEntry other) {
        if (other == null) return false;
        if (this.lastModified() < other.lastModified()) return true;
        if (this.lastModified() == other.lastModified()) {
            if (this.quality() < other.quality()) return true;
        }
        return false;
    }

}
