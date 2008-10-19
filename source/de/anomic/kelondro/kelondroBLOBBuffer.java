// kelondroBLOBBuffer.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 17.10.2008 on http://yacy.net
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


package de.anomic.kelondro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class kelondroBLOBBuffer extends Thread implements kelondroBLOB {

    static byte[] gzipMagic  = {(byte) 'z', (byte) '|'}; // magic for gzip-encoded content
    static byte[] plainMagic = {(byte) 'p', (byte) '|'}; // magic for plain content (no encoding)
    
    private kelondroBLOB backend;
    private LinkedBlockingQueue<Map.Entry<byte[], byte[]>> rawQueue;        // entries which are not compressed, format is RAW (without magic)
    private LinkedBlockingQueue<Map.Entry<byte[], byte[]>> compressedQueue; // entries which are compressed, format is with leading magic
    private kelondroBytesIntMap contentLength;
    private long queueLength;
    private long maxCacheSize;
    private int cdr;
    
    private class Entry implements Map.Entry<byte[], byte[]> {

        byte[] key, value;
        
        public Entry(byte[] key, byte[] value) {
            this.key = key;
            this.value = value;
        }
        
        public byte[] getKey() {
            return this.key;
        }

        public byte[] getValue() {
            return this.value;
        }

        public byte[] setValue(byte[] value) {
            byte[] b = this.value;
            this.value = value;
            return b;
        }
        
    }
    
    public kelondroBLOBBuffer(kelondroBLOB backend, long cachesize, boolean compress) {
        this.backend = backend;
        this.maxCacheSize = cachesize;
        cdr = 0;
        initQueues(compress);
    }
    
    public synchronized void clear() throws IOException {
        initQueues(this.compressedQueue != null);
        this.backend.clear();
    }
    
    private void initQueues(boolean compress) {
        this.rawQueue = new LinkedBlockingQueue<Map.Entry<byte[], byte[]>>();
        this.compressedQueue = (compress) ? new LinkedBlockingQueue<Map.Entry<byte[], byte[]>>() : null;
        this.contentLength = new kelondroBytesIntMap(backend.keylength(), backend.ordering(), 500);
        this.queueLength = 0;
    }

    public kelondroByteOrder ordering() {
        return this.backend.ordering();
    }
    
    public synchronized void close() {
        // no more thread is running, flush all queues
        try {
            flushAll();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.backend.close();
    }
    
    private byte[] compress(byte[] b) {
        // compressed a byte array and adds a leading magic for the compression
        try {
            cdr++;
            //System.out.print("/(" + cdr + ")"); // DEBUG
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(b.length / 5);
            baos.write(gzipMagic);
            final OutputStream os = new GZIPOutputStream(baos, 512);
            os.write(b);
            os.close();
            baos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private byte[] markWithPlainMagic(byte[] b) {
        //System.out.print("+"); // DEBUG
        byte[] r = new byte[b.length + 2];
        r[0] = plainMagic[0];
        r[1] = plainMagic[1];
        System.arraycopy(b, 0, r, 2, b.length);
        return r;
    }

    private byte[] decompress(byte[] b) {
        // use a magic in the head of the bytes to identify compression type
        if (kelondroByteArray.equals(b, gzipMagic)) {
            //System.out.print("\\"); // DEBUG
            cdr--;
            ByteArrayInputStream bais = new ByteArrayInputStream(b);
            // eat up the magic
            bais.read();
            bais.read();
            // decompress what is remaining
            InputStream gis;
            try {
                gis = new GZIPInputStream(bais);
                final ByteArrayOutputStream baos = new ByteArrayOutputStream(b.length);
                final byte[] buf = new byte[1024];
                int n;
                while ((n = gis.read(buf)) > 0) baos.write(buf, 0, n);
                gis.close();
                bais.close();
                baos.close();
                
                return baos.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        } else if (kelondroByteArray.equals(b, plainMagic)) {
            System.out.print("-"); // DEBUG
            byte[] r = new byte[b.length - 2];
            System.arraycopy(b, 2, r, 0, b.length - 2);
            return r;
        } else {
            // we consider that the entry is also plain, but without leading magic
            return b;
        }
    }
    
    private byte[] getFromQueue(byte[] key, LinkedBlockingQueue<Map.Entry<byte[], byte[]>> queue) {
        Iterator<Map.Entry<byte[], byte[]>> i = queue.iterator();
        Map.Entry<byte[], byte[]> e;
        while (i.hasNext()) {
            e = i.next();
            if (kelondroByteArray.equals(key, e.getKey())) return e.getValue();
        }
        return null;
    }

    public synchronized byte[] get(byte[] key) throws IOException {
        // depending on the source of the result, we additionally do entry compression
        // because if a document was read once, we think that it will not be retrieved another time again soon
        byte[] b;
        if (this.compressedQueue == null) {
            b = getFromQueue(key, rawQueue);
            if (b != null) return b;
        } else {
            b = removeFromQueue(key, rawQueue);
            if (b != null) {
                // put the entry on the compressed queue
                byte[] bb = compress(b);
                this.compressedQueue.add(new Entry(key, bb));
                this.queueLength = this.queueLength - b.length + bb.length;
                return b;
            }
        }
        // no special handling for elements from the compressed queue
        b = (compressedQueue == null) ? null : getFromQueue(key, compressedQueue);
        if (b != null) {
            //System.out.print("CASEA"); // DEBUG
            return decompress(b);
        }
        
        // finally return from the backend
        b = this.backend.get(key);
        if (b == null) return null;
        return decompress(b);
    }

    private boolean hasInQueue(byte[] key, LinkedBlockingQueue<Map.Entry<byte[], byte[]>> queue) {
        Iterator<Map.Entry<byte[], byte[]>> i = queue.iterator();
        Map.Entry<byte[], byte[]> e;
        while (i.hasNext()) {
            e = i.next();
            if (kelondroByteArray.equals(key, e.getKey())) return true;
        }
        return false;
    }
    
    public synchronized boolean has(byte[] key) throws IOException {
        return 
          (rawQueue != null && hasInQueue(key, rawQueue)) ||
          (compressedQueue != null && hasInQueue(key, compressedQueue)) ||
          this.backend.has(key);
    }

    public int keylength() {
        return this.backend.keylength();
    }

    public synchronized long length() {
        try {
            return this.backend.length() + this.queueLength;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }
    
    public synchronized long length(byte[] key) throws IOException {
        int i = this.contentLength.geti(key);
        if (i >= 0) {
            //System.out.print("CASEC"); // DEBUG
            return (long) i;
        }
        byte[] b = getFromQueue(key, rawQueue);
        if (b != null) return b.length;
        b = (compressedQueue == null) ? null : getFromQueue(key, compressedQueue);
        if (b != null) {
            //System.out.print("CASEB"); // DEBUG
            return decompress(b).length;
        }
        return this.backend.length(key);
    }

    private byte[] removeFromQueue(byte[] key, LinkedBlockingQueue<Map.Entry<byte[], byte[]>> queue) {
        Iterator<Map.Entry<byte[], byte[]>> i = queue.iterator();
        Map.Entry<byte[], byte[]> e;
        while (i.hasNext()) {
            e = i.next();
            if (kelondroByteArray.equals(key, e.getKey())) {
                i.remove();
                return e.getValue();
            }
        }
        return null;
    }
    
    private int removeFromQueues(byte[] key) throws IOException {
        byte[] b = removeFromQueue(key, rawQueue);
        if (b != null) return b.length;
        b = (compressedQueue == null) ? null : removeFromQueue(key, compressedQueue);
        if (b != null) return b.length;
        return 0;
    }
    
    public synchronized void put(byte[] key, byte[] b) throws IOException {
        
        // first ensure that the files do not exist anywhere
        this.backend.remove(key);
        long rx = removeFromQueues(key);
        if (rx > 0) this.queueLength -= rx;
        
        // check if the buffer is full or could be full after this write
        if (this.queueLength + b.length * 2 > this.maxCacheSize) {
            // in case that we compress, just compress as much as is necessary to get enough room
            if (this.compressedQueue == null) {
                flushAll();
            } else {
                while (this.queueLength + b.length * 2 > this.maxCacheSize && this.rawQueue.size() > 0) {
                    flushOneRaw();
                }
                // in case that this was not enough, just flush all
                if (this.queueLength + b.length * 2 > this.maxCacheSize) flushAll();
            }
        }

        // files are written uncompressed to the uncompressed-queue
        // they are either written uncompressed to the database
        // or compressed later
        this.rawQueue.add(new Entry(key, b));
        this.queueLength += b.length;
        this.contentLength.puti(key, b.length);
        if (this.contentLength.size() > 500) this.contentLength.clear(); // prevent the case that this object becomes a memory leak
    }

    public synchronized void remove(byte[] key) throws IOException {
        this.backend.remove(key);
        long rx = removeFromQueues(key);
        if (rx > 0) this.queueLength -= rx;
    }

    public int size() {
        return this.backend.size();
    }
    
    public synchronized kelondroCloneableIterator<byte[]> keys(boolean up, boolean rotating) throws IOException {
        flushAll();
        return this.backend.keys(up, rotating);
    }

    public synchronized kelondroCloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
        flushAll();
        return this.backend.keys(up, firstKey);
    }
    
    private boolean flushOneRaw() throws IOException {
        if (this.rawQueue.size() == 0) return false;
        // depending on process case, write it to the file or compress it to the other queue
        try {
            Map.Entry<byte[], byte[]> entry = this.rawQueue.take();
            this.queueLength -= entry.getValue().length;
            if (this.compressedQueue != null) {
                entry.setValue(compress(entry.getValue()));
                this.queueLength += entry.getValue().length;
                this.compressedQueue.add(entry);
            } else {
                this.backend.put(entry.getKey(), markWithPlainMagic(entry.getValue()));
                assert this.queueLength == 0;
                if (this.rawQueue.size() == 0) this.queueLength = 0;
            }
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private boolean flushOneCompressed() throws IOException {
        if (this.compressedQueue == null || this.compressedQueue.size() == 0) return false;
        // write compressed entry to the file
        try {
            //System.out.print("#"); // DEBUG
            Map.Entry<byte[], byte[]> entry = this.compressedQueue.take();
            this.queueLength -= entry.getValue().length;
            this.backend.put(entry.getKey(), entry.getValue());
            if (this.rawQueue.size() == 0 && this.compressedQueue.size() == 0) this.queueLength = 0;
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private void flushAll() throws IOException {
        while (this.rawQueue.size() > 0) {
            if (!flushOneRaw()) break;
        }
        while (this.compressedQueue != null && this.compressedQueue.size() > 0) {
            if (!flushOneCompressed()) break;
        }
        assert this.queueLength == 0;
    }

}
