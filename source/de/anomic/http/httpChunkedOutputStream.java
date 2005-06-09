package de.anomic.http;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class httpChunkedOutputStream extends FilterOutputStream
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
