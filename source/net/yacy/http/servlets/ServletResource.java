/**
 *  ServletResource
 *  Copyright 2026 by Michael Peter Christen
 *  First released 12.07.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.http.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;

/** Container-neutral view of a static resource served by YaCy. */
public interface ServletResource extends AutoCloseable {

    ServletResource addPath(String path) throws IOException;

    boolean exists();

    boolean isDirectory();

    long lastModified();

    long length();

    String getName();

    File getFile() throws IOException;

    InputStream getInputStream() throws IOException;

    String getListHTML(String base, boolean parent, String query) throws IOException;

    void writeTo(OutputStream output, long start, long count) throws IOException;

    @Override
    void close();
}
