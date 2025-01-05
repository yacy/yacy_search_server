// loaderCore.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
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

package net.yacy.utils;

import java.util.Properties;

public abstract class loaderCore implements loaderProcess {
    
    // status constants
    public static final int STATUS_IDLE      = -1; // not yet initialized
    public static final int STATUS_READY     = 0;  // initialized, but not yet started
    public static final int STATUS_RUNNING   = 1;  // started and running
    public static final int STATUS_ABORTED   = 2;  // terminated before completion
    public static final int STATUS_FAILED    = 3;  // failed before completion
    public static final int STATUS_COMPLETED = 4;  // completed; may run again
    public static final int STATUS_FINALIZED = 9;  // completed; may not run again

    // class variables
    protected Exception error = null;
    protected int status = STATUS_IDLE;
    protected Properties result = new Properties();
    protected boolean run = true;
    protected int completion = 0;
    
    // steering methods
    @Override
    public abstract void feed(byte[] a); // returns true if process was successful; should be always synchronized

    @Override
    public void terminate() {
        // if terminated before completion, completed() shows x < 100
        run = false;
    }
    
    // feed-back methods
    @Override
    public Properties result() {
        return result;
    }

    @Override
    public int completed() {
        // guess of completion status. shall be 100 if totally completed.
        return completion;
    }

    // error control
    @Override
    public int status() {
        // -1=idle, 0=ready, 1=running, 2=aborted, 3=failed, 4=completed, 9=finalized
        return status;
    }

    @Override
    public boolean available() {
        // true if it is ok to feed with feed()
        return (status() == STATUS_READY) ||
               (status() == STATUS_COMPLETED && (result == null || result.isEmpty()));
    }
    
    @Override
    public Exception error() {
        // if in error status: this returnes exception
        return error;
    }
    
}
