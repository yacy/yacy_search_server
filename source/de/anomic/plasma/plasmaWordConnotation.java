// plasmaWordCon.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 22.09.2004
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

/*
 */


package de.anomic.plasma;

import java.io.File;
import java.io.IOException;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroDynTree;

public class plasmaWordConnotation {
    
    private static final int wordlength = 32;
    private static final int countlength = 4;
    private static final int nodesize = 4048;
    private kelondroDynTree refDB;
    
    public plasmaWordConnotation(File refDBfile, int bufferkb) {
        if (refDBfile.exists()) try {
            refDB = new kelondroDynTree(refDBfile, bufferkb * 0x400);
        } catch (IOException e) {
            refDB = new kelondroDynTree(refDBfile, bufferkb * 0x400, wordlength, nodesize, new int[] {wordlength, countlength}, true);
        } else {
            refDB = new kelondroDynTree(refDBfile, bufferkb * 0x400, wordlength, nodesize, new int[] {wordlength, countlength}, true);
        }
    }

    private void addSingleRef(String word, String reference) throws IOException {
        //word = word.toLowerCase();
        //reference = reference.toLowerCase();
        byte[][] record = refDB.get(word, reference.getBytes());
        long c;
        if (record == null) c = 0; else c = kelondroBase64Order.enhancedCoder.decodeLong(new String(record[1]));
        record[1] = kelondroBase64Order.enhancedCoder.encodeLong(c++, countlength).getBytes();
        refDB.put(word, record);
    }
    
    public void addSentence(String[] words) throws IOException {
        for (int i = 0; i < words.length; i++) words[i] = words[i].toLowerCase();
        for (int i = 0; i < words.length; i++) {
            for (int j = 0; j < words.length; j++) {
                if ((i != j) && (words[i].length() > 2) && (words[j].length() > 2))
                    addSingleRef(words[i], words[j]);
            }
        }
    }
    
    public void addSentence(String sentence) throws IOException {
        addSentence(sentence.split(" "));
    }
    
    /*
    public String[] getConnotation(String word, int count) {
        TreeMap map = new TreeMap();
        return null;
    }
    */
}
