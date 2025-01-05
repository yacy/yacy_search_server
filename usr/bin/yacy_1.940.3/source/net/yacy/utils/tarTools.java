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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import net.yacy.cora.util.ConcurrentLog;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;

/**
 * Tar archives utilities for YaCy
 */
public class tarTools {

	/**
	 * Convenience method to open a stream on a tar archive file eventually
	 * compressed with gzip.
	 * 
	 * @param tarPath
	 *            .tar or .tar.gz file path
	 * @return an opened input stream
	 * @throws FileNotFoundException
	 *             when the file does not exist, is a directory rather than a
	 *             regular file, or for some other reason cannot be opened for
	 *             reading.
	 */
	public static InputStream getInputStream(final String tarPath) throws FileNotFoundException {
		if (tarPath.endsWith(".gz")) {
			FileInputStream fileInStream = null;
			try {
				fileInStream = new FileInputStream(new File(tarPath));
				return new GZIPInputStream(fileInStream);
			} catch (FileNotFoundException e) {
				/*
				 * FileNotFoundException is is a subClass of IOException but the
				 * following behavior does not apply
				 */
				throw e;
			} catch (final IOException e) {
				if(fileInStream != null) {
					try {
						/* release the now useless firstly opened file input stream 
						 * (we can not reuse it as the header has been read by the GZIPInputStream) */
						fileInStream.close();
					} catch (IOException e1) {
						ConcurrentLog.warn("UNTAR", "Could not close input stream on file " + tarPath);
					}
				}
		        // this might happen if the stream is not in gzip format.
		        // there may be a 'gz' extension, but it may still be a raw tar file
		        // this can be caused by 'one too much gzip-content header' that was attached
		        // by a release file server, so just try to open is as normal stream
				return new FileInputStream(new File(tarPath));
			}
		}
		return new FileInputStream(new File(tarPath));
	}

	/**
	 * Convenience method to open a stream on a tar archive file eventually
	 * compressed with gzip.
	 * 
	 * @param tarFile
	 *            .tar or .tar.gz file
	 * @return an opened input stream
	 * @throws FileNotFoundException
	 *             when the file does not exist, is a directory rather than a
	 *             regular file, or for some other reason cannot be opened for
	 *             reading.
	 */
	public static InputStream getInputStream(final File tarFile) throws Exception {
		return getInputStream(tarFile.toString());
	}

	/**
	 * Untar for any tar archive, overwrites existing data. Closes the
	 * InputStream once terminated.
	 * 
	 * @param in
	 *            input stream. Must not be null. (use
	 *            {@link #getInputStream(String)} for convenience)
	 * @param untarDir
	 *            destination path. Must not be null.
	 * @throws IOException
	 *             when a read/write error occurred
	 * @throws FileNotFoundException
	 *             when the untarDir does not exists
	 * @throws NullPointerException
	 *             when a parameter is null
	 */
	public static void unTar(final InputStream in, final String untarDir) throws IOException {
		ConcurrentLog.info("UNTAR", "starting");
		if (new File(untarDir).exists()) {
			final TarArchiveInputStream tin = new TarArchiveInputStream(in);
			try {
				TarArchiveEntry tarEntry = tin.getNextEntry();
				if (tarEntry == null) {
					throw new IOException("tar archive is empty or corrupted");
				}
				while(tarEntry != null){
					final File destPath = new File(untarDir + File.separator + tarEntry.getName());
					if (!tarEntry.isDirectory()) {
						new File(destPath.getParent()).mkdirs(); // create missing subdirectories
						try (
							/* Automatically closed by this try-with-resources statement */
							final FileOutputStream fout = new FileOutputStream(destPath);
						) {
							IOUtils.copyLarge(tin, fout, 0, tarEntry.getSize());
						}
					} else {
						destPath.mkdir();
					}
					tarEntry = tin.getNextEntry();
				}
			} finally {
				try {
					tin.close();
				} catch (IOException ignored) {
					ConcurrentLog.warn("UNTAR", "InputStream could not be closed");
				}
			}
		} else { // untarDir doesn't exist
			ConcurrentLog.warn("UNTAR", "destination " + untarDir + " doesn't exist.");
			/* Still have to close the input stream */
			try {
				in.close();
			} catch (IOException ignored) {
				ConcurrentLog.warn("UNTAR", "InputStream could not be closed");
			}
			throw new FileNotFoundException("Output untar directory not found : " + untarDir);
		}
		ConcurrentLog.info("UNTAR", "finished");
	}

	/**
	 * Untar a tar archive.
	 * @param args 
	 * <ol>
	 * <li>args[0] : source file path</li>
	 * <li>args[1] : destination directory path</li>
	 * </ol>
	 */
	public static void main(final String args[]) {
		try {
			if (args.length == 2) {
				try {
					unTar(getInputStream(args[0]), args[1]);
				} catch (final Exception e) {
					System.out.println(e);
				}
			} else {
				System.out.println("usage: <source> <destination>");
			}
		} finally {
			ConcurrentLog.shutdown();
		}
	}
}
