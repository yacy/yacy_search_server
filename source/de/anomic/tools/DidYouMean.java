package de.anomic.tools;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
									  'q','r','s','t','u','v','w','x','y','z','ä','ö','ü','ß'}; 
	private final Set<String> set;
	private String word;
	private int len;
	private final plasmaSwitchboard sb;
	
	public DidYouMean(final plasmaSwitchboard env) {
		this.set = new HashSet<String>();
		this.word = "";
		this.len = 0;
		this.sb = env;
	}
	
	public Set<String> getSuggestion(String word) {
		this.word = word.toLowerCase();
		this.len = word.length();
		ChangingOneLetter();
		AddingOneLetter();
		DeletingOneLetter();
		ReversingTwoConsecutiveLetters();
		Iterator<String> it = this.set.iterator();
		String s;
		final HashSet<String> rset = new HashSet<String>();
		while(it.hasNext()) {
			s = it.next();			
			if(sb.indexSegment.termIndex().has(Word.word2hash(s))) {
				rset.add(s);
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
	
}


