package SevenZip.Archive.SevenZip;

import java.util.Vector;

import Common.BoolVector;
import Common.IntVector;
import Common.LongVector;

class ArchiveDatabase {
	
    public LongVector PackSizes = new LongVector();
    public BoolVector PackCRCsDefined = new BoolVector();
    public IntVector PackCRCs = new IntVector();
    public Vector Folders = new Vector();
    public IntVector NumUnPackStreamsVector = new IntVector();
    public Vector Files = new Vector();
    
    void Clear() {
        PackSizes.clear();
        PackCRCsDefined.clear();
        PackCRCs.clear();
        Folders.clear();
        NumUnPackStreamsVector.clear();
        Files.clear();
    }
}