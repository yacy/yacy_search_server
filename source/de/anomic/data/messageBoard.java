// messageBoard.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.06.2004
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

package de.anomic.data;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import de.anomic.kelondro.kelondroBLOBTree;
import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroMap;
import de.anomic.kelondro.kelondroNaturalOrder;

public class messageBoard {
    
    private static final int categoryLength = 12;
    private static final String dateFormat = "yyyyMMddHHmmss";
    private static final int recordSize = 512;

    static SimpleDateFormat SimpleFormatter = new SimpleDateFormat(dateFormat);

    static {
        SimpleFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    kelondroMap database = null;
    private int sn = 0;

    public messageBoard(final File path) {
        new File(path.getParent()).mkdir();
        if (database == null) {
            database = new kelondroMap(new kelondroBLOBTree(path, true, true, categoryLength + dateFormat.length() + 2, recordSize, '_', kelondroNaturalOrder.naturalOrder, true, false, false), 500);
        }
        sn = 0;
    }

    public int size() {
        return database.size();
    }
    
    public void close() {
        database.close();
    }
    
    static String dateString() {
        synchronized (SimpleFormatter) {
            return SimpleFormatter.format(new Date());
        }
    }

    String snString() {
	String s = Integer.toString(sn);
	if (s.length() == 1) s = "0" + s;
	sn++;
	if (sn > 99) sn = 0;
	return s;
    }

    public entry newEntry(final String category,
                          final String authorName, final String authorHash,
                          final String recName, final String recHash,
                          final String subject, final byte[] message) {
	return new entry(category, authorName, authorHash, recName, recHash, subject, message);
    }

    public class entry {
	
	String key; // composed by category and date
    Map<String, String> record; // contains author, target hash, subject and message

	public entry(final String category,
                     String authorName, String authorHash,
                     String recName, String recHash,
                     String subject, final byte[] message) {
	    record = new HashMap<String, String>();
	    key = category;
	    if (key.length() > categoryLength) key = key.substring(0, categoryLength);
	    while (key.length() < categoryLength) key += "_";
	    key += dateString() + snString();
	    if ((authorName == null) || (authorName.length() == 0)) authorName = "anonymous";
	    record.put("author", authorName);
	    if ((recName == null) || (recName.length() == 0)) recName = "anonymous";
	    record.put("recipient", recName);
	    if (authorHash == null) authorHash = "";
	    record.put("ahash", authorHash);
	    if (recHash == null) recHash = "";
	    record.put("rhash", recHash);
            if (subject == null) subject = "";
	    record.put("subject", subject);
            if (message == null)
		record.put("message", "");
	    else
		record.put("message", kelondroBase64Order.enhancedCoder.encode(message));
            record.put("read", "false");
	}

	entry(final String key, final Map<String, String> record) {
	    this.key = key;
	    this.record = record;
	}

	public Date date() {
	    try {
		String c = key.substring(categoryLength);
		c = c.substring(0, c.length() - 2);
                synchronized (SimpleFormatter) {
                    return SimpleFormatter.parse(c);
                }
	    } catch (final ParseException e) {
		return new Date();
	    }
	}

	public String category() {
	    String c = key.substring(0, categoryLength);
	    while (c.endsWith("_")) c = c.substring(0, c.length() - 1);
	    return c;
	}

	public String author() {
	    final String a = record.get("author");
	    if (a == null) return "anonymous";
        return a;
	}

	public String recipient() {
	    final String a = record.get("recipient");
	    if (a == null) return "anonymous";
        return a;
	}

	public String authorHash() {
	    final String a = record.get("ahash");
	    if (a == null) return null;
        return a;
	}

	public String recipientHash() {
	    final String a = record.get("rhash");
	    if (a == null) return null;
        return a;
	}

        public String subject() {
	    final String s = record.get("subject");
	    if (s == null) return "";
        return s;
	}

	public byte[] message() {
	    final String m = record.get("message");
	    if (m == null) return new byte[0];
            record.put("read", "true");
	    return kelondroBase64Order.enhancedCoder.decode(m, "de.anomic.data.messageBoard.message()");
	}
        
        public boolean read() {
            final String r = record.get("read");
            if (r == null) return false;
            if (r.equals("false")) return false;
            return true;
        }
    }

    public String write(final entry message) {
        // writes a message and returns key
        try {
            database.put(message.key, message.record);
            return message.key;
        } catch (final IOException e) {
            return null;
        }
    }
    
    public entry read(final String key) {
        Map<String, String> record;
        try {
            record = database.get(key);
        } catch (final IOException e) {
            return null;
        }
	    return new entry(key, record);
    }
    
    public void remove(final String key) {
        try {
            database.remove(key);
        } catch (final IOException e) {
        }
    }
    
    public Iterator<String> keys(final String category, final boolean up) throws IOException {
	//return database.keys();
        return new catIter(category, up);
    }

    public class catIter implements Iterator<String> {
    
        Iterator<byte[]> allIter = null;
        String nextKey = null;
        String category = "";
        
        public catIter(final String category, final boolean up) throws IOException {
            this.allIter = database.keys(up, false);
            this.category = category;
            findNext();
        }
        
        public void findNext() {
            while (allIter.hasNext()) {
                nextKey = new String(allIter.next());
                if (this.category==null || nextKey.startsWith(this.category)) return;
            }
            nextKey = null;
        }
        
        public boolean hasNext() {
            return nextKey != null;
        }
        
        public String next() {
            final String next = nextKey;
            findNext();
            return next;
        }
        
        public void remove() {
        }
        
    }
}
