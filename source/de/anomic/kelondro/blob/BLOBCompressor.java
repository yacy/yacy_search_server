// kelondroBLOBCompressor.java
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


package de.anomic.kelondro.blob;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import de.anomic.kelondro.order.ByteOrder;
import de.anomic.kelondro.order.CloneableIterator;
import de.anomic.kelondro.tools.ByteArray;

public class BLOBCompressor extends Thread implements BLOB {

    static byte[] gzipMagic  = {(byte) 'z', (byte) '|'}; // magic for gzip-encoded content
    static byte[] plainMagic = {(byte) 'p', (byte) '|'}; // magic for plain content (no encoding)
    
    private BLOB backend;
    private HashMap<String, byte[]> buffer; // entries which are not yet compressed, format is RAW (without magic)
    private long bufferlength;
    private long maxbufferlength;
    private int cdr;
    
    public BLOBCompressor(BLOB backend, long buffersize) {
        this.backend = backend;
        this.maxbufferlength = buffersize;
        this.cdr = 0;
        initBuffer();
    }
    
    public String name() {
        return this.backend.name();
    }
    
    public synchronized void clear() throws IOException {
        initBuffer();
        this.backend.clear();
    }
    
    private void initBuffer() {
        this.buffer = new HashMap<String, byte[]>();
        this.bufferlength = 0;
    }

    public ByteOrder ordering() {
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
        int l = b.length;
        if (l < 100) return markWithPlainMagic(b);
        byte[] bb = compressAddMagic(b);
        if (bb.length >= l) return markWithPlainMagic(b);
        return bb;
    }
    
    private byte[] compressAddMagic(byte[] b) {
        // compress a byte array and add a leading magic for the compression
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
        if (b == null) return null;
        if (ByteArray.equals(b, gzipMagic)) {
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
        } else if (ByteArray.equals(b, plainMagic)) {
            System.out.print("-"); // DEBUG
            byte[] r = new byte[b.length - 2];
            System.arraycopy(b, 2, r, 0, b.length - 2);
            return r;
        } else {
            // we consider that the entry is also plain, but without leading magic
            return b;
        }
    }

    public synchronized byte[] get(byte[] key) throws IOException {
        // depending on the source of the result, we additionally do entry compression
        // because if a document was read once, we think that it will not be retrieved another time again soon
        byte[] b = buffer.remove(new String(key));
        if (b != null) {
            // compress the entry now and put it to the backend
            byte[] bb = compress(b);
            this.backend.put(key, bb);
            this.bufferlength = this.bufferlength - b.length;
            return b;
        }
        
        // return from the backend
        b = this.backend.get(key);
        if (b == null) return null;
        return decompress(b);
    }

    public synchronized boolean has(byte[] key) {
        return 
          this.buffer.containsKey(new String(key)) || this.backend.has(key);
    }

    public int keylength() {
        return this.backend.keylength();
    }

    public synchronized long length() {
        try {
            return this.backend.length() + this.bufferlength;
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }
    
    public synchronized long length(byte[] key) throws IOException {
        byte[] b = buffer.get(new String(key));
        if (b != null) return b.length;
        b = this.backend.get(key);
        if (b == null) return 0;
        b = decompress(b);
        return (b == null) ? 0 : b.length;
    }
    
    private int removeFromQueues(byte[] key) throws IOException {
        byte[] b = buffer.remove(new String(key));
        if (b != null) return b.length;
        return 0;
    }
    
    public synchronized void put(byte[] key, byte[] b) throws IOException {
        
        // first ensure that the files do not exist anywhere
        remove(key);
        
        // check if the buffer is full or could be full after this write
        if (this.bufferlength + b.length * 2 > this.maxbufferlength) {
            // in case that we compress, just compress as much as is necessary to get enough room
            while (this.bufferlength + b.length * 2 > this.maxbufferlength && this.buffer.size() > 0) {
                flushOne();
            }
            // in case that this was not enough, just flush all
            if (this.bufferlength + b.length * 2 > this.maxbufferlength) flushAll();
        }

        // files are written uncompressed to the uncompressed-queue
        // they are either written uncompressed to the database
        // or compressed later
        this.buffer.put(new String(key), b);
        this.bufferlength += b.length;
    }

    public synchronized void remove(byte[] key) throws IOException {
        this.backend.remove(key);
        long rx = removeFromQueues(key);
        if (rx > 0) this.bufferlength -= rx;
    }

    public int size() {
        return this.backend.size() + this.buffer.size();
    }
    
    public synchronized CloneableIterator<byte[]> keys(boolean up, boolean rotating) throws IOException {
        flushAll();
        return this.backend.keys(up, rotating);
    }

    public synchronized CloneableIterator<byte[]> keys(boolean up, byte[] firstKey) throws IOException {
        flushAll();
        return this.backend.keys(up, firstKey);
    }
    
    private boolean flushOne() throws IOException {
        if (this.buffer.size() == 0) return false;
        // depending on process case, write it to the file or compress it to the other queue
        Map.Entry<String, byte[]> entry = this.buffer.entrySet().iterator().next();
        this.buffer.remove(entry.getKey());
        byte[] b = entry.getValue();
        this.bufferlength -= b.length;
        b = compress(b);
        this.backend.put(entry.getKey().getBytes(), b);
        return true;
    }

    private void flushAll() throws IOException {
        while (this.buffer.size() > 0) {
            if (!flushOne()) break;
        }
        assert this.bufferlength == 0;
    }

    public int replace(byte[] key, Rewriter rewriter) throws IOException {
        byte[] b = get(key);
        if (b == null) return 0;
        byte[] c = rewriter.rewrite(b);
        int reduction = c.length - b.length;
        assert reduction >= 0;
        if (reduction == 0) return 0;
        this.put(key, c);
        return reduction;
    }

}
