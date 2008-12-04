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
    abstract public void write(byte[] b, int off, int len) throws IOException;
    abstract public void seek(long pos) throws IOException;
    abstract public void close() throws IOException;

    // derived methods:
    public byte[] readFully() throws IOException {
        int a = (int) this.available();
        if (a <= 0) return null;
        final byte[] buffer = new byte[a];
        this.readFully(buffer, 0, a);
        return buffer;
    }

    public short readShort() throws IOException {
        byte[] b = new byte[2];
        this.readFully(b, 0, 2);
        if ((b[0] | b[1]) < 0) throw new IOException("kelondroAbstractRA.readInt: wrong values; ch1=" + (b[0] & 0xFF) + ", ch2=" + (b[1] & 0xFF));
        return (short) (((b[0] & 0xFF) << 8) | (b[1] & 0xFF));
    }

    public void writeShort(final int v) throws IOException {
        byte[] b = new byte[2];
        b[0] = (byte) ((v >>>  8) & 0xFF);
        b[1] = (byte) ( v         & 0xFF);
        this.write(b);
    }

    public int readInt() throws IOException {
        byte[] b = new byte[4];
        this.readFully(b, 0, 4);
        if ((b[0] | b[1] | b[2] | b[3]) < 0) throw new IOException("kelondroAbstractRA.readInt: wrong values; ch1=" + (b[0] & 0xFF) + ", ch2=" + (b[1] & 0xFF) + ", ch3=" + (b[2] & 0xFF) + ", ch4=" + (b[3] & 0xFF));
        return (((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF));
    }

    public void writeInt(final int v) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) ((v >>> 24) & 0xFF);
        b[1] = (byte) ((v >>> 16) & 0xFF);
        b[2] = (byte) ((v >>>  8) & 0xFF);
        b[3] = (byte) ( v         & 0xFF);
        this.write(b);
    }

    public long readLong() throws IOException {
        return ((long) (readInt()) << 32) | (readInt() & 0xFFFFFFFFL);
    }

    public void writeLong(final long v) throws IOException {
        byte[] b = new byte[8];
        b[0] = (byte) ((v >>> 56) & 0xFF);
        b[1] = (byte) ((v >>> 48) & 0xFF);
        b[2] = (byte) ((v >>> 40) & 0xFF);
        b[3] = (byte) ((v >>> 32) & 0xFF);
        b[4] = (byte) ((v >>> 24) & 0xFF);
        b[5] = (byte) ((v >>> 16) & 0xFF);
        b[6] = (byte) ((v >>>  8) & 0xFF);
        b[7] = (byte) ( v         & 0xFF);
        this.write(b);
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
    
    public void deleteOnExit() {
        if (this.file != null) this.file.deleteOnExit();
    }

}