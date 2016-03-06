// LZ.OutWindow

package SevenZip.Compression.LZ;

import java.io.IOException;

public class OutWindow {
    byte[] _buffer;
    byte[] _buffer2 = null;
    int    _bufferPos2 = 0;
    int _pos;
    int _windowSize = 0;
    int _streamPos;
    java.io.OutputStream _stream;
    long _processedSize;
    
    public void Create(int windowSize) {
        final int kMinBlockSize = 1;
        if (windowSize < kMinBlockSize)
            windowSize = kMinBlockSize;
        
        if (_buffer == null || _windowSize != windowSize)
            _buffer = new byte[windowSize];
        _windowSize = windowSize;
        _pos = 0;
        _streamPos = 0;
    }
    
    public void SetStream(java.io.OutputStream stream) throws IOException {
        ReleaseStream();
        _stream = stream;
    }
    
    public void ReleaseStream() throws IOException {
        Flush();
        _stream = null;
    }
    
    public void SetMemStream(byte [] d) {
        _buffer2 = d;
        _bufferPos2 = 0;
    }
    
    public void Init() {
        Init(false);
    }
    public void Init(boolean solid) {
        _processedSize = 0;
        if (!solid) {
            _streamPos = 0;
            _pos = 0;
        }
    }
    
    public void Flush() throws IOException {
        int size = _pos - _streamPos;
        if (size == 0)
            return;
        if (_stream != null) _stream.write(_buffer, _streamPos, size);
        if (_buffer2 != null) {
            System.arraycopy(_buffer, _streamPos, _buffer2, _bufferPos2, size);
            _bufferPos2 += size;
        }
        if (_pos >= _windowSize)
            _pos = 0;
        _streamPos = _pos;
    }
    
    public void CopyBlock(int distance, int len) throws IOException {
        int pos = _pos - distance - 1;
        if (pos < 0)
            pos += _windowSize;
        for (; len != 0; len--) {
            if (pos >= _windowSize)
                pos = 0;
            _buffer[_pos++] = _buffer[pos++];
            _processedSize++;
            if (_pos >= _windowSize)
                Flush();
        }
    }
    
    public void PutByte(byte b) throws IOException {
        _buffer[_pos++] = b;
        _processedSize++;
        if (_pos >= _windowSize)
            Flush();
    }
    
    public void WriteByte(int b)  throws IOException {
        _buffer[_pos++] = (byte)b;
        _processedSize++;
        if (_pos >= _windowSize)
            Flush();
    }
    
    public byte GetByte(int distance) {
        int pos = _pos - distance - 1;
        if (pos < 0)
            pos += _windowSize;
        return _buffer[pos];
    }
    
    public long GetProcessedSize() {
        return _processedSize;
    }
}
