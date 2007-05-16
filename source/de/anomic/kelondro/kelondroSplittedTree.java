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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class kelondroSplittedTree implements kelondroIndex {

    private kelondroTree[] ktfs;
    private kelondroOrder order;
    private int ff;
    private String filename;
    
    private static File dbFile(File path, String filenameStub, int forkfactor, int columns, int number) {
        String ns = Integer.toHexString(number).toUpperCase();
        while (ns.length() < 2) ns = "0" + ns;
        String ff = Integer.toHexString(forkfactor).toUpperCase();
        while (ff.length() < 2) ff = "0" + ff;
        String co = Integer.toHexString(columns).toUpperCase();
        while (co.length() < 2) co = "0" + co;
        return new File(path, filenameStub + "." + ff + "." + co + "." + ns + ".ktc");
    }
    
    public kelondroSplittedTree(File pathToFiles, String filenameStub, kelondroOrder objectOrder,
                            long preloadTime,
                            int forkfactor, kelondroRow rowdef, int txtProps, int txtPropsWidth) {
        try {
            this.filename = new File(pathToFiles, filenameStub).getCanonicalPath();
        } catch (IOException e) {
            this.filename = null;
        }
        ktfs = new kelondroTree[forkfactor];
        File f;
        for (int i = 0; i < forkfactor; i++) {
            f = dbFile(pathToFiles, filenameStub, forkfactor, rowdef.columns(), i);
            ktfs[i] = kelondroTree.open(f, true, preloadTime / forkfactor, rowdef, txtProps, txtPropsWidth);
        }
        this.order = objectOrder;
        ff = forkfactor;
    }
    
    public void reset() throws IOException {
    	for (int i = 0; i < ktfs.length; i++) {
            ktfs[i].reset();
        }
    }
    
    public void close() {
        for (int i = 0; i < ktfs.length; i++) ktfs[i].close();
    }
    
    public int size() {
        return ktfs[0].size();
    }
    
    public kelondroRow row() {
        return ktfs[0].row();
    }
    
    private int partition(byte[] key) {
        // return number of db file where this key should be managed
        return (int) order.partition(key, ff);
    }
    
    public boolean has(byte[] key) throws IOException {
        throw new UnsupportedOperationException("has should not be used with kelondroSplittedTree.");
    }
    
    public kelondroRow.Entry get(byte[] key) throws IOException {
        return ktfs[partition(key)].get(key);
    }

    public synchronized void putMultiple(List rows) throws IOException {
        Iterator i = rows.iterator();
        kelondroRow.Entry row;
        ArrayList[] parts = new ArrayList[ktfs.length];
        for (int j = 0; j < ktfs.length; j++) parts[j] = new ArrayList();
        while (i.hasNext()) {
            row = (kelondroRow.Entry) i.next();
            parts[partition(row.getColBytes(0))].add(row);
        }
        for (int j = 0; j < ktfs.length; j++) ktfs[j].putMultiple(parts[j]);
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        return put(row);
    }
    
    public synchronized void addUnique(kelondroRow.Entry row) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    public synchronized void addUnique(kelondroRow.Entry row, Date entryDate) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    public synchronized void addUniqueMultiple(List rows) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        return ktfs[partition(row.getColBytes(0))].put(row);
    }

    public kelondroRow.Entry remove(byte[] key) throws IOException {
        return ktfs[partition(key)].remove(key);
    }
    
    public kelondroRow.Entry removeOne() throws IOException {
        // removes one entry from the partition with the most entries
        int maxc = -1, maxi = 0;
        for (int i = 0; i < ktfs.length; i++) {
            if (ktfs[i].size() > maxc) {
                maxc = ktfs[i].size();
                maxi = i;
            }
        }
        if (maxc > 0) {
            return ktfs[maxi].removeOne();
        } else {
            return null;
        }
    }
    
    public kelondroCloneableIterator rows(boolean up, byte[] firstKey) throws IOException {
        return new ktfsIterator(up, firstKey);
    }
    
    public class ktfsIterator implements kelondroCloneableIterator {

        int c = 0;
        Iterator ktfsI;
        boolean up;
        
        public ktfsIterator(boolean up, byte[] firstKey) throws IOException {
            this.up = up;
            c = (up) ? 0 : (ff - 1);
            if (firstKey != null) throw new UnsupportedOperationException("ktfsIterator does not work with a start key");
            ktfsI = ktfs[c].rows(up, firstKey); // FIXME: this works only correct with firstKey == null
        }
        
        public Object clone(Object secondKey) {
            try {
                return new ktfsIterator(up, (byte[]) secondKey);
            } catch (IOException e) {
                return null;
            }
        }
        
        public boolean hasNext() {
            return ((ktfsI.hasNext()) ||
                    ((up) && (c < ff)) ||
                    ((!(up)) && (c > 0)));
        }

        public Object next() {
            if (ktfsI.hasNext()) return ktfsI.next();
            if (up) {
                if (c < (ff - 1)) {
                    c++;
                    try {
                        ktfsI = ktfs[c].rows(true, null);
                    } catch (IOException e) {
                        return null;
                    }
                    return ktfsI.next();
                } else {
                    return null;
                }
            } else {
                if (c > 0) {
                    c--;
                    try {
                        ktfsI = ktfs[c].rows(false, null);
                    } catch (IOException e) {
                        return null;
                    }
                    return ktfsI.next();
                } else {
                    return null;
                }
            }
        }

        public void remove() {
            ktfsI.remove();
        }
        
    }

    public kelondroOrder order() {
        return this.order;
    }

    public int primarykey() {
        return 0;
    }

    public kelondroProfile profile() {
        kelondroProfile[] profiles = new kelondroProfile[ktfs.length];
        for (int i = 0; i < ktfs.length; i++) profiles[i] = ktfs[i].profile();
        return kelondroProfile.consolidate(profiles);
    }
    
    public final int cacheObjectChunkSize() {
        // dummy method
        return -1;
    }
    
    public long[] cacheObjectStatus() {
        // dummy method
        return null;
    }
    
    public final int cacheNodeChunkSize() {
        // returns the size that the node cache uses for a single entry
        return -1;
    }
    
    public final int[] cacheNodeStatus() {
        // a collection of different node cache status values
        return new int[]{0,0,0,0,0,0,0,0,0,0};
    }

    public String filename() {
        return this.filename;
    }

}
