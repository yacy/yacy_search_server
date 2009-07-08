// plasmaParserConfig.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: theli $
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

package de.anomic.document;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import de.anomic.yacy.yacyURL;
import de.anomic.yacy.logging.Log;

public class ParserConfig {
    
    /**
     * A list containing all enabled parsers and the mimeType that they can handle
     * @see #loadEnabledParserList()
     * @see #setEnabledParserList(Enumeration)
     */
    public final HashSet<String> enabledParserList;    
    
    /**
     * A list of file extensions that are supported by all enabled parsers
     */
    private final HashSet<String> supportedFileExt;
    
    public ParserConfig() {
        supportedFileExt = new HashSet<String>();
        enabledParserList = new HashSet<String>();
    }
    
    public boolean supportedContent(final yacyURL url, String mimeType) {
        // TODO: we need some exceptions here to index URLs like this
        //       http://www.musicabona.com/respighi/12668/cd/index.html.fr
        mimeType = ParserDispatcher.normalizeMimeType(mimeType);
        if (
                mimeType.equals("text/html") ||
                mimeType.equals("application/xhtml+xml") ||
                mimeType.equals("text/plain")
            ) {
            return supportedMimeTypesContains(mimeType);
        }
        return supportedMimeTypesContains(mimeType) && supportedFileExt(url);
    }        
    
    public boolean supportedMimeTypesContains(String mimeType) {
        mimeType = ParserDispatcher.normalizeMimeType(mimeType);
        
        synchronized (ParserDispatcher.supportedHTMLMimeTypes) {
            if (ParserDispatcher.supportedHTMLMimeTypes.contains(mimeType)) return true;
        }        

        synchronized (this.enabledParserList) { 
            return this.enabledParserList.contains(mimeType);
        }
    }        
    
    private boolean supportedFileExt(final yacyURL url) {
        if (url == null) throw new NullPointerException();
        
        // getting the file path
        final String name = ParserDispatcher.getFileExt(url);
        return supportedFileExtContains(name);
    }
    
    public boolean supportedFileExtContains(String fileExt) {
        if (fileExt == null) return false;        
        fileExt = fileExt.trim().toLowerCase();

        synchronized (ParserDispatcher.supportedHTMLFileExt) {
            if (ParserDispatcher.supportedHTMLFileExt.contains(fileExt)) return true;
        }        

        synchronized(this.supportedFileExt) {
            return this.supportedFileExt.contains(fileExt);
        }
    }        
    
    public void addParseableMimeTypes(final String enabledMimeTypes) {
        HashSet<String> mimeTypes = null;
        if ((enabledMimeTypes == null) || (enabledMimeTypes.length() == 0)) {
            mimeTypes = new HashSet<String>();
        } else {            
            final String[] enabledMimeTypeList = enabledMimeTypes.split(",");
            mimeTypes = new HashSet<String>(enabledMimeTypeList.length);
            for (int i = 0; i < enabledMimeTypeList.length; i++) mimeTypes.add(enabledMimeTypeList[i].toLowerCase().trim());
        }
        setEnabledParserList(mimeTypes);
    }
    
    public void enableAllParsers() {
        final Set<String> availableMimeTypes = ParserDispatcher.availableParserList.keySet();
        setEnabledParserList(availableMimeTypes);
    }
    
    public String[] setEnabledParserList(final Set<String> mimeTypeSet) {
        
        final HashSet<String> newEnabledParsers = new HashSet<String>();
        final HashSet<String> newSupportedFileExt = new HashSet<String>();
        
        if (mimeTypeSet != null) {
            final Iterator<String> mimeTypes = mimeTypeSet.iterator();
            while (mimeTypes.hasNext()) {
                final String mimeType = mimeTypes.next();
                Parser theParser = ParserDispatcher.availableParserList.get(mimeType);
                if (theParser != null) {
                    try {
                        // getting a list of mimeTypes that the parser supports
                        final Hashtable<String, String> parserSupportsMimeTypes = theParser.getSupportedMimeTypes();
                        if (parserSupportsMimeTypes != null) {
                            final Object supportedExtensions = parserSupportsMimeTypes.get(mimeType);
                            if ((supportedExtensions != null) &&
                                    (supportedExtensions instanceof String) &&
                                    (((String)supportedExtensions).length() > 0)) {
                                final String[] extArray = ((String)supportedExtensions).split(",");
                                newSupportedFileExt.addAll(Arrays.asList(extArray));
                            }
                        }
                        newEnabledParsers.add(mimeType);
                        
                    } catch (final Exception e) {
                        Log.logSevere("PARSER", "error in setEnabledParserList", e);
                    } finally {
                        if (theParser != null)
                            theParser = null; // destroy object
                    }
                }
            }
        }
        
        synchronized (this.enabledParserList) {
            this.enabledParserList.addAll(newEnabledParsers);
        }
        
        synchronized (this.supportedFileExt) {
            this.supportedFileExt.addAll(newSupportedFileExt);
        }

        return newEnabledParsers.toArray(new String[newEnabledParsers.size()]);
    }
    
    @SuppressWarnings("unchecked")
    public HashSet<String> getEnabledParserList() {
        synchronized (this.enabledParserList) {
            return (HashSet<String>) this.enabledParserList.clone();
        }        
    }
}