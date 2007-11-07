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
import de.anomic.kelondro.kelondroRow.Entry;

public class indexRWIVarEntry implements indexRWIEntry {

    public kelondroBitfield flags;
    public long freshUntil, lastModified;
    public String language, urlHash;
    public char type;
    public int hitcount, llocal, lother, phrasesintext, posintext,
               posinphrase, posofphrase,
               quality, urlcomps, urllength, virtualAge,
               worddistance, wordsintext, wordsintitle;
    
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
        this.posofphrase= e.posofphrase();
        this.quality = e.quality();
        this.urlcomps = e.urlcomps();
        this.urllength = e.urllength();
        this.virtualAge = e.virtualAge();
        this.worddistance = e.worddistance();
        this.wordsintext = e.wordsintext();
        this.wordsintitle = e.wordsintitle();
    }
    
    public void combineDistance(indexRWIEntry oe) {
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
        // TODO Auto-generated method stub
        return false;
    }

    public boolean isOlder(indexRWIEntry other) {
        // TODO Auto-generated method stub
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

    public int quality() {
        return quality;
    }

    public Entry toKelondroEntry() {
        // TODO Auto-generated method stub
        return null;
    }

    public String toPropertyForm() {
        // TODO Auto-generated method stub
        return null;
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

    public static final void min(indexRWIVarEntry t, indexRWIEntry other) {
        int v;
        long w;
        if (t.hitcount() > (v = other.hitcount())) t.hitcount = v;
        if (t.llocal() > (v = other.llocal())) t.llocal = v;
        if (t.lother() > (v = other.lother())) t.lother = v;
        if (t.quality() > (v = other.quality())) t.quality = v;
        if (t.virtualAge() > (v = other.virtualAge())) t.virtualAge = v;
        if (t.wordsintext() > (v = other.wordsintext())) t.wordsintext = v;
        if (t.phrasesintext() > (v = other.phrasesintext())) t.phrasesintext = v;
        if (t.posintext() > (v = other.posintext())) t.posintext = v;
        if (t.posinphrase() > (v = other.posinphrase())) t.posinphrase = v;
        if (t.posofphrase() > (v = other.posofphrase())) t.posofphrase = v;
        if (t.worddistance() > (v = other.worddistance())) t.worddistance = v;
        if (t.lastModified() > (w = other.lastModified())) t.lastModified = w;
        if (t.freshUntil() > (w = other.freshUntil())) t.freshUntil = w;
        if (t.urllength() > (v = other.urllength())) t.urllength = v;
        if (t.urlcomps() > (v = other.urlcomps())) t.urlcomps = v;
        if (t.wordsintitle() > (v = other.wordsintitle())) t.wordsintitle = v;
    }
    
    public static final void max(indexRWIVarEntry t, indexRWIEntry other) {
        int v;
        long w;
        if (t.hitcount() < (v = other.hitcount())) t.hitcount = v;
        if (t.llocal() < (v = other.llocal())) t.llocal = v;
        if (t.lother() < (v = other.lother())) t.lother = v;
        if (t.quality() < (v = other.quality())) t.quality = v;
        if (t.virtualAge() < (v = other.virtualAge())) t.virtualAge = v;
        if (t.wordsintext() < (v = other.wordsintext())) t.wordsintext = v;
        if (t.phrasesintext() < (v = other.phrasesintext())) t.phrasesintext = v;
        if (t.posintext() < (v = other.posintext())) t.posintext = v;
        if (t.posinphrase() < (v = other.posinphrase())) t.posinphrase = v;
        if (t.posofphrase() < (v = other.posofphrase())) t.posofphrase = v;
        if (t.worddistance() < (v = other.worddistance())) t.worddistance = v;
        if (t.lastModified() < (w = other.lastModified())) t.lastModified = w;
        if (t.freshUntil() < (w = other.freshUntil())) t.freshUntil = w;
        if (t.urllength() < (v = other.urllength())) t.urllength = v;
        if (t.urlcomps() < (v = other.urlcomps())) t.urlcomps = v;
        if (t.wordsintitle() < (v = other.wordsintitle())) t.wordsintitle = v;
    }

}
