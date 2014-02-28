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
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.util.MemoryControl;


public class Compressor implements BLOB, Iterable<byte[]> {

    private static byte[] gzipMagic  = {(byte) 'z', (byte) '|'}; // magic for gzip-encoded content
    private static byte[] plainMagic = {(byte) 'p', (byte) '|'}; // magic for plain content (no encoding)

    private final BLOB backend;
    private TreeMap<byte[], byte[]> buffer; // entries which are not yet compressed, format is RAW (without magic)
    private long bufferlength;
    private final long maxbufferlength;

    public Compressor(final BLOB backend, final long buffersize) {
        this.backend = backend;
        this.maxbufferlength = buffersize;
        initBuffer();
    }

    @Override
    public long mem() {
        return this.backend.mem();
    }

    @Override
    public void optimize() {
        this.backend.optimize();
    }

    @Override
    public String name() {
        return this.backend.name();
    }

    @Override
    public synchronized void clear() throws IOException {
        initBuffer();
        this.backend.clear();
    }

    private void initBuffer() {
        this.buffer = new TreeMap<byte[], byte[]>(this.backend.ordering());
        this.bufferlength = 0;
    }

    @Override
    public ByteOrder ordering() {
        return this.backend.ordering();
    }

    @Override
    public synchronized void close(final boolean writeIDX) {
        // no more thread is running, flush all queues
        flushAll();
        this.backend.close(writeIDX);
    }

    private static byte[] compress(final byte[] b) {
        final int l = b.length;
        if (l < 100) return markWithPlainMagic(b);
        final byte[] bb = compressAddMagic(b);
        if (bb.length >= l) return markWithPlainMagic(b);
        return bb;
    }

    private static byte[] compressAddMagic(final byte[] b) {
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
        } catch (final IOException e) {
            ConcurrentLog.severe("Compressor", "", e);
            return null;
        }
    }

    private static byte[] markWithPlainMagic(final byte[] b) {
        //System.out.print("+"); // DEBUG
        final byte[] r = new byte[b.length + 2];
        r[0] = plainMagic[0];
        r[1] = plainMagic[1];
        System.arraycopy(b, 0, r, 2, b.length);
        return r;
    }

    private static byte[] decompress(final byte[] b) {
        // use a magic in the head of the bytes to identify compression type
        if (b == null) return null;
        if (ByteArray.startsWith(b, gzipMagic)) {
            //System.out.print("\\"); // DEBUG
            final ByteArrayInputStream bais = new ByteArrayInputStream(b);
            // eat up the magic
            bais.read();
            bais.read();
            // decompress what is remaining
            InputStream gis;
            try {
                gis = new GZIPInputStream(bais);
                final ByteArrayOutputStream baos = new ByteArrayOutputStream(b.length);
                final byte[] buf = new byte[1024 * 4];
                int n;
                while ((n = gis.read(buf)) > 0) baos.write(buf, 0, n);
                gis.close();
                bais.close();
                baos.close();

                return baos.toByteArray();
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                return null;
            }
        } else if (ByteArray.startsWith(b, plainMagic)) {
            //System.out.print("-"); // DEBUG
            final byte[] r = new byte[b.length - 2];
            System.arraycopy(b, 2, r, 0, b.length - 2);
            return r;
        } else {
            // we consider that the entry is also plain, but without leading magic
            return b;
        }
    }

    @Override
    public byte[] get(final byte[] key) throws IOException, SpaceExceededException {
        // depending on the source of the result, we additionally do entry compression
        // because if a document was read once, we think that it will not be retrieved another time again soon
        byte[] b = null;
        synchronized (this) {
            b = this.buffer.remove(key);
            if (b != null) {
                this.backend.insert(key, compress(b));
                this.bufferlength = this.bufferlength - b.length;
                return b;
            }
        }

        // return from the backend
        b = this.backend.get(key);
        if (b == null) return null;
        if (!MemoryControl.request(b.length * 2, true)) {
            throw new SpaceExceededException(b.length * 2, "decompress needs 2 * " + b.length + " bytes");
        }
        return decompress(b);
    }

    @Override
    public byte[] get(final Object key) {
        if (!(key instanceof byte[])) return null;
        try {
            return get((byte[]) key);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        return null;
    }

    @Override
    public boolean containsKey(final byte[] key) {
        synchronized (this) {
            return this.buffer.containsKey(key) || this.backend.containsKey(key);
        }
    }

    @Override
    public int keylength() {
        return this.backend.keylength();
    }

    @Override
    public synchronized long length() {
        try {
            return this.backend.length() + this.bufferlength;
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return 0;
        }
    }

    @Override
    public long length(final byte[] key) throws IOException {
        synchronized (this) {
            byte[] b = this.buffer.get(key);
            if (b != null) return b.length;
            try {
                b = this.backend.get(key);
                if (b == null) return 0;
                b = decompress(b);
                return (b == null) ? 0 : b.length;
            } catch (final SpaceExceededException e) {
                throw new IOException(e.getMessage());
            }
        }
    }

    private int removeFromQueues(final byte[] key) {
        final byte[] b = this.buffer.remove(key);
        if (b != null) return b.length;
        return 0;
    }

    @Override
    public void insert(final byte[] key, final byte[] b) throws IOException {

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
            this.buffer.put(key, b);
            this.bufferlength += b.length;
        }

        if (MemoryControl.shortStatus()) flushAll();
    }

    @Override
    public synchronized void delete(final byte[] key) throws IOException {
        this.backend.delete(key);
        final long rx = removeFromQueues(key);
        if (rx > 0) this.bufferlength -= rx;
    }

    @Override
    public synchronized int size() {
        return this.backend.size() + this.buffer.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        if (!this.backend.isEmpty()) return false;
        if (!this.buffer.isEmpty()) return false;
        return true;
    }

    @Override
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final boolean rotating) throws IOException {
        flushAll();
        return this.backend.keys(up, rotating);
    }

    @Override
    public synchronized CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) throws IOException {
        flushAll();
        return this.backend.keys(up, firstKey);
    }

    @Override
    public Iterator<byte[]> iterator() {
        flushAll();
        try {
            return this.backend.keys(true, false);
        } catch (final IOException e) {
            return null;
        }
    }

    private boolean flushOne() {
        if (this.buffer.isEmpty()) return false;
        // depending on process case, write it to the file or compress it to the other queue
        final Map.Entry<byte[], byte[]> entry = this.buffer.entrySet().iterator().next();
        this.buffer.remove(entry.getKey());
        try {
            this.backend.insert(entry.getKey(), compress(entry.getValue()));
            this.bufferlength -= entry.getValue().length;
            return true;
        } catch (final IOException e) {
            this.buffer.put(entry.getKey(), entry.getValue());
            return false;
        }
    }

    public void flushAll() {
        while (!this.buffer.isEmpty()) {
            if (!flushOne()) break;
        }
    }

    @Override
    public int replace(final byte[] key, final Rewriter rewriter) throws IOException, SpaceExceededException {
        final byte[] b = get(key);
        if (b == null) return 0;
        final byte[] c = rewriter.rewrite(b);
        final int reduction = c.length - b.length;
        assert reduction >= 0;
        if (reduction == 0) return 0;
        insert(key, c);
        return reduction;
    }

    @Override
    public int reduce(final byte[] key, final Reducer reducer) throws IOException, SpaceExceededException {
        final byte[] b = get(key);
        if (b == null) return 0;
        final byte[] c = reducer.rewrite(b);
        final int reduction = c.length - b.length;
        assert reduction >= 0;
        if (reduction == 0) return 0;
        insert(key, c);
        return reduction;
    }


}
