// CitationReferenceRow.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 03.04.2009 on http://yacy.net
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

package net.yacy.kelondro.data.citation;

import java.util.Collection;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Column;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.MicroDate;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.util.ByteArray;

public final class CitationReferenceRow implements Reference /*, Cloneable*/ {

    // this object stores citation attributes to URL references

    public static final Row citationRow = new Row(new Column[]{
            new Column("h", Column.celltype_string,    Column.encoder_bytes, Word.commonHashLength, "urlhash"),
            new Column("a", Column.celltype_cardinal,  Column.encoder_b256,  2, "lastModified"),
            new Column("a", Column.celltype_cardinal,  Column.encoder_b256,  2, "lastAccessed"),
            new Column("t", Column.celltype_cardinal,  Column.encoder_b256,  2, "posintext"),
            new Column("x", Column.celltype_cardinal,  Column.encoder_b256,  1, "llocal"),
            new Column("y", Column.celltype_cardinal,  Column.encoder_b256,  1, "lother"),
            new Column("m", Column.celltype_cardinal,  Column.encoder_b256,  1, "urlLength"),
            new Column("n", Column.celltype_cardinal,  Column.encoder_b256,  1, "urlComps"),
            new Column("g", Column.celltype_binary,    Column.encoder_bytes, 1, "typeofurl"),
            new Column("k", Column.celltype_cardinal,  Column.encoder_b256,  1, "reserve")
        },
        Base64Order.enhancedCoder
    );
    // available chars: b,e,j,q
    
    // static properties
    private static final int col_urlhash       =  0; // h 12 the url hash b64-encoded
    private static final int col_lastModified  =  1; // a  2 last-modified time of the document where url appears
    private static final int col_lastAccessed  =  2; // a  2 curent time when the url was seen
    private static final int col_posintext     =  3; // t  2 appearance of url in text; simply counts up the urls
    private static final int col_llocal        =  4; // x  1 outlinks to same domain
    private static final int col_lother        =  5; // y  1 outlinks to other domain
    private static final int col_urlLength     =  6; // m  1 byte-length of complete URL
    private static final int col_urlComps      =  7; // n  1 number of path components
    private static final int col_typeofurl     =  8; // g  typeofurl
    private static final int col_reserve       =  9; // k  1 reserve2

    private final Row.Entry entry;
    
    public CitationReferenceRow(
            final byte[]   urlHash,
            final long     lastmodified,  // last-modified time of the document where word appears
            final long     updatetime,    // update time
            final int      posintext,     // occurrence of url; counts the url
            final int      llocal,
            final int      lother,
            final int      urlLength,     // byte-length of complete URL
            final int      urlComps,      // number of path components
            final byte     typeofurl      // outlinks to same domain
    ) {
        assert (urlHash.length == 12) : "urlhash = " + UTF8.String(urlHash);
        this.entry = citationRow.newEntry();
        final int mddlm = MicroDate.microDateDays(lastmodified);
        final int mddct = MicroDate.microDateDays(updatetime);
        this.entry.setCol(col_urlhash, urlHash);
        this.entry.setCol(col_lastModified, mddlm);
        this.entry.setCol(col_lastAccessed, mddct);
        this.entry.setCol(col_posintext, posintext);
        this.entry.setCol(col_llocal, llocal);
        this.entry.setCol(col_lother, lother);
        this.entry.setCol(col_urlLength, urlLength);
        this.entry.setCol(col_urlComps, urlComps);
        this.entry.setCol(col_typeofurl, new byte[]{typeofurl});
        this.entry.setCol(col_reserve, 0);
    }
    
    public CitationReferenceRow(final String urlHash, final String code) {
        // the code is the external form of the row minus the leading urlHash entry
        this.entry = citationRow.newEntry(UTF8.getBytes((urlHash + code)));
    }
    
    public CitationReferenceRow(final String external) {
        this.entry = citationRow.newEntry(external, true);
    }
    
    public CitationReferenceRow(final byte[] row) {
        this.entry = citationRow.newEntry(row);
    }
    
    public CitationReferenceRow(final byte[] row, final int offset, final boolean clone) {
        this.entry = citationRow.newEntry(row, offset, clone);
    }
    
    public CitationReferenceRow(final Row.Entry rentry) {
        // FIXME: see if cloning is necessary
        this.entry = rentry;
    }
    
    @Override
    public CitationReferenceRow clone() {
        final byte[] b = new byte[citationRow.objectsize];
        System.arraycopy(entry.bytes(), 0, b, 0, citationRow.objectsize);
        return new CitationReferenceRow(b);
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
    
    public int posintext() {
        return (int) this.entry.getColLong(col_posintext);
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
    
    public double citationFrequency() {
        return 1.0 / ((double) (llocal() + lother() + 1));
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

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof CitationReferenceRow)) return false;
        CitationReferenceRow other = (CitationReferenceRow) obj;
        return Base64Order.enhancedCoder.equal(this.metadataHash(), other.metadataHash());
    }
    
    public int distance() {
        throw new UnsupportedOperationException();
    }

    public void join(Reference oe) {
        throw new UnsupportedOperationException();
    }

    public int maxposition() {
        throw new UnsupportedOperationException();
    }

    public int minposition() {
        throw new UnsupportedOperationException();
    }

    public int position(int p) {
        throw new UnsupportedOperationException();
    }

    public Collection<Integer> positions() {
        throw new UnsupportedOperationException();
    }

}
