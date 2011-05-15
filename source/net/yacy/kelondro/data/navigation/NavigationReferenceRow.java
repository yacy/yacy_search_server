// NavigationReferenceRow.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 19.05.2009 on http://yacy.net
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

package net.yacy.kelondro.data.navigation;

import java.util.Collection;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.index.Column;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.rwi.AbstractReference;
import net.yacy.kelondro.rwi.Reference;

public final class NavigationReferenceRow extends AbstractReference implements NavigationReference, Cloneable {

    /*
     * A Navigation Reference is a index that stores the occurrences of Words from a specific Domain
     * (when words in documents occur in spcial categories or in a dictionary) in relation to a normal
     * word index of documents. The navigation index is like a property addition to the word index.
     * Example: Navigation for Author name Occurrences. The dictionary for the navigation index contains
     * names of Authors. Consider that a document is indexed, and the Word 'Goethe' appears in a author field.
     * Then an reference entry in the Author Navigation index is made. The reference entry must contain the following
     * entities:
     * - reference to document (like in the RWI; this is the primary key of the reference entry)
     * - matching word (in this case: term hash of 'Goethe')
     * - flag to see where the tag appeared (in a qualified field for authors or anywhere in the text)
     * - number of occurrences
     */

    public static final Row navEntryRow = new Row(new Column[]{
            new Column("n", Column.celltype_string,    Column.encoder_bytes, Word.commonHashLength * 2, "navhash"), // hash of the navigation term plus document reference hash
            new Column("c", Column.celltype_cardinal,  Column.encoder_b256,  1, "hitcount"),                              // number of occurrences of navigation term
            new Column("p", Column.celltype_cardinal,  Column.encoder_b256,  2, "posintext"),                             // position of navigation term in document
            new Column("f", Column.celltype_cardinal,  Column.encoder_b256,  1, "reserve")                                // reserve for flags etc
        },
        Base64Order.enhancedCoder
    );
    
    // static properties
    private static final int col_navhash       =  0; // n 24 the navigation hash and reference hash b64-encoded
    private static final int col_count         =  1; // c the number of occurences
    private static final int col_pos           =  2; // p the position of the first occurence
    private static final int col_flags         =  3; // f reserve, may be used for flags

    // appearance flags, used in RWI entry
    // some names are derived from the Dublin Core Metadata tag set
    public  static final int flag_app_dc_description= 1; // word appears in anchor description text (the reference to an url), or any alternative text field of a link
    public  static final int flag_app_dc_title      = 2; // word appears in title or headline or any description part
    public  static final int flag_app_dc_creator    = 3; // word appears in author
    public  static final int flag_app_dc_subject    = 4; // word appears in header tags or other descriptive part
    public  static final int flag_app_dc_identifier = 5; // word appears in url or document identifier
    public  static final int flag_app_emphasized    = 6; // word is emphasized in text (i.e. bold, italics, special size)

    private final Row.Entry entry;
    
    public NavigationReferenceRow(
            final byte[]   termhash,
            final byte[]   refhash,
            final int      count,
            final int      pos,
            final byte     flags
    ) {
        assert (termhash.length == 12) : "termhash = " + UTF8.String(termhash);
        assert (refhash.length == 12) : "refhash = " + UTF8.String(refhash);
        this.entry = navEntryRow.newEntry();
        this.entry.setCol(col_navhash, UTF8.String(termhash) + UTF8.String(refhash));
        this.entry.setCol(col_count, count);
        this.entry.setCol(col_pos, pos);
        this.entry.setCol(col_flags, flags);
    }
    
    public NavigationReferenceRow(final byte[] row) {
        this.entry = navEntryRow.newEntry(row);
    }
    
    public NavigationReferenceRow(final Row.Entry entry) {
        this.entry = entry;
    }
    
    @Override
    public NavigationReferenceRow clone() {
        final byte[] b = new byte[navEntryRow.objectsize];
        System.arraycopy(entry.bytes(), 0, b, 0, navEntryRow.objectsize);
        return new NavigationReferenceRow(b);
    }

    public String toPropertyForm() {
        return entry.toPropertyForm('=', true, true, false, false);
    }
    
    public Entry toKelondroEntry() {
        return this.entry;
    }

    public String navigationHash() {
        return this.entry.getColString(col_navhash);
    }

    public byte[] metadataHash() {
        return UTF8.getBytes(navigationHash().substring(12));
    }

    public byte[] termHash() {
        return UTF8.getBytes(navigationHash().substring(0, 12));
    }

    public int hitcount() {
        return (int) this.entry.getColLong(col_count);
    }

    public int position(final int p) {
        assert p == 0 : "p = " + p;
        return (int) this.entry.getColLong(col_pos);
    }
    
    public byte flags() {
        return (byte) this.entry.getColLong(col_flags);
    }
 
    @Override
    public String toString() {
        return toPropertyForm();
    }
    
    @Override
    public int hashCode() {
        return this.navigationHash().hashCode();
    }
    
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof NavigationReferenceRow)) return false;
        NavigationReferenceRow other = (NavigationReferenceRow) obj;
        return this.navigationHash().equals(other.navigationHash());
    }
    
    public boolean isOlder(final Reference other) {
        return false;
    }

    
    // unsupported operations:

    public void join(final Reference oe) {
        throw new UnsupportedOperationException();
    }

    public long lastModified() {
        throw new UnsupportedOperationException();
    }

    public Collection<Integer> positions() {
        throw new UnsupportedOperationException();
    }
    
}
