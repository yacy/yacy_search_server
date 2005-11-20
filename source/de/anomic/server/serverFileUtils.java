// serverFileUtils.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 05.08.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.PrintWriter;
import java.util.StringTokenizer;
import java.util.zip.GZIPOutputStream;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Properties;
import java.util.Hashtable;
import java.util.Iterator;

public final class serverFileUtils {
    
    public static int copy(InputStream source, OutputStream dest) throws IOException {
        byte[] buffer = new byte[4096];
        
        int c, total = 0;
        while ((c = source.read(buffer)) > 0) {
            dest.write(buffer, 0, c);
            dest.flush();
            total += c;
        }
        dest.flush();
        
        return total;
    }
          
    public static void copy(InputStream source, File dest) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(dest);
            copy(source, fos);
        } finally {
            if (fos != null) try {fos.close();} catch (Exception e) {}
        }
    }
    
    public static void copy(File source, OutputStream dest) throws IOException {
		InputStream fis = null;
        try {
			fis = new FileInputStream(source);
			copy(fis, dest);
        } finally {
            if (fis != null) try { fis.close(); } catch (Exception e) {}
        }
    }
    
    public static void copy(File source, File dest) throws IOException {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(source);
            fos = new FileOutputStream(dest);
            copy(fis, fos);
        } finally {
            if (fis != null) try {fis.close();} catch (Exception e) {}
            if (fos != null) try {fos.close();} catch (Exception e) {}
        }
    }

    public static byte[] read(InputStream source) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(source, baos);
        baos.close();
        return baos.toByteArray();
    }
    
    public static byte[] read(File source) throws IOException {
        byte[] buffer = new byte[(int) source.length()];
        InputStream fis = null;
        try {
            fis = new FileInputStream(source);
            int p = 0, c;
            while ((c = fis.read(buffer, p, buffer.length - p)) > 0) p += c;
        } finally {
            if (fis != null) try { fis.close(); } catch (Exception e) {}
        }
        return buffer;
    }
    
    public static byte[] readAndZip(File source) throws IOException {
        ByteArrayOutputStream byteOut = null;
        GZIPOutputStream zipOut = null;
        try {
            byteOut = new ByteArrayOutputStream((int)(source.length()/2));
            zipOut = new GZIPOutputStream(byteOut);
            copy(source, zipOut);
            zipOut.close();
            return byteOut.toByteArray();
        } finally {
            if (zipOut != null) try { zipOut.close(); } catch (Exception e) {}
            if (byteOut != null) try { byteOut.close(); } catch (Exception e) {}
        }
    }
    
    public static void writeAndZip(byte[] source, File dest) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(dest);
            writeAndZip(source, fos);
        } finally {
            if (fos != null) try {fos.close();} catch (Exception e) {}
        }
    }
    
    public static void writeAndZip(byte[] source, OutputStream dest) throws IOException {
        GZIPOutputStream zipOut = null;
        try {
            zipOut = new GZIPOutputStream(dest);
            write(source, zipOut);
            zipOut.close();
        } finally {
            if (zipOut != null) try { zipOut.close(); } catch (Exception e) {}
        }
    }
    
    public static void write(byte[] source, OutputStream dest) throws IOException {
        copy(new ByteArrayInputStream(source), dest);
    }
    
    public static void write(byte[] source, File dest) throws IOException {
        copy(new ByteArrayInputStream(source), dest);
    }
    
    public static HashSet loadList(String filename) {
        HashSet set = new HashSet();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if ((line.length() > 0) && (!(line.startsWith("#")))) set.add(line.trim().toLowerCase());
            }
            br.close();
        } catch (IOException e) {
        } finally {
            if (br != null) try { br.close(); } catch (Exception e) {}
        }
        return set;
    }

    public static Map loadHashMap(File f) {
        // load props
        Properties prop = new Properties();
        BufferedInputStream bufferedIn = null;
        try {
            prop.load(bufferedIn = new BufferedInputStream(new FileInputStream(f)));
        } catch (IOException e1) {
            System.err.println("ERROR: " + f.toString() + " not found in settings path");
            prop = null;
        } finally {
            if (bufferedIn != null)try{bufferedIn.close();}catch(Exception e){}
        }
        return (Hashtable) prop;
    }

    public static void saveMap(File file, Map props, String comment) throws IOException {
        PrintWriter pw = null;
        File tf = new File(file.toString() + "." + (System.currentTimeMillis() % 1000));
        pw = new PrintWriter(new BufferedOutputStream(new FileOutputStream(tf)));
        pw.println("# " + comment);
        Iterator i = props.entrySet().iterator();
        String key, value;
        Map.Entry entry;
        while (i.hasNext()) {
            entry  = (Map.Entry) i.next();
            key = (String) entry.getKey();
            value = ((String) entry.getValue()).replaceAll("\n", "\\\\n");
            pw.println(key + "=" + value);
        }
        pw.println("# EOF");
        pw.close();
        file.delete();
        tf.renameTo(file);
    }
    
    public static Set loadSet(File file, int chunksize, boolean tree) throws IOException {
        Set set = (tree) ? (Set) new TreeSet() : (Set) new HashSet();
        byte[] b = read(file);
        for (int i = 0; (i + chunksize) <= b.length; i++) {
            set.add(new String(b, i, chunksize));
        }
        return set;
    }

    public static Set loadSet(File file, String sep, boolean tree) throws IOException {
        Set set = (tree) ? (Set) new TreeSet() : (Set) new HashSet();
        byte[] b = read(file);
        StringTokenizer st = new StringTokenizer(new String(b), sep);
        while (st.hasMoreTokens()) {
            set.add(st.nextToken());
        }
        return set;
    }

    public static void saveSet(File file, Set set, String sep) throws IOException {
        File tf = new File(file.toString() + "." + (System.currentTimeMillis() % 1000));
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tf));
        Iterator i = set.iterator();
        String key;
        if (i.hasNext()) {
            key = i.next().toString();
            bos.write(key.getBytes());
        }
        while (i.hasNext()) {
            key = i.next().toString();
            if (sep != null) bos.write(sep.getBytes());
            bos.write(key.getBytes());
        }
        bos.close();
        file.delete();
        tf.renameTo(file);
    }
    
    public static void moveAll(File from_dir, File to_dir) {
        if (!(from_dir.isDirectory())) return;
        if (!(to_dir.isDirectory())) return;
        String[] list = from_dir.list();
        for (int i = 0; i < list.length; i++) (new File(from_dir, list[i])).renameTo(new File(to_dir, list[i]));
    }
    
    public static void main(String[] args) {
        try {
            writeAndZip("ein zwei drei, Zauberei".getBytes(), new File("zauberei.txt.gz"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
