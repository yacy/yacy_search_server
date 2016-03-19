package SevenZip.Common;

import java.io.IOException;
import java.io.InputStream;

public class InBuffer {
	
    int _bufferPos;
    int _bufferLimit;
    byte [] _bufferBase;
    InputStream _stream = null; // CMyComPtr<ISequentialInStream>
    long _processedSize;
    int _bufferSize;
    boolean _wasFinished;
    
    public InBuffer() {
        
    }
    // ~CInBuffer() { Free(); }
    
    public void Create(int bufferSize) {
        final int kMinBlockSize = 1;
        if (bufferSize < kMinBlockSize)
            bufferSize = kMinBlockSize;
        if (_bufferBase != null && _bufferSize == bufferSize)
            return ;
        Free();
        _bufferSize = bufferSize;
        _bufferBase = new byte[bufferSize];
    }
    void Free() {
        _bufferBase = null;
    }
    
    public void SetStream(InputStream stream) { // ISequentialInStream
        _stream = stream;
    }
    public void Init() {
        _processedSize = 0;
        _bufferPos = 0; //  = _bufferBase;
        _bufferLimit = 0; // _buffer;
        _wasFinished = false;
    }
    public void ReleaseStream() throws IOException {
        if (_stream != null) _stream.close(); // _stream.Release();
        _stream = null;
    }
    
    public int read() throws IOException {
        if(_bufferPos >= _bufferLimit)
            return ReadBlock2();
        return _bufferBase[_bufferPos++] & 0xFF;
    }
    
    public boolean ReadBlock() throws IOException {
        if (_wasFinished)
            return false;
        _processedSize += _bufferPos; // (_buffer - _bufferBase);
        
        int  numProcessedBytes = _stream.read(_bufferBase, 0,_bufferSize);
        if (numProcessedBytes == -1) numProcessedBytes = 0; // EOF
        
        _bufferPos = 0; // _bufferBase;
        _bufferLimit = numProcessedBytes; // _buffer + numProcessedBytes;
        _wasFinished = (numProcessedBytes == 0);
        return (!_wasFinished);
    }
    
    public int ReadBlock2() throws IOException {
        if(!ReadBlock())
            return -1; // 0xFF;
        return _bufferBase[_bufferPos++] & 0xFF;
    }
    
    public long GetProcessedSize() { return _processedSize + (_bufferPos); }
    public boolean WasFinished() { return _wasFinished; }
}
