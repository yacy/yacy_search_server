// kelondroAbstractRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 09.02.2004
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

import java.io.*;
import java.util.*;

import de.anomic.server.*;

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

    // derivated methods:
    public byte readByte() throws IOException {
	int ch = this.read();
	if (ch < 0) throw new IOException();
	return (byte)(ch);
    }

    public void writeByte(int v) throws IOException {
	this.write(v);
    }

    public short readShort() throws IOException {
	int ch1 = this.read();
	int ch2 = this.read();
	if ((ch1 | ch2) < 0) throw new IOException();
	return (short) ((ch1 << 8) + (ch2 << 0));
    }

    public void writeShort(int v) throws IOException {
	this.write((v >>> 8) & 0xFF); this.write((v >>> 0) & 0xFF);
    }

    public int readInt() throws IOException {
	int ch1 = this.read();
	int ch2 = this.read();
	int ch3 = this.read();
	int ch4 = this.read();
	if ((ch1 | ch2 | ch3 | ch4) < 0) throw new IOException("kelondroAbstractRA.readInt: wrong values; ch1=" + ch1 + ", ch2=" + ch2 + ", ch3=" + ch3 + ", ch4=" + ch4);
	return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public void writeInt(int v) throws IOException {
	this.write((v >>> 24) & 0xFF); this.write((v >>> 16) & 0xFF);
	this.write((v >>>  8) & 0xFF); this.write((v >>>  0) & 0xFF);
    }

    public long readLong() throws IOException {
	return ((long) (readInt()) << 32) + (readInt() & 0xFFFFFFFFL);
    }

    public void writeLong(long v) throws IOException {
	this.write((int) (v >>> 56) & 0xFF); this.write((int) (v >>> 48) & 0xFF);
	this.write((int) (v >>> 40) & 0xFF); this.write((int) (v >>> 32) & 0xFF);
	this.write((int) (v >>> 24) & 0xFF); this.write((int) (v >>> 16) & 0xFF);
	this.write((int) (v >>>  8) & 0xFF); this.write((int) (v >>>  0) & 0xFF);
    }

    public void write(byte[] b) throws IOException {
	this.write(b, 0, b.length);
    }

    private static final byte cr = 13;
    private static final byte lf = 10;
    private static final String crlf = new String(new byte[] {cr, lf});

    public void writeLine(String line) throws IOException {
	this.write((line + crlf).getBytes());
    }

    public String readLine() throws IOException {
	// with these functions, we consider a line as always terminated by CRLF
	serverByteBuffer sb = new serverByteBuffer();
	int c;
	while (true) {
	    c = read();
	    if (c < 0) {
		if (sb.length() == 0) return null; else return sb.toString();
	    }
	    if (c == cr) continue;
	    if (c == lf) return sb.toString();
	    sb.append((byte) c);
	}
    }

    public void writeProperties(Properties props, String comment) throws IOException {
	this.seek(0);
	writeLine("# " + comment);
	Enumeration e = props.propertyNames();
	String key, value;
	while (e.hasMoreElements()) {
	    key = (String) e.nextElement();
	    value = props.getProperty(key, "");
	    writeLine(key + "=" + value);
	}
	writeLine("# EOF");
    }

    public Properties readProperties() throws IOException {
	this.seek(0);
	Properties props = new Properties();
	String line;
	int pos;
	while ((line = readLine()) != null) {
	    line = line.trim();
	    if (line.equals("# EOF")) return props;
	    if ((line.length() == 0) || (line.startsWith("#"))) continue;
	    pos = line.indexOf("=");
	    if (pos < 0) continue;
	    props.setProperty(line.substring(0, pos).trim(), line.substring(pos + 1).trim());
	}
	return props;
    }

    public void writeMap(Map map, String comment) throws IOException {
	this.seek(0);
	writeLine("# " + comment);
	Iterator i = map.keySet().iterator();
	String key, value;
	while (i.hasNext()) {
	    key = (String) i.next();
	    value = (String) map.get(key);
	    writeLine(key + "=" + value);
	}
	writeLine("# EOF");
    }

    public Map readMap() throws IOException {
	this.seek(0);
	TreeMap map = new TreeMap();
	String line;
	int pos;
	while ((line = readLine()) != null) { // very slow readLine????
	    line = line.trim();
	    if (line.equals("# EOF")) return map;
	    if ((line.length() == 0) || (line.startsWith("#"))) continue;
	    pos = line.indexOf("=");
	    if (pos < 0) continue;
	    map.put(line.substring(0, pos), line.substring(pos + 1));
	}
	return map;
    }

    public void writeArray(byte[] b) throws IOException {
	// this does not write the content to the see position
	// but to the very beginning of the record
	// some additional bytes will ensure that we know the correct content size later on
	seek(0);
	writeInt(b.length);
	write(b);
    }

    public byte[] readArray() throws IOException {
	seek(0);
	int l = readInt();
	byte[] b = new byte[l];
	read(b, 0, l);
	return b;
    }

}
