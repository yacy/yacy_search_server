package SevenZip.Archive.Common;


import SevenZip.HRESULT;
import java.io.IOException;
import SevenZip.ICompressCoder;
import SevenZip.ICompressSetOutStream;

/*
  // #ifdef _ST_MODE
  public ICompressSetInStream,
  public ISequentialInStream,
  public ICompressSetOutStream,
  public ISequentialOutStream,
  public IOutStreamFlush,
  // #endif
 */
public class FilterCoder extends java.io.OutputStream implements ICompressCoder , ICompressSetOutStream {
    
    public SevenZip.ICompressFilter Filter = null;
    
    java.io.OutputStream _outStream = null;
    int _bufferPos;  //  UInt32
    
    boolean _outSizeIsDefined;
    long _outSize;
    long _nowPos64;
    
    int   Init() // HRESULT
    {
        _nowPos64 = 0;
        _outSizeIsDefined = false;
        return Filter.Init();
    }
    
    // ICompressCoder
    public void Code(
            java.io.InputStream inStream, // , ISequentialInStream
            java.io.OutputStream outStream, // ISequentialOutStream
            long outSize, SevenZip.ICompressProgressInfo progress) throws java.io.IOException {
        throw new java.io.IOException("Not implemented");
    }
    
    // java.io.OutputStream
    public  void write(int b) {
        throw new UnknownError("FilterCoder write");
    }
    
    public void write(byte b[], int off, int size) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (size < 0) ||
                ((off + size) > b.length) || ((off + size) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (size == 0) {
            return;
        }
        
        if (off != 0) throw new IOException("FilterCoder - off <> 0");
        
        byte [] cur_data = b;
        int     cur_off = 0;
        while(size > 0) {
            int sizeMax = kBufferSize - _bufferPos;
            int sizeTemp = size;
            if (sizeTemp > sizeMax)
                sizeTemp = sizeMax;
            System.arraycopy(cur_data, cur_off,    _buffer, _bufferPos , sizeTemp); // memmove(_buffer + _bufferPos, data, sizeTemp);
            size -= sizeTemp;
            cur_off = cur_off + sizeTemp;
            int endPos = _bufferPos + sizeTemp;
            _bufferPos = Filter.Filter(_buffer, endPos);
            if (_bufferPos == 0) {
                _bufferPos = endPos;
                break;
            }
            if (_bufferPos > endPos) {
                if (size != 0)
                    throw new IOException("FilterCoder - write() : size  <> 0"); // return HRESULT.E_FAIL;
                break;
            }
            
            WriteWithLimit(_outStream, _bufferPos);
            
            int i = 0;
            while(_bufferPos < endPos)
                _buffer[i++] = _buffer[_bufferPos++];
            _bufferPos = i;
        }
        
        // return HRESULT.S_OK;
    }
    
    void WriteWithLimit(java.io.OutputStream outStream, int size) throws IOException {
        if (_outSizeIsDefined) {
            long remSize = _outSize - _nowPos64;
            if (size > remSize)
                size = (int)remSize;
        }
        
        outStream.write(_buffer,0,size);
        
        _nowPos64 += size;
    }
    
    byte [] _buffer;
    
    static final int kBufferSize = 1 << 17;
    public FilterCoder() {
        _buffer = new byte[kBufferSize];
    }
    
    
    // ICompressSetOutStream
    public int SetOutStream(java.io.OutputStream outStream) {
        _bufferPos = 0;
        _outStream = outStream;
        return Init();
    }
    
    public int ReleaseOutStream() throws IOException {
        if (_outStream != null) _outStream.close(); // Release()
        _outStream = null;
        return HRESULT.S_OK;
    }
    
    public void flush() throws IOException {
        if (_bufferPos != 0) {
            int endPos = Filter.Filter(_buffer, _bufferPos);
            if (endPos > _bufferPos) {
                for (; _bufferPos < endPos; _bufferPos++)
                    _buffer[_bufferPos] = 0;
                if (Filter.Filter(_buffer, endPos) != endPos)
                    throw new IOException("FilterCoder - flush() : E_FAIL"); // return HRESULT.E_FAIL;
            }
            _outStream.write(_buffer,0,_bufferPos);
            _bufferPos = 0;
        }
        _outStream.flush();
    }
    
    public void close() throws IOException {
        if (_outStream != null) _outStream.close(); // Release()
        _outStream = null;
    }
    
}
