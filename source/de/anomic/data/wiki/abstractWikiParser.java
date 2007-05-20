package de.anomic.data.wiki;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import de.anomic.plasma.plasmaSwitchboard;

public abstract class abstractWikiParser implements wikiParser {
    
    protected plasmaSwitchboard sb;
    
    public abstractWikiParser(plasmaSwitchboard sb) {
        this.sb = sb;
    }
    
    protected abstract String transform(BufferedReader reader, int length, plasmaSwitchboard sb) throws IOException;

    public String transform(String content) {
        return transform(content, this.sb);
    }
    
    public String transform(String content, plasmaSwitchboard sb) {
        try {
            return transform(new BufferedReader(new StringReader(content)), content.length(), sb);
        } catch (IOException e) {
            return "internal error: " + e.getMessage();
        }
    }

    public String transform(byte[] content) throws UnsupportedEncodingException {
        return transform(content, "UTF-8", this.sb);
    }
    
    public String transform(byte[] content, String encoding) throws UnsupportedEncodingException {
        return transform(content, encoding, this.sb);
    }

    public String transform(byte[] content, String encoding, plasmaSwitchboard switchboard) throws UnsupportedEncodingException {
        ByteArrayInputStream bais = new ByteArrayInputStream(content);
        try {
            return transform(new BufferedReader(new InputStreamReader(bais, encoding)), content.length, switchboard);
        } catch (IOException e) {
            return "internal error: " + e.getMessage();
        }
    }
}
