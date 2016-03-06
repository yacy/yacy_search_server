package SevenZip.Archive;

import java.io.IOException;

public interface IInArchive {
    public final static int NExtract_NAskMode_kExtract = 0;
    public final static int NExtract_NAskMode_kTest = 1;
    public final static int NExtract_NAskMode_kSkip = 2;
    
    public final static int NExtract_NOperationResult_kOK = 0;
    public final static int NExtract_NOperationResult_kUnSupportedMethod = 1;
    public final static int NExtract_NOperationResult_kDataError = 2;
    public final static int NExtract_NOperationResult_kCRCError = 3;
    
    // Static-SFX (for Linux) can be big.
    public final long kMaxCheckStartPosition = 1 << 22;
    
    public SevenZipEntry getEntry(int index);
    
    public int size();
    
    public void close() throws IOException ;
    
    public void Extract(int [] indices, int numItems,
            int testModeSpec, IArchiveExtractCallback extractCallbackSpec) throws java.io.IOException;
    
}

