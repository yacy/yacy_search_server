package de.anomic.http;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class httpdByteCountOutputStream extends BufferedOutputStream {
    
    private static final Object syncObject = new Object();
    private static long globalByteCount = 0;    
    private boolean finished = false;    
    
    private long byteCount;

    /**
     * Constructor of this class
     * @param outputStream the {@link OutputStream} to write to
     */
    public httpdByteCountOutputStream(OutputStream outputStream) {
        super(outputStream);
    }
    
    /**
     * Constructor of this class
     * @param outputStream the {@link OutputStream} to write to
     * @param initByteCount to initialize the bytecount with a given value
     */
    public httpdByteCountOutputStream(OutputStream outputStream, int initByteCount) {
        super(outputStream);
        this.byteCount = initByteCount;
    }    

    /** @see java.io.OutputStream#write(byte[]) */
    public void write(byte[] b) throws IOException {
        super.write(b);
        this.byteCount += b.length;
    }

    /** @see java.io.OutputStream#write(byte[], int, int) */
    public void write(byte[] b, int off, int len) throws IOException {        
        super.write(b, off, len);
        this.byteCount += len;
    }

    /** @see java.io.OutputStream#write(int) */
    public void write(int b) throws IOException {
        super.write(b);
        this.byteCount++;
    }

    /**
     * The number of bytes that have passed through this stream.
     * @return the number of bytes accumulated
     */
    public long getCount() {
        return this.byteCount;
    }
    
    public static long getGlobalCount() {
        synchronized (syncObject) {
            return globalByteCount;
        }
    }
    
    public static void resetCount() {
        synchronized (syncObject) {
            globalByteCount = 0;
        }
    }    
    
    public void finish() {
        if (this.finished) return;
        
        this.finished = true;
        synchronized (syncObject) {
            globalByteCount += this.byteCount;
        }        
    }
    
    protected void finalize() throws Throwable {
        if (!this.finished) 
            finish();
        super.finalize();
    }    
}
