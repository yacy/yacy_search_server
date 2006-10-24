//httpContentLengthInputStream.java 
//-----------------------
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
// This file is contributed by Martin Thelian
//
// $LastChangedDate: 2006-09-15 17:01:25 +0200 (Fr, 15 Sep 2006) $
// $LastChangedRevision: 2598 $
// $LastChangedBy: theli $
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

import java.io.IOException;
import java.io.InputStream;

public class httpContentLengthInputStream extends httpdByteCountInputStream {

    private long contentLength;
    private boolean closed = false;
    
    public httpContentLengthInputStream(InputStream inputStream, long contentLength) {
        super(inputStream);
        this.contentLength = contentLength;
    }
    
    public long getContentLength() {
        return this.contentLength;
    }
    
    public boolean isClosed() {
        return this.closed;
    }
    
    /**
     * Closes this input stream.
     * <b>Attention:</b> This does not close the wrapped input stream, because
     * otherwise keep-alive connections would terminate
     */
    public void close() throws IOException {
    	if (!this.closed) {    		
    		try {
    			// read to the end of the stream and throw read bytes away
    			httpChunkedInputStream.exhaustInputStream(this);
    		} finally {
    			this.closed = true;
    		}        	         
    	}
    }
    
    public int read() throws IOException {
        if (this.closed) throw new IOException("Stream already closed.");        
        
        // if we have already finished reading ...
        if (this.byteCount >= this.contentLength) return -1;        
        return super.read();
    }
    
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }
    
    public int read(byte[] buffer, int off, int len) throws IOException {
        if (this.closed) throw new IOException("Stream already closed."); 
        
        // if we have already finished reading ...
        if (this.byteCount >= this.contentLength) return -1; 
        
        // only read until body end
        if (this.byteCount + len > this.contentLength) {
            len = (int) (this.contentLength - this.byteCount);
        }                
        
        return super.read(buffer, off, len);
    }
    
    public long skip(long len) throws IOException {        
        long skipLength = Math.min(len, this.contentLength - this.byteCount);
        return super.skip(skipLength);
    }    

}
