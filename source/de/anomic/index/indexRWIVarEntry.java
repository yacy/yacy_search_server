// indexRWIVarEntry.java
// (C) 2007 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 07.11.2007 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.index;

import de.anomic.kelondro.kelondroBitfield;
import de.anomic.plasma.plasmaWordIndex;

public class indexRWIVarEntry implements indexRWIEntry {

    public kelondroBitfield flags;
    public long freshUntil, lastModified;
    public String language, urlHash;
    public char type;
    public int hitcount, llocal, lother, phrasesintext, posintext,
               posinphrase, posofphrase,
               urlcomps, urllength, virtualAge,
               worddistance, wordsintext, wordsintitle;
    public double termFrequency;
    
    public indexRWIVarEntry(String  urlHash,
            int      urlLength,     // byte-length of complete URL
            int      urlComps,      // number of path components
            int      titleLength,   // length of description/length (longer are better?)
            int      hitcount,      // how often appears this word in the text
            int      wordcount,     // total number of words
            int      phrasecount,   // total number of phrases
            int      posintext,     // position of word in all words
            int      posinphrase,   // position of word in its phrase
            int      posofphrase,   // number of the phrase where word appears
            long     lastmodified,  // last-modified time of the document where word appears
            long     updatetime,    // update time; this is needed to compute a TTL for the word, so it can be removed easily if the TTL is short
            String   language,      // (guessed) language of document
            char     doctype,       // type of document
            int      outlinksSame,  // outlinks to same domain
            int      outlinksOther, // outlinks to other domain
            kelondroBitfield flags,  // attributes to the url and to the word according the url
            int      worddistance,
            double   termfrequency
    ) {
        if ((language == null) || (language.length() != 2)) language = "uk";
        int mddlm = plasmaWordIndex.microDateDays(lastmodified);
        int mddct = plasmaWordIndex.microDateDays(updatetime);
        this.flags = flags;
        this.freshUntil = Math.max(0, mddlm + (mddct - mddlm) * 2);
        this.lastModified = lastmodified;
        this.language = language;
        this.urlHash = urlHash;
        this.type = doctype;
        this.hitcount = hitcount;
        this.llocal = outlinksSame;
        this.lother = outlinksOther;
        this.phrasesintext = outlinksOther;
        this.posintext = posintext;
        this.posinphrase = posinphrase;
        this.posofphrase = posofphrase;
        this.urlcomps = urlComps;
        this.urllength = urlLength;
        this.virtualAge = mddlm;
        this.worddistance = worddistance;
        this.wordsintext = wordcount;
        this.wordsintitle = titleLength;
        this.termFrequency = termfrequency;
    }
    
    public indexRWIVarEntry(indexRWIRowEntry e) {
        this.flags = e.flags();
        this.freshUntil = e.freshUntil();
        this.lastModified = e.lastModified();
        this.language = e.getLanguage();
        this.urlHash = e.urlHash();
        this.type = e.getType();
        this.hitcount = e.hitcount();
        this.llocal = e.llocal();
        this.lother = e.lother();
        this.phrasesintext = e.phrasesintext();
        this.posintext = e.posintext();
        this.posinphrase = e.posinphrase();
        this.posofphrase = e.posofphrase();
        this.urlcomps = e.urlcomps();
        this.urllength = e.urllength();
        this.virtualAge = e.virtualAge();
        this.worddistance = 0;
        this.wordsintext = e.wordsintext();
        this.wordsintitle = e.wordsintitle();
        this.termFrequency = e.termFrequency();
    }
    
    public indexRWIVarEntry clone() {
        indexRWIVarEntry c = new indexRWIVarEntry(
                this.urlHash,
                this.urllength,
                this.urlcomps,
                this.wordsintitle,
                this.hitcount,
                this.wordsintext,
                this.phrasesintext,
                this.posintext,
                this.posinphrase,
                this.posofphrase,
                this.lastModified,
                System.currentTimeMillis(),
                this.language,
                this.type,
                this.llocal,
                this.lother,
                this.flags,
                this.worddistance,
                this.termFrequency);
        return c;
    }
    
    public void join(indexRWIVarEntry oe) {
        // combine the distance
        this.worddistance = this.worddistance + oe.worddistance + Math.abs(this.posintext - oe.posintext);
        this.posintext = Math.min(this.posintext, oe.posintext);
        this.posinphrase = (this.posofphrase == oe.posofphrase) ? Math.min(this.posinphrase, oe.posinphrase) : 0;
        this.posofphrase = Math.min(this.posofphrase, oe.posofphrase);

        // combine term frequency
        this.wordsintext = this.wordsintext + oe.wordsintext;
        this.termFrequency = this.termFrequency + oe.termFrequency;
    }

    public kelondroBitfield flags() {
        return flags;
    }

    public long freshUntil() {
        return freshUntil;
    }

    public String getLanguage() {
        return language;
    }

    public char getType() {
        return type;
    }

    public int hitcount() {
        return hitcount;
    }

    public boolean isNewer(indexRWIEntry other) {
        assert false; // should not be used
        return false;
    }

    public boolean isOlder(indexRWIEntry other) {
        assert false; // should not be used
        return false;
    }

    public long lastModified() {
        return lastModified;
    }

    public int llocal() {
        return llocal;
    }

    public int lother() {
        return lother;
    }

    public int phrasesintext() {
        return phrasesintext;
    }

    public int posinphrase() {
        return posinphrase;
    }

    public int posintext() {
        return posintext;
    }

    public int posofphrase() {
        return posofphrase;
    }
    
    public indexRWIRowEntry toRowEntry() {
        return new indexRWIRowEntry(
                urlHash,
                urllength,     // byte-length of complete URL
                urlcomps,      // number of path components
                wordsintitle,  // length of description/length (longer are better?)
                hitcount,      // how often appears this word in the text
                wordsintext,   // total number of words
                phrasesintext, // total number of phrases
                posintext,     // position of word in all words
                posinphrase,   // position of word in its phrase
                posofphrase,   // number of the phrase where word appears
                lastModified,  // last-modified time of the document where word appears
                System.currentTimeMillis(),    // update time;
                language,      // (guessed) language of document
                type,          // type of document
                llocal,        // outlinks to same domain
                lother,        // outlinks to other domain
                flags          // attributes to the url and to the word according the url
        );
    }

    public String toPropertyForm() {
        return toRowEntry().toPropertyForm();
    }

    public String urlHash() {
        return urlHash;
    }

    public int urlcomps() {
        return urlcomps;
    }

    public int urllength() {
        return urllength;
    }

    public int virtualAge() {
        return virtualAge;
    }

    public int worddistance() {
        return worddistance;
    }

    public int wordsintext() {
        return wordsintext;
    }

    public int wordsintitle() {
        return wordsintitle;
    }

    public double termFrequency() {
        if (this.termFrequency == 0.0) this.termFrequency = (((double) this.hitcount()) / ((double) (this.wordsintext() + this.wordsintitle() + 1)));
        return this.termFrequency;
    }
    
    public final void min(indexRWIVarEntry other) {
        int v;
        long w;
        double d;
        if (this.hitcount > (v = other.hitcount)) this.hitcount = v;
        if (this.llocal > (v = other.llocal)) this.llocal = v;
        if (this.lother > (v = other.lother)) this.lother = v;
        if (this.virtualAge > (v = other.virtualAge)) this.virtualAge = v;
        if (this.wordsintext > (v = other.wordsintext)) this.wordsintext = v;
        if (this.phrasesintext > (v = other.phrasesintext)) this.phrasesintext = v;
        if (this.posintext > (v = other.posintext)) this.posintext = v;
        if (this.posinphrase > (v = other.posinphrase)) this.posinphrase = v;
        if (this.posofphrase > (v = other.posofphrase)) this.posofphrase = v;
        if (this.worddistance > (v = other.worddistance)) this.worddistance = v;
        if (this.lastModified > (w = other.lastModified)) this.lastModified = w;
        if (this.freshUntil > (w = other.freshUntil)) this.freshUntil = w;
        if (this.urllength > (v = other.urllength)) this.urllength = v;
        if (this.urlcomps > (v = other.urlcomps)) this.urlcomps = v;
        if (this.wordsintitle > (v = other.wordsintitle)) this.wordsintitle = v;
        if (this.termFrequency > (d = other.termFrequency)) this.termFrequency = d;
    }
    
    public final void max(indexRWIVarEntry other) {
        int v;
        long w;
        double d;
        if (this.hitcount < (v = other.hitcount)) this.hitcount = v;
        if (this.llocal < (v = other.llocal)) this.llocal = v;
        if (this.lother < (v = other.lother)) this.lother = v;
        if (this.virtualAge < (v = other.virtualAge)) this.virtualAge = v;
        if (this.wordsintext < (v = other.wordsintext)) this.wordsintext = v;
        if (this.phrasesintext < (v = other.phrasesintext)) this.phrasesintext = v;
        if (this.posintext < (v = other.posintext)) this.posintext = v;
        if (this.posinphrase < (v = other.posinphrase)) this.posinphrase = v;
        if (this.posofphrase < (v = other.posofphrase)) this.posofphrase = v;
        if (this.worddistance < (v = other.worddistance)) this.worddistance = v;
        if (this.lastModified < (w = other.lastModified)) this.lastModified = w;
        if (this.freshUntil < (w = other.freshUntil)) this.freshUntil = w;
        if (this.urllength < (v = other.urllength)) this.urllength = v;
        if (this.urlcomps < (v = other.urlcomps)) this.urlcomps = v;
        if (this.wordsintitle < (v = other.wordsintitle)) this.wordsintitle = v;
        if (this.termFrequency < (d = other.termFrequency)) this.termFrequency = d;
    }

    public void join(indexRWIEntry oe) {
        // joins two entries into one entry
        
        // combine the distance
        this.worddistance = this.worddistance + ((oe instanceof indexRWIVarEntry) ? ((indexRWIVarEntry) oe).worddistance : 0) + Math.abs(this.posintext() - oe.posintext());
        this.posintext = Math.min(this.posintext, oe.posintext());
        this.posinphrase = (this.posofphrase == oe.posofphrase()) ? Math.min(this.posinphrase, oe.posinphrase()) : 0;
        this.posofphrase = Math.min(this.posofphrase, oe.posofphrase());

        // combine term frequency
        this.termFrequency = this.termFrequency + oe.termFrequency();
        this.wordsintext = this.wordsintext + oe.wordsintext();
    }

    public int hashCode() {
        return this.urlHash.hashCode();
    }
}
