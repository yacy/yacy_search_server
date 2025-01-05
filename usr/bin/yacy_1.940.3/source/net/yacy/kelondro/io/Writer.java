// Writer.java 
// -----------------------
// (C) 2004 by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 09.02.2004
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public interface Writer extends Reader {

    // pseudo-native methods:
    public void setLength(long length) throws IOException;
    public void write(byte[] b, int off, int len) throws IOException;

    // derived methods:
    public void writeShort(int v) throws IOException;
    public void writeInt(int v) throws IOException;
    public void writeLong(long v) throws IOException;
    public void write(byte[] b) throws IOException;
    public void writeLine(String line) throws IOException;
    
    public void writeMap(Map<String, String> props, String comment) throws IOException;
    public HashMap<String, String> readMap() throws IOException;

    public void deleteOnExit();
}
