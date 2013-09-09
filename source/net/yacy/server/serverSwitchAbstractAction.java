// serverSwitchAbstractAction.java 
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 11.05.2005
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

package net.yacy.server;

import net.yacy.cora.util.ConcurrentLog;

public abstract class serverSwitchAbstractAction {

    protected ConcurrentLog log = null;
    private String shortDescr = "", longDescr = "";
    
    public void setDescription(final String shortText, final String longText) {
        // sets a visible description string
        this.shortDescr = shortText;
        this.longDescr  = longText;
    }
    
    public String getShortDescription() {
	// returns short description string for online display
        return this.shortDescr;
    }
    
    public String getLongDescription() {
	// returns long description string for online display
	return this.longDescr;
    }

    public void setLog(final ConcurrentLog log) {
        // defines a log where process states can be written to
        this.log = log;
    }
    
}
