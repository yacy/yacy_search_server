// httpDate.java
// ------------------------------
// part of YaCy
// (C) by Bjoern 'Fuchs' Krombholz; fox.box@gmail.com
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005, 2006
// 
// This Class was written by Martin Thelian
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

package de.anomic.http;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import de.anomic.server.logging.serverLog;

/**
 * Helper class for parsing HTTP Dates according to RFC 2616 *
 */
public final class httpDate {
    
    public static String PAT_DATE_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static String PAT_DATE_RFC1036 = "EEEE, dd-MMM-yy HH:mm:ss zzz";
    public static String PAT_DATE_ANSI    = "EEE MMM d HH:mm:ss yyyy";
        
    /**
     * RFC 2616 requires that HTTP clients are able to parse all 3 different
     * formats. All times MUST be in GMT/UTC, but ...
     */
    public static SimpleDateFormat[] DATE_PARSERS = new SimpleDateFormat[] {
            // RFC 1123/822 (Standard) "Mon, 12 Nov 2007 10:11:12 GMT"
            new SimpleDateFormat(PAT_DATE_RFC1123, Locale.US),
            // RFC 1036/850 (old)      "Monday, 12-Nov-07 10:11:12 GMT"
            new SimpleDateFormat(PAT_DATE_RFC1036, Locale.US),
            // ANSI C asctime()        "Mon Nov 12 10:11:12 2007"
            new SimpleDateFormat(PAT_DATE_ANSI, Locale.US),
    };
    
    static {
        // 2-digit dates are automatically parsed by SimpleDateFormat,
        // we need to detect the real year by adding 1900 or 2000 to
        // the year value starting with 1990 (before there was no WWW)
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        // 01 Jan 1990 00:00:00
        c.set(1990, 1, 1, 0, 0, 0);
        
        for (int i = 0; i < DATE_PARSERS.length; i++) {
            SimpleDateFormat f = DATE_PARSERS[i];
            // is this necessary?
            f.setTimeZone(TimeZone.getTimeZone("GMT"));
            f.set2DigitYearStart(c.getTime());
        }
    }
    
    private httpDate() {};

    /**
     * Parse a HTTP string representation of a date into a Date instance.
     * @param s The date String to parse.
     * @return The Date instance if successful, <code>null</code> otherwise.
     */
    public static Date parseHTTPDate(String s) {
        try {
            return httpDate.parseHTTPDate(s, true);
        } catch (ParseException e) {
            serverLog.logSevere("HTTPC-header", "DATE ERROR (Parse): " + s);
            return null;
        } catch (java.lang.NumberFormatException e) {
            serverLog.logSevere("HTTPC-header", "DATE ERROR (NumberFormat): " + s);
            return null;
        }
    }

    /**
     * Parse a HTTP string representation of a date into a Date instance.
     * @param s The date String to parse.
     * @param ignoreTimezone parse the timezone? Currently ignored, always parsed.
     * @return The Date instance if successful, <code>null</code> otherwise.
     * @throws ParseException Thrown, when a parsing problem occured (date String had no leagal format)
     * @throws NumberFormatException 
     */
    public static Date parseHTTPDate(String s, boolean /*unused*/ ignoreTimezone) throws ParseException {
        
        if ((s == null) || (s.length() < 9)) return null;
        s = s.trim();

        //Why was this here?
        //if (s.indexOf("Mrz") > 0) s = s.replaceAll("Mrz", "March");
        
        ParseException pe = null;
        for(int i = 0; i < DATE_PARSERS.length; i++) {
            try {
                // if parse() throws an Exception we try the next pattern
                return DATE_PARSERS[i].parse(s);
            } catch (ParseException e) {
                // we re-throw the last Exception when parsing was not possible
                pe = e;
            }
        }

        // no match
        throw pe;
    }
}
