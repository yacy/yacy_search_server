package net.yacy.cora.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

public class GzipDecompressingEntity extends HttpEntityWrapper {
	
	private static final int DEFAULT_BUFFER_SIZE = 1024; // this is also the maximum chunk size

	public GzipDecompressingEntity(final HttpEntity entity) {
		super(entity);
	}

	public InputStream getContent() throws IOException, IllegalStateException {

		// the wrapped entity's getContent() decides about repeatability
		InputStream wrappedin = wrappedEntity.getContent();

		return new GZIPInputStream(wrappedin);
	}
	
	public void writeTo(OutputStream outstream) throws IOException {
		if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        InputStream instream = this.getContent();
        int l;
        byte[] tmp = new byte[DEFAULT_BUFFER_SIZE];
        while ((l = instream.read(tmp)) != -1) {
            outstream.write(tmp, 0, l);
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
