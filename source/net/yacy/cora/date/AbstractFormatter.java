/**
 *  AbstractFormatter
 *  Copyright 2011 by Michael Peter Christen
 *  First released 2.1.2011 at https://yacy.net
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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

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

    private static final HashMap<Pattern, SimpleDateFormat> DATE_FORMAT_REGEXPS = new HashMap<>() {
        private static final long serialVersionUID = 1321140786174228717L;
    {
        put(Pattern.compile("^\\d{8}$"), new SimpleDateFormat("yyyyMMdd", Locale.US));
        put(Pattern.compile("^\\d{1,2}-\\d{1,2}-\\d{4}$"), new SimpleDateFormat("dd-MM-yyyy", Locale.US));
        put(Pattern.compile("^\\d{4}-\\d{1,2}-\\d{1,2}$"), new SimpleDateFormat("yyyy-MM-dd", Locale.US));
        put(Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{4}$"), new SimpleDateFormat("MM/dd/yyyy", Locale.US));
        put(Pattern.compile("^\\d{4}/\\d{1,2}/\\d{1,2}$"), new SimpleDateFormat("yyyy/MM/dd", Locale.US));
        put(Pattern.compile("^\\d{1,2}\\s[a-z]{3}\\s\\d{4}$"), new SimpleDateFormat("dd MMM yyyy", Locale.US));
        put(Pattern.compile("^\\d{1,2}\\s[a-z]{4,}\\s\\d{4}$"), new SimpleDateFormat("dd MMMM yyyy", Locale.US));
        put(Pattern.compile("^\\d{12}$"), new SimpleDateFormat("yyyyMMddHHmm", Locale.US));
        put(Pattern.compile("^\\d{8}\\s\\d{4}$"), new SimpleDateFormat("yyyyMMdd HHmm", Locale.US));
        put(Pattern.compile("^\\d{1,2}-\\d{1,2}-\\d{4}\\s\\d{1,2}:\\d{2}$"), new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US));
        put(Pattern.compile("^\\d{4}-\\d{1,2}-\\d{1,2}\\s\\d{1,2}:\\d{2}$"), new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US));
        put(Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{4}\\s\\d{1,2}:\\d{2}$"), new SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.US));
        put(Pattern.compile("^\\d{4}/\\d{1,2}/\\d{1,2}\\s\\d{1,2}:\\d{2}$"), new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US));
        put(Pattern.compile("^\\d{1,2}\\s[a-z]{3}\\s\\d{4}\\s\\d{1,2}:\\d{2}$"), new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US));
        put(Pattern.compile("^\\d{1,2}\\s[a-z]{4,}\\s\\d{4}\\s\\d{1,2}:\\d{2}$"), new SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.US));
        put(Pattern.compile("^\\d{14}$"), new SimpleDateFormat("yyyyMMddHHmmss", Locale.US));
        put(Pattern.compile("^\\d{8}\\s\\d{6}$"), new SimpleDateFormat("yyyyMMdd HHmmss", Locale.US));
        put(Pattern.compile("^\\d{1,2}-\\d{1,2}-\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$"), new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US));
        put(Pattern.compile("^\\d{4}-\\d{1,2}-\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}$"), new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US));
        put(Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$"), new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US));
        put(Pattern.compile("^\\d{4}/\\d{1,2}/\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}$"), new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US));
        put(Pattern.compile("^\\d{1,2}\\s[a-z]{3}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$"), new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.US));
        put(Pattern.compile("^\\d{1,2}\\s[a-z]{4,}\\s\\d{4}\\s\\d{1,2}:\\d{2}:\\d{2}$"), new SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale.US));
        put(Pattern.compile("^[a-z]{3}\\s[a-z]{3}\\s\\d{1,2}\\s\\d{1,2}:\\d{2}:\\d{2}\\s[a-z]{4,}\\s\\d{4}$"), new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US));
    }};

    @Override
    public Date parse(final String s) {
        return parseAny(s);
    }

    public static Date parseAny(final String s) {
        for (final Map.Entry<Pattern, SimpleDateFormat> ps: DATE_FORMAT_REGEXPS.entrySet()) {
            if (ps.getKey().matcher(s.toLowerCase()).matches()) {
                try {
                    return ps.getValue().parse(s);
                } catch (final ParseException e) {
                }
            }
        }
        return null; // Unknown format.
    }

}
