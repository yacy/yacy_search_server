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

package de.anomic.kelondro.io;

import java.io.IOException;


public final class BufferedIOChunks extends AbstractIOChunks implements IOChunksInterface {

    protected RandomAccessInterface ra;
    private int bufferSize;
    private final long commitTimeout;
    private byte[] buffer;
    private long lastCommit = 0;
    
    public BufferedIOChunks(final RandomAccessInterface ra, final String name, final int buffersize, final long commitTimeout) {
        this.name = name;
        this.ra = ra;
        this.bufferSize = 0;
        this.commitTimeout = commitTimeout;
        this.buffer = null; // this is a buffer at the end of the file. It will be initialized if necessary
        this.lastCommit = System.currentTimeMillis();
    }

    public RandomAccessInterface getRA() {
    	return this.ra;
    }
    
    public long length() throws IOException {
        return ra.length() + this.bufferSize;
    }
    
    public synchronized void readFully(final long pos, final byte[] b, final int off, final int len) throws IOException {
        assert (b.length >= off + len): "read pos=" + pos  + ", b.length=" + b.length + ", off=" + off + ", len=" + len;
        
        // check commit time
        if (this.lastCommit + this.commitTimeout > System.currentTimeMillis()) {
            commit();
        }

        // do the read
        if (pos >= this.ra.length()) {
            // read from the buffer
            if (this.buffer == null) this.buffer = new byte[this.bufferSize];
            System.arraycopy(this.buffer, (int) (pos - this.ra.length()), b, off, len);
        } else if (pos + len >= this.ra.length()) {
            // the content is partly in the file and partly in the buffer
            commit();
            this.ra.seek(pos);
            ra.readFully(b, off, len);
        } else {
            // read from the file
            this.ra.seek(pos);
            ra.readFully(b, off, len);
        }
    }

    public synchronized void write(final long pos, final byte[] b, final int off, final int len) throws IOException {
        assert (b.length >= off + len): "write pos=" + pos + ", b.length=" + b.length + ", b='" + new String(b) + "', off=" + off + ", len=" + len;
        //assert pos <= this.ra.length(): "pos = " + pos + ", this.ra.length() = " + this.ra.length();
        
        if (len == 0) return;
        if (pos >= this.ra.length()) {
            // the position is fully outside of the file
            if (this.buffer != null && pos - this.ra.length() + len > this.buffer.length) {
                // this does not fit into the buffer
                commit();
                this.ra.seek(pos);
                this.ra.write(b, off, len);
                return;
            }
            if (this.buffer == null) this.buffer = new byte[this.bufferSize];
            System.arraycopy(b, off, this.buffer, (int) (pos - this.ra.length()), len);
            this.bufferSize = (int) Math.max(this.bufferSize, pos - this.ra.length() + len);
            return;
        } else if (pos + len >= this.ra.length()) {
            // the content is partly in the file and partly in the buffer
            commit();
            this.ra.seek(pos);
            this.ra.write(b, off, len);
            return;
        } else {
            // the position is fully inside the file
            this.ra.seek(pos);
            this.ra.write(b, off, len);
            return;
        }
    }

    public synchronized void commit() throws IOException {
        this.lastCommit = System.currentTimeMillis();
        if (this.buffer == null || this.bufferSize == 0) return;
        this.ra.seek(this.ra.length()); // move to end of file
        this.ra.write(this.buffer, 0, this.bufferSize);
        this.bufferSize = 0;
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
