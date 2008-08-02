// wikiBoard.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
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

import de.anomic.kelondro.kelondroBLOBTree;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroException;
import de.anomic.kelondro.kelondroMap;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.server.serverDate;
import de.anomic.server.logging.serverLog;

public class blogBoard {
    
    public  static final int keyLength = 64;
    private static final int recordSize = 512;
    
    kelondroMap database = null;
    
    public blogBoard(final File actpath) {
    		new File(actpath.getParent()).mkdir();
        if (database == null) {
            database = new kelondroMap(new kelondroBLOBTree(actpath, true, true, keyLength, recordSize, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 500);
        }
    }
    public int size() {
        return database.size();
    }
    public void close() {
        database.close();
    }
    private static String normalize(final String key) {
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
    public String guessAuthor(final String ip) {
        return wikiBoard.guessAuthor(ip);
    }
    /**
     * Create a new BlogEntry an return it
     * @param key
     * @param subject
     * @param author
     * @param ip
     * @param date
     * @param page the content of the Blogentry
     * @param comments
     * @param commentMode possible params are: 0 - no comments allowed, 1 - comments allowed, 2 - comments moderated
     * @return BlogEntry
     */
    public BlogEntry newEntry(final String key, final byte[] subject, final byte[] author, final String ip, final Date date, final byte[] page, final ArrayList<String> comments, final String commentMode) {
        return new BlogEntry(normalize(key), subject, author, ip, date, page, comments, commentMode);
    }

    /*
     * writes a new page and return the key
     */
    public String writeBlogEntry(final BlogEntry page) {
        try {
            database.put(page.key, page.record);
            return page.key;
        } catch (final IOException e) {
            return null;
        }
    }
    public BlogEntry readBlogEntry(final String key) {
        return readBlogEntry(key, database);
    }
    private BlogEntry readBlogEntry(String key, final kelondroMap base) {
    	key = normalize(key);
        if (key.length() > keyLength) key = key.substring(0, keyLength);
        HashMap<String, String> record;
        try {
            record = base.get(key);
        } catch (final IOException e) {
            record = null;
        }
        if (record == null) 
            return newEntry(key, "".getBytes(), "anonymous".getBytes(), "127.0.0.1", new Date(), "".getBytes(), null, null);
        return new BlogEntry(key, record);
    }
    public boolean importXML(final String input) {
    	final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	try {
			final DocumentBuilder builder = factory.newDocumentBuilder();
			final Document doc = builder.parse(new ByteArrayInputStream(input.getBytes()));
			return parseXMLimport(doc);
		} catch (final ParserConfigurationException e) {
		} catch (final SAXException e) {
		} catch (final IOException e) {}
		
    	return false;
    }
    private boolean parseXMLimport(final Document doc) {
    	if(!doc.getDocumentElement().getTagName().equals("blog"))
    		return false;
    	
    	final NodeList items = doc.getDocumentElement().getElementsByTagName("item");
    	if(items.getLength() == 0)
    		return false; 
    	
    	for(int i=0;i<items.getLength();++i) {
    		String key = null, ip = null, StrSubject = null, StrAuthor = null, StrPage = null, StrDate = null;
    		Date date = null;
    		
    		if(!items.item(i).getNodeName().equals("item"))
    			continue;
    		
    		final NodeList currentNodeChildren = items.item(i).getChildNodes();
    		
    		for(int j=0;j<currentNodeChildren.getLength();++j) {
    			final Node currentNode = currentNodeChildren.item(j);
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
			} catch (final ParseException e1) {
				date = new Date();
			}
    		
    		if(key == null || ip == null || StrSubject == null || StrAuthor == null || StrPage == null || date == null)
    			return false;
    		
    		byte[] subject,author,page;
    		try {
				subject = StrSubject.getBytes("UTF-8");
			} catch (final UnsupportedEncodingException e1) {
				subject = StrSubject.getBytes();
			}
			try {
				author = StrAuthor.getBytes("UTF-8");
			} catch (final UnsupportedEncodingException e1) {
				author = StrAuthor.getBytes();
			}
			try {
				page = StrPage.getBytes("UTF-8");
			} catch (final UnsupportedEncodingException e1) {
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
		} catch (final IOException e) { }
    }
    public Iterator<byte[]> keys(final boolean up) throws IOException {
        return database.keys(up, false);
    }
    /**
     * Comparator to sort objects of type Blog according to their timestamps
     */
    public class BlogComparator implements Comparator<String> {
        
        private final boolean newestFirst;
        
        /**
         * @param newestFirst newest first, or oldest first?
         */
        public BlogComparator(final boolean newestFirst){
            this.newestFirst = newestFirst;
        }
        
        public int compare(final String obj1, final String obj2) {
            final BlogEntry blogEntry1 = readBlogEntry(obj1);
            final BlogEntry blogEntry2 = readBlogEntry(obj2);
            if(blogEntry1 == null || blogEntry2 == null)
                return 0;
            if (this.newestFirst) {
                if (Long.parseLong(blogEntry2.getTimestamp()) - Long.parseLong(blogEntry1.getTimestamp()) > 0) 
                    return 1;
                return -1;
            }
            if (Long.parseLong(blogEntry1.getTimestamp()) - Long.parseLong(blogEntry2.getTimestamp()) > 0) 
                return 1;
            return -1;
        }
    }
    public Iterator<String> getBlogIterator(final boolean priv){
        final TreeSet<String> set = new TreeSet<String>(new BlogComparator(true));
        final Iterator<BlogEntry> iterator = blogIterator(true);
        BlogEntry blogEntry;
        while(iterator.hasNext()){
            blogEntry=iterator.next();
            if(priv || blogEntry.isPublic()){
                set.add(blogEntry.getKey());
            }
        }
        return set.iterator();
    }
    public Iterator<BlogEntry> blogIterator(final boolean up){
        try {
            return new BlogIterator(up);
        } catch (final IOException e) {
            return new HashSet<BlogEntry>().iterator();
        }
    }
    /**
     * Subclass of blogBoard, which provides the blogIterator object-type
     */
    public class BlogIterator implements Iterator<BlogEntry> {
        Iterator<byte[]> blogIter;
        blogBoard.BlogEntry nextEntry;
        public BlogIterator(final boolean up) throws IOException {
            this.blogIter = blogBoard.this.database.keys(up, false);
            this.nextEntry = null;
        }
        
        public boolean hasNext() {
            try {
                return this.blogIter.hasNext();
            } catch (final kelondroException e) {
                //resetDatabase();
                return false;
            }
        }
        
        public BlogEntry next() {
            try {
                return readBlogEntry(new String(this.blogIter.next()));
            } catch (final kelondroException e) {
                //resetDatabase();
                return null;
            }
        }
        
        public void remove() {
            if (this.nextEntry != null) {
                try {
                    final Object blogKey = this.nextEntry.getKey();
                    if (blogKey != null) deleteBlogEntry((String) blogKey);
                } catch (final kelondroException e) {
                    //resetDatabase();
                }
            }
        }
    }
 
    public class BlogEntry {
        
        String key;
        HashMap<String, String> record;
    
        public BlogEntry(final String nkey, final byte[] subject, final byte[] author, final String ip, final Date date, final byte[] page, final ArrayList<String> comments, final String commentMode) {
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
        BlogEntry(final String key, final HashMap<String, String> record) {
            this.key = key;
            this.record = record;
            if (this.record.get("comments")==null) this.record.put("comments", listManager.collection2string(new ArrayList<String>()));
            if (this.record.get("commentMode")==null || this.record.get("commentMode").equals("")) this.record.put("commentMode", "1");
        }
        private void setKey(final String key) {
            if (key.length() > keyLength) 
                this.key = key.substring(0, keyLength);
            this.key = key;
        }
        public String getKey() {
            return key;
        }
        public byte[] getSubject() {
            final String m = record.get("subject");
            if (m == null) return new byte[0];
            final byte[] b = kelondroBase64Order.enhancedCoder.decode(m, "de.anomic.data.blogBoard.subject()");
            if (b == null) return "".getBytes();
            return b;
        }
        private void setSubject(final byte[] subject) {
            if (subject == null) 
                record.put("subject","");
            else 
                record.put("subject", kelondroBase64Order.enhancedCoder.encode(subject));
        }
        public Date getDate() {
            try {
                final String date = record.get("date");
                if (date == null) {
                    serverLog.logFinest("Blog", "ERROR: date field missing in blogBoard");
                    return new Date();
                }
                return serverDate.parseShortSecond(date);
            } catch (final ParseException e) {
                return new Date();
            }
        }
        private void setDate(Date date) {
            if(date == null) 
                date = new Date();
            record.put("date", serverDate.formatShortSecond(date));
        }
        public String getTimestamp() {
            final String timestamp = record.get("date");
            if (timestamp == null) {
                serverLog.logFinest("Blog", "ERROR: date field missing in blogBoard");
                return serverDate.formatShortSecond();
            }
            return timestamp;
        }
        public byte[] getAuthor() {
            final String author = record.get("author");
            if (author == null) return new byte[0];
            final byte[] b = kelondroBase64Order.enhancedCoder.decode(author, "de.anomic.data.blogBoard.author()");
            if (b == null) return "".getBytes();
            return b;
        }
        private void setAuthor(final byte[] author) {
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
            final ArrayList<String> commentsize = listManager.string2arraylist(record.get("comments"));
            return commentsize.size();
        }
        public ArrayList<String> getComments() {
            final ArrayList<String> comments = listManager.string2arraylist(record.get("comments"));
            return comments;
        }
        private void setComments(final ArrayList<String> comments) {
            if (comments == null) 
                record.put("comments", listManager.collection2string(new ArrayList<String>()));
            else 
                record.put("comments", listManager.collection2string(comments));
        }
        public String getIp() {
            final String ip = record.get("ip");
            if (ip == null) return "127.0.0.1";
            return ip;
        }
        private void setIp(String ip) {
            if ((ip == null) || (ip.length() == 0)) 
                ip = "";
            record.put("ip", ip);
        }
        public byte[] getPage() {
            final String page = record.get("page");
            if (page == null) return new byte[0];
            final byte[] page_as_byte = kelondroBase64Order.enhancedCoder.decode(page, "de.anomic.data.blogBoard.page()");
            if (page_as_byte == null) return "".getBytes();
            return page_as_byte;
        }        
        private void setPage(final byte[] page) {
            if (page == null) 
                record.put("page", "");
            else 
                record.put("page", kelondroBase64Order.enhancedCoder.encode(page));
        }
        public void addComment(final String commentID) {
            final ArrayList<String> comments = listManager.string2arraylist(record.get("comments"));
            comments.add(commentID);
            record.put("comments", listManager.collection2string(comments));
        }
        public boolean removeComment(final String commentID) {
            final ArrayList<String> comments = listManager.string2arraylist(record.get("comments"));
            final boolean success = comments.remove(commentID);
            record.put("comments", listManager.collection2string(comments));
            return success;
        }
        /**
         * returns the comment mode
         * 0 - no comments allowed
         * 1 - comments allowed
         * 2 - comments moderated
         * @return comment mode
         */
        public int getCommentMode(){
            return Integer.parseInt(record.get("commentMode"));
        }
        private void setCommentMode(final String mode) {
            if (mode == null) 
                record.put("commentMode", "1");
            else 
                record.put("commentMode", mode);
        }
        public boolean isPublic() {
            final String privacy = record.get("privacy");
            if (privacy == null)
                return true;
            if(privacy.equalsIgnoreCase("public"))
                return true;
            return false;
        }
    }
}
