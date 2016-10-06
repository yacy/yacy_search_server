/**
 *  DateDetection
 *  Copyright 2014 by Michael Peter Christen
 *  First released 12.12.2014 at http://yacy.net
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

import com.ibm.icu.util.DateRule;
import com.ibm.icu.util.EasterHoliday;
import com.ibm.icu.util.SimpleDateRule;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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

    private static final TimeZone TZ_GMT = TimeZone.getTimeZone("GMT");
    private static final String CONPATT  = "yyyy/MM/dd";
    private static final SimpleDateFormat CONFORM = new SimpleDateFormat(CONPATT, Locale.US);
    private static final LinkedHashMap<Language, String[]> Weekdays = new LinkedHashMap<>();
    private static final LinkedHashMap<Language, String[]> Months = new LinkedHashMap<>();
    private static final int[] MaxDaysInMonth = new int[]{31,29,31,30,31,30,31,31,30,31,30,31};

    // to assign names for days and months, we must know what language is used to express that time
    public static enum Language {
        GERMAN, ENGLISH, FRENCH, SPANISH, ITALIAN, PORTUGUESE;
    }
    
    static {
        CONFORM.setTimeZone(TZ_GMT);
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

    private final static Date TODAY = new Date();
    private final static int CURRENT_YEAR  = Integer.parseInt(CONFORM.format(TODAY).substring(0, 4)); // we need that to parse dates without given years, see the ShortStyle class

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
        try {
            // German
            Holidays.put("Neujahr",                   sameDayEveryYear(Calendar.JANUARY, 1));
            Holidays.put("Heilige Drei Könige",       sameDayEveryYear(Calendar.JANUARY, 6));
            Holidays.put("Valentinstag",              sameDayEveryYear(Calendar.FEBRUARY, 14));
            Holidays.put("Weiberfastnacht",           new Date[]{CONFORM.parse("2014/02/27"), CONFORM.parse("2015/02/12"), CONFORM.parse("2016/02/04")});
            Holidays.put("Weiberfasching",            Holidays.get("Weiberfastnacht"));
            Holidays.put("Rosenmontag",               new Date[]{CONFORM.parse("2014/03/03"), CONFORM.parse("2015/03/16"), CONFORM.parse("2016/02/08")});
            Holidays.put("Faschingsdienstag",         holiDayEventRule(EasterHoliday.SHROVE_TUESDAY.getRule()));// new Date[]{CONFORM.parse("2014/03/04"), CONFORM.parse("2015/03/17"), CONFORM.parse("2016/02/09")});
            Holidays.put("Fastnacht",                 Holidays.get("Faschingsdienstag")); // new Date[]{CONFORM.parse("2014/03/04"), CONFORM.parse("2015/03/17"), CONFORM.parse("2016/02/09")});
            Holidays.put("Aschermittwoch",            holiDayEventRule(EasterHoliday.ASH_WEDNESDAY.getRule()));// new Date[]{CONFORM.parse("2014/03/05"), CONFORM.parse("2015/03/18"), CONFORM.parse("2016/02/10")});
            Holidays.put("Palmsonntag",               holiDayEventRule(EasterHoliday.PALM_SUNDAY.getRule()));// new Date[]{CONFORM.parse("2014/04/13"), CONFORM.parse("2015/03/29"), CONFORM.parse("2016/04/20")});
            Holidays.put("Gründonnerstag",            holiDayEventRule(EasterHoliday.MAUNDY_THURSDAY.getRule()));// new Date[]{CONFORM.parse("2014/04/17"), CONFORM.parse("2015/04/02"), CONFORM.parse("2016/04/24")});
            Holidays.put("Karfreitag",                holiDayEventRule(EasterHoliday.GOOD_FRIDAY.getRule()));// new Date[]{CONFORM.parse("2014/04/18"), CONFORM.parse("2015/04/03"), CONFORM.parse("2016/04/25")});
            Holidays.put("Karsamstag",                new Date[]{CONFORM.parse("2014/04/19"), CONFORM.parse("2015/04/04"), CONFORM.parse("2016/04/26")});
            Holidays.put("Ostersonntag",              holiDayEventRule(EasterHoliday.EASTER_SUNDAY.getRule()));// new Date[]{CONFORM.parse("2014/04/20"), CONFORM.parse("2015/04/05"), CONFORM.parse("2016/04/27")});
            Holidays.put("Ostermontag",               holiDayEventRule(EasterHoliday.EASTER_MONDAY.getRule()));// new Date[]{CONFORM.parse("2014/04/21"), CONFORM.parse("2015/04/06"), CONFORM.parse("2016/04/28")});
            Holidays.put("Ostern",                    new Date[]{CONFORM.parse("2014/04/20"), CONFORM.parse("2015/04/05"), CONFORM.parse("2016/04/27"),
                                                                 CONFORM.parse("2014/04/21"), CONFORM.parse("2015/04/06"), CONFORM.parse("2016/04/28")});
            Holidays.put("Walpurgisnacht",            sameDayEveryYear(Calendar.APRIL, 30));
            Holidays.put("Tag der Arbeit",            sameDayEveryYear(Calendar.MAY, 1));
            Holidays.put("Muttertag",                 new Date[]{CONFORM.parse("2014/05/11"), CONFORM.parse("2015/05/10"), CONFORM.parse("2016/05/08")});
            Holidays.put("Christi Himmelfahrt",       holiDayEventRule(EasterHoliday.ASCENSION.getRule()));// new Date[]{CONFORM.parse("2014/05/29"), CONFORM.parse("2015/05/14"), CONFORM.parse("2016/05/05")});
            Holidays.put("Pfingstsonntag",            holiDayEventRule(EasterHoliday.WHIT_SUNDAY.getRule()));// new Date[]{CONFORM.parse("2014/06/08"), CONFORM.parse("2015/05/24"), CONFORM.parse("2016/05/15")});
            Holidays.put("Pfingstmontag",             holiDayEventRule(EasterHoliday.WHIT_MONDAY.getRule()));// new Date[]{CONFORM.parse("2014/06/09"), CONFORM.parse("2015/05/25"), CONFORM.parse("2016/05/16")});
            Holidays.put("Fronleichnam",              holiDayEventRule(EasterHoliday.CORPUS_CHRISTI.getRule()));// new Date[]{CONFORM.parse("2014/06/19"), CONFORM.parse("2015/06/04"), CONFORM.parse("2016/05/25")});
            Holidays.put("Mariä Himmelfahrt",         sameDayEveryYear(Calendar.AUGUST, 15));
            Holidays.put("Tag der Deutschen Einheit", sameDayEveryYear(Calendar.OCTOBER, 3));
            Holidays.put("Reformationstag",           sameDayEveryYear(Calendar.OCTOBER, 31));
            Holidays.put("Allerheiligen",             sameDayEveryYear(Calendar.NOVEMBER, 1));
            Holidays.put("Allerseelen",               sameDayEveryYear(Calendar.NOVEMBER, 2));
            Holidays.put("Martinstag",                sameDayEveryYear(Calendar.NOVEMBER, 11));
            Holidays.put("St. Martin",                Holidays.get("Martinstag"));
            Holidays.put("Volkstrauertag",            new Date[]{CONFORM.parse("2014/11/16"), CONFORM.parse("2015/11/15"), CONFORM.parse("2016/11/13")});
            Holidays.put("Buß- und Bettag",           holiDayEventRule(new SimpleDateRule(Calendar.NOVEMBER, 22, Calendar.WEDNESDAY, true))); // new Date[]{CONFORM.parse("2014/11/19"), CONFORM.parse("2015/11/18"), CONFORM.parse("2016/11/16")});
            Holidays.put("Totensonntag",              new Date[]{CONFORM.parse("2014/11/23"), CONFORM.parse("2015/11/22"), CONFORM.parse("2016/11/20")});
            Holidays.put("Nikolaus",                  sameDayEveryYear(Calendar.DECEMBER, 6));
            Holidays.put("Heiligabend",               sameDayEveryYear(Calendar.DECEMBER, 24));
            Holidays.put("1. Weihnachtsfeiertag",     sameDayEveryYear(Calendar.DECEMBER, 25));
            Holidays.put("2. Weihnachtsfeiertag",     sameDayEveryYear(Calendar.DECEMBER, 26));
            Holidays.put("1. Advent",                 new Date[]{CONFORM.parse("2014/11/30"), CONFORM.parse("2015/11/29"), CONFORM.parse("2016/11/27")});
            Holidays.put("2. Advent",                 new Date[]{CONFORM.parse("2014/12/07"), CONFORM.parse("2015/12/06"), CONFORM.parse("2016/12/04")});
            Holidays.put("3. Advent",                 new Date[]{CONFORM.parse("2014/12/14"), CONFORM.parse("2015/12/13"), CONFORM.parse("2016/12/11")});
            Holidays.put("4. Advent",                 new Date[]{CONFORM.parse("2014/12/21"), CONFORM.parse("2015/12/20"), CONFORM.parse("2016/12/18")});
            Holidays.put("Silvester",                 sameDayEveryYear(Calendar.DECEMBER, 31));
            
            // English
            Holidays.put("Eastern",                   Holidays.get("Ostern"));
            Holidays.put("New Year's Day",            Holidays.get("Neujahr"));
            Holidays.put("Epiphany",                  Holidays.get("Heilige Drei Könige"));
            Holidays.put("Valentine's Day",           Holidays.get("Valentinstag"));
            Holidays.put("Orthodox Christmas",        sameDayEveryYear(Calendar.JANUARY, 7));
            Holidays.put("St. Patrick's Day",         sameDayEveryYear(Calendar.MARCH, 17));
            Holidays.put("April Fools' Day",          sameDayEveryYear(Calendar.APRIL, 1));
            Holidays.put("Independence Day",          sameDayEveryYear(Calendar.JULY, 4));
            Holidays.put("Halloween",                 Holidays.get("Reformationstag"));
            Holidays.put("Thanksgiving",              holiDayEventRule(new SimpleDateRule(Calendar.NOVEMBER, 22, Calendar.THURSDAY, true)));
            Holidays.put("Immaculate Conception of the Virgin Mary", sameDayEveryYear(Calendar.DECEMBER, 8));
            Holidays.put("Christmas Eve",             Holidays.get("Heiligabend"));
            Holidays.put("Christmas Day",             Holidays.get("1. Weihnachtsfeiertag"));
            Holidays.put("Boxing Day",                Holidays.get("2. Weihnachtsfeiertag"));
            Holidays.put("New Year's Eve",            Holidays.get("Silvester"));
        } catch (ParseException e) {}
        
        
        for (Map.Entry<String, Date[]> holiday: Holidays.entrySet()) {
            HolidayPattern.put(Pattern.compile(BODNCG + holiday.getKey() + EODNCG), holiday.getValue());
        }
    }

    /**
     * @param month value of month (Calendar.month is 0 based)
     * @param day
     * @return four years of same date starting in last year
     */
    private static Date[] sameDayEveryYear(int month, int day) {
        Date[] r = new Date[4];
        Calendar cal = CONFORM.getCalendar();
        cal.clear();
        cal.set(CURRENT_YEAR - 1, month, day); // set start in previous year
        r[0] = cal.getTime();
        for (int y = 1; y < 4; y++) {
            cal.add(Calendar.YEAR, 1);
            r[y] = cal.getTime();
        }
        return r;
    }

    /**
     * @param holidayrule
     * @return 3 years of same holiday starting in last year
     */
    private static Date[] holiDayEventRule(DateRule holidayrule) {
        Date[] r = new Date[3];
        Calendar cal = CONFORM.getCalendar();
        cal.set(CURRENT_YEAR - 1, 1, 1); // set previous year as start year
        r[0] = holidayrule.firstAfter(cal.getTime());
        for (int y = 1; y < 3; y++) {
            cal.add(Calendar.YEAR, 1);
            r[y] = holidayrule.firstAfter(cal.getTime());
        }
        return r;
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
                synchronized (CONFORM) {try {
                    dates.add(CONFORM.parse(year + "/" + (month < 10 ? "0" : "") + month + "/" + (day < 10 ? "0" : "") + day));
                } catch (ParseException e) {
                    continue;
                }}
                if (dates.size() > 100) {dates.clear(); break;} // that does not make sense
            }
            return dates;
        }
        
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
                synchronized (CONFORM) {try {
                    String datestub = "/" + (month < 10 ? "0" : "") + month + "/" + (day < 10 ? "0" : "") + day;
                    Date atThisYear = CONFORM.parse(thisyear + datestub);
                    Date atNextYear = CONFORM.parse(nextyear + datestub);
                    dates.add(atThisYear);
                    dates.add(atNextYear);
                    //dates.add(atThisYear.after(TODAY) ? atThisYear : atNextYear); // we consider these kind of dates as given for the future
                } catch (ParseException e) {
                    continue;
                }}
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
     * @param timezoneOffset
     * @return determined date or null
     */
    public static Date parseLine(final String text, final int timezoneOffset) {
        Date d = null;
        // check standard date formats
        try {d = CONFORM.parse(text);} catch (ParseException e) {}
        //if (d == null) try {d = GenericFormatter.FORMAT_SHORT_DAY.parse(text);} catch (ParseException e) {} // did not work well and fired for wrong formats; do not use
        if (d == null) try {d = GenericFormatter.FORMAT_RFC1123_SHORT.parse(text);} catch (ParseException e) {}
        if (d == null) try {d = GenericFormatter.FORMAT_ANSIC.parse(text);} catch (ParseException e) {}
            
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
