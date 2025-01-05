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

import java.util.TreeMap;

import net.yacy.cora.util.ConcurrentLog;


public abstract class InstantBusyThread extends AbstractBusyThread implements BusyThread {

    private final Long   handle;

    private static final TreeMap<Long, String> jobs = new TreeMap<Long, String>();

	/**
	 * @param idleSleep defines min idle sleep time that can be set via setIdleSleep()
	 * @param busySleep defines min busy sleep time that can be set via setBusySleep()
	 */
	public InstantBusyThread(final long idleSleep, final long busySleep) {
		this("InstantBusyThread.job", idleSleep, busySleep);
    }
	
	/**
	 * @param jobName the job name used to monitor the thread
	 * @param idleSleep defines min idle sleep time that can be set via setIdleSleep()
	 * @param busySleep defines min busy sleep time that can be set via setBusySleep()
	 */
	public InstantBusyThread(final String jobName, final long idleSleep, final long busySleep) {
        super(idleSleep, busySleep);
        setName("BusyThread " + jobName);
        this.handle = Long.valueOf(System.currentTimeMillis() + getName().hashCode());
    }
	

    @Override
    public boolean job() throws Exception {
        //System.out.println("started job " + this.handle + ": " + this.getName());
        synchronized(jobs) {jobs.put(this.handle, getName());}
        boolean jobHasDoneSomething = false;
        try {
            jobHasDoneSomething = jobImpl();
        } catch (final IllegalArgumentException e) {
            ConcurrentLog.severe("BUSYTHREAD", "Internal Error in InstantBusyThread.job: " + e.getMessage());
            ConcurrentLog.severe("BUSYTHREAD", "shutting down thread '" + getName() + "'");
            terminate(false);
        } catch (final OutOfMemoryError e) {
            ConcurrentLog.severe("BUSYTHREAD", "OutOfMemory Error in InstantBusyThread.job, thread '" + getName() + "': " + e.getMessage());
            ConcurrentLog.logException(e);
            freemem();
        } catch (final Exception e) {
            ConcurrentLog.severe("BUSYTHREAD", "Generic Exception, thread '" + getName() + "': " + e.getMessage());
            ConcurrentLog.logException(e);
        }
        synchronized(jobs) {jobs.remove(this.handle);}
        return jobHasDoneSomething;
    }
    
    /**
     * The job's main logic implementation
     * @return true if it has done something, false if it is idle and does not expect to work on more for a longer time
     * @throws Exception when an unexpected error occurred
     */
    public abstract boolean jobImpl() throws Exception;

    @Override
    public void freemem() {
        try {
        	freememImpl();
        } catch (final OutOfMemoryError e) {
            ConcurrentLog.severe("BUSYTHREAD", "OutOfMemory Error in InstantBusyThread.freemem, thread '" + getName() + "': " + e.getMessage());
            ConcurrentLog.logException(e);
        }
    }
    
	@Override
	public int getJobCount() {
		return Integer.MAX_VALUE;
	}
	
    /**
     * Called when an outOfMemoryCycle is performed.
     */
	public void freememImpl() {
		// Do nothing in this implementation, please override
	}

    
    @Override
    public void open() {
        // Not implemented in this thread
    }

    @Override
    public synchronized void close() {
     // Not implemented in this thread
    }
    
	/**
	 * Construct an InstantBusyThread instance from a runnable task.
	 * 
	 * @param task
	 *            the task to run as a job
	 * @param idleSleep
	 *            defines min idle sleep time that can be set via setIdleSleep()
	 * @param busySleep
	 *            defines min busy sleep time that can be set via setBusySleep()
	 * @return a InstantBusyThread instance
	 * @throws IllegalArgumentException
	 *             when the task is null
	 */
	public static InstantBusyThread createFromRunnable(final Runnable task, final long idleSleep,
			final long busySleep) {
		if (task == null) {
			throw new IllegalArgumentException("Runnable task must not be null");
		}
		InstantBusyThread busyThread = new InstantBusyThread(task.getClass().getName() + ".run", idleSleep, busySleep) {

			@Override
			public boolean jobImpl() throws Exception {
				task.run();
				return true;
			}

		};
		return busyThread;
	}

}
