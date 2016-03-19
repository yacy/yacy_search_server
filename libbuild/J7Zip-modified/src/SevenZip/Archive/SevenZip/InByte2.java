package SevenZip.Archive.SevenZip;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

class InByte2 {
    byte [] _buffer;
    int _size;
    int _pos;
    
    public InByte2() {
    }
    
    public InByte2(byte [] buffer, int size) {
        _buffer = buffer;
        _size = size;
        _pos = 0;
    }
    
    public int ReadByte() throws IOException {
        if(_pos >= _size)
            throw new IOException("CInByte2 - Can't read stream");
        return (_buffer[_pos++] & 0xFF);
    }
    
    int ReadBytes2(byte[] data, int size) {
        int processedSize;
        for(processedSize = 0; processedSize < size && _pos < _size; processedSize++)
            data[processedSize] = _buffer[_pos++];
        return processedSize;
    }
    
    boolean ReadBytes(byte[] data, int size) {
        int processedSize = ReadBytes2(data, size);
        return (processedSize == size);
    }
    
    int readBytes(ByteArrayOutputStream baos, int size) {
    	int processedSize;
    	for (processedSize = 0; processedSize < size && _pos < _size; processedSize++)
    		baos.write(_buffer[_pos++]);
    	return processedSize;
    }
    
    int GetProcessedSize() { return _pos; }
}
