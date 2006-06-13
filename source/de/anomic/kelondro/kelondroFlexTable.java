// kelondroFlexTable.java
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
import java.util.Iterator;

public class kelondroFlexTable extends kelondroFlexWidthArray implements kelondroIndex {

    private kelondroCollectionIntMap index;
    
    public kelondroFlexTable(File path, String tablename, kelondroRow rowdef, boolean exitOnFail) throws IOException {
        super(path, tablename, rowdef, exitOnFail);
        
        // fill the index
        this.index = new kelondroCollectionIntMap(super.row().width(0), 0);
        /*
        kelondroFixedWidthArray indexArray = new kelondroFixedWidthArray(new File(path, colfilename(0,0)));
        for (int i = 0; i < indexArray.size(); i++) index.put(indexArray.get(i).getColBytes(0), new Integer(i));
        indexArray.close();
        */
        System.out.print("Loading " + path);
        Iterator content = super.col[0].contentNodes();
        kelondroRecords.Node node;
        int i;
        while (content.hasNext()) {
            node = (kelondroRecords.Node) content.next();
            i = node.handle().hashCode();
            index.addi(node.getValueRow(), i);
            if ((i % 10000) == 0) System.out.print('.');
        }
        index.sort(super.row().width(0));
        System.out.println(index.size() + " index entries initialized and sorted");
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
    
    public synchronized kelondroRow.Entry get(byte[] key) throws IOException {
        synchronized (index) {
            int i = index.geti(key);
            if (i >= this.size()) {
                System.out.println("errror");
            }
            if (i < 0) return null;
            return super.get(i);
        }
    }

    public synchronized kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        synchronized (index) {
            int i = index.geti(row.getColBytes(0));
            if (i < 0) {
                index.puti(row.getColBytes(0), super.add(row));
                return null;
            } else {
                return super.set(i, row);
            }
        }
    }
    
    public synchronized kelondroRow.Entry remove(byte[] key) throws IOException {
        synchronized (index) {
            int i = index.removei(key);
            if (i < 0) return null;
            kelondroRow.Entry r = super.get(i);
            super.remove(i);
            return r;
        }
    }

}
