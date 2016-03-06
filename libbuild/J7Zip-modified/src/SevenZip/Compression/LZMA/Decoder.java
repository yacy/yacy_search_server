package SevenZip.Compression.LZMA;

import SevenZip.ICompressCoder;
import SevenZip.ICompressGetInStreamProcessedSize;
import SevenZip.ICompressProgressInfo;
import SevenZip.ICompressSetInStream;
import SevenZip.ICompressSetOutStreamSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import SevenZip.Compression.LZ.OutWindow;
import SevenZip.Compression.LZMA.Base;
import SevenZip.Compression.RangeCoder.BitTreeDecoder;

public class Decoder extends InputStream implements ICompressCoder, ICompressGetInStreamProcessedSize,
		ICompressSetInStream, ICompressSetOutStreamSize {
	
	private static final int kLenIdFinished 		= -1;
	private static final int kLenIdNeedInit 		= -2;
	
	private final SevenZip.Compression.RangeCoder.Decoder m_RangeDecoder = new SevenZip.Compression.RangeCoder.Decoder();
	private final OutWindow m_OutWindow 			= new OutWindow();
	
	private final short[] m_IsMatchDecoders 		= new short[Base.kNumStates << Base.kNumPosStatesBitsMax];
	private final short[] m_IsRepDecoders 			= new short[Base.kNumStates];
	private final short[] m_IsRepG0Decoders 		= new short[Base.kNumStates];
	private final short[] m_IsRepG1Decoders 		= new short[Base.kNumStates];
	private final short[] m_IsRepG2Decoders 		= new short[Base.kNumStates];
	private final short[] m_IsRep0LongDecoders 		= new short[Base.kNumStates << Base.kNumPosStatesBitsMax];
	private final short[] m_PosDecoders 			= new short[Base.kNumFullDistances - Base.kEndPosModelIndex];
	
	private final BitTreeDecoder[] m_PosSlotDecoder = new BitTreeDecoder[Base.kNumLenToPosStates];
	private final BitTreeDecoder m_PosAlignDecoder 	= new BitTreeDecoder(Base.kNumAlignBits);
	
	private final LenDecoder m_LenDecoder;
	private final LenDecoder m_RepLenDecoder;
	
	private final LiteralDecoder m_LiteralDecoder;
	
	private final int m_DictionarySize;
	private final int m_DictionarySizeCheck;
	
	private int m_posStateMask;
	
	private long _outSize = 0;
	private boolean _outSizeDefined 			= false;
	private int _remainLen; // -1 means end of stream. // -2 means need Init
	private int _rep0;
	private int _rep1;
	private int _rep2;
	private int _rep3;
	private int _state;
	
	public Decoder(byte[] properties) {
		for (int i = 0; i < Base.kNumLenToPosStates; i++)
			this.m_PosSlotDecoder[i] = new BitTreeDecoder(Base.kNumPosSlotBits);
		
		if (properties.length < 5)
			throw new IllegalArgumentException("properties.length < 5");
		int val = properties[0] & 0xFF;
		int lc = val % 9;
		int remainder = val / 9;
		int lp = remainder % 5;
		int pb = remainder / 5;
		int dictionarySize = 0;
		for (int i = 0; i < 4; i++)
			dictionarySize += ((int)(properties[1 + i]) & 0xFF) << (i * 8);
		
		// Set lc, lp, pb
		if (lc > Base.kNumLitContextBitsMax || lp > 4 || pb > Base.kNumPosStatesBitsMax)
			throw new IllegalArgumentException("lc > Base.kNumLitContextBitsMax || lp > 4 || pb > Base.kNumPosStatesBitsMax");
		if (this.m_LiteralDecoder != null) throw new NullPointerException("LiteralDecoder != null, WTF?!");
		this.m_LiteralDecoder = new LiteralDecoder(lp, lc);
		int numPosStates = 1 << pb;
		this.m_LenDecoder = new LenDecoder(numPosStates);
		this.m_RepLenDecoder = new LenDecoder(numPosStates);
		this.m_posStateMask = numPosStates - 1;
		
		// set dictionary size
		if (dictionarySize < 0)
			throw new IllegalArgumentException("dictionarySize must not be smaller than 0");
		this.m_DictionarySize = dictionarySize;
		this.m_DictionarySizeCheck = Math.max(this.m_DictionarySize, 1);
		this.m_OutWindow.Create(Math.max(this.m_DictionarySizeCheck, (1 << 12)));
		this.m_RangeDecoder.Create(1 << 20);
	}
	
	public long GetInStreamProcessedSize() {
		throw new UnknownError("GetInStreamProcessedSize");
		// return m_RangeDecoder.GetProcessedSize();
	}
	
	public void ReleaseInStream() throws IOException {
		this.m_RangeDecoder.ReleaseStream();
	}
	
	public void SetInStream(InputStream inStream) { // Common.ISequentialInStream
		this.m_RangeDecoder.SetStream(inStream);
	}
	
	public void SetOutStreamSize(long outSize /* const UInt64 *outSize*/ ) {
		this._outSizeDefined = (outSize != ICompressSetOutStreamSize.INVALID_OUTSIZE);
		if (this._outSizeDefined)
			this._outSize = outSize;
		this._remainLen = kLenIdNeedInit;
		this.m_OutWindow.Init();
	}
	
	// #ifdef _ST_MODE
	public int read() throws IOException {
		throw new IOException("LZMA Decoder - read() not implemented");
	}
	
	public int read(byte [] data, int off, int size) throws IOException  {
		if (off != 0) throw new IOException("LZMA Decoder - read(byte [] data, int off != 0, int size)) not implemented");
		
		long startPos = this.m_OutWindow.GetProcessedSize();
		this.m_OutWindow.SetMemStream(data);
		CodeSpec(size);
		Flush();
		int ret = (int)(this.m_OutWindow.GetProcessedSize() - startPos);
		if (ret == 0) ret = -1;
		return ret;
	}
	
	// #endif // _ST_MODE
	
	private void Init() throws IOException {
		this.m_OutWindow.Init(false);
		
		SevenZip.Compression.RangeCoder.Decoder.InitBitModels(this.m_IsMatchDecoders);
		SevenZip.Compression.RangeCoder.Decoder.InitBitModels(this.m_IsRep0LongDecoders);
		SevenZip.Compression.RangeCoder.Decoder.InitBitModels(this.m_IsRepDecoders);
		SevenZip.Compression.RangeCoder.Decoder.InitBitModels(this.m_IsRepG0Decoders);
		SevenZip.Compression.RangeCoder.Decoder.InitBitModels(this.m_IsRepG1Decoders);
		SevenZip.Compression.RangeCoder.Decoder.InitBitModels(this.m_IsRepG2Decoders);
		SevenZip.Compression.RangeCoder.Decoder.InitBitModels(this.m_PosDecoders);
		
		this._rep0 = this._rep1 = this._rep2 = this._rep3 = 0;
		this._state = Base.StateInit();
		
		// this.m_LiteralDecoder.Init();
		for (int i = 0; i < Base.kNumLenToPosStates; i++)
			this.m_PosSlotDecoder[i].Init();
		// this.m_LenDecoder.Init();
		// this.m_RepLenDecoder.Init();
		this.m_PosAlignDecoder.Init();
	}
	
	public void Flush() throws IOException {
		this.m_OutWindow.Flush();
	}
	
	private void ReleaseStreams() throws IOException  {
		this.m_OutWindow.ReleaseStream();
		ReleaseInStream();
	}
	
	public void CodeReal(
			InputStream inStream, // , ISequentialInStream
			OutputStream outStream, // ISequentialOutStream
			long outSize,
			ICompressProgressInfo progress // useless_progress
	) throws IOException {
		SetInStream(inStream);
		this.m_OutWindow.SetStream(outStream);
		SetOutStreamSize(outSize);
		
		do {
			int curSize = 1 << 18;
			CodeSpec(curSize);
			if (this._remainLen == kLenIdFinished)
				break;
			
			if (progress != null) {
				long inSize = this.m_RangeDecoder.GetProcessedSize();
				long nowPos64 = this.m_OutWindow.GetProcessedSize();
				progress.SetRatioInfo(inSize, nowPos64);
			}
		} while (!this._outSizeDefined || this.m_OutWindow.GetProcessedSize() < this._outSize);
		Flush();
	}
	
	public void Code(
			InputStream inStream, // , ISequentialInStream
			OutputStream outStream, // ISequentialOutStream
			long outSize,
			ICompressProgressInfo progress // useless_progress
	) throws IOException {
		try {
			CodeReal(inStream,outStream,outSize,progress);
		} finally {
			Flush();
			ReleaseStreams();
		}
	}
	
	private void CodeSpec(int curSize) throws IOException {
		if (this._outSizeDefined) {
			long rem = this._outSize - this.m_OutWindow.GetProcessedSize();
			if (curSize > rem)
				curSize = (int)rem;
		}
		
		if (this._remainLen == kLenIdFinished)
			return;
		if (this._remainLen == kLenIdNeedInit) {
			this.m_RangeDecoder.Init();
			Init();
			this._remainLen = 0;
		}
		if (curSize == 0)
			return;
		
		int rep0 = this._rep0;
		int rep1 = this._rep1;
		int rep2 = this._rep2;
		int rep3 = this._rep3;
		int state = this._state;
		byte prevByte;
		
		while(this._remainLen > 0 && curSize > 0) {
			prevByte = this.m_OutWindow.GetByte(rep0);
			this.m_OutWindow.PutByte(prevByte);
			this._remainLen--;
			curSize--;
		}
		long nowPos64 = this.m_OutWindow.GetProcessedSize();
		if (nowPos64 == 0) {
			prevByte = 0;
		} else {
			prevByte = this.m_OutWindow.GetByte(0);
		}
		
		while(curSize > 0) {
			if (this.m_RangeDecoder.bufferedStream.WasFinished())
				throw new IOException("m_RangeDecoder.bufferedStream was finised");
				// return HRESULT.S_FALSE;
			int posState = (int)nowPos64 & this.m_posStateMask;
			
			if (this.m_RangeDecoder.DecodeBit(this.m_IsMatchDecoders, (state << Base.kNumPosStatesBitsMax) + posState) == 0) {
				LiteralDecoder.Decoder2 decoder2 = this.m_LiteralDecoder.GetDecoder((int)nowPos64, prevByte);
				if (!Base.StateIsCharState(state)) {
					prevByte = decoder2.DecodeWithMatchByte(this.m_RangeDecoder, this.m_OutWindow.GetByte(rep0));
				} else {
					prevByte = decoder2.DecodeNormal(this.m_RangeDecoder);
				}
				this.m_OutWindow.PutByte(prevByte);
				state = Base.StateUpdateChar(state);
				curSize--;
				nowPos64++;
			} else {
				int len;
				if (this.m_RangeDecoder.DecodeBit(this.m_IsRepDecoders, state) == 1) {
					len = 0;
					if (this.m_RangeDecoder.DecodeBit(this.m_IsRepG0Decoders, state) == 0) {
						if (this.m_RangeDecoder.DecodeBit(this.m_IsRep0LongDecoders, (state << Base.kNumPosStatesBitsMax) + posState) == 0) {
							state = Base.StateUpdateShortRep(state);
							len = 1;
						}
					} else {
						int distance;
						if (this.m_RangeDecoder.DecodeBit(this.m_IsRepG1Decoders, state) == 0) {
							distance = rep1;
						} else {
							if (this.m_RangeDecoder.DecodeBit(this.m_IsRepG2Decoders, state) == 0) {
								distance = rep2;
							} else {
								distance = rep3;
								rep3 = rep2;
							}
							rep2 = rep1;
						}
						rep1 = rep0;
						rep0 = distance;
					}
					if (len == 0) {
						len = this.m_RepLenDecoder.Decode(this.m_RangeDecoder, posState) + Base.kMatchMinLen;
						state = Base.StateUpdateRep(state);
					}
				} else {
					rep3 = rep2;
					rep2 = rep1;
					rep1 = rep0;
					len = Base.kMatchMinLen + this.m_LenDecoder.Decode(this.m_RangeDecoder, posState);
					state = Base.StateUpdateMatch(state);
					int posSlot = this.m_PosSlotDecoder[Base.GetLenToPosState(len)].Decode(this.m_RangeDecoder);
					if (posSlot >= Base.kStartPosModelIndex) {
						int numDirectBits = (posSlot >> 1) - 1;
						rep0 = ((2 | (posSlot & 1)) << numDirectBits);
						if (posSlot < Base.kEndPosModelIndex) {
							rep0 += BitTreeDecoder.ReverseDecode(
									this.m_PosDecoders,
									rep0 - posSlot - 1,
									this.m_RangeDecoder,
									numDirectBits);
						} else {
							rep0 += (this.m_RangeDecoder.DecodeDirectBits(numDirectBits - Base.kNumAlignBits) << Base.kNumAlignBits);
							rep0 += this.m_PosAlignDecoder.ReverseDecode(this.m_RangeDecoder);
							if (rep0 < 0) {
								if (rep0 == -1)
									break;
								throw new IOException("rep0 == -1");
								// return HRESULT.S_FALSE;
							}
						}
					} else {
						rep0 = posSlot;
					}
				}
				
				if (rep0 >= nowPos64 || rep0 >= this.m_DictionarySizeCheck) {
					// m_OutWindow.Flush();
					this._remainLen = kLenIdFinished;
					throw new IOException("rep0 >= nowPos64 || rep0 >= m_DictionarySizeCheck");
					// return HRESULT.S_FALSE;
				}
				
				
				int locLen = len;
				if (len > curSize)
					locLen = curSize;
				// if (!m_OutWindow.CopyBlock(rep0, locLen))
					//    return HRESULT.S_FALSE;
				this.m_OutWindow.CopyBlock(rep0, locLen);
				prevByte = this.m_OutWindow.GetByte(0);
				curSize -= locLen;
				nowPos64 += locLen;
				len -= locLen;
				if (len != 0) {
					this._remainLen = len;
					break;
				}
			}
		}
		
		if (this.m_RangeDecoder.bufferedStream.WasFinished())
			throw new IOException("m_RangeDecoder.bufferedStream was finised");
		
		this._rep0 = rep0;
		this._rep1 = rep1;
		this._rep2 = rep2;
		this._rep3 = rep3;
		this._state = state;
	}
	
	private class LenDecoder {
		
		private final short[] m_Choice = new short[2];
		private final BitTreeDecoder[] m_LowCoder = new BitTreeDecoder[Base.kNumPosStatesMax];
		private final BitTreeDecoder[] m_MidCoder = new BitTreeDecoder[Base.kNumPosStatesMax];
		private final BitTreeDecoder m_HighCoder = new BitTreeDecoder(Base.kNumHighLenBits);
		private int m_NumPosStates = 0;
		
		public LenDecoder(int numPosStates) {
			while (this.m_NumPosStates < numPosStates) {
				this.m_LowCoder[this.m_NumPosStates] = new BitTreeDecoder(Base.kNumLowLenBits);
				this.m_MidCoder[this.m_NumPosStates] = new BitTreeDecoder(Base.kNumMidLenBits);
				this.m_NumPosStates++;
			}
			SevenZip.Compression.RangeCoder.Decoder.InitBitModels(this.m_Choice);
			for (int posState = 0; posState < this.m_NumPosStates; posState++) {
				this.m_LowCoder[posState].Init();
				this.m_MidCoder[posState].Init();
			}
			this.m_HighCoder.Init();
		}
		
		public int Decode(SevenZip.Compression.RangeCoder.Decoder rangeDecoder, int posState) throws IOException {
			if (rangeDecoder.DecodeBit(this.m_Choice, 0) == 0)
				return this.m_LowCoder[posState].Decode(rangeDecoder);
			int symbol = Base.kNumLowLenSymbols;
			if (rangeDecoder.DecodeBit(this.m_Choice, 1) == 0)
				symbol += this.m_MidCoder[posState].Decode(rangeDecoder);
			else
				symbol += Base.kNumMidLenSymbols + this.m_HighCoder.Decode(rangeDecoder);
			return symbol;
		}
	}
	
	private static class LiteralDecoder {
		
		private final Decoder2[] m_Coders;
		private final int m_NumPrevBits;
		private final int m_NumPosBits;
		private final int m_PosMask;
		
		public LiteralDecoder(int numPosBits, int numPrevBits) {
			this.m_NumPrevBits = numPrevBits;
			this.m_NumPosBits = numPosBits;
			this.m_PosMask = (1 << numPosBits) - 1;
			final int numStates = 1 << (this.m_NumPrevBits + this.m_NumPosBits);
			this.m_Coders = new Decoder2[numStates];
			for (int i = 0; i < numStates; i++)
				this.m_Coders[i] = new Decoder2();
		}
		
		private Decoder2 GetDecoder(int pos, byte prevByte) {
			final int indexHigh = (pos & this.m_PosMask) << this.m_NumPrevBits;
			final int indexLow = (prevByte & 0xFF) >>> (8 - this.m_NumPrevBits);
			return this.m_Coders[indexHigh + indexLow];
		}
		
		private static class Decoder2 {
			final short[] m_Decoders = new short[0x300];
			
			public Decoder2() {
				SevenZip.Compression.RangeCoder.Decoder.InitBitModels(this.m_Decoders);
			}
			
			public byte DecodeNormal(SevenZip.Compression.RangeCoder.Decoder rangeDecoder) throws IOException {
				int symbol = 1;
				do {
					symbol = (symbol << 1) | rangeDecoder.DecodeBit(this.m_Decoders, symbol);
				} while (symbol < 0x100);
				return (byte)symbol;
			}
			
			public byte DecodeWithMatchByte(SevenZip.Compression.RangeCoder.Decoder rangeDecoder, byte matchByte) throws IOException {
				int symbol = 1, matchBit, bit;
				do {
					matchBit = (matchByte >> 7) & 1;
					matchByte <<= 1;
					bit = rangeDecoder.DecodeBit(this.m_Decoders, ((1 + matchBit) << 8) + symbol);
					symbol = (symbol << 1) | bit;
					if (matchBit != bit) {
						while (symbol < 0x100)
							symbol = (symbol << 1) | rangeDecoder.DecodeBit(this.m_Decoders, symbol);
						break;
					}
				} while (symbol < 0x100);
				return (byte)symbol;
			}
		}
	}
}
