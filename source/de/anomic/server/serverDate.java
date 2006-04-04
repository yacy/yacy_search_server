// serverDate.java 
// -------------------------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


// this class is needed to replace the slow java built-in date method by a faster version

package de.anomic.server;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public final class serverDate {
    
    
    // statics
    private final static long secondMillis = 1000;
    private final static long minuteMillis = 60 * secondMillis;
    private final static long hourMillis = 60 * minuteMillis;
    private final static long dayMillis = 24 * hourMillis;
    private final static long normalyearMillis = 365 * dayMillis;
    private final static long leapyearMillis = 366 * dayMillis;
    private final static int january = 31, normalfebruary = 28, leapfebruary = 29, march = 31,
                             april = 30, may = 31, june = 30, july = 31, august = 31,
                             september = 30, october = 31, november = 30, december = 31;
    private final static int[] dimnormal = {january, normalfebruary, march, april, may, june, july, august, september, october, november, december};
    private final static int[] dimleap = {january, leapfebruary, march, april, may, june, july, august, september, october, november, december};
    private final static String[] wkday = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
    //private final static String[] month = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

    // find out time zone and DST offset
    private static Calendar thisCalendar = Calendar.getInstance();
    //private static long zoneOffsetHours = thisCalendar.get(Calendar.ZONE_OFFSET);
    //private static long DSTOffsetHours = thisCalendar.get(Calendar.DST_OFFSET);
    //private static long offsetHours = zoneOffsetHours + DSTOffsetHours; // this must be subtracted from current Date().getTime() to produce a GMT Time

    // pre-calculation of time tables
    private final static long[] dimnormalacc, dimleapacc;
    private static long[] utimeyearsacc;
    static {
        long millis = 0;
        utimeyearsacc = new long[67];
        for (int i = 0; i < 67; i++) {
            utimeyearsacc[i] = millis;
            millis += ((i & 3) == 0) ? leapyearMillis : normalyearMillis;
        }
        millis = 0;
        dimnormalacc = new long[12];
        for (int i = 0; i < 12; i++) {
            dimnormalacc[i] = millis;
            millis += (dayMillis * dimnormal[i]);
        }
        millis = 0;
        dimleapacc = new long[12];
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
        long offsetHours = UTCDiff();
        int om = Math.abs((int) (offsetHours / minuteMillis)) % 60;
        int oh = Math.abs((int) (offsetHours / hourMillis));
        String diff = Integer.toString(om);
        if (diff.length() < 2) diff = "0" + diff;
        diff = Integer.toString(oh) + diff;
        if (diff.length() < 4) diff = "0" + diff;
        if (offsetHours >= 0) {
            return "+" + diff;
        } else {
            return "-" + diff;
        }
    }

    public static long UTCDiff() {
        long zoneOffsetHours = thisCalendar.get(Calendar.ZONE_OFFSET);
        long DSTOffsetHours = thisCalendar.get(Calendar.DST_OFFSET);
        return zoneOffsetHours + DSTOffsetHours;
    }
    
    public static long UTCDiff(String diffString) {
        if (diffString.length() != 5) throw new RuntimeException("UTC String malformed (wrong size):" + diffString);
        boolean ahead = true;
        if (diffString.charAt(0) == '+') ahead = true;
        else if (diffString.charAt(0) == '-') ahead = false;
        else throw new RuntimeException("UTC String malformed (wrong sign):" + diffString);
        long oh = Long.parseLong(diffString.substring(1, 3));
        long om = Long.parseLong(diffString.substring(3));
        return ((ahead) ? (long) 1 : (long) -1) * (oh * hourMillis + om * minuteMillis);
    }
    
    /*
    public static Date UTC0Date() {
        return new Date(UTC0Time());
    }
    */

    public static long correctedUTCTime() {
        return System.currentTimeMillis() - UTCDiff();
    }
    
    public serverDate() {
        this(System.currentTimeMillis());
    }
    
    public serverDate(long utime) {
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
        
    public serverDate(String datestring) throws java.text.ParseException {
        // parse a date string; othervise throw a java.text.ParseException
        if ((datestring.length() == 14) || (datestring.length() == 17)) {
            // parse a ShortString
            try {years = Integer.parseInt(datestring.substring(0, 4)) - 1970;} catch (NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong year", 0);
            }
            if (years < 0) throw new java.text.ParseException("serverDate '" + datestring + "' wrong year", 0);
            try {months = Integer.parseInt(datestring.substring(4, 6)) - 1;} catch (NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong month", 4);
            }
            if ((months < 0) || (months > 11)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong month", 4);
            try {days = Integer.parseInt(datestring.substring(6, 8)) - 1;} catch (NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong day", 6);
            }
            if ((days < 0) || (days > 30)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong day", 6);
            try {hours = Integer.parseInt(datestring.substring(8, 10));} catch (NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong hour", 8);
            }
            if ((hours < 0) || (hours > 23)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong hour", 8);
            try {minutes = Integer.parseInt(datestring.substring(10, 12));} catch (NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong minute", 10);
            }
            if ((minutes < 0) || (minutes > 59)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong minute", 10);
            try {seconds = Integer.parseInt(datestring.substring(12, 14));} catch (NumberFormatException e) {
                throw new java.text.ParseException("serverDate '" + datestring + "' wrong second", 12);
            }
            if ((seconds < 0) || (seconds > 59)) throw new java.text.ParseException("serverDate '" + datestring + "' wrong second", 12);
            if (datestring.length() == 17) {
                try {milliseconds = Integer.parseInt(datestring.substring(14, 17));} catch (NumberFormatException e) {
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
    
    public String toShortString(boolean millis) {
        // returns a "yyyyMMddHHmmssSSS"
        byte[] result = new byte[(millis) ? 17 : 14];
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
    
    // the following is only here to compare the kelondroDate with java-Date:
    private static TimeZone GMTTimeZone = TimeZone.getTimeZone("GMT");
    private static Calendar gregorian = new GregorianCalendar(GMTTimeZone);
    private static SimpleDateFormat testSFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
    private static SimpleDateFormat testLFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    public static String testSDateShortString() {
	return testSFormatter.format(gregorian.getTime());
    }
    
    public static String intervalToString(long millis) {
        try {
            long mins = millis / 60000;
            
            StringBuffer uptime = new StringBuffer();
            
            int uptimeDays  = (int) (Math.floor(mins/1440));
            int uptimeHours = (int) (Math.floor(mins/60)%24);
            int uptimeMins  = (int) mins%60;
            
            uptime.append(uptimeDays)
                  .append(((uptimeDays == 1)?" day ":" days "))
                  .append((uptimeHours < 10)?"0":"")
                  .append(uptimeHours)
                  .append(":")
                  .append((uptimeMins < 10)?"0":"")
                  .append(uptimeMins);            
            
            return uptime.toString();       
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    public static long remainingTime(long start, long due, long minimum) {
        if (due < 0) return -1;
        long r = due + start - System.currentTimeMillis();
        if (r <= 0) return minimum; else return r;
    }
    
    public static void main(String[] args) {
        //System.out.println("kelondroDate is (" + new kelondroDate().toString() + ")");
        System.out.println("offset is " + (UTCDiff()/1000/60/60) + " hours, javaDate is " + new Date() + ", correctedDate is " + new Date(correctedUTCTime()));
        System.out.println("serverDate : " + new serverDate().toShortString(false));
        System.out.println("  javaDate : " + testSDateShortString());
        System.out.println("serverDate : " + new serverDate().toString());
        System.out.println("  JavaDate : " + testLFormatter.format(new Date()));
        System.out.println("serverDate0: " + new serverDate(0).toShortString(false));
        System.out.println("  JavaDate0: " + testSFormatter.format(new Date(0)));
        System.out.println("serverDate0: " + new serverDate(0).toString());
        System.out.println("  JavaDate0: " + testLFormatter.format(new Date(0)));
        // parse test
        try {
            System.out.println("serverDate re-parse short: " + new serverDate(new serverDate().toShortString(false)).toShortString(true));
            System.out.println("serverDate re-parse long : " + new serverDate(new serverDate().toShortString(true)).toShortString(true));
        } catch (java.text.ParseException e) {
            System.out.println("Parse Exception: " + e.getMessage() + ", pos " + e.getErrorOffset());
        }
        //String testresult;
        int cycles = 10000;
        long start;
        
        start = System.currentTimeMillis();
        for (int i = 0; i < cycles; i++) /*testresult =*/ new serverDate().toShortString(false);
        System.out.println("time for " + cycles + " calls to serverDate:" + (System.currentTimeMillis() - start) + " milliseconds");
        
        start = System.currentTimeMillis();
        for (int i = 0; i < cycles; i++) /*testresult =*/ testSDateShortString();
        System.out.println("time for " + cycles + " calls to   javaDate:" + (System.currentTimeMillis() - start) + " milliseconds");
    }    
}
