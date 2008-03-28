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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class serverProcessor<I extends serverProcessorJob, O extends serverProcessorJob> {

    public static final int availableCPU = Runtime.getRuntime().availableProcessors();
    public static int       useCPU = availableCPU;

    ExecutorService executor;
    BlockingQueue<I> input;
    BlockingQueue<O> output;
    int poolsize;
    
    public serverProcessor(Object env, String jobExec, BlockingQueue<I> input, BlockingQueue<O> output) {
        this(env, jobExec, input, output, useCPU + 1);
    }

    public serverProcessor(Object env, String jobExec, BlockingQueue<I> input, BlockingQueue<O> output, int poolsize) {
        // start a fixed number of executors that handle entries in the process queue
        this.input = input;
        this.output = output;
        this.poolsize = poolsize;
        executor = Executors.newCachedThreadPool();
        for (int i = 0; i < poolsize; i++) {
            executor.submit(new serverInstantBlockingThread<I, O>(env, jobExec, input, output));
        }
    }
    
    @SuppressWarnings("unchecked")
    public void shutdown(long millisTimeout) {
        if (executor == null) return;
        if (executor.isShutdown()) return;
        // put poison pills into the queue
        for (int i = 0; i < poolsize; i++) {
            try {
                input.put((I) shutdownJob);
            } catch (InterruptedException e) { }
        }
        // wait for shutdown
        try {
            executor.awaitTermination(millisTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {}
        executor.shutdown();
        executor = null;
    }
    
    public static class SpecialJob implements serverProcessorJob {
        int type = 0;
    }
    
    public static final serverProcessorJob shutdownJob = new SpecialJob();
    
}
