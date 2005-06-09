package de.anomic.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class httpdByteCountInputStream extends FilterInputStream {
    
    private static final Object syncObject = new Object();
    private static long globalByteCount = 0;
    
    private boolean finished = false;
    private long byteCount;

    /**
     * Constructor of this class
     * @param inputStream the {@link InputStream} to read from
     */
    public httpdByteCountInputStream(InputStream inputStream) {
        super(inputStream);
    }
    
    /**
     * Constructor of this class
     * @param inputStream the {@link InputStream} to read from
     * @param initByteCount to initialize the bytecount with a given value
     */
    public httpdByteCountInputStream(InputStream inputStream, int initByteCount) {
        super(inputStream);
        this.byteCount = initByteCount;
    }  
    
    public int read(byte[] b) throws IOException {
        int readCount = super.read(b);
        this.byteCount += readCount;
        return readCount;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int readCount = super.read(b, off, len);
        this.byteCount += readCount;
        return readCount;
    }

    public int read() throws IOException {
        this.byteCount++;
        return super.read();
    }

    public long getCount() {
        return this.byteCount;
    }
    
    public static long getGlobalCount() {
        synchronized (syncObject) {
            return globalByteCount;
        }
    }
    
    public void finish() throws IOException {
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
