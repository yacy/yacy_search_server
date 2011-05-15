// ImageReferenceRow.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 21.01.2010 on http://yacy.net
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

package net.yacy.kelondro.data.image;

import java.util.ArrayList;
import java.util.Collection;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Column;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.order.MicroDate;
import net.yacy.kelondro.rwi.AbstractReference;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.util.ByteArray;

/**
 * this object stores attributes to URL references inside RWI collections
 *
 */
public final class ImageReferenceRow extends AbstractReference implements /*ImageReference,*/ Cloneable {

	/**
	 * object for termination of concurrent blocking queue processing
	 */
	public static final ImageReferenceRow poison = new ImageReferenceRow((Row.Entry) null);
    
    
    public static final Row urlEntryRow = new Row(new Column[]{
            new Column("h", Column.celltype_string,    Column.encoder_bytes, Word.commonHashLength, "urlhash"),
            new Column("f", Column.celltype_cardinal,  Column.encoder_b256,  4, "created"),
            new Column("m", Column.celltype_cardinal,  Column.encoder_b256,  4, "modified"),
            new Column("s", Column.celltype_cardinal,  Column.encoder_bytes, 4, "size-bytes"),
            new Column("d", Column.celltype_binary,    Column.encoder_bytes, 1, "doctype"),
            new Column("q", Column.celltype_binary,    Column.encoder_bytes, 1, "quality"),
            new Column("w", Column.celltype_cardinal,  Column.encoder_b256,  2, "width"), // pixels
            new Column("i", Column.celltype_cardinal,  Column.encoder_b256,  2, "height"), // pixels
            new Column("i", Column.celltype_cardinal,  Column.encoder_b256,  2, "iso"), // iso number
            new Column("i", Column.celltype_cardinal,  Column.encoder_b256,  2, "verschlusszeit"), // the x in 1/x
            new Column("i", Column.celltype_cardinal,  Column.encoder_b256,  2, "blende"), 
            new Column("i", Column.celltype_cardinal,  Column.encoder_b256,  4, "distance"),
            new Column("o", Column.celltype_cardinal,  Column.encoder_b256,  4, "author-id"),  // author, creator, operator, camera-number
            new Column("o", Column.celltype_cardinal,  Column.encoder_b256,  4, "group-id"),  // may be also a crawl start identifier
            new Column("o", Column.celltype_cardinal,  Column.encoder_b256,  4, "subgroupgroup-id"),  // may be also a pages-in-crawl identifier
            new Column("o", Column.celltype_cardinal,  Column.encoder_b256,  4, "counter-in-subgroup"), // may be also a counter of images on a page
            new Column("o", Column.celltype_cardinal,  Column.encoder_b256,  4, "location-lon-x"),
            new Column("a", Column.celltype_cardinal,  Column.encoder_b256,  4, "location-lat-y"),
            new Column("l", Column.celltype_cardinal,  Column.encoder_b256,  4, "location-alt-h"),
            new Column("t", Column.celltype_string,    Column.encoder_bytes, 4, "typeOfImage"), // a 4-stage taxonomy
            new Column("z", Column.celltype_bitfield,  Column.encoder_bytes, 4, "flags"),
            new Column("r", Column.celltype_binary,    Column.encoder_bytes, 3, "RGBAverage"),
            new Column("k", Column.celltype_cardinal,  Column.encoder_b256,  1, "reserve")
        },
        Base64Order.enhancedCoder
    );
    // available chars: b,e,j,q
    
    // static properties
    private static final int col_urlhash       =  0; // h 12 the url hash b64-encoded
    private static final int col_lastModified  =  1; // a  2 last-modified time of the document where word appears
    private static final int col_freshUntil    =  2; // s  2 TTL for the word, so it can be removed easily if the TTL is short
    private static final int col_doctype       =  6; // d  1 type of document
    private static final int col_urlLength     = 10; // m  1 byte-length of complete URL
    private static final int col_urlComps      = 11; // n  1 number of path components

    // dynamic properties
    //private static final int col_rgbaverage    = 12; // g  6 an average of the RGB values
    //private static final int col_typeofimage   = 12; // g  4 classification
    private static final int col_flags         = 13; // z  4 b64-encoded appearance flags (24 bit, see definition below)
    private static final int col_hitcount      = 14; // c  1 number of occurrences of this word in text
    private static final int col_posintext     = 15; // t  2 first appearance of word in text
    private static final int col_posinphrase   = 16; // r  1 position of word in its phrase
    private static final int col_posofphrase   = 17; // o  1 number of the phrase where word appears
    private static final int col_reserve1      = 18; // i  1 reserve1
    private static final int col_reserve2      = 19; // k  1 reserve2
    
    // ideas for the classification bytes
    // 0 : content-type (person-portrait, persons-group, landscape, buildings, technical, artistical)
    // 1 : content-situation (a categorization of the type, like: person/standing, building/factory, artistical/cubistic)
    // 2 : content-category (a classification that is taken from the text environment by text analysis)
    // 3 : 

    private final Row.Entry entry;
    
    public ImageReferenceRow(final byte[]  urlHash,
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

        assert (urlHash.length == 12) : "urlhash = " + urlHash;
        this.entry = urlEntryRow.newEntry();
        final int mddlm = MicroDate.microDateDays(lastmodified);
        final int mddct = MicroDate.microDateDays(updatetime);
        this.entry.setCol(col_urlhash, urlHash);
        this.entry.setCol(col_lastModified, mddlm);
        this.entry.setCol(col_freshUntil, Math.max(0, mddlm + (mddct - mddlm) * 2)); // TTL computation
        this.entry.setCol(col_doctype, new byte[]{(byte) doctype});
        this.entry.setCol(col_urlLength, urlLength);
        this.entry.setCol(col_urlComps, urlComps);
        this.entry.setCol(col_flags, flags.bytes());
        this.entry.setCol(col_hitcount, hitcount);
        this.entry.setCol(col_posintext, posintext);
        this.entry.setCol(col_posinphrase, posinphrase);
        this.entry.setCol(col_posofphrase, posofphrase);
        this.entry.setCol(col_reserve1, 0);
        this.entry.setCol(col_reserve2, 0);
    }
    
    public ImageReferenceRow(final byte[]  urlHash,
                            final int      urlLength,     // byte-length of complete URL
                            final int      urlComps,      // number of path components
                            final int      titleLength,   // length of description/length (longer are better?)
                            final int      wordcount,     // total number of words
                            final int      phrasecount,   // total number of phrases
                            final long     lastmodified,  // last-modified time of the document where word appears
                            final long     updatetime,    // update time; this is needed to compute a TTL for the word, so it can be removed easily if the TTL is short
                            final String   language,      // (guessed) language of document
                            final char     doctype,       // type of document
                            final int      outlinksSame,  // outlinks to same domain
                            final int      outlinksOther  // outlinks to other domain
                    ) {
                        assert (urlHash.length == 12) : "urlhash = " + UTF8.String(urlHash);
                        this.entry = urlEntryRow.newEntry();
                        final int mddlm = MicroDate.microDateDays(lastmodified);
                        final int mddct = MicroDate.microDateDays(updatetime);
                        this.entry.setCol(col_urlhash, urlHash);
                        this.entry.setCol(col_lastModified, mddlm);
                        this.entry.setCol(col_freshUntil, Math.max(0, mddlm + (mddct - mddlm) * 2)); // TTL computation
                        this.entry.setCol(col_doctype, new byte[]{(byte) doctype});
                        this.entry.setCol(col_urlLength, urlLength);
                        this.entry.setCol(col_urlComps, urlComps);
                        this.entry.setCol(col_reserve1, 0);
                        this.entry.setCol(col_reserve2, 0);
                    }
    
    public ImageReferenceRow(final String urlHash, final String code) {
        // the code is the external form of the row minus the leading urlHash entry
        this.entry = urlEntryRow.newEntry(UTF8.getBytes((urlHash + code)));
    }
    
    public ImageReferenceRow(final String external) {
        this.entry = urlEntryRow.newEntry(external, true);
    }
    
    public ImageReferenceRow(final byte[] row) {
        this.entry = urlEntryRow.newEntry(row);
    }
    
    public ImageReferenceRow(final byte[] row, final int offset, final boolean clone) {
        this.entry = urlEntryRow.newEntry(row, offset, clone);
    }
    
    public ImageReferenceRow(final Row.Entry rentry) {
        // FIXME: see if cloning is necessary
        this.entry = rentry;
    }
    
    @Override
    public ImageReferenceRow clone() {
        final byte[] b = new byte[urlEntryRow.objectsize];
        System.arraycopy(entry.bytes(), 0, b, 0, urlEntryRow.objectsize);
        return new ImageReferenceRow(b);
    }

    public String toPropertyForm() {
        return entry.toPropertyForm('=', true, true, false, false);
    }
    
    public Entry toKelondroEntry() {
        return this.entry;
    }

    public byte[] metadataHash() {
        return this.entry.getColBytes(col_urlhash, true);
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

    public Collection<Integer> positions() {
        return new ArrayList<Integer>(0);
    }

    public int position(int p) {
        assert p == 0 : "p = " + p;
        return (int) this.entry.getColLong(col_posintext);
    }

    public int posinphrase() {
        return (int) this.entry.getColLong(col_posinphrase);
    }

    public int posofphrase() {
        return (int) this.entry.getColLong(col_posofphrase);
    }


    public char getType() {
        return (char) this.entry.getColByte(col_doctype);
    }
    
    public int urllength() {
        return (int) this.entry.getColLong(col_urlLength);
    }
    
    public int urlcomps() {
        return (int) this.entry.getColLong(col_urlComps);
    }
    
    public Bitfield flags() {
        return new Bitfield(this.entry.getColBytes(col_flags, true));
    }
    
    @Override
    public String toString() {
        return toPropertyForm();
    }

    public boolean isOlder(final Reference other) {
        if (other == null) return false;
        if (this.lastModified() < other.lastModified()) return true;
        return false;
    }
    
    @Override
    public int hashCode() {
        return ByteArray.hashCode(this.metadataHash());
    }

    public void join(Reference oe) {
        throw new UnsupportedOperationException("");
        
    }
    
}
