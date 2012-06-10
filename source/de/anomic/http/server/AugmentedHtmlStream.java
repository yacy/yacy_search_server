package de.anomic.http.server;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.interaction.AugmentHtmlStream;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.search.Switchboard;

public class AugmentedHtmlStream extends FilterOutputStream {
	private Writer out;
	private ByteArrayOutputStream buffer;
	private Charset charset;
	private DigestURI url;
	private byte[] urlhash;
	private RequestHeader requestHeader;

	public AugmentedHtmlStream(OutputStream out, Charset charset, DigestURI url, byte[] urlhash, RequestHeader requestHeader) {
		super(out);
		this.out = new BufferedWriter(new OutputStreamWriter(out, charset));
		this.buffer = new ByteArrayOutputStream();
		this.charset = charset;
		this.url = url;
		this.urlhash = urlhash;
		this.requestHeader = requestHeader;
	}
	
	public void write(int b) throws IOException {
		this.buffer.write(b);
	}
	
	public void write(byte[] b, int off, int len) throws IOException {
		this.buffer.write(b, off, len);
	}
	
	public void close() throws IOException {
		StringBuffer b = new StringBuffer(this.buffer.toString(charset.name()));
		b = process(b);
		out.write(b.toString());
		out.close();
	}
	
	public StringBuffer process(StringBuffer data) {
		
		if (Switchboard.getSwitchboard().getConfigBool("proxyAugmentation", false) == true) {
			
			if (!this.url.toNormalform(false, true).contains("currentyacypeer/")) {
								
				return AugmentHtmlStream.process (data, charset, url, requestHeader);
				
			} else {
				return data;
			}
			
		} else {		
			return data;
		}
	}
	
	public static boolean supportsMime(String mime) {
//		System.out.println("mime" +mime);
		return mime.split(";")[0].equals("text/html");
	}

}
