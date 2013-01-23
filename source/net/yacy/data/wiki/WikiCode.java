// WikiCode.java
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
package net.yacy.data.wiki;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.server.serverCore;

/** Provides methods to handle texts that have been posted in the yacyWiki or other
  * parts of YaCy which use wiki code, like the blog or the profile.
  *
  * @author Alexander Schier [AS], Franz Brausze [FB], Marc Nause [MN]
  */
public class WikiCode extends AbstractWikiParser implements WikiParser {

    private static final String EMPTY = "";
    private static final String PIPE_ESCAPED = "&#124;";
    private static final Pattern REGEX_NOT_CHAR_NUM_OR_UNDERSCORE_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");
    private static final Pattern SPACE_PATTERN = Pattern.compile(" ");

    private static enum Tags {
        HEADLINE_1("=", "<h1>", "</h1>"),
        HEADLINE_2("==", "<h2>", "</h2>"),
        HEADLINE_3("===", "<h3>", "</h3>"),
        HEADLINE_4("====", "<h4>", "</h4>"),
        HEADLINE_5("=====", "<h5>", "</h5>"),
        HEADLINE_6("======", "<h6>", "</h6>"),

        EMPHASIZE_1("\'\'", "<i>", "</i>"),
        EMPHASIZE_2("\'\'\'", "<b>", "</b>"),
        EMPHASIZE_3("\'\'\'\'\'", "<b><i>", "</i></b>"),

        STRIKE("&lt;s&gt;", "&lt;/s&gt;", "<span class=\"strike\">", "</span>"),
        UNDERLINE("&lt;u&gt;", "&lt;/u&gt;", "<span class=\"underline\">", "</span>");

        final String openHTML;
        final String closeHTML;
        final String openWiki;
        final String closeWiki;

        final int openWikiLength;
        final int closeWikiLength;

        Tags(final String openWiki, final String closeWiki, final String openHTML, final String closeHTML) {
            if (openHTML == null || closeHTML == null || openWiki == null || closeWiki == null) {
                throw new IllegalArgumentException("Parameter may not be null.");
            }

            this.openHTML = openHTML;
            this.closeHTML = closeHTML;
            this.openWiki = openWiki;
            this.closeWiki = closeWiki;

            this.openWikiLength = openWiki.length();
            this.closeWikiLength = closeWiki.length();
        }

        Tags(final String wiki, final String openHTML, final String closeHTML) {
            this(wiki, wiki, openHTML, closeHTML);
        }
    }

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
    private static final String WIKI_CLOSE_METADATA = "}}";
    private static final String WIKI_OPEN_METADATA = "{{";
    private static final String WIKI_CLOSE_EXTERNAL_LINK = "]";
    private static final String WIKI_OPEN_EXTERNAL_LINK = "[";
    private static final String WIKI_CLOSE_PRE_ESCAPED = "&lt;/pre&gt;";
    private static final String WIKI_HR_LINE = "----";
    private static final String WIKI_IMAGE = "Image:";
    private static final String WIKI_VIDEO_YOUTUBE = "Youtube:";
    private static final String WIKI_VIDEO_VIMEO = "Vimeo:";
    private static final String WIKI_OPEN_PRE_ESCAPED = "&lt;pre&gt;";

    private static final char ASTERISK = '*';
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
    private static final int LEN_WIKI_CLOSE_LINK = WIKI_CLOSE_LINK.length();
    private static final int LEN_WIKI_IMAGE = WIKI_IMAGE.length();
    private static final int LEN_WIKI_VIDEO_YOUTUBE = WIKI_VIDEO_YOUTUBE.length();
    private static final int LEN_WIKI_VIDEO_VIMEO = WIKI_VIDEO_VIMEO.length();
    private static final int LEN_WIKI_OPEN_EXTERNAL_LINK = WIKI_OPEN_EXTERNAL_LINK.length();
    private static final int LEN_WIKI_CLOSE_EXTERNAL_LINK = WIKI_CLOSE_EXTERNAL_LINK.length();
    private static final int LEN_WIKI_HR_LINE = WIKI_HR_LINE.length();
    private static final int LEN_PIPE_ESCAPED = PIPE_ESCAPED.length();
    private static final int LEN_WIKI_OPEN_METADATA = WIKI_OPEN_METADATA.length();

    /** List of properties which can be used in tables. */
    private final static String[] TABLE_PROPERTIES = {"rowspan", "colspan", "vspace", "hspace", "cellspacing", "cellpadding", "border"};

    /** Map which contains possible values for several parameters. */
    private final static Map<String, String[]> PROPERTY_VALUES = new HashMap<String, String[]>();

    /** Tags for different types of headlines in wikiCode. */
    private final static String[] HEADLINE_TAGS =
            new String[]{Tags.HEADLINE_6.openWiki,
                         Tags.HEADLINE_5.openWiki,
                         Tags.HEADLINE_4.openWiki,
                         Tags.HEADLINE_3.openWiki,
                         Tags.HEADLINE_2.openWiki,
                         Tags.HEADLINE_1.openWiki};

    private final static char[] HEADLINE_LEVEL = new char[]{ONE, TWO, THREE, FOUR, FIVE, SIX};

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

    private final String tableStart = "&#123;" + PIPE_ESCAPED;        // {|
    private final String newLine = PIPE_ESCAPED + "-";                // |-
    private final String cellDivider = PIPE_ESCAPED + PIPE_ESCAPED;   // ||
    private final String tableEnd = PIPE_ESCAPED + "&#125;";          // |}
    private final String attribDivider = PIPE_ESCAPED;                // |
    private final int lenTableStart = this.tableStart.length();
    private final int lenCellDivider = this.cellDivider.length();
    private final int lenTableEnd = this.tableEnd.length();
    private final int lenAttribDivider = this.attribDivider.length();

    private enum ListType {
        ORDERED, UNORDERED;
    }

    private String orderedListLevel = EMPTY;
    private String unorderedListLevel = EMPTY;
    private String defListLevel = EMPTY;
    private boolean processingCell = false;             //needed for prevention of double-execution of replaceHTML
    private boolean processingDefList = false;          //needed for definition lists
    private final boolean escape = false;                     //needed for escape
    private final boolean escaped = false;                    //needed for <pre> not getting in the way
    private boolean newRowStart = false;                //needed for the first row not to be empty
    private boolean noList = false;                     //needed for handling of [= and <pre> in lists
    private boolean processingPreformattedText = false; //needed for preformatted text
    private boolean preformattedSpanning = false;       //needed for <pre> and </pre> spanning over several lines
    private boolean replacedHtmlAlready = false;        //indicates if method replaceHTML has been used with line already
    private boolean processingTable = false;            //needed for tables, because they reach over several lines
    private int preindented = 0;                        //needed for indented <pre>s

    private final TableOfContent tableOfContents = new TableOfContent();

    /**
     * Transforms a text which contains wiki code to HTML fragment.
     * @param hostport
     * @param reader contains the text to be transformed.
     * @param length expected length of text, used to create buffer with right size.
     * @return HTML fragment.
     * @throws IOException in case input from reader can not be read.
     */
    @Override
    protected String transform(final String hostport, final BufferedReader reader, final int length)
            throws IOException {
        final StringBuilder out = new StringBuilder(length);
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(processLineOfWikiCode(hostport, line)).append(serverCore.CRLF_STRING);
        }
        out.insert(0, createTableOfContents());
        this.tableOfContents.clear();
        return out.toString();
    }

    private void processHeadline(
            final StringBuilder input,
            final int firstPosition,
            final Tags tags,
            final int secondPosition,
            String direlem)
    {
        //add anchor and create headline
        if ((direlem = input.substring(firstPosition + tags.openWikiLength, secondPosition)) != null) {
            //counting double headlines
            int doubles = 0;
            final Iterator<String> iterator = this.tableOfContents.iterator();
            String element;
            while (iterator.hasNext()) {
                element = iterator.next();
                // no element with null value should ever be in directory
                assert (element != null);
                if (element.substring(1).equals(direlem)) {
                    doubles++;
                }
            }
            String anchor = REGEX_NOT_CHAR_NUM_OR_UNDERSCORE_PATTERN.matcher(SPACE_PATTERN.matcher(direlem).replaceAll("_")).replaceAll(EMPTY); //replace blanks with underscores and delete everything thats not a regular character, a number or _
            //if there are doubles, add underscore and number of doubles plus one
            if (doubles > 0) {
                anchor = anchor + "_" + (doubles + 1);
            }
            final StringBuilder link = new StringBuilder();
            link.append("<a name=\"");
            link.append(anchor);
            link.append("\"></a>");
            link.append(tags.openHTML);
            link.append(direlem);
            link.append(tags.closeHTML);

            input.replace(firstPosition, secondPosition + tags.closeWikiLength, link.toString());

            //add headlines to list of headlines (so TOC can be created)
            if (Arrays.binarySearch(HEADLINE_TAGS, tags.openWiki) >= 0) {
                this.tableOfContents.add((tags.openWikiLength - 1) + direlem);
            }
        }
    }

    // contributed by [FB], changes by [MN]
    /**
     * Processes tags which are connected to tables.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processTable(final String line) {

        final StringBuilder out = new StringBuilder();

        if (line.startsWith(this.tableStart) && !this.processingTable) {
            this.processingTable = true;
            this.newRowStart = true;
            out.append("<table");
            if (line.trim().length() > this.lenTableStart) {
                out.append(filterTableProperties(line.substring(this.lenTableStart).trim()));
            }
            out.append(">");
        } else if (line.startsWith(this.newLine) && this.processingTable) {          // new row
            if (!this.newRowStart) {
                out.append("\t</tr>\n");
            } else {
                this.newRowStart = false;
            }
            out.append("\t<tr>");
        } else if (line.startsWith(this.cellDivider) && this.processingTable) {
            out.append("\t\t<td");
            final int cellEnd = (line.indexOf(this.cellDivider, this.lenCellDivider) > 0) ? (line.indexOf(this.cellDivider, this.lenCellDivider)) : (line.length());
            int propEnd = line.indexOf(this.attribDivider, this.lenCellDivider);
            final int occImage = line.indexOf("[[Image:", this.lenCellDivider);
            final int occEscape = line.indexOf("[=", this.lenCellDivider);
            //If resultOf("[[Image:") is less than propEnd, that means that there is no
            //property for this cell, only an image. Without this, YaCy could get confused
            //by a | in [[Image:picture.png|alt-text]] or [[Image:picture.png|alt-text]]
            //Same for [= (part of [= =])
            if ((propEnd > this.lenCellDivider) && ((occImage > propEnd) || (occImage < 0)) && ((occEscape > propEnd) || (occEscape < 0))) {
                propEnd = line.indexOf(this.attribDivider, this.lenCellDivider) + this.lenAttribDivider;
            } else {
                propEnd = cellEnd;
            }
            // both point at same place => new line
            if (propEnd == cellEnd) {
                propEnd = this.lenCellDivider;
            } else {
                out.append(filterTableProperties(line.substring(this.lenCellDivider, propEnd - this.lenAttribDivider).trim()));
            }
            // quick&dirty fix [MN]
            if (propEnd > cellEnd) {
                propEnd = this.lenCellDivider;
            }
            this.processingTable = false;
            this.processingCell = true;
            out.append(">");
            out.append(processTable(line.substring(propEnd, cellEnd).trim()));
            out.append("</td>");
            this.processingTable = true;
            this.processingCell = false;
            if (cellEnd < line.length()) {
                out.append("\n");
                out.append(processTable(line.substring(cellEnd)));
            }
        } else if (line.startsWith(this.tableEnd) && (this.processingTable)) {          // Table end
            this.processingTable = false;
            out.append("\t</tr>\n</table>");
            out.append(line.substring(this.lenTableEnd));
        } else {
            out.append(line);
        }
        return out.toString();
    }

    // contributed by [MN], changes by [FB]
    /** Takes possible table properties and tests if they are valid.
     * Valid in this case means if they are a property for the table, tr or td
     * tag as stated in the HTML Pocket Reference by Jennifer Niederst (1st edition)
     * The method is important to avoid XSS attacks on the wiki via table properties.
     * @param properties String which may contain several table properties and/or junk.
     * @return String containing only table properties.
     */
    private static StringBuilder filterTableProperties(final String properties) {
        final String[] values = properties.replaceAll("&quot;", EMPTY).split("[= ]");     //splitting the string at = and blanks
        final StringBuilder stringBuilder = new StringBuilder(properties.length());
        String key, value;
        String[] posVals;
        final int numberOfValues = values.length;
        for (int i = 0; i < numberOfValues; i++) {
            key = values[i].trim();
            if ("nowrap".equals(key)) {
                appendKeyValuePair("nowrap", "nowrap", stringBuilder);
            } else if (i + 1 < numberOfValues) {
                value = values[++i].trim();
                if (("summary".equals(key))
                        || ("bgcolor".equals(key) && value.matches("#{0,1}[0-9a-fA-F]{1,6}|[a-zA-Z]{3,}"))
                        || (("width".equals(key) || "height".equals(key)) && value.matches("\\d+%{0,1}"))
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
    private static StringBuilder appendKeyValuePair(final String key, final String value, final StringBuilder stringBuilder) {
        return stringBuilder.append(" ").append(key).append("=\"").append(value).append("\"");
    }

    /**
     * Processes tags which are connected to ordered lists.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processOrderedList(final String line) {
        return processList(line, ListType.ORDERED);
    }

    /**
     * Processes tags which are connected to unordered lists.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processUnorderedList(final String line) {
        return processList(line, ListType.UNORDERED);
    }

    /**
     * Processes tags which are connected to ordered or unordered lists.
     * @author contains code by [AS]
     * @param line line of text to be transformed from wiki code to HTML
     * @param listType type of tags to be processed
     * @return HTML fragment
     */
    private String processList(final String line, final ListType listType) {

        final String ret;

        if (!this.noList) {    //lists only get processed if not forbidden (see code for [= and <pre>).

            String listLevel;
            final String htmlOpenList;
            final String htmlCloseList;
            final char symbol;

            if (ListType.ORDERED.equals(listType)) {
                listLevel = this.orderedListLevel;
                symbol = '#';
                htmlOpenList = HTML_OPEN_ORDERED_LIST;
                htmlCloseList = HTML_CLOSE_ORDERED_LIST;
            } else if (ListType.UNORDERED.equals(listType)) {
                listLevel = this.unorderedListLevel;
                symbol = ASTERISK;
                htmlOpenList = HTML_OPEN_UNORDERED_LIST;
                htmlCloseList = HTML_CLOSE_UNORDERED_LIST;
            } else {
                throw new IllegalArgumentException("Unknown list type " + listType);
            }

            if (line.startsWith(listLevel + symbol)) {      //more #
                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(htmlOpenList);
                stringBuilder.append(serverCore.CRLF_STRING);
                stringBuilder.append(HTML_OPEN_LIST_ELEMENT);
                stringBuilder.append(line.substring(listLevel.length() + 1).trim());
                stringBuilder.append(HTML_CLOSE_LIST_ELEMENT);
                ret = stringBuilder.toString();
                listLevel += symbol;
            } else if (!listLevel.isEmpty() && line.startsWith(listLevel)) {           //equal number of #
                final StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(HTML_OPEN_LIST_ELEMENT);
                stringBuilder.append(line.substring(listLevel.length()).trim());
                stringBuilder.append(HTML_CLOSE_LIST_ELEMENT);
                ret = stringBuilder.toString();
            } else if (!listLevel.isEmpty()) {            //less #
                final StringBuilder stringBuilder = new StringBuilder();
                final StringBuilder tmp = new StringBuilder();

                int i = listLevel.length();
                while (!line.startsWith(listLevel.substring(0, i))) {
                    tmp.append(htmlCloseList);
                    i--;
                }
                listLevel = listLevel.substring(0, i);

                final int startOfContent = listLevel.length();

                if (startOfContent > 0) {
                    stringBuilder.append(tmp);
                    stringBuilder.append(HTML_OPEN_LIST_ELEMENT);
                    stringBuilder.append(line.substring(startOfContent).trim());
                    stringBuilder.append(HTML_CLOSE_LIST_ELEMENT);
                } else {
                    stringBuilder.append(tmp);
                    stringBuilder.append(line.substring(startOfContent).trim());
                }
                ret = stringBuilder.toString();
            }  else {
                ret = line;
            }

            if (ListType.ORDERED.equals(listType)) {
                this.orderedListLevel = listLevel;
            } else if (ListType.UNORDERED.equals(listType)) {
                this.unorderedListLevel = listLevel;
            }
        } else {
            ret = line;
        }
        return ret;
    }

    /**
     * Processes tags which are connected to definition lists.
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processDefinitionList(final String line) {
        final String ret;

        if (!this.noList) {    //lists only get processed if not forbidden (see code for [= and <pre>). [MN]

            if (line.startsWith(this.defListLevel + ";")) { //more semicolons
                final String copyOfLine = line.substring(this.defListLevel.length() + 1);
                final int positionOfOpeningTag;
                if ((positionOfOpeningTag = copyOfLine.indexOf(':',0)) > 0) {
                    final String definitionItem = copyOfLine.substring(0, positionOfOpeningTag);
                    final String definitionDescription = copyOfLine.substring(positionOfOpeningTag + 1);
                    final StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(HTML_OPEN_DEFINITION_LIST);
                    stringBuilder.append(HTML_OPEN_DEFINITION_ITEM);
                    stringBuilder.append(definitionItem);
                    stringBuilder.append(HTML_CLOSE_DEFINITION_ITEM);
                    stringBuilder.append(HTML_OPEN_DEFINITION_DESCRIPTION);
                    stringBuilder.append(definitionDescription);
                    this.processingDefList = true;
                    ret = stringBuilder.toString();
                } else {
                    ret = line;
                }
                this.defListLevel += ";";
            } else if (!this.defListLevel.isEmpty() && line.startsWith(this.defListLevel)) { //equal number of semicolons
                final String copyOfLine = line.substring(this.defListLevel.length());
                final int positionOfOpeningTag;
                if ((positionOfOpeningTag = copyOfLine.indexOf(':',0)) > 0) {
                    final String definitionItem = copyOfLine.substring(0, positionOfOpeningTag);
                    final String definitionDescription = copyOfLine.substring(positionOfOpeningTag + 1);
                    final StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(HTML_OPEN_DEFINITION_ITEM);
                    stringBuilder.append(definitionItem);
                    stringBuilder.append(HTML_CLOSE_DEFINITION_ITEM);
                    stringBuilder.append(HTML_OPEN_DEFINITION_DESCRIPTION);
                    stringBuilder.append(definitionDescription);
                    this.processingDefList = true;
                    ret = stringBuilder.toString();
                } else {
                    ret = line;
                }
            } else if (!this.defListLevel.isEmpty()) { //less semicolons
                int i = this.defListLevel.length();
                String tmp = EMPTY;
                while (!line.startsWith(this.defListLevel.substring(0, i))) {
                    tmp = HTML_CLOSE_DEFINITION_DESCRIPTION + HTML_CLOSE_DEFINITION_LIST;
                    i--;
                }
                this.defListLevel = this.defListLevel.substring(0, i);
                int positionOfOpeningTag = this.defListLevel.length();
                if (!this.defListLevel.isEmpty()) {
                    final String copyOfLine = line.substring(positionOfOpeningTag);
                    if ((positionOfOpeningTag = copyOfLine.indexOf(':',0)) > 0) {
                        final String definitionItem = copyOfLine.substring(0, positionOfOpeningTag);
                        final String definitionDescription = copyOfLine.substring(positionOfOpeningTag + 1);
                        final StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(tmp);
                        stringBuilder.append(HTML_OPEN_DEFINITION_ITEM);
                        stringBuilder.append(definitionItem);
                        stringBuilder.append(HTML_CLOSE_DEFINITION_ITEM);
                        stringBuilder.append(HTML_OPEN_DEFINITION_DESCRIPTION);
                        stringBuilder.append(definitionDescription);
                        this.processingDefList = true;
                        ret = stringBuilder.toString();
                    } else {
                        ret = line;
                    }
                } else {
                    final StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(tmp);
                    stringBuilder.append(line.substring(positionOfOpeningTag));
                    ret = stringBuilder.toString();
                }
            } else {
                ret = line;
            }
        } else {
            ret = line;
        }
        return ret;
    }

    /**
     * Processes tags which are connected to links and images.
     * @author [AS], [MN]
     * @param hostport
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private static String processLinksAndImages(final String hostport, String line) {

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
                        if (("bottom".equals(align))
                                || ("center".equals(align))
                                || ("left".equals(align))
                                || ("middle".equals(align))
                                || ("right".equals(align))
                                || ("top".equals(align))) {
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
                if (kl.indexOf("://",0) < 1) {
                    kl = "http://" + hostport + "/" + kl;
                }

                line = line.substring(0, positionOfOpeningTag) + "<img src=\"" + kl + "\"" + align + alt + ">" + line.substring(positionOfClosingTag + LEN_WIKI_CLOSE_LINK);
            }            
            // this is the part of the code that is responsible for Youtube video links supporting only the video ID as parameter
            else if (kl.startsWith(WIKI_VIDEO_YOUTUBE)) {
            	kl = kl.substring(LEN_WIKI_VIDEO_YOUTUBE);
            	line = line.substring(0, positionOfOpeningTag) + "" + "<object width=\"425\" height=\"350\"><param name=\"movie\" value=\"http://www.youtube.com/v/" + kl + "\"></param><param name=\"wmode\" value=\"transparent\"></param><embed src=\"http://www.youtube.com/v/" + kl + "\" type=\"application/x-shockwave-flash\" wmode=\"transparent\" width=\"425\" height=\"350\"></embed></object>";            			
            }
            // this is the part of the code that is responsible for Vimeo video links supporting only the video ID as parameter
            else if (kl.startsWith(WIKI_VIDEO_VIMEO)) {
            	kl = kl.substring(LEN_WIKI_VIDEO_VIMEO);
            	line = line.substring(0, positionOfOpeningTag) + "" + "<iframe src=\"http://player.vimeo.com/video/" + kl + "\" width=\"425\" height=\"350\" frameborder=\"0\" webkitAllowFullScreen mozallowfullscreen allowFullScreen></iframe>";            			
            }
            // if it's no image, it might be an internal link
            else {
                if ((p = kl.indexOf(PIPE_ESCAPED)) > 0) {
                    kv = kl.substring(p + LEN_PIPE_ESCAPED);
                    kl = kl.substring(0, p);
                } else {
                    kv = kl;
                }
                line = line.substring(0, positionOfOpeningTag) + "<a class=\"known\" href=\"Wiki.html?page=" + kl + "\">" + kv + "</a>" + line.substring(positionOfClosingTag + LEN_WIKI_CLOSE_LINK); // oob exception in append() !
            }
        }

        // external links
        while ((positionOfOpeningTag = line.indexOf(WIKI_OPEN_EXTERNAL_LINK)) >= 0) {
            positionOfClosingTag = line.indexOf(WIKI_CLOSE_EXTERNAL_LINK, positionOfOpeningTag + LEN_WIKI_OPEN_EXTERNAL_LINK);
            if (positionOfClosingTag <= positionOfOpeningTag) {
                break;
            }
            kl = line.substring(positionOfOpeningTag + LEN_WIKI_OPEN_EXTERNAL_LINK, positionOfClosingTag);
            if ((p = kl.indexOf(' ',0)) > 0) {
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
            if (kl.indexOf("://",0) < 1) {
                kl = "http://" + hostport + "/" + kl;
            }
            line = line.substring(0, positionOfOpeningTag) + "<a class=\"extern\" href=\"" + kl + "\">" + kv + "</a>" + line.substring(positionOfClosingTag + LEN_WIKI_CLOSE_EXTERNAL_LINK);
        }
        return line;
    }

    /**
     * Processes tags which are connected preformatted text (&lt;pre&gt; &lt;/pre&gt;).
     * @param hostport
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processPreformattedText(final String hostport, String line) {
        if (!this.escaped) {
            final int positionOfOpeningTag = line.indexOf(WIKI_OPEN_PRE_ESCAPED);
            final int positionOfClosingTag = line.indexOf(WIKI_CLOSE_PRE_ESCAPED);
            //both <pre> and </pre> in the same line
            if (positionOfOpeningTag >= 0 && positionOfClosingTag > 0) {
                if (positionOfOpeningTag < positionOfClosingTag) {
                    final StringBuilder preformattedText = new StringBuilder();
                    preformattedText.append("<pre style=\"border:dotted;border-width:thin;\">");
                    preformattedText.append(line.substring(positionOfOpeningTag + LEN_WIKI_OPEN_PRE_ESCAPED, positionOfClosingTag));
                    preformattedText.append("</pre>");

                    line = processLineOfWikiCode(hostport, line.substring(0, positionOfOpeningTag).replaceAll("!pre!", "!pre!!") + "!pre!txt!" + line.substring(positionOfClosingTag + LEN_WIKI_CLOSE_PRE_ESCAPED).replaceAll("!pre!", "!pre!!"));
                    line = line.replace("!pre!txt!", preformattedText.toString().replaceAll("!pre!", "!pre!!"));
                    line = line.replaceAll("!pre!!", "!pre!");
                } //handles cases like <pre><pre> </pre></pre> <pre> </pre> that would cause an exception otherwise
                else {
                    this.processingPreformattedText = true;
                    final String temp1 = processLineOfWikiCode(hostport, line.substring(0, positionOfOpeningTag - 1).replaceAll("!tmp!", "!tmp!!") + "!tmp!txt!");
                    this.noList = true;
                    final String temp2 = processLineOfWikiCode(hostport, line.substring(positionOfOpeningTag));
                    this.noList = false;
                    line = temp1.replaceAll("!tmp!txt!", temp2);
                    line = line.replaceAll("!tmp!!", "!tmp!");
                    this.processingPreformattedText = false;
                }
            } //start <pre>
            else if (positionOfOpeningTag >= 0 && !this.preformattedSpanning) {
                this.processingPreformattedText = true;    //prevent surplus line breaks
                final StringBuilder openBlockQuoteTags = new StringBuilder();  //gets filled with <blockquote>s as needed
                String preformattedText = "<pre style=\"border:dotted;border-width:thin;\">" + line.substring(positionOfOpeningTag + LEN_WIKI_OPEN_PRE_ESCAPED);
                preformattedText = preformattedText.replaceAll("!pre!", "!pre!!");
                //taking care of indented lines
                while (this.preindented < positionOfOpeningTag && positionOfOpeningTag < line.length() &&
                        line.substring(this.preindented, positionOfOpeningTag).charAt(0) == WIKI_INDENTION) {
                    this.preindented++;
                    openBlockQuoteTags.append(HTML_OPEN_BLOCKQUOTE);
                }
                line = processLineOfWikiCode(hostport, line.substring(this.preindented, positionOfOpeningTag).replaceAll("!pre!", "!pre!!") + "!pre!txt!");
                line = openBlockQuoteTags + line.replace("!pre!txt!", preformattedText);
                line = line.replaceAll("!pre!!", "!pre!");
                this.preformattedSpanning = true;
            } //end </pre>
            else if (positionOfClosingTag >= 0 && this.preformattedSpanning) {
                this.preformattedSpanning = false;
                final StringBuilder endBlockQuoteTags = new StringBuilder(); //gets filled with </blockquote>s as needed
                String preformattedText = line.substring(0, positionOfClosingTag) + "</pre>";
                preformattedText = preformattedText.replaceAll("!pre!", "!pre!!");
                //taking care of indented lines
                while (this.preindented > 0) {
                    endBlockQuoteTags.append(HTML_CLOSE_BLOCKQUOTE);
                    this.preindented--;
                }
                line = processLineOfWikiCode(hostport, "!pre!txt!" + line.substring(positionOfClosingTag + LEN_WIKI_CLOSE_PRE_ESCAPED).replaceAll("!pre!", "!pre!!"));
                line = line.replace("!pre!txt!", preformattedText) + endBlockQuoteTags;
                line = line.replaceAll("!pre!!", "!pre!");
                this.processingPreformattedText = false;
            } //Getting rid of surplus </pre>
            else if (positionOfOpeningTag >= 0 && !this.preformattedSpanning) {
                int posTag;
                while ((posTag = line.indexOf(WIKI_CLOSE_PRE_ESCAPED)) >= 0) {
                    line = line.substring(0, posTag) + line.substring(posTag + LEN_WIKI_CLOSE_PRE_ESCAPED);
                }
                line = processLineOfWikiCode(hostport, line);
            }
        }
        return line;
    }

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
        if ((s = this.tableOfContents.size()) > 2) {
            directory.append("<table><tr><td><div class=\"WikiTOCBox\">\n");
            for (int i = 0; i < s; i++) {
                if (i >= this.tableOfContents.size()) {
                    break;
                }
                element = this.tableOfContents.get(i);
                if (element == null) {
                    continue;
                }
                //counting double headlines
                doubles = 0;
                for (int j = 0; j < i; j++) {
                    if (j >= this.tableOfContents.size()) {
                        break;
                    }
                    final String d = this.tableOfContents.get(j);
                    if (d == null || d.isEmpty()) {
                        continue;
                    }
                    final String a = REGEX_NOT_CHAR_NUM_OR_UNDERSCORE_PATTERN.matcher(SPACE_PATTERN.matcher(d.substring(1)).replaceAll("_")).replaceAll(EMPTY);
                    final String b = REGEX_NOT_CHAR_NUM_OR_UNDERSCORE_PATTERN.matcher(SPACE_PATTERN.matcher(element.substring(1)).replaceAll("_")).replaceAll(EMPTY);
                    if (a.equals(b)) {
                        doubles++;
                    }
                }
                //if there are doubles, create anchor extension
                if (doubles > 0) {
                    anchorext = "_" + (doubles + 1);
                }

                final char l = element.charAt(0);
                String temp = "";
                if (Arrays.binarySearch(HEADLINE_LEVEL, l) >= 0 && !element.isEmpty()) {

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
                        default: {
                            throw new IllegalArgumentException("illegal headline level: " + l);
                        }
                    }
                    directory.append(REGEX_NOT_CHAR_NUM_OR_UNDERSCORE_PATTERN.matcher(SPACE_PATTERN.matcher(temp).replaceAll("_")).replaceAll(EMPTY));
                    directory.append(anchorext);
                    directory.append("\" class=\"WikiTOC\">");
                    directory.append(element);
                    directory.append("</a><br />\n");
                }
                anchorext = EMPTY;
            }
            directory.append("</div></td></tr></table>\n");
        }

        return directory;
    }

    /**
     * Replaces the wiki representation of tags with the HTML representation.
     * @param input String which potentially contains tags to be replaced.
     * @param tags tags to be replaced.
     * @return String with replaced tags.
     */
    private String tagReplace(final String input, final Tags tags) {
        final String direlem = null;    //string to keep headlines until they get added to List dirElements

        final StringBuilder stringBuilder = new StringBuilder(input);

        int firstPosition = 0;
        int secondPosition = 0;
        //replace pattern if a pair of the pattern can be found in the line
        while (((firstPosition = stringBuilder.indexOf(tags.openWiki, secondPosition)) >= 0) &&
                ((secondPosition = stringBuilder.indexOf(tags.closeWiki, firstPosition + tags.openWikiLength)) >= 0)) {

            //extra treatment for headlines
            if (Arrays.binarySearch(HEADLINE_TAGS, tags.openWiki) >= 0) {
                processHeadline(stringBuilder, firstPosition, tags, secondPosition, direlem);
            } else {
                final int oldLength = stringBuilder.length();
                stringBuilder.replace(firstPosition, firstPosition + tags.openWikiLength, tags.openHTML);
                secondPosition += stringBuilder.length() - oldLength;
                stringBuilder.replace(secondPosition, secondPosition + tags.closeWikiLength, tags.closeHTML);
            }
        }

        return stringBuilder.toString();
    }

    /** Replaces wiki tags with HTML tags in one line of text.
     * @param hostport
     * @param line line of text to be transformed from wiki code to HTML
     * @return HTML fragment
     */
    private String processLineOfWikiCode(final String hostport, String line) {
        //If HTML has not been replaced yet (can happen if method gets called in recursion), replace now!
        line = processMetadata(line);
        if ((!this.replacedHtmlAlready || this.preformattedSpanning) && line.indexOf(WIKI_CLOSE_PRE_ESCAPED) < 0) {
            line = CharacterCoding.unicode2html(line, true);
            this.replacedHtmlAlready = true;
        }

        //check if line contains preformatted symbols or if we are in a preformatted sequence already.
        if ((line.indexOf(WIKI_OPEN_PRE_ESCAPED) >= 0) ||
                (line.indexOf(WIKI_CLOSE_PRE_ESCAPED) >= 0) ||
                this.preformattedSpanning) {
            line = processPreformattedText(hostport, line);
        } else {

            //tables first -> wiki-tags in cells can be treated after that
            line = processTable(line);

            // format lines
            if (!line.isEmpty() && line.charAt(0) == WIKI_FORMATTED) {
                line = "<tt>" + line.substring(1) + "</tt>";
            }
            if (line.startsWith(WIKI_HR_LINE)) {
                line = "<hr />" + line.substring(LEN_WIKI_HR_LINE);
            }

            if (!line.isEmpty() && line.charAt(0) == WIKI_INDENTION) {
                final StringBuilder head = new StringBuilder();
                final StringBuilder tail = new StringBuilder();
                while (!line.isEmpty() && line.charAt(0) == WIKI_INDENTION) {
                    head.append(HTML_OPEN_BLOCKQUOTE);
                    tail.append(HTML_CLOSE_BLOCKQUOTE);
                    line = line.substring(1);
                }
                line = head + line + tail;
            }

            // format headers
            line = tagReplace(line, Tags.HEADLINE_6);
            line = tagReplace(line, Tags.HEADLINE_5);
            line = tagReplace(line, Tags.HEADLINE_4);
            line = tagReplace(line, Tags.HEADLINE_3);
            line = tagReplace(line, Tags.HEADLINE_2);
            line = tagReplace(line, Tags.HEADLINE_1);

            line = tagReplace(line, Tags.EMPHASIZE_3);
            line = tagReplace(line, Tags.EMPHASIZE_2);
            line = tagReplace(line, Tags.EMPHASIZE_1);

            line = tagReplace(line, Tags.STRIKE);
            line = tagReplace(line, Tags.UNDERLINE);

            line = processUnorderedList(line);
            line = processOrderedList(line);
            line = processDefinitionList(line);

            line = processLinksAndImages(hostport, line);

        }

        if (!this.processingPreformattedText) {
            this.replacedHtmlAlready = false;
        }
        if (!(line.endsWith(HTML_CLOSE_LIST_ELEMENT) || this.processingDefList || this.escape || this.processingPreformattedText || this.processingTable || this.processingCell)) {
            line += "<br />";
        }
        return line;
    }


    private static String processMetadata(String line) {
        int p, q, s = 0;
        while ((p = line.indexOf(WIKI_OPEN_METADATA, s)) >= 0 && (q = line.indexOf(WIKI_CLOSE_METADATA, p + 1)) >= 0) {
            s = q; // continue with next position
            final String a = line.substring(p + LEN_WIKI_OPEN_METADATA, q);
            if (a.toLowerCase().startsWith("coordinate")) {
                // parse Geographical Coordinates as described in
                // http://en.wikipedia.org/wiki/Wikipedia:Manual_of_Style_%28dates_and_numbers%29#Geographical_coordinates
                // looks like:
                // {{Coord|57|18|22.5|N|4|27|32.7|W|display=title}}
                // however, such information does not appear as defined above but as:
                // {{coordinate|NS=52.205944|EW=0.117593|region=GB-CAM|type=landmark}}
                // {{coordinate|NS=43/50/29/N|EW=73/23/17/W|type=landmark|region=US-NY}}
                // and if passed through this parser:
                // {{Coordinate |NS 45/37/43.0/N |EW. 07/58/41.0/E |type=landmark |region=IT-BI}} ## means: degree/minute/second
                // {{Coordinate |NS 51.48994 |EW. 7.33249 |type=landmark |region=DE-NW}}
                final String b[] = a.split("\\|");
                float lon = 0.0f, lat = 0.0f;
                float lonm = 0.0f, latm = 0.0f;
                String lono = "E", lato = "N";
                String name = "";
                for (final String c: b) {
                    if (c.toLowerCase().startsWith("name=")) {
                        name = c.substring(5);
                    }
                    if (c.toUpperCase().startsWith("NS=")) {
                        final String d[] = c.substring(3).split("/");
                        if (d.length == 1) {float l = Float.parseFloat(d[0]); if (l < 0) {lato = "S"; l = -l;} lat = (float) Math.floor(l); latm = 60.0f * (l - lat);}
                        else if (d.length == 2) {lat = Float.parseFloat(d[0]); latm = Float.parseFloat(d[1]);}
                        else if (d.length == 3) {lat = Float.parseFloat(d[0]); latm = Float.parseFloat(d[1]) + Float.parseFloat(d[2]) / 60.0f;}
                        if (d[d.length-1].toUpperCase().equals("S")) {}
                    }
                    if (c.toUpperCase().startsWith("EW=")) {
                        final String d[] = c.substring(3).split("/");
                        if (d.length == 1) {float l = Float.parseFloat(d[0]); if (l < 0) {lono = "W"; l = -l;} lon = (float) Math.floor(l); lonm = 60.0f * (l - lon);}
                        else if (d.length == 2) {lon = Float.parseFloat(d[0]); lonm = Float.parseFloat(d[1]);}
                        else if (d.length == 3) {lon = Float.parseFloat(d[0]); lonm = Float.parseFloat(d[1]) + Float.parseFloat(d[2]) / 60.0f;}
                        if (d[d.length-1].toUpperCase().equals("w")) {lon = -lon; lonm = -lonm;}
                    }
                }
                if (lon != 0.0d && lat != 0.0d) {
                    // replace this with a format that the html parser can understand
                    line = line.substring(0, p) + (name.length() > 0 ? (" " + name) : "") + " <nobr> " + lato + " " + lat + "\u00B0 " + latm + "'</nobr><nobr>" + lono + " " + lon + "\u00B0 " + lonm + "'</nobr> " + line.substring(q + WIKI_CLOSE_METADATA.length());
                    s = p;
                    continue;
                }
            }
        }
        return line;
    }

    private class TableOfContent {

        private final List<String> toc = new ArrayList<String>();   // needs to be list which ensures order

        int size() {
            return this.toc.size();
        }

        String get(final int index) {
            return this.toc.get(index);
        }

        synchronized boolean add(final String element) {
            return this.toc.add(element);
        }

        Iterator<String> iterator() {
            return this.toc.iterator();
        }

        void clear() {
            this.toc.clear();
        }
    }
}
