// plasmaCrawlerPool.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: theli $
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


package de.anomic.plasma.crawler;

import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import de.anomic.server.logging.serverLog;

public final class plasmaCrawlerPool extends GenericKeyedObjectPool {
    private final ThreadGroup theThreadGroup;
    public boolean isClosed = false;

    public plasmaCrawlerPool(plasmaCrawlerFactory objFactory, GenericKeyedObjectPool.Config config, ThreadGroup threadGroup) {
        super(objFactory, config);
        this.theThreadGroup = threadGroup;
        objFactory.setPool(this);
    }

    public Object borrowObject(Object key) throws Exception  {
       return super.borrowObject(key);
    }

    public void returnObject(Object key,Object obj) {
        if (obj == null) return;
        if (obj instanceof plasmaCrawlWorker) {
            try {
                ((plasmaCrawlWorker)obj).setNameTrailer("_inPool");
                super.returnObject(key,obj);
            } catch (Exception e) {
                ((plasmaCrawlWorker)obj).setStopped(true);
                serverLog.logSevere("CRAWLER-POOL","Unable to return crawler thread to pool.",e);                
            }
        } else {
            serverLog.logSevere("CRAWLER-POOL","Object of wrong type '" + obj.getClass().getName() +
            "' returned to pool.");            
        }        
    }        
    
    public void invalidateObject(Object key,Object obj) {
        if (obj == null) return;
        if (this.isClosed) return;
        if (obj instanceof plasmaCrawlWorker) {
            try {
                ((plasmaCrawlWorker)obj).setNameTrailer("_invalidated");
                ((plasmaCrawlWorker)obj).setStopped(true);
                super.invalidateObject(key,obj);
            } catch (Exception e) {
                serverLog.logSevere("CRAWLER-POOL","Unable to invalidate crawling thread.",e); 
            }
        }
    }        
    
    public synchronized void close() throws Exception {
        try {
            /*
             * shutdown all still running session threads ...
             */
            this.isClosed  = true;

            /* waiting for all threads to finish */
            int threadCount  = this.theThreadGroup.activeCount();    
            Thread[] threadList = new Thread[threadCount];     
            threadCount = this.theThreadGroup.enumerate(threadList);

            // signaling shutdown to all still running or pooled threads ...
            serverLog.logInfo("CRAWLER","Signaling shutdown to " + threadCount + " remaining crawler threads ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                ((plasmaCrawlWorker)threadList[currentThreadIdx]).setStopped(true);
            }   

            // giving the crawlers some time to finish shutdown
            try { Thread.sleep(500); } catch(Exception e) {/* Ignore this. Shutdown in progress */}            

            // sending interrupted signal to all remaining threads
            serverLog.logInfo("CRAWLER","Sending interruption signal to " + this.theThreadGroup.activeCount() + " remaining crawler threads ...");
            this.theThreadGroup.interrupt();        

            // aborting all crawlers by closing all still open httpc sockets
            serverLog.logInfo("CRAWLER","Trying to abort  " + this.theThreadGroup.activeCount() + " remaining crawler threads ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                Thread currentThread = threadList[currentThreadIdx];
                if (currentThread.isAlive()) {
                    serverLog.logInfo("CRAWLER","Trying to shutdown crawler thread '" + currentThread.getName() + "' [" + currentThreadIdx + "].");
                    ((plasmaCrawlWorker)currentThread).close();
                }
            }            

            serverLog.logInfo("CRAWLER","Waiting for " + this.theThreadGroup.activeCount() + " remaining crawler threads to finish shutdown ...");
            for ( int currentThreadIdx = 0; currentThreadIdx < threadCount; currentThreadIdx++ )  {
                Thread currentThread = threadList[currentThreadIdx];
                if (currentThread.isAlive()) {
                    serverLog.logInfo("CRAWLER","Waiting for crawler thread '" + currentThread.getName() + "' [" + currentThreadIdx + "] to finish shutdown.");
                    try { currentThread.join(500); } catch (InterruptedException ex) {/* Ignore this. Shutdown in progress */}
                }
            }
            serverLog.logWarning("CRAWLER","Shutdown of remaining crawler threads finish.");
        }
        catch (Exception e) {
            serverLog.logWarning("CRAWLER","Unexpected error while trying to shutdown all remaining crawler threads.",e);  
        }        

        super.close();        

    }

}
