// kelondroDyn.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 09.02.2004
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
  This class extends the kelondroTree and adds dynamic data handling
  A dynamic content is created, by using several tree nodes and
  combining them over a set of associated keys.
  Example: a byte[] of length 1000 shall be stored in a kelondroTree
  with node size 256. The key for the entry is 'entry'.
  Then kelondroDyn stores the first part of four into the entry
  'entry00', the second into 'entry01', and so on.

*/

package de.anomic.kelondro;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

public class kelondroDyn extends kelondroTree {

    private static final int counterlen = 8;

    protected int keylen;
    private int reclen;
    private int segmentCount;
    private char fillChar;
    private kelondroObjectBuffer buffer;
    
    public kelondroDyn(File file, long buffersize /*bytes*/, long preloadTime, int key, int nodesize, char fillChar, boolean exitOnFail) {
        this(file, buffersize, preloadTime, key, nodesize, fillChar, new kelondroNaturalOrder(true), exitOnFail);
    }
    
    public kelondroDyn(File file, long buffersize /* bytes */, long preloadTime, int key,
            int nodesize, char fillChar, kelondroOrder objectOrder,
            boolean exitOnFail) {
        // creates a new dynamic tree
        super(file, buffersize, preloadTime,  kelondroTree.defaultObjectCachePercent, new kelondroRow("byte[] key-" + (key + counterlen) + ", byte[] node-" + nodesize), objectOrder, 1, 8, exitOnFail);
        this.keylen = row().width(0) - counterlen;
        this.reclen = row().width(1);
        this.fillChar = fillChar;
        this.segmentCount = 0;
        writeSegmentCount();
        buffer = new kelondroObjectBuffer(file.toString());
    }

    public kelondroDyn(File file, long buffersize, long preloadTime, char fillChar) throws IOException {
        // this opens a file with an existing dynamic tree
        super(file, buffersize, preloadTime, kelondroTree.defaultObjectCachePercent);
        this.keylen = row().width(0) - counterlen;
        this.reclen = row().width(1);
        this.fillChar = fillChar;
        this.segmentCount = 0;
        buffer = new kelondroObjectBuffer(file.toString());
    }
    
    private void writeSegmentCount() {
        try {
            setText(0, kelondroBase64Order.enhancedCoder.encodeLong(segmentCount, 8).getBytes());
        } catch (Exception e) {
            
        }
    }
    
    public synchronized int sizeDyn() {
        //this.segmentCount = 0;
        //Iterator i = keys(true); while (i.hasNext()) segmentCount++;
        //return segmentCount;
        return super.size();
    }
    
    private static String counter(int c) {
        String s = Integer.toHexString(c);
        while (s.length() < counterlen) s = "0" + s;
        return s;
    }

    private byte[] dynKey(String key, int record) {
        if (key.length() > keylen) throw new RuntimeException("key len (" + key.length() + ") out of limit (" + keylen + "): '" + key + "'");
        while (key.length() < keylen) key = key + fillChar;
        key = key + counter(record);
        return key.getBytes();
    }

    protected String origKey(byte[] rawKey) {
        int n = keylen - 1;
        if (n >= rawKey.length) n = rawKey.length - 1;
        while ((n > 0) && (rawKey[n] == (byte) fillChar)) n--;
        return new String(rawKey, 0, n + 1);
    }

    public class dynKeyIterator implements Iterator {
        // the iterator iterates all keys, which are byte[] objects
        Iterator ri;
        String nextKey;

        public dynKeyIterator(Iterator iter) {
            ri = iter;
            nextKey = n();
        }

        public boolean hasNext() {
            return nextKey != null;
        }

        public Object next() {
            String result = nextKey;
            nextKey = n();
            return origKey(result.getBytes());
        }

        public void remove() {
            throw new UnsupportedOperationException("no remove in RawKeyIterator");
        }

        private String n() {
            byte[] g;
            String k;
            String v;
            int c;
            kelondroRow.Entry nt;
            while (ri.hasNext()) {
                nt = (kelondroRow.Entry) ri.next();
                if (nt == null) throw new kelondroException(filename, "no more elements available");
                g = nt.getColBytes(0);
                if (g == null) return null;
                k = new String(g, 0, keylen);
                v = new String(g, keylen, counterlen);
                try {
                    c = Integer.parseInt(v, 16);
                } catch (NumberFormatException e) {
                    c = -1;
                }
                if (c == 0) return k;
            }
            return null;
        }
    }

    public synchronized dynKeyIterator dynKeys(boolean up, boolean rotating) throws IOException {
        // iterates only the keys of the Nodes
        // enumerated objects are of type String
        return new dynKeyIterator(super.rows(up, rotating, null));
    }

    public synchronized dynKeyIterator dynKeys(boolean up, boolean rotating, byte[] firstKey) throws IOException {
        return new dynKeyIterator(super.rows(up, rotating, firstKey));
    }
    
    private byte[] getValueCached(byte[] key) throws IOException {

        // read from buffer
        byte[] buffered = (byte[]) buffer.get(key);
        if (buffered != null) return buffered;
        
        // read from db
        kelondroRow.Entry result = get(key);
        if (result == null) return null;

        // return result
        return result.getColBytes(1);
    }

    private synchronized void setValueCached(byte[] key, byte[] value) throws IOException {
        // update storage
        synchronized (this) {
            put(key, value);
            buffer.put(key, value);
        }
    }

    public synchronized int getDyn(String key, int pos) throws IOException {
        int reccnt = pos / reclen;
        // read within a single record
        byte[] buf = getValueCached(dynKey(key, reccnt));
        if (buf == null) return -1;
        int recpos = pos % reclen;
        if (buf.length <= recpos) return -1;
        return buf[recpos] & 0xFF;
    }
    
    public synchronized byte[] getDyn(String key, int pos, int len) throws IOException {
        int recpos = pos % reclen;
        int reccnt = pos / reclen;
        byte[] segment1;
        // read first within a single record
        if ((recpos == 0) && (reclen == len)) {
            segment1 = getValueCached(dynKey(key, reccnt));
            if (segment1 == null) return null;
        } else {
            byte[] buf = getValueCached(dynKey(key, reccnt));
            if (buf == null) return null;
            if (buf.length < reclen) {
                byte[] buff = new byte[reclen];
                System.arraycopy(buf, 0, buff, 0, buf.length);
                buf = buff;
                buff = null;
            }
            // System.out.println("read:
            // buf.length="+buf.length+",recpos="+recpos+",len="+len);
            if (recpos + len <= reclen) {
                segment1 = new byte[len];
                System.arraycopy(buf, recpos, segment1, 0, len);
            } else {
                segment1 = new byte[reclen - recpos];
                System.arraycopy(buf, recpos, segment1, 0, reclen - recpos);
            }
        }
        // if this is all, return
        if (recpos + len <= reclen) return segment1;
        // read from several records
        // we combine recursively all participating records
        // we have two segments: the one in the starting record, and the remaining
        // segment 1 in record <reccnt> : start = recpos, length = reclen - recpos
        // segment 2 in record <reccnt>+1: start = 0, length = len - reclen + recpos
        // recursively step further
        byte[] segment2 = getDyn(key, pos + segment1.length, len - segment1.length);
        if (segment2 == null) return segment1;
        // now combine the two segments into the result
        byte[] result = new byte[segment1.length + segment2.length];
        System.arraycopy(segment1, 0, result, 0, segment1.length);
        System.arraycopy(segment2, 0, result, segment1.length, segment2.length);
        return result;
    }

    public synchronized void putDyn(String key, int pos, byte[] b, int off, int len) throws IOException {
        int recpos = pos % reclen;
        int reccnt = pos / reclen;
        byte[] buf;
        // first write current record
        if ((recpos == 0) && (reclen == len)) {
            if (off == 0) {
                setValueCached(dynKey(key, reccnt), b);
            } else {
                buf = new byte[len];
                System.arraycopy(b, off, buf, 0, len);
                setValueCached(dynKey(key, reccnt), b);
            }
        } else {
            buf = getValueCached(dynKey(key, reccnt));
            if (buf == null) {
                buf = new byte[reclen];
            } else if (buf.length < reclen) {
                byte[] buff = new byte[reclen];
                System.arraycopy(buf, 0, buff, 0, buf.length);
                buf = buff;
                buff = null;
            }
            // System.out.println("write:
            // b.length="+b.length+",off="+off+",len="+(reclen-recpos));
            if (len < (reclen - recpos))
                System.arraycopy(b, off, buf, recpos, len);
            else
                System.arraycopy(b, off, buf, recpos, reclen - recpos);
            setValueCached(dynKey(key, reccnt), buf);
        }
        // if more records are necessary, write to them also recursively
        if (recpos + len > reclen) {
            putDyn(key, pos + reclen - recpos, b, off + reclen - recpos, len - reclen + recpos);
        }
    }

    public synchronized void remove(String key) throws IOException {
        // remove value in cache and tree
        if (key == null) return;
        int recpos = 0;
        byte[] k;
        while (super.get(k = dynKey(key, recpos)) != null) {
            super.remove(k);
            buffer.remove(k);
            recpos++;
        }
        //segmentCount--; writeSegmentCount();
    }

    public synchronized boolean existsDyn(String key) throws IOException {
        return (key != null) && (getValueCached(dynKey(key, 0)) != null);
    }

    public synchronized kelondroRA getRA(String filekey) {
        // this returns always a RARecord, even if no existed bevore
        //return new kelondroBufferedRA(new RARecord(filekey), 512, 0);
        return new RARecord(filekey);
    }

    public class RARecord extends kelondroAbstractRA implements kelondroRA {

        int seekpos = 0;

        String filekey;

        public RARecord(String filekey) {
            this.filekey = filekey;
        }

        public long length() throws IOException {
            return Long.MAX_VALUE;
        }
        
        public long available() throws IOException {
            return Long.MAX_VALUE;
        }
        
        public int read() throws IOException {
            return getDyn(filekey, seekpos++);
        }

        public void write(int i) throws IOException {
            byte[] b = new byte[1];
            b[0] = (byte) i;
            putDyn(filekey, seekpos++, b, 0, 1);
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int l = Math.min(b.length - off, len);
            byte[] buf = getDyn(filekey, seekpos, l);
            if (buf == null) return -1;
            l = Math.min(buf.length, l);
            System.arraycopy(buf, 0, b, off, l);
            seekpos += l;
            return l;
        }

        public void write(byte[] b, int off, int len) throws IOException {
            putDyn(filekey, seekpos, b, off, len);
            seekpos += len;
        }

        public void seek(long pos) throws IOException {
            seekpos = (int) pos;
        }

        public void close() throws IOException {
            // no need to do anything here
        }

    }

    public synchronized void writeFile(String key, File f) throws IOException {
        // reads a file from the FS and writes it into the database
        kelondroRA kra = null;
        FileInputStream fis = null;
        try {
            kra = getRA(key);
            byte[] buffer = new byte[1024];
            byte[] result = new byte[(int) f.length()];
            fis = new FileInputStream(f);
            int i;
            int pos = 0;
            while ((i = fis.read(buffer)) > 0) {
                System.arraycopy(buffer, 0, result, pos, i);
                pos += i;
            }
            fis.close();
            kra.writeArray(result);
        } finally {
            if (fis != null)
                try {
                    fis.close();
                } catch (Exception e) {
                }
            if (kra != null)
                try {
                    kra.close();
                } catch (Exception e) {
                }
        }
    }
    
    public synchronized void readFile(String key, File f) throws IOException {
        // reads a file from the DB and writes it to the FS
        kelondroRA kra = null;
        FileOutputStream fos = null;
        try {
            kra = getRA(key);
            byte[] result = kra.readArray();
            fos = new FileOutputStream(f);
            fos.write(result);
        } finally {
            if (fos != null)
                try {
                    fos.close();
                } catch (Exception e) {
                }
            if (kra != null)
                try {
                    kra.close();
                } catch (Exception e) {
                }
        }
    }

    public static void main(String[] args) {
        // test app for DB functions
        // reads/writes files to a database table
        // arguments:
        // {-f2db/-db2f} <db-name> <key> <filename>

        if (args.length == 0) {
            randomtest(20);
        } else if (args.length == 1) {
            // open a db and list keys
            try {
                kelondroDyn kd = new kelondroDyn(new File(args[0]), 0x100000, 0, '_');
                System.out.println(kd.size() + " elements in DB");
                Iterator i = kd.dynKeys(true, false);
                while (i.hasNext())
                    System.out.println((String) i.next());
                kd.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (args.length == 4) {
            boolean writeFile = (args[0].equals("-db2f"));
            File db = new File(args[1]);
            String key = args[2];
            File f = new File(args[3]);
            kelondroDyn kd;
            try {
                if (db.exists())
                    kd = new kelondroDyn(db, 0x100000, 0, '_');
                else
                    kd = new kelondroDyn(db, 0x100000, 0, 80, 200, '_', true);
                if (writeFile)
                    kd.readFile(key, f);
                else
                    kd.writeFile(key, f);
            } catch (IOException e) {
                System.out.println("ERROR: " + e.toString());
            }
        }
    }
    
    public static void randomtest(int elements) {
        System.out.println("random " + elements + ":");
        String s = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".substring(0, elements);
        String t, d;
        char c;
        kelondroDyn tt;
        File testFile = new File("test.db");
        byte[] b;
        byte[] cont;
        try {
            int steps = 0;
            while (true) {
                if (testFile.exists()) testFile.delete();
                tt = new kelondroDyn(testFile, 0, 0, 4 ,100, '_', true);
                steps = ((int) System.currentTimeMillis() % 7) * (((int) System.currentTimeMillis() + 17) % 11);
                t = s;
                d = "";
                System.out.println("NEW SESSION");
                for (int i = 0; i < steps; i++) {
                    if ((d.length() < 3) || ((t.length() > 0) && (((int) System.currentTimeMillis() % 7) < 3))) {
                        // add one
                        c = t.charAt((int) (System.currentTimeMillis() % t.length()));
                        b = testWord(c);
                        cont = new byte[(int) (System.currentTimeMillis() % 777L)];
                        tt.putDyn(new String(b), 0, cont, 0, cont.length);
                        d = d + c;
                        t = t.substring(0, t.indexOf(c)) + t.substring(t.indexOf(c) + 1);
                        System.out.println("added " + new String(b) + ", " + cont.length + " bytes");
                    } else {
                        // delete one
                        c = d.charAt((int) (System.currentTimeMillis() % d.length()));
                        b = testWord(c);
                        tt.remove(new String(b));
                        d = d.substring(0, d.indexOf(c)) + d.substring(d.indexOf(c) + 1);
                        t = t + c;
                        System.out.println("removed " + new String(b));
                    }
                    if (countElementsDyn(tt) != tt.sizeDyn()) {
                        System.out.println("wrong size: count=" + countElementsDyn(tt) + ", size=" + tt.sizeDyn() + "; Tree:");
                        //tt.print();
                        //break;
                    }
                }
                //tt.print();
                tt.close();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("TERMINATED");
        }
    }
    
    public static int countElementsDyn(kelondroDyn t) {
        int count = 0;
        try {
            Iterator iter = t.dynKeys(true, false);
            while (iter.hasNext()) {count++; if (iter.next() == null) System.out.println("ERROR! null element found");}
            return count;
        } catch (IOException e) {
            return -1;
        }
    }
}
