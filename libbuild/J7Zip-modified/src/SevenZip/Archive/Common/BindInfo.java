package SevenZip.Archive.Common;

import java.util.Vector;

import Common.IntVector;

public class BindInfo {
	
    public Vector<CoderStreamsInfo> Coders = new Vector();
    public Vector<BindPair> BindPairs = new Vector();
    public IntVector InStreams = new IntVector();
    public IntVector OutStreams = new IntVector();
    
    public void Clear() {
        Coders.clear();
        BindPairs.clear();
        InStreams.clear();
        OutStreams.clear();
    }
    
    public int FindBinderForInStream(int inStream) {
        for (int i = 0; i < BindPairs.size(); i++)
            if (((BindPair)BindPairs.get(i)).InIndex == inStream)
                return i;
        return -1;
    }
    
    public int FindBinderForOutStream(int outStream) // const
    {
        for (int i = 0; i < BindPairs.size(); i++)
            if (((BindPair)BindPairs.get(i)).OutIndex == outStream)
                return i;
        return -1;
    }
    
    public int GetCoderInStreamIndex(int coderIndex) // const
    {
        int streamIndex = 0;
        for (int i = 0; i < coderIndex; i++)
            streamIndex += ((CoderStreamsInfo)Coders.get(i)).NumInStreams;
        return streamIndex;
    }
    
    public int GetCoderOutStreamIndex(int coderIndex) // const
    {
        int streamIndex = 0;
        for (int i = 0; i < coderIndex; i++)
            streamIndex += ((CoderStreamsInfo)Coders.get(i)).NumOutStreams;
        return streamIndex;
    }

    /**
     * @param streamIndex
     * @return the coder index number 
     */
    public int FindInStream(int streamIndex) {
        for (int i=0; i<Coders.size(); i++) {
            int curSize = ((CoderStreamsInfo)Coders.get(i)).NumInStreams;
            if (streamIndex < curSize) {
                return i;
            }
            streamIndex -= curSize;
        }
        return -1; //throw new UnknownError("1");
    }
    
    /**
     * @param streamIndex
     * @return  the coder index number 
     */
    public int FindOutStream(int streamIndex) {
        for (int i=0; i<Coders.size(); i++) {
            int curSize = ((CoderStreamsInfo)Coders.get(i)).NumOutStreams;
            if (streamIndex < curSize)
                return i;
            streamIndex -= curSize;
        }
        return -1; //throw new UnknownError("1");
    }
    
    public boolean equals(Object obj) {
    	if (obj instanceof BindInfo) {
    		BindInfo arg = (BindInfo)obj;
	    	if (this.Coders.size() != arg.Coders.size()) return false;
			if (this.BindPairs.size() != arg.BindPairs.size()) return false;
			if (this.InStreams.size() != arg.InStreams.size()) return false;
			if (this.OutStreams.size() != arg.OutStreams.size()) return false;
			int i;
			for (i = 0; i < this.Coders.size(); i++)
				if (!this.Coders.get(i).equals(arg.Coders.get(i))) return false;
			for (i = 0; i < this.BindPairs.size(); i++)
				if (!this.BindPairs.get(i).equals(arg.BindPairs.get(i))) return false;
			return true;
    	}
		return super.equals(obj);
    }
}

