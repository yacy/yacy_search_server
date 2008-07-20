// kelondroIOChunks.java 
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

public interface kelondroIOChunks {

    // logging support
    public String name();
    
    // reference handling
    public kelondroRA getRA();
    
    // pseudo-native methods:
    public long length() throws IOException;
    public int read(long pos, byte[] b, int off, int len) throws IOException;
    public void write(long pos, byte[] b, int off, int len) throws IOException;
    public void commit() throws IOException;
    public void close() throws IOException;

    // derived methods:
    public void readFully(long pos, byte[] b, int off, int len) throws IOException;
    public byte readByte(long pos) throws IOException;
    public void writeByte(long pos, int v) throws IOException;

    public short readShort(long pos) throws IOException;
    public void writeShort(long pos, int v) throws IOException;

    public int readInt(long pos) throws IOException;
    public void writeInt(long pos, int v) throws IOException;

    public long readLong(long pos) throws IOException;
    public void writeLong(long pos, long v) throws IOException;

    public void write(long pos, byte[] b) throws IOException;

    public kelondroProfile profile();
}
