package SevenZip.Common;

import SevenZip.ICompressProgressInfo;
import SevenZip.IProgress;

public class LocalProgress implements ICompressProgressInfo {
    private final IProgress _progress;
    private final boolean _inSizeIsMain;
    
    public LocalProgress(IProgress progress, boolean inSizeIsMain) {
    	this._progress = progress;
    	this._inSizeIsMain = inSizeIsMain;
    }
    
    public void SetRatioInfo(long inSize, long outSize) {
        _progress.SetCompleted(_inSizeIsMain ? inSize : outSize);
    }
    
}
