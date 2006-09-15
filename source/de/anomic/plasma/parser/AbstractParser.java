//AbstractParser.java 
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import de.anomic.net.URL;

import de.anomic.plasma.plasmaParserDocument;
import de.anomic.server.serverThread;
import de.anomic.server.logging.serverLog;

/**
 * New classes implementing the {@link de.anomic.plasma.parser.Parser} interface
 * can extend this class to inherit all functions already implemented in this class.
 * @author Martin Thelian
 * @version $LastChangedRevision$ / $LastChangedDate$
 */
public abstract class AbstractParser implements Parser{

    /**
     * a list of library names that are needed by this parser
     */
    protected String[] libxDependencies = null;
    
    /**
     * the logger class that should be used by the parser module for logging
     * purposes.
     */
    protected serverLog theLogger = null;

    /**
     * Version number of the parser
     */    
    protected String parserVersionNr = "0.1";
    
    /**
     * Parser name
     */
    protected String parserName = this.getClass().getName();
    
    /**
     * The Constructor of this class.
     */
	public AbstractParser(String[] libxDependencies) {
		super();
        this.libxDependencies = libxDependencies;
	}

    public static final void checkInterruption() throws InterruptedException {
        Thread currentThread = Thread.currentThread();
        if ((currentThread instanceof serverThread) && ((serverThread)currentThread).shutdownInProgress()) throw new InterruptedException("Shutdown in progress ...");
        if (currentThread.isInterrupted()) throw new InterruptedException("Shutdown in progress ...");    
    }
    
	/**
	 * Parsing a document available as byte array.
     * @param location the origin of the document 
     * @param mimeType the mimetype of the document
     * @param charset the supposed charset of the document or <code>null</code> if unkown
     * @param source the content byte array
     * @return a {@link plasmaParserDocument} containing the extracted plain text of the document
     * and some additional metadata.
	 * @throws ParserException if the content could not be parsed properly 
	 * 
	 * @see de.anomic.plasma.parser.Parser#parse(de.anomic.net.URL, java.lang.String, byte[])
	 */
	public plasmaParserDocument parse(
            URL location, 
            String mimeType,
            String charset,
            byte[] source
    ) throws ParserException, InterruptedException {
        ByteArrayInputStream contentInputStream = null;
        try {
            contentInputStream = new ByteArrayInputStream(source);
            return this.parse(location,mimeType,charset,contentInputStream); 
        } finally {
            if (contentInputStream != null) {
                try {
                    contentInputStream.close();
                    contentInputStream = null;
                } catch (Exception e){}
            }
        }
	}

	/**
	 * Parsing a document stored in a {@link File}
     * @param location the origin of the document 
     * @param mimeType the mimetype of the document
     * @param charset the supposed charset of the document or <code>null</code> if unkown
     * @param sourceFile the file containing the content of the document
     * @return a {@link plasmaParserDocument} containing the extracted plain text of the document
     * and some additional metadata.
	 * @throws ParserException if the content could not be parsed properly 
	 * 
	 * @see de.anomic.plasma.parser.Parser#parse(de.anomic.net.URL, java.lang.String, java.io.File)
	 */
	public plasmaParserDocument parse(
            URL location, 
            String mimeType,
            String charset,
			File sourceFile
	) throws ParserException, InterruptedException {
        BufferedInputStream contentInputStream = null;
        try {
            contentInputStream = new BufferedInputStream(new FileInputStream(sourceFile));
            return this.parse(location, mimeType, charset, contentInputStream);
        } catch (FileNotFoundException e) {
            throw new ParserException(e.getMessage());
        } finally {
            if (contentInputStream != null) try{contentInputStream.close();}catch(Exception e){}
        }
	}
    
    /**
     * Parsing a document available as {@link InputStream}
     * @param location the origin of the document 
     * @param mimeType the mimetype of the document
     * @param charset the supposed charset of the document or <code>null</code> if unkown
     * @param source the {@link InputStream} containing the document content
     * @return a {@link plasmaParserDocument} containing the extracted plain text of the document
     * and some additional metadata.
     * @throws ParserException if the content could not be parsed properly 
     * 
     * @see de.anomic.plasma.parser.Parser#parse(de.anomic.net.URL, java.lang.String, java.io.InputStream)
     */
    public abstract plasmaParserDocument parse(URL location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException;

    /**
     * @return Returns a list of library names that are needed by this parser
     * @see de.anomic.plasma.parser.Parser#getLibxDependences()
     */
    public String[] getLibxDependences() {
        return this.libxDependencies;
    }
    
    /**
     * Setting the logger that should be used by this parser class ...
     */
    public void setLogger(serverLog log) {
        this.theLogger = log;
    }
    
    /**
     * Returns the version number of the parser
     * @return parser version number
     */
    public String getVersion() {
        return this.parserVersionNr;
    }
    
    /**
     * Return the name of the parser
     */
    public String getName() {
        return parserName;
    }
}
