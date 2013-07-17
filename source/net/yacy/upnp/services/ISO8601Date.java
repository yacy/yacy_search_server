/*
 * ============================================================================
 *                 The Apache Software License, Version 1.1
 * ============================================================================
 *
 * Copyright (C) 2002 The Apache Software Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modifica-
 * tion, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of  source code must  retain the above copyright  notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. The end-user documentation included with the redistribution, if any, must
 *    include the following  acknowledgment: "This product includes software
 *    developed by SuperBonBon Industries (http://www.sbbi.net/)."
 *    Alternately, this acknowledgment may appear in the software itself, if
 *    and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "UPNPLib" and "SuperBonBon Industries" must not be
 *    used to endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    info@sbbi.net.
 *
 * 5. Products  derived from this software may not be called
 *    "SuperBonBon Industries", nor may "SBBI" appear in their name,
 *    without prior written permission of SuperBonBon Industries.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS  FOR A PARTICULAR  PURPOSE ARE  DISCLAIMED. IN NO EVENT SHALL THE
 * APACHE SOFTWARE FOUNDATION OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT,INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLU-
 * DING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software  consists of voluntary contributions made by many individuals
 * on behalf of SuperBonBon Industries. For more information on
 * SuperBonBon Industries, please see <http://www.sbbi.net/>.
 */
package net.yacy.upnp.services;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.TimeZone;

/**
 * ISO8601 Date implementation taken from org.w3c package and modified
 * to work with UPNP date types
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class ISO8601Date {

  private static boolean check(StringTokenizer st, String token) throws NumberFormatException {
    try {
      if (st.nextToken().equals(token)) {
        return true;
      }
      throw new NumberFormatException("Missing [" + token + "]");
    } catch (final NoSuchElementException ex) {
      return false;
    }
  }

  private static Calendar getCalendar( String isodate ) throws NumberFormatException {
    // YYYY-MM-DDThh:mm:ss.sTZD or hh:mm:ss.sTZD
    // does it contains a date ?
    boolean isATime = isodate.indexOf( ':' ) != -1;
    boolean isADate = isodate.indexOf( '-' ) != -1 || ( isodate.length() == 4 && !isATime );
    if ( isATime && ! isADate ) {
      if ( !isodate.toUpperCase().startsWith( "T" ) ) {
        isodate = "T" + isodate;
      }
    }
    StringTokenizer st = new StringTokenizer(isodate, "-T:.+Z", true);
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.clear();
    if ( isADate ) {
      // Year
      if (st.hasMoreTokens()) {
        int year = Integer.parseInt(st.nextToken());
        calendar.set(Calendar.YEAR, year);
      } else {
        return calendar;
      }
      // Month
      if (check(st, "-") && (st.hasMoreTokens())) {
        int month = Integer.parseInt(st.nextToken()) - 1;
        calendar.set(Calendar.MONTH, month);
      } else {
        return calendar;
      }
      // Day
      if (check(st, "-") && (st.hasMoreTokens())) {
        int day = Integer.parseInt(st.nextToken());
        calendar.set(Calendar.DAY_OF_MONTH, day);
      } else {
        return calendar;
      }
    }
    // Hour
    if ( ( check(st, "T") ) && ( st.hasMoreTokens() ) ) {
      int hour = Integer.parseInt(st.nextToken());
      calendar.set(Calendar.HOUR_OF_DAY, hour);
    } else {
      calendar.set(Calendar.HOUR_OF_DAY, 0);
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);
      return calendar;
    }
    // Minutes
    if (check(st, ":") && (st.hasMoreTokens())) {
      int minutes = Integer.parseInt(st.nextToken());
      calendar.set(Calendar.MINUTE, minutes);
    } else {
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);
      return calendar;
    }

    //
    // Not mandatory now
    //

    // Secondes
    if (!st.hasMoreTokens()) {
      return calendar;
    }
    String tok = st.nextToken();
    if (tok.equals(":")) { // secondes
      if (st.hasMoreTokens()) {
        int secondes = Integer.parseInt(st.nextToken());
        calendar.set(Calendar.SECOND, secondes);
        if (!st.hasMoreTokens()) {
          return calendar;
        }
        // frac sec
        tok = st.nextToken();
        if (tok.equals(".")) {
          // bug fixed, thx to Martin Bottcher
          String nt = st.nextToken();
          while (nt.length() < 3) {
            nt += "0";
          }
          nt = nt.substring(0, 3); // Cut trailing chars..
          int millisec = Integer.parseInt(nt);
          // int millisec = Integer.parseInt(st.nextToken()) * 10;
          calendar.set(Calendar.MILLISECOND, millisec);
          if (!st.hasMoreTokens()) {
            return calendar;
          }
          tok = st.nextToken();
        } else {
          calendar.set(Calendar.MILLISECOND, 0);
        }
      } else {
        throw new NumberFormatException("No secondes specified");
      }
    } else {
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);
    }
    // Timezone
    if (!tok.equals("Z")) { // UTC
      if (!(tok.equals("+") || tok.equals("-"))) {
        throw new NumberFormatException("only Z, + or - allowed");
      }
      boolean plus = tok.equals("+");
      if (!st.hasMoreTokens()) {
        throw new NumberFormatException("Missing hour field");
      }
      int tzhour = Integer.parseInt(st.nextToken());
      int tzmin = 0;
      if (check(st, ":") && (st.hasMoreTokens())) {
        tzmin = Integer.parseInt(st.nextToken());
      } else {
        throw new NumberFormatException("Missing minute field");
      }
      if (plus) {
        calendar.add(Calendar.HOUR, -tzhour);
        calendar.add(Calendar.MINUTE, -tzmin);
      } else {
        calendar.add(Calendar.HOUR, tzhour);
        calendar.add(Calendar.MINUTE, tzmin);
      }
    }
    return calendar;
  }

  /**
   * Parse the given string in ISO 8601 format and build a Date object.
   * @param isodate the date in ISO 8601 format
   * @return a Date instance
   * @exception InvalidDateException
   *              if the date is not valid
   */
  public static Date parse(String isodate) throws NumberFormatException {
    Calendar calendar = getCalendar(isodate);
    return calendar.getTime();
  }

  private static String twoDigit(int i) {
    if (i >= 0 && i < 10) {
      return "0" + String.valueOf(i);
    }
    return String.valueOf(i);
  }

  /**
   * Generate a ISO 8601 date
   * @param date a Date instance
   * @return a string representing the date in the ISO 8601 format
   */
  public static String getIsoDate(Date date) {
    Calendar calendar = new GregorianCalendar();
    calendar.setTime(date);
    StringBuilder buffer = new StringBuilder();
    buffer.append(calendar.get(Calendar.YEAR));
    buffer.append("-");
    buffer.append(twoDigit(calendar.get(Calendar.MONTH) + 1));
    buffer.append("-");
    buffer.append(twoDigit(calendar.get(Calendar.DAY_OF_MONTH)));
    return buffer.toString();
  }

  /**
   * Generate a ISO 8601 date time without timezone
   * @param date a Date instance
   * @return a string representing the date in the ISO 8601 format
   */
  public static String getIsoDateTime(Date date) {
    Calendar calendar = new GregorianCalendar();
    calendar.setTime(date);
    StringBuilder buffer = new StringBuilder();
    buffer.append(calendar.get(Calendar.YEAR));
    buffer.append("-");
    buffer.append(twoDigit(calendar.get(Calendar.MONTH) + 1));
    buffer.append("-");
    buffer.append(twoDigit(calendar.get(Calendar.DAY_OF_MONTH)));
    buffer.append("T");
    buffer.append(twoDigit(calendar.get(Calendar.HOUR_OF_DAY)));
    buffer.append(":");
    buffer.append(twoDigit(calendar.get(Calendar.MINUTE)));
    buffer.append(":");
    buffer.append(twoDigit(calendar.get(Calendar.SECOND)));
    buffer.append(".");
    buffer.append(twoDigit(calendar.get(Calendar.MILLISECOND) / 10));
    return buffer.toString();
  }

  /**
   * Generate a ISO 8601 date time with timezone
   * @param date a Date instance
   * @return a string representing the date in the ISO 8601 format
   */
  public static String getIsoDateTimeZone(Date date) {
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.setTime(date);
    StringBuilder buffer = new StringBuilder();
    buffer.append(calendar.get(Calendar.YEAR));
    buffer.append("-");
    buffer.append(twoDigit(calendar.get(Calendar.MONTH) + 1));
    buffer.append("-");
    buffer.append(twoDigit(calendar.get(Calendar.DAY_OF_MONTH)));
    buffer.append("T");
    buffer.append(twoDigit(calendar.get(Calendar.HOUR_OF_DAY)));
    buffer.append(":");
    buffer.append(twoDigit(calendar.get(Calendar.MINUTE)));
    buffer.append(":");
    buffer.append(twoDigit(calendar.get(Calendar.SECOND)));
    buffer.append(".");
    buffer.append(twoDigit(calendar.get(Calendar.MILLISECOND) / 10));
    buffer.append("Z");
    return buffer.toString();
  }

  /**
   * Generate a ISO 8601 time
   * @param date a Date instance
   * @return a string representing the date in the ISO 8601 format
   */
  public static String getIsoTime(Date date) {
    Calendar calendar = new GregorianCalendar();
    calendar.setTime(date);
    StringBuilder buffer = new StringBuilder();
    buffer.append(twoDigit(calendar.get(Calendar.HOUR_OF_DAY)));
    buffer.append(":");
    buffer.append(twoDigit(calendar.get(Calendar.MINUTE)));
    buffer.append(":");
    buffer.append(twoDigit(calendar.get(Calendar.SECOND)));
    buffer.append(".");
    buffer.append(twoDigit(calendar.get(Calendar.MILLISECOND) / 10));
    return buffer.toString();
  }

  /**
   * Generate a ISO 8601 time
   * @param date a Date instance
   * @return a string representing the date in the ISO 8601 format
   */
  public static String getIsoTimeZone(Date date) {
    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.setTime(date);
    StringBuilder buffer = new StringBuilder();
    buffer.append(twoDigit(calendar.get(Calendar.HOUR_OF_DAY)));
    buffer.append(":");
    buffer.append(twoDigit(calendar.get(Calendar.MINUTE)));
    buffer.append(":");
    buffer.append(twoDigit(calendar.get(Calendar.SECOND)));
    buffer.append(".");
    buffer.append(twoDigit(calendar.get(Calendar.MILLISECOND) / 10));
    buffer.append("Z");
    return buffer.toString();
  }

}
