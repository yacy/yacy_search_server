package de.anomic.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public final class httpdByteCountInputStream extends FilterInputStream {
    
    private static final Object syncObject = new Object();
    private static final HashMap byteCountInfo = new HashMap(2);
    private static long globalByteCount = 0;
    
    private boolean finished = false;
    private long byteCount;
    private String byteCountAccountName = null; 

    /**
     * Constructor of this class
     * @param inputStream the {@link InputStream} to read from
     */
    public httpdByteCountInputStream(InputStream inputStream, String accountName) {
        super(inputStream);
        this.byteCountAccountName = accountName;
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
                return ((Long)byteCountInfo.get(accountName)).longValue();
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
                    lastByteCount = ((Long)byteCountInfo.get(this.byteCountAccountName)).longValue();
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
