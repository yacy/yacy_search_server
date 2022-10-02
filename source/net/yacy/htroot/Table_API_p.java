// Table_API_p.java
// -----------------------
// (C) 2010 by Michael Peter Christen; mc@yacy.net
// first published 01.02.2010 in Frankfurt, Germany on http://yacy.net
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

package net.yacy.htroot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import net.yacy.cora.date.AbstractFormatter;
import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.MultiProtocolURL;
import net.yacy.cora.protocol.Domains;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.data.TransactionManager;
import net.yacy.data.WorkTables;
import net.yacy.kelondro.blob.Tables;
import net.yacy.kelondro.blob.Tables.Row;
import net.yacy.kelondro.blob.Tables.SortDirection;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.search.query.QueryParams;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class Table_API_p {

	/** Default results page size */
	private static final int DEFAULT_MAX_RECORDS = 25;

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

    	final DateFormat dateFormat = GenericFormatter.newSimpleDateFormat();

        prop.put("showexec", 0);
        prop.put("showtable", 0);

        int startRecord = 0;
        int maximumRecords = DEFAULT_MAX_RECORDS;
        Pattern query = QueryParams.catchall_pattern;
        if (post != null && post.containsKey("startRecord")) {
            startRecord = post.getInt("startRecord", 0);
        }
        if (post != null && post.containsKey("maximumRecords")) {
            maximumRecords = post.getInt("maximumRecords", 0);
        }
        String queryParam = "";
        if (post != null && post.containsKey("query") && !post.get("query", "").isEmpty()) {
        	queryParam = post.get("query", "");
            query = Pattern.compile(".*" + queryParam + ".*");
        }
        final boolean inline = (post != null && post.getBoolean("inline"));

        prop.put("inline", (inline) ? 1 : 0);

        Pattern typefilter = QueryParams.catchall_pattern;
        if (post != null && post.containsKey("filter") && post.get("filter", "").length() > 0) {
            typefilter = Pattern.compile(post.get("filter", ".*"));
        }

        /* Applying JSON API convention for the sort parameter format (http://jsonapi.org/format/#fetching-sorting) */
        String sortParam = WorkTables.TABLE_API_COL_DATE_RECORDING;
        if(post != null) {
        	sortParam = post.get("sort", WorkTables.TABLE_API_COL_DATE_RECORDING).trim();
        }
        final SortDirection sortDir;
        final String sortColumn;
        if(sortParam.startsWith("-")) {
        	sortColumn = sortParam.substring(1);
        	sortDir = SortDirection.DESC;
        } else {
        	sortColumn = sortParam;
        	sortDir = SortDirection.ASC;
        }

        // process scheduler and event input actions
        boolean scheduleeventaction = false; // flag if schedule info of row changes
        String current_pk = ""; // pk of changed schedule data row
        if (post != null && post.containsKey("scheduleeventaction")) {
            scheduleeventaction = post.get("scheduleeventaction", "false").equalsIgnoreCase("true");
            prop.put("scheduleeventaction", "false");
            current_pk = post.get("current_pk", "");
        }
        if (post != null && scheduleeventaction && !current_pk.isEmpty()) {

        	/* Check this is a valid transaction */
        	TransactionManager.checkPostTransaction(header, post);

            try {
                final Tables.Row row = sb.tables.select(WorkTables.TABLE_API_NAME, current_pk.getBytes());
                if (row != null) {
                    String action;

                    // events
                    if (post.containsKey("event_select_" + current_pk) && post.get("event_select_" + current_pk, "off").equals("on")) {
                        row.put(WorkTables.TABLE_API_COL_APICALL_EVENT_KIND, "regular");
                        row.put(WorkTables.TABLE_API_COL_APICALL_EVENT_ACTION, "startup");
                    }

                    if (post.containsKey("event_kind_" + current_pk)  ) {
                        if ("off".equals(action = post.get("event_kind_" + current_pk, "off"))) {
                            row.put(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, "");
                        }
                        row.put(WorkTables.TABLE_API_COL_APICALL_EVENT_KIND, action);
                    }

                    if (post.containsKey("event_action_" + current_pk)  ) {
                        row.put(WorkTables.TABLE_API_COL_APICALL_EVENT_ACTION, post.get("event_action_" + current_pk, "startup"));
                    }

                    // scheduler
                    if (post.containsKey("repeat_select_" + current_pk) && post.get("repeat_select_" + current_pk, "off").equals("on")) {
                        row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 7);
                        row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_UNIT, "days");
                    }

                    if (post.containsKey("repeat_time_" + current_pk)  ) {
                        if ("off".equals(action = post.get("repeat_time_" + current_pk, "off"))) {
                            row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 0);
                        } else {
                            row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, Integer.parseInt(action));
                        }
                    }

                    if (post.containsKey("repeat_unit_" + current_pk)  ) {
                        action = post.get("repeat_unit_" + current_pk, "seldays");
                        final int time = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 1);
                        row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_UNIT, action.substring(3));
                        if (action.equals("selminutes") && time > 0 && time < 10) {
                            row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 10);
                        }
                        if (action.equals("selminutes") && time > 59) {
                            row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 59);
                        }
                        if (action.equals("selhours") && time > 23) {
                            row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 23);
                        }
                        if (action.equals("seldays") && time > 30) {
                            row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 30);
                        }
                    }

                    // switch scheduler off if event kind is 'regular'
                    final String kind = row.get(WorkTables.TABLE_API_COL_APICALL_EVENT_KIND, "off");
                    if ("regular".equals(kind)) row.put(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 0);

                    WorkTables.calculateAPIScheduler(row, false);
                    sb.tables.update(WorkTables.TABLE_API_NAME, row);
                }
            } catch (final Throwable e) { ConcurrentLog.logException(e); }
        }

        /* Edition of scheduled next execution dates */
        final Map<String, String> invalidNextExecDateFormats = new HashMap<>();
        final Map<String, String> nextExecDatesBeforeNow = new HashMap<>();
        if (post != null && post.containsKey("submitNextExecDates")) {

        	/* Check this is a valid transaction */
        	TransactionManager.checkPostTransaction(header, post);


        	final String dateNexExecPrefix = "date_next_exec_";
        	final Date now = new Date();
            for (final Map.Entry<String, String> entry : post.entrySet()) {
                if (entry.getKey().startsWith(dateNexExecPrefix) && entry.getValue() != null) {
                    try {
                    	final String rowPkStr = entry.getKey().substring(dateNexExecPrefix.length());
						final Tables.Row row = sb.tables.select(WorkTables.TABLE_API_NAME,
								rowPkStr.getBytes(StandardCharsets.UTF_8));
						if(row != null) {
							final int time = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 0);
							final String dateNextExecStr = entry.getValue().trim();
							try {
								final Date dateNextExec = dateFormat.parse(dateNextExecStr);

								if(time != 0) { // Check there is effectively a schedule period on this row
									if(dateNextExec.before(now)) {
										nextExecDatesBeforeNow.put(rowPkStr, dateNextExecStr);
									} else {
										row.put(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, dateNextExec);

										sb.tables.update(WorkTables.TABLE_API_NAME, row);
									}
								}
							} catch (final ParseException e) {
								invalidNextExecDateFormats.put(rowPkStr, dateNextExecStr);
							}

						}
					} catch (final IOException | SpaceExceededException e) {
						ConcurrentLog.logException(e);
					}
                }
            }
        }

        if (post != null && !post.get("deleterows", "").isEmpty()) {

        	/* Check this is a valid transaction */
        	TransactionManager.checkPostTransaction(header, post);

            for (final Map.Entry<String, String> entry : post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) {
                    try {
                        sb.tables.delete(WorkTables.TABLE_API_NAME, entry.getValue().substring(5).getBytes());
                    } catch (final IOException e) {
                        ConcurrentLog.logException(e);
                    }
                }
            }
        }

        if (post != null && !post.get("deleteold", "").isEmpty()) {

        	/* Check this is a valid transaction */
        	TransactionManager.checkPostTransaction(header, post);

            final int days = post.getInt("deleteoldtime", 365);
            try {
                final Iterator<Row> ri = sb.tables.iterator(WorkTables.TABLE_API_NAME);
                Row row;
                final Date now = new Date();
                final Date limit = new Date(now.getTime() - AbstractFormatter.dayMillis * days);
                final List<byte[]> pkl = new ArrayList<byte[]>();
                while (ri.hasNext()) {
                    row = ri.next();
                    final Date d = row.get(WorkTables.TABLE_API_COL_DATE_RECORDING, now);
                    if (d.before(limit)) {
                        pkl.add(row.getPK());
                    }
                }
                for (final byte[] pk: pkl) {
                    sb.tables.delete(WorkTables.TABLE_API_NAME, pk);
                }

                // store this call as api call; clean the call a bit before
                final Iterator<Entry<String, String[]>> ei = post.getSolrParams().getMap().entrySet().iterator();
                Entry<String, String[]> entry;
                while (ei.hasNext()) {
                    entry = ei.next();
                    if (entry.getKey().startsWith("event_select")) {
                        ei.remove();
                    }
                    if (entry.getKey().startsWith("repeat_select")) {
                        ei.remove();
                    }
                }
                sb.tables.recordAPICall(post, "Table_API_p.html", WorkTables.TABLE_API_TYPE_STEERING, "delete API calls older than " + days + " days");
            } catch (final IOException e) {
                ConcurrentLog.logException(e);
            }
        }

        if (post != null && !post.get("execrows", "").isEmpty()) {

        	/* Check this is a valid transaction */
        	TransactionManager.checkPostTransaction(header, post);

            // create a time-ordered list of events to execute
            final Set<String> pks = new TreeSet<String>();
            for (final Map.Entry<String, String> entry : post.entrySet()) {
                if (entry.getValue().startsWith("mark_")) {
                    pks.add(entry.getValue().substring(5));
                }
            }

            // now call the api URLs and store the result status
            final Map<String, Integer> l = sb.tables.execAPICalls(Domains.LOCALHOST, sb.getLocalPort(), pks, sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_USER_NAME, "admin"), sb.getConfig(SwitchboardConstants.ADMIN_ACCOUNT_B64MD5, ""));

            // construct result table
            prop.put("showexec", l.isEmpty() ? 0 : 1);

            final Iterator<Map.Entry<String, Integer>> resultIterator = l.entrySet().iterator();
            Map.Entry<String, Integer> record;
            int count = 0;
            boolean dark = true;
            while (resultIterator.hasNext()) {
                record = resultIterator.next();
                if (record == null) {
                    continue;
                }
                prop.put("showexec_list_" + count + "_dark", ((dark) ? 1 : 0));
                dark = !dark;
                prop.put("showexec_list_" + count + "_status", record.getValue());
                prop.put("showexec_list_" + count + "_url", record.getKey());
                count++;
            }
            prop.put("showexec_list", count);
        }

        // generate table
        prop.put("showtable", 1);
        prop.put("showtable_inline", inline ? 1 : 0);

        /* Acquire a transaction token for the next POST form submission */
        final String nextTransactionToken = TransactionManager.getTransactionToken(header);
        prop.put(TransactionManager.TRANSACTION_TOKEN_PARAM, nextTransactionToken);
        prop.put("showtable_" + TransactionManager.TRANSACTION_TOKEN_PARAM, nextTransactionToken);

        final Date now = new Date();

        // insert rows
        final List<Tables.Row> table = new ArrayList<Tables.Row>(maximumRecords);
        int count = 0;
        int tablesize = 0;
        int filteredSize = 0;
        try {
            tablesize = sb.tables.size(WorkTables.TABLE_API_NAME);
            final Iterator<Tables.Row> plainIterator = sb.tables.iterator(WorkTables.TABLE_API_NAME);
			final Iterator<Tables.Row> mapIterator;
			if(sortColumn.isEmpty()) {
				mapIterator = plainIterator;
			} else {
				if (WorkTables.TABLE_API_COL_APICALL_COUNT.equals(sortColumn)
						|| WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME.equals(sortColumn)) {
					mapIterator = Tables.orderByInt(plainIterator, sortColumn, 0, sortDir).iterator();
				} else if (WorkTables.TABLE_API_COL_DATE.equals(sortColumn)
						|| WorkTables.TABLE_API_COL_DATE_RECORDING.equals(sortColumn)
						|| WorkTables.TABLE_API_COL_DATE_LAST_EXEC.equals(sortColumn)
						|| WorkTables.TABLE_API_COL_DATE_NEXT_EXEC.equals(sortColumn)) {
					mapIterator = Tables.orderByDate(plainIterator, sortColumn, now, sortDir).iterator();
				} else {
					mapIterator = Tables.orderByString(plainIterator, sortColumn, "", sortDir).iterator();
				}
			}
            Tables.Row r;
            boolean dark = true;
            boolean scheduledactions = false;
            int matchCount = 0;
            byte[] typeb, commentb, urlb;
            String type, comment, url;
			final boolean hasFilter = typefilter != QueryParams.catchall_pattern
					|| query != QueryParams.catchall_pattern;

            // first prepare a list
            while (mapIterator.hasNext()) {
                r = mapIterator.next();
                if (r == null) {
                	continue;
                }
                typeb = r.get(WorkTables.TABLE_API_COL_TYPE);
                if (typeb == null) {
                	continue;
                }
                type = UTF8.String(typeb);
                if (!typefilter.matcher(type).matches()) {
                	continue;
                }
                commentb = r.get(WorkTables.TABLE_API_COL_COMMENT);
                if (commentb == null) {
                	continue;
                }
                comment = UTF8.String(commentb);
                urlb = r.get(WorkTables.TABLE_API_COL_URL);
                if (urlb == null) {
                	continue;
                }
                url = UTF8.String(urlb);
                if(inline) {
                	if (!query.matcher(comment).matches()) {
                    	/* When inlined, the url is not displayed so we search only on the comment */
                    	continue;
                	}
                } else if (!query.matcher(comment).matches() && !query.matcher(url).matches()) {
                	/* The entry is evicted when both url and comment do not match the query */
                	continue;
                }
                if (matchCount >= startRecord && table.size() < maximumRecords) {
                	table.add(r);
                }
                matchCount++;
                if (table.size() >= maximumRecords && !hasFilter) {
                	/* When a filter is defined, we must continue iterating over the table to get the total count of matching items */
                	break;
                }
            }
            if(hasFilter) {
            	filteredSize = matchCount;
            } else {
            	filteredSize = tablesize;
            }

            // then work on the list
            for (final Tables.Row row : table) {
            	final String rowPKStr = UTF8.String(row.getPK());
                final Date date = row.containsKey(WorkTables.TABLE_API_COL_DATE) ? row.get(WorkTables.TABLE_API_COL_DATE, now) : null;
                final Date date_recording = row.get(WorkTables.TABLE_API_COL_DATE_RECORDING, date);
                final Date date_last_exec = row.get(WorkTables.TABLE_API_COL_DATE_LAST_EXEC, date);
                final Date date_next_exec = row.get(WorkTables.TABLE_API_COL_DATE_NEXT_EXEC, (Date) null);
                final int callcount = row.get(WorkTables.TABLE_API_COL_APICALL_COUNT, 1);
                final int time = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_TIME, 0);
                prop.put("showtable_list_" + count + "_inline", inline ? 1 : 0);
                prop.put("showtable_list_" + count + "_dark", dark ? 1 : 0);
                dark = !dark;
                prop.put("showtable_list_" + count + "_pk", UTF8.String(row.getPK()));
                prop.put("showtable_list_" + count + "_count", count);
                prop.put("showtable_list_" + count + "_callcount", callcount);
                prop.put("showtable_list_" + count + "_dateRecording", date_recording == null ? "-" : dateFormat.format(date_recording));
                prop.put("showtable_list_" + count + "_dateLastExec", date_last_exec == null ? "-" : dateFormat.format(date_last_exec));

                prop.put("showtable_list_" + count + "_editableDateNext", time != 0);
                final String enteredDateBeforeNow = nextExecDatesBeforeNow.get(rowPKStr);
                prop.put("showtable_list_" + count + "_editableDateNext_dateBeforeNowError", enteredDateBeforeNow != null);
                if(enteredDateBeforeNow != null) {
                	prop.put("showtable_list_" + count + "_editableDateNext_dateBeforeNowError_invalidDate", enteredDateBeforeNow);
                }

                final String invalidEnteredDate = invalidNextExecDateFormats.get(rowPKStr);
                prop.put("showtable_list_" + count + "_editableDateNext_dateFormatError", invalidEnteredDate != null);
                if(invalidEnteredDate != null) {
                	prop.put("showtable_list_" + count + "_editableDateNext_dateFormatError_invalidDate", invalidEnteredDate);
                }

                prop.put("showtable_list_" + count + "_editableDateNext_dateLastExecPattern", GenericFormatter.PATTERN_SIMPLE_REGEX);
                prop.put("showtable_list_" + count + "_editableDateNext_dateNextExec", date_next_exec == null ? "-" : dateFormat.format(date_next_exec));
                prop.put("showtable_list_" + count + "_editableDateNext_pk", rowPKStr);

                prop.put("showtable_list_" + count + "_type", row.get(WorkTables.TABLE_API_COL_TYPE));
                prop.putHTML("showtable_list_" + count + "_comment", row.get(WorkTables.TABLE_API_COL_COMMENT));
                // check type & action to link crawl start URLs back to CrawlStartExpert.html
                if (prop.get("showtable_list_" + count + "_type", "").equals(WorkTables.TABLE_API_TYPE_CRAWLER)
                        && prop.get("showtable_list_" + count + "_comment", "").startsWith("crawl start for")) {
                    final String editUrl = UTF8.String(row.get(WorkTables.TABLE_API_COL_URL)).replace("Crawler_p", "CrawlStartExpert");
                    if (editUrl.length() > 1000) {
                        final MultiProtocolURL u = new MultiProtocolURL("http://localhost:8090" + editUrl);
                        prop.put("showtable_list_" + count + "_isCrawlerStart", 2);
                        prop.put("showtable_list_" + count + "_isCrawlerStart_pk", rowPKStr);
                        prop.put("showtable_list_" + count + "_isCrawlerStart_servlet", "CrawlStartExpert.html");
                        final Map<String, String> attr = u.getAttributes();
                        int ac = 0;
                        for (final Map.Entry<String, String> entry: attr.entrySet()) {
                            prop.put("showtable_list_" + count + "_isCrawlerStart_attr_" + ac + "_key", entry.getKey());
                            prop.put("showtable_list_" + count + "_isCrawlerStart_attr_" + ac + "_value", entry.getValue());
                            ac++;
                        }
                        prop.put("showtable_list_" + count + "_isCrawlerStart_attr", ac);
                    } else {
                        // short calls
                        prop.put("showtable_list_" + count + "_isCrawlerStart", 1);
                        /* For better integration of YaCy peers behind a reverse proxy subfolder,
                         * ensure a path relative to this servlet (with no starting slash) is used for clone links.
                         * We keep the paths starting with a slash for other URL displays. */
                        prop.put("showtable_list_" + count + "_isCrawlerStart_url", editUrl.startsWith("/") ? editUrl.substring(1, editUrl.length()) : editUrl);
                    }
                } else {
                    prop.put("showtable_list_" + count + "_isCrawlerStart", 0);
                }
                prop.putHTML("showtable_list_" + count + "_inline_url", UTF8.String(row.get(WorkTables.TABLE_API_COL_URL)));
                prop.put("showtable_list_" + count + "_scheduler_inline", inline ? "true" : "false");
                prop.put("showtable_list_" + count + "_scheduler_filter", typefilter.pattern());
                prop.put("showtable_list_" + count + "_scheduler_query", query.pattern());
                prop.put("showtable_list_" + count + "_scheduler_startRecord", startRecord);
                prop.put("showtable_list_" + count + "_scheduler_maximumRecords", maximumRecords);

                // events
                final String kind = row.get(WorkTables.TABLE_API_COL_APICALL_EVENT_KIND, "off");
                final String action = row.get(WorkTables.TABLE_API_COL_APICALL_EVENT_ACTION, "startup");
                prop.put("showtable_list_" + count + "_event_pk", rowPKStr);
                final boolean schedulerDisabled = "regular".equals(kind);
                if ("off".equals(kind)) {
                    prop.put("showtable_list_" + count + "_event", 0);
                } else {
                    prop.put("showtable_list_" + count + "_event", 1);
                    prop.put("showtable_list_" + count + "_event_selectedoff", "off".equals(kind) ? 1 : 0);
                    prop.put("showtable_list_" + count + "_event_selectedonce", "once".equals(kind) ? 1 : 0);
                    prop.put("showtable_list_" + count + "_event_selectedregular", "regular".equals(kind) ? 1 : 0);
                    prop.put("showtable_list_" + count + "_event_selectedstartup", "startup".equals(action) ? 1 : 0);
                    for (int i = 0; i < 24; i++) {
                        String is = Integer.toString(i);
                        if (is.length() == 1) is = "0" + is;
                        is = is + "00";
                        prop.put("showtable_list_" + count + "_event_selected" + is, is.equals(action) ? 1 : 0);
                    }
                }

                // scheduler
                final String unit = row.get(WorkTables.TABLE_API_COL_APICALL_SCHEDULE_UNIT, "days");
                prop.put("showtable_list_" + count + "_selectedMinutes", unit.equals("minutes") ? 1 : 0);
                prop.put("showtable_list_" + count + "_selectedHours", unit.equals("hours") ? 1 : 0);
                prop.put("showtable_list_" + count + "_selectedDays", (unit.isEmpty() || unit.equals("days")) ? 1 : 0);
                prop.put("showtable_list_" + count + "_scheduler_pk", rowPKStr);
                prop.put("showtable_list_" + count + "_scheduler_disabled", schedulerDisabled ? 1 : 0);
                prop.put("showtable_list_" + count + "_repeatTime", time);
                if (time == 0) {
                    prop.put("showtable_list_" + count + "_scheduler", 0);
                } else {
                    scheduledactions = true;
                    prop.put("showtable_list_" + count + "_scheduler", 1);
                    prop.put("showtable_list_" + count + "_scheduler_scale_" + 0 + "_time", "off");
                    prop.put("showtable_list_" + count + "_scheduler_selectedMinutes", 0);
                    prop.put("showtable_list_" + count + "_scheduler_selectedHours", 0);
                    prop.put("showtable_list_" + count + "_scheduler_selectedDays", 0);
                    if (unit.equals("minutes")) {
                        for (int i = 1; i <= 5; i++) {
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_time", i * 10);
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_selected", 0);
                        }
                        prop.put("showtable_list_" + count + "_scheduler_scale_" + (time / 10) + "_selected", 1);
                        prop.put("showtable_list_" + count + "_scheduler_scale", 6);
                        prop.put("showtable_list_" + count + "_scheduler_selectedMinutes", 1);
                    } else if (unit.equals("hours")) {
                        for (int i = 1; i <= 23; i++) {
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_time", i);
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_selected", 0);
                        }
                        prop.put("showtable_list_" + count + "_scheduler_scale_" + time + "_selected", 1);
                        prop.put("showtable_list_" + count + "_scheduler_scale", 24);
                        prop.put("showtable_list_" + count + "_scheduler_selectedHours", 1);
                    } else {
                        for (int i = 1; i <= 30; i++) {
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_time", i);
                            prop.put("showtable_list_" + count + "_scheduler_scale_" + i + "_selected", 0);
                        }
                        prop.put("showtable_list_" + count + "_scheduler_scale_" + time + "_selected", 1);
                        prop.put("showtable_list_" + count + "_scheduler_scale", 31);
                        prop.put("showtable_list_" + count + "_scheduler_selectedDays", 1);
                    }

                }
                count++;
            }
            prop.put("showtable_hasEditableNextExecDate", scheduledactions);
            if (scheduledactions) {
                prop.put("showschedulerhint", 1);
                prop.put("showschedulerhint_tfminutes", sb.getConfigLong(SwitchboardConstants.CLEANUP_BUSYSLEEP, 300000) / 60000);
            } else {
                prop.put("showschedulerhint", 0);
            }
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
        }
        prop.put("showtable_list", count);
        prop.put("showtable_num", count);

        // write navigation details
        prop.put("showtable_startRecord", startRecord);
        prop.put("showtable_maximumRecords", maximumRecords);
        prop.put("showtable_inline", (inline) ? 1 : 0);
        prop.put("showtable_filter", typefilter.pattern());
        prop.put("showtable_query", queryParam);
        prop.put("showtable_sort", sortParam);

        putTableSortProperties(prop, sortDir, sortColumn);

        if (filteredSize > maximumRecords) {
            prop.put("showtable_navigation", 1);
            prop.put("showtable_navigation_startRecord", startRecord);
            prop.put("showtable_navigation_to", Math.min(filteredSize, startRecord + table.size()));
            prop.put("showtable_navigation_of", filteredSize);
            prop.put("showtable_navigation_left", startRecord == 0 ? 0 : 1);
            prop.put("showtable_navigation_left_startRecord", Math.max(0, startRecord - maximumRecords));
            prop.put("showtable_navigation_left_maximumRecords", maximumRecords);
            prop.put("showtable_navigation_left_inline", (inline) ? 1 : 0);
            prop.put("showtable_navigation_left_filter", typefilter.pattern());
            prop.put("showtable_navigation_left_query", queryParam);
            prop.put("showtable_navigation_left_sort", sortParam);
            prop.put("showtable_navigation_left", startRecord == 0 ? 0 : 1);
            prop.put("showtable_navigation_filter", typefilter.pattern());
            prop.put("showtable_navigation_right", startRecord + maximumRecords >= filteredSize ? 0 : 1);
            prop.put("showtable_navigation_right_startRecord", startRecord + maximumRecords);
            prop.put("showtable_navigation_right_maximumRecords", maximumRecords);
            prop.put("showtable_navigation_right_inline", (inline) ? 1 : 0);
            prop.put("showtable_navigation_right_filter", typefilter.pattern());
            prop.put("showtable_navigation_right_query", queryParam);
            prop.put("showtable_navigation_right_sort", sortParam);
        } else {
            prop.put("showtable_navigation", 0);
        }

        // return rewrite properties
        return prop;
    }

	/**
	 * Fill the serverObjects instance with table columns sort properties.
	 *
	 * @param prop
	 *            the serverObjects instance to fill. Must not be null.
	 * @param sortDir
	 *            the current sort direction
	 * @param sortColumn
	 *            the current sort column
	 */
	private static void putTableSortProperties(final serverObjects prop, final SortDirection sortDir,
			final String sortColumn) {
		boolean sortedByAsc = WorkTables.TABLE_API_COL_TYPE.equals(sortColumn) && sortDir == SortDirection.ASC;
		prop.put("showtable_sortedByType", WorkTables.TABLE_API_COL_TYPE.equals(sortColumn));
		prop.put("showtable_sortedByType_asc", sortedByAsc);
		prop.put("showtable_nextSortTypeDesc", sortedByAsc);

		sortedByAsc = WorkTables.TABLE_API_COL_COMMENT.equals(sortColumn) && sortDir == SortDirection.ASC;
		prop.put("showtable_sortedByComment", WorkTables.TABLE_API_COL_COMMENT.equals(sortColumn));
		prop.put("showtable_sortedByComment_asc", sortedByAsc);
		prop.put("showtable_nextSortCommentDesc", sortedByAsc);

		sortedByAsc = WorkTables.TABLE_API_COL_APICALL_COUNT.equals(sortColumn) && sortDir == SortDirection.ASC;
		prop.put("showtable_sortedByApiCallCount", WorkTables.TABLE_API_COL_APICALL_COUNT.equals(sortColumn));
		prop.put("showtable_sortedByApiCallCount_asc", sortedByAsc);
		prop.put("showtable_nextSortApiCallCountDesc", sortedByAsc);

		sortedByAsc = WorkTables.TABLE_API_COL_DATE_RECORDING.equals(sortColumn) && sortDir == SortDirection.ASC;
		prop.put("showtable_sortedByDateRecording", WorkTables.TABLE_API_COL_DATE_RECORDING.equals(sortColumn));
		prop.put("showtable_sortedByDateRecording_asc", sortedByAsc);
		prop.put("showtable_nextSortDateRecordingDesc", sortedByAsc);

		sortedByAsc = WorkTables.TABLE_API_COL_DATE_LAST_EXEC.equals(sortColumn) && sortDir == SortDirection.ASC;
		prop.put("showtable_sortedByDateLastExec", WorkTables.TABLE_API_COL_DATE_LAST_EXEC.equals(sortColumn));
		prop.put("showtable_sortedByDateLastExec_asc", sortedByAsc);
		prop.put("showtable_nextSortDateLastExecDesc", sortedByAsc);

		sortedByAsc = WorkTables.TABLE_API_COL_DATE_NEXT_EXEC.equals(sortColumn) && sortDir == SortDirection.ASC;
		prop.put("showtable_sortedByDateNextExec", WorkTables.TABLE_API_COL_DATE_NEXT_EXEC.equals(sortColumn));
		prop.put("showtable_sortedByDateNextExec_asc", sortedByAsc);
		prop.put("showtable_nextSortDateNextExecDesc", sortedByAsc);
	}
}
