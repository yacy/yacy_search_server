// tarTools.java
// (C) 2008 by David Wieditz; d.wieditz@gmx.de
// first published 21.05.2008 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate:  $
// $LastChangedRevision:  $
// $LastChangedBy:  $
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

package de.anomic.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import com.ice.tar.TarEntry;
import com.ice.tar.TarInputStream;

import de.anomic.server.logging.serverLog;

public class tarTools {
	
	public static InputStream getInputStream(final String tarFileName) throws Exception{
		if(tarFileName.endsWith(".gz")){
			return new GZIPInputStream(new FileInputStream(new File(tarFileName)));
		} else {
			return new FileInputStream(new File(tarFileName));
		}
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
		serverLog.logInfo("UNTAR", "starting");
		if(new File(untarDir).exists()){
			final TarInputStream tin = new TarInputStream(in);
			TarEntry tarEntry = tin.getNextEntry();
			while(tarEntry != null){
				final File destPath = new File(untarDir + File.separator + tarEntry.getName());
				if (!tarEntry.isDirectory()) {
					new File(destPath.getParent()).mkdirs(); // create missing subdirectories
					final FileOutputStream fout = new FileOutputStream(destPath);
					tin.copyEntryContents(fout);
					fout.close();
				} else {
					destPath.mkdir();
				}
				tarEntry = tin.getNextEntry();
			}
			tin.close();
		} else { // untarDir doesn't exist
			serverLog.logWarning("UNTAR", "destination " + untarDir + " doesn't exist.");
		}
		serverLog.logInfo("UNTAR", "finished");
	}
	
	public static void main(final String args[]){
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