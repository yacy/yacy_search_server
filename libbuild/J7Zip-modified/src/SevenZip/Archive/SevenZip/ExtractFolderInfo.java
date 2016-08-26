package SevenZip.Archive.SevenZip;

import Common.BoolVector;

public class ExtractFolderInfo {
  /* #ifdef _7Z_VOL
     int VolumeIndex;
  #endif */
  public int FileIndex;
  public int FolderIndex;
  public BoolVector ExtractStatuses = new BoolVector();
  public long UnPackSize;
  public ExtractFolderInfo(
    /* #ifdef _7Z_VOL
    int volumeIndex, 
    #endif */
    int fileIndex, int folderIndex)  // CNum fileIndex, CNum folderIndex
  {
    /* #ifdef _7Z_VOL
    VolumeIndex(volumeIndex),
    #endif */
    FileIndex = fileIndex;
    FolderIndex = folderIndex;
    UnPackSize = 0;

    if (fileIndex != ArchiveDB.kNumNoIndex)
    {
      ExtractStatuses.Reserve(1);
      ExtractStatuses.add(true);
    }
  }
}

