package de.anomic.tools;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
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
	private static final long TIMEOUT = 500;
	
	private final Set<String> set;
	private final IndexCell<WordReference> index;
	private String word;
	private int len;
	
	private Thread ChangingOneLetter;
	private Thread AddingOneLetter;
	private Thread DeletingOneLetter;
	private Thread ReversingTwoConsecutiveLetters;
	
	private final BlockingQueue bq = new BlockingQueue();
	
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

	    // create worker threads
	    Thread[] workers = new Thread[8];
	    for (int i=0; i<workers.length; i++) {
		      workers[i] = new Worker("WorkerThread_"+i);
	    }
	    // first push the tasks for calculation of word variations on blocking queue
		bq.push(this.ChangingOneLetter);
		bq.push(this.AddingOneLetter);
		bq.push(this.DeletingOneLetter);
		bq.push(this.ReversingTwoConsecutiveLetters);		
		
		// check for timeout
		boolean run = true;
		while(run && (System.currentTimeMillis()-startTime) < TIMEOUT) {
			if(bq.size() > 0) {
				run = true;
			} else {
				run = false;
			}
		}
		
		// push "poison pill" for each worker thread
	    for (int i=0; i<workers.length; i++) {
	    	bq.push(new Thread() {
	    		public void run() {
	    			Thread.currentThread().interrupt();
	    		}
	        });
	    }
	    
		this.set.remove(word.toLowerCase());
		Log.logInfo("DidYouMean", "found "+this.set.size()+" terms; execution time: "
				+(System.currentTimeMillis()-startTime)+"ms"+ (run?"(timed out)":""));
		
		return this.set;
			
	}
	
	private class ChangingOneLetter extends Thread {
		
		public ChangingOneLetter() {
			this.setName("ChangingOneLetter");
		}
		
		// tests: alphabet.length * len
		public void run() {
			String s;
			for(int i=0; i<len; i++) {
				for(int j=0; j<alphabet.length; j++) {
					s = word.substring(0, i) + alphabet[j] + word.substring(i+1);
					bq.push(new Tester(s));
				}
			}
		}
	}
	
	private class DeletingOneLetter extends Thread {
		
		public DeletingOneLetter() {
			this.setName("DeletingOneLetter");
		}
		
		// tests: len
		public void run() {
			String s;
			for(int i=0; i<len;i++) {
				s = word.substring(0, i) + word.substring(i+1);
				bq.push(new Tester(s));
			}
		}
	}
	
	private class AddingOneLetter extends Thread {
		
		public AddingOneLetter() {
			this.setName("AddingOneLetter");
		}
		
		// tests: alphabet.length * len
		public void run() {
			String s;
			for(int i=0; i<=len;i++) {
				for(int j=0; j<alphabet.length; j++) {
					s = word.substring(0, i) + alphabet[j] + word.substring(i);
					bq.push(new Tester(s));
				}			
			}
		}
	}
	
	private class ReversingTwoConsecutiveLetters extends Thread {
		
		public ReversingTwoConsecutiveLetters() {
			this.setName("ReversingTwoConsecutiveLetters");
		}
		
		// tests: (len - 1)
		public void run() {
			String s;
			for(int i=0; i<len-1; i++) {
				s = word.substring(0,i)+word.charAt(i+1)+word.charAt(i)+word.substring(i+2);
				bq.push(new Tester(s));
			}
		}
	}
	
	private class Tester extends Thread {
		
		private String s;
		
		public Tester(String s) {
			this.s = s;
		}
		public void run() {
			if (index.has(Word.word2hash(s))) {
				set.add(s);
			}
		}
	}
	
	private class Worker extends Thread {
		public Worker(String name) { 
			super(name); 
			start(); 
		}		
		public void run() {
			try {
				while(!isInterrupted()) {
					((Runnable)bq.pop()).run();
				}
			} catch(InterruptedException e) {				
			}
		}
	}
	
	private class BlockingQueue {
		private final LinkedList<Thread> queue = new LinkedList<Thread>();

		public void push(Thread t) {
			synchronized(queue) {
				queue.add(t);
				queue.notify();
			}
		}
		public Object pop() throws InterruptedException {
			synchronized(queue) {
				while (queue.isEmpty()) {
					queue.wait();
				}
				return queue.removeFirst();
			}
		}
		public int size() {
			return queue.size();
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



