package SevenZip.Archive.Common;

public class BindPair {
    public int InIndex;
    public int OutIndex;
    
    public BindPair() {
        InIndex = 0;
        OutIndex = 0;
    }
    
    public boolean equals(Object obj) {
    	if (obj instanceof BindPair) {
    		BindPair arg = (BindPair)obj;
    		return (this.InIndex == arg.InIndex) &&  (this.OutIndex == arg.OutIndex);
    	}
    	return super.equals(obj);
    }
}
