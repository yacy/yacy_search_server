package SevenZip.Archive.Common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

import SevenZip.ICompressCoder2;
import SevenZip.ICompressCoder;
import SevenZip.ICompressSetOutStreamSize;
import Common.LongVector;
import SevenZip.HRESULT;

import SevenZip.ICompressProgressInfo;
import SevenZip.ICompressSetInStream;

public class CoderMixer2ST implements ICompressCoder2, CoderMixer2 {
	
	final BindInfo bindInfo;
	Vector<STCoderInfo> coders = new Vector();
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
		coders.lastElement().Coder2 = coder;
	}
	
	public void AddCoder(ICompressCoder coder, boolean isMain) {
		AddCoderCommon(isMain);
		coders.lastElement().Coder = coder;
	}
	
	public void ReInit() {
	}
	
	public void SetCoderInfo(int coderIndex, LongVector inSizes, LongVector outSizes) {
		// _coders[coderIndex].SetCoderInfo(inSizes, outSizes);
		coders.get(coderIndex).SetCoderInfo(inSizes, outSizes);
	}

    public int GetInStream(
            Vector<InputStream> inStreams,
            //Object useless_inSizes, // const UInt64 **inSizes,
            int streamIndex,
            InputStream [] inStreamRes) {
        java.io.InputStream seqInStream;
        int i;
        for(i = 0; i < bindInfo.InStreams.size(); i++)
            if (bindInfo.InStreams.get(i) == streamIndex) {
            seqInStream = inStreams.get(i);
            inStreamRes[0] = seqInStream; // seqInStream.Detach();
            return  HRESULT.S_OK;
            }
        int binderIndex = bindInfo.FindBinderForInStream(streamIndex);
        if (binderIndex < 0)
            return HRESULT.E_INVALIDARG;


        int tmp1[] = new int[2]; // TBD
        //int tmp2 [] = new int[1]; // TBD
        tmp1 = bindInfo.FindOutStream(bindInfo.BindPairs.get(binderIndex).OutIndex);
               // , tmp1 /* coderIndex */ , tmp2 /* coderStreamIndex */ );
        int coderIndex = tmp1[0], coderStreamIndex = tmp1[0];

        CoderInfo coder = coders.get(coderIndex);
        if (coder.Coder == null)
            return HRESULT.E_NOTIMPL;

        seqInStream = (java.io.InputStream)coder.Coder; // coder.Coder.QueryInterface(IID_ISequentialInStream, &seqInStream);
        if (seqInStream == null)
            return HRESULT.E_NOTIMPL;

        int startIndex = bindInfo.GetCoderInStreamIndex(coderIndex);

        if (coder.Coder == null)
            return HRESULT.E_NOTIMPL;

        ICompressSetInStream setInStream = (ICompressSetInStream)coder.Coder; //  coder.Coder.QueryInterface(IID_ICompressSetInStream, &setInStream);
        if (setInStream == null)
            return HRESULT.E_NOTIMPL;

        if (coder.NumInStreams > 1)
            return HRESULT.E_NOTIMPL;
        for (i = 0; i < (int)coder.NumInStreams; i++) {
            InputStream [] tmp = new java.io.InputStream[1];
            int res = GetInStream(inStreams, /*useless_inSizes,*/ startIndex + i, tmp /* &seqInStream2 */ );
            if (res != HRESULT.S_OK) return res;
            InputStream seqInStream2 = tmp[0];
            setInStream.SetInStream(seqInStream2);
            //if (res != HRESULT.S_OK) return res;
        }
        inStreamRes[0] = seqInStream; // seqInStream.Detach();
        return HRESULT.S_OK;
    }

	public void Code(
			Vector<InputStream> inStreams,
			//Object useless_inSizes, // const UInt64 ** inSizes ,
			int numInStreams,
			Vector<OutputStream> outStreams,
			//Object useless_outSizes, // const UInt64 ** /* outSizes */,
			int numOutStreams,
			ICompressProgressInfo progress) throws IOException {
		if (numInStreams != bindInfo.InStreams.size() || numOutStreams != bindInfo.OutStreams.size())
			throw new IllegalArgumentException("internal error: numInStreams != _bindInfo.InStreams.size() || numOutStreams != _bindInfo.OutStreams.size()");
		
		// Find main coder
		int mainCoderIndex = -1;
		for (int i=0; i<coders.size(); i++)
			if ((coders.get(i)).IsMain) {
				mainCoderIndex = i;
				break;
			}
		
		if (mainCoderIndex < 0)
			for (int i=0; i<coders.size(); i++)
				if ((coders.get(i)).NumInStreams > 1) {
					if (mainCoderIndex >= 0) // TODO: description, what exactly is not implemented
						throw new IOException("not implemented");
					mainCoderIndex = i;
				}
		
		if (mainCoderIndex < 0)
			mainCoderIndex = 0;
		
		// _mainCoderIndex = 0;
		// _mainCoderIndex = _coders.Size() - 1;
		CoderInfo mainCoder = coders.get(mainCoderIndex);

		Vector<InputStream> seqInStreams = new Vector(); // CObjectVector< CMyComPtr<ISequentialInStream> >
		int startInIndex = bindInfo.GetCoderInStreamIndex(mainCoderIndex);

        // this original (from J7Zip 4.43a) replaces blows loop, as with LZMA BCJ2 format seqInStreams.size() is incorrect (array out of index) with modified code
        for (int i = 0; i < (int)mainCoder.NumInStreams; i++) {
            java.io.InputStream tmp [] = new  java.io.InputStream[1];
            int res = GetInStream(inStreams, /*useless_inSizes,*/ startInIndex + i, tmp /* &seqInStream */ );
            if (res != HRESULT.S_OK) return;
            java.io.InputStream seqInStream = tmp[0];
            seqInStreams.add(seqInStream);
        }
        /* --- replaced by above ---
		for (int i=0; i<mainCoder.NumInStreams; i++)
			for (int j=0; j<bindInfo.InStreams.size(); j++)
				if (bindInfo.InStreams.get(j) == startInIndex + i)
					seqInStreams.add(inStreams.get(j));
	*/
		Vector<OutputStream> seqOutStreams = new Vector(); // CObjectVector< CMyComPtr<ISequentialOutStream> >
		int startOutIndex = bindInfo.GetCoderOutStreamIndex(mainCoderIndex);
		for (int i=0; i<mainCoder.NumOutStreams; i++)
			for (int j=0; j<bindInfo.OutStreams.size(); j++)
				if (bindInfo.OutStreams.get(j) == startOutIndex + i)
					seqOutStreams.add(outStreams.get(j));
		
		for (int i=0; i<coders.size(); i++) {
			if (i == mainCoderIndex) continue;
			CoderInfo coder = coders.get(i);
			((ICompressSetOutStreamSize)coder.Coder).SetOutStreamSize(coder.OutSizePointers.Front());
		}
		
		if (mainCoder.Coder != null) {
			mainCoder.Coder.Code(
					seqInStreams.firstElement(),
					seqOutStreams.firstElement(),
					mainCoder.OutSizePointers.Front(),
					progress);
		} else {
			mainCoder.Coder2.Code(
					seqInStreams,
					//new Long(mainCoder.InSizePointers.Front()),
					mainCoder.NumInStreams,
					seqOutStreams,
					//new Long(mainCoder.OutSizePointers.Front()),
					mainCoder.NumOutStreams,
					progress);
		}
		
		OutputStream stream = seqOutStreams.firstElement();
		if (stream != null) stream.flush();
	}
	
	public void close() {
		
	}
}
