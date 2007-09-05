// plasmaURLPattern.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// last major change: 11.07.2005
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

package de.anomic.plasma.urlPattern;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;


public class defaultURLPattern extends abstractURLPattern implements plasmaURLPattern {

    public defaultURLPattern(File rootPath) {
        super(rootPath);
    }

    public String getEngineInfo() {
        return "Default YaCy Blacklist Engine";
    }

    public boolean isListed(String blacklistType, String hostlow, String path) {
        if (hostlow == null) throw new NullPointerException();
        if (path == null) throw new NullPointerException();

        // getting the proper blacklist
        HashMap blacklistMap = super.getBlacklistMap(blacklistType);

        if (path.length() > 0 && path.charAt(0) == '/') path = path.substring(1);
        ArrayList app;
        boolean matched = false;
        String pp = ""; // path-pattern

        // first try to match the domain with wildcard '*'
        // [TL] While "." are found within the string
        int index = 0;
        while (!matched && (index = hostlow.indexOf('.', index + 1)) != -1) {
            if ((app = (ArrayList) blacklistMap.get(hostlow.substring(0, index + 1) + "*")) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = (String)app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
        }
        index = hostlow.length();
        while (!matched && (index = hostlow.lastIndexOf('.', index - 1)) != -1) {
            if ((app = (ArrayList) blacklistMap.get("*" + hostlow.substring(index, hostlow.length()))) != null) {
                for (int i=app.size()-1; !matched && i>-1; i--) {
                    pp = (String)app.get(i);
                    matched |= ((pp.equals("*")) || (path.matches(pp)));
                }
            }
        }

        // try to match without wildcard in domain
        if (!matched && (app = (ArrayList)blacklistMap.get(hostlow)) != null) {
            for (int i=app.size()-1; !matched && i>-1; i--) {
                pp = (String)app.get(i);
                matched |= ((pp.equals("*")) || (path.matches(pp)));
            }
        }

        return matched;
    }
}
