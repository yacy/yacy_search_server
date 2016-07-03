package SevenZip.Archive.SevenZip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

import SevenZip.ICompressCoder2;
import SevenZip.ICompressCoder;
import SevenZip.ICompressFilter;
import SevenZip.ICompressProgressInfo;
import SevenZip.IInStream;

import SevenZip.Archive.Common.CoderMixer2ST;
import SevenZip.Archive.Common.FilterCoder;

import Common.LongVector;

public class Decoder {
	
	private boolean _bindInfoExPrevIsDefined;
	private BindInfoEx _bindInfoExPrev;
	
	private boolean _multiThread;
	
	// CoderMixer2MT _mixerCoderMTSpec;
	private CoderMixer2ST _mixerCoderSTSpec;
	private final Vector _decoders;
	
	public Decoder(boolean multiThread) {
		this._multiThread = multiThread;
		this._bindInfoExPrevIsDefined = false;
		this._bindInfoExPrev = new BindInfoEx();
		this._decoders = new Vector();
	}
	
	private static ICompressCoder getSimpleCoder(AltCoderInfo altCoderInfo) throws IOException {
		ICompressCoder decoder = null;
		ICompressFilter filter = null;
		
		// #ifdef COMPRESS_LZMA
		if (altCoderInfo.MethodID.equals(MethodID.k_LZMA))
			decoder = new SevenZip.Compression.LZMA.Decoder(altCoderInfo.Properties.toByteArray()); // NCompress::NLZMA::CDecoder;
		if (altCoderInfo.MethodID.equals(MethodID.k_PPMD))
			throw new IOException("PPMD not implemented"); // decoder = new NCompress::NPPMD::CDecoder;
		if (altCoderInfo.MethodID.equals(MethodID.k_BCJ_X86))
			filter = new SevenZip.Compression.Branch.BCJ_x86_Decoder();
		if (altCoderInfo.MethodID.equals(MethodID.k_Deflate))
			throw new IOException("DEFLATE not implemented"); // decoder = new NCompress::NDeflate::NDecoder::CCOMCoder;
		if (altCoderInfo.MethodID.equals(MethodID.k_BZip2))
			throw new IOException("BZIP2 not implemented"); // decoder = new NCompress::NBZip2::CDecoder;
		if (altCoderInfo.MethodID.equals(MethodID.k_Copy))
			decoder = new SevenZip.Compression.Copy.Decoder(); // decoder = new NCompress::CCopyCoder;
		if (altCoderInfo.MethodID.equals(MethodID.k_7zAES))
			throw new IOException("k_7zAES not implemented"); // filter = new NCrypto::NSevenZ::CDecoder;
		
		if (filter != null) {
			FilterCoder coderSpec = new FilterCoder();
			coderSpec.Filter = filter;
			decoder = coderSpec;
		}
		
		if (decoder == null)
			throw new IOException("decoder " + altCoderInfo.MethodID + " not implemented");
		return decoder;
	}
	
	private static ICompressCoder2 getComplexCoder(AltCoderInfo altCoderInfo) throws IOException {
		ICompressCoder2 decoder = null;
		
		if (altCoderInfo.MethodID.equals(MethodID.k_BCJ2))
			decoder = new SevenZip.Compression.Branch.BCJ2_x86_Decoder();
		
		if (decoder == null)
			throw new IOException("decoder " + altCoderInfo.MethodID + " not implemented");
		return decoder;
	}
	
	private void createNewCoders(
			BindInfoEx bindInfo,
			Folder folderInfo) throws IOException {
		int i;
		this._decoders.clear();
		if (this._mixerCoderSTSpec != null) this._mixerCoderSTSpec.close(); // _mixerCoder.Release();
		if (this._multiThread) {
			/*
            _mixerCoderMTSpec = new CoderMixer2MT();
            _mixerCoder = _mixerCoderMTSpec;
            _mixerCoderCommon = _mixerCoderMTSpec;
			 */
			throw new IOException("multithreaded decoder not implemented");
		} else {
			this._mixerCoderSTSpec = new CoderMixer2ST(bindInfo);
		}
		
		for (i=0; i<folderInfo.Coders.size(); i++) {
			CoderInfo coderInfo = folderInfo.Coders.get(i);
			AltCoderInfo altCoderInfo = (AltCoderInfo)coderInfo.AltCoders.firstElement();
			
			if (coderInfo.IsSimpleCoder()) {
				ICompressCoder decoder = getSimpleCoder(altCoderInfo);
				this._decoders.add(decoder);
				
				if (this._multiThread) {
					// _mixerCoderMTSpec.AddCoder(decoder);
					// has already ben checked above
					// throw new IOException("Multithreaded decoder is not implemented");
				} else {
					this._mixerCoderSTSpec.AddCoder(decoder, false);
				}
			} else {
				ICompressCoder2 decoder = getComplexCoder(altCoderInfo);
				this._decoders.add(decoder);
				
				if (this._multiThread) {
					// _mixerCoderMTSpec.AddCoder2(decoder);
					// has already ben checked above
					// throw new IOException("Multithreaded decoder is not implemented");
				} else {
					this._mixerCoderSTSpec.AddCoder2(decoder, false);
				}
			}
		}
		this._bindInfoExPrev = bindInfo;
		this._bindInfoExPrevIsDefined = true;
	}
	
	private void setCoderMixerCommonInfos(Folder folderInfo, LongVector packSizes) {
		int packStreamIndex = 0, unPackStreamIndex = 0;
		for (int i=0; i<folderInfo.Coders.size(); i++) {
			CoderInfo coderInfo = folderInfo.Coders.get(i);
			int numInStreams = coderInfo.NumInStreams;
			int numOutStreams = coderInfo.NumOutStreams;
			LongVector packSizesPointers = new LongVector(); // CRecordVector<const UInt64 *>
			LongVector unPackSizesPointers = new LongVector(); // CRecordVector<const UInt64 *>
			packSizesPointers.Reserve(numInStreams);
			unPackSizesPointers.Reserve(numOutStreams);
			int j;
			
			for (j=0; j<numOutStreams; j++, unPackStreamIndex++)
				unPackSizesPointers.add(folderInfo.UnPackSizes.get(unPackStreamIndex));
			 
			for (j=0; j<numInStreams; j++, packStreamIndex++) {
				final long packSizesPointer;
				final int bindPairIndex = folderInfo.FindBindPairForInStream(packStreamIndex);
				final int index;
				if (bindPairIndex >= 0) {
					index = (folderInfo.BindPairs.get(bindPairIndex)).OutIndex;
					packSizesPointer = folderInfo.UnPackSizes.get(index);
				} else {
					index = folderInfo.FindPackStreamArrayIndex(packStreamIndex);
					if (index < 0)
						throw new IndexOutOfBoundsException("PackStreamArrayIndex: " + index);
					packSizesPointer = packSizes.get(index);
				}
				packSizesPointers.add(packSizesPointer);
			}
			
			this._mixerCoderSTSpec.SetCoderInfo(
					i,
					packSizesPointers, // &packSizesPointers.Front(),
					unPackSizesPointers // &unPackSizesPointers.Front()
			);
		}
	}
	
	public void Decode(
			IInStream inStream, long startPos,
			LongVector packSizes, int packSizesOffset,
			Folder folderInfo,
			OutputStream outStream,
			ICompressProgressInfo compressProgress
	) throws IOException {
		
		final Vector<InputStream> inStreams = folderInfo.getInStreams(
				inStream,
				startPos,
				packSizes,
				packSizesOffset);
		
		final BindInfoEx bindInfo = folderInfo.toBindInfoEx();
		
		if (!(this._bindInfoExPrevIsDefined && bindInfo.equals(this._bindInfoExPrev))) {
			createNewCoders(bindInfo, folderInfo);
		} else { /* should not happen, as far as I understood... */ }
		
		this._mixerCoderSTSpec.ReInit();
		// this._mixerCoderCommon.setCoderInfos(this._decoders, folderInfo, packSizes);
		setCoderMixerCommonInfos(folderInfo, packSizes);
		
        // int mainCoder = bindInfo.FindOutStream(bindInfo.OutStreams.get(0))[0];
        
		if (this._multiThread) {
			// _mixerCoderMTSpec.SetProgressCoderIndex(mainCoder);
			throw new IOException("Multithreaded decoder is not implemented");
		}
		
		if (folderInfo.Coders.size() == 0)
			throw new IOException("no decoders available");
		
		final Vector outStreams = new Vector(1);
		outStreams.add(outStream);
		
		this._mixerCoderSTSpec.Code(
				inStreams,
				//null,
				inStreams.size(),
				outStreams,
				//null,
				1,
				compressProgress);
	}
}
