// serverInstantThread.java
// -----------------------
// (C) by Michael Peter Christen; mc@anomic.de
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

package de.anomic.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import de.anomic.server.logging.serverLog;

public final class serverInstantThread extends serverAbstractThread implements serverThread {
    
    private Method jobExecMethod, jobCountMethod, freememExecMethod;
    private Object environment;
    
    public static int instantThreadCounter = 0;
    
    public serverInstantThread(Object env, String jobExec, String jobCount, String freemem) {
        // jobExec is the name of a method of the object 'env' that executes the one-step-run
        // jobCount is the name of a method that returns the size of the job
        // freemem is the name of a method that tries to free memory and returns void
        try {
            this.jobExecMethod = env.getClass().getMethod(jobExec, new Class[0]);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of jobExec: " + e.getMessage());
        }
        try {
            if (jobCount == null)
                this.jobCountMethod = null;
            else
                this.jobCountMethod = env.getClass().getMethod(jobCount, new Class[0]);
            
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of jobCount: " + e.getMessage());
        }
        try {
            if (freemem == null)
                this.freememExecMethod = null;
            else
                this.freememExecMethod = env.getClass().getMethod(freemem, new Class[0]);
            
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of freemem: " + e.getMessage());
        }
        this.environment = env;
        this.setName(env.getClass().getName() + "." + jobExec);
    }
    
    public int getJobCount() {
        if (this.jobCountMethod == null) return Integer.MAX_VALUE;
        try {
            Object result = jobCountMethod.invoke(environment, new Object[0]);
            if (result instanceof Integer)
                return ((Integer) result).intValue();
            else
                return -1;
        } catch (IllegalAccessException e) {
            return -1;
        } catch (IllegalArgumentException e) {
            return -1;
        } catch (InvocationTargetException e) {
            serverLog.logSevere("SERVER", "invocation serverInstantThread of thread '" + this.getName() + "': " + e.getMessage(), e);
            return -1;
        }
    }
        
    public boolean job() throws Exception {
        instantThreadCounter++;
        boolean jobHasDoneSomething = false;
        try {
            Object result = jobExecMethod.invoke(environment, new Object[0]);
            if (result == null) jobHasDoneSomething = true;
            else if (result instanceof Boolean) jobHasDoneSomething = ((Boolean) result).booleanValue();
        } catch (IllegalAccessException e) {
            serverLog.logSevere("SERVER", "Internal Error in serverInstantThread.job: " + e.getMessage());
            serverLog.logSevere("SERVER", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (IllegalArgumentException e) {
            serverLog.logSevere("SERVER", "Internal Error in serverInstantThread.job: " + e.getMessage());
            serverLog.logSevere("SERVER", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (InvocationTargetException e) {
            serverLog.logSevere("SERVER", "Runtime Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage() + "; target exception: " + e.getTargetException().getMessage(), e.getTargetException());
            e.getTargetException().printStackTrace();
        } catch (OutOfMemoryError e) {
            serverLog.logSevere("SERVER", "OutOfMemory Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage());
            e.printStackTrace();
            freemem();
        }
        instantThreadCounter--;
        return jobHasDoneSomething;
    }
    
    public void freemem() {
        if (freememExecMethod == null) return;
        try {
            freememExecMethod.invoke(environment, new Object[0]);
        } catch (IllegalAccessException e) {
            serverLog.logSevere("SERVER", "Internal Error in serverInstantThread.freemem: " + e.getMessage());
            serverLog.logSevere("SERVER", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (IllegalArgumentException e) {
            serverLog.logSevere("SERVER", "Internal Error in serverInstantThread.freemem: " + e.getMessage());
            serverLog.logSevere("SERVER", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (InvocationTargetException e) {
            serverLog.logSevere("SERVER", "Runtime Error in serverInstantThread.freemem, thread '" + this.getName() + "': " + e.getMessage() + "; target exception: " + e.getTargetException().getMessage(), e.getTargetException());
            e.getTargetException().printStackTrace();
        } catch (OutOfMemoryError e) {
            serverLog.logSevere("SERVER", "OutOfMemory Error in serverInstantThread.freemem, thread '" + this.getName() + "': " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static serverThread oneTimeJob(Object env, String jobExec, serverLog log, long startupDelay) {
        // start the job and execute it once as background process
        serverThread thread = new serverInstantThread(env, jobExec, null, null);
        thread.setStartupSleep(startupDelay);
        thread.setIdleSleep(-1);
        thread.setBusySleep(-1);
        thread.setMemPreReqisite(0);
        thread.setLog(log);
        thread.start();
        return thread;
    }
    
    public static serverThread oneTimeJob(Runnable thread, long startupDelay) {
        serverLog log = new serverLog(thread.getClass().getName() + "/run");
        log.setLevel(java.util.logging.Level.INFO);
        return oneTimeJob(thread, "run", log, startupDelay);
    }
    
    public static serverThread oneTimeJob(Runnable thread, long startupDelay, int maxJobs) {
        while (instantThreadCounter >= maxJobs) try {Thread.sleep(100);} catch (InterruptedException e) {break;}
        return oneTimeJob( thread, startupDelay);
    }
    
}
