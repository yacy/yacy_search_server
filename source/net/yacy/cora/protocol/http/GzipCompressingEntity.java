/**
 *  GzipCompressingEntity
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
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;

public class GzipCompressingEntity extends HttpEntityWrapper {

	private static final String GZIP_CODEC = "gzip";
//	private static final int DEFAULT_BUFFER_SIZE = 1024; // this is also the maximum chunk size

	public GzipCompressingEntity(final HttpEntity entity) {
		super(entity);
	}

	@Override
    public Header getContentEncoding() {
		return new BasicHeader(HTTP.CONTENT_ENCODING, GZIP_CODEC);
	}

	@Override
    public long getContentLength() {
		return -1;
	}

	@Override
    public boolean isChunked() {
		// force content chunking
		return true;
	}

	@Override
    public void writeTo(final OutputStream outstream) throws IOException {
		if (outstream == null) {
			throw new IllegalArgumentException("Output stream may not be null");
		}
		GZIPOutputStream gzip = new GZIPOutputStream(outstream);
		wrappedEntity.writeTo(gzip);
		gzip.finish();
	}

}
