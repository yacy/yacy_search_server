package de.anomic.data.wiki;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

public abstract class abstractWikiParser implements wikiParser {
    
    String address;
    
    public abstractWikiParser(String address) {
        this.address = address;
    }
    
    protected abstract String transform(BufferedReader reader, int length) throws IOException;

    public String transform(final String content) {
        try {
            return transform(
                    new BufferedReader(new StringReader(content)),
                    content.length());
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }
    
    public String transform(final String content, final String publicAddress) {
        try {
            return transform(
                    new BufferedReader(new StringReader(content)),
                    content.length());
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }
    
    public String transform(final byte[] content) throws UnsupportedEncodingException {
        return transform(content, "UTF-8");
    }
    
    public String transform(final byte[] content, final String encoding, final String publicAddress) throws UnsupportedEncodingException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(content);
        try {
            return transform(
                    new BufferedReader(new InputStreamReader(bais, encoding)),
                    content.length);
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }
    
    public String transform(final byte[] content, final String encoding) throws UnsupportedEncodingException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(content);
        try {
            return transform(
                    new BufferedReader(new InputStreamReader(bais, encoding)),
                    content.length);
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }
}
