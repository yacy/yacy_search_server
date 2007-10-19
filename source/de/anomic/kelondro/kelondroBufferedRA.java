// kelondroBufferedRA.java 
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 13.09.2005
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
import de.anomic.server.serverByteBuffer;

public class kelondroBufferedRA extends kelondroAbstractRA implements kelondroRA {

    private serverByteBuffer sbb;
    private long pos;
    
    public kelondroBufferedRA() {
        sbb = new serverByteBuffer();
        pos = 0;
    }
    
    public kelondroBufferedRA(serverByteBuffer bb) {
        sbb = bb;
        pos = 0;
    }
    
    public serverByteBuffer getBuffer() {
        return this.sbb;
    }
    
    public long available() throws IOException {
        return Long.MAX_VALUE - sbb.length();
    }

    public void close() throws IOException {
        sbb = null;
    }

    public long length() throws IOException {
        return sbb.length();
    }

    public int read() throws IOException {
        return 0xff & sbb.byteAt((int) pos++);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        byte[] g = sbb.getBytes((int) pos, len);
        pos += g.length;
        System.arraycopy(g, 0, b, off, g.length);
        return g.length;
    }

    public void seek(long pos) throws IOException {
        this.pos = pos;
    }

    public void write(int b) throws IOException {
        this.sbb.overwrite((int) pos, b);
        pos++;
    }

    public void write(byte[] b, int off, int len) throws IOException {
        this.sbb.overwrite((int) pos, b, off, len);
        pos += len;
    }

}
