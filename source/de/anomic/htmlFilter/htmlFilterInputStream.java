// htmlFilterInputStream.java
// (C) 2005, 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 2005 on http://www.anomic.de
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

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
import de.anomic.yacy.yacyURL;

public class htmlFilterInputStream extends InputStream implements htmlFilterEventListener {
    
    private static final int MODE_PRESCAN = 0;
    private static final int MODE_PRESCAN_FINISHED = 1;
    private int mode = 1;
    
    private long preBufferSize = 2048;
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
            yacyURL rooturl,
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
                    this.detectedCharset = httpHeader.extractCharsetFromMimetypeHeader(contentType);
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
