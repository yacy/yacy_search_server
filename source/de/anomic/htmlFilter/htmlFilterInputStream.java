package de.anomic.htmlFilter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Properties;

import de.anomic.http.httpHeader;
import de.anomic.net.URL;


public class htmlFilterInputStream extends InputStream implements htmlFilterEventListener {
    
    private static final int MODE_PRESCAN = 0;
    private static final int MODE_PRESCAN_FINISHED = 1;
    private int mode = 1;
    
    private long preBufferSize = 143336;
    private long preRead = 0;
    private BufferedInputStream bufferedIn;

    private String detectedCharset;
    private boolean charsetChanged = false;
    private boolean endOfHead = false;
    
    private Reader reader;
    private Writer writer;
    
    public htmlFilterInputStream(
            InputStream inStream,
            String inputStreamCharset,
            URL rooturl,
            htmlFilterTransformer transformer,
            boolean passbyIfBinarySuspect
    ) throws UnsupportedEncodingException {
        // create a input stream for buffereing
        this.bufferedIn = new BufferedInputStream(inStream,(int)this.preBufferSize);
        this.bufferedIn.mark((int)this.preBufferSize);
        
        htmlFilterContentScraper scraper = new htmlFilterContentScraper(rooturl);
        scraper.registerHtmlFilterEventListener(this);
        
        this.reader = new InputStreamReader(this,inputStreamCharset); 
        this.writer = new htmlFilterWriter(null,null,scraper,transformer,passbyIfBinarySuspect);
    }

    public void scrapeTag0(String tagname, Properties tagopts) {
        if (tagname == null || tagname.length() == 0) return;
        
        if (tagname.equalsIgnoreCase("meta")) {
            if (tagopts.containsKey("http-equiv")) {
                String value = tagopts.getProperty("http-equiv");
                if (value.equalsIgnoreCase("Content-Type")) {
                    String contentType = tagopts.getProperty("content","");
                    this.detectedCharset = httpHeader.extractCharsetFromMimetyeHeader(contentType);
                    if (this.detectedCharset != null && this.detectedCharset.length() > 0) {
                        this.charsetChanged = true;
                    } else if (tagopts.containsKey("charset")) { 
                        // sometimes the charset property is configured as extra attribut. try it ...
                        this.detectedCharset = tagopts.getProperty("charset");
                        this.charsetChanged = true;
                    }
                }
            }
        }
    }

    public void scrapeTag1(String tagname, Properties tagopts, char[] text) {
        if (tagname == null || tagname.length() == 0) return;
        
        if (tagname.equalsIgnoreCase("head")) {
            this.endOfHead = true;
        }
    }
    
    public String detectCharset() throws IOException {
        this.mode = MODE_PRESCAN; 
        
        // loop until we have detected the header element or the charset data
        int c;
        while ((c = this.reader.read())!= -1) {
            this.writer.write(c);
        }
        
        // free writer
        this.writer = null;        
        // don't close writer here, otherwise it will shutdown our source stream 

        // reset the buffer if not already done
        if (this.mode != MODE_PRESCAN_FINISHED) {
            this.mode++;
            this.bufferedIn.reset();
        }
        
        // return scanning result
        return (this.charsetChanged) ? this.detectedCharset : null;
    }

    public int read() throws IOException {
        // mode 0 is called from within the detectCharset function
        if (this.mode == MODE_PRESCAN) {      
            if (this.endOfHead || this.charsetChanged || this.preRead >= this.preBufferSize-1) {
                return -1;            
            }
            this.preRead++;            
        }        
        return this.bufferedIn.read();
    }

    
}
