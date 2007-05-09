//icapHeader.java 
//-----------------------
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
//This file is contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.


package de.anomic.icap;

import java.text.Collator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import de.anomic.server.serverCore;

public class icapHeader extends TreeMap implements Map {

    private static final long serialVersionUID = 1L;
	
	/* =============================================================
     * Constants defining icap methods
     * ============================================================= */
    public static final String METHOD_REQMOD = "REQMOD";
    public static final String METHOD_RESPMOD = "RESPMOD";
    public static final String METHOD_OPTIONS = "OPTIONS";
    
    /* =============================================================
     * Constants defining http header names
     * ============================================================= */     
    public static final String HOST = "Host";
    public static final String USER_AGENT = "User-Agent";
    public static final String CONNECTION = "Connection";
    public static final String DATE = "Date";
    public static final String SERVER = "Server";
    public static final String ISTAG = "ISTAG";
    public static final String METHODS = "Methods";
    public static final String ALLOW = "Allow";
    public static final String ENCAPSULATED = "Encapsulated";
    public static final String MAX_CONNECTIONS = "Max-Connections";
    public static final String OPTIONS_TTL = "Options-TTL";
    public static final String SERVICE = "Service";
    public static final String SERVICE_ID = "Service-ID";
    public static final String PREVIEW = "Preview";
    public static final String TRANSFER_PREVIEW = "Transfer-Preview";
    public static final String TRANSFER_IGNORE = "Transfer-Ignore";
    public static final String TRANSFER_COMPLETE = "Transfer-Complete";
    
    public static final String X_YACY_KEEP_ALIVE_REQUEST_COUNT = "X-Keep-Alive-Request-Count";
    
    /* =============================================================
     * defining default icap status messages
     * ============================================================= */    
    public static final HashMap icap1_0 = new HashMap();
    static {
        // (1yz) Informational codes
        icap1_0.put("100","Continue after ICAP preview");
        
        // (2yz) Success codes:
        icap1_0.put("200","OK");
        icap1_0.put("204","No modifications needed");
        
        // (4yz) Client error codes:
        icap1_0.put("400","Bad request");
        icap1_0.put("404","ICAP Service not found");
        icap1_0.put("405","Method not allowed for service");
        icap1_0.put("408","Request timeout");
        
        // (5yz) Server error codes:
        icap1_0.put("500","Server error");
        icap1_0.put("501","Method not implemented");
        icap1_0.put("502","Bad Gateway");
        icap1_0.put("503","Service overloaded");
        icap1_0.put("505","ICAP version not supported by server");
    }
    
    /* PROPERTIES: General properties */    
    public static final String CONNECTION_PROP_ICAP_VER = "ICAP";
    public static final String CONNECTION_PROP_HOST = "HOST";   
    public static final String CONNECTION_PROP_PATH = "PATH";
    public static final String CONNECTION_PROP_EXT = "EXT";    
    public static final String CONNECTION_PROP_METHOD = "METHOD";
    public static final String CONNECTION_PROP_REQUESTLINE = "REQUESTLINE";
    public static final String CONNECTION_PROP_CLIENTIP = "CLIENTIP";
    public static final String CONNECTION_PROP_URL = "URL";
    public static final String CONNECTION_PROP_ARGS = "ARGS";
    public static final String CONNECTION_PROP_PERSISTENT = "PERSISTENT";
    public static final String CONNECTION_PROP_KEEP_ALIVE_COUNT = "KEEP-ALIVE_COUNT";
    
    private static final Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
        insensitiveCollator.setStrength(Collator.SECONDARY);
        insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }    
    
    public icapHeader() {
        super(insensitiveCollator);
    }
    
    public boolean allow(int statusCode) {
        if (!super.containsKey("Allow")) return false;
                
        String allow = (String)get("Allow");
        return (allow.indexOf(Integer.toString(statusCode))!=-1); 
    }    
    
    // to make the occurrence of multiple keys possible, we add them using a counter
    public Object add(Object key, Object value) {
        int c = keyCount((String) key);
        if (c == 0) return put(key, value); else return put("*" + key + "-" + c, value);
    }
    
    public int keyCount(String key) {
        if (!(containsKey(key))) return 0;
        int c = 1;
        while (containsKey("*" + key + "-" + c)) c++;
        return c;
    }
    
    // a convenience method to access the map with fail-over defaults
    public Object get(Object key, Object dflt) {
        Object result = get(key);
        if (result == null) return dflt; else return result;
    }
    
    // return multiple results
    public Object getSingle(Object key, int count) {
        if (count == 0) return get(key, null);
        return get("*" + key + "-" + count, null);
    }
    
    public StringBuffer toHeaderString(String icapVersion, int icapStatusCode, String icapStatusText) {
        
        if ((icapStatusText == null)||(icapStatusText.length()==0)) {
            if (icapVersion.equals("ICAP/1.0") && icapHeader.icap1_0.containsKey(Integer.toString(icapStatusCode))) 
                icapStatusText = (String) icapHeader.icap1_0.get(Integer.toString(icapStatusCode));
        }
        
        StringBuffer theHeader = new StringBuffer();        
        
        // write status line
        theHeader.append(icapVersion).append(" ")
                 .append(Integer.toString(icapStatusCode)).append(" ")
                 .append(icapStatusText).append("\r\n");
        
        // write header
        Iterator i = keySet().iterator();
        String key;
        char tag;
        int count;
        while (i.hasNext()) {
            key = (String) i.next();
            tag = key.charAt(0);
            if ((tag != '*') && (tag != '#')) { // '#' in key is reserved for proxy attributes as artificial header values
                count = keyCount(key);
                for (int j = 0; j < count; j++) {
                    theHeader.append(key).append(": ").append((String) getSingle(key, j)).append("\r\n");  
                }
            }            
        }
        // end header
        theHeader.append("\r\n");        
        
        
        return theHeader;
    }
    
    public static Properties parseRequestLine(String cmd, String s, Properties prop, String virtualHost) {
        
        // reset property from previous run   
        prop.clear();
        
        // storing informations about the request
        prop.setProperty(CONNECTION_PROP_METHOD, cmd);
        prop.setProperty(CONNECTION_PROP_REQUESTLINE,cmd + " " + s);
       
        
        // this parses a whole URL
        if (s.length() == 0) {
            prop.setProperty(CONNECTION_PROP_HOST, virtualHost);
            prop.setProperty(CONNECTION_PROP_PATH, "/");
            prop.setProperty(CONNECTION_PROP_ICAP_VER, "ICAP/1.0");
            prop.setProperty(CONNECTION_PROP_EXT, "");
            return prop;
        }
        
        // store the version propery "ICAP" and cut the query at both ends
        int sep = s.indexOf(" ");
        if (sep >= 0) {
            // ICAP version is given
            prop.setProperty(CONNECTION_PROP_ICAP_VER, s.substring(sep + 1).trim());
            s = s.substring(0, sep).trim(); // cut off ICAP version mark
        } else {
            // ICAP version is not given, it will be treated as ver 0.9
            prop.setProperty(CONNECTION_PROP_ICAP_VER, "ICAP/1.0");
        }
        
        
        String argsString = "";
        sep = s.indexOf("?");
        if (sep >= 0) {
            // there are values attached to the query string
            argsString = s.substring(sep + 1); // cut haed from tail of query
            s = s.substring(0, sep);
        }
        prop.setProperty(CONNECTION_PROP_URL, s); // store URL
        if (argsString.length() != 0) prop.setProperty(CONNECTION_PROP_ARGS, argsString); // store arguments in original form
        
        // finally find host string
        if (s.toUpperCase().startsWith("ICAP://")) {
            // a host was given. extract it and set path
            s = s.substring(7);
            sep = s.indexOf("/");
            if (sep < 0) {
                // this is a malformed url, something like
                // http://index.html
                // we are lazy and guess that it means
                // /index.html
                // which is a localhost access to the file servlet
                prop.setProperty(CONNECTION_PROP_HOST, virtualHost);
                prop.setProperty(CONNECTION_PROP_PATH, "/" + s);
            } else {
                // THIS IS THE "GOOD" CASE
                // a perfect formulated url
                prop.setProperty(CONNECTION_PROP_HOST, s.substring(0, sep));
                prop.setProperty(CONNECTION_PROP_PATH, s.substring(sep)); // yes, including beginning "/"
            }
        } else {
            // no host in url. set path
            if (s.startsWith("/")) {
                // thats also fine, its a perfect localhost access
                // in this case, we simulate a
                // http://localhost/s
                // access by setting a virtual host
                prop.setProperty(CONNECTION_PROP_HOST, virtualHost);
                prop.setProperty(CONNECTION_PROP_PATH, s);
            } else {
                // the client 'forgot' to set a leading '/'
                // this is the same case as above, with some lazyness
                prop.setProperty(CONNECTION_PROP_HOST, virtualHost);
                prop.setProperty(CONNECTION_PROP_PATH, "/" + s);
            }
        }
        return prop;        
        
    }
    
    public static icapHeader readHeader(Properties prop, serverCore.Session theSession) {
        // reading all headers
        icapHeader header = new icapHeader();
        int p;
        String line;
        long start = System.currentTimeMillis();
        while ((line = theSession.readLineAsString()) != null) {
            if (line.length() == 0) break; // this seperates the header of the HTTP request from the body
            // parse the header line: a property seperated with the ':' sign
            if ((p = line.indexOf(":")) >= 0) {
                // store a property
                header.add(line.substring(0, p).trim(), line.substring(p + 1).trim());
            }
            if (System.currentTimeMillis() - start > theSession.socketTimeout) break;
        }
        
        return header;        
    }
}
