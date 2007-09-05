//Parser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file was contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
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

package de.anomic.plasma.parser;

import java.io.File;
import java.io.InputStream;
import java.util.Hashtable;

import de.anomic.plasma.plasmaParserDocument;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

/**
 * This interface defines a list of methods that needs to be implemented
 * by each content parser class.
 * @author Martin Thelian
 * @version $LastChangedRevision$ / $LastChangedDate$
 */
public interface Parser {
    

    public static long MAX_KEEP_IN_MEMORY_SIZE = 5 * 1024 * 1024;    
    
    /**
     * Parsing a document available as byte array
     * @param location the origin of the document 
     * @param mimeType the mimetype of the document
     * @param charset the supposed charset of the document or <code>null</code> if unkown
     * @param source the content byte array
     * @return a {@link plasmaParserDocument} containing the extracted plain text of the document
     * and some additional metadata.
     *  
     * @throws ParserException if the content could not be parsed properly 
     */
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset, byte[] source)
    throws ParserException, InterruptedException;
    
    /**
     * Parsing a document stored in a {@link File}
     * @param location the origin of the document 
     * @param mimeType the mimetype of the document
     * @param charset the supposed charset of the document or <code>null</code> if unkown 
     * @param sourceFile the file containing the content of the document
     * @return a {@link plasmaParserDocument} containing the extracted plain text of the document
     * and some additional metadata.
     *  
     * @throws ParserException if the content could not be parsed properly 
     */    
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset, File sourceFile)
    throws ParserException, InterruptedException;
    
    /**
     * Parsing a document available as {@link InputStream}
     * @param location the origin of the document 
     * @param mimeType the mimetype of the document
     * @param charset the supposed charset of the document or <code>null</code> if unkown 
     * @param source the {@link InputStream} containing the document content
     * @return a {@link plasmaParserDocument} containing the extracted plain text of the document
     * and some additional metadata.
     *  
     * @throws ParserException if the content could not be parsed properly 
     */    
    public plasmaParserDocument parse(yacyURL location, String mimeType, String charset, InputStream source) 
    throws ParserException, InterruptedException;
            
    /**
     * Can be used to determine the MimeType(s) that are supported by the parser
     * @return a {@link Hashtable} containing a list of MimeTypes that are supported by 
     * the parser
     */
    public Hashtable getSupportedMimeTypes();
    
    /**
     * This function should be called before reusing the parser object.
     */
    public void reset();
    
    public void setContentLength(long length);
    
    /**
     * @return Returns a list of library names that are needed by this parser
     */
    public String[] getLibxDependences();
    
    /**
     * Can be used to set the logger that should be used by the parser module
     * @param log the {@link serverLog logger} that should be used 
     */
    public void setLogger(serverLog log);
    
    /**
     * Returns the version number of the current parser
     * @return parser version number
     */
    public String getVersion();
    
    /**
     * Returns the name of the parser
     * @return parser name
     */
    public String getName();
}


