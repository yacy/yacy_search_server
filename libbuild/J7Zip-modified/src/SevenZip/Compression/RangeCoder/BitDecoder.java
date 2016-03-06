package SevenZip.Compression.RangeCoder;

import SevenZip.Compression.RangeCoder.Decoder;


public class BitDecoder extends BitModel
{
    public BitDecoder(int num) {
        super(num);
    }
  public int Decode(Decoder decoder)  throws java.io.IOException
  {
    int newBound = (decoder.Range >>> kNumBitModelTotalBits) * this.Prob;
    if ((decoder.Code ^ 0x80000000) < (newBound ^ 0x80000000))
    {
      decoder.Range = newBound;
      this.Prob += (kBitModelTotal - this.Prob) >>> numMoveBits;
      if ((decoder.Range & kTopMask) == 0)
      {
        decoder.Code = (decoder.Code << 8) | decoder.bufferedStream.read();
        decoder.Range <<= 8;
      }
      return 0;
    }
    else
    {
      decoder.Range -= newBound;
      decoder.Code -= newBound;
      this.Prob -= (this.Prob) >>> numMoveBits;
      if ((decoder.Range & kTopMask) == 0)
      {
        decoder.Code = (decoder.Code << 8) | decoder.bufferedStream.read();
        decoder.Range <<= 8;
      }
      return 1;
    }
  }
}
