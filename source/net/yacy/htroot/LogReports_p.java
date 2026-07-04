/**
 *  LogReports_p
 *  Copyright 2026 by contributors to the YaCy project
 *  First released 27.06.2026 at https://yacy.net
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 */

package net.yacy.htroot;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

import net.yacy.ai.LogReportService;
import net.yacy.ai.LogReportService.ReportEntry;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class LogReports_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        final LogReportService service = new LogReportService(sb);
        final File reportDirectory = service.getReportDirectory();

        final int configuredMax = sb.getConfigInt(LogReportService.CONFIG_FEED_MAX_ENTRIES, LogReportService.DEFAULT_FEED_MAX_ENTRIES);
        final int requestedMax = post == null ? Math.min(20, configuredMax) : post.getInt("count", Math.min(20, configuredMax));
        final int maxEntries = Math.max(0, Math.min(requestedMax, configuredMax));

        sb.setConfig("ui.LogReports_p.visited", "true");

        prop.put("runReportResult", "0");
        prop.putHTML("runReportFile", "");
        prop.putHTML("runReportResult_runReportFile", "");
        prop.putNum("runReportResult_runReportDurationSeconds", 0);
        if (post != null && post.containsKey("runReportNow")) {
            final long start = System.currentTimeMillis();
            try {
                final File reportFile = service.generateCurrentHourReportOverwrite();
                final long durationSeconds = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
                prop.putNum("runReportResult_runReportDurationSeconds", durationSeconds);
                if (reportFile == null) {
                    prop.put("runReportResult", LogReportService.hasConfiguredLogReportModel() ? "2" : "3");
                } else {
                    prop.put("runReportResult", "1");
                    prop.putHTML("runReportFile", reportFile.getName());
                    prop.putHTML("runReportResult_runReportFile", reportFile.getName());
                }
            } catch (final Exception e) {
                final long durationSeconds = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
                prop.putNum("runReportResult_runReportDurationSeconds", durationSeconds);
                prop.put("runReportResult", "4");
                final String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                prop.putHTML("runReportFile", message);
                prop.putHTML("runReportResult_runReportFile", message);
            }
        }

        final List<ReportEntry> reports = service.discoverReports(maxEntries);

        prop.put("modelConfigured", LogReportService.hasConfiguredLogReportModel() ? "1" : "0");
        prop.putNum("count", maxEntries);
        prop.putNum("maxcount", configuredMax);
        prop.putHTML("reportdir", reportDirectory.getAbsolutePath());
        prop.put("reportdirExists", reportDirectory.isDirectory() ? "1" : "0");
        prop.put("reportdirMissing", reportDirectory.isDirectory() ? "0" : "1");
        prop.put("jsonFeed", "api/logreports.json?count=" + maxEntries);
        prop.put("rssFeed", "api/logreports.rss?count=" + maxEntries);

        for (int i = 0; i < reports.size(); i++) {
            final ReportEntry report = reports.get(i);
            prop.putHTML("reports_" + i + "_filename", report.filename);
            prop.putHTML("reports_" + i + "_title", report.title);
            prop.putHTML("reports_" + i + "_published", report.published.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            prop.putHTML("reports_" + i + "_type", report.daily ? "daily" : "hourly");
            prop.put("reports_" + i + "_daily", report.daily ? "1" : "0");
            prop.putNum("reports_" + i + "_chars", report.content.length());
            prop.putHTML("reports_" + i + "_content", report.content);
        }
        prop.put("reports", reports.size());
        prop.putNum("reportCount", reports.size());
        prop.put("hasReports", reports.isEmpty() ? "0" : "1");
        prop.put("noReports", reports.isEmpty() ? "1" : "0");

        return prop;
    }
}
