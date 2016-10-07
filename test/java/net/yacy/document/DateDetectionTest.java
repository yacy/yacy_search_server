package net.yacy.document;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimeZone;
import org.apache.solr.util.DateFormatUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class DateDetectionTest {

    /**
     * Test of parseLine method, of class DateDetection.
     */
    @Test
    public void testParseLine() {

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.clear(); // get rid of sec, millisec
        cal.set(2016, Calendar.JANUARY, 1); // set the target date

        // test some date input representations
        Set<String> testtext = new LinkedHashSet();
        testtext.add("2016-01-01");
        testtext.add("2016/01/01");
        testtext.add("1.1.2016");
        testtext.add("1. Januar 2016");
        testtext.add("2016, January 1.");

        testtext.add("beginning text 1.1.2016");
        testtext.add("line break\n1.1.2016");
        for (String text : testtext) {
            Date d = DateDetection.parseLine(text, 0);

            // this formatter is used to create Solr search queries, use it to compare equality
            String cs = DateFormatUtil.formatExternal(cal.getTime());
            String ds = DateFormatUtil.formatExternal(d);

            assertEquals(text, cs, ds);
        }

        // test holidays
        cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        int currentyear = cal.get(Calendar.YEAR); // instance is init to NOW
        cal.clear(); // get rid of sec, millisec
        cal.set(currentyear, Calendar.JANUARY, 1); // use Calendar const (month is 0 based)

        testtext.add("Neujahr");
        testtext.add("New Year's Day");

        for (String text : testtext) {
            Date d = DateDetection.parseLine(text, 0);

            // this formatter is used to create Solr search queries, use it to compare equality
            String cs = DateFormatUtil.formatExternal(cal.getTime());
            String ds = DateFormatUtil.formatExternal(d);

            assertEquals(text, cs, ds);
        }

        // test relative dates
        cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        currentyear = cal.get(Calendar.YEAR); // instance is init to NOW
        int currentmonth = cal.get(Calendar.MONTH);
        int currentday = cal.get(Calendar.DAY_OF_MONTH);
        cal.clear(); // get rid of sec, millisec

        cal.set(currentyear, currentmonth, currentday); // use Calendar const (month is 0 based)
        cal.add(Calendar.DAY_OF_MONTH, 1);
        
        testtext.clear();
        testtext.add("morgen");
        testtext.add("tomorrow");

        for (String text : testtext) {
            Date d = DateDetection.parseLine(text, 0);

            // this formatter is used to create Solr search queries, use it to compare equality
            String cs = DateFormatUtil.formatExternal(cal.getTime());
            String ds = DateFormatUtil.formatExternal(d);

            assertEquals(text, cs, ds);
        }
    }

    /**
     * Negative test of parseLine method, of class DateDetection
     * with cases that represent NOT a date
     */
    @Test
    public void testParseLineNoDate() {

        // test input representations
        Set<String> testtext = new LinkedHashSet();
        testtext.add("3.1.2.0102"); // example of a program version string
        // testtext.add("3.1.20.0102"); // date end-capture not working (on modification conflict with YMD parser)
        testtext.add("v3.1.21");
        testtext.add("v3.1.22.");

        for (String text : testtext) {
            Date d = DateDetection.parseLine(text, 0);
            assertNull("not a date: " + text, d);
        }
    }
}
