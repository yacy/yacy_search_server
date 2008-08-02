package de.anomic.data.wiki;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import de.anomic.plasma.plasmaSwitchboard;

public abstract class abstractWikiParser implements wikiParser {
    
    private final plasmaSwitchboard sb;
    
    public abstractWikiParser(final plasmaSwitchboard sb) {
        this.sb = sb;
    }
    
    protected abstract String transform(BufferedReader reader, int length, String publicAddress, plasmaSwitchboard sb) throws IOException;

    public String transform(final String content) {
        return transform(content, this.sb);
    }
    
    public String transform(final String content, final plasmaSwitchboard sb) {
        try {
            return transform(
                    new BufferedReader(new StringReader(content)),
                    content.length(),
                    sb.webIndex.seedDB.mySeed().getPublicAddress(),
                    sb);
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }
    
    public String transform(final String content, final String publicAddress) {
        try {
            return transform(
                    new BufferedReader(new StringReader(content)),
                    content.length(),
                    publicAddress,
                    null);
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }
    
    public String transform(final byte[] content) throws UnsupportedEncodingException {
        return transform(content, "UTF-8", this.sb);
    }
    
    public String transform(final byte[] content, final String encoding) throws UnsupportedEncodingException {
        return transform(content, encoding, this.sb);
    }

    public String transform(final byte[] content, final String encoding, final String publicAddress) throws UnsupportedEncodingException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(content);
        try {
            return transform(
                    new BufferedReader(new InputStreamReader(bais, encoding)),
                    content.length,
                    publicAddress,
                    null);
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }
    
    public String transform(final byte[] content, final String encoding, final plasmaSwitchboard switchboard) throws UnsupportedEncodingException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(content);
        try {
            return transform(
                    new BufferedReader(new InputStreamReader(bais, encoding)),
                    content.length,
                    sb.webIndex.seedDB.mySeed().getPublicAddress(),
                    switchboard);
        } catch (final IOException e) {
            return "internal error: " + e.getMessage();
        }
    }
}
