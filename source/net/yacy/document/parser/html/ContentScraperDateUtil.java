package net.yacy.document.parser.html;

import net.yacy.cora.date.CustomISO8601Formatter;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.storage.SizeLimitedMap;
import net.yacy.cora.util.ConcurrentLog;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContentScraperDateUtil {

    private final static ConcurrentLog log = new ConcurrentLog("SCRAPER_DATE");

    private static final Pattern URL_DATE_REGEX =
            Pattern.compile(
                    "/(19\\d{2}|20\\d{2})[-/]?(0[1-9]|1[0-2]|January|February|March|April|May|June|July|August|September|October|November|December)((?:[-/]?(0[1-9]|[12]\\d|3[01]))?)/",
                    Pattern.CASE_INSENSITIVE);

    public static Date getDate(DigestURL root, SizeLimitedMap<String, String> metas, int timezoneOffset, List<Date> startDates, Date lastModified) {
        var currentDate = new Date();

        var date = new AtomicReference<Date>();

        // root url like: https://denikn.cz/1188398/nova-socialni-sit-threads-ma-vazne-nedostatky-ale-dokonale-nacasovani-meta-ji-chce-nahradit-twitter/
        parseDate("<script id=\"schema\" type=\"application/ld+json\">{...,\"datePublished\":\"2023-07-10T14:40:52+02:00\",..}</script>",
                metas.get("script.datepublished"),
                timezoneOffset, date);

        parseDate("<meta name=\"article:published_time\" content=\"YYYY-MM-DD...\" />",
                metas.get("article:published_time"),
                timezoneOffset, date);

        parseDate("<meta name=\"DC.date.issued\" content=\"YYYY-MM-DD...\" />",
                metas.get("dc.date.issued"),
                timezoneOffset, date);
        parseDate("<meta name=\"DC.date.modified\" content=\"YYYY-MM-DD...\" />",
                metas.get("dc.date.modified"),
                timezoneOffset, date);
        parseDate("<meta name=\"DC.date.created\" content=\"YYYY-MM-DD...\" />",
                metas.get("dc.date.created"),
                timezoneOffset, date);
        parseDate("<meta name=\"DC.date\" content=\"YYYY-MM-DD...\" />",
                metas.get("dc.date"),
                timezoneOffset, date);

        String content = root.toString();
        if (date.get() == null && content != null) {
            // Regex by https://github.com/codelucas/newspaper/blob/master/newspaper/urls.py
            Matcher dateMatcher = URL_DATE_REGEX.matcher(content);
            if (dateMatcher.find()) {
                // root url like: http://www.dailytimes.com.pk/digital_images/400/2015-11-26/norway-return-number-of-asylum-seekers-to-pakistan-1448538771-7363.jpg
                int year = Integer.parseInt(dateMatcher.group(1));
                String monthPart = dateMatcher.group(2);
                String dayPart = dateMatcher.group(4); // scraper will be null (or empty?) if the day is not present

                int monthValue;
                try {
                    // Try parsing as integer (e.g., "01")
                    monthValue = Integer.parseInt(monthPart);
                } catch (NumberFormatException e) {
                    // If not an integer, it's a month name (e.g., "January")
                    monthValue = Month.valueOf(monthPart.toUpperCase(Locale.US)).getValue();
                }

                int dayValue;
                if (dayPart != null && !dayPart.isEmpty()) {
                    dayValue = Integer.parseInt(dayPart);
                } else {
                    dayValue = 1; // Deduce as the first day of the month
                    log.info("Day part missing, deduced as the first day of the month in URL: +'" + content + "'");
                }

                try {
                    LocalDate parsedDate = LocalDate.of(year, monthValue, dayValue);
                    return CustomISO8601Formatter.CUSTOM_FORMATTER.parse(parsedDate.format(DateTimeFormatter.ISO_DATE), timezoneOffset).getTime();
                } catch (DateTimeException | ParseException e) {
                    log.warn("Error: " + e.getMessage() + " (probably invalid day for month)");
                }
            }
        }

        if (date.get() == null && lastModified != null) {
            date.set(lastModified);
        }

        if (date.get() == null) {
            // find the most frequent date in starDates in the content
            date.set(findMostFrequentDate(startDates, currentDate));
            if (date.get() != null) {
                log.info("Publish date found in startDates in the page content with value: '" + date + "'");
                return date.get();
            }
        } else {
            return date.get();
        }

        log.info("Publish date not found, current date used: '" + currentDate + "'");

        return currentDate;
    }

    private static void parseDate(String tag, String date, int timezoneOffset, AtomicReference<Date> result) {
        if (result.get() != null) {
            return;
        }
        if (date != null) {
            try {
                log.info("Publish date found according to: '" + tag + "' pattern with value: '" + date + "'");
                result.set(CustomISO8601Formatter.CUSTOM_FORMATTER.parse(date, timezoneOffset).getTime());
            } catch (final ParseException e) {
                // Intentionally empty due to performance reasons
            }
        }
    }

    private static Date findMostFrequentDate(List<Date> dates, Date currentDate){
        if (dates == null || dates.isEmpty()) {
            return null; // Handle an empty or null list
        }

        Map<Date, Integer> dateCounts = new HashMap<>();
        for (Date date : dates) {
            dateCounts.put(date, dateCounts.getOrDefault(date, 0) + 1);
        }

        int maxCount = Collections.max(dateCounts.values()); // Find the maximum count

        List<Date> maxDates = new java.util.ArrayList<>();
        for (Map.Entry<Date, Integer> entry : dateCounts.entrySet()) {
            if (entry.getValue() == maxCount && !entry.getKey().before(currentDate)) {
                maxDates.add(entry.getKey());
            }
        }

        if (maxDates.isEmpty()) {
            return null;
        }

        return Collections.max(maxDates);
    }

}
