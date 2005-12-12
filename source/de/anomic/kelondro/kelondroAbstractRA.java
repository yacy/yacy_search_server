// kelondroAbstractRA.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

abstract class kelondroAbstractRA implements kelondroRA {

    // logging support
    protected String name = null;
    public String name() {
        return name;
    }

    // pseudo-native methods:
    abstract public int read() throws IOException;
    abstract public void write(int b) throws IOException;

    abstract public int read(byte[] b, int off, int len) throws IOException;
    abstract public void write(byte[] b, int off, int len) throws IOException;

    abstract public void seek(long pos) throws IOException;
    abstract public void close() throws IOException;

    // derived methods:
    public void readFully(byte[] b, int off, int len) throws IOException {
        if (len < 0) throw new IndexOutOfBoundsException("length is negative:" + len);
        if (b.length < off + len) throw new IndexOutOfBoundsException("bounds do not fit: b.length=" + b.length + ", off=" + off + ", len=" + len);
        while (len > 0) {
            int r = read(b, off, len);
            if (r < 0) throw new IOException("EOF"); // read exceeded EOF
            off += r;
            len -= r;
        }
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
        this.write(line.getBytes());
        this.write(cr);
        this.write(lf);
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
            if (c == lf) return new String(bb, 0, bbsize);

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

    public void writeProperties(final Properties props, final String comment) throws IOException {
        this.seek(0);
        writeLine("# " + comment);
        final Enumeration e = props.propertyNames();
        String key, value;
        while (e.hasMoreElements()) {
            key = (String) e.nextElement();
            value = props.getProperty(key, "");
            write(key.getBytes());
            write((byte) '=');
            writeLine(value);
        }
        writeLine("# EOF");
    }

    public Properties readProperties() throws IOException {
        this.seek(0);
        final Properties props = new Properties();
        String line;
        int pos;
        while ((line = readLine()) != null) {
            line = line.trim();
            if (line.equals("# EOF")) return props;
            if ((line.length() == 0) || (line.charAt(0) == '#')) continue;
            pos = line.indexOf("=");
            if (pos < 0) continue;
            props.setProperty(line.substring(0, pos).trim(), line.substring(pos + 1).trim());
        }
        return props;
    }

    public void writeMap(final Map map, final String comment) throws IOException {
        this.seek(0);
        writeLine("# " + comment);
        final Iterator iter = map.entrySet().iterator();
        Map.Entry entry;
        while (iter.hasNext()) {
            entry = (Map.Entry) iter.next();
            write(((String) entry.getKey()).getBytes());
            write((byte) '=');
           writeLine((String) entry.getValue());
        }
        writeLine("# EOF");
    }

    public Map readMap() throws IOException {
        this.seek(0);
        final TreeMap map = new TreeMap();
        String line;
        int pos;
        while ((line = readLine()) != null) { // very slow readLine????
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

}