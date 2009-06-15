package de.anomic.tools;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import de.anomic.kelondro.text.IndexCell;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.plasma.parser.Word;
import de.anomic.yacy.logging.Log;

// People make mistakes when they type words.  
// The most common mistakes are the four categories listed below:
// (1)	Changing one letter: bat / cat;
// (2)	Adding one letter: bat / boat;
// (3)	Deleting one letter: frog / fog; or
// (4)	Reversing two consecutive letters: two / tow.

public class DidYouMean {

	private static final char[] alphabet = {'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p',
									  'q','r','s','t','u','v','w','x','y','z','\u00e4','\u00f6','\u00fc','\u00df'}; 
	private static final long TIMEOUT = 500;
	
	final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();
	
	private final Set<String> set;
	private final IndexCell<WordReference> index;
	private String word;
	private int len;
	
	public DidYouMean(final IndexCell<WordReference> index) {
		// this.set = Collections.synchronizedSortedSet(new TreeSet<String>(new wordSizeComparator()));
		this.set = Collections.synchronizedSet(new HashSet<String>());
		this.word = "";
		this.len = 0;
		this.index = index;
	}
	
	public Set<String> getSuggestion(final String word) {
		long startTime = System.currentTimeMillis();
		this.word = word.toLowerCase();
		this.len = word.length();
		
	    // create producers
		Thread[] producers = new Thread[4];
		producers[0] = new ChangingOneLetter();
		producers[1] = new AddingOneLetter();		
		producers[2] = new DeletingOneLetter();
		producers[3] = new ReversingTwoConsecutiveLetters();
		
		// start producers
	    for (int i=0; i<producers.length; i++) {
		      producers[i].start();
	    }
	    
	    // create and start 8 consumers threads
	    Thread[] consumers = new Thread[8];
	    for (int i=0; i<consumers.length; i++) {
		      consumers[i] = new Consumer();
		      consumers[i].start();
	    }
	    
		// check if timeout has been reached
		boolean cont = false;
	    while(((System.currentTimeMillis()-startTime) < TIMEOUT)) {			
			if(queue.size()==0) {
				// check if at least one producers is still running
			    for (int i=0; i<producers.length; i++) {
				      if(producers[i].isAlive())
				    	  cont = true;
			    }
			    if(!cont) break;
			}
		}
		
	    // interupt all consumer threads
	    for (int i=0; i<consumers.length; i++) {
		      consumers[i].interrupt();
	    }
	    
		/* put "poison pill" for each consumer thread
	    for (int i=0; i<consumers.length; i++) {
	    	try {
				queue.put("\n"); 
			} catch (InterruptedException e) {
			}
	    }
	    */
	    
	    // interupt all remaining producer threads
	    for (int i=0; i<producers.length; i++) {
		      producers[i].interrupt();
	    }
	    
		this.set.remove(word.toLowerCase());
		Log.logInfo("DidYouMean", "found "+this.set.size()+" terms; execution time: "
				+(System.currentTimeMillis()-startTime)+"ms"+ " - remaining queue size: "+queue.size());
		
		return this.set;
			
	}
	
	private class ChangingOneLetter extends Thread {
		
		// tests: alphabet.length * len
		public void run() {
			String s;
			for(int i=0; i<len; i++) {
				for(int j=0; j<alphabet.length; j++) {
					s = word.substring(0, i) + alphabet[j] + word.substring(i+1);
					try {
						queue.put(s);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		}
	}
	
	private class DeletingOneLetter extends Thread {
		
		// tests: len
		public void run() {
			String s;
			for(int i=0; i<len;i++) {
				s = word.substring(0, i) + word.substring(i+1);
				try {
					queue.put(s);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}
	
	private class AddingOneLetter extends Thread {
		
		// tests: alphabet.length * len
		public void run() {
			String s;
			for(int i=0; i<=len;i++) {
				for(int j=0; j<alphabet.length; j++) {
					s = word.substring(0, i) + alphabet[j] + word.substring(i);
					try {
						queue.put(s);
					} catch (InterruptedException e) {
						return;
					}
				}			
			}
		}
	}
	
	private class ReversingTwoConsecutiveLetters extends Thread {
	
		// tests: (len - 1)
		public void run() {
			String s;
			for(int i=0; i<len-1; i++) {
				s = word.substring(0,i)+word.charAt(i+1)+word.charAt(i)+word.substring(i+2);
				try {
					queue.put(s);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	class Consumer extends Thread {

		public void run() {
			try {
				while(true) { 
					String s = (String)queue.take();
					if(s.equals("\n"))
						this.interrupt();
					else
						consume(s); 
				}
			} catch (InterruptedException e) { 
				return; 
			}
		}
		void consume(String s) { 	
			if (index.has(Word.word2hash(s))) {
				set.add(s);
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



