// gzip.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 13.05.2004
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

package de.anomic.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import de.anomic.server.logging.serverLog;

public class gzip {

    private static serverLog logger = new serverLog("GZIP");
    
    public static void gzipFile(String inFile, String outFile) {
	try {
	    InputStream  fin  = new FileInputStream(inFile);
	    OutputStream fout = new GZIPOutputStream(new FileOutputStream(outFile), 128);
	    copy(fout, fin, 128);
	    fin.close();
	    fout.close();
	} catch (FileNotFoundException e) {
            //System.err.println("ERROR: file '" + inFile + "' not found");
	    logger.logWarning("ERROR: file '" + inFile + "' not found", e);
	} catch (IOException e) {
            //System.err.println("ERROR: IO trouble ");
            logger.logWarning("ERROR: IO trouble ",e);
	}
    }

    public static void gunzipFile(String inFile, String outFile) {
	try {
	    InputStream  fin  = new GZIPInputStream(new FileInputStream(inFile));
	    OutputStream fout = new FileOutputStream(outFile);
	    copy(fout, fin, 128);
	    fin.close();
	    fout.close();
	} catch (FileNotFoundException e) {
            //System.err.println("ERROR: file '" + inFile + "' not found");
	    logger.logWarning("ERROR: file '" + inFile + "' not found", e);
	} catch (IOException e) {
            //System.err.println("ERROR: IO trouble ");
            logger.logWarning("ERROR: IO trouble ",e);
	}
    }

    public static byte[] gzipString(String in) {
	try {
	    InputStream  fin  = new ByteArrayInputStream(in.getBytes("UTF8"));
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    OutputStream fout = new GZIPOutputStream(baos, 128);
	    copy(fout, fin, 128);
	    fin.close();
	    fout.close();
	    return baos.toByteArray();
	} catch (IOException e) {
            //System.err.println("ERROR: IO trouble ");
	    logger.logWarning("ERROR: IO trouble ",e);
	    return null;
	}
    }
	
    public static String gunzipString(byte[] in) {
	try {
	    InputStream  fin  = new GZIPInputStream(new ByteArrayInputStream(in));
	    ByteArrayOutputStream fout = new ByteArrayOutputStream();
	    copy(fout, fin, 128);
	    fin.close();
	    fout.close();
	    return new String(fout.toByteArray(), "UTF-8");
	} catch (IOException e) {
            //System.err.println("ERROR: IO trouble ");
	    logger.logWarning("ERROR: IO trouble ",e);
	    return null;
	}
    }

    private static void copy(OutputStream out, InputStream in, int bufferSize) throws IOException {
	InputStream  bIn  = new BufferedInputStream(in, bufferSize);
	OutputStream bOut = new BufferedOutputStream(out, bufferSize);
	byte[] buf = new byte[bufferSize];
	int n;
	while ((n = bIn.read(buf)) > 0) bOut.write(buf, 0, n);
	bIn.close();
	bOut.close();
    }
	
    private static void help() {
	System.out.println("AnomicGzip (2004) by Michael Christen");
	System.out.println("usage: gzip [-u] <file> [<target-file>]");
    }


    public static void main(String[] s) {
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
	    gzip.gunzipFile((s[1]), target);
	    System.exit(0);
	}
	if ((s.length < 1) || (s.length > 2)) {help(); System.exit(-1);}
	String target;
	if (s.length == 1) target = s[0] + ".gz"; else target = s[1];
	gzip.gzipFile((s[0]), target);
	System.exit(0);
    }

}
