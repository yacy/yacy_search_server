// YMarkDate.java
// (C) 2011 by Stefan Förster, sof@gmx.de, Norderstedt, Germany
// first published 2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2011-03-09 13:50:39 +0100 (Mi, 09 Mrz 2011) $
// $LastChangedRevision: 7574 $
// $LastChangedBy: apfelmaennchen $
//
// LICENSE
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

package net.yacy.data.ymark;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.yacy.cora.date.ISO8601Formatter;
import net.yacy.cora.document.encoding.UTF8;

public class YMarkDate {

	private long date;

	public YMarkDate() {
		this.date = System.currentTimeMillis();
	}

	public YMarkDate(final byte[] date) {
		this.set(date);
	}

	public YMarkDate(final Date date) {
		this.date = date.getTime();
	}

	public long parseISO8601(final String s) throws ParseException {
    	if(s == null || s.length() < 1) {
    		throw new ParseException("parseISO8601 - empty string, nothing to parse", 0);
    	}
    	SimpleDateFormat dateformat;
    	StringBuilder date = new StringBuilder(s);
    	if(s.length()==10)
    		dateformat = new SimpleDateFormat("yyyy-MM-dd");
    	else {
    		dateformat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");
	        if(date.charAt(date.length()-1) == 'Z') {
	        	date.deleteCharAt(date.length()-1);
	        	date.append("GMT-00:00");
	        } else {
	            date.insert(date.length()-6, "GMT");
	        }
    	}
    	this.date = dateformat.parse(date.toString()).getTime();
        return this.date;
    }

    public String toISO8601() {
    	return (this.date == 0) ? YMarkEntry.BOOKMARK.DATE_MODIFIED.deflt() : ISO8601(new Date(this.date));
    }

    public static String ISO8601(final Date date) {
    	return ISO8601Formatter.FORMATTER.format(date);
    }

    public byte[] toBytes() {
		return String.valueOf(this.date).getBytes();
    }

    @Override
    public String toString() {
    	return String.valueOf(this.date);
    }

    public long get() {
    	return this.date;
    }

    public void set(long date) {
    	this.date = date;
    }

    public void set(byte[] date) {
        final String s = UTF8.String(date);
        if(!s.isEmpty()) {
            this.date = Long.parseLong(s);
        } else {
            this.date = 0;
        }
    }
}
