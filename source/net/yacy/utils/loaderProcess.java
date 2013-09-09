// loaderProcess.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 28.09.2004
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

public interface loaderProcess {
    
    // steering methods
    public void feed(byte[] v); // returns true if process was successful; should be always synchronized
    public void terminate(); // if terminated before completion, completed() shows x < 100
    
    // feed-back methods
    public Properties result();
    public int completed(); // guess of completion status. shall be 100 if totally completed.

    // error control
    public int status(); // see loaderCore status constants
    public boolean available(); // true if it is ok to feed with feed()
    public Exception error(); // if in error status: this returnes exception
    
}
