package de.anomic.tools;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;

import de.anomic.kelondro.text.IndexCell;
import de.anomic.kelondro.text.referencePrototype.WordReference;
import de.anomic.plasma.parser.Word;
import de.anomic.yacy.logging.Log;

/**
 * People make mistakes when they type words.  
 * The most common mistakes are the four categories listed below:
 * <ol>
 * <li>Changing one letter: bat / cat;</li>
 * <li>Adding one letter: bat / boat;</li>
 * <li>Deleting one letter: frog / fog; or</li>
 * <li>Reversing two consecutive letters: two / tow.</li>
 * </ol>
 * DidYouMean provides producer threads, that feed a blocking queue with word variations according to
 * the above mentioned four categories. Consumer threads check then the generated word variations against a term index.
 * Only words contained in the term index are return by the getSuggestion method.<p/>
 * @author apfelmaennchen
 */
public class DidYouMean {

	private static final char[] alphabet = {'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p',
									  'q','r','s','t','u','v','w','x','y','z','\u00e4','\u00f6','\u00fc','\u00df'}; 
	
	public static final int availableCPU = Runtime.getRuntime().availableProcessors();
	final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();
	
	private final Set<String> set;
	private final IndexCell<WordReference> index;
	private String word;
	private int len;
	
	
	/**
	 * @param index a termIndex - most likely retrieved from a switchboard object.
	 * @param sort true/false -  sorts the resulting TreeSet by index.count(); <b>Warning:</b> this causes heavy i/o.
	 */
	public DidYouMean(final IndexCell<WordReference> index, boolean sort) {
		if(sort)
			this.set = Collections.synchronizedSortedSet(new TreeSet<String>(new wordSizeComparator()));
		else
			this.set = Collections.synchronizedSet(new HashSet<String>());
		this.word = "";
		this.len = 0;
		this.index = index;
	}
	
	/**
	 * @param index a termIndex - most likely retrieved from a switchboard object.
	 */
	public DidYouMean(final IndexCell<WordReference> index) {
		this(index, false);
	}
	
	/**
	 * This method triggers the 4 producer and 8 consumer threads of DidYouMean.
	 * <p/><b>Note:</b> the default timeout is 500ms
	 * @param word a String with a single word
	 * @return a Set&lt;String&gt; with word variations contained in index.
	 */
	public Set<String> getSuggestion(final String word) {
		return getSuggestion(word, 500);
	}
	
	/**
	 * This method triggers the 4 producer and 8 consumer threads of the DidYouMean object.
	 * @param word a String with a single word
	 * @param timeout execution time in ms.
	 * @return a Set&lt;String&gt; with word variations contained in term index.
	 */
	public Set<String> getSuggestion(final String word, long timeout) {
		long startTime = System.currentTimeMillis();
		this.word = word.toLowerCase();
		this.len = word.length();
		
	    // create producers
		// the intention of the 4 producers is to mix results, as there
		// is currently no default sorting or ranking due to the i/o performance of index.count()
		Thread[] producers = new Thread[4];
		producers[0] = new ChangingOneLetter();
		producers[1] = new AddingOneLetter();		
		producers[2] = new DeletingOneLetter();
		producers[3] = new ReversingTwoConsecutiveLetters();
		
		// start producers
	    for (int i=0; i<producers.length; i++) {
		      producers[i].start();
	    }
	    
	    // create and start consumers threads
	    Thread[] consumers = new Thread[availableCPU];
	    for (int i=0; i<consumers.length; i++) {
		      consumers[i] = new Consumer();
		      consumers[i].start();
	    }
	    
		// check if timeout has been reached
		boolean cont = false;
	    while(((System.currentTimeMillis()-startTime) < timeout)) {
	    	// checks if queue is already empty
			if(queue.size()==0) {
				// check if at least one producers is still running and potentially filling the queue
			    for (int i=0; i<producers.length; i++) {
				      if(producers[i].isAlive())
				    	  cont = true;
			    }
			    // as the queue is empty and no producer is running we can break the timeout-loop
			    if(!cont) break;
			}
		}
		
	    // interrupt all consumer threads
	    for (int i=0; i<consumers.length; i++) {
		      consumers[i].interrupt();
	    }
	    
	    // interrupt all remaining producer threads
	    for (int i=0; i<producers.length; i++) {
		      producers[i].interrupt();
	    }
	    
		this.set.remove(word.toLowerCase());
		Log.logInfo("DidYouMean", "found "+this.set.size()+" terms; execution time: "
				+(System.currentTimeMillis()-startTime)+"ms"+ " - remaining queue size: "+queue.size());
		
		return this.set;
			
	}
    /**
     * DidYouMean's producer thread that changes one letter (e.g. bat/cat) for a given term
     * based on the given alphabet and puts it on the blocking queue, to be 'consumed' by a consumer thread.<p/>
     * <b>Note:</b> the loop runs (alphabet.length * len) tests.
     */	
	private class ChangingOneLetter extends Thread {
		
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
    /**
     * DidYouMean's producer thread that deletes extra letters (e.g. frog/fog) for a given term
     * and puts it on the blocking queue, to be 'consumed' by a consumer thread.<p/>
     * <b>Note:</b> the loop runs (len) tests.
     */
	private class DeletingOneLetter extends Thread {
		
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
    /**
     * DidYouMean's producer thread that adds missing letters (e.g. bat/boat) for a given term
     * based on the given alphabet and puts it on the blocking queue, to be 'consumed' by a consumer thread.<p/>
     * <b>Note:</b> the loop runs (alphabet.length * len) tests.
     */
	private class AddingOneLetter extends Thread {
		
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
    /**
     * DidYouMean's producer thread that reverses any two consecutive letters (e.g. two/tow) for a given term
     * and puts it on the blocking queue, to be 'consumed' by a consumer thread.<p/>
     * <b>Note:</b> the loop runs (len-1) tests.
     */
	private class ReversingTwoConsecutiveLetters extends Thread {
	
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
    /**
     * DidYouMean's consumer thread takes a String object (term) from the blocking queue
     * and checks if it is contained in YaCy's RWI index. The thread recognizes "\n" as poison pill!<p/>
     * <b>Note:</b> this causes no or moderate i/o as it uses the efficient index.has() method.
     */
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
    /**
     * wordSizeComparator is used by DidYouMean to order terms by index.count()<p/>
     * <b>Warning:</b> this causes heavy i/o
     */
    private class wordSizeComparator implements Comparator<String> {
		public int compare(final String o1, final String o2) {
    		final Integer i1 = index.count(Word.word2hash(o1));
    		final Integer i2 = index.count(Word.word2hash(o2));
    		return i2.compareTo(i1);
    	}    	
    }

}



