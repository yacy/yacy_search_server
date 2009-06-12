package de.anomic.tools;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.parser.Word;

// People make mistakes when they type words.  
// The most common mistakes are the four categories listed below:
// (1)	Changing one letter: bat / cat;
// (2)	Adding one letter: bat / boat;
// (3)	Deleting one letter: frog / fog; or
// (4)	Reversing two consecutive letters: two / tow.

public class DidYouMean {

	private static char[] alphabet = {'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p',
									  'q','r','s','t','u','v','w','x','y','z','\u00e4','\u00f6','\u00fc','\u00df'}; 
	private final Set<String> set;
	private final plasmaSwitchboard sb;
	private String word;
	private int len;
	
	public DidYouMean(final plasmaSwitchboard env) {
		this.set = new HashSet<String>();
		this.word = "";
		this.len = 0;
		this.sb = env;
	}
	
	public Set<String> getSuggestion(final String word) {
		this.word = word.toLowerCase();
		this.len = word.length();
		
		ChangingOneLetter();
		AddingOneLetter();
		DeletingOneLetter();
		ReversingTwoConsecutiveLetters();
		
		final Iterator<String> it = this.set.iterator();
		// final TreeSet<String> rset = new TreeSet<String>(new wordSizeComparator());
		final TreeSet<String> rset = new TreeSet<String>();
		String s;
		int count = 0;
		while(count<10 && it.hasNext()) {
			s = it.next();			
			if(sb.indexSegment.termIndex().has(Word.word2hash(s))) {
				rset.add(s);
				count++;
			}			
		}	
		rset.remove(word.toLowerCase());
		return rset;
	}
	
	private void ChangingOneLetter() {		
		for(int i=0; i<this.len; i++) {
			for(int j=0; j<alphabet.length; j++) {
				this.set.add(this.word.substring(0, i) + alphabet[j] + this.word.substring(i+1));
			}
		}
	}
	
	private void DeletingOneLetter() {
		for(int i=0; i<this.len;i++) {
			this.set.add(this.word.substring(0, i) + this.word.substring(i+1));			
		}
	}
	
	private void AddingOneLetter() {
		for(int i=0; i<this.len;i++) {
			for(int j=0; j<alphabet.length; j++) {
				this.set.add(this.word.substring(0, i) + alphabet[j] + this.word.substring(i));
			}			
		}
	}
	
	private void ReversingTwoConsecutiveLetters() {
		for(int i=0; i<this.word.length()-1; i++) {
			this.set.add(this.word.substring(0,i)+this.word.charAt(i+1)+this.word.charAt(i)+this.word.substring(i+2));
		}
	}
	
    public class wordSizeComparator implements Comparator<String> {

		public int compare(final String o1, final String o2) {
    		final Integer i1 = sb.indexSegment.termIndex().count(Word.word2hash(o1));
    		final Integer i2 = sb.indexSegment.termIndex().count(Word.word2hash(o2));
    		return i1.compareTo(i2);
    	}
    	
    }
	
}


