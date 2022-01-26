// WordReferenceVars.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.11.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.data.word;

import java.util.Collection;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.cora.date.MicroDate;
import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.util.ByteArray;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.rwi.AbstractReference;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.rwi.ReferenceContainer;
import net.yacy.kelondro.util.Bitfield;
import net.yacy.kelondro.workflow.WorkflowProcessor;


public class WordReferenceVars extends AbstractReference implements WordReference, Reference, Cloneable, Comparable<WordReferenceVars>, Comparator<WordReferenceVars> {

    /**
     * object for termination of concurrent blocking queue processing
     */
    public static final WordReferenceVars poison = new WordReferenceVars();
    protected static final byte[] default_language = UTF8.getBytes("en");

    private final Bitfield flags;
    private long lastModified;
    private final String language;
    public final byte[] urlHash;
    private String hostHash = null;
    private final char type;
    private int hitcount, // how often appears this word in the text
            llocal, lother, phrasesintext,
            posintext, // word position in text
            posinphrase, posofphrase,
            urlcomps, urllength,
            wordsintext, wordsintitle;
    
    /** Stored average words distance, when it can not be processed from positions because created from a WordReferenceRow instance */
    private int distance;
    private int virtualAge;
    private Queue<Integer> positions; // word positons of joined references
    private double termFrequency;
    private final boolean local;

    public WordReferenceVars(
            final byte[]   urlHash,
            final int      urlLength,     // byte-length of complete URL
            final int      urlComps,      // number of path components
            final int      titleLength,   // length of description/length (longer are better?)
            final int      hitcount,      // how often appears this word in the text
            final int      wordcount,     // total number of words
            final int      phrasecount,   // total number of phrases
            final int      posintext,     // first position of word in text
            final Queue<Integer> ps,      // positions of words that are joined into the reference
            final int      posinphrase,   // position of word in its phrase
            final int      posofphrase,   // number of the phrase where word appears
            final long     lastmodified,  // last-modified time of the document where word appears
                  String   language,      // (guessed) language of document
            final char     doctype,       // type of document
            final int      outlinksSame,  // outlinks to same domain
            final int      outlinksOther, // outlinks to other domain
            final Bitfield flags,  // attributes to the url and to the word according the url
            final double   termfrequency
    ) {
        //final int mddct = MicroDate.microDateDays(updatetime);
        this.flags = flags;
        //this.freshUntil = Math.max(0, mddlm + (mddct - mddlm) * 2);
        this.lastModified = lastmodified;
        this.language = language;
        this.urlHash = urlHash;
        this.type = doctype;
        this.hitcount = hitcount;
        this.llocal = outlinksSame;
        this.lother = outlinksOther;
        this.phrasesintext = phrasecount;
        
        if (ps != null && !ps.isEmpty()) {
            this.positions = new LinkedBlockingQueue<Integer>();
            for (final Integer i : ps) this.positions.add(i);
        } else {
            this.positions = null;
        }
        this.distance = 0; // stored distance value is set to zero here because it has to be calculated from positions
        this.posinphrase = posinphrase;
        this.posintext = posintext;
        this.posofphrase = posofphrase;
        this.urlcomps = urlComps;
        this.urllength = urlLength;
        this.virtualAge = -1; // compute that later
        this.wordsintext = wordcount;
        this.wordsintitle = titleLength;
        this.termFrequency = termfrequency;
        this.local = true;
    }

    public WordReferenceVars(final WordReference e, boolean local) {
        this.flags = e.flags();
        //this.freshUntil = e.freshUntil();
        this.lastModified = e.lastModified();
        this.language = ASCII.String(e.getLanguage());
        this.urlHash = e.urlhash();
        this.type = e.getType();
        this.hitcount = e.hitcount();
        this.llocal = e.llocal();
        this.lother = e.lother();
        this.phrasesintext = e.phrasesintext();
        
        if (e.positions() != null && !e.positions().isEmpty()) {
            this.positions = new LinkedBlockingQueue<Integer>();
            for (final Integer i: e.positions()) this.positions.add(i);
        } else {
            this.positions = null;
        }
        this.distance = e.distance();
        this.posinphrase = e.posinphrase();
        this.posintext = e.posintext();
        this.posofphrase = e.posofphrase();
        this.urlcomps = e.urlcomps();
        this.urllength = e.urllength();
        this.virtualAge = e.virtualAge();
        this.wordsintext = e.wordsintext();
        this.wordsintitle = e.wordsintitle();
        this.termFrequency = e.termFrequency();
        this.local = local;
    }

    /**
     * initializer for special poison object
     */
    private WordReferenceVars() {
        this.flags = null;
        this.lastModified = 0;
        this.language = null;
        this.urlHash = null;
        this.type = ' ';
        this.hitcount = 0;
        this.llocal = 0;
        this.lother = 0;
        this.phrasesintext = 0;
        this.positions = null;
        this.distance = 0;
        this.posinphrase = 0;
        this.posintext = 0;
        this.posofphrase = 0;
        this.urlcomps = 0;
        this.urllength = 0;
        this.virtualAge = 0;
        this.wordsintext = 0;
        this.wordsintitle = 0;
        this.termFrequency = 0.0;
        this.local = true;
    }

    @Override
    public WordReferenceVars clone() {
        final WordReferenceVars c = new WordReferenceVars(
                this.urlHash,
                this.urllength,
                this.urlcomps,
                this.wordsintitle,
                this.hitcount,
                this.wordsintext,
                this.phrasesintext,
                this.posintext,
                this.positions,
                this.posinphrase,
                this.posofphrase,
                this.lastModified,
                this.language,
                this.type,
                this.llocal,
                this.lother,
                this.flags,
                this.termFrequency);
        return c;
    }

    @Override
    public Bitfield flags() {
        return this.flags;
    }

    @Override
    public byte[] getLanguage() {
        return ASCII.getBytes(this.language);
    }
    
    /**
     * @return the ISO 639 language code of the reference
     */
    public String getLanguageString() {
    	return this.language;
    }

    @Override
    public char getType() {
        return this.type;
    }

    /**
     * How often appears this word in the text
     * @return
     */
    @Override
    public int hitcount() {
        return this.hitcount;
    }

    @Override
    public long lastModified() {
        return this.lastModified;
    }

    @Override
    public int llocal() {
        return this.llocal;
    }

    @Override
    public int lother() {
        return this.lother;
    }

    @Override
    public int phrasesintext() {
        return this.phrasesintext;
    }

    @Override
    public int posinphrase() {
        return this.posinphrase;
    }

    /**
     * First word position in text.
     * @return min position
     */
    @Override
    public int posintext() {
        return this.posintext;
    }

    /**
     * Word positions for joined references (for multi word queries).
     * @see #posintext()
     * @return the word positions of the joined references
     */
    @Override
    public Collection<Integer> positions() {
        return this.positions;
    }
    
    @Override
    public int distance() {
    	int value =  super.distance();
    	if(value == 0) {
    		/* Calcualtion from positions returned 0 : let's try with the stored value */
    		value = this.distance;
    	}
    	return value;
    }

    @Override
    public int posofphrase() {
        return this.posofphrase;
    }

    private WordReferenceRow toRowEntry() {
        return new WordReferenceRow(
                this.urlHash,
                this.urllength,     // byte-length of complete URL
                this.urlcomps,      // number of path components
                this.wordsintitle,  // length of description/length (longer are better?)
                this.hitcount,      // how often appears this word in the text
                this.wordsintext,   // total number of words
                this.phrasesintext, // total number of phrases
                this.posintext,     // position of word in all words (WordReferenceRow stores first position in text)
                this.posinphrase,   // position of word in its phrase
                this.posofphrase,   // number of the phrase where word appears
                this.lastModified,  // last-modified time of the document where word appears
                System.currentTimeMillis(),    // update time;
                ASCII.getBytes(this.language), // (guessed) language of document
                this.type,          // type of document
                this.llocal,        // outlinks to same domain
                this.lother,        // outlinks to other domain
                this.distance(),    // // average distance of multi search query words
                this.flags          // attributes to the url and to the word according the url
        );
    }

    @Override
    public Entry toKelondroEntry() {
        return toRowEntry().toKelondroEntry();
    }

    @Override
    public String toPropertyForm() {
        return toRowEntry().toPropertyForm();
    }

    @Override
    public byte[] urlhash() {
        return this.urlHash;
    }

    @Override
    public String hosthash() {
        if (this.hostHash != null) return this.hostHash;
        this.hostHash = ASCII.String(this.urlHash, 6, 6);
        return this.hostHash;
    }

    @Override
    public int urlcomps() {
        return this.urlcomps;
    }

    @Override
    public int urllength() {
        return this.urllength;
    }

    @Override
    public int virtualAge() {
        if (this.virtualAge > 0) return this.virtualAge;
        this.virtualAge = MicroDate.microDateDays(this.lastModified);
        return this.virtualAge;
    }

    @Override
    public int wordsintext() {
        return this.wordsintext;
    }

    @Override
    public int wordsintitle() {
        return this.wordsintitle;
    }

    @Override
    public double termFrequency() {
        if (this.termFrequency == 0.0) this.termFrequency = (((double) hitcount()) / ((double) (wordsintext() + wordsintitle() + 1)));
        return this.termFrequency;
    }
    
    public boolean local() {
        return this.local;
    }

    public final void min(final WordReferenceVars other) {
    	if (other == null) return;
        int v;
        long w;
        double d;
        if (this.hitcount > (v = other.hitcount)) this.hitcount = v;
        if (this.llocal > (v = other.llocal)) this.llocal = v;
        if (this.lother > (v = other.lother)) this.lother = v;
        if (virtualAge() > (v = other.virtualAge())) this.virtualAge = v;
        if (this.wordsintext > (v = other.wordsintext)) this.wordsintext = v;
        if (this.phrasesintext > (v = other.phrasesintext)) this.phrasesintext = v;
        if (this.posintext > (v = other.posintext)) this.posintext = v;

        // calculate and remember min distance
        if (this.distance() > 0 || other.distance() > 0) {
            int odist = other.distance();
            int dist = this.distance();
            if (odist > 0 && odist < dist) {
                if (this.positions == null) {
                    this.positions = new LinkedBlockingQueue<Integer>();
                } else {
                    this.positions.clear();
                }
                this.positions.add(this.posintext + odist);
            }
        }

        if (this.posinphrase > (v = other.posinphrase)) this.posinphrase = v;
        if (this.posofphrase > (v = other.posofphrase)) this.posofphrase = v;
        if (this.lastModified > (w = other.lastModified)) this.lastModified = w;
        //if (this.freshUntil > (w = other.freshUntil)) this.freshUntil = w;
        if (this.urllength > (v = other.urllength)) this.urllength = v;
        if (this.urlcomps > (v = other.urlcomps)) this.urlcomps = v;
        if (this.wordsintitle > (v = other.wordsintitle)) this.wordsintitle = v;
        if (this.termFrequency > (d = other.termFrequency)) this.termFrequency = d;
    }

    public final void max(final WordReferenceVars other) {
    	if (other == null) return;
        int v;
        long w;
        double d;
        if (this.hitcount < (v = other.hitcount)) this.hitcount = v;
        if (this.llocal < (v = other.llocal)) this.llocal = v;
        if (this.lother < (v = other.lother)) this.lother = v;
        if (virtualAge() < (v = other.virtualAge())) this.virtualAge = v;
        if (this.wordsintext < (v = other.wordsintext)) this.wordsintext = v;
        if (this.phrasesintext < (v = other.phrasesintext)) this.phrasesintext = v;
        if (this.posintext < (v = other.posintext)) this.posintext = v;

        // calculate and remember max distance
        if (this.distance() > 0 || other.distance() > 0) {
            int odist = other.distance();
            int dist = this.distance();
            if (odist > 0 && odist > dist) {
                if (this.positions == null) {
                    this.positions = new LinkedBlockingQueue<Integer>();
                } else {
                    this.positions.clear();
                }
                this.positions.add(this.posintext + odist);
            }
        }

        if (this.posinphrase < (v = other.posinphrase)) this.posinphrase = v;
        if (this.posofphrase < (v = other.posofphrase)) this.posofphrase = v;
        if (this.lastModified < (w = other.lastModified)) this.lastModified = w;
        //if (this.freshUntil < (w = other.freshUntil)) this.freshUntil = w;
        if (this.urllength < (v = other.urllength)) this.urllength = v;
        if (this.urlcomps < (v = other.urlcomps)) this.urlcomps = v;
        if (this.wordsintitle < (v = other.wordsintitle)) this.wordsintitle = v;
        if (this.termFrequency < (d = other.termFrequency)) this.termFrequency = d;
    }

    /**
     * joins two entries into one entry
     *
     * Main usage is on multi word searches to combine the position values for ranking and word distance calculation,
     * A Join is valid for the same url.
     * @param r WordReference
     */
    @Override
    public void join(final Reference r) {

        final WordReference oe = (WordReference) r;

        // choose min posintext (for > 0)
        if (this.posintext > 0 && oe.posintext() > 0) {
            if (this.posintext > oe.posintext()) {
                this.addPosition(this.posintext); // remember larger position (for distance calculation)
                this.posintext = oe.posintext();
            } else {
                this.addPosition(oe.posintext()); // remember other position (for distance calculation)
            }
        } else if (this.posintext == 0) {
            this.posintext = oe.posintext();
        }
       
        // join phrase
        // this.posinphrase = (this.posofphrase == oe.posofphrase()) ? Math.min(this.posinphrase, oe.posinphrase()) : 0;
        // this.posofphrase = Math.min(this.posofphrase, oe.posofphrase());
        final int oePosofphrase = oe.posofphrase();
        if (this.posofphrase == oePosofphrase) {
            this.posinphrase = Math.min(this.posinphrase, oe.posinphrase());
        } else if (this.posofphrase > oePosofphrase) {
            this.posofphrase = oePosofphrase; // choose min posofphrase
            this.posinphrase = oe.posinphrase(); // with corresponding posinphrase
        }

        // combine term frequency
        this.termFrequency = this.termFrequency + oe.termFrequency();

        this.wordsintext = Math.max(this.wordsintext, oe.wordsintext()); // as it is same url asume the word count to be the max
        this.wordsintitle = Math.max(this.wordsintitle, oe.wordsintitle());
        this.phrasesintext = Math.max(this.phrasesintext, oe.phrasesintext());
        this.hitcount = Math.max(this.hitcount, oe.hitcount());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof WordReferenceVars)) return false;
        final WordReferenceVars other = (WordReferenceVars) obj;
        return Base64Order.enhancedCoder.equal(this.urlHash, other.urlHash);
    }

    private int hashCache = Integer.MIN_VALUE; // if this is used in a compare method many times, a cache is useful

    @Override
    public int hashCode() {
        if (this.hashCache == Integer.MIN_VALUE) {
            this.hashCache = ByteArray.hashCode(this.urlHash);
        }
        return this.hashCache;
    }

    @Override
    public int compareTo(final WordReferenceVars o) {
        return Base64Order.enhancedCoder.compare(this.urlHash, o.urlhash());
    }

    @Override
    public int compare(final WordReferenceVars o1, final WordReferenceVars o2) {
        return o1.compareTo(o2);
    }

    /**
     * Add a position for word distance calculation to the list if position > 0
     * @param position
     */
    public void addPosition(final int position) {
        if (this.positions == null && position > 0) this.positions = new LinkedBlockingQueue<Integer>();
        if (position > 0) this.positions.add(position);
    }

    /**
     * transform a reference container into a stream of parsed entries
     * @param container
     * @return a blocking queue filled with WordReferenceVars that is still filled when the object is returned
     */
    public static BlockingQueue<WordReferenceVars> transform(final ReferenceContainer<WordReference> container, final long maxtime, final boolean local) {
    	final LinkedBlockingQueue<WordReferenceVars> vars = new LinkedBlockingQueue<WordReferenceVars>();
    	if (container.size() <= 100) {
    	    // transform without concurrency to omit thread creation overhead
    	    for (final Row.Entry entry: container) {
    	        try {
    	            vars.put(new WordReferenceVars(new WordReferenceRow(entry), local));
    	        } catch (final InterruptedException e) {}
    	    }
            try {
                vars.put(WordReferenceVars.poison);
            } catch (final InterruptedException e) {}
            return vars;
    	}
    	final Thread distributor = new TransformDistributor(container, vars, maxtime, local);
    	distributor.start();

    	// return the resulting queue while the processing queues are still working
    	return vars;
    }

    private static class TransformDistributor extends Thread {

    	private ReferenceContainer<WordReference> container;
    	private BlockingQueue<WordReferenceVars> out;
    	private long maxtime;
    	private final boolean local;
    	private TransformDistributor(final ReferenceContainer<WordReference> container, final BlockingQueue<WordReferenceVars> out, final long maxtime, final boolean local) {
    		super("WordReferenceVars.TransformDistributor");
    		this.container = container;
    		this.out = out;
    		this.maxtime = maxtime;
    		this.local = local;
    	}

        @Override
    	public void run() {
        	// start the transformation threads
        	final int cores0 = Math.min(WorkflowProcessor.availableCPU, this.container.size() / 100) + 1;
        	final TransformWorker[] worker = new TransformWorker[cores0];
        	for (int i = 0; i < cores0; i++) {
        		worker[i] = new TransformWorker(this.out, this.maxtime, this.local);
        		worker[i].start();
        	}
        	long timeout = this.maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + this.maxtime;

        	// fill the queue
        	int p = this.container.size();
    		while (p > 0) {
    			p--;
				worker[p % cores0].add(this.container.get(p, false));
				if (p % 100 == 0 && System.currentTimeMillis() > timeout) {
				    ConcurrentLog.warn("TransformDistributor", "distribution of WordReference entries to worker queues ended with timeout = " + this.maxtime);
				    break;
				}
            }

        	// insert poison to stop the queues
        	for (int i = 0; i < cores0; i++) {
        	    worker[i].add(WordReferenceRow.poisonRowEntry);
        	}

        	// wait for the worker to terminate because we want to place a poison entry into the out queue afterwards
        	for (int i = 0; i < cores0; i++) {
                try {
                    worker[i].join();
                } catch (final InterruptedException e) {
                }
            }

        	this.out.add(WordReferenceVars.poison);
    	}
    }

    private static class TransformWorker extends Thread {

    	private BlockingQueue<Row.Entry> in;
    	private BlockingQueue<WordReferenceVars> out;
    	private long maxtime;
    	private final boolean local;

    	private TransformWorker(final BlockingQueue<WordReferenceVars> out, final long maxtime, final boolean local) {
    		super("WordReferenceVars.TransformWorker");
    		this.in = new LinkedBlockingQueue<Row.Entry>();
    		this.out = out;
    		this.maxtime = maxtime;
    		this.local = local;
    	}

    	private void add(final Row.Entry entry) {
    		try {
				this.in.put(entry);
			} catch (final InterruptedException e) {
			}
    	}

        @Override
    	public void run() {
        	Row.Entry entry;
        	long timeout = this.maxtime == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + this.maxtime;
    		try {
				while ((entry = this.in.take()) != WordReferenceRow.poisonRowEntry) {
				    this.out.put(new WordReferenceVars(new WordReferenceRow(entry), local));
				    if (System.currentTimeMillis() > timeout) {
	                    ConcurrentLog.warn("TransformWorker", "normalization of row entries from row to vars ended with timeout = " + this.maxtime);
				        break;
				    }
				}
			} catch (final InterruptedException e) {}
    	}
    }

}
