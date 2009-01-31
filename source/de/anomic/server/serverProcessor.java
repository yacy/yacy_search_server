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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import de.anomic.kelondro.util.Log;
import de.anomic.kelondro.util.NamePrefixThreadFactory;

public class serverProcessor<J extends serverProcessorJob> {

    public static final int availableCPU = Runtime.getRuntime().availableProcessors();
    public static int       useCPU = availableCPU;
    private static final ArrayList<serverProcessor<?>> processMonitor = new ArrayList<serverProcessor<?>>();

    private ExecutorService executor;
    private BlockingQueue<J> input;
    private serverProcessor<J> output;
    private int poolsize;
    private Object environment;
    private String processName, methodName, description;
    private String[] childs;
    private long blockTime, execTime, passOnTime; 
    private long execCount;
    
    public serverProcessor(
            String name, String description, String[] childnames,
            final Object env, final String jobExecMethod, final int inputQueueSize, final serverProcessor<J> output, final int poolsize) {
        // start a fixed number of executors that handle entries in the process queue
        this.environment = env;
        this.processName = name;
        this.description = description;
        this.methodName = jobExecMethod;
        this.childs = childnames;
        this.input = new LinkedBlockingQueue<J>(inputQueueSize);
        this.output = output;
        this.poolsize = poolsize;
        this.executor = Executors.newCachedThreadPool(new NamePrefixThreadFactory(jobExecMethod));
        for (int i = 0; i < poolsize; i++) {
            this.executor.submit(new serverInstantBlockingThread<J>(env, jobExecMethod, this));
        }
        // init statistics
        blockTime = 0;
        execTime = 0;
        passOnTime = 0;
        execCount = 0;
        
        // store this object for easy monitoring
        processMonitor.add(this);
    }
    
    public int queueSize() {
        return this.input.size();
    }
    
    public int queueSizeMax() {
        return this.input.size() + this.input.remainingCapacity();
    }
    
    public int concurrency() {
        return this.poolsize;
    }
    
    public J take() throws InterruptedException {
        // read from the input queue
        if (this.input == null) return null;
        long t = System.currentTimeMillis();
        J j = this.input.take();
        this.blockTime += System.currentTimeMillis() - t;
        return j;
    }
    
    public void passOn(J next) throws InterruptedException {
        // don't mix this method up with enQueue()!
        // this method enqueues into the _next_ queue, not this queue!
        if (this.output == null) return;
        long t = System.currentTimeMillis();
        this.output.enQueue(next);
        this.passOnTime += System.currentTimeMillis() - t;
    }
    
    public void clear() {
        if (this.input != null) this.input.clear();
    }
    
    public synchronized void relaxCapacity() {
        if (this.input.size() == 0) return;
        if (this.input.remainingCapacity() > 1000) return;
        BlockingQueue<J> i = new LinkedBlockingQueue<J>();
        J e;
        while (this.input.size() > 0) {
            e = this.input.poll();
            if (e == null) break;
            i.add(e);
        }
        this.input = i;
    }
    
    @SuppressWarnings("unchecked")
    public void enQueue(final J in) throws InterruptedException {
        // ensure that enough job executors are running
        if ((this.input == null) || (executor == null) || (executor.isShutdown()) || (executor.isTerminated())) {
            // execute serialized without extra thread
            Log.logWarning("PROCESSOR", "executing job " + environment.getClass().getName() + "." + methodName + " serialized");
            try {
                final J out = (J) serverInstantBlockingThread.execMethod(this.environment, this.methodName).invoke(environment, new Object[]{in});
                if ((out != null) && (output != null)) output.enQueue(out);
            } catch (final IllegalArgumentException e) {
                e.printStackTrace();
            } catch (final IllegalAccessException e) {
                e.printStackTrace();
            } catch (final InvocationTargetException e) {
                e.printStackTrace();
            }
            return;
        }
        // execute concurrent in thread
        this.input.put(in);
    }
    
    @SuppressWarnings("unchecked")
    public void announceShutdown() {
        if (executor == null) return;
        if (executor.isShutdown()) return;
        // before we put pills into the queue, make sure that they will take them
        relaxCapacity();
        // put poison pills into the queue
        for (int i = 0; i < poolsize; i++) {
            try {
                Log.logInfo("serverProcessor", "putting poison pill in queue " + this.processName + ", thread " + i);
                input.put((J) serverProcessorJob.poisonPill); // put a poison pill into the queue which will kill the job
                Log.logInfo("serverProcessor", ".. poison pill is in queue " + this.processName + ", thread " + i + ". awaiting termination");
            } catch (final InterruptedException e) { }
        }
    }
    
    public void awaitShutdown(final long millisTimeout) {
        if (executor != null & !executor.isShutdown()) {
            // wait for shutdown
            try {
                executor.shutdown();
                executor.awaitTermination(millisTimeout, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {}
        }
        Log.logInfo("serverProcessor", "queue " + this.processName + ": shutdown.");
        this.executor = null;
        this.input = null;
        // remove entry from monitor
        Iterator<serverProcessor<?>> i = processes();
        serverProcessor<?> p;
        while (i.hasNext()) {
            p = i.next();
            if (p == this) {
                i.remove();
                break;
            }
        }
    }
    
    public static Iterator<serverProcessor<?>> processes() {
        return processMonitor.iterator();
    }
    
    protected void increaseJobTime(long time) {
        this.execTime += time;
        this.execCount++;
    }
    
    public String getName() {
        return this.processName;
    }
    
    public String getDescription() {
        return this.description;
    }
    
    public String getChilds() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < this.childs.length; i++) {
            s.append(this.childs[i]);
            s.append(' ');
        }
        return s.toString();
    }
    
    /**
     * the block time is the time that a take() blocks until it gets a value
     * @return
     */
    public long getBlockTime() {
        return blockTime;
    }
    
    /**
     * the exec time is the complete time of the execution and processing of the value from take()
     * @return
     */
    public long getExecTime() {
        return execTime;
    }
    public long getExecCount() {
        return execCount;
    }
    
    /**
     * the passOn time is the time that a put() takes to enqueue a result value to the next queue
     * in case that the target queue is limited and may be full, this value may increase
     * @return
     */
    public long getPassOnTime() {
        return passOnTime;
    }
    
}
