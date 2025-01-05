package net.yacy.document;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.junit.Test;

import net.yacy.document.DateDetection.HolidayMap;

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
        Set<String> testtext = new LinkedHashSet<>();
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
            String cs = cal.getTime().toInstant().toString();
            String ds = d.toInstant().toString();

            assertEquals(text, cs, ds);
        }

        // test holidays
        cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        int currentyear = cal.get(Calendar.YEAR); // instance is initilized to NOW
        cal.clear(); // get rid of sec, millisec
        cal.set(currentyear, Calendar.JANUARY, 1); // use Calendar const (month is 0 based)

        testtext.clear();
        testtext.add("Neujahr");
        testtext.add("New Year's Day");

        for (String text : testtext) {
            Date d = DateDetection.parseLine(text, 0);

            // this formatter is used to create Solr search queries, use it to compare equality
            String cs = cal.getTime().toInstant().toString();
            String ds = d.toInstant().toString();

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
            String cs = cal.getTime().toInstant().toString();
            String ds = d.toInstant().toString();

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
        Set<String> testtext = new LinkedHashSet<>();
        testtext.add("3.1.2.0102"); // example of a program version string
        // testtext.add("3.1.20.0102"); // date end-capture not working (on modification conflict with YMD parser)
        testtext.add("v3.1.21");
        testtext.add("v3.1.22.");

        for (String text : testtext) {
            Date d = DateDetection.parseLine(text, 0);
            assertNull("not a date: " + text, d);
        }
    }
    
    /**
     * Compare the dates associated to the holiday name in the map with the expected result
     * @param holidays holidays map for a given year
     * @param holidayName the holiday name (key in the map) to check
     * @param expected the expected formatted dates
     */
	private void checkFormattedDates(final HolidayMap holidays, final String holidayName, final String[] expected) {
		/* Simple format for easier assertions reading */
		final SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));

		final Date[] actual = holidays.get(holidayName);

		String[] actualFormatted = new String[actual.length];
		for (int i = 0; i < actual.length; i++) {
			actualFormatted[i] = format.format(actual[i]);
		}
		assertArrayEquals(holidayName, expected, actualFormatted);
	}
    
    /**
     * Check generated holidays dates with some references dates 
     */
    @Test
	public void testGetHolidays() {
		HolidayMap holidays = DateDetection.getHolidays(2015);
		
		/* Dates initially hardcoded in DateDetection class */
		checkFormattedDates(holidays, "Ostersonntag", new String[] { "2014/04/20", "2015/04/05", "2016/03/27" });
		checkFormattedDates(holidays, "Weiberfastnacht", new String[] { "2014/02/27", "2015/02/12", "2016/02/04" });
		checkFormattedDates(holidays, "Rosenmontag", new String[] { "2014/03/03", "2015/02/16", "2016/02/08" });
		checkFormattedDates(holidays, "Karsamstag", new String[] { "2014/04/19", "2015/04/04", "2016/03/26" });
		checkFormattedDates(holidays, "Ostern",
				new String[] { "2014/04/20", "2015/04/05", "2016/03/27", "2014/04/21", "2015/04/06", "2016/03/28" });
		checkFormattedDates(holidays, "Muttertag", new String[] { "2014/05/11", "2015/05/10", "2016/05/08" });
		checkFormattedDates(holidays, "Volkstrauertag", new String[] { "2014/11/16", "2015/11/15", "2016/11/13" });
		checkFormattedDates(holidays, "Totensonntag", new String[] { "2014/11/23", "2015/11/22", "2016/11/20" });
		checkFormattedDates(holidays, "1. Advent", new String[] { "2014/11/30", "2015/11/29", "2016/11/27" });
		checkFormattedDates(holidays, "2. Advent", new String[] { "2014/12/07", "2015/12/06", "2016/12/04" });
		checkFormattedDates(holidays, "3. Advent", new String[] { "2014/12/14", "2015/12/13", "2016/12/11" });
		checkFormattedDates(holidays, "4. Advent", new String[] { "2014/12/21", "2015/12/20", "2016/12/18" });
		
		/* Some more recent examples */
		holidays = DateDetection.getHolidays(2018);
		checkFormattedDates(holidays, "Ostersonntag", new String[] { "2017/04/16", "2018/04/01", "2019/04/21"});
		checkFormattedDates(holidays, "Weiberfastnacht", new String[] { "2017/02/23", "2018/02/08", "2019/02/28" });
		checkFormattedDates(holidays, "Rosenmontag", new String[] { "2017/02/27", "2018/02/12", "2019/03/04" });
		checkFormattedDates(holidays, "Muttertag", new String[] { "2017/05/14", "2018/05/13", "2019/05/12" });
		checkFormattedDates(holidays, "1. Advent", new String[] { "2017/12/03", "2018/12/02", "2019/12/01" });
	}
}
