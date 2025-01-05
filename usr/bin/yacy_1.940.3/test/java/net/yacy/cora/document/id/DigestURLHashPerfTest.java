// DigestURLHashPerfTest.java
// -----------------------
// part of YaCy
// Copyright 2017 by luccioman; https://github.com/luccioman
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

package net.yacy.cora.document.id;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.output.NullOutputStream;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;

/**
 * Testing DigestURL hash generation performances
 */
public class DigestURLHashPerfTest {

	/**
	 * Run and measure the {@link DigestURL#hash()} method on a list of urls
	 * provided in a given file (one URL per line). When an output file path is
	 * provided, generated hashes are written to it.
	 *
	 * @param args
	 *            parameters
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		if (args.length < 1) {
			System.out.println("Usage : java DigestURLHashPerfTest <urlsFilePath> [outputFilePath]");
			return;
		}

		final File inFile = new File(args[0]);
		final List<String> urls = FileUtils.getListArray(inFile);

		System.out.println(urls.size() + " URLs loaded from " + inFile.getAbsolutePath());

		try (OutputStream outStream = args.length >= 2 ? new FileOutputStream(args[1]) : NullOutputStream.NULL_OUTPUT_STREAM;
				OutputStreamWriter writer = new OutputStreamWriter(outStream, StandardCharsets.UTF_8.name());
				BufferedWriter out = new BufferedWriter(writer);) {

			if (args.length >= 2) {
				System.out.println("Writing URL hashes to " + args[1]);
			}
			byte[] hash;
			DigestURL url;
			long beginTime = System.nanoTime(), time, minTime = Long.MAX_VALUE, maxTime = 0, meanTime = 0,
					totalTime = 0;
			int step = 0;
			for (final String urlStr : urls) {
				try {
					url = new DigestURL(urlStr);
					beginTime = System.nanoTime();
					hash = url.hash();
					time = System.nanoTime() - beginTime;
					minTime = Math.min(minTime, time);
					maxTime = Math.max(maxTime, time);
					totalTime += time;
					out.write(ASCII.String(hash));
					out.newLine();
					step++;
				} catch (final MalformedURLException e) {
					e.printStackTrace();
				}
			}
			if (step > 0) {
				meanTime = totalTime / step;
			} else {
				meanTime = totalTime;
			}

			System.out.println("Hash generation total time (ms) : " + TimeUnit.NANOSECONDS.toMillis(totalTime) + " on "
					+ step + " urls.");
			System.out.println("Render mean time (ms) : " + TimeUnit.NANOSECONDS.toMillis(meanTime));
			System.out.println("Render min time (ms) : " + TimeUnit.NANOSECONDS.toMillis(minTime));
			System.out.println("Render max time (ms) : " + TimeUnit.NANOSECONDS.toMillis(maxTime));
		} finally {
			try {
				Domains.close();
			} finally {
				ConcurrentLog.shutdown();
			}
		}

	}

}
