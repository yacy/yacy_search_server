package de.anomic.data;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.kelondro.data.word.Word;
import net.yacy.kelondro.data.word.WordReference;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.rwi.IndexCell;


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
 * @author orbiter (extensions for multi-language support)
 */
public class DidYouMean {

    private static final char[] ALPHABET_LATIN = {
        'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p',
		'q','r','s','t','u','v','w','x','y','z',
		'\u00df',
        '\u00e0','\u00e1','\u00e2','\u00e3','\u00e4','\u00e5','\u00e6','\u00e7',
        '\u00e8','\u00e9','\u00ea','\u00eb','\u00ec','\u00ed','\u00ee','\u00ef',
        '\u00f0','\u00f1','\u00f2','\u00f3','\u00f4','\u00f5','\u00f6',
        '\u00f8','\u00f9','\u00fa','\u00fb','\u00fc','\u00fd','\u00fe','\u00ff'};
    private static final char[] ALPHABET_KANJI = new char[512];
    static {
        // this is very experimental: a very small subset of Kanji
        for (char a = '\u3400'; a <= '\u34ff'; a++) ALPHABET_KANJI[0xff & (a - '\u3400')] = a;
        for (char a = '\u4e00'; a <= '\u4eff'; a++) ALPHABET_KANJI[0xff & (a - '\u4e00') + 256] = a;
    }
    private static final char[][] ALPHABETS = {ALPHABET_LATIN, ALPHABET_KANJI};
    
    private   static final String POISON_STRING = "\n";
    public    static final int AVAILABLE_CPU = Runtime.getRuntime().availableProcessors();
    private static final wordLengthComparator WORD_LENGTH_COMPARATOR = new wordLengthComparator();
	
    private final IndexCell<WordReference> index;
    private       char[] alphabet;
    private final String word;
    private final int wordLen;
    private final LinkedBlockingQueue<String> guessGen, guessLib;
    private long timeLimit;
    private boolean createGen; // keeps the value 'true' as long as no entry in guessLib is written
    private final SortedSet<String> resultSet;
    
	
    /**
     * @param index a termIndex - most likely retrieved from a switchboard object.
     * @param sort true/false -  sorts the resulting TreeSet by index.count(); <b>Warning:</b> this causes heavy i/o.
     */
    public DidYouMean(final IndexCell<WordReference> index, String word0) {
        this.resultSet = Collections.synchronizedSortedSet(new TreeSet<String>(WORD_LENGTH_COMPARATOR));
        this.word = word0.toLowerCase();
        this.wordLen = word.length();
        this.index = index;
        this.guessGen = new LinkedBlockingQueue<String>();
        this.guessLib = new LinkedBlockingQueue<String>();
        this.createGen = true;
        
        // identify language
        if (this.word.length() == 0) {
            this.alphabet = ALPHABET_LATIN;
        } else {
            char testchar = this.word.charAt(0);
            this.alphabet = null;
            alphatest: for (char[] alpha: ALPHABETS) {
                if (isAlphabet(alpha, testchar)) {
                    this.alphabet = alpha;
                    break alphatest;
                }
            }
            if (this.alphabet == null) {
                // generate generic alphabet using simply a character block of 256 characters
                char firstchar = (char) ((0xff & (testchar / 256)) * 256);
                char lastchar = (char) (firstchar + 255);
                this.alphabet = new char[256];
                for (char a = firstchar; a <= lastchar; a++) {
                    this.alphabet[0xff & (a - firstchar)] = a;
                }
            }
        }
    }
	
    private static final boolean isAlphabet(char[] alpha, char testchar) {
        for (char a: alpha) if (a == testchar) return true;
        return false;
    }
    
    public void reset() {
        this.resultSet.clear();
        this.guessGen.clear();
        this.guessLib.clear();
    }
	
    /**
     * get suggestions for a given word. The result is first ordered using a term size ordering,
     * and a subset of the result is sorted again with a IO-intensive order based on the index size
     * @param word0
     * @param timeout
     * @param preSortSelection the number of words that participate in the IO-intensive sort
     * @return
     */
    public SortedSet<String> getSuggestions(long timeout, int preSortSelection) {
        if (this.word.indexOf(' ') > 0) return getSuggestions(this.word.split(" "), timeout, preSortSelection, this.index);
        long timelimit = System.currentTimeMillis() + timeout;
        SortedSet<String> preSorted = getSuggestions(timeout);
        if (System.currentTimeMillis() > timelimit) return preSorted;
        SortedSet<String> countSorted = Collections.synchronizedSortedSet(new TreeSet<String>(new indexSizeComparator()));
        int wc = index.count(Word.word2hash(this.word)); // all counts must be greater than this
        int c0;
        for (final String s: preSorted) {
            if (System.currentTimeMillis() > timelimit) break;
            if (preSortSelection <= 0) break;
            c0 = index.count(Word.word2hash(s));
            if (c0 > wc) countSorted.add(s);
            preSortSelection--;
        }
        return countSorted;
    }
	
    /**
     * return a string that is a suggestion list for the list of given words
     * @param words
     * @param timeout
     * @param preSortSelection
     * @return
     */
    @SuppressWarnings("unchecked")
    private static SortedSet<String> getSuggestions(final String[] words, long timeout, int preSortSelection, final IndexCell<WordReference> index) {
        final SortedSet<String>[] s = new SortedSet[words.length];
        for (int i = 0; i < words.length; i++) {
            s[i] = new DidYouMean(index, words[i]).getSuggestions(timeout / words.length, preSortSelection);
        }
        // make all permutations
        final SortedSet<String> result = new TreeSet<String>();
        StringBuilder sb;
        for (int i = 0; i < words.length; i++) {
            if (s[i].isEmpty()) continue;
            sb = new StringBuilder(20);
            for (int j = 0; j < words.length; j++) {
                if (j > 0) sb.append(' ');
                if (i == j) sb.append(s[j].first()); else sb.append(words[j]);
            }
            result.add(sb.toString());
        }
        return result;
    }
    
    /**
     * This method triggers the producer and consumer threads of the DidYouMean object.
     * @param word a String with a single word
     * @param timeout execution time in ms.
     * @return a Set&lt;String&gt; with word variations contained in term index.
     */
    private SortedSet<String> getSuggestions(long timeout) {
        long startTime = System.currentTimeMillis();
        this.timeLimit = startTime + timeout;
        
        // create one consumer thread that checks the guessLib queue
        // for occurrences in the index. If the producers are started next, their
        // results can be consumers directly
        Consumer[] consumers = new Consumer[AVAILABLE_CPU];
        consumers[0] = new Consumer();
        consumers[0].start();

        // get a single recommendation for the word without altering the word
        Set<String> libr = LibraryProvider.dymLib.recommend(this.word);
        for (final String t: libr) {
            if (!t.equals(this.word)) try {
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
        for (final Thread t: producers) t.start();
	    
        // start more consumers if there are more cores
        if (consumers.length > 1) for (int i = 1; i < consumers.length; i++) {
            consumers[i] = new Consumer();
            consumers[i].start();
        }
        
        // now decide which kind of guess is better
        // we take guessLib entries as long as there is any entry in it
        // to see if this is the case, we must wait for termination of the producer
        for (final Thread t: producers) try { t.join(); } catch (InterruptedException e) {}
        
        // if there is not any entry in guessLib, then transfer all entries from the
        // guessGen to guessLib
        if (createGen) try {
            this.guessGen.put(POISON_STRING);
            String s;
            while (!(s = this.guessGen.take()).equals(POISON_STRING)) this.guessLib.put(s);
        } catch (InterruptedException e) {}
        
        // put poison into guessLib to terminate consumers
        for (@SuppressWarnings("unused") final Consumer c: consumers)
            try { guessLib.put(POISON_STRING); } catch (InterruptedException e) {}
        
        // wait for termination of consumer
        for (final Consumer c: consumers)
            try { c.join(); } catch (InterruptedException e) {}
	    
        // we don't want the given word in the result
        this.resultSet.remove(this.word);

        // finished
        Log.logInfo("DidYouMean", "found "+this.resultSet.size()+" terms; execution time: "
                        +(System.currentTimeMillis()-startTime)+"ms"+ " - remaining queue size: "+guessLib.size());

        return this.resultSet;
			
    }
	
    private void test(final String s) throws InterruptedException {
        Set<String> libr = LibraryProvider.dymLib.recommend(s);
        libr.addAll(LibraryProvider.geoLoc.recommend(s));
        if (!libr.isEmpty()) createGen = false;
        for (final String t: libr) {
            guessLib.put(t);
        }
        if (createGen) {
            guessGen.put(s);
        }
    }

    /**
     * DidYouMean's producer thread that changes one letter (e.g. bat/cat) for a given term
     * based on the given alphabet and puts it on the blocking queue, to be 'consumed' by a consumer thread.<p/>
     * <b>Note:</b> the loop runs (alphabet.length * len) tests.
     */
    public class ChangingOneLetter extends Thread {
		
        @Override
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
	private class DeletingOneLetter extends Thread {
		
            @Override
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
	private class AddingOneLetter extends Thread {
		
            @Override
            public void run() {
                for (int i = 0; i <= wordLen; i++) try {
                    for (final char c: alphabet) {
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
	private class ReversingTwoConsecutiveLetters extends Thread {
	
            @Override
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
	private class Consumer extends Thread {

            @Override
            public void run() {
                String s;
                try {
                    while (!(s = guessLib.take()).equals(POISON_STRING)) {
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
    private class indexSizeComparator implements Comparator<String> {

        public int compare(final String o1, final String o2) {
            final int i1 = index.count(Word.word2hash(o1));
            final int i2 = index.count(Word.word2hash(o2));
            if (i1 == i2) return WORD_LENGTH_COMPARATOR.compare(o1, o2);
            return (i1 < i2) ? 1 : -1; // '<' is correct, because the largest count shall be ordered to be the first position in the result
    	}    	
    }
    
    /**
     * wordLengthComparator is used by DidYouMean to order terms by the term length<p/>
     * This is the default order if the indexSizeComparator is not used
     */
    private static class wordLengthComparator implements Comparator<String> {

        public int compare(final String o1, final String o2) {
            final int i1 = o1.length();
            final int i2 = o2.length();
            if (i1 == i2) return o1.compareTo(o2);
            return (i1 > i2) ? 1 : -1; // '>' is correct, because the shortest word shall be first
        }
        
    }

}



