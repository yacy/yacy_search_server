package SevenZip;

public interface ICompressProgressInfo {
    public static final long INVALID = -1;
    void SetRatioInfo(long inSize, long outSize);
}
