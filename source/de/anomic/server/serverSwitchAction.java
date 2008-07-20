// serverSwitchAction.java 
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

package de.anomic.server;

import de.anomic.server.logging.serverLog;

public interface serverSwitchAction {

    // --------------------------------------------------------------------------
    // the following methods are implemented by serverSwitchAsbtractAction
    // and do not need to be altered
    
    public void setDescription(String shortText, String longText);
    // sets a visible description string
    
    public String getShortDescription();
    // returns short description string for online display
    
    public String getLongDescription();
    // returns long description string for online display
    
    public void setLog(serverLog log);
    // defines a log where process states can be written to

    // ---------------------------------------------------------------------
    // the following methods are supposed to be implemented
    // by extending a serverSwitchAbstractAction object

    public void doBevoreSetConfig(String key, String newvalue);
    public void doAfterSetConfig(String key, String newvalue, String oldvalue);
    public void doWhenGetConfig(String key, String actualvalue, String defaultvalue);

    public void close(); // called when an action is undeployed
}
