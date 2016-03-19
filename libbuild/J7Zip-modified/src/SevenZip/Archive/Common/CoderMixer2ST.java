package SevenZip.Archive.Common;

import SevenZip.HRESULT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Vector;

import Common.LongVector;
import SevenZip.ICompressCoder2;
import SevenZip.ICompressCoder;
import SevenZip.ICompressSetInStream;
import SevenZip.ICompressSetOutStream;
import SevenZip.ICompressSetOutStreamSize;
import SevenZip.ICompressProgressInfo;


public class CoderMixer2ST implements ICompressCoder2 , CoderMixer2 {

    BindInfo _bindInfo = new BindInfo();
    Vector<STCoderInfo> _coders = new Vector<STCoderInfo>();
    int _mainCoderIndex;

    public CoderMixer2ST(BindInfo bindInfo) {
        this._bindInfo = bindInfo;
    }

    public void AddCoderCommon(boolean isMain) {
        CoderStreamsInfo csi = _bindInfo.Coders.get(_coders.size());
        _coders.add(new STCoderInfo(csi.NumInStreams, csi.NumOutStreams, isMain));
    }

    public void AddCoder2(ICompressCoder2 coder, boolean isMain) {
        AddCoderCommon(isMain);
        _coders.lastElement().Coder2 = coder;
    }

    public void AddCoder(ICompressCoder coder, boolean isMain) {
        AddCoderCommon(isMain);
        _coders.lastElement().Coder = coder;
    }

    @Override
    public void ReInit() {
    }

    @Override
    public void SetCoderInfo(int coderIndex,LongVector inSizes, LongVector outSizes) {
        // _coders[coderIndex].SetCoderInfo(inSizes, outSizes);
        _coders.get(coderIndex).SetCoderInfo(inSizes, outSizes);
    }

    public int GetInStream(
            Vector<InputStream> inStreams,
            //Object useless_inSizes, // const UInt64 **inSizes,
            int streamIndex,
            InputStream [] inStreamRes) {
        InputStream seqInStream;
        int i;
        for(i = 0; i < _bindInfo.InStreams.size(); i++)
            if (_bindInfo.InStreams.get(i) == streamIndex) {
            seqInStream = inStreams.get(i);
            inStreamRes[0] = seqInStream; // seqInStream.Detach();
            return  HRESULT.S_OK;
            }
        int binderIndex = _bindInfo.FindBinderForInStream(streamIndex);
        if (binderIndex < 0)
            return HRESULT.E_INVALIDARG;

        int coderIndex = _bindInfo.FindOutStream(_bindInfo.BindPairs.get(binderIndex).OutIndex);
        if (coderIndex < 0)
            return HRESULT.E_INVALIDARG;

        CoderInfo coder = _coders.get(coderIndex);
        if (coder.Coder == null)
            return HRESULT.E_NOTIMPL;

        seqInStream = (InputStream)coder.Coder; // coder.Coder.QueryInterface(IID_ISequentialInStream, &seqInStream);
        if (seqInStream == null)
            return HRESULT.E_NOTIMPL;

        int startIndex = _bindInfo.GetCoderInStreamIndex(coderIndex);

        if (coder.Coder == null)
            return HRESULT.E_NOTIMPL;

        ICompressSetInStream setInStream = (ICompressSetInStream)coder.Coder; //  coder.Coder.QueryInterface(IID_ICompressSetInStream, &setInStream);
        if (setInStream == null)
            return HRESULT.E_NOTIMPL;

        if (coder.NumInStreams > 1)
            return HRESULT.E_NOTIMPL;
        for (i = 0; i < (int)coder.NumInStreams; i++) {
            InputStream [] tmp = new InputStream[1];
            int res = GetInStream(inStreams, /*useless_inSizes,*/ startIndex + i, tmp /* &seqInStream2 */ );
            if (res != HRESULT.S_OK) return res;
            InputStream seqInStream2 = tmp[0];
            setInStream.SetInStream(seqInStream2);
            //if (res != HRESULT.S_OK) return res;
        }
        inStreamRes[0] = seqInStream; // seqInStream.Detach();
        return HRESULT.S_OK;
    }

    public int GetOutStream(
            Vector<OutputStream> outStreams,
            //Object useless_outSizes, //  const UInt64 **outSizes,
            int streamIndex,
            OutputStream [] outStreamRes) {
        OutputStream seqOutStream;
        int i;
        for(i = 0; i < _bindInfo.OutStreams.size(); i++)
            if (_bindInfo.OutStreams.get(i) == streamIndex) {
            seqOutStream = outStreams.get(i);
            outStreamRes[0] = seqOutStream; // seqOutStream.Detach();
            return  HRESULT.S_OK;
            }
        int binderIndex = _bindInfo.FindBinderForOutStream(streamIndex);
        if (binderIndex < 0)
            return HRESULT.E_INVALIDARG;

        int coderIndex = _bindInfo.FindInStream(_bindInfo.BindPairs.get(binderIndex).InIndex);
        if (coderIndex < 0 )
            return HRESULT.E_INVALIDARG;

        CoderInfo coder = _coders.get(coderIndex);
        if (coder.Coder == null)
            return HRESULT.E_NOTIMPL;

        try
        {
            seqOutStream = (OutputStream)coder.Coder; // coder.Coder.QueryInterface(IID_ISequentialOutStream, &seqOutStream);
        } catch (java.lang.ClassCastException e) {
            return HRESULT.E_NOTIMPL;
        }

        int startIndex = _bindInfo.GetCoderOutStreamIndex(coderIndex);

        if (coder.Coder == null)
            return HRESULT.E_NOTIMPL;

        ICompressSetOutStream setOutStream = null;
        try {
            setOutStream = (ICompressSetOutStream)coder.Coder; // coder.Coder.QueryInterface(IID_ICompressSetOutStream, &setOutStream);
        } catch (java.lang.ClassCastException e) {
            return HRESULT.E_NOTIMPL;
        }

        if (coder.NumOutStreams > 1)
            return HRESULT.E_NOTIMPL;
        for (i = 0; i < (int)coder.NumOutStreams; i++) {
            OutputStream [] tmp = new OutputStream[1];
            int res = GetOutStream(outStreams, /*useless_outSizes,*/ startIndex + i, tmp /* &seqOutStream2 */ );
            if (res != HRESULT.S_OK) return res;
            OutputStream seqOutStream2 = tmp[0];
            res = setOutStream.SetOutStream(seqOutStream2);
            if (res != HRESULT.S_OK) return res;
        }
        outStreamRes[0] = seqOutStream; // seqOutStream.Detach();
        return HRESULT.S_OK;
    }

    @Override
    public int Code(
            Vector<InputStream>  inStreams,
            //Object useless_inSizes, // const UInt64 ** inSizes ,
            int numInStreams,
            Vector<OutputStream> outStreams,
            //Object useless_outSizes, // const UInt64 ** /* outSizes */,
            int numOutStreams,
            ICompressProgressInfo progress) throws IOException {
        if (numInStreams != _bindInfo.InStreams.size() ||
                numOutStreams != _bindInfo.OutStreams.size())
            return HRESULT.E_INVALIDARG;

        // Find main coder
        int _mainCoderIndex = -1;
        int i;
        for (i = 0; i < _coders.size(); i++)
            if (_coders.get(i).IsMain) {
            _mainCoderIndex = i;
            break;
            }
        if (_mainCoderIndex < 0)
            for (i = 0; i < _coders.size(); i++)
                if (_coders.get(i).NumInStreams > 1) {
            if (_mainCoderIndex >= 0)
                return HRESULT.E_NOTIMPL;
            _mainCoderIndex = i;
                }
        if (_mainCoderIndex < 0)
            _mainCoderIndex = 0;

        // _mainCoderIndex = 0;
        // _mainCoderIndex = _coders.Size() - 1;
        CoderInfo mainCoder = _coders.get(_mainCoderIndex);

        Vector<InputStream> seqInStreams = new Vector<InputStream>(); // CObjectVector< CMyComPtr<ISequentialInStream> >
        Vector<OutputStream> seqOutStreams = new Vector<OutputStream>(); // CObjectVector< CMyComPtr<ISequentialOutStream> >
        int startInIndex = _bindInfo.GetCoderInStreamIndex(_mainCoderIndex);
        int startOutIndex = _bindInfo.GetCoderOutStreamIndex(_mainCoderIndex);
        for (i = 0; i < (int)mainCoder.NumInStreams; i++) {
            InputStream tmp [] = new  InputStream[1];
            int res = GetInStream(inStreams, /*useless_inSizes,*/ startInIndex + i, tmp /* &seqInStream */ );
            if (res != HRESULT.S_OK) return res;
            InputStream seqInStream = tmp[0];
            seqInStreams.add(seqInStream);
        }
        for (i = 0; i < (int)mainCoder.NumOutStreams; i++) {
            OutputStream tmp [] = new  OutputStream[1];
            int res = GetOutStream(outStreams, /*useless_outSizes,*/ startOutIndex + i, tmp);
            if (res != HRESULT.S_OK) return res;
            OutputStream seqOutStream = tmp[0];
            seqOutStreams.add(seqOutStream);
        }
        Vector<InputStream> seqInStreamsSpec = new Vector<InputStream>();
        Vector<OutputStream> seqOutStreamsSpec = new Vector<OutputStream>();
        for (i = 0; i < (int)mainCoder.NumInStreams; i++)
            seqInStreamsSpec.add(seqInStreams.get(i));
        for (i = 0; i < (int)mainCoder.NumOutStreams; i++)
            seqOutStreamsSpec.add(seqOutStreams.get(i));

        for (i = 0; i < _coders.size(); i++) {
            if (i == _mainCoderIndex)
                continue;
            CoderInfo coder = _coders.get(i);

            ICompressSetOutStreamSize setOutStreamSize = null;
            try
            {
                setOutStreamSize = (ICompressSetOutStreamSize)coder.Coder;

                /*int res =*/ setOutStreamSize.SetOutStreamSize(coder.OutSizePointers.get(0));
                //if (res != HRESULT.S_OK) return res;
            } catch (java.lang.ClassCastException e) {
                // nothing to do
            }
        }
        if (mainCoder.Coder != null) {
            /*int res =*/ mainCoder.Coder.Code(
                    seqInStreamsSpec.get(0),
                    seqOutStreamsSpec.get(0),
                    // TBD mainCoder.InSizePointers.get(0),
                    mainCoder.OutSizePointers.get(0),
                    progress);
            //if (res != HRESULT.S_OK) return res;
        } else {
            /*int res =*/ mainCoder.Coder2.Code(
                    seqInStreamsSpec, // &seqInStreamsSpec.Front(
                    //mainCoder.InSizePointers.Front(), // &mainCoder.InSizePointers.Front()
                    mainCoder.NumInStreams,
                    seqOutStreamsSpec, // &seqOutStreamsSpec.Front()
                    //mainCoder.OutSizePointers.Front(), // &mainCoder.OutSizePointers.Front()
                    mainCoder.NumOutStreams,
                    progress);
            //if (res != HRESULT.S_OK) return res;
        }

        OutputStream stream = seqOutStreams.firstElement();
        stream.flush();

        return HRESULT.S_OK;
    }

    @Override
    public void close() {

    }
}
