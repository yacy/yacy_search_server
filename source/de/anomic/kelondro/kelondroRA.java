// kelondroRA.java 
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


/*
  The random access interface for the kelondro database.
  kelondro stores data always through the kelondroRecords class,
  which in turn also needs a random access file or similar
  to store the database structure. To provide more than
  ony file - random-access, we need an abstract interface.
*/

package de.anomic.kelondro;

import java.io.IOException;
import java.util.Map;

public interface kelondroRA {

    // logging support
    public String name();
    
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
    public Map<String, String> readMap() throws IOException;

    public void writeArray(byte[] b) throws IOException;
    public byte[] readArray() throws IOException;
}
