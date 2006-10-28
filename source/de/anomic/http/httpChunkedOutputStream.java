//httpChunkedOutputStream.java 
//-------------------------------------
//part of YACY
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file ist contributed by Martin Thelian
//last major change: 05.09.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.http;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class httpChunkedOutputStream extends FilterOutputStream
{
    private boolean finished = false;
    private static final byte[] crlf = {(byte)13,(byte)10};    
    
    public httpChunkedOutputStream(OutputStream out) {
        super(out);
    }
    
    public void close() throws IOException {
        if (!this.finished) this.finish();
        this.out.close();
    }
    
    public void finish() throws IOException {
        if (!this.finished) {
            this.out.write("0\r\n\r\n".getBytes());
            this.out.flush();
            this.finished = true;
        }
    }
    
    public void write(byte[] b) throws IOException {
        if (this.finished) throw new IOException("ChunkedOutputStream already finalized.");        
        if (b.length == 0) return;
            
        this.out.write(Integer.toHexString(b.length).getBytes());
        this.out.write(crlf);
        this.out.write(b);
        this.out.write(crlf);
        this.out.flush();
    }
    
    public void write(byte[] b, int off, int len) throws IOException {
        if (this.finished) throw new IOException("ChunkedOutputStream already finalized.");
        if (len == 0) return;
        
        this.out.write(Integer.toHexString(len).getBytes());
        this.out.write(crlf);
        this.out.write(b, off, len);
        this.out.write(crlf);
        this.out.flush();
    }
    
    public void write(int b) throws IOException {
        if (this.finished) throw new IOException("ChunkedOutputStream already finalized.");
        
        this.out.write("1".getBytes());
        this.out.write(crlf);
        this.out.write(b);
        this.out.write(crlf);
        this.out.flush();
    }
}
