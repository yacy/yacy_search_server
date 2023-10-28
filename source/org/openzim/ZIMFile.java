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
 *         extension to more attributes as defined in spec (bugfix for mime type loading)
 *         int/long bugfix (did reading of long values with int variables, causing negative offsets)
 */
public class ZIMFile extends File {

    private static final long serialVersionUID = 1L;

    // Header values
    public final int  header_magicNumber;
    public final int  header_majorVersion;
    public final int  header_minorVersion;
    public final int  header_entryCount;
    public final int  header_clusterCount;
    public final long header_urlPtrPos;
    public final long header_titlePtrPos;
    public final long header_clusterPtrPos;
    public final long header_mimeListPos;
    public final int  header_mainPage;
    public final int  header_layoutPage;
    public final long header_checksumPos;

    // content cache
    public final List<String> mimeList;

    public ZIMFile(final String path) throws IOException {
        super(path);

        // Check whether the file exists
        if (!(this.exists())) {
            throw new FileNotFoundException(
                    "The file that you specified was not found.");
        }

        // The reader that will be used to read contents from the file
        final RandomAccessFileZIMInputStream reader = new RandomAccessFileZIMInputStream(new RandomAccessFile(this, "r"));

        // Read the contents of the header
        this.header_magicNumber   = reader.readFourLittleEndianBytesInt();     //  4
        this.header_majorVersion  = reader.readTwoLittleEndianBytesInt();      //  2
        this.header_minorVersion  = reader.readTwoLittleEndianBytesInt();      //  4
        RandomAccessFileZIMInputStream.skipFully(reader, 16); // skip the uuid, this is not used
        this.header_entryCount    = reader.readFourLittleEndianBytesInt();     //  4
        this.header_clusterCount  = reader.readFourLittleEndianBytesInt();     //  4
        this.header_urlPtrPos     = reader.readEightLittleEndianBytesLong();   //  8
        this.header_titlePtrPos   = reader.readEightLittleEndianBytesLong();   //  8
        this.header_clusterPtrPos = reader.readEightLittleEndianBytesLong();   //  8
        this.header_mimeListPos   = reader.readEightLittleEndianBytesLong();   //  8
        this.header_mainPage      = reader.readFourLittleEndianBytesInt();     //  4
        this.header_layoutPage    = reader.readFourLittleEndianBytesInt();     //  4
        this.header_checksumPos   = reader.readEightLittleEndianBytesLong();   //  8 [FIX!]

        // Initialise the MIMETypeList
        int len = 0;
        StringBuffer mimeBuffer = null;
        this.mimeList = new ArrayList<>();
        while (true) {
            int b = reader.read(); // read only one byte to check if this is a zero
            len = 0;
            mimeBuffer = new StringBuffer();
            while (b != '\0') {
                mimeBuffer.append((char) b);
                b = reader.read();
                len++;
            }
            if (len == 0) {
                break;
            }
            String mimeType = mimeBuffer.toString();
            System.out.println(mimeType);
            this.mimeList.add(mimeType);
        }

    }

}
