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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroMapObjects;

public class blogBoard {
    
    public  static final int keyLength = 64;
    private static final String dateFormat = "yyyyMMddHHmmss";
    private static final int recordSize = 512;

    private static TimeZone GMTTimeZone = TimeZone.getTimeZone("PST");
    private static SimpleDateFormat SimpleFormatter = new SimpleDateFormat(dateFormat);

    private kelondroMapObjects datbase = null;
    
    public blogBoard(File actpath, long preloadTime) {
    		new File(actpath.getParent()).mkdir();
        if (datbase == null) {
            datbase = new kelondroMapObjects(new kelondroDyn(actpath, true, true, preloadTime, keyLength, recordSize, '_', true, false, false), 500);
        }
    }
    
    public int size() {
        return datbase.size();
    }
    
    public void close() {
        datbase.close();
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

    public entry newEntry(String key, byte[] subject, byte[] author, String ip, Date date, byte[] page, ArrayList comments, String commentMode) {
	return new entry(normalize(key), subject, author, ip, date, page, comments, commentMode);
    }

    public class entry {
	
	String key;
        Map record;

    public entry(String nkey, byte[] subject, byte[] author, String ip, Date date, byte[] page, ArrayList comments, String commentMode) {
	    record = new HashMap();
	    key = nkey;
	    if (key.length() > keyLength) key = key.substring(0, keyLength);
	    if(date == null) date = new GregorianCalendar(GMTTimeZone).getTime(); 
	    record.put("date", dateString(date));
	    if (subject == null) record.put("subject","");
	    else record.put("subject", kelondroBase64Order.enhancedCoder.encode(subject));
	    if (author == null) record.put("author","");
	    else record.put("author", kelondroBase64Order.enhancedCoder.encode(author));
	    if ((ip == null) || (ip.length() == 0)) ip = "";
	    record.put("ip", ip);
        if (page == null) record.put("page", "");
        else record.put("page", kelondroBase64Order.enhancedCoder.encode(page));
        if (comments == null) record.put("comments", listManager.arraylist2string(new ArrayList()));
        else record.put("comments", listManager.arraylist2string(comments));
        if (commentMode == null) record.put("commentMode", "1");
        else record.put("commentMode", commentMode);
	    
        wikiBoard.setAuthor(ip, new String(author));
        //System.out.println("DEBUG: setting author " + author + " for ip = " + ip + ", authors = " + authors.toString());
	}

	private entry(String key, Map record) {
	    this.key = key;
	    this.record = record;
        if (this.record.get("comments")==null) this.record.put("comments", listManager.arraylist2string(new ArrayList()));
        if (this.record.get("commentMode")==null || this.record.get("commentMode").equals("")) this.record.put("commentMode", "1");
	}
	
	public String key() {
		return key;
	}

	public byte[] subject() {
		String m = (String) record.get("subject");
	    if (m == null) return new byte[0];
	    byte[] b = kelondroBase64Order.enhancedCoder.decode(m);
	    if (b == null) return "".getBytes();
	    return b;
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
	
	public String timestamp() {
		String c = (String) record.get("date");
		if (c == null) {
	        System.out.println("DEBUG - ERROR: date field missing in blogBoard");
	        return dateString(new Date());
		}
		return c;
	}
	
    public byte[] author() {
        String m = (String) record.get("author");
        if (m == null) return new byte[0];
        byte[] b = kelondroBase64Order.enhancedCoder.decode(m);
        if (b == null) return "".getBytes();
        return b;
    }
    
    public byte[] commentsSize() {
        ArrayList m = listManager.string2arraylist((String) record.get("comments"));
        if (m == null) return new byte[0];
        byte[] b = Integer.toString(m.size()).getBytes();
        if (b == null) return "".getBytes();
        return b;
    }

    public ArrayList comments() {
        ArrayList m = listManager.string2arraylist((String) record.get("comments"));
        if (m == null) return new ArrayList();
        return m;
    }

	public String ip() {
	    String a = (String) record.get("ip");
	    if (a == null) return "127.0.0.1";
	    return a;
	}

	public byte[] page() {
	    String m = (String) record.get("page");
	    if (m == null) return new byte[0];
	    byte[] b = kelondroBase64Order.enhancedCoder.decode(m);
	    if (b == null) return "".getBytes();
	    return b;
	}        

    public void addComment(String commentID) {
        ArrayList comments = listManager.string2arraylist((String) record.get("comments"));
        comments.add(commentID);
        record.put("comments", listManager.arraylist2string(comments));
    }
    
    public boolean removeComment(String commentID) {
        ArrayList comments = listManager.string2arraylist((String) record.get("comments"));
        boolean success = comments.remove(commentID);
        record.put("comments", listManager.arraylist2string(comments));
        return success;
    }
    
    public int getCommentMode(){
        return Integer.parseInt((String) record.get("commentMode"));
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

    private entry read(String key, kelondroMapObjects base) {
    	key = normalize(key);
        if (key.length() > keyLength) key = key.substring(0, keyLength);
        Map record = base.getMap(key);
        if (record == null) return newEntry(key, "".getBytes(), "anonymous".getBytes(), "127.0.0.1", new GregorianCalendar(GMTTimeZone).getTime(), "".getBytes(), null, null);
        return new entry(key, record);
    }
    
    public boolean importXML(String input) {
    	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new ByteArrayInputStream(input.getBytes()));
			return parseXMLimport(doc);
		} catch (ParserConfigurationException e) {
		} catch (SAXException e) {
		} catch (IOException e) {}
		
    	return false;
    }
    
    private boolean parseXMLimport(Document doc) {
    	if(!doc.getDocumentElement().getTagName().equals("blog"))
    		return false;
    	
    	NodeList items = doc.getDocumentElement().getElementsByTagName("item");
    	if(items.getLength() == 0)
    		return false; 
    	
    	for(int i=0;i<items.getLength();++i) {
    		String key = null, ip = null, StrSubject = null, StrAuthor = null, StrPage = null, StrDate = null;
    		Date date = null;
    		
    		if(!items.item(i).getNodeName().equals("item"))
    			continue;
    		
    		NodeList currentNodeChildren = items.item(i).getChildNodes();
    		
    		for(int j=0;j<currentNodeChildren.getLength();++j) {
    			Node currentNode = currentNodeChildren.item(j);
    			if(currentNode.getNodeName().equals("id"))
    				key = currentNode.getFirstChild().getNodeValue();
    			else if(currentNode.getNodeName().equals("ip"))
    				ip = currentNode.getFirstChild().getNodeValue();
    			else if(currentNode.getNodeName().equals("timestamp"))
    				StrDate = currentNode.getFirstChild().getNodeValue();
    			else if(currentNode.getNodeName().equals("subject"))
    				StrSubject = currentNode.getFirstChild().getNodeValue();
    			else if(currentNode.getNodeName().equals("author"))
    				StrAuthor = currentNode.getFirstChild().getNodeValue();
    			else if(currentNode.getNodeName().equals("content"))
    				StrPage = currentNode.getFirstChild().getNodeValue();
    		}
    		
    		try {
				date = SimpleFormatter.parse(StrDate);
			} catch (ParseException e1) {
				date = new Date();
			}
    		
    		if(key == null || ip == null || StrSubject == null || StrAuthor == null || StrPage == null || date == null)
    			return false;
    		
    		byte[] subject,author,page;
    		try {
				subject = StrSubject.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e1) {
				subject = StrSubject.getBytes();
			}
			try {
				author = StrAuthor.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e1) {
				author = StrAuthor.getBytes();
			}
			try {
				page = StrPage.getBytes("UTF-8");
			} catch (UnsupportedEncodingException e1) {
				page = StrPage.getBytes();
			}

		write (newEntry(key, subject, author, ip, date, page, null, null));
    	}
    	return true;
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
