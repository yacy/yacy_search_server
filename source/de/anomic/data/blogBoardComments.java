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
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.anomic.kelondro.kelondroBLOBTree;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroMapDataMining;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.logging.serverLog;

public class blogBoardComments {
    
    public  static final int keyLength = 64;
    private static final String dateFormat = "yyyyMMddHHmmss";
    private static final int recordSize = 512;

    static SimpleDateFormat SimpleFormatter = new SimpleDateFormat(dateFormat);

    static {
        SimpleFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    private kelondroMapDataMining database = null;
    public blogBoardComments(File actpath) {
    		new File(actpath.getParent()).mkdir();
        if (database == null) {
            database = new kelondroMapDataMining(new kelondroBLOBTree(actpath, true, true, keyLength, recordSize, '_', kelondroNaturalOrder.naturalOrder, false, false, false), 500);
        }
    }
    public int size() {
        return database.size();
    }
    public void close() {
        database.close();
    }
    static String dateString(Date date) {
        synchronized (SimpleFormatter) {
            return SimpleFormatter.format(date);
        }
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
    public CommentEntry newEntry(String key, byte[] subject, byte[] author, String ip, Date date, byte[] page) {
        return new CommentEntry(normalize(key), subject, author, ip, date, page);
    }
    public String write(CommentEntry page) {
        // writes a new page and returns key
    	try {
    	    database.put(page.key, page.record);
    	    return page.key;
    	} catch (IOException e) {
    	    return null;
    	}
    }
    public CommentEntry read(String key) {
        //System.out.println("DEBUG: read from blogBoardComments");
        return read(key, database);
    }
    private CommentEntry read(String key, kelondroMapDataMining base) {
        key = normalize(key);
        if (key.length() > keyLength) key = key.substring(0, keyLength);
        HashMap<String, String> record = base.getMap(key);
        if (record == null) return newEntry(key, "".getBytes(), "anonymous".getBytes(), "127.0.0.1", new Date(), "".getBytes());
        return new CommentEntry(key, record);
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

		write (newEntry(key, subject, author, ip, date, page));
    	}
    	return true;
    }
    public void delete(String key) {
    	key = normalize(key);
    	try {
			database.remove(key);
		} catch (IOException e) { }
    }
    public Iterator<byte[]> keys(boolean up) throws IOException {
        return database.keys(up, false);
    }

    public class CommentEntry {
        
        String key;
        HashMap<String, String> record;
    
        public CommentEntry(String nkey, byte[] subject, byte[] author, String ip, Date date, byte[] page) {
            record = new HashMap<String, String>();
            
            setKey(nkey);
            setDate(date);
            setSubject(subject);
            setAuthor(author);
            setIp(ip);
            setPage(page);
            
            wikiBoard.setAuthor(ip, new String(author));
        }
    
        CommentEntry(String key, HashMap<String, String> record) {
            this.key = key;
            this.record = record;
            if (this.record.get("comments")==null) this.record.put("comments", listManager.collection2string(new ArrayList<String>()));
        }
        
        public String getKey() {
            return key;
        }
        private void setKey(String var) {
            key = var;
            if (key.length() > keyLength) 
                key = var.substring(0, keyLength);
        }
        private void setSubject(byte[] subject) {
            if (subject == null) 
                record.put("subject","");
            else 
                record.put("subject", kelondroBase64Order.enhancedCoder.encode(subject));
        }
        public byte[] getSubject() {
            String subject = record.get("subject");
            if (subject == null) return new byte[0];
            byte[] subject_bytes = kelondroBase64Order.enhancedCoder.decode(subject, "de.anomic.data.blogBoardComments.subject()");
            if (subject_bytes == null) return "".getBytes();
            return subject_bytes;
        }
        private void setDate(Date date) {
            if(date == null) 
                date = new Date(); 
            record.put("date", dateString(date));
        }
        public Date getDate() {
            try {
                String date = record.get("date");
                if (date == null) {
                    serverLog.logFinest("Blog", "ERROR: date field missing in blogBoard");
                    return new Date();
                }
                synchronized (SimpleFormatter) {
                    return SimpleFormatter.parse(date);
                }
            } catch (ParseException e) {
                return new Date();
            }
        }
        
        public String getTimestamp() {
            String timestamp = record.get("date");
            if (timestamp == null) {
                serverLog.logFinest("Blog", "ERROR: date field missing in blogBoard");
                return dateString(new Date());
            }
            return timestamp;
        }
        private void setAuthor(byte[] author) {
            if (author == null) 
                record.put("author","");
            else 
                record.put("author", kelondroBase64Order.enhancedCoder.encode(author));
        }
        public byte[] getAuthor() {
            String author = record.get("author");
            if (author == null) 
                return new byte[0];
            byte[] author_byte = kelondroBase64Order.enhancedCoder.decode(author, "de.anomic.data.blogBoardComments.author()");
            if (author_byte == null) 
                return "".getBytes();
            return author_byte;
        }
        private void setIp(String ip) {
            if ((ip == null) || (ip.length() == 0)) 
                ip = "";
            record.put("ip", ip);
        }
        public String getIp() {
            String ip = record.get("ip");
            if (ip == null) 
                return "127.0.0.1";
            return ip;
        }
        private void setPage(byte[] page) {
            if (page == null) 
                record.put("page", "");
            else 
                record.put("page", kelondroBase64Order.enhancedCoder.encode(page));
        }
        public byte[] getPage() {
            String page = record.get("page");
            if (page == null) 
                return new byte[0];
            byte[] page_byte = kelondroBase64Order.enhancedCoder.decode(page, "de.anomic.data.blogBoardComments.page()");
            if (page_byte == null) 
                return "".getBytes();
            return page_byte;
        }        
        /**
         * Is the comment allowed? 
         * this is possible for moderated blog entry only and means 
         * the administrator has explicit allowed the comment.
         * @return 
         */
        public boolean isAllowed() {
            return (record.get("moderated") != null) && record.get("moderated").equals("true");
        } 
        public void allow() {
            record.put("moderated", "true");
        } 
    
    }

}
