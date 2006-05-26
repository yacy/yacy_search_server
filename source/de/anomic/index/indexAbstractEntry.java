// indexbstractEntry.java
// (C) 2006 by Michael Peter Christen; mc@anomic.de, Frankfurt a. M., Germany
// first published 20.05.2006 on http://www.anomic.de
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

//import de.anomic.plasma.plasmaURL;
import de.anomic.plasma.plasmaWordIndex;

public abstract class indexAbstractEntry implements indexEntry {

    // the associated hash
    protected String urlHash;

    // discrete values
    protected int    hitcount;    // number of this words in file
    protected int    wordcount;   // number of all words in the file
    protected int    phrasecount; // number of all phrases in the file
    protected int    posintext;   // first position of the word in text as number of word; 0=unknown or irrelevant position
    protected int    posinphrase; // position within a phrase of the word
    protected int    posofphrase; // position of the phrase in the text as count of sentences; 0=unknown; 1=path; 2=keywords; 3=headline; >4: in text
    protected int    worddistance;// distance between the words, only used if the index is artificial (from a conjunction)
    protected long   lastModified;// calculated by using last-modified
    protected int    quality;     // result of a heuristic on the source file
    protected byte[] language;    // essentially the country code (the TLD as heuristic), two letters lowercase only
    protected char   doctype;     // type of source
    protected char   localflag;   // indicates if the index was created locally

    public abstract Object clone();
    
    public abstract String toEncodedStringForm();
    
    public abstract byte[] toEncodedByteArrayForm();
    
    public abstract String toPropertyForm();
    
    public void combineDistance(indexEntry oe) {
        this.worddistance = this.worddistance + ((indexAbstractEntry) oe).worddistance + Math.abs(this.posintext - ((indexAbstractEntry) oe).posintext);
        this.posintext = Math.min(this.posintext, ((indexAbstractEntry) oe).posintext);
        if (this.posofphrase != ((indexAbstractEntry) oe).posofphrase) this.posinphrase = 0; // (unknown)
        this.posofphrase = Math.min(this.posofphrase, ((indexAbstractEntry) oe).posofphrase);
        this.wordcount = (this.wordcount + ((indexAbstractEntry) oe).wordcount) / 2;
    }
    
    public void min(indexEntry other) {
        if (this.hitcount > ((indexAbstractEntry) other).hitcount) this.hitcount = ((indexAbstractEntry) other).hitcount;
        if (this.wordcount > ((indexAbstractEntry) other).wordcount) this.wordcount = ((indexAbstractEntry) other).wordcount;
        if (this.phrasecount > ((indexAbstractEntry) other).phrasecount) this.phrasecount = ((indexAbstractEntry) other).phrasecount;
        if (this.posintext > ((indexAbstractEntry) other).posintext) this.posintext = ((indexAbstractEntry) other).posintext;
        if (this.posinphrase > ((indexAbstractEntry) other).posinphrase) this.posinphrase = ((indexAbstractEntry) other).posinphrase;
        if (this.posofphrase > ((indexAbstractEntry) other).posofphrase) this.posofphrase = ((indexAbstractEntry) other).posofphrase;
        if (this.worddistance > ((indexAbstractEntry) other).worddistance) this.worddistance = ((indexAbstractEntry) other).worddistance;
        if (this.lastModified > ((indexAbstractEntry) other).lastModified) this.lastModified = ((indexAbstractEntry) other).lastModified;
        if (this.quality > ((indexAbstractEntry) other).quality) this.quality = ((indexAbstractEntry) other).quality;
    }
    
    public void max(indexEntry other) {
        if (this.hitcount < ((indexAbstractEntry) other).hitcount) this.hitcount = ((indexAbstractEntry) other).hitcount;
        if (this.wordcount < ((indexAbstractEntry) other).wordcount) this.wordcount = ((indexAbstractEntry) other).wordcount;
        if (this.phrasecount < ((indexAbstractEntry) other).phrasecount) this.phrasecount = ((indexAbstractEntry) other).phrasecount;
        if (this.posintext < ((indexAbstractEntry) other).posintext) this.posintext = ((indexAbstractEntry) other).posintext;
        if (this.posinphrase < ((indexAbstractEntry) other).posinphrase) this.posinphrase = ((indexAbstractEntry) other).posinphrase;
        if (this.posofphrase < ((indexAbstractEntry) other).posofphrase) this.posofphrase = ((indexAbstractEntry) other).posofphrase;
        if (this.worddistance < ((indexAbstractEntry) other).worddistance) this.worddistance = ((indexAbstractEntry) other).worddistance;
        if (this.lastModified < ((indexAbstractEntry) other).lastModified) this.lastModified = ((indexAbstractEntry) other).lastModified;
        if (this.quality < ((indexAbstractEntry) other).quality) this.quality = ((indexAbstractEntry) other).quality;
    }
    
    public void normalize(indexEntry mi, indexEntry ma) {
        indexAbstractEntry min = (indexAbstractEntry) mi;
        indexAbstractEntry max = (indexAbstractEntry) ma;
        this.hitcount     = (this.hitcount     == 0) ? 0 : 1 + 255 * (this.hitcount     - min.hitcount    ) / (1 + max.hitcount     - min.hitcount);
        this.wordcount    = (this.wordcount    == 0) ? 0 : 1 + 255 * (this.wordcount    - min.wordcount   ) / (1 + max.wordcount    - min.wordcount);
        this.phrasecount  = (this.phrasecount  == 0) ? 0 : 1 + 255 * (this.phrasecount  - min.phrasecount ) / (1 + max.phrasecount  - min.phrasecount);
        this.posintext    = (this.posintext    == 0) ? 0 : 1 + 255 * (this.posintext    - min.posintext   ) / (1 + max.posintext    - min.posintext);
        this.posinphrase  = (this.posinphrase  == 0) ? 0 : 1 + 255 * (this.posinphrase  - min.posinphrase ) / (1 + max.posinphrase  - min.posinphrase);
        this.posofphrase  = (this.posofphrase  == 0) ? 0 : 1 + 255 * (this.posofphrase  - min.posofphrase ) / (1 + max.posofphrase  - min.posofphrase);
        this.worddistance = (this.worddistance == 0) ? 0 : 1 + 255 * (this.worddistance - min.worddistance) / (1 + max.worddistance - min.worddistance);
        this.lastModified = (this.lastModified == 0) ? 0 : 1 + 255 * (this.lastModified - min.lastModified) / (1 + max.lastModified - min.lastModified);
        this.quality      = (this.quality      == 0) ? 0 : 1 + 255 * (this.quality      - min.quality     ) / (1 + max.quality      - min.quality);
    }
    
    public indexEntry generateNormalized(indexEntry min, indexEntry max) {
        indexEntry e = (indexAbstractEntry) this.clone();
        e.normalize(min, max);
        return e;
    }
    
    public String getUrlHash() { return urlHash; }
    public int getQuality() { return quality; }
    public int getVirtualAge() { return plasmaWordIndex.microDateDays(lastModified); }
    public long getLastModified() { return lastModified; }
    public int hitcount() { return hitcount; }
    public int posintext() { return posintext; }
    public int posinphrase() { return posinphrase; }
    public int posofphrase() { return posofphrase; }
    public int worddistance() { return worddistance; }
    public int wordcount() { return wordcount; }
    public int phrasecount() { return phrasecount; }
    public String getLanguage() { return new String(language); }
    public char getType() { return doctype; }
    public boolean isLocal() { return localflag == indexEntryAttribute.LT_LOCAL; }

    public boolean isNewer(indexEntry other) {
        if (other == null) return true;
        if (this.lastModified > ((indexAbstractEntry) other).lastModified) return true;
        if (this.lastModified == ((indexAbstractEntry) other).getLastModified()) {
            if (this.quality > ((indexAbstractEntry) other).quality) return true;
        }
        return false;
    }
 
    public boolean isOlder(indexEntry other) {
        if (other == null) return false;
        if (this.lastModified < ((indexAbstractEntry) other).getLastModified()) return true;
        if (this.lastModified == ((indexAbstractEntry) other).getLastModified()) {
            if (this.quality < ((indexAbstractEntry) other).quality) return true;
        }
        return false;
    }

    public int domlengthNormalized() {
        return 255 * indexURL.domLengthEstimation(this.urlHash) / 30;
    }

    public static void main(String[] args) {
        // outputs the word hash to a given word
        if (args.length != 1) System.exit(0);
        System.out.println("WORDHASH: " + indexEntryAttribute.word2hash(args[0]));
    }

}
