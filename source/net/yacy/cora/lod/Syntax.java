/**
 *  Syntax
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 17.12.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
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

package net.yacy.cora.lod;

import java.util.HashMap;
import java.util.Map;

import net.yacy.cora.lod.vocabulary.CreativeCommons;
import net.yacy.cora.lod.vocabulary.DublinCore;
import net.yacy.cora.lod.vocabulary.Foaf;
import net.yacy.cora.lod.vocabulary.Geo;
import net.yacy.cora.lod.vocabulary.HttpHeader;
import net.yacy.cora.lod.vocabulary.Rdf;
import net.yacy.cora.lod.vocabulary.YaCyMetadata;

/**
 * helper class to understand xml tags and vocabularies
 */
public class Syntax {

    private final static Class<?>[] vocabularies = new Class<?>[]{
        CreativeCommons.class,
        DublinCore.class,
        Foaf.class,
        Geo.class,
        HttpHeader.class,
        Rdf.class,
        YaCyMetadata.class
    };
    
    private final static Map<String, Vocabulary> tagMap = new HashMap<String, Vocabulary>();
    
    static {
        Vocabulary voc;
        for (Class<?> v: vocabularies) {
            Object[] cs = v.getEnumConstants();
            for (Object c: cs) {
                voc = (Vocabulary) c;
                tagMap.put(voc.getPredicate(), voc);
            }
        }
    }
    
    /**
     * recognizer for vocabulary tag names
     * @param tag
     * @return the vocabulary object for the given tag
     */
    public static Vocabulary getVocabulary(String tag) {
        return tagMap.get(tag);
    }
    
    public static void main(String[] args) {
        System.out.println(tagMap);
    }
}
