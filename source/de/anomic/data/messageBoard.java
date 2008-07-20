// messageBoard.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
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

    public messageBoard(File path) {
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

    public entry newEntry(String category,
                          String authorName, String authorHash,
                          String recName, String recHash,
                          String subject, byte[] message) {
	return new entry(category, authorName, authorHash, recName, recHash, subject, message);
    }

    public class entry {
	
	String key; // composed by category and date
    HashMap<String, String> record; // contains author, target hash, subject and message

	public entry(String category,
                     String authorName, String authorHash,
                     String recName, String recHash,
                     String subject, byte[] message) {
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

	entry(String key, HashMap<String, String> record) {
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
	    } catch (ParseException e) {
		return new Date();
	    }
	}

	public String category() {
	    String c = key.substring(0, categoryLength);
	    while (c.endsWith("_")) c = c.substring(0, c.length() - 1);
	    return c;
	}

	public String author() {
	    String a = record.get("author");
	    if (a == null) return "anonymous";
        return a;
	}

	public String recipient() {
	    String a = record.get("recipient");
	    if (a == null) return "anonymous";
        return a;
	}

	public String authorHash() {
	    String a = record.get("ahash");
	    if (a == null) return null;
        return a;
	}

	public String recipientHash() {
	    String a = record.get("rhash");
	    if (a == null) return null;
        return a;
	}

        public String subject() {
	    String s = record.get("subject");
	    if (s == null) return "";
        return s;
	}

	public byte[] message() {
	    String m = record.get("message");
	    if (m == null) return new byte[0];
            record.put("read", "true");
	    return kelondroBase64Order.enhancedCoder.decode(m, "de.anomic.data.messageBoard.message()");
	}
        
        public boolean read() {
            String r = record.get("read");
            if (r == null) return false;
            if (r.equals("false")) return false;
            return true;
        }
    }

    public String write(entry message) {
        // writes a message and returns key
        try {
            database.put(message.key, message.record);
            return message.key;
        } catch (IOException e) {
            return null;
        }
    }
    
    public entry read(String key) {
        HashMap<String, String> record;
        try {
            record = database.get(key);
        } catch (IOException e) {
            return null;
        }
	    return new entry(key, record);
    }
    
    public void remove(String key) {
        try {
            database.remove(key);
        } catch (IOException e) {
        }
    }
    
    public Iterator<String> keys(String category, boolean up) throws IOException {
	//return database.keys();
        return new catIter(category, up);
    }

    public class catIter implements Iterator<String> {
    
        Iterator<byte[]> allIter = null;
        String nextKey = null;
        String category = "";
        
        public catIter(String category, boolean up) throws IOException {
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
            String next = nextKey;
            findNext();
            return next;
        }
        
        public void remove() {
        }
        
    }
}
