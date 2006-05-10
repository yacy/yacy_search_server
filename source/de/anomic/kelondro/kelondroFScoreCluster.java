// kelondroScore.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.09.2004
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

/*
 * This class manages counted words,
 * in a word-count table.
 * word counts can be increased, and the words can be enumerated
 * in order of their count.
 */


package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public class kelondroFScoreCluster {
    
    private static final int wordlength = 32;
    private static final int countlength = 6;
    //private static final int nodesize = 4048;
    private kelondroTree refcountDB;
    private kelondroTree countrefDB;

    public kelondroFScoreCluster(File refcountDBfile, File countrefDBfile, kelondroOrder objectOrder, boolean exitOnFail) {
        if ((refcountDBfile.exists()) && (countrefDBfile.exists())) try {
            refcountDB = new kelondroTree(refcountDBfile, 0x100000L, kelondroTree.defaultObjectCachePercent);
            refcountDB.setText(0, kelondroBase64Order.enhancedCoder.encodeLong(0, countlength).getBytes()); // counter of all occurrences
            countrefDB = new kelondroTree(countrefDBfile, 0x100000L, kelondroTree.defaultObjectCachePercent);
            countrefDB.setText(0, kelondroBase64Order.enhancedCoder.encodeLong(0, countlength).getBytes());
        } catch (IOException e) {
            refcountDBfile.delete();
            countrefDBfile.delete();
            refcountDB = new kelondroTree(refcountDBfile, 0x100000L, kelondroTree.defaultObjectCachePercent, new int[] {wordlength, countlength}, objectOrder, 1, countlength, exitOnFail);
            countrefDB = new kelondroTree(countrefDBfile, 0x100000L, kelondroTree.defaultObjectCachePercent, new int[] {countlength + wordlength, 4}, objectOrder, 1, countlength, exitOnFail);
        } else if ((!(refcountDBfile.exists())) && (!(countrefDBfile.exists()))) {
            refcountDB = new kelondroTree(refcountDBfile, 0x100000L, kelondroTree.defaultObjectCachePercent, new int[] {wordlength, countlength}, objectOrder, 1, countlength, exitOnFail);
            countrefDB = new kelondroTree(countrefDBfile, 0x100000L, kelondroTree.defaultObjectCachePercent, new int[] {countlength + wordlength, 4}, objectOrder, 1, countlength, exitOnFail);
        } else {
            if (exitOnFail) {
                System.exit(-1);
            } else {
                throw new RuntimeException("both word/count db files must exists");
            }
        }
    }
    
    public void addScore(String word) throws IOException {
        word = word.toLowerCase();
        byte[][] record = refcountDB.get(word.getBytes());
        long c;
        String cs;
        if (record == null) {
            // new entry
            c = 0;
        } else {
            // delete old entry
            c = kelondroBase64Order.enhancedCoder.decodeLong(new String(record[1]));
            cs = kelondroBase64Order.enhancedCoder.encodeLong(c, countlength);
            countrefDB.remove((cs + word).getBytes());
            c++;
        }
        cs = kelondroBase64Order.enhancedCoder.encodeLong(c, countlength);
        refcountDB.put(word.getBytes(), cs.getBytes());
        countrefDB.put((cs + word).getBytes(), new byte[] {0,0,0,0});
        // increase overall counter
        refcountDB.setText(0, kelondroBase64Order.enhancedCoder.encodeLong(getTotalCount() + 1, countlength).getBytes());
    }
    
    public long getTotalCount() {
        return kelondroBase64Order.enhancedCoder.decodeLong(new String(refcountDB.getText(0)));
    }

    public int getElementCount() {
        return refcountDB.size();
    }
        
    public long getScore(String word) throws IOException {
        word = word.toLowerCase();
        byte[][] record = refcountDB.get(word.getBytes());
        if (record == null) {
            return 0;
        } else {
            return kelondroBase64Order.enhancedCoder.decodeLong(new String(record[1]));
        }
    }
    
    public Iterator scores(boolean up) throws IOException {
        // iterates <word>'-'<score> Strings
        return new scoreIterator(up, false);
    }
    
    private class scoreIterator implements Iterator {
        // iteration of score objects
        
        Iterator iterator;
        
        public scoreIterator(boolean up, boolean rotating) throws IOException {
            iterator = countrefDB.rows(up, rotating, null);
        }
       
        public boolean hasNext() {
            return iterator.hasNext();
        }
        
        public Object next() {
            String s = new String(((byte[][]) iterator.next())[0]);
            return s.substring(countlength) + "-" + kelondroBase64Order.enhancedCoder.decodeLong(s.substring(0, countlength));
        }
        
        public void remove() {
        }
        
    }
}
