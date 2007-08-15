// plasmaCrawlerFactory.java 
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

import java.lang.reflect.Constructor;

import org.apache.commons.pool.KeyedPoolableObjectFactory;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.logging.serverLog;

public final class plasmaCrawlerFactory implements KeyedPoolableObjectFactory {

    private plasmaCrawlerPool thePool;
    private final ThreadGroup theThreadGroup;
    private final serverLog       theLog;
    private final plasmaSwitchboard sb;

    public plasmaCrawlerFactory(           
            ThreadGroup threadGroup,
            plasmaSwitchboard theSb,
            serverLog log
    ) {

        super();  

        if (threadGroup == null)
            throw new IllegalArgumentException("The threadgroup object must not be null.");

        this.theThreadGroup = threadGroup;
        this.sb = theSb;  
        this.theLog = log;
    }

    public void setPool(plasmaCrawlerPool pool) {
        this.thePool = pool;    
    }

    public Object makeObject(Object key) throws Exception { 
        return makeObject(key, true);
    }
    
    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
     */
    public Object makeObject(Object key, boolean usePool) throws Exception {        
        if (!(key instanceof String))
            throw new IllegalArgumentException("The object key must be of type string.");        
        
        // getting the class name
        String className = this.getClass().getPackage().getName() + "." + key + ".CrawlWorker";
        
        // loading class by name
        Class moduleClass = Class.forName(className);

        // getting the constructor
        Constructor classConstructor = moduleClass.getConstructor( new Class[] { 
                ThreadGroup.class,
                plasmaCrawlerPool.class,
                plasmaSwitchboard.class,
                serverLog.class
        } );

        // instantiating class
        plasmaCrawlWorker theCrawlWorker = (plasmaCrawlWorker) classConstructor.newInstance(new Object[] {
              this.theThreadGroup,
              (usePool)?this.thePool:null,
              this.sb,
              this.theLog
        });           
        
        // return the newly created object
        return theCrawlWorker;
        
//        return new plasmaCrawlWorker(
//                this.theThreadGroup,
//                this.thePool,
//                this.sb,
//                this.cacheManager,
//                this.theLog);
    }

     /**
     * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
     */
    public void destroyObject(Object key, Object obj) {
        if (obj == null) return;
        if (obj instanceof plasmaCrawlWorker) {
            plasmaCrawlWorker theWorker = (plasmaCrawlWorker) obj;
            synchronized(theWorker) {
                theWorker.setDestroyed(true);
                theWorker.setNameTrailer("_destroyed");
                theWorker.setStopped(true);
                ((Thread)theWorker).interrupt();
            }
        }
    }

    /**
     * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
     */
    public boolean validateObject(Object key, Object obj) {
        return true;
    }

    /**
     * @param obj 
     * 
     */
    public void activateObject(Object key, Object obj)  {
        //log.debug(" activateObject...");
    }

    /**
     * @param obj 
     * 
     */
    
    public void passivateObject(Object key, Object obj) { 
        //log.debug(" passivateObject..." + obj);
        /*
        if (obj instanceof plasmaCrawlWorker)  {
            plasmaCrawlWorker theWorker = (plasmaCrawlWorker) obj;             
        }
     */
    }
    
}
