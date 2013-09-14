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

package net.yacy.peers;

import java.io.File;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.search.Switchboard;


public class Accessible {
    public long lastUpdated;
    public boolean IWasAccessed;
    
    /**
     * updates Shortcut /addon/YaCy-Search.html
     * @param newPort
     */
    public static void setNewPortLink(final int newPort){
    	try {
        	final Switchboard sb = Switchboard.getSwitchboard();
        	final File shortcut = new File(sb.getAppPath() + "/addon/YaCy-Search.html".replace("/", File.separator));
        	final String content = "<meta http-equiv=\"refresh\" content=\"0;url=http://localhost:" + newPort + "/\">";
        	FileUtils.copy(UTF8.getBytes(content), shortcut);
		} catch (final Exception e) {
			return;
		}
    }
    
    /**
     * updates Shortcut /addon/YaCy-Search.bat
     * @param newPort
     */
    public static void setNewPortBat(final int newPort){
    	try {
        	final Switchboard sb = Switchboard.getSwitchboard();
        	final File shortcut = new File(sb.getAppPath() + "/addon/YaCy-Search.bat".replace("/", File.separator));
        	final String content = "rundll32 url.dll,FileProtocolHandler \"http://localhost:" + newPort + "\"";
        	FileUtils.copy(UTF8.getBytes(content), shortcut);
		} catch (final Exception e) {
			return;
		}
    }
}
