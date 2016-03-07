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
     * @return int[2], where the element at index 0 specifies the coder index number and
     * the element at index 1 is the stream index corresponding to the found coder index.
     * <p>Returns <code>null</code> if no InStream for <code>streamIndex</code> could be
     * found.</p>
     */
    public int[] FindInStream(int streamIndex) {
        for (int i=0; i<Coders.size(); i++) {
            int curSize = ((CoderStreamsInfo)Coders.get(i)).NumInStreams;
            if (streamIndex < curSize) {
                return new int[] { i, streamIndex };
            }
            streamIndex -= curSize;
        }
        return null;
    }
    
    /**
     * @param streamIndex
     * @return int[2], where the element at index 0 specifies the coder index number and
     * the element at index 1 is the stream index corresponding to the found coder index.
     * <p>Returns <code>null</code> if no OutStream for <code>streamIndex</code> could be
     * found.</p>
     */
    public int[] FindOutStream(int streamIndex) {
        for (int i=0; i<Coders.size(); i++) {
            int curSize = ((CoderStreamsInfo)Coders.get(i)).NumOutStreams;
            if (streamIndex < curSize)
                return new int[] { i, streamIndex };
            streamIndex -= curSize;
        }
        return null;
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

