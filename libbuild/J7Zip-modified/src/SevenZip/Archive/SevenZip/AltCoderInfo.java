package SevenZip.Archive.SevenZip;

import java.io.ByteArrayOutputStream;

public class AltCoderInfo {
    public MethodID MethodID;
    public ByteArrayOutputStream Properties;
    
    public AltCoderInfo(int size) {
    	MethodID = new MethodID(null);
    	Properties = new ByteArrayOutputStream(size);
    }
    
    public AltCoderInfo() {
        MethodID = new MethodID(null);
        Properties = new ByteArrayOutputStream();
    } 
}
