//httpChunkedInputStream.java 
//-----------------------
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
// This file is contributed by Martin Thelian
// last major change: $LastChangedDate$ by $LastChangedBy$
// Revision: $LastChangedRevision$
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.anomic.server.serverByteBuffer;

/**
 * Some parts of this class code was copied from <a href="http://www.devdaily.com/java/jwarehouse/commons-httpclient-2.0/src/java/org/apache/commons/httpclient/ChunkedInputStream.shtml">Apache httpclient Project.</a>
 * @author theli
 */
public final class httpChunkedInputStream extends InputStream {
    
    private static final int READ_CHUNK_STATE_NORMAL = 0;
    private static final int READ_CHUNK_STATE_CR_READ = 1;
    private static final int READ_CHUNK_STATE_IN_EXT_CHUNK = 2;
    private static final int READ_CHUNK_STATE_FINISHED = -1;
    
    private static final char CR = '\r';
    private static final char LF = '\n';
    
    private final InputStream inputStream;
    private int currPos;
    private int currChunkSize;
    private httpHeader httpTrailer;
    
    private boolean beginningOfStream = true;
    private boolean isEOF = false;
    private boolean isClosed = false;
    
    
    public httpChunkedInputStream(InputStream in) throws IOException {
        
        if (in == null)throw new IllegalArgumentException("InputStream must not be null");
        
        this.inputStream = in;
        this.currPos = 0;
    }
    
    public int read() throws IOException {
        
        if (this.isClosed) throw new IOException("Inputstream already closed.");
        if (this.isEOF) return -1;
        
        if (this.currPos >= this.currChunkSize) {
            readNextChunk();
            if (this.isEOF) return -1;
        }
        this.currPos++;
        return this.inputStream.read();
    }
    
    
    public int read (byte[] b, int off, int len) throws IOException {
        if (this.isClosed) throw new IOException("Inputstream already closed.");
        if (this.isEOF) return -1;
        
        if (this.currPos >= this.currChunkSize) {
            readNextChunk();
            if (this.isEOF) return -1;
        }
        len = Math.min(len, this.currChunkSize - this.currPos);
        int count = this.inputStream.read(b, off, len);
        this.currPos += count;
        return count;
    }
    
    public int read (byte[] b) throws IOException {
        return read(b, 0, b.length);
    }
    
    /**
     * Read the CRLF terminator.
     * @throws IOException If an IO error occurs.
     */
    private void readCRLF() throws IOException {
        int cr = this.inputStream.read();
        int lf = this.inputStream.read();
        if ((cr != CR) || (lf != LF)) { 
            throw new IOException("Malformed chunk. CRLF expected but '" + cr + lf + "' found");
        }
    }
    
    
    private void readNextChunk() throws IOException {
        if (!this.beginningOfStream) readCRLF();
        
        this.currChunkSize = readChunkFromStream(this.inputStream);
        this.beginningOfStream = false;
        this.currPos = 0;
        if (this.currChunkSize == 0) {
            this.isEOF = true;
            readTrailer();
        }
    }
    
    
    private void readTrailer() throws IOException {
        BufferedReader reader = null;
        serverByteBuffer bout = null;
        try {
            bout = new serverByteBuffer();
            do {
                int ch;
                while ((ch = this.inputStream.read()) >= 0) {
                    bout.write(ch);
                    if (ch == LF) {
                        break;
                    }
                }            
                if (bout.length() <= 2) break;
            } while(true);
            
            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            reader = new BufferedReader(new InputStreamReader(bin));
            this.httpTrailer = httpHeader.readHttpHeader(reader);
        } finally {
            if (reader != null) try {reader.close();}catch(Exception e){}
            if (bout != null) try {bout.close();}catch(Exception e){}
        }
    }
    
    public httpHeader getTrailer() {
        return this.httpTrailer;
    }
    
    private static int readChunkFromStream(final InputStream in) 
    throws IOException {           
        
        serverByteBuffer baos = new serverByteBuffer();
        int state = 0; 
        while (state != READ_CHUNK_STATE_FINISHED) {
            int b = in.read();
            if (b == -1) throw new IOException("Malformed chunk. Unexpected end");
            
            switch (state) {
            case 0: 
                switch (b) {
                case CR:
                    state = READ_CHUNK_STATE_CR_READ;
                    break;
                case '\"':
                case ';':
                case ' ':
                    state = READ_CHUNK_STATE_IN_EXT_CHUNK;
                    break;
                default:
                    baos.write(b);
                }
                break;
                
            case 1:
                if (b == LF) {
                    state = READ_CHUNK_STATE_FINISHED;
                } else {
                    // this was not CRLF
                    throw new IOException("Malformed chunk. Unexpected enf of chunk. MIssing CR character.");
                }
                break;
                
            case 2:
                switch (b) {
                case CR:
                    state = READ_CHUNK_STATE_CR_READ;
                    break;
                default:
                    break;
                }
                break;
            default: throw new RuntimeException("Malformed chunk. Illegal state.");
            }
        }
        
        
        int result;
        try {
            result = Integer.parseInt(baos.toString().trim(), 16);
        } catch (NumberFormatException e) {
            throw new IOException ("Malformed chunk. Bad chunk size: " + baos.toString());
        }
        return result;
    }
    
    public void close() throws IOException {
        if (!this.isClosed) {
            try {
                if (!this.isEOF) {
                    exhaustInputStream(this);
                }
            } finally {
                this.isEOF = true;
                this.isClosed = true;
            }
        }
    }
    
    
    static void exhaustInputStream(InputStream inStream) throws IOException {
        byte buffer[] = new byte[1024];
        while (inStream.read(buffer) >= 0) {
            ;
        }
    }
}


