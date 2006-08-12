// kelondroBufferedIOChunks.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
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

// this is a WRITE-buffer!
// the buffer MUST be flushed before closing of the underlying kelondroRA

package de.anomic.kelondro;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public final class kelondroBufferedIOChunks extends kelondroAbstractIOChunks implements kelondroIOChunks {

    protected kelondroRA ra;
    private long bufferMaxSize, bufferCurrSize;
    private long commitTimeout;
    private TreeMap buffer;
    private long lastCommit = 0;
    
    private static final int overhead = 40;
    
    
    public kelondroBufferedIOChunks(kelondroRA ra, String name, long buffer, long commitTimeout) {
        this.name = name;
        this.ra = ra;
        this.bufferMaxSize = buffer;
        this.bufferCurrSize = 0;
        this.commitTimeout = commitTimeout;
        this.buffer = new TreeMap();
        this.lastCommit = System.currentTimeMillis();
    }

    public long length() throws IOException {
        return ra.length();
    }
    
    public int read(long pos, byte[] b, int off, int len) throws IOException {
        assert (b.length >= off + len): "read pos=" + pos  + ", b.length=" + b.length + ", off=" + off + ", len=" + len;
        
        // check commit time
        if ((bufferCurrSize > bufferMaxSize) ||
            (this.lastCommit + this.commitTimeout < System.currentTimeMillis())) {
            commit();
            this.lastCommit = System.currentTimeMillis();
        }

        // do the read
        synchronized (this.buffer) {
            byte[] bb = (byte[]) buffer.get(new Long(pos));
            if (bb == null) {
                // entry not known, read direktly from IO
                synchronized (this.ra) {
                    this.ra.seek(pos + off);
                    return ra.read(b, off, len);
                }
            } else {
                // use buffered entry
                if (bb.length >= off + len) {
                    // the bufferd entry is long enough
                    System.arraycopy(bb, off, b, off, len);
                    return len;
                } else {
                    // the entry is not long enough. transmit only a part
                    System.arraycopy(bb, off, b, off, bb.length - off);
                    return bb.length - off;
                }
            }
        }
    }

    public void write(long pos, byte[] b, int off, int len) throws IOException {
        assert (b.length >= off + len): "write pos=" + pos + ", b.length=" + b.length + ", b='" + new String(b) + "', off=" + off + ", len=" + len;

        //if (len > 10) System.out.println("WRITE(" + name + ", " + pos + ", " + b.length + ", "  + off + ", "  + len + ")");
        
        // do the write into buffer
        byte[] bb = kelondroObjectSpace.alloc(len);
        System.arraycopy(b, off, bb, 0, len);
        synchronized (buffer) {
            buffer.put(new Long(pos + off), bb);
            bufferCurrSize += overhead + pos + off;
        }
        
        // check commit time
        if ((bufferCurrSize > bufferMaxSize) ||
            (this.lastCommit + this.commitTimeout < System.currentTimeMillis())) {
            commit();
            this.lastCommit = System.currentTimeMillis();
        }
    }

    public void commit() throws IOException {
        synchronized (buffer) {
            if (buffer.size() == 0) return;
            Iterator i = buffer.entrySet().iterator();
            Map.Entry entry = (Map.Entry) i.next();
            long lastPos = ((Long) entry.getKey()).longValue();
            byte[] lastChunk = (byte[]) entry.getValue();
            long nextPos;
            byte[] nextChunk, tmpChunk;
            synchronized (this.ra) {
                while (i.hasNext()) {
                    entry = (Map.Entry) i.next();
                    nextPos = ((Long) entry.getKey()).longValue();
                    nextChunk = (byte[]) entry.getValue();
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
    
    public void close() throws IOException {
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
    
}
