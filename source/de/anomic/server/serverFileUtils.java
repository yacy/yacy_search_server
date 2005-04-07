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

import java.io.*;

public class serverFileUtils {
    
    public static void copy(InputStream source, OutputStream dest) throws IOException {
	byte[] buffer = new byte[4096];
	int c;
	while ((c = source.read(buffer)) > 0) dest.write(buffer, 0, c);
	dest.flush();
    }
          
    public static void copy(InputStream source, File dest) throws IOException {
        FileOutputStream fos = new FileOutputStream(dest);
        copy(source, fos);
        fos.close();
    }
    
    public static void copy(File source, OutputStream dest) throws IOException {
	InputStream fis = new FileInputStream(source);
        copy(fis, dest);
	fis.close();
    }
    
    public static void copy(File source, File dest) throws IOException {
        FileInputStream fis = new FileInputStream(source);
        FileOutputStream fos = new FileOutputStream(dest);
        copy(fis, fos);
        fis.close();
        fos.close();
    }

    public static byte[] read(InputStream source) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(source, baos);
        baos.close();
        return baos.toByteArray();
    }
    
    public static byte[] read(File source) throws IOException {
        byte[] buffer = new byte[(int) source.length()];
        InputStream fis = new FileInputStream(source);
        int p = 0;
        int c;
	while ((c = fis.read(buffer, p, buffer.length - p)) > 0) p += c;
	fis.close();
        return buffer;
    }
    
    public static void write(byte[] source, OutputStream dest) throws IOException {
        copy(new ByteArrayInputStream(source), dest);
    }
    
    public static void write(byte[] source, File dest) throws IOException {
        copy(new ByteArrayInputStream(source), dest);
    }
    
}
