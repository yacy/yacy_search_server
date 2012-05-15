// ImageReferenceVars.java
// (C) 2010 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 21.01.2010 on http://yacy.net
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

package net.yacy.kelondro.data.image;

import java.io.Serializable;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.order.Bitfield;
import net.yacy.kelondro.order.MicroDate;
import net.yacy.kelondro.rwi.AbstractReference;
import net.yacy.kelondro.rwi.Reference;
import net.yacy.kelondro.util.ByteArray;


public class ImageReferenceVars extends AbstractReference implements ImageReference, Reference, Cloneable, Serializable {

    private static final long serialVersionUID=3669156620967277347L;


    /**
	 * object for termination of concurrent blocking queue processing
	 */
	public static final ImageReferenceVars poison = new ImageReferenceVars();


    public Bitfield flags;
    public long lastModified;
    public byte[] urlHash;
    public String language;
    public char type;
    public int hitcount, llocal, lother, phrasesintext,
               posinphrase, posofphrase,
               urlcomps, urllength, virtualAge,
               wordsintext, wordsintitle;
    private final Queue<Integer> positions;
    public double termFrequency;

    public ImageReferenceVars(
    		final byte[]   urlHash,
            final int      urlLength,     // byte-length of complete URL
            final int      urlComps,      // number of path components
            final int      titleLength,   // length of description/length (longer are better?)
            final int      hitcount,      // how often appears this word in the text
            final int      wordcount,     // total number of words
            final int      phrasecount,   // total number of phrases
            final Queue<Integer> ps,      // positions of words that are joined into the reference
            final int      posinphrase,   // position of word in its phrase
            final int      posofphrase,   // number of the phrase where word appears
            final long     lastmodified,  // last-modified time of the document where word appears
            final long     updatetime,    // update time; this is needed to compute a TTL for the word, so it can be removed easily if the TTL is short
                  String   language,      // (guessed) language of document
            final char     doctype,       // type of document
            final int      outlinksSame,  // outlinks to same domain
            final int      outlinksOther, // outlinks to other domain
            final Bitfield flags,  // attributes to the url and to the word according the url
            final double   termfrequency
    ) {
        if ((language == null) || (language.length() != 2)) language = "uk";
        final int mddlm = MicroDate.microDateDays(lastmodified);
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
        this.positions = new LinkedBlockingQueue<Integer>();
        for (Integer i: ps) this.positions.add(i);
        this.posinphrase = posinphrase;
        this.posofphrase = posofphrase;
        this.urlcomps = urlComps;
        this.urllength = urlLength;
        this.virtualAge = mddlm;
        this.wordsintext = wordcount;
        this.wordsintitle = titleLength;
        this.termFrequency = termfrequency;
    }

    public ImageReferenceVars(final ImageReference e) {
        this.flags = e.flags();
        //this.freshUntil = e.freshUntil();
        this.lastModified = e.lastModified();
        this.language = e.getLanguage();
        this.urlHash = e.urlhash();
        this.type = e.getType();
        this.hitcount = e.hitcount();
        this.llocal = e.llocal();
        this.lother = e.lother();
        this.phrasesintext = e.phrasesintext();
        this.positions = new LinkedBlockingQueue<Integer>();
        for (Integer i: e.positions()) this.positions.add(i);
        this.posinphrase = e.posinphrase();
        this.posofphrase = e.posofphrase();
        this.urlcomps = e.urlcomps();
        this.urllength = e.urllength();
        this.virtualAge = e.virtualAge();
        this.wordsintext = e.wordsintext();
        this.wordsintitle = e.wordsintitle();
        this.termFrequency = e.termFrequency();
    }

    /**
     * initializer for special poison object
     */
    public ImageReferenceVars() {
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
        this.posinphrase = 0;
        this.posofphrase = 0;
        this.urlcomps = 0;
        this.urllength = 0;
        this.virtualAge = 0;
        this.wordsintext = 0;
        this.wordsintitle = 0;
        this.termFrequency = 0.0;
    }

    @Override
    public ImageReferenceVars clone() {
        final ImageReferenceVars c = new ImageReferenceVars(
                this.urlHash,
                this.urllength,
                this.urlcomps,
                this.wordsintitle,
                this.hitcount,
                this.wordsintext,
                this.phrasesintext,
                this.positions,
                this.posinphrase,
                this.posofphrase,
                this.lastModified,
                System.currentTimeMillis(),
                this.language,
                this.type,
                this.llocal,
                this.lother,
                this.flags,
                this.termFrequency);
        return c;
    }

    public void join(final ImageReferenceVars v) {
        // combine the distance
        this.positions.addAll(v.positions);
        this.posinphrase = (this.posofphrase == v.posofphrase) ? Math.min(this.posinphrase, v.posinphrase) : 0;
        this.posofphrase = Math.min(this.posofphrase, v.posofphrase);

        // combine term frequency
        this.wordsintext = this.wordsintext + v.wordsintext;
        this.termFrequency = this.termFrequency + v.termFrequency;
    }

    @Override
    public Bitfield flags() {
        return this.flags;
    }
/*
    public long freshUntil() {
        return freshUntil;
    }
*/
    @Override
    public String getLanguage() {
        return this.language;
    }

    @Override
    public char getType() {
        return this.type;
    }

    @Override
    public int hitcount() {
        return this.hitcount;
    }

    @Override
    public boolean isOlder(final Reference other) {
        assert false; // should not be used
        return false;
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

    @Override
    public Collection<Integer> positions() {
        return this.positions;
    }

    @Override
    public int posofphrase() {
        return this.posofphrase;
    }

    public ImageReferenceRow toRowEntry() {
        return new ImageReferenceRow(
                this.urlHash,
                this.urllength,     // byte-length of complete URL
                this.urlcomps,      // number of path components
                this.wordsintitle,  // length of description/length (longer are better?)
                this.hitcount,      // how often appears this word in the text
                this.wordsintext,   // total number of words
                this.phrasesintext, // total number of phrases
                this.positions.iterator().next(), // position of word in all words
                this.posinphrase,   // position of word in its phrase
                this.posofphrase,   // number of the phrase where word appears
                this.lastModified,  // last-modified time of the document where word appears
                System.currentTimeMillis(),    // update time;
                this.language,      // (guessed) language of document
                this.type,          // type of document
                this.llocal,        // outlinks to same domain
                this.lother,        // outlinks to other domain
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
    public int urlcomps() {
        return this.urlcomps;
    }

    @Override
    public int urllength() {
        return this.urllength;
    }

    @Override
    public int virtualAge() {
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
        if (this.termFrequency == 0.0) this.termFrequency = (((double) this.hitcount()) / ((double) (this.wordsintext() + this.wordsintitle() + 1)));
        return this.termFrequency;
    }

    public final void min(final ImageReferenceVars other) {
    	if (other == null) return;
        int v;
        long w;
        double d;
        if (this.hitcount > (v = other.hitcount)) this.hitcount = v;
        if (this.llocal > (v = other.llocal)) this.llocal = v;
        if (this.lother > (v = other.lother)) this.lother = v;
        if (this.virtualAge > (v = other.virtualAge)) this.virtualAge = v;
        if (this.wordsintext > (v = other.wordsintext)) this.wordsintext = v;
        if (this.phrasesintext > (v = other.phrasesintext)) this.phrasesintext = v;
        if (other.positions != null) a(this.positions, min(this.positions, other.positions));
        if (this.posinphrase > (v = other.posinphrase)) this.posinphrase = v;
        if (this.posofphrase > (v = other.posofphrase)) this.posofphrase = v;
        if (this.lastModified > (w = other.lastModified)) this.lastModified = w;
        //if (this.freshUntil > (w = other.freshUntil)) this.freshUntil = w;
        if (this.urllength > (v = other.urllength)) this.urllength = v;
        if (this.urlcomps > (v = other.urlcomps)) this.urlcomps = v;
        if (this.wordsintitle > (v = other.wordsintitle)) this.wordsintitle = v;
        if (this.termFrequency > (d = other.termFrequency)) this.termFrequency = d;
    }

    public final void max(final ImageReferenceVars other) {
    	if (other == null) return;
        int v;
        long w;
        double d;
        if (this.hitcount < (v = other.hitcount)) this.hitcount = v;
        if (this.llocal < (v = other.llocal)) this.llocal = v;
        if (this.lother < (v = other.lother)) this.lother = v;
        if (this.virtualAge < (v = other.virtualAge)) this.virtualAge = v;
        if (this.wordsintext < (v = other.wordsintext)) this.wordsintext = v;
        if (this.phrasesintext < (v = other.phrasesintext)) this.phrasesintext = v;
        if (other.positions != null) a(this.positions, max(this.positions, other.positions));
        if (this.posinphrase < (v = other.posinphrase)) this.posinphrase = v;
        if (this.posofphrase < (v = other.posofphrase)) this.posofphrase = v;
        if (this.lastModified < (w = other.lastModified)) this.lastModified = w;
        //if (this.freshUntil < (w = other.freshUntil)) this.freshUntil = w;
        if (this.urllength < (v = other.urllength)) this.urllength = v;
        if (this.urlcomps < (v = other.urlcomps)) this.urlcomps = v;
        if (this.wordsintitle < (v = other.wordsintitle)) this.wordsintitle = v;
        if (this.termFrequency < (d = other.termFrequency)) this.termFrequency = d;
    }

    @Override
    public void join(final Reference r) {
        // joins two entries into one entry

        // combine the distance
        ImageReference oe = (ImageReference) r;
        for (Integer i: r.positions()) this.positions.add(i);
        this.posinphrase = (this.posofphrase == oe.posofphrase()) ? Math.min(this.posinphrase, oe.posinphrase()) : 0;
        this.posofphrase = Math.min(this.posofphrase, oe.posofphrase());

        // combine term frequency
        this.termFrequency = this.termFrequency + oe.termFrequency();
        this.wordsintext = this.wordsintext + oe.wordsintext();
    }

    @Override
    public int hashCode() {
        return ByteArray.hashCode(this.urlHash);
    }

    public void addPosition(int position) {
        this.positions.add(position);
    }

}
