package SevenZip.Archive.SevenZip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Vector;

import Common.BoolVector;
import Common.IntVector;

import SevenZip.IInStream;
import SevenZip.Common.StreamUtils;

public class InStream {
    
	public static final int kNumMax 	= 0x7FFFFFFF;
    
    final IInStream stream;
    
    private final Vector _inByteVector = new Vector();
    private /* TODO: final */ InByte2 _inByteBack = null;
    
    final long archiveBeginStreamPosition;
    long position;
    
    public InStream(IInStream stream, long searchHeaderSizeLimit) throws IOException {
        this.stream = stream;
        this.position = stream.Seek(0, IInStream.STREAM_SEEK_CUR);
        this.archiveBeginStreamPosition = FindAndReadSignature(searchHeaderSizeLimit);
    }
    
    private long FindAndReadSignature(
            long searchHeaderSizeLimit) throws IOException {
        
        this.stream.Seek(this.position, IInStream.STREAM_SEEK_SET);
        
        byte[] signature = new byte[Header.kSignatureSize];
        
        int processedSize = ReadDirect(this.stream, signature, 0, Header.kSignatureSize);
        if (processedSize != Header.kSignatureSize)
            throw new IOException("detected illegal signature length: " + processedSize + " at byte " + this.position);
        
        if (TestSignatureCandidate(signature, 0))
            return this.position;
        
        // SFX support
        final int kBufferSize = 1 << 16;
        byte [] buffer = new byte[kBufferSize];
        int numPrevBytes = Header.kSignatureSize - 1;
        
        System.arraycopy(signature, 1, buffer, 0, numPrevBytes);
        
        long curTestPos = this.position + 1;
        while (searchHeaderSizeLimit == -1 ||
        		curTestPos - this.position <= searchHeaderSizeLimit) {
            
        	int numReadBytes = kBufferSize - numPrevBytes;
            processedSize = ReadDirect(this.stream, buffer, numPrevBytes, numReadBytes);
            if (processedSize == -1)
            	throw new IOException("unexpected EOF during search for signature");
            
            int numBytesInBuffer = numPrevBytes + processedSize;
            if (numBytesInBuffer < Header.kSignatureSize)
                throw new IOException("detected illegal signature length: " + numBytesInBuffer + " at byte " + this.position);
            
            int numTests = numBytesInBuffer - Header.kSignatureSize + 1;
            for (int pos=0; pos<numTests; pos++, curTestPos++) {
                if (TestSignatureCandidate(buffer, pos)) {
                    this.position = curTestPos + Header.kSignatureSize;
                    this.stream.Seek(this.position, IInStream.STREAM_SEEK_SET);
                    return curTestPos;
                }
            }
            numPrevBytes = numBytesInBuffer - numTests;
            System.arraycopy(buffer, numTests, buffer, 0, numPrevBytes);
        }
        
        throw new IOException("signature not found within the given " + searchHeaderSizeLimit + " bytes");
    }
    
    public void AddByteStream(byte [] buffer, int size) {
        this._inByteVector.add(this._inByteBack = new InByte2(buffer, size));
    }
    
    public void DeleteByteStream() {
        this._inByteVector.removeElementAt(this._inByteVector.size() - 1);
        if (!this._inByteVector.isEmpty())
            this._inByteBack = (InByte2)this._inByteVector.lastElement();
    }
    
    public static boolean TestSignatureCandidate(byte[] testBytes, int off) {
    	if (off == 0)
    		return Arrays.equals(testBytes, Header.kSignature);
        for (int i=0; i<Header.kSignatureSize; i++) {
            // System.out.println(" " + i + ":" + testBytes[i] + " " + kSignature[i]);
            if (testBytes[i + off] != Header.kSignature[i])
                return false;
        }
        return true;
    }
    
    public int ReadDirect(
    		IInStream stream,
            byte[] data,
            int off,
            int size) throws IOException {
        int realProcessedSize = StreamUtils.ReadStream(stream, data, off, size);
        if (realProcessedSize != -1)
        	this.position += realProcessedSize;
        return realProcessedSize;
    }
    
    public int ReadDirect(byte[] data, int size) throws IOException {
        return ReadDirect(this.stream, data, 0, size);
    }
    
    public boolean SafeReadDirect(byte[] data, int size) throws IOException {
    	return (ReadDirect(data, size) == size);
    }
    
    public int SafeReadDirectUInt32() throws IOException {
        int val = 0;
        byte[] b = new byte[4];
        
        int realProcessedSize = ReadDirect(b, 4);
        if (realProcessedSize != 4)
            throw new IOException("Unexpected End Of Archive"); // throw CInArchiveException(CInArchiveException::kUnexpectedEndOfArchive);
        
        for (int i = 0; i < 4; i++)
            val |= (b[i] & 0xFF) << (8 * i);
        return val;
    }
    
    public int ReadUInt32() throws IOException {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int b = ReadByte();
            value |= ((b) << (8 * i));
        }
        return value;
    }
    
    public long ReadUInt64() throws IOException {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            int b = ReadByte();
            value |= (((long)b) << (8 * i));
        }
        return value;
    }
    
    public boolean ReadBytes(byte data[], int size)  throws IOException {
        return this._inByteBack.ReadBytes(data, size);
    }
    
    public boolean ReadBytes(ByteArrayOutputStream baos, int size) throws IOException {
    	return this._inByteBack.readBytes(baos, size) == size;
    }
    
    public int ReadByte()  throws IOException {
        return this._inByteBack.ReadByte();
    }
    
    public long SafeReadDirectUInt64() throws IOException {
        long val = 0;
        byte [] b = new byte[8];
        
        int realProcessedSize = ReadDirect(b, 8);
        if (realProcessedSize != 8)
            throw new IOException("Unexpected End Of Archive"); // throw CInArchiveException(CInArchiveException::kUnexpectedEndOfArchive);
        
        for (int i = 0; i < 8; i++) {
            val |= ((long)(b[i] & 0xFF) << (8 * i));
        }
        return val;
    }
    
    public char ReadWideCharLE()   throws IOException {
        int b1 = this._inByteBack.ReadByte();
        int b2 = this._inByteBack.ReadByte();
        char c = (char)(((char)(b2) << 8) + b1);
        return c;
    }
    
    public long ReadNumber() throws IOException {
        int firstByte = ReadByte();
        
        int mask = 0x80;
        long value = 0;
        for (int i = 0; i < 8; i++) {
            if ((firstByte & mask) == 0) {
                long highPart = firstByte & (mask - 1);
                value += (highPart << (i * 8));
                return value;
            }
            int b = ReadByte();
            if (b < 0)
                throw new IOException("ReadNumber - Can't read stream");
            
            value |= (((long)b) << (8 * i));
            mask >>= 1;
        }
        return value;
    }
    
    public int ReadNum()  throws IOException { // CNum
        long value64 = ReadNumber();
        if (value64 > InStream.kNumMax)
            throw new IOException("ReadNum - value > CNum.kNumMax"); // return E_FAIL;
        
        return (int)value64;
    }
    
    public long ReadID() throws IOException {
        return ReadNumber();
    }
    
    public void SkeepData(long size) throws IOException {
        for (long i = 0; i < size; i++)
            ReadByte();
    }
    
    public void SkeepData() throws IOException {
        long size = ReadNumber();
        SkeepData(size); 
    }
    
    public void skipToAttribute(long attribute) throws IOException {
        long type;
    	while ((type = ReadID()) != attribute) {
            if (type == Header.NID.kEnd)
                throw new IOException("unexpected end of archive");
            SkeepData();
        }
    }
    
    public void close() throws IOException {
        if (this.stream != null) this.stream.close(); // _stream.Release();
    }
    
    public BoolVector ReadBoolVector(int numItems) throws IOException {
        BoolVector v = new BoolVector(numItems);
        int b = 0;
        int mask = 0;
        for (int i=0; i<numItems; i++) {
            if (mask == 0) {
                b = ReadByte();
                mask = 0x80;
            }
            v.add((b & mask) != 0);
            mask >>= 1;
        }
        return v;
    }
    
    public BoolVector ReadBoolVector2(int numItems)  throws IOException { // CBoolVector
        int allAreDefined = ReadByte();
        if (allAreDefined == 0)
            return ReadBoolVector(numItems);
        BoolVector v = new BoolVector(numItems);
        for (int i = 0; i < numItems; i++)
            v.add(true);
        return v;
    }
    
    public IntVector ReadHashDigests(
    		int numItems,
            BoolVector digestsDefined) throws IOException {
        digestsDefined.setBoolVector(ReadBoolVector2(numItems));
        final IntVector digests = new IntVector(numItems);
        digests.clear();
        digests.Reserve(numItems);
        for (int i=0; i<numItems; i++) {
            int crc = 0;
            if (digestsDefined.get(i))
                crc = ReadUInt32();
            digests.add(crc);
        }
        return digests;
    }
    
    public static final long SECS_BETWEEN_EPOCHS = 11644473600L;
    public static final long SECS_TO_100NS = 10000000L; /* 10^7 */
    
    public static long FileTimeToLong(int dwHighDateTime, int dwLowDateTime) {
        // The FILETIME structure is a 64-bit value representing the number of 100-nanosecond intervals since January 1
        long tm = dwHighDateTime;
        tm <<=32;
        tm |= (dwLowDateTime & 0xFFFFFFFFL);
        return (tm - (SECS_BETWEEN_EPOCHS * SECS_TO_100NS)) / (10000L); /* now convert to milliseconds */
    }
}
