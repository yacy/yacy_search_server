// query.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last change: 15.05.2004
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
// javac -classpath .:../../Classes query.java
// if the shell's current path is HTROOT

import java.util.Hashtable;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyCore;

public class query {

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch sb) {
	// return variable that accumulates replacements
	serverObjects prop = new serverObjects();

	plasmaSwitchboard switchboard = (plasmaSwitchboard) sb;
	// System.out.println("YACYQUERY: RECEIVED POST = " + ((post == null) ? "NULL" : post.toString()));

	if ((post == null) || (switchboard == null)) return new serverObjects();

	String iam    = (String) post.get("iam", "");    // complete seed of the requesting peer
	String youare = (String) post.get("youare", ""); // seed hash of the target peer, used for testing network stability
	String key    = (String) post.get("key", "");    // transmission key for response
	String obj    = (String) post.get("object", ""); // keyword for query subject
	String env    = (String) post.get("env", "");    // argument to query

	// check if we are the right target and requester has correct information about this peer
	if ((yacyCore.seedDB.mySeed == null) || (!(yacyCore.seedDB.mySeed.hash.equals(youare)))) {
	    // this request has a wrong target
	    prop.put("response", "-1"); // request rejected
	    return prop;
	}

	// requests about environment

	if (obj.equals("wordcount")) {
	    // the total number of different words in the rwi is returned
	    prop.put("response", "0"); // dummy response
	    return prop;
	}

	if (obj.equals("rwicount")) {
	    // return the number of available word indexes
	    // <env> shall contain a word hash, the number of assigned lurls to this hash is returned
	    prop.put("response", "0"); // dummy response
	    return prop;
	}

	if (obj.equals("lurlcount")) {
	    // return the number of all available l-url's
	    Hashtable result = switchboard.action("urlcount", null);
	    //System.out.println("URLCOUNT result = " + ((result == null) ? "NULL" : result.toString()));
	    prop.put("response", ((result == null) ? "-1" : (String) result.get("urls")));
	    return prop;
	}

	if (obj.equals("purlcount")) {
	    // return number of stacked prefetch urls
	    prop.put("response", "0"); // dummy response
	    return prop;
	}

	if (obj.equals("seedcount")) {
	    // return number of stacked prefetch urls
	    prop.put("response", "0"); // dummy response
	    return prop;
	}


	// requests about requirements

	if (obj.equals("wantedlurls")) {
	    prop.put("response", "0"); // dummy response
	    return prop;
	}

	if (obj.equals("wantedpurls")) {
	    prop.put("response", "0"); // dummy response
	    return prop;
	}

	if (obj.equals("wantedword")) {
	    // response returns a list of wanted word hashes
	    prop.put("response", "0"); // dummy response
	    return prop;
	}

	if (obj.equals("wantedrwi")) {
	    // <env> shall contain a word hash, the number of wanted lurls for this hash is returned
	    prop.put("response", "0"); // dummy response
	    return prop;
	}

	if (obj.equals("wantedseeds")) {
	    // return a number of wanted seed
	    prop.put("response", "0"); // dummy response
	    return prop;
	}

        prop.put("mytime", yacyCore.universalDateShortString());
	// return rewrite properties
	return prop;
    }

}
