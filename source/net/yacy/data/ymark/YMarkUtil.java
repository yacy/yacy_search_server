// YMarkUtil.java
// (C) 2011 by Stefan Foerster, sof@gmx.de, Norderstedt, Germany
// first published 2011 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2011-03-09 13:50:39 +0100 (Mi, 09 Mrz 2011) $
// $LastChangedRevision: 7574 $
// $LastChangedBy: apfelmaennchen $
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

package net.yacy.data.ymark;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Pattern;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.kelondro.data.word.Word;

public class YMarkUtil {
    public final static String TAGS_SEPARATOR = ",";
    public final static String FOLDERS_SEPARATOR = "/";
    public final static String SPACE = " ";
    public final static String EMPTY_STRING = new String();

    public final static Pattern TAGS_SEPARATOR_PATTERN = Pattern.compile(TAGS_SEPARATOR);
    public final static Pattern FOLDERS_SEPARATOR_PATTERN = Pattern.compile(FOLDERS_SEPARATOR);
    
    /**
     * conveniance function to generate url hashes for YMark bookmarks
     * @param url a string representation of a valid url
     * @return a byte[] hash for the input URL string
     * @throws MalformedURLException
     * @see net.yacy.kelondro.data.meta.DigestURI.DigestURI(String url, byte[] hash).hash()
     */
    public final static byte[] getBookmarkId(String url) throws MalformedURLException {
		return (new DigestURL(url)).hash();
    }

    /**
     * conveniance function to generate word hashes for YMark tags and folders
     * @param key a tag or folder name
     * @return a byte[] hash for the input string
     * @see net.yacy.kelondro.data.word.Word.word2hash(final String word)
     */
    public final static byte[] getKeyId(final String key) {
        return Word.word2hash(key.toLowerCase());
    }

    public final static byte[] keySetToBytes(final HashSet<String> urlSet) {
    	return UTF8.getBytes(keySetToString(urlSet));
    }

    public final static String keySetToString(final HashSet<String> urlSet) {
    	final Iterator<String> urlIter = urlSet.iterator();
    	final
    	StringBuilder urls = new StringBuilder(urlSet.size()*20);
    	while(urlIter.hasNext()) {
    		urls.append(TAGS_SEPARATOR);
    		urls.append(urlIter.next());
    	}
    	urls.deleteCharAt(0);
    	return urls.toString();
    }

    public final static HashSet<String> keysStringToSet(final String keysString) {
    	HashSet<String> keySet = new HashSet<String>();
        final String[] keyArray = keysString.split(TAGS_SEPARATOR);
        for (final String key : keyArray) {
        	keySet.add(key);
        }
        return keySet;
    }

    public final static String cleanTagsString(final String tagsString) {
    	return cleanTagsString(tagsString, YMarkUtil.EMPTY_STRING);
    }

    public final static String cleanTagsString(final String tagsString, final String dflt) {
    	StringBuilder ts = new StringBuilder(tagsString);
    	if(ts.length() == 0)
    		return dflt;
    	// get rid of double commas and space characters following a comma
    	for (int i = 0; i < ts.length()-1; i++) {
    		if (ts.charAt(i) == TAGS_SEPARATOR.charAt(0)) {
    			if (ts.charAt(i+1) == TAGS_SEPARATOR.charAt(0) || ts.charAt(i+1) == ' ') {
    				ts.deleteCharAt(i+1);
    				i--;
    			}
    		}
    	}
		// get rid of heading and trailing comma
		if (ts.charAt(0) == TAGS_SEPARATOR.charAt(0))
			ts.deleteCharAt(0);
		if (ts.length()>0 && ts.charAt(ts.length()-1) == TAGS_SEPARATOR.charAt(0))
			ts.deleteCharAt(ts.length()-1);
    	return new String(ts);
    }

    public final static String cleanFoldersString(final String foldersString) {
    	return cleanFoldersString(foldersString, YMarkUtil.EMPTY_STRING);
    }
    
    public final static String cleanFoldersString(final String foldersString, final String dflt) {
    	if(foldersString.isEmpty()) {
    		return dflt;
    	}
    	return cleanFoldersString(new StringBuilder(cleanTagsString(foldersString)));
    }

    public final static String cleanFoldersString(final StringBuilder fs) {
    	if(fs.length() == 0)
    		return YMarkEntry.BOOKMARK.FOLDERS.deflt();
    	for (int i = 0; i < fs.length()-1; i++) {
    		if (fs.charAt(i) == FOLDERS_SEPARATOR.charAt(0)) {
    			if (fs.charAt(i+1) == TAGS_SEPARATOR.charAt(0) || fs.charAt(i+1) == FOLDERS_SEPARATOR.charAt(0)) {
    				fs.deleteCharAt(i);
    				i--;
    			} else if (fs.charAt(i+1) == ' ') {
    				fs.deleteCharAt(i+1);
    				i--;
    			}
    		}
    	}
		if (fs.charAt(fs.length()-1) == FOLDERS_SEPARATOR.charAt(0)) {
			fs.deleteCharAt(fs.length()-1);
		}		
    	return new String(fs);
    }
}
