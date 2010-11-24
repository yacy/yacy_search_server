package de.anomic.data;

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
