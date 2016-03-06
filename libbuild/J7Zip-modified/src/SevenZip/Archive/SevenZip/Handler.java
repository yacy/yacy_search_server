package SevenZip.Archive.SevenZip;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import SevenZip.HRESULT;
import SevenZip.IInStream;
import SevenZip.MyRandomAccessFile;

import SevenZip.ICompressProgressInfo;
import SevenZip.Common.LocalProgress;
import SevenZip.Common.LocalCompressProgressInfo;
import SevenZip.Archive.IInArchive;
import SevenZip.Archive.IArchiveExtractCallback;
import SevenZip.Archive.SevenZipEntry;

public class Handler implements IInArchive {
	
    public IInStream _inStream;
    public ArchiveDB _database;
    int _numThreads = 1;	// XXX: configurable
    
    public Handler(File archive) throws IOException {
        this(new MyRandomAccessFile(archive, "r"), kMaxCheckStartPosition);
    }
    
    public Handler(IInStream stream) throws IOException {
        this(stream, kMaxCheckStartPosition);
    }
    
    public Handler(IInStream stream, long maxCheckStartPosition) throws IOException {
        InStream archive = new InStream(stream, maxCheckStartPosition);
        this._database = new ArchiveDB(archive);
        this._inStream = stream;
    }
    
    public void Extract(int [] indices, int numItems,
            int testModeSpec, IArchiveExtractCallback extractCallback) throws IOException {
        
        boolean testMode = (testModeSpec != 0);
        long importantTotalUnPacked = 0;
        
        boolean allFilesMode = (numItems == -1);
        if (allFilesMode)
            numItems = this._database.Files.size();
        
        if (numItems == 0)
            return;
        
        Vector extractFolderInfoVector = new Vector();
        for (int ii = 0; ii < numItems; ii++) {
            int ref2Index = allFilesMode ? ii : indices[ii];
            
            ArchiveDB database = _database;
            int fileIndex = ref2Index;
            
            int folderIndex = database.FileIndexToFolderIndexMap.get(fileIndex);
            if (folderIndex == ArchiveDB.kNumNoIndex) {
                extractFolderInfoVector.add( new ExtractFolderInfo(fileIndex, ArchiveDB.kNumNoIndex));
                continue;
            }
            if (extractFolderInfoVector.isEmpty() ||
                    folderIndex != ((ExtractFolderInfo)extractFolderInfoVector.lastElement()).FolderIndex) {
                extractFolderInfoVector.add(new ExtractFolderInfo(ArchiveDB.kNumNoIndex, folderIndex));
                Folder folderInfo = (Folder)database.Folders.get(folderIndex);
                long unPackSize = folderInfo.GetUnPackSize();
                importantTotalUnPacked += unPackSize;
                ((ExtractFolderInfo)extractFolderInfoVector.lastElement()).UnPackSize = unPackSize;
            }
            
            ExtractFolderInfo efi = (ExtractFolderInfo)extractFolderInfoVector.lastElement();
            
            int startIndex = database.FolderStartFileIndex.get(folderIndex); // CNum
            for (int index = efi.ExtractStatuses.size(); index <= fileIndex - startIndex; index++)
                efi.ExtractStatuses.add(index == fileIndex - startIndex);
        }
        
        extractCallback.SetTotal(importantTotalUnPacked);
        
        Decoder decoder = new Decoder(false);
        
        long currentImportantTotalUnPacked = 0;
        long totalFolderUnPacked;
        
        for (int i = 0; i < extractFolderInfoVector.size(); i++, currentImportantTotalUnPacked += totalFolderUnPacked) {
            ExtractFolderInfo efi = (ExtractFolderInfo)extractFolderInfoVector.get(i);
            totalFolderUnPacked = efi.UnPackSize;
            
            extractCallback.SetCompleted(currentImportantTotalUnPacked);
            
            int startIndex; // CNum
            if (efi.FileIndex != ArchiveDB.kNumNoIndex)
                startIndex = efi.FileIndex;
            else
                startIndex = this._database.FolderStartFileIndex.get(efi.FolderIndex);
            
            
            FolderOutStream folderOutStream = new FolderOutStream(this._database, 0, startIndex, efi.ExtractStatuses, extractCallback, testMode);
            int result = HRESULT.S_OK;
            
            if (efi.FileIndex != ArchiveDB.kNumNoIndex)
                continue;
            
            int folderIndex = efi.FolderIndex; // CNum
            Folder folderInfo = (Folder)this._database.Folders.get(folderIndex);
            
            LocalProgress localProgressSpec = new LocalProgress(extractCallback, false);
            
            ICompressProgressInfo compressProgress = new LocalCompressProgressInfo(
            		localProgressSpec,
            		ICompressProgressInfo.INVALID,
            		currentImportantTotalUnPacked);
            
            int packStreamIndex = this._database.FolderStartPackStreamIndex.get(folderIndex); // CNum
            long folderStartPackPos = this._database.GetFolderStreamPos(folderIndex, 0);
            
            try {
                /* TODO: result = */ decoder.Decode(
                		this._inStream,
                        folderStartPackPos,
                        this._database.PackSizes,
                        packStreamIndex,
                        folderInfo,
                        folderOutStream,
                        compressProgress);
                
                if (result == HRESULT.S_FALSE) {
                    folderOutStream.FlushCorrupted(IInArchive.NExtract_NOperationResult_kDataError);
                    // if (result != HRESULT.S_OK) return result;
                    continue;
                }
                if (result == HRESULT.E_NOTIMPL) {
                    folderOutStream.FlushCorrupted(IInArchive.NExtract_NOperationResult_kUnSupportedMethod);
                    // if (result != HRESULT.S_OK) return result;
                    continue;
                }
                if (folderOutStream.IsWritingFinished()) {
                    folderOutStream.FlushCorrupted(IInArchive.NExtract_NOperationResult_kDataError);
                    // if (result != HRESULT.S_OK) return result;
                    continue;
                }
            } catch(Exception e) {
                System.out.println("IOException : " + e);
                e.printStackTrace();
                folderOutStream.FlushCorrupted(IInArchive.NExtract_NOperationResult_kDataError);
                // if (result != HRESULT.S_OK) return result;
                continue;
            }
        }
    }
    
    protected void finalize() throws Throwable {
    	close();
    	super.finalize();
    }
    
    public void close() throws IOException {
        if (_inStream != null) _inStream.close();
        _inStream = null;
        _database.clear();
    }
    
    public int size() {
        return _database.Files.size();
    }
    
    private long getPackSize(int index2) {
        long packSize = 0;
        int folderIndex = _database.FileIndexToFolderIndexMap.get(index2);
        if (folderIndex != ArchiveDB.kNumNoIndex) {
            if (_database.FolderStartFileIndex.get(folderIndex) == index2)
                packSize = _database.GetFolderFullPackSize(folderIndex);
        }
        return packSize;
    }
    
    private static int GetUInt32FromMemLE(byte [] p , int off) {
        return p[off]
                 | (((int)p[off + 1]) <<  8)
                 | (((int)p[off + 2]) << 16)
                 | (((int)p[off + 3]) << 24);
    }
    
    private static String GetStringForSizeValue(int value) {
        for (int i = 31; i >= 0; i--)
            if ((1 << i) == value)
                return Integer.toString(i);
        StringBuffer result = new StringBuffer();
        if (value % (1 << 20) == 0) {
            result.append(value >> 20);
            result.append('m');
        } else if (value % (1 << 10) == 0) {
            result.append(value >> 10);
            result.append('k');
        } else {
            result.append(value);
            result.append('b');
        }
        return result.toString();
    }
    
    private String getMethods(int index2) {
        int folderIndex = _database.FileIndexToFolderIndexMap.get(index2);
        if (folderIndex != ArchiveDB.kNumNoIndex) {
            Folder folderInfo = (Folder)_database.Folders.get(folderIndex);
            StringBuffer methodsString = new StringBuffer();
            for (int i = folderInfo.Coders.size() - 1; i >= 0; i--) {
                CoderInfo coderInfo = (CoderInfo)folderInfo.Coders.get(i);
                if (methodsString.length() > 0)
                    methodsString.append(' ');
                
                // MethodInfo methodInfo;
                
                for (int j = 0; j < coderInfo.AltCoders.size(); j++) {
                    if (j > 0) methodsString.append('|');
                    AltCoderInfo altCoderInfo = (AltCoderInfo)coderInfo.AltCoders.get(j);
                    
                    if (altCoderInfo.MethodID.getName() == null) {
                        // TBD methodsString += altCoderInfo.MethodID.ConvertToString();
                    } else {
                        methodsString.append(altCoderInfo.MethodID.getName());
                        
                        if (altCoderInfo.MethodID.equals(MethodID.k_LZMA)) {
                            if (altCoderInfo.Properties.size() >= 5) {
                                methodsString.append(':');
                                int dicSize = GetUInt32FromMemLE(altCoderInfo.Properties.toByteArray(), 1);
                                methodsString.append(GetStringForSizeValue(dicSize));
                            }
                        }
                        /* else if (altCoderInfo.MethodID == k_PPMD) {
                            if (altCoderInfo.Properties.GetCapacity() >= 5) {
                                Byte order = *(const Byte *)altCoderInfo.Properties;
                                methodsString += ":o";
                                methodsString += ConvertUInt32ToString(order);
                                methodsString += ":mem";
                                UInt32 dicSize = GetUInt32FromMemLE(
                                        ((const Byte *)altCoderInfo.Properties + 1));
                                methodsString += GetStringForSizeValue(dicSize);
                            }
                        } else if (altCoderInfo.MethodID == k_AES) {
                            if (altCoderInfo.Properties.GetCapacity() >= 1) {
                                methodsString += ":";
                                const Byte *data = (const Byte *)altCoderInfo.Properties;
                                Byte firstByte = *data++;
                                UInt32 numCyclesPower = firstByte & 0x3F;
                                methodsString += ConvertUInt32ToString(numCyclesPower);
                            }
                        } else {
                            if (altCoderInfo.Properties.GetCapacity() > 0) {
                                methodsString += ":[";
                                for (size_t bi = 0; bi < altCoderInfo.Properties.GetCapacity(); bi++) {
                                    if (bi > 5 && bi + 1 < altCoderInfo.Properties.GetCapacity()) {
                                        methodsString += "..";
                                        break;
                                    } else
                                        methodsString += GetHex2(altCoderInfo.Properties[bi]);
                                }
                                methodsString += "]";
                            }
                        }
                         */
                    }
                }
            }
            return methodsString.toString();
        }
        
        return new String();
    }
    
    public SevenZipEntry getEntry(int index) {
        SevenZip.Archive.SevenZip.FileItem item = (FileItem)_database.Files.get(index);
        return new SevenZipEntry(
                item.name,
                getPackSize(index),
                item.UnPackSize,
                (item.IsFileCRCDefined) ? item.FileCRC & 0xFFFFFFFFL : -1,
                item.LastWriteTime,
                (item.IsStartPosDefined) ? item.StartPos : -1,
                item.IsDirectory,
                item.Attributes,
                getMethods(index));
    }
    
}