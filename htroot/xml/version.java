// /xml/version.java
// -------------------------------
// (C) 2006 Alexander Schier
// part of YaCy
//
// last major change: 06.02.2006
// this file is contributed by Alexander Schier
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
// Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  US


package xml;
import de.anomic.http.httpHeader;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class version {
    
    
    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        
        prop.put("version", env.getConfig("version", "0.0"));
        prop.put("svnRev", env.getConfig("svnRevision", "0"));
        prop.put("buildDate", env.getConfig("vdate", "19700101"));
        // return rewrite properties
        return prop;
    }
    
}



