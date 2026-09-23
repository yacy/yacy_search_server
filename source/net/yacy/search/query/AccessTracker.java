/**
 *  AccessTracker
 *  an interface for Adaptive Replacement Caches
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.08.2009 at https://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
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

package net.yacy.search.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.input.BoundedInputStream;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.WordCache;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.search.EventTracker;

public class AccessTracker {

    private final static long DUMP_PERIOD = 3600000L;
    private final static int DUMP_SIZE = 50000;

    private static final Pattern DUMP_RECORD = Pattern.compile("([0-9]{14}) ([0-9]{1,5}) (.*)", Pattern.DOTALL);
    private static final DateTimeFormatter DUMP_TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuuMMddHHmmss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private static final int minSize = 100;
    private static final int maxSize = 1000;
    private static final int maxAge = 24 * 60 * 60 * 1000;
    
    public static class QueryEvent {
        final public String address;
        final public String userAgent;
        final public String query;
        final public Date date;
        final public short offset;
        final public short requestedResults;
        final public short returnedResults;
        final public short knownResults;
        final public short executionTime;
        
        public QueryEvent(
                final String address, final String userAgent,
                final String query, final Date date,
                final short offset, final short requestedResults,
                final short returnedResults, final short knownResults,
                final short executionTime) {
            this.address = address;
            this.userAgent = userAgent;
            this.query = query;
            this.date = date;
            this.offset = offset;
            this.requestedResults = requestedResults;
            this.returnedResults = returnedResults;
            this.knownResults = knownResults;
            this.executionTime = executionTime;
        }
    }
    
    public enum Location {local, remote}

    private static final LinkedList<QueryParams> localSearches = new LinkedList<QueryParams>();
    private static final LinkedList<QueryParams> remoteSearches = new LinkedList<QueryParams>();
    private static final ArrayList<String> log = new ArrayList<String>();
    private static long lastLogDump = System.currentTimeMillis();
    private static long localCount = 0;
    private static long remoteCount = 0;
    private static File dumpFile = null;

    public static void setDumpFile(File f) {
        dumpFile = f;
    }

    public static File getDumpFile() {
        return dumpFile;
    }

    public static void add(final Location location, final QueryParams query, int resultCount) {
        if (location == Location.local) synchronized (localSearches) {add(localSearches, query, resultCount);}
        if (location == Location.remote) synchronized (remoteSearches) {add(remoteSearches, query, resultCount);}
    }

    private static void add(final LinkedList<QueryParams> list, final QueryParams query, int resultCount) {
        // learn that this word can be a word completion for the DidYouMeanLibrary
        String queryString = query.getQueryGoal().getQueryString(false);
        if (resultCount > 10 && queryString != null && queryString.length() > 0) {
            final StringBuilder sb = new StringBuilder(queryString);
            sb.append(queryString);
            WordCache.learn(sb);
        }

        // add query to statistics list
        list.add(query);

        // shrink dump list but keep essentials in dump
        while (list.size() > maxSize || (!list.isEmpty() && MemoryControl.shortStatus())) {
            synchronized (list) {
                if (!list.isEmpty()) addToDump(list.removeFirst(), resultCount); else break;
            }
        }

        // if the list is small we can terminate
        if (list.size() <= minSize) return;

        // if the list is large we look for too old entries
        final long timeout = System.currentTimeMillis() - maxAge;
        while (!list.isEmpty()) {
            final QueryParams q = list.getFirst();
            if (q.starttime > timeout) break;
            addToDump(list.removeFirst(), resultCount);
        }
    }

    public static Iterator<QueryParams> get(final Location location) {
        if (location == Location.local) return localSearches.descendingIterator();
        if (location == Location.remote) return remoteSearches.descendingIterator();
        return null;
    }

    public static long size(final Location location) {
        if (location == Location.local) synchronized (localSearches) {return localCount + localSearches.size();}
        if (location == Location.remote) synchronized (remoteSearches) {return remoteCount + remoteSearches.size();}
        return 0;
    }

    private static void addToDump(final QueryParams query, long resultCount) {
        String queryString = query.getQueryGoal().getQueryString(false);
        if (queryString == null || queryString.isEmpty()) return;
        addToDump(queryString, resultCount, new Date(query.starttime), "qs");
    }

    public static void addToDump(String querystring, long resultCount) {
        addToDump(querystring, resultCount, new Date(), "qs");
    }

    /**
     * Add a line to the queries log
     *
     * @param querystring the original query
     * @param resultcount found results
     * @param d start time
     * @param querySyntax used syntax (qs=normal querstring, sq=solr querystring,
     */
    public static void addToDump(String querystring, long resultcount, Date d, String querySyntax) {
        //if (query.resultcount == 0) return;
        if (querystring == null || querystring.isEmpty()) return;
        final StringBuilder sb = new StringBuilder(40);
        sb.append(GenericFormatter.SHORT_SECOND_FORMATTER.format(d));
        sb.append(' ');
        sb.append(resultcount);
        sb.append(' ');
        sb.append(querySyntax);
        sb.append(' ');
        // A query may contain line breaks, but each persisted record must occupy one line.
        sb.append(querystring.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' '));
        synchronized (log) {
            log.add(sb.toString());
        }
        if (log.size() > DUMP_SIZE || lastLogDump + DUMP_PERIOD < System.currentTimeMillis()) {
            dumpLog();
        }
    }

    public static void dumpLog() {
        lastLogDump = System.currentTimeMillis();
    	localCount += localSearches.size();
    	synchronized (localSearches) {
    	    while (!localSearches.isEmpty()) {
                addToDump(localSearches.removeFirst(), 0);
            }
    	}
        remoteCount += remoteSearches.size();
        synchronized (remoteSearches) {
            while (!remoteSearches.isEmpty()) {
                addToDump(remoteSearches.removeFirst(), 0);
            }
        }
        Thread t = new Thread("AccessTracker.dumpLog") {
            @Override
            public void run() {
                ArrayList<String> logCopy = new ArrayList<String>();
                synchronized (log) {
                    logCopy.addAll(log);
                    log.clear();
                }
                RandomAccessFile raf = null;
                try {
                    raf = new RandomAccessFile(dumpFile, "rw");
                    raf.seek(raf.length());
                    for (final String s: logCopy) {
                        raf.write(UTF8.getBytes(s));
                        raf.writeByte(10);
                    }
                    logCopy.clear();
                } catch (final FileNotFoundException e) {
                    ConcurrentLog.logException(e);
                } catch (final IOException e) {
                    ConcurrentLog.logException(e);
                } finally {
                    if (raf != null) try {raf.close();} catch (final IOException e) {}
                }
            }
        };
        t.start();
    }
    
    /**
     * read the log and return a list of lines which are equal or greater than
     * the from-date and smaller than the to-date
     * @param f the dump file
     * @param from the left boundary of the sequence to search for (included)
     * @param to the right boundary of the sequence to search for (excluded)
     * @return a list of lines within the given dates
     */
    public static List<EventTracker.Event> readLog(File f, Date from, Date to) {
        final List<EventTracker.Event> events = new ArrayList<>();
        if (!from.before(to)) return events;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            final long length = raf.length();
            final long seekFrom = binarySearch(raf, from, 0, length);
            final long seekTo = binarySearch(raf, to, seekFrom, length);
            raf.seek(seekFrom);
            // Buffer the selected byte range without copying the entire range into a byte array.
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    BoundedInputStream.builder().setInputStream(new FileInputStream(raf.getFD()))
                            .setMaxCount(seekTo - seekFrom).get(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final EventTracker.Event event = parseLogRecord(line);
                    if (event != null && event.getTime() >= from.getTime() && event.getTime() < to.getTime()) {
                        events.add(event);
                    }
                }
            }
        } catch (final FileNotFoundException e) {
            ConcurrentLog.logException(e);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        return events;
    }

    private static EventTracker.Event parseLogRecord(final String line) {
        final Matcher record = DUMP_RECORD.matcher(line);
        if (!record.matches()) return null;
        try {
            final long time = LocalDateTime.parse(record.group(1), DUMP_TIMESTAMP)
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
            final int hits = Integer.parseInt(record.group(2));
            return new EventTracker.Event(Long.valueOf(time), 0, "query", record.group(3), hits);
        } catch (final DateTimeParseException | NumberFormatException e) {
            return null;
        }
    }

    /**
     * Find the first valid record at or after the given date in a chronological log.
     * Malformed physical lines are ignored, including legacy multiline query continuations.
     * @param raf the random access file
     * @param date the given date
     * @param l first seek position to look (included, we expect a date there or after the position l)
     * @param r last seek position to look (excluded, we do not expect that there is a date)
     * @return the first matching record position, or the original right boundary when none exists
     */
    private static long binarySearch(RandomAccessFile raf, Date date, long l, long r) throws IOException {
        final long end = r;
        while (l < r) {
            final long middle = l + (r - l) / 2;
            long start = seekLB(raf, middle);
            raf.seek(start);
            EventTracker.Event event = null;
            while (start < r) {
                final String line = raf.readLine();
                if (line == null) break;
                event = parseLogRecord(line);
                if (event != null) break;
                start = raf.getFilePointer();
            }
            if (event == null) {
                r = middle;
            } else if (event.getTime() < date.getTime()) {
                l = raf.getFilePointer();
            } else {
                r = start;
            }
        }
        // The converged boundary may precede malformed lines; return a complete valid record.
        raf.seek(l);
        while (l < end) {
            final String line = raf.readLine();
            if (line == null) break;
            final EventTracker.Event event = parseLogRecord(line);
            if (event != null && event.getTime() >= date.getTime()) return l;
            l = raf.getFilePointer();
        }
        return end;
    }
    
    /**
     * find the beginning of a line
     * @param raf the random access file
     * @param x any seek position in the file
     * @return the seek position of the beginning of a line smaller or equal to x
     * @throws IOException
     */
    private static long seekLB(RandomAccessFile raf, long x) throws IOException {
        while (x > 0) {
            raf.seek(x - 1);
            final int previous = raf.read();
            if (previous == '\n') break;
            if (previous == '\r') {
                if (raf.read() == '\n') x++;
                break;
            }
            x--;
        }
        return x;
    }
    
    public static void main(String[] args) {
        // i.e. /Users/admin/git/rc1/DATA/LOG/queries.log 20140522135156 20140614223118
        String file = args[0];
        Date from;
        try {
            from = GenericFormatter.SHORT_SECOND_FORMATTER.parse(args[1], 0).getTime();
            Date to = GenericFormatter.SHORT_SECOND_FORMATTER.parse(args[2], 0).getTime();
            List<EventTracker.Event> dump = readLog(new File(file), from, to);
            for (EventTracker.Event s: dump) System.out.println(s.toString());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
}
