package Common;

import java.io.InputStream;

public class LimitedSequentialInStream extends InputStream {
    final InputStream _stream; // ISequentialInStream
    final long _size;
    long _pos;
    boolean _wasFinished;
    
    public LimitedSequentialInStream(InputStream stream, long streamSize) {
        _stream = stream;
        _size = streamSize;
        _pos = 0;
        _wasFinished = false;
    }
    
    /*
    public void SetStream(InputStream stream) { // ISequentialInStream
        _stream = stream;
    }
    
    public void Init(long streamSize) {
        _size = streamSize;
        _pos = 0;
        _wasFinished = false;
    }*/
    
    public int read() throws java.io.IOException {
        int ret = _stream.read();
        if (ret == -1) _wasFinished = true;
        return ret;
    }
    
    public int read(byte [] data,int off, int size) throws java.io.IOException {
        long sizeToRead2 = (_size - _pos);
        if (size < sizeToRead2) sizeToRead2 = size;
        
        int sizeToRead = (int)sizeToRead2;
        
        if (sizeToRead > 0) {
            int realProcessedSize = _stream.read(data, off, sizeToRead);
            if (realProcessedSize == -1) {
                _wasFinished = true;
                return -1;
            }
            _pos += realProcessedSize;
            return realProcessedSize;
        }
        
        return -1; // EOF
    }
}

