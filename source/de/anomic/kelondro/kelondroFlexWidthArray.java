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
import java.util.HashMap;
import java.util.Map;

import de.anomic.server.serverFileUtils;

public class kelondroFlexWidthArray implements kelondroArray {

    protected kelondroFixedWidthArray[] col;
    protected kelondroRow rowdef;
    
    public kelondroFlexWidthArray(File path, String tablename, kelondroRow rowdef) throws IOException {
        this.rowdef = rowdef;
        
        // initialize columns
        col = new kelondroFixedWidthArray[rowdef.columns()];
        String check = "";
        for (int i = 0; i < rowdef.columns(); i++) {
            col[i] = null;
            check += '_';
        }
        
        // check if table directory exists
        File tabledir = new File(path, tablename);
        if (tabledir.exists()) {
            if (!(tabledir.isDirectory())) throw new IOException("path " + tabledir.toString() + " must be a directory");
        } else {
            tabledir.mkdirs();
            tabledir.mkdir();
        }

        // save/check property file for this array
        File propfile = new File(tabledir, "properties");
        Map props = new HashMap();
        if (propfile.exists()) {
            props = serverFileUtils.loadHashMap(propfile);
            String stored_rowdef = (String) props.get("rowdef");
            if ((stored_rowdef == null) || (!(rowdef.subsumes(new kelondroRow(stored_rowdef))))) {
                System.out.println("FATAL ERROR: stored rowdef '" + stored_rowdef + "' does not match with new rowdef '" + 
                        rowdef + "' for flex table '" + path + "'");
                System.exit(-1);
            }
        }
        props.put("rowdef", rowdef.toString());
        serverFileUtils.saveMap(propfile, props, "FlexWidthArray properties");
        
        // open existing files
        String[] files = tabledir.list();
        for (int i = 0; i < files.length; i++) {
            if ((files[i].startsWith("col.") && (files[i].endsWith(".list")))) {
                int colstart = Integer.parseInt(files[i].substring(4, 7));
                int colend   = (files[i].charAt(7) == '-') ? Integer.parseInt(files[i].substring(8, 11)) : colstart;
                
                kelondroColumn columns[] = new kelondroColumn[colend - colstart + 1];
                for (int j = colstart; j <= colend; j++) columns[j-colstart] = rowdef.column(j);
                col[colstart] = new kelondroFixedWidthArray(new File(tabledir, files[i]), new kelondroRow(columns), 16);
                for (int j = colstart; j <= colend; j++) check = check.substring(0, j) + "X" + check.substring(j + 1);
            }
        }
        
        // check if all columns are there
        int p, q;
        while ((p = check.indexOf('_')) >= 0) {
            q = p;
            if (p != 0) {
                while ((q <= check.length() - 1) && (check.charAt(q) == '_')) q++;
                q--;
            }
            // create new array file
            kelondroColumn[] columns = new kelondroColumn[q - p + 1];
            for (int j = p; j <= q; j++) {
                columns[j - p] = rowdef.column(j);
                check = check.substring(0, j) + "X" + check.substring(j + 1);
            }
            col[p] = new kelondroFixedWidthArray(new File(tabledir, colfilename(p, q)), new kelondroRow(columns), 16);
        }
    }
    
    public void close() throws IOException {
        for (int i = 0; i < col.length; i++) if (col[i] != null) col[i].close();
    }
    
    protected static final String colfilename(int start, int end) {
        String f = Integer.toString(end);
        while (f.length() < 3) f = "0" + f;
        if (start == end) return "col." + f + ".list";
        f = Integer.toString(start) + "-" + f;
        while (f.length() < 7) f = "0" + f;
        return "col." + f + ".list";
    }
    

    public kelondroRow row() {
        return rowdef;
    }
    
    public int size() {
        return col[0].size();
    }
    
    public kelondroRow.Entry set(int index, kelondroRow.Entry rowentry) throws IOException {
        int c = 0;
        kelondroRow.Entry e0, e1, p;
        p = rowdef.newEntry();
        int lastcol;
        synchronized (col) {
            while (c < rowdef.columns()) {
                lastcol  = c + col[c].row().columns() - 1;
                e0 = col[c].row().newEntry(
                        rowentry.bytes(),
                        rowdef.colstart[c],
                        rowdef.colstart[lastcol] - rowdef.colstart[c]
                                + rowdef.width(lastcol));
                e1 = col[c].set(index, e0);
                for (int i = 0; i < col[c].row().columns(); i++) {
                    p.setCol(c + i, e1.getColBytes(i));
                }
                c = c + col[c].row().columns();
            }
        }
        return p;
    }
    
    public int add(kelondroRow.Entry rowentry) throws IOException {
        kelondroRow.Entry e;
        int index = -1;
        int lastcol;
        synchronized (col) {
            e = col[0].row().newEntry(rowentry.bytes(), 0, rowdef.width(0));
            index = col[0].add(e);
            int c = col[0].row().columns();

            while (c < rowdef.columns()) {
                lastcol  = c + col[c].row().columns() - 1;
                e = col[c].row().newEntry(
                        rowentry.bytes(),
                        rowdef.colstart[c],
                        rowdef.colstart[lastcol] + rowdef.width(lastcol) - rowdef.colstart[c]);
                col[c].set(index,e);
                c = c + col[c].row().columns();
            }
        }
        return index;
    }

    public kelondroRow.Entry get(int index) throws IOException {
        int r = 0;
        kelondroRow.Entry e, p;
        p = rowdef.newEntry();
        synchronized (col) {
            while (r < rowdef.columns()) {
                e = col[r].get(index);
                for (int i = 0; i < col[r].row().columns(); i++)
                    p.setCol(r + i, e.getColBytes(i));
                r = r + col[r].row().columns();
            }
        }
        return p;
    }

    public void remove(int index) throws IOException {
        int r = 0;
        synchronized (col) {
            
            // remove only from the first column
            col[0].remove(index);
            r = r + col[r].row().columns();
            
            // the other columns will be blanked out only
            while (r < rowdef.columns()) {
                col[r].set(index, null);
                r = r + col[r].row().columns();
            }
        }
    }
    
    public void print() throws IOException {
        System.out.println("PRINTOUT of table, length=" + size());
        kelondroRow.Entry row;
        for (int i = 0; i < col[0].USAGE.allCount(); i++) {
            System.out.print("row " + i + ": ");
            row = get(i);
            System.out.println(row.toString());
            //for (int j = 0; j < row().columns(); j++) System.out.print(((row.empty(j)) ? "NULL" : row.getColString(j, "UTF-8")) + ", ");
            //System.out.println();
        }
        System.out.println("EndOfTable");
    }

    public static void main(String[] args) {
        //File f = new File("d:\\\\mc\\privat\\fixtest.db");
        File f = new File("/Users/admin/");
        kelondroRow rowdef = new kelondroRow("byte[] a-12, byte[] b-4");
        String testname = "flextest";
        try {
            System.out.println("erster Test");
            new File(f, testname).delete();
            
            kelondroFlexWidthArray k = new kelondroFlexWidthArray(f, "flextest", rowdef);
            k.add(k.row().newEntry(new byte[][]{"a".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"b".getBytes(), "xxxx".getBytes()}));
            k.remove(0);
            
            k.add(k.row().newEntry(new byte[][]{"c".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"d".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"e".getBytes(), "xxxx".getBytes()}));
            k.add(k.row().newEntry(new byte[][]{"f".getBytes(), "xxxx".getBytes()}));
            k.remove(0);
            k.remove(1);
            
            k.print();
            k.col[0].print(true);
            k.col[1].print(true);
            k.close();
            
            
            System.out.println("zweiter Test");
            new File(f, testname).delete();
            k = new kelondroFlexWidthArray(f, "flextest", rowdef);
            for (int i = 1; i <= 20; i = i * 2) {
                for (int j = 0; j < i*2; j++) {
                    k.add(k.row().newEntry(new byte[][]{(Integer.toString(i) + "-" + Integer.toString(j)).getBytes(), "xxxx".getBytes()}));
                }
                for (int j = 0; j < i; j++) {
                    k.remove(j);
                }
            }
            k.print();
            k.col[0].print(true);
            k.close();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
