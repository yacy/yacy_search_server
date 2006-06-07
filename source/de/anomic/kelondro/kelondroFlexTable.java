// kelondroFrexTable.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 01.06.2006 on http://www.anomic.de
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


import java.io.File;
import java.io.IOException;

public class kelondroFlexTable extends kelondroFlexWidthArray implements kelondroIndex {

    private kelondroCollection index;
    private kelondroRow indexrow;
    
    public kelondroFlexTable(File path, String tablename, kelondroRow rowdef, boolean exitOnFail) throws IOException {
        super(path, tablename, rowdef, exitOnFail);
        
        // fill the index
        this.index = new kelondroCollection(super.row().width(0) + 4);
        /*
        kelondroFixedWidthArray indexArray = new kelondroFixedWidthArray(new File(path, colfilename(0,0)));
        for (int i = 0; i < indexArray.size(); i++) index.put(indexArray.get(i).getColBytes(0), new Integer(i));
        indexArray.close();
        */
        this.indexrow = new kelondroRow(new int[]{super.row().width(0), 4});
        kelondroRow.Entry indexentry;
        for (int i = 0; i < super.col[0].size(); i++) {
            indexentry = indexrow.newEntry();
            indexentry.setCol(0, super.col[0].get(i).getColBytes(0));
            indexentry.setColLongB256(1, i);
            index.add(indexentry.bytes());
        }
        this.index.setOrdering(kelondroNaturalOrder.naturalOrder);
    }
    
    /*
    private final static byte[] read(File source) throws IOException {
        byte[] buffer = new byte[(int) source.length()];
        InputStream fis = null;
        try {
            fis = new FileInputStream(source);
            int p = 0, c;
            while ((c = fis.read(buffer, p, buffer.length - p)) > 0) p += c;
        } finally {
            if (fis != null) try { fis.close(); } catch (Exception e) {}
        }
        return buffer;
    }
    */
    
    public kelondroRow.Entry get(byte[] key) throws IOException {
        kelondroRow.Entry indexentry = this.indexrow.newEntry(this.index.get(key));
        if (indexentry == null) return null;
        return super.get((int) indexentry.getColLongB256(1));
    }

    public kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        kelondroRow.Entry indexentry = this.indexrow.newEntry(this.index.get(row.getColBytes(0)));
        if (indexentry == null) {
            indexentry = indexrow.newEntry();
            indexentry.setCol(0, row.getColBytes(0));
            indexentry.setColLongB256(1, super.add(row));
            index.add(indexentry.bytes());
            return null;
        } else {
            return super.set((int) indexentry.getColLongB256(1), row);
        }
    }
    
    public kelondroRow.Entry remove(byte[] key) throws IOException {
        kelondroRow.Entry indexentry = this.indexrow.newEntry(this.index.get(key));
        if (indexentry == null) return null;
        kelondroRow.Entry r = super.get((int) indexentry.getColLongB256(1));
        super.remove((int) indexentry.getColLongB256(1));
        return r;
    }

}
