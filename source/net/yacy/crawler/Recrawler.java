/**
 * RecrawlBusyThread.java
 * Copyright 2015 by Burkhard Buelte
 * First released 15.05.2015 at http://yacy.net
 *
 * This is a part of YaCy, a peer-to-peer based web search engine
 *
 * LICENSE
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt If not, see
 * <http://www.gnu.org/licenses/>.
 */
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

/*
  the yacy process of getting in touch of other peers starts as follows:
  - init seed cache. It is needed to determine the right peer for the Hello-Process
  - create a own seed. This can be a new one or one loaded from a file
  - The httpd must start up then first
  - the own seed is completed by performing the 'yacyHello' process. This
    process will result in a request back to the own peer to check if it runs
    in server mode. This is the reason that the httpd must be started in advance.

*/

// contributions:
// principal peer status via file generation by Alexander Schier [AS]

package net.yacy.crawler;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.data.CrawlQueues;
import net.yacy.crawler.data.CrawlProfile;
import net.yacy.crawler.retrieval.Request;
import net.yacy.crawler.data.NoticedURL;
import net.yacy.search.Switchboard;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import net.yacy.cora.date.ISO8601Formatter;


public class Recrawler {

	// statics
	public static final ThreadGroup publishThreadGroup = new ThreadGroup("publishThreadGroup");
	public static final HashMap<String, String> seedUploadMethods = new HashMap<String, String>();
	public static final ConcurrentLog log = new ConcurrentLog("YACY");
	/** pseudo-random key derived from a time-interval while YaCy startup */
	public static long speedKey = 0;
	public static long magic = System.currentTimeMillis();
	public CrawlQueues crawlQueues;
	

	// public static yacyShare shareManager = null;
	// public static boolean terminate = false;

	// class variables
	Switchboard sb;

	public Recrawler(final Switchboard sb) {
		final long time = System.currentTimeMillis();
		this.sb = sb;
		// sb.setConfig("yacyStatus", "");

		// create a peer news channel
		// final RSSFeed peernews = EventChannel.channels(EventChannel.PEERNEWS);
		// peernews.addMessage(new RSSMessage("YaCy started", "", ""));

		// ensure that correct IP is used
		// final String staticIP = sb.getConfig(SwitchboardConstants.SERVER_STATICIP,
		// "");
		// if (staticIP.length() != 0 && Seed.isProperIP(staticIP)) {
		// serverCore.useStaticIP = true;
		// sb.peers.mySeed().setIP(staticIP);
		// log.info("staticIP set to " + staticIP);
		// } else {
		// serverCore.useStaticIP = false;
		// }

		// loadSeedUploadMethods();

		log.info("RECRWALER INITIALIZED");
		// ATTENTION, VERY IMPORTANT: before starting the thread, the httpd yacy server
		// must be running!

		speedKey = System.currentTimeMillis() - time;
	}

	protected class publishThread extends Thread {
		@Override
		public final void run() {
			try {
				log.info("RECRWALER RUN");
			} catch (final Exception e) {
				ConcurrentLog.logException(e);
				log.severe("RECRWALER: error ");

			}
		}
	}

	
	public final void HeartBeat() {
		
		final CrawlQueues cq = sb.crawlQueues;
		// check if queue is not too full, 500 is a fair limit
		if (cq.coreCrawlJobSize() > 500) {
			log.info("RECRWALER: not adding URLs, queue size " + cq.coreCrawlJobSize() +" > 500" );
			return;
		}
		log.info("RECRWALER: will adding URLs, queue size < 500: " + cq.coreCrawlJobSize() +" > 500");
		
		sb.index.fulltext().commit(true);
		log.info("RECRWALER starting cycle to add URLs to be recrawled");
		String rows = "50"; // number of lines to be fetched
		String days = "365"; // URLs last load > x days
		//Long counter;
		String dateQuery = String.format("fresh_date_dt:[* TO NOW/DAY-30DAY] AND load_date_dt:[* TO NOW/DAY-%sDAY]",
				days, days); // URLs which have a fresh date > 30 days and were loaded > x days ago

		final SolrQuery query = new SolrQuery();
		query.setQuery(dateQuery);
		// query.setQuery(this.sb.getConfig(SwitchboardConstants.AUTOCRAWL_QUERY,
		// "*:*"));
		query.setFields("sku, httpstatus_i");
		// query.addFilterQuery(dateQuery);
		//query.add("group", "true");
		//query.add("group.field", "sku");
		//query.add("group.limit", "1");
		//query.add("group.main", "true");
		query.add("rows", rows);
		query.addSort("load_date_dt", SolrQuery.ORDER.asc);

		log.info("RECRWALER QUERY:" + query.toString());
		try {
			
			//counter = sb.index.fulltext().getDefaultConnector().getCountByQuery(query);
			QueryResponse resp = sb.index.fulltext().getDefaultConnector().getResponseByParams(query);
			//counter = resp.count();
			log.info("RECRWALER RESPONSE:" + resp.toString());

			final CrawlProfile profile = sb.crawler.defaultTextSnippetGlobalProfile;
			
			int added = 0;
			Date now = new Date();
			
			for (SolrDocument doc : resp.getResults()) {

				DigestURL url;
				// ScRe
				if (doc.getFieldValue("sku") != null) {

					final String u = doc.getFieldValue("sku").toString();
					if (doc.getFieldValue("httpstatus_i") != null) {
						//log.info("RECRWALER ADD: " + u + "with status: " + doc.getFieldValue("httpstatus_i").toString() );
					} else {
						log.info("RECRWALER ADD: " + u + "with status: NOSTATUS");
					}
					
					
					url = new DigestURL(u);
					
					
			
					//sb.index.fulltext().remove(url.hash());
					//sb.index.fulltext().commit(true);
					final Request request = sb.loader.request(url, true, true);
	                String acceptedError = sb.crawlStacker.checkAcceptanceChangeable(url, profile, 0);
	                if (acceptedError == null) { // skip check if failed docs to be included
	                    acceptedError = sb.crawlStacker.checkAcceptanceInitially(url, profile);
	                }
	                if (acceptedError != null) {
	                	log.info("RECRWALER addToCrawler: cannot load " + url.toNormalform(true) + ": " + acceptedError);
	                	//sb.index.fulltext().remove(url.hash());
	                	//sb.index.fulltext().commit(true);
	                    continue;
	                }
	                final String s;
	                s = sb.crawlQueues.noticeURL.push(NoticedURL.StackType.LOCAL, request, profile, sb.robots);

	                if (s != null) {
	                	log.info("RECRWALER addToCrawler: failed to add " + url.toNormalform(true) + ": " + s);
	                	sb.index.fulltext().remove(url.hash()); // If adding URL fails, delete it from index
	                } else {
	                    added++;
	                    
	                }
	                //sb.index.fulltext().remove(url.hash());
				} else {

				}
				
				 //doc.setField(CollectionSchema.load_date_dt.name(), ISO8601Formatter.FORMATTER.format(now) );
				 //doc.setField(CollectionSchema.fresh_date_dt.name(), ISO8601Formatter.FORMATTER.format(now) );
				 sb.index.fulltext().commit(true);
				 //sb.index.fulltext().putDocument(doc);
				 //sb.index.fulltext().getDefaultConnector().update(doc);
				 //sb.index.fulltext().getDefaultConnector().update(doc);
				 //sb.index.fulltext().getDefaultConnector().commit(false);
				 
			}
			log.info("RECRWALER ADDED " + added + " URLs with timestamp: " + ISO8601Formatter.FORMATTER.format(now));
		} catch (SolrException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return;
	}

}
