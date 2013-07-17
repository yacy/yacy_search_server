/**
 *  SnippetExtractor
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 22.10.2010 at http://yacy.net
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

package net.yacy.document;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yacy.cora.storage.HandleSet;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.RowHandleSet;

public class SnippetExtractor {

    String snippetString;
    HandleSet remainingHashes;

    public SnippetExtractor(final Collection<StringBuilder> sentences, final HandleSet queryhashes, int maxLength) throws UnsupportedOperationException {
        if (sentences == null) throw new UnsupportedOperationException("sentence == null");
        if (queryhashes == null || queryhashes.isEmpty()) throw new UnsupportedOperationException("queryhashes == null");
        SortedMap<byte[], Integer> hs;
        final TreeMap<Long, StringBuilder> order = new TreeMap<Long, StringBuilder>();
        long uniqCounter = 999L;
        Integer pos;
        TreeSet<Integer> positions;
        int linenumber = 0;
        int fullmatchcounter = 0;
        lookup: for (final StringBuilder sentence: sentences) {
            hs = WordTokenizer.hashSentence(sentence.toString(), null, 100);
            positions = new TreeSet<Integer>();
            for (final byte[] word: queryhashes) {
                pos = hs.get(word);
                if (pos != null) {
                    positions.add(pos);
                }
            }
            int worddistance = positions.size() > 1 ? positions.last() - positions.first() : 0;
            // sort by
            // - 1st order: number of matching words
            // - 2nd order: word distance
            // - 3th order: line length (not too short and not too long)
            // - 4rd order: line number
            if (!positions.isEmpty()) {
                order.put(Long.valueOf(-100000000L * (linenumber == 0 ? 1 : 0) + 10000000L * positions.size() + 1000000L * worddistance + 100000L * linelengthKey(sentence.length(), maxLength) - 10000L * linenumber + uniqCounter--), sentence);
                if (order.size() > 5) order.remove(order.firstEntry().getKey());
                if (positions.size() == queryhashes.size()) fullmatchcounter++;
                if (fullmatchcounter >= 3) break lookup;
            }
            linenumber++;
        }

        StringBuilder sentence;
        SnippetExtractor tsr;
        while (!order.isEmpty()) {
            sentence = order.remove(order.lastKey()); // sentence with the biggest score
            try {
                tsr = new SnippetExtractor(sentence.toString(), queryhashes, maxLength);
            } catch (final UnsupportedOperationException e) {
                continue;
            }
            this.snippetString = tsr.snippetString;
            if (this.snippetString != null && this.snippetString.length() > 0) {
                this.remainingHashes = tsr.remainingHashes;
                if (this.remainingHashes.isEmpty()) {
                    // we have found the snippet
                    return; // finished!
                } else if (this.remainingHashes.size() < queryhashes.size()) {
                    // the result has not all words in it.
                    // find another sentence that represents the missing other words
                    // and find recursively more sentences
                    maxLength = maxLength - this.snippetString.length();
                    if (maxLength < 20) maxLength = 20;
                    try {
                        tsr = new SnippetExtractor(order.values(), this.remainingHashes, maxLength);
                    } catch (final UnsupportedOperationException e) {
                        throw e;
                    }
                    final String nextSnippet = tsr.snippetString;
                    if (nextSnippet == null) return;
                    this.snippetString = this.snippetString + (" / " + nextSnippet);
                    this.remainingHashes = tsr.remainingHashes;
                    return;
                } else {
                    // error
                    //assert remaininghashes.size() < queryhashes.size() : "remaininghashes.size() = " + remaininghashes.size() + ", queryhashes.size() = " + queryhashes.size() + ", sentence = '" + sentence + "', result = '" + result + "'";
                    continue;
                }
            }
        }
        throw new UnsupportedOperationException("no snippet computed");
    }

    private static int linelengthKey(int givenlength, int maxlength) {
        if (givenlength > maxlength) return 1;
        if (givenlength >= maxlength / 2 && givenlength < maxlength) return 7;
        if (givenlength >= maxlength / 4 && givenlength < maxlength / 2) return 5;
        if (givenlength >= maxlength / 8 && givenlength < maxlength / 4) return 3;
        return 0;
    }

    private SnippetExtractor(String sentence, final HandleSet queryhashes, final int maxLength) throws UnsupportedOperationException {
        try {
            if (sentence == null) throw new UnsupportedOperationException("no sentence given");
            if (queryhashes == null || queryhashes.isEmpty()) throw new UnsupportedOperationException("queryhashes == null");
            byte[] hash;

            // find all hashes that appear in the sentence
            final Map<byte[], Integer> hs = WordTokenizer.hashSentence(sentence, null, 100);
            final Iterator<byte[]> j = queryhashes.iterator();
            Integer pos;
            int p, minpos = sentence.length(), maxpos = -1;
            final HandleSet remainingHashes = new RowHandleSet(queryhashes.keylen(), queryhashes.comparator(), 0);
            while (j.hasNext()) {
                hash = j.next();
                pos = hs.get(hash);
                if (pos == null) {
                    try {
                        remainingHashes.put(hash);
                    } catch (final SpaceExceededException e) {
                        ConcurrentLog.logException(e);
                    }
                } else {
                    p = pos.intValue();
                    if (p > maxpos) maxpos = p;
                    if (p < minpos) minpos = p;
                }
            }
            // check result size
            maxpos = maxpos + 10;
            if (maxpos > sentence.length()) maxpos = sentence.length();
            if (minpos < 0) minpos = 0;
            // we have a result, but is it short enough?
            if (maxpos - minpos + 10 > maxLength) {
                // the string is too long, even if we cut at both ends
                // so cut here in the middle of the string
                final int lenb = sentence.length();
                sentence = sentence.substring(0, (minpos + 20 > sentence.length()) ? sentence.length() : minpos + 20).trim() +
                " [..] " +
                sentence.substring((maxpos + 26 > sentence.length()) ? sentence.length() : maxpos + 26).trim();
                maxpos = maxpos + lenb - sentence.length() + 6;
            } else if (maxpos > maxLength) {
                // the string is too long, even if we cut it at the end
                // so cut it here at both ends at once
                assert maxpos >= minpos;
                final int newlen = Math.max(10, maxpos - minpos + 10);
                assert maxLength >= newlen: "maxLength = " + maxLength + ", newlen = " + newlen;
                final int around = (maxLength - newlen) / 2;
                assert minpos - around < sentence.length() : "maxpos = " + maxpos + ", minpos = " + minpos + ", around = " + around + ", sentence.length() = " + sentence.length() + ", maxLength = " + maxLength + ", newlen = " + newlen; //maxpos = 435, minpos = 17, around = -124, sentence.length() = 44
                sentence = "[..] " + sentence.substring(minpos - around, ((maxpos + around) > sentence.length()) ? sentence.length() : (maxpos + around)).trim() + " [..]";
                minpos = around;
                maxpos = sentence.length() - around - 5;
            }
            if (sentence.length() > maxLength) {
                // trim sentence, 1st step (cut at right side)
                sentence = sentence.substring(0, Math.min(maxpos + 20, sentence.length())).trim() + " [..]";
            }
            if (sentence.length() > maxLength) {
                // trim sentence, 2nd step (cut at left side)
                sentence = "[..] " + sentence.substring(Math.max(minpos - 20, 0)).trim();
            }
            if (sentence.length() > maxLength) {
                // trim sentence, 3rd step (cut in the middle)
                sentence = sentence.substring(6, 20).trim() + " [..] " + sentence.substring(sentence.length() - 26, sentence.length() - 6).trim();
            }
            this.snippetString = sentence;
            this.remainingHashes = remainingHashes;
        } catch (final IndexOutOfBoundsException e) {
            throw new UnsupportedOperationException(e.getMessage());
        }
    }

    public String getSnippet() {
        return this.snippetString;
    }

    public HandleSet getRemainingWords() {
        return this.remainingHashes;
    }
}
