// kelondroAbstractRA.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.anomic.server.serverByteBuffer;

abstract class kelondroAbstractRA implements kelondroRA {

    // logging support
    protected String name = null;
    protected File file = null;
    public String name() {
        return name;
    }
    public File file() {
        return file;
    }

    // pseudo-native methods:
    abstract public long length() throws IOException;
    abstract public long available() throws IOException;
    
    abstract public int read() throws IOException;
    abstract public void write(int b) throws IOException;

    abstract public int read(byte[] b, int off, int len) throws IOException;
    abstract public void write(byte[] b, int off, int len) throws IOException;

    abstract public void seek(long pos) throws IOException;
    abstract public void close() throws IOException;

    // derived methods:
    public void readFully(final byte[] b, int off, int len) throws IOException {
        if (len < 0) throw new IndexOutOfBoundsException("length is negative:" + len);
        if (b.length < off + len) throw new IndexOutOfBoundsException("bounds do not fit: b.length=" + b.length + ", off=" + off + ", len=" + len);
        while (len > 0) {
            final int r = read(b, off, len);
            if (r < 0) throw new IOException("EOF"); // read exceeded EOF
            off += r;
            len -= r;
        }
    }
    
    public byte[] readFully() throws IOException {
        final ByteArrayOutputStream dest = new ByteArrayOutputStream(512);
        final byte[] buffer = new byte[1024];
        
        int c, total = 0;
        while ((c = read(buffer, 0, 1024)) > 0) {
            dest.write(buffer, 0, c);
            total += c;
        }
        dest.flush();
        dest.close();
        return dest.toByteArray();
    }
    
    public byte readByte() throws IOException {
        final int ch = this.read();
        if (ch < 0) throw new IOException();
        return (byte)(ch);
    }

    public void writeByte(final int v) throws IOException {
        this.write(v);
    }

    public short readShort() throws IOException {
        final int ch1 = this.read();
        final int ch2 = this.read();
        if ((ch1 | ch2) < 0) throw new IOException();
        return (short) ((ch1 << 8) | (ch2 << 0));
    }

    public void writeShort(final int v) throws IOException {
        this.write((v >>> 8) & 0xFF); this.write((v >>> 0) & 0xFF);
    }

    public int readInt() throws IOException {
        final int ch1 = this.read();
        final int ch2 = this.read();
        final int ch3 = this.read();
        final int ch4 = this.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0) throw new IOException("kelondroAbstractRA.readInt: wrong values; ch1=" + ch1 + ", ch2=" + ch2 + ", ch3=" + ch3 + ", ch4=" + ch4);
        return ((ch1 << 24) | (ch2 << 16) | (ch3 << 8) | ch4);
    }

    public void writeInt(final int v) throws IOException {
        this.write((v >>> 24) & 0xFF); this.write((v >>> 16) & 0xFF);
        this.write((v >>>  8) & 0xFF); this.write((v >>>  0) & 0xFF);
    }

    public long readLong() throws IOException {
        return ((long) (readInt()) << 32) | (readInt() & 0xFFFFFFFFL);
    }

    public void writeLong(final long v) throws IOException {
        this.write((int) (v >>> 56) & 0xFF); this.write((int) (v >>> 48) & 0xFF);
        this.write((int) (v >>> 40) & 0xFF); this.write((int) (v >>> 32) & 0xFF);
        this.write((int) (v >>> 24) & 0xFF); this.write((int) (v >>> 16) & 0xFF);
        this.write((int) (v >>>  8) & 0xFF); this.write((int) (v >>>  0) & 0xFF);
    }

    public void write(final byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    private static final byte cr = 13;
    private static final byte lf = 10;
    
    public void writeLine(final String line) throws IOException {
        final byte[] b = new byte[line.length() + 2];
        System.arraycopy(line.getBytes(), 0, b, 0, line.length());
        b[b.length - 2] = cr;
        b[b.length - 1] = lf;
        this.write(b);
    }

    public void writeLine(final byte[] line) throws IOException {
        final byte[] b = new byte[line.length + 2];
        System.arraycopy(line, 0, b, 0, line.length);
        b[b.length - 2] = cr;
        b[b.length - 1] = lf;
        this.write(b);
    }

    public String readLine() throws IOException {
        // with these functions, we consider a line as always terminated by CRLF
        byte[] bb = new byte[80];
        int bbsize = 0;
        int c;
        while (true) {
            c = read();
            if (c < 0) {
                if (bbsize == 0) return null;
                return new String(bb, 0, bbsize);
            }
            if (c == cr) continue;
            if (c == lf) return new String(bb, 0, bbsize, "UTF-8");

            // append to bb
            if (bbsize == bb.length) {
                // extend bb size
                byte[] newbb = new byte[bb.length * 2];
                System.arraycopy(bb, 0, newbb, 0, bb.length);
                bb = newbb;
                newbb = null;
            }
            bb[bbsize++] = (byte) c;
        }
    }

    public void writeMap(final Map<String, String> map, final String comment) throws IOException {
        this.seek(0);
        final Iterator<Map.Entry<String, String>> iter = map.entrySet().iterator();
        Map.Entry<String, String> entry;
        final serverByteBuffer bb = new serverByteBuffer(map.size() * 40);
        bb.append("# ").append(comment).append("\r\n");
        while (iter.hasNext()) {
            entry = iter.next();
            bb.append(entry.getKey()).append('=');
            if (entry.getValue() != null) { bb.append(entry.getValue()); }
            bb.append("\r\n");
        }
        bb.append("# EOF\r\n");
        write(bb.getBytes());
        bb.close();
    }

    public HashMap<String, String> readMap() throws IOException {
        this.seek(0);
        final byte[] b = readFully();
        final BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(b)));
        final HashMap<String, String> map = new HashMap<String, String>();
        String line;
        int pos;
        while ((line = br.readLine()) != null) { // very slow readLine????
            line = line.trim();
            if (line.equals("# EOF")) return map;
            if ((line.length() == 0) || (line.charAt(0) == '#')) continue;
            pos = line.indexOf("=");
            if (pos < 0) continue;
            map.put(line.substring(0, pos), line.substring(pos + 1));
        }
        return map;
    }
    
    
    /**
     * this does not write the content to the see position
     * but to the very beginning of the record
     * some additional bytes will ensure that we know the correct content size later on
     */
    public void writeArray(final byte[] b) throws IOException {
        seek(0);
        writeInt(b.length);
        write(b);
    }

    public byte[] readArray() throws IOException {
        seek(0);
        final int l = readInt();
        final byte[] b = new byte[l];
        read(b, 0, l);
        return b;
    }
    
    public void deleteOnExit() {
        if (this.file != null) this.file.deleteOnExit();
    }

}