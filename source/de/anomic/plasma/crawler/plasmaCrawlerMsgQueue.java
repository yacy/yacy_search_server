// plasmaCrawlerMsgQueue.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import de.anomic.plasma.plasmaCrawlLoaderMessage;
import de.anomic.server.serverSemaphore;

public class plasmaCrawlerMsgQueue {
    private final serverSemaphore readSync;
    private final serverSemaphore writeSync;
    private final ArrayList messageList;
    
    public plasmaCrawlerMsgQueue()  {
        this.readSync  = new serverSemaphore (0);
        this.writeSync = new serverSemaphore (1);
        
        this.messageList = new ArrayList(10);        
    }

    /**
     * 
     * @param newMessage
     * @throws MessageQueueLockedException
     * @throws InterruptedException
     */
    public void addMessage(plasmaCrawlLoaderMessage newMessage) 
        throws InterruptedException, NullPointerException 
    {
        if (newMessage == null) throw new NullPointerException();

        this.writeSync.P();

            boolean insertionDoneSuccessfully = false;
            synchronized(this.messageList) {
                insertionDoneSuccessfully = this.messageList.add(newMessage);
            }

            if (insertionDoneSuccessfully)  {
                this.sortMessages();
                this.readSync.V();              
            }
        
        this.writeSync.V();
    }

    public plasmaCrawlLoaderMessage waitForMessage() throws InterruptedException {
        this.readSync.P();         
        this.writeSync.P();

        plasmaCrawlLoaderMessage newMessage = null;
        synchronized(this.messageList) {               
            newMessage = (plasmaCrawlLoaderMessage) this.messageList.remove(0);
        }

        this.writeSync.V();
        return newMessage;
    }

    protected void sortMessages() {
        Collections.sort(this.messageList, new Comparator()  { 
            public int compare(Object o1, Object o2)
            {
                plasmaCrawlLoaderMessage message1 = (plasmaCrawlLoaderMessage) o1; 
                plasmaCrawlLoaderMessage message2 = (plasmaCrawlLoaderMessage) o2; 
                
                int message1Priority = message1.crawlingPriority;
                int message2Priority = message2.crawlingPriority;
                
                if (message1Priority > message2Priority){ 
                    return -1; 
                } else if (message1Priority < message2Priority) { 
                    return 1; 
                }  else { 
                    return 0; 
                }            
            } 
        }); 
    }
}