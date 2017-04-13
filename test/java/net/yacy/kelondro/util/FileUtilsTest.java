// FileUtilsTest.java
// Copyright 2016 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.kelondro.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the {@link FileUtils} class. 
 *
 */
public class FileUtilsTest {

	/**
	 * Copy stream : normal case
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testCopyInputStreamOutputStream() throws IOException {
		InputStream source = new ByteArrayInputStream("A test string".getBytes(StandardCharsets.UTF_8));
		ByteArrayOutputStream dest = new ByteArrayOutputStream();
		try {
			FileUtils.copy(source, dest);
		} finally {
			source.close();
			dest.close();
		}
		String resultStr = new String(dest.toByteArray(), StandardCharsets.UTF_8);
		Assert.assertEquals("A test string", resultStr);
	}
	
	/**
	 * Copy stream : empty input
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testCopyEmptyInputStreamOutputStream() throws IOException {
		InputStream source = new ByteArrayInputStream(new byte[0]);
		ByteArrayOutputStream dest = new ByteArrayOutputStream();
		try {
			FileUtils.copy(source, dest);
		} finally {
			source.close();
			dest.close();
		}
		String resultStr = new String(dest.toByteArray(), StandardCharsets.UTF_8);
		Assert.assertEquals("", resultStr);
	}
	
	/**
	 * Copy stream : output stream with existing content
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testCopyInputStreamOutputStreamNotEmpty() throws IOException {
		InputStream source = new ByteArrayInputStream("An input String".getBytes(StandardCharsets.UTF_8));
		ByteArrayOutputStream dest = new ByteArrayOutputStream();
		try {
			dest.write("Non empty out stream.".getBytes(StandardCharsets.UTF_8));
			FileUtils.copy(source, dest);
		} finally {
			source.close();
			dest.close();
		}
		String resultStr = new String(dest.toByteArray(), StandardCharsets.UTF_8);
		Assert.assertEquals("Non empty out stream.An input String", resultStr);
	}
	
	/**
	 * Partial Stream Copy
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testPartialCopyInputStreamOutputStream() throws IOException {
		/* Fill an input stream with more bytes than FileUtils.DEFAULT_BUFFER_SIZE */
		byte[] sourceBytes = new byte[2000];
		for(int i = 0; i < sourceBytes.length; i++) {
			sourceBytes[i] = (byte)(i % Byte.MAX_VALUE);
		}
		InputStream source = new ByteArrayInputStream(sourceBytes);
		ByteArrayOutputStream dest = new ByteArrayOutputStream();
		final int COUNT = 8;
		try {
			FileUtils.copy(source, dest, COUNT);
		} finally {
			source.close();
			dest.close();
		}
		Assert.assertEquals(COUNT, dest.size());
	}

}
