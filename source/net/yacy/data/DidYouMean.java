package net.yacy.data;

import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.sorting.ClusteredScoreMap;
import net.yacy.cora.sorting.ReversibleScoreMap;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.StringBuilderComparator;
import net.yacy.document.LibraryProvider;
import net.yacy.search.index.Segment;


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

    private static final int MinimumInputWordLength = 2;
    private static final int MinimumOutputWordLength = 4;

    private static final char[] ALPHABET_LATIN = {
        'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p',
		'q','r','s','t','u','v','w','x','y','z',
		'\u00df',
        '\u00e0','\u00e1','\u00e2','\u00e3','\u00e4','\u00e5','\u00e6','\u00e7',
        '\u00e8','\u00e9','\u00ea','\u00eb','\u00ec','\u00ed','\u00ee','\u00ef',
        '\u00f0','\u00f1','\u00f2','\u00f3','\u00f4','\u00f5','\u00f6',
        '\u00f8','\u00f9','\u00fa','\u00fb','\u00fc','\u00fd','\u00fe','\u00ff'};
    private static final char[] ALPHABET_KANJI = new char[512]; // \u3400-\u34ff + \u4e00-\u4eff
    private static final char[] ALPHABET_HIRAGANA = new char[96]; // \u3040-\u309F
    private static final char[] ALPHABET_KATAKANA = new char[96]; // \u30A0-\u30FF
    private static final char[] ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part1 = new char[5376]; // \u4E00-\u62FF
    private static final char[] ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part2 = new char[5376]; // \u6300-\u77FF
    private static final char[] ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part3 = new char[5376]; // \u7800-\u8CFF
    private static final char[] ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part4 = new char[4864]; // \u8D00-\u9FFF
    static {
        // this is very experimental: a very small subset of Kanji
        for (char a = '\u3400'; a <= '\u34ff'; a++) ALPHABET_KANJI[0xff & (a - '\u3400')] = a;
        for (char a = '\u4e00'; a <= '\u4eff'; a++) ALPHABET_KANJI[0xff & (a - '\u4e00') + 256] = a;
        for (char a = '\u3040'; a <= '\u309F'; a++) ALPHABET_HIRAGANA[0xff & (a - '\u3040')] = a;
        for (char a = '\u30A0'; a <= '\u30FF'; a++) ALPHABET_KATAKANA[0xff & (a - '\u30A0')] = a;
        for (char a = '\u4E00'; a <= '\u62FF'; a++) ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part1[0xff & (a - '\u4E00')] = a;
        for (char a = '\u6300'; a <= '\u77FF'; a++) ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part2[0xff & (a - '\u6300')] = a;
        for (char a = '\u7800'; a <= '\u8CFF'; a++) ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part3[0xff & (a - '\u7800')] = a;
        for (char a = '\u8D00'; a <= '\u9FFF'; a++) ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part4[0xff & (a - '\u8D00')] = a;
    }

    private static final char[][] ALPHABETS = {
        ALPHABET_LATIN, ALPHABET_KANJI, ALPHABET_HIRAGANA, ALPHABET_KATAKANA,
        ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part1, ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part2, ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part3, ALPHABET_CJK_UNIFIED_IDEOGRAPHS_Part4};
    private static final StringBuilder POISON_STRING = new StringBuilder("\n");
    public  static final int AVAILABLE_CPU = Runtime.getRuntime().availableProcessors();
    private static final wordLengthComparator WORD_LENGTH_COMPARATOR = new wordLengthComparator();

    private final Segment segment;
    private final StringBuilder word;
    private final int wordLen;
    private final LinkedBlockingQueue<StringBuilder> guessGen, guessLib;
    private long timeLimit;
    private boolean createGen; // keeps the value 'true' as long as no entry in guessLib is written
    private final SortedSet<StringBuilder> resultSet;
    private final indexSizeComparator INDEX_SIZE_COMPARATOR;
    private char[] alphabet;


    /**
     * @param index a termIndex - most likely retrieved from a switchboard object.
     * @param sort true/false -  sorts the resulting TreeSet by index.count(); <b>Warning:</b> this causes heavy i/o.
     */
    public DidYouMean(final Segment segment, final StringBuilder word0) {
        this.resultSet = Collections.synchronizedSortedSet(new TreeSet<StringBuilder>(new headMatchingComparator(word0, WORD_LENGTH_COMPARATOR)));
        this.word = word0;
        this.wordLen = this.word.length();
        this.segment = segment;
        this.guessGen = new LinkedBlockingQueue<StringBuilder>();
        this.guessLib = new LinkedBlockingQueue<StringBuilder>();
        this.createGen = true;
        this.INDEX_SIZE_COMPARATOR = new indexSizeComparator();

        // identify language
        if (this.word.length() > 0) {
            final char testchar = this.word.charAt(0);
            boolean alphafound = false;
            alphatest: for (final char[] alpha: ALPHABETS) {
                if (isAlphabet(alpha, testchar)) {
                    this.alphabet = new char[alpha.length];
                    System.arraycopy(alpha, 0, this.alphabet, 0, alpha.length);
                    alphafound = true;
                    break alphatest;
                }
            }
            if (!alphafound && testchar < 'A') {
                this.alphabet = new char[ALPHABET_LATIN.length];
                System.arraycopy(ALPHABET_LATIN, 0, this.alphabet, 0, ALPHABET_LATIN.length);
                alphafound = true;
            }
            if (!alphafound) {
                // generate generic alphabet using simply a character block of 256 characters
                final int firstchar = (0xff & (testchar / 256)) * 256;
                final int lastchar = firstchar + 255;
                this.alphabet = new char[256];
                // test this with /suggest.json?q=%EF%BD%84
                for (int a = firstchar; a <= lastchar; a++) {
                    this.alphabet[0xff & (a - firstchar)] = (char) a;
                }
            }
        }
    }

    private static final boolean isAlphabet(final char[] alpha, final char testchar) {
        for (final char a: alpha) {
            if (a == testchar) {
                return true;
            }
        }
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
    public SortedSet<StringBuilder> getSuggestions(final long timeout, final int preSortSelection) {
        if (this.word.length() < MinimumInputWordLength) {
            return this.resultSet; // return nothing if input is too short
        }
        final long startTime = System.currentTimeMillis();
        final long timelimit = startTime + timeout;
        if (StringBuilderComparator.CASE_INSENSITIVE_ORDER.indexOf(this.word, ' ') > 0) {
            return getSuggestions(StringBuilderComparator.CASE_INSENSITIVE_ORDER.split(this.word, ' '), timeout, preSortSelection, this.segment);
        }
        final SortedSet<StringBuilder> preSorted = getSuggestions(timeout);
        if (System.currentTimeMillis() > timelimit) {
            ConcurrentLog.info("DidYouMean", "found and returned " + preSorted.size() + " unsorted suggestions (1); execution time: "
                + (System.currentTimeMillis() - startTime) + "ms");
            return preSorted;
        }

        final ReversibleScoreMap<StringBuilder> scored = new ClusteredScoreMap<StringBuilder>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);
        try {
	        for (final StringBuilder s: preSorted) {
	            if (System.currentTimeMillis() > timelimit) {
	                break;
	            }
	            if (!(scored.sizeSmaller(2 * preSortSelection))) {
	                break;
	            }
	            scored.inc(s, this.segment.getWordCountGuess(s.toString()));
	        }
        } catch (final ConcurrentModificationException e) {
        }
        final SortedSet<StringBuilder> countSorted = Collections.synchronizedSortedSet(new TreeSet<StringBuilder>(new headMatchingComparator(this.word, this.INDEX_SIZE_COMPARATOR)));
        final int wc = this.segment.getWordCountGuess(this.word.toString()); // all counts must be greater than this
        while (!scored.isEmpty() && countSorted.size() < preSortSelection) {
            final StringBuilder s = scored.getMaxKey();
            final int score = scored.delete(s);
            if (s.length() >= MinimumOutputWordLength && score > wc) {
                countSorted.add(s);
            }
            if (System.currentTimeMillis() > timelimit) {
                break;
            }
        }

        // finished
        if (countSorted.isEmpty()) {
            ConcurrentLog.info("DidYouMean", "found and returned " + preSorted.size() + " unsorted suggestions (2); execution time: "
                    + (System.currentTimeMillis() - startTime) + "ms");
                return preSorted;
        }
        ConcurrentLog.info("DidYouMean", "found " + preSorted.size() + " unsorted terms, returned " + countSorted.size() + " sorted suggestions; execution time: "
                        + (System.currentTimeMillis() - startTime) + "ms");

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
    private static SortedSet<StringBuilder> getSuggestions(final StringBuilder[] words, final long timeout, final int preSortSelection, final Segment segment) {
        final SortedSet<StringBuilder>[] s = new SortedSet[words.length];
        for (int i = 0; i < words.length; i++) {
            s[i] = new DidYouMean(segment, words[i]).getSuggestions(timeout / words.length, preSortSelection);
        }
        // make all permutations
        final SortedSet<StringBuilder> result = new TreeSet<StringBuilder>(StringBuilderComparator.CASE_INSENSITIVE_ORDER);
        StringBuilder sb;
        for (int i = 0; i < words.length; i++) {
            if (s[i].isEmpty()) {
                continue;
            }
            sb = new StringBuilder(20);
            for (int j = 0; j < words.length; j++) {
                if (j > 0) {
                    sb.append(' ');
                }
                if (i == j) {
                    sb.append(s[j].first());
                } else {
                    sb.append(words[j]);
                }
            }
            result.add(sb);
        }
        return result;
    }

    /**
     * This method triggers the producer and consumer threads of the DidYouMean object.
     * @param word a String with a single word
     * @param timeout execution time in ms.
     * @return a Set&lt;String&gt; with word variations contained in term index.
     */
    private SortedSet<StringBuilder> getSuggestions(final long timeout) {
        final long startTime = System.currentTimeMillis();
        this.timeLimit = startTime + timeout;

        // create one consumer thread that checks the guessLib queue
        // for occurrences in the index. If the producers are started next, their
        // results can be consumers directly
        final Consumer[] consumers = new Consumer[AVAILABLE_CPU];
        consumers[0] = new Consumer();
        consumers[0].start();

        // get a single recommendation for the word without altering the word
        final Set<StringBuilder> libr = LibraryProvider.dymLib.recommend(this.word);
        for (final StringBuilder t: libr) {
            if (!t.equals(this.word)) {
                try {
                    this.createGen = false;
                    this.guessLib.put(t);
                } catch (final InterruptedException e) {}
            }
        }

        // create and start producers
        // the CPU load to create the guessed words is very low, but the testing
        // against the library may be CPU intensive. Since it is possible to test
        // words in the library concurrently, it is a good idea to start separate threads
        final Thread[] producers = new Thread[4];
        producers[0] = new ChangingOneLetter();
        producers[1] = new AddingOneLetter();
        producers[2] = new DeletingOneLetter();
        producers[3] = new ReversingTwoConsecutiveLetters();
        for (final Thread t: producers) {
            t.start();
        }

        // start more consumers if there are more cores
        if (consumers.length > 1) {
            for (int i = 1; i < consumers.length; i++) {
                consumers[i] = new Consumer();
                consumers[i].start();
            }
        }

        // now decide which kind of guess is better
        // we take guessLib entries as long as there is any entry in it
        // to see if this is the case, we must wait for termination of the producer
        for (final Thread t: producers) {
            if (this.timeLimit > System.currentTimeMillis()) try {
                t.join(Math.max(0, this.timeLimit - System.currentTimeMillis()));
            } catch (final InterruptedException e) {}
        }

        // if there is not any entry in guessLib, then transfer all entries from the
        // guessGen to guessLib
        if (this.createGen) {
            try {
                this.guessGen.put(POISON_STRING);
                StringBuilder s;
                while (!(s = this.guessGen.take()).equals(POISON_STRING)) {
                    this.guessLib.put(s);
                }
            } catch (final InterruptedException e) {}
        }

        // put poison into guessLib to terminate consumers
        for (@SuppressWarnings("unused") final Consumer c: consumers) {
            try { this.guessLib.put(POISON_STRING); } catch (final InterruptedException e) {}
        }

        // wait for termination of consumer
        for (final Consumer c: consumers) {
            if (this.timeLimit > System.currentTimeMillis()) try {
                c.join(Math.max(0, this.timeLimit - System.currentTimeMillis()));
            } catch (final InterruptedException e) {}
        }

        // we don't want the given word in the result
        this.resultSet.remove(this.word);

        return this.resultSet;

    }

    private void test(final StringBuilder s) throws InterruptedException {
        final Set<StringBuilder> libr = LibraryProvider.dymLib.recommend(s);
        libr.addAll(LibraryProvider.geoLoc.recommend(s));
        if (!libr.isEmpty()) {
            this.createGen = false;
        }
        for (final StringBuilder t: libr) {
            this.guessLib.put(t);
        }
        if (this.createGen) {
            this.guessGen.put(s);
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
            char m;
            for (int i = 0; i < DidYouMean.this.wordLen; i++) {
                try {
                    m = DidYouMean.this.word.charAt(i);
                    for (final char c: DidYouMean.this.alphabet) {
                        if (m != c) {
                            final StringBuilder ts = new StringBuilder(DidYouMean.this.word.length() + 1).append(DidYouMean.this.word.substring(0, i)).append(c).append(DidYouMean.this.word.substring(i + 1));
                            test(ts);
                        }
                        if (System.currentTimeMillis() > DidYouMean.this.timeLimit) {
                            return;
                        }
                    }
                } catch (final InterruptedException e) {}
            }
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
                for (int i = 0; i < DidYouMean.this.wordLen; i++) {
                    try {
                        final StringBuilder ts = new StringBuilder(DidYouMean.this.word.length() + 1).append(DidYouMean.this.word.substring(0, i)).append(DidYouMean.this.word.substring(i + 1));
                        test(ts);
                        if (System.currentTimeMillis() > DidYouMean.this.timeLimit) {
                            return;
                        }
                    } catch (final InterruptedException e) {}
                }
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
                for (int i = 0; i <= DidYouMean.this.wordLen; i++) {
                    try {
                        for (final char c: DidYouMean.this.alphabet) {
                            final StringBuilder ts = new StringBuilder(DidYouMean.this.word.length() + 1).append(DidYouMean.this.word.substring(0, i)).append(c).append(DidYouMean.this.word.substring(i));
                            test(ts);
                            if (System.currentTimeMillis() > DidYouMean.this.timeLimit) {
                                return;
                            }
                        }
                    } catch (final InterruptedException e) {}
                }
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
                for (int i = 0; i < DidYouMean.this.wordLen - 1; i++) {
                    try {
                        final StringBuilder ts = new StringBuilder(DidYouMean.this.word.length() + 1).append(DidYouMean.this.word.substring(0, i)).append(DidYouMean.this.word.charAt(i + 1)).append(DidYouMean.this.word.charAt(i)).append(DidYouMean.this.word.substring(i + 2));
                        test(ts);
                        if (System.currentTimeMillis() > DidYouMean.this.timeLimit) {
                            return;
                        }
                    } catch (final InterruptedException e) {}
                }
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
                StringBuilder s;
                try {
                    while ((s = DidYouMean.this.guessLib.take()) != POISON_STRING) {
                        if (s.length() >= MinimumOutputWordLength && DidYouMean.this.segment.getWordCountGuess(s.toString()) > 2) {
                            DidYouMean.this.resultSet.add(s);
                        }
                        if (System.currentTimeMillis() > DidYouMean.this.timeLimit) {
                            return;
                        }
                    }
                } catch (final InterruptedException e) {}
            }
	}

    /**
     * indexSizeComparator is used by DidYouMean to order terms by index.count()
     * <b>Warning:</b> this causes heavy i/o
     */
    private class indexSizeComparator implements Comparator<StringBuilder> {

        @Override
        public int compare(final StringBuilder o1, final StringBuilder o2) {
            final int i1 = DidYouMean.this.segment.getWordCountGuess(o1.toString());
            final int i2 = DidYouMean.this.segment.getWordCountGuess(o2.toString());
            if (i1 == i2) {
                return WORD_LENGTH_COMPARATOR.compare(o1, o2);
            }
            return (i1 < i2) ? 1 : -1; // '<' is correct, because the largest count shall be ordered to be the first position in the result
        }
    }

    /**
     * wordLengthComparator is used by DidYouMean to order terms by the term length
     * This is the default order if the indexSizeComparator is not used
     */
    private static class wordLengthComparator implements Comparator<StringBuilder> {

        @Override
        public int compare(final StringBuilder o1, final StringBuilder o2) {
            final int i1 = o1.length();
            final int i2 = o2.length();
            if (i1 == i2) {
                return StringBuilderComparator.CASE_INSENSITIVE_ORDER.compare(o1, o2);
            }
            return (i1 < i2) ? 1 : -1; // '<' is correct, because the longest word shall be first
        }

    }

    /**
     * headMatchingComparator is used to sort results in such a way that words that match with the given words are sorted first
     */
    private static class headMatchingComparator implements Comparator<StringBuilder> {
        private final StringBuilder head;
        private final Comparator<StringBuilder> secondaryComparator;
        public headMatchingComparator(final StringBuilder head, final Comparator<StringBuilder> secondaryComparator) {
            this.head = head;
            this.secondaryComparator = secondaryComparator;
        }

        @Override
        public int compare(final StringBuilder o1, final StringBuilder o2) {
            final boolean o1m = StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(o1, this.head);
            final boolean o2m = StringBuilderComparator.CASE_INSENSITIVE_ORDER.startsWith(o2, this.head);
            if ((o1m && o2m) || (!o1m && !o2m)) {
                return this.secondaryComparator.compare(o1, o2);
            }
            return o1m ? -1 : 1;
        }
    }

}



