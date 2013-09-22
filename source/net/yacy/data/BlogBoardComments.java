// BlogBoardComments.java
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

package net.yacy.data;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.wiki.WikiBoard;
import net.yacy.kelondro.blob.MapHeap;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


public class BlogBoardComments {

    private final static int KEY_LENGTH = 64;
    private final static String DATE_FORMAT = "yyyyMMddHHmmss";

    private final static SimpleDateFormat SIMPLE_DATE_FORMATTER = new SimpleDateFormat(DATE_FORMAT, Locale.US);
    static {
        SIMPLE_DATE_FORMATTER.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private MapHeap database = null;

    public BlogBoardComments(final File actpath) throws IOException {
        new File(actpath.getParent()).mkdir();
        //database = new MapView(BLOBTree.toHeap(actpath, true, true, keyLength, recordSize, '_', NaturalOrder.naturalOrder, newFile), 500, '_');
        this.database = new MapHeap(actpath, KEY_LENGTH, NaturalOrder.naturalOrder, 1024 * 64, 500, '_');
    }

    public int size() {
        return this.database.size();
    }

    public synchronized void close() {
        this.database.close();
    }

    static String dateString(final Date date) {
        synchronized (SIMPLE_DATE_FORMATTER) {
            return SIMPLE_DATE_FORMATTER.format(date);
        }
    }

    private static String normalize(final String key) {
        return (key == null) ? "null" : key.trim().toLowerCase();
    }
    public static String webalize(final String key) {
        if (key == null) {
            return "null";
        }
        String ret = key.trim().toLowerCase();
        int p;
        while ((p = ret.indexOf(' ',0)) >= 0)
            ret = ret.substring(0, p) + "%20" + key.substring(p +1);
        return ret;
    }

    public String guessAuthor(final String ip) {
        return WikiBoard.guessAuthor(ip);
    }

    public CommentEntry newEntry(final String key, final byte[] subject, final byte[] author, final String ip, final Date date, final byte[] page) {
        return new CommentEntry(normalize(key), subject, author, ip, date, page);
    }

    public String write(final CommentEntry page) {
        // writes a new page and returns key
    	try {
    	    this.database.insert(UTF8.getBytes(page.key), page.record);
    	    return page.key;
    	} catch (final Exception e) {
    	    ConcurrentLog.logException(e);
    	    return null;
    	}
    }
    public CommentEntry read(final String key) {
        //System.out.println("DEBUG: read from blogBoardComments");
        return read(key, this.database);
    }

    private CommentEntry read(final String key, final MapHeap base) {
        String copyOfKey = normalize(key);
        copyOfKey = copyOfKey.substring(0, Math.min(copyOfKey.length(), KEY_LENGTH));
        Map<String, String> record;
        try {
            record = base.get(UTF8.getBytes(copyOfKey));
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            record = null;
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
            record = null;
        }
        return (record == null) ?
            newEntry(copyOfKey, new byte[0], UTF8.getBytes("anonymous"), Domains.LOCALHOST, new Date(), new byte[0]) :
            new CommentEntry(copyOfKey, record);
    }

    public boolean importXML(final String input) {
        boolean ret = false;
    	final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	try {
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.parse(new ByteArrayInputStream(UTF8.getBytes(input)));
            ret = parseXMLimport(doc);
        }
        catch (final ParserConfigurationException e) {}
        catch (final SAXException e) {}
        catch (final IOException e) {}

    	return ret;
    }

    private boolean parseXMLimport(final Document doc) {
    	if(!"blog".equals(doc.getDocumentElement().getTagName())) {
            return false;
        }

    	final NodeList items = doc.getDocumentElement().getElementsByTagName("item");
    	if(items.getLength() == 0) {
            return false;
        }

    	for(int i = 0, n = items.getLength(); i < n; ++i) {
            String key = null, ip = null, StrSubject = null, StrAuthor = null, StrPage = null, StrDate = null;
            Date date = null;

            if(!"item".equals(items.item(i).getNodeName()))
                continue;

            final NodeList currentNodeChildren = items.item(i).getChildNodes();

            for(int j=0, m = currentNodeChildren.getLength(); j < m; ++j) {

                final Node currentNode = currentNodeChildren.item(j);
                if ("id".equals(currentNode.getNodeName())) {
                    key = currentNode.getFirstChild().getNodeValue();
                } else if("ip".equals(currentNode.getNodeName())) {
                    ip = currentNode.getFirstChild().getNodeValue();
                } else if("timestamp".equals(currentNode.getNodeName())) {
                    StrDate = currentNode.getFirstChild().getNodeValue();
                } else if("subject".equals(currentNode.getNodeName())) {
                    StrSubject = currentNode.getFirstChild().getNodeValue();
                } else if("author".equals(currentNode.getNodeName())) {
                    StrAuthor = currentNode.getFirstChild().getNodeValue();
                } else if("content".equals(currentNode.getNodeName())) {
                    StrPage = currentNode.getFirstChild().getNodeValue();
                }
            }

            try {
                date = SIMPLE_DATE_FORMATTER.parse(StrDate);
            } catch (final ParseException ex) {
                    date = new Date();
            }

            if (key == null || ip == null || StrSubject == null || StrAuthor == null || StrPage == null || date == null)
                return false;

            byte[] subject,author,page;
            subject = UTF8.getBytes(StrSubject);
            author = UTF8.getBytes(StrAuthor);
            page = UTF8.getBytes(StrPage);
            write (newEntry(key, subject, author, ip, date, page));
    	}

    	return true;
    }

    public void delete(final String key) {
    	try {
            this.database.delete(UTF8.getBytes(normalize(key)));
        }
        catch (final IOException e) { }
    }

    public Iterator<byte[]> keys(final boolean up) throws IOException {
        return this.database.keys(up, false);
    }

    public static class CommentEntry {

        String key;
        Map<String, String> record;

        public CommentEntry(final String nkey, final byte[] subject, final byte[] author, final String ip, final Date date, final byte[] page) {
            this.record = new HashMap<String, String>();

            setKey(nkey);
            setDate(date);
            setSubject(subject);
            setAuthor(author);
            setIp(ip);
            setPage(page);

            WikiBoard.setAuthor(ip, UTF8.String(author));
        }

        CommentEntry(final String key, final Map<String, String> record) {
            this.key = key;
            this.record = record;
            if (this.record.get("comments") == null) {
                this.record.put("comments", ListManager.collection2string(new ArrayList<String>()));
            }
        }

        public String getKey() {
            return this.key;
        }

        private void setKey(final String var) {
            this.key = var.substring(0, Math.min(var.length(), KEY_LENGTH));
        }

        private void setSubject(final byte[] subject) {
            if (subject == null)
                this.record.put("subject","");
            else
                this.record.put("subject", Base64Order.enhancedCoder.encode(subject));
        }
        public byte[] getSubject() {
            final String subject = this.record.get("subject");
            if (subject == null) return new byte[0];
            final byte[] subject_bytes = Base64Order.enhancedCoder.decode(subject);
            if (subject_bytes == null) return new byte[0];
            return subject_bytes;
        }
        private void setDate(Date date) {
            if(date == null)
                date = new Date();
            this.record.put("date", dateString(date));
        }
        public Date getDate() {
            try {
                final String date = this.record.get("date");
                if (date == null) {
                    if (ConcurrentLog.isFinest("Blog")) ConcurrentLog.finest("Blog", "ERROR: date field missing in blogBoard");
                    return new Date();
                }
                synchronized (SIMPLE_DATE_FORMATTER) {
                    return SIMPLE_DATE_FORMATTER.parse(date);
                }
            } catch (final ParseException e) {
                return new Date();
            }
        }

        public String getTimestamp() {
            final String timestamp = this.record.get("date");
            if (timestamp == null) {
                if (ConcurrentLog.isFinest("Blog")) ConcurrentLog.finest("Blog", "ERROR: date field missing in blogBoard");
                return dateString(new Date());
            }
            return timestamp;
        }
        private void setAuthor(final byte[] author) {
            if (author == null)
                this.record.put("author","");
            else
                this.record.put("author", Base64Order.enhancedCoder.encode(author));
        }
        public byte[] getAuthor() {
            final String author = this.record.get("author");
            if (author == null)
                return new byte[0];
            final byte[] author_byte = Base64Order.enhancedCoder.decode(author);
            if (author_byte == null)
                return new byte[0];
            return author_byte;
        }
        private void setIp(String ip) {
            if ((ip == null) || (ip.isEmpty()))
                ip = "";
            this.record.put("ip", ip);
        }
        public String getIp() {
            final String ip = this.record.get("ip");
            if (ip == null)
                return Domains.LOCALHOST;
            return ip;
        }
        private void setPage(final byte[] page) {
            if (page == null)
                this.record.put("page", "");
            else
                this.record.put("page", Base64Order.enhancedCoder.encode(page));
        }
        public byte[] getPage() {
            final String page = this.record.get("page");
            if (page == null)
                return new byte[0];
            final byte[] page_byte = Base64Order.enhancedCoder.decode(page);
            if (page_byte == null)
                return new byte[0];
            return page_byte;
        }
        /**
         * Is the comment allowed?
         * this is possible for moderated blog entry only and means
         * the administrator has explicit allowed the comment.
         * @return
         */
        public boolean isAllowed() {
            return "true".equals(this.record.get("moderated"));
        }
        public void allow() {
            this.record.put("moderated", "true");
        }

    }

}
