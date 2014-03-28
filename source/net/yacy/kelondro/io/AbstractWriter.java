// AbstractWriter.java
// -------------------------------
// (C) 2004 by Michael Peter Christen; mc@yacy.net
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

package net.yacy.kelondro.io;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.ByteBuffer;


public abstract class AbstractWriter extends AbstractReader implements Writer {

    
    // pseudo-native methods:
    @Override
    abstract public void setLength(long length) throws IOException;
    @Override
    abstract public void write(byte[] b, int off, int len) throws IOException;
    
    // derived methods:


    @Override
    public final void writeShort(final int v) throws IOException {
        byte[] b = new byte[2];
        b[0] = (byte) ((v >>>  8) & 0xFF);
        b[1] = (byte) ( v         & 0xFF);
        this.write(b);
    }

    @Override
    public final void writeInt(final int v) throws IOException {
        this.write(int2array(v));
    }
    
    public final static byte[] int2array(final int v) {
        byte[] b = new byte[4];
        b[0] = (byte) ((v >>> 24) & 0xFF);
        b[1] = (byte) ((v >>> 16) & 0xFF);
        b[2] = (byte) ((v >>>  8) & 0xFF);
        b[3] = (byte) ( v         & 0xFF);
        return b;
    }

    @Override
    public final void writeLong(final long v) throws IOException {
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

    @Override
    public final void write(final byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    private final static byte cr = 13;
    private final static byte lf = 10;
    
    @Override
    public final void writeLine(final String line) throws IOException {
        final byte[] b = new byte[line.length() + 2];
        System.arraycopy(UTF8.getBytes(line), 0, b, 0, line.length());
        b[b.length - 2] = cr;
        b[b.length - 1] = lf;
        this.write(b);
    }

    public final void writeLine(final byte[] line) throws IOException {
        final byte[] b = new byte[line.length + 2];
        System.arraycopy(line, 0, b, 0, line.length);
        b[b.length - 2] = cr;
        b[b.length - 1] = lf;
        this.write(b);
    }
    
    @Override
    public final void writeMap(final Map<String, String> map, final String comment) throws IOException {
        this.seek(0);
        final Iterator<Map.Entry<String, String>> iter = map.entrySet().iterator();
        Map.Entry<String, String> entry;
        final ByteBuffer bb = new ByteBuffer(map.size() * 40);
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

    @Override
    public final HashMap<String, String> readMap() throws IOException {
        this.seek(0);
        final byte[] b = readFully();
        final BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(b)));
        final HashMap<String, String> map = new HashMap<String, String>();
        String line;
        int pos;
        while ((line = br.readLine()) != null) { // very slow readLine????
            line = line.trim();
            if ("# EOF".equals(line)) return map;
            if ((line.isEmpty()) || (line.charAt(0) == '#')) continue;
            pos = line.indexOf('=');
            if (pos < 0) continue;
            map.put(line.substring(0, pos), line.substring(pos + 1));
        }
        return map;
    }
    
    @Override
    public final void deleteOnExit() {
        if (this.file != null) this.file.deleteOnExit();
    }

}