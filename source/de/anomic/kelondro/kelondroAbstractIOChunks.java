// kelondroAbstractIOChunks.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
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

package de.anomic.kelondro;

import java.io.IOException;

public abstract class kelondroAbstractIOChunks {

    // logging support
    protected String name = null;
    public String name() {
        return name;
    }

    // pseudo-native methods:
    abstract public int read(long pos, byte[] b, int off, int len) throws IOException;
    abstract public void write(long pos, byte[] b, int off, int len) throws IOException;
    abstract public void close() throws IOException;
    

    // derived methods:
    public void readFully(long pos, byte[] b, int off, int len) throws IOException {
        if (len < 0) throw new IndexOutOfBoundsException("length is negative:" + len);
        if (b.length < off + len) throw new IndexOutOfBoundsException("bounds do not fit: b.length=" + b.length + ", off=" + off + ", len=" + len);
        while (len > 0) {
            int r = read(pos, b, off, len);
            if (r < 0) throw new IOException("EOF"); // read exceeded EOF
            pos += r;
            off += r;
            len -= r;
        }
    }

    public byte readByte(long pos) throws IOException {
        byte[] b = new byte[1];
        this.readFully(pos, b, 0, 1);
        return b[0];
    }

    public void writeByte(long pos, final int v) throws IOException {
        this.write(pos, new byte[]{(byte) (v & 0xFF)});
    }

    public short readShort(long pos) throws IOException {
        byte[] b = new byte[2];
        this.readFully(pos, b, 0, 2);
        return (short) ((((int) b[0] & 0xFF) << 8) | (((int) b[1] & 0xFF) << 0));
    }

    public void writeShort(long pos, final int v) throws IOException {
        this.write(pos, new byte[]{(byte) ((v >>> 8) & 0xFF), (byte) ((v >>> 0) & 0xFF)});
    }

    public int readInt(long pos) throws IOException {
        byte[] b = new byte[4];
        this.readFully(pos, b, 0, 4);
        return (((int) b[0] & 0xFF) << 24) | (((int) b[1] & 0xFF) << 16) | (((int) b[2] & 0xFF) << 8) | ((int) b[3] & 0xFF);
    }

    public void writeInt(long pos, final int v) throws IOException {
        this.write(pos, new byte[]{
                        (byte) ((v >>> 24) & 0xFF),
                        (byte) ((v >>> 16) & 0xFF),
                        (byte) ((v >>>  8) & 0xFF),
                        (byte) ((v >>>  0) & 0xFF)
                        });
    }

    public long readLong(long pos) throws IOException {
        byte[] b = new byte[8];
        this.readFully(pos, b, 0, 8);
        return (((long) b[0] & 0xFF) << 56) | (((long) b[1] & 0xFF) << 48) | (((long) b[2]) << 40) | (((long) b[3] & 0xFF) << 32) | (((long) b[4] & 0xFF) << 24) | (((long) b[5] & 0xFF) << 16) | (((long) b[6] & 0xFF) << 8) | ((long) b[7] & 0xFF);
    }

    public void writeLong(long pos, final long v) throws IOException {
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

    public void write(long pos, final byte[] b) throws IOException {
        this.write(pos, b, 0, b.length);
    }

}