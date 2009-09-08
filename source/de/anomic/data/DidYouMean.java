package de.anomic.data;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;

import de.anomic.document.Word;
import de.anomic.kelondro.text.IndexCell;
import de.anomic.kelondro.text.referencePrototype.WordReference;
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

    protected static final char[] alphabet = {
        'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p',
		'q','r','s','t','u','v','w','x','y','z','\u00e4','\u00f6','\u00fc','\u00df'}; 
	private   static final String poisonString = "\n";
	public    static final int availableCPU = Runtime.getRuntime().availableProcessors();
	protected static final wordLengthComparator wlComp = new wordLengthComparator();
	
	protected final IndexCell<WordReference> index;
    protected String word;
	protected int wordLen;
	protected LinkedBlockingQueue<String> guessGen, guessLib;
	protected long timeLimit;
	protected boolean createGen; // keeps the value 'true' as long as no entry in guessLib is written
	protected final Set<String> resultSet;
    
	
	/**
	 * @param index a termIndex - most likely retrieved from a switchboard object.
	 * @param sort true/false -  sorts the resulting TreeSet by index.count(); <b>Warning:</b> this causes heavy i/o.
	 */
	public DidYouMean(final IndexCell<WordReference> index) {
		this.resultSet = Collections.synchronizedSortedSet(new TreeSet<String>(wlComp));
		this.word = "";
		this.wordLen = 0;
		this.index = index;
		this.guessGen = new LinkedBlockingQueue<String>();
		this.guessLib = new LinkedBlockingQueue<String>();
		this.createGen = true;
	}
	
	/**
	 * get a single suggestion
	 * @param word
	 * @param timeout
	 * @return
	 */
	public String getSuggestion(final String word, long timeout) {
	    Set<String> s = getSuggestions(word, timeout);
	    if (s == null || s.size() == 0) return null;
	    return s.iterator().next();
	}
	
	/**
     * get a single suggestion with additional sort
     * @param word
     * @param timeout
     * @return
     */
    public String getSuggestion(final String word, long timeout, int preSortSelection) {
        Set<String> s = getSuggestions(word, timeout, preSortSelection);
        if (s == null || s.size() == 0) return null;
        return s.iterator().next();
    }
	
	/**
	 * get suggestions for a given word. The result is first ordered using a term size ordering,
	 * and a subset of the result is sorted again with a IO-intensive order based on the index size
	 * @param word
	 * @param timeout
	 * @param preSortSelection the number of words that participate in the IO-intensive sort
	 * @return
	 */
	public Set<String> getSuggestions(final String word, long timeout, int preSortSelection) {
	    long startTime = System.currentTimeMillis();
	    Set<String> preSorted = getSuggestions(word, timeout);
	    long timelimit = 2 * System.currentTimeMillis() - startTime + timeout;
        if (System.currentTimeMillis() > timelimit) return preSorted;
        Set<String> countSorted = Collections.synchronizedSortedSet(new TreeSet<String>(new indexSizeComparator()));
        for (String s: preSorted) {
	        if (System.currentTimeMillis() > timelimit) break;
	        if (preSortSelection <= 0) break;
	        countSorted.add(s);
	        preSortSelection--;
	    }
	    return countSorted;
	}
	
	/**
	 * This method triggers the producer and consumer threads of the DidYouMean object.
	 * @param word a String with a single word
	 * @param timeout execution time in ms.
	 * @return a Set&lt;String&gt; with word variations contained in term index.
	 */
	public Set<String> getSuggestions(final String word, long timeout) {
		long startTime = System.currentTimeMillis();
		this.timeLimit = startTime + timeout;
		this.word = word.toLowerCase();
		this.wordLen = word.length();
		
		// create one consumer thread that checks the guessLib queue
		// for occurrences in the index. If the producers are started next, their
		// results can be consumers directly
        Consumer[] consumers = new Consumer[availableCPU];
        consumers[0] = new Consumer();
        consumers[0].start();
        
        // get a single recommendation for the word without altering the word
        Set<String> libr = LibraryProvider.dymLib.recommend(word);
        for (String t: libr) {
            if (!t.equals(word)) try {
                createGen = false;
                guessLib.put(t);
            } catch (InterruptedException e) {}
        }
        
	    // create and start producers
        // the CPU load to create the guessed words is very low, but the testing
        // against the library may be CPU intensive. Since it is possible to test
        // words in the library concurrently, it is a good idea to start separate threads
		Thread[] producers = new Thread[4];
		producers[0] = new ChangingOneLetter();
		producers[1] = new AddingOneLetter();
        producers[2] = new DeletingOneLetter();
        producers[3] = new ReversingTwoConsecutiveLetters();
        for (Thread t: producers) t.start();
	    
        // start more consumers if there are more cores
        if (consumers.length > 1) for (int i = 1; i < consumers.length; i++) {
            consumers[i] = new Consumer();
            consumers[i].start();
        }
        
        // now decide which kind of guess is better
        // we take guessLib entries as long as there is any entry in it
        // to see if this is the case, we must wait for termination of the producer
        for (Thread t: producers) try { t.join(); } catch (InterruptedException e) {}
        
        // if there is not any entry in guessLib, then transfer all entries from the
        // guessGen to guessLib
        if (createGen) try {
            this.guessGen.put(poisonString);
            String s;
            while ((s = this.guessGen.take()) != poisonString) this.guessLib.put(s);
        } catch (InterruptedException e) {}
        
        // put poison into guessLib to terminate consumers
        for (@SuppressWarnings("unused") Consumer c: consumers)
            try { guessLib.put(poisonString); } catch (InterruptedException e) {}
        
        // wait for termination of consumer
	    for (Consumer c: consumers)
	        try { c.join(); } catch (InterruptedException e) {}
	    
	    // we don't want the given word in the result
		this.resultSet.remove(word.toLowerCase());
		
		// finished
		Log.logInfo("DidYouMean", "found "+this.resultSet.size()+" terms; execution time: "
				+(System.currentTimeMillis()-startTime)+"ms"+ " - remaining queue size: "+guessLib.size());
		
		return this.resultSet;
			
	}
	
	public void test(String s) throws InterruptedException {
		Set<String> libr = LibraryProvider.dymLib.recommend(s);
		libr.addAll(LibraryProvider.geoDB.recommend(s));
		if (libr.size() != 0) createGen = false;
		for (String t: libr) guessLib.put(t);
		if (createGen) guessGen.put(s);
	}

	/**
     * DidYouMean's producer thread that changes one letter (e.g. bat/cat) for a given term
     * based on the given alphabet and puts it on the blocking queue, to be 'consumed' by a consumer thread.<p/>
     * <b>Note:</b> the loop runs (alphabet.length * len) tests.
     */	
	public class ChangingOneLetter extends Thread {
		
		public void run() {
			for (int i = 0; i < wordLen; i++) try {
				for (char c: alphabet) {
					test(word.substring(0, i) + c + word.substring(i + 1));
					if (System.currentTimeMillis() > timeLimit) return;
				}
			} catch (InterruptedException e) {}
		}
	}
	
    /**
     * DidYouMean's producer thread that deletes extra letters (e.g. frog/fog) for a given term
     * and puts it on the blocking queue, to be 'consumed' by a consumer thread.<p/>
     * <b>Note:</b> the loop runs (len) tests.
     */
	protected class DeletingOneLetter extends Thread {
		
		public void run() {
			for (int i = 0; i < wordLen; i++) try {
				test(word.substring(0, i) + word.substring(i+1));
                if (System.currentTimeMillis() > timeLimit) return;
			} catch (InterruptedException e) {}
		}
	}
	
    /**
     * DidYouMean's producer thread that adds missing letters (e.g. bat/boat) for a given term
     * based on the given alphabet and puts it on the blocking queue, to be 'consumed' by a consumer thread.<p/>
     * <b>Note:</b> the loop runs (alphabet.length * len) tests.
     */
	protected class AddingOneLetter extends Thread {
		
		public void run() {
			for (int i = 0; i <= wordLen; i++) try {
				for (char c: alphabet) {
					test(word.substring(0, i) + c + word.substring(i));
                    if (System.currentTimeMillis() > timeLimit) return;
				}			
			} catch (InterruptedException e) {}
		}
	}
	
    /**
     * DidYouMean's producer thread that reverses any two consecutive letters (e.g. two/tow) for a given term
     * and puts it on the blocking queue, to be 'consumed' by a consumer thread.<p/>
     * <b>Note:</b> the loop runs (len-1) tests.
     */
	protected class ReversingTwoConsecutiveLetters extends Thread {
	
		public void run() {
			for (int i = 0; i < wordLen - 1; i++) try {
				test(word.substring(0, i) + word.charAt(i + 1) + word.charAt(i) + word.substring(i +2));
                if (System.currentTimeMillis() > timeLimit) return;
			} catch (InterruptedException e) {}
		}
	}
	
    /**
     * DidYouMean's consumer thread takes a String object (term) from the blocking queue
     * and checks if it is contained in YaCy's RWI index.
     * <b>Note:</b> this causes no or moderate i/o as it uses the efficient index.has() method.
     */
	class Consumer extends Thread {

		public void run() {
		    String s;
		    try {
		        while ((s = guessLib.take()) != poisonString) {
		            if (index.has(Word.word2hash(s))) resultSet.add(s);
                    if (System.currentTimeMillis() > timeLimit) return;
		        }
		    } catch (InterruptedException e) {}
		}
	}
	
    /**
     * indexSizeComparator is used by DidYouMean to order terms by index.count()<p/>
     * <b>Warning:</b> this causes heavy i/o
     */
    protected class indexSizeComparator implements Comparator<String> {
		public int compare(final String o1, final String o2) {
    		final int i1 = index.count(Word.word2hash(o1));
    		final int i2 = index.count(Word.word2hash(o2));
    		if (i1 == i2) return wlComp.compare(o1, o2);
    		return (i1 < i2) ? 1 : -1; // '<' is correct, because the largest count shall be ordered to be the first position in the result
    	}    	
    }
    
    /**
     * wordLengthComparator is used by DidYouMean to order terms by the term length<p/>
     * This is the default order if the indexSizeComparator is not used
     */
    protected static class wordLengthComparator implements Comparator<String> {
        public int compare(final String o1, final String o2) {
            final int i1 = o1.length();
            final int i2 = o2.length();
            if (i1 == i2) return o1.compareTo(o2);
            return (i1 > i2) ? 1 : -1; // '>' is correct, because the shortest word shall be first
        }       
    }

}



