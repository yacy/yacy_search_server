// WorkTables.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.02.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
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

package net.yacy.data;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.ymark.YMarkTables;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.rwi.IndexCell;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;

public class WorkTables extends Tables {

    public final static String TABLE_API_NAME = "api";
    public final static String TABLE_API_TYPE_STEERING = "steering";
    public final static String TABLE_API_TYPE_CONFIGURATION = "configuration";
    public final static String TABLE_API_TYPE_CRAWLER = "crawler";
    public final static String TABLE_API_TYPE_DELETION = "deletion";

    public final static String TABLE_API_COL_TYPE = "type";
    public final static String TABLE_API_COL_COMMENT = "comment";
    public final static String TABLE_API_COL_DATE_RECORDING = "date_recording"; // if not present default to old date field
    public final static String TABLE_API_COL_DATE_LAST_EXEC = "date_last_exec"; // if not present default to old date field
    public final static String TABLE_API_COL_DATE_NEXT_EXEC = "date_next_exec"; // if not present default to zero
    public final static String TABLE_API_COL_DATE = "date"; // old date; do not set in new records
    public final static String TABLE_API_COL_URL = "url";
    public final static String TABLE_API_COL_APICALL_PK = "apicall_pk"; // the primary key for the table entry of that api call (not really a database field, only a name in the apicall)
    public final static String TABLE_API_COL_APICALL_COUNT = "apicall_count"; // counts how often the API was called (starts with 1)
    public final static String TABLE_API_COL_APICALL_SCHEDULE_TIME = "apicall_schedule_time"; // factor for SCHEULE_UNIT time units
    public final static String TABLE_API_COL_APICALL_SCHEDULE_UNIT = "apicall_schedule_unit"; // may be 'minutes', 'hours', 'days'
    public final static String TABLE_API_COL_APICALL_EVENT_KIND = "apicall_event_kind"; // 
    public final static String TABLE_API_COL_APICALL_EVENT_ACTION = "apicall_event_action"; // 

    public final static String TABLE_ROBOTS_NAME = "robots";

    public final static String TABLE_ACTIVECRAWLS_NAME = "crawljobsActive";
    public final static String TABLE_PASSIVECRAWLS_NAME = "crawljobsPassive";

    public final static String TABLE_SEARCH_FAILURE_NAME = "searchfl";
    public final static String TABLE_SEARCH_FAILURE_COL_URL = "url";
    public final static String TABLE_SEARCH_FAILURE_COL_DATE = "date";
    public final static String TABLE_SEARCH_FAILURE_COL_WORDS = "words";
    public final static String TABLE_SEARCH_FAILURE_COL_COMMENT = "comment";

    public YMarkTables bookmarks;

    public WorkTables(final File workPath) {
        super(workPath, 12);
        this.bookmarks = new YMarkTables(this);
    }

    /**
     * recording of a api call. stores the call parameters into the API database table
     * @param post the post arguments of the api call
     * @param servletName the name of the servlet
     * @param type name of the servlet category
     * @param comment visual description of the process
     * @return the pk of the new entry in the api table
     */
    public byte[] recordAPICall(final serverObjects post, final String servletName, final String type, final String comment) {
        // remove the apicall attributes from the post object
        String[] pks = post.remove(TABLE_API_COL_APICALL_PK);
        byte[] pk = pks == null ? null : UTF8.getBytes(pks[0]);

        // generate the apicall url - without the apicall attributes
        final String apiurl = /*"http://localhost:" + getConfig("port", "8090") +*/ "/" + servletName + "?" + post.toString();

        // read old entry from the apicall table (if exists)
        Row row = null;
        try {
            row = (pk == null) ? null : super.select(TABLE_API_NAME, pk);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }

        // insert or update entry
        try {
            if (row == null) {
                // create and insert new entry
                Data data = new Data();
                data.put(TABLE_API_COL_TYPE, UTF8.getBytes(type));
                data.put(TABLE_API_COL_COMMENT, UTF8.getBytes(comment));
                byte[] date = UTF8.getBytes(GenericFormatter.SHORT_MILSEC_FORMATTER.format());
                data.put(TABLE_API_COL_DATE_RECORDING, date);
                data.put(TABLE_API_COL_DATE_LAST_EXEC, date);
                data.put(TABLE_API_COL_URL, UTF8.getBytes(apiurl));

                // insert APICALL attributes
                data.put(TABLE_API_COL_APICALL_COUNT, "1");
                pk = super.insert(TABLE_API_NAME, data);
            } else {
                // modify and update existing entry

                // modify date attributes and patch old values
                row.put(TABLE_API_COL_DATE_LAST_EXEC, UTF8.getBytes(GenericFormatter.SHORT_MILSEC_FORMATTER.format()));
                if (!row.containsKey(TABLE_API_COL_DATE_RECORDING)) row.put(TABLE_API_COL_DATE_RECORDING, row.get(TABLE_API_COL_DATE));
                row.remove(TABLE_API_COL_DATE);

                // insert APICALL attributes
                row.put(TABLE_API_COL_APICALL_COUNT, row.get(TABLE_API_COL_APICALL_COUNT, 1) + 1);
                super.update(TABLE_API_NAME, row);
                assert pk != null;
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        ConcurrentLog.info("APICALL", apiurl);
        return pk;
    }

    /**
     * store a API call and set attributes to schedule a re-call of that API call according to a given frequence
     * This is the same as the previous method but it also computes a re-call time and stores that additionally
     * @param post the post arguments of the api call
     * @param servletName the name of the servlet
     * @param type name of the servlet category
     * @param comment visual description of the process
     * @param time the time until next scheduled execution of this api call
     * @param unit the time unit for the scheduled call
     * @return the pk of the new entry in the api table
     */
    public byte[] recordAPICall(final serverObjects post, final String servletName, final String type, final String comment, int time, String unit) {
        if (post.containsKey(TABLE_API_COL_APICALL_PK)) {
            // this api call has already been stored somewhere.
            return recordAPICall(post, servletName, type, comment);
        }
        if (time < 0 || unit == null || unit.isEmpty() || "minutes,hours,days".indexOf(unit) < 0) {
            time = 0; unit = "";
        } else {
            if (unit.equals("minutes") && time < 10) time = 10;
        }

        // generate the apicall url - without the apicall attributes
        final String apiurl = /*"http://localhost:" + getConfig("port", "8090") +*/ "/" + servletName + "?" + post.toString();
        byte[] pk = null;
        // insert entry
        try {
            // create and insert new entry
            Data data = new Data();
            data.put(TABLE_API_COL_TYPE, UTF8.getBytes(type));
            data.put(TABLE_API_COL_COMMENT, UTF8.getBytes(comment));
            byte[] date = UTF8.getBytes(GenericFormatter.SHORT_MILSEC_FORMATTER.format());
            data.put(TABLE_API_COL_DATE_RECORDING, date);
            data.put(TABLE_API_COL_DATE_LAST_EXEC, date);
            data.put(TABLE_API_COL_URL, UTF8.getBytes(apiurl));

            // insert APICALL attributes
            data.put(TABLE_API_COL_APICALL_COUNT, UTF8.getBytes("1"));
            data.put(TABLE_API_COL_APICALL_SCHEDULE_TIME, ASCII.getBytes(Integer.toString(time)));
            data.put(TABLE_API_COL_APICALL_SCHEDULE_UNIT, UTF8.getBytes(unit));
            calculateAPIScheduler(data, false); // set next execution time
            pk = super.insert(TABLE_API_NAME, data);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        } catch (final SpaceExceededException e) {
            ConcurrentLog.logException(e);
        }
        ConcurrentLog.info("APICALL", apiurl);
        return pk;
    }

    /**
     * execute an API call using a api table row which contains all essentials
     * to access the server also the host and port must be given
     * @param pks a collection of primary keys denoting the rows in the api table
     * @param host the host where the api shall be called
     * @param port the port on the host
     * @return a map of the called urls and the http status code of the api call or -1 if any other IOException occurred
     */
    public Map<String, Integer> execAPICalls(String host, int port, Collection<String> pks, final String username, final String pass) {
        // now call the api URLs and store the result status
        final HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
        client.setTimout(120000);
        Tables.Row row;
        String url;
        LinkedHashMap<String, Integer> l = new LinkedHashMap<String, Integer>();
        for (final String pk: pks) {
            row = null;
            try {
                row = select(WorkTables.TABLE_API_NAME, UTF8.getBytes(pk));
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            } catch (final SpaceExceededException e) {
                ConcurrentLog.logException(e);
            }
            if (row == null) continue;
            url = "http://" + host + ":" + port + UTF8.String(row.get(WorkTables.TABLE_API_COL_URL));
            url += "&" + WorkTables.TABLE_API_COL_APICALL_PK + "=" + UTF8.String(row.getPK());
            ConcurrentLog.info("WorkTables", "executing url: " + url);
            try {
                client.GETbytes(url, username, pass, false);
                l.put(url, client.getStatusCode());
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
                l.put(url, -1);
            }
        }
        return l;
    }

    public static int execAPICall(String host, int port, String path, byte[] pk, final String username, final String pass) {
        // now call the api URLs and store the result status
        final HTTPClient client = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent);
        client.setTimout(120000);
        String url = "http://" + host + ":" + port + path;
        if (pk != null) url += "&" + WorkTables.TABLE_API_COL_APICALL_PK + "=" + UTF8.String(pk);
        try {
            client.GETbytes(url, username, pass, false);
            return client.getStatusCode();
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return -1;
        }
    }

    /**
     * simplified call to execute a single entry in the api database table
     * @param pk the primary key of the entry
     * @param host the host where the api shall be called
     * @param port the port on the host
     * @param realm authentification realm
     * @return the http status code of the api call or -1 if any other IOException occurred
     */
    public int execAPICall(String pk, String host, int port, final String username, final String pass) {
        ArrayList<String> pks = new ArrayList<String>();
        pks.add(pk);
        Map<String, Integer> m = execAPICalls(host, port, pks, username, pass);
        if (m.isEmpty()) return -1;
        return m.values().iterator().next().intValue();
    }

    final static long hour = 1000L * 60L * 60L;
    final static long day = hour * 24L;
    
    /**
     * calculate the execution time in a api call table based on given scheduling time and last execution time
     * @param row the database row in the api table
     * @param update if true then the next execution time is based on the latest computed execution time; otherwise it is based on the last execution time
     */
    public static void calculateAPIScheduler(Tables.Data row, boolean update) {
        Date date = row.containsKey(WorkTables.TABLE_API_COL_DATE) ? row.get(WorkTables.TABLE_API_COL_DATE, (Date) null) : null;
        date = update ? row.get(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, date) : row.get(WorkTables.TABLE_API_COL_DATE_LAST_EXEC, date);
        if (date == null) return;
        long d = date.getTime();
        
        final String kind = row.get(WorkTables.TABLE_API_COL_APICALL_EVENT_KIND, "off");
        if ("off".equals(kind)) {
            int time = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 1);
            if (time <= 0) {
                row.put(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, "");
                return;
            }
            String unit = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_UNIT, "days");
            if (unit.equals("minutes")) d += 60000L * Math.max(10, time);
            if (unit.equals("hours"))   d += 60000L * 60L * time;
            if (unit.equals("days"))    d += 60000L * 60L * 24L * time;
            if (d < System.currentTimeMillis()) d = System.currentTimeMillis() + 600000L;
            d -= d % 60000; // remove seconds
        } else {
            String action = row.get(WorkTables.TABLE_API_COL_APICALL_EVENT_ACTION, "startup");
            if (!"startup".equals(action)) try {
                SimpleDateFormat dateFormat  = new SimpleDateFormat("yyyyMMddHHmm");
                d = dateFormat.parse(dateFormat.format(new Date()).substring(0, 8) + action).getTime();
                if (d < System.currentTimeMillis()) d += day;
            } catch (final ParseException e) {} else {
                row.put(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, "");
                return;
            }
        }
        row.put(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, new Date(d));
    }

    public void failURLsRegisterMissingWord(IndexCell<WordReference> indexCell, final DigestURL url, HandleSet queryHashes, final String reason) {

        // remove words from index
        if (indexCell != null) {
            for (final byte[] word: queryHashes) {
                indexCell.removeDelayed(word, url.hash());
            }
        }

        // insert information about changed url into database
        try {
            // create and insert new entry
            Data data = new Data();
            byte[] date = UTF8.getBytes(GenericFormatter.SHORT_MILSEC_FORMATTER.format());
            data.put(TABLE_SEARCH_FAILURE_COL_URL, url.toNormalform(true));
            data.put(TABLE_SEARCH_FAILURE_COL_DATE, date);
            data.put(TABLE_SEARCH_FAILURE_COL_WORDS, queryHashes.export());
            data.put(TABLE_SEARCH_FAILURE_COL_COMMENT, UTF8.getBytes(reason));
            super.insert(TABLE_SEARCH_FAILURE_NAME, url.hash(),  data);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
    }

    public boolean failURLsContains(byte[] urlhash) {
        try {
            return super.has(TABLE_SEARCH_FAILURE_NAME, urlhash);
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            return false;
        }
    }

    /**
     * cleanup cached failed searchs older then timeout
     */
    public void cleanFailURLS(long timeout) {
    	if (timeout >= 0) {
    		try {
    			Row row;
    			Date date;
    			Iterator<Row> iter = this.iterator(WorkTables.TABLE_SEARCH_FAILURE_NAME);
				while (iter.hasNext()) {
					row = iter.next();
					date = new Date();
					date = row.get(TABLE_SEARCH_FAILURE_COL_DATE, date);
					if(date.before(new Date(System.currentTimeMillis() - timeout))) {
						this.delete(TABLE_SEARCH_FAILURE_NAME, row.getPK());
					}
				}
			} catch (final IOException e) {
	            ConcurrentLog.logException(e);
			}
    	}
    }

    public static Map<byte[], String> commentCache(Switchboard sb) {
        Map<byte[], String> comments = new TreeMap<byte[], String>(Base64Order.enhancedCoder);
        Iterator<Tables.Row> i;
        try {
            i = sb.tables.iterator(WorkTables.TABLE_API_NAME);
            Tables.Row row;
            while (i.hasNext()) {
                row = i.next();
                comments.put(row.getPK(), UTF8.String(row.get(WorkTables.TABLE_API_COL_COMMENT)));
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        return comments;
    }
}
