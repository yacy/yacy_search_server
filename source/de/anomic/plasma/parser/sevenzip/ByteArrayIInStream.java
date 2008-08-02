// ByteArrayIInStream.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// 
// This file ist contributed by Franz Brausze
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

package de.anomic.plasma.parser.sevenzip;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import SevenZip.IInStream;

public class ByteArrayIInStream extends IInStream {
    
    private class SeekableByteArrayInputStream extends ByteArrayInputStream {
        public SeekableByteArrayInputStream(final byte[] buf) { super(buf); }
        public SeekableByteArrayInputStream(final byte[] buf, final int off, final int len) { super(buf, off, len); }
        
        public int getPosition() { return super.pos; }
        public void seekRelative(final int offset) { seekAbsolute(super.pos + offset); }
        public void seekAbsolute(final int offset) {
            if (offset > super.count)
                throw new IndexOutOfBoundsException(Integer.toString(offset));
            super.pos = offset;
        }
    }
    
    private final SeekableByteArrayInputStream sbais;
    
    public ByteArrayIInStream(final byte[] buffer) {
        this.sbais = new SeekableByteArrayInputStream(buffer);
    }
    
    public long Seek(final long offset, final int origin) {
        switch (origin) {
            case STREAM_SEEK_SET: this.sbais.seekAbsolute((int)offset); break;
            case STREAM_SEEK_CUR: this.sbais.seekRelative((int)offset); break;
        }
        return this.sbais.getPosition();
    }
    
    public int read() throws IOException {
        return this.sbais.read();
    }
    
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return this.sbais.read(b, off, len);
    }
}