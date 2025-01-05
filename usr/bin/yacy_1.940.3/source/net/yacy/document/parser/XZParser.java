// XZParser.java
// ---------------------------
// Copyright 2018 by luccioman; https://github.com/luccioman
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

package net.yacy.document.parser;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZUtils;

import net.yacy.kelondro.util.MemoryControl;

/**
 * Parser for xz archives. Uncompresses and parses the content and adds it to
 * the created main parsed document.
 *
 * @see <a href="https://tukaani.org/xz/format.html">xz file format website</a>
 */
public class XZParser extends AbstractCompressorParser {

	public XZParser() {
		super("XZ Compressed Archive Parser");
		this.SUPPORTED_EXTENSIONS.add("xz");
		this.SUPPORTED_EXTENSIONS.add("txz");
		this.SUPPORTED_MIME_TYPES.add("application/x-xz");
	}

	@Override
	protected CompressorInputStream createDecompressStream(final InputStream source) throws IOException {
		/*
		 * Limit the size dedicated to reading compressed blocks to at most 25% of the
		 * available memory. Eventual stricter limits should be handled by the caller
		 * (see for example crawler.[protocol].maxFileSize configuration setting).
		 */
		final long availableMemory = MemoryControl.available();
		final long maxKBytes = (long) (availableMemory * 0.25 / 1024.0);
		return new XZCompressorInputStream(source, false, (int) Math.min(Integer.MAX_VALUE, maxKBytes));
	}

	@Override
	protected String getUncompressedFilename(final String filename) {
		return XZUtils.getUncompressedFileName(filename);
	}

}
