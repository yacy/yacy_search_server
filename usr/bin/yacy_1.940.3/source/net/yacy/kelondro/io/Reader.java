// Reader.java 
// -----------------------
// (C) 2009 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 09.09.2009
//
//  $LastChangedDate$
//  $LastChangedRevision$
//  $LastChangedBy$
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

public interface Reader {

	// logging support
    public String name();
    public File   file();
    
    // pseudo-native methods:
    public long length() throws IOException;
    public long available() throws IOException;
    
    public void readFully(byte[] b, int off, int len) throws IOException;
    
    public void seek(long pos) throws IOException;
    public void close() throws IOException;

    // derived methods:
    public byte[] readFully() throws IOException;
    
    public short readShort() throws IOException;
    public int readInt() throws IOException;
    public long readLong() throws IOException;
    
}
