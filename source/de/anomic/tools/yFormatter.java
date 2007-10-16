// yFormatter.java 
// -----------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
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
    private static NumberFormat numForm = NumberFormat.getInstance(new Locale("en"));
    static {
        initDefaults();
    }


    /**
     * @param locale the locale to set
     */
    public static void setLocale(Locale locale) {
        numForm = NumberFormat.getInstance(locale);
    }
    
    public static void setLocale(String lang) {
        String l = (lang.equalsIgnoreCase("default") ? "en" : lang.toLowerCase());
        if (l.equals("none")) {
            // TODO fix the following without breaking yacystats and similar.
            // special format for backwards compatibility in .xml files
            // #.###.## (dots for grouping + decimal separator)
            DecimalFormatSymbols dfs = new DecimalFormatSymbols(Locale.ENGLISH);
            dfs.setGroupingSeparator('.');
            numForm = new DecimalFormat("#,###.##", dfs);
        } else {
            setLocale(new Locale(l));
            initDefaults();
        }
    }

    private static void initDefaults() {
        numForm.setGroupingUsed(true);          // always group int digits
        numForm.setParseIntegerOnly(false);     // allow int/double/float
        numForm.setMaximumFractionDigits(2);    // 2 decimal digits for float/double
    }

    public static String number(double d) {
        return numForm.format(d);
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
