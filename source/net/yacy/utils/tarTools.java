// tarTools.java
// (C) 2008 by David Wieditz; d.wieditz@gmx.de
// first published 21.05.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.util.ConcurrentLog;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;


public class tarTools {
	
	public static InputStream getInputStream(final String tarFileName) throws Exception{
		if (tarFileName.endsWith(".gz")) {
		    try {
		        return new GZIPInputStream(new FileInputStream(new File(tarFileName)));
		    } catch (final IOException e) {
		        // this might happen if the stream is not in gzip format.
		        // there may be a 'gz' extension, but it may still be a raw tar file
		        // this can be caused by 'one too much gzip-content header' that was attached
		        // by a release file server, so just try to open is as normal stream
		        return new FileInputStream(new File(tarFileName));
		    }
		}
		return new FileInputStream(new File(tarFileName));
	}

	public static InputStream getInputStream(final File tarFileName) throws Exception{
		return getInputStream(tarFileName.toString());
	}
	
	/**
	 * untar for any archive, overwrites existing data
	 * @param in use getInputStream() for convenience
	 * @param untarDir destination path
	 * @throws Exception (IOException or FileNotFoundException)
	 */
	public static void unTar(final InputStream in, final String untarDir) throws Exception{
		ConcurrentLog.info("UNTAR", "starting");
		if(new File(untarDir).exists()){
			final TarArchiveInputStream tin = new TarArchiveInputStream(in);
			TarArchiveEntry tarEntry = tin.getNextTarEntry();
			while(tarEntry != null){
				final File destPath = new File(untarDir + File.separator + tarEntry.getName());
				if (!tarEntry.isDirectory()) {
					new File(destPath.getParent()).mkdirs(); // create missing subdirectories
					final FileOutputStream fout = new FileOutputStream(destPath);
                                        IOUtils.copyLarge(tin,fout,0,tarEntry.getSize());
					fout.close();
				} else {
					destPath.mkdir();
				}
				tarEntry = tin.getNextTarEntry();
			}
			tin.close();
		} else { // untarDir doesn't exist
			ConcurrentLog.warn("UNTAR", "destination " + untarDir + " doesn't exist.");
		}
		ConcurrentLog.info("UNTAR", "finished");
	}
	
	public static void main(final String args[]) {
		// @arg0 source
		// @arg1 destination
		if(args.length == 2){
		try {
			unTar(getInputStream(args[0]), args[1]);
		} catch (final Exception e) {
			System.out.println(e);
		}
		} else {
			System.out.println("usage: <source> <destination>");
		}
	}
}
