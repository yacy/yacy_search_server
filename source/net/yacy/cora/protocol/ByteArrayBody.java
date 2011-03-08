/**
 *  ByteArrayBody
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


package net.yacy.cora.protocol;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.content.AbstractContentBody;

public class ByteArrayBody extends AbstractContentBody {
	
	private final String filename;
	private final byte[] bytes;

	/**
	 * 
	 * @param bytes of 'file'
	 * @param filename
	 */
	public ByteArrayBody(final byte[] bytes, final String filename) {
		super("application/octet-stream");
		this.bytes = bytes;
		this.filename = filename;
	}

	/**
     * @deprecated use {@link #writeTo(OutputStream)}
     */
    @Deprecated
    public void writeTo(final OutputStream out, int mode) throws IOException {
        writeTo(out);
    }

    @Override
	public void writeTo(OutputStream outputStream) throws IOException {
    	outputStream.write(bytes);
    	outputStream.flush();
	}

	public String getFilename() {
		return this.filename;
	}

	public String getCharset() {
		return null;
	}

	public long getContentLength() {
		return bytes.length;
	}

	public String getTransferEncoding() {
		return MIME.ENC_BINARY;
	}

}
