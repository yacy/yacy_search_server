// kelondroRAIOChunks.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 11.12.2004
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

public final class kelondroRAIOChunks extends kelondroAbstractIOChunks implements kelondroIOChunks {

    protected kelondroRA ra;
    
    public kelondroRAIOChunks(final kelondroRA ra, final String name) {
        this.name = name;
        this.ra = ra;
    }
    
    public kelondroRA getRA() {
    	return this.ra;
    }

    public synchronized long length() throws IOException {
        return ra.length();
    }
    
    public synchronized int read(final long pos, final byte[] b, final int off, final int len) throws IOException {
        if (len == 0) return 0;
        this.ra.seek(pos);
        final long available = ra.available();
        if (available >= len) {
            return ra.read(b, off, len);
        } else if (available == 0) {
            return -1;
        } else {
            return ra.read(b, off, (int) available);
        }
    }

    public synchronized void write(final long pos, final byte[] b, final int off, final int len) throws IOException {
        this.ra.seek(pos);
        this.ra.write(b, off, len);
    }

    public void commit() throws IOException {
        // do nothing here
        // this method is used to flush write-buffers
    }
    
    public synchronized void close() throws IOException {
        if (this.ra != null) this.ra.close();
        this.ra = null;
    }

    protected void finalize() throws Throwable {
        if (this.ra != null) this.close();
        super.finalize();
    }

}
