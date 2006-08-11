// kelondroRow.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
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
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class kelondroRow {
   
    private   kelondroColumn[] row;
    protected int[]            colstart;
    private   int              objectsize;
    private   Map              nickref = null;
    
    public kelondroRow(kelondroColumn[] row) {
        this.row = row;
        this.colstart = new int[row.length];
        this.objectsize = 0;
        for (int i = 0; i < row.length; i++) {
            this.colstart[i] = this.objectsize;
            this.objectsize += this.row[i].cellwidth();
        }
    }

    public kelondroRow(String structure) {
        // define row with row syntax
        // example:
        //# Structure=<pivot-12>,'=',<UDate-3>,<VDate-3>,<LCount-2>,<GCount-2>,<ICount-2>,<DCount-2>,<TLength-3>,<WACount-3>,<WUCount-3>,<Flags-1>

        // parse a structure string
        kelondroColumn pivot_col = null;

        // parse pivot definition:
        int p = structure.indexOf(",'='");
        if (p >= 0) {
            String pivot = structure.substring(0, p);
            structure = structure.substring(p + 5);
            pivot_col = new kelondroColumn(pivot);
        }
        
        // parse property part definition:
        p = structure.indexOf(",'|'");
        if (p < 0) p = structure.length();
        ArrayList l = new ArrayList();
        String attr = structure.substring(0, p);
        StringTokenizer st = new StringTokenizer(attr, ",");
        while (st.hasMoreTokens()) {
            l.add(new kelondroColumn(st.nextToken()));
        }
        
        // define columns
        int piv_offset = (pivot_col == null) ? 0 : 1;
        this.row = new kelondroColumn[l.size() + piv_offset];
        this.colstart = new int[row.length];
        this.objectsize = 0;
        if (pivot_col != null) {
            this.colstart[0] = 0;
            this.row[0] = pivot_col;
            this.objectsize += this.row[0].cellwidth();
        }
        for (int i = 0; i < l.size(); i++) {
            this.colstart[i + piv_offset] = this.objectsize;
            this.row[i + piv_offset] = (kelondroColumn) l.get(i);
            this.objectsize += this.row[i + piv_offset].cellwidth();
        }

    }
    
    private void genNickRef() {
        if (nickref != null) return;
        nickref = new HashMap(row.length);
        for (int i = 0; i < row.length; i++) nickref.put(row[i].nickname(), new Object[]{row[i], new Integer(colstart[i])});
    }
    
    public int columns() {
        return this.row.length;
    }
    
    public int objectsize() {
        return this.objectsize;
    }
    
    public kelondroColumn column(int col) {
        return row[col];
    }
    
    public int width(int row) {
        return this.row[row].cellwidth();
    }
    
    public int[] widths() {
        int[] w = new int[this.row.length];
        for (int i = 0; i < this.row.length; i++) w[i] = row[i].cellwidth();
        return w;
    }
    
    public String toString() {
        StringBuffer s = new StringBuffer();
        s.append(row[0].toString());
        for (int i = 1; i < row.length; i++) {
            s.append(", ");
            s.append(row[i].toString());
        }
        return new String(s);
    }
    
    public Entry newEntry() {
        return new Entry();
    }
    
    public Entry newEntry(byte[] rowinstance) {
        if (rowinstance == null) return null;
        return new Entry(rowinstance);
    }
    
    public Entry newEntry(byte[] rowinstance, int start, int length) {
        if (rowinstance == null) return null;
        return new Entry(rowinstance, start, length);
    }
    
    public Entry newEntry(byte[][] cells) {
        if (cells == null) return null;
        return new Entry(cells);
    }
    
    public Entry newEntry(String external) {
        if (external == null) return null;
        return new Entry(external);
    }
    
    public class Entry {

        private byte[] rowinstance;
        
        public Entry() {
            rowinstance = new byte[objectsize];
            for (int i = 0; i < objectsize; i++) this.rowinstance[i] = 0;
        }
        
        public Entry(byte[] rowinstance) {
            if (rowinstance.length == objectsize) {
                this.rowinstance = rowinstance;
            } else {
                this.rowinstance = new byte[objectsize];
                int ll = Math.min(objectsize, rowinstance.length);
                System.arraycopy(rowinstance, 0, this.rowinstance, 0, ll);
                for (int i = ll; i < objectsize; i++) this.rowinstance[i] = 0;
            }
        }
        
        public Entry(byte[] rowinstance, int start, int length) {
            this.rowinstance = new byte[length];
            System.arraycopy(rowinstance, start, this.rowinstance, 0, length);
            for (int i = rowinstance.length; i < objectsize; i++) this.rowinstance[i] = 0;
        }
        
        public Entry(byte[][] cols) {
            rowinstance = new byte[objectsize];
            int ll;
            for (int i = 0; i < row.length; i++) {
                if ((i >= cols.length) || (cols[i] == null)) {
                    for (int j = 0; j < row[i].cellwidth(); j++) this.rowinstance[colstart[i] + j] = 0;
                } else {
                    ll = Math.min(cols[i].length, row[i].cellwidth());
                    System.arraycopy(cols[i], 0, rowinstance, colstart[i], ll);
                    for (int j = ll; j < row[i].cellwidth(); j++) this.rowinstance[colstart[i] + j] = 0;
                }
            }
        }
        
        public Entry(String external) {
            // parse external form
            if (external.charAt(0) == '{') external = external.substring(1, external.length() - 1);
            String[] elts = external.split(",");
            if (nickref == null) genNickRef();
            String nick;
            int p;
            Object[] f;
            rowinstance = new byte[objectsize];
            for (int i = 0; i < elts.length; i++) {
                p = elts[i].indexOf('=');
                if (p > 0) {
                    nick = elts[i].substring(0, p).trim();
                    f = (Object[]) nickref.get(nick);
                    System.arraycopy(elts[i].substring(p + 1).trim().getBytes(), 0, rowinstance, ((Integer) f[1]).intValue(), ((kelondroColumn) f[0]).cellwidth());
                }
            }
        }
        
        public byte[] bytes() {
            return rowinstance;
        }
        
        public int columns() {
            return row.length;
        }
        
        public int objectsize() {
            return objectsize;
        }
        
        public boolean empty(int column) {
            return rowinstance[colstart[column]] == 0;
        }
        
        public void setCol(int column, byte[] cell) {
            int valuewidth = row[column].cellwidth();
            int targetoffset = colstart[column];
            if (cell == null) {
                while (valuewidth-- > 0) rowinstance[targetoffset + valuewidth] = 0;
            } else {
                System.arraycopy(cell, 0, rowinstance, targetoffset, Math.min(cell.length, valuewidth)); // error?
                if (cell.length < valuewidth) {
                    while (valuewidth-- > cell.length) rowinstance[targetoffset + valuewidth] = 0;
                }
            }
        }
        
        public void setCol(int column, byte c) {
            rowinstance[colstart[column]] = c;
        }
        
        public void setCol(int column, String cell, String encoding) {
            if (encoding == null)
                setCol(column, cell.getBytes());
            else
                try {
                    setCol(column, cell.getBytes(encoding));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
        }
        
        public void setCol(int column, long cell) {
            // uses the column definition to choose the right encoding
            switch (row[column].encoder()) {
            case kelondroColumn.encoder_none:
                throw new kelondroException("ROW", "setColLong has celltype none, no encoder given");
            case kelondroColumn.encoder_b64e:
                kelondroBase64Order.enhancedCoder.encodeLong(cell, rowinstance, colstart[column], row[column].cellwidth());
                break;
            case kelondroColumn.encoder_b256:
                kelondroNaturalOrder.encodeLong(cell, rowinstance, colstart[column], row[column].cellwidth());
                break;
            case kelondroColumn.encoder_bytes:
                throw new kelondroException("ROW", "setColLong of celltype bytes not applicable");
            }
        }
        
        public String getColString(int column, String encoding) {
            int length = row[column].cellwidth();
            int offset = colstart[column];
            if (rowinstance[offset] == 0) return null;
            if (length > rowinstance.length - offset) length = rowinstance.length - offset;
            while ((length > 0) && (rowinstance[offset + length - 1] == 0)) length--;
            if (length == 0) return null;
            try {
                if ((encoding == null) || (encoding.length() == 0))
                    return new String (rowinstance, offset, length);
                else
                    return new String(rowinstance, offset, length, encoding);
            } catch (UnsupportedEncodingException e) {
                return "";
            }
        }
        
        public long getColLong(int column) {
            // uses the column definition to choose the right encoding
            switch (row[column].encoder()) {
            case kelondroColumn.encoder_none:
                throw new kelondroException("ROW", "getColLong has celltype none, no encoder given");
            case kelondroColumn.encoder_b64e:
                return kelondroBase64Order.enhancedCoder.decodeLong(rowinstance, colstart[column], row[column].cellwidth());
            case kelondroColumn.encoder_b256:
                return kelondroNaturalOrder.decodeLong(rowinstance, colstart[column], row[column].cellwidth());
            case kelondroColumn.encoder_bytes:
                throw new kelondroException("ROW", "getColLong of celltype bytes not applicable");
            }
            throw new kelondroException("ROW", "getColLong did not find appropriate encoding");
        }

        public byte getColByte(int column) {
            return rowinstance[colstart[column]];
        }
        
        public byte[] getColBytes(int column) {
            byte[] c = new byte[row[column].cellwidth()];
            System.arraycopy(rowinstance, colstart[column], c, 0, row[column].cellwidth());
            return c;
        }
        
        public String toPropertyForm(boolean includeBraces, boolean decimalCardinal) {
            StringBuffer sb = new StringBuffer();
            if (includeBraces) sb.append("{");
            int encoder, cellwidth;
            for (int i = 0; i < row.length; i++) {
                encoder = row[i].encoder();
                cellwidth = row[i].cellwidth();
                switch (row[i].celltype()) {
                case kelondroColumn.celltype_undefined:
                    throw new kelondroException("ROW", "toEncodedForm of celltype undefined not possible");
                case kelondroColumn.celltype_boolean:
                    throw new kelondroException("ROW", "toEncodedForm of celltype boolean not yet implemented");
                case kelondroColumn.celltype_binary:
                    sb.append(row[i].nickname());
                    sb.append('=');
                    for (int j = colstart[i]; j < colstart[i] + cellwidth; j++) sb.append((char) rowinstance[j]);
                    sb.append(',');
                    continue;
                case kelondroColumn.celltype_string:
                    sb.append(row[i].nickname());
                    sb.append('=');
                    for (int j = colstart[i]; j < colstart[i] + cellwidth; j++) sb.append((char) rowinstance[j]);
                    sb.append(',');
                    continue;
                case kelondroColumn.celltype_cardinal:
                    if (decimalCardinal) {
                        sb.append(row[i].nickname());
                        sb.append('=');
                        sb.append(Long.toString(bytes2long(rowinstance, colstart[i], cellwidth)));
                        sb.append(',');
                        continue;
                    } else if (encoder == kelondroColumn.encoder_b64e) {
                        sb.append(row[i].nickname());
                        sb.append('=');
                        long c = bytes2long(rowinstance, colstart[i], cellwidth);
                        sb.append(kelondroBase64Order.enhancedCoder.encodeLongSmart(c, cellwidth).getBytes());
                        sb.append(',');
                        continue;
                    } else throw new kelondroException("ROW", "toEncodedForm of celltype cardinal has no encoder (" + encoder + ")");
                }
            }
            if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1); // remove ',' at end
            if (includeBraces) sb.append("}");
            return sb.toString();
        }
        
        public String toString() {
            return toPropertyForm(true, true);
        }
    }
    
    public final static void long2bytes(long x, byte[] b, int offset, int length) {
        for (int i = length - 1; i >= 0; i--) {
            b[offset + i] = (byte) (x & 0XFF);
            x >>= 8;
        }
    }
    
    public final static long bytes2long(byte[] b, int offset, int length) {
        if (b == null) return 0;
        long x = 0;
        for (int i = 0; i < length; i++) x = (x << 8) | (0xff & b[offset + i]);
        return x;
    }
    
    public boolean subsumes(kelondroRow otherRow) {
        // returns true, if this row has at least all columns as the other row
        // and possibly some more
        if (this.objectsize < otherRow.objectsize) return false;
        for (int i = 0; i < otherRow.row.length; i++) {
            if (!(this.row[i].equals(otherRow.row[i]))) return false;
        }
        return true;
    }
    
}
