// yacyAccessible.java
// -------------------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// this file is contributed by Stephan Hermens
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

/* This class just defines a container */

package de.anomic.yacy;

import java.io.File;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverFileUtils;

public class yacyAccessible {
    public long lastUpdated;
    public boolean IWasAccessed;
    
    /**
     * updates Shortcut /addon/YaCy-Search.html
     * @param newPort
     */
    public static void setNewPortLink(int newPort){
    	try {
        	plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
        	File shortcut = new File(sb.getRootPath() + "/addon/YaCy-Search.html".replace("/", File.separator));
        	String content = "<meta http-equiv=\"refresh\" content=\"0;url=http://localhost:" + newPort + "/\">";
        	serverFileUtils.copy(content.getBytes(), shortcut);
		} catch (Exception e) {
			return;
		}
    }
    
    /**
     * updates Shortcut /addon/YaCy-Search.bat
     * @param newPort
     */
    public static void setNewPortBat(int newPort){
    	try {
        	plasmaSwitchboard sb = plasmaSwitchboard.getSwitchboard();
        	File shortcut = new File(sb.getRootPath() + "/addon/YaCy-Search.bat".replace("/", File.separator));
        	String content = "rundll32 url.dll,FileProtocolHandler \"http://localhost:" + newPort + "\"";
        	serverFileUtils.copy(content.getBytes(), shortcut);
		} catch (Exception e) {
			return;
		}
    }
}
