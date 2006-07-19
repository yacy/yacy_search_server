//page.java - super template, to allow a scripted layout.
//----------------------------------------------------------
//part of YaCy
//
// (C) 2006 by Alexander Schier
//
// last change: $LastChangedDate:  $ by $LastChangedBy: $
// $LastChangedRevision: $
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


//note: this isn't useful, yet.
//this is only a basic demonstration, what can be done with the supertemplates.
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class page {
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        //plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        //prop.put("test1", "testit!");
        //prop.put("test2", 1);
        
        return prop;
    }
    
}