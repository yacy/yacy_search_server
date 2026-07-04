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

        // fixed display limit; the navigation column shows at most this many reports
        final int maxEntries = 100;

        sb.setConfig("ui.LogReports_p.visited", "true");

        // "run report now" starts the computation asynchronously (the LLM call can take
        // minutes and would run into the request timeout); the page polls itself while
        // LogReportService reports the job as running and shows the result afterwards
        prop.put("runReportResult", "0");
        prop.putHTML("runReportResult_runReportFile", "");
        prop.putNum("runReportResult_runReportDurationSeconds", 0);
        if (post != null && post.containsKey("runReportNow")) {
            service.startManualReportJob(); // no-op if a job is already running
        }
        final boolean reportRunning = LogReportService.isManualReportJobRunning();
        prop.put("reportRunning", reportRunning ? "1" : "0");
        prop.putNum("reportRunning_elapsedSeconds", LogReportService.manualReportJobElapsedSeconds());
        if (!reportRunning) {
            final LogReportService.ManualJobResult result = LogReportService.consumeManualReportJobResult();
            if (result != null) {
                prop.put("runReportResult", result.outcome);
                prop.putHTML("runReportResult_runReportFile", result.message);
                prop.putNum("runReportResult_runReportDurationSeconds", result.durationSeconds);
            }
        }

        // delete a report when requested from the navigation column (immediate, no
        // confirmation); the filename is strictly validated against the report
        // filename patterns, which excludes any path traversal
        final String deleteReport = post == null ? "" : post.get("deleteReport", "");
        if (deleteReport.matches("report-\\d{4}-\\d{2}-\\d{2}(-\\d{2})?\\.md")) {
            final File deleteFile = new File(reportDirectory, deleteReport);
            if (deleteFile.isFile() && !deleteFile.delete()) {
                net.yacy.cora.util.ConcurrentLog.warn("LogReports", "could not delete log report " + deleteFile.getAbsolutePath());
            }
        }

        final List<ReportEntry> reports = service.discoverReports(maxEntries);

        prop.put("modelConfigured", LogReportService.hasConfiguredLogReportModel() ? "1" : "0");
        prop.putHTML("reportdir", reportDirectory.getAbsolutePath());
        prop.put("reportdirExists", reportDirectory.isDirectory() ? "1" : "0");
        prop.put("reportdirMissing", reportDirectory.isDirectory() ? "0" : "1");
        prop.put("jsonFeed", "api/logreports.json?count=" + maxEntries);
        prop.put("rssFeed", "api/logreports.rss?count=" + maxEntries);

        // the report to show in the main view is selected by filename from the navigation
        // column; matching against the discovered list only prevents path traversal.
        // default is the newest report (first entry, list is sorted newest first)
        final String requestedReport = post == null ? "" : post.get("report", "");
        ReportEntry selected = null;
        for (final ReportEntry report : reports) {
            if (report.filename.equals(requestedReport)) {
                selected = report;
                break;
            }
        }
        if (selected == null && !reports.isEmpty()) selected = reports.get(0);

        // navigation list and selected report live inside the #(hasReports)# alternative,
        // therefore all template keys carry the "hasReports_" prefix
        for (int i = 0; i < reports.size(); i++) {
            final ReportEntry report = reports.get(i);
            final String p = "hasReports_reports_" + i + "_";
            prop.putHTML(p + "filename", report.filename);
            prop.putHTML(p + "title", report.title);
            prop.putHTML(p + "date", report.published.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            prop.putHTML(p + "type", report.daily ? "daily" : "hourly");
            prop.put(p + "selected", report == selected ? "1" : "0");
        }
        prop.put("hasReports_reports", reports.size());

        prop.put("hasReports_selectedReport", selected == null ? "0" : "1");
        if (selected != null) {
            prop.putHTML("hasReports_selectedReport_filename", selected.filename);
            prop.putHTML("hasReports_selectedReport_title", selected.title);
            prop.putHTML("hasReports_selectedReport_published", selected.published.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            prop.putHTML("hasReports_selectedReport_type", selected.daily ? "daily" : "hourly");
            prop.putNum("hasReports_selectedReport_chars", selected.content.length());
            prop.putHTML("hasReports_selectedReport_content", selected.content);
        }
        prop.putNum("reportCount", reports.size());
        prop.put("hasReports", reports.isEmpty() ? "0" : "1");
        prop.put("noReports", reports.isEmpty() ? "1" : "0");

        return prop;
    }
}
