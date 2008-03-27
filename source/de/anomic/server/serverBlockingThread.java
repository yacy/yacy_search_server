// serverBlockingThread.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 27.03.2008 on http://yacy.net
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

public interface serverBlockingThread<I, O> extends serverThread {

    public void setInputQueue(BlockingQueue<I> queue);
    public void setOutputQueue(BlockingQueue<O> queue);
    public BlockingQueue<I> getInputQueue();
    public BlockingQueue<O> getOutputQueue();

    public O job(I next) throws Exception;
    // performes one job procedure; this loopes until terminate() is called
    // job returns true if it has done something
    // it returns false if it is idle and does not expect to work on more for a longer time

}
