package SevenZip.Archive.Common;

public class CoderStreamsInfo {
    public int NumInStreams;
    public int NumOutStreams;
    
    public CoderStreamsInfo() {
        NumInStreams = 0;
        NumOutStreams = 0;
    }
    
    public boolean equals(Object obj) {
    	if (obj instanceof CoderStreamsInfo) {
    		CoderStreamsInfo arg = (CoderStreamsInfo)obj;
    		return (this.NumInStreams == arg.NumInStreams) && (this.NumOutStreams != arg.NumOutStreams);
    	}
    	return super.equals(obj);
    }
}
