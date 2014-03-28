/**
 *  ISO8601
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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class ISO8601Formatter extends AbstractFormatter implements DateFormatter {

    /** pattern for a W3C datetime variant of a non-localized ISO8601 date */
    private static final String PATTERN_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    
    /** Date formatter/non-sloppy parser for W3C datetime (ISO8601) in GMT/UTC */
    private static final SimpleDateFormat FORMAT_ISO8601 = new SimpleDateFormat(PATTERN_ISO8601, Locale.US);
    
    static {
        FORMAT_ISO8601.setTimeZone(TZ_GMT);
    }
    
    public static final ISO8601Formatter FORMATTER = new ISO8601Formatter();

    public ISO8601Formatter() {
        last_time = 0;
        last_format = "";
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
    @Override
    public Date parse(String s) throws ParseException {
        // do some lazy checks here
        s = s.trim();
        while (!s.isEmpty() && s.endsWith("?")) s = s.substring(0, s.length() - 1); // sometimes used if write is not sure about date
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        while (!s.isEmpty() && (s.charAt(0) > '9' || s.charAt(0) < '0')) s = s.substring(1);
        if (s.endsWith("--")) s = s.substring(0, s.length() - 2) + "00";
        int p = s.indexOf(';'); if (p >= 0) s = s.substring(0, p); // a semicolon may be used to separate two dates from each other; then we take the first
        p = s.indexOf(','); if (p >= 0) s = s.substring(0, p); // a comma may be used to separate two dates from each other; then we take the first
        while (!s.isEmpty() && s.endsWith("?")) s = s.substring(0, s.length() - 1); // sometimes used if write is not sure about date
        
        // no go for exact parsing
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
            // The standard says:
            // if there is an hour there has to be a minute and a timezone token, too.
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
            //serverLog.logInfo("SERVER", "parseISO8601: DATE ERROR with: '" + s + "' got so far: '" + cal.toString());
        }
        
        // in case we couldn't even parse a year
        if (!cal.isSet(Calendar.YEAR))
            throw new ParseException("parseISO8601: Cannot parse '" + s + "'", 0);
        Date d = cal.getTime();
        return d;
    }


    /**
     * Creates a String representation of a Date using the format defined
     * in ISO8601/W3C datetime
     * The result will be in UTC/GMT, e.g. "2007-12-19T10:20:30Z".
     * 
     * @param date The Date instance to transform.
     * @return A fixed width (20 chars) ISO8601 date String.
     */
    @Override
    public final String format(final Date date) {
        if (date == null) return "";
        if (Math.abs(date.getTime() - last_time) < 1000) return last_format;
        synchronized (FORMAT_ISO8601) {
            last_format = FORMAT_ISO8601.format(date);
            last_time = date.getTime();
        }
        return last_format;
    }
    @Override
    public final String format() {
        long time = System.currentTimeMillis();
        if (Math.abs(time - last_time) < 1000) return last_format;
        synchronized (FORMAT_ISO8601) {
            last_format = FORMAT_ISO8601.format(new Date(time));
            last_time = time;
        }
        return last_format;
    }
    
}
