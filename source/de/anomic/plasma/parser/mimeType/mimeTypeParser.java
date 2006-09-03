//mimeTypeParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//last major change: 16.05.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.plasma.parser.mimeType;

import java.io.File;
import java.io.InputStream;
import de.anomic.net.URL;
import java.util.Collection;
import java.util.Hashtable;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import net.sf.jmimemagic.Magic;
import net.sf.jmimemagic.MagicMatch;

import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverFileUtils;

public class mimeTypeParser
extends AbstractParser
implements Parser {
    
    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */    
    public static final Hashtable SUPPORTED_MIME_TYPES = new Hashtable();    
    static { 
        SUPPORTED_MIME_TYPES.put("text/xml","xml");
        SUPPORTED_MIME_TYPES.put("application/xml","xml"); 
        SUPPORTED_MIME_TYPES.put("application/x-xml","xml");        
        SUPPORTED_MIME_TYPES.put("application/octet-stream","");        
        SUPPORTED_MIME_TYPES.put("application/x-compress","");
        SUPPORTED_MIME_TYPES.put("application/x-compressed","");
    } 
    
    /**
     * a list of library names that are needed by this parser
     * @see Parser#getLibxDependences()
     */
    private static final String[] LIBX_DEPENDENCIES = new String[] {
        "jmimemagic-0.0.4a.jar",
        "jakarta-oro-2.0.7.jar",
        "log4j-1.2.9.jar",
        "xerces.jar"
    };
    
    /**
     * Helping structure used to detect loops in the mimeType detection
     * process
     */
    private static Hashtable threadLoopDetection = new Hashtable();
    
    public mimeTypeParser() {
        super(LIBX_DEPENDENCIES);
        parserName = "MimeType Parser"; 
    }
    
    public String getMimeType (File sourceFile) {
        String mimeType = null;
        
        try {    
            Magic theMagic = new Magic();           
            MagicMatch match = theMagic.getMagicMatch(sourceFile);        
            
            // if a match was found we can return the new mimeType
            if (match!=null) {
                Collection subMatches = match.getSubMatches();
                if ((subMatches != null) && (!subMatches.isEmpty())) {
                    mimeType = ((MagicMatch) subMatches.iterator().next()).getMimeType();
                } else {
                    mimeType = match.getMimeType();
                }
                return mimeType;
            }
        } catch (Exception e) {
            /* ignore this */
        }
        return null;        
    }
    
    public plasmaParserDocument parse(URL location, String mimeType, File sourceFile) throws ParserException, InterruptedException {
        
        String orgMimeType = mimeType;
        
        // determining the mime type of the file ...
        try {       
            // adding current thread to loop detection list
            Integer loopDepth = null;
            if (threadLoopDetection.containsKey(Thread.currentThread())) {
                loopDepth = (Integer) threadLoopDetection.get(Thread.currentThread());                
            } else {
                loopDepth = new Integer(0);
            }
            if (loopDepth.intValue() > 5) return null;
            threadLoopDetection.put(Thread.currentThread(),new Integer(loopDepth.intValue()+1));
            
            // deactivating the logging for jMimeMagic
            Logger theLogger = Logger.getLogger("net.sf.jmimemagic");
            theLogger.setLevel(Level.OFF);
            
            Magic theMagic = new Magic();           
            MagicMatch match = theMagic.getMagicMatch(sourceFile);
            
            
            // if a match was found we can return the new mimeType
            if (match!=null) {
                Collection subMatches = match.getSubMatches();
                if ((subMatches != null) && (!subMatches.isEmpty())) {
                    mimeType = ((MagicMatch) subMatches.iterator().next()).getMimeType();
                    if ((mimeType == null)||(mimeType.length() == 0)) mimeType = match.getMimeType();
                } else {
                    mimeType = match.getMimeType();
                }
                
                // to avoid loops we have to test if the mimetype has changed ...
                if (this.getSupportedMimeTypes().containsKey(mimeType)) return null;
                if (orgMimeType.equals(mimeType)) return null;
                                
                // check for interruption
                checkInterruption();
                
                // parsing the content using the determined mimetype
                plasmaParser theParser = new plasmaParser();
                return theParser.parseSource(location,mimeType,sourceFile);
            }
            return null;
            
        } catch (Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            return null;
        } finally {
            Integer loopDepth = (Integer) threadLoopDetection.get(Thread.currentThread());                
            if (loopDepth.intValue() <= 1) {
                threadLoopDetection.remove(Thread.currentThread());
            } else {
                threadLoopDetection.put(Thread.currentThread(), new Integer(loopDepth.intValue()-1));
            }
        }
    }
    
    public plasmaParserDocument parse(URL location, String mimeType,
            InputStream source) throws ParserException {
        File dstFile = null;
        try {
            dstFile = File.createTempFile("mimeTypeParser",".tmp");
            serverFileUtils.copy(source,dstFile);
            return parse(location,mimeType,dstFile);
        } catch (Exception e) {            
            return null;
        } finally {
            if (dstFile != null) {dstFile.delete();}            
        }
        
    }
    
    public java.util.Hashtable getSupportedMimeTypes() {
        return mimeTypeParser.SUPPORTED_MIME_TYPES;
    }
    
    public void reset() {
        // Nothing todo here at the moment
    }
    
}
