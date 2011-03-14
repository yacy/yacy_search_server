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
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.logging.Log;


public final class FileUtils {

    private static final int DEFAULT_BUFFER_SIZE = 1024; // this is also the maximum chunk size
    
    public static long copy(final InputStream source, final OutputStream dest) throws IOException {
        return copy(source, dest, -1);
    }
    
    /**
     * Copies an InputStream to an OutputStream.
     * 
     * @param source InputStream
     * @param dest OutputStream
     * @param count the total amount of bytes to copy (-1 for all, else must be greater than zero)
     * @return Total number of bytes copied.
     * @throws IOException 
     * 
     * @see #copy(InputStream source, File dest)
     * @see #copyRange(File source, OutputStream dest, int start)
     * @see #copy(File source, OutputStream dest)
     * @see #copy(File source, File dest)
     */
    public static long copy(final InputStream source, final OutputStream dest, final long count) throws IOException {
        assert count < 0 || count > 0 : "precondition violated: count == " + count + " (nothing to copy)";
        if (count == 0) {
            // no bytes to copy
            return 0;
        }
        
        final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];                
        int chunkSize = (int) ((count > 0) ? Math.min(count, DEFAULT_BUFFER_SIZE) : DEFAULT_BUFFER_SIZE);
        
        int c; long total = 0;
        while ((c = source.read(buffer, 0, chunkSize)) > 0) {
            dest.write(buffer, 0, c);
            dest.flush();
            total += c;
            
            if (count > 0) {
                chunkSize = (int) Math.min(count - total, DEFAULT_BUFFER_SIZE);
                if (chunkSize == 0) break;
            }
            
        }
        dest.flush();
        
        return total;
    }
    
    public static int copy(final File source, final Charset inputCharset, final Writer dest) throws IOException {
        InputStream fis = null;
        try {
            fis = new FileInputStream(source);
            return copy(fis, dest, inputCharset);
        } finally {
            if (fis != null) try { fis.close(); } catch (final Exception e) {}
        }
    }    
    
    public static int copy(final InputStream source, final Writer dest) throws IOException {
        final InputStreamReader reader = new InputStreamReader(source);
        return copy(reader,dest);
    }
    
    public static int copy(final InputStream source, final Writer dest, final Charset inputCharset) throws IOException {
        final InputStreamReader reader = new InputStreamReader(source,inputCharset);
        return copy(reader,dest);
    }
    
    public static int copy(final String source, final Writer dest) throws IOException {
        dest.write(source);
        dest.flush();
        return source.length();
    }
    
    public static int copy(final Reader source, final Writer dest) throws IOException {        
        final char[] buffer = new char[DEFAULT_BUFFER_SIZE];
        int count = 0;
        int n = 0;
        try {
            while (-1 != (n = source.read(buffer))) {
                dest.write(buffer, 0, n);
                count += n;
            }
            dest.flush();
        } catch (final Exception e) {
            // an "sun.io.MalformedInputException: Missing byte-order mark" - exception may occur here
            throw new IOException(e.getMessage());
        }
        return count;
    }
    
    public static void copy(final InputStream source, final File dest) throws IOException {
        copy(source,dest,-1);
    }
    
    /**
     * Copies an InputStream to a File.
     * 
     * @param source    InputStream
     * @param dest    File
     * @param count the amount of bytes to copy
     * @throws IOException 
     * @see #copy(InputStream source, OutputStream dest)
     * @see #copyRange(File source, OutputStream dest, int start)
     * @see #copy(File source, OutputStream dest)
     * @see #copy(File source, File dest)
     */
    public static void copy(final InputStream source, final File dest, final long count) throws IOException {
        String path = dest.getParent();
        if (path != null && path.length() > 0) new File(path).mkdirs();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(dest);
            copy(source, fos, count);
        } finally {
            if (fos != null) try {fos.close();} catch (final Exception e) { Log.logWarning("FileUtils", "cannot close FileOutputStream for "+ dest +"! "+ e.getMessage()); }
        }
    }

    /**
     * Copies a part of a File to an OutputStream.
     * @param source    File
     * @param dest    OutputStream
     * @param start Number of bytes to skip from the beginning of the File
     * @throws IOException 
     * @see #copy(InputStream source, OutputStream dest)
     * @see #copy(InputStream source, File dest)
     * @see #copy(File source, OutputStream dest)
     * @see #copy(File source, File dest)
     */
    public static void copyRange(final File source, final OutputStream dest, final int start) throws IOException {
        InputStream fis = null;
        try {
            fis = new FileInputStream(source);
            final long skipped = fis.skip(start);
            if (skipped != start) throw new IllegalStateException("Unable to skip '" + start + "' bytes. Only '" + skipped + "' bytes skipped.");
            copy(fis, dest, -1);
        } finally {
            if (fis != null) try { fis.close(); } catch (final Exception e) {}
        }
    }

    /**
     * Copies a File to an OutputStream.
     * @param source    File
     * @param dest    OutputStream
     * @throws IOException 
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
            if (fis != null) try { fis.close(); } catch (final Exception e) {}
        }
    }

    /**
     * Copies a File to a File.
     * @param source    File
     * @param dest    File
     * @param count the amount of bytes to copy
     * @throws IOException 
     * @see #copy(InputStream source, OutputStream dest)
     * @see #copy(InputStream source, File dest)
     * @see #copyRange(File source, OutputStream dest, int start)
     * @see #copy(File source, OutputStream dest)
     */
    public static void copy(final File source, final File dest) throws IOException {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(source);
            fos = new FileOutputStream(dest);
            copy(fis, fos, -1);
        } finally {
            if (fis != null) try {fis.close();} catch (final Exception e) {}
            if (fos != null) try {fos.close();} catch (final Exception e) {}
        }
    }

    public static void copy(final byte[] source, final OutputStream dest) throws IOException {
        dest.write(source, 0, source.length);
        dest.flush();
    }
    
    public static void copy(final byte[] source, final File dest) throws IOException {
        copy(new ByteArrayInputStream(source), dest);
    }

    public static byte[] read(final InputStream source) throws IOException {
        return read(source,-1);
    }
    
    public static byte[] read(final InputStream source, final int count) throws IOException {
        if (count > 0) {
            byte[] b = new byte[count];
            int c = source.read(b, 0, count);
            assert c == count: "count = " + count + ", c = " + c;
            if (c != count) {
            	byte[] bb = new byte[c];
            	System.arraycopy(b, 0, bb, 0, c);
            	return bb;
            }
            return b;
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        copy(source, baos);
        baos.close();
        return baos.toByteArray();
    }

    public static byte[] read(final File source) throws IOException {
        final byte[] buffer = new byte[(int) source.length()];
        InputStream fis = null;
        try {
            fis = new FileInputStream(source);
            int p = 0, c;
            while ((c = fis.read(buffer, p, buffer.length - p)) > 0) p += c;
        } finally {
            if (fis != null) try { fis.close(); } catch (final Exception e) {}
            fis = null;
        }
        return buffer;
    }

    public static byte[] readAndZip(final File source) throws IOException {
        ByteArrayOutputStream byteOut = null;
        GZIPOutputStream zipOut = null;
        try {
            byteOut = new ByteArrayOutputStream((int)(source.length()/2));
            zipOut = new GZIPOutputStream(byteOut);
            copy(source, zipOut);
            zipOut.close();
            return byteOut.toByteArray();
        } finally {
            if (zipOut != null) try { zipOut.close(); } catch (final Exception e) {}
            if (byteOut != null) try { byteOut.close(); } catch (final Exception e) {}
        }
    }

    public static void writeAndGZip(final byte[] source, final File dest) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(dest);
            writeAndGZip(source, fos);
        } finally {
            if (fos != null) try {fos.close();} catch (final Exception e) {}
        }
    }

    public static void writeAndGZip(final byte[] source, final OutputStream dest) throws IOException {
        GZIPOutputStream zipOut = null;
        try {
            zipOut = new GZIPOutputStream(dest);
            copy(source, zipOut);
            zipOut.close();
        } finally {
            if (zipOut != null) try { zipOut.close(); } catch (final Exception e) {}
        }
    }
    
    /**
     * This function determines if a byte array is gzip compressed and uncompress it
     * @param source properly gzip compressed byte array
     * @return uncompressed byte array
     * @throws IOException
     */
    public static byte[] uncompressGZipArray(byte[] source) throws IOException {
    	if (source == null) return null;
    	
        // support of gzipped data (requested by roland)
		/* "Bitwise OR of signed byte value
		 * 
		 * [...] Values loaded from a byte array are sign extended to 32 bits before
		 * any any bitwise operations are performed on the value. Thus, if b[0]
		 * contains the value 0xff, and x is initially 0, then the code ((x <<
		 * 8) | b[0]) will sign extend 0xff to get 0xffffffff, and thus give the
		 * value 0xffffffff as the result. [...]" findbugs description of BIT_IOR_OF_SIGNED_BYTE
		 */
        if ((source.length > 1) && (((source[1] << 8) | (source[0] & 0xff)) == GZIPInputStream.GZIP_MAGIC)) {
            System.out.println("DEBUG: uncompressGZipArray - uncompressing source");
            try {
                final ByteArrayInputStream byteInput = new ByteArrayInputStream(source);
                final ByteArrayOutputStream byteOutput = new ByteArrayOutputStream(source.length / 5);
                final GZIPInputStream zippedContent = new GZIPInputStream(byteInput);
                final byte[] data = new byte[1024];
                int read = 0;
                
                // reading gzip file and store it uncompressed
                while((read = zippedContent.read(data, 0, 1024)) != -1) {
                    byteOutput.write(data, 0, read);
                }
                zippedContent.close();
                byteOutput.close();   
                
                source = byteOutput.toByteArray();
            } catch (final Exception e) {
                if (!e.getMessage().equals("Not in GZIP format")) {
                    throw new IOException(e.getMessage());
                }
            }
        }    
        
        return source;
    }

    public static HashSet<String> loadList(final File file) {
        final HashSet<String> set = new HashSet<String>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0 && line.charAt(0) != '#') set.add(line.trim().toLowerCase());
            }
            br.close();
        } catch (final IOException e) {
        } finally {
            if (br != null) try { br.close(); } catch (final Exception e) {}
        }
        return set;
    }

    public static ConcurrentHashMap<String, String> loadMap(final File f) {
        // load props
        try {
            final byte[] b = read(f);
            return table(strings(b));
        } catch (final IOException e2) {
            Log.logSevere("FileUtils", f.toString() + " not found", e2);
            return null;
        }
    }
    
    public static void saveMap(final File file, final Map<String, String> props, final String comment) throws IOException {
        PrintWriter pw = null;
        final File tf = new File(file.toString() + "." + (System.currentTimeMillis() % 1000));
        pw = new PrintWriter(tf, "UTF-8");
        pw.println("# " + comment);
        String key, value;
        for (final Map.Entry<String, String> entry: props.entrySet()) {
            key = entry.getKey();
            if (key != null)
            	key = key.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\=");
            if (entry.getValue() == null) {
                value = "";
            } else {
                value = entry.getValue().replace("\\", "\\\\").replace("\n", "\\n");
            }
            pw.println(key + "=" + value);
        }
        pw.println("# EOF");
        pw.close();
        forceMove(tf, file);
    }

    public static Set<String> loadSet(final File file, final int chunksize, final boolean tree) throws IOException {
        final Set<String> set = (tree) ? (Set<String>) new TreeSet<String>() : (Set<String>) new HashSet<String>();
        final byte[] b = read(file);
        for (int i = 0; (i + chunksize) <= b.length; i++) {
            set.add(UTF8.String(b, i, chunksize));
        }
        return set;
    }

    public static Set<String> loadSet(final File file, final String sep, final boolean tree) throws IOException {
        final Set<String> set = (tree) ? (Set<String>) new TreeSet<String>() : (Set<String>) new HashSet<String>();
        final byte[] b = read(file);
        final StringTokenizer st = new StringTokenizer(UTF8.String(b), sep);
        while (st.hasMoreTokens()) {
            set.add(st.nextToken());
        }
        return set;
    }

    public static void saveSet(final File file, final String format, final Set<byte[]> set, final String sep) throws IOException {
        final File tf = new File(file.toString() + ".prt" + (System.currentTimeMillis() % 1000));
        OutputStream os = null;
        if ((format == null) || (format.equals("plain"))) {
            os = new BufferedOutputStream(new FileOutputStream(tf));
        } else if (format.equals("gzip")) {
            os = new GZIPOutputStream(new FileOutputStream(tf));
        } else if (format.equals("zip")) {
            final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file));
            String name = file.getName();
            if (name.endsWith(".zip")) name = name.substring(0, name.length() - 4);
            zos.putNextEntry(new ZipEntry(name + ".txt"));
            os = zos;
        }
        if(os != null) {
            for (final Iterator<byte[]> i = set.iterator(); i.hasNext(); ) {
                os.write(i.next());
                if (sep != null) os.write(UTF8.getBytes(sep));
            }
            os.close();
        }
        forceMove(tf, file);
    }

    public static void saveSet(final File file, final String format, final RowSet set, final String sep) throws IOException {
        final File tf = new File(file.toString() + ".prt" + (System.currentTimeMillis() % 1000));
        OutputStream os = null;
        if ((format == null) || (format.equals("plain"))) {
            os = new BufferedOutputStream(new FileOutputStream(tf));
        } else if (format.equals("gzip")) {
            os = new GZIPOutputStream(new FileOutputStream(tf));
        } else if (format.equals("zip")) {
            final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file));
            String name = file.getName();
            if (name.endsWith(".zip")) name = name.substring(0, name.length() - 4);
            zos.putNextEntry(new ZipEntry(name + ".txt"));
            os = zos;
        }
        if (os != null) {
            final Iterator<Row.Entry> i = set.iterator();
            String key;
            if (i.hasNext()) {
                key = UTF8.String(i.next().getPrimaryKeyBytes());
                os.write(UTF8.getBytes(key));
            }
            while (i.hasNext()) {
                key = UTF8.String(i.next().getPrimaryKeyBytes());
                if (sep != null) os.write(UTF8.getBytes(sep));
                os.write(UTF8.getBytes(key));
            }
            os.close();
        }
        forceMove(tf, file);
    }

    public static ConcurrentHashMap<String, String> table(Reader r) {
    	BufferedReader br = new BufferedReader(r);
    	return table(new StringsIterator(br));
	}
    
    public static ConcurrentHashMap<String, String> table(Iterator<String> li) {
    	String line;
    	final ConcurrentHashMap<String, String> props = new ConcurrentHashMap<String, String>();
    	while (li.hasNext()) {
        	int pos = 0;
    		line = li.next().trim();
    		if (line.length() > 0 && line.charAt(0) == '#') continue; // exclude comments
    		do {
    			// search for unescaped =
    			pos = line.indexOf('=', pos+1);
    		} while ( pos > 0 && line.charAt(pos-1) == '\\');
    		if (pos > 0) {
    			String key = line.substring(0, pos).trim().replace("\\=", "=").replace("\\n", "\n").replace("\\", "\\");
    			String value = line.substring(pos + 1).trim().replace("\\n", "\n").replace("\\\\", "\\");
        		props.put(key, value);
    		}
    	}
    	return props;
	}
    
    public static Map<String, String> table(final byte[] a) {
        return table(strings(a));
    }

    public static Iterator<String> strings(byte[] a) {
        if (a == null) return new ArrayList<String>().iterator();
        try {
            return new StringsIterator(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(a), "UTF-8")));
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }
    

    /**
     * Read lines of a file into an ArrayList.
     * 
     * @param listFile the file
     * @return the resulting array as an ArrayList
     */
    public static ArrayList<String> getListArray(final File listFile){
        String line;
        final ArrayList<String> list = new ArrayList<String>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile),"UTF-8"));

            while((line = br.readLine()) != null){
                list.add(line);
            }
            br.close();
        } catch(final IOException e) {
            // list is empty
        } finally {
            if (br!=null) try { br.close(); } catch (final Exception e) {}
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
    public static boolean writeList(final File listFile, final String out) {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new PrintWriter(new FileWriter(listFile)));
            bw.write(out);
            bw.close();
            return true;
        } catch(final IOException e) {
            return false;
        } finally {
            if (bw!=null) try { bw.close(); } catch (final Exception e) {}
        }
    }

    public static final char LF = (char) 10;
    public static final char CR = (char) 13;

    /**
     * Read lines of a text file into a String, optionally ignoring comments.
     * 
     * @param listFile the File to read from.
     * @param withcomments If <code>false</code> ignore lines starting with '#'.
     * @return String representation of the file content.
     */
    public static String getListString(final File listFile, final boolean withcomments){
        final StringBuilder temp = new StringBuilder(300);
        
        BufferedReader br = null;        
        try{
            br = new BufferedReader(new InputStreamReader(new FileInputStream(listFile)));
            temp.ensureCapacity((int) listFile.length());
            
            // Read the List
            String line = "";
            while ((line = br.readLine()) != null) {
                if (line.length() == 0) continue;
                if (line.charAt(0) != '#' || withcomments) {
                    //temp += line + serverCore.CRLF_STRING;
                    temp.append(line).append(CR).append(LF);
                }
            }
            br.close();
        } catch (final IOException e) {
        } finally {
            if (br!=null) try { br.close(); } catch (final Exception e) {}
        }

        return new String(temp);
    }

    /**
     * Read content of a directory into a String array of file names.
     * @param dirname The directory to get the file listing from. If it doesn't exist yet,
     * it will be created.
     * @return array of file names
     */
    public static List<String> getDirListing(final String dirname){
        return getDirListing(dirname, null);
    }
    
    /**
     * Read content of a directory into a String array of file names.
     * @param dirname The directory to get the file listing from. If it doesn't exist yet,
     * it will be created.
     * @param filter String which contains a regular expression which has to be matched by
     * file names in order to appear in returned array. All file names will be returned if
     * filter is null.
     * @return array of file names
     */
    public static List<String> getDirListing(final String dirname, final String filter) {
        return getDirListing(new File(dirname), filter);
    }

    /**
     * Read content of a directory into a String array of file names.
     * 
     * @param dir The directory to get the file listing from. If it doesn't exist yet,
     * it will be created.
     * @return array of file names
     */
    public static List<String> getDirListing(final File dir){
        return getDirListing(dir, null);
    }
    
    /**
     * Read content of a directory into a String array of file names.
     * @param dir The directory to get the file listing from. If it doesn't exist yet,
     * it will be created.
     * @param filter String which contains a regular expression which has to be matched by
     * file names in order to appear in returned array. All file names will be returned if
     * filter is null.
     * @return array of file names
     */
    public static List<String> getDirListing(final File dir, final String filter){
        List<String> ret = new LinkedList<String>();
        File[] fileList;
        if (dir != null ) {
            if (!dir.exists()) {
                dir.mkdir();
            }
            fileList = dir.listFiles();
            for (int i=0; i<= fileList.length-1; i++) {
                if (filter == null || fileList[i].getName().matches(filter)) {
                    ret.add(fileList[i].getName());
                }
            }
            return ret;
        }
        return null;
    }    

    // same as below
    public static ArrayList<File> getDirsRecursive(final File dir, final String notdir){
        return getDirsRecursive(dir, notdir, true);
    }
    
    /**
     * Returns a List of all dirs and subdirs as File Objects
     *
     * Warning: untested
     */
    public static ArrayList<File> getDirsRecursive(final File dir, final String notdir, final boolean excludeDotfiles){
        final File[] dirList = dir.listFiles();
        final ArrayList<File> resultList = new ArrayList<File>();
        ArrayList<File> recursive;
        Iterator<File> iter;
        for (int i=0;i<dirList.length;i++) {
            if (dirList[i].isDirectory() && (!excludeDotfiles || !dirList[i].getName().startsWith(".")) && !dirList[i].getName().equals(notdir)) {
                resultList.add(dirList[i]);
                recursive = getDirsRecursive(dirList[i], notdir, excludeDotfiles);
                iter=recursive.iterator();
                while (iter.hasNext()) {
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
    public static boolean writeList(final File listFile, final String[] list){
        final StringBuilder out = new StringBuilder(list.length * 40 + 1);
        for(int i=0;i < list.length; i++){
            out.append(list[i]).append(CR).append(LF);
        }
        return FileUtils.writeList(listFile, new String(out)); //(File, String)
    }
    
    public static class StringsIterator implements Iterator<String> {
        private final BufferedReader reader;
        private String nextLine;
        public StringsIterator(final BufferedReader reader) {
            this.reader = reader;
            this.nextLine = null;
            next();
        }
        public boolean hasNext() {
            return nextLine != null;
        }

        public String next() {
            String line = nextLine;
            try {
                while ((nextLine = reader.readLine()) != null) {
                    nextLine = nextLine.trim();
                    if (nextLine.length() > 0) break;
                }
            } catch (IOException e) {
                nextLine = null;
            } catch (OutOfMemoryError e) {
                Log.logException(e);
                nextLine = null;
            }
            return line;
        }

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
        if(!(to.delete() && from.renameTo(to))) {
            // do it manually
            copy(from, to);
            FileUtils.deletedelete(from);
        }
    }
    
    /**
    * Moves all files from a directory to another.
    * @param from_dir    Directory which contents will be moved.
    * @param to_dir    Directory to move into. It must exist already.
    */
    public static void moveAll(final File from_dir, final File to_dir) {
        if (!(from_dir.isDirectory())) return;
        if (!(to_dir.isDirectory())) return;
        final String[] list = from_dir.list();
        for (int i = 0; i < list.length; i++) {
        	if(!new File(from_dir, list[i]).renameTo(new File(to_dir, list[i])))
        		Log.logWarning("serverFileUtils", "moveAll(): could not move from "+ from_dir + list[i] +" to "+ to_dir + list[i]);
        }
    }
    

    public static class dirlistComparator implements Comparator<File>, Serializable {
        
        /**
		 * generated serial
		 */
		private static final long serialVersionUID = -5196490300039230135L;

		public int compare(final File file1, final File file2) {
            if (file1.isDirectory() && !file2.isDirectory()) {
                return -1;
            } else if (!file1.isDirectory() && file2.isDirectory()) {
                return 1;
            } else {
                return file1.getName().compareToIgnoreCase(file2.getName());
            }
        }
    }    

    public static final File createTempFile(Class<?> classObj, final String name) throws IOException {
        String parserClassName = classObj.getName();
        int idx = parserClassName.lastIndexOf('.');
        if (idx != -1) {
            parserClassName = parserClassName.substring(idx+1);
        } 
                    
        // get the file extension
        idx = name.lastIndexOf('/');
        final String fileName = (idx != -1) ? name.substring(idx+1) : name;        
        
        idx = fileName.lastIndexOf('.');
        final String fileExt = (idx > -1) ? fileName.substring(idx+1) : "";
        
        // create the temp file
        final File tempFile = File.createTempFile(parserClassName + "_" + ((idx>-1)?fileName.substring(0,idx):fileName), (fileExt.length()>0)?"."+fileExt:fileExt);
        return tempFile;
    }
    
    /**
     * copies the input stream to one output stream (byte per byte)
     * @param in
     * @param out
     * @return number of copies bytes
     * @throws IOException
     */
    public static int copyToStream(final BufferedInputStream in, final BufferedOutputStream out) throws IOException {
        int count = 0;
        // copy bytes
        int b;
        while ((b = in.read()) != -1) {
            count++;
            out.write(b);
        }
        out.flush();
        return count;
    }
    
    /**
     * copies the input stream to both output streams (byte per byte)
     * @param in
     * @param out0
     * @param out1
     * @return number of copies bytes
     * @throws IOException
     */
    public static int copyToStreams(final BufferedInputStream in, final BufferedOutputStream out0, final BufferedOutputStream out1) throws IOException {
        assert out0 != null;
        assert out1 != null;
        
        int count = 0;
        // copy bytes
        int b;
        while((b = in.read()) != -1) {
            count++;
            out0.write(b);
            out1.write(b);
        }
        out0.flush();
        out1.flush();
        return count;
    }

    /**
     * copies the input stream to all writers (byte per byte)
     * @param data
     * @param writer
     * @param charSet
     * @return
     * @throws IOException
     */
    public static int copyToWriter(final BufferedInputStream data, final BufferedWriter writer, final Charset charSet) throws IOException {
        // the docs say: "For top efficiency, consider wrapping an InputStreamReader within a BufferedReader."
        final Reader sourceReader = new InputStreamReader(data, charSet);
        
        int count = 0;
        // copy bytes
        int b;
        while((b = sourceReader.read()) != -1) {
            count++;
            writer.write(b);
        }
        writer.flush();
        return count;
    }
    
    public static int copyToWriters(final BufferedInputStream data, final BufferedWriter writer0, final BufferedWriter writer1, final Charset charSet) throws IOException {
        // the docs say: "For top efficiency, consider wrapping an InputStreamReader within a BufferedReader."
        assert writer0 != null;
        assert writer1 != null;
        final Reader sourceReader = new InputStreamReader(data, charSet);
        
        int count = 0;
        // copy bytes
        int b;
        while((b = sourceReader.read()) != -1) {
            count++;
            writer0.write(b);
            writer1.write(b);
        }
        writer0.flush();
        writer1.flush();
        return count;
    }
    
    /**
     * delete files and directories
     * if a directory is not empty, delete also everything inside
     * because deletion sometimes fails on windows, there is also a windows exec included
     * @param path
     */
    public static void deletedelete(final File path) {
        if (path == null || !path.exists()) return;

        // empty the directory first
        if (path.isDirectory()) {
            final String[] list = path.list();
            if (list != null) {
                for (String s: list) deletedelete(new File(path, s));
            }
        }
        
        int c = 0;
        while (c++ < 20) {
            if (!path.exists()) break;
            if (path.delete()) break;
            // some OS may be slow when giving up file pointer
            //System.runFinalization();
            //System.gc();
            try { Thread.sleep(200); } catch (InterruptedException e) { break; }
        }
        if (path.exists()) {
            path.deleteOnExit();
            String p = "";
            try {
                p = path.getCanonicalPath();
            } catch (IOException e1) {
                Log.logException(e1);
            }
            if (System.getProperties().getProperty("os.name","").toLowerCase().startsWith("windows")) {
                // deleting files on windows sometimes does not work with java
                try {
                    String command = "cmd /C del /F /Q \"" + p + "\"";
                    Process r = Runtime.getRuntime().exec(command);
                    if (r == null) {
                        Log.logSevere("FileUtils", "cannot execute command: " + command);
                    } else {
                        byte[] response = read(r.getInputStream());
                        Log.logInfo("FileUtils", "deletedelete: " + UTF8.String(response));
                    }
                } catch (IOException e) {
                    Log.logException(e);
                }                
            }
            if (path.exists()) Log.logSevere("FileUtils", "cannot delete file " + p);
        }
    }
    
    public static void main(final String[] args) {
        try {
            writeAndGZip("ein zwei drei, Zauberei".getBytes(), new File("zauberei.txt.gz"));
        } catch (final IOException e) {
            Log.logException(e);
        }
    }

}
