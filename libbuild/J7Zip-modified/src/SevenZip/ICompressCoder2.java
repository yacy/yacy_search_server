package SevenZip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

public interface ICompressCoder2 {
	
    public void Code(
            Vector<InputStream> inStreams,
            //Object useless1, // const UInt64 ** /* inSizes */,
            int numInStreams,
            Vector<OutputStream> outStreams,
            //Object useless2, // const UInt64 ** /* outSizes */,
            int numOutStreams,
            ICompressProgressInfo progress) throws IOException;
    
    public void close() throws java.io.IOException ; // destructor
}
