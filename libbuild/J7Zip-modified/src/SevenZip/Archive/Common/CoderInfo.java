package SevenZip.Archive.Common;

import SevenZip.ICompressCoder;
import SevenZip.ICompressCoder2;

import Common.LongVector;

public class CoderInfo {
    ICompressCoder Coder;
    ICompressCoder2 Coder2;
    int NumInStreams;
    int NumOutStreams;
    
    LongVector InSizes = new LongVector();
    LongVector OutSizes = new LongVector();
    LongVector InSizePointers = new LongVector();
    LongVector OutSizePointers = new LongVector();
    
    public CoderInfo(int numInStreams, int numOutStreams) {
        NumInStreams = numInStreams;
        NumOutStreams = numOutStreams;
        InSizes.Reserve(NumInStreams);
        InSizePointers.Reserve(NumInStreams);
        OutSizePointers.Reserve(NumOutStreams);
        OutSizePointers.Reserve(NumOutStreams);
    }
    
    public static void SetSizes(
            LongVector srcSizes,
            LongVector sizes,
            LongVector sizePointers,
            int numItems) {
        sizes.clear();
        sizePointers.clear();
        for(int i = 0; i < numItems; i++) {
            if (srcSizes == null || srcSizes.get(i) == -1)  // TBD null => -1
            {
                sizes.add(0L);
                sizePointers.add(-1);
            } else {
                sizes.add(srcSizes.get(i)); // sizes.Add(*srcSizes[i]);
                sizePointers.add(sizes.Back()); // sizePointers.Add(&sizes.Back());
            }
        }
    }
    
    public void SetCoderInfo(LongVector inSizes, LongVector outSizes) {
        SetSizes(inSizes, InSizes, InSizePointers, NumInStreams);
        SetSizes(outSizes, OutSizes, OutSizePointers, NumOutStreams);
    }
}
