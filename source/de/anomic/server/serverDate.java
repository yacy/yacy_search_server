// serverDate.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// (C) by by Bjoern 'Fuchs' Krombholz; fox.box@gmail.com
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005, 2007
// last major change: 14.03.2005
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

// this class is needed to replace the slow java built-in date method by a faster version

package de.anomic.server;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.TimeZone;

import de.anomic.server.logging.serverLog;

public final class serverDate {
    
    /** minimal date format without time information (fixed width: 8) */
    public static final String PATTERN_SHORT_DAY    = "yyyyMMdd";
    /** minimal date format (fixed width: 14) */
    public static final String PATTERN_SHORT_SECOND = "yyyyMMddHHmmss";
    /** minimal date format including milliseconds (fixed width: 17) */
    public static final String PATTERN_SHORT_MILSEC = "yyyyMMddHHmmssSSS";
    
    /** default HTTP 1.1 header date format pattern */
    public static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";
    /** date pattern used in older HTTP implementations */
    public static final String PATTERN_ANSIC   = "EEE MMM d HH:mm:ss yyyy";
    /** date pattern used in older HTTP implementations */
    public static final String PATTERN_RFC1036 = "EEEE, dd-MMM-yy HH:mm:ss zzz";
    
    /** pattern for a W3C datetime variant of a non-localized ISO8601 date */
    public static final String PATTERN_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    /** predefined GMT TimeZone object */
    private static final TimeZone TZ_GMT = TimeZone.getTimeZone("GMT");
    
    /** predefined non-localized Calendar object for generic GMT dates */ 
    private static final Calendar CAL_GMT = Calendar.getInstance(TZ_GMT, Locale.US);

    /** Date formatter/parser for minimal yyyyMMdd pattern */
    private static final SimpleDateFormat FORMAT_SHORT_DAY    = new SimpleDateFormat(PATTERN_SHORT_DAY);
    /** Date formatter/parser for minimal yyyyMMddHHmmss pattern */
    private static final SimpleDateFormat FORMAT_SHORT_SECOND = new SimpleDateFormat(PATTERN_SHORT_SECOND);
    /** Date formatter/parser for minimal yyyyMMddHHmmssSSS pattern */
    private static final SimpleDateFormat FORMAT_SHORT_MILSEC = new SimpleDateFormat(PATTERN_SHORT_MILSEC);
    
    /** Date formatter/non-sloppy parser for W3C datetime (ISO8601) in GMT/UTC */
    private static final SimpleDateFormat FORMAT_ISO8601      = new SimpleDateFormat(PATTERN_ISO8601);
    
    /** Date formatter/parser for standard compliant HTTP header dates (RFC 1123) */
    private static final SimpleDateFormat FORMAT_RFC1123      = new SimpleDateFormat(PATTERN_RFC1123, Locale.US); 

    /**
     * RFC 2616 requires that HTTP clients are able to parse all 3 different
     * formats. All times MUST be in GMT/UTC, but ...
     */
    public static SimpleDateFormat[] FORMATS_HTTP = new SimpleDateFormat[] {
            // RFC 1123/822 (Standard) "Mon, 12 Nov 2007 10:11:12 GMT"
            FORMAT_RFC1123,
            // RFC 1036/850 (old)      "Monday, 12-Nov-07 10:11:12 GMT"
            new SimpleDateFormat(PATTERN_RFC1036, Locale.US),
            // ANSI C asctime()        "Mon Nov 12 10:11:12 2007"
            new SimpleDateFormat(PATTERN_ANSIC, Locale.US),
    };
    
    /** Initialization of static formats */
    static {
        // 2-digit dates are automatically parsed by SimpleDateFormat,
        // we need to detect the real year by adding 1900 or 2000 to
        // the year value starting with 1970
        CAL_GMT.setTimeInMillis(0);
        
        for (int i = 0; i < serverDate.FORMATS_HTTP.length; i++) {
            final SimpleDateFormat f = serverDate.FORMATS_HTTP[i];
            f.setTimeZone(TZ_GMT);
            f.set2DigitYearStart(CAL_GMT.getTime());
        }
        
        // we want GMT times on the SHORT formats as well as they don't support any timezone
        FORMAT_SHORT_DAY.setTimeZone(TZ_GMT);
        FORMAT_SHORT_SECOND.setTimeZone(TZ_GMT);
        FORMAT_SHORT_MILSEC.setTimeZone(TZ_GMT);
        FORMAT_ISO8601.setTimeZone(TZ_GMT);
    }

    /**
     * Parse a HTTP string representation of a date into a Date instance.
     * @param s The date String to parse.
     * @return The Date instance if successful, <code>null</code> otherwise.
     */
    public static Date parseHTTPDate(String s) {
        s = s.trim();
        if ((s == null) || (s.length() < 9)) return null;
    
        for(int i = 0; i < FORMATS_HTTP.length; i++) {
            try {
                return parse(FORMATS_HTTP[i], s);
            } catch (final ParseException e) {
                // on ParseException try again with next parser
            }
        }
    
        // the method didn't return a Date, so we got an illegal String
        serverLog.logSevere("HTTPC-header", "DATE ERROR (Parse): " + s);
        return null;
    }

    /**
     * Creates a String representation of a Date using the format defined
     * in ISO8601/W3C datetime
     * The result will be in UTC/GMT, e.g. "2007-12-19T10:20:30Z".
     * 
     * @param date The Date instance to transform.
     * @return A fixed width (20 chars) ISO8601 date String.
     */
    public static String formatISO8601(final Date date){
            return format(FORMAT_ISO8601, date);
    }

    /**
     * Parse dates as defined in {@linkplain http://www.w3.org/TR/NOTE-datetime}.
     * This format (also specified in ISO8601) allows different "precisions".
     * The following lower precision versions for the complete date 
     * "2007-12-19T10:20:30.567+0300" are allowed:<br>
     * "2007"<br>
     * "2007-12"<br>
     * "2007-12-19"<br>
     * "2007-12-19T10:20+0300<br>
     * "2007-12-19T10:20:30+0300<br>
     * "2007-12-19T10:20:30.567+0300<br>
     * Additionally a timezone offset of "+0000" can be substituted as "Z".<br>
     * Parsing is done in a fuzzy way. If there is an illegal character somewhere in
     * the String, the date parsed so far will be returned, e.g. the input
     * "2007-12-19FOO" would return a date that represents "2007-12-19".
     * 
     * @param s
     * @return
     * @throws ParseException
     */
    public static Date parseISO8601(final String s) throws ParseException {
        final Calendar cal = Calendar.getInstance(TZ_GMT, Locale.US);
        cal.clear();
        
        // split 2007-12-19T10:20:30.789+0500 into its parts
        // correct: yyyy['-'MM['-'dd['T'HH':'MM[':'ss['.'SSS]]('Z'|ZZZZZ)]]]
        final StringTokenizer t = new StringTokenizer(s, "-T:.Z+", true);
        if (s == null || t.countTokens() == 0)
            throw new ParseException("parseISO8601: Cannot parse '" + s + "'", 0);
        
        try {
            // year
            cal.set(Calendar.YEAR, Integer.parseInt(t.nextToken()));
            // month
            if (t.nextToken().equals("-")) {
                cal.set(Calendar.MONTH, Integer.parseInt(t.nextToken()) - 1);
            } else {
                return cal.getTime();
            }
            // day
            if (t.nextToken().equals("-")) {
                cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(t.nextToken()));
            } else {
                return cal.getTime();
            }
            // The standard says: if there is an hour there has to be a minute and a
            // timezone token, too.
            // hour
            if (t.nextToken().equals("T")) {
                final int hour = Integer.parseInt(t.nextToken());
                // no error, got hours
                int min = 0;
                int sec = 0;
                int msec = 0;
                if (t.nextToken().equals(":")) {
                    min = Integer.parseInt(t.nextToken());
                    // no error, got minutes
                    // need TZ or seconds
                    String token = t.nextToken();
                    if (token.equals(":")) {
                        sec = Integer.parseInt(t.nextToken());
                        // need millisecs or TZ
                        token = t.nextToken();
                        if (token.equals(".")) {
                            msec = Integer.parseInt(t.nextToken());
                            // need TZ
                            token = t.nextToken();
                        }
                    }
                    
                    // check for TZ data
                    int offset;
                    if (token.equals("Z")) {
                        offset = 0;
                    } else {
                        int sign = 0;
                        if (token.equals("+")) {
                            sign = 1;
                        } else if (token.equals("-")) {
                            sign = -1;
                        } else {
                            // no legal TZ offset found
                            return cal.getTime();
                        }
                        offset = sign * Integer.parseInt(t.nextToken()) * 10 * 3600;
                    }
                    cal.set(Calendar.ZONE_OFFSET, offset);
                }
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, min);
                cal.set(Calendar.SECOND, sec);
                cal.set(Calendar.MILLISECOND, msec);
            }
        } catch (final NoSuchElementException e) {
            // ignore this as it is perfectly fine to have non-complete date in this format
        } catch (final Exception e) {
            // catch all Exceptions and return what we parsed so far
            serverLog.logInfo("SERVER", "parseISO8601: DATE ERROR with: '" + s + "' got so far: '" + cal.toString());
        }
        
        // in case we couldn't even parse a year
        if (!cal.isSet(Calendar.YEAR))
            throw new ParseException("parseISO8601: Cannot parse '" + s + "'", 0);
        
        return cal.getTime();
    }

    /**
     * Note: The short day format doesn't include any timezone information. This method
     * transforms the date into the GMT/UTC timezone. Example: If the local system time is,
     * 2007-12-18 01:15:00 +0200, then the resulting String will be "2007-12-17".
     * In case you need a format with a timezon offset, use {@link #formatShortDay(TimeZone)}
     * @return a String representation of the current system date in GMT using the
     *         short day format, e.g. "20071218".
     */
    public static String formatShortDay() {
        return format(FORMAT_SHORT_DAY, new Date());
    }

    /**
     * @see #formatShortDay()
     * @param date the Date to transform
     */
    public static String formatShortDay(final Date date) {
        return format(FORMAT_SHORT_DAY, date);
    }

    /**
     * This should only be used, if you need a short date String that needs to be aligned to
     * a special timezone other than GMT/UTC. Be aware that a receiver won't be able to
     * recreate the original Date without additional timezone information.
     * @see #formatShortDay()
     * @param date the Date to transform
     * @param tz a TimeZone the resulting date String should be aligned to.
     */
    public static String formatShortDay(final Date date, final TimeZone tz) {
        return format(FORMAT_SHORT_DAY, date, tz);
    }

    /**
     * Parse a String representation of a Date in short day format assuming the date
     * is aligned to the GMT/UTC timezone. An example for such a date string is "20071218".
     * @see #formatShortDay()
     * @throws ParseException The exception is thrown if an error occured during while parsing
     * the String.
     */
    public static Date parseShortDay(final String timeString) throws ParseException {
        return parse(FORMAT_SHORT_DAY, timeString);
    }

    /**
     * Returns the current date in short second format which is a fixed width (14 chars)
     * String including the date and the time like "20071218233510". The result is in GMT/UTC.
     * @see #formatShortDay()
     */
    public static String formatShortSecond() {
        return formatShortSecond(new Date());
    }

    /**
     * Identical to {@link #formatShortDay(Date)}, but for short second format.
     */
    public static String formatShortSecond(final Date date) {
        return format(FORMAT_SHORT_SECOND, date);
    }
    
    /**
     * Identical to {@link #formatShortDay(Date, TimeZone)}, but for short second format.
     */
    public static String formatShortSecond(final Date date, final TimeZone tz) {
        return format(FORMAT_SHORT_SECOND, date, tz);
    }
    
    //TODO check the following 2 parse methods for correct use (GMT vs. different timezone)
    /**
     * Like {@link #parseShortDay(String)}, but for the "short second" format which is short date
     * plus a 6 digit day time value, like "20071218233510". The String should be in GMT/UTC to
     * get a correct Date.
     */
    public static Date parseShortSecond(final String timeString) throws ParseException {
            return parse(FORMAT_SHORT_SECOND, timeString);
    }

    /**
     * Like {@link #parseShortSecond(String)} using additional timezone information provided in an
     * offset String, like "+0100" for CET.
     */
    public static Date parseShortSecond(final String remoteTimeString, final String remoteUTCOffset) {
        // FIXME: This method returns an incorrect date, check callers!
        // ex: de.anomic.server.serverDate.parseShortSecond("20070101120000", "+0200").toGMTString()
        // => 1 Jan 2007 13:00:00 GMT
        if (remoteTimeString == null || remoteTimeString.length() == 0) { return new Date(); }
        if (remoteUTCOffset == null || remoteUTCOffset.length() == 0) { return new Date(); }
        try {
            return new Date(parse(FORMAT_SHORT_SECOND, remoteTimeString).getTime() - serverDate.UTCDiff() + serverDate.UTCDiff(remoteUTCOffset));
        } catch (final java.text.ParseException e) {
            serverLog.logFinest("parseUniversalDate", e.getMessage() + ", remoteTimeString=[" + remoteTimeString + "]");
            return new Date();
        } catch (final java.lang.NumberFormatException e) {
            serverLog.logFinest("parseUniversalDate", e.getMessage() + ", remoteTimeString=[" + remoteTimeString + "]");
            return new Date();
        }
    }


    /**
     * Format a time inteval in milliseconds into a String of the form
     * X 'day'['s'] HH':'mm
     */
    public static String formatInterval(final long millis) {
        try {
            final long mins = millis / 60000;
            
            final StringBuffer uptime = new StringBuffer();
            
            final int uptimeDays  = (int) (Math.floor(mins/1440));
            final int uptimeHours = (int) (Math.floor(mins/60)%24);
            final int uptimeMins  = (int) mins%60;
            
            uptime.append(uptimeDays)
                  .append(((uptimeDays == 1)?" day ":" days "))
                  .append((uptimeHours < 10)?"0":"")
                  .append(uptimeHours)
                  .append(":")
                  .append((uptimeMins < 10)?"0":"")
                  .append(uptimeMins);            
            
            return uptime.toString();       
        } catch (final Exception e) {
            return "unknown";
        }
    }
    
    /** called by all public format...(..., TimeZone) methods */
    private static String format(final SimpleDateFormat format, final Date date, final TimeZone tz) {
        final TimeZone bakTZ = format.getTimeZone();
        String result;
        
        synchronized (format) {
            format.setTimeZone(tz == null ? TZ_GMT : tz);
            result = format.format(date);
            format.setTimeZone(bakTZ);
        }
        
        return result;
    }

    /** called by all public format...(...) methods */
    private static String format(final SimpleDateFormat format, final Date date) {
        synchronized (format) {
            return format.format(date);
        }
    }

    /** calles by all public parse...(...) methods */
    private static Date parse(final SimpleDateFormat format, final String dateString) throws ParseException {
        synchronized (format) {
            return format.parse(dateString);
        }
    }

    // statics
    public final static long secondMillis = 1000;
    public final static long minuteMillis = 60 * secondMillis;
    public final static long hourMillis = 60 * minuteMillis;
    public final static long dayMillis = 24 * hourMillis;
    public final static long normalyearMillis = 365 * dayMillis;
    public final static long leapyearMillis = 366 * dayMillis;
    public final static int january = 31, normalfebruary = 28, leapfebruary = 29, march = 31,
                             april = 30, may = 31, june = 30, july = 31, august = 31,
                             september = 30, october = 31, november = 30, december = 31;
    public final static int[] dimnormal = {january, normalfebruary, march, april, may, june, july, august, september, october, november, december};
    public final static int[] dimleap = {january, leapfebruary, march, april, may, june, july, august, september, october, november, december};
    public final static String[] wkday = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
    //private final static String[] month = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

    // find out time zone and DST offset
    private static Calendar thisCalendar = Calendar.getInstance();

    // pre-calculation of time tables
    private final static long[] dimnormalacc = new long[12], dimleapacc = new long[12];
    private final static long[] utimeyearsacc = new long[67];
    static {
        long millis = 0;
        for (int i = 0; i < 67; i++) {
            utimeyearsacc[i] = millis;
            millis += ((i & 3) == 0) ? leapyearMillis : normalyearMillis;
        }
        millis = 0;
        for (int i = 0; i < 12; i++) {
            dimnormalacc[i] = millis;
            millis += (dayMillis * dimnormal[i]);
        }
        millis = 0;
        for (int i = 0; i < 12; i++) {
            dimleapacc[i] = millis;
            millis += (dayMillis * dimleap[i]);
        }
    }
    
    // class variables
    private int milliseconds, seconds, minutes, hours, days, months, years; // years since 1970
    private int dow; // day-of-week
    private long utime;

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
        final int om = Math.abs((int) (offsetHours / minuteMillis)) % 60;
        final int oh = Math.abs((int) (offsetHours / hourMillis));
        String diff = Integer.toString(om);
        if (diff.length() < 2) diff = "0" + diff;
        diff = Integer.toString(oh) + diff;
        if (diff.length() < 4) diff = "0" + diff;
        if (offsetHours < 0) {
            return "-" + diff;
        }
        return "+" + diff;
    }

    public static long UTCDiff() {
        // DST_OFFSET is dependent on the time of the Calendar, so it has to be updated
        // to get the correct current offset
        synchronized(thisCalendar) {
            thisCalendar.setTimeInMillis(System.currentTimeMillis());
            final long zoneOffsetHours = thisCalendar.get(Calendar.ZONE_OFFSET);
            final long DSTOffsetHours = thisCalendar.get(Calendar.DST_OFFSET);
            return zoneOffsetHours + DSTOffsetHours;
        }
    }
    
    public static long UTCDiff(final String diffString) {
        if (diffString.length() != 5) throw new IllegalArgumentException("UTC String malformed (wrong size):" + diffString);
        boolean ahead = true;
        if (diffString.charAt(0) == '+') ahead = true;
        else if (diffString.charAt(0) == '-') ahead = false;
        else throw new IllegalArgumentException("UTC String malformed (wrong sign):" + diffString);
        final long oh = Long.parseLong(diffString.substring(1, 3));
        final long om = Long.parseLong(diffString.substring(3));
        return ((ahead) ? (long) 1 : (long) -1) * (oh * hourMillis + om * minuteMillis);
    }
    

    public static long correctedUTCTime() {
        return System.currentTimeMillis() - UTCDiff();
    }
    
    public serverDate() {
        this(System.currentTimeMillis());
    }
    
    public serverDate(final long utime) {
        // set the time as the difference, measured in milliseconds,
        // between the current time and midnight, January 1, 1970 UTC/GMT
        this.utime = utime;
        dow = (int) (((utime / dayMillis) + 3) % 7);
        years = (int) (utime / normalyearMillis); // a guess
        if (utime < utimeyearsacc[years]) years--; // the correction
        long remain = utime - utimeyearsacc[years];
        months = (int) (remain / (29 * dayMillis)); // a guess
        if (months > 11) months = 11;
        if ((years & 3) == 0) {
            if (remain < dimleapacc[months]) months--; // correction
            remain = remain - dimleapacc[months];           
        } else {
            if (remain < dimnormalacc[months]) months--; // correction
            remain = remain - dimnormalacc[months];
        }
        days = (int) (remain / dayMillis); remain = remain % dayMillis;
        hours = (int) (remain / hourMillis); remain = remain % hourMillis;
        minutes = (int) (remain / minuteMillis); remain = remain % minuteMillis;
        seconds = (int) (remain / secondMillis); remain = remain % secondMillis;
        milliseconds = (int) remain;
    }

    private void calcUTime() {
        this.utime = utimeyearsacc[years] + dimleapacc[months - 1] + dayMillis * (days - 1) +
                     hourMillis * hours + minuteMillis * minutes + secondMillis * seconds + milliseconds;
        this.dow = (int) (((utime / dayMillis) + 3) % 7);
    }
        
    public serverDate(final String datestring) throws java.text.ParseException {
        // parse a date string; othervise throw a java.text.ParseException
        if ((datestring.length() == 14) || (datestring.length() == 17)) {
            // parse a ShortString
            try {years = Integer.parseInt(datestring.substring(0, 4)) - 1970;} catch (final NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong year", 0);
            }
            if (years < 0) throw new java.text.ParseException("serverDate '" + datestring + "' wrong year", 0);
            try {months = Integer.parseInt(datestring.substring(4, 6)) - 1;} catch (final NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong month", 4);
            }
            if ((months < 0) || (months > 11)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong month", 4);
            try {days = Integer.parseInt(datestring.substring(6, 8)) - 1;} catch (final NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong day", 6);
            }
            if ((days < 0) || (days > 30)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong day", 6);
            try {hours = Integer.parseInt(datestring.substring(8, 10));} catch (final NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong hour", 8);
            }
            if ((hours < 0) || (hours > 23)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong hour", 8);
            try {minutes = Integer.parseInt(datestring.substring(10, 12));} catch (final NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong minute", 10);
            }
            if ((minutes < 0) || (minutes > 59)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong minute", 10);
            try {seconds = Integer.parseInt(datestring.substring(12, 14));} catch (final NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong second", 12);
            }
            if ((seconds < 0) || (seconds > 59)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong second", 12);
            if (datestring.length() == 17) {
                try {milliseconds = Integer.parseInt(datestring.substring(14, 17));} catch (final NumberFormatException e) {
                    throw new java.text.ParseException("serverDate '" + datestring + "' wrong millisecond", 14);
                }
            } else {
                milliseconds = 0;
            }
            if ((milliseconds < 0) || (milliseconds > 999)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong millisecond", 14);
            calcUTime();
            return;
        }
        throw new java.text.ParseException("serverDate '" + datestring + "' format unknown", 0);
    }
    
    public String toString() {
        return "utime=" + utime + ", year=" + (years + 1970) +
               ", month=" + (months + 1) + ", day=" + (days + 1) +
               ", hour=" + hours + ", minute=" + minutes +
               ", second=" + seconds + ", millis=" + milliseconds +
               ", day-of-week=" + wkday[dow];
    }
    
    public String toShortString(final boolean millis) {
        // returns a "yyyyMMddHHmmssSSS"
        final byte[] result = new byte[(millis) ? 17 : 14];
        int x = 1970 + years;
        result[ 0] = (byte) (48 + (x / 1000)); x = x % 1000;
        result[ 1] = (byte) (48 + (x / 100)); x = x % 100;
        result[ 2] = (byte) (48 + (x / 10)); x = x % 10;
        result[ 3] = (byte) (48 + x);
        x = months + 1;
        result[ 4] = (byte) (48 + (x / 10));
        result[ 5] = (byte) (48 + (x % 10));
        x = days + 1;
        result[ 6] = (byte) (48 + (x / 10));
        result[ 7] = (byte) (48 + (x % 10));
        result[ 8] = (byte) (48 + (hours / 10));
        result[ 9] = (byte) (48 + (hours % 10));
        result[10] = (byte) (48 + (minutes / 10));
        result[11] = (byte) (48 + (minutes % 10));
        result[12] = (byte) (48 + (seconds / 10));
        result[13] = (byte) (48 + (seconds % 10));
        if (millis) {
            x = milliseconds;
            result[14] = (byte) (48 + (x / 100)); x = x % 100;
            result[15] = (byte) (48 + (x / 10)); x = x % 10;
            result[16] = (byte) (48 + x);
        }
        return new String(result);
    }
    
    public static long remainingTime(final long start, final long due, final long minimum) {
        if (due < 0) return -1;
        final long r = due + start - System.currentTimeMillis();
        if (r <= 0) return minimum;
        return r;
    }
    
    public static void main(final String[] args) {
        //System.out.println("kelondroDate is (" + new kelondroDate().toString() + ")");
        System.out.println("offset is " + (UTCDiff()/1000/60/60) + " hours, javaDate is " + new Date() + ", correctedDate is " + new Date(correctedUTCTime()));
        System.out.println("serverDate : " + new serverDate().toShortString(false));
        System.out.println("  javaDate : " + formatShortSecond());
        System.out.println("serverDate : " + new serverDate().toString());
        System.out.println("  JavaDate : " + DateFormat.getDateInstance().format(new Date()));
        System.out.println("serverDate0: " + new serverDate(0).toShortString(false));
        System.out.println("  JavaDate0: " + format(FORMAT_SHORT_SECOND, new Date(0)));
        System.out.println("serverDate0: " + new serverDate(0).toString());
        System.out.println("  JavaDate0: " + DateFormat.getDateInstance().format(new Date(0)));
        // parse test
        try {
            System.out.println("serverDate re-parse short: " + new serverDate(new serverDate().toShortString(false)).toShortString(true));
            System.out.println("serverDate re-parse long : " + new serverDate(new serverDate().toShortString(true)).toShortString(true));
        } catch (final java.text.ParseException e) {
            System.out.println("Parse Exception: " + e.getMessage() + ", pos " + e.getErrorOffset());
        }
        //String testresult;
        final int cycles = 10000;
        long start;
        
        final String[] testresult = new String[10000];
        start = System.currentTimeMillis();
        for (int i = 0; i < cycles; i++) testresult[i] = new serverDate().toShortString(false);
        System.out.println("time for " + cycles + " calls to serverDate:" + (System.currentTimeMillis() - start) + " milliseconds");
        
        start = System.currentTimeMillis();
        for (int i = 0; i < cycles; i++) testresult[i] = format(FORMAT_SHORT_SECOND, new Date());
        System.out.println("time for " + cycles + " calls to   javaDate:" + (System.currentTimeMillis() - start) + " milliseconds");
    }    
}
