// OneTimeBusyThread.java
// ---------------------------
// Copyright 2017 by luccioman; https://github.com/luccioman
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

/**
 * A busy thread to run a job only once
 */
public abstract class OneTimeBusyThread extends InstantBusyThread {

	/**
	 * Construct an instance able to run a job once, after a given delay
	 * @param jobName the job name used to monitor the thread
	 * @param startupDelay the delay in milliseconds to wait before starting the job
	 */
	public OneTimeBusyThread(final String jobName, final long startupDelay) {
		super(jobName, Long.MIN_VALUE, Long.MIN_VALUE);
		this.setStartupSleep(startupDelay);
		this.setIdleSleep(-1);
		this.setBusySleep(-1);
		this.setMemPreReqisite(0);
		this.setLoadPreReqisite(
				Double.MAX_VALUE); /*
									 * this is called during initialization phase and some code parts depend on it;
									 * therefore we cannot set a prerequisite that prevents the start of that thread
									 */
	}

	/**
	 * Construct an instance able to run a job once and immediately
	 * @param jobName the job name used to monitor the thread
	 */
	public OneTimeBusyThread(final String jobName) {
		this(jobName, 0);
	}
	
	/**
	 * Construct and start a OneTimeBusyThread instance from a runnable task.
	 * @param task the task to run once
	 * @param startupDelay the delay in milliseconds to wait before starting the job
	 * @return a OneTimeBusyThread instance
	 * @throws IllegalArgumentException when the task is null
	 */
    public static OneTimeBusyThread startFromRunnable(final Runnable task, final long startupDelay) {
    	if(task == null) {
    		throw new IllegalArgumentException("Runnable task must not be null");
    	}
    	OneTimeBusyThread busyThread = new OneTimeBusyThread(task.getClass().getName() + ".run", startupDelay) {
			
			@Override
			public boolean jobImpl() throws Exception {
				task.run();
				return true;
			}
			
		};
		busyThread.start();
		return busyThread;
    }

}
