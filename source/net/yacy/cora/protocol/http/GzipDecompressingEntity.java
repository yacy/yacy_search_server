/**
 *  GzipDecompressingEntity
 *  Copyright 2010 by Sebastian Gaebel
 *  First released 01.07.2010 at http://yacy.net
 *  
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
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


package net.yacy.cora.protocol.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

public class GzipDecompressingEntity extends HttpEntityWrapper {
	
	private static final int DEFAULT_BUFFER_SIZE = 1024; // this is also the maximum chunk size
	private GZIPInputStream gzipInputStream = null;

	public GzipDecompressingEntity(final HttpEntity entity) {
		super(entity);
	}

	public InputStream getContent() throws IOException, IllegalStateException {
		if (gzipInputStream == null) {
			gzipInputStream = new GZIPInputStream(wrappedEntity.getContent());
		}
		return gzipInputStream;
	}
	
	public void writeTo(OutputStream outstream) throws IOException {
		if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        InputStream instream = this.getContent();
        try {
			int l;
			byte[] tmp = new byte[DEFAULT_BUFFER_SIZE];
			while ((l = instream.read(tmp)) != -1) {
			    outstream.write(tmp, 0, l);
			}
		} finally {
			instream.close();
		}
	}
	
	public boolean isChunked() {
		return true;
	}

	public long getContentLength() {
		// length of ungzipped content not known in advance
		return -1;
	}

}
