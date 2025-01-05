//httpByteCountinputStream.java
//-----------------------
//(C) by Michael Peter Christen; mc@yacy.net
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

package net.yacy.kelondro.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.yacy.cora.util.ConcurrentLog;

public final class ByteCountInputStream extends FilterInputStream {

//    private final static Object syncObject = new Object();
//    private final static HashMap<String, Long> byteCountInfo = new HashMap<String, Long>(2);
//    private static long globalByteCount = 0;

    private boolean finished = false;
    protected long byteCount;
    private String byteCountAccountName = null;

    protected ByteCountInputStream(final InputStream inputStream) {
        this(inputStream, null);
    }

    /**
     * Constructor of this class
     * @param inputStream the {@link InputStream} to read from
     */
    public ByteCountInputStream(final InputStream inputStream, final String accountName) {
        this(inputStream,0,accountName);
    }

    /**
     * Constructor of this class
     * @param inputStream the {@link InputStream} to read from
     * @param initByteCount to initialize the bytecount with a given value
     */
    public ByteCountInputStream(final InputStream inputStream, final int initByteCount, final String accountName) {
        super(inputStream);
        this.byteCount = initByteCount;
        this.byteCountAccountName = accountName;
    }

    @Override
    public final int read(final byte[] b) throws IOException {
        final int readCount = super.read(b);
        if (readCount > 0) this.byteCount += readCount;
        return readCount;
    }

    @Override
    public final int read(final byte[] b, final int off, final int len) throws IOException {
        try {
        final int readCount = super.read(b, off, len);
        if (readCount > 0) this.byteCount += readCount;
        return readCount;
        } catch (final IOException e) {
            throw new IOException(e.getMessage() + "; b.length = " + b.length + ", off = " + off + ", len = " + len);
        }
    }

    @Override
    public final int read() throws IOException {
        this.byteCount++;
        return super.read();
    }

    @Override
    public final long skip(final long len) throws IOException {
        final long skipCount = super.skip(len);
        if (skipCount > 0) this.byteCount += skipCount;
        return skipCount;
    }

    public final long getCount() {
        return this.byteCount;
    }

    public final String getAccountName() {
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

    @Override
    public final synchronized void close() throws IOException {
        try {
            super.close();
        } catch (final OutOfMemoryError e) {
            ConcurrentLog.logException(e);
        }
        this.finish();
    }

    public final void finish() {
        if (this.finished) return;

        this.finished = true;
        ByteCount.addAccountCount(this.byteCountAccountName, this.byteCount);
//        synchronized (syncObject) {
//            globalByteCount += this.byteCount;
//            if (this.byteCountAccountName != null) {
//                long lastByteCount = 0;
//                if (byteCountInfo.containsKey(this.byteCountAccountName)) {
//                    lastByteCount = byteCountInfo.get(this.byteCountAccountName).longValue();
//                }
//                lastByteCount += this.byteCount;
//                byteCountInfo.put(this.byteCountAccountName, Long.valueOf(lastByteCount));
//            }
//
//        }
    }

//    public final static void resetCount() {
//        synchronized (syncObject) {
//            globalByteCount = 0;
//            byteCountInfo.clear();
//        }
//    }
}
