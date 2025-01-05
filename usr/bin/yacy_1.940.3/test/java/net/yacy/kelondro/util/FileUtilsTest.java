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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests for the {@link FileUtils} class. 
 *
 */
public class FileUtilsTest {
	
	@BeforeClass
	public static void beforeAll() {
		/* Disable assertions here : success/failure of these tests should not rely on assertions that are likely to be disabled at runtime */
		FileUtilsTest.class.getClassLoader().setDefaultAssertionStatus(false);
	}
	
	/**
	 * A test stream reading each time less than desired bytes.
	 * Simulates what can occur for example on real-world HTTP streams.
	 */
	private class LowerReadThanExpectedInputStream extends FilterInputStream {
		
		protected LowerReadThanExpectedInputStream(InputStream in) {
			super(in);
		}

		/**
		 * Reads less than specified len bytes
		 */
		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if(len > 10 ) {
				return super.read(b, off, len - 10);
			}
			return super.read(b, off, len);
		}
		
	}
	
	

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

		final int COUNT = 1200;
		try (InputStream source = new ByteArrayInputStream(sourceBytes);
				ByteArrayOutputStream dest = new ByteArrayOutputStream();) {
			FileUtils.copy(source, dest, COUNT);
			Assert.assertEquals(COUNT, dest.size());
		}
		
		/* Copy from a stream reading less than desired bytes (can occurs for example on real-world HTTP streams) */
		try (InputStream bufferedSource = new ByteArrayInputStream(sourceBytes);
				InputStream source = new LowerReadThanExpectedInputStream(bufferedSource);
				ByteArrayOutputStream dest = new ByteArrayOutputStream();) {
			FileUtils.copy(source, dest, COUNT);
			Assert.assertEquals(COUNT, dest.size());
		}
	}
	
	/**
	 * Copy reader : normal case
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testCopyReaderWriter() throws IOException {
		StringReader source = new StringReader("A test string");
		StringWriter dest = new StringWriter();
		
		try {
			FileUtils.copy(source, dest);
		} finally {
			source.close();
			dest.close();
		}
		Assert.assertEquals("A test string", dest.toString());
	}
	
	/**
	 * Copy reader : empty input
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testCopyEmptyReaderWriter() throws IOException {
		StringReader source = new StringReader("");
		StringWriter dest = new StringWriter();
		try {
			FileUtils.copy(source, dest);
		} finally {
			source.close();
			dest.close();
		}
		Assert.assertEquals("", dest.toString());
	}
	
	/**
	 * Copy reader : writer with existing content
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testCopyReaderWriterNotEmpty() throws IOException {
		StringReader source = new StringReader("An input String");
		StringWriter dest = new StringWriter();
		
		try {
			dest.write("Non empty out stream.");
			FileUtils.copy(source, dest);
		} finally {
			source.close();
			dest.close();
		}
		Assert.assertEquals("Non empty out stream.An input String", dest.toString());
	}
		
	/**
	 * Test reading n bytes in a stream
	 * @throws IOException when a read/write error occurred
	 */
	@Test
	public void testReadInputStream() throws IOException {


		/* Fill an input stream with more bytes than FileUtils.DEFAULT_BUFFER_SIZE */
		byte[] sourceBytes = new byte[2000];
		for(int i = 0; i < sourceBytes.length; i++) {
			sourceBytes[i] = (byte)(i % Byte.MAX_VALUE);
		}
		
		/* Read all*/
		try(InputStream source = new ByteArrayInputStream(sourceBytes);) {
			Assert.assertEquals(sourceBytes.length, FileUtils.read(source, -1).length);
		}
		
		/* Read zero */
		try(InputStream source = new ByteArrayInputStream(sourceBytes);) {
			Assert.assertEquals(0, FileUtils.read(source, 0).length);
		}
		
		/* Read only one */
		try(InputStream source = new ByteArrayInputStream(sourceBytes);) {
			Assert.assertEquals(1, FileUtils.read(source, 1).length);
		}
		
		/* Read half */
		try(InputStream source = new ByteArrayInputStream(sourceBytes);) {
			Assert.assertEquals(sourceBytes.length / 2, FileUtils.read(source, sourceBytes.length / 2).length);
		}
		
		/* Read half on a stream reading each time less than desired bytes (can occurs for example on real-world HTTP streams) */
		try(InputStream bufferedStream = new ByteArrayInputStream(sourceBytes);
			InputStream source = new LowerReadThanExpectedInputStream(bufferedStream);) {
			Assert.assertEquals(sourceBytes.length / 2, FileUtils.read(source, sourceBytes.length / 2).length);
		}
		
		/* Read exactly source bytes count */
		try(InputStream source = new ByteArrayInputStream(sourceBytes);) {
			Assert.assertEquals(sourceBytes.length, FileUtils.read(source, sourceBytes.length).length);
		}
		
		/* Trying to read more than source bytes count */
		try(InputStream source = new ByteArrayInputStream(sourceBytes);) {
			Assert.assertEquals(sourceBytes.length, FileUtils.read(source, sourceBytes.length + 10).length);
		}
	}
}
