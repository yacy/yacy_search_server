// Row.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.05.2006 on http://www.anomic.de
//
// This is a part of the kelondro database,
// which is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.kelondro.index;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.AbstractOrder;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.order.Order;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.NumberTools;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.util.kelondroException;


public final class Row implements Serializable {

    //private final static Pattern commaPattern = Pattern.compile(",");
    private static final long serialVersionUID=-148412365988669116L;

    protected final Column[]        row;
    public final int[]              colstart;
    public final ByteOrder          objectOrder;
    public final int                objectsize;
    public final int                primaryKeyLength;
    protected Map<String, Object[]> nickref = null; // a mapping from nicknames to Object[2]{kelondroColumn, Integer(colstart)}

    public Row(final Column[] row, final ByteOrder objectOrder) {
        assert objectOrder != null;
        this.objectOrder = objectOrder;
        this.row = row;
        assert (objectOrder != null);
        this.colstart = new int[row.length];
        int os = 0;
        for (int i = 0; i < row.length; i++) {
            this.colstart[i] = os;
            os+= this.row[i].cellwidth;
        }
        this.objectsize = os;
        this.primaryKeyLength = this.row[0].cellwidth;
    }

    public Row(final String structure, final ByteOrder objectOrder) {
        assert (objectOrder != null);
        this.objectOrder = objectOrder;
        // define row with row syntax
        // example:
        //# Structure=<pivot-12>,<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>

        // parse pivot definition:
        //does not work with 'String idx-26 "id = created + originator",String cat-8,String rec-14,short  dis-2 {b64e},String att-462'
        //structure = structure.replace('=', ',');

        // parse property part definition:
        int p = structure.indexOf('|');
        if (p < 0) p = structure.length();
        final ArrayList<Column> l = new ArrayList<Column>();
        final String attr = structure.substring(0, p);
        final StringTokenizer st = new StringTokenizer(attr, ",");
        while (st.hasMoreTokens()) {
            l.add(new Column(st.nextToken()));
        }

        // define columns
        this.row = new Column[l.size()];
        this.colstart = new int[this.row.length];
        int os = 0;
        for (int i = 0; i < l.size(); i++) {
            this.colstart[i] = os;
            this.row[i] = l.get(i);
            os += this.row[i].cellwidth;
        }
        this.objectsize = os;
        this.primaryKeyLength = this.row[0].cellwidth;
    }

    public final ByteOrder getOrdering() {
        return this.objectOrder;
    }

    protected final void genNickRef() {
        if (this.nickref != null) return;
        this.nickref = new ConcurrentHashMap<String, Object[]>(this.row.length);
        for (int i = 0; i < this.row.length; i++) this.nickref.put(this.row[i].nickname, new Object[]{this.row[i], Integer.valueOf(this.colstart[i])});
    }

    public final int columns() {
        return this.row.length;
    }

    public final Column column(final int col) {
        return this.row[col];
    }

    public final int width(final int column) {
        return this.row[column].cellwidth;
    }

    public final int[] widths() {
        final int[] w = new int[this.row.length];
        for (int i = 0; i < this.row.length; i++) w[i] = this.row[i].cellwidth;
        return w;
    }

    @Override
    public final String toString() {
        final StringBuilder s = new StringBuilder(80);
        s.append(this.row[0].toString());
        for (int i = 1; i < this.row.length; i++) {
            s.append(", ");
            s.append(this.row[i].toString());
        }
        return s.toString();
    }

    public final Entry newEntry() {
        return new Entry();
    }

    public final Entry newEntry(final byte[] rowinstance) {
        if (rowinstance == null) return null;
        assert (this.objectOrder.wellformed(rowinstance, 0, this.primaryKeyLength)) :  "row not well-formed: rowinstance[0] = " + UTF8.String(rowinstance, 0, this.primaryKeyLength) + " / " + NaturalOrder.arrayList(rowinstance, 0, this.primaryKeyLength);
        return new Entry(rowinstance, 0, false);
    }

    public final Entry newEntry(final Entry oldrow, final int fromColumn) {
        if (oldrow == null) return null;
        assert (oldrow.getColBytes(0, false)[0] != 0);
        assert (this.objectOrder.wellformed(oldrow.getPrimaryKeyBytes(), 0, this.primaryKeyLength));
        return new Entry(oldrow.rowinstance, oldrow.offset + oldrow.colstart(fromColumn), false);
    }

    public final Entry newEntry(final byte[] rowinstance, final int start, final boolean clone) {
        if (rowinstance == null) return null;
        try {
            assert (this.objectOrder.wellformed(rowinstance, start, this.primaryKeyLength)) : "rowinstance = " + UTF8.String(rowinstance);
        } catch (final Throwable e) {
            ConcurrentLog.logException(e);
        }
        // this method offers the option to clone the content
        // this is necessary if it is known that the underlying byte array may change and therefore
        // the reference to the byte array does not contain the original content
        return new Entry(rowinstance, start, clone);
    }

    public final Entry newEntry(final byte[][] cells) {
        if (cells == null) return null;
        assert (cells[0][0] != 0);
        assert (this.objectOrder.wellformed(cells[0], 0, this.primaryKeyLength));
        return new Entry(cells);
    }

    public final Entry newEntry(final String external, final boolean decimalCardinal) {
        if (external == null) return null;
        return new Entry(external, decimalCardinal);
    }

    public final EntryIndex newEntryIndex(final byte[] rowinstance, final int index) {
        if (rowinstance == null) return null;
        assert (rowinstance[0] != 0);
        assert (this.objectOrder.wellformed(rowinstance, 0, this.primaryKeyLength));
        return new EntryIndex(rowinstance, index);
    }

    public static class EntryComparator extends AbstractOrder<Entry> implements Order<Entry>, Comparator<Entry>, Cloneable {

        ByteOrder base;
        public EntryComparator(final ByteOrder baseOrder) {
            this.base = baseOrder;
        }

        @Override
        public int compare(final Entry a, final Entry b) {
            return a.compareTo(b);
        }

        @Override
        public boolean equal(final Entry a, final Entry b) {
            return a.equals(b);
        }

        @Override
        public Order<Entry> clone() {
            return new EntryComparator(this.base);
        }

        @Override
        public long cardinal(final Entry key) {
            return this.base.cardinal(key.bytes(), 0, key.getPrimaryKeyLength());
        }

        @Override
        public String signature() {
            return this.base.signature();
        }

        @Override
        public boolean wellformed(final Entry a) {
            return this.base.wellformed(a.getPrimaryKeyBytes());
        }

    }

    public class Entry implements Comparable<Entry>, Comparator<Entry>, Cloneable, Serializable {

        private static final long serialVersionUID=-2576312347345553495L;

        private byte[] rowinstance;
        private int offset; // the offset where the row starts within rowinstance

        public Entry() {
            this.rowinstance = new byte[Row.this.objectsize];
            //for (int i = 0; i < objectsize; i++) this.rowinstance[i] = 0;
            this.offset = 0;
        }

        public Entry(final byte[] newrow, final int start, final boolean forceclone) {
            if (forceclone || newrow.length - start < Row.this.objectsize) {
                this.rowinstance = new byte[Row.this.objectsize];
                System.arraycopy(newrow, start, this.rowinstance, 0, Math.min(newrow.length, Row.this.objectsize));
                this.offset = 0;
            } else {
                this.rowinstance = newrow;
                this.offset = start;
            }
            //for (int i = ll; i < objectsize; i++) this.rowinstance[i] = 0;
        }

        public Entry(final byte[][] cols) {
            assert Row.this.row.length == cols.length : "cols.length = " + cols.length + ", row.length = " + Row.this.row.length;
            this.rowinstance = new byte[Row.this.objectsize];
            this.offset = 0;
            int ll;
            int cs, cw;
            for (int i = 0; i < Row.this.row.length; i++) {
                cs = Row.this.colstart[i];
                cw = Row.this.row[i].cellwidth;
                if ((i >= cols.length) || (cols[i] == null)) {
                    for (int j = 0; j < cw; j++) this.rowinstance[cs + j] = 0;
                } else {
                    //assert cols[i].length <= cw : "i = " + i + ", cols[i].length = " + cols[i].length + ", cw = " + cw;
                    ll = Math.min(cols[i].length, cw);
                    System.arraycopy(cols[i], 0, this.rowinstance, cs, ll);
                    for (int j = ll; j < cw; j++) this.rowinstance[cs + j] = 0;
                }
            }
        }

        public Entry(String external, final boolean decimalCardinal) {
            // parse external form
            if (!external.isEmpty() && external.charAt(0) == '{') external = external.substring(1, external.length() - 1);
            //final String[] elts = commaPattern.split(external);
            final StringTokenizer st = new StringTokenizer(external, ",");
            if (Row.this.nickref == null) genNickRef();
            String nick;
            int p;
            this.rowinstance = new byte[Row.this.objectsize];
            this.offset = 0;
            String token;
            while (st.hasMoreTokens()) {
                token = st.nextToken();
                p = token.indexOf('=');
                if (p < 0) p = token.indexOf(':');
                if (p > 0) {
                    nick = token.substring(0, p).trim();
                    if (nick.charAt(0) == '"' && nick.charAt(nick.length() - 1) == '"') nick = nick.substring(1, nick.length() - 1);
                    final Object[] ref = Row.this.nickref.get(nick);
                    final Column col = (Column) ref[0];
                    final int clstrt = ((Integer) ref[1]).intValue();
                    if (p + 1 == token.length()) {
                        setCol(clstrt, col.cellwidth, null);
                    } else {
                        if ((decimalCardinal) && (col.celltype == Column.celltype_cardinal)) {
                            try {
                                setCol(col.encoder, this.offset + clstrt, col.cellwidth, NumberTools.parseLongDecSubstring(token, p + 1));
                            } catch (final NumberFormatException e) {
                                ConcurrentLog.severe("kelondroRow", "NumberFormatException for celltype_cardinal, celltype = " + col.celltype + ", encoder = " + col.encoder + ", value = '" + token.substring(p + 1).trim() + "'");
                                setCol(col.encoder, this.offset + clstrt, col.cellwidth, 0);
                            }
                        } else if ((decimalCardinal) && (col.celltype == Column.celltype_binary)) {
                            assert col.cellwidth == 1;
                            try {
                                setCol(clstrt, col.cellwidth, new byte[]{(byte) NumberTools.parseIntDecSubstring(token, p + 1)});
                            } catch (final NumberFormatException e) {
                                ConcurrentLog.severe("kelondroRow", "NumberFormatException for celltype_binary, celltype = " + col.celltype + ", encoder = " + col.encoder + ", value = '" + token.substring(p + 1).trim() + "'");
                                setCol(clstrt, col.cellwidth, new byte[]{0});
                            }
                        } else if ((decimalCardinal) && (col.celltype == Column.celltype_bitfield)) {
                            setCol(clstrt, col.cellwidth, (new Bitfield(col.cellwidth, token.substring(p + 1).trim())).bytes());
                        } else {
                            setCol(clstrt, col.cellwidth, UTF8.getBytes(token.substring(p + 1).trim()));
                        }
                    }
                }
            }
        }

        protected final int colstart(final int column) {
            return Row.this.colstart[column];
        }

        protected final int cellwidth(final int column) {
            return Row.this.row[column].cellwidth;
        }

        @Override
        public final int compareTo(final Entry o) {
            // compares only the content of the primary key
            if (Row.this.objectOrder == null) throw new kelondroException("objects cannot be compared, no order given");
            assert Row.this.primaryKeyLength == o.getPrimaryKeyLength();
            return Row.this.objectOrder.compare(bytes(), o.bytes(), Row.this.primaryKeyLength);
        }

        @Override
        public int compare(final Entry o1, final Entry o2) {
            return o1.compareTo(o2);
        }

        // compare the content of the primary key
        @Override
        public boolean equals(final Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof Entry)) return false;
            final Entry other = (Entry) obj;
            final byte[] t = bytes();
            final byte[] o = other.bytes();
            for (int i = 0; i < Row.this.primaryKeyLength; i++) {
                if (t[i] != o[i]) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            final byte[] b = getPrimaryKeyBytes();
            final int len = b.length;
            int h = 1;
            for (int i = 0; i < len; i++) {
                h = 31 * h + b[i];
            }
            return h;
        }

        public final byte[] bytes() {
            if (this.offset == 0 && this.rowinstance.length == Row.this.objectsize) {
                return this.rowinstance;
            }
            final byte[] tmp = new byte[Row.this.objectsize];
            System.arraycopy(this.rowinstance, this.offset, tmp, 0, Row.this.objectsize);
            return tmp;
        }

        public final void writeToArray(final byte[] target, final int targetOffset) {
            // this method shall replace the byte()s where possible, bacause it may reduce the number of new byte[] allocations
            assert (targetOffset + Row.this.objectsize <= target.length) : "targetOffset = " + targetOffset + ", target.length = " + target.length + ", objectsize = " + Row.this.objectsize;
            System.arraycopy(this.rowinstance, this.offset, target, targetOffset, Row.this.objectsize);
        }

        public final int columns() {
            return Row.this.row.length;
        }

        public final int objectsize() {
            return Row.this.objectsize;
        }

        public final boolean empty(final int column) {
            return this.rowinstance[this.offset + Row.this.colstart[column]] == 0;
        }

        public final void setCol(final int column, final byte[] cell) {
            setCol(Row.this.colstart[column], Row.this.row[column].cellwidth, cell);
        }

        public final void setCol(final int column, final char[] cell) {
            final int clstrt = Row.this.colstart[column];
            for (int i = 0; i < cell.length; i++) this.rowinstance[this.offset + clstrt + i] = (byte) cell[i];
            for (int i = cell.length; i < Row.this.row[column].cellwidth; i++) this.rowinstance[this.offset + clstrt + i] = 0;
        }

        private final void setCol(final int clstrt, int length, final byte[] cell) {
            if (cell == null) {
                while (length-- > 0) this.rowinstance[this.offset + clstrt + length] = 0;
            } else {
                if (cell.length < length) {
                    System.arraycopy(cell, 0, this.rowinstance, this.offset + clstrt, cell.length);
                    while (length-- > cell.length) this.rowinstance[this.offset + clstrt + length] = 0;
                } else {
                    //assert cell.length == length;
                    System.arraycopy(cell, 0, this.rowinstance, this.offset + clstrt, length);
                }
            }
        }

        public final void setCol(final int column, final byte c) {
            this.rowinstance[this.offset + Row.this.colstart[column]] = c;
        }

        public final void setCol(final int column, final String cell) {
            setCol(column, UTF8.getBytes(cell));
        }

        public final void setCol(final int column, final long cell) {
            // uses the column definition to choose the right encoding
            setCol(Row.this.row[column].encoder, this.offset + Row.this.colstart[column], Row.this.row[column].cellwidth, cell);
        }

        private final void setCol(final int encoder, final int offset, final int length, final long cell) {
            switch (encoder) {
            case Column.encoder_none:
                throw new kelondroException("ROW", "setColLong has celltype none, no encoder given");
            case Column.encoder_b64e:
                Base64Order.enhancedCoder.encodeLong(cell, this.rowinstance, offset, length);
                break;
            case Column.encoder_b256:
                NaturalOrder.encodeLong(cell, this.rowinstance, offset, length);
                break;
            case Column.encoder_bytes:
                throw new kelondroException("ROW", "setColLong of celltype bytes not applicable");
            default:
                throw new kelondroException("ROW", "setColLong has celltype none, no encoder given");
            }
        }

        public final long incCol(final int column, final long c) {
            final int encoder = Row.this.row[column].encoder;
            final int colstrt = Row.this.colstart[column];
            final int cellwidth = Row.this.row[column].cellwidth;
            long l;
            switch (encoder) {
            case Column.encoder_b64e:
                l = c + Base64Order.enhancedCoder.decodeLong(this.rowinstance, this.offset + colstrt, cellwidth);
                Base64Order.enhancedCoder.encodeLong(l, this.rowinstance, this.offset + colstrt, cellwidth);
                return l;
            case Column.encoder_b256:
                l = c + NaturalOrder.decodeLong(this.rowinstance, this.offset + colstrt, cellwidth);
                NaturalOrder.encodeLong(l, this.rowinstance, this.offset + colstrt, cellwidth);
                return l;
            default:
                throw new kelondroException("ROW", "addCol did not find appropriate encoding");
            }
        }

        public final byte[] getPrimaryKeyBytes() {
            if (this.rowinstance[this.offset] == 0) return null;
            if (Row.this.row.length == 1 && this.offset == 0 && this.rowinstance.length == Row.this.primaryKeyLength) {
                // avoid memory allocation in case that the row consists in only the primary key
                return this.rowinstance;
            }
            final byte[] c = new byte[Row.this.primaryKeyLength];
            System.arraycopy(this.rowinstance, this.offset, c, 0, Row.this.primaryKeyLength);
            return c;
        }

        /**
         * get the utf-8 value of the primary key
         * you will most likely want to call .trim() on that value if the key does not have a fixed length
         * because the return value may have a fill-up with zero bytes at the end of the string
         *
         * @return
         */
        public final String getPrimaryKeyUTF8() {
            if (this.rowinstance[this.offset] == 0) return null;
            if (Row.this.row.length == 1 && this.offset == 0 && this.rowinstance.length == Row.this.primaryKeyLength) {
                // avoid memory allocation in case that the row consists in only the primary key
                return UTF8.String(this.rowinstance);
            }
            return UTF8.String(this.rowinstance, this.offset, Row.this.primaryKeyLength);
        }

        public final String getPrimaryKeyASCII() {
            if (this.rowinstance[this.offset] == 0) return null;
            if (Row.this.row.length == 1 && this.offset == 0 && this.rowinstance.length == Row.this.primaryKeyLength) {
                // avoid memory allocation in case that the row consists in only the primary key
                return ASCII.String(this.rowinstance);
            }
            return ASCII.String(this.rowinstance, this.offset, Row.this.primaryKeyLength);
        }

        public final String getColUTF8(final int column) {
            final int clstrt = Row.this.colstart[column];
            if (this.rowinstance[this.offset + clstrt] == 0) return null;
            final int length = getColLength(column, clstrt);
            if (length == 0) return null;
            return UTF8.String(this.rowinstance, this.offset + clstrt, length);
        }

        public final String getColASCII(final int column) {
            final int clstrt = Row.this.colstart[column];
            if (this.rowinstance[this.offset + clstrt] == 0) return null;
            final int length = getColLength(column, clstrt);
            if (length == 0) return null;
            return ASCII.String(this.rowinstance, this.offset + clstrt, length);
        }

        private final int getColLength(final int column, final int clstrt) {
            int length = Row.this.row[column].cellwidth;
            assert length <= this.rowinstance.length - this.offset - clstrt;
            if (length > this.rowinstance.length - this.offset - clstrt) length = this.rowinstance.length - this.offset - clstrt;
            while ((length > 0) && (this.rowinstance[this.offset + clstrt + length - 1] == 0)) length--;
            return length;
        }

        public final long getColLong(final int column) {
            // uses the column definition to choose the right encoding
            return getColLong(Row.this.row[column].encoder, Row.this.colstart[column], Row.this.row[column].cellwidth);
        }

        protected final long getColLong(final int encoder, final int clstrt, final int length) {
            switch (encoder) {
            case Column.encoder_none:
                throw new kelondroException("ROW", "getColLong has celltype none, no encoder given");
            case Column.encoder_b64e:
                // start - fix for badly stored parameters
                if ((length >= 3) && (this.rowinstance[this.offset + clstrt] == '[') && (this.rowinstance[this.offset + clstrt + 1] == 'B') && (this.rowinstance[this.offset + clstrt + 2] == '@')) return 0;
                if ((length == 2) && (this.rowinstance[this.offset + clstrt] == '[') && (this.rowinstance[this.offset + clstrt + 1] == 'B')) return 0;
                if ((length == 1) && (this.rowinstance[this.offset + clstrt] == '[')) return 0;
                boolean maxvalue = true;
                for (int i = 0; i < length; i++) if (this.rowinstance[this.offset + clstrt + i] != '_') {maxvalue = false; break;}
                if (maxvalue) return 0;
                // stop - fix for badly stored parameters
                return Base64Order.enhancedCoder.decodeLong(this.rowinstance, this.offset + clstrt, length);
            case Column.encoder_b256:
                return NaturalOrder.decodeLong(this.rowinstance, this.offset + clstrt, length);
            case Column.encoder_bytes:
                throw new kelondroException("ROW", "getColLong of celltype bytes not applicable");
            default:
                throw new kelondroException("ROW", "getColLong did not find appropriate encoding");
            }
        }

        public final byte getColByte(final int column) {
            return this.rowinstance[this.offset + Row.this.colstart[column]];
        }

        public final int getPrimaryKeyLength() {
            return Row.this.primaryKeyLength;
        }

        public final byte[] getColBytes(final int column, final boolean nullIfEmpty) {
            assert this.offset + Row.this.colstart[column] + Row.this.row[column].cellwidth <= this.rowinstance.length :
                "column = " + column + ", offset = " + this.offset + ", colstart[column] = " + Row.this.colstart[column] + ", row[column].cellwidth() = " + Row.this.row[column].cellwidth + ", rowinstance.length = " + this.rowinstance.length;
            final int clstrt = Row.this.colstart[column];
            final int w = Row.this.row[column].cellwidth;
            if (nullIfEmpty) {
                int length = w;
                while (length > 0 && this.rowinstance[this.offset + clstrt + length - 1] == 0) length--;
                if (length == 0) return null;
            }
            final byte[] c = new byte[w];
            System.arraycopy(this.rowinstance, this.offset + clstrt, c, 0, w);
            return c;
        }

        public final void writeToArray(final int column, final byte[] target, final int targetOffset) {
            // this method shall replace the getColBytes where possible, bacause it may reduce the number of new byte[] allocations
            assert (targetOffset + Row.this.row[column].cellwidth <= target.length) : "targetOffset = " + targetOffset + ", target.length = " + target.length + ", row[column].cellwidth() = " + Row.this.row[column].cellwidth;
            System.arraycopy(this.rowinstance, this.offset + Row.this.colstart[column], target, targetOffset, Row.this.row[column].cellwidth);
        }

        public final String toPropertyForm(final char propertySymbol, final boolean includeBraces, final boolean decimalCardinal, final boolean longname, final boolean quotes) {
            final ByteBuffer bb = new ByteBuffer(objectsize() * 2);
            if (includeBraces) bb.append('{');
            for (int i = 0; i < Row.this.row.length; i++) {
                if (quotes) bb.append('"');
                bb.append((longname) ? Row.this.row[i].description : Row.this.row[i].nickname);
                if (quotes) bb.append('"');
                bb.append(propertySymbol);
                if (quotes) bb.append('"');
                if ((decimalCardinal) && (Row.this.row[i].celltype == Column.celltype_cardinal)) {
                    bb.append(Long.toString(getColLong(i)));
                } else if ((decimalCardinal) && (Row.this.row[i].celltype == Column.celltype_bitfield)) {
                    bb.append((new Bitfield(getColBytes(i, true))).exportB64());
                } else if ((decimalCardinal) && (Row.this.row[i].celltype == Column.celltype_binary)) {
                    assert Row.this.row[i].cellwidth == 1 : toString();
                    bb.append(Integer.toString((0xff & getColByte(i))));
                } else {
                    bb.append(this.rowinstance, this.offset + Row.this.colstart[i], Row.this.row[i].cellwidth);
                }
                if (quotes) bb.append('"');
                if (i < Row.this.row.length - 1) {
                    bb.append(',');
                    if (longname) bb.append(' ');
                }
            }
            if (includeBraces) bb.append('}');
            //System.out.println("DEBUG-ROW " + bb.toString());
            String bbs = bb.toString();
            try {bb.close();} catch (final IOException e) {}
            return bbs;
        }

        @Override
        public final String toString() {
            return toPropertyForm('=', true, false, false, false);
        }

    }

    public final class EntryIndex extends Entry implements Serializable {

        private static final long serialVersionUID=153069052590699231L;

        private final int index;
        public EntryIndex(final byte[] row, final int i) {
            super(row, 0, false);
            this.index = i;
        }
        public int index() {
            return this.index;
        }
    }

    public Queue newQueue(final int maxsize) {
        return new Queue(maxsize);
    }

    public final class Queue {

        private final ArrayBlockingQueue<Entry> queue;

        public Queue(final int maxsize) {
            this.queue = new ArrayBlockingQueue<Entry>(maxsize);
        }

        public void put(final Entry e) throws InterruptedException {
            this.queue.put(e);
        }

        public Entry take() throws InterruptedException {
            return this.queue.take();
        }

        public Entry get(final byte[] key) {
            for (final Entry e: this.queue) {
                assert key.length == e.getPrimaryKeyLength();
                if (Row.this.objectOrder.compare(key, 0, e.bytes(), 0, key.length) == 0) {
                    return e;
                }
            }
            return null;
        }

        public Entry delete(final byte[] key) {
            final Iterator<Entry> i = this.queue.iterator();
            Entry e;
            while (i.hasNext()) {
                e = i.next();
                assert key.length == e.getPrimaryKeyLength();
                if (Row.this.objectOrder.compare(key, 0, e.bytes(), 0, key.length) == 0) {
                    i.remove();
                    return e;
                }
            }
            return null;
        }
    }

    public final static void long2bytes(long x, final byte[] b, final int offset, final int length) {
        for (int i = length - 1; i >= 0; i--) {
            b[offset + i] = (byte) (x & 0XFF);
            x >>= 8;
        }
    }

    public final static long bytes2long(final byte[] b, final int offset, final int length) {
        if (b == null) return 0;
        long x = 0;
        for (int i = 0; i < length; i++) x = (x << 8) | (0xff & b[offset + i]);
        return x;
    }

    public final boolean subsumes(final Row otherRow) {
        // returns true, if this row has at least all columns as the other row
        // and possibly some more
        if (this.objectsize < otherRow.objectsize) return false;
        for (int i = 0; i < otherRow.row.length; i++) {
            if ((this.row[i].cellwidth == otherRow.row[i].cellwidth) &&
                (this.row[i].celltype == Column.celltype_bitfield) &&
                (otherRow.row[i].celltype == Column.celltype_binary)) continue;
            if (!(this.row[i].equals(otherRow.row[i]))) return false;
        }
        return true;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Row)) return false;
        final Row other = (Row) obj;
        if (this.objectsize != other.objectsize) return false;
        if (columns() != other.columns()) return false;
        for (int i = 0; i < other.row.length; i++) {
            if (!(this.row[i].equals(other.row[i]))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

}
