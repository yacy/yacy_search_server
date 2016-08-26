package SevenZip.Archive.Common;

import java.io.IOException;
import java.io.OutputStream;

import Common.CRC;

public class OutStreamWithCRC extends OutputStream {
    
    private OutputStream _stream;
    private long _size;
    private CRC _crc = new CRC();
    private boolean _calculateCrc;
    
    public void write(int b) throws IOException {
        throw new IOException("OutStreamWithCRC - write() not implemented");
    }
    
    public void write(byte [] data,int off, int  size) throws IOException {
        if (_stream != null)
            _stream.write(data, off,size);
        if (_calculateCrc)
            _crc.Update(data, off, size);
        
        _size += size;
    }
    
    public void setStream(OutputStream stream) { _stream = stream; }
    
    public void reset() { reset(true); }
    
    public void reset(boolean calculateCrc) {
        _size = 0;
        _calculateCrc = calculateCrc;
        _crc.Init();
    }
    
    public void releaseStream() throws IOException {
        // _stream.Release();
        if (_stream != null) _stream.close();
        _stream = null;
    }
    
    public long getSize()  {
        return _size;
    }
    
    public int getCRC()  {
        return _crc.GetDigest();
    }
    
    public void resetCRC() {
        _crc.Init();
    }
}
