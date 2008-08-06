//mimeTypeParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.plasma.parser.mimeType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Hashtable;

import net.sf.jmimemagic.Magic;
import net.sf.jmimemagic.MagicMatch;
import net.sf.jmimemagic.MagicMatchNotFoundException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.parser.AbstractParser;
import de.anomic.plasma.parser.Parser;
import de.anomic.plasma.parser.ParserException;
import de.anomic.server.serverFileUtils;
import de.anomic.yacy.yacyURL;

public class mimeTypeParser extends AbstractParser implements Parser {
    
    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */    
    public static final Hashtable<String, String> SUPPORTED_MIME_TYPES = new Hashtable<String, String>();   
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
        "commons-logging-1.1.1.jar",
        "jmimemagic-0.1.0.jar",
        "jakarta-oro-2.0.7.jar",
        "log4j-1.2.9.jar",
        "xerces.jar"
    };
    
    /**
     * Helping structure used to detect loops in the mimeType detection
     * process
     */
    private static Hashtable<Thread, Integer> threadLoopDetection = new Hashtable<Thread, Integer>();
    
    public mimeTypeParser() {
        super(LIBX_DEPENDENCIES);
        this.parserName = "MimeType Parser"; 
    }
    
    @SuppressWarnings("unchecked")
    public String getMimeType (final File sourceFile) {
        String mimeType = null;
        
        try {           
            final MagicMatch match = Magic.getMagicMatch(sourceFile,true);        
            
            // if a match was found we can return the new mimeType
            if (match!=null) {
                final Collection<MagicMatch> subMatches = match.getSubMatches();
                if ((subMatches != null) && (!subMatches.isEmpty())) {
                    mimeType = subMatches.iterator().next().getMimeType();
                } else {
                    mimeType = match.getMimeType();
                }
                return mimeType;
            }
        } catch (final Exception e) {
            /* ignore this */
        }
        return null;        
    }
    
    @SuppressWarnings("unchecked")
    public plasmaParserDocument parse(final yacyURL location, String mimeType, final String charset, final File sourceFile) throws ParserException, InterruptedException {
        
        final String orgMimeType = mimeType;
        
        // determining the mime type of the file ...
        try {       
            // adding current thread to loop detection list
            Integer loopDepth = null;
            if (threadLoopDetection.containsKey(Thread.currentThread())) {
                loopDepth = threadLoopDetection.get(Thread.currentThread());                
            } else {
                loopDepth = 0;
            }
            if (loopDepth.intValue() > 5) return null;
            threadLoopDetection.put(Thread.currentThread(),Integer.valueOf(loopDepth.intValue()+1));
            
            // deactivating the logging for jMimeMagic
            final Logger jmimeMagicLogger = Logger.getLogger("net.sf.jmimemagic");
            jmimeMagicLogger.setLevel(Level.OFF);

            final MagicMatch match = Magic.getMagicMatch(sourceFile,true,false);
            
            // if a match was found we can return the new mimeType
            if (match!=null) {
                final Collection<MagicMatch> subMatches = match.getSubMatches();
                if ((subMatches != null) && (!subMatches.isEmpty())) {
                    mimeType = subMatches.iterator().next().getMimeType();
                    if ((mimeType == null)||(mimeType.length() == 0)) mimeType = match.getMimeType();
                } else {
                    mimeType = match.getMimeType();
                }
                
                // to avoid loops we have to test if the mimetype has changed ...
                if (this.getSupportedMimeTypes().containsKey(mimeType)) throw new ParserException("Unable to detect mimetype of resource (1).",location);
                if (orgMimeType.equals(mimeType)) throw new ParserException("Unable to detect mimetype of resource (2).",location);
                                
                // check for interruption
                checkInterruption();
                
                // parsing the content using the determined mimetype
                final plasmaParser theParser = new plasmaParser();
                return theParser.parseSource(location,mimeType,charset,sourceFile);
            }
            throw new ParserException("Unable to detect mimetype of resource (3).",location);
        } catch (final MagicMatchNotFoundException e) {
            throw new ParserException("Unable to detect mimetype of resource (4).",location);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while detect mimetype of resource. " + e.getMessage(),location); 
        } finally {
            final Integer loopDepth = threadLoopDetection.get(Thread.currentThread());                
            if (loopDepth.intValue() <= 1) {
                threadLoopDetection.remove(Thread.currentThread());
            } else {
                threadLoopDetection.put(Thread.currentThread(), Integer.valueOf(loopDepth.intValue()-1));
            }
        }
    }
    
    public plasmaParserDocument parse(final yacyURL location, final String mimeType,final String charset, final InputStream source) throws ParserException, InterruptedException {
        File dstFile = null;
        try {
            dstFile = File.createTempFile("mimeTypeParser",".tmp");
            serverFileUtils.copy(source,dstFile);
            return parse(location,mimeType,charset,dstFile);
        } catch (final IOException e) {
            throw new ParserException("Unexpected error while detect mimetype of resource. " + e.getMessage(),location);
        } finally {
            if (dstFile != null) {dstFile.delete();}            
        }
        
    }
    
    public java.util.Hashtable<String, String> getSupportedMimeTypes() {
        return mimeTypeParser.SUPPORTED_MIME_TYPES;
    }
    
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
    
}
