// ReferenceRow.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.05.2006 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-03-20 16:44:59 +0100 (Fr, 20 Mrz 2009) $
// $LastChangedRevision: 5736 $
// $LastChangedBy: borg-0300 $
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

package de.anomic.kelondro.text.referencePrototype;

import de.anomic.kelondro.index.Column;
import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.Row.Entry;
import de.anomic.kelondro.order.Base64Order;
import de.anomic.kelondro.order.Bitfield;
import de.anomic.kelondro.order.MicroDate;
import de.anomic.kelondro.text.AbstractReference;
import de.anomic.kelondro.text.Reference;
import de.anomic.yacy.yacySeedDB;

public final class WordReferenceRow extends AbstractReference implements WordReference, Cloneable {

    // this object stores attributes to URL references inside RWI collections

    public static final Row urlEntryRow = new Row(new Column[]{
            new Column("h", Column.celltype_string,    Column.encoder_bytes, yacySeedDB.commonHashLength, "urlhash"),
            new Column("a", Column.celltype_cardinal,  Column.encoder_b256,  2, "lastModified"),
            new Column("s", Column.celltype_cardinal,  Column.encoder_b256,  2, "freshUntil"),
            new Column("u", Column.celltype_cardinal,  Column.encoder_b256,  1, "wordsInTitle"),
            new Column("w", Column.celltype_cardinal,  Column.encoder_b256,  2, "wordsInText"),
            new Column("p", Column.celltype_cardinal,  Column.encoder_b256,  2, "phrasesInText"),
            new Column("d", Column.celltype_binary,    Column.encoder_bytes, 1, "doctype"),
            new Column("l", Column.celltype_string,    Column.encoder_bytes, 2, "language"),
            new Column("x", Column.celltype_cardinal,  Column.encoder_b256,  1, "llocal"),
            new Column("y", Column.celltype_cardinal,  Column.encoder_b256,  1, "lother"),
            new Column("m", Column.celltype_cardinal,  Column.encoder_b256,  1, "urlLength"),
            new Column("n", Column.celltype_cardinal,  Column.encoder_b256,  1, "urlComps"),
            new Column("g", Column.celltype_binary,    Column.encoder_bytes, 1, "typeofword"),
            new Column("z", Column.celltype_bitfield,  Column.encoder_bytes, 4, "flags"),
            new Column("c", Column.celltype_cardinal,  Column.encoder_b256,  1, "hitcount"),
            new Column("t", Column.celltype_cardinal,  Column.encoder_b256,  2, "posintext"),
            new Column("r", Column.celltype_cardinal,  Column.encoder_b256,  1, "posinphrase"),
            new Column("o", Column.celltype_cardinal,  Column.encoder_b256,  1, "posofphrase"),
            new Column("i", Column.celltype_cardinal,  Column.encoder_b256,  1, "worddistance"),
            new Column("k", Column.celltype_cardinal,  Column.encoder_b256,  1, "reserve")
        },
        Base64Order.enhancedCoder
    );
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
    private static final int col_reserve1      = 18; // i  1 reserve1
    private static final int col_reserve2      = 19; // k  1 reserve2

    // appearance flags, used in RWI entry
    // some names are derived from the Dublin Core Metadata tag set
    // the flags 0..23 are identical to the category flags in plasmaCondenser
    public  static final int flag_app_dc_description= 24; // word appears in anchor description text (the reference to an url), or any alternative text field of a link
    public  static final int flag_app_dc_title      = 25; // word appears in title or headline or any description part
    public  static final int flag_app_dc_creator    = 26; // word appears in author
    public  static final int flag_app_dc_subject    = 27; // word appears in header tags or other descriptive part
    public  static final int flag_app_dc_identifier = 28; // word appears in url or document identifier
    public  static final int flag_app_emphasized    = 29; // word is emphasized in text (i.e. bold, italics, special size)

    private final Row.Entry entry;
    
    public WordReferenceRow(final String  urlHash,
            final int      urlLength,     // byte-length of complete URL
            final int      urlComps,      // number of path components
            final int      titleLength,   // length of description/length (longer are better?)
            final int      hitcount,      // how often appears this word in the text
            final int      wordcount,     // total number of words
            final int      phrasecount,   // total number of phrases
            final int      posintext,     // position of word in all words
            final int      posinphrase,   // position of word in its phrase
            final int      posofphrase,   // number of the phrase where word appears
            final long     lastmodified,  // last-modified time of the document where word appears
            final long     updatetime,    // update time; this is needed to compute a TTL for the word, so it can be removed easily if the TTL is short
            final String   language,      // (guessed) language of document
            final char     doctype,       // type of document
            final int      outlinksSame,  // outlinks to same domain
            final int      outlinksOther, // outlinks to other domain
            final Bitfield flags  // attributes to the url and to the word according the url
    ) {

        assert (urlHash.length() == 12) : "urlhash = " + urlHash;
        this.entry = urlEntryRow.newEntry();
        final int mddlm = MicroDate.microDateDays(lastmodified);
        final int mddct = MicroDate.microDateDays(updatetime);
        this.entry.setCol(col_urlhash, urlHash, null);
        this.entry.setCol(col_lastModified, mddlm);
        this.entry.setCol(col_freshUntil, Math.max(0, mddlm + (mddct - mddlm) * 2)); // TTL computation
        this.entry.setCol(col_wordsInTitle, titleLength / 6); // word count estimation; TODO: change value handover to number of words
        this.entry.setCol(col_wordsInText, wordcount);
        this.entry.setCol(col_phrasesInText, phrasecount);
        this.entry.setCol(col_doctype, new byte[]{(byte) doctype});
        this.entry.setCol(col_language, ((language == null) || (language.length() != urlEntryRow.width(col_language))) ? "uk" : language, null);
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
        this.entry.setCol(col_reserve1, 0);
        this.entry.setCol(col_reserve2, 0);
    }
    
    public WordReferenceRow(final String urlHash, final String code) {
        // the code is the external form of the row minus the leading urlHash entry
        this.entry = urlEntryRow.newEntry((urlHash + code).getBytes());
    }
    
    public WordReferenceRow(final String external) {
        this.entry = urlEntryRow.newEntry(external, true);
    }
    
    public WordReferenceRow(final byte[] row) {
        this.entry = urlEntryRow.newEntry(row);
    }
    
    public WordReferenceRow(final byte[] row, final int offset, final boolean clone) {
        this.entry = urlEntryRow.newEntry(row, offset, clone);
    }
    
    public WordReferenceRow(final Row.Entry rentry) {
        // FIXME: see if cloning is necessary
        this.entry = rentry;
    }
    
    public WordReferenceRow clone() {
        final byte[] b = new byte[urlEntryRow.objectsize];
        System.arraycopy(entry.bytes(), 0, b, 0, urlEntryRow.objectsize);
        return new WordReferenceRow(b);
    }

    public String toPropertyForm() {
        return entry.toPropertyForm(true, true, false);
    }
    
    public Entry toKelondroEntry() {
        return this.entry;
    }

    public String metadataHash() {
        return this.entry.getColString(col_urlhash, null);
    }

    public int virtualAge() {
        return (int) this.entry.getColLong(col_lastModified);  // this is the time in MicoDateDays format
    }

    public long lastModified() {
        return MicroDate.reverseMicroDateDays((int) this.entry.getColLong(col_lastModified));
    }
    
    public long freshUntil() {
        return MicroDate.reverseMicroDateDays((int) this.entry.getColLong(col_freshUntil));
    }

    public int hitcount() {
        return (int) this.entry.getColLong(col_hitcount);
    }

    public int positions() {
        return 1;
    }

    public int position(int p) {
        assert p == 0 : "p = " + p;
        return (int) this.entry.getColLong(col_posintext);
    }

    public void addPosition(int position) {
        throw new UnsupportedOperationException("");
        
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
    
    public Bitfield flags() {
        return new Bitfield(this.entry.getColBytes(col_flags));
    }
    
    public double termFrequency() {
        return (((double) this.hitcount()) / ((double) (this.wordsintext() + this.wordsintitle() + 1)));
    }
    
    public String toString() {
        return toPropertyForm();
    }

    public boolean isNewer(final Reference other) {
        if (other == null) return true;
        if (this.lastModified() > other.lastModified()) return true;
        return false;
    }
 
    public boolean isOlder(final Reference other) {
        if (other == null) return false;
        if (this.lastModified() < other.lastModified()) return true;
        return false;
    }
    
    public int hashCode() {
        return this.metadataHash().hashCode();
    }

    public void join(Reference oe) {
        throw new UnsupportedOperationException("");
        
    }
}
