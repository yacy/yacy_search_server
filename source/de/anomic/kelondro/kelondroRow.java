// kelondroRow.java
// (C) 2006 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 24.05.2006 on http://www.anomic.de
//
// This is a part of the kelondro database,
// which is a part of YaCy, a peer-to-peer based web search engine
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

package de.anomic.kelondro;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import de.anomic.server.serverByteBuffer;
import de.anomic.server.logging.serverLog;

public final class kelondroRow {
   
    protected kelondroColumn[]      row;
    protected int[]                 colstart;
    protected kelondroByteOrder     objectOrder;
    public    int                   objectsize;
    public    int                   primaryKeyIndex, primaryKeyLength;
    protected Map<String, Object[]> nickref = null; // a mapping from nicknames to Object[2]{kelondroColumn, Integer(colstart)}
    
    public kelondroRow(final kelondroColumn[] row, final kelondroByteOrder objectOrder, final int primaryKey) {
        assert objectOrder != null;
        this.objectOrder = objectOrder;
        this.row = row;
        assert (objectOrder != null);
        this.colstart = new int[row.length];
        this.objectsize = 0;
        for (int i = 0; i < row.length; i++) {
            this.colstart[i] = this.objectsize;
            this.objectsize += this.row[i].cellwidth;
        }
        this.primaryKeyIndex = primaryKey;
        this.primaryKeyLength = (primaryKey < 0) ? this.objectsize : row[primaryKeyIndex].cellwidth;
    }

    public kelondroRow(String structure, final kelondroByteOrder objectOrder, final int primaryKey) {
        assert (objectOrder != null);
        this.objectOrder = objectOrder;
        // define row with row syntax
        // example:
        //# Structure=<pivot-12>,'=',<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>

        // parse pivot definition:
        //does not work with 'String idx-26 "id = created + originator",String cat-8,String rec-14,short  dis-2 {b64e},String att-462'
        //structure = structure.replace('=', ',');
        
        // parse property part definition:
        int p = structure.indexOf('|');
        if (p < 0) p = structure.length();
        final ArrayList<kelondroColumn> l = new ArrayList<kelondroColumn>();
        final String attr = structure.substring(0, p);
        final StringTokenizer st = new StringTokenizer(attr, ",");
        while (st.hasMoreTokens()) {
            l.add(new kelondroColumn(st.nextToken()));
        }
        
        // define columns
        this.row = new kelondroColumn[l.size()];
        this.colstart = new int[row.length];
        this.objectsize = 0;
        for (int i = 0; i < l.size(); i++) {
            this.colstart[i] = this.objectsize;
            this.row[i] = l.get(i);
            this.objectsize += this.row[i].cellwidth;
        }
        this.primaryKeyIndex = primaryKey;
        this.primaryKeyLength = (primaryKey < 0) ? this.objectsize : row[primaryKeyIndex].cellwidth;
    }
    
    public final void setOrdering(final kelondroByteOrder objectOrder, final int primaryKey) {
        assert (objectOrder != null);
        this.objectOrder = objectOrder;
        this.primaryKeyIndex = primaryKey;
        this.primaryKeyLength = (primaryKey < 0) ? this.objectsize : row[primaryKeyIndex].cellwidth;
    }
    
    public final kelondroOrder<byte[]> getOrdering() {
        return this.objectOrder;
    }
    
    protected final void genNickRef() {
        if (nickref != null) return;
        nickref = new HashMap<String, Object[]>(row.length);
        for (int i = 0; i < row.length; i++) nickref.put(row[i].nickname, new Object[]{row[i], Integer.valueOf(colstart[i])});
    }
    
    public final int columns() {
        return this.row.length;
    }
    /*
    public final int objectsize() {
        return this.objectsize;
    }
    */
    public final kelondroColumn column(final int col) {
        return row[col];
    }
    
    public final int width(final int column) {
        return this.row[column].cellwidth;
    }
    
    public final int[] widths() {
        final int[] w = new int[this.row.length];
        for (int i = 0; i < this.row.length; i++) w[i] = row[i].cellwidth;
        return w;
    }
    
    public final String toString() {
        final StringBuffer s = new StringBuffer();
        s.append(row[0].toString());
        for (int i = 1; i < row.length; i++) {
            s.append(", ");
            s.append(row[i].toString());
        }
        return new String(s);
    }
    
    public final Entry newEntry() {
        return new Entry();
    }
    
    public final Entry newEntry(final byte[] rowinstance) {
        if (rowinstance == null) return null;
        //assert (rowinstance[0] != 0);
        assert (this.objectOrder.wellformed(rowinstance, 0, row[0].cellwidth)) : "rowinstance[0] = " + new String(rowinstance, 0, row[0].cellwidth) + " / " + serverLog.arrayList(rowinstance, 0, row[0].cellwidth);
        if (!(this.objectOrder.wellformed(rowinstance, 0, row[0].cellwidth))) return null;
        return new Entry(rowinstance, false);
    }
    
    public final Entry newEntry(final Entry oldrow, final int fromColumn) {
        if (oldrow == null) return null;
        assert (oldrow.getColBytes(0)[0] != 0);
        assert (this.objectOrder.wellformed(oldrow.getColBytes(0), 0, row[0].cellwidth));
        return new Entry(oldrow, fromColumn, false);
    }
    
    public final Entry newEntry(final byte[] rowinstance, final int start, final boolean clone) {
        if (rowinstance == null) return null;
        //assert (rowinstance[0] != 0);
        assert (this.objectOrder.wellformed(rowinstance, start, row[0].cellwidth)) : "rowinstance = " + new String(rowinstance);
        // this method offers the option to clone the content
        // this is necessary if it is known that the underlying byte array may change and therefore
        // the reference to the byte array does not contain the original content
        return new Entry(rowinstance, start, clone);
    }
    
    public final Entry newEntry(final byte[][] cells) {
        if (cells == null) return null;
        assert (cells[0][0] != 0);
        assert (this.objectOrder.wellformed(cells[0], 0, row[0].cellwidth));
        return new Entry(cells);
    }
    
    public final Entry newEntry(final String external, final boolean decimalCardinal) {
        if (external == null) return null;
        return new Entry(external, decimalCardinal);
    }
    
    public final EntryIndex newEntryIndex(final byte[] rowinstance, final int index) {
        if (rowinstance == null) return null;
        assert (rowinstance[0] != 0);
        assert (this.objectOrder.wellformed(rowinstance, 0, row[0].cellwidth));
        return new EntryIndex(rowinstance, index);
    }
    
    public static class EntryComparator extends kelondroAbstractOrder<Entry> implements kelondroOrder<Entry>, Comparator<Entry>, Cloneable {

        kelondroByteOrder base;
        public EntryComparator(final kelondroByteOrder baseOrder) {
            this.base = baseOrder;
        }
        
        public int compare(final Entry a, final Entry b) {
            return a.compareTo(b);
        }

        public kelondroOrder<Entry> clone() {
            return new EntryComparator(base);
        }

        public long cardinal(final Entry key) {
            return base.cardinal(key.getPrimaryKeyBytes());
        }

        public String signature() {
            return base.signature();
        }

        public boolean wellformed(final Entry a) {
            return base.wellformed(a.getPrimaryKeyBytes());
        }

    }
    
    public class Entry implements Comparable<Entry> {

        private byte[] rowinstance;
        private int offset; // the offset where the row starts within rowinstance
        
        public Entry() {
            rowinstance = new byte[objectsize];
            for (int i = 0; i < objectsize; i++) this.rowinstance[i] = 0;
            offset = 0;
        }
        
        public Entry(final byte[] newrow, final boolean forceclone) {
            this(newrow, 0, forceclone);
        }
        
        public Entry(final Entry oldrow, final int fromColumn, final boolean forceclone) {
            this(oldrow.rowinstance, oldrow.offset + oldrow.colstart(fromColumn), forceclone);
        }
        
        public Entry(final byte[] newrow, final int start, final boolean forceclone) {
            if ((!forceclone) && (newrow.length - start >= objectsize)) {
                this.rowinstance = newrow;
                this.offset = start;
            } else {
                this.rowinstance = new byte[objectsize];
                System.arraycopy(newrow, start, this.rowinstance, 0, objectsize);
                this.offset = 0;
            }
            //for (int i = ll; i < objectsize; i++) this.rowinstance[i] = 0;
        }
        
        public Entry(final byte[][] cols) {
            assert row.length == cols.length : "cols.length = " + cols.length + ", row.length = " + row.length;
            this.rowinstance = new byte[objectsize];
            this.offset = 0;
            int ll;
            int cs, cw;
            for (int i = 0; i < row.length; i++) {
                cs = colstart[i];
                cw = row[i].cellwidth;
                if ((i >= cols.length) || (cols[i] == null)) {
                    for (int j = 0; j < cw; j++) this.rowinstance[cs + j] = 0;
                } else {
                    //assert cols[i].length <= cw : "i = " + i + ", cols[i].length = " + cols[i].length + ", cw = " + cw;
                    ll = Math.min(cols[i].length, cw);
                    System.arraycopy(cols[i], 0, rowinstance, cs, ll);
                    for (int j = ll; j < cw; j++) this.rowinstance[cs + j] = 0;
                }
            }
        }
        
        public Entry(String external, final boolean decimalCardinal) {
            // parse external form
            if (external.charAt(0) == '{') external = external.substring(1, external.length() - 1);
            final String[] elts = external.split(",");
            if (nickref == null) genNickRef();
            String nick;
            int p;
            this.rowinstance = new byte[objectsize];
            this.offset = 0;
            for (int i = 0; i < elts.length; i++) {
                p = elts[i].indexOf('=');
                if (p > 0) {
                    nick = elts[i].substring(0, p).trim();
                    if (p + 1 == elts[i].length())
                        setCol(nick, null);
                    else {
                        if ((decimalCardinal) && (row[i].celltype == kelondroColumn.celltype_cardinal)) {
                            try {
                                setCol(nick, Long.parseLong(elts[i].substring(p + 1).trim()));
                            } catch (final NumberFormatException e) {
                                serverLog.logSevere("kelondroRow", "NumberFormatException for celltype_cardinal; row = " + i + ", celltype = " + row[i].celltype + ", encoder = " + row[i].encoder + ", value = '" + elts[i].substring(p + 1).trim() + "'");
                                setCol(nick, 0);
                            }
                        } else if ((decimalCardinal) && (row[i].celltype == kelondroColumn.celltype_binary)) {
                            assert row[i].cellwidth == 1;
                            try {
                                setCol(nick, new byte[]{(byte) Integer.parseInt(elts[i].substring(p + 1).trim())});
                            } catch (final NumberFormatException e) {
                                serverLog.logSevere("kelondroRow", "NumberFormatException for celltype_binary; row = " + i + ", celltype = " + row[i].celltype + ", encoder = " + row[i].encoder + ", value = '" + elts[i].substring(p + 1).trim() + "'");
                                setCol(nick, new byte[]{0});
                            }
                        } else if ((decimalCardinal) && (row[i].celltype == kelondroColumn.celltype_bitfield)) {
                            setCol(nick, (new kelondroBitfield(row[i].cellwidth, elts[i].substring(p + 1).trim())).bytes());
                        } else {
                            setCol(nick, elts[i].substring(p + 1).trim().getBytes());
                        }
                    }
                }
            }
        }

        protected final int colstart(final int column) {
            return colstart[column];
        }
        
        protected final int cellwidth(final int column) {
            return row[column].cellwidth;
        }
        
        public final int compareTo(final Entry o) {
            // compares only the content of the primary key
            if (objectOrder == null) throw new kelondroException("objects cannot be compared, no order given");
            return objectOrder.compare(this.getPrimaryKeyBytes(), (o).getPrimaryKeyBytes());
        }
        
        public final boolean equals(final Entry otherEntry) {
            // compares the content of the complete entry
            final byte[] t = this.bytes();
            final byte[] o = otherEntry.bytes();
            if (o.length != t.length) return false;
            for (int i = 0; i < t.length; i++) {
                if (t[i] != o[i]) return false;
            }
            return true;
        }
        
        public final byte[] bytes() {
            if ((offset == 0) && (rowinstance.length == objectsize)) {
                return rowinstance;
            }
            final byte[] tmp = new byte[objectsize];
            System.arraycopy(rowinstance, offset, tmp, 0, objectsize);
            return tmp;
        }
        
        public final void writeToArray(final byte[] target, final int targetOffset) {
            // this method shall replace the byte()s where possible, bacause it may reduce the number of new byte[] allocations
            assert (targetOffset + objectsize <= target.length) : "targetOffset = " + targetOffset + ", target.length = " + target.length + ", objectsize = " + objectsize;
            System.arraycopy(rowinstance, offset, target, targetOffset, objectsize);
        }
        
        public final int columns() {
            return row.length;
        }
        
        public final int objectsize() {
            return objectsize;
        }
        
        public final boolean empty(final int column) {
            return rowinstance[offset + colstart[column]] == 0;
        }
        
        public final void setCol(final String nickname, final char c) {
            if (nickref == null) genNickRef();
            final Object[] ref = nickref.get(nickname);
            if (ref == null) return;
            rowinstance[offset + ((Integer) ref[1]).intValue()] = (byte) c;
        }
        
        public final void setCol(final String nickname, final byte[] cell) {
            if (nickref == null) genNickRef();
            final Object[] ref = nickref.get(nickname);
            if (ref == null) return;
            final kelondroColumn col = (kelondroColumn) ref[0];
            setCol(col.encoder, ((Integer) ref[1]).intValue(), col.cellwidth, cell);
        }
        
        public final void setCol(final int column, final byte[] cell) {
            setCol(row[column].encoder, colstart[column], row[column].cellwidth, cell);
        }
        
        public final void setCol(final int column, final char[] cell) {
            final int clstrt = colstart[column];
            for (int i = 0; i < cell.length; i++) rowinstance[offset + clstrt + i] = (byte) cell[i];
            for (int i = cell.length; i < row[column].cellwidth; i++) rowinstance[offset + clstrt + i] = 0;
        }
        
        private final void setCol(final int encoding, final int clstrt, int length, final byte[] cell) {
            if (cell == null) {
                while (length-- > 0) rowinstance[offset + clstrt + length] = 0;
            } else {
                if (cell.length < length) {
                    System.arraycopy(cell, 0, rowinstance, offset + clstrt, cell.length);
                    while (length-- > cell.length) rowinstance[offset + clstrt + length] = 0;
                } else {
                    //assert cell.length == length;
                    System.arraycopy(cell, 0, rowinstance, offset + clstrt, length);
                }
            }
        }
        
        public final void setCol(final int column, final byte c) {
            rowinstance[offset + colstart[column]] = c;
        }
        
        public final void setCol(final int column, final String cell, final String encoding) {
            if (encoding == null)
                setCol(column, cell.getBytes());
            else
                try {
                    setCol(column, (cell == null) ? null : cell.getBytes(encoding));
                } catch (final UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
        }
        
        public final void setCol(final String nick, final String cell, final String encoding) {
            if (encoding == null)
                setCol(nick, cell.getBytes());
            else
                try {
                    setCol(nick, cell.getBytes(encoding));
                } catch (final UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
        }
        
        public final void setCol(final String nickname, final long cell) {
            if (nickref == null) genNickRef();
            final Object[] ref = nickref.get(nickname);
            if (ref == null) return;
            final kelondroColumn col = (kelondroColumn) ref[0];
            setCol(col.encoder, offset + ((Integer) ref[1]).intValue(), col.cellwidth, cell);
        }
        
        public final void setCol(final int column, final long cell) {
            // uses the column definition to choose the right encoding
            setCol(row[column].encoder, offset + colstart[column], row[column].cellwidth, cell);
        }
        
        private final void setCol(final int encoder, final int offset, final int length, final long cell) {
            switch (encoder) {
            case kelondroColumn.encoder_none:
                throw new kelondroException("ROW", "setColLong has celltype none, no encoder given");
            case kelondroColumn.encoder_b64e:
                kelondroBase64Order.enhancedCoder.encodeLong(cell, rowinstance, offset, length);
                break;
            case kelondroColumn.encoder_b256:
                kelondroNaturalOrder.encodeLong(cell, rowinstance, offset, length);
                break;
            case kelondroColumn.encoder_bytes:
                throw new kelondroException("ROW", "setColLong of celltype bytes not applicable");
            }
        }
        
        public final byte[] getCol(final String nickname, final byte[] dflt) {
            if (nickref == null) genNickRef();
            final Object[] ref = nickref.get(nickname);
            if (ref == null) return dflt;
            final kelondroColumn col = (kelondroColumn) ref[0];
            final byte[] cell = new byte[col.cellwidth];
            System.arraycopy(rowinstance, offset + ((Integer) ref[1]).intValue(), cell, 0, cell.length);
            return cell;
        }
        
        public final String getColString(final String nickname, final String dflt, final String encoding) {
            if (nickref == null) genNickRef();
            final Object[] ref = nickref.get(nickname);
            if (ref == null) return dflt;
            final kelondroColumn col = (kelondroColumn) ref[0];
            return getColString(col.encoder, ((Integer) ref[1]).intValue(), col.cellwidth, encoding);
        }
        
        public final String getColString(final int column, final String encoding) {
            return getColString(row[column].encoder, colstart[column], row[column].cellwidth, encoding);
        }
        
        private final String getColString(final int encoder, final int clstrt, int length, final String encoding) {
            if (rowinstance[offset + clstrt] == 0) return null;
            if (length > rowinstance.length - offset - clstrt) length = rowinstance.length - offset - clstrt;
            while ((length > 0) && (rowinstance[offset + clstrt + length - 1] == 0)) length--;
            if (length == 0) return null;
            try {
                if ((encoding == null) || (encoding.length() == 0))
                    return new String(rowinstance, offset + clstrt, length);
                return new String(rowinstance, offset + clstrt, length, encoding);
            } catch (final UnsupportedEncodingException e) {
                return "";
            }
        }
        
        public final long getColLong(final String nickname, final long dflt) {
            if (nickref == null) genNickRef();
            final Object[] ref = nickref.get(nickname);
            if (ref == null) return dflt;
            final kelondroColumn col = (kelondroColumn) ref[0];
            final int clstrt = ((Integer) ref[1]).intValue();
            return getColLong(col.encoder, clstrt, col.cellwidth);
        }
        
        public final long getColLong(final int column) {
            // uses the column definition to choose the right encoding
            return getColLong(row[column].encoder, colstart[column], row[column].cellwidth);
        }
        
        protected final long getColLong(final int encoder, final int clstrt, final int length) {
            switch (encoder) {
            case kelondroColumn.encoder_none:
                throw new kelondroException("ROW", "getColLong has celltype none, no encoder given");
            case kelondroColumn.encoder_b64e:
                // start - fix for badly stored parameters
                if ((length >= 3) && (rowinstance[offset + clstrt] == '[') && (rowinstance[offset + clstrt + 1] == 'B') && (rowinstance[offset + clstrt + 2] == '@')) return 0;
                if ((length == 2) && (rowinstance[offset + clstrt] == '[') && (rowinstance[offset + clstrt + 1] == 'B')) return 0;
                if ((length == 1) && (rowinstance[offset + clstrt] == '[')) return 0;
                boolean maxvalue = true;
                for (int i = 0; i < length; i++) if (rowinstance[offset + clstrt + i] != '_') {maxvalue = false; break;}
                if (maxvalue) return 0;
                // stop - fix for badly stored parameters
                return kelondroBase64Order.enhancedCoder.decodeLong(rowinstance, offset + clstrt, length);
            case kelondroColumn.encoder_b256:
                return kelondroNaturalOrder.decodeLong(rowinstance, offset + clstrt, length);
            case kelondroColumn.encoder_bytes:
                throw new kelondroException("ROW", "getColLong of celltype bytes not applicable");
            }
            throw new kelondroException("ROW", "getColLong did not find appropriate encoding");
        }
        
        public final byte getColByte(final String nickname, final byte dflt) {
            if (nickref == null) genNickRef();
            final Object[] ref = nickref.get(nickname);
            if (ref == null) return dflt;
            return rowinstance[offset + ((Integer) ref[1]).intValue()];
        }
        
        public final byte getColByte(final int column) {
            return rowinstance[offset + colstart[column]];
        }
        
        public final byte[] getPrimaryKeyBytes() {
            final byte[] c = new byte[primaryKeyLength];
            System.arraycopy(rowinstance, offset + ((primaryKeyIndex < 0) ? 0 : colstart[primaryKeyIndex]), c, 0, primaryKeyLength);
            return c;
        }
        
        public final byte[] getColBytes(final int column) {
            assert offset + colstart[column] + row[column].cellwidth <= rowinstance.length :
                "column = " + column + ", offset = " + offset + ", colstart[column] = " + colstart[column] + ", row[column].cellwidth() = " + row[column].cellwidth + ", rowinstance.length = " + rowinstance.length;
            final int w = row[column].cellwidth;
            final byte[] c = new byte[w];
            System.arraycopy(rowinstance, offset + colstart[column], c, 0, w);
            return c;
        }
        
        public final char[] getColChars(final int column) {
            final int w = row[column].cellwidth;
            final char[] c = new char[w];
            System.arraycopy(rowinstance, offset + colstart[column], c, 0, w);
            return c;
        }
        
        public final void writeToArray(final int column, final byte[] target, final int targetOffset) {
            // this method shall replace the getColBytes where possible, bacause it may reduce the number of new byte[] allocations
            assert (targetOffset + row[column].cellwidth <= target.length) : "targetOffset = " + targetOffset + ", target.length = " + target.length + ", row[column].cellwidth() = " + row[column].cellwidth;
            System.arraycopy(rowinstance, offset + colstart[column], target, targetOffset, row[column].cellwidth);
        }
        
        public final String toPropertyForm(final boolean includeBraces, final boolean decimalCardinal, final boolean longname) {
            final serverByteBuffer bb = new serverByteBuffer();
            if (includeBraces) bb.append('{');
            for (int i = 0; i < row.length; i++) {
                bb.append((longname) ? row[i].description : row[i].nickname);
                bb.append('=');
                if ((decimalCardinal) && (row[i].celltype == kelondroColumn.celltype_cardinal)) {
                    bb.append(Long.toString(getColLong(i)));
                } else if ((decimalCardinal) && (row[i].celltype == kelondroColumn.celltype_bitfield)) {
                    bb.append((new kelondroBitfield(getColBytes(i))).exportB64());
                } else if ((decimalCardinal) && (row[i].celltype == kelondroColumn.celltype_binary)) {
                    assert row[i].cellwidth == 1;
                    bb.append(Integer.toString((0xff & getColByte(i))));
                } else {
                    bb.append(rowinstance, offset + colstart[i], row[i].cellwidth);
                }
                if (i < row.length - 1) {
                    bb.append(',');
                    if (longname) bb.append(' ');
                }
            }
            if (includeBraces) bb.append('}');
            //System.out.println("DEBUG-ROW " + bb.toString());
            return bb.toString();
        }
        
        public final String toString() {
            return toPropertyForm(true, false, false);
        }
        
    }
    
    public final class EntryIndex extends Entry {
        private final int index;
        public EntryIndex(final byte[] row, final int i) {
            super(row, false);
            this.index = i;
        }
        public int index() {
            return index;
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
    
    public final boolean subsumes(final kelondroRow otherRow) {
        // returns true, if this row has at least all columns as the other row
        // and possibly some more
        if (this.objectsize < otherRow.objectsize) return false;
        for (int i = 0; i < otherRow.row.length; i++) {
            if ((this.row[i].cellwidth == otherRow.row[i].cellwidth) &&
                (this.row[i].celltype == kelondroColumn.celltype_bitfield) &&
                (otherRow.row[i].celltype == kelondroColumn.celltype_binary)) continue;
            if (!(this.row[i].equals(otherRow.row[i]))) return false;
        }
        return true;
    }
    
    public final boolean equals(final kelondroRow otherRow) {
        if (this.objectsize != otherRow.objectsize) return false;
        if (this.columns() != otherRow.columns()) return false;
        for (int i = 0; i < otherRow.row.length; i++) {
            if (!(this.row[i].equals(otherRow.row[i]))) return false;
        }
        return true;
    }
    
}
