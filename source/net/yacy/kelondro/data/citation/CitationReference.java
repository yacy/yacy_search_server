/**
 *  CitationReferenceRow
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 13.02.2012 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.kelondro.data.citation;

import java.io.Serializable;
import java.util.Collection;

import net.yacy.cora.date.MicroDate;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.ByteArray;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Column;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.rwi.Reference;

public class CitationReference implements Reference, Serializable {

    // this object stores citation attributes to URL references

    private static final long serialVersionUID=1920200210928897131L;

    public static final Row citationRow = new Row(new Column[]{
            new Column("h", Column.celltype_string,    Column.encoder_bytes, Word.commonHashLength, "urlhash"),
            new Column("m", Column.celltype_cardinal,  Column.encoder_b256,  2, "lastModified"),
            new Column("r", Column.celltype_cardinal,  Column.encoder_b256,  2, "reserve")
        },
        Base64Order.enhancedCoder
    );

    // static properties
    private static final int col_urlhash       =  0; // h 12 the url hash b64-encoded
    private static final int col_lastModified  =  1; // a  2 last-modified time of the document where url appears
    private static final int col_reserve       =  2; // k  2 reserve2

    private final Row.Entry entry;

    public CitationReference(
            final byte[]   urlHash,
            final long     lastmodified  // last-modified time of the document where word appears
    ) {
        assert (urlHash.length == 12) : "urlhash = " + ASCII.String(urlHash);
        this.entry = citationRow.newEntry();
        final int mddlm = MicroDate.microDateDays(lastmodified);
        this.entry.setCol(col_urlhash, urlHash);
        this.entry.setCol(col_lastModified, mddlm >> 2);
        this.entry.setCol(col_reserve, 0);
    }

    private CitationReference(final byte[] row) {
        this.entry = citationRow.newEntry(row);
    }

    public CitationReference(final Row.Entry rentry) {
        // FIXME: see if cloning is necessary
        this.entry = rentry;
    }

    @Override
    public CitationReference clone() {
        final byte[] b = new byte[citationRow.objectsize];
        System.arraycopy(this.entry.bytes(), 0, b, 0, citationRow.objectsize);
        return new CitationReference(b);
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
    
    public byte[] hosthash() {
        byte[] uh = this.entry.getColBytes(col_urlhash, true);
        byte[] hh = new byte[6];
        System.arraycopy(uh, 6, hh, 0, 6);
        return hh;
    }

    public int virtualAge() {
        return (int) this.entry.getColLong(col_lastModified);  // this is the time in MicoDateDays format
    }

    @Override
    public long lastModified() {
        return MicroDate.reverseMicroDateDays(((int) this.entry.getColLong(col_lastModified)) << 2);
    }


    @Override
    public String toString() {
        return toPropertyForm();
    }

    @Override
    public boolean isOlder(final Reference other) {
        if (other == null) return false;
        if (this.lastModified() < other.lastModified()) return true;
        return false;
    }

    private int hashCache = Integer.MIN_VALUE; // if this is used in a compare method many times, a cache is useful

    @Override
    public int hashCode() {
        if (this.hashCache == Integer.MIN_VALUE) {
            this.hashCache = ByteArray.hashCode(this.urlhash());
        }
        return this.hashCache;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof CitationReference)) return false;
        CitationReference other = (CitationReference) obj;
        return Base64Order.enhancedCoder.equal(this.urlhash(), other.urlhash());
    }

    @Override
    public int distance() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void join(Reference oe) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int maxposition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int minposition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Integer> positions() {
        throw new UnsupportedOperationException();
    }

}
