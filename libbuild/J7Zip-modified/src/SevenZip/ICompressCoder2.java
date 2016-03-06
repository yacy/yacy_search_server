package SevenZip;

import java.io.IOException;
import java.util.Vector;

public interface ICompressCoder2 {
	
    public void Code(
            Vector inStreams,
            Object useless1, // const UInt64 ** /* inSizes */,
            int numInStreams,
            Vector outStreams,
            Object useless2, // const UInt64 ** /* outSizes */,
            int numOutStreams,
            ICompressProgressInfo progress) throws IOException;
    
    public void close() throws java.io.IOException ; // destructor
}
