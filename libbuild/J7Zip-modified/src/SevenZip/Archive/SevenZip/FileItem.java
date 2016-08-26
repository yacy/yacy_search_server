package SevenZip.Archive.SevenZip;

public class FileItem {
    
    public long CreationTime;
    public long LastWriteTime;
    public long LastAccessTime;
    
    public long UnPackSize;
    public long StartPos;
    public int Attributes;
    public int FileCRC;
    
    public boolean IsDirectory;
    public boolean IsAnti;
    public boolean IsFileCRCDefined;
    public boolean AreAttributesDefined;
    public boolean HasStream;
    // public boolean IsCreationTimeDefined; replace by (CreationTime != 0)
    // public boolean IsLastWriteTimeDefined; replace by (LastWriteTime != 0)
    // public boolean IsLastAccessTimeDefined; replace by (LastAccessTime != 0)
    public boolean IsStartPosDefined;
    public String name;
    
    public FileItem() {
        HasStream = true;
        IsDirectory = false;
        IsAnti = false;
        IsFileCRCDefined = false;
        AreAttributesDefined = false;
        CreationTime = 0; // IsCreationTimeDefined = false;
        LastWriteTime = 0; // IsLastWriteTimeDefined = false;
        LastAccessTime = 0; // IsLastAccessTimeDefined = false;
        IsStartPosDefined = false;
    }
}