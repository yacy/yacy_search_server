package de.anomic.data.wiki;

import java.io.UnsupportedEncodingException;

import de.anomic.plasma.plasmaSwitchboard;

public interface wikiParser {
    
    public String transform(String text);
    public String transform(String text, plasmaSwitchboard switchboard);
    public String transform(byte[] text) throws UnsupportedEncodingException;
    public String transform(byte[] text, String encoding) throws UnsupportedEncodingException;
    public String transform(byte[] text, String encoding, plasmaSwitchboard switchboard) throws UnsupportedEncodingException;
}
