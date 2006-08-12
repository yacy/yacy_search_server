// kelondroRAIOChunks.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
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

import java.io.IOException;

public final class kelondroRAIOChunks extends kelondroAbstractIOChunks implements kelondroIOChunks {

    protected kelondroRA ra;
    
    public kelondroRAIOChunks(kelondroRA ra, String name) {
        this.name = name;
        this.ra = ra;
    }

    public long length() throws IOException {
        return ra.length();
    }
    
    public int read(long pos, byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        synchronized (this.ra) {
            this.ra.seek(pos);
            long available = ra.available();
            if (available >= len) {
                return ra.read(b, off, len);
            } else if (available == 0) {
                return -1;
            } else {
                return ra.read(b, off, (int) available);
            }
        }
    }

    public void write(long pos, byte[] b, int off, int len) throws IOException {
        synchronized (this.ra) {
            this.ra.seek(pos);
            this.ra.write(b, off, len);
        }
    }

    public void commit() throws IOException {
        // do nothing here
        // this method is used to flush write-buffers
    }
    
    public void close() throws IOException {
        if (this.ra != null) this.ra.close();
        this.ra = null;
    }

    protected void finalize() throws Throwable {
        if (this.ra != null) this.close();
        super.finalize();
    }

}
