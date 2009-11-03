// CSVParser
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 02.10.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-09-23 23:26:14 +0200 (Mi, 23 Sep 2009) $
// $LastChangedRevision: 6340 $
// $LastChangedBy: low012 $
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


package net.yacy.document.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.ParserException;
import net.yacy.kelondro.data.meta.DigestURI;

/**
 * a parser for comma-separated values
 * The values may also be separated by semicolon or tab,
 * the separator character is detected automatically
 */
public class csvParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */    
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("csv");
    }
    
    public csvParser() {
        super("Comma Separated Value Parser");
    }
    
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
    
    @Override
    public Document parse(DigestURI location, String mimeType, String charset, InputStream source) throws ParserException, InterruptedException {
        // construct a document using all cells of the document
        // the first row is used as headline
        // all lines are artificially terminated by a '.' to separate them as sentence for the condenser.
        List<String[]> table = getTable(location, mimeType, charset, source);
        if (table.size() == 0) throw new ParserException("document has no lines", location);
        StringBuilder sb = new StringBuilder();
        for (String[] row: table) sb.append(concatRow(row)).append(' ');
        try {
            return new Document(
                    location,
                    mimeType,
                    charset,
                    null,
                    null,
                    concatRow(table.get(0)),
                    "",
                    null,
                    null,
                    sb.toString().getBytes(charset),
                    null,
                    null);
        } catch (UnsupportedEncodingException e) {
            throw new ParserException("error in csvParser, getBytes: " + e.getMessage(), location);
        }
    }

    public String concatRow(String[] column) {
        StringBuilder sb = new StringBuilder(80);
        for (int i = 0; i < column.length; i++) {
            if (i != 0) sb.append(' ');
            sb.append(column[i]);
        }
        sb.append('.');
        return sb.toString();
    }
    
    public List<String[]> getTable(DigestURI location, String mimeType, String charset, InputStream source) {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(source, charset));
        } catch (UnsupportedEncodingException e1) {
            reader = new BufferedReader(new InputStreamReader(source));
        }
        String row;
        String separator = null;
        int columns = -1;
        try {
            while ((row = reader.readLine()) != null) {
                row = row.trim();
                if (row.length() == 0) continue;
                if (separator == null) {
                    // try comma, semicolon and tab; take that one that results with more columns
                    String[] colc = row.split(",");
                    String[] cols = row.split(";");
                    String[] colt = row.split("\t");
                    if (colc.length >= cols.length && colc.length >= colt.length) separator = ",";
                    if (cols.length >= colc.length && cols.length >= colt.length) separator = ";";
                    if (colt.length >= cols.length && colt.length >= colc.length) separator = "\t";
                }
                row = stripQuotes(row, '\"', separator.charAt(0), ' ');
                row = stripQuotes(row, '\'', separator.charAt(0), ' ');
                String[] cols = row.split(separator);
                if (columns == -1) columns = cols.length;
                //if (cols.length != columns) continue; // skip lines that have the wrong number of columns
                rows.add(cols);
            }
        } catch (IOException e) {
        }
        return rows;
    }

    /**
     * remove quotes AND separator characters within the quotes
     * to make it possible to split the line using the String.split method
     * @param line
     * @param quote
     * @param separator
     * @param replacement
     * @return the line without the quotes
     */
    public static String stripQuotes(String line, char quote, char separator, char replacement) {
        int p, q;
        // find left quote
        while ((p = line.indexOf(quote)) >= 0) {
            q = line.indexOf(quote, p + 1);
            if (q < 0) {
                // there is only a single quote but no 'right' quote.
                // This data is not well-formed. Just remove the quote and give up.
                return line.substring(0, p) + line.substring(p + 1);
            }
            line = line.substring(0, p) + line.substring(p + 1, q).replace(separator, replacement) + line.substring(q + 1);
        }
        return line;
    }

}
