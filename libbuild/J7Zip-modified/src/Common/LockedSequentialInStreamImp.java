package Common;

import java.io.InputStream;

public class LockedSequentialInStreamImp extends InputStream {
    final LockedInStream _lockedInStream;
    long _pos;
    
    public LockedSequentialInStreamImp(LockedInStream lockedInStream, long startPos) {
        _lockedInStream = lockedInStream;
        _pos = startPos;
    }
    
    /*
    public void Init(LockedInStream lockedInStream, long startPos) {
        _lockedInStream = lockedInStream;
        _pos = startPos;
    }*/
    
    public int read() throws java.io.IOException {
        throw new java.io.IOException("LockedSequentialInStreamImp : read() not implemented");
        /*
        int ret = _lockedInStream.read(_pos);
        if (ret == -1) return -1; // EOF
         
        _pos += 1;
         
        return ret;
         */
    }
    
    public int read(byte [] data, int off, int size) throws java.io.IOException {
        int realProcessedSize = _lockedInStream.read(_pos, data,off, size);
        if (realProcessedSize == -1) return -1; // EOF
        
        _pos += realProcessedSize;
        
        return realProcessedSize;
    }
    
}
