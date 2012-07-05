// InstantBusyThread.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
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

package net.yacy.kelondro.workflow;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.TreeMap;

import net.yacy.kelondro.logging.Log;


public final class InstantBusyThread extends AbstractBusyThread implements BusyThread {

    private Method jobExecMethod, jobCountMethod, freememExecMethod;
    private final Object environment;
    private final Long   handle;

    public static int instantThreadCounter = 0;
    public static final TreeMap<Long, String> jobs = new TreeMap<Long, String>();

    public InstantBusyThread(
              final Object env,
              final String jobExec,
              final String jobCount,
              final String freemem,
              final long minIdleSleep,
              final long maxIdleSleep,
              final long minBusySleep,
              final long maxBusySleep) {
        super(minIdleSleep, maxIdleSleep, minBusySleep, maxBusySleep);

        // jobExec is the name of a method of the object 'env' that executes the one-step-run
        // jobCount is the name of a method that returns the size of the job
        // freemem is the name of a method that tries to free memory and returns void
        final Class<?> theClass = (env instanceof Class<?>) ? (Class<?>) env : env.getClass();
        try {
            this.jobExecMethod = theClass.getMethod(jobExec);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of jobExec: " + e.getMessage());
        }
        try {
            if (jobCount == null)
                this.jobCountMethod = null;
            else
                this.jobCountMethod = theClass.getMethod(jobCount);

        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of jobCount: " + e.getMessage());
        }
        try {
            if (freemem == null)
                this.freememExecMethod = null;
            else
                this.freememExecMethod = theClass.getMethod(freemem);

        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of freemem: " + e.getMessage());
        }
        this.environment = (env instanceof Class<?>) ? null : env;
        setName(theClass.getName() + "." + jobExec);
        this.handle = Long.valueOf(System.currentTimeMillis() + getName().hashCode());
    }

    @Override
    public int getJobCount() {
        if (this.jobCountMethod == null) return Integer.MAX_VALUE;
        try {
            final Object result = this.jobCountMethod.invoke(this.environment);
            return (result instanceof Integer) ? ((Integer) result).intValue() : -1;
        } catch (final IllegalAccessException e) {
            return -1;
        } catch (final IllegalArgumentException e) {
            return -1;
        } catch (final InvocationTargetException e) {
            Log.logSevere("BUSYTHREAD", "invocation serverInstantThread of thread '" + getName() + "': " + e.getMessage(), e);
            return -1;
        }
    }

    @Override
    public boolean job() throws Exception {
        instantThreadCounter++;
        //System.out.println("started job " + this.handle + ": " + this.getName());
        synchronized(jobs) {jobs.put(this.handle, getName());}
        boolean jobHasDoneSomething = false;
        try {
            final Object result = this.jobExecMethod.invoke(this.environment);
            if (result == null) jobHasDoneSomething = true;
            else if (result instanceof Boolean) jobHasDoneSomething = ((Boolean) result).booleanValue();
        } catch (final IllegalAccessException e) {
            Log.logSevere("BUSYTHREAD", "Internal Error in serverInstantThread.job: " + e.getMessage());
            Log.logSevere("BUSYTHREAD", "shutting down thread '" + getName() + "'");
            terminate(false);
        } catch (final IllegalArgumentException e) {
            Log.logSevere("BUSYTHREAD", "Internal Error in serverInstantThread.job: " + e.getMessage());
            Log.logSevere("BUSYTHREAD", "shutting down thread '" + getName() + "'");
            terminate(false);
        } catch (final InvocationTargetException e) {
            final String targetException = e.getTargetException().getMessage();
            Log.logException(e);
            Log.logException(e.getCause());
            Log.logException(e.getTargetException());
            Log.logSevere("BUSYTHREAD", "Runtime Error in serverInstantThread.job, thread '" + getName() + "': " + e.getMessage() + "; target exception: " + targetException, e.getTargetException());
        } catch (final OutOfMemoryError e) {
            Log.logSevere("BUSYTHREAD", "OutOfMemory Error in serverInstantThread.job, thread '" + getName() + "': " + e.getMessage());
            Log.logException(e);
            freemem();
        } catch (final Exception e) {
            Log.logSevere("BUSYTHREAD", "Generic Exception, thread '" + getName() + "': " + e.getMessage());
            Log.logException(e);
        }
        instantThreadCounter--;
        synchronized(jobs) {jobs.remove(this.handle);}
        return jobHasDoneSomething;
    }

    @Override
    public void freemem() {
        if (this.freememExecMethod == null) return;
        try {
            this.freememExecMethod.invoke(this.environment);
        } catch (final IllegalAccessException e) {
            Log.logSevere("BUSYTHREAD", "Internal Error in serverInstantThread.freemem: " + e.getMessage());
            Log.logSevere("BUSYTHREAD", "shutting down thread '" + getName() + "'");
            terminate(false);
        } catch (final IllegalArgumentException e) {
            Log.logSevere("BUSYTHREAD", "Internal Error in serverInstantThread.freemem: " + e.getMessage());
            Log.logSevere("BUSYTHREAD", "shutting down thread '" + getName() + "'");
            terminate(false);
        } catch (final InvocationTargetException e) {
            final String targetException = e.getTargetException().getMessage();
            if (targetException.indexOf("heap space",0) > 0) Log.logException(e.getTargetException());
            Log.logSevere("BUSYTHREAD", "Runtime Error in serverInstantThread.freemem, thread '" + getName() + "': " + e.getMessage() + "; target exception: " + targetException, e.getTargetException());
            Log.logException(e.getTargetException());
        } catch (final OutOfMemoryError e) {
            Log.logSevere("BUSYTHREAD", "OutOfMemory Error in serverInstantThread.freemem, thread '" + getName() + "': " + e.getMessage());
            Log.logException(e);
        }
    }

    public static BusyThread oneTimeJob(final Object env, final String jobExec, final Log log, final long startupDelay) {
        // start the job and execute it once as background process
        final BusyThread thread = new InstantBusyThread(
                env, jobExec, null, null, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE);
        thread.setStartupSleep(startupDelay);
        thread.setIdleSleep(-1);
        thread.setBusySleep(-1);
        thread.setMemPreReqisite(0);
        thread.start();
        return thread;
    }

    public static WorkflowThread oneTimeJob(final Runnable thread, final long startupDelay) {
        final Log log = new Log(thread.getClass().getName() + "/run");
        log.setLevel(java.util.logging.Level.INFO);
        return oneTimeJob(thread, "run", log, startupDelay);
    }

    public static WorkflowThread oneTimeJob(final Runnable thread, final long startupDelay, final int maxJobs) {
        while (instantThreadCounter >= maxJobs) try {Thread.sleep(100);} catch (final InterruptedException e) {break;}
        return oneTimeJob( thread, startupDelay);
    }

    @Override
    public void open() {
        // Not implemented in this thread
    }

    @Override
    public synchronized void close() {
     // Not implemented in this thread
    }

}
