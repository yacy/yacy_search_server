/**
 *  GenericFormatter
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

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import net.yacy.cora.util.NumberTools;

public class GenericFormatter extends AbstractFormatter implements DateFormatter {

    public static final String PATTERN_SHORT_DAY    = "yyyyMMdd";
    public static final String PATTERN_SHORT_MINUTE = "yyyyMMddHHmm";
    public static final String PATTERN_SHORT_SECOND = "yyyyMMddHHmmss";
    public static final String PATTERN_SHORT_MILSEC = "yyyyMMddHHmmssSSS";
    public static final String PATTERN_RFC1123_SHORT = "EEE, dd MMM yyyy";
    public static final String PATTERN_ANSIC   = "EEE MMM d HH:mm:ss yyyy";
    public static final String PATTERN_SIMPLE  = "yyyy/MM/dd HH:mm:ss";

	/**
	 * A regular expression matching the PATTERN_SIMPLE pattern (does not control
	 * last day of month (30/31 or 28/29 for february). Can be used as a HTML5 input
	 * field validation pattern
	 */
	public static final String PATTERN_SIMPLE_REGEX = "[0-9]{4}/(0[1-9]|1[012])/(0[1-9]|1[0-9]|2[0-9]|3[01]) (0[0-9]|1[0-9]|2[0-3])(:[0-5][0-9]){2}";

	/**
	 * A thread-safe date formatter using the
	 * {@link GenericFormatter#PATTERN_SHORT_DAY} pattern with the US locale on the
	 * UTC time zone.
	 */
	public static final DateTimeFormatter FORMAT_SHORT_DAY = DateTimeFormatter
			.ofPattern(PATTERN_SHORT_DAY.replace("yyyy", "uuuu"), Locale.US).withZone(ZoneOffset.UTC);

	/**
	 * A thread-safe date formatter using the
	 * {@link GenericFormatter#PATTERN_SHORT_MINUTE} pattern with the US locale on
	 * the system time zone.
	 */
	public static final DateTimeFormatter FORMAT_SHORT_MINUTE = DateTimeFormatter
			.ofPattern(PATTERN_SHORT_MINUTE.replace("yyyy", "uuuu"), Locale.US).withZone(ZoneId.systemDefault());


	/**
	 * A thread-safe date formatter using the
	 * {@link GenericFormatter#PATTERN_SHORT_SECOND} pattern with the US locale on
	 * the UTC time zone.
	 */
	public static final DateTimeFormatter FORMAT_SHORT_SECOND = DateTimeFormatter
			.ofPattern(PATTERN_SHORT_SECOND.replace("yyyy", "uuuu"), Locale.US).withZone(ZoneOffset.UTC);

	/**
	 * A thread-safe date formatter using the
	 * {@link GenericFormatter#PATTERN_SHORT_MILSEC} pattern with the US locale on
	 * the UTC time zone.
	 */
	public static final DateTimeFormatter FORMAT_SHORT_MILSEC = new DateTimeFormatterBuilder()
			.appendPattern(PATTERN_SHORT_MILSEC.replace("yyyy", "uuuu").replaceAll("SSS", ""))
			.appendValue(ChronoField.MILLI_OF_SECOND, 3).toFormatter().withLocale(Locale.US)
			.withZone(ZoneOffset.UTC);/* we can not use here the 'SSS' pattern for milliseconds on JDK 8 (see https://bugs.openjdk.java.net/browse/JDK-8031085) */

	/**
	 * A thread-safe date formatter using the
	 * {@link GenericFormatter#PATTERN_RFC1123_SHORT} pattern with the US locale on
	 * the system time zone.
	 */
	public static final DateTimeFormatter FORMAT_RFC1123_SHORT = DateTimeFormatter
			.ofPattern(PATTERN_RFC1123_SHORT.replace("yyyy", "uuuu"), Locale.US).withZone(ZoneId.systemDefault());

	/**
	 * A thread-safe date formatter using the {@link GenericFormatter#PATTERN_ANSIC}
	 * pattern with the US locale on the system time zone.
	 */
	public static final DateTimeFormatter FORMAT_ANSIC = DateTimeFormatter.ofPattern(PATTERN_ANSIC.replace("yyyy", "uuuu"), Locale.US)
			.withZone(ZoneId.systemDefault());

	/**
	 * A thread-safe date formatter using the
	 * {@link GenericFormatter#PATTERN_SIMPLE} pattern (adapted for the DateTimeFormatter class) with the US locale on the
	 * system time zone.
	 */
	public static final DateTimeFormatter FORMAT_SIMPLE = DateTimeFormatter.ofPattern(PATTERN_SIMPLE.replace("yyyy", "uuuu"), Locale.US)
			.withZone(ZoneId.systemDefault());

	/**
	 * @return a new SimpleDateFormat instance using the
	 *         {@link GenericFormatter#PATTERN_SHORT_DAY} pattern with the US
	 *         locale.
	 */
	public static SimpleDateFormat newShortDayFormat() {
		final SimpleDateFormat dateFormat = new SimpleDateFormat(GenericFormatter.PATTERN_SHORT_DAY, Locale.US);

		// we want GMT times on the formats as well as they don't support any timezone
		dateFormat.setTimeZone(UTCtimeZone);

		return dateFormat;
	}

	/**
	 * @return a new SimpleDateFormat instance using the
	 *         {@link GenericFormatter#PATTERN_SHORT_MINUTE} pattern with the US
	 *         locale.
	 */
	public static SimpleDateFormat newShortMinuteFormat() {
		return new SimpleDateFormat(GenericFormatter.PATTERN_SHORT_MINUTE, Locale.US);
	}

	/**
	 * @return a new SimpleDateFormat instance using the
	 *         {@link GenericFormatter#PATTERN_SHORT_SECOND} pattern with the US
	 *         locale.
	 */
	public static SimpleDateFormat newShortSecondFormat() {
		final SimpleDateFormat dateFormat = new SimpleDateFormat(GenericFormatter.PATTERN_SHORT_SECOND, Locale.US);

		// we want GMT times on the formats as well as they don't support any timezone
		dateFormat.setTimeZone(UTCtimeZone);

		return dateFormat;
	}

	/**
	 * @return a new SimpleDateFormat instance using the
	 *         {@link GenericFormatter#PATTERN_SHORT_MILSEC} pattern with the US
	 *         locale.
	 */
	public static SimpleDateFormat newShortMilsecFormat() {
		final SimpleDateFormat dateFormat = new SimpleDateFormat(GenericFormatter.PATTERN_SHORT_MILSEC, Locale.US);

		// we want GMT times on the formats as well as they don't support any timezone
		dateFormat.setTimeZone(UTCtimeZone);

		return dateFormat;
	}

	/**
	 * @return a new SimpleDateFormat instance using the
	 *         {@link GenericFormatter#PATTERN_RFC1123_SHORT} pattern with the US
	 *         locale.
	 */
	public static SimpleDateFormat newRfc1123ShortFormat() {
		return new SimpleDateFormat(GenericFormatter.PATTERN_RFC1123_SHORT, Locale.US);
	}

	/**
	 * @return a new SimpleDateFormat instance using the
	 *         {@link GenericFormatter#PATTERN_ANSIC} pattern with the US locale.
	 */
	public static SimpleDateFormat newAnsicFormat() {
		return new SimpleDateFormat(GenericFormatter.PATTERN_ANSIC, Locale.US);
	}

	/**
	 * @return a new SimpleDateFormat instance using the
	 *         {@link GenericFormatter#PATTERN_SIMPLE} pattern with the US locale.
	 */
	public static SimpleDateFormat newSimpleDateFormat() {
		return new SimpleDateFormat(GenericFormatter.PATTERN_SIMPLE, Locale.US);
	}

    public static final long time_second =  1000L;
    public static final long time_minute = 60000L;
    public static final long time_hour   = 60 * time_minute;
    public static final long time_day    = 24 * time_hour;

    public static final GenericFormatter SHORT_DAY_FORMATTER     = new GenericFormatter(newShortDayFormat(), time_minute);
    public static final GenericFormatter SHORT_MINUTE_FORMATTER  = new GenericFormatter(newShortMinuteFormat(), time_second);
    public static final GenericFormatter SHORT_SECOND_FORMATTER  = new GenericFormatter(newShortSecondFormat(), time_second);
    public static final GenericFormatter SHORT_MILSEC_FORMATTER  = new GenericFormatter(newShortMilsecFormat(), 1);
    public static final GenericFormatter RFC1123_SHORT_FORMATTER = new GenericFormatter(newRfc1123ShortFormat(), time_minute);
    public static final GenericFormatter ANSIC_FORMATTER         = new GenericFormatter(newAnsicFormat(), time_second);
    public static final GenericFormatter SIMPLE_FORMATTER        = new GenericFormatter(newSimpleDateFormat(), time_second);

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
     * In case you need a format with a timezone offset, use {@link #formatShortDay(TimeZone)}
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
    public Calendar parse(final String timeString, final int timezoneOffset) throws ParseException {
        synchronized (this.dateFormat) {
            final Calendar cal = Calendar.getInstance(UTCtimeZone);
            cal.setTime(this.dateFormat.parse(timeString));
            cal.add(Calendar.MINUTE, timezoneOffset); // add a correction; i.e. for UTC+1 -60 minutes is added to patch a time given in UTC+1 to the actual time at UTC
            return cal;
        }
    }

    /**
     * Like {@link #parseShortSecond(String)} using additional timezone information provided in an
     * offset String, like "+0100" for CET.
     * @throws ParseException
     */
    public Calendar parse(final String timeString, final String UTCOffset) throws ParseException {
        if (timeString == null || timeString.isEmpty()) { return Calendar.getInstance(UTCtimeZone); }
        if (UTCOffset == null || UTCOffset.isEmpty()) { return Calendar.getInstance(UTCtimeZone); }
        return parse(timeString, UTCDiff(UTCOffset)); // offset expected in min
    }

    /**
     * Calculates the time offset in minutes given as timezoneoffsetstring (diffString)
     * e.g. "+0300"  returns 180
     *
     * @param diffString with fixed timezone format
     * @return parsed timezone string in minutes
     */
    private static int UTCDiff(final String diffString) {
        if (diffString.length() != 5) throw new IllegalArgumentException("UTC String malformed (wrong size):" + diffString);
        boolean ahead = true;
        if (diffString.length() > 0 && diffString.charAt(0) == '+') ahead = true;
        else if (diffString.length() > 0 && diffString.charAt(0) == '-') ahead = false;
        else throw new IllegalArgumentException("UTC String malformed (wrong sign):" + diffString);
        final int oh = NumberTools.parseIntDecSubstring(diffString, 1, 3);
        final int om = NumberTools.parseIntDecSubstring(diffString, 3);
        return ((ahead) ? 1 : -1) * (oh * 60 + om);
    }

    /**
     * get the difference of this servers time zone to UTC/GMT in milliseconds
     * @return
     */
    private static long UTCDiff() {
        // DST_OFFSET is dependent on the time of the Calendar, so it has to be updated
        // to get the correct current offset
        synchronized (testCalendar) {
            testCalendar.setTimeInMillis(System.currentTimeMillis());
            final long zoneOffsetHours = testCalendar.get(Calendar.ZONE_OFFSET);
            final long DSTOffsetHours = testCalendar.get(Calendar.DST_OFFSET);
            return zoneOffsetHours + DSTOffsetHours;
        }
    }

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

    private final static DecimalFormat D2 = new DecimalFormat("00");

	/**
	 * Safely format the given time value using the given formatter. Fallback to
	 * ISO-8601 representation or to raw time value without exception when the
	 * format can not be applied.
	 *
	 * @param time
	 *            a time value as millisecnods from Epoch (1970-01-01T00:00:00Z)
	 * @param formatter
	 *            the formatter to use
	 * @return a String representation of the time value
	 */
	public static String formatSafely(final long time, final DateTimeFormatter formatter) {
		String res;
		try {
			res = formatSafely(Instant.ofEpochMilli(time), formatter);
		} catch (final DateTimeException e) {
			/*
			 * Can occur on Instant.ofEpochMilli when the time value is greater than
			 * Instant.MAX.toEpochMilli() or lower than Instant.MIN.toEpochMilli()
			 */
			res = String.valueOf(time);
		}
		return res;
	}

	/**
	 * Safely format the given instant using the given formatter. Fallback to
	 * ISO-8601 representation without exception when the format can not be applied.
	 *
	 * @param instant
	 *            the instant to format
	 * @param formatter
	 *            the formatter to use
	 * @return a String representation of the time value
	 */
	public static String formatSafely(final Instant instant, final DateTimeFormatter formatter) {
		String res;
		if (instant == null) {
			res = "";
		} else {
			try {
				if (formatter != null) {
					res = formatter.format(instant);
				} else {
					res = instant.toString();
				}
			} catch (final DateTimeException e) {
				res = instant.toString();
			}
		}
		return res;
	}

    public static void main(final String[] args) {
        System.out.println(UTCDiffString());
    }
}
