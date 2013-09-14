// yacyNewsActions.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
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

package net.yacy.peers;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.repository.Blacklist.BlacklistType;
import net.yacy.search.Switchboard;

public class NewsPool {

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
    private static final String CATEGORY_PROFILE_VOTE_GOOD = "prflegvt";
    /**
     * a peer has done something bad (i.e. spammed) and gets a negative vote
     */
    private static final String CATEGORY_PROFILE_VOTE_BAD = "prflebvt";

    /* ------------------------------------------------------------------------
     * CRAWLING related CATEGORIES
     * ------------------------------------------------------------------------ */
    /**
     * a crawl with remote indexing was started
     */
    public static final String CATEGORY_CRAWL_START = "crwlstrt";
    /**
     * a crawl with remote indexing was stopped
     */
    private static final String CATEGORY_CRAWL_STOP = "crwlstop";
    /**
     * a comment on a crawl with remote indexing
     */
    private static final String CATEGORY_CRAWL_COMMENT = "crwlcomm";

    /* ------------------------------------------------------------------------
     * BLACKLIST related CATEGORIES
     * ------------------------------------------------------------------------ */
    /**
     * a private blacklist entry was added
     */
    private static final String CATEGORY_BLACKLIST_ADD = "blckladd";
    /**
     * a vote and comment on a private blacklist add
     */
    private static final String CATEGORY_BLACKLIST_VOTE_ADD = "blcklavt";
    /**
     * a private blacklist entry was deleted
     */
    private static final String CATEGORY_BLACKLIST_DELETE = "blckldel";
    /**
     * a vote and comment on a private blacklist delete
     */
    private static final String CATEGORY_BLACKLIST_VOTE_DEL = "blckldvt";

    /* ------------------------------------------------------------------------
     * FLIE-SHARE related CATEGORIES
     * ------------------------------------------------------------------------ */
    /**
     * a file was added to the file share
     */
    private static final String CATEGORY_FILESHARE_ADD = "flshradd";
    /**
     * a file was added to the file share
     */
    private static final String CATEGORY_FILESHARE_DEL = "flshrdel";
    /**
     * a comment to a file share entry
     */
    private static final String CATEGORY_FILESHARE_COMMENT = "flshrcom";

    /* ------------------------------------------------------------------------
     * BOOKMARK related CATEGORIES
     * ------------------------------------------------------------------------ */
    /**
     * a bookmark was added/created
     */
    public static final String CATEGORY_BOOKMARK_ADD = "bkmrkadd";
    /**
     * a vote and comment on a bookmark add
     */
    private static final String CATEGORY_BOOKMARK_VOTE_ADD = "bkmrkavt";
    /**
     * a bookmark was moved
     */
    private static final String CATEGORY_BOOKMARK_MOVE = "bkmrkmov";
    /**
     * a vote and comment on a bookmark move
     */
    private static final String CATEGORY_BOOKMARK_VOTE_MOVE = "bkmrkmvt";
    /**
     * a bookmark was deleted
     */
    private static final String CATEGORY_BOOKMARK_DEL = "bkmrkdel";
    /**
     * a vote and comment on a bookmark delete
     */
    private static final String CATEGORY_BOOKMARK_VOTE_DEL = "bkmrkdvt";

    /* ------------------------------------------------------------------------
     * SURFTIPP related CATEGORIES
     * ------------------------------------------------------------------------ */
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
	/**
	 * a wiki page was updated
	 */
	public static final String CATEGORY_WIKI_UPDATE = "wiki_upd";
	/**
	 * a wiki page das deleted
	 */
	private static final String CATEGORY_WIKI_DEL = "wiki_del";

    /* ------------------------------------------------------------------------
     * BLOG related CATEGORIES
     * ------------------------------------------------------------------------ */
	/**
	 * a blog entry was added
	 */
	public static final String CATEGORY_BLOG_ADD = "blog_add";
	/**
	 * a blog page das deleted
	 */
	private static final String CATEGORY_BLOG_DEL = "blog_del";

    /* ========================================================================
     * ARRAY of valid CATEGORIES
     * ======================================================================== */
    private static final String[] category = {
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
    private static final Set<String> categories = new HashSet<String>();
    static {
        categories.addAll(Arrays.asList(category));
    }

    private final static long MILLISECONDS_PER_HOUR = 1000 * 60 * 60;
    private final static long MILLISECONDS_PER_DAY = MILLISECONDS_PER_HOUR * 24;

    private final NewsDB newsDB;
    private final NewsQueue outgoingNews, publishedNews, incomingNews, processedNews;
    private final int maxDistribution;

    public NewsPool(
    		final File yacyDBPath,
            final boolean useTailCache,
            final boolean exceed134217727) {
        this.newsDB = new NewsDB(new File(yacyDBPath, "news1024.db"), 1024, useTailCache, exceed134217727);
        this.outgoingNews  = new NewsQueue(new File(yacyDBPath, "newsOut.table"), this.newsDB);
        this.publishedNews = new NewsQueue(new File(yacyDBPath, "newsPublished.table"), this.newsDB);
        this.incomingNews  = new NewsQueue(new File(yacyDBPath, "newsIn.table"), this.newsDB);
        this.processedNews = new NewsQueue(new File(yacyDBPath, "newsProcessed.table"), this.newsDB);
        this.maxDistribution = 30;
    }

    public synchronized void close() {
        this.newsDB.close();
        this.outgoingNews.close();
        this.publishedNews.close();
        this.incomingNews.close();
        this.processedNews.close();
    }

    public Iterator<NewsDB.Record> recordIterator(final int dbKey) {
        // returns an iterator of yacyNewsRecord-type objects
        final NewsQueue queue = switchQueue(dbKey);
        return queue.iterator();
    }

    public NewsDB.Record parseExternal(final String external) {
        return this.newsDB.newRecord(external);
    }

    public void publishMyNews(final Seed mySeed, final String category, final Map<String, String> attributes) {
        publishMyNews(this.newsDB.newRecord(mySeed, category, attributes));
    }

    public void publishMyNews(final Seed mySeed, final String category, final Properties attributes) {
        publishMyNews(this.newsDB.newRecord(mySeed, category, attributes));
    }

    private void publishMyNews(final NewsDB.Record record) {
        // this shall be called if our peer generated a new news record and wants to publish it
        if (record == null) return;
        try {
            if (this.newsDB.get(record.id()) == null) {
                this.incomingNews.push(record); // we want to see our own news..
                this.outgoingNews.push(record); // .. and put it on the publishing list
            }
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
    }

    public NewsDB.Record myPublication() throws IOException, SpaceExceededException {
        // generate a record for next peer-ping
        if (this.outgoingNews.isEmpty()) return null;
        final NewsDB.Record record = this.outgoingNews.pop();
        if (record == null) return null;

        record.incDistribution();

        if (record.distributed() >= this.maxDistribution) {
            // move record to its final position. This is only for history
            this.publishedNews.push(record);
        } else {
            this.outgoingNews.push(record);
        }
        return record;
    }

    public void enqueueIncomingNews(final NewsDB.Record record) throws IOException, SpaceExceededException {
        // called if a news is attached to a seed

        // check consistency
        if (record.id() == null) return;
        if (record.id().length() != NewsDB.idLength) return;
        if (record.category() == null) return;
        if (!(categories.contains(record.category()))) return;
        if (record.created().getTime() == 0) return;
        final Map<String, String> attributes = record.attributes();
        if (attributes.containsKey("url")){
            if (Switchboard.urlBlacklist.isListed(BlacklistType.NEWS, new DigestURL(attributes.get("url")))){
                System.out.println("DEBUG: ignored news-entry url blacklisted: " + attributes.get("url"));
                return;
            }
        }
        if (attributes.containsKey("startURL")){
            if (Switchboard.urlBlacklist.isListed(BlacklistType.NEWS, new DigestURL(attributes.get("startURL")))){
                System.out.println("DEBUG: ignored news-entry url blacklisted: " + attributes.get("startURL"));
                return;
            }
        }

        // double-check with old news
        if (this.newsDB.get(record.id()) != null) return;
        this.incomingNews.push(record);

        // add message to feed channel
        //RSSFeed.channels(yacyCore.channelName).addMessage(new RSSMessage("Incoming News: " + record.category() + " from " + record.originator(), record.attributes().toString()));
    }

    public int size(final int dbKey) {
        return switchQueue(dbKey).size();
    }

    public int automaticProcess(final SeedDB seedDB) throws IOException, InterruptedException, SpaceExceededException {
        // processes news in the incoming-db
        // returns number of processes
        NewsDB.Record record;
        int pc = 0;
        synchronized (this.incomingNews) {
            final Iterator<NewsDB.Record> i = this.incomingNews.iterator();
            final Set<String> removeIDs = new HashSet<String>();
            // this loop should not run too long! This may happen if the incoming news are long and not deleted after processing
            final long start = System.currentTimeMillis();
            while (i.hasNext()) {
                // check for interruption
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Shutdown in progress");

                // get next news record
                record = i.next();
                if (automaticProcessP(seedDB, record)) {
                    this.processedNews.push(record);
                    removeIDs.add(record.id());
                    pc++;
                }
                if (System.currentTimeMillis() - start > 100) break; // time-out for this to avoid deadlocks during concurrent peer-pings
            }
            for (final String id: removeIDs) {
                final NewsDB.Record deletedRecord = this.incomingNews.remove(id);
                assert deletedRecord != null;
            }
        }
        return pc;
    }

    private static boolean automaticProcessP(final SeedDB seedDB, final NewsDB.Record record) {
        if (record == null) return false;
        if (record.category() == null) return true;
        final long created = record.created().getTime();
        if ((System.currentTimeMillis() - created) > (6L * MILLISECONDS_PER_HOUR)) {
            // remove everything after 1 day
            return true;
        }
        if ((record.category().equals(CATEGORY_WIKI_UPDATE)) &&
                ((System.currentTimeMillis() - created) > (3L * MILLISECONDS_PER_DAY))) {
                return true;
            }
        if ((record.category().equals(CATEGORY_BLOG_ADD)) &&
                ((System.currentTimeMillis() - created) > (3L * MILLISECONDS_PER_DAY))) {
                return true;
            }
        if ((record.category().equals(CATEGORY_PROFILE_UPDATE)) &&
                ((System.currentTimeMillis() - created) > (3L * MILLISECONDS_PER_DAY))) {
                return true;
            }
        if ((record.category().equals(CATEGORY_CRAWL_START)) &&
            ((System.currentTimeMillis() - created) > (3L * MILLISECONDS_PER_DAY))) {
            final Seed seed = seedDB.get(record.originator());
            if (seed == null) return true;
            try {
                return (Integer.parseInt(seed.get(Seed.ISPEED, "-")) < 10);
            } catch (final NumberFormatException ee) {
                return true;
            }
        }
        return false;
    }

    public synchronized NewsDB.Record getSpecific(final int dbKey, final String category, final String key, final String value) {
        final NewsQueue queue = switchQueue(dbKey);
        NewsDB.Record record;
        String s;
        final Iterator<NewsDB.Record> i = queue.iterator();
        while (i.hasNext()) {
            record = i.next();
            if ((record != null) && (record.category().equals(category))) {
                s = record.attributes().get(key);
                if ((s != null) && (s.equals(value))) return record;
            }
        }
        return null;
    }

    public synchronized NewsDB.Record getByOriginator(final int dbKey, final String category, final String originatorHash) {
        final NewsQueue queue = switchQueue(dbKey);
        NewsDB.Record record;
        final Iterator<NewsDB.Record> i = queue.iterator();
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

    public synchronized NewsDB.Record getByID(final int dbKey, final String id) {
        switch (dbKey) {
            case INCOMING_DB:   return this.incomingNews.get(id);
            case PROCESSED_DB:  return this.processedNews.get(id);
            case OUTGOING_DB:   return this.outgoingNews.get(id);
            case PUBLISHED_DB:  return this.publishedNews.get(id);
            default:
            return null;
        }
    }

    private NewsQueue switchQueue(final int dbKey) {
        switch (dbKey) {
            case INCOMING_DB:	return this.incomingNews;
            case PROCESSED_DB:  return this.processedNews;
            case OUTGOING_DB:   return this.outgoingNews;
            case PUBLISHED_DB:  return this.publishedNews;
            default: return null;
            }
    }

    public void clear(final int dbKey) {
        // clear a table
        switch (dbKey) {
            case INCOMING_DB:	this.incomingNews.clear(); break;
            case PROCESSED_DB:  this.processedNews.clear(); break;
            case OUTGOING_DB:   this.outgoingNews.clear(); break;
            case PUBLISHED_DB:  this.publishedNews.clear(); break;
            default: return;
            }
    }

    public void moveOff(final int dbKey, final String id) throws IOException, SpaceExceededException {
        // this is called if a queue element shall be moved to another queue or off the queue
        // it depends on the dbKey how the record is handled
        switch (dbKey) {
            case INCOMING_DB:   moveOff(this.incomingNews, this.processedNews, id); break;
            case PROCESSED_DB:  moveOff(this.processedNews, null,id); break;
            case OUTGOING_DB:   moveOff(this.outgoingNews, this.publishedNews, id); break;
            case PUBLISHED_DB:  moveOff(this.publishedNews, null, id); break;
            default: return;
            }
    }

    private boolean moveOff(final NewsQueue fromqueue, final NewsQueue toqueue, final String id) throws IOException, SpaceExceededException {
        // called if a published news shall be removed
        final NewsDB.Record record = fromqueue.remove(id);
        if (record == null) {
            return false;
        }
        if (toqueue != null) {
            toqueue.push(record);
        } else if ((this.incomingNews.get(id) == null) && (this.processedNews.get(id) == null) && (this.outgoingNews.get(id) == null) && (this.publishedNews.get(id) == null)) {
            this.newsDB.remove(id);
        }
        return true;
    }

    public void moveOffAll(final int dbKey) throws IOException, SpaceExceededException {
        // this is called if a queue element shall be moved to another queue or off the queue
        // it depends on the dbKey how the record is handled
        switch (dbKey) {
            case INCOMING_DB:   moveOffAll(this.incomingNews, this.processedNews); break;
            case PROCESSED_DB:  this.processedNews.clear(); break;
            case OUTGOING_DB:   moveOffAll(this.outgoingNews, this.publishedNews); break;
            case PUBLISHED_DB:  this.publishedNews.clear(); break;
        }
    }

    private static int moveOffAll(final NewsQueue fromqueue, final NewsQueue toqueue) throws IOException, SpaceExceededException {
        // move off all news from a specific queue to another queue
        final Iterator<NewsDB.Record> i = fromqueue.iterator();
        NewsDB.Record record;
        if (toqueue == null) return 0;
        int c = 0;
        while (i.hasNext()) {
            record = i.next();
            if (record == null) continue;
            toqueue.push(record);
            c++;
        }
        fromqueue.clear();
        return c;
    }

}
