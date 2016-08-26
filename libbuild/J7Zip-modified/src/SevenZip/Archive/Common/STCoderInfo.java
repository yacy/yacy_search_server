package SevenZip.Archive.Common;

public class STCoderInfo extends CoderInfo {
    boolean IsMain;

    public STCoderInfo(int numInStreams, int  numOutStreams, boolean isMain) {
        super(numInStreams, numOutStreams);
        this.IsMain = isMain;
    }   
}
