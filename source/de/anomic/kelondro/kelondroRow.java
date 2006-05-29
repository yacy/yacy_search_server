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

import java.util.HashMap;

public class kelondroRow {

    public static final int encoder_b64e   = 0;
    public static final int encoder_string = 1;
    public static final int encoder_bytes  = 2;
    public static final int encoder_char   = 3;
    
    
    private kelondroColumn[] row;
    private HashMap encodedFormConfiguration;
    private int     encodedFormLength;
    
    public kelondroRow(kelondroColumn[] row) {
        this.row = row;
        this.encodedFormConfiguration = null;
        this.encodedFormLength = -1;
    }

    public kelondroRow(int[] row) {
        this.row = new kelondroColumn[row.length];
        for (int i = 0; i < row.length; i++) this.row[i] = new kelondroColumn(kelondroColumn.celltype_undefined, row[i], "col_" + i, "");
        this.encodedFormConfiguration = null;
        this.encodedFormLength = -1;
    }
    
    public int columns() {
        return this.row.length;
    }
    
    public int width(int row) {
        return this.row[row].dbwidth();
    }
    
    public int[] widths() {
        int[] w = new int[this.row.length];
        for (int i = 0; i < this.row.length; i++) w[i] = row[i].dbwidth();
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

    public class Entry {

        private byte[][] cols;
        
        public Entry(byte[][] cols) {
            this.cols = cols;
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
                case kelondroColumn.celltype_bytes:
                    System.arraycopy(cols[i], 0, b, p, length);
                    p += length;
                    continue;
                case kelondroColumn.celltype_string:
                    System.arraycopy(cols[i], 0, b, p, length);
                    p += length;
                    continue;
                case kelondroColumn.celltype_cardinal:
                    if (encoder == encoder_b64e) {
                        long c = kelondroRecords.bytes2long(cols[i]);
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
}
