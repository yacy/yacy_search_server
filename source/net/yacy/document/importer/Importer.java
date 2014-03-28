/**
 *  Importer
 *  Copyright 2009 by Michael Peter Christen
 *  First released 29.04.2010 at http://yacy.net
 *  
 *  This is a part of YaCy, a peer-to-peer based web search engine
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.importer;

public interface Importer extends Runnable {

    
    public String source();
    
    public int count();
    
    /**
     * return the number of articles per second
     * @return
     */
    public int speed();
    
    /**
     * return the time this import is already running
     * @return
     */
    public long runningTime();
    
    
    /**
     * return the remaining seconds for the completion of all records in milliseconds
     * @return
     */
    public long remainingTime();

    public String status();
    
    public boolean isAlive();
    
    public void start();
    
    /**
     * the run method from runnable
     */
    @Override
    public void run();
    
}
