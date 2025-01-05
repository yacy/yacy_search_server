// InstantBlockingThread.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.03.2008 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.kelondro.workflow;

import java.util.concurrent.atomic.AtomicInteger;

import net.yacy.cora.util.ConcurrentLog;


public class InstantBlockingThread<J extends WorkflowJob> extends AbstractBlockingThread<J> implements BlockingThread<J> {
    private static final String BLOCKINGTHREAD = "BLOCKINGTHREAD";

    private final WorkflowTask<J> task;
    private static AtomicInteger handleCounter = new AtomicInteger(0);
    private static AtomicInteger instantThreadCounter = new AtomicInteger(0);

    public InstantBlockingThread(final WorkflowProcessor<J> manager) {
        super();
        
        // set the manager of blocking queues for input and output
        setManager(manager);

        // define task to be executed
        this.task = manager.getTask();
        setName(manager.getName() + "." + handleCounter.getAndIncrement());
    }

    @Override
    public int getJobCount() {
        return getManager().getQueueSize();
    }

    @Override
    @SuppressWarnings("unchecked")
    public J job(final J next) throws Exception {
        J out = null;

        // see if we got a poison pill to tell us to shut down
        if (next == null) {
            out = (J) WorkflowJob.poisonPill;
        } else if (next == WorkflowJob.poisonPill || next.status == WorkflowJob.STATUS_POISON) {
            out = next;
        } else {
            final long t = System.currentTimeMillis();

            instantThreadCounter.incrementAndGet();
            //System.out.println("started job " + this.handle + ": " + this.getName());

            try {
                out = this.task.process(next);
            } catch (final Throwable e) {
                ConcurrentLog.severe(BLOCKINGTHREAD, "Internal Error in serverInstantThread.job: " + e.getMessage());
                ConcurrentLog.severe(BLOCKINGTHREAD, "shutting down thread '" + getName() + "'");
                ConcurrentLog.logException(e);
                ConcurrentLog.logException(e.getCause());
                ConcurrentLog.severe(BLOCKINGTHREAD, "Runtime Error in serverInstantThread.job, thread '" + getName() + "': " + e.getMessage());
            }
            instantThreadCounter.decrementAndGet();
            getManager().increaseJobTime(System.currentTimeMillis() - t);
        }
        return out;
    }

}