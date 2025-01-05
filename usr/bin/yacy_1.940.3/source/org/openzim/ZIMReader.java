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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

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
 *         extension to more attributes as defined in spec (bugfix for mime type loading),
 *         bugfix to long parsing (prevented reading of large files),
 *         added extended cluster size parsing,
 *         added ZStandard compression parsing (cluster type 5),
 *         added cluster index and cluster iteration for efficient blob extraction
 */
public class ZIMReader {

    private final static int MAX_CLUSTER_CACHE_SIZE = 100;
    public final static String[] METADATA_KEYS = new String[] {
            "Name", "Title", "Creator", "Publisher", "Date", "Description", "LongDescription",
            "Language", "License", "Tags", "Relation", "Flavour", "Source", "Counter", "Scraper"
    };

    private final ZIMFile mFile;
    private List<ArticleEntry> allArticlesCache = null;
    private Map<Integer, Map<Integer, ArticleEntry>> indexedArticlesCache = null;
    private final ArrayList<Cluster> clusterCache = new ArrayList<>();

    public class DirectoryEntry {

        private final int mimetype;
        public final char namespace;
        public final String url;
        public final String title;
        public final int urlListindex;

        public DirectoryEntry(
                final int urlListindex,
                final char namespace, final String url, final String title, final int mimeType) {
            assert url != null;
            assert title != null;
            this.mimetype = mimeType;
            this.namespace = namespace;
            this.url = url;
            this.title = title;
            this.urlListindex = urlListindex;
        }

        public String getMimeType() {
            return mFile.getMimeType(this.mimetype);
        }

    }

    public class ArticleEntry extends DirectoryEntry {

        public final int cluster_number;
        public final int blob_number;

        public ArticleEntry(
                final int urlListindex,
                final char namespace, final String url, final String title, final int mimeType,
                final int cluster_number, final int blob_number) {
            super(urlListindex, namespace, url, title, mimeType);
            this.cluster_number = cluster_number;
            this.blob_number = blob_number;
        }

    }

    public class RedirectEntry extends DirectoryEntry {

        public final int redirect_index;

        public RedirectEntry(
                final int urlListindex,
                final char namespace, final String url, final String title, final int mimeType,
                final int redirect_index) {
            super(urlListindex, namespace, url, title, mimeType);
            this.redirect_index = redirect_index;
        }

    }

    public class ArticleBlobEntry {

        public final ArticleEntry article;
        public final byte[] blob;

        public ArticleBlobEntry(final ArticleEntry article, final byte[] blob) {
            assert article != null;
            assert blob != null;
            this.article = article;
            this.blob = blob;
        }

    }

    public ZIMReader(final ZIMFile file) {
        this.mFile = file;
    }

    public ZIMFile getZIMFile() {
        return this.mFile;
    }

    public List<ArticleEntry> getAllArticles() throws IOException {
        if (this.allArticlesCache != null) return allArticlesCache;
        List<ArticleEntry> list = new ArrayList<>();
        for (int i = 0; i < this.mFile.header_entryCount; i++) {
            DirectoryEntry de = getDirectoryInfo(i);
            if (de instanceof ArticleEntry) list.add((ArticleEntry) de);
        }
        this.allArticlesCache = list;
        return list;
    }

    public Map<Integer, Map<Integer, ArticleEntry>> getIndexedArticles(List<ArticleEntry> list) {
        if (this.indexedArticlesCache != null) return indexedArticlesCache;
        Map<Integer, Map<Integer, ArticleEntry>> index = new HashMap<>();
        for (ArticleEntry entry: list) {
            Map<Integer, ArticleEntry> cluster = index.get(entry.cluster_number);
            if (cluster == null) {
                cluster = new HashMap<Integer, ArticleEntry>();
                index.put(entry.cluster_number, cluster);
            }
            cluster.put(entry.blob_number, entry);
        }
        this.indexedArticlesCache = index;
        return index;
    }

    /**
     * A cluster iterator is the most efficient way to read all documents.
     * Because iteration over the documents will cause that clusters are
     * decompressed many times (as much as documents are in the cluster)
     * it makes more sense to iterate over the clusters and not over the
     * documents. That requires that we maintain an index of document entries
     * which can be used to find out which documents are actually contained
     * in a cluster. Reading of all document entries at first will create some
     * waiting time at the beginning of the iteration, but this is not a on-top
     * computing time, just concentrated for once at the beginning of all
     * document fetch times. If the zim file is very large, this requires
     * some extra RAM to cache the indexed document entries.
     */
    public class ClusterIterator implements Iterator<ArticleBlobEntry> {

        private Map<Integer, Map<Integer, ArticleEntry>> index;
        private Cluster cluster;
        private int clusterCounter;
        private int blobCounter;

        public ClusterIterator() throws IOException {
            List<ArticleEntry> list = getAllArticles();
            this.index = getIndexedArticles(list);
            this.clusterCounter = 0;
            this.blobCounter = 0;
            this.cluster = null; // not loaded
        }

        private final void loadCluster() {
            if (this.cluster == null) {
                // load cluster
                try {
                    this.cluster = new Cluster(this.clusterCounter);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public boolean hasNext() {
            if (this.clusterCounter >= mFile.header_clusterCount) return false;
            loadCluster(); // ensure cluster is loaded
            return this.blobCounter < this.cluster.blobs.size();
        }

        @Override
        public ArticleBlobEntry next() {
            Map<Integer, ArticleEntry> clusterMap = this.index.get(this.clusterCounter);
            ArticleEntry ae = clusterMap.get(this.blobCounter);
            loadCluster(); // ensure cluster is loaded
            ArticleBlobEntry abe = new ArticleBlobEntry(ae, this.cluster.getBlob(this.blobCounter));

            // increase the counter(s)
            this.blobCounter++;
            if (this.blobCounter >= this.cluster.blobs.size()) {
                this.clusterCounter++;
                this.cluster = null; // unload cluster
                this.blobCounter = 0;
            }

            return abe;
        }
    }

    public final String getMetadata(String key) throws IOException {
        DirectoryEntry de = getDirectoryInfo('M', key);
        if (de == null) return null; // metadata not found; that would be normal
        byte[] val = getArticleData(de);
        if (val == null) return null; // article data not found: that is not normal
        if (val.length == 0) return null; // that empty string is a proper value, however, not usable for a client
        return new String(val, StandardCharsets.UTF_8);
    }

    public DirectoryEntry getMainDirectoryEntry() throws IOException {
        DirectoryEntry de = getDirectoryInfo(this.mFile.header_mainPage);
        if (de instanceof RedirectEntry) {
            // resolve redirect to get the actual main page
            int redirect = ((RedirectEntry) de).redirect_index;
            de = getDirectoryInfo(redirect);
        }
        // For the main entry we demand a "text/html" mime type.
        // Many zim files do not provide this as the main file, which is strange (maybe lazy/irresponsibe)
        // Because the main entry is important for a validation, we seek for one entry which may
        // be proper for indexing.
        int entryNumner = 0;
        while (!de.getMimeType().equals("text/html") && entryNumner < this.mFile.header_entryCount) {
            de = getDirectoryInfo(entryNumner);
            entryNumner++;
            if (de.namespace != 'C' && de.namespace != 'A') continue;
            if (!(de instanceof ArticleEntry)) continue;
            if (!de.getMimeType().equals("text/html")) continue;
            if (de.url.contains("404") || de.title.contains("404") || de.title.contains("301")) continue; // is a pain
            return de;
        }
        return de;
    }

    public String getURLByURLOrder(final int entryNumber) throws IOException {

        // The position of URL i
        long pos = this.mFile.getURLPtr(entryNumber);
        this.mFile.mReader.seek(pos);

        // Article or Redirect entry?
        int mimeType = this.mFile.mReader.readTwoLittleEndianBytesInt();

        if (mimeType == 65535) {
            this.mFile.mReader.seek(pos + 12);
            return this.mFile.mReader.readZeroTerminatedString();
        } else {
            this.mFile.mReader.seek(pos + 16);
            return this.mFile.mReader.readZeroTerminatedString();
        }
    }

    public String getURLByTitleOrder(final int entryNumber) throws IOException {

        // The articleNumber of the position of URL i
        int articleNumber = this.mFile.getTitlePtr(entryNumber);
        return getURLByURLOrder(articleNumber);
    }

    public DirectoryEntry getDirectoryInfo(final int entryNumber) throws IOException {

        // Get value of article at index
        int pointer_to_the_URL_pointer = this.mFile.getTitlePtr(entryNumber);

        // Get value of article in urlPtrPos
        long pointer_to_the_directory_entry = this.mFile.getURLPtr(pointer_to_the_URL_pointer);

        // Go to the location of the directory entry
        this.mFile.mReader.seek(pointer_to_the_directory_entry);

        // read the Content Entry
        final int type = this.mFile.mReader.readTwoLittleEndianBytesInt(); // 2, 0xffff for redirect
        this.mFile.mReader.read();                                         // 1, ignore, parameter length not used
        final char namespace = (char) this.mFile.mReader.read();           // 1
        this.mFile.mReader.readFourLittleEndianBytesInt();                 // 4, ignore, revision not used

        // Article or Redirect entry
        if (type == 65535) {
            final int redirectIndex = this.mFile.mReader.readFourLittleEndianBytesInt();
            final String url = this.mFile.mReader.readZeroTerminatedString();
            String title = this.mFile.mReader.readZeroTerminatedString();
            title = title.equals("") ? url : title;
            return new RedirectEntry(entryNumber, namespace, url, title, type, redirectIndex);
        } else {
            final int cluster_number = this.mFile.mReader.readFourLittleEndianBytesInt(); // 4
            final int blob_number = this.mFile.mReader.readFourLittleEndianBytesInt();    // 4
            final String url = this.mFile.mReader.readZeroTerminatedString();             // zero terminated
            String title = this.mFile.mReader.readZeroTerminatedString();                 // zero terminated
            title = title.equals("") ? url : title;
            return new ArticleEntry(entryNumber, namespace, url, title, type, cluster_number, blob_number);
        }
    }

    // Gives the minimum required information needed for the given articleName
    // This makes a binary search on the article name entry list.
    public DirectoryEntry getDirectoryInfo(final char namespace, String articleName) throws IOException {

        DirectoryEntry entry;
        String cmpStr;
        final int numberOfArticles = this.mFile.header_entryCount;
        int beg = 0, end = numberOfArticles, mid;

        articleName = namespace + "/" + articleName;

        while (beg <= end) {
            mid = beg + ((end - beg) / 2);
            entry = getDirectoryInfo(mid);
            if (entry == null) {
                return null;
            }
            cmpStr = entry.namespace + "/" + entry.title;
            if (articleName.compareTo(cmpStr) < 0) {
                end = mid - 1;
            } else if (articleName.compareTo(cmpStr) > 0) {
                beg = mid + 1;
            } else {
                return entry;
            }
        }

        return null;
    }

    public Cluster getCluster(int clusterNumber) throws IOException {
        for (int i = 0; i < this.clusterCache.size(); i++) {
            Cluster c = clusterCache.get(i);
            if (c.cluster_number == clusterNumber) return c;
        }

        // cache miss
        Cluster c = new Cluster(clusterNumber);

        // check cache size
        if (clusterCache.size() >= MAX_CLUSTER_CACHE_SIZE) {
            // remove one entry: the first entry is the oldest entry
            this.clusterCache.remove(0);
        }

        this.clusterCache.add(c);
        return c;
    }

    /**
     * Cluster class is required to read a whole cluster with all documents inside at once.
     * This is a good thing because reading single documents from a cluster requires that the
     * cluster is decompressed every time again and again. Doing whole clusters with all documents
     * at once means that the decompression is much more efficient because it is done only once.
     * This can of course only be done, if:
     * - we want to iterate through all documents of a ZIM file
     * - we have reverse indexed all directory entries to be able to assign metadata to cluster documents
     * 
     * Reference implementation: https://github.com/openzim/libzim/blob/main/src/cluster.cpp
     */
    private class Cluster {

        private int cluster_number; // used to identify the correct cache entry
        private List<byte[]> blobs;
        private boolean extended;

        public Cluster(int cluster_number) throws IOException {
            this.cluster_number = cluster_number;

            // open the cluster and make a Input Stream with the proper decompression type
            final long clusterPos = mFile.geClusterPtr(cluster_number);
            mFile.mReader.seek(clusterPos);
            final int compressionType = mFile.mReader.read();
            InputStream is = null;
            if (compressionType <= 1 || compressionType == 8 || compressionType == 9) {
                extended = compressionType > 1;
                is = mFile.mReader;
            }
            if (compressionType == 4 || compressionType == 12) {
                extended = compressionType == 12;
                is = new SingleXZInputStream(mFile.mReader, 41943040);
            }

            if (compressionType == 5 || compressionType == 13) {
                extended = compressionType == 13;
                is = new ZstdInputStream(mFile.mReader);
            }
            if (is == null) throw new IOException("compression type unknown: " + compressionType);

            // read the offset list
            List<Long> offsets = new ArrayList<>();
            byte[] buffer = new byte[extended ? 8 : 4];

            // the first offset is a pointer to the first blob, it therefore also points to the
            // end of the offset list. Consequently, we name it end_offset because it points there:
            is.read(buffer);
            long end_offset = extended ? RandomAccessFileZIMInputStream.toEightLittleEndianLong(buffer) : RandomAccessFileZIMInputStream.toFourLittleEndianInteger(buffer);

            // even if it is the end of the offsets, it is the first offset pointer in the list of offsets
            offsets.add(end_offset);

            // when divided by the pointer size, the offset to the first blob is the number of offsets pointers
            int offset_count = (int) (end_offset / (extended ? 8 : 4));

            // there are now (offset_count - 1) remaining pointers left to read.
            // however, the last offset does not point to a final blob, it points to the end
            // of the last blob. The number of blobs is therefore offset_count - 1
            for (int i = 0; i < offset_count - 1; i++) {
                is.read(buffer);
                long l = extended ? RandomAccessFileZIMInputStream.toEightLittleEndianLong(buffer) : RandomAccessFileZIMInputStream.toFourLittleEndianInteger(buffer);
                offsets.add(l);
            }

            // now all document sizes are known because they are defined by the offset deltas
            // the seek position should be now at the beginning of the first document
            this.blobs = new ArrayList<>();
            for (int i = 0; i < offsets.size() - 1; i++) { // loop until the size - 1 because the last offset is the end of the last document
                int length = (int) (offsets.get(i + 1) - offsets.get(i)); // yes the maximum document length is 2GB, for now
                byte[] b = new byte[length];
                RandomAccessFileZIMInputStream.readFully(is, b);
                this.blobs.add(b);
            }
        }

        public byte[] getBlob(int i) {
            return this.blobs.get(i);
        }

        @SuppressWarnings("unused")
        public int getSize() {
            return this.blobs.size();
        }
    }

    public byte[] getArticleData(final DirectoryEntry directoryInfo) throws IOException {

        // fail fast
        if (directoryInfo == null) return null;
        if (directoryInfo.getClass() != ArticleEntry.class) return null;

        // This is now an article, so thus we can cast to ArticleEntry
        final ArticleEntry article = (ArticleEntry) directoryInfo;

        // Read the cluster
        Cluster c = getCluster(article.cluster_number);

        // read the blob
        byte[] blob = c.getBlob(article.blob_number);

        return blob;
    }

    /*
    public byte[] getArticleData(final DirectoryEntry directoryInfo) throws IOException {

        // fail fast
        if (directoryInfo == null) return null;
        if (directoryInfo.getClass() != ArticleEntry.class) return null;

        // This is now an article, so thus we can cast to ArticleEntry
        final ArticleEntry article = (ArticleEntry) directoryInfo;

        // Read the location of the cluster
        final long clusterPos = this.mFile.geClusterPtr(article.cluster_number);

        // Move to the cluster
        this.mFile.mReader.seek(clusterPos);

        // Read the first byte, for compression information
        final int compressionType = this.mFile.mReader.read();

        // Check the compression type that was read
        // type = 1 uncompressed
        if (compressionType <= 1 || compressionType == 8 || compressionType == 9) {
            boolean extended = compressionType > 1;
            return readClusterEntry(this.mFile.mReader, article.blob_number, extended);
        }
        // 2 for zlib and 3 for bzip2 (removed)

        // LZMA2 compressed data
        if (compressionType == 4 || compressionType == 12) {
            boolean extended = compressionType == 12;
            // Create a dictionary with size 40MiB, the zimlib uses this size while creating
            SingleXZInputStream xzReader= new SingleXZInputStream(this.mFile.mReader, 41943040);
            return readClusterEntry(xzReader, article.blob_number, extended);
        }

        // Zstandard compressed data
        if (compressionType == 5 || compressionType == 13) {
            boolean extended = compressionType == 13;
            ZstdInputStream zReader = new ZstdInputStream(this.mFile.mReader);
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
        if (blob_size == 0) return new byte[0]; // skip the skipping to get to a zero-length object (they exist!)
        byte[] entry = new byte[(int) blob_size]; // TODO: we should be able to read blobs larger than MAXINT
        // we must do two skip steps: first to the end of the offset list and second to the start of the blob
        // - the whole number of offset list entries is numberOfBlobs1, which includes the extra entry for the end offset
        // - the number of offset entries that we alreay read now is article.blob_number + 2 (in any case at least 2)
        // - the remaining number of offset entries to skip is therefore numberOfBlobs1 - (article.blob_number + 2)
        // - the addon skip of number of bytes to the start of the entry is offset1 - firstoffset with firstoffset = 4 * numberOfBlobs1
        // - the full skip length is 4 * (numberOfBlobs1 - (article.blob_number + 2)) + offset1 - 4 * numberOfBlobs1
        //   = offset1 - 4 * (article.blob_number + 2)
        RandomAccessFileZIMInputStream.skipFully(is, (offset1 - (extended ? 8 : 4) * (blob_number + 2)));
        RandomAccessFileZIMInputStream.readFully(is, entry);

        return entry;
    }
    */
}
