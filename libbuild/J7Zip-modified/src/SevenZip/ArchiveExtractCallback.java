package SevenZip;

import SevenZip.Archive.IArchiveExtractCallback;
import SevenZip.Archive.IInArchive;
import SevenZip.Archive.SevenZipEntry;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class ArchiveExtractCallback implements IArchiveExtractCallback // , ICryptoGetTextPassword,
{
    
    protected IInArchive archiveHandler;  	// IInArchive
    protected String filePath;       		// name inside arcvhive
    protected String diskFilePath;   		// full path to file on disk
    
    public long NumErrors;
    public boolean PasswordIsDefined;
    protected String Password;
    protected boolean extractMode;
    
    protected boolean isDirectory;
    protected final File exractPath;
    
    public ArchiveExtractCallback(File extractPath) {
    	this.PasswordIsDefined = false;
    	this.exractPath = extractPath;
    }
    
    public ArchiveExtractCallback() {
    	this(null);
    }
    
    public void Init(IInArchive archiveHandler) {
        this.NumErrors = 0;
        this.archiveHandler = archiveHandler;
    }
    
    public void SetTotal(long size) {  }
    public void SetCompleted(long completeValue) {  }
    
    public void PrepareOperation(int askExtractMode) {
        this.extractMode = (askExtractMode == IInArchive.NExtract_NAskMode_kExtract);
        switch (askExtractMode) {
            case IInArchive.NExtract_NAskMode_kExtract:
                System.out.print("Extracting  ");
                break;
            case IInArchive.NExtract_NAskMode_kTest:
            	System.out.print("Testing     ");
                break;
            case IInArchive.NExtract_NAskMode_kSkip:
            	System.out.print("Skipping    ");
                break;
        };
        System.out.print(this.filePath);
    }
    
    public void SetOperationResult(int operationResult) throws IOException {
        if (operationResult != IInArchive.NExtract_NOperationResult_kOK) {
            this.NumErrors++;
            System.out.print("     ");
            switch(operationResult) {
                case IInArchive.NExtract_NOperationResult_kUnSupportedMethod:
                    throw new IOException("Unsupported Method");
                case IInArchive.NExtract_NOperationResult_kCRCError:
                	throw new IOException("CRC Failed");
                case IInArchive.NExtract_NOperationResult_kDataError:
                	throw new IOException("Data Error");
                default:
                	// throw new IOException("Unknown Error");
            }
        }
        System.out.println();
    }
    
    public OutputStream GetStream(int index, int askExtractMode) throws IOException {
        RAOutputStream r = null;
        SevenZipEntry item = this.archiveHandler.getEntry(index);
        this.filePath = item.getName();
        
        File file;
        if (this.exractPath == null) {
        	file = new File(this.filePath);
        } else {
        	file = new File(this.exractPath, this.filePath);
        }
        this.diskFilePath = file.getAbsolutePath();
        
        if (askExtractMode == IInArchive.NExtract_NAskMode_kExtract) {
            if (this.isDirectory = item.isDirectory()) {
            	if (!file.isDirectory() && !file.mkdirs())
            		throw new IOException("failed to make directories: " + file);
                return null;
            }
            
            File dirs = file.getParentFile();
            if (dirs != null && !dirs.isDirectory() && !dirs.mkdirs()) {
            	throw new IOException("failed to make directories: " + dirs);
            }
            
            long pos = item.getPosition();
            if (pos == -1) {
                file.delete();
            }
            
            RandomAccessFile outStr = new RandomAccessFile(file, "rw");
            
            if (pos != -1)
                outStr.seek(pos);
             
            r = new RAOutputStream(outStr);
        }
        
        // other case : skip ...
        return r;
    }
    
    private class RAOutputStream extends OutputStream {
        RandomAccessFile file;
        
        public RAOutputStream(RandomAccessFile f) {
            this.file = f;
        }
        
        public void close() throws IOException {
            this.file.close();
            this.file = null;
        }
        /*
        public void flush() throws IOException {
            file.flush();
        }
         */
        public void write(byte[] b) throws IOException {
            this.file.write(b);
        }
        
        public void write(byte[] b, int off, int len) throws IOException {
            this.file.write(b,off,len);
        }
        
        public void write(int b) throws IOException {
            this.file.write(b);
        }
    }
}
