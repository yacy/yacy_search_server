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


import net.yacy.kelondro.logging.Log;


public class InstantBlockingThread<J extends WorkflowJob> extends AbstractBlockingThread<J> implements BlockingThread<J> {
    private static final String BLOCKINGTHREAD = "BLOCKINGTHREAD";

    private final Method jobExecMethod;
    private final Object environment;
    private final Long   handle;
    private static int handleCounter = 0;
    public static int instantThreadCounter = 0;
    public static final ConcurrentMap<Long, String> jobs = new ConcurrentHashMap<Long, String>();
    
    public InstantBlockingThread(final Object env, final String jobExec, final WorkflowProcessor<J> manager) {
        // jobExec is the name of a method of the object 'env' that executes the one-step-run
        // jobCount is the name of a method that returns the size of the job
        
        // set the manager of blocking queues for input and output
        this.setManager(manager);
        
        // define execution class
        this.jobExecMethod = execMethod(env, jobExec);
        this.environment = (env instanceof Class<?>) ? null : env;
        this.setName(jobExecMethod.getClass().getName() + "." + jobExecMethod.getName() + "." + handleCounter++);
        this.handle = Long.valueOf(System.currentTimeMillis() + this.getName().hashCode());
    }
    
    public InstantBlockingThread(final Object env, final Method jobExecMethod, final WorkflowProcessor<J> manager) {
        // jobExec is the name of a method of the object 'env' that executes the one-step-run
        // jobCount is the name of a method that returns the size of the job
        
        // set the manager of blocking queues for input and output
        this.setManager(manager);
        
        // define execution class
        this.jobExecMethod = jobExecMethod;
        this.environment = (env instanceof Class<?>) ? null : env;
        this.setName(jobExecMethod.getClass().getName() + "." + jobExecMethod.getName() + "." + handleCounter++);
        this.handle = Long.valueOf(System.currentTimeMillis() + this.getName().hashCode());
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
    
    public int getJobCount() {
        return this.getManager().queueSize();
    }
        
    @SuppressWarnings("unchecked")
    public J job(final J next) throws Exception {
        J out = null;

        // see if we got a poison pill to tell us to shut down
        if (next == null) {
            out = (J) WorkflowJob.poisonPill;
        } else if (next == WorkflowJob.poisonPill || next.status == WorkflowJob.STATUS_POISON) {
            out = next;
        } else {
            long t = System.currentTimeMillis();

            instantThreadCounter++;
            //System.out.println("started job " + this.handle + ": " + this.getName());
            jobs.put(this.handle, this.getName());

            try {
                out = (J) jobExecMethod.invoke(environment, new Object[]{next});
            } catch (final IllegalAccessException e) {
                Log.logSevere(BLOCKINGTHREAD, "Internal Error in serverInstantThread.job: " + e.getMessage());
                Log.logSevere(BLOCKINGTHREAD, "shutting down thread '" + this.getName() + "'");
                this.terminate(false);
            } catch (final IllegalArgumentException e) {
                Log.logSevere(BLOCKINGTHREAD, "Internal Error in serverInstantThread.job: " + e.getMessage());
                Log.logSevere(BLOCKINGTHREAD, "shutting down thread '" + this.getName() + "'");
                this.terminate(false);
            } catch (final InvocationTargetException e) {
                final String targetException = e.getTargetException().getMessage();
                Log.logException(e.getTargetException());
                Log.logException(e);
                if ((targetException != null) &&
                        ((targetException.indexOf("heap space") > 0) ||
                        (targetException.indexOf("NullPointerException") > 0))) {
                    Log.logException(e.getTargetException());
                }
                Log.logSevere(BLOCKINGTHREAD, "Runtime Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage() + "; target exception: " + targetException, e.getTargetException());
            } catch (final OutOfMemoryError e) {
                Log.logSevere(BLOCKINGTHREAD, "OutOfMemory Error in serverInstantThread.job, thread '" + this.getName() + "': " + e.getMessage());
                Log.logException(e);
            }
            instantThreadCounter--;
            jobs.remove(this.handle);
            this.getManager().increaseJobTime(System.currentTimeMillis() - t);
        }
        return out;
    }
    
}