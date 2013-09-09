/**
 *  Triplestore_p
 *  Copyright 2012 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 14.09.2011 at http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

import net.yacy.cora.lod.JenaTripleStore;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public final class Triplestore_p {

	public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header,
			final serverObjects post, final serverSwitch env) {
		// return variable that accumulates replacements
		final serverObjects prop = new serverObjects();

		if (post != null) {

			if (post.containsKey("tsSettings")) {

				env.setConfig("triplestore.persistent",
						"on".equals(post.get("tspersistentenabled")) ? true : false);

//				env.setConfig("interaction.feedback.accept",
//						"on".equals(post.get("acceptfeedbackenabled")) ? true : false);

			}


		}

		prop.put("tspersistentenabled_checked",
				env.getConfigBool("triplestore.persistent", false) ? "1" : "0");

//		prop.put("acceptfeedbackenabled_checked",
//				env.getConfigBool("interaction.feedback.accept", false) ? "1" : "0");
		prop.put("size", JenaTripleStore.size());

		// return rewrite properties
		return prop;
	}

}
