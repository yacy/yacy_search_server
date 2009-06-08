// kelondroBLOBTree.java
// (C) 2004 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.02.2004 (as "kelondroDyn.java") on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

/*
  This class extends the kelondroTree and adds dynamic data handling
  A dynamic content is created, by using several tree nodes and
  combining them over a set of associated keys.
  Example: a byte[] of length 1000 shall be stored in a kelondroTree
  with node size 256. The key for the entry is 'entry'.
  Then kelondroDyn stores the first part of four into the entry
  'entry00', the second into 'entry01', and so on.

*/

package de.anomic.kelondro.blob;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

import de.anomic.kelondro.index.Row;
import de.anomic.kelondro.index.ObjectIndex;
import de.anomic.kelondro.io.AbstractRandomAccess;
import de.anomic.kelondro.io.RandomAccessInterface;
import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.order.RotateIterator;
import de.anomic.kelondro.table.EcoTable;
import de.anomic.kelondro.table.Tree;
import de.anomic.kelondro.util.FileUtils;
import de.anomic.kelondro.util.kelondroException;

public class BLOBTree {

    private static final int counterlen = 8;
    
    protected int keylen;
    private final int reclen;
    //private int segmentCount;
    private final char fillChar;
    private final ObjectIndex index;
    private ObjectBuffer buffer;
    private final Row rowdef;
    
    /**
     * Deprecated Class. Please use kelondroBLOBHeap instead
     */
    private BLOBTree(final File file, final boolean useNodeCache, final boolean useObjectCache, final int key,
            final int nodesize, final char fillChar, final ByteOrder objectOrder) {
        // creates or opens a dynamic tree
        rowdef = new Row("byte[] key-" + (key + counterlen) + ", byte[] node-" + nodesize, objectOrder);
        ObjectIndex fbi;
		try {
			fbi = new Tree(file, useNodeCache, 0, rowdef, 1, 8);
		} catch (final IOException e) {
			e.printStackTrace();
			FileUtils.deletedelete(file);
			throw new kelondroException(e.getMessage());
		}
        this.index = ((useObjectCache) && (!(fbi instanceof EcoTable))) ? (ObjectIndex) new Cache(fbi) : fbi;
        this.keylen = key;
        this.reclen = nodesize;
        this.fillChar = fillChar;
        //this.segmentCount = 0;
        //if (!(tree.fileExisted)) writeSegmentCount();
        buffer = new ObjectBuffer(file.toString());
    }
    
    public static BLOBHeap toHeap(final File file, final boolean useNodeCache, final boolean useObjectCache, final int key,
            final int nodesize, final char fillChar, final ByteOrder objectOrder, final File blob) throws IOException {
        if (blob.exists() || !file.exists()) {
            // open the blob file and ignore the tree
            return new BLOBHeap(blob, key, objectOrder, 1024 * 64);
        }
        // open a Tree and migrate everything to a Heap
        BLOBTree tree = new BLOBTree(file, useNodeCache, useObjectCache, key, nodesize, fillChar, objectOrder);
        BLOBHeap heap = new BLOBHeap(blob, key, objectOrder, 1024 * 64);
        Iterator<byte[]> i = tree.keys(true, false);
        byte[] k, kk = new byte[key], v;
        String s;
        while (i.hasNext()) {
            k = i.next();
            //assert k.length == key : "k.length = " + k.length + ", key = " + key;
            if (k == null) continue;
            v = tree.get(k);
            if (v == null) continue;
            s = new String(v, "UTF-8").trim();
            // enlarge entry key to fit into the given key length
            if (k.length == key) {
                heap.put(k, s.getBytes("UTF-8"));
            } else {
                System.arraycopy(k, 0, kk, 0, k.length);
                for (int j = k.length; j < key; j++) kk[j] = (byte) fillChar;
                heap.put(kk, s.getBytes("UTF-8"));
            }
        }
        tree.close(false);
        return heap;
    }
    
    private static String counter(final int c) {
        String s = Integer.toHexString(c);
        while (s.length() < counterlen) s = "0" + s;
        return s;
    }

    private byte[] elementKey(String key, final int record) {
        if (key.length() > keylen) throw new RuntimeException("key len (" + key.length() + ") out of limit (" + keylen + "): '" + key + "'");
        while (key.length() < keylen) key = key + fillChar;
        key = key + counter(record);
        try {
	    return key.getBytes("UTF-8");
	} catch (UnsupportedEncodingException e) {
	    return key.getBytes();
	}
    }

    private String origKey(final byte[] rawKey) {
        int n = keylen - 1;
        if (n >= rawKey.length) n = rawKey.length - 1;
        while ((n > 0) && (rawKey[n] == (byte) fillChar)) n--;
        try {
	    return new String(rawKey, 0, n + 1, "UTF-8");
	} catch (UnsupportedEncodingException e) {
	    return new String(rawKey, 0, n + 1);
	}
    }

    private class keyIterator implements CloneableIterator<byte[]> {
        // the iterator iterates all keys
        CloneableIterator<Row.Entry> ri;
        String nextKey;

        private keyIterator(final CloneableIterator<Row.Entry> iter) {
            ri = iter;
            nextKey = n();
        }

		public keyIterator clone(final Object modifier) {
			return new keyIterator(ri.clone(modifier));
		}

		public boolean hasNext() {
            return nextKey != null;
        }

		public byte[] next() {
            final String result = nextKey;
            nextKey = n();
            try {
		return origKey(result.getBytes("UTF-8")).getBytes("UTF-8");
	    } catch (UnsupportedEncodingException e) {
		return origKey(result.getBytes()).getBytes();
	    }
        }

		public void remove() {
            throw new UnsupportedOperationException("no remove in RawKeyIterator");
        }

        private String n() {
            byte[] g;
            String k;
            String v;
            int c;
            Row.Entry nt;
            while (ri.hasNext()) {
                nt = ri.next();
                if (nt == null) return null;
                g = nt.getColBytes(0);
                if (g == null) return null;
                try {
		    k = new String(g, 0, keylen, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
		    k = new String(g, 0, keylen);
		}
                try {
		    v = new String(g, keylen, counterlen, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
		    v = new String(g, keylen, counterlen);
		}
                try {
                    c = Integer.parseInt(v, 16);
                } catch (final NumberFormatException e) {
                    c = -1;
                }
                if (c == 0) return k;
            }
            return null;
        }

    }

    private synchronized CloneableIterator<byte[]> keys(final boolean up, final boolean rotating) throws IOException {
        // iterates only the keys of the Nodes
        // enumerated objects are of type String
        final keyIterator i = new keyIterator(index.rows(up, null));
        if (rotating) return new RotateIterator<byte[]>(i, null, index.size());
        return i;
    }
    
    private byte[] getValueCached(final byte[] key) throws IOException {

        // read from buffer
        final byte[] buffered = (byte[]) buffer.get(key);
        if (buffered != null) return buffered;
        
        // read from db
        final Row.Entry result = index.get(key);
        if (result == null) return null;

        // return result
        return result.getColBytes(1);
    }

    private synchronized void setValueCached(final byte[] key, final byte[] value) throws IOException {
        // update storage
        synchronized (this) {
            index.put(rowdef.newEntry(new byte[][]{key, value}));
            buffer.put(key, value);
        }
    }

    private synchronized int get(final String key, final int pos) throws IOException {
        final int reccnt = pos / reclen;
        // read within a single record
        final byte[] buf = getValueCached(elementKey(key, reccnt));
        if (buf == null) return -1;
        final int recpos = pos % reclen;
        if (buf.length <= recpos) return -1;
        return buf[recpos] & 0xFF;
    }
    
    private synchronized byte[] get(final byte[] key) throws IOException {
        final RandomAccessInterface ra = getRA(new String(key, "UTF-8"));
        if (ra == null) return null;
        return ra.readFully();
    }
    
    private synchronized byte[] get(final String key, final int pos, final int len) throws IOException {
        final int recpos = pos % reclen;
        final int reccnt = pos / reclen;
        byte[] segment1;
        // read first within a single record
        if ((recpos == 0) && (reclen == len)) {
            segment1 = getValueCached(elementKey(key, reccnt));
            if (segment1 == null) return null;
        } else {
            byte[] buf = getValueCached(elementKey(key, reccnt));
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
        final byte[] segment2 = get(key, pos + segment1.length, len - segment1.length);
        if (segment2 == null) return segment1;
        // now combine the two segments into the result
        final byte[] result = new byte[segment1.length + segment2.length];
        System.arraycopy(segment1, 0, result, 0, segment1.length);
        System.arraycopy(segment2, 0, result, segment1.length, segment2.length);
        return result;
    }
    
    private synchronized void put(final String key, final int pos, final byte[] b, final int off, final int len) throws IOException {
        final int recpos = pos % reclen;
        final int reccnt = pos / reclen;
        byte[] buf;
        // first write current record
        if ((recpos == 0) && (reclen == len)) {
            if (off == 0) {
                setValueCached(elementKey(key, reccnt), b);
            } else {
                buf = new byte[len];
                System.arraycopy(b, off, buf, 0, len);
                setValueCached(elementKey(key, reccnt), b);
            }
        } else {
            buf = getValueCached(elementKey(key, reccnt));
            if (buf == null) {
                buf = new byte[reclen];
            } else if (buf.length < reclen) {
                byte[] buff = new byte[reclen];
                System.arraycopy(buf, 0, buff, 0, buf.length);
                buf = buff;
            }
            // System.out.println("write:
            // b.length="+b.length+",off="+off+",len="+(reclen-recpos));
            if (len < (reclen - recpos))
                System.arraycopy(b, off, buf, recpos, len);
            else
                System.arraycopy(b, off, buf, recpos, reclen - recpos);
            setValueCached(elementKey(key, reccnt), buf);
        }
        // if more records are necessary, write to them also recursively
        if (recpos + len > reclen) {
            put(key, pos + reclen - recpos, b, off + reclen - recpos, len - reclen + recpos);
        }
    }

    private synchronized void put(final String key, final int pos, final int b) throws IOException {
        final int recpos = pos % reclen;
        final int reccnt = pos / reclen;
        byte[] buf;
        // first write current record
        buf = getValueCached(elementKey(key, reccnt));
        if (buf == null) {
            buf = new byte[reclen];
        } else if (buf.length < reclen) {
            byte[] buff = new byte[reclen];
            System.arraycopy(buf, 0, buff, 0, buf.length);
            buf = buff;
        }
        buf[recpos] = (byte) b;
        setValueCached(elementKey(key, reccnt), buf);
    }
    
    private synchronized RandomAccessInterface getRA(final String filekey) {
        // this returns always a RARecord, even if no existed bevore
        //return new kelondroBufferedRA(new RARecord(filekey), 512, 0);
        return new RARecord(filekey);
    }

    private class RARecord extends AbstractRandomAccess implements RandomAccessInterface {

        int seekpos = 0;
        int compLength = -1;
        
        String filekey;

        private RARecord(final String filekey) {
            this.filekey = filekey;
        }

        public long length() throws IOException {
            if (compLength >= 0) return compLength;
            int p = 0;
            while (get(filekey, p, reclen) != null) p+= reclen;
            compLength = p-1;
            return p-1;
        }
        
        public void setLength(long length) throws IOException {
            throw new UnsupportedOperationException();
        }
        
        public long available() throws IOException {
            return length() - seekpos;
        }
        
        public int read() throws IOException {
            return get(filekey, seekpos++);
        }

        public void write(final int i) throws IOException {
            put(filekey, seekpos++, i);
        }

        public void readFully(final byte[] b, final int off, final int len) throws IOException {
            int l = Math.min(b.length - off, len);
            final byte[] buf = get(filekey, seekpos, l);
            if (buf == null) throw new IOException("record at off " + off + ", len " + len + " not found");
            l = Math.min(buf.length, l);
            System.arraycopy(buf, 0, b, off, l);
            seekpos += l;
        }

        public void write(final byte[] b, final int off, final int len) throws IOException {
            put(filekey, seekpos, b, off, len);
            seekpos += len;
        }

        public void seek(final long pos) throws IOException {
            seekpos = (int) pos;
        }

        public void close() throws IOException {
            // no need to do anything here
        }

    }
    
    private synchronized void close(boolean writeIDX) {
        index.close();
    }
}