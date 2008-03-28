// advancedURLPattern.java 
// -----------------------
// part of YaCy
// (C) by Alexander Fieger webmaster@lulabad.de
// first published on http://www.lulabad.de
// Ingolstadt, Germany, 2006
// last major change: 12.08.2006
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
package de.anomic.index;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;


public class indexAdvancedDefaultWhiteReferenceBlacklist extends indexAbstractReferenceBlacklist implements indexReferenceBlacklist {

	public indexAdvancedDefaultWhiteReferenceBlacklist(File rootPath) {
        super(rootPath);    
    }
    
    public String getEngineInfo() {
        return "Advanced YaCy Whitelist Engine by lulabad";
    }    
    
    public boolean isListed(String blacklistType, String hostlow, String path) {
        if (hostlow == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();
        
        // getting the proper blacklist
        HashMap blacklistMap = super.getBlacklistMap(blacklistType);
        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        String pp = ""; // path-pattern

        String tmp1 = "";
        String tmp2 = "";

        ArrayList app;
        boolean matched = true;

        String[] result = hostlow.split("\\.");

		for (int x=0; x<result.length; x++) {
            if ((app = (ArrayList) blacklistMap.get("*." + result[x])) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
            if ((app = (ArrayList)  blacklistMap.get(result[x] + ".*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
            if ((app = (ArrayList)  blacklistMap.get("*." + result[x] + ".*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
			
			tmp1 = tmp1.concat("." + result[x]);
			tmp2 = result[result.length-1-x].concat("."+ tmp2);
         

            if ((app = (ArrayList)  blacklistMap.get(tmp1.substring(1))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
            if ((app = (ArrayList)  blacklistMap.get("*" + tmp1)) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
            if ((app = (ArrayList)  blacklistMap.get(tmp1.substring(1) + ".*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
            if ((app = (ArrayList)  blacklistMap.get("*" + tmp1 + ".*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }


            if ((app = (ArrayList)  blacklistMap.get(tmp2.substring(0,tmp2.length()-1))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
            if ((app = (ArrayList)  blacklistMap.get("*." + tmp2.substring(0,tmp2.length()-1))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
            if ((app = (ArrayList)  blacklistMap.get(tmp2 + "*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
            if ((app = (ArrayList)  blacklistMap.get("*." + tmp2 + "*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                	pp = (String)app.get(i);
                	matched ^= ((pp.equals("*")) || (path.matches(pp)));
                }
                return !matched;
            }
		}
        return true;
    }

}
