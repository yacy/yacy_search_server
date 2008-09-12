// kelondroBufferedIOChunks.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 11.12.2004
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

// this is a WRITE-buffer!
// the buffer MUST be flushed before closing of the underlying kelondroRA

package de.anomic.kelondro;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public final class kelondroBufferedIOChunks extends kelondroAbstractIOChunks implements kelondroIOChunks {

    protected kelondroRA ra;
    private final long bufferMaxSize;
    private long bufferCurrSize;
    private final long commitTimeout;
    private final TreeMap<Long, byte[]> buffer;
    private long lastCommit = 0;
    
    private static final int overhead = 40;
    
    
    public kelondroBufferedIOChunks(final kelondroRA ra, final String name, final long buffer, final long commitTimeout) {
        this.name = name;
        this.ra = ra;
        this.bufferMaxSize = buffer;
        this.bufferCurrSize = 0;
        this.commitTimeout = commitTimeout;
        this.buffer = new TreeMap<Long, byte[]>();
        this.lastCommit = System.currentTimeMillis();
    }

    public kelondroRA getRA() {
    	return this.ra;
    }
    
    public long length() throws IOException {
        return ra.length();
    }
    
    public synchronized int read(final long pos, final byte[] b, final int off, final int len) throws IOException {
        assert (b.length >= off + len): "read pos=" + pos  + ", b.length=" + b.length + ", off=" + off + ", len=" + len;
        
        // check commit time
        if ((bufferCurrSize > bufferMaxSize) ||
            (this.lastCommit + this.commitTimeout > System.currentTimeMillis())) {
            commit();
            this.lastCommit = System.currentTimeMillis();
        }

        // do the read
        synchronized (this.buffer) {
            final byte[] bb = buffer.get(Long.valueOf(pos));
            if (bb == null) {
                // entry not known, read directly from IO
                synchronized (this.ra) {
                    this.ra.seek(pos + off);
                    return ra.read(b, off, len);
                }
            }
            // use buffered entry
            if (bb.length >= off + len) {
                // the buffered entry is long enough
                System.arraycopy(bb, off, b, off, len);
                return len;
            }
            // the entry is not long enough. transmit only a part
            System.arraycopy(bb, off, b, off, bb.length - off);
            return bb.length - off;
        }
    }

    public synchronized void write(final long pos, final byte[] b, final int off, final int len) throws IOException {
        assert (b.length >= off + len): "write pos=" + pos + ", b.length=" + b.length + ", b='" + new String(b) + "', off=" + off + ", len=" + len;

        //if (len > 10) System.out.println("WRITE(" + name + ", " + pos + ", " + b.length + ", "  + off + ", "  + len + ")");
        
        // do the write into buffer
        final byte[] bb = kelondroObjectSpace.alloc(len);
        System.arraycopy(b, off, bb, 0, len);
        synchronized (buffer) {
            buffer.put(Long.valueOf(pos + off), bb);
            bufferCurrSize += overhead + len;
        }
        
        // check commit time
        if ((bufferCurrSize > bufferMaxSize) ||
            (this.lastCommit + this.commitTimeout > System.currentTimeMillis())) {
            commit();
            this.lastCommit = System.currentTimeMillis();
        }
    }

    public synchronized void commit() throws IOException {
        synchronized (buffer) {
            if (buffer.size() == 0) return;
            final Iterator<Map.Entry<Long, byte[]>> i = buffer.entrySet().iterator();
            Map.Entry<Long, byte[]> entry = i.next();
            long lastPos = (entry.getKey()).longValue();
            byte[] lastChunk = entry.getValue();
            long nextPos;
            byte[] nextChunk, tmpChunk;
            synchronized (this.ra) {
                while (i.hasNext()) {
                    entry = i.next();
                    nextPos = (entry.getKey()).longValue();
                    nextChunk = entry.getValue();
                    if (lastPos + lastChunk.length == nextPos) {
                        // try to combine the new chunk with the previous chunk
                        //System.out.println("combining chunks pos0=" + lastPos + ", chunk0.length=" + lastChunk.length + ", pos1=" + nextPos + ", chunk1.length=" + nextChunk.length);
                        tmpChunk = kelondroObjectSpace.alloc(lastChunk.length + nextChunk.length);
                        System.arraycopy(lastChunk, 0, tmpChunk, 0, lastChunk.length);
                        System.arraycopy(nextChunk, 0, tmpChunk, lastChunk.length, nextChunk.length);
                        kelondroObjectSpace.recycle(lastChunk);
                        lastChunk = tmpChunk;
                        tmpChunk = null;
                        kelondroObjectSpace.recycle(nextChunk);
                    } else {
                        // write the last chunk and take nextChunk next time als lastChunk
                        this.ra.seek(lastPos);
                        this.ra.write(lastChunk);
                        kelondroObjectSpace.recycle(lastChunk);
                        lastPos = nextPos;
                        lastChunk = nextChunk;
                    }
                }
                // at the end write just the last chunk
                this.ra.seek(lastPos);
                this.ra.write(lastChunk);
                kelondroObjectSpace.recycle(lastChunk);
            }
            buffer.clear();
            bufferCurrSize = 0;
        }
    }
    
    public synchronized void close() throws IOException {
        if (this.ra != null) {
            commit();
            this.ra.close();
        }
        this.ra = null;
    }

    protected void finalize() throws Throwable {
        if (this.ra != null) this.close();
        super.finalize();
    }
    
    public void deleteOnExit() {
        this.ra.deleteOnExit();
    }
}
