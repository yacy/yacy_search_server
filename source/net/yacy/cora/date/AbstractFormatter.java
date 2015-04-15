/**
 *  AbstractFormatter
 *  Copyright 2011 by Michael Peter Christen
 *  First released 2.1.2011 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.date;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public abstract class AbstractFormatter implements DateFormatter {

    public final static Calendar testCalendar = Calendar.getInstance(); // a calendar in the current time zone of the server
    public final static Calendar UTCCalendar = Calendar.getInstance();
    public final static TimeZone UTCtimeZone = TimeZone.getTimeZone("UTC");
    static {
        UTCCalendar.setTimeZone(UTCtimeZone);
    }
    
    // statics
    public final static long secondMillis = 1000;
    public final static long minuteMillis = 60 * secondMillis;
    public final static long hourMillis = 60 * minuteMillis;
    public final static long dayMillis = 24 * hourMillis;
    public final static long monthAverageMillis = 30 * dayMillis;
    public final static long normalyearMillis = 365 * dayMillis;
    public final static long leapyearMillis = 366 * dayMillis;

    protected long last_time;
    protected String last_format;
    
    @Override
    public abstract Calendar parse(String s, int timezoneOffset) throws ParseException;
    @Override
    public abstract String format(final Date date);
    @Override
    public abstract String format();
}
