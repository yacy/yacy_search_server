// YMarkWordCountComparator.java
// (C) 2011 by Stefan Förster, sof@gmx.de, Norderstedt, Germany
// first published 2010 on http://yacy.net
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

package de.anomic.data.ymark;

import java.util.Comparator;
import java.util.Map;

import net.yacy.kelondro.data.word.Word;

public class YMarkWordCountComparator implements Comparator<String> {

	private Map<String,Word> words;
	
	public YMarkWordCountComparator(final Map<String,Word> words) {
		this.words = words;
	}
	
	public int compare(final String k1, final String k2) {
		final Word w1 = this.words.get(k1);
		final Word w2 = this.words.get(k2);
		
        if(w1.occurrences() > w2.occurrences())
            return 1;
        else if(w1.occurrences() < w2.occurrences())
            return -1;
        else
            return 0; 
	}
}
