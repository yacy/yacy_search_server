// IndexAbstracts.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.10.2005 on http://yacy.net
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

package net.yacy.search;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class IndexAbstracts extends TreeMap<String, TreeMap<String, String>> {

	private static final long serialVersionUID = 3037740969349726216L;

	public IndexAbstracts() {
		super();
	}
	
	public String wordsFromPeer(final String peerhash, final String urls) {
        Map.Entry<String, TreeMap<String, String>> entry;
        String word, peerlist, url, wordlist = "";
        TreeMap<String, String> urlPeerlist;
        int p;
        boolean hasURL;
        synchronized (this) {
            final Iterator<Map.Entry <String, TreeMap<String, String>>> i = this.entrySet().iterator();
            while (i.hasNext()) {
                entry = i.next();
                word = entry.getKey();
                urlPeerlist = entry.getValue();
                hasURL = true;
                for (int j = 0; j < urls.length(); j = j + 12) {
                    url = urls.substring(j, j + 12);
                    peerlist = urlPeerlist.get(url);
                    p = (peerlist == null) ? -1 : peerlist.indexOf(peerhash);
                    if ((p < 0) || (p % 12 != 0)) {
                        hasURL = false;
                        break;
                    }
                }
                if (hasURL) wordlist += word;
            }
        }
        return wordlist;
    }
	
}