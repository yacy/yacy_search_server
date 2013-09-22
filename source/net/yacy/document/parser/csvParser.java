/**
 *  tarParser
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 02.10.2009 at http://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.document.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.yacy.cora.document.id.AnchorURL;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;

/**
 * a parser for comma-separated values
 * The values may also be separated by semicolon or tab,
 * the separator character is detected automatically
 */
public class csvParser extends AbstractParser implements Parser {

    public csvParser() {
        super("Comma Separated Value Parser");
        this.SUPPORTED_EXTENSIONS.add("csv");
    }

    @Override
    public Document[] parse(AnchorURL location, String mimeType, String charset, InputStream source) throws Parser.Failure, InterruptedException {
        // construct a document using all cells of the document
        // the first row is used as headline
        // all lines are artificially terminated by a '.' to separate them as sentence for the condenser.
        final List<String[]> table = getTable(charset, source);
        if (table.isEmpty()) throw new Parser.Failure("document has no lines", location);
        final StringBuilder sb = new StringBuilder();
        for (final String[] row: table) {
            sb.append(concatRow(row)).append(' ');
        }
        return new Document[]{new Document(
		        location,
		        mimeType,
		        charset,
		        this,
		        null,
		        null,
		        singleList(concatRow(table.get(0))),
		        "",
		        "",
		        null,
		        null,
		        0.0f, 0.0f,
		        sb.toString(),
		        null,
		        null,
		        null,
		        false,
		        new Date())};
    }

    private static String concatRow(String[] columns) {
        final StringBuilder sb = new StringBuilder(80);
        for (final String column : columns) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(column);
        }
        sb.append('.');
        return sb.toString();
    }

    private static List<String[]> getTable(String charset, InputStream source) {
        final List<String[]> rows = new ArrayList<String[]>();
        BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(source, charset));
        } catch (final UnsupportedEncodingException e1) {
            reader = new BufferedReader(new InputStreamReader(source));
        }
        String row;
        String separator = null;
        int columns = -1;
        try {
            while ((row = reader.readLine()) != null) {
                row = row.trim();
                if (row.isEmpty()) continue;
                if (separator == null) {
                    // try comma, semicolon and tab; take that one that results with more columns
                    final String[] colc = row.split(",");
                    final String[] cols = row.split(";");
                    final String[] colt = row.split("\t");
                    if (colc.length >= cols.length && colc.length >= colt.length) separator = ",";
                    if (cols.length >= colc.length && cols.length >= colt.length) separator = ";";
                    if (colt.length >= cols.length && colt.length >= colc.length) separator = "\t";
                }
                row = stripQuotes(row, '\"', separator.charAt(0), ' ');
                row = stripQuotes(row, '\'', separator.charAt(0), ' ');
                final String[] cols = row.split(separator);
                if (columns == -1) columns = cols.length;
                //if (cols.length != columns) continue; // skip lines that have the wrong number of columns
                rows.add(cols);
            }
        } catch (final IOException e) {
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
    private static String stripQuotes(final String line, final char quote,
            final char separator, final char replacement) {
        String ret = line;

        int p, q;
        // find left quote
        while ((p = ret.indexOf(quote)) >= 0) {
            q = ret.indexOf(quote, p + 1);
            if (q < 0) {
                // there is only a single quote but no 'right' quote.
                // This data is not well-formed. Just remove the quote and give up.
                return ret.substring(0, p) + ret.substring(p + 1);
            }
            ret = ret.substring(0, p) + ret.substring(p + 1, q).replace(separator, replacement) + ret.substring(q + 1);
        }
        return ret;
    }

}
