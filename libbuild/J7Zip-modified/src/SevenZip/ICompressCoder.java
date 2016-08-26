package SevenZip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface ICompressCoder {
	
    void Code(
            InputStream inStream, // , ISequentialInStream
            OutputStream outStream, // ISequentialOutStream
            long outSize, ICompressProgressInfo progress) throws IOException;
}
