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
    public final static String TABLE_API_COL_DATE = "date";
    public final static String TABLE_API_COL_URL = "url";
    
    
    public WorkTables(File workPath) {
        super(workPath, 12);
    }
    
    public void recordAPICall(final serverObjects post, final String servletName, String type, String comment) {
        String apiurl = /*"http://localhost:" + getConfig("port", "8080") +*/ "/" + servletName + "?" + post.toString();
        try {
            super.insert(
                    TABLE_API_NAME,
                    TABLE_API_COL_TYPE, type.getBytes(),
                    TABLE_API_COL_COMMENT, comment.getBytes(),
                    TABLE_API_COL_DATE, DateFormatter.formatShortMilliSecond(new Date()).getBytes(),
                    TABLE_API_COL_URL, apiurl.getBytes()
                    );
        } catch (IOException e) {
            Log.logException(e);
        } catch (NullPointerException e) {
        	Log.logException(e);
        }
        Log.logInfo("APICALL", apiurl);
    }
    
}
