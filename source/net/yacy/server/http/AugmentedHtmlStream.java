package net.yacy.server.http;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.interaction.AugmentHtmlStream;

public class AugmentedHtmlStream extends FilterOutputStream {
    private final Writer out;
    private final ByteArrayOutputStream buffer;
    private final Charset charset;
    private final DigestURL url;
    private final String urls;
    private final RequestHeader requestHeader;

    public AugmentedHtmlStream(OutputStream out, Charset charset, DigestURL url, RequestHeader requestHeader) {
        super(out);
        this.out = new BufferedWriter(new OutputStreamWriter(out, charset));
        this.buffer = new ByteArrayOutputStream();
        this.charset = charset;
        this.url = url;
        this.urls = this.url.toNormalform(false);
        this.requestHeader = requestHeader;
    }

	@Override
    public void write(int b) throws IOException {
		this.buffer.write(b);
	}

	@Override
    public void write(byte[] b, int off, int len) throws IOException {
		this.buffer.write(b, off, len);
	}

	@Override
    public void close() throws IOException {
	    StringBuilder b = new StringBuilder(this.buffer.toString(this.charset.name()));
		b = process(b);
		this.out.write(b.toString());
		this.out.close();
	}

    public StringBuilder process(StringBuilder data) {
        if (this.urls.contains("StringBuilder/")) {
            return data;
        }
        return AugmentHtmlStream.process(data, this.url, this.requestHeader);
    }

	public static boolean supportsMime(String mime) {
//		System.out.println("mime" +mime);
		return mime.split(";")[0].equals("text/html");
	}

}
