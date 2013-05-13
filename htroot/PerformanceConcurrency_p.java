// PerformanceConcurrency_p.java
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http:// www.yacy.net
// Frankfurt, Germany, 19.12.2008
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

import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.workflow.WorkflowProcessor;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class PerformanceConcurrency_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, @SuppressWarnings("unused") final serverObjects post, @SuppressWarnings("unused") final serverSwitch sb) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();

        // calculate totals
        long blocktime_total = 0, exectime_total = 0, passontime_total = 0;
        Iterator<WorkflowProcessor<?>> threads = WorkflowProcessor.processes();
        WorkflowProcessor<?> p;
        while (threads.hasNext()) {
            p = threads.next();
            blocktime_total += p.getBlockTime();
            exectime_total += p.getExecTime();
            passontime_total += p.getPassOnTime();
        }
        if (blocktime_total == 0) blocktime_total = 1;
        if (exectime_total == 0) exectime_total = 1;
        if (passontime_total == 0) passontime_total = 1;

        // set templates for latest news from the threads
        long blocktime, exectime, passontime;
        threads = WorkflowProcessor.processes();
        int c = 0;
        long cycles;
        while (threads.hasNext()) {
            p = threads.next();
            cycles = p.getExecCount();
            if (cycles == 0) cycles = 1; // avoid division by zero

            // set values to templates
            prop.put("table_" + c + "_threadname", p.getName());
            prop.putHTML("table_" + c + "_longdescr", p.getDescription());
            prop.put("table_" + c + "_queuesize", p.getQueueSize());
            prop.put("table_" + c + "_queuesizemax", p.getMaxQueueSize());
            prop.put("table_" + c + "_concurrency", p.getMaxConcurrency());
            prop.put("table_" + c + "_executors", p.getExecutors());
            prop.putHTML("table_" + c + "_childs", p.getChilds());

            blocktime = p.getBlockTime();
            exectime = p.getExecTime();
            passontime = p.getPassOnTime();
            prop.putNum("table_" + c + "_blockreadtime", blocktime / cycles);
            prop.putNum("table_" + c + "_blockreadpercent", 100 * blocktime / blocktime_total);
            prop.putNum("table_" + c + "_exectime", exectime / cycles);
            prop.putNum("table_" + c + "_execpercent", 100 * exectime / exectime_total);
            prop.putNum("table_" + c + "_blockwritetime", passontime / cycles);
            prop.putNum("table_" + c + "_blockwritepercent", 100 * passontime / passontime_total);
            prop.putNum("table_" + c + "_totalcycles", p.getExecCount());

            // set a color for the line to show problems
            boolean problem = false;
            boolean warning = false;
            if (p.getQueueSize() == p.getMaxQueueSize()) problem = true;
            if (p.getQueueSize() > p.getMaxQueueSize() * 8 / 10) warning = true;
            if (100 * blocktime / blocktime_total > 80) warning = true;
            if (100 * exectime / exectime_total > 80) warning = true;
            if (100 * passontime / passontime_total > 80) warning = true;
            prop.put("table_" + c + "_class", (!warning && !problem) ? 0 : (!problem) ? 1 : 2);
            c++;
        }
        prop.put("table", c);
        // return rewrite values for templates
        return prop;
    }
}
