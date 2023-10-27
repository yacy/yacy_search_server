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

public class ArticleEntry extends DirectoryEntry {

    int clusterNumber;

    int blobnumber;

    public ArticleEntry(final int mimeType, final char namespace, final int revision,
            final int clusterNumber, final int blobNumber, final String url, final String title,
            final int urlListindex) {

        super(mimeType, namespace, revision, url, title, urlListindex);

        this.clusterNumber = clusterNumber;
        this.blobnumber = blobNumber;
    }

    public int getClusterNumber() {
        return this.clusterNumber;
    }

    public int getBlobnumber() {
        return this.blobnumber;
    }

}
