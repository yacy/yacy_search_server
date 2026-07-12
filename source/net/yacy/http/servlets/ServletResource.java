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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** JDK-only static resource implementation shared by all servlet containers. */
final class ServletResource {

    private static final int COPY_BUFFER_SIZE = 8192;

    private final Path path;
    private final URL url;

    private ServletResource(final Path path, final URL url) {
        this.path = path;
        this.url = url;
    }

    static ServletResource from(final String location) throws IOException {
        if (location == null) {
            return null;
        }
        if (location.contains("://") || location.startsWith("file:") || location.startsWith("jar:")) {
            return from(new URL(location));
        }
        return from(Path.of(location));
    }

    static ServletResource from(final File file) {
        return file == null ? null : from(file.toPath());
    }

    static ServletResource from(final URL url) throws IOException {
        if (url == null) {
            return null;
        }
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            try {
                return from(Path.of(url.toURI()));
            } catch (final URISyntaxException | IllegalArgumentException error) {
                throw new IOException("Invalid file resource URL: " + url, error);
            }
        }
        return new ServletResource(null, url);
    }

    private static ServletResource from(final Path path) {
        return new ServletResource(path.toAbsolutePath().normalize(), null);
    }

    public ServletResource addPath(final String child) throws IOException {
        if (child == null) {
            throw new IllegalArgumentException("Resource path must not be null");
        }
        final String relative = child.replace('\\', '/').replaceFirst("^/+", "");
        final Path normalizedRelative = Path.of(relative).normalize();
        if (normalizedRelative.isAbsolute() || normalizedRelative.startsWith("..")) {
            throw new IllegalArgumentException("Resource path escapes its base: " + child);
        }
        if (this.path != null) {
            final Path resolved = this.path.resolve(normalizedRelative).normalize();
            if (!resolved.startsWith(this.path)) {
                throw new IllegalArgumentException("Resource path escapes its base: " + child);
            }
            return from(resolved);
        }
        try {
            final URI base = this.url.toURI();
            final String baseText = base.toString().endsWith("/") ? base.toString() : base + "/";
            return from(URI.create(baseText).resolve(relative).normalize().toURL());
        } catch (final URISyntaxException error) {
            throw new IOException("Invalid resource URL: " + this.url, error);
        }
    }

    public boolean exists() {
        if (this.path != null) {
            return Files.exists(this.path);
        }
        try (InputStream ignored = this.openConnection().getInputStream()) {
            return true;
        } catch (final IOException error) {
            return false;
        }
    }

    public boolean isDirectory() {
        return this.path != null && Files.isDirectory(this.path);
    }

    public long lastModified() {
        try {
            return this.path != null
                    ? Files.getLastModifiedTime(this.path).toMillis()
                    : this.openConnection().getLastModified();
        } catch (final IOException error) {
            return 0L;
        }
    }

    public long length() {
        try {
            return this.path != null ? Files.size(this.path) : this.openConnection().getContentLengthLong();
        } catch (final IOException error) {
            return -1L;
        }
    }

    public String getName() {
        return this.path != null ? this.path.toString() : this.url.toExternalForm();
    }

    public File getFile() throws IOException {
        if (this.path == null) {
            throw new IOException("Resource is not backed by a file: " + this.url);
        }
        return this.path.toFile();
    }

    public InputStream getInputStream() throws IOException {
        return this.path != null ? Files.newInputStream(this.path) : this.openConnection().getInputStream();
    }

    public String getListHTML(final String base, final boolean parent, final String query) throws IOException {
        if (this.path == null || !Files.isDirectory(this.path)) {
            return null;
        }
        final StringBuilder html = new StringBuilder(512);
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Directory: ")
                .append(escapeHtml(base)).append("</title></head><body><h1>Directory: ")
                .append(escapeHtml(base)).append("</h1><ul>");
        if (parent) {
            html.append("<li><a href=\"../\">../</a></li>");
        }
        try (Stream<Path> children = Files.list(this.path)) {
            children.sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(child -> appendDirectoryEntry(html, base, child));
        }
        return html.append("</ul></body></html>").toString();
    }

    private static void appendDirectoryEntry(final StringBuilder html, final String base, final Path child) {
        final String name = child.getFileName().toString();
        final boolean directory = Files.isDirectory(child);
        try {
            final String encoded = new URI(null, null, name, null).toASCIIString();
            html.append("<li><a href=\"").append(escapeHtml(base)).append(encoded);
            if (directory) {
                html.append('/');
            }
            html.append("\">").append(escapeHtml(name));
            if (directory) {
                html.append('/');
            }
            html.append("</a></li>");
        } catch (final URISyntaxException error) {
            throw new IllegalArgumentException("Invalid directory entry: " + name, error);
        }
    }

    private static String escapeHtml(final String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    public void writeTo(final OutputStream output, final long start, final long count) throws IOException {
        if (start < 0 || count < -1) {
            throw new IllegalArgumentException("Invalid resource range: " + start + "+" + count);
        }
        try (InputStream input = this.getInputStream()) {
            input.skipNBytes(start);
            final byte[] buffer = new byte[COPY_BUFFER_SIZE];
            long remaining = count;
            while (remaining != 0) {
                final int requested = remaining < 0 ? buffer.length : (int) Math.min(buffer.length, remaining);
                final int read = input.read(buffer, 0, requested);
                if (read < 0) {
                    break;
                }
                output.write(buffer, 0, read);
                if (remaining > 0) {
                    remaining -= read;
                }
            }
        }
    }

    private URLConnection openConnection() throws IOException {
        final URLConnection connection = this.url.openConnection();
        connection.setUseCaches(false);
        return connection;
    }

    public void close() {
        // Streams are opened per operation and closed by their caller.
    }

    @Override
    public String toString() {
        return this.getName();
    }
}
