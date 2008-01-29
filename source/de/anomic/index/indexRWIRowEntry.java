// indexRWIRowEntry.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.05.2006 on http://yacy.net
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

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.kelondro.kelondroColumn;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRow.Entry;
import de.anomic.plasma.plasmaWordIndex;
import de.anomic.yacy.yacySeedDB;

public final class indexRWIRowEntry implements indexRWIEntry {

    // this object stores attributes to URL references inside RWI collections

    public static kelondroRow urlEntryRow = new kelondroRow(new kelondroColumn[]{
            new kelondroColumn("h", kelondroColumn.celltype_string,    kelondroColumn.encoder_bytes, yacySeedDB.commonHashLength, "urlhash"),
            new kelondroColumn("a", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  2, "lastModified"),
            new kelondroColumn("s", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  2, "freshUntil"),
            new kelondroColumn("u", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "wordsInTitle"),
            new kelondroColumn("w", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  2, "wordsInText"),
            new kelondroColumn("p", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  2, "phrasesInText"),
            new kelondroColumn("d", kelondroColumn.celltype_binary,    kelondroColumn.encoder_bytes, 1, "doctype"),
            new kelondroColumn("l", kelondroColumn.celltype_string,    kelondroColumn.encoder_bytes, 2, "language"),
            new kelondroColumn("x", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "llocal"),
            new kelondroColumn("y", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "lother"),
            new kelondroColumn("m", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "urlLength"),
            new kelondroColumn("n", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "urlComps"),
            new kelondroColumn("g", kelondroColumn.celltype_binary,    kelondroColumn.encoder_bytes, 1, "typeofword"),
            new kelondroColumn("z", kelondroColumn.celltype_bitfield,  kelondroColumn.encoder_bytes, 4, "flags"),
            new kelondroColumn("c", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "hitcount"),
            new kelondroColumn("t", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  2, "posintext"),
            new kelondroColumn("r", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "posinphrase"),
            new kelondroColumn("o", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "posofphrase"),
            new kelondroColumn("i", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "worddistance"),
            new kelondroColumn("k", kelondroColumn.celltype_cardinal,  kelondroColumn.encoder_b256,  1, "reserve")
    },
    kelondroBase64Order.enhancedCoder,
    0);
    // available chars: b,e,j,q
    
    // static properties
    private static final int col_urlhash       =  0; // h 12 the url hash b64-encoded
    private static final int col_lastModified  =  1; // a  2 last-modified time of the document where word appears
    private static final int col_freshUntil    =  2; // s  2 TTL for the word, so it can be removed easily if the TTL is short
    private static final int col_wordsInTitle  =  3; // u  1 words in description/length (longer are better?)
    private static final int col_wordsInText   =  4; // w  2 total number of words in document
    private static final int col_phrasesInText =  5; // p  2 total number of phrases in document
    private static final int col_doctype       =  6; // d  1 type of document
    private static final int col_language      =  7; // l  2 (guessed) language of document
    private static final int col_llocal        =  8; // x  1 outlinks to same domain
    private static final int col_lother        =  9; // y  1 outlinks to other domain
    private static final int col_urlLength     = 10; // m  1 byte-length of complete URL
    private static final int col_urlComps      = 11; // n  1 number of path components

    // dynamic properties    
    private static final int col_typeofword    = 12; // g  1 grammatical classification
    private static final int col_flags         = 13; // z  4 b64-encoded appearance flags (24 bit, see definition below)
    private static final int col_hitcount      = 14; // c  1 number of occurrences of this word in text
    private static final int col_posintext     = 15; // t  2 first appearance of word in text
    private static final int col_posinphrase   = 16; // r  1 position of word in its phrase
    private static final int col_posofphrase   = 17; // o  1 number of the phrase where word appears
    private static final int col_worddistance  = 18; // i  1 initial zero; may be used as reserve: is filled during search
    private static final int col_reserve       = 19; // k  1 reserve

    private kelondroRow.Entry entry;
    
    public indexRWIRowEntry(String  urlHash,
            int      urlLength,     // byte-length of complete URL
            int      urlComps,      // number of path components
            int      titleLength,   // length of description/length (longer are better?)
            int      hitcount,      // how often appears this word in the text
            int      wordcount,     // total number of words
            int      phrasecount,   // total number of phrases
            int      posintext,     // position of word in all words
            int      posinphrase,   // position of word in its phrase
            int      posofphrase,   // number of the phrase where word appears
            int      worddistance,  // word distance; this is 0 by default, and set to the difference of posintext from two indexes if these are combined (simultanous search). If stored, this shows that the result was obtained by remote search
            int      sizeOfPage,    // # of bytes of the page TODO: not needed any more
            long     lastmodified,  // last-modified time of the document where word appears
            long     updatetime,    // update time; this is needed to compute a TTL for the word, so it can be removed easily if the TTL is short
            String   language,      // (guessed) language of document
            char     doctype,       // type of document
            int      outlinksSame,  // outlinks to same domain
            int      outlinksOther, // outlinks to other domain
            kelondroBitfield flags  // attributes to the url and to the word according the url
    ) {

        assert (urlHash.length() == 12) : "urlhash = " + urlHash;
        if ((language == null) || (language.length() != urlEntryRow.width(col_language))) language = "uk";
        this.entry = urlEntryRow.newEntry();
        int mddlm = plasmaWordIndex.microDateDays(lastmodified);
        int mddct = plasmaWordIndex.microDateDays(updatetime);
        this.entry.setCol(col_urlhash, urlHash, null);
        this.entry.setCol(col_lastModified, mddlm);
        this.entry.setCol(col_freshUntil, Math.max(0, mddlm + (mddct - mddlm) * 2)); // TTL computation
        this.entry.setCol(col_wordsInTitle, titleLength / 6); // word count estimation; TODO: change value handover to number of words
        this.entry.setCol(col_wordsInText, wordcount);
        this.entry.setCol(col_phrasesInText, phrasecount);
        this.entry.setCol(col_doctype, new byte[]{(byte) doctype});
        this.entry.setCol(col_language, language, null);
        this.entry.setCol(col_llocal, outlinksSame);
        this.entry.setCol(col_lother, outlinksOther);
        this.entry.setCol(col_urlLength, urlLength);
        this.entry.setCol(col_urlComps, urlComps);
        this.entry.setCol(col_typeofword, new byte[]{(byte) 0}); // TODO: grammatical classification
        this.entry.setCol(col_flags, flags.bytes());
        this.entry.setCol(col_hitcount, hitcount);
        this.entry.setCol(col_posintext, posintext);
        this.entry.setCol(col_posinphrase, posinphrase);
        this.entry.setCol(col_posofphrase, posofphrase);
        this.entry.setCol(col_worddistance, worddistance);
        this.entry.setCol(col_reserve, 0);
    }
    
    public indexRWIRowEntry(String urlHash, String code) {
        // the code is the external form of the row minus the leading urlHash entry
        this.entry = urlEntryRow.newEntry((urlHash + code).getBytes());
    }
    
    public indexRWIRowEntry(String external) {
        this.entry = urlEntryRow.newEntry(external, true);
    }
    
    public indexRWIRowEntry(byte[] row) {
        this.entry = urlEntryRow.newEntry(row);
    }
    
    public indexRWIRowEntry(byte[] row, int offset, boolean clone) {
        this.entry = urlEntryRow.newEntry(row, offset, clone);
    }
    
    public indexRWIRowEntry(kelondroRow.Entry rentry) {
        // FIXME: see if cloning is necessary
        this.entry = rentry;
    }
    
    public static int days(long time) {
        // calculates the number of days since 1.1.1970 and returns this as 4-byte array
        return (int) (time / 86400000);
    }
    
    public Object clone() {
        byte[] b = new byte[urlEntryRow.objectsize];
        System.arraycopy(entry.bytes(), 0, b, 0, urlEntryRow.objectsize);
        return new indexRWIRowEntry(b);
    }

    public String toPropertyForm() {
        return entry.toPropertyForm(true, true, false);
    }
    
    public Entry toKelondroEntry() {
        return this.entry;
    }

    public String urlHash() {
        return this.entry.getColString(col_urlhash, null);
    }

    public int quality() {
        return 0; // not used any more
    }

    public int virtualAge() {
        return (int) this.entry.getColLong(col_lastModified);  // this is the time in MicoDateDays format
    }

    public long lastModified() {
        return plasmaWordIndex.reverseMicroDateDays((int) this.entry.getColLong(col_lastModified));
    }
    
    public long freshUntil() {
        return plasmaWordIndex.reverseMicroDateDays((int) this.entry.getColLong(col_freshUntil));
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

    public int wordsintext() {
        return (int) this.entry.getColLong(col_wordsInText);
    }

    public int phrasesintext() {
        return (int) this.entry.getColLong(col_phrasesInText);
    }

    public String getLanguage() {
        return this.entry.getColString(col_language, null);
    }

    public char getType() {
        return (char) this.entry.getColByte(col_doctype);
    }

    public int wordsintitle() {
        return (int) this.entry.getColLong(col_wordsInTitle);
    }
    
    public int llocal() {
        return (int) this.entry.getColLong(col_llocal);
    }
    
    public int lother() {
        return (int) this.entry.getColLong(col_lother);
    }
    
    public int urllength() {
        return (int) this.entry.getColLong(col_urlLength);
    }
    
    public int urlcomps() {
        return (int) this.entry.getColLong(col_urlComps);
    }
    
    public kelondroBitfield flags() {
        return new kelondroBitfield(this.entry.getColBytes(col_flags));
    }
    
    public double termFrequency() {
        return (((double) this.hitcount()) / ((double) (this.wordsintext() + this.wordsintitle() + 1)));
    }
    
    public String toString() {
        return toPropertyForm();
    }
    
    public static indexRWIEntry combineDistance(indexRWIRowEntry ie1, indexRWIEntry ie2) {
        // returns a modified entry of the first argument
        ie1.entry.setCol(col_worddistance, ie1.worddistance() + ie2.worddistance() + Math.abs(ie1.posintext() - ie2.posintext()));
        ie1.entry.setCol(col_posintext, Math.min(ie1.posintext(), ie2.posintext()));
        ie1.entry.setCol(col_posinphrase, (ie1.posofphrase() == ie2.posofphrase()) ? ie1.posofphrase() : 0 /*unknown*/);
        ie1.entry.setCol(col_posofphrase, Math.min(ie1.posofphrase(), ie2.posofphrase()));
        ie1.entry.setCol(col_wordsInText, (ie1.wordsintext() + ie2.wordsintext()) / 2);
        return ie1;
    }
    
    public void combineDistance(indexRWIEntry oe) {
        combineDistance(this, oe);
    }

    public int worddistance() {
        return (int) this.entry.getColLong(col_worddistance);
    }
    
    public boolean isNewer(indexRWIEntry other) {
        if (other == null) return true;
        if (this.lastModified() > other.lastModified()) return true;
        if (this.lastModified() == other.lastModified()) {
            if (this.quality() > other.quality()) return true;
        }
        return false;
    }
 
    public boolean isOlder(indexRWIEntry other) {
        if (other == null) return false;
        if (this.lastModified() < other.lastModified()) return true;
        if (this.lastModified() == other.lastModified()) {
            if (this.quality() < other.quality()) return true;
        }
        return false;
    }
    
}