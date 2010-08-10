// ResponseHeader.java 
// -----------------------
// (C) 2008 by Michael Peter Christen; mc@yacy.net
// first published on http://yacy.net
// Frankfurt, Germany, 22.08.2008
//
// last major change: $LastChangedDate$ by $LastChangedBy$
// Revision: $LastChangedRevision$
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

package de.anomic.http.server;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;

import net.yacy.kelondro.logging.Log;


public class ResponseHeader extends HeaderFramework {

    // response header properties
   
    private static final long serialVersionUID = 0L;

    public ResponseHeader() {
        super();
    }

    public ResponseHeader(Header[] headers) {
        super();
        for (final Header h : headers) {
        	this.add(h.getName(), h.getValue());
        }
    }
    
    public ResponseHeader(final HashMap<String, String> reverseMappingCache) {
        super(reverseMappingCache);
    }
    
    public ResponseHeader(final HashMap<String, String> reverseMappingCache, final Map<String, String> othermap)  {
        super(reverseMappingCache, othermap);
    }

    public Date date() {
        Date d = headerDate(HeaderFramework.DATE);
        if (d == null) return new Date(); else return d;
    }
    
    public Date expires() {
        return headerDate(EXPIRES);
    }
    
    public Date lastModified() {
        Date d = headerDate(LAST_MODIFIED);
        if (d == null) return new Date(); else return d;
    }
    
    public long age() {
        final Date lm = lastModified();
        final Date sd = date();
        if (lm == null) return Long.MAX_VALUE;
        return ((sd == null) ? new Date() : sd).getTime() - lm.getTime();
    }
    
    public boolean gzip() {
        return ((containsKey(CONTENT_ENCODING)) &&
        ((get(CONTENT_ENCODING)).toUpperCase().startsWith("GZIP")));
    }

    public static Object[] parseResponseLine(final String respLine) {
        
        if ((respLine == null) || (respLine.length() == 0)) {
            return new Object[]{"HTTP/1.0",Integer.valueOf(500),"status line parse error"};
        }
        
        int p = respLine.indexOf(" ");
        if (p < 0) {
            return new Object[]{"HTTP/1.0",Integer.valueOf(500),"status line parse error"};
        }
        
        String httpVer, status, statusText;
        Integer statusCode;
        
        // the http version reported by the server
        httpVer = respLine.substring(0,p);
        
        // Status of the request, e.g. "200 OK"
        status = respLine.substring(p + 1).trim(); // the status code plus reason-phrase
        
        // splitting the status into statuscode and statustext
        p = status.indexOf(" ");
        try {
            statusCode = Integer.valueOf((p < 0) ? status.trim() : status.substring(0,p).trim());
            statusText = (p < 0) ? "" : status.substring(p+1).trim();
        } catch (final Exception e) {
            statusCode = Integer.valueOf(500);
            statusText = status;
        }
        
        return new Object[]{httpVer,statusCode,statusText};
    }
    

    /**
     * @param header
     * @return a supported Charset, so data can be encoded (may not be correct)
     */
    public Charset getCharSet() {
        String charSetName = getCharacterEncoding();
        if (charSetName == null) {
            // no character encoding is sent by the server
            charSetName = DEFAULT_CHARSET;
        }
        // maybe the charset is valid but not installed on this computer
        try {
            if(!Charset.isSupported(charSetName)) {
                Log.logWarning("httpHeader", "charset '"+ charSetName +"' is not supported on this machine, using default ("+ Charset.defaultCharset().name() +")");
                // use system default
                return Charset.defaultCharset();
            }
        } catch(IllegalCharsetNameException e) {
            Log.logSevere("httpHeader", "Charset in header is illegal: '"+ charSetName +"'\n    "+ toString() + "\n" + e.getMessage());
            // use system default
            return Charset.defaultCharset();
        } catch (UnsupportedCharsetException e) {
        	Log.logSevere("httpHeader", "Charset in header is unsupported: '"+ charSetName +"'\n    "+ toString() + "\n" + e.getMessage());
            // use system default
            return Charset.defaultCharset();
        }
        return Charset.forName(charSetName);
    } 
}
