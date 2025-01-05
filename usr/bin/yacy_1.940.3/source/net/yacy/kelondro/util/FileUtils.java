// serverFileUtils.java
// -------------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang.StringUtils;
import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsPSMDetector;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.storage.Files;
import net.yacy.cora.util.ConcurrentLog;

public final class FileUtils {

    private static final int DEFAULT_BUFFER_SIZE = 1024; // this is also the maximum chunk size

    /**
     * Copy a whole InputStream to an OutputStream. Important : it is the responsibility of the caller to close the input and output streams.
     *
     * @param source InputStream instance
     * @param dest OutputStream instance
     * @param count the total amount of bytes to copy (-1 for all, else must be greater than zero)
     * @return Total number of bytes copied.
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     */
    public static long copy(final InputStream source, final OutputStream dest) throws IOException {
        return copy(source, dest, -1);
    }

    /**
     * Copies a specified amount of bytes from an InputStream to an OutputStream. Important : it is the responsibility of the caller to close the input and output streams.
     *
     * @param source InputStream instance
     * @param dest OutputStream instance
     * @param count the total amount of bytes to copy (-1 for all, else must be greater than zero)
     * @return Total number of bytes copied.
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     * @see #copy(InputStream source, File dest)
     * @see #copyRange(File source, OutputStream dest, int start)
     * @see #copy(File source, OutputStream dest)
     * @see #copy(File source, File dest)
     */
    public static long copy(final InputStream source, final OutputStream dest, final long count)
            throws IOException {
        assert count < 0 || count > 0 : "precondition violated: count == " + count + " (nothing to copy)";
        if ( count == 0 ) {
            // no bytes to copy
            return 0;
        }

        final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        final int chunkSize = (int) ((count > 0) ? Math.min(count, DEFAULT_BUFFER_SIZE) : DEFAULT_BUFFER_SIZE);

        int c;
        long total = 0;
        long remaining;
        if(count > 0) {
            remaining = count;
        } else {
            remaining = Long.MAX_VALUE;
        }
        while ( (c = source.read(buffer, 0, remaining < chunkSize ? (int)remaining : chunkSize)) > 0 ) {
            dest.write(buffer, 0, c);
            dest.flush();
            total += c;
            remaining -= c;

            if ( count > 0 && count == total) {
                break;
            }

        }
        dest.flush();

        return total;
    }

    /**
     * Copy a whole InputStream to a Writer, using the default platform charset to decode input stream bytes.
     * Important : it is the responsibility of the caller to close the input stream and output writer.
     *
     * @param source InputStream instance
     * @param dest Writer instance
     * @return the total number of characters copied.
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     */
    public static int copy(final InputStream source, final Writer dest) throws IOException {
        final InputStreamReader reader = new InputStreamReader(source);
        return copy(reader, dest);
    }

    /**
     * Copy a whole InputStream to a Writer, using the specified charset to decode input stream bytes.
     * Important : it is the responsibility of the caller to close the input stream and output writer.
     *
     * @param source InputStream instance
     * @param dest Writer instance
     * @return the total number of characters copied.
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     */
    public static int copy(final InputStream source, final Writer dest, final Charset inputCharset)
            throws IOException {
        final InputStreamReader reader = new InputStreamReader(source, inputCharset);
        return copy(reader, dest);
    }

    /**
     * Copy a String to Writer.
     * Important : it is the responsibility of the caller to close the output writer.
     *
     * @param source String instance
     * @param dest writer instance
     * @return the total number of characters copied (source.length)
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     */
    public static int copy(final String source, final Writer dest) throws IOException {
        dest.write(source);
        dest.flush();
        return source.length();
    }

    /**
     * Copy a whole Reader to a Writer.
     * Important : it is the responsibility of the caller to close the input reader and output writer.
     *
     * @param source InputStream instance
     * @param dest Writer instance
     * @return the total number of characters copied.
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     */
    public static int copy(final Reader source, final Writer dest) throws IOException {
        assert source != null;
        assert dest != null;
        if ( source == null ) {
            throw new IOException("source is null");
        }
        if ( dest == null ) {
            throw new IOException("dest is null");
        }
        final char[] buffer = new char[DEFAULT_BUFFER_SIZE];
        int count = 0;
        int n = 0;
        try {
            while ( -1 != (n = source.read(buffer)) ) {
                dest.write(buffer, 0, n);
                count += n;
            }
            dest.flush();
        } catch (final Exception e ) {
            assert e != null;
            // an "sun.io.MalformedInputException: Missing byte-order mark" - exception may occur here
            //Log.logException(e);
            throw new IOException(
                    e == null ? "null" : e.getMessage() == null ? e.toString() : e.getMessage(),
                            e);
        }
        return count;
    }

    /**
     * Copy a whole InputStream to a File.
     * Important : it is the responsibility of the caller to close the input stream.
     *
     * @param source InputStream instance
     * @param dest File instance
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     */
    public static void copy(final InputStream source, final File dest) throws IOException {
        copy(source, dest, -1);
    }

    /**
     * Copies an InputStream to a File. Important : it is the responsibility of the caller to close the source stream.
     *
     * @param source InputStream instance
     * @param dest File instance
     * @param count the amount of bytes to copy (-1 for all, else must be greater than zero)
     * @return the number of bytes actually copied (may be lower than count)
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     * @see #copy(InputStream source, OutputStream dest)
     * @see #copyRange(File source, OutputStream dest, int start)
     * @see #copy(File source, OutputStream dest)
     * @see #copy(File source, File dest)
     */
    public static long copy(final InputStream source, final File dest, final long count) throws IOException {
        final String path = dest.getParent();
        if ( path != null && path.length() > 0 ) {
            new File(path).mkdirs();
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(dest);
            return copy(source, fos, count);
        } finally {
            if ( fos != null ) {
                try {
                    fos.close();
                } catch (final Exception e ) {
                    ConcurrentLog.warn(
                            "FileUtils",
                            "cannot close FileOutputStream for " + dest + "! " + e.getMessage());
                }
            }
        }
    }

    /**
     * Copies a part of a File to an OutputStream.
     * Important : it is the responsibility of the caller to close the destination stream.
     * @param source File instance
     * @param dest OutputStream instance
     * @param start Number of bytes to skip from the beginning of the File
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     * @throws IllegalStateException when an error occurred while skipping bytes
     * @see #copy(InputStream source, OutputStream dest)
     * @see #copy(InputStream source, File dest)
     * @see #copy(File source, OutputStream dest)
     * @see #copy(File source, File dest)
     */
    public static void copyRange(final File source, final OutputStream dest, final int start)
            throws IOException {
        InputStream fis = null;
        try {
            fis = new FileInputStream(source);
            final long skipped = fis.skip(start);
            if ( skipped != start ) {
                throw new IllegalStateException("Unable to skip '"
                        + start
                        + "' bytes. Only '"
                        + skipped
                        + "' bytes skipped.");
            }
            copy(fis, dest, -1);
        } finally {
            if ( fis != null ) {
                try {
                    fis.close();
                } catch (final Exception e ) {
                    ConcurrentLog.warn("FileUtils", "Could not close input stream on file " + source);
                }
            }
        }
    }

    /**
     * Copies a File to an OutputStream. Important : it is the responsibility of the caller to close the output stream.
     *
     * @param source File instance
     * @param dest OutputStream instance
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     * @see #copy(InputStream source, OutputStream dest)
     * @see #copy(InputStream source, File dest)
     * @see #copyRange(File source, OutputStream dest, int start)
     * @see #copy(File source, File dest)
     */
    public static void copy(final File source, final OutputStream dest) throws IOException {
        InputStream fis = null;
        try {
            fis = new FileInputStream(source);
            copy(fis, dest, -1);
        } finally {
            if ( fis != null ) {
                try {
                    fis.close();
                } catch (final Exception e ) {
                    ConcurrentLog.warn("FileUtils", "Could not close input stream on file " + source);
                }
            }
        }
    }

    /**
     * Copy a whole byte array to an output stream.
     * Important : it is the responsibility of the caller to close the output stream.
     *
     * @param source a byte array
     * @param dest OutputStream instance
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     */
    public static void copy(final byte[] source, final OutputStream dest) throws IOException {
        dest.write(source, 0, source.length);
        dest.flush();
    }

    /**
     * Copy a whole byte array to a destination file.
     *
     * @param source a byte array
     * @param dest File instance
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when a parameter is null
     */
    public static void copy(final byte[] source, final File dest) throws IOException {
        copy(new ByteArrayInputStream(source), dest);
    }

    /**
     * Read fully source stream and close it.
     * @param source must not be null
     * @return source content as a byte array.
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when source parameter is null
     */
    public static byte[] read(final InputStream source) throws IOException {
        byte[] content;
        try {
            content = read(source, -1);
        } finally {
            /* source input stream must be closed here in all cases */
            try {
                source.close();
            } catch(final IOException ignoredException) {
            }
        }
        return content;
    }

    /**
     * Read the specified amount of bytes from a source stream.
     * Important : it is the responsibility of the caller to close the stream.
     * @param source InputStream instance. Must not be null
     * @param count maximum amount of bytes to read. A negative value means no limit.
     * @return source content as a byte array.
     * @throws IOException when a read/write error occurred
     * @throws NullPointerException when source parameter is null
     */
    public static byte[] read(final InputStream source, final int count) throws IOException {
        if(count == 0) {
            return new byte[0];
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        copy(source, baos, count);
        baos.close();
        return baos.toByteArray();
    }

    public static byte[] read(final File source) throws IOException {
        final byte[] buffer = new byte[(int) source.length()];
        InputStream fis = null;
        try {
            fis = new FileInputStream(source);
            int p = 0, c;
            while ( (c = fis.read(buffer, p, buffer.length - p)) > 0 ) {
                p += c;
            }
        } finally {
            if ( fis != null ) {
                try {
                    fis.close();
                } catch (final Exception e ) {
                    ConcurrentLog.warn("FileUtils", "Could not close input stream on file " + source);
                }
            }
            fis = null;
        }
        return buffer;
    }

    /**
     * This function determines if a byte array is gzip compressed and uncompress it
     *
     * @param source properly gzip compressed byte array
     * @return uncompressed byte array
     * @throws IOException
     */
    public static byte[] uncompressGZipArray(byte[] source) throws IOException {
        if ( source == null ) {
            return null;
        }

        // support of gzipped data (requested by roland)
        /* "Bitwise OR of signed byte value
         *
         * [...] Values loaded from a byte array are sign extended to 32 bits before
         * any any bitwise operations are performed on the value. Thus, if b[0]
         * contains the value 0xff, and x is initially 0, then the code ((x <<
         * 8) | b[0]) will sign extend 0xff to get 0xffffffff, and thus give the
         * value 0xffffffff as the result. [...]" findbugs description of BIT_IOR_OF_SIGNED_BYTE
         */
        if ( (source.length > 1) && (((source[1] << 8) | (source[0] & 0xff)) == GZIPInputStream.GZIP_MAGIC) ) {
            System.out.println("DEBUG: uncompressGZipArray - uncompressing source");
            try {
                final ByteArrayInputStream byteInput = new ByteArrayInputStream(source);
                final ByteArrayOutputStream byteOutput = new ByteArrayOutputStream(source.length / 5);
                final GZIPInputStream zippedContent = new GZIPInputStream(byteInput);
                final byte[] data = new byte[1024];
                int read = 0;

                // reading gzip file and store it uncompressed
                while ( (read = zippedContent.read(data, 0, 1024)) != -1 ) {
                    byteOutput.write(data, 0, read);
                }
                zippedContent.close();
                byteOutput.close();

                source = byteOutput.toByteArray();
            } catch (final Exception e ) {
                if ( !e.getMessage().equals("Not in GZIP format") ) {
                    throw new IOException(e.getMessage());
                }
            }
        }

        return source;
    }

    /**
     * Generate a set of strings matching each line of the given file. Lines are
     * lower cased and any eventual surrounding space characters are removed. Empty
     * lines and lines starting with the '#' character are ignored.
     *
     * @param file
     *            a file to load
     * @return a set of strings eventually empty
     */
    public static HashSet<String> loadList(final File file) {
        final HashSet<String> set = new HashSet<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            while ( (line = br.readLine()) != null ) {
                line = line.trim();
                if ( line.length() > 0 && line.charAt(0) != '#' ) {
                    set.add(line.trim().toLowerCase());
                }
            }
        } catch (final IOException e ) {
        } finally {
            if ( br != null ) {
                try {
                    br.close();
                } catch (final Exception e ) {
                    ConcurrentLog.warn("FileUtils", "Could not close input stream on file " + file);
                }
            }
        }
        return set;
    }

    public static ConcurrentHashMap<String, String> loadMap(final File f) {
        // load props
        try {
            final byte[] b = read(f);
            return table(strings(b));
        } catch (final IOException e2 ) {
            ConcurrentLog.severe("FileUtils", f.toString() + " not found", e2);
            return null;
        }
    }

    public static ConcurrentHashMap<String, byte[]> loadMapB(final File f) {
        final ConcurrentHashMap<String, String> m = loadMap(f);
        if (m == null) return null;
        final ConcurrentHashMap<String, byte[]> mb = new ConcurrentHashMap<>();
        for (final Map.Entry<String, String> e: m.entrySet()) mb.put(e.getKey(), UTF8.getBytes(e.getValue()));
        return mb;
    }

    private final static String[] unescaped_strings_in = {"\r\n", "\r", "\n", "=", "\\"};
    private final static String[] escaped_strings_out = {"\\n", "\\n", "\\n", "\\=", "\\\\"};
    private final static String[] escaped_strings_in = {"\\\\", "\\n", "\\="};
    private final static String[] unescaped_strings_out = {"\\", "\n", "="};

    public static void saveMap(final File file, final Map<String, String> props, final String comment) {
        boolean err = false;
        PrintWriter pw = null;
        final File tf = new File(file.toString() + "." + (System.currentTimeMillis() % 1000));
        try {
            pw = new PrintWriter(tf, StandardCharsets.UTF_8.name());
            pw.println("# " + comment);
            String key, value;
            for ( final Map.Entry<String, String> entry : props.entrySet() ) {
                key = entry.getKey();
                if ( key != null ) {
                    key = StringUtils.replaceEach(key, unescaped_strings_in, escaped_strings_out);
                }
                if ( entry.getValue() == null ) {
                    value = "";
                } else {
                    value = entry.getValue();
                    value = StringUtils.replaceEach(value, unescaped_strings_in, escaped_strings_out);
                }
                pw.println(key + "=" + value);
            }
            pw.println("# EOF");
        } catch (final  FileNotFoundException | UnsupportedEncodingException e ) {
            ConcurrentLog.warn("FileUtils", e.getMessage(), e);
            err = true;
        } finally {
            if ( pw != null ) {
                pw.close();
            }
            pw = null;
        }
        if (!err) try {
            forceMove(tf, file);
        } catch (final  IOException e ) {
            // ignore
        }
    }

    public static void saveMapB(final File file, final Map<String, byte[]> props, final String comment) {
        final HashMap<String, String> m = new HashMap<>();
        for (final Map.Entry<String, byte[]> e: props.entrySet()) m.put(e.getKey(), UTF8.String(e.getValue()));
        saveMap(file, m, comment);
    }

    public static ConcurrentHashMap<String, String> table(final Reader r) {
        final BufferedReader br = new BufferedReader(r);
        return table(new StringsIterator(br));
    }

    public static ConcurrentHashMap<String, String> table(final Iterator<String> li) {
        String line;
        final ConcurrentHashMap<String, String> props = new ConcurrentHashMap<>();
        while ( li.hasNext() ) {
            int pos = 0;
            line = li.next().trim();
            if ( !line.isEmpty() && line.charAt(0) == '#' ) {
                continue; // exclude comments
            }
            do {
                // search for unescaped =
                pos = line.indexOf('=', pos + 1);
            } while ( pos > 0 && line.charAt(pos - 1) == '\\' );
            if ( pos > 0 ) try {
                final String key = StringUtils.replaceEach(line.substring(0, pos).trim(), escaped_strings_in, unescaped_strings_out);
                final String value = StringUtils.replaceEach(line.substring(pos + 1).trim(), escaped_strings_in, unescaped_strings_out);
                //System.out.println("key = " + key + ", value = " + value);
                props.put(key, value);
            } catch (final IndexOutOfBoundsException e) {
                ConcurrentLog.logException(e);
            }
        }
        return props;
    }

    public static Map<String, String> table(final byte[] a) {
        if (a == null) return new ConcurrentHashMap<>();
        //System.out.println("***TABLE: a.size = " + a.length);
        return table(strings(a));
    }

    public static Iterator<String> strings(final byte[] a) {
        if ( a == null ) {
            return new ArrayList<String>().iterator();
        }
        return new StringsIterator(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(a), StandardCharsets.UTF_8)));
    }

    /**
     * Read lines of a file into an ArrayList.
     * Empty lines in the file are ignored.
     *
     * @param listFile the file
     * @return the resulting array as an ArrayList
     */
    public static ArrayList<String> getListArray(final File listFile) {
        String line;
        final ArrayList<String> list = new ArrayList<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile), StandardCharsets.UTF_8));

            while ( (line = br.readLine()) != null ) {
                if (!line.isEmpty()) list.add(line);
            }
        } catch (final IOException e ) {
            // list is empty
        } finally {
            if ( br != null ) {
                try {
                    br.close();
                } catch (final Exception e ) {
                    ConcurrentLog.warn("FileUtils", "Could not close input stream on file " + listFile);
                }
            }
        }
        return list;
    }

    /**
     * Write a String to a file (used for string representation of lists).
     *
     * @param listFile the file to write to
     * @param out the String to write
     * @return returns <code>true</code> if successful, <code>false</code> otherwise
     */
    private static boolean writeList(final File listFile, final String out) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new PrintWriter(new FileWriter(listFile)));
            bw.write(out);
            bw.close();
            return true;
        } catch (final IOException e ) {
            return false;
        } finally {
            if ( bw != null ) {
                try {
                    bw.close();
                } catch (final Exception e ) {
                }
            }
        }
    }

    private static final char LF = (char) 10;
    private static final char CR = (char) 13;

    /**
     * Read lines of a text file into a String, optionally ignoring comments.
     * Empty lines are always ignored.
     *
     * @param listFile the File to read from.
     * @param withcomments If <code>false</code> ignore lines starting with '#'.
     * @return String representation of the file content.
     */
    public static String getListString(final File listFile, final boolean withcomments) {
        final StringBuilder temp = new StringBuilder(300);

        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile)));
            temp.ensureCapacity((int) listFile.length());

            // Read the List
            String line = "";
            while ( (line = br.readLine()) != null ) {
                if ( line.isEmpty() ) {
                    continue;
                }
                if ( line.charAt(0) != '#' || withcomments ) {
                    //temp += line + serverCore.CRLF_STRING;
                    temp.append(line).append(CR).append(LF);
                }
            }
        } catch (final IOException e ) {
        } finally {
            if ( br != null ) {
                try {
                    br.close();
                } catch (final Exception e ) {
                    ConcurrentLog.warn("FileUtils", "Could not close input stream on file " + listFile);
                }
            }
        }

        return new String(temp);
    }

    /**
     * Read content of a directory into a String array of file names.
     *
     * @param dirname The directory to get the file listing from. If it doesn't exist yet, it will be created.
     * @return array of file names
     */
    public static List<String> getDirListing(final String dirname) {
        return getDirListing(dirname, null);
    }

    /**
     * Read content of a directory into a String array of file names.
     *
     * @param dirname The directory to get the file listing from. If it doesn't exist yet, it will be created.
     * @param filter String which contains a regular expression which has to be matched by file names in order
     *        to appear in returned array. All file names will be returned if filter is null.
     * @return array of file names
     */
    public static List<String> getDirListing(final String dirname, final String filter) {
        return getDirListing(new File(dirname), filter);
    }

    /**
     * Read content of a directory into a String array of file names.
     *
     * @param dir The directory to get the file listing from. If it doesn't exist yet, it will be created.
     * @return array of file names
     */
    public static List<String> getDirListing(final File dir) {
        return getDirListing(dir, null);
    }

    /**
     * Read content of a directory into a String array of file names.
     *
     * @param dir The directory to get the file listing from. If it doesn't exist yet, it will be created.
     * @param filter String which contains a regular expression which has to be matched by file names in order
     *        to appear in returned array. All file names will be returned if filter is null.
     * @return array of file names
     */
    public static List<String> getDirListing(final File dir, final String filter) {
        final List<String> ret = new LinkedList<>();
        File[] fileList;
        if ( dir != null ) {
            if ( !dir.exists() ) {
                dir.mkdir();
            }
            fileList = dir.listFiles();
            for ( int i = 0; i <= fileList.length - 1; i++ ) {
                if ( filter == null || fileList[i].getName().matches(filter) ) {
                    ret.add(fileList[i].getName());
                }
            }
            return ret;
        }
        return null;
    }

    // same as below
    public static ArrayList<File> getDirsRecursive(final File dir, final String notdir) {
        return getDirsRecursive(dir, notdir, true);
    }

    /**
     * @param sourceDir source directory. Must be not null.
     * @param notdir name of dir to exlcude. Can be null
     * @param fileNameFilter filter to apply on file names. Can be null.
     * @return list of all files passing fileFilter under sourceDir including sub directories
     */
    public static List<File> getFilesRecursive(final File sourceDir, final String notdir, final FilenameFilter fileNameFilter) {
        final List<File> dirList = getDirsRecursive(sourceDir,
                notdir);
        dirList.add(sourceDir);
        final List<File> files = new ArrayList<>();
        for (final File dir : dirList) {
            Collections.addAll(files, dir.listFiles(fileNameFilter));
        }
        return files;
    }

    /**
     * Returns a List of all dirs and subdirs as File Objects Warning: untested
     */
    private static ArrayList<File> getDirsRecursive(
            final File dir,
            final String notdir,
            final boolean excludeDotfiles) {
        final File[] dirList = dir.listFiles();
        final ArrayList<File> resultList = new ArrayList<>();
        ArrayList<File> recursive;
        Iterator<File> iter;
        for ( int i = 0; i < dirList.length; i++ ) {
            if ( dirList[i].isDirectory()
                    && (!excludeDotfiles || !dirList[i].getName().startsWith("."))
                    && !dirList[i].getName().equals(notdir) ) {
                resultList.add(dirList[i]);
                recursive = getDirsRecursive(dirList[i], notdir, excludeDotfiles);
                iter = recursive.iterator();
                while ( iter.hasNext() ) {
                    resultList.add(iter.next());
                }
            }
        }
        return resultList;
    }

    /**
     * Write elements of an Array of Strings to a file (one element per line).
     *
     * @param listFile the file to write to
     * @param list the Array to write
     * @return returns <code>true</code> if successful, <code>false</code> otherwise
     */
    public static boolean writeList(final File listFile, final String[] list) {
        final StringBuilder out = new StringBuilder(list.length * 40 + 1);
        for ( final String element : list ) {
            out.append(element).append(CR).append(LF);
        }
        return FileUtils.writeList(listFile, new String(out)); //(File, String)
    }

    private static class StringsIterator implements Iterator<String> {
        private BufferedReader reader;
        private String nextLine;

        private StringsIterator(final BufferedReader reader) {
            this.reader = reader;
            this.nextLine = null;
            next();
        }

        @Override
        public boolean hasNext() {
            return this.nextLine != null;
        }

        @Override
        public String next() {
            final String line = this.nextLine;
            if (this.reader != null) try {
                while ( (this.nextLine = this.reader.readLine()) != null ) {
                    this.nextLine = this.nextLine.trim();
                    if ( !this.nextLine.isEmpty() ) {
                        break;
                    }
                }
            } catch (final IOException e ) {
                this.nextLine = null;
            } catch (final OutOfMemoryError e ) {
                ConcurrentLog.logException(e);
                this.nextLine = null;
            }
            if (this.nextLine == null && this.reader != null) {
                try {
                    this.reader.close();
                } catch (final IOException e) {} finally {
                    this.reader = null;
                }
            }
            return line;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    /**
     * @param from
     * @param to
     * @throws IOException
     */
    private static void forceMove(final File from, final File to) throws IOException {
        if ( !(to.delete() && from.renameTo(to)) ) {
            // do it manually
            Files.copy(from, to);
            FileUtils.deletedelete(from);
        }
    }

    /**
     * Creates a temp file in the default system tmp directory (System property ""java.io.tmpdir"")
     * with a name constructed by combination of class name and name.
     * Marks the file with deleteOnExit() to be at least deleted on shutdown of jvm
     *
     * @param classObj name is used as prefix
     * @param name
     * @return temp file
     * @throws IOException
     */
    public static final File createTempFile(final Class<?> classObj, final String name) throws IOException {
        String parserClassName = classObj.getName();
        int idx = parserClassName.lastIndexOf('.');
        if ( idx != -1 ) {
            parserClassName = parserClassName.substring(idx + 1);
        }

        // get the file extension
        idx = name.lastIndexOf('/');
        final String fileName = (idx != -1) ? name.substring(idx + 1) : name;

        idx = fileName.lastIndexOf('.');
        final String fileExt = (idx > -1) ? fileName.substring(idx + 1) : "";

        // create the temp file
        final File tempFile =
                File.createTempFile(
                        parserClassName + "_" + ((idx > -1) ? fileName.substring(0, idx) : fileName),
                        (!fileExt.isEmpty()) ? "." + fileExt : fileExt);
        return tempFile;
    }

    /**
     * delete files and directories if a directory is not empty, delete also everything inside because
     * deletion sometimes fails on windows, there is also a windows exec included
     *
     * @param path
     */
    public static void deletedelete(final File path) {
        if ( path == null || !path.exists() ) {
            return;
        }

        // empty the directory first
        if ( path.isDirectory() ) {
            final String[] list = path.list();
            if ( list != null ) {
                for ( final String s : list ) {
                    deletedelete(new File(path, s));
                }
            }
        }

        if (path.exists()) path.delete();
        /*
        int c = 0;
        while ( c++ < 20 ) {
            if ( !path.exists() ) {
                break;
            }
            if ( path.delete() ) {
                break;
            }
            // some OS may be slow when giving up file pointer
            //System.runFinalization();
            //System.gc();
            try {
                Thread.sleep(200);
            } catch (final InterruptedException e ) {
                break;
            }
        }
         */
        if ( path.exists() ) {
            path.deleteOnExit();
            String p = "";
            try {
                p = path.getCanonicalPath();
            } catch (final IOException e1 ) {
                ConcurrentLog.logException(e1);
            }
            if ( System.getProperties().getProperty("os.name", "").toLowerCase().startsWith("windows") ) {
                // deleting files on windows sometimes does not work with java
                try {
                    final String command = "cmd /C del /F /Q \"" + p + "\"";
                    @SuppressWarnings("deprecation")
                    final Process r = Runtime.getRuntime().exec(command);
                    if ( r == null ) {
                        ConcurrentLog.severe("FileUtils", "cannot execute command: " + command);
                    } else {
                        final byte[] response = read(r.getInputStream());
                        ConcurrentLog.info("FileUtils", "deletedelete: " + UTF8.String(response));
                    }
                } catch (final IOException e ) {
                    ConcurrentLog.logException(e);
                }
            }
            if ( path.exists() ) {
                ConcurrentLog.severe("FileUtils", "cannot delete file " + p);
            }
        }
    }

    /**
     * Checks if a certain file is in a given directory.
     * @param file the file to check
     * @param directory the directory which must contain the file
     * @return true if file is contained in directory
     */
    public static boolean isInDirectory(final File file, final File directory) {

        boolean inDirectory;

        try {
            inDirectory = (
                    directory != null
                    && directory.isDirectory()
                    && file != null
                    && file.isFile()
                    && directory.getCanonicalPath().equalsIgnoreCase(
                            file.getParentFile().getCanonicalPath()));
        } catch (final IOException e) {
            inDirectory = false;
        }

        return inDirectory;
    }

    /**
     * Auto-detect the charset of content in a stream.
     * Used code from http://jchardet.sourceforge.net/.
     * Don't forget to close the stream in caller.
     * @see <a href="http://www-archive.mozilla.org/projects/intl/chardet.html">chardet</a>
     * @param inStream an open stream
     * @return a list of probable charsets
     * @throws IOException when a read error occured
     */
    public static List<String> detectCharset(final InputStream inStream) throws IOException {
        // auto-detect charset, used code from http://jchardet.sourceforge.net/; see also: http://www-archive.mozilla.org/projects/intl/chardet.html
        List<String> result;
        final nsDetector det = new nsDetector(nsPSMDetector.ALL);
        final byte[] buf = new byte[1024] ;
        int len;
        boolean done = false ;
        boolean isAscii = true ;
        while ((len = inStream.read(buf,0,buf.length)) != -1) {
            if (isAscii) {
                isAscii = det.isAscii(buf,len);
            }
            if (!isAscii && !done) {
                done = det.DoIt(buf,len, false);
            }
        }   det.DataEnd();
        result = new ArrayList<>();
        if (isAscii) {
            result.add(StandardCharsets.US_ASCII.name());
        } else {
            for (final String c: det.getProbableCharsets()) result.add(c); // worst case this returns "nomatch"
        }
        return result;
    }

    /**
     * Because the checking of very large files for their charset may take some time, we do this concurrently in this method
     * This method does not return anything but it logs an info line if the charset is a good choice
     * and it logs a warning line if the choice was bad.
     * @param file the file to be checked
     * @param givenCharset the charset that we consider to be valid
     * @param concurrent if this shall run concurrently
     */
    public static void checkCharset(final File file, final String givenCharset, final boolean concurrent) {
        final Thread t = new Thread("FileUtils.checkCharset") {
            @Override
            public void run() {
                try (final FileInputStream fileStream = new FileInputStream(file);
                        final BufferedInputStream imp = new BufferedInputStream(fileStream)) { // try-with-resource to close resources
                    final List<String> charsets = FileUtils.detectCharset(imp);
                    if (charsets.contains(givenCharset)) {
                        ConcurrentLog.info("checkCharset", "appropriate charset '" + givenCharset + "' for import of " + file + ", is part one detected " + charsets);
                    } else {
                        ConcurrentLog.warn("checkCharset", "possibly wrong charset '" + givenCharset + "' for import of " + file + ", use one of " + charsets);
                    }
                } catch (final IOException e) {}

            }
        };
        if (concurrent) t.start(); else t.run();
    }

}
