
/**
 * IndexReIndexMonitor_p Copyright 2013 by Michael Peter Christen First released
 * 29.04.2013 at https://yacy.net
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
package net.yacy.htroot;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import net.yacy.migration;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.federate.solr.connector.SolrConnector;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.sorting.OrderedScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.crawler.RecrawlBusyThread;
import net.yacy.data.TransactionManager;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.search.Switchboard;
import net.yacy.search.index.ReindexSolrBusyThread;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexReIndexMonitor_p {

	/** This servlet name, used for identifying recorded API calls */
	private static final String SERVLET_NAME = IndexReIndexMonitor_p.class.getSimpleName() + ".html";

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        /* Acquire a transaction token for the next possible POST form submissions */
        final String nextTransactionToken = TransactionManager.getTransactionToken(header);
        prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, nextTransactionToken);

        prop.put("docsprocessed", "0");
        prop.put("currentselectquery","");
        BusyThread reidxbt = sb.getThread(ReindexSolrBusyThread.THREAD_NAME);
        if (reidxbt == null) {
            if (post != null && post.containsKey("reindexnow") && sb.index.fulltext().connectedLocalSolr()) {
            	/* Check the transaction is valid */
            	TransactionManager.checkPostTransaction(header, post);

                migration.reindexToschema(sb);
                prop.put("querysize", "0");
                prop.put("infomessage","reindex job started");

                reidxbt = sb.getThread(ReindexSolrBusyThread.THREAD_NAME); //get new created job for following posts
            }
        }

        if (reidxbt != null) {
            prop.put("reindexjobrunning", 1);
            prop.put("querysize", reidxbt.getJobCount());

            if (reidxbt instanceof ReindexSolrBusyThread) {
                prop.put("docsprocessed", ((ReindexSolrBusyThread) reidxbt).getProcessed());
                prop.put("currentselectquery","q="+((ReindexSolrBusyThread) reidxbt).getCurrentQuery());
                // prepare list of fields in queue
                final OrderedScoreMap<String> querylist = ((ReindexSolrBusyThread) reidxbt).getQueryList();
                if (querylist != null) {
                    int i = 0;
                    for (final String oneqs : querylist) { // just use fieldname from query (fieldname:[* TO *])
                        prop.put("reindexjobrunning_fieldlist_"+i+"_fieldname", oneqs.substring(0, oneqs.indexOf(':')));
                        prop.put("reindexjobrunning_fieldlist_"+i+"_fieldscore", querylist.get(oneqs));
                        i++;
                    }
                    prop.put("reindexjobrunning_fieldlist", querylist.size());
                } else {
                    prop.put("reindexjobrunning_fieldlist", 0);
                }
            }

            if (post != null && post.containsKey("stopreindex")) {
            	/* Check the transaction is valid */
            	TransactionManager.checkPostTransaction(header, post);

                sb.terminateThread(ReindexSolrBusyThread.THREAD_NAME, false);
                prop.put("infomessage", "reindex job stopped");
                prop.put("reindexjobrunning",0);
            } else {
                prop.put("infomessage", "reindex is running");
            }
        } else {
            prop.put("reindexjobrunning", 0);
            if (sb.index.fulltext().connectedLocalSolr()) {
                prop.put("querysize", "is empty");
                prop.put("infomessage", "no reindex job running");
            } else {
                prop.put("querysize", "");
                prop.putHTML("infomessage", "! reindex works only with embedded Solr index !");
            }
        }

		processReindexReport(header, sb, prop, reidxbt instanceof ReindexSolrBusyThread ? (ReindexSolrBusyThread)reidxbt : null);

        // recrawl job handling
        BusyThread recrawlbt = sb.getThread(RecrawlBusyThread.THREAD_NAME);

    	String recrawlQuery = RecrawlBusyThread.DEFAULT_QUERY;
        boolean inclerrdoc = RecrawlBusyThread.DEFAULT_INCLUDE_FAILED;
        boolean deleteOnRecrawl = RecrawlBusyThread.DEFAULT_DELETE_ON_RECRAWL;
        // to signal that a setting shall change the form provides a fixed parameter setup=recrawljob, if not present return status only
        if (post != null && "recrawljob".equals(post.get("setup"))) { // it's a command to recrawlThread

        	/* Check the transaction is valid */
        	TransactionManager.checkPostTransaction(header, post);

        	if(post.containsKey("recrawlquerytext")) {
        		recrawlQuery = post.get("recrawlquerytext");
        	}

            if (post.containsKey("includefailedurls")) {
                inclerrdoc = post.getBoolean("includefailedurls");
            }

            if (post.containsKey("deleteOnRecrawl")) {
                deleteOnRecrawl = post.getBoolean("deleteOnRecrawl");
            }

            if (recrawlbt == null || recrawlbt.shutdownInProgress()) {
                prop.put("recrawljobrunning_simulationResult", 0);
                prop.put("recrawljobrunning_error", 0);
            	if (post.containsKey("recrawlnow")) {
            		sb.deployThread(RecrawlBusyThread.THREAD_NAME, "ReCrawl", "recrawl existing documents", null,
            				new RecrawlBusyThread(Switchboard.getSwitchboard(), recrawlQuery, inclerrdoc, deleteOnRecrawl), 1000);
            		recrawlbt = sb.getThread(RecrawlBusyThread.THREAD_NAME);

            		/* store this call as an api call for easy scheduling possibility */
            		if(sb.tables != null) {
            			/* We avoid creating a duplicate of any already recorded API call with the same parameters */
            			final Row lastExecutedCall = WorkTables
            					.selectLastExecutedApiCall(IndexReIndexMonitor_p.SERVLET_NAME, post, sb);
            			if (lastExecutedCall != null && !post.containsKey(WorkTables.TABLE_API_COL_APICALL_PK)) {
            				final byte[] lastExecutedCallPk = lastExecutedCall.getPK();
            				if (lastExecutedCallPk != null) {
            					post.add(WorkTables.TABLE_API_COL_APICALL_PK, UTF8.String(lastExecutedCallPk));
            				}
            			}
            			sb.tables.recordAPICall(post, IndexReIndexMonitor_p.SERVLET_NAME, WorkTables.TABLE_API_TYPE_CRAWLER,
            					"Recrawl documents matching selection query : " + recrawlQuery);
            		}
            	} else if(post.containsKey("simulateRecrawl")) {
            		final SolrConnector solrConnector = sb.index.fulltext().getDefaultConnector();
            		if (solrConnector != null && !solrConnector.isClosed()) {
            			try {
            				/* Ensure indexed data is up-to-date */
            				solrConnector.commit(true);
            				// query all or only httpstatus=200 depending on includefailed flag
            				final String finalQuery = RecrawlBusyThread.buildSelectionQuery(recrawlQuery, inclerrdoc);
            				final long count = solrConnector.getCountByQuery(finalQuery);
            				prop.put("recrawljobrunning_simulationResult", 1);
            				prop.put("recrawljobrunning_simulationResult_docCount", count);
            				if(count > 0) {
            					/* Got some results : add a link to the related solr select URL for easily browsing results */
            					final int maxRows = 10;
            					final String solrSelectUrl = genLocalSolrSelectUrl(finalQuery, maxRows);
            					prop.put("recrawljobrunning_simulationResult_showSelectLink", 1);
            					prop.put("recrawljobrunning_simulationResult_showSelectLink_rows", maxRows);
            					prop.put("recrawljobrunning_simulationResult_showSelectLink_browseSelectedUrl", solrSelectUrl);
            				} else {
            					prop.put("recrawljobrunning_simulationResult_showSelectLink", 0);
            				}
            			} catch (final IOException e) {
            				prop.put("recrawljobrunning_simulationResult", 2);
            				ConcurrentLog.logException(e);
            			}
            		} else {
            			prop.put("recrawljobrunning_simulationResult", 3);
            		}
            	}

                if(post.containsKey("recrawlDefaults")) {
                	recrawlQuery = RecrawlBusyThread.DEFAULT_QUERY;
                    inclerrdoc = RecrawlBusyThread.DEFAULT_INCLUDE_FAILED;
                    deleteOnRecrawl = RecrawlBusyThread.DEFAULT_DELETE_ON_RECRAWL;
                }
            } else {
                if (post.containsKey("stoprecrawl")) {
            		/* We do not remove the thread from the Switchboard worker threads using serverSwitch.terminateThread(String,boolean),
            		 * because we want to be able to provide a report after its termination */
                    recrawlbt.terminate(false);
                    prop.put("recrawljobrunning", 0);
                }
            }

            if (recrawlbt != null && !recrawlbt.shutdownInProgress()) {
                if (post.containsKey("updquery") && post.containsKey("recrawlquerytext")) {
                    ((RecrawlBusyThread) recrawlbt).setQuery(recrawlQuery, inclerrdoc, deleteOnRecrawl);
                } else {
                    ((RecrawlBusyThread) recrawlbt).setIncludeFailed(inclerrdoc);
                    ((RecrawlBusyThread) recrawlbt).setDeleteOnRecrawl(deleteOnRecrawl);
                }
            }
        }

		processRecrawlReport(header, sb, prop, (RecrawlBusyThread)recrawlbt);

        // just post status of recrawlThread
        if (recrawlbt != null && !recrawlbt.shutdownInProgress()) { // provide status
            prop.put("recrawljobrunning", 1);
            prop.put("recrawljobrunning_docCount", ((RecrawlBusyThread) recrawlbt).getUrlsToRecrawl());
            prop.put("recrawljobrunning_recrawlquerytext", ((RecrawlBusyThread) recrawlbt).getQuery());
            prop.put("recrawljobrunning_includefailedurls", ((RecrawlBusyThread) recrawlbt).getIncludeFailed());
            prop.put("recrawljobrunning_deleteOnRecrawl", ((RecrawlBusyThread) recrawlbt).getDeleteOnRecrawl());
        } else {
			prop.put("recrawljobrunning", 0);
            prop.put("recrawljobrunning_recrawlquerytext", recrawlQuery);
            prop.put("recrawljobrunning_includefailedurls", inclerrdoc);
            prop.put("recrawljobrunning_deleteOnRecrawl", deleteOnRecrawl);
        }

        // return rewrite properties
        return prop;
    }

	/**
	 * Write information on the eventual currently running or last reindex job
	 * terminated
	 *
	 * @param header
	 *            current request header. Must not be null.
	 * @param sb
	 *            Switchboard instance holding server environment
	 * @param prop
	 *            this template result to write on. Must not be null.
	 * @param recrawlbt
	 *            the eventual reindex thread
	 */
	private static void processReindexReport(final RequestHeader header, final Switchboard sb, final serverObjects prop,
			final ReindexSolrBusyThread recrawlbt) {
		if (recrawlbt != null) {
			prop.put("reindexReport", 1);

			prop.put("reindexReport_currentQuery", recrawlbt.getCurrentQuery());

			prop.put("reindexReport_currentQuerySize",
					recrawlbt.getQueryList() != null ? recrawlbt.getQueryList().size() : 0);
			prop.put("reindexReport_processedCount", recrawlbt.getProcessed());
		} else {
			prop.put("reindexReport", 0);
		}
	}

	/**
	 * @param query
	 *            the Solr selection query. Must not be null and not
	 *            percent-encoded.
	 * @return the URL of the select query targetting the local Solr
	 */
	protected static String genLocalSolrSelectUrl(final String query, final int maxRows) {
		String urlEncodedQUery;
		try {
			urlEncodedQUery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
		} catch (final UnsupportedEncodingException e) {
			// should not happen as UTF-8 must be supported on any Java platform
			urlEncodedQUery = query;
		}
		return "solr/select?core=collection1&wt=html&start=0&rows=" + maxRows + "&q=" + urlEncodedQUery;
	}

    /**
     * Write information on the eventual currently running or last recrawl job terminated
     * @param header current request header. Must not be null.
     * @param sb Switchboard instance holding server environment
     * @param prop this template result to write on. Must not be null.
     * @param recrawlbt the eventual recrawl thread
     */
	private static void processRecrawlReport(final RequestHeader header, final Switchboard sb,
			final serverObjects prop, final RecrawlBusyThread recrawlbt) {
		if (recrawlbt != null) {
			prop.put("recrawlReport", 1);

			prop.put("recrawlReport_error", recrawlbt.isTerminatedBySolrFailure());


			int jobStatus;
			if(recrawlbt.isAlive()) {
				if(recrawlbt.shutdownInProgress()) {
					jobStatus = 1; // Shutdown in progress
				} else {
					jobStatus = 0; // Running
				}
			} else {
				jobStatus = 2; // Terminated
			}
			prop.put("recrawlReport_jobStatus", jobStatus);

			prop.put("recrawlReport_recrawlquerytext", recrawlbt.getQuery());

			Locale formatLocale;
			if (sb != null) {
				final String lng = sb.getConfig("locale.language", Locale.ENGLISH.getLanguage());
				if ("browser".equals(lng)) {
					/* Only use the client locale when locale.language is set to browser */
					formatLocale = header.getLocale();
				} else {
					formatLocale = Locale.forLanguageTag(lng);
				}
			} else {
				/* Match the default language used in html templates */
				formatLocale = Locale.ENGLISH;
			}
			final DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
					.withLocale(formatLocale);
			prop.put("recrawlReport_startTime", formatDateTime(formatter, recrawlbt.getStartTime()));
			prop.put("recrawlReport_endTime", formatDateTime(formatter, recrawlbt.getEndTime()));
			prop.put("recrawlReport_urlsToRecrawlCount", recrawlbt.getUrlsToRecrawl());
			prop.put("recrawlReport_recrawledUrlsCount", recrawlbt.getRecrawledUrlsCount());
			prop.put("recrawlReport_rejectedUrlsCount", recrawlbt.getRejectedUrlsCount());
			prop.put("recrawlReport_malformedUrlsCount", recrawlbt.getMalformedUrlsCount());
			prop.put("recrawlReport_malformedUrlsDeletedCount", recrawlbt.getMalformedUrlsDeletedCount());
		} else {
			prop.put("recrawlReport", 0);
		}
	}

	/**
	 * @param formatter the formatter to use. Must not be null.
	 * @param time the date/time value to format. Can be null.
	 * @return a string representing the formatted date/time, eventually empty.
	 */
	protected static String formatDateTime(final DateTimeFormatter formatter, final LocalDateTime time) {
		String formattedTime;
		if(time != null) {
			try {
				formattedTime = time.format(formatter);
			} catch(final DateTimeException e) {
				/* Fallback to ISO-8601 on any eventual formatting failure */
				formattedTime = time.toString();
			}
		} else {
			formattedTime = "";
		}
		return formattedTime;
	}
}