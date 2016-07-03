package SevenZip.Archive.Common;

import Common.LongVector;

public interface CoderMixer2 {
    
    void ReInit();

    // void setCoderInfos(Vector decoders, Folder folderInfo, LongVector packSizes);
    
    void SetCoderInfo(int coderIndex,LongVector inSizes, LongVector outSizes);
}
