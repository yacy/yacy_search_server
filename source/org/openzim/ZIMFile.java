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
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Arunesh Mathur
 *
 *         A ZIM file implementation that stores the Header and the MIMETypeList
 *
 */
public class ZIMFile extends File {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private Header mHeader;

    private List<String> mMIMETypeList; // Can be removed if not needed

    public ZIMFile(final String path) {
        super(path);

        try {
            readHeader();
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void readHeader() throws FileNotFoundException {

        // Helpers
        int len = 0;
        StringBuffer mimeBuffer = null;

        // The byte[] that will help us in reading bytes out of the file
        final byte[] buffer = new byte[16];

        // Check whether the file exists
        if (!(this.exists())) {
            throw new FileNotFoundException(
                    "The file that you specified was not found.");
        }

        // The reader that will be used to read contents from the file

        final RandomAcessFileZIMInputStream reader = new RandomAcessFileZIMInputStream(
                new RandomAccessFile(this, "r"));

        // The ZIM file header
        this.mHeader = new Header();

        // Read the contents of the header
        try {
            this.mHeader.magicNumber = reader.readFourLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.magicNumber);

            this.mHeader.version = reader.readFourLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.version);

            this.mHeader.uuid = reader.readSixteenLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.uuid); reader.read(buffer, 0, 4);

            this.mHeader.articleCount = reader
                    .readFourLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.articleCount);

            this.mHeader.clusterCount = reader
                    .readFourLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.clusterCount);

            this.mHeader.urlPtrPos = reader.readEightLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.urlPtrPos);

            this.mHeader.titlePtrPos = reader
                    .readEightLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.titlePtrPos);

            this.mHeader.clusterPtrPos = reader
                    .readEightLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.clusterPtrPos);

            this.mHeader.mimeListPos = reader
                    .readEightLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.mimeListPos);

            this.mHeader.mainPage = reader.readFourLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.mainPage);

            this.mHeader.layoutPage = reader.readFourLittleEndianBytesValue(buffer);
            // System.out.println(mHeader.layoutPage);

            // Initialise the MIMETypeList
            this.mMIMETypeList = new ArrayList<>();
            while (true) {
                reader.read(buffer, 0, 1);
                len = 0;
                mimeBuffer = new StringBuffer();
                while (buffer[0] != '\0') {
                    mimeBuffer.append((char) buffer[0]);
                    reader.read(buffer, 0, 1);
                    len++;
                }
                if (len == 0) {
                    break;
                }
                this.mMIMETypeList.add(mimeBuffer.toString());
                // System.out.println(mimeBuffer);
            }

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public int getVersion() {
        return this.mHeader.version;
    }

    public int getUuid() {
        return this.mHeader.uuid;
    }

    public int getArticleCount() {
        return this.mHeader.articleCount;
    }

    public int getClusterCount() {
        return this.mHeader.clusterCount;
    }

    public int getUrlPtrPos() {
        return this.mHeader.urlPtrPos;
    }

    public int getTitlePtrPos() {
        return this.mHeader.titlePtrPos;
    }

    public int getClusterPtrPos() {
        return this.mHeader.clusterPtrPos;
    }

    public String getMIMEType(final int mimeNumber) {
        return this.mMIMETypeList.get(mimeNumber);
    }

    public int getHeaderSize() {
        return this.mHeader.mimeListPos;
    }

    public int getMainPage() {
        return this.mHeader.mainPage;
    }

    public int getLayoutPage() {
        return this.mHeader.layoutPage;
    }

    public class Header {
        int magicNumber;
        int version;
        int uuid;
        int articleCount;
        int clusterCount;
        int urlPtrPos;
        int titlePtrPos;
        int clusterPtrPos;
        int mimeListPos;
        int mainPage;
        int layoutPage;
    }

}
