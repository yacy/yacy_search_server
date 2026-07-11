/**
 *  Jetty9ServletResource
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.eclipse.jetty.util.resource.Resource;

/** Jetty 9 adapter for the static-resource operations used by YaCy. */
final class Jetty9ServletResource implements ServletResource {

    private final Resource delegate;

    private Jetty9ServletResource(final Resource delegate) {
        this.delegate = delegate;
    }

    static void disableDefaultCaches() {
        Resource.setDefaultUseCaches(false);
    }

    static ServletResource from(final String location) throws IOException {
        return wrap(Resource.newResource(location));
    }

    static ServletResource from(final File file) throws IOException {
        return wrap(Resource.newResource(file));
    }

    static ServletResource from(final URL url) throws IOException {
        return wrap(Resource.newResource(url));
    }

    private static ServletResource wrap(final Resource resource) {
        return resource == null ? null : new Jetty9ServletResource(resource);
    }

    @Override
    public ServletResource addPath(final String path) throws IOException {
        return wrap(this.delegate.addPath(path));
    }

    @Override
    public boolean exists() {
        return this.delegate.exists();
    }

    @Override
    public boolean isDirectory() {
        return this.delegate.isDirectory();
    }

    @Override
    public long lastModified() {
        return this.delegate.lastModified();
    }

    @Override
    public long length() {
        return this.delegate.length();
    }

    @Override
    public String getName() {
        return this.delegate.getName();
    }

    @Override
    public File getFile() throws IOException {
        return this.delegate.getFile();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.delegate.getInputStream();
    }

    @Override
    public String getListHTML(final String base, final boolean parent, final String query) throws IOException {
        return this.delegate.getListHTML(base, parent, query);
    }

    @Override
    public void writeTo(final OutputStream output, final long start, final long count) throws IOException {
        this.delegate.writeTo(output, start, count);
    }

    @Override
    public void close() {
        this.delegate.close();
    }

    @Override
    public String toString() {
        return this.delegate.toString();
    }
}
