package SevenZip;

import java.io.InputStream;

public interface ICompressSetInStream {
    public void SetInStream(InputStream inStream);
    public void ReleaseInStream() throws java.io.IOException ;
}

