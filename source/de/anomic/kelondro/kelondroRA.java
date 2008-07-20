// kelondroRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
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

/*
  The random access interface for the kelondro database.
  kelondro stores data always through the kelondroRecords class,
  which in turn also needs a random access file or similar
  to store the database structure. To provide more than
  ony file - random-access, we need an abstract interface.
*/

package de.anomic.kelondro;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public interface kelondroRA {

    // logging support
    public String name();
    public File   file();
    
    // pseudo-native methods:
    public long length() throws IOException;
    public long available() throws IOException;
    
    public int read() throws IOException;
    public void write(int b) throws IOException;

    public int read(byte[] b, int off, int len) throws IOException;
    public void write(byte[] b, int off, int len) throws IOException;

    public void seek(long pos) throws IOException;
    public void close() throws IOException;

    // derived methods:
    public void readFully(byte[] b, int off, int len) throws IOException;
    public byte[] readFully() throws IOException;
    public byte readByte() throws IOException;
    public void writeByte(int v) throws IOException;

    public short readShort() throws IOException;
    public void writeShort(int v) throws IOException;

    public int readInt() throws IOException;
    public void writeInt(int v) throws IOException;

    public long readLong() throws IOException;
    public void writeLong(long v) throws IOException;

    public void write(byte[] b) throws IOException;

    public void writeLine(String line) throws IOException;
    public String readLine() throws IOException;

    public void writeMap(Map<String, String> props, String comment) throws IOException;
    public HashMap<String, String> readMap() throws IOException;

    public void writeArray(byte[] b) throws IOException;
    public byte[] readArray() throws IOException;
}
