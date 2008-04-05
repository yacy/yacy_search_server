// plasmaStore.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 20.01.2004
//
// You agree that the Author(s) is (are) not responsible for cost,
// loss of data or any harm that may be caused by usage of this softare or
// this documentation. The usage of this software is on your own risk. The
// installation and usage (starting/running) of this software may allow other
// people or application to access your computer and any attached devices and
// is highly dependent on the configuration of the software which must be
// done by the user of the software;the author(s) is (are) also
// not responsible for proper configuration and usage of the software, even
// if provoked by documentation provided together with the software.
//
// THE SOFTWARE THAT FOLLOWS AS ART OF PROGRAMMING BELOW THIS SECTION
// IS PUBLISHED UNDER THE GPL AS DOCUMENTED IN THE FILE gpl.txt ASIDE THIS
// FILE AND AS IN http://www.gnu.org/licenses/gpl.txt
// ANY CHANGES TO THIS FILE ACCORDING TO THE GPL CAN BE DONE TO THE
// LINES THAT FOLLOWS THIS COPYRIGHT NOTICE HERE, BUT CHANGES MUST NOT
// BE DONE ABOVE OR INSIDE THE COPYRIGHT NOTICE. A RE-DISTRIBUTION
// MUST CONTAIN THE INTACT AND UNCHANGED COPYRIGHT NOTICE.
// CONTRIBUTIONS AND CHANGES TO THE PROGRAM CODE SHOULD BE MARKED AS SUCH.

/*
   This class provides storage functions for the plasma search engine.
   Unlike the plasmaSwitchboard, which holds run-time information,
   this class holds general index information that is in run-time
   specific.
*/

package de.anomic.plasma;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class plasmaStore {


    // some static helper methods
    public static void saveGzip(File f, byte[] content) throws IOException {
        java.util.zip.GZIPOutputStream gzipout = null;
        try {
            f.getParentFile().mkdirs();
            gzipout = new java.util.zip.GZIPOutputStream(new FileOutputStream(f));
            gzipout.write(content, 0, content.length);
        } finally {
            if (gzipout!=null)try{gzipout.close();}catch(Exception e){}
        }        
    }

    public static byte[] loadGzip(File f) throws IOException {
        java.util.zip.GZIPInputStream gzipin = null;
        try {
            gzipin = new java.util.zip.GZIPInputStream(new FileInputStream(f));
            byte[] result = new byte[1024];
            byte[] buffer = new byte[512];
            byte[] b;
            int len = 0;
            int last;
            while ((last = gzipin.read(buffer, 0, buffer.length)) > 0) {
                // assert the buffer to the result
                while (result.length - len < last) {
                    // the result array is too small, increase space
                    b = new byte[result.length * 2];
                    System.arraycopy(result, 0, b, 0, len); 
                    result = b; b = null;
                }
                // copy the last read
                System.arraycopy(buffer, 0, result, len, last); 
                len += last;
            }
            gzipin.close();
            // finished with reading. now cut the result to the right size
            b = new byte[len];
            System.arraycopy(result, 0, b, 0, len); 
            result = null;
            return b;
        } finally {
            if (gzipin != null) try{gzipin.close();}catch(Exception e){}
        }
    }

    /*    public static void saveProperties(File f, Properties props, String comment) throws IOException {
	File fp = f.getParentFile();
	if (fp != null) fp.mkdirs();
	FileOutputStream fos = new FileOutputStream(f);
	props.store(fos, comment);
	fos.close();
    }

    public static Properties loadProperties(File f) throws IOException {
	Properties p = new Properties();
	FileInputStream fis = new FileInputStream(f);
	p.load(fis);
	fis.close();
	return p;
    }
    */

    /*
    private static long[] appendFileToStack(File fragment, File dest) throws IOException {
		// returns a long[2] with
		// long[0] = startOfFileFragemt in dest
		// long[1] = lengthOfFileFragment in dest
		long l = fragment.length();
		long p = dest.length();
		RandomAccessFile fo = new RandomAccessFile(dest, "rw");
		FileInputStream fi = new FileInputStream(fragment);
		byte[] buffer = new byte[1024];
		int c;
		fo.seek(p);
		while ((c = fi.read(buffer)) >= 0)
			fo.write(buffer, 0, c);
		fi.close();
		fo.close();
		long[] r = new long[2];
		r[0] = p;
		r[1] = l;
		return r;
	}
	*/

    /*
    public static void main(String[] args) {
	try {
	    HashSet set = new HashSet();
	    for (int i = 0; i < args.length; i++) set.add(args[i]);
	    plasmaStore store = new plasmaStore(new File("DATABASE"));
	    List result = plasmaSearch.search(set);
	    for (int i = 0; i < result.size(); i++) {
		((plasmaLURL.entry) result.get(i)).print();
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
    */
}
