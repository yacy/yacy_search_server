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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroDyn;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMapObjects;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.serverDate;
import de.anomic.server.logging.serverLog;

public class blogBoard {
    
    public  static final int keyLength = 64;
    private static final int recordSize = 512;
    
    kelondroMapObjects database = null;
    
    public blogBoard(File actpath, long preloadTime) {
    		new File(actpath.getParent()).mkdir();
        if (database == null) {
            database = new kelondroMapObjects(new kelondroDyn(actpath, true, true, preloadTime, keyLength, recordSize, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 500);
        }
    }
    public int size() {
        return database.size();
    }
    public void close() {
        database.close();
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
    public BlogEntry newEntry(String key, byte[] subject, byte[] author, String ip, Date date, byte[] page, ArrayList<String> comments, String commentMode) {
        return new BlogEntry(normalize(key), subject, author, ip, date, page, comments, commentMode);
    }

    public class BlogEntry {
	
		String key;
	    HashMap<String, String> record;
	
	    public BlogEntry(String nkey, byte[] subject, byte[] author, String ip, Date date, byte[] page, ArrayList<String> comments, String commentMode) {
		    record = new HashMap<String, String>();
		    setKey(nkey);
		    setDate(date);
		    setSubject(subject);
		    setAuthor(author);
		    setIp(ip);
		    setPage(page);
		    setComments(comments);
		    setCommentMode(commentMode);
		    
	        // TODO: implement this function
	        record.put("privacy", "public");
	        
	        wikiBoard.setAuthor(ip, new String(author));
		}
		private BlogEntry(String key, HashMap<String, String> record) {
		    this.key = key;
		    this.record = record;
	        if (this.record.get("comments")==null) this.record.put("comments", listManager.collection2string(new ArrayList<String>()));
	        if (this.record.get("commentMode")==null || this.record.get("commentMode").equals("")) this.record.put("commentMode", "1");
		}
		private void setKey(String key) {
            if (key.length() > keyLength) 
                this.key = key.substring(0, keyLength);
            this.key = key;
		}
		public String getKey() {
			return key;
		}
		public byte[] getSubject() {
			String m = record.get("subject");
		    if (m == null) return new byte[0];
		    byte[] b = kelondroBase64Order.enhancedCoder.decode(m, "de.anomic.data.blogBoard.subject()");
		    if (b == null) return "".getBytes();
		    return b;
		}
		private void setSubject(byte[] subject) {
            if (subject == null) 
                record.put("subject","");
            else 
                record.put("subject", kelondroBase64Order.enhancedCoder.encode(subject));
		}
		public Date getDate() {
		    try {
    			String date = record.get("date");
    			if (date == null) {
    			    serverLog.logFinest("Blog", "ERROR: date field missing in blogBoard");
    	            return new Date();
    	        }
    		    return serverDate.parseShortSecond(date);
		    } catch (ParseException e) {
		        return new Date();
		    }
		}
		private void setDate(Date date) {
            if(date == null) 
                date = new Date();
            record.put("date", serverDate.formatShortSecond(date));
		}
		public String getTimestamp() {
			String timestamp = record.get("date");
			if (timestamp == null) {
			    serverLog.logFinest("Blog", "ERROR: date field missing in blogBoard");
		        return serverDate.formatShortSecond();
			}
			return timestamp;
		}
	    public byte[] getAuthor() {
	        String author = record.get("author");
	        if (author == null) return new byte[0];
	        byte[] b = kelondroBase64Order.enhancedCoder.decode(author, "de.anomic.data.blogBoard.author()");
	        if (b == null) return "".getBytes();
	        return b;
	    }
	    private void setAuthor(byte[] author) {
            if (author == null) 
                record.put("author","");
            else 
                record.put("author", kelondroBase64Order.enhancedCoder.encode(author));
	    }
	    public int getCommentsSize() {
            // This ist a Bugfix for Version older than 4443.
            if(record.get("comments").startsWith(",")) {
                    record.put("comments", record.get("comments").substring(1));
                    writeBlogEntry(this);
            }
	        ArrayList<String> commentsize = listManager.string2arraylist(record.get("comments"));
	        return commentsize.size();
	    }
	    public ArrayList<String> getComments() {
	        ArrayList<String> comments = listManager.string2arraylist(record.get("comments"));
	        return comments;
	    }
	    private void setComments(ArrayList<String> comments) {
            if (comments == null) 
                record.put("comments", listManager.collection2string(new ArrayList<String>()));
            else 
                record.put("comments", listManager.collection2string(comments));
	    }
	    public String getIp() {
		    String ip = record.get("ip");
		    if (ip == null) return "127.0.0.1";
		    return ip;
		}
		private void setIp(String ip) {
            if ((ip == null) || (ip.length() == 0)) 
                ip = "";
            record.put("ip", ip);
		}
		public byte[] getPage() {
		    String page = record.get("page");
		    if (page == null) return new byte[0];
		    byte[] page_as_byte = kelondroBase64Order.enhancedCoder.decode(page, "de.anomic.data.blogBoard.page()");
		    if (page_as_byte == null) return "".getBytes();
		    return page_as_byte;
		}        
		private void setPage(byte[] page) {
            if (page == null) 
                record.put("page", "");
            else 
                record.put("page", kelondroBase64Order.enhancedCoder.encode(page));
		}
		public void addComment(String commentID) {
	        ArrayList<String> comments = listManager.string2arraylist(record.get("comments"));
	        comments.add(commentID);
	        record.put("comments", listManager.collection2string(comments));
	    }
	    public boolean removeComment(String commentID) {
	        ArrayList<String> comments = listManager.string2arraylist(record.get("comments"));
	        boolean success = comments.remove(commentID);
	        record.put("comments", listManager.collection2string(comments));
	        return success;
	    }
	    public int getCommentMode(){
	        return Integer.parseInt(record.get("commentMode"));
	    }
	    private void setCommentMode(String mode) {
            if (mode == null) 
                record.put("commentMode", "1");
            else 
                record.put("commentMode", mode);
	    }
	    public boolean isPublic() {
	        String privacy = record.get("privacy");
	        if (privacy == null)
	            return true;
	        if(privacy.equalsIgnoreCase("public"))
	            return true;
	        return false;
	    }
    }
    /*
     * writes a new page and return the key
     */
    public String writeBlogEntry(BlogEntry page) {
        try {
            database.set(page.key, page.record);
            return page.key;
        } catch (IOException e) {
            return null;
        }
    }
    public BlogEntry readBlogEntry(String key) {
        return readBlogEntry(key, database);
    }
    private BlogEntry readBlogEntry(String key, kelondroMapObjects base) {
    	key = normalize(key);
        if (key.length() > keyLength) 
            key = key.substring(0, keyLength);
        HashMap<String, String> record = base.getMap(key);
        if (record == null) 
            return newEntry(key, "".getBytes(), "anonymous".getBytes(), "127.0.0.1", new Date(), "".getBytes(), null, null);
        return new BlogEntry(key, record);
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
				date = serverDate.parseShortSecond(StrDate);
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

		writeBlogEntry (newEntry(key, subject, author, ip, date, page, null, null));
    	}
    	return true;
    }
    public void deleteBlogEntry(String key) {
    	key = normalize(key);
    	try {
			database.remove(key);
		} catch (IOException e) { }
    }
    public Iterator<String> keys(boolean up) throws IOException {
        return database.keys(up, false);
    }
    /**
     * Comparator to sort objects of type Blog according to their timestamps
     */
    public class BlogComparator implements Comparator<String> {
        
        private boolean newestFirst;
        
        /**
         * @param newestFirst newest first, or oldest first?
         */
        public BlogComparator(boolean newestFirst){
            this.newestFirst = newestFirst;
        }
        
        public int compare(String obj1, String obj2) {
            BlogEntry blogEntry1=readBlogEntry(obj1);
            BlogEntry blogEntry2=readBlogEntry(obj2);
            if(blogEntry1 == null || blogEntry2 == null)
                return 0;
            if(this.newestFirst){
                if(Long.valueOf(blogEntry2.getTimestamp()) - Long.valueOf(blogEntry1.getTimestamp()) >0) 
                    return 1;
                return -1;
            }
            if(Long.valueOf(blogEntry1.getTimestamp()) - Long.valueOf(blogEntry2.getTimestamp()) >0) 
                return 1;
            return -1;
        }
    }
    public Iterator<String> getBlogIterator(boolean priv){
        TreeSet<String> set = new TreeSet<String>(new BlogComparator(true));
        Iterator<BlogEntry> iterator = blogIterator(priv);
        BlogEntry blogEntry;
        while(iterator.hasNext()){
            blogEntry=(BlogEntry)iterator.next();
            if(priv || blogEntry.isPublic()){
                set.add(blogEntry.getKey());
            }
        }
        return set.iterator();
    }
    public Iterator<BlogEntry> blogIterator(boolean up){
        try {
            return new BlogIterator(up);
        } catch (IOException e) {
            return new HashSet<BlogEntry>().iterator();
        }
    }
    /**
     * Subclass of blogBoard, which provides the blogIterator object-type
     */
    public class BlogIterator implements Iterator<BlogEntry> {
        Iterator<String> blogIter;
        blogBoard.BlogEntry nextEntry;
        public BlogIterator(boolean up) throws IOException {
            //flushBookmarkCache(); //XXX: this will cost performance
            this.blogIter = blogBoard.this.database.keys(up, false);
            this.nextEntry = null;
        }
        
        public boolean hasNext() {
            try {
                return this.blogIter.hasNext();
            } catch (kelondroException e) {
                //resetDatabase();
                return false;
            }
        }
        
        public BlogEntry next() {
            try {
                return readBlogEntry((String) this.blogIter.next());
            } catch (kelondroException e) {
                //resetDatabase();
                return null;
            }
        }
        
        public void remove() {
            if (this.nextEntry != null) {
                try {
                    Object blogKey = this.nextEntry.getKey();
                    if (blogKey != null) deleteBlogEntry((String) blogKey);
                } catch (kelondroException e) {
                    //resetDatabase();
                }
            }
        }
    }
 
}
