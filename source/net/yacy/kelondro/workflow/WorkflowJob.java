// WorkflowJob.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 29.02.2008 on http://yacy.net
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

public class WorkflowJob {

    public final static int STATUS_INITIATED =  0;
    public final static int STATUS_STARTED   =  1;
    public final static int STATUS_RUNNING   =  2;
    public final static int STATUS_FINISHED  =  3;
    public final static int STATUS_POISON    = 99;
    
    public int status = STATUS_INITIATED;
    
    public WorkflowJob() {
        this.status = STATUS_INITIATED;
    }
    
    private WorkflowJob(final int status) {
        this.status = status;
    }
    

    public static final WorkflowJob poisonPill = new WorkflowJob(WorkflowJob.STATUS_POISON); // kills job queue executors
    
}
