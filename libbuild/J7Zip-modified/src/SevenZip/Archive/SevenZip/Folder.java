package SevenZip.Archive.SevenZip;

import java.io.IOException;
import java.util.Vector;

import Common.IntVector;
import Common.LimitedSequentialInStream;
import Common.LockedInStream;
import Common.LockedSequentialInStreamImp;
import Common.LongVector;

import SevenZip.IInStream;
import SevenZip.Archive.Common.BindPair;
import SevenZip.Archive.Common.CoderStreamsInfo;

public class Folder {
	
    public Vector<CoderInfo> Coders = new Vector();
    public Vector<BindPair> BindPairs = new Vector();
    public IntVector PackStreams = new IntVector();
    public LongVector UnPackSizes = new LongVector();
    int UnPackCRC;
    boolean UnPackCRCDefined;
    
    Folder() {
        UnPackCRCDefined = false;
    }

    public long GetUnPackSize() throws IOException {
        if (UnPackSizes.isEmpty())
            return 0;
        for (int i = UnPackSizes.size() - 1; i >= 0; i--)
            if (FindBindPairForOutStream(i) < 0)
                return UnPackSizes.get(i);
        throw new IOException("1"); // throw 1  // TBD
    }
    
    public int FindBindPairForInStream(int inStreamIndex) {
        for(int i = 0; i < BindPairs.size(); i++)
            if ((BindPairs.get(i)).InIndex == inStreamIndex)
                return i;
        return -1;
    }
    
    public int FindBindPairForOutStream(int outStreamIndex) {
        for(int i = 0; i < BindPairs.size(); i++)
            if ((BindPairs.get(i)).OutIndex == outStreamIndex)
                return i;
        return -1;
    }
    
    public int FindPackStreamArrayIndex(int inStreamIndex) {
        for(int i = 0; i < PackStreams.size(); i++)
            if (PackStreams.get(i) == inStreamIndex)
                return i;
        return -1;
    }
      
    public int GetNumOutStreams() {
        int result = 0;
        for (int i = 0; i < Coders.size(); i++)
            result += (Coders.get(i)).NumOutStreams;
        return result;
    }
    
	public Vector getInStreams(
			IInStream inStream, long startPos,
			LongVector packSizes, int packSizesOffset) {
		final Vector inStreams = new Vector(this.PackStreams.size());
		final LockedInStream lockedInStream = new LockedInStream(inStream);
		for (int j = 0; j < this.PackStreams.size(); j++) {
			inStreams.add(new LimitedSequentialInStream(
					new LockedSequentialInStreamImp(lockedInStream, startPos),
					packSizes.get(j + packSizesOffset)));
			startPos += packSizes.get(j + packSizesOffset);
		}
		return inStreams;
	}
    
    public BindInfoEx toBindInfoEx() {
		BindInfoEx bindInfo = new BindInfoEx();
		
		for (int i = 0; i < this.BindPairs.size(); i++) {
			BindPair bindPair = new BindPair();
			bindPair.InIndex = (this.BindPairs.get(i)).InIndex;
			bindPair.OutIndex = (this.BindPairs.get(i)).OutIndex;
			bindInfo.BindPairs.add(bindPair);
		}
		int outStreamIndex = 0;
		for (int i = 0; i < this.Coders.size(); i++) {
			CoderStreamsInfo coderStreamsInfo = new CoderStreamsInfo();
			CoderInfo coderInfo = this.Coders.get(i);
			coderStreamsInfo.NumInStreams = coderInfo.NumInStreams;
			coderStreamsInfo.NumOutStreams = coderInfo.NumOutStreams;
			bindInfo.Coders.add(coderStreamsInfo);
			AltCoderInfo altCoderInfo = (AltCoderInfo)coderInfo.AltCoders.firstElement();
			bindInfo.CoderMethodIDs.add(altCoderInfo.MethodID);
			for (int j = 0; j < coderStreamsInfo.NumOutStreams; j++, outStreamIndex++)
				if (this.FindBindPairForOutStream(outStreamIndex) < 0)
					bindInfo.OutStreams.add(outStreamIndex);
		}
		for (int i = 0; i < this.PackStreams.size(); i++)
			bindInfo.InStreams.add(this.PackStreams.get(i));
		return bindInfo;
    }
}