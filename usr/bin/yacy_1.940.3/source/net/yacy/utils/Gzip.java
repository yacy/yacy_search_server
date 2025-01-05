// gzip.java
// -------------------------------------
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

package net.yacy.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.ConcurrentLog;


public class Gzip {

    private final static ConcurrentLog logger = new ConcurrentLog("GZIP");

    public static void gzipFile(final String inFile, final String outFile) {
    	InputStream fin = null;
    	OutputStream fout = null;
    	try {
    		fin  = new BufferedInputStream(new FileInputStream(inFile));
    		fout = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)), 65536){{def.setLevel(Deflater.BEST_COMPRESSION);}};
    		copy(fout, fin, 1024);

    	} catch (final FileNotFoundException e) {
    		logger.warn("ERROR: file '" + inFile + "' not found", e);
    	} catch (final IOException e) {
            logger.warn("ERROR: IO trouble ",e);
    	} finally {
    		if(fin != null) {
    			try {
    				fin.close();
    			} catch(IOException e) {
    				logger.warn("ERROR: Could not close file " + inFile);
    			}
    		}
    		if(fout != null) {
    			try {
    				fout.close();
    			} catch(IOException e) {
    				logger.warn("ERROR: Could not close file " + outFile);
    			}
    		}
    	}
    }

    public static File gunzipFile(final File in) {
        assert in.getName().endsWith(".gz");
        final String on = in.getName().substring(0, in.getName().length() - 3);
        final File outf = new File(in.getParent(), on);
        gunzipFile(in, outf);
        return outf;
    }

    public static void gunzipFile(final File inFile, final File outFile) {
	try (
		/* Resources automatically closed by this try-with-resources statement */
		final FileInputStream fileInStream = new FileInputStream(inFile);
	    final InputStream  fin  = new GZIPInputStream(new BufferedInputStream(fileInStream));
		final FileOutputStream fileOutStream = new FileOutputStream(outFile);
	    final OutputStream fout = new BufferedOutputStream(fileOutStream);
	) {
	    copy(fout, fin, 1024);
	} catch (final FileNotFoundException e) {
	    logger.warn("ERROR: file '" + inFile + "' not found", e);
	} catch (final IOException e) {
            logger.warn("ERROR: IO trouble ",e);
	}
    }

    public static byte[] gzipString(final String in) {
        return gzip(UTF8.getBytes(in));
    }

    public static String gunzipString(final byte[] in) {
	    return UTF8.String(gunzip(in));
    }
    
    public static byte[] gzip(final byte[] b) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(b.length);
            GZIPOutputStream out = new GZIPOutputStream(baos, Math.min(65536, b.length)){{def.setLevel(Deflater.BEST_COMPRESSION);}};
            out.write(b, 0, b.length);
            out.finish();
            out.close();
            return baos.toByteArray();
        } catch (IOException e) {}
        return null;
    }
    
    public static byte[] gunzip(byte[] b) {
        byte[] buffer = new byte[Math.min(2^20, b.length)];
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(b.length * 2);
            GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(b), Math.min(65536, b.length));
            int l; while ((l = in.read(buffer)) > 0) baos.write(buffer, 0, l);
            in.close();
            baos.close();
            return baos.toByteArray();
        } catch (IOException e) {}
        return null;
    }

    private static void copy(final OutputStream out, final InputStream in, final int bufferSize) throws IOException {
	final InputStream  bIn  = new BufferedInputStream(in, bufferSize);
	final OutputStream bOut = new BufferedOutputStream(out, bufferSize);
	final byte[] buf = new byte[bufferSize];
	int n;
	while ((n = bIn.read(buf)) > 0) bOut.write(buf, 0, n);
	bIn.close();
	bOut.close();
    }


    // some static helper methods
    public static void saveGzip(final File f, final byte[] content) throws IOException {
        f.getParentFile().mkdirs();
        
        try (
        	/* Resources automatically closed by this try-with-resources statement */
        	final FileOutputStream fileOutStream = new FileOutputStream(f);
            final java.util.zip.GZIPOutputStream gzipout = new java.util.zip.GZIPOutputStream(fileOutStream, 65536){{def.setLevel(Deflater.BEST_COMPRESSION);}};
        ) {
            gzipout.write(content, 0, content.length);
        }
    }

    public static byte[] loadGzip(final File f) throws IOException {
        java.util.zip.GZIPInputStream gzipin = null;
        try {
            gzipin = new java.util.zip.GZIPInputStream(new FileInputStream(f));
            byte[] result = new byte[1024];
            final byte[] buffer = new byte[512];
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
            return b;
        } finally {
            if (gzipin != null) try{gzipin.close();}catch(final Exception e){}
        }
    }

    private static void help() {
	System.out.println("AnomicGzip (2004) by Michael Christen");
	System.out.println("usage: gzip [-u] <file> [<target-file>]");
    }


    public static void main(final String[] s) {
	if (s.length == 0) {
	    help();
	    System.exit(0);
	}
	if ((s[0].equals("-h")) || (s[0].equals("-help"))) {
	    help();
	    System.exit(0);
	}
	if (s[0].equals("-u")) {
	    if ((s.length < 2) || (s.length > 3)) {help(); System.exit(-1);}
	    String target;
	    if (s.length == 2) {
		if (s[1].endsWith(".gz"))
		    target = s[1].substring(0, s[1].length() - 3);
		else
		    target = s[1] + ".gunzip";
	    } else {
		target = s[2];
	    }
	    Gzip.gunzipFile(new File(s[1]), new File(target));
	    System.exit(0);
	}
	if ((s.length < 1) || (s.length > 2)) {help(); System.exit(-1);}
	String target;
	if (s.length == 1) target = s[0] + ".gz"; else target = s[1];
	Gzip.gzipFile((s[0]), target);
	System.exit(0);
    }

}
