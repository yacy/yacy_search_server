// BlogBoard.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 20.07.2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.NaturalOrder;
import net.yacy.kelondro.util.kelondroException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.anomic.data.wiki.WikiBoard;
import java.util.List;
import java.util.Set;

public class BlogBoard {
    
    private static final int KEY_LENGTH = 64;
    
    private MapHeap database = null;
    
    public BlogBoard(final File actpath) throws IOException {
        new File(actpath.getParent()).mkdir();
        //database = new MapView(BLOBTree.toHeap(actpath, true, true, keyLength, recordSize, '_', NaturalOrder.naturalOrder, newFile), 500, '_');
        database = new MapHeap(actpath, KEY_LENGTH, NaturalOrder.naturalOrder, 1024 * 64, 500, '_');
    }
    
    public int size() {
        return database.size();
    }
    
    /**
     * Tells if the database contains an element.
     * @param key the ID of the element
     * @return true if the database contains the element, else false
     */
    public boolean contains(final String key) {
        return database.containsKey(UTF8.getBytes(key));
    }
    
    public void close() {
        database.close();
    }
    
    private static String normalize(final String key) {
        return (key == null) ? "null" : key.trim().toLowerCase();
    }
    
    public static String webalize(final String key) {
        return (key == null) ? "null": key.trim().toLowerCase().replaceAll(" ", "%20");
    }
    
    public String guessAuthor(final String ip) {
        return WikiBoard.guessAuthor(ip);
    }
    
    /**
     * Create a new BlogEntry and return it
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
    public BlogEntry newEntry(final String key, final byte[] subject, final byte[] author, final String ip, final Date date, final byte[] page, final List<String> comments, final String commentMode) {
        return new BlogEntry(normalize(key), subject, author, ip, date, page, comments, commentMode);
    }

    /*
     * writes a new page and return the key
     */
    public String writeBlogEntry(final BlogEntry page) {
        String ret = null;
        try {
            database.insert(UTF8.getBytes(page.key), page.record);
            ret = page.key;
        } catch (IOException ex) {
            Log.logException(ex);
        } catch (RowSpaceExceededException ex) {
            Log.logException(ex);
        }
        return ret;
    }
    
    public BlogEntry readBlogEntry(final String key) {
        return readBlogEntry(key, database);
    }
    
    private BlogEntry readBlogEntry(final String key, final MapHeap base) {
        final String normalized = normalize(key);
        Map<String, String> record;
        try {
            record = base.get(UTF8.getBytes(normalized.substring(0, Math.min(normalized.length(), KEY_LENGTH))));
        } catch (final IOException e) {
            Log.logException(e);
            record = null;
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
            record = null;
        }
        return (record == null) ?
            newEntry(key, new byte[0], UTF8.getBytes("anonymous"), "127.0.0.1", new Date(), new byte[0], null, null) :
            new BlogEntry(key, record);
    }
    
    public boolean importXML(final String input) {
    	final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	try {
            final DocumentBuilder builder = factory.newDocumentBuilder();
            return parseXMLimport(builder.parse(new ByteArrayInputStream(UTF8.getBytes(input))));
        } catch (final ParserConfigurationException ex) {
            Log.logException(ex);
        } catch (final SAXException ex) {
            Log.logException(ex);
        } catch (final IOException ex) {
            Log.logException(ex);
        }
		
    	return false;
    }
    
    private boolean parseXMLimport(final Document doc) {
    	if(!"blog".equals(doc.getDocumentElement().getTagName())) {
            return false;
        }
    	
    	final NodeList items = doc.getDocumentElement().getElementsByTagName("item");
    	if(items.getLength() == 0) {
            return false;
        }
    	
    	for (int i = 0, n = items.getLength(); i < n; ++i) {
            String key = null, ip = null, StrSubject = null, StrAuthor = null, StrPage = null, StrDate = null;
            Date date = null;

            if(!"item".equals(items.item(i).getNodeName())) continue;

            final NodeList currentNodeChildren = items.item(i).getChildNodes();

            for (int j = 0, m = currentNodeChildren.getLength(); j < m; ++j) {
                final Node currentNode = currentNodeChildren.item(j);
                if ("id".equals(currentNode.getNodeName())) {
                    key = currentNode.getFirstChild().getNodeValue();
                } else if ("ip".equals(currentNode.getNodeName())) {
                    ip = currentNode.getFirstChild().getNodeValue();
                } else if ("timestamp".equals(currentNode.getNodeName())) {
                    StrDate = currentNode.getFirstChild().getNodeValue();
                } else if ("subject".equals(currentNode.getNodeName())) {
                    StrSubject = currentNode.getFirstChild().getNodeValue();
                } else if ("author".equals(currentNode.getNodeName())) {
                    StrAuthor = currentNode.getFirstChild().getNodeValue();
                } else if ("content".equals(currentNode.getNodeName())) {
                    StrPage = currentNode.getFirstChild().getNodeValue();
                }
            }
    		
            try {
                date = GenericFormatter.SHORT_SECOND_FORMATTER.parse(StrDate);
            } catch (final ParseException e1) {
                date = new Date();
            }

            if(key == null || ip == null || StrSubject == null || StrAuthor == null || StrPage == null || date == null) {
                return false;
            }

            byte[] subject,author,page;
            subject = UTF8.getBytes(StrSubject);
            author = UTF8.getBytes(StrAuthor);
            page = UTF8.getBytes(StrPage);
            writeBlogEntry (newEntry(key, subject, author, ip, date, page, null, null));
    	}
    	return true;
    }
    
    public void deleteBlogEntry(final String key) {
    	try {
            database.delete(UTF8.getBytes(normalize(key)));
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
            if (blogEntry1 == null || blogEntry2 == null)
                return 0;
            if (this.newestFirst) {
                if (Long.parseLong(blogEntry2.getTimestamp()) - Long.parseLong(blogEntry1.getTimestamp()) > 0) {
                    return 1;
                }
                return -1;
            }
            if (Long.parseLong(blogEntry1.getTimestamp()) - Long.parseLong(blogEntry2.getTimestamp()) > 0) {
                return 1;
            }
            return -1;
        }
    }
    
    public Iterator<String> getBlogIterator(final boolean priv){
        final Set<String> set = new TreeSet<String>(new BlogComparator(true));
        final Iterator<BlogEntry> iterator = blogIterator(true);
        BlogEntry blogEntry;
        while (iterator.hasNext()) {
            blogEntry = iterator.next();
            if (priv || blogEntry.isPublic()) {
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
        //blogBoard.BlogEntry nextEntry;
        public BlogIterator(final boolean up) throws IOException {
            this.blogIter = BlogBoard.this.database.keys(up, false);
            //this.nextEntry = null;
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
                return readBlogEntry(UTF8.String(this.blogIter.next()));
            } catch (final kelondroException e) {
                //resetDatabase();
                return null;
            }
        }
        
        public void remove() {
//            if (this.nextEntry != null) {
//                try {
//                    final Object blogKey = this.nextEntry.getKey();
//                    if (blogKey != null) deleteBlogEntry((String) blogKey);
//                } catch (final kelondroException e) {
//                    //resetDatabase();
//                }
//            }
            throw new UnsupportedOperationException("Method not implemented yet.");
        }
    }
 
    public class BlogEntry {
        
        String key;
        Map<String, String> record;
    
        public BlogEntry(final String nkey, final byte[] subject, final byte[] author, final String ip, final Date date, final byte[] page, final List<String> comments, final String commentMode) {
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
            
            WikiBoard.setAuthor(ip, UTF8.String(author));
        }
        
        BlogEntry(final String key, final Map<String, String> record) {
            this.key = key;
            this.record = record;
            if (this.record.get("comments") == null) {
                this.record.put("comments", ListManager.collection2string(new ArrayList<String>()));
            }
            if (this.record.get("commentMode") == null || this.record.get("commentMode").length() < 1) {
                this.record.put("commentMode", "2");
            }
        }
        
        private void setKey(final String key) {
            this.key = key.substring(0, Math.min(key.length(), KEY_LENGTH));
        }
        
        public String getKey() {
            return key;
        }
        
        public byte[] getSubject() {
            final String m = record.get("subject");
            if (m == null) {
                return new byte[0];
            }
            final byte[] b = Base64Order.enhancedCoder.decode(m);
            return (b == null) ? new byte[0] : b;
        }
        
        private void setSubject(final byte[] subject) {
            if (subject == null) {
                record.put("subject","");
            } else {
                record.put("subject", Base64Order.enhancedCoder.encode(subject));
            }
        }
        
        public Date getDate() {
            try {
                final String date = record.get("date");
                if (date == null) {
                    if (Log.isFinest("Blog")) {
                        Log.logFinest("Blog", "ERROR: date field missing in blogBoard");
                    }
                    return new Date();
                }
                return GenericFormatter.SHORT_SECOND_FORMATTER.parse(date);
            } catch (final ParseException ex) {
                return new Date();
            }
        }
        
        private void setDate(final Date date) {
            Date ret = date;
            if (ret == null) {
                ret = new Date();
            }
            record.put("date", GenericFormatter.SHORT_SECOND_FORMATTER.format(ret));
        }
        
        public String getTimestamp() {
            final String timestamp = record.get("date");
            if (timestamp == null) {
                if (Log.isFinest("Blog")) {
                    Log.logFinest("Blog", "ERROR: date field missing in blogBoard");
                }
                return GenericFormatter.SHORT_SECOND_FORMATTER.format();
            }
            return timestamp;
        }
        
        public byte[] getAuthor() {
            final String author = record.get("author");
            if (author == null) {
                return new byte[0];
            }
            final byte[] b = Base64Order.enhancedCoder.decode(author);
            return (b == null) ? new byte[0] : b;
        }
        
        private void setAuthor(final byte[] author) {
            if (author == null)
                record.put("author","");
            else
            record.put("author", Base64Order.enhancedCoder.encode(author));
        }
        
        public int getCommentsSize() {
            // This ist a Bugfix for Version older than 4443.
            if (record.get("comments").startsWith(",")) {
                    record.put("comments", record.get("comments").substring(1));
                    writeBlogEntry(this);
            }
            final List<String> commentsize = ListManager.string2arraylist(record.get("comments"));
            return commentsize.size();
        }
        
        public List<String> getComments() {
            return ListManager.string2arraylist(record.get("comments"));
        }
        
        private void setComments(final List<String> comments) {
            if (comments == null) {
                record.put("comments", ListManager.collection2string(new ArrayList<String>()));
            } else {
                record.put("comments", ListManager.collection2string(comments));
            }
        }
        
        public String getIp() {
            final String ip = record.get("ip");
            return (ip == null) ? "127.0.0.1" : ip;
        }
        
        private void setIp(final String ip) {
            String ret = ip;
            if ((ret == null) || (ret.length() == 0))
                ret = "";
            record.put("ip", ret);
        }
        
        public byte[] getPage() {
            final String page = record.get("page");
            if (page == null) {
                return new byte[0];
            }
            final byte[] page_as_byte = Base64Order.enhancedCoder.decode(page);
            return (page_as_byte == null) ? new byte[0] : page_as_byte;
        }  
        
        private void setPage(final byte[] page) {
            if (page == null) {
                record.put("page", "");
            } else {
                record.put("page", Base64Order.enhancedCoder.encode(page));
            }
        }
        
        public void addComment(final String commentID) {
            final List<String> comments = ListManager.string2arraylist(record.get("comments"));
            comments.add(commentID);
            record.put("comments", ListManager.collection2string(comments));
        }
        
        public boolean removeComment(final String commentID) {
            final List<String> comments = ListManager.string2arraylist(record.get("comments"));
            final boolean success = comments.remove(commentID);
            record.put("comments", ListManager.collection2string(comments));
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
            if (mode == null) {
                record.put("commentMode", "2");
            } else {
                record.put("commentMode", mode);
            }
        }
        
        public boolean isPublic() {
            final String privacy = record.get("privacy");
            return (privacy == null || privacy.equalsIgnoreCase("public")) ? true : false;
        }
        
    }
}
