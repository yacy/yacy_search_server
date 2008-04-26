// yacyNewsActions.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import de.anomic.index.indexReferenceBlacklist;
import de.anomic.plasma.plasmaSwitchboard;

public class yacyNewsPool {
    
    public static final int INCOMING_DB  = 0;
    public static final int PROCESSED_DB = 1;
    public static final int OUTGOING_DB  = 2;
    public static final int PUBLISHED_DB = 3;
    
    /* ========================================================================
     * CATEGORIES for YACY NEWS
     * ======================================================================== */
    /* ------------------------------------------------------------------------
     * PROFILE related CATEGORIES
     * ------------------------------------------------------------------------ */
    public static final String CATEGORY_PROFILE = "prfl";
    /**
     * a profile entry was updated (implemented)
     */
    public static final String CATEGORY_PROFILE_UPDATE = "prfleupd";
    /**
    * a peer starts up and renews its profile broadcast; used to implement supporter page
    */
    public static final String CATEGORY_PROFILE_BROADCAST = "prflecst";
    /**
     * a peer has done something good (i.e. served good search results)
     * and gets a positive vote so it can rise on the supporter page
     */
    public static final String CATEGORY_PROFILE_VOTE_GOOD = "prflegvt";
    /**
     * a peer has done something bad (i.e. spammed) and gets a negative vote
     */
    public static final String CATEGORY_PROFILE_VOTE_BAD = "prflebvt";
    
    /* ------------------------------------------------------------------------
     * CRAWLING related CATEGORIES
     * ------------------------------------------------------------------------ */    
    public static final String CATEGORY_CRAWL = "crwl";
    /**
     * a crawl with remote indexing was startet
     */
    public static final String CATEGORY_CRAWL_START = "crwlstrt";
    /**
     * a crawl with remote indexing was stopped
     */
    public static final String CATEGORY_CRAWL_STOP = "crwlstop";
    /**
     * a comment on a crawl with remote indexing
     */
    public static final String CATEGORY_CRAWL_COMMENT = "crwlcomm";
    
    /* ------------------------------------------------------------------------
     * BLACKLIST related CATEGORIES
     * ------------------------------------------------------------------------ */  
    public static final String CATEGORY_BLACKLIST = "blckl";
    /**
     * a public blacklist entry was added
     */
    public static final String CATEGORY_BLACKLIST_ADD = "blckladd";
    /**
     * a vote and comment on a public blacklist add
     */
    public static final String CATEGORY_BLACKLIST_VOTE_ADD = "blcklavt";
    /**
     * a public blacklist entry was deleted
     */
    public static final String CATEGORY_BLACKLIST_DELETE = "blckldel";
    /**
     * a vote and comment on a public blacklist delete
     */
    public static final String CATEGORY_BLACKLIST_VOTE_DEL = "blckldvt";
    
    /* ------------------------------------------------------------------------
     * FLIE-SHARE related CATEGORIES
     * ------------------------------------------------------------------------ */    
    public static final String CATEGORY_FILESHARE = "flshr";
    /**
     * a file was added to the file share
     */
    public static final String CATEGORY_FILESHARE_ADD = "flshradd";
    /**
     * a file was added to the file share
     */
    public static final String CATEGORY_FILESHARE_DEL = "flshrdel";
    /**
     * a comment to a file share entry
     */
    public static final String CATEGORY_FILESHARE_COMMENT = "flshrcom";
    
    /* ------------------------------------------------------------------------
     * BOOKMARK related CATEGORIES
     * ------------------------------------------------------------------------ */       
    public static final String CATEGORY_BOOKMARK = "bkmrk";
    /**
     * a bookmark was added/created
     */
    public static final String CATEGORY_BOOKMARK_ADD = "bkmrkadd";
    /**
     * a vote and comment on a bookmark add
     */
    public static final String CATEGORY_BOOKMARK_VOTE_ADD = "bkmrkavt";
    /**
     * a bookmark was moved
     */
    public static final String CATEGORY_BOOKMARK_MOVE = "bkmrkmov";
    /**
     * a vote and comment on a bookmark move
     */
    public static final String CATEGORY_BOOKMARK_VOTE_MOVE = "bkmrkmvt";
    /**
     * a bookmark was deleted
     */
    public static final String CATEGORY_BOOKMARK_DEL = "bkmrkdel";
    /**
     * a vote and comment on a bookmark delete
     */
    public static final String CATEGORY_BOOKMARK_VOTE_DEL = "bkmrkdvt";
    
    /* ------------------------------------------------------------------------
     * SURFTIPP related CATEGORIES
     * ------------------------------------------------------------------------ */ 
    public static final String CATEGORY_SURFTIPP = "stipp";
    /** 
     * a surf tipp was added
     */
	public static final String CATEGORY_SURFTIPP_ADD = "stippadd";	 
	/**
	 * a vote and comment on a surf tipp
	 */
	public static final String CATEGORY_SURFTIPP_VOTE_ADD = "stippavt";
	
    /* ------------------------------------------------------------------------
     * WIKI related CATEGORIES
     * ------------------------------------------------------------------------ */ 	
	public static final String CATEGORY_WIKI = "wiki";
	/**
	 * a wiki page was updated
	 */
	public static final String CATEGORY_WIKI_UPDATE = "wiki_upd";
	/** 
	 * a wiki page das deleted
	 */
	public static final String CATEGORY_WIKI_DEL = "wiki_del";
	
    /* ------------------------------------------------------------------------
     * BLOG related CATEGORIES
     * ------------------------------------------------------------------------ */ 	
	public static final String CATEGORY_BLOG = "blog";
	/**
	 * a blog entry was added
	 */
	public static final String CATEGORY_BLOG_ADD = "blog_add";
	/**
	 * a blog page das deleted
	 */
	public static final String CATEGORY_BLOG_DEL = "blog_del";
    
    /* ========================================================================
     * ARRAY of valid CATEGORIES
     * ======================================================================== */	
    public static final String[] category = {
    	// PROFILE related CATEGORIES
    	CATEGORY_PROFILE_UPDATE,
        CATEGORY_PROFILE_BROADCAST,
        CATEGORY_PROFILE_VOTE_GOOD,
        CATEGORY_PROFILE_VOTE_BAD,
    	
    	// CRAWLING related CATEGORIES
    	CATEGORY_CRAWL_START, 
    	CATEGORY_CRAWL_STOP,
    	CATEGORY_CRAWL_COMMENT,
    	
    	// BLACKLIST related CATEGORIES
    	CATEGORY_BLACKLIST_ADD,  
    	CATEGORY_BLACKLIST_VOTE_ADD,
    	CATEGORY_BLACKLIST_DELETE,
    	CATEGORY_BLACKLIST_VOTE_DEL,
    	
        // FILESHARE related CATEGORIES    	
    	CATEGORY_FILESHARE_ADD,
    	CATEGORY_FILESHARE_DEL,
    	CATEGORY_FILESHARE_COMMENT,
    	
    	// BOOKMARK related CATEGORIES
    	CATEGORY_BOOKMARK_ADD,
    	CATEGORY_BOOKMARK_VOTE_ADD,
    	CATEGORY_BOOKMARK_MOVE,
    	CATEGORY_BOOKMARK_VOTE_MOVE,
    	CATEGORY_BOOKMARK_DEL,
    	CATEGORY_BOOKMARK_VOTE_DEL,
        
    	// SURFTIPP related CATEGORIES
    	CATEGORY_SURFTIPP_ADD,
    	CATEGORY_SURFTIPP_VOTE_ADD,
    	
    	// WIKI related CATEGORIE
    	CATEGORY_WIKI_UPDATE,
    	CATEGORY_WIKI_DEL,
    	
    	// BLOG related CATEGORIES
    	CATEGORY_BLOG_ADD,
    	CATEGORY_BLOG_DEL
    };
    public static HashSet<String> categories;
    static {
        categories = new HashSet<String>();
        for (int i = 0; i < category.length; i++) categories.add(category[i]);
    }
    
    private yacyNewsDB newsDB;
    private yacyNewsQueue outgoingNews, publishedNews, incomingNews, processedNews;
    private int maxDistribution;
    
    
    public yacyNewsPool(File yacyDBPath) {
        newsDB = new yacyNewsDB(new File(yacyDBPath, "news2.db"));
        outgoingNews  = new yacyNewsQueue(new File(yacyDBPath, "newsOut1.stack"), newsDB);
        publishedNews = new yacyNewsQueue(new File(yacyDBPath, "newsPublished1.stack"), newsDB);
        incomingNews  = new yacyNewsQueue(new File(yacyDBPath, "newsIn1.stack"), newsDB);
        processedNews = new yacyNewsQueue(new File(yacyDBPath, "newsProcessed1.stack"), newsDB);
        maxDistribution = 30;
    }
    
    public synchronized void close() {
        newsDB.close();
        outgoingNews.close();
        publishedNews.close();
        incomingNews.close();
        processedNews.close();
    }
    
    public int dbSize() {
        return newsDB.size();
    }
    
    public Iterator<yacyNewsRecord> recordIterator(int dbKey, boolean up) {
        // returns an iterator of yacyNewsRecord-type objects
        yacyNewsQueue queue = switchQueue(dbKey);
        return queue.records(up);
    }
    
    public void publishMyNews(yacyNewsRecord record) {
        // this shall be called if our peer generated a new news record and wants to publish it
        if (record == null) return;
        try {
            if (newsDB.get(record.id()) == null) {
                incomingNews.push(record); // we want to see our own news..
                outgoingNews.push(record); // .. and put it on the publishing list
            }
        } catch (IOException e) {}
    }
    
    public yacyNewsRecord myPublication() throws IOException {
        // generate a record for next peer-ping
        if (outgoingNews.size() == 0) return null;
        yacyNewsRecord record = outgoingNews.topInc();
        if ((record != null) && (record.distributed() >= maxDistribution)) {
            // move record to its final position. This is only for history
            publishedNews.push(outgoingNews.pop());
        }
        return record;
    }
    
    public void enqueueIncomingNews(yacyNewsRecord record) throws IOException {
        // called if a news is attached to a seed

        // check consistency
        if (record.id() == null) return;
        if (record.id().length() != yacyNewsRecord.idLength) return;
        if (record.category() == null) return;
        if (!(categories.contains(record.category()))) return;
        if (record.created().getTime() == 0) return;
        Map<String, String> attributes = record.attributes();
        if (attributes.containsKey("url")){
            if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_NEWS, new yacyURL((String) attributes.get("url"), null))){
                System.out.println("DEBUG: ignored news-entry url blacklisted: " + attributes.get("url"));
                return;
            }
        }
        if (attributes.containsKey("startURL")){
            if (plasmaSwitchboard.urlBlacklist.isListed(indexReferenceBlacklist.BLACKLIST_NEWS, new yacyURL((String) attributes.get("startURL"), null))){
                System.out.println("DEBUG: ignored news-entry url blacklisted: " + attributes.get("startURL"));
                return;
            }
        }
        
        // double-check with old news
        if (newsDB.get(record.id()) != null) return;
        incomingNews.push(record);
        
        // add message to feed channel
        //RSSFeed.channels("PEERNEWS").addMessage(new RSSMessage("Incoming News: " + record.category() + " from " + record.originator(), record.attributes().toString()));
    }
    
    public int size(int dbKey) {
        return switchQueue(dbKey).size();
    }
    
    public int automaticProcess() throws IOException, InterruptedException {
        // processes news in the incoming-db
        // returns number of processes
        yacyNewsRecord record;
        int pc = 0;
        synchronized (this.incomingNews) {
            Iterator<yacyNewsRecord> i = incomingNews.records(true);
            while (i.hasNext()) {
                // check for interruption
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Shutdown in progress");
                
                // get next news record
                record = (yacyNewsRecord) i.next();
                if (automaticProcessP(record)) {
                    this.processedNews.push(record);
                    i.remove();
                    pc++;
                }
            }
        }
        return pc;
    }
    
    long day = 1000 * 60 * 60 * 24;
    private boolean automaticProcessP(yacyNewsRecord record) {
        if (record == null) return false;
        if (record.category() == null) return true;
        if ((System.currentTimeMillis() - record.created().getTime()) > (14 * day)) {
            // remove everything after 1 week
            return true;
        }
        if ((record.category().equals(CATEGORY_WIKI_UPDATE)) &&
                ((System.currentTimeMillis() - record.created().getTime()) > (3 * day))) {
                return true;
            }
        if ((record.category().equals(CATEGORY_BLOG_ADD)) &&
                ((System.currentTimeMillis() - record.created().getTime()) > (3 * day))) {
                return true;
            }
        if ((record.category().equals(CATEGORY_PROFILE_UPDATE)) &&
                ((System.currentTimeMillis() - record.created().getTime()) > (7 * day))) {
                return true;
            }
        if ((record.category().equals(CATEGORY_CRAWL_START)) &&
            ((System.currentTimeMillis() - record.created().getTime()) > (2 * day))) {
            yacySeed seed = yacyCore.seedDB.get(record.originator());
            if (seed == null) return false;
            try {
                return (Integer.parseInt(seed.get(yacySeed.ISPEED, "-")) < 10);
            } catch (NumberFormatException ee) {
                return true;
            }
        }
        return false;
    }
    
    public synchronized yacyNewsRecord getSpecific(int dbKey, String category, String key, String value) {
        yacyNewsQueue queue = switchQueue(dbKey);
        yacyNewsRecord record;
        String s;
        Iterator<yacyNewsRecord> i = queue.records(true);
        while (i.hasNext()) {
            record = i.next();
            if ((record != null) && (record.category().equals(category))) {
                s = (String) record.attributes().get(key);
                if ((s != null) && (s.equals(value))) return record;
            }
        }
        return null;
    }

    public synchronized yacyNewsRecord getByOriginator(int dbKey, String category, String originatorHash) {
        yacyNewsQueue queue = switchQueue(dbKey);
        yacyNewsRecord record;
        Iterator<yacyNewsRecord> i = queue.records(true);
        while (i.hasNext()) {
            record = i.next();
            if ((record != null) &&
                (record.category().equals(category)) &&
                (record.originator().equals(originatorHash))) {
                return record;
            }
        }
        return null;
    }

    public synchronized yacyNewsRecord getByID(int dbKey, String id) {
        switch (dbKey) {
            case INCOMING_DB:   return incomingNews.get(id);
            case PROCESSED_DB:  return processedNews.get(id);
            case OUTGOING_DB:   return outgoingNews.get(id);
            case PUBLISHED_DB:  return publishedNews.get(id);
        }
        return null;
    }

    private yacyNewsQueue switchQueue(int dbKey) {
        switch (dbKey) {
            case INCOMING_DB:	return incomingNews;
            case PROCESSED_DB:  return processedNews;
            case OUTGOING_DB:   return outgoingNews;
            case PUBLISHED_DB:  return publishedNews;
        }
        return null;
    }
    
    public void clear(int dbKey) {
        // clear a table
        switch (dbKey) {
            case INCOMING_DB:	incomingNews.clear(); break;
            case PROCESSED_DB:  processedNews.clear(); break;
            case OUTGOING_DB:   outgoingNews.clear(); break;
            case PUBLISHED_DB:  publishedNews.clear(); break;
        }
    }

    public void moveOff(int dbKey, String id) throws IOException {
        // this is called if a queue element shall be moved to another queue or off the queue
        // it depends on the dbKey how the record is handled
        switch (dbKey) {
            case INCOMING_DB:   moveOff(incomingNews, processedNews, id); break;
            case PROCESSED_DB:  moveOff(processedNews, null,id); break;
            case OUTGOING_DB:   moveOff(outgoingNews, publishedNews, id); break;
            case PUBLISHED_DB:  moveOff(publishedNews, null, id); break;
        }
    }

    private boolean moveOff(yacyNewsQueue fromqueue, yacyNewsQueue toqueue, String id) throws IOException {
        // called if a published news shall be removed
        yacyNewsRecord record = fromqueue.remove(id);
        if (record == null) {
            return false;
        }
        if (toqueue != null) {
            toqueue.push(record);
        } else if ((incomingNews.get(id) == null) && (processedNews.get(id) == null) && (outgoingNews.get(id) == null) && (publishedNews.get(id) == null)) {
            newsDB.remove(id);
        }
        return true;
    }
    
    public void moveOffAll(int dbKey) throws IOException {
        // this is called if a queue element shall be moved to another queue or off the queue
        // it depends on the dbKey how the record is handled
        switch (dbKey) {
            case INCOMING_DB:   moveOffAll(incomingNews, processedNews); break;
            case PROCESSED_DB:  processedNews.clear(); break;
            case OUTGOING_DB:   moveOffAll(outgoingNews, publishedNews); break;
            case PUBLISHED_DB:  publishedNews.clear(); break;
        }
    }

    private int moveOffAll(yacyNewsQueue fromqueue, yacyNewsQueue toqueue) throws IOException {
        // move off all news from a specific queue to another queue
        Iterator<yacyNewsRecord> i = fromqueue.records(true);
        yacyNewsRecord record;
        if (toqueue == null) return 0;
        int c = 0;
        while (i.hasNext()) {
            record = (yacyNewsRecord) i.next();
            if (record == null) continue;
            toqueue.push(record);
            c++;
        }
        fromqueue.clear();
        return c;
    }
    
}
