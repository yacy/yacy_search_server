// wikiCode.java
// -------------------------------------
// part of YACY
//
// (C) 2005, 2006 by Alexander Schier, Marc Nause, Franz Brausze
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
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
package de.anomic.data.wiki;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.yacy.document.parser.html.CharacterCoding;

import de.anomic.server.serverCore;

/** This class provides methods to handle texts that have been posted in the yacyWiki or other
* parts of YaCy that use this class, like the blog or the profile.
*
* @author Alexander Schier [AS], Franz Brausze [FB], Marc Nause [MN]
*/
public class wikiCode extends abstractWikiParser implements wikiParser {
    private static final String ASTERISK = "*";
    private static final String EMPTY = "";
    private static final String PIPE_ESCAPED = "&#124;";
    private static final String REGEX_NOT_CHAR_NUM_OR_UNDERSCORE = "[^a-zA-Z0-9_]";

    private static final String HTML_OPEN_DEFINITION_DESCRIPTION = "<dd>";
    private static final String HTML_CLOSE_DEFINITION_DESCRIPTION = "</dd>";
    private static final String HTML_OPEN_DEFINITION_ITEM = "<dt>";
    private static final String HTML_CLOSE_DEFINITION_ITEM = "</dt>";
    private static final String HTML_OPEN_DEFINITION_LIST = "<dl>";
    private static final String HTML_CLOSE_DEFINITION_LIST = "</dl>";
    private static final String HTML_OPEN_UNORDERED_LIST = "<ul>";
    private static final String HTML_CLOSE_UNORDERED_LIST = "</ul>";
    private static final String HTML_CLOSE_BLOCKQUOTE = "</blockquote>";
    private static final String HTML_CLOSE_LIST_ELEMENT = "</li>";
    private static final String HTML_CLOSE_ORDERED_LIST = "</ol>";
    private static final String HTML_OPEN_BLOCKQUOTE = "<blockquote>";
    private static final String HTML_OPEN_LIST_ELEMENT = "<li>";
    private static final String HTML_OPEN_ORDERED_LIST = "<ol>";

    private static final String WIKI_CLOSE_LINK = "]]";
    private static final String WIKI_OPEN_LINK = "[[";
    private static final String WIKI_CLOSE_EXTERNAL_LINK = "]";
    private static final String WIKI_CLOSE_PRE_ESCAPED = "&lt;/pre&gt;";
    private static final String WIKI_OPEN_STRIKE = "&lt;s&gt;";
    private static final String WIKI_CLOSE_STRIKE = "&lt;/s&gt;";
    private static final String WIKI_EMPHASIZE_1 = "\'\'";
    private static final String WIKI_EMPHASIZE_2 = "\'\'\'";
    private static final String WIKI_EMPHASIZE_3 = "\'\'\'\'\'";
    private static final String WIKI_HEADLINE_TAG_1 = "=";
    private static final String WIKI_HEADLINE_TAG_2 = "==";
    private static final String WIKI_HEADLINE_TAG_3 = "===";
    private static final String WIKI_HEADLINE_TAG_4 = "====";
    private static final String WIKI_HEADLINE_TAG_5 = "=====";
    private static final String WIKI_HEADLINE_TAG_6 = "======";
    private static final String WIKI_HR_LINE = "----";
    private static final String WIKI_IMAGE = "Image:";
    private static final String WIKI_OPEN_EXTERNAL_LINK = "[";
    private static final String WIKI_OPEN_PRE_ESCAPED = "&lt;pre&gt;";

    private static final char ONE = '1';
    private static final char TWO = '2';
    private static final char THREE = '3';
    private static final char FOUR = '4';
    private static final char FIVE = '5';
    private static final char SIX = '6';
    private static final char WIKI_FORMATTED = ' ';
    private static final char WIKI_INDENTION = ':';

    private static final int LEN_WIKI_CLOSE_PRE_ESCAPED = WIKI_CLOSE_PRE_ESCAPED.length();
    private static final int LEN_WIKI_OPEN_PRE_ESCAPED = WIKI_OPEN_PRE_ESCAPED.length();
    private static final int LEN_WIKI_OPEN_LINK = WIKI_OPEN_LINK.length();
    private static final int LEN_WIKI_IMAGE = WIKI_IMAGE.length();
    private static final int LEN_WIKI_OPEN_EXTERNAL_LINK = WIKI_OPEN_EXTERNAL_LINK.length();
    private static final int LEN_WIKI_CLOSE_EXTERNAL_LINK = WIKI_CLOSE_EXTERNAL_LINK.length();
    private static final int LEN_WIKI_HR_LINE = WIKI_HR_LINE.length();

    private final List<String> tableOfContentElements = new ArrayList<String>();    //list of headlines used to create table of content of page

    /** List of properties which can be used in tables. */
    private final static String[] TABLE_PROPERTIES = {"rowspan", "colspan", "vspace", "hspace", "cellspacing", "cellpadding", "border"};

    /** Map which contains possible values for deveral parameters. */
    private final static Map<String, String[]> PROPERTY_VALUES = new HashMap<String, String[]>();

    /** Tags for different types of headlines in wikiCode. */
    private final static String[] HEADLINE_TAGS = new String[]{WIKI_HEADLINE_TAG_6, WIKI_HEADLINE_TAG_5, WIKI_HEADLINE_TAG_4, WIKI_HEADLINE_TAG_3, WIKI_HEADLINE_TAG_2, WIKI_HEADLINE_TAG_1};

    private final static char[] HEADLINE_LEVEL = new char[]{ONE, TWO, THREE, FOUR, FIVE, SIX};

    private String numberedListLevel = EMPTY;
    private String unorderedListLevel = EMPTY;
    private String defListLevel = EMPTY;
    private boolean processingCell = false;             //needed for prevention of double-execution of replaceHTML
    private boolean processingDefList = false;          //needed for definition lists
    private boolean escape = false;                     //needed for escape
    private boolean escaped = false;                    //needed for <pre> not getting in the way
    private boolean newRowStart = false;                //needed for the first row not to be empty
    private boolean noList = false;                     //needed for handling of [= and <pre> in lists
    private boolean processingPreformattedText = false; //needed for preformatted text
    private boolean preformattedSpanning = false;       //needed for <pre> and </pre> spanning over several lines
    private boolean replacedHtmlAlready = false;        //indicates if method replaceHTML has been used with line already
    private boolean processingTable = false;            //needed for tables, because they reach over several lines
    private int preindented = 0;                        //needed for indented <pre>s

    static {
        /* Arrays must be sorted since Arrays.searchBinary() is used later. For more info go to
         * http://java.sun.com/javase/6/docs/api/java/util/Arrays.html#binarySearch(T[], T, java.util.Comparator)
         */
        Arrays.sort(HEADLINE_LEVEL);
        Arrays.sort(HEADLINE_TAGS);
        Arrays.sort(TABLE_PROPERTIES);
        String[] array;
        Arrays.sort(array = new String[]{"void", "above", "below", "hsides", "lhs", "rhs", "vsides", "box", "border"});
        PROPERTY_VALUES.put("frame", array);
        Arrays.sort(array = new String[]{"none", "groups", "rows", "cols", "all"});
        PROPERTY_VALUES.put("rules", array);
        Arrays.sort(array = new String[]{"top", "middle", "bottom", "baseline"});
        PROPERTY_VALUES.put("valign", array);
        Arrays.sort(array = new String[]{"left", "right", "center"});
        PROPERTY_VALUES.put("align", array);
    }

    /**
     * Constructor
     * @param address
     */
    public wikiCode(final String address) {
        super(address);
    }

    /**
     * Transforms a text which contains wiki code to HTML fragment.
     * @param reader contains the text to be transformed.
     * @param length expected length of text, used to create buffer with right size.
     * @return HTML fragment.
     * @throws IOException in case input from reader can not be read.
     */
    protected String transform(final BufferedReader reader, final int length)
            throws IOException {
        final StringBuilder out = new StringBuilder(length);
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(processLineOfWikiCode(line)).append(serverCore.CRLF_STRING);
        }
        return out.insert(0, createTableOfContents()).toString();
    }

    // contributed by [FB], changes by [MN]
    /**
     * Processes tags which are connected to tables.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processTable(final String line) {
        //some variables that make it easier to change codes for the table
        final StringBuilder out = new StringBuilder();
        final String tableStart = "&#123;" + PIPE_ESCAPED;        // {|
        final String newLine = PIPE_ESCAPED + "-";                // |-
        final String cellDivider = PIPE_ESCAPED + PIPE_ESCAPED;   // ||
        final String tableEnd = PIPE_ESCAPED + "&#125;";          // |}
        final String attribDivider = PIPE_ESCAPED;                // |
        final int lenTableStart = tableStart.length();
        final int lenCellDivider = cellDivider.length();
        final int lenTableEnd = tableEnd.length();
        final int lenAttribDivider = attribDivider.length();

        if ((line.startsWith(tableStart)) && (!processingTable)) {
            processingTable = true;
            newRowStart = true;
            out.append("<table");
            if (line.trim().length() > lenTableStart) {
                out.append(filterTableProperties(line.substring(lenTableStart).trim()));
            }
            out.append(">");
        } else if (line.startsWith(newLine) && (processingTable)) {          // new row
            if (!newRowStart) {
                out.append("\t</tr>\n");
            } else {
                newRowStart = false;
            }
            out.append("\t<tr>");
        } else if ((line.startsWith(cellDivider)) && (processingTable)) {
            out.append("\t\t<td");
            final int cellEnd = (line.indexOf(cellDivider, lenCellDivider) > 0) ? (line.indexOf(cellDivider, lenCellDivider)) : (line.length());
            int propEnd = line.indexOf(attribDivider, lenCellDivider);
            final int occImage = line.indexOf("[[Image:", lenCellDivider);
            final int occEscape = line.indexOf("[=", lenCellDivider);
            //If resultOf("[[Image:") is less than propEnd, that means that there is no
            //property for this cell, only an image. Without this, YaCy could get confused
            //by a | in [[Image:picture.png|alt-text]] or [[Image:picture.png|alt-text]]
            //Same for [= (part of [= =])
            if ((propEnd > lenCellDivider) && ((occImage > propEnd) || (occImage < 0)) && ((occEscape > propEnd) || (occEscape < 0))) {
                propEnd = line.indexOf(attribDivider, lenCellDivider) + lenAttribDivider;
            } else {
                propEnd = cellEnd;
            }
            // both point at same place => new line
            if (propEnd == cellEnd) {
                propEnd = lenCellDivider;
            } else {
                out.append(filterTableProperties(line.substring(lenCellDivider, propEnd - lenAttribDivider).trim()));
            }
            // quick&dirty fix [MN]
            if (propEnd > cellEnd) {
                propEnd = lenCellDivider;
            }
            processingTable = false;
            processingCell = true;
            out.append(">");
            out.append(processTable(line.substring(propEnd, cellEnd).trim()));
            out.append("</td>");
            processingTable = true;
            processingCell = false;
            if (cellEnd < line.length()) {
                out.append("\n");
                out.append(processTable(line.substring(cellEnd)));
            }
        } else if (line.startsWith(tableEnd) && (processingTable)) {          // Table end
            processingTable = false;
            out.append("\t</tr>\n</table>");
            out.append(line.substring(lenTableEnd));
        } else {
            out.append(line);
        }
        return out.toString();
    }

    // contributed by [MN], changes by [FB]
    /** This method takes possible table properties and tests if they are valid.
     * Valid in this case means if they are a property for the table, tr or td
     * tag as stated in the HTML Pocket Reference by Jennifer Niederst (1st edition)
     * The method is important to avoid XSS attacks on the wiki via table properties.
     * @param properties String which may contain several table properties and/or junk.
     * @return String containing only table properties.
     */
    private StringBuilder filterTableProperties(final String properties) {
        final String[] values = properties.replaceAll("&quot;", EMPTY).split("[= ]");     //splitting the string at = and blanks
        final StringBuilder stringBuilder = new StringBuilder(properties.length());
        String key, value;
        String[] posVals;
        final int numberOfValues = values.length;
        for (int i = 0; i < numberOfValues; i++) {
            key = values[i].trim();
            if (key.equals("nowrap")) {
                appendKeyValuePair("nowrap", "nowrap", stringBuilder);
            } else if (i + 1 < numberOfValues) {
                value = values[++i].trim();
                if ((key.equals("summary"))
                        || (key.equals("bgcolor") && value.matches("#{0,1}[0-9a-fA-F]{1,6}|[a-zA-Z]{3,}"))
                        || ((key.equals("width") || key.equals("height")) && value.matches("\\d+%{0,1}"))
                        || ((posVals = PROPERTY_VALUES.get(key)) != null && Arrays.binarySearch(posVals, value) >= 0)
                        || (Arrays.binarySearch(TABLE_PROPERTIES, key) >= 0 && value.matches("\\d+"))) {
                    appendKeyValuePair(key, value, stringBuilder);
                }
            }
        }
        return stringBuilder;
    }

    /**
     * Appends a key/value pair in HTML syntax to a given StringBuilder.
     * @param key key to be appended.
     * @param value value of key.
     * @param stringBuilder this is what key/value are appended to.
     * @return
     */
    private StringBuilder appendKeyValuePair(final String key, final String value, final StringBuilder stringBuilder) {
        return stringBuilder.append(" ").append(key).append("=\"").append(value).append("\"");
    }

    /**
     * Processes tags which are connected to ordered lists.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processOrderedList(String line) {
        if (!noList) {    //lists only get processed if not forbidden (see code for [= and <pre>). [MN]

            //# sorted Lists contributed by [AS]
            //## Sublist
            if (line.startsWith(numberedListLevel + "#")) { //more #
                line = HTML_OPEN_ORDERED_LIST + serverCore.CRLF_STRING
                        + HTML_OPEN_LIST_ELEMENT
                        + line.substring(numberedListLevel.length() + 1, line.length())
                        + HTML_CLOSE_LIST_ELEMENT;
                numberedListLevel += "#";
            } else if (numberedListLevel.length() > 0 && line.startsWith(numberedListLevel)) { //equal number of #
                line = HTML_OPEN_LIST_ELEMENT
                        + line.substring(numberedListLevel.length(), line.length())
                        + HTML_CLOSE_LIST_ELEMENT;
            } else if (numberedListLevel.length() > 0) { //less #
                int i = numberedListLevel.length();
                String tmp = EMPTY;

                while (!line.startsWith(numberedListLevel.substring(0, i))) {
                    tmp += HTML_CLOSE_ORDERED_LIST;
                    i--;
                }
                numberedListLevel = numberedListLevel.substring(0, i);
                final int positionOfOpeningTag = numberedListLevel.length();
                final int positionOfClosingTag = line.length();

                if (numberedListLevel.length() > 0) {
                    line = tmp
                            + HTML_OPEN_LIST_ELEMENT
                            + line.substring(positionOfOpeningTag, positionOfClosingTag)
                            + HTML_CLOSE_LIST_ELEMENT;
                } else {
                    line = tmp + line.substring(positionOfOpeningTag, positionOfClosingTag);
                }
            }
            // end contrib [AS]
        }
        return line;
    }

    //contributed by [AS] put into it's own method by [MN]
    /**
     * Processes tags which are connected to unordered lists.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processUnorderedList(String line) {
        if (!noList) {    //lists only get processed if not forbidden (see code for [= and <pre>). [MN]
            //contributed by [AS]
            if (line.startsWith(unorderedListLevel + ASTERISK)) { //more stars
                line = HTML_OPEN_UNORDERED_LIST + serverCore.CRLF_STRING
                        + HTML_OPEN_LIST_ELEMENT
                        + line.substring(unorderedListLevel.length() + 1, line.length())
                        + HTML_CLOSE_LIST_ELEMENT;
                unorderedListLevel += ASTERISK;
            } else if (unorderedListLevel.length() > 0 && line.startsWith(unorderedListLevel)) { //equal number of stars
                line = HTML_OPEN_LIST_ELEMENT
                        + line.substring(unorderedListLevel.length(), line.length())
                        + HTML_CLOSE_LIST_ELEMENT;
            } else if (unorderedListLevel.length() > 0) { //less stars
                int i = unorderedListLevel.length();
                String tmp = EMPTY;

                while (unorderedListLevel.length() >= i && !line.startsWith(unorderedListLevel.substring(0, i))) {
                    tmp += HTML_CLOSE_UNORDERED_LIST;
                    i--;
                }
                int positionOfOpeningTag = unorderedListLevel.length();
                if (i < positionOfOpeningTag) {
                    unorderedListLevel = unorderedListLevel.substring(0, i);
                    positionOfOpeningTag = unorderedListLevel.length();
                }
                final int positionOfClosingTag = line.length();

                if (unorderedListLevel.length() > 0) {
                    line = tmp
                            + HTML_OPEN_LIST_ELEMENT
                            + line.substring(positionOfOpeningTag, positionOfClosingTag)
                            + HTML_CLOSE_LIST_ELEMENT;
                } else {
                    line = tmp + line.substring(positionOfOpeningTag, positionOfClosingTag);
                }
            }
            //end contrib [AS]
        }
        return line;
    }

    //contributed by [MN] based on unordered list code by [AS]
    /**
     * Processes tags which are connected to definition lists.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processDefinitionList(String line) {
        if (!noList) {    //lists only get processed if not forbidden (see code for [= and <pre>). [MN]

            if (line.startsWith(defListLevel + ";")) { //more semicolons
                String definitionItem = EMPTY;
                String definitionDescription = EMPTY;
                final int positionOfOpeningTag;
                final int positionOfClosingTag = line.length();
                final String copyOfLine = line.substring(defListLevel.length() + 1, positionOfClosingTag);
                if ((positionOfOpeningTag = copyOfLine.indexOf(":")) > 0) {
                    definitionItem = copyOfLine.substring(0, positionOfOpeningTag);
                    definitionDescription = copyOfLine.substring(positionOfOpeningTag + 1);
                    line = HTML_OPEN_DEFINITION_LIST +
                            HTML_OPEN_DEFINITION_ITEM +
                            definitionItem +
                            HTML_CLOSE_DEFINITION_ITEM +
                            HTML_OPEN_DEFINITION_DESCRIPTION +
                            definitionDescription;
                    processingDefList = true;
                }
                defListLevel += ";";
            } else if (defListLevel.length() > 0 && line.startsWith(defListLevel)) { //equal number of semicolons
                String definitionItem = EMPTY;
                String definitionDescription = EMPTY;
                final int positionOfOpeningTag;
                final int positionOfClosingTag = line.length();
                final String copyOfLine = line.substring(defListLevel.length(), positionOfClosingTag);
                if ((positionOfOpeningTag = copyOfLine.indexOf(":")) > 0) {
                    definitionItem = copyOfLine.substring(0, positionOfOpeningTag);
                    definitionDescription = copyOfLine.substring(positionOfOpeningTag + 1);
                    line = HTML_OPEN_DEFINITION_ITEM +
                            definitionItem +
                            HTML_CLOSE_DEFINITION_ITEM +
                            HTML_OPEN_DEFINITION_DESCRIPTION +
                            definitionDescription;
                    processingDefList = true;
                }
            } else if (defListLevel.length() > 0) { //less semicolons
                String definitionItem = EMPTY;
                String definitionDescription = EMPTY;
                int i = defListLevel.length();
                String tmp = EMPTY;
                while (!line.startsWith(defListLevel.substring(0, i))) {
                    tmp = HTML_CLOSE_DEFINITION_DESCRIPTION + HTML_CLOSE_DEFINITION_LIST;
                    i--;
                }
                defListLevel = defListLevel.substring(0, i);
                int positionOfOpeningTag = defListLevel.length();
                final int positionOfClosingTag = line.length();
                if (defListLevel.length() > 0) {
                    final String copyOfLine = line.substring(positionOfOpeningTag, positionOfClosingTag);
                    if ((positionOfOpeningTag = copyOfLine.indexOf(":")) > 0) {
                        definitionItem = copyOfLine.substring(0, positionOfOpeningTag);
                        definitionDescription = copyOfLine.substring(positionOfOpeningTag + 1);
                        line = tmp + HTML_OPEN_DEFINITION_ITEM + definitionItem + HTML_CLOSE_DEFINITION_ITEM + HTML_OPEN_DEFINITION_DESCRIPTION + definitionDescription;
                        processingDefList = true;
                    }
                } else {
                    line = tmp + line.substring(positionOfOpeningTag, positionOfClosingTag);
                }
            }
        }
        return line;
    }

    //contributed by [AS] except where stated otherwise
    /**
     * Processes tags which are connected to links and images.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processLinksAndImages(String line) {

        // create links
        String kl, kv, alt, align;
        int p;
        int positionOfOpeningTag;
        int positionOfClosingTag;
        // internal links and images
        while ((positionOfOpeningTag = line.indexOf(WIKI_OPEN_LINK)) >= 0) {
            positionOfClosingTag = line.indexOf(WIKI_CLOSE_LINK, positionOfOpeningTag + LEN_WIKI_OPEN_LINK);
            if (positionOfClosingTag <= positionOfOpeningTag) {
                break;
            }
            kl = line.substring(positionOfOpeningTag + LEN_WIKI_OPEN_LINK, positionOfClosingTag);

            // this is the part of the code that's responsible for images
            // contributed by [MN]
            if (kl.startsWith(WIKI_IMAGE)) {
                alt = EMPTY;
                align = EMPTY;
                kv = EMPTY;
                kl = kl.substring(LEN_WIKI_IMAGE);

                // are there any arguments for the image?
                if ((p = kl.indexOf(PIPE_ESCAPED)) > 0) {
                    kv = kl.substring(p + LEN_WIKI_IMAGE);
                    kl = kl.substring(0, p);
                    // if there are 2 arguments, write them into ALIGN and ALT
                    if ((p = kv.indexOf(PIPE_ESCAPED)) > 0) {
                        align = kv.substring(0, p);
                        //checking validity of value for align. Only non browser specific
                        //values get supported. Not supported: absmiddle, baseline, texttop
                        if ((align.equals("bottom"))
                                || (align.equals("center"))
                                || (align.equals("left"))
                                || (align.equals("middle"))
                                || (align.equals("right"))
                                || (align.equals("top"))) {
                            align = " align=\"" + align + "\"";
                        } else {
                            align = EMPTY;
                        }
                        alt = " alt=\"" + kv.substring(p + LEN_WIKI_IMAGE) + "\"";
                    } // if there is just one, put it into ALT
                    else {
                        alt = " alt=\"" + kv + "\"";
                    }
                }

                // replace incomplete URLs and make them point to http://peerip:port/...
                // with this feature you can access an image in DATA/HTDOCS/share/yacy.gif
                // using the wikicode [[Image:share/yacy.gif]]
                // or an image DATA/HTDOCS/grafics/kaskelix.jpg with [[Image:grafics/kaskelix.jpg]]
                // you are free to use other sub-paths of DATA/HTDOCS
                if (kl.indexOf("://") < 1) {
                    kl = "http://" + super.address + "/" + kl;
                }

                line = line.substring(0, positionOfOpeningTag) + "<img src=\"" + kl + "\"" + align + alt + ">" + line.substring(positionOfClosingTag + 2);
            } // end contrib [MN]
            // if it's no image, it might be an internal link
            else {
                if ((p = kl.indexOf(PIPE_ESCAPED)) > 0) {
                    kv = kl.substring(p + 6);
                    kl = kl.substring(0, p);
                } else {
                    kv = kl;
                }
                line = line.substring(0, positionOfOpeningTag) + "<a class=\"known\" href=\"Wiki.html?page=" + kl + "\">" + kv + "</a>" + line.substring(positionOfClosingTag + 2); // oob exception in append() !
            }
        }

        // external links
        while ((positionOfOpeningTag = line.indexOf(WIKI_OPEN_EXTERNAL_LINK)) >= 0) {
            positionOfClosingTag = line.indexOf(WIKI_CLOSE_EXTERNAL_LINK, positionOfOpeningTag + LEN_WIKI_OPEN_EXTERNAL_LINK);
            if (positionOfClosingTag <= positionOfOpeningTag) {
                break;
            }
            kl = line.substring(positionOfOpeningTag + LEN_WIKI_OPEN_EXTERNAL_LINK, positionOfClosingTag);
            if ((p = kl.indexOf(" ")) > 0) {
                kv = kl.substring(p + 1);
                kl = kl.substring(0, p);
            } // No text for the link? -> <a href="http://www.url.com/">http://www.url.com/</a>
            else {
                kv = kl;
            }
            // replace incomplete URLs and make them point to http://peerip:port/...
            // with this feature you can access a file at DATA/HTDOCS/share/page.html
            // using the wikicode [share/page.html]
            // or a file DATA/HTDOCS/www/page.html with [www/page.html]
            // you are free to use other sub-paths of DATA/HTDOCS
            if (kl.indexOf("://") < 1) {
                kl = "http://" + super.address + "/" + kl;
            }
            line = line.substring(0, positionOfOpeningTag) + "<a class=\"extern\" href=\"" + kl + "\">" + kv + "</a>" + line.substring(positionOfClosingTag + LEN_WIKI_CLOSE_EXTERNAL_LINK);
        }
        return line;
    }

    //contributed by [MN]
    /**
     * Processes tags which are connected preformatted text (&lt;pre&gt; &lt;/pre&gt;).
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processPreformattedText(String line) {
        final int positionOfOpeningTag = line.indexOf(WIKI_OPEN_PRE_ESCAPED);
        final int positionOfClosingTag = line.indexOf(WIKI_CLOSE_PRE_ESCAPED);
        //both <pre> and </pre> in the same line
        if ((positionOfOpeningTag >= 0) && (positionOfClosingTag > 0) && !escaped) {
            if (positionOfOpeningTag < positionOfClosingTag) {
                String preformattedText = "<pre style=\"border:dotted;border-width:thin;\">" + line.substring(positionOfOpeningTag + LEN_WIKI_OPEN_PRE_ESCAPED, positionOfClosingTag) + "</pre>";
                preformattedText = preformattedText.replaceAll("!pre!", "!pre!!");
                line = processLineOfWikiCode(line.substring(0, positionOfOpeningTag).replaceAll("!pre!", "!pre!!") + "!pre!txt!" + line.substring(positionOfClosingTag + LEN_WIKI_CLOSE_PRE_ESCAPED).replaceAll("!pre!", "!pre!!"));
                line = line.replaceAll("!pre!txt!", preformattedText);
                line = line.replaceAll("!pre!!", "!pre!");
            } //handles cases like <pre><pre> </pre></pre> <pre> </pre> that would cause an exception otherwise
            else {
                processingPreformattedText = true;
                final String temp1 = processLineOfWikiCode(line.substring(0, positionOfOpeningTag - 1).replaceAll("!tmp!", "!tmp!!") + "!tmp!txt!");
                noList = true;
                final String temp2 = processLineOfWikiCode(line.substring(positionOfOpeningTag));
                noList = false;
                line = temp1.replaceAll("!tmp!txt!", temp2);
                line = line.replaceAll("!tmp!!", "!tmp!");
                processingPreformattedText = false;
            }
        } //start <pre>
        else if ((positionOfOpeningTag >= 0) && !preformattedSpanning && !escaped) {
            processingPreformattedText = true;    //prevent surplus line breaks
            final StringBuilder openBlockQuoteTags = new StringBuilder();  //gets filled with <blockquote>s as needed
            String preformattedText = "<pre style=\"border:dotted;border-width:thin;\">" + line.substring(positionOfOpeningTag + LEN_WIKI_OPEN_PRE_ESCAPED);
            preformattedText = preformattedText.replaceAll("!pre!", "!pre!!");
            //taking care of indented lines
            while (preindented < positionOfOpeningTag && positionOfOpeningTag < line.length() &&
                    line.substring(preindented, positionOfOpeningTag).charAt(0) == WIKI_INDENTION) {
                preindented++;
                openBlockQuoteTags.append(HTML_OPEN_BLOCKQUOTE);
            }
            line = processLineOfWikiCode(line.substring(preindented, positionOfOpeningTag).replaceAll("!pre!", "!pre!!") + "!pre!txt!");
            line = openBlockQuoteTags + line.replaceAll("!pre!txt!", preformattedText);
            line = line.replaceAll("!pre!!", "!pre!");
            preformattedSpanning = true;
        } //end </pre>
        else if ((positionOfClosingTag >= 0) && preformattedSpanning && !escaped) {
            preformattedSpanning = false;
            final StringBuilder endBlockQuoteTags = new StringBuilder(); //gets filled with </blockquote>s as needed
            String preformattedText = line.substring(0, positionOfClosingTag) + "</pre>";
            preformattedText = preformattedText.replaceAll("!pre!", "!pre!!");
            //taking care of indented lines
            while (preindented > 0) {
                endBlockQuoteTags.append(HTML_CLOSE_BLOCKQUOTE);
                preindented--;
            }
            line = processLineOfWikiCode("!pre!txt!" + line.substring(positionOfClosingTag + LEN_WIKI_CLOSE_PRE_ESCAPED).replaceAll("!pre!", "!pre!!"));
            line = line.replaceAll("!pre!txt!", preformattedText) + endBlockQuoteTags;
            line = line.replaceAll("!pre!!", "!pre!");
            processingPreformattedText = false;
        } //Getting rid of surplus </pre>
        else if ((positionOfOpeningTag >= 0) && !preformattedSpanning && !escaped) {
            int posTag;
            while ((posTag = line.indexOf(WIKI_CLOSE_PRE_ESCAPED)) >= 0) {
                line = line.substring(0, posTag) + line.substring(posTag + LEN_WIKI_CLOSE_PRE_ESCAPED);
            }
            line = processLineOfWikiCode(line);
        }
        return line;
    }

    //method contributed by [MN]
    /** Creates table of contents for a wiki page.
     * @return HTML fragment
     */
    private StringBuilder createTableOfContents() {
        final StringBuilder directory = new StringBuilder();
        String element;
        int s = 0;
        int level = 1;
        int level1 = 0;
        int level2 = 0;
        int level3 = 0;
        int level4 = 0;
        int level5 = 0;
        int level6 = 0;
        int doubles = 0;
        String anchorext = EMPTY;
        if ((s = tableOfContentElements.size()) > 2) {
            directory.append("<table><tr><td><div class=\"WikiTOCBox\">\n");
            for (int i = 0; i < s; i++) {
                if (i >= tableOfContentElements.size()) {
                    break;
                }
                element = tableOfContentElements.get(i);
                if (element == null) {
                    continue;
                }
                //counting double headlines
                doubles = 0;
                for (int j = 0; j < i; j++) {
                    if (j >= tableOfContentElements.size()) {
                        break;
                    }
                    String d = tableOfContentElements.get(j);
                    if (d == null || d.length() < 1) {
                        continue;
                    }
                    String a = d.substring(1).replaceAll(" ", "_").replaceAll(REGEX_NOT_CHAR_NUM_OR_UNDERSCORE, EMPTY);
                    String b = element.substring(1).replaceAll(" ", "_").replaceAll(REGEX_NOT_CHAR_NUM_OR_UNDERSCORE, EMPTY);
                    if (a.equals(b)) {
                        doubles++;
                    }
                }
                //if there are doubles, create anchorextension
                if (doubles > 0) {
                    anchorext = "_" + (doubles + 1);
                }

                final char l = element.charAt(0);
                String temp = "";
                if (Arrays.binarySearch(HEADLINE_LEVEL, l) >= 0 && element.length() > 0) {
                    
                    switch (l) {

                       case SIX: {
                           if (level < 6) {
                                level = 6;
                                level6 = 0;
                            }
                            level6++;
                            temp = element.substring(1);
                            element = level1 + "." + level2 + "." + level3 + "." + level4 + "." + level5 + "." + level6 + " " + temp;
                            directory.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#");
                            break;
                        }
                        case FIVE: {
                            if (level == 1) {
                                level2 = 0;
                                level = 2;
                            }
                            if (level == 3) {
                                level = 2;
                            }
                            level5++;
                            temp = element.substring(1);
                            element = level1 + "." + level2 + "." + level3 + "." + level4 + "." + level5 + " " + temp;
                            directory.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#");
                            break;
                        }
                        case FOUR: {
                            if (level == 1) {
                                level2 = 0;
                                level = 2;
                            }
                            if (level == 3) {
                                level = 2;
                            }
                            level4++;
                            temp = element.substring(1);
                            element = level1 + "." + level2 + "." + level3 + "." + level4 + " " + temp;
                            directory.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#");
                            break;
                        }
                        case THREE: {
                            if (level == 1) {
                                level2 = 0;
                                level = 2;
                            }
                            if (level == 3) {
                                level = 2;
                            }
                            level3++;
                            temp = element.substring(1);
                            element = level1 + "." + level2 + "." + level3 + " " + temp;
                            directory.append("&nbsp;&nbsp;&nbsp;&nbsp;<a href=\"#");
                            break;
                        }
                        case TWO: {
                            if (level == 1) {
                                level2 = 0;
                                level = 2;
                            }
                            if (level == 3) {
                                level = 2;
                            }
                            level2++;
                            temp = element.substring(1);
                            element = level1 + "." + level2 + " " + temp;
                            directory.append("&nbsp;&nbsp;<a href=\"#");
                            break;
                        }
                        case ONE: {
                            if (level > 1) {
                                level = 1;
                                level2 = 0;
                                level3 = 0;
                                level4 = 0;
                                level5 = 0;
                                level6 = 0;
                            }
                            level1++;
                            temp = element.substring(1);
                            element = level1 + ". " + temp;
                            directory.append("<a href=\"#");
                            break;
                        }
                    }

                    directory.append(temp.replaceAll(" ", "_").replaceAll(REGEX_NOT_CHAR_NUM_OR_UNDERSCORE, EMPTY));
                    directory.append(anchorext);
                    directory.append("\" class=\"WikiTOC\">");
                    directory.append(element);
                    directory.append("</a><br />\n");
                }
                anchorext = EMPTY;
            }
            directory.append("</div></td></tr></table>\n");
        }
        if (!tableOfContentElements.isEmpty()) {
            tableOfContentElements.clear();
        }
        return directory;
    }

    /** Replaces two occurences of a substring in a string by a pair of strings if
     * that substring occurs twice in the string. This method is not greedy! You'll
     * have to run it in a loop if you want to replace all occurences of the substring.
     * This method provides special treatment for headlines.
     * @param input the string that something is to be replaced in
     * @param pat substring to be replaced
     * @param repl1 string substring gets replaced by on uneven occurences
     * @param repl2 string substring gets replaced by on even occurences
     */
    //[MN]
    private String pairReplace(String input, final String pat, final String repl1, final String repl2) {
        return pairReplace(input, pat, pat, repl1, repl2);
    }

    private String pairReplace(String input, final String pat1, final String pat2,
            final String repl1, final String repl2) {
        String direlem = null;    //string to keep headlines until they get added to List dirElements
        int firstPosition;
        final int secondPosition;
        final int pat1Len = pat1.length();
        final int pat2Len = pat2.length();
        //replace pattern if a pair of the pattern can be found in the line
        if (((firstPosition = input.indexOf(pat1)) >= 0) && ((secondPosition = input.indexOf(pat2, firstPosition + pat1Len)) >= 0)) {
            //extra treatment for headlines
            if (Arrays.binarySearch(HEADLINE_TAGS, pat1) >= 0) {
                //add anchor and create headline
                direlem = input.substring(firstPosition + pat1Len, secondPosition);
                if (direlem != null) {
                    //counting double headlines
                    int doubles = 0;
                    for (int i = 0; i < tableOfContentElements.size(); i++) {
                        // no element with null value should ever be in directory
                        assert (tableOfContentElements.get(i) != null);

                        if (tableOfContentElements.size() > i && tableOfContentElements.get(i).substring(1).equals(direlem)) {
                            doubles++;
                        }
                    }
                    String anchor = direlem.replaceAll(" ", "_").replaceAll(REGEX_NOT_CHAR_NUM_OR_UNDERSCORE, EMPTY); //replace blanks with underscores and delete everything thats not a regular character, a number or _
                    //if there are doubles, add underscore and number of doubles plus one
                    if (doubles > 0) {
                        anchor = anchor + "_" + (doubles + 1);
                    }
                    input = input.substring(0, firstPosition) + "<a name=\"" + anchor + "\"></a>" + repl1
                            + direlem + repl2 + input.substring(secondPosition + pat2Len);
                    //add headlines to list of headlines (so TOC can be created)
                    if (Arrays.binarySearch(HEADLINE_TAGS, pat1) >= 0) {
                        tableOfContentElements.add((pat1Len - 1) + direlem);
                    }
                }
            } else {
                input = input.substring(0, firstPosition) + repl1
                        + (input.substring(firstPosition + pat1Len, secondPosition)) + repl2
                        + input.substring(secondPosition + pat2Len);
            }
        }
        //recursion if another pair of the pattern can still be found in the line
        if (((firstPosition = input.indexOf(pat1)) >= 0) && (input.indexOf(pat2, firstPosition + pat1Len) >= 0)) {
            input = pairReplace(input, pat1, pat2, repl1, repl2);
        }
        return input;
    }

    /** Replaces wiki tags with HTML tags in one line of text.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    public String processLineOfWikiCode(String line) {
        //If HTML has not been replaced yet (can happen if method gets called in recursion), replace now!
        if ((!replacedHtmlAlready || preformattedSpanning) && line.indexOf(WIKI_CLOSE_PRE_ESCAPED) < 0) {
            line = CharacterCoding.unicode2html(line, true);
            replacedHtmlAlready = true;
        }

        //check if line contains preformatted symbols or if we are in a preformatted sequence already.
        if ((line.indexOf(WIKI_OPEN_PRE_ESCAPED) >= 0) ||
                (line.indexOf(WIKI_CLOSE_PRE_ESCAPED) >= 0) ||
                preformattedSpanning) {
            line = processPreformattedText(line);
        } else {

            //tables first -> wiki-tags in cells can be treated after that
            line = processTable(line);

            // format lines
            if (line.length() > 0 && line.charAt(0) == WIKI_FORMATTED) {
                line = "<tt>" + line.substring(1) + "</tt>";
            }
            if (line.startsWith(WIKI_HR_LINE)) {
                line = "<hr />" + line.substring(LEN_WIKI_HR_LINE);
            }

            // citings contributed by [MN]
            if (line.length() > 0 && line.charAt(0) == WIKI_INDENTION) {
                final StringBuilder head = new StringBuilder();
                final StringBuilder tail = new StringBuilder();
                while (line.length() > 0 && line.charAt(0) == WIKI_INDENTION) {
                    head.append(HTML_OPEN_BLOCKQUOTE);
                    tail.append(HTML_CLOSE_BLOCKQUOTE);
                    line = line.substring(1);
                }
                line = head + line + tail;
            }
            // end contrib [MN]

            // format headers
            line = pairReplace(line, WIKI_HEADLINE_TAG_6, "<h6>", "</h6>");
            line = pairReplace(line, WIKI_HEADLINE_TAG_5, "<h5>", "</h5>");
            line = pairReplace(line, WIKI_HEADLINE_TAG_4, "<h4>", "</h4>");
            line = pairReplace(line, WIKI_HEADLINE_TAG_3, "<h3>", "</h3>");
            line = pairReplace(line, WIKI_HEADLINE_TAG_2, "<h2>", "</h2>");
            line = pairReplace(line, WIKI_HEADLINE_TAG_1, "<h1>", "</h1>");

            line = pairReplace(line, WIKI_EMPHASIZE_3, "<b><i>", "</i></b>");
            line = pairReplace(line, WIKI_EMPHASIZE_2, "<b>", "</b>");
            line = pairReplace(line, WIKI_EMPHASIZE_1, "<i>", "</i>");

            line = pairReplace(line, WIKI_OPEN_STRIKE, WIKI_CLOSE_STRIKE, "<span class=\"strike\">", "</span>");

            line = processUnorderedList(line);
            line = processOrderedList(line);
            line = processDefinitionList(line);

            line = processLinksAndImages(line);

        }

        if (!processingPreformattedText) {
            replacedHtmlAlready = false;
        }
        if (!(line.endsWith(HTML_CLOSE_LIST_ELEMENT) || processingDefList || escape || processingPreformattedText || processingTable || processingCell)) {
            line += "<br />";
        }
        return line;
    }
}