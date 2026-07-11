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
