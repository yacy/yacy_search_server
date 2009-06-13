package de.anomic.tools;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.anomic.kelondro.text.IndexCell;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.kelondro.util.Log;
import de.anomic.plasma.parser.Word;

// People make mistakes when they type words.  
// The most common mistakes are the four categories listed below:
// (1)	Changing one letter: bat / cat;
// (2)	Adding one letter: bat / boat;
// (3)	Deleting one letter: frog / fog; or
// (4)	Reversing two consecutive letters: two / tow.

public class DidYouMean {

	private static final char[] alphabet = {'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p',
									  'q','r','s','t','u','v','w','x','y','z','\u00e4','\u00f6','\u00fc','\u00df'}; 
	private static final long TIMEOUT = 2000;
	
	private final Set<String> set;
	private final IndexCell<WordReference> index;
	private String word;
	private int len;
	
	private Thread ChangingOneLetter;
	private Thread AddingOneLetter;
	private Thread DeletingOneLetter;
	private Thread ReversingTwoConsecutiveLetters;
	
	public DidYouMean(final IndexCell<WordReference> index) {
		// this.set = Collections.synchronizedSortedSet(new TreeSet<String>(new wordSizeComparator()));
		this.set = Collections.synchronizedSet(new HashSet<String>());
		this.word = "";
		this.len = 0;
		this.index = index;
		
		this.ChangingOneLetter = new ChangingOneLetter();
		this.AddingOneLetter = new AddingOneLetter();
		this.DeletingOneLetter = new DeletingOneLetter();
		this.ReversingTwoConsecutiveLetters = new ReversingTwoConsecutiveLetters();
	}
	
	public Set<String> getSuggestion(final String word) {
		long startTime = System.currentTimeMillis();
		this.word = word.toLowerCase();
		this.len = word.length();

		this.ChangingOneLetter.start();
		this.AddingOneLetter.start();
		this.DeletingOneLetter.start();
		this.ReversingTwoConsecutiveLetters.start();
		
		try {
			this.ReversingTwoConsecutiveLetters.join(TIMEOUT);
			this.DeletingOneLetter.join(TIMEOUT);
			this.ChangingOneLetter.join(TIMEOUT);
			this.AddingOneLetter.join(TIMEOUT);
		} catch (InterruptedException e) {
		}		
		
		this.set.remove(word.toLowerCase());
		Log.logInfo("DidYouMean", "found "+this.set.size()+" terms; execution time: "+(System.currentTimeMillis()-startTime)+"ms");
		
		return this.set;
			
	}
	
	private class ChangingOneLetter extends Thread {
		// tests: alphabet.length * len
		public void run() {
			String s;
			int count = 0;
			for(int i=0; i<len; i++) {
				for(int j=0; j<alphabet.length; j++) {
					s = word.substring(0, i) + alphabet[j] + word.substring(i+1);
					if (index.has(Word.word2hash(s))) {
						set.add(s);
						count++;
					}
				}
			}
		}
	}
	
	private class DeletingOneLetter extends Thread {
		// tests: len
		public void run() {
		String s;
		int count = 0;
			for(int i=0; i<len;i++) {
				s = word.substring(0, i) + word.substring(i+1);
				if (index.has(Word.word2hash(s))) {
					set.add(s);
					count++;
				}
			}
		}		
	}
	
	private class AddingOneLetter extends Thread {
		// tests: alphabet.length * len
		public void run() {
			String s;
			int count = 0;
			for(int i=0; i<=len;i++) {
				for(int j=0; j<alphabet.length; j++) {
					s = word.substring(0, i) + alphabet[j] + word.substring(i);
					if (index.has(Word.word2hash(s))) {
						set.add(s);
						count++;
					}
				}			
			}
		}
	}
	
	private class ReversingTwoConsecutiveLetters extends Thread {
		// tests: (len - 1)
		public void run() {
			String s;
			int count = 0;
			for(int i=0; i<len-1; i++) {
				s = word.substring(0,i)+word.charAt(i+1)+word.charAt(i)+word.substring(i+2);
				if (index.has(Word.word2hash(s))) {
					set.add(s);
					count++;
				}
			}
		}
	}
	
	/*
    private class wordSizeComparator implements Comparator<String> {
		public int compare(final String o1, final String o2) {
    		final Integer i1 = index.count(Word.word2hash(o1));
    		final Integer i2 = index.count(Word.word2hash(o2));
    		return i2.compareTo(i1);
    	}    	
    }
    */
	
}


