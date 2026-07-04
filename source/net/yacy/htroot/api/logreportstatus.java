/**
 *  logreportstatus
 *  Copyright 2026 by contributors to the YaCy project
 *  First released 04.07.2026 at https://yacy.net
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 */

package net.yacy.htroot.api;

import net.yacy.ai.LogReportService;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/**
 * Polling endpoint for the manual "run report now" job on /LogReports_p.html.
 * Returns the job state and the partially generated report text, so the page can
 * show the report growing live (via JavaScript, without hard page reloads) while
 * the LLM is still streaming its output. When the job has finished, the first
 * poll consumes the one-time job result and reports it in outcome/message.
 */
public class logreportstatus {

    public static serverObjects respond(final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, final serverSwitch env) {
        final serverObjects prop = new serverObjects();
        prop.put("authorized", "0");
        prop.put("running", "0");
        prop.putNum("elapsedSeconds", 0);
        prop.putJSON("partial", "");
        prop.putNum("outcome", 0);
        prop.putJSON("message", "");
        prop.putNum("durationSeconds", 0);

        if (header == null || env == null) return prop;
        final Switchboard sb = (Switchboard) env;
        if (!sb.verifyAuthentication(header)) return prop;
        prop.put("authorized", "1");

        final boolean running = LogReportService.isManualReportJobRunning();
        prop.put("running", running ? "1" : "0");
        prop.putNum("elapsedSeconds", LogReportService.manualReportJobElapsedSeconds());
        prop.putJSON("partial", LogReportService.manualReportJobPartialReport());
        if (!running) {
            final LogReportService.ManualJobResult result = LogReportService.consumeManualReportJobResult();
            if (result != null) {
                prop.putNum("outcome", result.outcome);
                prop.putJSON("message", result.message);
                prop.putNum("durationSeconds", result.durationSeconds);
            }
        }
        return prop;
    }
}
