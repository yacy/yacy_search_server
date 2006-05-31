// kelondroSplittedTree.java
// -------------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// created 07.01.2006
//
// $LastChangedDate: 2005-09-22 22:01:26 +0200 (Thu, 22 Sep 2005) $
// $LastChangedRevision: 774 $
// $LastChangedBy: orbiter $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class kelondroSplittedTree implements kelondroIndex {

    private kelondroTree[] ktfs;
    private kelondroOrder order;
    private int ff;
    
    private static File dbFile(File path, String filenameStub, int forkfactor, int columns, int number) {
        String ns = Integer.toHexString(number).toUpperCase();
        while (ns.length() < 2) ns = "0" + ns;
        String ff = Integer.toHexString(forkfactor).toUpperCase();
        while (ff.length() < 2) ff = "0" + ff;
        String co = Integer.toHexString(columns).toUpperCase();
        while (co.length() < 2) co = "0" + co;
        return new File(path, filenameStub + "." + ff + "." + co + "." + ns + ".ktc");
    }

    private static boolean existsAll(File pathToFiles, String filenameStub, int forkfactor, int columns){
        for (int i = 0; i < forkfactor; i++) {
            if (!(dbFile(pathToFiles, filenameStub, forkfactor, columns, i)).exists()) return false;
        }
        return true;
    }
    
    public kelondroSplittedTree(File pathToFiles, String filenameStub, kelondroOrder objectOrder,
                            long buffersize,
                            int forkfactor,
                            int[] columns,
                            int txtProps, int txtPropsWidth,
                            boolean exitOnFail) {
        ktfs = new kelondroTree[forkfactor];
        File f;
        for (int i = 0; i < forkfactor; i++) {
            f = dbFile(pathToFiles, filenameStub, forkfactor, columns.length, i);
            if (f.exists()) {
                try {
                    ktfs[i] = new kelondroTree(f, buffersize/forkfactor, kelondroTree.defaultObjectCachePercent);
                    this.order = ktfs[i].order();
                } catch (IOException e) {
                    ktfs[i] = new kelondroTree(f, buffersize/forkfactor, kelondroTree.defaultObjectCachePercent,
                                               columns, objectOrder, txtProps, txtPropsWidth, exitOnFail);
                    this.order = objectOrder;
                }
            } else {
                ktfs[i] = new kelondroTree(f, buffersize/forkfactor, kelondroTree.defaultObjectCachePercent,
                                           columns, objectOrder, txtProps, txtPropsWidth, exitOnFail);
                this.order = objectOrder;
            }
        }
        ff = forkfactor;
    }

    public kelondroSplittedTree(File pathToFiles, String filenameStub, kelondroOrder objectOrder,
                            long buffersize, int forkfactor, int columns) throws IOException {
        ktfs = new kelondroTree[forkfactor];
        for (int i = 0; i < forkfactor; i++) {
            ktfs[i] = new kelondroTree(dbFile(pathToFiles, filenameStub, forkfactor, columns, i), buffersize/forkfactor, kelondroTree.defaultObjectCachePercent);
        }
        ff = forkfactor;
        this.order = objectOrder;
    }
    
    public static kelondroSplittedTree open(File pathToFiles, String filenameStub, kelondroOrder objectOrder,
                    long buffersize,
                    int forkfactor,
                    int[] columns, int txtProps, int txtPropsWidth,
                    boolean exitOnFail) throws IOException {
        // generated a new splittet tree if it not exists or
        // opens an existing one
        if (existsAll(pathToFiles, filenameStub, forkfactor, columns.length)) {
            return new kelondroSplittedTree(pathToFiles, filenameStub, objectOrder, buffersize, forkfactor, columns.length);
        } else {
            return new kelondroSplittedTree(pathToFiles, filenameStub, objectOrder,
                            buffersize,
                            forkfactor,
                            columns, txtProps, txtPropsWidth,
                            exitOnFail);
        }
    }
    
    public int columns() {
        return ktfs[0].columns();
    }

    public int columnSize(int column) {
        return ktfs[0].columnSize(column);
    }
    
    private int partition(byte[] key) {
        // return number of db file where this key should be managed
        return (int) order.partition(key, ff);
    }
    
    public kelondroRow.Entry get(byte[] key) throws IOException {
        return ktfs[partition(key)].get(key);
    }

    public byte[][] put(byte[][] row) throws IOException {
        return ktfs[partition(row[0])].put(row);
    }

    public byte[][] remove(byte[] key) throws IOException {
        return ktfs[partition(key)].remove(key);
    }

    public Iterator rows(boolean up, boolean rotating) throws IOException {
        return new ktfsIterator(up, rotating);
    }
    
    public class ktfsIterator implements Iterator {

        int c = 0;
        Iterator ktfsI;
        boolean up, rot;
        
        public ktfsIterator(boolean up, boolean rotating) throws IOException {
            this.up = up;
            this.rot = rotating;
            c = (up) ? 0 : (ff - 1);
            ktfsI = ktfs[c].rows(up, false, null);
        }
        
        public boolean hasNext() {
            return ((rot) ||
                    (ktfsI.hasNext()) ||
                    ((up) && (c < ff)) ||
                    ((!(up)) && (c > 0)));
        }

        public Object next() {
            if (ktfsI.hasNext()) return ktfsI.next();
            if (up) {
                if (c < (ff - 1)) {
                    c++;
                    try {
                        ktfsI = ktfs[c].rows(true, false, null);
                    } catch (IOException e) {
                        return null;
                    }
                    return ktfsI.next();
                } else {
                    if (rot) {
                        c = 0;
                        try {
                            ktfsI = ktfs[c].rows(true, false, null);
                        } catch (IOException e) {
                            return null;
                        }
                        return ktfsI.next();
                    }
                    return null;
                }
            } else {
                if (c > 0) {
                    c--;
                    try {
                        ktfsI = ktfs[c].rows(false, false, null);
                    } catch (IOException e) {
                        return null;
                    }
                    return ktfsI.next();
                } else {
                    if (rot) {
                        c = ff - 1;
                        try {
                            ktfsI = ktfs[c].rows(false, false, null);
                        } catch (IOException e) {
                            return null;
                        }
                        return ktfsI.next();
                    }
                    return null;
                }
            }
        }

        public void remove() {
            ktfsI.remove();
        }
        
    }


}
