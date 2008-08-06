// serverInstantBlockingThread.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.03.2008 on http://yacy.net
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import de.anomic.server.logging.serverLog;

public class serverInstantBlockingThread<J extends serverProcessorJob> extends serverAbstractBlockingThread<J> implements serverBlockingThread<J> {

    private final Method jobExecMethod;
    private final Object environment;
    private final Long   handle;
    private static int handleCounter = 0;
    public static int instantThreadCounter = 0;
    public static final ConcurrentHashMap<Long, String> jobs = new ConcurrentHashMap<Long, String>();
    
    public serverInstantBlockingThread(final Object env, final String jobExec, final BlockingQueue<J> input, final serverProcessor<J> output) {
        // jobExec is the name of a method of the object 'env' that executes the one-step-run
        // jobCount is the name of a method that returns the size of the job
        
        // set the blocking queues for input and output
        this.setInputQueue(input);
        this.setOutputProcess(output);
        
        // define execution class
        this.jobExecMethod = execMethod(env, jobExec);
        this.environment = (env instanceof Class) ? null : env;
        this.setName(jobExecMethod.getClass().getName() + "." + jobExecMethod.getName() + "." + handleCounter++);
        this.handle = Long.valueOf(System.currentTimeMillis() + this.getName().hashCode());
    }
    
    protected static Method execMethod(final Object env, final String jobExec) {
        final Class<?> theClass = (env instanceof Class) ? (Class<?>) env : env.getClass();
        try {
            final Method[] methods = theClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if ((methods[i].getParameterTypes().length == 1) && (methods[i].getName().equals(jobExec))) {
                    return methods[i];
                }
            }
            throw new NoSuchMethodException(jobExec + " does not exist in " + env.getClass().getName());
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of jobExec: " + e.getMessage());
        }
    }
    
    public int getJobCount() {
        return this.getInputQueue().size();
    }
        
    @SuppressWarnings("unchecked")
    public J job(final J next) throws Exception {
        if (next == null) return null; // poison pill: shutdown
        instantThreadCounter++;
        //System.out.println("started job " + this.handle + ": " + this.getName());
        jobs.put(this.handle, this.getName());
        J out = null;
        try {
            out = (J) jobExecMethod.invoke(environment, new Object[]{next});
        } catch (final IllegalAccessException e) {
            serverLog.logSevere("BLOCKINGTHREAD", "Internal Error in serverInstantThread.job: " + e.getMessage());
            serverLog.logSevere("BLOCKINGTHREAD", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (final IllegalArgumentException e) {
            serverLog.logSevere("BLOCKINGTHREAD", "Internal Error in serverInstantThread.job: " + e.getMessage());
            serverLog.logSevere("BLOCKINGTHREAD", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (final InvocationTargetException e) {
            final String targetException = e.getTargetException().getMessage();
            e.getTargetException().printStackTrace();
            e.printStackTrace();
            if ((targetException != null) && ((targetException.indexOf("heap space") > 0) || (targetException.indexOf("NullPointerException") > 0))) e.getTargetException().printStackTrace();
            serverLog.logSevere("BLOCKINGTHREAD", "Runtime Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage() + "; target exception: " + targetException, e.getTargetException());
            e.getTargetException().printStackTrace();
        } catch (final OutOfMemoryError e) {
            serverLog.logSevere("BLOCKINGTHREAD", "OutOfMemory Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage());
            e.printStackTrace();
        }
        instantThreadCounter--;
        jobs.remove(this.handle);
        return out;
    }
    
}