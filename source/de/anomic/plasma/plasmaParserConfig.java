// plasmaParserConfig.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
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


package de.anomic.plasma;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserInfo;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

public class plasmaParserConfig {
    /**
     * A list containing all enabled parsers and the mimeType that they can handle
     * @see #loadEnabledParserList()
     * @see #setEnabledParserList(Enumeration)
     */
    final HashSet enabledParserList = new HashSet();    
    
    /**
     * A list of file extensions that are supported by all enabled parsers
     */
    final HashSet supportedFileExt = new HashSet();
    
    /**
     * Parsermode this configuration belongs to
     */
    public String parserMode = null;
    
    public plasmaParserConfig(String theParserMode) {
        if (!plasmaParser.PARSER_MODE.contains(theParserMode)) {
            throw new IllegalArgumentException("Unknown parser mode " + theParserMode);
        }
        
        this.parserMode = theParserMode;            
    }
    
    public boolean supportedContent(yacyURL url, String mimeType) {
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
    
    
    public boolean supportedFileExt(yacyURL url) {
        if (url == null) throw new NullPointerException();
        
        // getting the file path
        String name = plasmaParser.getFileExt(url);
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
    
    public void initParseableMimeTypes(String enabledMimeTypes) {
        HashSet mimeTypes = null;
        if ((enabledMimeTypes == null) || (enabledMimeTypes.length() == 0)) {
            mimeTypes = new HashSet();
        } else {            
            String[] enabledMimeTypeList = enabledMimeTypes.split(",");
            mimeTypes = new HashSet(enabledMimeTypeList.length);
            for (int i = 0; i < enabledMimeTypeList.length; i++) mimeTypes.add(enabledMimeTypeList[i].toLowerCase().trim());
        }
        setEnabledParserList(mimeTypes);
    }
    
    public void enableAllParsers() {
        Set availableMimeTypes = plasmaParser.availableParserList.keySet();
        setEnabledParserList(availableMimeTypes);
    }
    
    public  String[] setEnabledParserList(Set mimeTypeSet) {
        
        HashSet newEnabledParsers = new HashSet();
        HashSet newSupportedFileExt = new HashSet();
        
        if (mimeTypeSet != null) {
            Iterator mimeTypes = mimeTypeSet.iterator();
            while (mimeTypes.hasNext()) {
                String mimeType = (String) mimeTypes.next();
                if (plasmaParser.availableParserList.containsKey(mimeType)) {
                    Parser theParser = null;
                    try {
                        // getting the parser
                        theParser = plasmaParser.makeParser(((ParserInfo)plasmaParser.availableParserList.get(mimeType)).parserClassName);
                        
                        // getting a list of mimeTypes that the parser supports
                        Hashtable parserSupportsMimeTypes = theParser.getSupportedMimeTypes();
                        if (parserSupportsMimeTypes != null) {
                            Object supportedExtensions = parserSupportsMimeTypes.get(mimeType);
                            if ((supportedExtensions != null) &&
                                    (supportedExtensions instanceof String) &&
                                    (((String)supportedExtensions).length() > 0)) {
                                String[] extArray = ((String)supportedExtensions).split(",");
                                newSupportedFileExt.addAll(Arrays.asList(extArray));
                            }
                        }
                        newEnabledParsers.add(mimeType);
                        
                    } catch (Exception e) {
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

        return (String[])newEnabledParsers.toArray(new String[newEnabledParsers.size()]);
    }
    
    public HashSet getEnabledParserList() {
        synchronized (this.enabledParserList) {
            return (HashSet) this.enabledParserList.clone();
        }        
    }
}