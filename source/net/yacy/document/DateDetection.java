/**
 *  DateDetection
 *  Copyright 2014 by Michael Peter Christen
 *  First released 12.12.2014 at https://yacy.net
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

package net.yacy.document;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.icu.util.DateRule;
import com.ibm.icu.util.EasterHoliday;
import com.ibm.icu.util.SimpleDateRule;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.date.GenericFormatter;

/**
 * The purpose of this class exceeds the demands on simple date parsing using a SimpleDateFormat
 * because it tries to 
 * - discover where in a text a date is given
 * - recognize human ways of date description and get it into a context, like 'next friday'
 * - enrich partially given dates, i.e. when the year is omitted
 * - understand different languages
 */
public class DateDetection {

    private static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");
    private static final String CONPATT  = "uuuu/MM/dd";

    private static final DateTimeFormatter CONFORM = DateTimeFormatter.ofPattern(CONPATT).withLocale(Locale.US)
            .withZone(ZoneOffset.UTC);
    private static final LinkedHashMap<Language, String[]> Weekdays = new LinkedHashMap<>();
    private static final LinkedHashMap<Language, String[]> Months = new LinkedHashMap<>();
    private static final int[] MaxDaysInMonth = new int[]{31,29,31,30,31,30,31,31,30,31,30,31};

    // to assign names for days and months, we must know what language is used to express that time
    public static enum Language {
        GERMAN, ENGLISH, FRENCH, SPANISH, ITALIAN, PORTUGUESE;
    }

    static {
        // all names must be lowercase because compared strings are made to lowercase as well
        Weekdays.put(Language.GERMAN,  new String[]{"montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag" /*oder: "sonnabend"*/, "sonntag"});
        Weekdays.put(Language.ENGLISH, new String[]{"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"});
        Weekdays.put(Language.FRENCH,  new String[]{"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"});
        Weekdays.put(Language.SPANISH, new String[]{"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"});
        Weekdays.put(Language.ITALIAN, new String[]{"lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica"});
        Months.put(Language.GERMAN,    new String[]{"januar", "februar", "märz", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember"});
        Months.put(Language.ENGLISH,   new String[]{"january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"});
        Months.put(Language.FRENCH,    new String[]{"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"});
        Months.put(Language.SPANISH,   new String[]{"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"});
        Months.put(Language.ITALIAN,   new String[]{"gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"});
        Months.put(Language.PORTUGUESE,new String[]{"janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"});

    }

    // RFC 822 day and month specification as a norm for date formats. This is needed to reconstruct the actual date later
    public static enum Weekday {
        Mon(Weekdays, 0),
        Tue(Weekdays, 1),
        Wed(Weekdays, 2),
        Thu(Weekdays, 3),
        Fri(Weekdays, 4),
        Sat(Weekdays, 5),
        Sun(Weekdays, 6);

        private final Map<String, Language> inLanguages; // a map from the word to the language
        public final int offset; // the day offset in the week, monday = 0
        private Weekday(final LinkedHashMap<Language, String[]> weekdayMap, final int offset) {
            this.inLanguages = new HashMap<>();
            this.offset = offset;
            for (Map.Entry<Language, String[]> entry: weekdayMap.entrySet()) {
                this.inLanguages.put(entry.getValue()[offset], entry.getKey());
            }
        }
    }

    public static enum Month {
        Jan( 1), Feb( 2), Mar( 3), Apr( 4), May( 5), Jun( 6),
        Jul( 7), Aug( 8), Sep( 9), Oct(10), Nov(11), Dec(12);
        //private final Map<String, Language> inLanguages;
        private final int count;
        private Month(final int count) {
            this.count = count;
        }
    }

    public static enum EntityType {
        YEAR(new LinkedHashMap<Language, String[]>()),
        MONTH(Months),
        DAY(new LinkedHashMap<Language, String[]>()),
        WEEKDAYS(Weekdays);
        LinkedHashMap<Language, String[]> languageTerms;
        EntityType(LinkedHashMap<Language, String[]> languageTerms) {
            this.languageTerms = languageTerms;
        }
    }

    private final static int CURRENT_YEAR  = LocalDate.now().getYear(); // we need that to parse dates without given years, see the ShortStyle class

    private final static String BODNCG = "(?:\\s|^)"; // begin of date non-capturing group
    private final static String EODNCG = "(?:[).:;! ]|$)"; // end of date non-capturing group
    private final static String SEPARATORNCG = "(?:/|-| - |\\.\\s|,\\s|\\.|,|\\s)"; // separator non-capturing group
    private final static String DAYCAPTURE = "(\\d{1,2})";
    private final static String YEARCAPTURE = "(\\d{2}|\\d{4})";
    private final static String MONTHCAPTURE = "(\\p{L}{3,}|\\d{1,2})";

    public static class HolidayMap extends TreeMap<String, Date[]>{
        private static final long serialVersionUID = 1L;
        public HolidayMap() {
            super(String.CASE_INSENSITIVE_ORDER);
        }
    }

    public static HolidayMap Holidays = new HolidayMap();
    public static Map<Pattern, Date[]> HolidayPattern = new HashMap<>();

    static {
        Holidays.putAll(getHolidays(CURRENT_YEAR));

        for (Map.Entry<String, Date[]> holiday: Holidays.entrySet()) {
            HolidayPattern.put(Pattern.compile(BODNCG + holiday.getKey() + EODNCG), holiday.getValue());
        }
    }

    /**
     * @param currentYear
     *            the current year reference to use
     * @return a new mapping from holiday names to arrays of
     *         three or four holiday dates starting from currentYear - 1. Each date time is 00:00:00 on UTC+00:00 time zone.
     */
    public static HolidayMap getHolidays(final int currentYear) {
        final HolidayMap result = new HolidayMap();

        /* Date rules from icu4j library used here (SimpleDateRule and EasterRule) use internally the default time zone and this can not be modified (up to icu4j 60.1) */
        final TimeZone dateRulesTimeZone = TimeZone.getDefault();
        // German
        result.put("Neujahr",                   sameDayEveryYear(Calendar.JANUARY, 1, currentYear));
        result.put("Heilige Drei Könige",       sameDayEveryYear(Calendar.JANUARY, 6, currentYear));
        result.put("Valentinstag",              sameDayEveryYear(Calendar.FEBRUARY, 14, currentYear));

        /* Fat Thursday : Thursday (6 days) before Ash Wednesday (52 days before Easter Sunday) */
        result.put("Weiberfastnacht",           holiDayEventRule(new EasterHoliday(-52, "Weiberfastnacht").getRule(), currentYear, dateRulesTimeZone)); // new Date[]{CONFORM.parse("2014/02/27"), CONFORM.parse("2015/02/12"), CONFORM.parse("2016/02/04")});
        result.put("Weiberfasching",            result.get("Weiberfastnacht"));

        /* Rose Monday : Monday before Ash Wednesday (48 days before Easter Sunday) */
        result.put("Rosenmontag",               holiDayEventRule(new EasterHoliday(-48, "Rosenmontag").getRule(), currentYear, dateRulesTimeZone)); // new Date[]{CONFORM.parse("2014/03/03"), CONFORM.parse("2015/03/16"), CONFORM.parse("2016/02/08")});
        result.put("Faschingsdienstag",         holiDayEventRule(EasterHoliday.SHROVE_TUESDAY.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/03/04"), CONFORM.parse("2015/03/17"), CONFORM.parse("2016/02/09")});
        result.put("Fastnacht",                 result.get("Faschingsdienstag")); // new Date[]{CONFORM.parse("2014/03/04"), CONFORM.parse("2015/03/17"), CONFORM.parse("2016/02/09")});
        result.put("Aschermittwoch",            holiDayEventRule(EasterHoliday.ASH_WEDNESDAY.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/03/05"), CONFORM.parse("2015/03/18"), CONFORM.parse("2016/02/10")});
        result.put("Palmsonntag",               holiDayEventRule(EasterHoliday.PALM_SUNDAY.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/04/13"), CONFORM.parse("2015/03/29"), CONFORM.parse("2016/04/20")});
        result.put("Gründonnerstag",            holiDayEventRule(EasterHoliday.MAUNDY_THURSDAY.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/04/17"), CONFORM.parse("2015/04/02"), CONFORM.parse("2016/04/24")});
        result.put("Karfreitag",                holiDayEventRule(EasterHoliday.GOOD_FRIDAY.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/04/18"), CONFORM.parse("2015/04/03"), CONFORM.parse("2016/04/25")});

        /* Holy Saturday (also called Easter Eve, Black Saturday) : one day before Easter Sunday */
        result.put("Karsamstag",                holiDayEventRule(new EasterHoliday(-1, "Karsamstag").getRule(), currentYear, dateRulesTimeZone)); // new Date[]{CONFORM.parse("2014/04/19"), CONFORM.parse("2015/04/04"), CONFORM.parse("2016/04/26")});
        result.put("Ostersonntag",              holiDayEventRule(EasterHoliday.EASTER_SUNDAY.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/04/20"), CONFORM.parse("2015/04/05"), CONFORM.parse("2016/04/27")});
        result.put("Ostermontag",               holiDayEventRule(EasterHoliday.EASTER_MONDAY.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/04/21"), CONFORM.parse("2015/04/06"), CONFORM.parse("2016/04/28")});

        /* Include both Easter Sunday and Monday */
        result.put("Ostern",                    getOsternEventRule(currentYear, dateRulesTimeZone));
        result.put("Walpurgisnacht",            sameDayEveryYear(Calendar.APRIL, 30, currentYear));
        result.put("Tag der Arbeit",            sameDayEveryYear(Calendar.MAY, 1, currentYear));

        /* Mother's Day : Second sunday of may in Germany */
        final Date[] mothersDays = new Date[3];
        int year = currentYear - 1;
        for (int i = 0; i < 3; i++) {
             final LocalDate firstMay = LocalDate.of(year, java.time.Month.MAY, 1);
               final LocalDate mothersDay = firstMay.with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY)).with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
               mothersDays[i] = toMidnightUTCDate(mothersDay);
               year++;
        }
        result.put("Muttertag", mothersDays);
        result.put("Christi Himmelfahrt",       holiDayEventRule(EasterHoliday.ASCENSION.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/05/29"), CONFORM.parse("2015/05/14"), CONFORM.parse("2016/05/05")});
        result.put("Pfingstsonntag",            holiDayEventRule(EasterHoliday.WHIT_SUNDAY.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/06/08"), CONFORM.parse("2015/05/24"), CONFORM.parse("2016/05/15")});
        result.put("Pfingstmontag",             holiDayEventRule(EasterHoliday.WHIT_MONDAY.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/06/09"), CONFORM.parse("2015/05/25"), CONFORM.parse("2016/05/16")});
        result.put("Fronleichnam",              holiDayEventRule(EasterHoliday.CORPUS_CHRISTI.getRule(), currentYear, dateRulesTimeZone));// new Date[]{CONFORM.parse("2014/06/19"), CONFORM.parse("2015/06/04"), CONFORM.parse("2016/05/25")});
        result.put("Mariä Himmelfahrt",         sameDayEveryYear(Calendar.AUGUST, 15, currentYear));
        result.put("Tag der Deutschen Einheit", sameDayEveryYear(Calendar.OCTOBER, 3, currentYear));
        result.put("Reformationstag",           sameDayEveryYear(Calendar.OCTOBER, 31, currentYear));
        result.put("Allerheiligen",             sameDayEveryYear(Calendar.NOVEMBER, 1, currentYear));
        result.put("Allerseelen",               sameDayEveryYear(Calendar.NOVEMBER, 2, currentYear));
        result.put("Martinstag",                sameDayEveryYear(Calendar.NOVEMBER, 11, currentYear));
        result.put("St. Martin",                result.get("Martinstag"));
        result.put("Buß- und Bettag",           holiDayEventRule(new SimpleDateRule(Calendar.NOVEMBER, 22, Calendar.WEDNESDAY, true), currentYear, dateRulesTimeZone)); // new Date[]{CONFORM.parse("2014/11/19"), CONFORM.parse("2015/11/18"), CONFORM.parse("2016/11/16")});
        result.put("Nikolaus",                  sameDayEveryYear(Calendar.DECEMBER, 6, currentYear));
        result.put("Heiligabend",               sameDayEveryYear(Calendar.DECEMBER, 24, currentYear));
        result.put("1. Weihnachtsfeiertag",     sameDayEveryYear(Calendar.DECEMBER, 25, currentYear));
        result.put("2. Weihnachtsfeiertag",     sameDayEveryYear(Calendar.DECEMBER, 26, currentYear));

        /* Advent : four Sundays before Chritsmas */
        final Date[] advents1 = new Date[3], advents2 = new Date[3], advents3 = new Date[3], advents4 = new Date[3],
                volkstrauertagen = new Date[3], sundaysOfTheDead = new Date[3];

        year = currentYear - 1;
        final TemporalAdjuster prevSunday = TemporalAdjusters.previous(DayOfWeek.SUNDAY);
        for (int i = 0; i < 3; i++) {
            final LocalDate christmas = LocalDate.of(year, java.time.Month.DECEMBER, 25);
            final LocalDate advent4 = christmas.with(prevSunday);
            final LocalDate advent3 = advent4.with(prevSunday);
            final LocalDate advent2 = advent3.with(prevSunday);
            final LocalDate advent1 = advent2.with(prevSunday);
            final LocalDate sundayOfTheDead = advent1.with(prevSunday);
            final LocalDate volkstrauertag = sundayOfTheDead.with(prevSunday);
            advents4[i] = toMidnightUTCDate(advent4);
            advents3[i] = toMidnightUTCDate(advent3);
            advents2[i] = toMidnightUTCDate(advent2);
            advents1[i] = toMidnightUTCDate(advent1);
            sundaysOfTheDead[i] = toMidnightUTCDate(sundayOfTheDead);
            volkstrauertagen[i] = toMidnightUTCDate(volkstrauertag);
            year++;
        }

        result.put("1. Advent", advents1);
        result.put("2. Advent", advents2);
        result.put("3. Advent", advents3);
        result.put("4. Advent", advents4);

        /* Sunday of the Dead (also called Eternity Sunday) : last Sunday before Advent */
        result.put("Totensonntag", sundaysOfTheDead);

        /* "people's day of mourning" : two Sundays before Advent */
        result.put("Volkstrauertag", volkstrauertagen);

        result.put("Silvester",                 sameDayEveryYear(Calendar.DECEMBER, 31, currentYear));

        // English
        result.put("Eastern",                   result.get("Ostern"));
        result.put("New Year's Day",            result.get("Neujahr"));
        result.put("Epiphany",                  result.get("Heilige Drei Könige"));
        result.put("Valentine's Day",           result.get("Valentinstag"));
        result.put("Orthodox Christmas",        sameDayEveryYear(Calendar.JANUARY, 7, currentYear));
        result.put("St. Patrick's Day",         sameDayEveryYear(Calendar.MARCH, 17, currentYear));
        result.put("April Fools' Day",          sameDayEveryYear(Calendar.APRIL, 1, currentYear));
        result.put("Independence Day",          sameDayEveryYear(Calendar.JULY, 4, currentYear));
        result.put("Halloween",                 result.get("Reformationstag"));
        result.put("Thanksgiving",              holiDayEventRule(new SimpleDateRule(Calendar.NOVEMBER, 22, Calendar.THURSDAY, true), currentYear, dateRulesTimeZone));
        result.put("Immaculate Conception of the Virgin Mary", sameDayEveryYear(Calendar.DECEMBER, 8, currentYear));
        result.put("Christmas Eve",             result.get("Heiligabend"));
        result.put("Christmas Day",             result.get("1. Weihnachtsfeiertag"));
        result.put("Boxing Day",                result.get("2. Weihnachtsfeiertag"));
        result.put("New Year's Eve",            result.get("Silvester"));
        return result;
    }

    /**
     * Convert a date to an old style java.util.Date instance with time set at
     * midnight on UTC time zone.
     * 
     * @param localDate
     *            a simple date with year month and day without time zone
     * @return a java.util.Date instance or null when localDate is null
     */
    public static Date toMidnightUTCDate(final LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return Date.from(ZonedDateTime.of(localDate, LocalTime.MIDNIGHT, UTC_TIMEZONE.toZoneId()).toInstant());
    }

    /**
     * @param month value of month (Calendar.month is 0 based)
     * @param day
     * @param currentYear the current year reference to use
     * @return four years of same date starting in last year (currentYear - 1)
     */
    private static Date[] sameDayEveryYear(final int month, final int day, final int currentYear) {
        final Date[] r = new Date[4];
        final Calendar cal = new GregorianCalendar(UTC_TIMEZONE);
        cal.clear();
        cal.set(currentYear - 1, month, day); // set start in previous year
        r[0] = cal.getTime();
        for (int y = 1; y < 4; y++) {
            cal.add(Calendar.YEAR, 1);
            r[y] = cal.getTime();
        }
        return r;
    }

    /**
     * @param holidayrule a date rule to calculate a holiday from a reference date
     * @param ruleTimeZone the time zone of calendar used in the holiday rule
     * @param currentYear the current year reference to use
     * @return 3 years of same holiday starting in last year (currentYear - 1)
     */
    private static Date[] holiDayEventRule(final DateRule holidayrule, final int currentYear, final TimeZone ruleTimeZone) {
        final Date[] r = new Date[3];
        final Calendar january1Calendar = new GregorianCalendar(ruleTimeZone);
        /* Clear all fields to get a 00:00:00:000 time part */
        january1Calendar.clear();

        /* Calendar using UTC time zone to produce date results */
        final Calendar utcCalendar = new GregorianCalendar(UTC_TIMEZONE);

        /* Calendar using the same time zone as in the holidayrule to extract year,month, and day fields */
        final Calendar ruleCalendar = new GregorianCalendar(ruleTimeZone);

        int year = currentYear -1; // set previous year as start year
        for (int y = 0; y < 3; y++) {
            january1Calendar.set(year, Calendar.JANUARY, 1);
            Date holiday = holidayrule.firstAfter(january1Calendar.getTime());
            ruleCalendar.setTime(holiday);
            utcCalendar.set(ruleCalendar.get(Calendar.YEAR), ruleCalendar.get(Calendar.MONTH),
                    ruleCalendar.get(Calendar.DAY_OF_MONTH));
            r[y] = utcCalendar.getTime();
            year++;
        }
        return r;
    }

    /**
     * @param currentYear the current year reference to use
     * @param ruleTimeZone the time zone of calendar used in the holiday rule
     * @return Easter sunday and monday dates on three years starting from last year
     */
    private static Date[] getOsternEventRule(final int currentYear, final TimeZone ruleTimeZone) {
        ArrayList<Date> osternDates = new ArrayList<>();
        Collections.addAll(osternDates, holiDayEventRule(EasterHoliday.EASTER_SUNDAY.getRule(), currentYear, ruleTimeZone));
        Collections.addAll(osternDates, holiDayEventRule(EasterHoliday.EASTER_MONDAY.getRule(), currentYear, ruleTimeZone));
        return osternDates.toArray(new Date[osternDates.size()]);
    }

    /**
     * The language recognition subclass understands date description parts in different languages.
     * It can also be used to identify the language of a text, if that text uses words from a date vocabulary.
     */
    public static class LanguageRecognition {

        private final Pattern weekdayMatch, monthMatch;
        private final Set<Language> usedInLanguages;
        private final Map<String, Integer> weekdayIndex, monthIndex, monthIndexAbbrev;

        public LanguageRecognition(Language[] languages) {
            this.usedInLanguages = new HashSet<Language>();
            // prepare a month index for the languages that this notion supports
            this.weekdayIndex = new HashMap<>();
            this.monthIndex = new HashMap<>();
            this.monthIndexAbbrev = new HashMap<>();
            StringBuilder weekdayMatchString = new StringBuilder();
            StringBuilder monthMatchString = new StringBuilder();
            for (Language language: languages) {
                this.usedInLanguages.add(language);

                String[] weekdays = Weekdays.get(language);
                if (weekdays != null) {
                    assert weekdays.length == 7;
                    for (int i = 0; i < 7; i++) {
                        this.weekdayIndex.put(weekdays[i], i);
                        weekdayMatchString.append("|(?:").append(BODNCG).append(weekdays[i]).append(SEPARATORNCG).append(EODNCG).append(')');
                    }
                }

                String[] months = Months.get(language);
                if (months != null) {
                    assert months.length == 12;
                    for (int i = 0; i < 12; i++) {
                        monthIndex.put(months[i], i + 1);
                        monthMatchString.append("|(?:").append(BODNCG).append(months[i]).append(SEPARATORNCG).append(EODNCG).append(')');
                        String abbrev = months[i].substring(0, 3);
                        if (monthIndexAbbrev.containsKey(abbrev) && monthIndexAbbrev.get(abbrev).intValue() != i + 1)
                            monthIndexAbbrev.put(abbrev, -1); // ambiguous months get a -1
                        else
                            monthIndexAbbrev.put(abbrev, i + 1);
                    }
                }
            }
            this.weekdayMatch = Pattern.compile(weekdayMatchString.length() > 0 ? weekdayMatchString.substring(1) : "");
            this.monthMatch = Pattern.compile(monthMatchString.length() > 0 ? monthMatchString.substring(1) : "");
        }

        /**
         * this is an expensive check that looks if any of the words from the date expressions (month and weekday expressions)
         * appear in the text. This should only be used to verify a parse result if the result was ambiguous
         * @param text
         * @return true if one of the month and weekday expressions appear in the text
         */
        public boolean usesLanguageOfNotion(String text) {
            return this.weekdayMatch.matcher(text).matches() || this.monthMatch.matcher(text).matches();
        }

        /**
         * parse a part of a date
         * @param entity
         * @param object
         * @return a scalar value associated with this date part
         */
        public int parseEntity(EntityType entity, String object) {
            if (entity == EntityType.YEAR) {
                try {
                    int i = Integer.parseInt(object);
                    if (i < 100) i += 2000; // yes that makes it possible to parse the years 0-99 and it will be incorrect in the year 2100 when that is abbreviated with 00
                    if (i > CURRENT_YEAR + 10) return -1; // there are very rarely dates in the future that far
                    return i;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            if (entity == EntityType.MONTH) {
                try {
                    int i = Integer.parseInt(object);
                    if (i >= 1 && i <= 12) return i;
                    return -1; // no reason to try in a different way, its just a wrong number
                } catch (NumberFormatException e) {
                    // this may be the name of a month
                    if (object.length() == 3) {
                        // try RFC 822 names
                        object = object.substring(0, 1).toUpperCase() + object.substring(1).toLowerCase();
                        try {
                            Month m = Month.valueOf(object);
                            return m.count;
                        } catch (IllegalArgumentException | NoClassDefFoundError ee) {} // just ignore this, that was just a try to shorten things..
                    }
                    // try the collection of names for each language
                    object = object.toLowerCase(); // the stored month names are all lowercase
                    Integer i = this.monthIndex.get(object);
                    if (i != null) return i.intValue();
                    // try an abbreviation
                    if (object.length() == 3) {
                        i = this.monthIndexAbbrev.get(object.substring(0, 3));
                        if (i != null) return i.intValue(); // may also be -1!
                    }
                    return -1;
                }
            }
            if (entity == EntityType.DAY) {
                try {
                    int i = Integer.parseInt(object);
                    if (i < 1 || i > 31) return -1;
                    return i;
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            return -1;
        }

    }

    private final static LanguageRecognition ENGLISH_LANGUAGE = new LanguageRecognition(new Language[]{Language.ENGLISH});
    private final static LanguageRecognition GERMAN_LANGUAGE = new LanguageRecognition(new Language[]{Language.GERMAN});
    private final static LanguageRecognition FRENCH_LANGUAGE = new LanguageRecognition(new Language[]{Language.FRENCH});
    private final static LanguageRecognition ENGLISH_GERMAN_LANGUAGE = new LanguageRecognition(new Language[]{Language.GERMAN, Language.ENGLISH});
    private final static LanguageRecognition ENGLISH_GERMAN_FRENCH_SPANISH_ITALIAN_LANGUAGE = new LanguageRecognition(new Language[]{Language.GERMAN, Language.ENGLISH, Language.FRENCH, Language.SPANISH, Language.ITALIAN, Language.PORTUGUESE});

    public static interface StyleParser {
        /**
         * get all dates in the text
         * @param text
         * @return a set of dates, ordered by occurrence.
         */
        public LinkedHashSet<Date> parse(String text);
    }

    /**
     * Regular expressions for various types of date writings.
     * Uses terminology and data taken from:
     * http://en.wikipedia.org/wiki/Date_format_by_country
     */
    public static enum EndianStyle implements StyleParser {
        YMD(EntityType.YEAR, EntityType.MONTH, EntityType.DAY, // Big-endian (year, month, day), e.g. 1996-04-22
            ENGLISH_GERMAN_LANGUAGE, // GERMAN: 'official standard date format', ENGLISH: used in UK
            BODNCG + YEARCAPTURE + SEPARATORNCG + MONTHCAPTURE + SEPARATORNCG  + DAYCAPTURE + EODNCG
           ),
        DMY(EntityType.DAY, EntityType.MONTH, EntityType.YEAR, // Little-endian (day, month, year), e.g. 22.04.96 or 22/04/96 or 22 April 1996
            ENGLISH_GERMAN_FRENCH_SPANISH_ITALIAN_LANGUAGE, // GERMAN: traditional, ENGLISH: used in UK
            BODNCG + DAYCAPTURE + SEPARATORNCG + MONTHCAPTURE + SEPARATORNCG  + YEARCAPTURE + EODNCG
           ),
        MDY(EntityType.MONTH, EntityType.DAY, EntityType.YEAR, // Middle-endian (month, day, year), e.g. 04/22/96 or April 22, 1996
            ENGLISH_LANGUAGE, // ENGLISH: used in USA
            BODNCG + MONTHCAPTURE + SEPARATORNCG + DAYCAPTURE + SEPARATORNCG  + YEARCAPTURE + EODNCG
           );

        private final Pattern pattern;
        private final EntityType firstEntity, secondEntity, thirdEntity;
        public final LanguageRecognition languageParser;
        EndianStyle(EntityType firstEntity, EntityType secondEntity, EntityType thirdEntity, LanguageRecognition languageParser, String patternString) {
            this.firstEntity = firstEntity;
            this.secondEntity = secondEntity;
            this.thirdEntity = thirdEntity;
            this.pattern = Pattern.compile(patternString);
            this.languageParser = languageParser;
        }

        /**
         * get all dates in the text
         * @param text
         * @return a set of dates, ordered by occurrence.
         */
        @Override
        public LinkedHashSet<Date> parse(final String text) {
            LinkedHashSet<Date> dates = new LinkedHashSet<>();
            Matcher matcher = this.pattern.matcher(text);
            while (matcher.find()) {
                if (!(matcher.groupCount() == 3)) continue;
                String entity1 = matcher.group(1); if (entity1 == null) continue;
                String entity2 = matcher.group(2); if (entity2 == null) continue;
                String entity3 = matcher.group(3); if (entity3 == null) continue;
                //System.out.println("FRAGMENTS: entity1=" + entity1 + ", entity2=" + entity2 + ", entity3=" + entity3); // DEBUG
                int i1 = languageParser.parseEntity(this.firstEntity, entity1);
                if (i1 < 0) continue;
                int i2 = languageParser.parseEntity(this.secondEntity, entity2);
                if (i2 < 0) continue;
                int i3 = languageParser.parseEntity(this.thirdEntity, entity3);
                if (i3 < 0) continue;
                int day = this.firstEntity == EntityType.DAY ? i1 : this.secondEntity == EntityType.DAY ? i2 : i3;
                int month = this.firstEntity == EntityType.MONTH ? i1 : this.secondEntity == EntityType.MONTH ? i2 : i3;
                if (day > MaxDaysInMonth[month - 1]) continue; // validity check of the day number
                int year = this.firstEntity == EntityType.YEAR ? i1 : this.secondEntity == EntityType.YEAR ? i2 : i3;
                final Date parsed = parseDateSafely(
                        year + "/" + (month < 10 ? "0" : "") + month + "/" + (day < 10 ? "0" : "") + day, CONFORM);
                if(parsed != null) {
                    dates.add(parsed);
                }
                if (dates.size() > 100) {dates.clear(); break;} // that does not make sense
            }
            return dates;
        }

    }

    /**
     * Safely parse the given string to an instant using the given formatter. Return
     * null when the format can not be applied to the given string or when any
     * parsing error occurred.
     * 
     * @param str
     *            the string to parse
     * @param formatter
     *            the formatter to use
     * @return an Instant instance or null
     */
    protected static Date parseDateSafely(final String str, final DateTimeFormatter formatter) {
        Date res = null;
        if (str != null && !str.isEmpty()) {
            try {
                if (formatter != null) {
                    res = Date.from(LocalDate.parse(str, formatter).atStartOfDay().toInstant(ZoneOffset.UTC));
                }
            } catch (final RuntimeException ignored) {
            }
        }
        return res;
    }

    public static enum ShortStyle implements StyleParser {
        MD_ENGLISH(EntityType.MONTH, EntityType.DAY, // Big-endian (month, day), e.g. "from october 1st to september 13th"
            ENGLISH_LANGUAGE,
            BODNCG + "on " + MONTHCAPTURE + SEPARATORNCG  + DAYCAPTURE + EODNCG
           ),
        DM_GERMAN(EntityType.DAY, EntityType.MONTH, // Little-endian (day, month), e.g. "am 1. April"
            GERMAN_LANGUAGE,
            BODNCG + "am " + DAYCAPTURE + SEPARATORNCG + MONTHCAPTURE + EODNCG
           ),
        DM_FRENCH(EntityType.DAY, EntityType.MONTH, // Little-endian (day, month), e.g. "le 29 Septembre,"
            FRENCH_LANGUAGE,
            BODNCG + "le " + DAYCAPTURE + " " + MONTHCAPTURE + EODNCG
        ),
        DM_ITALIAN(EntityType.DAY, EntityType.MONTH, // Little-endian (day, month), e.g. "il 29 settembre,"
            FRENCH_LANGUAGE,
            BODNCG + "il " + DAYCAPTURE + " " + MONTHCAPTURE + EODNCG
        ),
        DM_SPANISH(EntityType.DAY, EntityType.MONTH, // Little-endian (day, month), e.g. "el 29 de septiembre,"
            FRENCH_LANGUAGE,
            BODNCG + "el " + DAYCAPTURE + " de " + MONTHCAPTURE + EODNCG
        );
        public final Pattern pattern;
        private final EntityType firstEntity, secondEntity;
        public final LanguageRecognition languageParser;
        ShortStyle(EntityType firstEntity, EntityType secondEntity, LanguageRecognition languageParser, String patternString) {
            this.firstEntity = firstEntity;
            this.secondEntity = secondEntity;
            this.pattern = Pattern.compile(patternString);
            this.languageParser = languageParser;
        }

        /**
         * get all dates in the text
         * @param text
         * @return a set of dates, ordered by occurrence.
         */
        @Override
        public LinkedHashSet<Date> parse(final String text) {
            LinkedHashSet<Date> dates = new LinkedHashSet<>();
            Matcher matcher = this.pattern.matcher(text);
            //ConcurrentLog.info("DateDetection", "applying matcher: " + matcher.toString());
            while (matcher.find()) {
                if (!(matcher.groupCount() == 2)) continue;
                String entity1 = matcher.group(1); if (entity1 == null) continue;
                String entity2 = matcher.group(2); if (entity2 == null) continue;
                //System.out.println("FRAGMENTS: entity1=" + entity1 + ", entity2=" + entity2 + ", entity3=" + entity3); // DEBUG
                int i1 = languageParser.parseEntity(this.firstEntity, entity1);
                if (i1 < 0) continue;
                int i2 = languageParser.parseEntity(this.secondEntity, entity2);
                if (i2 < 0) continue;
                int day = this.firstEntity == EntityType.DAY ? i1 : i2;
                int month = this.firstEntity == EntityType.MONTH ? i1 :  i2;
                if (day > MaxDaysInMonth[month - 1]) continue; // validity check of the day number
                int thisyear = CURRENT_YEAR;
                int nextyear = CURRENT_YEAR + 1;
                String datestub = "/" + (month < 10 ? "0" : "") + month + "/" + (day < 10 ? "0" : "") + day;

                final Date atThisYear = parseDateSafely(thisyear + datestub, CONFORM);
                if(atThisYear != null) {
                    dates.add(atThisYear);
                }

                final Date atNextYear = parseDateSafely(nextyear + datestub, CONFORM);
                if(atNextYear != null) {
                    dates.add(atNextYear);
                }
                //dates.add(atThisYear.after(TODAY) ? atThisYear : atNextYear); // we consider these kind of dates as given for the future
                if (dates.size() > 100) {dates.clear(); break;} // that does not make sense
            }
            return dates;
        }

    }

    private static final HashMap<String, Long> specialDayOffset = new HashMap<>();
    static {
        specialDayOffset.put("today", 0L); specialDayOffset.put("heute", 0L);
        specialDayOffset.put("tomorrow", AbstractFormatter.dayMillis); specialDayOffset.put("morgen", AbstractFormatter.dayMillis);
        specialDayOffset.put("dayaftertomorrow", 2 * AbstractFormatter.dayMillis); specialDayOffset.put("uebermorgen", 2 * AbstractFormatter.dayMillis);
        specialDayOffset.put("yesterday", -AbstractFormatter.dayMillis); specialDayOffset.put("gestern", -AbstractFormatter.dayMillis);
    }

    /**
     * get all dates in the text
     * @param text
     * @param timezoneOffset TODO: implement
     * @return a set of dates, ordered by time. first date in the ordered set is the oldest time.
     */
    public static LinkedHashSet<Date> parse(String text, int timezoneOffset) {

        LinkedHashSet<Date> dates = parseRawDate(text);

        for (Map.Entry<Pattern, Date[]> entry: HolidayPattern.entrySet()) {
            if (entry.getKey().matcher(text).find()) {
                for (Date d: entry.getValue()) dates.add(d);
            }
        }
        return dates;
    }

    /**
     * Parse a line expected to contain one date expression only.
     * This is used by the query parser for query date modifier on:, from: or to:
     *
     * @param text
     * @param timezoneOffset TODO: implement
     * @return determined date or null
     */
    public static Date parseLine(final String text, final int timezoneOffset) {
        // check standard date formats
        Date d = parseDateSafely(text, CONFORM);
        //if (d == null) try {d = GenericFormatter.FORMAT_SHORT_DAY.parse(text);} catch (ParseException e) {} // did not work well and fired for wrong formats; do not use
        if (d == null) {
            d = parseDateSafely(text, GenericFormatter.FORMAT_RFC1123_SHORT);
        }
        if (d == null) {
            d = parseDateSafely(text, GenericFormatter.FORMAT_ANSIC);
        }

        if (d == null) {
            // check other date formats
            Set<Date> dd = parseRawDate(text);
            if (dd.size() >= 1) d = dd.iterator().next(); // this returns the oldest/earliest date from the set (as set is typically ordered by date)
        }

        if (d == null) {
            Long offset;
            if ((offset = specialDayOffset.get(text)) != null) {
                d = new Date((System.currentTimeMillis() / AbstractFormatter.dayMillis) * AbstractFormatter.dayMillis + offset.longValue());
            }
        }

        if (d == null) {
            // check holidays
            Date[] dd = Holidays.get(text); // as we expect single expression, we can get directly (w/o matcher)
            // TODO: consider user enters expression like "Silvester 2016" or "Eastern/2017" -> needs a special matcher
            if (dd != null) {
                if (dd.length > 1) {
                    d = dd[1]; // this is usually date in current year (as array is initialized [year-1, year, year+1, year+2]
                } else {
                    d = dd[0];
                }
            }
        }
        return d;
    }

    private static LinkedHashSet<Date> parseRawDate(String text) {
        // get parse alternatives for different date styles; we consider that one document uses only one style
        LinkedHashSet<Date> DMYDates = EndianStyle.DMY.parse(text);
        ShortStyle[] shortStyleCheck = new ShortStyle[]{ShortStyle.DM_GERMAN, ShortStyle.DM_FRENCH, ShortStyle.DM_ITALIAN, ShortStyle.DM_SPANISH};
        LinkedHashSet<Date>  DMDates = new LinkedHashSet<>();
        for (ShortStyle shortStyle: shortStyleCheck) {
            DMDates.addAll(shortStyle.parse(text));
            if (DMDates.size() > 0) break;
        }
        DMYDates.addAll(DMDates);

        LinkedHashSet<Date> MDYDates = DMYDates.size() == 0 ? EndianStyle.MDY.parse(text) : new LinkedHashSet<Date>(0);
        LinkedHashSet<Date>  MDDates = DMYDates.size() == 0 ? ShortStyle.MD_ENGLISH.parse(text) : new LinkedHashSet<Date>(0);
        MDYDates.addAll(MDDates);

        LinkedHashSet<Date> YMDDates = DMYDates.size() == 0 && MDYDates.size() == 0 ? EndianStyle.YMD.parse(text) : new LinkedHashSet<Date>(0);

        // if either one of them contains any and the other contain no date, chose that one (we don't want to mix them)
        if (YMDDates.size() > 0 && DMYDates.size() == 0 && MDYDates.size() == 0) return YMDDates;
        if (YMDDates.size() == 0 && DMYDates.size() > 0 && MDYDates.size() == 0) return DMYDates;
        if (YMDDates.size() == 0 && DMYDates.size() == 0 && MDYDates.size() > 0) return MDYDates;

        // if we have several sets, check if we can detect the language from month or weekday expressions
        // we sort out such sets, which do not contain any of these languages
        boolean usesLanguageOfYMD = YMDDates.size() > 0 ? false : EndianStyle.YMD.languageParser.usesLanguageOfNotion(text);
        boolean usesLanguageOfDMY = DMYDates.size() > 0 ? false : EndianStyle.DMY.languageParser.usesLanguageOfNotion(text);
        boolean usesLanguageOfMDY = MDYDates.size() > 0 ? false : EndianStyle.MDY.languageParser.usesLanguageOfNotion(text);

        // now check again
        if (usesLanguageOfYMD && !usesLanguageOfDMY && !usesLanguageOfMDY) return YMDDates;
        if (!usesLanguageOfYMD && usesLanguageOfDMY && !usesLanguageOfMDY) return DMYDates;
        if (!usesLanguageOfYMD && !usesLanguageOfDMY && usesLanguageOfMDY) return MDYDates;

        // if this fails, we return only the DMY format since that has the most chances to be right (it is mostly used)
        // we choose DMYDates even if it is empty to avoid false positives.
        return DMYDates;
    }

    public static void main(String[] args) {
        String fill = ""; for (int i = 0; i < 1000; i++) fill += 'x';
        String[] test = new String[]{
               "\n laden die Stadtwerke \n X am Rosenmontag und am \n Faschingsdienstag zur Disko auf die \n",
                "kein Datum im Text",
                " Fastnacht am 4. März noch",
                " Fastnacht am 4. April noch­",
                "heute 12. Dezember 2014. ",
                "heute 12. Dezember 2014",
                "12. Dezember 2014. ",
                "heute 12. Dezember 2014 ",
                "heute 12. Dezember 2014. ",
                "Donnerstag, 18. Dezember 2014 xyz",
                "Donnerstag, 18 Dezember 2014 xyz",
                "Donnerstag, 18.Dezember 2014 xyz",
                "Montag, 8. Dezember 2014 xyz",
                "Montag, 8.Dezember 2014 xyz",
                "Donnerstag, 18.12.2014 xyz",
                "Montag, 8.12.2014 xyz",
                "Donnerstag, 18.12.14 xyz",
                "Montag, 8.12.14 xyz",
                "Mitglied seit: 13. Januar 2007 xyz",
                "Im Dezember 2014 xyz",
                "11.12.2014",
                "11. September 2001",
                "12.12.2014 08:43",
                "immer am 1. Dezember abends",
                "immer am 31. Dezember abends",
                "immer am 31. dezember abends",
                "on october 20 every year",
                " on october 20 every year",
                "on September 29,",
                "am Karfreitag um 15:00 Uhr",
                "11 fevereiro 2001", // portuguese
                "12. fevereiro 2002", // portuguese
                "13 de fevereiro 2003", // portuguese
                "Fevereiro 14, 2004" // portuguese
        };
        long t = System.currentTimeMillis();
        for (String s: test) {
            String parsed = parse(fill + " " + s + " " + fill, 0).toString();
            System.out.println("SOURCE: " + s);
            System.out.println("DATE  : " + parsed);
            System.out.println();
        }
        System.out.println("Runtime: " + (System.currentTimeMillis() - t) + " milliseconds.");
    }

}
