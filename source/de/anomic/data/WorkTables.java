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
import java.util.Date;

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
    
    
    public WorkTables(final File workPath) {
        super(workPath, 12);
    }
    
    public void recordAPICall(final serverObjects post, final String servletName, final String type, final String comment) {
        // remove the apicall attributes from the post object
        String pk    = post.remove(TABLE_API_COL_APICALL_PK);
        String count = post.remove(TABLE_API_COL_APICALL_COUNT);
        if (count == null) count = "1";
        String time  = post.remove(TABLE_API_COL_APICALL_SCHEDULE_TIME);
        String unit  = post.remove(TABLE_API_COL_APICALL_SCHEDULE_UNIT);
        if (time == null || unit == null || unit.length() == 0 || "minues,hours,days".indexOf(unit) < 0) {
            time = ""; unit = "";
        }
        
        // generate the apicall url - without the apicall attributes
        final String apiurl = /*"http://localhost:" + getConfig("port", "8080") +*/ "/" + servletName + "?" + post.toString();

        // read old entry from the apicall table (if exists)
        Row row = null;
        try {
            row = (pk == null) ? null : super.select(TABLE_API_NAME, pk.getBytes());
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        
        // insert or update entry
        try {
            if (row != null) {
                // modify and update existing entry

                // modify date attributes and patch old values
                row.put(TABLE_API_COL_DATE_LAST_EXEC, DateFormatter.formatShortMilliSecond(new Date()).getBytes());
                if (!row.containsKey(TABLE_API_COL_DATE_RECORDING)) row.put(TABLE_API_COL_DATE_RECORDING, row.get(TABLE_API_COL_DATE));
                row.remove(TABLE_API_COL_DATE);
                
                // insert APICALL attributes 
                row.put(TABLE_API_COL_APICALL_COUNT, count.getBytes());
                row.put(TABLE_API_COL_APICALL_SCHEDULE_TIME, time.getBytes());
                row.put(TABLE_API_COL_APICALL_SCHEDULE_UNIT, unit.getBytes());
                super.update(TABLE_API_NAME, row);
            } else {
                // create and insert new entry
                Data data = new Data();
                data.put(TABLE_API_COL_TYPE, type.getBytes());
                data.put(TABLE_API_COL_COMMENT, comment.getBytes());
                byte[] date = DateFormatter.formatShortMilliSecond(new Date()).getBytes();
                data.put(TABLE_API_COL_DATE_RECORDING, date);
                data.put(TABLE_API_COL_DATE_LAST_EXEC, date);
                data.put(TABLE_API_COL_URL, apiurl.getBytes());
                
                // insert APICALL attributes 
                data.put(TABLE_API_COL_APICALL_COUNT, count.getBytes());
                data.put(TABLE_API_COL_APICALL_SCHEDULE_TIME, time.getBytes());
                data.put(TABLE_API_COL_APICALL_SCHEDULE_UNIT, unit.getBytes());
                super.insert(TABLE_API_NAME, data);
            }
        } catch (IOException e) {
            Log.logException(e);
        } catch (RowSpaceExceededException e) {
            Log.logException(e);
        }
        Log.logInfo("APICALL", apiurl);
    }
    
}
