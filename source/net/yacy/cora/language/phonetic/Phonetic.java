/**
 *  Phonetic
 *  Copyright 201 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 13.12.2011 at http://yacy.net
 *
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.language.phonetic;

public class Phonetic {

    public enum Encoder {
        SOUNDEX("Soundex", ""),
        COLONE_PHONETIC("Koelner Phonetik", "http://de.wikipedia.org/wiki/K%C3%B6lner_Phonetik"),
        METAPHONE("Metaphone", ""),
        DOUBLE_METAPHONE("Double Metaphone", ""),
        NONE("", "");
        
        final String printName;
        final String infoUrl;
        
        private Encoder(final String printName, final String infoUrl) {
            this.printName = printName;
            this.infoUrl = infoUrl;
        }
    }
    
    private static final Soundex soundexEncoder = new Soundex();
    private static final Metaphone metaphoneEncoder = new Metaphone();
    private static final DoubleMetaphone doubleMetaphoneEncoder = new DoubleMetaphone();
    private static final ColognePhonetic colognePhonetic = new ColognePhonetic();
    
    public static String encode(final Encoder encoder, final String s) {
        try {
            if (encoder == Encoder.SOUNDEX) return soundexEncoder.encode(s);
            if (encoder == Encoder.COLONE_PHONETIC) return colognePhonetic.encode(s);
            if (encoder == Encoder.METAPHONE) return metaphoneEncoder.encode(s);
            if (encoder == Encoder.DOUBLE_METAPHONE) return doubleMetaphoneEncoder.encode(s);
            return s;
        } catch (final Throwable e) {
            // some encoders do not work with non-ASCII charachters and throw an exception
            return s;
        }
    }
    
    public static void main(String[] args) {
        for (Encoder encoder: Encoder.values()) {
            for (String s: args) {
                System.out.print(Phonetic.encode(encoder, s));
                System.out.print(" ");
            }
            System.out.println();
        }
    }
    
}
