// list.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
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
// javac -classpath .:../../classes list.java
// if the shell's current path is HTROOT

import java.io.File;
import de.anomic.data.listManager;
import de.anomic.http.httpHeader;
import de.anomic.server.serverCore;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;

public class list {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final String col = (String) post.get("col", "");

        final File listsPath = new File(env.getRootPath(),env.getConfig("listsPath", "DATA/LISTS"));

        if (col.equals("black")) {
            String filename = "";
            final StringBuffer out = new StringBuffer();

            final String filenames=env.getConfig("proxyBlackListsShared", "");
            final String[] filenamesarray = filenames.split(",");

            if(filenamesarray.length >0){
                for(int i = 0;i <= filenamesarray.length -1; i++){
                    filename = filenamesarray[i];
                    out.append(listManager.getListString(new File(listsPath,filename).toString(), false)).append(serverCore.crlfString);
                }
            } // if filenamesarray.length >0

            prop.put("list",out);
        } else {
            prop.put("list","");
        }

        return prop;
    }

}