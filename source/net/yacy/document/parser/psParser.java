//psParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2007
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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

package net.yacy.document.parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.VocabularyScraper;
import net.yacy.kelondro.util.FileUtils;


public class psParser extends AbstractParser implements Parser {

    public psParser() {
        super("PostScript Document Parser");
        this.SUPPORTED_EXTENSIONS.add("ps");
        this.SUPPORTED_MIME_TYPES.add("application/postscript");
        this.SUPPORTED_MIME_TYPES.add("application/ps");
        this.SUPPORTED_MIME_TYPES.add("application/x-postscript");
        this.SUPPORTED_MIME_TYPES.add("application/x-ps");
        this.SUPPORTED_MIME_TYPES.add("application/x-postscript-not-eps");
    }

    private Document[] parse(final DigestURL location, final String mimeType, @SuppressWarnings("unused") final String charset, final File sourceFile) throws Parser.Failure, InterruptedException {

    	File outputFile = null;
        try {
        	// creating a temp file for the output
        	outputFile = FileUtils.createTempFile(this.getClass(), "ascii.txt");

            parseUsingJava(sourceFile,outputFile);

            // return result
            final Document[] docs = new Document[]{new Document(
                    location, // url
                    mimeType, // mime
                    StandardCharsets.UTF_8.name(),  // charset
                    this,
                    null,     // languages
                    null,     // keywords
                    null,     // title
                    null,       // author
                    "",       // publisher
                    null,     // sections
                    null,     // abstract
                    0.0d, 0.0d,
                    outputFile, // fulltext
                    null,     // anchors
                    null,     // rss
                    null,     // images
                    false,    // indexingdenied
                    new Date())};  

            return docs;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;
            // throw exception
            throw new Parser.Failure("Unexpected error while parsing ps file. " + e.getMessage(),location);
        } finally {
            // delete temp file
            if (outputFile != null) FileUtils.deletedelete(outputFile);
        }
    }

    private static void parseUsingJava(final File inputFile, final File outputFile) throws Exception {

        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            reader = new BufferedReader(new FileReader(inputFile));
            writer = new BufferedWriter(new FileWriter(outputFile));

            final String versionInfoLine = reader.readLine();
            final String version = (versionInfoLine == null) ? "" : versionInfoLine.substring(versionInfoLine.length()-3);

            int ichar = 0;
            boolean isComment = false;
            boolean isText = false;

            if (version.length() > 0 && version.charAt(0) == '2') {
                boolean isConnector = false;

                while ((ichar = reader.read()) > 0) {
                    if (isConnector) {
                        if (ichar < 108) {
                            writer.write(' ');
                        }
                        isConnector = false;
                    } else if (ichar == '%') {
                        isComment = true;
                    } else if (ichar == '\n' && isComment) {
                        isComment = false;
                    } else if (ichar == ')' && isText ) {
                        isConnector = true;
                        isText = false;
                    } else if (isText) {
                    	writer.write((char)ichar);
                    } else if (ichar == '(' && !isComment) {
                        isText = true;
                    }
                }

            } else if (version.length() > 0 && version.charAt(0) == '3') {
                final StringBuilder stmt = new StringBuilder();
                boolean isBMP = false;
                boolean isStore = false;
                int store = 0;

                while ((ichar = reader.read()) > 0) {
                    if (ichar == '%') {
                        isComment = true;
                    } else if (ichar == '\n' && isComment){
                        isComment = false;
                    } else if (ichar == ')' && isText ) {
                        isText = false;
                    } else if (isText && !isBMP) {
                    	writer.write((char)ichar);
                    } else if (ichar == '(' && !isComment && !isBMP) {
                        isText = true;
                    } else if (isStore) {
                        if (store == 9 || ichar == ' ' || ichar == 10) {
                            isStore = false;
                            store = 0;
                            if (stmt.toString().equals("BEGINBITM")) {
                                isText = false;
                                isBMP = true;
                            } else if (stmt.toString().equals("ENDBITMAP")) {
                                isBMP = false;
                            }
                            stmt.delete(0,stmt.length());
                        }
                        else {
                            stmt.append((char)ichar);
                            store++;
                        }
                    } else if (!isComment && !isStore && (ichar == 66 || ichar == 69)) {
                        isStore = true;
                        stmt.append((char)ichar);
                        store++;
                    }
                }
            } else {
                throw new Exception("Unsupported Postscript version '" + version + "'.");
            }
        } finally {
            if (reader != null) try { reader.close(); } catch (final Exception e) {/* */}
            if (writer != null) try { writer.close(); } catch (final Exception e) {/* */}
        }


    }

    @Override
    public Document[] parse(
            final DigestURL location,
            final String mimeType,
            final String charset,
            final VocabularyScraper scraper, 
            final int timezoneOffset,
            final InputStream source)
            throws Parser.Failure, InterruptedException {

        File tempFile = null;
        try {
            // creating a tempfile
            tempFile = FileUtils.createTempFile(this.getClass(), "temp.ps");

            // copying inputstream into file
            FileUtils.copy(source,tempFile);

            // parsing the file
            return parse(location,mimeType,charset,tempFile);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            throw new Parser.Failure("Unable to parse the ps file. " + e.getMessage(), location);
        } finally {
            if (tempFile != null) FileUtils.deletedelete(tempFile);
        }
    }

}
