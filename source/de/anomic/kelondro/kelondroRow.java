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
import java.util.HashMap;

public class kelondroRow {

    public static final int encoder_b64e   = 0;
    public static final int encoder_string = 1;
    public static final int encoder_bytes  = 2;
    public static final int encoder_char   = 3;
    
    
    private   kelondroColumn[] row;
    protected int[]            colstart;
    private   HashMap          encodedFormConfiguration;
    private   int              encodedFormLength;
    private   int              objectsize;
    
    public kelondroRow(kelondroColumn[] row) {
        this.row = row;
        this.colstart = new int[row.length];
        this.objectsize = 0;
        for (int i = 0; i < row.length; i++) {
            this.colstart[i] = this.objectsize;
            this.objectsize += row[i].cellwidth();
        }
        this.encodedFormConfiguration = null;
        this.encodedFormLength = -1;
    }

    public kelondroRow(int[] row) {
        this.row = new kelondroColumn[row.length];
        this.colstart = new int[row.length];
        this.objectsize = 0;
        for (int i = 0; i < row.length; i++) {
            this.row[i] = new kelondroColumn(kelondroColumn.celltype_undefined, row[i], "col_" + i, "");
            this.colstart[i] = this.objectsize;
            this.objectsize += row[i];
        }
        this.encodedFormConfiguration = null;
        this.encodedFormLength = -1;
    }
    
    public int columns() {
        return this.row.length;
    }
    
    public int size() {
        return this.objectsize;
    }
    
    public int width(int row) {
        return this.row[row].cellwidth();
    }
    
    public int[] widths() {
        int[] w = new int[this.row.length];
        for (int i = 0; i < this.row.length; i++) w[i] = row[i].cellwidth();
        return w;
    }
    
    private static int encoderCode(String encoderName) {
        if (encoderName.equals("b54e")) return encoder_b64e;
        if (encoderName.equals("string")) return encoder_string;
        if (encoderName.equals("char")) return encoder_char;
        return -1;
    }
    
    public void configureEncodedForm(String[][] configuration) {
        encodedFormConfiguration = new HashMap();
        String nick;
        int encoder, length;
        this.encodedFormLength = 0;
        for (int i = 0; i < configuration.length; i++) {
            nick    = configuration[i][0];
            encoder = encoderCode(configuration[i][1]);
            length  = Integer.parseInt(configuration[i][2]);
            encodedFormConfiguration.put(nick, new int[]{encoder, length});
            this.encodedFormLength += length;
        }
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
        return new Entry(rowinstance);
    }
    
    public Entry newEntry(byte[][] cells) {
        if (cells == null) return null;
        return new Entry(cells);
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
                System.arraycopy(rowinstance, 0, this.rowinstance, 0, rowinstance.length);
                for (int i = rowinstance.length; i < objectsize; i++) this.rowinstance[i] = 0;
            }
        }
        
        public Entry(byte[] rowinstance, int start, int length) {
            this.rowinstance = new byte[length];
            System.arraycopy(rowinstance, start, this.rowinstance, 0, length);
            for (int i = rowinstance.length; i < objectsize; i++) this.rowinstance[i] = 0;
        }
        
        public Entry(byte[][] cols) {
            rowinstance = new byte[objectsize];
            for (int i = 0; i < objectsize; i++) this.rowinstance[i] = 0;
            for (int i = 0; i < cols.length; i++) {
                if (cols[i] != null) System.arraycopy(cols[i], 0, rowinstance, colstart[i], Math.min(cols[i].length, row[i].cellwidth()));
            }
        }
        
        public byte[] bytes() {
            return rowinstance;
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
        
        public void setCol(int column, long cell) {
            kelondroNaturalOrder.encodeLong(cell, rowinstance, colstart[column], row[column].cellwidth());
        }
        
        public byte[][] getCols() {
            byte[][] values = new byte[row.length][];

            int length, offset;
            for (int i = 0; i < row.length; i++) {
                length = row[i].cellwidth();
                offset = colstart[i];
                while ((length > 0) && (rowinstance[offset + length - 1] == 0)) length--;
                if (length == 0) {
                    values[i] = null;
                } else {
                    values[i] = new byte[length];
                    System.arraycopy(rowinstance, offset, values[i], 0, length);
                }
            }

            return values;
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
        
        public long getColLongB256(int column) {
            return kelondroNaturalOrder.decodeLong(rowinstance, colstart[column], row[column].cellwidth());
        }
        
        public long getColLongB64E(int column) {
            return kelondroBase64Order.enhancedCoder.decodeLong(rowinstance, colstart[column], row[column].cellwidth());
        }
        
        public byte getColByte(int column) {
            return rowinstance[colstart[column]];
        }
        
        public byte[] getColBytes(int column) {
            byte[] c = new byte[row[column].cellwidth()];
            System.arraycopy(rowinstance, colstart[column], c, 0, row[column].cellwidth());
            return c;
        }
        
        public byte[] toEncodedBytesForm() {
            byte[] b = new byte[encodedFormLength];
            int[] format;
            int encoder, length;
            int p = 0;
            for (int i = 0; i < row.length; i++) {
                format = (int[]) encodedFormConfiguration.get(row[i].nickname());
                encoder = format[0];
                length = format[1];
                switch (row[i].celltype()) {
                case kelondroColumn.celltype_undefined:
                    throw new kelondroException("ROW", "toEncodedForm of celltype undefined not possible");
                case kelondroColumn.celltype_boolean:
                    throw new kelondroException("ROW", "toEncodedForm of celltype boolean not yet implemented");
                case kelondroColumn.celltype_binary:
                    System.arraycopy(rowinstance, colstart[i], b, p, length);
                    p += length;
                    continue;
                case kelondroColumn.celltype_string:
                    System.arraycopy(rowinstance, colstart[i], b, p, length);
                    p += length;
                    continue;
                case kelondroColumn.celltype_cardinal:
                    if (encoder == encoder_b64e) {
                        long c = bytes2long(rowinstance, colstart[i]);
                        System.arraycopy(kelondroBase64Order.enhancedCoder.encodeLongSmart(c, length).getBytes(), 0, b, p, length);
                        p += length;
                        continue;
                    }
                    throw new kelondroException("ROW", "toEncodedForm of celltype cardinal has no encoder (" + encoder + ")");
                case kelondroColumn.celltype_real:
                    throw new kelondroException("ROW", "toEncodedForm of celltype real not yet implemented");
                }
            }
            return b;
         }
    }
    
    public final static void long2bytes(long x, byte[] b, int offset, int length) {
        for (int i = length - 1; i >= 0; i--) {
            b[offset + i] = (byte) (x & 0XFF);
            x >>= 8;
        }
    }
    
    public final static long bytes2long(byte[] b, int offset) {
        if (b == null) return 0;
        long x = 0;
        for (int i = 0; i < b.length; i++) x = (x << 8) | (0xff & b[offset + i]);
        return x;
    }
    
}
