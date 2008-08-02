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

package de.anomic.plasma;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import de.anomic.plasma.parser.Parser;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public class plasmaParserConfig {
    /**
     * A list containing all enabled parsers and the mimeType that they can handle
     * @see #loadEnabledParserList()
     * @see #setEnabledParserList(Enumeration)
     */
    final HashSet<String> enabledParserList = new HashSet<String>();    
    
    /**
     * A list of file extensions that are supported by all enabled parsers
     */
    final HashSet<String> supportedFileExt = new HashSet<String>();
    
    /**
     * Parsermode this configuration belongs to
     */
    public String parserMode = null;
    
    public plasmaParserConfig(final String theParserMode) {
        if (!plasmaParser.PARSER_MODE.contains(theParserMode)) {
            throw new IllegalArgumentException("Unknown parser mode " + theParserMode);
        }
        
        this.parserMode = theParserMode;            
    }
    
    public boolean supportedContent(final yacyURL url, String mimeType) {
        // TODO: we need some exceptions here to index URLs like this
        //       http://www.musicabona.com/respighi/12668/cd/index.html.fr
        mimeType = plasmaParser.normalizeMimeType(mimeType);
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
        mimeType = plasmaParser.normalizeMimeType(mimeType);
        
        synchronized (plasmaParser.supportedHTMLMimeTypes) {
            if (plasmaParser.supportedHTMLMimeTypes.contains(mimeType)) return true;
        }        

        synchronized (this.enabledParserList) { 
            return this.enabledParserList.contains(mimeType);
        }
    }        
    
    
    public boolean supportedFileExt(final yacyURL url) {
        if (url == null) throw new NullPointerException();
        
        // getting the file path
        final String name = plasmaParser.getFileExt(url);
        return supportedFileExtContains(name);
    }
    
    public boolean supportedFileExtContains(String fileExt) {
        if (fileExt == null) return false;        
        fileExt = fileExt.trim().toLowerCase();

        synchronized (plasmaParser.supportedHTMLFileExt) {
            if (plasmaParser.supportedHTMLFileExt.contains(fileExt)) return true;
        }        

        synchronized(this.supportedFileExt) {
            return this.supportedFileExt.contains(fileExt);
        }
    }        
    
    public void initParseableMimeTypes(final String enabledMimeTypes) {
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
        final Set<String> availableMimeTypes = plasmaParser.availableParserList.keySet();
        setEnabledParserList(availableMimeTypes);
    }
    
    public  String[] setEnabledParserList(final Set<String> mimeTypeSet) {
        
        final HashSet<String> newEnabledParsers = new HashSet<String>();
        final HashSet<String> newSupportedFileExt = new HashSet<String>();
        
        if (mimeTypeSet != null) {
            final Iterator<String> mimeTypes = mimeTypeSet.iterator();
            while (mimeTypes.hasNext()) {
                final String mimeType = mimeTypes.next();
                if (plasmaParser.availableParserList.containsKey(mimeType)) {
                    Parser theParser = null;
                    try {
                        // getting the parser
                        theParser = plasmaParser.makeParser((plasmaParser.availableParserList.get(mimeType)).parserClassName);
                        
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
                        serverLog.logSevere("PARSER", "error in setEnabledParserList", e);
                    } finally {
                        if (theParser != null)
                            theParser = null; // destroy object
                    }
                }
            }
        }
        
        synchronized (this.enabledParserList) {
            this.enabledParserList.clear();
            this.enabledParserList.addAll(newEnabledParsers);
        }
        
        
        synchronized (this.supportedFileExt) {
            this.supportedFileExt.clear();
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