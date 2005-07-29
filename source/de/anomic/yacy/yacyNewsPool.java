// yacyNewsActions.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 13.07.2005
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notice above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.yacy;

import java.io.File;
import java.io.IOException;

public class yacyNewsPool {
    
    public static final int INCOMING_DB  = 0;
    public static final int PROCESSED_DB = 1;
    public static final int OUTGOING_DB  = 2;
    public static final int PUBLISHED_DB = 3;
    
    private yacyNewsDB newsDB;
    private yacyNewsQueue outgoingNews, publishedNews, incomingNews, processedNews;
    private int maxDistribution;
    
    public yacyNewsPool(File yacyDBPath, int bufferkb) throws IOException {
        newsDB = new yacyNewsDB(new File(yacyDBPath, "news0.db"), bufferkb);
        outgoingNews  = new yacyNewsQueue(new File(yacyDBPath, "newsOut0.stack"), newsDB);
        publishedNews = new yacyNewsQueue(new File(yacyDBPath, "newsPublished0.stack"), newsDB);
        incomingNews  = new yacyNewsQueue(new File(yacyDBPath, "newsIn0.stack"), newsDB);
        processedNews = new yacyNewsQueue(new File(yacyDBPath, "newsProcessed0.stack"), newsDB);
        maxDistribution = 30;
    }
    
    public void enqueueMyNews(yacyNewsRecord record) throws IOException {
        if (newsDB.get(record.id()) == null) outgoingNews.push(record);
    }
    
    public yacyNewsRecord dequeueMyNews() throws IOException {
        // generate a record for next peer-ping
        if (outgoingNews.size() == 0) return null;
        yacyNewsRecord record = outgoingNews.topInc();
        if (record.distributed() >= maxDistribution) {
            // move record to its final position. This is only for history
            publishedNews.push(outgoingNews.pop(0));
        }
        return record;
    }
    
    public void enqueueGlobalNews(yacyNewsRecord record) throws IOException {
        if (newsDB.get(record.id()) == null) incomingNews.push(record);
    }
    
    public yacyNewsRecord getGlobalNews(int job) throws IOException {
        return incomingNews.top(job);
    }
    
    public synchronized boolean removeGlobalNews(String id) throws IOException {
        yacyNewsRecord record;
        for (int i = 0; i < incomingNews.size(); i++) {
            record = incomingNews.top(i);
            if (record.id().equals(id)) {
                incomingNews.pop(i);
                processedNews.push(record);
                return true;
            }
        }
        return false;
    }
    
    public int size(int dbKey) {
        switch (dbKey) {
            case OUTGOING_DB:   return outgoingNews.size();
            case PUBLISHED_DB:  return publishedNews.size();
            case INCOMING_DB:	return incomingNews.size();
            case PROCESSED_DB:  return processedNews.size();
            default: return -1;
        }
    }
    
    public yacyNewsRecord get(int dbKey, int element) throws IOException {
        switch (dbKey) {
            case OUTGOING_DB:   return outgoingNews.top(element);
            case PUBLISHED_DB:  return publishedNews.top(element);
            case INCOMING_DB:	return incomingNews.top(element);
            case PROCESSED_DB:  return processedNews.top(element);
            default: return null;
        }
    }
    
}
