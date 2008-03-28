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

public class serverInstantBlockingThread<I extends serverProcessorJob, O extends serverProcessorJob> extends serverAbstractBlockingThread<I, O> implements serverBlockingThread<I, O> {

    private Method jobExecMethod;
    private Object environment;
    private Long   handle;
    private static int handleCounter = 0;
    public static int instantThreadCounter = 0;
    public static ConcurrentHashMap<Long, String> jobs = new ConcurrentHashMap<Long, String>();
    
    public serverInstantBlockingThread(Object env, String jobExec, BlockingQueue<I> input, BlockingQueue<O> output) {
        // jobExec is the name of a method of the object 'env' that executes the one-step-run
        // jobCount is the name of a method that returns the size of the job
        
        // set the blocking queues for input and output
        this.setInputQueue(input);
        this.setOutputQueue(output);
        
        // define execution class
        Class<?> theClass = (env instanceof Class) ? (Class<?>) env : env.getClass();
        try {
            this.jobExecMethod = null;
            Method[] methods = theClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if ((methods[i].getParameterTypes().length == 1) && (methods[i].getName().equals(jobExec))) {
                    this.jobExecMethod = methods[i];
                    break;
                }
            }
            if (this.jobExecMethod == null) throw new NoSuchMethodException(jobExec + " does not exist in " + env.getClass().getName());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("serverInstantThread, wrong declaration of jobExec: " + e.getMessage());
        }
        
        this.environment = (env instanceof Class) ? null : env;
        this.setName(theClass.getName() + "." + jobExec + "." + handleCounter++);
        this.handle = new Long(System.currentTimeMillis() + this.getName().hashCode());
    }
    
    public int getJobCount() {
        return this.getInputQueue().size();
    }
        
    @SuppressWarnings("unchecked")
    public O job(I next) throws Exception {
        if (next == null) return null; // poison pill: shutdown
        instantThreadCounter++;
        //System.out.println("started job " + this.handle + ": " + this.getName());
        jobs.put(this.handle, this.getName());
        O out = null;
        try {
            out = (O) jobExecMethod.invoke(environment, new Object[]{next});
        } catch (IllegalAccessException e) {
            serverLog.logSevere("BLOCKINGTHREAD", "Internal Error in serverInstantThread.job: " + e.getMessage());
            serverLog.logSevere("BLOCKINGTHREAD", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (IllegalArgumentException e) {
            serverLog.logSevere("BLOCKINGTHREAD", "Internal Error in serverInstantThread.job: " + e.getMessage());
            serverLog.logSevere("BLOCKINGTHREAD", "shutting down thread '" + this.getName() + "'");
            this.terminate(false);
        } catch (InvocationTargetException e) {
            String targetException = e.getTargetException().getMessage();
            e.getTargetException().printStackTrace();
            e.printStackTrace();
            if ((targetException != null) && ((targetException.indexOf("heap space") > 0) || (targetException.indexOf("NullPointerException") > 0))) e.getTargetException().printStackTrace();
            serverLog.logSevere("BLOCKINGTHREAD", "Runtime Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage() + "; target exception: " + targetException, e.getTargetException());
            e.getTargetException().printStackTrace();
        } catch (OutOfMemoryError e) {
            serverLog.logSevere("BLOCKINGTHREAD", "OutOfMemory Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage());
            e.printStackTrace();
        }
        instantThreadCounter--;
        jobs.remove(this.handle);
        return out;
    }
    
}