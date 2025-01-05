// WordReferenceFactory.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 09.04.2009 on http://yacy.net
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

package net.yacy.kelondro.data.word;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.rwi.ReferenceFactory;

public class WordReferenceFactory implements ReferenceFactory<WordReference>, Serializable {

    private static final long serialVersionUID=-7168706947127349876L;

    @Override
    public WordReference produceSlow(final Entry e) {
        return new WordReferenceRow(e);
    }

    @Override
    public WordReference produceFast(final WordReference r, final boolean local) {
        if (r instanceof WordReferenceVars) return r;
        return new WordReferenceVars(r, local);
    }

    @Override
    public Row getRow() {
        return WordReferenceRow.urlEntryRow;
    }

    /**
     * create an index abstract for a given WordReference ReferenceContainer
     * This extracts all the host hashes from a reference Container and returns a byte buffer
     * with a compressed representation of the host references
     * @param <ReferenceType>
     * @param inputContainer
     * @param excludeContainer
     * @param maxtime
     * @return
     */
    public static final <ReferenceType extends WordReference> ByteBuffer compressIndex(final ReferenceContainer<WordReference> inputContainer, final ReferenceContainer<WordReference> excludeContainer, final long maxtime) {
        // collect references according to domains
        final long timeout = maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : (maxtime < 0) ? Long.MAX_VALUE : System.currentTimeMillis() + maxtime;
        final TreeMap<String, StringBuilder> doms = new TreeMap<String, StringBuilder>();
        synchronized (inputContainer) {
            final Iterator<WordReference> i = inputContainer.entries();
            WordReference iEntry;
            String dom, mod;
            StringBuilder paths;
            while (i.hasNext()) {
                iEntry = i.next();
                if ((excludeContainer != null) && (excludeContainer.getReference(iEntry.urlhash()) != null)) continue; // do not include urls that are in excludeContainer
                dom = (iEntry instanceof WordReferenceVars) ? ((WordReferenceVars) iEntry).hosthash() : ASCII.String(iEntry.urlhash(), 6, 6);
                mod = ASCII.String(iEntry.urlhash(), 0, 6);
                if ((paths = doms.get(dom)) == null) {
                    doms.put(dom, new StringBuilder(30).append(mod));
                } else {
                    doms.put(dom, paths.append(mod));
                }
                if (System.currentTimeMillis() > timeout)
                    break;
            }
        }
        // construct a result string
        final ByteBuffer bb = new ByteBuffer(inputContainer.size() * 6);
        bb.append('{');
        final Iterator<Map.Entry<String, StringBuilder>> i = doms.entrySet().iterator();
        Map.Entry<String, StringBuilder> entry;
        while (i.hasNext()) {
            entry = i.next();
            bb.append(entry.getKey());
            bb.append(':');
            bb.append(entry.getValue().toString());
            if (System.currentTimeMillis() > timeout)
                break;
            if (i.hasNext())
                bb.append(',');
        }
        bb.append('}');
        return bb;
    }

    /**
     * decompress an index abstract that was generated from a word index and transmitted over a network connection
     * @param ci
     * @param peerhash
     * @return a urlhash -> peerlist map: this shows in which peers an url is stored
     */
    public static final SortedMap<String, Set<String>> decompressIndex(ByteBuffer ci, final String peerhash) {
        SortedMap<String, Set<String>> target = Collections.synchronizedSortedMap(new TreeMap<String, Set<String>>());
        // target is a mapping from url-hashes to a string of peer-hashes
        if (ci.byteAt(0) != '{' || ci.byteAt(ci.length() - 1) != '}') return target;
        //System.out.println("DEBUG-DECOMPRESS: input is " + ci.toString());
        ci = ci.trim(1, ci.length() - 2);
        String dom, url;
        Set<String> peers;
        StringBuilder urlsb;
        while (ci.length() >= 13 && ci.byteAt(6) == ':') {
            assert ci.length() >= 6 : "ci.length() = " + ci.length();
            dom = ci.toStringBuilder(0, 6, 6).toString();
            ci.trim(7);
            while (!ci.isEmpty() && ci.byteAt(0) != ',') {
                assert ci.length() >= 6 : "ci.length() = " + ci.length();
                urlsb = ci.toStringBuilder(0, 6, 12);
                urlsb.append(dom);
                url = urlsb.toString();
                ci.trim(6);

                peers = target.get(url);
                if (peers == null) {
                    peers = new HashSet<String>();
                    target.put(url, peers);
                }
                peers.add(peerhash);
                //System.out.println("DEBUG-DECOMPRESS: " + url + ":" + target.get(url));
            }
            if (ci.byteAt(0) == ',') ci.trim(1);
        }
        //System.out.println("DEBUG-DECOMPRESS: " + target);
        return target;
    }
}
