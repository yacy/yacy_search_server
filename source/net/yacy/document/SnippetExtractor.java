/**
 *  SnippetExtractor
 *  Copyright 2010 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 22.10.2010 at https://yacy.net
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import net.yacy.cora.language.synonyms.SynonymLibrary;

public class SnippetExtractor {

    private String snippetString;
    private Set<String> remainingTerms;

    
    public SnippetExtractor(final Iterable<StringBuilder> sentences, final Set<String> queryTerms, int maxLength) throws UnsupportedOperationException {
        if (sentences == null) throw new UnsupportedOperationException("sentences == null");
        if (queryTerms == null || queryTerms.isEmpty()) throw new UnsupportedOperationException("queryTerms == null");
        final TreeMap<Long, StringBuilder> sentences_candidates = new TreeMap<Long, StringBuilder>();
        long uniqCounter = 999L;
        Integer pos;
        int linenumber = 0;
        int fullmatchcounter = 0;
        lookup: for(final StringBuilder sentence : sentences) {
            SortedMap<String, Integer> positions_in_sentence = WordTokenizer.tokenizeSentence(sentence.toString(), 100);
            TreeSet<Integer> found_positions = new TreeSet<Integer>(); // the positions of the query terms in the sentence
            for (final String word: queryTerms) {
                pos = positions_in_sentence.get(word);
                if (pos != null) {
                    found_positions.add(pos);
                } else {
                    // try to find synonyms
                    Set<String> syms = SynonymLibrary.getSynonyms(word);
                    if (syms != null && syms.size() > 0) {
                        symsearch: for (String sym: syms) {
                            pos = positions_in_sentence.get(sym);
                            if (pos != null) {
                                found_positions.add(pos);
                                break symsearch;
                            }
                        }
                    }
                }
            }
            int worddistance = found_positions.size() > 1 ? found_positions.last() - found_positions.first() : 0;
            // sort by
            // - 1st order: number of matching words
            // - 2nd order: word distance
            // - 3th order: line length (not too short and not too long)
            // - 4rd order: line number
            if (!found_positions.isEmpty()) {
                sentences_candidates.put(Long.valueOf(-100000000L * (linenumber == 0 ? 1 : 0) + 10000000L * found_positions.size() + 1000000L * worddistance + 100000L * linelengthKey(sentence.length(), maxLength) - 10000L * linenumber + uniqCounter--), sentence);
                if (sentences_candidates.size() > 5) sentences_candidates.remove(sentences_candidates.firstEntry().getKey());
                if (found_positions.size() == queryTerms.size()) fullmatchcounter++;
                if (fullmatchcounter >= 3) break lookup;
            }
            linenumber++;
        }

        StringBuilder sentence;
        SnippetExtractor tsr;
        while (!sentences_candidates.isEmpty()) {
            sentence = sentences_candidates.remove(sentences_candidates.lastKey()); // sentence with the biggest score
            try {
                tsr = new SnippetExtractor(sentence.toString(), queryTerms, maxLength);
            } catch (final UnsupportedOperationException e) {
                continue;
            }
            this.snippetString = tsr.snippetString;
            if (this.snippetString != null && this.snippetString.length() > 0) {
                this.remainingTerms = tsr.remainingTerms;
                if (this.remainingTerms.isEmpty()) {
                    // we have found the snippet
                    return; // finished!
                } else if (this.remainingTerms.size() < queryTerms.size()) {
                    // the result has not all words in it.
                    // find another sentence that represents the missing other words
                    // and find recursively more sentences
                    maxLength = maxLength - this.snippetString.length();
                    if (maxLength < 20) maxLength = 20;
                    try {
                        tsr = new SnippetExtractor(sentences_candidates.values(), this.remainingTerms, maxLength);
                    } catch (final UnsupportedOperationException e) {
                        throw e;
                    }
                    final String nextSnippet = tsr.snippetString;
                    if (nextSnippet == null) return;
                    this.snippetString = this.snippetString + (" / " + nextSnippet);
                    this.remainingTerms = tsr.remainingTerms;
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

    
    private SnippetExtractor(String sentence, final Set<String> queryTerms, final int maxLength) throws UnsupportedOperationException {
        try {
            if (sentence == null) throw new UnsupportedOperationException("no sentence given");
            if (queryTerms == null || queryTerms.isEmpty()) throw new UnsupportedOperationException("queryTerms == null");
            String term;

            // find all hashes that appear in the sentence
            final Map<String, Integer> hs = WordTokenizer.tokenizeSentence(sentence, 100);
            final Iterator<String> j = queryTerms.iterator();
            Integer pos;
            int p, minpos = sentence.length(), maxpos = -1;
            final Set<String> remainingTerms = new HashSet<>();
            while (j.hasNext()) {
                term = j.next();
                pos = hs.get(term);
                if (pos == null) {
                    // try to find synonyms
                    Set<String> syms = SynonymLibrary.getSynonyms(term);
                    boolean found = false;
                    if (syms != null && syms.size() > 0) {
                        symsearch: for (String sym : syms) {
                            pos = hs.get(sym);
                            if (pos != null) {
                                p = pos.intValue();
                                if (p > maxpos) maxpos = p;
                                if (p < minpos) minpos = p;
                                found = true;
                                break symsearch;
                            }
                        }
                    }
                    if (!found) remainingTerms.add(term);
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
            this.remainingTerms = remainingTerms;
        } catch (final IndexOutOfBoundsException e) {
            throw new UnsupportedOperationException(e.getMessage());
        }
    }

    public String getSnippet() {
        return this.snippetString;
    }

    public Set<String> getRemainingTerms() {
		return this.remainingTerms;
	}
}
