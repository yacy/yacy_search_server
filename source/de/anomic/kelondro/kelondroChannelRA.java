// kelondroFileRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 05.02.2004
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public final class kelondroChannelRA extends kelondroAbstractRA implements kelondroRA {

    private FileChannel channel;
    private RandomAccessFile raf;
    
    public kelondroChannelRA(final File file) throws IOException, FileNotFoundException {
        this.name = file.getName();
        this.file = file;
        this.raf = new RandomAccessFile(file, "rw");
        this.channel = raf.getChannel();
    }	
    
    public long length() throws IOException {
        return channel.size();
    }
    
    public long available() throws IOException {
        return channel.size() - channel.position();
    }

    public final void readFully(final byte[] b, final int off, final int len) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b);
        channel.read(bb);
    }

    public void write(final byte[] b, final int off, final int len) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b, off, len);
        channel.write(bb);
    }

    public void seek(final long pos) throws IOException {
        channel.position(pos);
    }

    public void close() throws IOException {
        channel.close();
        raf.close();
    }

    protected void finalize() throws Throwable {
        if (channel != null) {
            this.close();
        }
        super.finalize();
    }
}
