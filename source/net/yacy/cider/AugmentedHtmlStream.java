package net.yacy.cider;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

public class AugmentedHtmlStream extends FilterOutputStream {
	private Writer out;
	private ByteArrayOutputStream buffer;
	private Charset charset;

	public AugmentedHtmlStream(OutputStream out, Charset charset) {
		super(out);
		this.out = new BufferedWriter(new OutputStreamWriter(out, charset));
		this.buffer = new ByteArrayOutputStream();
		this.charset = charset;
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
		System.out.println(b);
		out.close();
	}
	
	public StringBuffer process(StringBuffer data) {
		System.out.println("got something!");
		data.append("It works!");
		return data;
	}
	
	public static boolean supportsMime(String mime) {
		System.out.println("mime" +mime);
		return mime.split(";")[0].equals("text/html");
	}

}
