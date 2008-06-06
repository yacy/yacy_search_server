//httpByteCountinputStream.java 
//-----------------------
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
// This file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class httpdByteCountInputStream extends FilterInputStream {
    
    private static final Object syncObject = new Object();
    private static final HashMap<String, Long> byteCountInfo = new HashMap<String, Long>(2);
    private static long globalByteCount = 0;
    
    private boolean finished = false;
    protected long byteCount;
    private String byteCountAccountName = null; 

    protected httpdByteCountInputStream(InputStream inputStream) {
        this(inputStream,null);
    }
    
    /**
     * Constructor of this class
     * @param inputStream the {@link InputStream} to read from
     */
    public httpdByteCountInputStream(InputStream inputStream, String accountName) {
        this(inputStream,0,accountName);
    }
    
    /**
     * Constructor of this class
     * @param inputStream the {@link InputStream} to read from
     * @param initByteCount to initialize the bytecount with a given value
     */
    public httpdByteCountInputStream(InputStream inputStream, int initByteCount, String accountName) {
        super(inputStream);
        this.byteCount = initByteCount;
        this.byteCountAccountName = accountName;
    }  
    
    public int read(byte[] b) throws IOException {
        int readCount = super.read(b);
        if (readCount > 0) this.byteCount += readCount;
        return readCount;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        try {
        int readCount = super.read(b, off, len);
        if (readCount > 0) this.byteCount += readCount;
        return readCount;
        } catch (IOException e) {
            throw new IOException(e.getMessage() + "; b.length = " + b.length + ", off = " + off + ", len = " + len);
        }
    }

    public int read() throws IOException {
        this.byteCount++;
        return super.read();
    }
    
    public long skip(long len) throws IOException {
        long skipCount = super.skip(len);
        if (skipCount > 0) this.byteCount += skipCount; 
        return skipCount;
    }

    public long getCount() {
        return this.byteCount;
    }
    
    public String getAccountName() {
        return this.byteCountAccountName;
    }
    
    public static long getGlobalCount() {
        synchronized (syncObject) {
            return globalByteCount;
        }
    }
    
    public static long getAccountCount(String accountName) {
        synchronized (syncObject) {
            if (byteCountInfo.containsKey(accountName)) {
                return (byteCountInfo.get(accountName)).longValue();
            }
            return 0;
        }
    }
    
    public void close() throws IOException {
        super.close();
        this.finish();
    }
    
    public void finish() {
        if (this.finished) return;
        
        this.finished = true;
        synchronized (syncObject) {
            globalByteCount += this.byteCount;
            if (this.byteCountAccountName != null) {
                long lastByteCount = 0;
                if (byteCountInfo.containsKey(this.byteCountAccountName)) {
                    lastByteCount = byteCountInfo.get(this.byteCountAccountName).longValue();
                }
                lastByteCount += this.byteCount;
                byteCountInfo.put(this.byteCountAccountName,new Long(lastByteCount));
            }
            
        }        
    }
    
    public static void resetCount() {
        synchronized (syncObject) {
            globalByteCount = 0;
            byteCountInfo.clear();
        }
    }
    
    protected void finalize() throws Throwable {
        if (!this.finished) 
            finish();
        super.finalize();
    }
}
