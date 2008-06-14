// serverProcessor.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.02.2008 on http://yacy.net
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import de.anomic.server.logging.serverLog;

public class serverProcessor<J extends serverProcessorJob> {

    public static final int availableCPU = Runtime.getRuntime().availableProcessors();
    public static int       useCPU = availableCPU;

    private ExecutorService executor;
    private BlockingQueue<J> input;
    private serverProcessor<J> output;
    private int poolsize;
    private Object environment;
    private String methodName;
    
    public serverProcessor(Object env, String jobExec, int inputQueueSize, serverProcessor<J> output) {
        this(env, jobExec, inputQueueSize, output, useCPU + 1);
    }

    public serverProcessor(Object env, String jobExec, int inputQueueSize, serverProcessor<J> output, int poolsize) {
        // start a fixed number of executors that handle entries in the process queue
        this.environment = env;
        this.methodName = jobExec;
        this.input = new LinkedBlockingQueue<J>(inputQueueSize);
        this.output = output;
        this.poolsize = poolsize;
        executor = Executors.newCachedThreadPool(new NamePrefixThreadFactory(jobExec));
        for (int i = 0; i < poolsize; i++) {
            executor.submit(new serverInstantBlockingThread<J>(env, jobExec, input, output));
        }
    }
    
    public int queueSize() {
        return input.size();
    }
    
    @SuppressWarnings("unchecked")
    public void enQueue(J in) throws InterruptedException {
        // ensure that enough job executors are running
        if ((this.input == null) || (executor == null) || (executor.isShutdown()) || (executor.isTerminated())) {
            // execute serialized without extra thread
            serverLog.logWarning("PROCESSOR", "executing job " + environment.getClass().getName() + "." + methodName + " serialized");
            try {
                J out = (J) serverInstantBlockingThread.execMethod(this.environment, this.methodName).invoke(environment, new Object[]{in});
                if ((out != null) && (output != null)) output.enQueue(out);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            return;
        }
        // execute concurrent in thread
        this.input.put(in);
    }
    
    @SuppressWarnings("unchecked")
    public void shutdown(long millisTimeout) {
        if (executor == null) return;
        if (executor.isShutdown()) return;
        // put poison pills into the queue
        for (int i = 0; i < poolsize; i++) {
            try {
                input.put((J) serverProcessorJob.poisonPill); // put a poison pill into the queue which will kill the job
            } catch (InterruptedException e) { }
        }
        // wait for shutdown
        try {
            executor.awaitTermination(millisTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {}
        executor.shutdown();
        this.executor = null;
        this.input = null;
    }
    
}
