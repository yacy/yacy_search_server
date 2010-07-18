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
