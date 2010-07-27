package net.yacy.cora.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

public class GzipDecompressingEntity extends HttpEntityWrapper {

	public GzipDecompressingEntity(final HttpEntity entity) {
		super(entity);
	}

	public InputStream getContent() throws IOException, IllegalStateException {

		// the wrapped entity's getContent() decides about repeatability
		InputStream wrappedin = wrappedEntity.getContent();

		return new GZIPInputStream(wrappedin);
	}

	public long getContentLength() {
		// length of ungzipped content not known in advance
		return -1;
	}

}
