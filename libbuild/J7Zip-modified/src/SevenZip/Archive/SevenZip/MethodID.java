package SevenZip.Archive.SevenZip;

public class MethodID implements Comparable {
    
    public static final MethodID k_LZMA      = new MethodID(0x3, 0x1, 0x1, "LZMA");
    public static final MethodID k_PPMD      = new MethodID(0x3, 0x4, 0x1, "PPMD");
    public static final MethodID k_BCJ_X86   = new MethodID(0x3, 0x3, 0x1, 0x3, "BCJ_x86");
    public static final MethodID k_BCJ       = new MethodID(0x3, 0x3, 0x1, 0x3, "BCJ");
    public static final MethodID k_BCJ2      = new MethodID(0x3, 0x3, 0x1, 0x1B, "BCJ2");
    public static final MethodID k_Deflate   = new MethodID(0x4, 0x1, 0x8, "Deflate");
    public static final MethodID k_Deflate64 = new MethodID(0x4, 0x1, 0x9, "Defalte64");
    public static final MethodID k_BZip2     = new MethodID(0x4, 0x2, 0x2, "BZip2");
    public static final MethodID k_Copy      = new MethodID(0x0, "Copy");
    public static final MethodID k_7zAES     = new MethodID(0x6, 0xF1, 0x07, 0x01, "7zAES");
    
    public byte[] ID;
    public byte IDSize;
    
    private static final int kMethodIDSize = 15;
    private final String name;
    
    public MethodID(String name) {
        this.ID = new byte[kMethodIDSize];
        this.IDSize = 0;
        this.name = name;
    }
 
    public MethodID(int a, String name) {
        int size = 1;
        this.ID = new byte[size];
        this.IDSize = (byte)size;
        this.ID[0] = (byte)a;
        this.name = name;
    } 
        
    public MethodID(int a, int b, int c, String name) {
        int size = 3;
        this.ID = new byte[size];
        this.IDSize = (byte)size;
        this.ID[0] = (byte)a;
        this.ID[1] = (byte)b;
        this.ID[2] = (byte)c;
        this.name = name;
    }    
 
    public MethodID(int a, int b, int c, int d, String name) {
        int size = 4;
        this.ID = new byte[size];
        this.IDSize = (byte)size;
        this.ID[0] = (byte)a;
        this.ID[1] = (byte)b;
        this.ID[2] = (byte)c;
        this.ID[3] = (byte)d;
        this.name = name;
    } 
    
    public int compareTo(Object arg) {
    	MethodID o = (MethodID)arg;
    	if (this.IDSize != o.IDSize) return (int)(this.IDSize - o.IDSize);
    	for (int i=0; i<this.IDSize; i++)
    		if (this.ID[i] != o.ID[i]) return (int)(this.ID[i] - o.ID[i]);
    	return 0;
    }
    
    public boolean equals(Object anObject) {
    	if (anObject instanceof MethodID) {
    		MethodID m = (MethodID)anObject;
    		if (this.IDSize != m.IDSize) return false;
            for(int i = 0; i < this.IDSize ; i++)
	            if (this.ID[i] != m.ID[i]) return false;
            return true;
    	}
    	return super.equals(anObject);
    }
    
    public String getName() {
    	return this.name;
    }
    
    public String toString() {
    	return (this.name == null) ? "undefined" : this.name;
    }
}
