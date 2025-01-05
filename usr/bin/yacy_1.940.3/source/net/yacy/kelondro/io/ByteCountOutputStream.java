//httpByteCountOutputStream.java 
//-----------------------
//(C) by Michael Peter Christen; mc@yacy.net
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

package net.yacy.kelondro.io;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
//import java.util.HashMap;

public final class ByteCountOutputStream extends BufferedOutputStream {
    
//    private final static Object syncObject = new Object();
//    private static long globalByteCount = 0;    
//    private final static HashMap<String, Long> byteCountInfo = new HashMap<String, Long>(2);
    
    protected long byteCount;
    protected String byteCountAccountName = null; 
    private boolean finished = false;    
    
    /**
     * Constructor of this class
     * @param outputStream the {@link OutputStream} to write to
     */
    public ByteCountOutputStream(final OutputStream outputStream) {
        this(outputStream,null);
    }
    
    public ByteCountOutputStream(final OutputStream outputStream, final String accountName) {
        this(outputStream,0,accountName);
    }    
    
    /**
     * Constructor of this class
     * @param outputStream the {@link OutputStream} to write to
     * @param initByteCount to initialize the bytecount with a given value
     */
    public ByteCountOutputStream(final OutputStream outputStream, final long initByteCount, final String accountName) {
        super(outputStream);
        this.byteCount = initByteCount;
        this.byteCountAccountName = accountName;
    }    

    /** @see java.io.OutputStream#write(byte[]) */
    @Override
    public final void write(final byte[] b) throws IOException {
        super.write(b);
        this.byteCount += b.length;
    }

    /** @see java.io.OutputStream#write(byte[], int, int) */
    @Override
    public final synchronized void write(final byte[] b, final int off, final int len) throws IOException {        
        super.write(b, off, len);
        this.byteCount += len;
    }

    /** @see java.io.OutputStream#write(int) */
    @Override
    public final synchronized void write(final int b) throws IOException {
        super.write(b);
        this.byteCount++;
    }

    /**
     * The number of bytes that have passed through this stream.
     * @return the number of bytes accumulated
     */
    public final long getCount() {
        return this.byteCount;
    }
    
    public String getAccountName() {
        return this.byteCountAccountName;
    }    
    
//    public final static long getGlobalCount() {
//        synchronized (syncObject) {
//            return globalByteCount;
//        }
//    }
    
//    public final static long getAccountCount(final String accountName) {
//        synchronized (syncObject) {
//            if (byteCountInfo.containsKey(accountName)) {
//                return (byteCountInfo.get(accountName)).longValue();
//            }
//            return 0;
//        }
//    }    
    
//    public final static void resetCount() {
//        synchronized (syncObject) {
//            globalByteCount = 0;
//            byteCountInfo.clear();
//        }
//    }    
    
    public final void finish() {
        if (this.finished) return;
        
        this.finished = true;
        ByteCount.addAccountCount(this.byteCountAccountName, this.byteCount);
//        synchronized (syncObject) {
//            globalByteCount += this.byteCount;
//            if (this.byteCountAccountName != null) {
//                long lastByteCount = 0;
//                if (byteCountInfo.containsKey(this.byteCountAccountName)) {
//                    lastByteCount = (byteCountInfo.get(this.byteCountAccountName)).longValue();
//                }
//                lastByteCount += this.byteCount;
//                byteCountInfo.put(this.byteCountAccountName, Long.valueOf(lastByteCount));
//            }
//            
//        }            
    }
    
}
