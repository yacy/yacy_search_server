//serverSemaphore.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.server;

public final class serverSemaphore  {
    private long currentValue = 0;    
    private long maximumValue = Long.MAX_VALUE;

    public serverSemaphore(long initialValue)  {
        this(initialValue,Long.MAX_VALUE);
    }    

    protected serverSemaphore(long initialValue, long maxValue) {
        /* some errorhandling */
        if (maxValue < initialValue) {
            throw new IllegalArgumentException("The semaphore maximum value must not be " +
                                               "greater than the semaphore init value.");
        }
        
        if (maxValue < 1)  {
             throw new IllegalArgumentException("The semaphore maximum value must be greater or equal 1.");          
        }
        
        if (initialValue < 0) {
             throw new IllegalArgumentException("The semaphore initial value must be greater or equal 0.");          
        }
        
        
        // setting the initial Sempahore Values
        this.currentValue = initialValue;
        this.maximumValue = maxValue;        
    }
    
    public synchronized void P() throws InterruptedException
    {   
         this.currentValue-- ;           
        
         if (this.currentValue < 0) {    
             try  { 
                 wait();
             } catch(InterruptedException e) { 
                 this.currentValue++;
                 throw e;
             }
         }
    }

    public synchronized void V() {
        if (this.currentValue+1 == this.maximumValue) {
              throw new IndexOutOfBoundsException("The maximum value of the semaphore was reached");
        }        
        
        this.currentValue++;        
         
        if (this.currentValue <= 0) {
            notify();
        }   
     }
 }