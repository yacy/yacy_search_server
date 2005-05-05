// loaderCore.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 29.09.2004
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


package de.anomic.tools;

import java.util.Properties;
import java.util.Vector;

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
    public abstract void feed(Vector v); // returns true if process was successful; should be always synchronized

    public void terminate() {
        // if terminated before completion, completed() shows x < 100
        run = false;
    }
    
    // feed-back methods
    public Properties result() {
        return result;
    }

    public int completed() {
        // guess of completion status. shall be 100 if totally completed.
        return completion;
    }

    // error control
    public int status() {
        // -1=idle, 0=ready, 1=running, 2=aborted, 3=failed, 4=completed, 9=finalized
        return status;
    }

    public boolean available() {
        // true if it is ok to feed with feed()
        return (status() == STATUS_READY) ||
               ((status() == STATUS_COMPLETED) && ((result == null) || (result.size() == 0)));
    }
    
    public Exception error() {
        // if in error status: this returnes exception
        return error;
    }
    
}
