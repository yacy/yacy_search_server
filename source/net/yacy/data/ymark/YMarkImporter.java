// YMarkImporter.java
// (C) 2012 by Stefan Foerster (apfelmaennchen), sof@gmx.de, Norderstedt, Germany
// first published 2012 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package net.yacy.data.ymark;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import net.yacy.cora.util.ConcurrentLog;
import net.yacy.search.Switchboard;

public abstract class YMarkImporter implements Runnable {
	// Statics
    public final static String XML_NAMESPACE_PREFIXES 	= "http://xml.org/sax/features/namespace-prefixes";
    public final static String XML_NAMESPACES 			= "http://xml.org/sax/features/namespaces";
    public final static String XML_VALIDATION 			= "http://xml.org/sax/features/validation";

	protected String importer;
    protected ArrayBlockingQueue<YMarkEntry> bookmarks;
	protected final MonitoredReader bmk_file;
	protected final String targetFolder;
	protected final String sourceFolder;

	public YMarkImporter(final MonitoredReader bmk_file, final int queueSize, final String sourceFolder, final String targetFolder) {
        this.bookmarks = new ArrayBlockingQueue<YMarkEntry>(queueSize);
        this.bmk_file = bmk_file;
        this.sourceFolder = YMarkUtil.cleanFoldersString(sourceFolder);
        this.targetFolder = YMarkUtil.cleanFoldersString(targetFolder);
	}

    @Override
    public void run() {
    	try {
    		parse();
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        } finally {
        	try {
        		ConcurrentLog.info(YMarkTables.BOOKMARKS_LOG, this.importer+" Importer inserted poison pill in queue");
				this.bookmarks.put(YMarkEntry.POISON);
			} catch (final InterruptedException e1) {
			    ConcurrentLog.logException(e1);
			}
        }
    }

    public YMarkEntry take() {
        try {
            return this.bookmarks.take();
        } catch (final InterruptedException e) {
            ConcurrentLog.logException(e);
            return null;
        }
    }

    public void setImporter(final String importer) {
    	this.importer = importer;
    }

    public long getProgress() {
    	return this.bmk_file.getProgress();
    }

    public long maxProgress() {
    	return this.bmk_file.maxProgress();
    }

    public abstract void parse() throws Exception;

    public Consumer getConsumer(final Switchboard sb, final String bmk_user, final ArrayBlockingQueue<String> autoTaggingQueue,
			final boolean autotag, final boolean empty, final String indexing, final boolean medialink) {
    	return new Consumer(sb, bmk_user, autoTaggingQueue, autotag, empty, indexing, medialink);
    }

    public class Consumer implements Runnable {
    	private final Switchboard sb;
    	private final String bmk_user;
    	private final ArrayBlockingQueue<String> autoTaggingQueue;
    	private final String indexing;

    	private final boolean autotag;
    	private final boolean empty;
    	private final boolean medialink;

		public Consumer(final Switchboard sb, final String bmk_user, final ArrayBlockingQueue<String> autoTaggingQueue,
				final boolean autotag, final boolean empty, final String indexing, final boolean medialink) {
			this.sb = sb;
			this.bmk_user = bmk_user;
			this.autoTaggingQueue = autoTaggingQueue;
			this.autotag = autotag;
			this.empty = empty;
			this.indexing = indexing;
			this.medialink = medialink;
		}

		@Override
        public void run() {
			YMarkEntry bmk;
			while ((bmk = take()) != YMarkEntry.POISON) {
				try {
					final String url = bmk.get(YMarkEntry.BOOKMARK.URL.key());
					// other protocols could cause problems
					if(url != null && url.startsWith("http")) {
					    this.sb.tables.bookmarks.addBookmark(this.bmk_user, bmk, true, true);
						if(this.autotag) {
							if(!this.empty) {
								this.autoTaggingQueue.put(url);
							} else if(!bmk.containsKey(YMarkEntry.BOOKMARK.TAGS.key()) || bmk.get(YMarkEntry.BOOKMARK.TAGS.key()).equals(YMarkEntry.BOOKMARK.TAGS.deflt())) {
								this.autoTaggingQueue.put(url);
							}
						}
						// fill crawler
						if (this.indexing.equals("single")) {
							bmk.crawl(YMarkCrawlStart.CRAWLSTART.SINGLE, this.medialink, this.sb);
						} else if (this.indexing.equals("onelink")) {
							bmk.crawl(YMarkCrawlStart.CRAWLSTART.ONE_LINK, this.medialink, this.sb);
		                } else if (this.indexing.equals("fulldomain")) {
		                	bmk.crawl(YMarkCrawlStart.CRAWLSTART.FULL_DOMAIN, this.medialink, this.sb);
		                }
					}
				} catch (final IOException e) {
					ConcurrentLog.logException(e);
				} catch (final InterruptedException e) {
					ConcurrentLog.logException(e);
				}
	        }
        	if(this.autotag) {
            	try {
    				this.autoTaggingQueue.put(YMarkAutoTagger.POISON);
    				ConcurrentLog.info(YMarkTables.BOOKMARKS_LOG, YMarkImporter.this.importer+" inserted poison pill into autoTagging queue");
    			} catch (final InterruptedException e) {
    				ConcurrentLog.logException(e);
    			}
        	}
		}
    }
}
