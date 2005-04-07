// list.java 
// -----------------------
// part of the AnomicHTTPProxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This File is contributed by Alexander Schier
// last change: 18.06.2004
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

// you must compile this file with
// javac -classpath .:../../Classes list.java
// if the shell's current path is HTROOT

import java.util.*;
import java.io.*;
import de.anomic.tools.*;
import de.anomic.server.*;
import de.anomic.yacy.*;
import de.anomic.http.*;
import de.anomic.data.*;

public class list {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
	// return variable that accumulates replacements
	serverObjects prop = new serverObjects();
	String col = (String) post.get("col", "");

	File listsPath = new File(env.getRootPath(),env.getConfig("listsPath", "DATA/LISTS"));

	if (col.equals("black")) {
	    String filename = "";
	    String line;
	    String out = "";
	    
		String filenames=env.getConfig("proxyBlackListsShared", "");
		String filenamesarray[] = filenames.split(",");

		if(filenamesarray.length >0){
			for(int i = 0;i <= filenamesarray.length -1; i++){
				filename = filenamesarray[i];
				out += listManager.getListString(new File(listsPath,filename).toString(), false) + serverCore.crlfString;
			}
		}//if filenamesarray.length >0
	    
	    prop.put("list",out);
	} else {
	    prop.put("list","");
	}

	return prop;
    }

}
