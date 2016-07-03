package SevenZip.Common;

import SevenZip.ICompressProgressInfo;

public class LocalCompressProgressInfo implements ICompressProgressInfo {
    final ICompressProgressInfo _progress;

    final boolean _inStartValueIsAssigned;
    final boolean _outStartValueIsAssigned;
    final long _inStartValue;
    final long _outStartValue;
    
    public LocalCompressProgressInfo(
    		ICompressProgressInfo progress,
    		long inStartValue,
    		long outStartValue) {
        _progress = progress;
        _inStartValueIsAssigned = (inStartValue != ICompressProgressInfo.INVALID);
        _inStartValue = inStartValue;
        _outStartValueIsAssigned = (outStartValue != ICompressProgressInfo.INVALID);
        _outStartValue = outStartValue;
    }
    
    public void SetRatioInfo(long inSize, long outSize) {
        long inSizeNew, outSizeNew;
        long inSizeNewPointer;
        long outSizeNewPointer;
        if (_inStartValueIsAssigned && inSize != ICompressProgressInfo.INVALID) {
            inSizeNew = _inStartValue + (inSize); // *inSize
            inSizeNewPointer = inSizeNew;
        } else {
            inSizeNewPointer = ICompressProgressInfo.INVALID;
        }
        
        if (_outStartValueIsAssigned && outSize != ICompressProgressInfo.INVALID) {
            outSizeNew = _outStartValue + (outSize);
            outSizeNewPointer = outSizeNew;
        } else {
            outSizeNewPointer = ICompressProgressInfo.INVALID;
        }
        _progress.SetRatioInfo(inSizeNewPointer, outSizeNewPointer);
    }
    
}
