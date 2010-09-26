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

package de.anomic.data.wiki;

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

import net.yacy.kelondro.blob.MapHeap;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.Base64Order;
import net.yacy.kelondro.order.NaturalOrder;


public class wikiBoard {

    public  static final int keyLength = 64;
    private static final String DATE_FORMAT = "yyyyMMddHHmmss";
    private static final String ANONYMOUS = "anonymous";

    protected static final SimpleDateFormat SimpleFormatter = new SimpleDateFormat(DATE_FORMAT, Locale.US);

    static {
        SimpleFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    MapHeap datbase = null;
    MapHeap bkpbase = null;
    static HashMap<String, String> authors = new HashMap<String, String>();

    public wikiBoard( final File actpath, final File bkppath) throws IOException {
        new File(actpath.getParent()).mkdirs();
        if (datbase == null) {
            //datbase = new MapView(BLOBTree.toHeap(actpath, true, true, keyLength, recordSize, '_', NaturalOrder.naturalOrder, actpathNew), 500, '_');
            datbase = new MapHeap(actpath, keyLength, NaturalOrder.naturalOrder, 1024 * 64, 500, '_');
        }
        new File(bkppath.getParent()).mkdirs();
        if (bkpbase == null) {
            //bkpbase = new MapView(BLOBTree.toHeap(bkppath, true, true, keyLength + dateFormat.length(), recordSize, '_', NaturalOrder.naturalOrder, bkppathNew), 500, '_');
            bkpbase = new MapHeap(bkppath, keyLength + DATE_FORMAT.length(), NaturalOrder.naturalOrder, 1024 * 64, 500, '_');
        }
    }

    public int sizeOfTwo() {
        return datbase.size() + bkpbase.size();
    }

    public int size() {
        return datbase.size();
    }

    public void close() {
        datbase.close();
        bkpbase.close();
    }

    static String dateString() {
        return dateString(new Date());
    }

    public static String dateString(final Date date) {
        synchronized (SimpleFormatter) {
            return SimpleFormatter.format(date);
        }
    }

    private static String normalize(final String key) {
        return (key != null) ? key.trim().toLowerCase() : "null";
    }

    public static String webalize(final String key) {
        return (key != null) ? normalize(key).replaceAll(" ", "%20") : "null";
    }

    public static String guessAuthor(final String ip) {
        final String author = authors.get(ip);
        //yacyCore.log.logDebug("DEBUG: guessing author for ip = " + ip + " is '" + author + "', authors = " + authors.toString());
        return author;
    }

    public static void setAuthor(final String ip, final String author) {
        authors.put(ip,author);
    }

    public entry newEntry(final String subject, final String author, final String ip, final String reason, final byte[] page) throws IOException {
        return new entry(normalize(subject), author, ip, reason, page);
    }

    public class entry {
        private static final String ANONYMOUS = "anonymous";

        String key;
        Map<String, String> record;

        public entry(final String subject, String author, String ip, String reason, final byte[] page) throws IOException {
            record = new HashMap<String, String>();
            key = subject;
            if (key.length() > keyLength) {
                key = key.substring(0, keyLength);
            }
            record.put("date", dateString());
            if ((author == null) || (author.length() == 0)) {
                author = ANONYMOUS;
            }
            record.put("author", Base64Order.enhancedCoder.encode(author.getBytes("UTF-8")));
            if ((ip == null) || (ip.length() == 0)) {
                ip = "";
            }
            record.put("ip", ip);
            if ((reason == null) || (reason.length() == 0)) {
                reason = "";
            }
            record.put("reason", Base64Order.enhancedCoder.encode(reason.getBytes("UTF-8")));
            if (page == null) {
                record.put("page", "");
            } else {
                record.put("page", Base64Order.enhancedCoder.encode(page));
            }
            authors.put(ip, author);
            //System.out.println("DEBUG: setting author " + author + " for ip = " + ip + ", authors = " + authors.toString());
        }

        entry(final String key, final Map<String, String> record) {
            this.key = key;
            this.record = record;
        }

        public String subject() {
            return key;
        }

        public Date date() {
            try {
                final String c = record.get("date");
                if (c == null) {
                    System.out.println("DEBUG - ERROR: date field missing in wikiBoard");
                    return new Date();
                }
                synchronized (SimpleFormatter) {
                    return SimpleFormatter.parse(c);
                }
            } catch (final ParseException e) {
                return new Date();
            }
        }

        public String author() {
            final String a = record.get("author");
            final byte[] b;
            return (a != null && (b = Base64Order.enhancedCoder.decode(a)) != null) ? new String(b) : ANONYMOUS;
        }

        public String reason() {
            final String r = record.get("reason");
            if (r == null) {
                return "";
            }
            final byte[] b = Base64Order.enhancedCoder.decode(r);
            if (b == null) {
                return "unknown";
            }
            return new String(b);
        }

        public byte[] page() {
            final String m = record.get("page");
            final byte[] b;
            return (m != null && (b = Base64Order.enhancedCoder.decode(m)) != null) ? b : new byte[0];
        }

        void setAncestorDate(final Date date) {
            record.put("bkp", dateString(date));
        }

        private Date getAncestorDate() {
            try {
                final String c = record.get("date");
                if (c == null) return null;
                synchronized (SimpleFormatter) {
                    return SimpleFormatter.parse(c);
                }
            } catch (final ParseException e) {
                return null;
            }
        }

        /*
	public boolean hasAncestor() {
	    Date ancDate = getAncestorDate();
	    if (ancDate == null) return false;
	    try {
		return bkpbase.has(key + dateString(ancDate));
	    } catch (IOException e) {
		return false;
	    }
	}
         */

        public entry getAncestor() {
            final Date ancDate = getAncestorDate();
            return (ancDate == null) ? null : read(key + dateString(ancDate), bkpbase);
        }

        void setChild(final String subject) {
            record.put("child", Base64Order.enhancedCoder.encode(subject.getBytes()));
        }

        private String getChildName() {
            final String c = record.get("child");
            final byte[] subject;
            return (c != null && (subject = Base64Order.enhancedCoder.decode(c)) != null) ? new String(subject) : null;
        }

        public boolean hasChild() {
            final String c = record.get("child");
            return (c != null && Base64Order.enhancedCoder.decode(c) != null) ? true : false;
        }

        public entry getChild() {
            final String childName = getChildName();
            return (childName == null) ? null : read(childName, datbase);
        }
    }

    public String write(final entry page) {
        // writes a new page and returns key
        String ret = null;
        try {
            // first load the old page
            final entry oldEntry = read(page.key);
            // set the bkp date of the new page to the date of the old page
            final Date oldDate = oldEntry.date();
            page.setAncestorDate(oldDate);
            oldEntry.setChild(page.subject());
            // write the backup
            //System.out.println("key = " + page.key);
            //System.out.println("oldDate = " + oldDate);
            //System.out.println("record = " + oldEntry.record.toString());
            bkpbase.insert((page.key + dateString(oldDate)).getBytes(), oldEntry.record);
            // write the new page
            datbase.insert(page.key.getBytes(), page.record);
            ret = page.key;
        } catch (final Exception e) {
            Log.logException(e);
        }
        return ret;
    }

    public entry read(final String key) {
        return read(key, datbase);
    }

    entry read(String key, final MapHeap base) {
        entry ret = null;
        try {
            key = normalize(key);
            if (key.length() > keyLength) {
                key = key.substring(0, keyLength);
            }
            final Map<String, String> record = base.get(key.getBytes());
            ret = (record == null) ? newEntry(key, ANONYMOUS, "127.0.0.1", "New Page", "".getBytes()) : new entry(key, record);
        } catch (final IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        return ret;
    }
    
    public entry readBkp(final String key) {
        return read(key, bkpbase);
    }

    /*
    public boolean has(String key) {
	try {
	    return datbase.has(normalize(key));
	} catch (IOException e) {
	    return false;
	}
    }
     */

    public Iterator<byte[]> keys(final boolean up) throws IOException {
        return datbase.keys(up, false);
    }

    public Iterator<byte[]> keysBkp(final boolean up) throws IOException {
        return bkpbase.keys(up, false);
    }
}
