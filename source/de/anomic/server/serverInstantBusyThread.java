// serverInstantThread.java
// -----------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 14.03.2005
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

package de.anomic.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.TreeMap;

import de.anomic.server.logging.serverLog;

public final class serverInstantBusyThread extends serverAbstractBusyThread implements serverBusyThread {
    
    private Method jobExecMethod, jobCountMethod, freememExecMethod;
    private final Object environment;
    private final Long   handle;
    
    public static int instantThreadCounter = 0;
    public static final TreeMap<Long, String> jobs = new TreeMap<Long, String>();
    
    public serverInstantBusyThread(final Object env, final String jobExec, final String jobCount, final String freemem) {
        // jobExec is the name of a method of the object 'env' that executes the one-step-run
        // jobCount is the name of a method that returns the size of the job
        // freemem is the name of a method that tries to free memory and returns void
        final Class<?> theClass = (env instanceof Class) ? (Class<?>) env : env.getClass();
        try {
            this.jobExecMethod = theClass.getMethod(jobExec, new Class[0]);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of jobExec: " + e.getMessage());
        }
        try {
            if (jobCount == null)
                this.jobCountMethod = null;
            else
                this.jobCountMethod = theClass.getMethod(jobCount, new Class[0]);
            
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of jobCount: " + e.getMessage());
        }
        try {
            if (freemem == null)
                this.freememExecMethod = null;
            else
                this.freememExecMethod = theClass.getMethod(freemem, new Class[0]);
            
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of freemem: " + e.getMessage());
        }
        this.environment = (env instanceof Class) ? null : env;
        this.setName(theClass.getName() + "." + jobExec);
        this.handle = Long.valueOf(System.currentTimeMillis() + this.getName().hashCode());
    }
    
    public int getJobCount() {
        if (this.jobCountMethod == null) return Integer.MAX_VALUE;
        try {
            final Object result = jobCountMethod.invoke(environment, new Object[0]);
            if (result instanceof Integer)
                return ((Integer) result).intValue();
            else
                return -1;
        } catch (final IllegalAccessException e) {
            return -1;
        } catch (final IllegalArgumentException e) {
            return -1;
        } catch (final InvocationTargetException e) {
            serverLog.logSevere("BUSYTHREAD", "invocation serverInstantThread of thread '" + this.getName() + "': " + e.getMessage(), e);
            return -1;
        }
    }
        
    public boolean job() throws Exception {
        instantThreadCounter++;
        //System.out.println("started job " + this.handle + ": " + this.getName());
        synchronized(jobs) {jobs.put(this.handle, this.getName());}
        boolean jobHasDoneSomething = false;
        try {
            final Object result = jobExecMethod.invoke(environment, new Object[0]);
            if (result == null) jobHasDoneSomething = true;
            else if (result instanceof Boolean) jobHasDoneSomething = ((Boolean) result).booleanValue();
        } catch (final IllegalAccessException e) {
            serverLog.logSevere("BUSYTHREAD", "Internal Error in serverInstantThread.job: " + e.getMessage());
            serverLog.logSevere("BUSYTHREAD", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (final IllegalArgumentException e) {
            serverLog.logSevere("BUSYTHREAD", "Internal Error in serverInstantThread.job: " + e.getMessage());
            serverLog.logSevere("BUSYTHREAD", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (final InvocationTargetException e) {
            final String targetException = e.getTargetException().getMessage();
            e.printStackTrace();
            serverLog.logSevere("BUSYTHREAD", "Runtime Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage() + "; target exception: " + targetException, e.getTargetException());
        } catch (final OutOfMemoryError e) {
            serverLog.logSevere("BUSYTHREAD", "OutOfMemory Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage());
            e.printStackTrace();
            freemem();
        }
        instantThreadCounter--;
        synchronized(jobs) {jobs.remove(this.handle);}
        return jobHasDoneSomething;
    }
    
    public void freemem() {
        if (freememExecMethod == null) return;
        try {
            freememExecMethod.invoke(environment, new Object[0]);
        } catch (final IllegalAccessException e) {
            serverLog.logSevere("BUSYTHREAD", "Internal Error in serverInstantThread.freemem: " + e.getMessage());
            serverLog.logSevere("BUSYTHREAD", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (final IllegalArgumentException e) {
            serverLog.logSevere("BUSYTHREAD", "Internal Error in serverInstantThread.freemem: " + e.getMessage());
            serverLog.logSevere("BUSYTHREAD", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (final InvocationTargetException e) {
            final String targetException = e.getTargetException().getMessage();
            if (targetException.indexOf("heap space") > 0) e.getTargetException().printStackTrace();
            serverLog.logSevere("BUSYTHREAD", "Runtime Error in serverInstantThread.freemem, thread '" + this.getName() + "': " + e.getMessage() + "; target exception: " + targetException, e.getTargetException());
            e.getTargetException().printStackTrace();
        } catch (final OutOfMemoryError e) {
            serverLog.logSevere("BUSYTHREAD", "OutOfMemory Error in serverInstantThread.freemem, thread '" + this.getName() + "': " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static serverBusyThread oneTimeJob(final Object env, final String jobExec, final serverLog log, final long startupDelay) {
        // start the job and execute it once as background process
        final serverBusyThread thread = new serverInstantBusyThread(env, jobExec, null, null);
        thread.setStartupSleep(startupDelay);
        thread.setIdleSleep(-1);
        thread.setBusySleep(-1);
        thread.setMemPreReqisite(0);
        thread.setLog(log);
        thread.start();
        return thread;
    }
    
    public static serverThread oneTimeJob(final Runnable thread, final long startupDelay) {
        final serverLog log = new serverLog(thread.getClass().getName() + "/run");
        log.setLevel(java.util.logging.Level.INFO);
        return oneTimeJob(thread, "run", log, startupDelay);
    }
    
    public static serverThread oneTimeJob(final Runnable thread, final long startupDelay, final int maxJobs) {
        while (instantThreadCounter >= maxJobs) try {Thread.sleep(100);} catch (final InterruptedException e) {break;}
        return oneTimeJob( thread, startupDelay);
    }
    
}
