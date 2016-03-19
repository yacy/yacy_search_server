package SevenZip.Archive.SevenZip;

import java.util.Vector;

public class CoderInfo {
    
    public int NumInStreams;
    public int NumOutStreams;
    public Vector AltCoders = new Vector();
    
    boolean IsSimpleCoder() { return (NumInStreams == 1) && (NumOutStreams == 1); }
    
    public CoderInfo() {
        NumInStreams = 0;
        NumOutStreams = 0;
    }
}
