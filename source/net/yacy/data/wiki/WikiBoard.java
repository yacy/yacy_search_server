//wikiBoard.java
//-------------------------------------
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.data.wiki;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.util.CommonPattern;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.blob.MapHeap;

/**
 *
 */
public class WikiBoard {

    public  static final int keyLength = 64;
    private static final String DATE_FORMAT = "yyyyMMddHHmmss";
    private static final String ANONYMOUS = "anonymous";

    protected static final SimpleDateFormat SimpleFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.US);

    static {
        SimpleFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private MapHeap datbase = null;
    private MapHeap bkpbase = null;
    private static final Map<String, String> AUTHORS = new HashMap<String, String>();

    /**
     * Constructor.
     * @param actpath path of database which contains current wiki data.
     * @param bkppath path of backup database.
     * @throws IOException if error occurs during HDD access.
     */
    public WikiBoard(final File actpath, final File bkppath) throws IOException {
        new File(actpath.getParent()).mkdirs();
        if (this.datbase == null) {
            //datbase = new MapView(BLOBTree.toHeap(actpath, true, true, keyLength, recordSize, '_', NaturalOrder.naturalOrder, actpathNew), 500, '_');
            this.datbase = new MapHeap(actpath, keyLength, NaturalOrder.naturalOrder, 1024 * 64, 500, '_');
        }
        new File(bkppath.getParent()).mkdirs();
        if (this.bkpbase == null) {
            //bkpbase = new MapView(BLOBTree.toHeap(bkppath, true, true, keyLength + dateFormat.length(), recordSize, '_', NaturalOrder.naturalOrder, bkppathNew), 500, '_');
            this.bkpbase = new MapHeap(bkppath, keyLength + DATE_FORMAT.length(), NaturalOrder.naturalOrder, 1024 * 64, 500, '_');
        }
    }

    /**
     * Gets total number of entries of wiki DB and DB which contains backup entries.
     * @return number of entries in wiki plus number of old entries.
     */
    public int sizeOfTwo() {
        return this.datbase.size() + this.bkpbase.size();
    }

    /**
     * Gets number of entries of wiki DB.
     * @return number of entries in wiki.
     */
    public int size() {
        return this.datbase.size();
    }

    /**
     * Closes database files.
     */
    public synchronized void close() {
        this.datbase.close();
        this.bkpbase.close();
    }

    /**
     * Gets current date.
     * @return current date.
     */
    static String dateString() {
        return dateString(new Date());
    }

    /**
     * Gets String representation of a Date.
     * @param date the Date.
     * @return String representation of Date.
     */
    public static String dateString(final Date date) {
        synchronized (SimpleFormatter) {
            return SimpleFormatter.format(date);
        }
    }

    /**
     * Gets normalized version of a key.
     * @param key the key.
     * @return normalized version of key.
     */
    private static String normalize(final String key) {
        return (key != null) ? key.trim().toLowerCase() : "null";
    }

    /**
     * Normalizes key and replaces spaces by escape code.
     * @param key the key.
     * @return normalized and webalized version.
     */
    public static String webalize(final String key) {
        return (key != null) ? CommonPattern.SPACE.matcher(normalize(key)).replaceAll("%20") : "null";
    }

    /**
     * Tries to guess the name of the author by a given IP address.
     * @param ip the IP address.
     * @return
     */
    public static String guessAuthor(final String ip) {
        final String author = AUTHORS.get(ip);
        //yacyCore.log.logDebug("DEBUG: guessing author for ip = " + ip + " is '" + author + "', authors = " + authors.toString());
        return author;
    }

    /**
     * Adds an author name and a corresponding IP to internal Map.
     * @param ip IP address of the author.
     * @param author name of author.
     */
    public static void setAuthor(final String ip, final String author) {
        AUTHORS.put(ip,author);
    }

    /**
     * Creates new Entry.
     * @param subject subject of entry.
     * @param author author of entry.
     * @param ip IP address of author.
     * @param reason reason for new Entry (for example "edit").
     * @param page content of Entry.
     * @return new Entry.
     * @throws IOException
     */
    public Entry newEntry(final String subject, final String author, final String ip, final String reason, final byte[] page) throws IOException {
        return new Entry(normalize(subject), author, ip, reason, page);
    }

    /**
     * Contains information of wiki page.
     */
    public class Entry {
        private static final String ANONYMOUS = "anonymous";

        private final String key;
        private final Map<String, String> record;

        /**
         * Constructor which creates new Entry using given information.
         * @param subject subject of Entry.
         * @param author author of Entry.
         * @param ip IP address of author.
         * @param reason reason for new Entry (for example "edit").
         * @param page content of Entry.
         * @throws IOException
         */
        public Entry(final String subject, final String author, final String ip, final String reason, final byte[] page) throws IOException {
            this.record = new HashMap<String, String>();
            this.key = subject.substring(0, Math.min((subject != null) ? subject.length() : 0, keyLength));
            this.record.put("date", dateString());
            this.record.put("author", Base64Order.enhancedCoder.encodeString((author != null && author.length() > 0) ? author : ANONYMOUS));
            this.record.put("ip", (ip != null && ip.length() > 0) ? ip : "");
            this.record.put("reason", Base64Order.enhancedCoder.encodeString((reason != null && reason.length() > 0) ? reason : ""));
            this.record.put("page", (page != null) ? Base64Order.enhancedCoder.encode(page) : "");
            AUTHORS.put(ip, author);
        }

        /**
         * Constructor which creates Entry using key and record.
         * @param key key of Entry.
         * @param record record which contains data.
         */
        Entry(final String key, final Map<String, String> record) {
            this.key = key;
            this.record = record;
        }

        /**
         * Gets subject of Entry.
         * @return subject of entry.
         */
        public String subject() {
            return this.key;
        }

        /**
         * Gets date of Entry.
         * @return date of Entry.
         */
        public Date date() {
            Date ret;
            try {
                final String c = this.record.get("date");
                if (c == null) {
                    System.out.println("DEBUG - ERROR: date field missing in wikiBoard");
                    ret = new Date();
                } else {
                    synchronized (SimpleFormatter) {
                        ret = SimpleFormatter.parse(c);
                    }
                }
            } catch (final ParseException e) {
                ret = new Date();
            }
            return ret;
        }

        /**
         * Gets author of Entry.
         * @return author of Entry.
         */
        public String author() {
            final String a = this.record.get("author");
            final byte[] b;
            return (a != null && (b = Base64Order.enhancedCoder.decode(a)) != null) ? UTF8.String(b) : ANONYMOUS;
        }

        /**
         * Gets reason for Entry.
         * @return reason for Entry.
         */
        public String reason() {
            final String ret;
            final String r = this.record.get("reason");
            if (r != null) {
                final byte[] b;
                ret = ((b = Base64Order.enhancedCoder.decode(r)) != null) ? UTF8.String(b) : "unknown";
            } else {
                ret = "";
            }
            return ret;
        }

        /**
         * Gets actual content of Entry.
         * @return content of Entry.
         */
        public byte[] page() {
            final String m = this.record.get("page");
            final byte[] b;
            return (m != null && (b = Base64Order.enhancedCoder.decode(m)) != null) ? b : new byte[0];
        }

        /**
         * Sets date of previous version of Entry.
         * @param date date of previous version of Entry.
         */
        void setAncestorDate(final Date date) {
            this.record.put("bkp", dateString(date));
        }

        /**
         * Gets date of previous version of Entry.
         * @return date of previous version of Entry.
         */
        private Date getAncestorDate() {
            Date ret = null;
            try {
                final String c = this.record.get("date");
                if (c != null) {
                    synchronized (SimpleFormatter) {
                        ret = SimpleFormatter.parse(c);
                    }
                }
            } catch (final ParseException e) {
                ret = null;
            }
            return ret;
        }

        /**
         * Gets previous version of Entry.
         * @return previous version of Entry.
         */
        public Entry getAncestor() {
            final Date ancDate = getAncestorDate();
            return (ancDate == null) ? null : read(this.key + dateString(ancDate), WikiBoard.this.bkpbase);
        }

        /**
         * Adds child of current Entry.
         * @param subject subject of child of current Entry.
         */
        void setChild(final String subject) {
            this.record.put("child", Base64Order.enhancedCoder.encode(UTF8.getBytes(subject)));
        }

        /**
         * Gets name (= subject) of child of this Entry.
         * @return name of child of this Entry.
         */
        private String getChildName() {
            final String c = this.record.get("child");
            final byte[] subject;
            return (c != null && (subject = Base64Order.enhancedCoder.decode(c)) != null) ? ASCII.String(subject) : null;
        }

        /**
         * Tells if Entry has child.
         * @return true if has child, else false.
         */
        public boolean hasChild() {
            final String c = this.record.get("child");
            return (c != null && Base64Order.enhancedCoder.decode(c) != null) ? true : false;
        }

        /**
         * Gets child of this Entry.
         * @return child of this Entry.
         */
        public Entry getChild() {
            final String childName = getChildName();
            return (childName == null) ? null : read(childName, WikiBoard.this.datbase);
        }
    }

    /**
     * Writes content of Entry to database and returns key.
     * @param entry Entry to be written.
     * @return key of Entry.
     */
    public String write(final Entry entry) {
        // writes a new page and returns key
        String key = null;
        try {
            // first load the old page
            final Entry oldEntry = read(entry.key);
            // set the bkp date of the new page to the date of the old page
            final Date oldDate = oldEntry.date();
            entry.setAncestorDate(oldDate);
            oldEntry.setChild(entry.subject());
            // write the backup
            this.bkpbase.insert(UTF8.getBytes(entry.key + dateString(oldDate)), oldEntry.record);
            // write the new page
            this.datbase.insert(UTF8.getBytes(entry.key), entry.record);
            key = entry.key;
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
        return key;
    }

    /**
     * Reads content of Entry from database.
     * @param key key of Entry.
     * @return Entry which contains data.
     */
    public Entry read(final String key) {
        return read(key, this.datbase);
    }

    /**
     * Reads content of Entry from database.
     * @param key key of Entry.
     * @param base database containing data.
     * @return Entry which contains data.
     */
    Entry read(final String key, final MapHeap base) {
        Entry ret = null;
        try {
            String copyOfKey = normalize(key);
            if (copyOfKey.length() > keyLength) {
                copyOfKey = copyOfKey.substring(0, keyLength);
            }
            final Map<String, String> record = base.get(UTF8.getBytes(copyOfKey));
            ret = (record == null) ? newEntry(copyOfKey, ANONYMOUS, Domains.LOCALHOST, "New Page", UTF8.getBytes("")) : new Entry(copyOfKey, record);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        return ret;
    }

    /**
     * Reads old Entry from backup database.
     * @param key key of Entry.
     * @return the Entry.
     */
    public Entry readBkp(final String key) {
        return read(key, this.bkpbase);
    }

    /**
     * Gets Iterator of keys in database.
     * @param up
     * @return keys of Entries in database.
     * @throws IOException
     */
    public Iterator<byte[]> keys(final boolean up) throws IOException {
        return this.datbase.keys(up, false);
    }

    /**
     * Gets Iterator of keys in backup database.
     * @param up
     * @return keys of Entries in backup database.
     * @throws IOException
     */
    public Iterator<byte[]> keysBkp(final boolean up) throws IOException {
        return this.bkpbase.keys(up, false);
    }
}
