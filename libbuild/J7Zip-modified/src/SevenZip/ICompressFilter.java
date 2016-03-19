package SevenZip;

public interface ICompressFilter {
  int Init();
  int Filter(byte [] data, int size);
  // Filter return outSize (UInt32)
  // if (outSize <= size): Filter have converted outSize bytes
  // if (outSize > size): Filter have not converted anything.
  //      and it needs at least outSize bytes to convert one block 
  //      (it's for crypto block algorithms).

}
