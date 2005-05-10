// httpHeader.java 
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 29.04.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
   Documentation:
   this class implements a key-value mapping, as a hashtable
   The difference to ordinary hashtable implementations is that the
   keys are not compared by the equal() method, but are always
   treated as string and compared as
   key.uppercase().equal(.uppercase(comparator))
   You use this class by first creation of a static HashMap
   that then is used a the reverse mapping cache for every new
   instance of this class.
*/

package de.anomic.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import de.anomic.server.serverLog;

public final class httpHeader extends TreeMap implements Map {

    private final HashMap reverseMappingCache;

    private static Collator insensitiveCollator = Collator.getInstance(Locale.US);
    static {
	insensitiveCollator.setStrength(Collator.SECONDARY);
	insensitiveCollator.setDecomposition(Collator.NO_DECOMPOSITION);
    }

    public httpHeader() {
	this(null);
    }

    public httpHeader(HashMap reverseMappingCache) {
	// this creates a new TreeMap with a case insesitive mapping
	// to provide a put-method that translates given keys into their
	// 'proper' appearance, a translation cache is needed.
	// upon instantiation, such a mapping cache can be handed over
	// If the reverseMappingCache is null, none is used
	super(insensitiveCollator);
	this.reverseMappingCache = reverseMappingCache;
    }

    public httpHeader(HashMap reverseMappingCache, File f) throws IOException {
	// creates also a case insensitive map and loads it initially
	// with some values
	super(insensitiveCollator);
	this.reverseMappingCache = reverseMappingCache;

	// load with data
	BufferedReader br = new BufferedReader(new FileReader(f));
	String line;
	int pos;
	while ((line = br.readLine()) != null) {
	    pos = line.indexOf("=");
	    if (pos >= 0) put(line.substring(0, pos), line.substring(pos + 1));
	}
	br.close();
    }

    public httpHeader(HashMap reverseMappingCache, Map othermap)  {
	// creates a case insensitive map from another map
	super(insensitiveCollator);
	this.reverseMappingCache = reverseMappingCache;

	// load with data
	if (othermap != null) this.putAll(othermap);
    }


    // we override the put method to make use of the reverseMappingCache
    public Object put(Object key, Object value) {
	String k = (String) key;
        String upperK = k.toUpperCase();
	if (reverseMappingCache == null) {
	    return super.put(k, value);
	} else {
	    if (reverseMappingCache.containsKey(upperK)) {
		// we put in the value using the reverse mapping
		return super.put(reverseMappingCache.get(upperK), value);
	    } else {
		// we put in without a cached key and store the key afterwards
		Object r = super.put(k, value);
		reverseMappingCache.put(upperK, k);
		return r;
	    }
	}
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
    
    public Object[] getMultiple(String key) {
        int count = keyCount(key);
        Object[] result = new Object[count];
        for (int i = 0; i < count; i++) result[i] = getSingle(key, i);
        return result;
    }
    
    // convenience methods for storing and loading to a file system
    public void store(File f) throws IOException {
	FileOutputStream fos = new FileOutputStream(f);
	Iterator i = keySet().iterator();
	String key, value;
	while (i.hasNext()) {
	    key = (String) i.next();
	    value = (String) get(key);
	    fos.write((key + "=" + value + "\r\n").getBytes());
	}
	fos.flush();
	fos.close();
    }

    public String toString() {
        return super.toString();
    }
    	/*
	  Connection=close
	  Content-Encoding=gzip
	  Content-Length=7281
	  Content-Type=text/html
	  Date=Mon, 05 Jan 2004 11:55:10 GMT
	  Server=Apache/1.3.26
	*/
    
    private static TimeZone GMTTimeZone = TimeZone.getTimeZone("PST");
    private static SimpleDateFormat HTTPGMTFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
    private static SimpleDateFormat EMLFormatter     = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US);
    
    public static Date parseHTTPDate(String s) {
	if ((s == null) || (s.length() < 9)) return new Date();
	s = s.trim();
	if (s.charAt(3) == ',') s = s.substring(5).trim(); // we skip the name of the day
	if (s.charAt(9) == ' ') s = s.substring(0, 7) + "20" + s.substring(7); // short year version
	if (s.charAt(2) == ',') s = s.substring(0, 2) + s.substring(3); // ommit comma after day of week
	if ((s.charAt(0) > '9') && (s.length() > 20) && (s.charAt(2) == ' ')) s = s.substring(3);
	if (s.length() > 20) s = s.substring(0, 20).trim(); // truncate remaining, since that must be wrong
        if (s.indexOf("Mrz") > 0) s = s.replaceAll("Mrz", "March");
	try {
	    return EMLFormatter.parse(s);
	} catch (java.text.ParseException e) {
	    //System.out.println("ERROR long version parse: " + e.getMessage() +  " at position " +  e.getErrorOffset());
	    serverLog.logError("HTTPC-header", "DATE ERROR (Parse): " + s);
	    return new Date();
	} catch (java.lang.NumberFormatException e) {
	    //System.out.println("ERROR long version parse: " + e.getMessage() +  " at position " +  e.getErrorOffset());
	    serverLog.logError("HTTPC-header", "DATE ERROR (NumberFormat): " + s);
	    return new Date();
	}
    }

    private Date headerDate(String kind) {
        if (containsKey(kind)) return parseHTTPDate((String) get(kind));
        else return null;
    }

    private static boolean isTextType(String type) {
        return ((type != null)  &&
		((type.startsWith("text/html")) || (type.startsWith("text/plain")))
		);
    }
    
    public boolean isTextType() {
        return isTextType(mime());
    }
    
    public String mime() {
        return (String) get("CONTENT-TYPE", "application/octet-stream");
    }
    
    public Date date() {
        return headerDate("Date");
    }
    
    public Date expires() {
        return headerDate("Expires");
    }
    
    public Date lastModified() {
        return headerDate("Last-modified");
    }
    
    public Date ifModifiedSince() {
        return headerDate("IF-MODIFIED-SINCE");
    }
    
    public long age() {
        Date lm = lastModified();
        if (lm == null) return Long.MAX_VALUE; else return (new Date()).getTime() - lm.getTime();
    }
    
    public long contentLength() {
        if (containsKey("CONTENT-LENGTH")) {
            try {
                return Long.parseLong((String) get("CONTENT-LENGTH"));
            } catch (NumberFormatException e) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    public boolean gzip() {
        return ((containsKey("CONTENT-ENCODING")) &&
		(((String) get("CONTENT-ENCODING")).toUpperCase().startsWith("GZIP")));
    }
    /*
    public static void main(String[] args) {
	Collator c;
	c = Collator.getInstance(Locale.US); c.setStrength(Collator.PRIMARY);
	System.out.println("PRIMARY:   compare(abc, ABC) = " + c.compare("abc", "ABC"));
	c = Collator.getInstance(Locale.US); c.setStrength(Collator.SECONDARY);
	System.out.println("SECONDARY: compare(abc, ABC) = " + c.compare("abc", "ABC"));
	c = Collator.getInstance(Locale.US); c.setStrength(Collator.TERTIARY);
	System.out.println("TERTIARY:  compare(abc, ABC) = " + c.compare("abc", "ABC"));
	c = Collator.getInstance(Locale.US); c.setStrength(Collator.IDENTICAL);
	System.out.println("IDENTICAL: compare(abc, ABC) = " + c.compare("abc", "ABC"));
    }
    */
}