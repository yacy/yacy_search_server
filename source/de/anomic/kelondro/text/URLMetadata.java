package de.anomic.kelondro.text;

import java.net.MalformedURLException;

import de.anomic.yacy.yacyURL;

public class URLMetadata {
    private yacyURL url;
    private final String dc_title, dc_creator, dc_subject, ETag;
    
    public URLMetadata(final String url, final String urlhash, final String title, final String author, final String tags, final String ETag) {
        try {
            this.url = new yacyURL(url, urlhash);
        } catch (final MalformedURLException e) {
            this.url = null;
        }
        this.dc_title = title;
        this.dc_creator = author;
        this.dc_subject = tags;
        this.ETag = ETag;
    }
    public URLMetadata(final yacyURL url, final String descr, final String author, final String tags, final String ETag) {
        this.url = url;
        this.dc_title = descr;
        this.dc_creator = author;
        this.dc_subject = tags;
        this.ETag = ETag;
    }
    public yacyURL url()    { return this.url; }
    public String  dc_title()  { return this.dc_title; }
    public String  dc_creator() { return this.dc_creator; }
    public String  dc_subject()   { return this.dc_subject; }
    public String  ETag()   { return this.ETag; }
    
}
