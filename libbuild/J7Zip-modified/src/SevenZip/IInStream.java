package SevenZip;

public abstract class IInStream extends java.io.InputStream
{
  static public final int STREAM_SEEK_SET	= 0;
  static public final int STREAM_SEEK_CUR	= 1;
  // static public final int STREAM_SEEK_END	= 2;
  public abstract long Seek(long offset, int seekOrigin)  throws java.io.IOException ;
  
}

