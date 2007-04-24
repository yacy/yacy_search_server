// profile.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004, 2005
//
// This file ist contributed by Alexander Schier
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

// You must compile this file with
// javac -classpath .:../../Classes hello.java
// if the shell's current path is HTROOT

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public final class profile {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch ss) {
        // return variable that accumulates replacements
        serverObjects prop = new serverObjects();
        plasmaSwitchboard sb = (plasmaSwitchboard) ss;
        if (prop == null) { return null; }

        if ((sb.isRobinsonMode()) &&
           	 (!((sb.isOpenRobinsonCluster()) ||
           	    (sb.isInMyCluster((String)header.get(httpHeader.CONNECTION_PROP_CLIENTIP)))))) {
               // if we are a robinson cluster, answer only if this client is known by our network definition
        	prop.put("list", 0);
            return prop;
        }
        
        Properties profile = new Properties();
        int count=0;
        String key="";
        String value="";

        FileInputStream fileIn = null;
        try {
            fileIn = new FileInputStream(new File("DATA/SETTINGS/profile.txt"));
            profile.load(fileIn);        
        } catch(IOException e) {
        } finally {
            if (fileIn != null) try { fileIn.close(); fileIn = null; } catch (Exception e) {}
        }

        Iterator it = ((Map)profile).keySet().iterator();
        while (it.hasNext()) {
            key=(String)it.next();
            value=profile.getProperty(key, "").replaceAll("\r","").replaceAll("\n","\\\\n");
            if( !(key.equals("")) && !(value.equals("")) ){
                prop.putASIS("list_"+count+"_key", key);
                prop.putASIS("list_"+count+"_value", value);
                count++;
            }
        }
        prop.put("list", count);

        // return rewrite properties
        return prop;
    }

}
