package SevenZip.Archive.SevenZip;

class Header {
	
    public static final int kSignatureSize = 6;
    public static final byte [] kSignature= { '7', 'z', (byte)0xBC, (byte)0xAF, 0x27, 0x1C };
    public static final byte kMajorVersion = 0;
    
    public static class NID {
        public static final int kEnd = 0;      
        public static final int kHeader = 1;
        public static final int kArchiveProperties = 2;
        public static final int kAdditionalStreamsInfo = 3;
        public static final int kMainStreamsInfo = 4;
        public static final int kFilesInfo = 5;
    
        public static final int kPackInfo = 6;
        public static final int kUnPackInfo = 7;
        public static final int kSubStreamsInfo = 8;

        public static final int kSize = 9;
        public static final int kCRC = 10;
        
        public static final int kFolder = 11;
        public static final int kCodersUnPackSize = 12;
        public static final int kNumUnPackStream = 13;
 
        public static final int kEmptyStream = 14;
        public static final int kEmptyFile = 15;
        public static final int kAnti = 16;

        public static final int kName = 17;
        public static final int kCreationTime = 18;
        public static final int kLastAccessTime = 19;
        public static final int kLastWriteTime = 20;
        public static final int kWinAttributes = 21;
        public static final int kComment = 22;

        public static final int kEncodedHeader = 23;

        public static final int kStartPos = 24;
    }
}
