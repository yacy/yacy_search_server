// serverProcessor.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 29.02.2008 on http://yacy.net
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

public class serverProcessorJob {

    public final static int STATUS_INITIATED =  0;
    public final static int STATUS_STARTED   =  1;
    public final static int STATUS_RUNNING   =  2;
    public final static int STATUS_FINISHED  =  3;
    public final static int STATUS_POISON    = 99;
    
    public int status = 0;
    
    public serverProcessorJob() {
        this.status = STATUS_INITIATED;
    }
    
    public serverProcessorJob(final int status) {
        this.status = status;
    }
    

    public static final serverProcessorJob poisonPill = new serverProcessorJob(serverProcessorJob.STATUS_POISON); // kills job queue executors
    
}
