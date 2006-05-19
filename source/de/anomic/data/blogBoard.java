// wikiBoard.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 20.07.2004
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

// This file is contributed by Jan Sandbrink
// based on the Code of wikiBoard.java

package de.anomic.data;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMap;

public class blogBoard {
    
    public  static final int keyLength = 64;
    private static final String dateFormat = "yyyyMMddHHmmss";
    private static final int recordSize = 512;

    private static TimeZone GMTTimeZone = TimeZone.getTimeZone("PST");
    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat(dateFormat);

    private kelondroMap datbase = null;
    
    public blogBoard(File actpath, int bufferkb) {
    		new File(actpath.getParent()).mkdir();
        if (datbase == null) {
            if (actpath.exists()) try {
                datbase = new kelondroMap(new kelondroDyn(actpath, bufferkb / 2 * 0x40, '_'));
            } catch (IOException e) {
                datbase = new kelondroMap(new kelondroDyn(actpath, bufferkb / 2 * 0x400, keyLength, recordSize, '_', true));
            } else {
                datbase = new kelondroMap(new kelondroDyn(actpath, bufferkb / 2 * 0x400, keyLength, recordSize, '_', true));
            }
        }
    }
    
    public int size() {
        return datbase.size();
    }
    
    public int[] dbCacheNodeChunkSize() {
        return datbase.cacheNodeChunkSize();
    }
    
    public int[] dbCacheNodeFillStatus() {
        return datbase.cacheNodeFillStatus();
    }
    
    public String[] dbCacheObjectStatus() {
        return datbase.cacheObjectStatus();
    }
    
    public void close() {
        try {datbase.close();} catch (IOException e) {}
    }

    private static String dateString(Date date) {
	return SimpleFormatter.format(date);
    }

    private static String normalize(String key) {
        if (key == null) return "null";
        return key.trim().toLowerCase();
    }

    public static String webalize(String key) {
        if (key == null) return "null";
        key = key.trim().toLowerCase();
        int p;
        while ((p = key.indexOf(" ")) >= 0)
            key = key.substring(0, p) + "%20" + key.substring(p +1);
        return key;
    }
    
    public String guessAuthor(String ip) {
        return wikiBoard.guessAuthor(ip);
    }

    public entry newEntry(String key, String subject, String author, String ip, Date date, byte[] page) throws IOException {
	return new entry(normalize(key), subject, author, ip, date, page);
    }

    public class entry {
	
	String key;
        Map record;

    public entry(String nkey, String subject, String author, String ip, Date date, byte[] page) throws IOException {
	    record = new HashMap();
	    key = nkey;
	    if (key.length() > keyLength) key = key.substring(0, keyLength);
	    if(date == null) date = new GregorianCalendar(GMTTimeZone).getTime(); 
	    record.put("date", dateString(date));
	    if ((subject == null) || (subject.length() == 0)) subject = "";
	    record.put("subject", kelondroBase64Order.enhancedCoder.encode(subject.getBytes("UTF-8")));
	    if ((author == null) || (author.length() == 0)) author = "anonymous";
	    record.put("author", kelondroBase64Order.enhancedCoder.encode(author.getBytes("UTF-8")));
	    if ((ip == null) || (ip.length() == 0)) ip = "";
	    record.put("ip", ip);
	    if (page == null)
		record.put("page", "");
	    else
		record.put("page", kelondroBase64Order.enhancedCoder.encode(page));
	    
        wikiBoard.setAuthor(ip, author);
        //System.out.println("DEBUG: setting author " + author + " for ip = " + ip + ", authors = " + authors.toString());
	}

	private entry(String key, Map record) {
	    this.key = key;
	    this.record = record;
	}
	
	public String key() {
		return key;
	}

	public String subject() {
		String a = (String) record.get("subject");
	    if (a == null) return "";
	    byte[] b = kelondroBase64Order.enhancedCoder.decode(a);
	    if (b == null) return "";
	    return new String(b);
	}

	public Date date() {
	    try {
		String c = (String) record.get("date");
		if (c == null) {
            System.out.println("DEBUG - ERROR: date field missing in blogBoard");
            return new Date();
        }
		return SimpleFormatter.parse(c);
	    } catch (ParseException e) {
		return new Date();
	    }
	}
	
	public String author() {
	    String a = (String) record.get("author");
	    if (a == null) return "anonymous";
	    byte[] b = kelondroBase64Order.enhancedCoder.decode(a);
	    if (b == null) return "anonymous";
	    return new String(b);
	}

	public byte[] page() {
	    String m = (String) record.get("page");
	    if (m == null) return new byte[0];
	    byte[] b = kelondroBase64Order.enhancedCoder.decode(m);
	    if (b == null) return "".getBytes();
	    return b;
	}        

    }

    public String write(entry page) {
	// writes a new page and returns key
	try {
	    datbase.set(page.key, page.record);
	    return page.key;
	} catch (IOException e) {
	    return null;
	}
    }

    public entry read(String key) {
	return read(key, datbase);
    }

    private entry read(String key, kelondroMap base) {
    	try {
            key = normalize(key);
            if (key.length() > keyLength) key = key.substring(0, keyLength);
            Map record = base.get(key);
            if (record == null) return newEntry(key, "", "anonymous", "127.0.0.1", new GregorianCalendar(GMTTimeZone).getTime(), "".getBytes());
            return new entry(key, record);
    	} catch (IOException e) {
    		return null;
    	}
    }
    
    public void delete(String key) {
    	key = normalize(key);
    	try {
			datbase.remove(key);
		} catch (IOException e) { }
    }
    
    public Iterator keys(boolean up) throws IOException {
	return datbase.keys(up, false);
    }

}
