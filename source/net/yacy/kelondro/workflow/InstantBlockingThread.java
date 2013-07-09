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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.yacy.cora.util.ConcurrentLog;


public class InstantBlockingThread<J extends WorkflowJob> extends AbstractBlockingThread<J> implements BlockingThread<J> {
    private static final String BLOCKINGTHREAD = "BLOCKINGTHREAD";

    private final Method jobExecMethod;
    private final Object environment;
    private final Long   handle;
    private static AtomicInteger handleCounter = new AtomicInteger(0);
    private static AtomicInteger instantThreadCounter = new AtomicInteger(0);
    private static final ConcurrentMap<Long, String> jobs = new ConcurrentHashMap<Long, String>();

    public InstantBlockingThread(final WorkflowProcessor<J> manager) {
        // jobExec is the name of a method of the object 'env' that executes the one-step-run
        // jobCount is the name of a method that returns the size of the job

        // set the manager of blocking queues for input and output
        setManager(manager);

        // define execution class
        final Object env = manager.getEnvironment();
        final String jobExec = manager.getMethodName();
        this.jobExecMethod = execMethod(env, jobExec);
        this.environment = (env instanceof Class<?>) ? null : env;
        setName(this.jobExecMethod.getClass().getName() + "." + this.jobExecMethod.getName() + "." + handleCounter.getAndIncrement());
        this.handle = Long.valueOf(System.currentTimeMillis() + getName().hashCode());
    }

    protected static Method execMethod(final Object env, final String jobExec) {
        final Class<?> theClass = (env instanceof Class<?>) ? (Class<?>) env : env.getClass();
        try {
            for (final Method method: theClass.getMethods()) {
                if ((method.getParameterTypes().length == 1) && (method.getName().equals(jobExec))) {
                    return method;
                }
            }
            throw new NoSuchMethodException(jobExec + " does not exist in " + env.getClass().getName());
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of jobExec: " + e.getMessage());
        }
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
            jobs.put(this.handle, getName());

            try {
                out = (J) this.jobExecMethod.invoke(this.environment, new Object[]{next});
            } catch (final Throwable e) {
                ConcurrentLog.severe(BLOCKINGTHREAD, "Internal Error in serverInstantThread.job: " + e.getMessage());
                ConcurrentLog.severe(BLOCKINGTHREAD, "shutting down thread '" + getName() + "'");
                final Throwable targetException = (e instanceof InvocationTargetException) ? ((InvocationTargetException) e).getTargetException() : null;
                ConcurrentLog.logException(e);
                ConcurrentLog.logException(e.getCause());
                if (targetException != null) ConcurrentLog.logException(targetException);
                ConcurrentLog.severe(BLOCKINGTHREAD, "Runtime Error in serverInstantThread.job, thread '" + getName() + "': " + e.getMessage());
            }
            instantThreadCounter.decrementAndGet();
            jobs.remove(this.handle);
            getManager().increaseJobTime(System.currentTimeMillis() - t);
        }
        return out;
    }

}