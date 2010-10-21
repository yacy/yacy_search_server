// Work.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 04.02.2010 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 6539 $
// $LastChangedBy: low012 $
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

package de.anomic.data;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import net.yacy.cora.protocol.http.HTTPClient;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.index.RowSpaceExceededException;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;
import de.anomic.server.serverObjects;

public class WorkTables extends Tables {
	
    public final static String TABLE_API_NAME = "api";
    public final static String TABLE_API_TYPE_STEERING = "steering";
    public final static String TABLE_API_TYPE_CONFIGURATION = "configuration";
    public final static String TABLE_API_TYPE_CRAWLER = "crawler";
    
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
    public final static String TABLE_API_COL_APICALL_SCHEDULE_UNIT= "apicall_schedule_unit"; // may be 'minutes', 'hours', 'days'

    public final static String TABLE_ROBOTS_NAME = "robots";
    
    public final static String TABLE_ACTIVECRAWLS_NAME = "crawljobsActive";
    public final static String TABLE_PASSIVECRAWLS_NAME = "crawljobsPassive";
    
    public YMarkTables bookmarks;
    
    public WorkTables(final File workPath) {
        super(workPath, 12);
        this.bookmarks = new YMarkTables(this);
    }
    
    public void clear(final String tablename) throws IOException {
    	super.clear(tablename);
    	this.bookmarks.cleanCache(tablename);
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
        String pks = post.remove(TABLE_API_COL_APICALL_PK);
        byte[] pk = pks == null ? null : pks.getBytes();
        
        // generate the apicall url - without the apicall attributes
        final String apiurl = /*"http://localhost:" + getConfig("port", "8080") +*/ "/" + servletName + "?" + post.toString();

        // read old entry from the apicall table (if exists)
        Row row = null;
        try {
            row = (pk == null) ? null : super.select(TABLE_API_NAME, pk);
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        
        // insert or update entry
        try {
            if (row == null) {
                // create and insert new entry
                Data data = new Data();
                data.put(TABLE_API_COL_TYPE, type.getBytes());
                data.put(TABLE_API_COL_COMMENT, comment.getBytes());
                byte[] date = DateFormatter.formatShortMilliSecond(new Date()).getBytes();
                data.put(TABLE_API_COL_DATE_RECORDING, date);
                data.put(TABLE_API_COL_DATE_LAST_EXEC, date);
                data.put(TABLE_API_COL_URL, apiurl.getBytes());
                
                // insert APICALL attributes 
                data.put(TABLE_API_COL_APICALL_COUNT, "1");
                pk = super.insert(TABLE_API_NAME, data);
            } else {
                // modify and update existing entry

                // modify date attributes and patch old values
                row.put(TABLE_API_COL_DATE_LAST_EXEC, DateFormatter.formatShortMilliSecond(new Date()).getBytes());
                if (!row.containsKey(TABLE_API_COL_DATE_RECORDING)) row.put(TABLE_API_COL_DATE_RECORDING, row.get(TABLE_API_COL_DATE));
                row.remove(TABLE_API_COL_DATE);
                
                // insert APICALL attributes 
                row.put(TABLE_API_COL_APICALL_COUNT, row.get(TABLE_API_COL_APICALL_COUNT, 1) + 1);
                super.update(TABLE_API_NAME, row);
                assert pk != null;
            }
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        Log.logInfo("APICALL", apiurl);
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
        if (time < 0 || unit == null || unit.length() == 0 || "minutes,hours,days".indexOf(unit) < 0) {
            time = 0; unit = "";
        } else {
            if (unit.equals("minutes") && time < 10) time = 10;
        }
        
        // generate the apicall url - without the apicall attributes
        final String apiurl = /*"http://localhost:" + getConfig("port", "8080") +*/ "/" + servletName + "?" + post.toString();
        byte[] pk = null;
        // insert entry
        try {
            // create and insert new entry
            Data data = new Data();
            data.put(TABLE_API_COL_TYPE, type.getBytes());
            data.put(TABLE_API_COL_COMMENT, comment.getBytes());
            byte[] date = DateFormatter.formatShortMilliSecond(new Date()).getBytes();
            data.put(TABLE_API_COL_DATE_RECORDING, date);
            data.put(TABLE_API_COL_DATE_LAST_EXEC, date);
            data.put(TABLE_API_COL_URL, apiurl.getBytes());
            
            // insert APICALL attributes 
            data.put(TABLE_API_COL_APICALL_COUNT, "1".getBytes());
            data.put(TABLE_API_COL_APICALL_SCHEDULE_TIME, Integer.toString(time).getBytes());
            data.put(TABLE_API_COL_APICALL_SCHEDULE_UNIT, unit.getBytes());
            calculateAPIScheduler(data, false); // set next execution time
            pk = super.insert(TABLE_API_NAME, data);
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        Log.logInfo("APICALL", apiurl);
        return pk;
    }
    
    /**
     * execute an API call using a api table row which contains all essentials
     * to access the server also the host, port and the authentication realm must be given
     * @param pks a collection of primary keys denoting the rows in the api table
     * @param host the host where the api shall be called
     * @param port the port on the host
     * @param realm authentification realm
     * @return a map of the called urls and the http status code of the api call or -1 if any other IOException occurred
     */
    public Map<String, Integer> execAPICalls(String host, int port, String realm, Collection<String> pks) {
        // now call the api URLs and store the result status
        final HTTPClient client = new HTTPClient();
        client.setRealm(realm);
        client.setTimout(120000);
        LinkedHashMap<String, Integer> l = new LinkedHashMap<String, Integer>();
        for (String pk: pks) {
            Tables.Row row = null;
            try {
                row = select(WorkTables.TABLE_API_NAME, pk.getBytes());
            } catch (IOException e) {
                Log.logException(e);
            } catch (RowSpaceExceededException e) {
                Log.logException(e);
            }
            if (row == null) continue;
            String url = "http://" + host + ":" + port + new String(row.get(WorkTables.TABLE_API_COL_URL));
            url += "&" + WorkTables.TABLE_API_COL_APICALL_PK + "=" + new String(row.getPK());
            try {
                client.GETbytes(url);
                l.put(url, client.getStatusCode());
            } catch (IOException e) {
                Log.logException(e);
                l.put(url, -1);
            }
        }
        return l;
    }
    
    public static int execAPICall(String host, int port, String realm, String path, byte[] pk) {
        // now call the api URLs and store the result status
        final HTTPClient client = new HTTPClient();
        client.setRealm(realm);
        client.setTimout(120000);
        String url = "http://" + host + ":" + port + path;
        if (pk != null) url += "&" + WorkTables.TABLE_API_COL_APICALL_PK + "=" + new String(pk);
        try {
            client.GETbytes(url);
            return client.getStatusCode();
        } catch (IOException e) {
            Log.logException(e);
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
    public int execAPICall(String pk, String host, int port, String realm) {
        ArrayList<String> pks = new ArrayList<String>();
        pks.add(pk);
        Map<String, Integer> m = execAPICalls(host, port, realm, pks);
        if (m.isEmpty()) return -1;
        return m.values().iterator().next().intValue();
    }

    /**
     * calculate the execution time in a api call table based on given scheduling time and last execution time
     * @param row the database row in the api table
     * @param update if true then the next execution time is based on the latest computed execution time; othervise it is based on the last execution time
     */
    public static void calculateAPIScheduler(Tables.Data row, boolean update) {
        Date date = row.containsKey(WorkTables.TABLE_API_COL_DATE) ? row.get(WorkTables.TABLE_API_COL_DATE, (Date) null) : null;
        date = update ? row.get(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, date) : row.get(WorkTables.TABLE_API_COL_DATE_LAST_EXEC, date);
        int time = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 1);
        if (time <= 0) {
            row.put(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, "");
            return;
        }
        String unit = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_UNIT, "days");
        long d = date.getTime();
        if (unit.equals("minutes")) d += 60000L * Math.max(10, time);
        if (unit.equals("hours"))   d += 60000L * 60L * time;
        if (unit.equals("days"))    d += 60000L * 60L * 24L * time;
        if (d < System.currentTimeMillis()) d = System.currentTimeMillis() + 600000L;
        d -= d % 60000; // remove seconds
        row.put(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, new Date(d));
    }
}
