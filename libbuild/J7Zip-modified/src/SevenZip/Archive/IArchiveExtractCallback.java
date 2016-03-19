package SevenZip.Archive;

import java.io.IOException;
import java.io.OutputStream;

public interface IArchiveExtractCallback extends SevenZip.IProgress {
    
    OutputStream GetStream(int index, int askExtractMode) throws IOException;
    
    void PrepareOperation(int askExtractMode);
    void SetOperationResult(int resultEOperationResult) throws IOException;
}
