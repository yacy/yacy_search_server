// kelondroFlexWidthArray.java
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

public class kelondroFlexWidthArray implements kelondroArray {

    private kelondroFixedWidthArray[] col;
    private kelondroRow rowdef;
    
    public kelondroFlexWidthArray(File path, String tablename, kelondroRow rowdef, boolean exitOnFail) throws IOException {
        this.rowdef = rowdef;
        
        // initialize columns
        col = new kelondroFixedWidthArray[rowdef.columns()];
        String check = "";
        for (int i = 0; i < rowdef.columns(); i++) {
            col = null;
            check += '_';
        }
        
        // open existing files
        String[] files = path.list();
        for (int i = 0; i < files.length; i++) {
            if ((files[i].startsWith("col.") && (files[i].endsWith(".list")))) {
                int colstart = Integer.parseInt(files[i].substring(4, 7));
                int colend   = (files[i].charAt(7) == '-') ? Integer.parseInt(files[i].substring(8, 11)) : colstart;
                /*
                int columns[] = new int[colend - colstart + 1];
                for (int j = colstart; j <= colend; j++) columns[j-colstart] = rowdef.width(j);
                col[colstart] = new kelondroFixedWidthArray(new File(path, files[i]), columns, 0, true);
                */
                col[colstart] = new kelondroFixedWidthArray(new File(path, files[i]));
                for (int j = colstart; j <= colend; j++) check = check.substring(0, j) + "X" + check.substring(j + 1);
            }
        }
        
        // check if all columns are there
        int p, q;
        while ((p = check.indexOf('_')) >= 0) {
            q = p;
            if (p != 0) while ((check.charAt(q) == '_') && (q <= check.length() - 1)) q++;
            // create new array file
            int columns[] = new int[q - p + 1];
            for (int j = p; j <= q; j++) {
                columns[j - p] = rowdef.width(j);
                check = check.substring(0, j) + "X" + check.substring(j + 1);
            }
            col[p] = new kelondroFixedWidthArray(new File(path, colfilename(p, q)), columns, 0, true);
        }
    }
    
    private static final String colfilename(int start, int end) {
        String f = Integer.toString(end);
        while (f.length() < 3) f = "0" + f;
        if (start == end) return "col." + f + ".list";
        f = Integer.toString(start) + "-" + f;
        while (f.length() < 7) f = "0" + f;
        return "col." + f + ".list";
    }
    
    public int size() {
        return col[0].size();
    }
    
    public int columns() {
        return rowdef.columns();
    }
    
    public synchronized kelondroRow.Entry set(int index, kelondroRow.Entry rowentry) throws IOException {
        int r = 0;
        kelondroRow.Entry e0, e1, p;
        p = rowdef.newEntry();
        while (r < rowdef.columns()) {
            e0 = col[r].row().newEntry(rowentry.bytes(), rowdef.colstart[r], rowdef.colstart[r] - rowdef.colstart[r + col[r].columns() - 1] + rowdef.width(r));
            e1 = col[r].set(index, e0);
            for (int i = 0; i < col[r].columns(); i++)
                p.setCol(r + i, e1.getColBytes(i));
            r = r + col[r].columns();
        }
        return p;
    }
    
    public synchronized kelondroRow.Entry get(int index) throws IOException {
        int r = 0;
        kelondroRow.Entry e, p;
        p = rowdef.newEntry();
        while (r < rowdef.columns()) {
            e = col[r].get(index);
            for (int i = 0; i < col[r].columns(); i++)
                p.setCol(r + i, e.getColBytes(i));
            r = r + col[r].columns();
        }
        return p;
    }

    public synchronized int add(kelondroRow.Entry rowentry) throws IOException {
        kelondroRow.Entry e;
        
        e = col[0].row().newEntry(rowentry.bytes(), rowdef.colstart[0], rowdef.colstart[0] - rowdef.colstart[col[0].columns() - 1] + rowdef.width(0));
        int index = col[0].add(e);
        int r = col[0].columns();
        
        while (r < rowdef.columns()) {
            e = col[r].row().newEntry(rowentry.bytes(), rowdef.colstart[r], rowdef.colstart[r] - rowdef.colstart[r + col[r].columns() - 1] + rowdef.width(r));
            col[r].set(index, e);
            r = r + col[r].columns();
        }
        return index;
    }

    public synchronized void remove(int index) throws IOException {
        int r = 0;
        while (r < rowdef.columns()) {
            col[r].remove(index);
            r = r + col[r].columns();
        }
    }
    
    public void print() throws IOException {
        System.out.println("PRINTOUT of table, length=" + size());
        kelondroRow.Entry row;
        for (int i = 0; i < size(); i++) {
            System.out.print("row " + i + ": ");
            row = get(i);
            for (int j = 0; j < columns(); j++) System.out.print(((row.empty(j)) ? "NULL" : row.getColString(j, "UTF-8")) + ", ");
            System.out.println();
        }
        System.out.println("EndOfTable");
    }
    
}
