package de.anomic.data.wiki;

import java.io.UnsupportedEncodingException;

public interface wikiParser {
    
    public String transform(String text);
    public String transform(byte[] text) throws UnsupportedEncodingException;
    public String transform(byte[] text, String encoding) throws UnsupportedEncodingException;
    
}
