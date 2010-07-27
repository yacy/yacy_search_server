package net.yacy.cora.protocol;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;

public class GzipRequestInterceptor implements HttpRequestInterceptor {

    private static final String ACCEPT_ENCODING = "Accept-Encoding";
    private static final String GZIP_CODEC = "gzip";
    
    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
        if (!request.containsHeader(ACCEPT_ENCODING)) {
            request.addHeader(ACCEPT_ENCODING, GZIP_CODEC);
        }
    }

}
