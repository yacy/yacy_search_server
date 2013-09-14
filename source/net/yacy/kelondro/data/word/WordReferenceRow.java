// WordReferenceRow.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 20.05.2006 on http://yacy.net
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

package net.yacy.kelondro.data.word;

import java.util.ArrayList;
import java.util.Collection;

import net.yacy.cora.date.MicroDate;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.ByteArray;
import net.yacy.kelondro.index.Column;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.rwi.AbstractReference;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.util.Bitfield;

/**
 * this object stores attributes to URL references inside RWI collections
 *
 */
public final class WordReferenceRow extends AbstractReference implements WordReference, Cloneable {


    public static final Row urlEntryRow = new Row(new Column[]{
            new Column("h", Column.celltype_string,    Column.encoder_bytes, Word.commonHashLength, "urlhash"),
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

    /**
	 * object for termination of concurrent blocking queue processing
	 */
    protected static final Row.Entry poisonRowEntry = urlEntryRow.newEntry();
    
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

    protected WordReferenceRow(
            final byte[]   urlHash,
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
            final byte[]   language,      // (guessed) language of document
            final char     doctype,       // type of document
            final int      outlinksSame,  // outlinks to same domain
            final int      outlinksOther, // outlinks to other domain
            final Bitfield flags  // attributes to the url and to the word according the url
    ) {

        assert (urlHash.length == 12) : "urlhash = " + ASCII.String(urlHash);
        this.entry = urlEntryRow.newEntry();
        final int mddlm = MicroDate.microDateDays(lastmodified);
        final int mddct = MicroDate.microDateDays(updatetime);
        this.entry.setCol(col_urlhash, urlHash);
        this.entry.setCol(col_lastModified, mddlm);
        this.entry.setCol(col_freshUntil, Math.max(0, mddlm + (mddct - mddlm) * 2)); // TTL computation
        this.entry.setCol(col_wordsInTitle, titleLength / 6); // word count estimation; TODO: change value handover to number of words
        this.entry.setCol(col_wordsInText, wordcount);
        this.entry.setCol(col_phrasesInText, phrasecount);
        this.entry.setCol(col_doctype, new byte[]{(byte) doctype});
        this.entry.setCol(col_language, (language == null || language.length != urlEntryRow.width(col_language)) ? WordReferenceVars.default_language : language);
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

    public WordReferenceRow(final byte[]   urlHash,
                            final int      urlLength,     // byte-length of complete URL
                            final int      urlComps,      // number of path components
                            final int      titleLength,   // length of description/length (longer are better?)
                            final int      wordcount,     // total number of words
                            final int      phrasecount,   // total number of phrases
                            final long     lastmodified,  // last-modified time of the document where word appears
                            final long     updatetime,    // update time; this is needed to compute a TTL for the word, so it can be removed easily if the TTL is short
                            final byte[]   language,      // (guessed) language of document
                            final char     doctype,       // type of document
                            final int      outlinksSame,  // outlinks to same domain
                            final int      outlinksOther  // outlinks to other domain
                    ) {
                        assert (urlHash.length == 12) : "urlhash = " + ASCII.String(urlHash);
                        this.entry = urlEntryRow.newEntry();
                        final int mddlm = MicroDate.microDateDays(lastmodified);
                        final int mddct = MicroDate.microDateDays(updatetime);
                        this.entry.setCol(col_urlhash, urlHash);
                        this.entry.setCol(col_lastModified, mddlm);
                        this.entry.setCol(col_freshUntil, Math.max(0, mddlm + (mddct - mddlm) * 2)); // TTL computation
                        this.entry.setCol(col_wordsInTitle, titleLength / 6); // word count estimation; TODO: change value handover to number of words
                        this.entry.setCol(col_wordsInText, wordcount);
                        this.entry.setCol(col_phrasesInText, phrasecount);
                        this.entry.setCol(col_doctype, new byte[]{(byte) doctype});
                        this.entry.setCol(col_language, ((language == null) || (language.length != urlEntryRow.width(col_language))) ? WordReferenceVars.default_language : language);
                        this.entry.setCol(col_llocal, outlinksSame);
                        this.entry.setCol(col_lother, outlinksOther);
                        this.entry.setCol(col_urlLength, urlLength);
                        this.entry.setCol(col_urlComps, urlComps);
                        this.entry.setCol(col_reserve1, 0);
                        this.entry.setCol(col_reserve2, 0);
                    }

    public void setWord(final Word word) {
                        this.entry.setCol(col_typeofword, new byte[]{(byte) 0});
                        this.entry.setCol(col_flags, word.flags.bytes());
                        this.entry.setCol(col_hitcount, word.count);
                        this.entry.setCol(col_posintext, word.posInText);
                        this.entry.setCol(col_posinphrase, word.posInPhrase);
                        this.entry.setCol(col_posofphrase, word.numOfPhrase);
    }
    
    public WordReferenceRow(final String external) {
        this.entry = urlEntryRow.newEntry(external, true);
    }

    private WordReferenceRow(final byte[] row) {
        this.entry = urlEntryRow.newEntry(row);
    }

    protected WordReferenceRow(final Row.Entry rentry) {
        // no cloning is necessary since there is no further manipulation after this initial instantiation
        this.entry = rentry;
    }

    @Override
    public WordReferenceRow clone() {
        final byte[] b = new byte[urlEntryRow.objectsize];
        System.arraycopy(this.entry.bytes(), 0, b, 0, urlEntryRow.objectsize);
        return new WordReferenceRow(b);
    }

    @Override
    public String toPropertyForm() {
        return this.entry.toPropertyForm('=', true, true, false, false);
    }

    @Override
    public Entry toKelondroEntry() {
        return this.entry;
    }

    @Override
    public byte[] urlhash() {
        return this.entry.getColBytes(col_urlhash, true);
    }

    @Override
    public int virtualAge() {
        return (int) this.entry.getColLong(col_lastModified);  // this is the time in MicoDateDays format
    }

    @Override
    public long lastModified() {
        return MicroDate.reverseMicroDateDays((int) this.entry.getColLong(col_lastModified));
    }

    @Override
    public int hitcount() {
        return (0xff & this.entry.getColByte(col_hitcount));
    }

    @Override
    public Collection<Integer> positions() {
        return new ArrayList<Integer>(0);
    }

    @Override
    public int posinphrase() {
        return (0xff & this.entry.getColByte(col_posinphrase));
    }

    @Override
    public int posofphrase() {
        return (0xff & this.entry.getColByte(col_posofphrase));
    }

    @Override
    public int wordsintext() {
        return (int) this.entry.getColLong(col_wordsInText);
    }

    @Override
    public int phrasesintext() {
        return (int) this.entry.getColLong(col_phrasesInText);
    }

    @Override
    public byte[] getLanguage() {
        return this.entry.getColBytes(col_language, true);
    }

    @Override
    public char getType() {
        return (char) this.entry.getColByte(col_doctype);
    }

    @Override
    public int wordsintitle() {
        return (0xff & this.entry.getColByte(col_wordsInTitle));
    }

    @Override
    public int llocal() {
        return (0xff & this.entry.getColByte(col_llocal));
    }

    @Override
    public int lother() {
        return (0xff & this.entry.getColByte(col_lother));
    }

    @Override
    public int urllength() {
        return (0xff & this.entry.getColByte(col_urlLength));
    }

    @Override
    public int urlcomps() {
        return (0xff & this.entry.getColByte(col_urlComps));
    }

    @Override
    public Bitfield flags() {
        return new Bitfield(this.entry.getColBytes(col_flags, false));
    }

    @Override
    public double termFrequency() {
        return (((double) hitcount()) / ((double) (wordsintext() + wordsintitle() + 1)));
    }

    @Override
    public String toString() {
        return toPropertyForm();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof WordReferenceRow)) return false;
        final WordReferenceRow other = (WordReferenceRow) obj;
        return Base64Order.enhancedCoder.equal(urlhash(), other.urlhash());
    }

    private int hashCache = Integer.MIN_VALUE; // if this is used in a compare method many times, a cache is useful

    @Override
    public int hashCode() {
        if (this.hashCache == Integer.MIN_VALUE) {
            this.hashCache = ByteArray.hashCode(urlhash());
        }
        return this.hashCache;
    }

    @Override
    public void join(final Reference oe) {
        throw new UnsupportedOperationException("");

    }

    @Override
    public String hosthash() {
        return ASCII.String(this.urlhash(), 6, 6);
    }


}
