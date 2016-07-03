package SevenZip.Archive;

import SevenZip.Archive.SevenZip.MethodID;

public class SevenZipEntry {
    
    protected long LastWriteTime;
    
    protected long UnPackSize;
    protected long PackSize;
    
    protected int Attributes;
    protected long FileCRC;
    
    protected boolean IsDirectory;
    
    protected String Name;
    protected String Methods;
    
    protected long Position;
    
    public SevenZipEntry(
            String name,
            long packSize,
            long unPackSize,
            long crc,
            long lastWriteTime,
            long position,
            boolean isDir,
            int att,
            String methods) {
        
        this.Name = name;
        this.PackSize = packSize;
        this.UnPackSize = unPackSize;
        this.FileCRC = crc;
        this.LastWriteTime = lastWriteTime;
        this.Position = position;
        this.IsDirectory = isDir;
        this.Attributes = att;
        this.Methods = methods;
    }
    
    public SevenZipEntry(
            String name,
            long packSize,
            long unPackSize,
            long crc,
            long lastWriteTime,
            long position,
            boolean isDir,
            int att,
            MethodID[] methods) {
        
        this.Name = name;
        this.PackSize = packSize;
        this.UnPackSize = unPackSize;
        this.FileCRC = crc;
        this.LastWriteTime = lastWriteTime;
        this.Position = position;
        this.IsDirectory = isDir;
        this.Attributes = att;
        
        StringBuffer methodNames = new StringBuffer();
        for (int i=0; i<methods.length; i++)
            if (methods[i].getName() != null) {
            	if (methodNames.length() > 0)
            		methodNames.append(' ');
                methodNames.append(methods[i].getName());
            }
        this.Methods = methodNames.toString();
    }
    
    public long getCompressedSize() {
        return PackSize;
    }
    
    public long getSize() {
        return UnPackSize;
    }
    
    public long getCrc() {
        return FileCRC;
    }
    
    public String getName() {
        return Name;
    }
    
    public long getTime() {
        return LastWriteTime;
    }
    
    public long getPosition() {
        return Position;
    }
    
    public boolean isDirectory() {
        return IsDirectory;
    }
    
    static final String kEmptyAttributeChar = ".";
    static final String kDirectoryAttributeChar = "D";
    static final String kReadonlyAttributeChar  = "R";
    static final String kHiddenAttributeChar    = "H";
    static final String kSystemAttributeChar    = "S";
    static final String kArchiveAttributeChar   = "A";
    static public final int FILE_ATTRIBUTE_READONLY =            0x00000001  ;
    static public final int FILE_ATTRIBUTE_HIDDEN    =           0x00000002  ;
    static public final int FILE_ATTRIBUTE_SYSTEM    =           0x00000004  ;
    static public final int FILE_ATTRIBUTE_DIRECTORY = 0x00000010;
    static public final int FILE_ATTRIBUTE_ARCHIVE  =            0x00000020  ;
    
    public String getAttributesString() {
        String ret = "";
        ret += ((Attributes & FILE_ATTRIBUTE_DIRECTORY) != 0 || IsDirectory) ?
            kDirectoryAttributeChar: kEmptyAttributeChar;
        ret += ((Attributes & FILE_ATTRIBUTE_READONLY) != 0)?
            kReadonlyAttributeChar: kEmptyAttributeChar;
        ret += ((Attributes & FILE_ATTRIBUTE_HIDDEN) != 0) ?
            kHiddenAttributeChar: kEmptyAttributeChar;
        ret += ((Attributes & FILE_ATTRIBUTE_SYSTEM) != 0) ?
            kSystemAttributeChar: kEmptyAttributeChar;
        ret += ((Attributes & FILE_ATTRIBUTE_ARCHIVE) != 0) ?
            kArchiveAttributeChar: kEmptyAttributeChar;
        return ret;
    }
    
    public String getMethods() {
        return Methods;
    }
}
