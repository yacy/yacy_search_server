package SevenZip.Archive.SevenZip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;

import Common.BoolVector;
import Common.CRC;
import Common.IntVector;
import Common.LongVector;

import SevenZip.IInStream;
import SevenZip.Archive.Common.BindPair;

public class ArchiveDB {

	public static final int kNumNoIndex = 0xFFFFFFFF;
	
    public final LongVector		PackSizes			= new LongVector();
	public final BoolVector		PackCRCsDefined			= new BoolVector();
	public final IntVector		PackCRCs			= new IntVector();
	public final IntVector		NumUnPackStreamsVector		= new IntVector();
	public final Vector<FileItem>	Files				= new Vector();
	public 	     Vector<Folder>	Folders				= new Vector();
	
	public final IntVector		FolderStartPackStreamIndex	= new IntVector();
	public final IntVector		FolderStartFileIndex		= new IntVector();
	public final IntVector		FileIndexToFolderIndexMap	= new IntVector();
	
	private final InStream		inStream;
	private final InArchiveInfo	ArchiveInfo			= new InArchiveInfo();
	private final LongVector	PackStreamStartPositions	= new LongVector();
    
    public ArchiveDB(InStream inStream) throws IOException {
        this.inStream = inStream;
		this.ArchiveInfo.StartPosition = this.inStream.archiveBeginStreamPosition;
        
        byte [] btmp = new byte[2];
        int realProcessedSize = this.inStream.ReadDirect(btmp, 2);
        if (realProcessedSize != 2)
            throw new IOException("Unexpected End Of Archive"); // throw CInArchiveException(CInArchiveException::kUnexpectedEndOfArchive);
        
        this.ArchiveInfo.ArchiveVersion_Major = btmp[0];
        this.ArchiveInfo.ArchiveVersion_Minor = btmp[1];
        
        if (this.ArchiveInfo.ArchiveVersion_Major != Header.kMajorVersion)
            throw new IOException("Unsupported Version: " +
            		this.ArchiveInfo.ArchiveVersion_Major + "." +
            		this.ArchiveInfo.ArchiveVersion_Minor);
        
        int crcFromArchive = this.inStream.SafeReadDirectUInt32();
        long nextHeaderOffset = this.inStream.SafeReadDirectUInt64();
        long nextHeaderSize = this.inStream.SafeReadDirectUInt64();
        int nextHeaderCRC = this.inStream.SafeReadDirectUInt32();
        
        this.ArchiveInfo.StartPositionAfterHeader = this.inStream.position;
        
        CRC crc = new CRC();
        crc.UpdateUInt64(nextHeaderOffset);
        crc.UpdateUInt64(nextHeaderSize);
        crc.UpdateUInt32(nextHeaderCRC);
        
        if (crc.GetDigest() != crcFromArchive)
            throw new IOException("Incorrect Header, CRCs don't match: archive: " +
            		Integer.toHexString(crcFromArchive) + ", calculated: " + crc); // CInArchiveException(CInArchiveException::kIncorrectHeader);
        
        if (nextHeaderSize == 0)
            return;
        
        if (nextHeaderSize >= 0xFFFFFFFFL)
            throw new IOException("second header too big: " + nextHeaderSize);
        
        this.inStream.position = this.inStream.stream.Seek(nextHeaderOffset,IInStream.STREAM_SEEK_CUR);
        
        this.readNextStreamHeaders((int)nextHeaderSize, nextHeaderCRC);
        this.readHeader();
        this.Fill();
    }
    
    private void readNextStreamHeaders(int nextHeaderSize, int nextHeaderCRC) throws IOException {
        byte[] buffer = new byte[nextHeaderSize];
        
        // SafeReadDirect(buffer2.data(), (int)nextHeaderSize);
        if (!this.inStream.SafeReadDirect(buffer, nextHeaderSize))
            throw new IOException("Unexpected End Of Archive"); // throw CInArchiveException(CInArchiveException::kUnexpectedEndOfArchive);
        
        if (!CRC.VerifyDigest(nextHeaderCRC, buffer, nextHeaderSize))
            throw new IOException("Incorrect Header, CRCs don't match"); // CInArchiveException(CInArchiveException::kIncorrectHeader);
        
        // TODO:
        StreamSwitch streamSwitch = new StreamSwitch();
        streamSwitch.Set(this.inStream, buffer);
        
        long type;
        while ((type = this.inStream.ReadID()) != Header.NID.kHeader) {
            if (type != Header.NID.kEncodedHeader)
                throw new IOException("Incorrect Header");
            
            Vector dataVector = this.ReadAndDecodePackedStreams(
                    this.ArchiveInfo.StartPositionAfterHeader, 1);
            
            if (dataVector.size() == 0) {
                return;
            } else if (dataVector.size() > 1) {
                throw new IOException("Incorrect Header");
            }
            streamSwitch.Set(this.inStream, (byte[])dataVector.firstElement()); // dataVector.Front()
        }
        
        streamSwitch.close();
    }
    
    private void ReadArchiveProperties(InArchiveInfo archiveInfo) throws IOException {
    	while (this.inStream.ReadID() != Header.NID.kEnd)
    		this.inStream.SkeepData();
    }
    
    private void readHeader() throws IOException {
        long type = this.inStream.ReadID();
        
        if (type == Header.NID.kArchiveProperties) {
            this.ReadArchiveProperties(this.ArchiveInfo);
            type = this.inStream.ReadID();
        }
        
        Vector dataVector = new Vector();
        
        if (type == Header.NID.kAdditionalStreamsInfo) {
            dataVector.addAll(ReadAndDecodePackedStreams(
                    this.ArchiveInfo.StartPositionAfterHeader, 1));
            this.ArchiveInfo.DataStartPosition2 += this.ArchiveInfo.StartPositionAfterHeader;
            type = this.inStream.ReadID();
        }
        
        LongVector unPackSizes = new LongVector();
        BoolVector digestsDefined = new BoolVector();
        IntVector digests = new IntVector();
        
        if (type == Header.NID.kMainStreamsInfo) {
            type = this.inStream.ReadID();
            assert (type == Header.NID.kPackInfo);
            this.ReadPackInfo(this.PackSizes, this.PackCRCsDefined, this.PackCRCs, 0);
            
            type = this.inStream.ReadID();
            assert (type == Header.NID.kUnPackInfo);
            this.Folders = ReadUnPackInfo(dataVector);
            
            type = this.inStream.ReadID();
            assert (type == Header.NID.kSubStreamsInfo);
            this.ReadSubStreamsInfo(this.Folders, this.NumUnPackStreamsVector, unPackSizes, digestsDefined, digests);
            
            type = this.inStream.ReadID();
            assert (type == Header.NID.kEnd);
            
            this.ArchiveInfo.DataStartPosition += this.ArchiveInfo.StartPositionAfterHeader;
            type = this.inStream.ReadID();
        } else {
            for(int i = 0; i < this.Folders.size(); i++) {
                this.NumUnPackStreamsVector.add(1);
                Folder folder = (Folder)this.Folders.get(i);
                unPackSizes.add(folder.GetUnPackSize());
                digestsDefined.add(folder.UnPackCRCDefined);
                digests.add(folder.UnPackCRC);
            }
        }
        
        if (type == Header.NID.kEnd)
            return;
        if (type != Header.NID.kFilesInfo)
            throw new IOException("Incorrect Header");
        
        this.readFileDescriptions(dataVector, unPackSizes, digests, digestsDefined);
    }
    
    private void readFileDescriptions(
    		Vector dataVector,
    		LongVector unPackSizes,
    		IntVector digests,
    		BoolVector digestsDefined) throws IOException {
        
        int numFiles = this.inStream.ReadNum();
        this.ArchiveInfo.FileInfoPopIDs.add(Header.NID.kSize);
        if (!this.PackSizes.isEmpty())
            this.ArchiveInfo.FileInfoPopIDs.add(Header.NID.kPackInfo);
        if (numFiles > 0 && !digests.isEmpty())
            this.ArchiveInfo.FileInfoPopIDs.add(Header.NID.kCRC);
        
        this.Files.clear();
        this.Files.ensureCapacity(numFiles);
        for (int i=0; i<numFiles; i++)
            this.Files.add(new FileItem());
        
        BoolVector emptyStreamVector = new BoolVector();
        emptyStreamVector.Reserve(numFiles);
        for(int i = 0; i < numFiles; i++)
            emptyStreamVector.add(false);
        BoolVector emptyFileVector = new BoolVector();
        BoolVector antiFileVector = new BoolVector();
        int numEmptyStreams = 0;
        
        long type;
        while ((type = this.inStream.ReadID()) != Header.NID.kEnd) {
            long size = this.inStream.ReadNumber();
            this.ArchiveInfo.FileInfoPopIDs.add(type);
            switch((int)type) {
                case Header.NID.kEmptyStream:
                    emptyStreamVector.setBoolVector(this.inStream.ReadBoolVector(numFiles));
                    for (int i=0; i<emptyStreamVector.size(); i++)
                        if (emptyStreamVector.get(i))
                            numEmptyStreams++;
                    emptyFileVector.Reserve(numEmptyStreams);
                    antiFileVector.Reserve(numEmptyStreams);
                    for (int i = 0; i < numEmptyStreams; i++) {
                        emptyFileVector.add(false);
                        antiFileVector.add(false);
                    }
                    break;
                case Header.NID.kName: 				this.ReadFileNames(dataVector); break;
                case Header.NID.kWinAttributes: 	this.ReadFileAttributes(dataVector); break;
                case Header.NID.kStartPos: 			this.ReadFileStartPositions(dataVector); break;
                case Header.NID.kEmptyFile: 		emptyFileVector.setBoolVector(this.inStream.ReadBoolVector(numEmptyStreams)); break;
                case Header.NID.kAnti: 				antiFileVector.setBoolVector(this.inStream.ReadBoolVector(numEmptyStreams)); break;
                case Header.NID.kCreationTime:
                case Header.NID.kLastWriteTime:
                case Header.NID.kLastAccessTime: 	this.ReadTime(dataVector, type); break;
                default:
                    this.ArchiveInfo.FileInfoPopIDs.DeleteBack();
                    this.inStream.SkeepData(size);
                    break;
            }
        }
        
        int emptyFileIndex = 0;
        int sizeIndex = 0;
        for(int i = 0; i < numFiles; i++) {
            FileItem file = (FileItem)this.Files.get(i);
            file.HasStream = !emptyStreamVector.get(i);
            if(file.HasStream) {
                file.IsDirectory = false;
                file.IsAnti = false;
                file.UnPackSize = unPackSizes.get(sizeIndex);
                file.FileCRC = digests.get(sizeIndex);
                file.IsFileCRCDefined = digestsDefined.get(sizeIndex);
                sizeIndex++;
            } else {
                file.IsDirectory = !emptyFileVector.get(emptyFileIndex);
                file.IsAnti = antiFileVector.get(emptyFileIndex);
                emptyFileIndex++;
                file.UnPackSize = 0;
                file.IsFileCRCDefined = false;
            }
        }
    }
    
    private void ReadSubStreamsInfo(
            Vector<Folder> folders,
            IntVector numUnPackStreamsInFolders,
            LongVector unPackSizes,
            BoolVector digestsDefined,
            IntVector digests)  throws IOException {
        numUnPackStreamsInFolders.clear();
        numUnPackStreamsInFolders.Reserve(folders.size());
        long type;
        
        while ((type = this.inStream.ReadID()) != Header.NID.kCRC &&
        		type != Header.NID.kSize &&
        		type != Header.NID.kEnd) {
            if (type == Header.NID.kNumUnPackStream) {
                for(int i = 0; i < folders.size(); i++) {
                    int value = this.inStream.ReadNum();
                    numUnPackStreamsInFolders.add(value);
                }
                continue;
            }
            this.inStream.SkeepData();
        }
        
        if (numUnPackStreamsInFolders.isEmpty())
            for (int i=0; i<folders.size(); i++)
                numUnPackStreamsInFolders.add(1);
        
        final ArrayList sizes = new ArrayList();
        int numSubstreams;
        long sum, size;
        for (int i=0; i<numUnPackStreamsInFolders.size(); i++) {
        	numSubstreams = numUnPackStreamsInFolders.get(i);
        	if (numSubstreams < 1) continue;
        	sum = 0;
        	if (type == Header.NID.kSize)
        		for (int j=1; j<numSubstreams; j++) {
	        		sum += size = this.inStream.ReadNumber();
	        		sizes.add(new Long(size));
        		}
        	sizes.add(new Long((((Folder)folders.get(i)).GetUnPackSize() - sum)));
        }
        unPackSizes.addAll(sizes);
        sizes.clear();
        
        if (type == Header.NID.kSize)
            type = this.inStream.ReadID();
        
        int numDigests = 0;
        int numDigestsTotal = 0;
        for(int i = 0; i < folders.size(); i++) {
            numSubstreams = numUnPackStreamsInFolders.get(i);
            if (numSubstreams != 1 || !((Folder)folders.get(i)).UnPackCRCDefined)
                numDigests += numSubstreams;
            numDigestsTotal += numSubstreams;
        }
        
        final ArrayList bsizes = new ArrayList();
        do {
            if (type == Header.NID.kCRC) {
                BoolVector digestsDefined2 = new BoolVector();
                IntVector digests2 = new IntVector();
                digests2 = this.inStream.ReadHashDigests(numDigests, digestsDefined2);
                int digestIndex = 0;
                
                for (int i=0; i<folders.size(); i++) {
                	numSubstreams = numUnPackStreamsInFolders.get(i);
                	Folder folder = (Folder)folders.get(i);
                	if (numSubstreams == 1 && folder.UnPackCRCDefined) {
                		bsizes.add(Boolean.TRUE);
                		sizes.add(Integer.valueOf(folder.UnPackCRC));
                	} else {
                		for (int j=0; j<numSubstreams; j++, digestIndex++) {
                			bsizes.add(Boolean.valueOf(digestsDefined2.get(digestIndex)));
                			sizes.add(Integer.valueOf(digests2.get(digestIndex)));
                		}
                	}
                }
                digestsDefined.addAll(bsizes);
                bsizes.clear();
                digests.addAll(sizes);
                sizes.clear();
            } else {
            	this.inStream.SkeepData();
            }
        } while ((type = this.inStream.ReadID()) != Header.NID.kEnd);
        
        if (digestsDefined.isEmpty()) {
            digests.clear();
            for (int i=0; i<numDigestsTotal; i++) {
                digestsDefined.add(false);
                digests.add(0);
            }
        }
    }
    
    private Vector ReadAndDecodePackedStreams(long baseOffset, int dataStartPosIndex) throws IOException {
        LongVector packSizes = new LongVector();
        
        BoolVector packCRCsDefined = new BoolVector();
        IntVector packCRCs = new IntVector();
        
        long type = this.inStream.ReadID();
        assert (type == Header.NID.kPackInfo);
        this.ReadPackInfo(packSizes, packCRCsDefined, packCRCs, dataStartPosIndex);
        
        type = this.inStream.ReadID();
        assert (type == Header.NID.kUnPackInfo);
        Vector<Folder> folders = ReadUnPackInfo(null);
        
        type = this.inStream.ReadID();
        assert (type == Header.NID.kEnd);
        
        int packIndex = 0;
        Decoder decoder = new Decoder(false); // _ST_MODE
        
        Vector dataVector = new Vector();
        long dataStartPos = baseOffset + ((dataStartPosIndex == 0) ?
        		this.ArchiveInfo.DataStartPosition : this.ArchiveInfo.DataStartPosition2);
        for(int i=0; i<folders.size(); i++) {
            Folder folder = (Folder)folders.get(i);
            long unPackSize = folder.GetUnPackSize();
            if (unPackSize > InStream.kNumMax || unPackSize > 0xFFFFFFFFL)
                throw new IOException("unPackSize too great: " + unPackSize);
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream((int)unPackSize);
            decoder.Decode(
            		this.inStream.stream, dataStartPos,
                    packSizes, packIndex,
                    folder, baos, null);
            byte[] data; // TODO: stream belassen!
            dataVector.add(data = baos.toByteArray());
            
            if (folder.UnPackCRCDefined)
                if (!CRC.VerifyDigest(folder.UnPackCRC, data, (int)unPackSize))
                    throw new IOException("Incorrect Header, CRCs of packed folder don't match: archive: " +
                    		Integer.toHexString(folder.UnPackCRC) + ", calculated: " +
                    		Integer.toHexString(CRC.CalculateDigest(data, (int)unPackSize)) +
                    		". Either is the archive corrupted or an internal error occured");
            
            for (int j = 0; j < folder.PackStreams.size(); j++)
                dataStartPos += packSizes.get(packIndex++);
        }
        
        return dataVector;
    }
    
    private void ReadPackInfo(
            LongVector packSizes,
            BoolVector packCRCsDefined,
            IntVector packCRCs,
            int dataStartPosIndex)
            throws IOException {
    	if (dataStartPosIndex == 0) {
      		this.ArchiveInfo.DataStartPosition = this.inStream.ReadNumber();
    	} else {
    		this.ArchiveInfo.DataStartPosition2 = this.inStream.ReadNumber();
    	}
        int numPackStreams = this.inStream.ReadNum();
        
        this.inStream.skipToAttribute(Header.NID.kSize);
        
        packSizes.clear();
        packSizes.Reserve(numPackStreams);
        for(int i = 0; i < numPackStreams; i++) {
            long size = this.inStream.ReadNumber();
            packSizes.add(size);
        }
        
        long type;
        while ((type = this.inStream.ReadID()) != Header.NID.kEnd) {
            if (type == Header.NID.kCRC) {
            	packCRCs = this.inStream.ReadHashDigests(numPackStreams, packCRCsDefined);
                continue;
            }
            this.inStream.SkeepData();
        }
        if (packCRCsDefined.isEmpty()) {
            packCRCsDefined.Reserve(numPackStreams);
            packCRCsDefined.clear();
            packCRCs.Reserve(numPackStreams);
            packCRCs.clear();
            for(int i = 0; i < numPackStreams; i++) {
                packCRCsDefined.add(false);
                packCRCs.add(0);
            }
        }
    }
    
    private void ReadFileNames(Vector from) throws IOException {
        StreamSwitch streamSwitch = new StreamSwitch();
        streamSwitch.Set(this.inStream, from);
    	StringBuffer name = new StringBuffer(30);
        char c;
        for (int i=0; i<this.Files.size(); i++) {
            while ((c = this.inStream.ReadWideCharLE()) != '\0')
                name.append(c);
            ((FileItem)this.Files.get(i)).name = new String(name);
            name.delete(0, name.length());
        }
        streamSwitch.close();
    }
    
    private void ReadFileAttributes(Vector from) throws IOException {
        BoolVector boolVector = this.inStream.ReadBoolVector2(this.Files.size());
        StreamSwitch streamSwitch = new StreamSwitch();
        streamSwitch.Set(this.inStream, from);
        for (int i=0; i<this.Files.size(); i++) {
            FileItem file = (FileItem)this.Files.get(i);
            file.AreAttributesDefined = boolVector.get(i);
            if (file.AreAttributesDefined)
                file.Attributes = this.inStream.ReadUInt32();
        }
        streamSwitch.close();
    }
    
    private void ReadFileStartPositions(Vector from) throws IOException {
        BoolVector boolVector = this.inStream.ReadBoolVector2(this.Files.size());
        StreamSwitch streamSwitch = new StreamSwitch();
        streamSwitch.Set(this.inStream, from);
        for(int i=0; i<this.Files.size(); i++) {
            FileItem file = (FileItem)this.Files.get(i);
            file.IsStartPosDefined = boolVector.get(i);
            if (file.IsStartPosDefined)
                file.StartPos = this.inStream.ReadUInt64();
        }
        streamSwitch.close();
    }
    
    private void ReadTime(Vector dataVector, long type) throws IOException {
        BoolVector boolVector = this.inStream.ReadBoolVector2(this.Files.size());
        
        StreamSwitch streamSwitch = new StreamSwitch();
        streamSwitch.Set(this.inStream, dataVector);
        
        for (int i=0; i<this.Files.size(); i++) {
            FileItem file = (FileItem)this.Files.get(i);
            int low = 0;
            int high = 0;
            boolean defined = boolVector.get(i);
            if (defined) {
                low = this.inStream.ReadUInt32();
                high = this.inStream.ReadUInt32();
            }
            switch((int)type) {
                case Header.NID.kCreationTime:
                    // file.IsCreationTimeDefined = defined;
                    if (defined)
                        file.CreationTime = InStream.FileTimeToLong(high,low);
                    break;
                case Header.NID.kLastWriteTime:
                    // file.IsLastWriteTimeDefined = defined;
                    if (defined)
                        file.LastWriteTime = InStream.FileTimeToLong(high,low);
                    break;
                case Header.NID.kLastAccessTime:
                    // file.IsLastAccessTimeDefined = defined;
                    if (defined)
                        file.LastAccessTime = InStream.FileTimeToLong(high,low);
                    break;
            }
        }
        streamSwitch.close();
    }
    
    private Vector<Folder> ReadUnPackInfo(Vector dataVector) throws IOException {
        this.inStream.skipToAttribute(Header.NID.kFolder);
        
        int numFolders = this.inStream.ReadNum();
        
        StreamSwitch streamSwitch = new StreamSwitch();
        streamSwitch.Set(this.inStream, dataVector);
        Vector<Folder> folders = new Vector(numFolders);
        for (int i=0; i<numFolders; i++)
            folders.add(GetNextFolderItem());
        streamSwitch.close();
        
        this.inStream.skipToAttribute(Header.NID.kCodersUnPackSize);
        
        for (int i=0; i<numFolders; i++) {
            Folder folder = folders.get(i);
            int numOutStreams = folder.GetNumOutStreams();
            folder.UnPackSizes.Reserve(numOutStreams);
            for (int j=0; j<numOutStreams; j++) {
                long unPackSize = this.inStream.ReadNumber();
                folder.UnPackSizes.add(unPackSize);
            }
        }
        
        long type;
        while ((type = this.inStream.ReadID()) != Header.NID.kEnd) {
            if (type == Header.NID.kCRC) {
                BoolVector crcsDefined = new BoolVector();
                IntVector crcs = new IntVector();
                crcs = this.inStream.ReadHashDigests(numFolders, crcsDefined);
                for (int i=0; i<numFolders; i++) {
                    Folder folder = folders.get(i);
                    folder.UnPackCRCDefined = crcsDefined.get(i);
                    folder.UnPackCRC = crcs.get(i);
                }
                continue;
            }
            this.inStream.SkeepData();
        }
        return folders;
    }
    
    private Folder GetNextFolderItem() throws IOException {
        int numCoders = this.inStream.ReadNum();
        
        Folder folder = new Folder();
        folder.Coders.clear();
        folder.Coders.ensureCapacity(numCoders);
        int numInStreams = 0;
        int numOutStreams = 0;
        for (int i=0; i<numCoders; i++) {
            folder.Coders.add(new CoderInfo());
            CoderInfo coder = folder.Coders.lastElement();
            int mainByte;
            do {
                AltCoderInfo altCoder = new AltCoderInfo();
                coder.AltCoders.add(altCoder);
                mainByte = this.inStream.ReadByte();
                altCoder.MethodID.IDSize = (byte)(mainByte & 0xF);
                if (!this.inStream.ReadBytes(altCoder.MethodID.ID, altCoder.MethodID.IDSize))
                	throw new IOException("error reading properties for alternative decoder");
                
                if ((mainByte & 0x10) != 0) {
                    coder.NumInStreams = this.inStream.ReadNum();
                    coder.NumOutStreams = this.inStream.ReadNum();
                } else {
                    coder.NumInStreams = 1;
                    coder.NumOutStreams = 1;
                }
                if ((mainByte & 0x20) != 0) {
                    int propertiesSize = this.inStream.ReadNum();
                    if (!this.inStream.ReadBytes(altCoder.Properties, propertiesSize))
                    	throw new IOException("error reading properties for alternative decoder");
                }
            } while ((mainByte & 0x80) != 0);
            numInStreams += coder.NumInStreams;
            numOutStreams += coder.NumOutStreams;
        }
        
        // RINOK(ReadNumber(numBindPairs));
        int numBindPairs = numOutStreams - 1;
        folder.BindPairs.clear();
        folder.BindPairs.ensureCapacity(numBindPairs);
        for (int i=0; i<numBindPairs; i++) {
            BindPair bindPair = new BindPair();
            bindPair.InIndex = this.inStream.ReadNum();
            bindPair.OutIndex = this.inStream.ReadNum();
            folder.BindPairs.add(bindPair);
        }
        
        int numPackedStreams = numInStreams - numBindPairs;
        folder.PackStreams.Reserve(numPackedStreams);
        if (numPackedStreams == 1) {
            for (int j=0; j<numInStreams; j++)
                if (folder.FindBindPairForInStream(j) < 0) {
                folder.PackStreams.add(j);
                break;
                }
        } else {
            for (int i=0; i<numPackedStreams; i++) {
	            int packStreamInfo = this.inStream.ReadNum();
	            folder.PackStreams.add(packStreamInfo);
            }
        }
        
        return folder;
    }
    
    private void Fill()  throws IOException {
        FillFolderStartPackStream();
        FillStartPos();
        FillFolderStartFileIndex();
    }
    
    private void FillFolderStartPackStream() {
        this.FolderStartPackStreamIndex.clear();
        this.FolderStartPackStreamIndex.Reserve(this.Folders.size());
        int startPos = 0;
        for(int i = 0; i < this.Folders.size(); i++) {
            this.FolderStartPackStreamIndex.add(startPos);
            startPos += ((Folder)this.Folders.get(i)).PackStreams.size();
        }
    }
    
    private void FillStartPos() {
        this.PackStreamStartPositions.clear();
        this.PackStreamStartPositions.Reserve(this.PackSizes.size());
        long startPos = 0;
        for(int i = 0; i < this.PackSizes.size(); i++) {
            this.PackStreamStartPositions.add(startPos);
            startPos += this.PackSizes.get(i);
        }
    }
    
    private void FillFolderStartFileIndex() throws IOException {
        this.FolderStartFileIndex.clear();
        this.FolderStartFileIndex.Reserve(this.Folders.size());
        this.FileIndexToFolderIndexMap.clear();
        this.FileIndexToFolderIndexMap.Reserve(this.Files.size());
        
        int folderIndex = 0;
        int indexInFolder = 0;
        for (int i = 0; i < this.Files.size(); i++) {
            FileItem file = (FileItem)this.Files.get(i);
            boolean emptyStream = !file.HasStream;
            if (emptyStream && indexInFolder == 0) {
                this.FileIndexToFolderIndexMap.add(kNumNoIndex);
                continue;
            }
            if (indexInFolder == 0) {
                // v3.13 incorrectly worked with empty folders
                // v4.07: Loop for skipping empty folders
                for (;;) {
                    if (folderIndex >= this.Folders.size())
                        throw new IOException("Incorrect Header");
                    this.FolderStartFileIndex.add(i); // check it
                    if (this.NumUnPackStreamsVector.get(folderIndex) != 0)
                        break;
                    folderIndex++;
                }
            }
            this.FileIndexToFolderIndexMap.add(folderIndex);
            if (emptyStream)
                continue;
            indexInFolder++;
            if (indexInFolder >= this.NumUnPackStreamsVector.get(folderIndex)) {
                folderIndex++;
                indexInFolder = 0;
            }
        }
    }
    
    /* ---------------------------------------------------------------------------------------------------
     * public methods
     * --------------------------------------------------------------------------------------------------- */
    
    public void clear() {
        this.ArchiveInfo.FileInfoPopIDs.clear();
        this.PackStreamStartPositions.clear();
        this.FolderStartPackStreamIndex.clear();
        this.FolderStartFileIndex.clear();
        this.FileIndexToFolderIndexMap.clear();
        
        this.PackSizes.clear();
        this.PackCRCsDefined.clear();
        this.PackCRCs.clear();
        this.Folders.clear();
        this.NumUnPackStreamsVector.clear();
        this.Files.clear();
    }
    
    public long GetFolderFullPackSize(int folderIndex) {
        int packStreamIndex = this.FolderStartPackStreamIndex.get(folderIndex);
        Folder folder = (Folder)this.Folders.get(folderIndex);
        long size = 0;
        for (int i = 0; i < folder.PackStreams.size(); i++)
            size += this.PackSizes.get(packStreamIndex + i);
        return size;
    }
    
    public long GetFolderStreamPos(int folderIndex, int indexInFolder) {
        return this.ArchiveInfo.DataStartPosition +
                this.PackStreamStartPositions.get(this.FolderStartPackStreamIndex.get(folderIndex) +
                indexInFolder);
    }
}