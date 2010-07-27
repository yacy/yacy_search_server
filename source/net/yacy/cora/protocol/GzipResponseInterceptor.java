package net.yacy.cora.protocol;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

public class GzipResponseInterceptor implements HttpResponseInterceptor {

    private static final String GZIP_CODEC = "gzip";
    
    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
		HttpEntity entity = response.getEntity();
		if (entity != null) {
			Header ceheader = entity.getContentEncoding();
			if (ceheader != null) {
				HeaderElement[] codecs = ceheader.getElements();
				for (int i = 0; i < codecs.length; i++) {
					if (codecs[i].getName().equalsIgnoreCase(GZIP_CODEC)) {
//						response.removeHeader(ceheader);
						response.setEntity(new GzipDecompressingEntity(response.getEntity()));
						return;
					}
				}
			}
		}
	}

}
