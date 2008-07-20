// yFormatter.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// (C) 2007 Bjoern 'Fuchs' Krombholz; fox.box@gmail.com
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

package de.anomic.tools;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * This class provides simple Formatters to unify YaCy's output
 * formattings.
 *
 * At the moment yFormatter can be used to format numbers according
 * to the locale set for YaCy.
 */
public final class yFormatter {
    // default formatter
    private static NumberFormat numForm = NumberFormat.getInstance(new Locale("en"));
    
    // generic formatter that can be used when no localized formatting is allowed
    private static final NumberFormat cleanNumForm = 
        new DecimalFormat("####.##", new DecimalFormatSymbols(Locale.ENGLISH));

    static {
        // just initialize defaults on class load
        initDefaults();
    }


    /**
     * @param locale the {@link Locale} to set or <code>null</code> to set the special
     * empty locale to create unformatted numbers
     */
    public static void setLocale(Locale locale) {
        numForm = (locale == null ? cleanNumForm : NumberFormat.getInstance(locale));
        initDefaults();
    }

    /**
     * @param lang an ISO 639 language code which is used to generate a {@link Locale}
     */
    public static void setLocale(String lang) {
        String l = (lang.equalsIgnoreCase("default") ? "en" : lang.toLowerCase());
        
        setLocale(l.equals("none") ? null : new Locale(l));
    }

    private static void initDefaults() {
        numForm.setGroupingUsed(true);          // always group int digits
        numForm.setParseIntegerOnly(false);     // allow int/double/float
        numForm.setMaximumFractionDigits(2);    // 2 decimal digits for float/double
    }

    public static String number(double d, boolean localized) {
        return (localized ? number(d) : cleanNumForm.format(d));
    }
    public static String number(double d) {
        return numForm.format(d);
    }

    public static String number(long l, boolean localized) {
        return (localized ? number(l) : cleanNumForm.format(l));
    }
    public static String number(long l) {
        return numForm.format(l);
    }

    /**
     * Method formats String representation of numbers according to the formatting
     * rules for numbers defined by this class. This method is probably only useful
     * for "numbers" read from property files.
     * @param s string to parse into a number and reformat
     * @return the formatted number as a String or "-" in case of a parsing error
     */
    public static String number(String s) {
        String ret = null;
        try {
            if (s.indexOf('.') == -1) {
                ret = number(Long.parseLong(s));
            } else {
                ret = number(Double.parseDouble(s));
            }
        } catch (NumberFormatException e) { /* empty */ }
        
        return (ret == null ? "-" : ret);
    }
}
