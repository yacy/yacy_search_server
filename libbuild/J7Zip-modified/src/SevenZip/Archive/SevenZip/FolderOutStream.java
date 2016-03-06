package SevenZip.Archive.SevenZip;

import java.io.IOException;
import java.io.OutputStream;

import SevenZip.Archive.Common.OutStreamWithCRC;
import SevenZip.Archive.IArchiveExtractCallback;
import SevenZip.Archive.IInArchive;

import Common.BoolVector;

public class FolderOutStream extends OutputStream {
	
	private OutStreamWithCRC		outStreamWithHashSpec;
	private ArchiveDB				archiveDatabase;
	private BoolVector				extractStatuses;
	private int						startIndex;
	private int						ref2Offset;
	private IArchiveExtractCallback	extractCallback;
	private boolean					testMode;
	private int						currentIndex;
	private boolean					fileIsOpen;
	private long					filePos;
	
	public FolderOutStream(
			ArchiveDB archiveDatabase,
			int ref2Offset,
			int startIndex,
			BoolVector extractStatuses,
			IArchiveExtractCallback extractCallback,
			boolean testMode) throws IOException {
		this(new OutStreamWithCRC(), archiveDatabase, ref2Offset, startIndex, extractStatuses, extractCallback, testMode);
	}
	
	public FolderOutStream(OutStreamWithCRC os,
			ArchiveDB archiveDatabase,
			int ref2Offset,
			int startIndex,
			BoolVector extractStatuses,
			IArchiveExtractCallback extractCallback,
			boolean testMode) throws IOException {
		if (os == null) throw new NullPointerException();
		this.outStreamWithHashSpec = os;
		this.archiveDatabase = archiveDatabase;
		this.ref2Offset = ref2Offset;
		this.startIndex = startIndex;
		
		this.extractStatuses = extractStatuses;
		this.extractCallback = extractCallback;
		this.testMode = testMode;
		
		this.currentIndex = 0;
		this.fileIsOpen = false;
		WriteEmptyFiles();
	}
	
	private void OpenFile() throws IOException {
		int askMode;
		if (this.extractStatuses.get(this.currentIndex)) {
			askMode = this.testMode
					? IInArchive.NExtract_NAskMode_kTest
					: IInArchive.NExtract_NAskMode_kExtract;
		} else {
			askMode = IInArchive.NExtract_NAskMode_kSkip;
		}
		
		int index = this.startIndex + this.currentIndex;
		
		OutputStream realOutStream = this.extractCallback.GetStream(this.ref2Offset + index, askMode);
		this.outStreamWithHashSpec.setStream(realOutStream);
		this.outStreamWithHashSpec.reset();
		if (realOutStream == null && askMode == IInArchive.NExtract_NAskMode_kExtract) {
			FileItem fileInfo = (FileItem)this.archiveDatabase.Files.get(index);
			if (!fileInfo.IsAnti && !fileInfo.IsDirectory)
				askMode = IInArchive.NExtract_NAskMode_kSkip;
		}
		this.extractCallback.PrepareOperation(askMode);
	}
	
	private int WriteEmptyFiles() throws IOException {
		int begin = this.currentIndex;
		for(;this.currentIndex < this.extractStatuses.size(); this.currentIndex++) {
			int index = this.startIndex + this.currentIndex;
			FileItem fileInfo = (FileItem)this.archiveDatabase.Files.get(index);
			if (!fileInfo.IsAnti && !fileInfo.IsDirectory && fileInfo.UnPackSize != 0)
				return -1; // return HRESULT.S_OK;
				OpenFile();
				
				this.extractCallback.SetOperationResult(IInArchive.NExtract_NOperationResult_kOK);
				this.outStreamWithHashSpec.releaseStream();
		}
		return this.extractStatuses.size() - begin;
	}
	
	public void write(int b) throws IOException {
		throw new IOException("FolderOutStream - write() not implemented");
	}
	
	public void /*  UInt32 *processedSize */ write(byte[] data, int off, int size) throws IOException {
		int realProcessedSize = 0;
		while(this.currentIndex < this.extractStatuses.size()) {
			if (this.fileIsOpen) {
				int index = this.startIndex + this.currentIndex;
				FileItem fileInfo = (FileItem)this.archiveDatabase.Files.get(index);
				long fileSize = fileInfo.UnPackSize;
				
				long numBytesToWrite2 = fileSize - this.filePos;
				int tmp = size - realProcessedSize;
				if (tmp < numBytesToWrite2) numBytesToWrite2 = tmp;
				
				int processedSizeLocal;
				// int res = _outStreamWithHash.Write((const Byte *)data + realProcessedSize,numBytesToWrite, &processedSizeLocal));
				// if (res != HRESULT.S_OK) throw new java.io.IOException("_outStreamWithHash.Write : " + res); // return res;
				processedSizeLocal = (int)numBytesToWrite2;
				this.outStreamWithHashSpec.write(data, realProcessedSize + off, (int)numBytesToWrite2);
				
				this.filePos += processedSizeLocal;
				realProcessedSize += processedSizeLocal;
				
				if (this.filePos == fileSize) {
					boolean digestsAreEqual = !fileInfo.IsFileCRCDefined || (fileInfo.FileCRC == this.outStreamWithHashSpec.getCRC());
					this.extractCallback.SetOperationResult(digestsAreEqual ?
							IInArchive.NExtract_NOperationResult_kOK :
							IInArchive.NExtract_NOperationResult_kCRCError);
					
					this.outStreamWithHashSpec.releaseStream();
					this.fileIsOpen = false;
					this.currentIndex++;
				}
				if (realProcessedSize == size) {
					WriteEmptyFiles();
					return ;// return realProcessedSize;
				}
			} else {
				OpenFile();
				this.fileIsOpen = true;
				this.filePos = 0;
			}
		}
	}
	
	public void FlushCorrupted(int resultEOperationResult) throws IOException {
		while(this.currentIndex < this.extractStatuses.size()) {
			if (this.fileIsOpen) {
				this.extractCallback.SetOperationResult(resultEOperationResult);
				
				this.outStreamWithHashSpec.releaseStream();
				this.fileIsOpen = false;
				this.currentIndex++;
			} else {
				OpenFile();
				this.fileIsOpen = true;
			}
		}
	}
	
	public boolean IsWritingFinished() {
		return this.currentIndex == this.extractStatuses.size();
	}
	
}
