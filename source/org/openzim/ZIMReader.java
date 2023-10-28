/*
 * Copyright (C) 2011 Arunesh Mathur
 *
 * This file is a part of zimreader-java.
 *
 * zimreader-java is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3.0 as
 * published by the Free Software Foundation.
 *
 * zimreader-java is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with zimreader-java.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.openzim;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.tukaani.xz.SingleXZInputStream;
import com.github.luben.zstd.ZstdInputStream;

/**
 * @author Arunesh Mathur
 *         A ZIMReader that reads data from the ZIMFile
 *
 * @author Michael Christen
 *         Proof-Reading, unclustering, refactoring,
 *         naming adoption to https://wiki.openzim.org/wiki/ZIM_file_format,
 *         change of Exception handling, 
 *         extension to more attributes as defined in spec (bugfix for mime type loading)
 *         bugfix to long parsing (prevented reading of large files),
 *         added extended cluster size parsing
 *         added ZStandard compression parsing (cluster type 5)
 */
public class ZIMReader {

    private final ZIMFile mFile;
    private RandomAccessFileZIMInputStream mReader;

    public static abstract class DirectoryEntry {

        public final int mimetype;
        public final char namespace;
        public final int cluster_number;
        public final String url;
        public final String title;
        public final long urlListindex;

        public DirectoryEntry(
                final int mimeType, final char namespace,
                final int cluster_number,
                final String url, final String title,
                final long index) {
            this.mimetype = mimeType;
            this.namespace = namespace;
            this.cluster_number = cluster_number;
            this.url = url;
            this.title = title;
            this.urlListindex = index;
        }

    }

    public static class ArticleEntry extends DirectoryEntry {

        public final int cluster_number;
        public final int blob_number;

        public ArticleEntry(
                final int mimeType, final char namespace,
                final int cluster_number, final int blob_number,
                final String url, final String title,
                final long urlListindex) {
            super(mimeType, namespace, cluster_number, url, title, urlListindex);
            this.cluster_number = cluster_number;
            this.blob_number = blob_number;
        }

    }

    public static class RedirectEntry extends DirectoryEntry {

        public final long redirect_index;

        public RedirectEntry(final int mimeType, final char namespace,
                final long redirect_index, final String url, final String title,
                final long urlListindex) {
            super(mimeType, namespace, 0, url, title, urlListindex);
            this.redirect_index = redirect_index;
        }

    }

    public ZIMReader(final ZIMFile file) {
        this.mFile = file;
        try {
            this.mReader = new RandomAccessFileZIMInputStream(new RandomAccessFile(this.mFile, "r"));
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public ZIMFile getZIMFile() {
        return this.mFile;
    }

    public String getURLByURLOrder(int entryNumber) throws IOException {

        // Move to the spot where URL's are listed
        this.mReader.seek(this.mFile.header_urlPtrPos + 8L * entryNumber);

        // The position of URL i
        long pos = this.mReader.readEightLittleEndianBytesLong();

        // Move to the position of URL i
        this.mReader.seek(pos);

        // Article or Redirect entry?
        int mimeType = this.mReader.readTwoLittleEndianBytesInt();

        if (mimeType == 65535) {
            this.mReader.seek(pos + 12);
            return this.mReader.readZeroTerminatedString();
        } else {
            this.mReader.seek(pos + 16);
            return this.mReader.readZeroTerminatedString();
        }
    }

    public String getURLByTitleOrder(int entryNumber) throws IOException {

        // Move to the spot where URL's are listed
        this.mReader.seek(this.mFile.header_titlePtrPos + 8L * entryNumber);

        // The articleNumber of the position of URL i
        int articleNumber = this.mReader.readFourLittleEndianBytesInt();

        this.mReader.seek(this.mFile.header_urlPtrPos + (8L * (articleNumber)));

        // The position of URL i
        long pos = this.mReader.readEightLittleEndianBytesLong();
        this.mReader.seek(pos);

        // Article or Redirect entry?
        int mimeType = this.mReader.readTwoLittleEndianBytesInt();

        if (mimeType == 65535) {
            this.mReader.seek(pos + 12);
            return this.mReader.readZeroTerminatedString();
        } else {
            this.mReader.seek(pos + 16);
            return this.mReader.readZeroTerminatedString();
        }
    }

    // position must be the seek position for the title in the Title Pointer List
    private DirectoryEntry getDirectoryInfoAtTitlePosition(final long position) throws IOException {

        // At the appropriate position in the titlePtrPos
        this.mReader.seek(position);

        // Get value of article at index
        int pointer_to_the_URL_pointer = this.mReader.readFourLittleEndianBytesInt();

        // Move to the position in urlPtrPos
        this.mReader.seek(this.mFile.header_urlPtrPos + 8 * pointer_to_the_URL_pointer);

        // Get value of article in urlPtrPos
        long pointer_to_the_directory_entry = this.mReader.readEightLittleEndianBytesLong();

        // Go to the location of the directory entry
        this.mReader.seek(pointer_to_the_directory_entry);

        // read the Content Entry
        final int type = this.mReader.readTwoLittleEndianBytesInt(); // 2, 0xffff for redirect
        this.mReader.read();                                         // 1, ignore, parameter length not used
        final char namespace = (char) this.mReader.read();           // 1
        this.mReader.readFourLittleEndianBytesInt();                 // 4, ignore, revision not used

        // Article or Redirect entry
        if (type == 65535) {
            final int redirectIndex = this.mReader.readFourLittleEndianBytesInt();
            final String url = this.mReader.readZeroTerminatedString();
            String title = this.mReader.readZeroTerminatedString();
            title = title.equals("") ? url : title;
            return new RedirectEntry(type, namespace, redirectIndex,
                    url, title, (position - this.mFile.header_urlPtrPos) / 8);
        } else {
            final int cluster_number = this.mReader.readFourLittleEndianBytesInt(); // 4
            final int blob_number = this.mReader.readFourLittleEndianBytesInt();    // 4
            final String url = this.mReader.readZeroTerminatedString();             // zero terminated
            String title = this.mReader.readZeroTerminatedString();                 // zero terminated
            title = title.equals("") ? url : title;

            return new ArticleEntry(
                    type, namespace,
                    cluster_number, blob_number,
                    url, title, (position - this.mFile.header_urlPtrPos) / 8);
        }

    }

    public DirectoryEntry getDirectoryInfo(final int entryNumber) throws IOException {
        if (entryNumber >= this.mFile.header_entryCount) throw new IOException("entryNumber exceeds entryCount");
        return getDirectoryInfoAtTitlePosition(this.mFile.header_titlePtrPos + 4 * entryNumber);
    }

    // Gives the minimum required information needed for the given articleName
    // This makes a binary search on the article name entry list.
    public DirectoryEntry getDirectoryInfo(final char namespace, String articleName) throws IOException {

        DirectoryEntry entry;
        String cmpStr;
        final int numberOfArticles = this.mFile.header_entryCount;
        long beg = this.mFile.header_titlePtrPos, end = beg + (numberOfArticles * 4), mid;

        articleName = namespace + "/" + articleName;

        while (beg <= end) {
            mid = beg + 4 * (((end - beg) / 4) / 2);
            entry = getDirectoryInfoAtTitlePosition(mid);
            if (entry == null) {
                return null;
            }
            cmpStr = entry.namespace + "/" + entry.url;
            if (articleName.compareTo(cmpStr) < 0) {
                end = mid - 4;

            } else if (articleName.compareTo(cmpStr) > 0) {
                beg = mid + 4;

            } else {
                return entry;
            }
        }

        return null;
    }

    public byte[] getArticleData(final DirectoryEntry directoryInfo) throws IOException {

        // fail fast
        if (directoryInfo == null) return null;
        if (directoryInfo.getClass() != ArticleEntry.class) return null;

        // This is now an article, so thus we can cast to ArticleEntry
        final ArticleEntry article = (ArticleEntry) directoryInfo;

        // Move to the cluster entry in the clusterPtrPos
        this.mReader.seek(this.mFile.header_clusterPtrPos + article.cluster_number * 8L);

        // Read the location of the cluster
        final long clusterPos = this.mReader.readEightLittleEndianBytesLong();

        // Move to the cluster
        this.mReader.seek(clusterPos);

        // Read the first byte, for compression information
        final int compressionType = this.mReader.read();

        // Check the compression type that was read
        // type = 1 uncompressed
        if (compressionType <= 1 || compressionType == 8 || compressionType == 9) {
            boolean extended = compressionType > 1;
            return readClusterEntry(this.mReader, article.blob_number, extended);
        }
        // 2 for zlib and 3 for bzip2 (removed)

        // LZMA2 compressed data
        if (compressionType == 4 || compressionType == 12) {
            boolean extended = compressionType == 12;
            // Create a dictionary with size 40MiB, the zimlib uses this size while creating
            SingleXZInputStream xzReader= new SingleXZInputStream(this.mReader, 41943040);
            return readClusterEntry(xzReader, article.blob_number, extended);
        }

        // Zstandard compressed data
        if (compressionType == 5 || compressionType == 13) {
            boolean extended = compressionType == 13;
            ZstdInputStream zReader = new ZstdInputStream(this.mReader);
            return readClusterEntry(zReader, article.blob_number, extended);
        }

        return null;
    }

    private static byte[] readClusterEntry(InputStream is, int blob_number, boolean extended) throws IOException {

        // Read the first 4(8) bytes to find out the number of articles
        byte[] buffer = new byte[extended ? 8 : 4];

        // The first four (eight) bytes are the offset of the zeroth blob
        is.read(buffer);
        long firstOffset = extended? RandomAccessFileZIMInputStream.toEightLittleEndianLong(buffer) : RandomAccessFileZIMInputStream.toFourLittleEndianInteger(buffer);

        // The number of blobs can be computed by the offset
        // the actual number is one less because there is one more offset entry than the actual number
        // to identify the end of the last blob.
        long numberOfBlobs1 = extended ? firstOffset / 8 : firstOffset / 4;

        // The blobNumber has to be lesser than the numberOfBlobs - 1
        // the blob numbers start with 0 even if the documentation states it is "the first blob".
        assert blob_number < numberOfBlobs1 - 1;
        long offset1;
        if (blob_number == 0) {
            // The first offset is what we read earlier
            offset1 = firstOffset;
        } else {
            // skip one less than required to get to the offset entry because the first entry is already read
            RandomAccessFileZIMInputStream.skipFully(is, (blob_number - 1) * (extended ? 8 : 4));
            is.read(buffer);
            offset1 = extended? RandomAccessFileZIMInputStream.toEightLittleEndianLong(buffer) : RandomAccessFileZIMInputStream.toFourLittleEndianInteger(buffer);
        }
        is.read(buffer);
        long offset2 = extended? RandomAccessFileZIMInputStream.toEightLittleEndianLong(buffer) : RandomAccessFileZIMInputStream.toFourLittleEndianInteger(buffer);
        long blob_size = offset2 - offset1;
        byte[] entry = new byte[(int) blob_size]; // TODO: we should be able to read blobs larger than MAXINT
        // we must do two skip steps: first to the end of the offset list and second to the start of the blob
        // - the whole number of offset list entries is numberOfBlobs1, which includes the extra entry for the end offset
        // - the number of offset entries that we alreay read now is article.blob_number + 2 (in any case at least 2)
        // - the remaining number of offset entries to skip is therefore numberOfBlobs1 - (article.blob_number + 2)
        // - the addon skip of number of bytes to the start of the entry is offset1 - firstoffset with firstoffset = 4 * numberOfBlobs1
        // - the full skip length is 4 * (numberOfBlobs1 - (article.blob_number + 2)) + offset1 - 4 * numberOfBlobs1
        //   = offset1 - 4 * (article.blob_number + 2)
        RandomAccessFileZIMInputStream.skipFully(is, (offset1 - (extended ? 8 : 4) * (blob_number + 2)));
        is.read(entry, 0, entry.length);

        return entry;
    }

}
