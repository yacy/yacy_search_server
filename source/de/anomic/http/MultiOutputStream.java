/**
 * MultiOutputStream.java
 * @since 26.08.2008
 */
package de.anomic.http;

import java.io.IOException;
import java.io.OutputStream;

/**
 * writes to multiple {link OutputStream}s (parallel)
 * 
 * @author daniel
 * 
 */
class MultiOutputStream extends OutputStream {

    private final OutputStream[] streams;

    /**
     * creates a new MultiOutputStream
     * 
     * @param streams
     */
    public MultiOutputStream(final OutputStream[] streams) {
        super();
        // make a copy to avoid external modifications
        this.streams = new OutputStream[streams.length]; 
        System.arraycopy(streams, 0, this.streams, 0, streams.length);
    }

    /**
     * writes the byte to each of the streams
     * 
     * @see java.io.OutputStream#write(int)
     */
    @Override
    public void write(int b) throws IOException {
        for(OutputStream stream: streams) {
            stream.write(b);
        }
    }

}
