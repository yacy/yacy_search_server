/**
 *  logreports
 *  Copyright 2026 by contributors to the YaCy project
 *  First released 26.06.2026 at https://yacy.net
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 */

package net.yacy.htroot.api;

import java.time.format.DateTimeFormatter;
import java.util.List;

import net.yacy.ai.LogReportService;
import net.yacy.ai.LogReportService.ReportEntry;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class logreports {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        prop.put("authorized", "0");
        prop.put("reports", "0");
        prop.putXML("channel_title", "YaCy Log Reports");
        prop.putXML("channel_description", "Generated YaCy self-enhancement log reports");
        prop.put("channel_pubDate", "");

        if (header == null || env == null) return prop;
        final Switchboard sb = (Switchboard) env;
        if (!sb.verifyAuthentication(header)) return prop;
        prop.put("authorized", "1");

        final int configuredMax = sb.getConfigInt(LogReportService.CONFIG_FEED_MAX_ENTRIES, LogReportService.DEFAULT_FEED_MAX_ENTRIES);
        final int requestedMax = post == null ? configuredMax : post.getInt("count", configuredMax);
        final int maxEntries = Math.max(0, Math.min(requestedMax, configuredMax));
        final List<ReportEntry> reports = new LogReportService(sb).discoverReports(maxEntries);

        for (int i = 0; i < reports.size(); i++) {
            final ReportEntry report = reports.get(i);
            prop.putJSON("reports_" + i + "_filename", report.filename);
            prop.putJSON("reports_" + i + "_title", report.title);
            prop.put("reports_" + i + "_published", report.published.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            prop.putJSON("reports_" + i + "_content", report.content);
            prop.put("reports_" + i + "_daily", report.daily ? "1" : "0");
            prop.put("reports_" + i + "_comma", i + 1 < reports.size() ? "1" : "0");

            prop.putXML("reports_" + i + "_title-rss", report.title);
            prop.putXML("reports_" + i + "_description-rss", report.content);
            prop.put("reports_" + i + "_pubDate-rss", report.published.format(DateTimeFormatter.RFC_1123_DATE_TIME));
            prop.putXML("reports_" + i + "_guid-rss", report.filename);
        }
        prop.put("reports", reports.size());
        if (!reports.isEmpty()) {
            prop.put("channel_pubDate", reports.get(0).published.format(DateTimeFormatter.RFC_1123_DATE_TIME));
        }
        return prop;
    }
}
