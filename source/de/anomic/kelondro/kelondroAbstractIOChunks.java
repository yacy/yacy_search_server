// kelondroAbstractIOChunks.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 11.12.2005
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

import java.io.IOException;

public abstract class kelondroAbstractIOChunks implements kelondroIOChunks {

    // logging support
    protected String name = null;
    public String name() {
        return name;
    }
    
    // pseudo-native methods:
    abstract public long length() throws IOException;
    abstract public void write(long pos, byte[] b, int off, int len) throws IOException;
    abstract public void close() throws IOException;
    abstract public void readFully(long pos, final byte[] b, int off, int len) throws IOException;

    public synchronized byte readByte(final long pos) throws IOException {
        final byte[] b = new byte[1];
        this.readFully(pos, b, 0, 1);
        return b[0];
    }

    public synchronized void writeByte(final long pos, final int v) throws IOException {
        this.write(pos, new byte[]{(byte) (v & 0xFF)});
    }

    public synchronized short readShort(final long pos) throws IOException {
        final byte[] b = new byte[2];
        this.readFully(pos, b, 0, 2);
        return (short) (((b[0] & 0xFF) << 8) | ((b[1] & 0xFF) << 0));
    }

    public synchronized void writeShort(final long pos, final int v) throws IOException {
        this.write(pos, new byte[]{(byte) ((v >>> 8) & 0xFF), (byte) ((v >>> 0) & 0xFF)});
    }

    public synchronized int readInt(final long pos) throws IOException {
        final byte[] b = new byte[4];
        this.readFully(pos, b, 0, 4);
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    public synchronized void writeInt(final long pos, final int v) throws IOException {
        this.write(pos, new byte[]{
                        (byte) ((v >>> 24) & 0xFF),
                        (byte) ((v >>> 16) & 0xFF),
                        (byte) ((v >>>  8) & 0xFF),
                        (byte) ((v >>>  0) & 0xFF)
                        });
    }

    public synchronized long readLong(final long pos) throws IOException {
        final byte[] b = new byte[8];
        this.readFully(pos, b, 0, 8);
        return (((long) b[0] & 0xFF) << 56) | (((long) b[1] & 0xFF) << 48) | (((long) b[2]) << 40) | (((long) b[3] & 0xFF) << 32) | (((long) b[4] & 0xFF) << 24) | (((long) b[5] & 0xFF) << 16) | (((long) b[6] & 0xFF) << 8) | ((long) b[7] & 0xFF);
    }

    public synchronized void writeLong(final long pos, final long v) throws IOException {
        this.write(pos, new byte[]{
                        (byte) ((v >>> 56) & 0xFF),
                        (byte) ((v >>> 48) & 0xFF),
                        (byte) ((v >>> 40) & 0xFF),
                        (byte) ((v >>> 32) & 0xFF),
                        (byte) ((v >>> 24) & 0xFF),
                        (byte) ((v >>> 16) & 0xFF),
                        (byte) ((v >>>  8) & 0xFF),
                        (byte) ((v >>>  0) & 0xFF)
                        });
    }

    public synchronized void write(final long pos, final byte[] b) throws IOException {
        this.write(pos, b, 0, b.length);
    }
    
    public synchronized void writeSpace(long pos, int spaceCount) throws IOException {
        if (spaceCount < 512) {
        	write(pos, space(spaceCount));
        	return;
        }
        byte[] b = space(512);
        while (spaceCount > b.length) {
        	write(pos, b);
        	pos += b.length;
        	spaceCount -= b.length;
        }
        if (spaceCount > 0) {
        	write(pos, space(spaceCount));
        }
    }
    
    private byte[] space(int count) {
    	byte[] s = new byte[count];
    	while (count-- > 0) s[count] = 0;
    	return s;
    }

}