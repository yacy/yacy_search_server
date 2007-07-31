// ByteArrayIInStream.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// 
// This file ist contributed by Franz Brausse
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

package de.anomic.plasma.parser.sevenzip;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import SevenZip.IInStream;

public class ByteArrayIInStream extends IInStream {
    
    private class SeekableByteArrayInputStream extends ByteArrayInputStream {
        public SeekableByteArrayInputStream(byte[] buf) { super(buf); }
        public SeekableByteArrayInputStream(byte[] buf, int off, int len) { super(buf, off, len); }
        
        public int getPosition() { return super.pos; }
        public void seekRelative(int offset) { seekAbsolute(super.pos + offset); }
        public void seekAbsolute(int offset) {
            if (offset > super.count)
                throw new IndexOutOfBoundsException(Integer.toString(offset));
            super.pos = offset;
        }
    }
    
    private final SeekableByteArrayInputStream sbais;
    
    public ByteArrayIInStream(byte[] buffer) {
        this.sbais = new SeekableByteArrayInputStream(buffer);
    }
    
    public long Seek(long offset, int origin) {
        switch (origin) {
            case STREAM_SEEK_SET: this.sbais.seekAbsolute((int)offset); break;
            case STREAM_SEEK_CUR: this.sbais.seekRelative((int)offset); break;
        }
        return this.sbais.getPosition();
    }
    
    public int read() throws IOException {
        return this.sbais.read();
    }
    
    public int read(byte[] b, int off, int len) throws IOException {
        return this.sbais.read(b, off, len);
    }
}