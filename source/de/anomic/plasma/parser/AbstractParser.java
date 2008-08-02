//AbstractParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
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

package de.anomic.plasma.parser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import de.anomic.plasma.plasmaParser;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.server.serverThread;
import de.anomic.server.logging.serverLog;
import de.anomic.yacy.yacyURL;

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
     * The source file file size in bytes if the source document was passed
     * in as file
     */
    protected long contentLength = -1;
    
    /**
     * The Constructor of this class.
     */
	public AbstractParser(final String[] libxDependencies) {
		super();
        this.libxDependencies = libxDependencies;
	}
    
    /**
     * Set the content length of the source file.
     * This value is needed by some parsers to decide
     * if the parsed text could be hold in memory
     */
    public void setContentLength(final long length) {
        this.contentLength = length;
    }

    /**
     * Check if the parser was interrupted.
     * @throws InterruptedException if the parser was interrupted
     */
    public static final void checkInterruption() throws InterruptedException {
        final Thread currentThread = Thread.currentThread();
        if ((currentThread instanceof serverThread) && ((serverThread)currentThread).shutdownInProgress()) throw new InterruptedException("Shutdown in progress ...");
        if (currentThread.isInterrupted()) throw new InterruptedException("Shutdown in progress ...");    
    }
    
    public final File createTempFile(final String name) throws IOException {
        String parserClassName = this.getClass().getName();
        int idx = parserClassName.lastIndexOf(".");
        if (idx != -1) {
            parserClassName = parserClassName.substring(idx+1);
        } 
                    
        // getting the file extension
        idx = name.lastIndexOf("/");
        final String fileName = (idx != -1) ? name.substring(idx+1) : name;        
        
        idx = fileName.lastIndexOf(".");
        final String fileExt = (idx > -1) ? fileName.substring(idx+1) : "";
        
        // creates the temp file
        final File tempFile = File.createTempFile(parserClassName + "_" + ((idx>-1)?fileName.substring(0,idx):fileName), (fileExt.length()>0)?"."+fileExt:fileExt);
        return tempFile;
    }
    
    public int parseDir(final yacyURL location, final String prefix, final File dir, final plasmaParserDocument doc)
            throws ParserException, InterruptedException, IOException {
        if (!dir.isDirectory())
            throw new ParserException("tried to parse ordinary file " + dir + " as directory", location);
        
        final String[] files = dir.list();
        int result = 0;
        for (int i=0; i<files.length; i++) {
            checkInterruption();
            final File file = new File(dir, files[i]);
            this.theLogger.logFine("parsing file " + location + "#" + file + " in archive...");
            if (file.isDirectory()) {
                result += parseDir(location, prefix, file, doc);
            } else try {
                final yacyURL url = yacyURL.newURL(location, "/" + prefix + "/"
                        // XXX: workaround for relative paths within document
                        + file.getPath().substring(file.getPath().indexOf(File.separatorChar) + 1)
                        + "/" + file.getName());
                final plasmaParserDocument subdoc = new plasmaParser().parseSource(
                        url,
                        plasmaParser.getMimeTypeByFileExt(files[i].substring(files[i].indexOf('.') + 1)),
                        null, file);
                // TODO: change anchors back to use '#' after archive name
                doc.addSubDocument(subdoc);
                subdoc.close();
                result++;
            } catch (final ParserException e) {
                this.theLogger.logInfo("unable to parse file " + file + " in " + location + ", skipping");
            }
        }
        return result;
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
            final yacyURL location, 
            final String mimeType,
            final String charset,
            final byte[] source
    ) throws ParserException, InterruptedException {
        ByteArrayInputStream contentInputStream = null;
        try {
            // convert the byte array into a stream
            contentInputStream = new ByteArrayInputStream(source);
            
            // parse the stream
            return this.parse(location,mimeType,charset,contentInputStream); 
        } finally {
            if (contentInputStream != null) {
                try {
                    contentInputStream.close();
                    contentInputStream = null;
                } catch (final Exception e){ /* ignore this */}
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
            final yacyURL location, 
            final String mimeType,
            final String charset,
			final File sourceFile
	) throws ParserException, InterruptedException {
        BufferedInputStream contentInputStream = null;
        try {
            // getting the file size of the document
            this.contentLength = sourceFile.length();            
            
            // create a stream from the file
            contentInputStream = new BufferedInputStream(new FileInputStream(sourceFile));
            
            // parse the stream
            return this.parse(location, mimeType, charset, contentInputStream);
        } catch (final FileNotFoundException e) {
            throw new ParserException("Unexpected error while parsing file. " + e.getMessage(),location); 
        } finally {
            if (contentInputStream != null) try{contentInputStream.close();}catch(final Exception e){/* ignore this */}
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
    public abstract plasmaParserDocument parse(yacyURL location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException;

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
    public void setLogger(final serverLog log) {
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
        return this.parserName;
    }
    
    public void reset() {
        this.contentLength = -1;
    }
}
