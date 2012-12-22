/**
 *  GenericFormatter
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

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import net.yacy.cora.util.NumberTools;

public class GenericFormatter extends AbstractFormatter implements DateFormatter {

    public static final String PATTERN_SHORT_DAY    = "yyyyMMdd";
    public static final String PATTERN_SHORT_SECOND = "yyyyMMddHHmmss";
    public static final String PATTERN_SHORT_MILSEC = "yyyyMMddHHmmssSSS";
    public static final String PATTERN_RFC1123_SHORT = "EEE, dd MMM yyyy";
    public static final String PATTERN_ANSIC   = "EEE MMM d HH:mm:ss yyyy";
    public static final String PATTERN_SIMPLE  = "yyyy/MM/dd HH:mm:ss";
    
    public static final SimpleDateFormat FORMAT_SHORT_DAY     = new SimpleDateFormat(PATTERN_SHORT_DAY, Locale.US);
    public static final SimpleDateFormat FORMAT_SHORT_SECOND  = new SimpleDateFormat(PATTERN_SHORT_SECOND, Locale.US);
    public static final SimpleDateFormat FORMAT_SHORT_MILSEC  = new SimpleDateFormat(PATTERN_SHORT_MILSEC, Locale.US);
    public static final SimpleDateFormat FORMAT_RFC1123_SHORT = new SimpleDateFormat(PATTERN_RFC1123_SHORT, Locale.US);
    public static final SimpleDateFormat FORMAT_ANSIC         = new SimpleDateFormat(PATTERN_ANSIC, Locale.US);
    public static final SimpleDateFormat FORMAT_SIMPLE        = new SimpleDateFormat(PATTERN_SIMPLE, Locale.US);

    // find out time zone and DST offset
    private static Calendar thisCalendar = Calendar.getInstance();

    static {
        // we want GMT times on the formats as well as they don't support any timezone
        FORMAT_SHORT_DAY.setTimeZone(TZ_GMT);
        FORMAT_SHORT_SECOND.setTimeZone(TZ_GMT);
        FORMAT_SHORT_MILSEC.setTimeZone(TZ_GMT);
    }

    public static final long time_second =  1000L;
    public static final long time_minute = 60000L;
    public static final long time_hour   = 60 * time_minute;
    public static final long time_day    = 24 * time_hour;

    public static final GenericFormatter SHORT_DAY_FORMATTER     = new GenericFormatter(FORMAT_SHORT_DAY, time_minute);
    public static final GenericFormatter SHORT_SECOND_FORMATTER  = new GenericFormatter(FORMAT_SHORT_SECOND, time_second);
    public static final GenericFormatter SHORT_MILSEC_FORMATTER  = new GenericFormatter(FORMAT_SHORT_MILSEC, 1);
    public static final GenericFormatter RFC1123_SHORT_FORMATTER = new GenericFormatter(FORMAT_RFC1123_SHORT, time_minute);
    public static final GenericFormatter ANSIC_FORMATTER         = new GenericFormatter(FORMAT_ANSIC, time_second);
    public static final GenericFormatter SIMPLE_FORMATTER        = new GenericFormatter(FORMAT_SIMPLE, time_second);

    private final SimpleDateFormat dateFormat;
    private final long maxCacheDiff;

    public GenericFormatter(final SimpleDateFormat dateFormat, final long maxCacheDiff) {
        this.dateFormat = (SimpleDateFormat) dateFormat.clone(); // avoid concurrency locking effects
        this.last_time = 0;
        this.last_format = "";
        this.maxCacheDiff = maxCacheDiff;
    }

    /**
     * Note: The short day format doesn't include any timezone information. This method
     * transforms the date into the GMT/UTC timezone. Example: If the local system time is,
     * 2007-12-18 01:15:00 +0200, then the resulting String will be "2007-12-17".
     * In case you need a format with a timezon offset, use {@link #formatShortDay(TimeZone)}
     * @return a String representation of the current system date in GMT using the
     *         short day format, e.g. "20071218".
     */
    @Override
    public String format(final Date date) {
        if (date == null) return "";
        synchronized (this.dateFormat) {
            // calculate the date
            return this.dateFormat.format(date);
        }
    }

    @Override
    public String format() {
        if (Math.abs(System.currentTimeMillis() - this.last_time) < this.maxCacheDiff) return this.last_format;
        // threads that had been waiting here may use the cache now instead of calculating the date again
        final long time = System.currentTimeMillis();

        // if the cache is not fresh, calculate the date
        synchronized (this.dateFormat) {
            if (Math.abs(time - this.last_time) < this.maxCacheDiff) return this.last_format;
            this.last_format = this.dateFormat.format(new Date(time));
        }
        this.last_time = time;
        return this.last_format;
    }

    /**
     * Parse a String representation of a Date in short day format assuming the date
     * is aligned to the GMT/UTC timezone. An example for such a date string is "20071218".
     * @see #formatShortDay()
     * @throws ParseException The exception is thrown if an error occured during while parsing
     * the String.
     */
    @Override
    public Date parse(final String timeString) throws ParseException {
        synchronized (this.dateFormat) {
            return this.dateFormat.parse(timeString);
        }
    }

    /**
     * Like {@link #parseShortSecond(String)} using additional timezone information provided in an
     * offset String, like "+0100" for CET.
     */
    public Date parse(final String timeString, final String UTCOffset) {
        // FIXME: This method returns an incorrect date, check callers!
        // ex: de.anomic.server.serverDate.parseShortSecond("20070101120000", "+0200").toGMTString()
        // => 1 Jan 2007 13:00:00 GMT
        if (timeString == null || timeString.isEmpty()) { return new Date(); }
        if (UTCOffset == null || UTCOffset.isEmpty()) { return new Date(); }
        try {
            synchronized (this.dateFormat) {
                return new Date(this.dateFormat.parse(timeString).getTime() - UTCDiff() + UTCDiff(UTCOffset));
            }
        } catch (final Throwable e) {
            //serverLog.logFinest("parseUniversalDate", e.getMessage() + ", remoteTimeString=[" + remoteTimeString + "]");
            return new Date();
        }
    }

    private static long UTCDiff(final String diffString) {
        if (diffString.length() != 5) throw new IllegalArgumentException("UTC String malformed (wrong size):" + diffString);
        boolean ahead = true;
        if (diffString.length() > 0 && diffString.charAt(0) == '+') ahead = true;
        else if (diffString.length() > 0 && diffString.charAt(0) == '-') ahead = false;
        else throw new IllegalArgumentException("UTC String malformed (wrong sign):" + diffString);
        final long oh = NumberTools.parseLongDecSubstring(diffString, 1, 3);
        final long om = NumberTools.parseLongDecSubstring(diffString, 3);
        return ((ahead) ? (long) 1 : (long) -1) * (oh * AbstractFormatter.hourMillis + om * AbstractFormatter.minuteMillis);
    }

    private static long UTCDiff() {
        // DST_OFFSET is dependent on the time of the Calendar, so it has to be updated
        // to get the correct current offset
        synchronized (thisCalendar) {
            thisCalendar.setTimeInMillis(System.currentTimeMillis());
            final long zoneOffsetHours = thisCalendar.get(Calendar.ZONE_OFFSET);
            final long DSTOffsetHours = thisCalendar.get(Calendar.DST_OFFSET);
            return zoneOffsetHours + DSTOffsetHours;
        }
    }

    private final static DecimalFormat D2 = new DecimalFormat("00");

    public static String UTCDiffString() {
        // we express the UTC Difference in 5 digits:
        // SHHMM
        // S  ::= '+'|'-'
        // HH ::= '00'|'01'|'02'|'03'|'04'|'05'|'06'|'07'|'08'|'09'|'10'|'11'|'12'
        // MM ::= '00'|'15'|'30'|'45'
        // since there are some places on earth where there is a time shift of half an hour
        // we need too show also the minutes of the time shift
        // Examples: http://www.timeanddate.com/library/abbreviations/timezones/
        final long offsetHours = UTCDiff();
        final StringBuilder sb = new StringBuilder(5);
        if (offsetHours < 0) {
            sb.append('-');
        } else {
            sb.append('+');
        }
        sb.append(D2.format(Math.abs((int) (offsetHours / AbstractFormatter.hourMillis))));
        sb.append(D2.format(Math.abs((int) (offsetHours / AbstractFormatter.minuteMillis)) % 60));
        return sb.toString();
    }

    public static long correctedUTCTime() {
        return System.currentTimeMillis() - UTCDiff();
    }

    public static void main(final String[] args) {
        System.out.println(UTCDiffString());
    }
}
