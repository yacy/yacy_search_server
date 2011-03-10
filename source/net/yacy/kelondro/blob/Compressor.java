// Compressor.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 17.10.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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


package net.yacy.kelondro.blob;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.util.ByteArray;


public class Compressor implements BLOB {

    static byte[] gzipMagic  = {(byte) 'z', (byte) '|'}; // magic for gzip-encoded content
    static byte[] plainMagic = {(byte) 'p', (byte) '|'}; // magic for plain content (no encoding)
    
    private final BLOB backend;
    private Map<String, byte[]> buffer; // entries which are not yet compressed, format is RAW (without magic)
    private BlockingQueue<Entity> writeQueue;
    private long bufferlength;
    private final long maxbufferlength;
    private final Worker[] worker;
    
    public Compressor(BLOB backend, long buffersize) {
        this.backend = backend;
        this.maxbufferlength = buffersize;
        this.writeQueue = new LinkedBlockingQueue<Entity>();
        this.worker = new Worker[Math.min(4, Runtime.getRuntime().availableProcessors())];
        for (int i = 0; i < this.worker.length; i++) {
            this.worker[i] = new Worker();
            this.worker[i].start();
        }
        initBuffer();
    }
    
    public long mem() {
        return backend.mem();
    }
    
    public void trim() {
        this.backend.trim();
    }
    
    private static class Entity implements Map.Entry<String, byte[]> {
        private String key;
        private byte[] payload;
        public Entity(String key, byte[] payload) {
            this.key = key;
            this.payload = payload;
        }
        public String getKey() {
            return this.key;
        }

        public byte[] getValue() {
            return this.payload;
        }

        public byte[] setValue(byte[] payload) {
            byte[] payload0 = payload;
            this.payload = payload;
            return payload0;
        }
    }
    
    private final static Entity poisonWorkerEntry = new Entity("poison", null);
    
    private class Worker extends Thread {
        public Worker() {
        }
        @Override
        public void run() {
            Entity entry;
            try {
                while ((entry = writeQueue.take()) != poisonWorkerEntry) {
                    try {
                        Compressor.this.backend.insert(UTF8.getBytes(entry.getKey()), compress(entry.getValue()));
                    } catch (IOException e) {
                        Log.logException(e);
                        buffer.put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (InterruptedException e) {
                Log.logException(e);
            }
        }
    }
    
    public String name() {
        return this.backend.name();
    }
    
    public synchronized void clear() throws IOException {
        initBuffer();
        this.writeQueue.clear();
        this.backend.clear();
    }
    
    private void initBuffer() {
        this.buffer = new ConcurrentHashMap<String, byte[]>();
        this.bufferlength = 0;
    }

    public ByteOrder ordering() {
        return this.backend.ordering();
    }
    
    public synchronized void close(boolean writeIDX) {
        // no more thread is running, flush all queues
        flushAll();
        for (int i = 0; i < this.worker.length; i++) try {
            this.writeQueue.put(poisonWorkerEntry);
        } catch (InterruptedException e) {
            Log.logException(e);
        }
        for (int i = 0; i < this.worker.length; i++) try {
            this.worker[i].join();
        } catch (InterruptedException e) {
            Log.logException(e);
        }
        this.backend.close(writeIDX);
    }
    
    private byte[] compress(byte[] b) {
        int l = b.length;
        if (l < 100) return markWithPlainMagic(b);
        byte[] bb = compressAddMagic(b);
        if (bb.length >= l) return markWithPlainMagic(b);
        return bb;
    }
    
    private byte[] compressAddMagic(byte[] b) {
        // compress a byte array and add a leading magic for the compression
        try {
            //System.out.print("/(" + cdr + ")"); // DEBUG
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(b.length / 5);
            baos.write(gzipMagic);
            final OutputStream os = new GZIPOutputStream(baos, 512);
            os.write(b);
            os.close();
            baos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            Log.logSevere("Compressor", "", e);
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
        if (b == null) return null;
        if (ByteArray.startsWith(b, gzipMagic)) {
            //System.out.print("\\"); // DEBUG
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
                Log.logException(e);
                return null;
            }
        } else if (ByteArray.startsWith(b, plainMagic)) {
            //System.out.print("-"); // DEBUG
            byte[] r = new byte[b.length - 2];
            System.arraycopy(b, 2, r, 0, b.length - 2);
            return r;
        } else {
            // we consider that the entry is also plain, but without leading magic
            return b;
        }
    }

    public byte[] get(byte[] key) throws IOException, RowSpaceExceededException {
        // depending on the source of the result, we additionally do entry compression
        // because if a document was read once, we think that it will not be retrieved another time again soon
        String keys = UTF8.String(key);
        byte[] b = null;
        synchronized (this) {
            b = buffer.remove(keys);
            if (b != null) {
                // compress the entry now and put it to the backend
                try {
                    this.writeQueue.put(new Entity(keys, b));
                    this.bufferlength = this.bufferlength - b.length;
                    return b;
                } catch (InterruptedException e) {
                    Log.logException(e);
                    buffer.put(keys, b);
                }
            }
            
            // return from the backend
            b = this.backend.get(key);
        }
        if (b == null) return null;
        return decompress(b);
    }

    public byte[] get(Object key) {
        if (!(key instanceof byte[])) return null;
        try {
            return get((byte[]) key);
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        return null;
    }
    
    public synchronized boolean containsKey(byte[] key) {
        return this.buffer.containsKey(UTF8.String(key)) || this.backend.containsKey(key);
    }

    public int keylength() {
        return this.backend.keylength();
    }

    public synchronized long length() {
        try {
            return this.backend.length() + this.bufferlength;
        } catch (IOException e) {
            Log.logException(e);
            return 0;
        }
    }
    
    public synchronized long length(byte[] key) throws IOException {
        byte[] b = buffer.get(UTF8.String(key));
        if (b != null) return b.length;
        try {
            b = this.backend.get(key);
            if (b == null) return 0;
            b = decompress(b);
            return (b == null) ? 0 : b.length;
        } catch (RowSpaceExceededException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    private int removeFromQueues(byte[] key) {
        byte[] b = buffer.remove(UTF8.String(key));
        if (b != null) return b.length;
        return 0;
    }
    
    public void insert(byte[] key, byte[] b) throws IOException {
        
        // first ensure that the files do not exist anywhere
        delete(key);
        
        // check if the buffer is full or could be full after this write
        if (this.bufferlength + b.length * 2 > this.maxbufferlength) synchronized (this) {
            // in case that we compress, just compress as much as is necessary to get enough room
            while (this.bufferlength + b.length * 2 > this.maxbufferlength) {
                if (this.buffer.isEmpty()) break;
                flushOne();
            }
            // in case that this was not enough, just flush all
            if (this.bufferlength + b.length * 2 > this.maxbufferlength) flushAll();
        }

        // files are written uncompressed to the uncompressed-queue
        // they are either written uncompressed to the database
        // or compressed later
        synchronized (this) {
            this.buffer.put(UTF8.String(key), b);
            this.bufferlength += b.length;
        }
    }

    public synchronized void delete(byte[] key) throws IOException {
        this.backend.delete(key);
        long rx = removeFromQueues(key);
        if (rx > 0) this.bufferlength -= rx;
    }

    public synchronized int size() {
        return this.backend.size() + this.buffer.size();
    }
    
    public synchronized boolean isEmpty() {
        if (!this.backend.isEmpty()) return false;
        if (!this.buffer.isEmpty()) return false;
        return true;
    }
    
    public synchronized CloneableIterator<byte[]> keys(boolean up, boolean rotating) throws IOException {
        flushAll();
        return this.backend.keys(up, rotating);
    }

    public synchronized CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
        flushAll();
        return this.backend.keys(up, firstKey);
    }
    
    private boolean flushOne() {
        if (this.buffer.isEmpty()) return false;
        // depending on process case, write it to the file or compress it to the other queue
        Map.Entry<String, byte[]> entry = this.buffer.entrySet().iterator().next();
        this.buffer.remove(entry.getKey());
        try {
            this.writeQueue.put(new Entity(entry.getKey(), entry.getValue()));
            this.bufferlength -= entry.getValue().length;
            return true;
        } catch (InterruptedException e) {
            this.buffer.put(entry.getKey(), entry.getValue());
            return false;
        }
    }

    private void flushAll() {
        while (!this.buffer.isEmpty()) {
            if (!flushOne()) break;
        }
    }

    public int replace(byte[] key, Rewriter rewriter) throws IOException, RowSpaceExceededException {
        byte[] b = get(key);
        if (b == null) return 0;
        byte[] c = rewriter.rewrite(b);
        int reduction = c.length - b.length;
        assert reduction >= 0;
        if (reduction == 0) return 0;
        this.insert(key, c);
        return reduction;
    }
    
    public int reduce(byte[] key, Reducer reducer) throws IOException, RowSpaceExceededException {
        byte[] b = get(key);
        if (b == null) return 0;
        byte[] c = reducer.rewrite(b);
        int reduction = c.length - b.length;
        assert reduction >= 0;
        if (reduction == 0) return 0;
        this.insert(key, c);
        return reduction;
    }

}
