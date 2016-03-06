package SevenZip.Archive.Common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

import SevenZip.ICompressCoder2;
import SevenZip.ICompressCoder;
import SevenZip.ICompressSetOutStreamSize;
import Common.LongVector;

import SevenZip.ICompressProgressInfo;

public class CoderMixer2ST implements ICompressCoder2, CoderMixer2 {
	
	final BindInfo bindInfo;
	Vector coders = new Vector();
	int mainCoderIndex;
	
	public CoderMixer2ST(BindInfo bindInfo) {
		this.bindInfo = bindInfo;
	}
	
	public void AddCoderCommon(boolean isMain) {
		CoderStreamsInfo csi = (CoderStreamsInfo)bindInfo.Coders.get(coders.size());
		coders.add(new STCoderInfo(csi.NumInStreams, csi.NumOutStreams, isMain));
	}
	
	public void AddCoder2(ICompressCoder2 coder, boolean isMain) {
		AddCoderCommon(isMain);
		((STCoderInfo)coders.lastElement()).Coder2 = coder;
	}
	
	public void AddCoder(ICompressCoder coder, boolean isMain) {
		AddCoderCommon(isMain);
		((STCoderInfo)coders.lastElement()).Coder = coder;
	}
	
	public void ReInit() {
	}
	
	public void SetCoderInfo(int coderIndex, LongVector inSizes, LongVector outSizes) {
		// _coders[coderIndex].SetCoderInfo(inSizes, outSizes);
		((STCoderInfo)coders.get(coderIndex)).SetCoderInfo(inSizes, outSizes);
	}
	
	public OutputStream GetOutStream(
			Vector outStreams,
			// Object useless_outSizes, //  const UInt64 **outSizes,
			int streamIndex) {
		return null;
	}
	
	public void Code(
			Vector inStreams,
			Object useless_inSizes, // const UInt64 ** inSizes ,
			int numInStreams,
			Vector outStreams,
			Object useless_outSizes, // const UInt64 ** /* outSizes */,
			int numOutStreams,
			ICompressProgressInfo progress) throws IOException {
		if (numInStreams != bindInfo.InStreams.size() || numOutStreams != bindInfo.OutStreams.size())
			throw new IllegalArgumentException("internal error: numInStreams != _bindInfo.InStreams.size() || numOutStreams != _bindInfo.OutStreams.size()");
		
		// Find main coder
		int mainCoderIndex = -1;
		for (int i=0; i<coders.size(); i++)
			if (((STCoderInfo)coders.get(i)).IsMain) {
				mainCoderIndex = i;
				break;
			}
		
		if (mainCoderIndex < 0)
			for (int i=0; i<coders.size(); i++)
				if (((STCoderInfo)coders.get(i)).NumInStreams > 1) {
					if (mainCoderIndex >= 0) // TODO: description, what exactly is not implemented
						throw new IOException("not implemented");
					mainCoderIndex = i;
				}
		
		if (mainCoderIndex < 0)
			mainCoderIndex = 0;
		
		// _mainCoderIndex = 0;
		// _mainCoderIndex = _coders.Size() - 1;
		CoderInfo mainCoder = (STCoderInfo)coders.get(mainCoderIndex);
		
		Vector seqInStreams = new Vector(); // CObjectVector< CMyComPtr<ISequentialInStream> >
		int startInIndex = bindInfo.GetCoderInStreamIndex(mainCoderIndex);
		for (int i=0; i<mainCoder.NumInStreams; i++)
			for (int j=0; j<bindInfo.InStreams.size(); j++)
				if (bindInfo.InStreams.get(j) == startInIndex + i)
					seqInStreams.add(inStreams.get(j));
		
		Vector seqOutStreams = new Vector(); // CObjectVector< CMyComPtr<ISequentialOutStream> >
		int startOutIndex = bindInfo.GetCoderOutStreamIndex(mainCoderIndex);
		for (int i=0; i<mainCoder.NumOutStreams; i++)
			for (int j=0; j<bindInfo.OutStreams.size(); j++)
				if (bindInfo.OutStreams.get(j) == startOutIndex + i)
					seqOutStreams.add(outStreams.get(j));
		
		for (int i=0; i<coders.size(); i++) {
			if (i == mainCoderIndex) continue;
			CoderInfo coder = (STCoderInfo)coders.get(i);
			((ICompressSetOutStreamSize)coder.Coder).SetOutStreamSize(coder.OutSizePointers.Front());
		}
		
		if (mainCoder.Coder != null) {
			mainCoder.Coder.Code(
					(InputStream)seqInStreams.firstElement(),
					(OutputStream)seqOutStreams.firstElement(),
					mainCoder.OutSizePointers.Front(),
					progress);
		} else {
			mainCoder.Coder2.Code(
					seqInStreams,
					new Long(mainCoder.InSizePointers.Front()),
					mainCoder.NumInStreams,
					seqOutStreams,
					new Long(mainCoder.OutSizePointers.Front()),
					mainCoder.NumOutStreams,
					progress);
		}
		
		OutputStream stream = (OutputStream)seqOutStreams.firstElement();
		if (stream != null) stream.flush();
	}
	
	public void close() {
		
	}
}
