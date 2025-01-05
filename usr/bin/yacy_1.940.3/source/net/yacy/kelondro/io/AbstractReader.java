// AbstractReader.java
// -------------------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 09.09.2009
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

import java.io.File;
import java.io.IOException;


public abstract class AbstractReader implements Reader {

	// logging support
    protected String name = null;
    protected File file = null;
    @Override
    public String name() {
        return name;
    }
    @Override
    public File file() {
        return file;
    }

    // pseudo-native methods:
    @Override
    abstract public void readFully(byte[] b, int off, int len) throws IOException;
    @Override
    abstract public long length() throws IOException;
    @Override
    abstract public long available() throws IOException;
    @Override
    abstract public void seek(long pos) throws IOException;
    @Override
    abstract public void close() throws IOException;

    
    // derived methods:
    @Override
    public final byte[] readFully() throws IOException {
        long a = this.available();
        if (a <= 0) return null;
        if (a > Integer.MAX_VALUE) throw new IOException("available too large for a single array");
        final byte[] buffer = new byte[(int) a];
        this.readFully(buffer, 0, (int) a);
        return buffer;
    }

    @Override
    public final short readShort() throws IOException {
        byte[] b = new byte[2];
        this.readFully(b, 0, 2);
        //if ((b[0] | b[1]) < 0) throw new IOException("kelondroAbstractRA.readInt: wrong values; ch1=" + (b[0] & 0xFF) + ", ch2=" + (b[1] & 0xFF));
        return (short) (((b[0] & 0xFF) << 8) | (b[1] & 0xFF));
    }
    
    @Override
    public final int readInt() throws IOException {
        byte[] b = new byte[4];
        this.readFully(b, 0, 4);
        //if ((b[0] | b[1] | b[2] | b[3]) < 0) throw new IOException("kelondroAbstractRA.readInt: wrong values; ch1=" + (b[0] & 0xFF) + ", ch2=" + (b[1] & 0xFF) + ", ch3=" + (b[2] & 0xFF) + ", ch4=" + (b[3] & 0xFF));
        return (((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF));
    }

    @Override
    public final long readLong() throws IOException {
        return ((long) (readInt()) << 32) | (readInt() & 0xFFFFFFFFL);
    }

}
