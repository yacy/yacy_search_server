// indexDefaultReference.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.07.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
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

package de.anomic.index;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;


public class indexDefaultReferenceBlacklist extends indexAbstractReferenceBlacklist implements indexReferenceBlacklist {

    public indexDefaultReferenceBlacklist(File rootPath) {
        super(rootPath);
    }

    public String getEngineInfo() {
        return "Default YaCy Blacklist Engine";
    }

    public boolean isListed(String blacklistType, String hostlow, String path) {
        if (hostlow == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();

        // getting the proper blacklist
        HashMap<String, ArrayList<String>> blacklistMap = super.getBlacklistMap(blacklistType);

        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        ArrayList<String> app;
        boolean matched = false;
        String pp = ""; // path-pattern

        // first try to match the domain with wildcard '*'
        // [TL] While "." are found within the string
        int index = 0;
        while (!matched && (index = hostlow.indexOf('.', index + 1)) != -1) {
            if ((app = blacklistMap.get(hostlow.substring(0, index + 1) + "*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = (String)app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
        }
        index = hostlow.length();
        while (!matched && (index = hostlow.lastIndexOf('.', index - 1)) != -1) {
            if ((app = blacklistMap.get("*" + hostlow.substring(index, hostlow.length()))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = (String)app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
        }

        // try to match without wildcard in domain
        if (!matched && (app = blacklistMap.get(hostlow)) != null) {
            for (int i=app.size()-1; !matched && i>-1; i--) {
                pp = (String)app.get(i);
                matched |= ((pp.equals("*")) || (path.matches(pp)));
            }
        }

        return matched;
    }
}
