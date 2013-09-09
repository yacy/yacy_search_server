/**
 *  Files
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.06.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-05-30 10:53:58 +0200 (Mo, 30 Mai 2011) $
 *  $LastChangedRevision: 7759 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class Files {

	/**
	 * open text files for reading. If the files are compressed, choose the
	 * appropriate decompression method automatically
	 * @param f
	 * @return the input stream for the file
	 * @throws IOException
	 */
	public static InputStream read(File f) throws IOException {

        // make input stream
        InputStream is = new BufferedInputStream(new FileInputStream(f));
        if (f.toString().endsWith(".bz2")) is = new BZip2CompressorInputStream(is);
        if (f.toString().endsWith(".gz")) is = new GZIPInputStream(is);

        return is;
	}

	/**
	 * reading a file line by line should be done with two concurrent processes
	 * - one reading the file and doing IO operations
	 * - one processing the result
	 * This method makes is easy to create concurrent file readers by providing
	 * a process that fills a blocking queue with lines from a file.
	 * After the method is called, it returns immediately a blocking queue which is
	 * filled concurrently with the lines of the file. When the reading is finished,
	 * this is signalled with a poison entry, the POISON_LINE String which can be
	 * compared with an "==" operation.
	 * @param f the file to read
	 * @param maxQueueSize
	 * @return a blocking queue which is filled with the lines, terminated by POISON_LINE
	 * @throws IOException
	 */
	public final static String POISON_LINE = "__@POISON__";
	public static BlockingQueue<String> concurentLineReader(final File f, final int maxQueueSize) throws IOException {
		final BlockingQueue<String> q = new ArrayBlockingQueue<String>(maxQueueSize);
		final InputStream is = read(f);
		final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		Thread t = new Thread() {
			@Override
            public void run() {
                Thread.currentThread().setName("Files.concurrentLineReader:" + f);
				String line;
				try {
					while ((line = br.readLine()) != null) {
						q.put(line);
					}
				} catch (final IOException e) {
				} catch (final InterruptedException e) {
				} finally {
					try {
						q.put(POISON_LINE);
						try {
							br.close();
							is.close();
						} catch (final IOException ee) {
						}
					} catch (final InterruptedException e) {
						// last try
						q.add(POISON_LINE);
						try {
							br.close();
							is.close();
						} catch (final IOException ee) {
						}
					}
				}
			}
		};
		t.start();
		return q;
	}

    /**
     * copy a file or a complete directory
     * @param from the source file or directory
     * @param to the destination file or directory
     * @throws IOException
     */
    public static void copy(final File from, final File to) throws IOException {
        if (!from.exists()) {
            throw new IOException("Can not find source: " + from.getAbsolutePath()+".");
        } else if (!from.canRead()) {
            throw new IOException("No right to source: " + from.getAbsolutePath()+".");
        }
        if (from.isDirectory())  {
            if (!to.exists() && !to.mkdirs()) {
                throw new IOException("Could not create directory: " + to.getAbsolutePath() + ".");
            }
            for (final String f : from.list()) {
                copy(new File(from, f) , new File(to, f));
            }
        } else {
            if (to.isDirectory()) throw new IOException("Cannot copy a file to an existing directory");
            if (to.exists()) to.delete();
            final byte[] buffer = new byte[4096];
            int bytesRead;
            final InputStream in =  new BufferedInputStream(new FileInputStream(from));
            final OutputStream out = new BufferedOutputStream(new FileOutputStream (to));
            while ((bytesRead = in.read(buffer)) >= 0) {
                out.write(buffer,0,bytesRead);
            }
            in.close();
            out.close();
        }
    }
}
