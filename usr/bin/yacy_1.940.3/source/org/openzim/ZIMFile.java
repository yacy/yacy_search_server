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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Arunesh Mathur
 *         A ZIM file implementation that stores the Header and the MIMETypeList
 *
 * @author Michael Christen
 *         Proof-Reading, unclustering, refactoring,
 *         naming adoption to https://wiki.openzim.org/wiki/ZIM_file_format,
 *         change of Exception handling, 
 *         extension to more attributes as defined in spec (bugfix for mime type loading),
 *         int/long bugfix (did reading of long values with int variables, causing negative offsets),
 *         added url pointer, title pointer and cluster pointer caches
 */
public class ZIMFile extends File {

    private static final long serialVersionUID = 1L;

    // Header values
    public final int  header_magicNumber;
    public final int  header_majorVersion;
    public final int  header_minorVersion;
    public final int  header_entryCount;
    public final int  header_clusterCount;
    private final long header_urlPtrPos;
    private final long header_titlePtrPos;
    private final long header_clusterPtrPos;
    public final long header_mimeListPos;
    public final int  header_mainPage;
    public final int  header_layoutPage;
    public final long header_checksumPos;

    // content handle
    public final RandomAccessFileZIMInputStream mReader;

    // content cache
    private final String[] mimeTypeList;
    private final byte[]   urlPtrListBlob;
    private final byte[]   titlePtrListBlob;
    private final byte[]   clusterPtrListBlob;

    public ZIMFile(final String path) throws IOException {
        super(path);

        // Check whether the file exists
        if (!(this.exists())) {
            throw new FileNotFoundException(
                    "The file that you specified was not found.");
        }

        // The reader that will be used to read contents from the file
        this.mReader = new RandomAccessFileZIMInputStream(new RandomAccessFile(this, "r"));

        // Read the contents of the header
        this.header_magicNumber   = mReader.readFourLittleEndianBytesInt();     //  4
        this.header_majorVersion  = mReader.readTwoLittleEndianBytesInt();      //  2
        this.header_minorVersion  = mReader.readTwoLittleEndianBytesInt();      //  4
        RandomAccessFileZIMInputStream.skipFully(mReader, 16); // skip the uuid, this is not used
        this.header_entryCount    = mReader.readFourLittleEndianBytesInt();     //  4
        this.header_clusterCount  = mReader.readFourLittleEndianBytesInt();     //  4
        this.header_urlPtrPos     = mReader.readEightLittleEndianBytesLong();   //  8
        this.header_titlePtrPos   = mReader.readEightLittleEndianBytesLong();   //  8
        this.header_clusterPtrPos = mReader.readEightLittleEndianBytesLong();   //  8
        this.header_mimeListPos   = mReader.readEightLittleEndianBytesLong();   //  8
        this.header_mainPage      = mReader.readFourLittleEndianBytesInt();     //  4
        this.header_layoutPage    = mReader.readFourLittleEndianBytesInt();     //  4
        this.header_checksumPos   = mReader.readEightLittleEndianBytesLong();   //  8 [FIX!]

        // Initialise the MIMETypeList
        int len = 0;
        StringBuffer mimeBuffer = null;
        List<String> mList = new ArrayList<>();
        while (true) {
            int b = mReader.read(); // read only one byte to check if this is a zero
            len = 0;
            mimeBuffer = new StringBuffer();
            while (b != '\0') {
                mimeBuffer.append((char) b);
                b = mReader.read();
                len++;
            }
            if (len == 0) {
                break;
            }
            String mimeType = mimeBuffer.toString();
            //System.out.println(mimeType);
            mList.add(mimeType);
        }
        this.mimeTypeList = mList.toArray(new String[mList.size()]);

        try {
            // Initialize the Url Pointer List
            this.urlPtrListBlob = new byte[this.header_entryCount * 8];
            mReader.seek(this.header_urlPtrPos);
            RandomAccessFileZIMInputStream.readFully(mReader, this.urlPtrListBlob);

            // Initialize the Title Pointer List
            this.titlePtrListBlob = new byte[this.header_entryCount * 4];
            mReader.seek(this.header_titlePtrPos);
            RandomAccessFileZIMInputStream.readFully(mReader, this.titlePtrListBlob);

            // Initialize the Cluster Pointer List
            this.clusterPtrListBlob = new byte[this.header_clusterCount * 8];
            mReader.seek(this.header_clusterPtrPos);
            RandomAccessFileZIMInputStream.readFully(mReader, this.clusterPtrListBlob);
        } catch (IndexOutOfBoundsException e) {
            throw new IOException(e.getMessage());
        }
    }

    public final String getMimeType(int idx) {
        if (idx >= this.mimeTypeList.length) return "";
        return this.mimeTypeList[idx];
    }

    public final long getURLPtr(final int idx) {
        return RandomAccessFileZIMInputStream.toEightLittleEndianLong(this.urlPtrListBlob, idx * 8);
    }

    public final int getTitlePtr(final int idx) {
        return RandomAccessFileZIMInputStream.toFourLittleEndianInteger(this.titlePtrListBlob, idx * 4);
    }

    public final long geClusterPtr(final int idx) {
        return RandomAccessFileZIMInputStream.toEightLittleEndianLong(this.clusterPtrListBlob, idx * 8);
    }
}
