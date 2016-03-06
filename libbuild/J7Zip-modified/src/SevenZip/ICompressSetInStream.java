package SevenZip;

public interface ICompressSetInStream {
    public void SetInStream(java.io.InputStream inStream);
    public void ReleaseInStream() throws java.io.IOException ;
}

