package SevenZip.Compression.Branch;

import SevenZip.HRESULT;
import SevenZip.ICompressFilter;

public class BCJ_x86_Decoder implements ICompressFilter {
    
    // struct CBranch86 - begin
    int  [] _prevMask = new int[1];  // UInt32
    int  [] _prevPos = new int[1]; // UInt32
    void x86Init() {
        _prevMask[0] = 0;
        _prevPos[0] = -5;
    }
    // struct CBranch86 - end
        
    static final boolean [] kMaskToAllowedStatus = {true, true, true, false, true, false, false, false };
    
    static final int [] kMaskToBitNumber = {0, 1, 2, 2, 3, 3, 3, 3};
    
    static final boolean Test86MSByte(int b) { return ((b) == 0 || (b) == 0xFF); }
    
    static final int x86_Convert(byte [] buffer, int endPos, int nowPos,
            int [] prevMask, int [] prevPos, boolean encoding) {
        int bufferPos = 0;
        int limit;
        
        if (endPos < 5)
            return 0;
        
        if (nowPos - prevPos[0] > 5)
            prevPos[0] = nowPos - 5;
        
        limit = endPos - 5;
        while(bufferPos <= limit) {
            int b = (buffer[bufferPos] & 0xFF);
            int offset;
            if (b != 0xE8 && b != 0xE9) {
                bufferPos++;
                continue;
            }
            offset = (nowPos + bufferPos - prevPos[0]);
            prevPos[0] = (nowPos + bufferPos);
            if (offset > 5)
                prevMask[0] = 0;
            else {
                for (int i = 0; i < offset; i++) {
                    prevMask[0] &= 0x77;
                    prevMask[0] <<= 1;
                }
            }
            b = (buffer[bufferPos + 4] & 0xFF);
            if (Test86MSByte(b) && kMaskToAllowedStatus[(prevMask[0] >> 1) & 0x7] &&
                    (prevMask[0] >>> 1) < 0x10) {
                int src =
                        ((int)(b) << 24) |
                        ((int)(buffer[bufferPos + 3] & 0xFF) << 16) |
                        ((int)(buffer[bufferPos + 2] & 0xFF) << 8) |
                        (buffer[bufferPos + 1] & 0xFF);
                
                int dest;
                for (;;) {
                    int index;
                    if (encoding)
                        dest = (nowPos + bufferPos + 5) + src;
                    else
                        dest = src - (nowPos + bufferPos + 5);
                    if (prevMask[0] == 0)
                        break;
                    index = kMaskToBitNumber[prevMask[0] >>> 1];
                    b = (int)((dest >> (24 - index * 8)) & 0xFF);
                    if (!Test86MSByte(b))
                        break;
                    src = dest ^ ((1 << (32 - index * 8)) - 1);
                }
                buffer[bufferPos + 4] = (byte)(~(((dest >> 24) & 1) - 1));
                buffer[bufferPos + 3] = (byte)(dest >> 16);
                buffer[bufferPos + 2] = (byte)(dest >> 8);
                buffer[bufferPos + 1] = (byte)dest;
                bufferPos += 5;
                prevMask[0] = 0;
            } else {
                bufferPos++;
                prevMask[0] |= 1;
                if (Test86MSByte(b))
                    prevMask[0] |= 0x10;
            }
        }
        return bufferPos;
    }
    
    public int SubFilter(byte [] data, int size) {
        return x86_Convert(data, size, _bufferPos, _prevMask, _prevPos, false);
    }
    
    public void SubInit() {
        x86Init();
    }
  
    int   _bufferPos; // UInt32
    
    // ICompressFilter interface
    public int Init() {
        _bufferPos = 0;
        SubInit();
        return HRESULT.S_OK;
    }
    
    public int Filter(byte [] data, int size) {
        int processedSize = SubFilter(data, size);
        _bufferPos += processedSize;
        return processedSize;
    }
}
