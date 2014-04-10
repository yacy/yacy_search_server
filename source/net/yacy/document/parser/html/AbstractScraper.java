// AbstractScraper.java
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// You agree that the Author(s) is (are) not responsible for cost,
// loss of data or any harm that may be caused by usage of this softare or
// this documentation. The usage of this software is on your own risk. The
// installation and usage (starting/running) of this software may allow other
// people or application to access your computer and any attached devices and
// is highly dependent on the configuration of the software which must be
// done by the user of the software;the author(s) is (are) also
// not responsible for proper configuration and usage of the software, even
// if provoked by documentation provided together with the software.
//
// THE SOFTWARE THAT FOLLOWS AS ART OF PROGRAMMING BELOW THIS SECTION
// IS PUBLISHED UNDER THE GPL AS DOCUMENTED IN THE FILE gpl.txt ASIDE THIS
// FILE AND AS IN http://www.gnu.org/licenses/gpl.txt
// ANY CHANGES TO THIS FILE ACCORDING TO THE GPL CAN BE DONE TO THE
// LINES THAT FOLLOWS THIS COPYRIGHT NOTICE HERE, BUT CHANGES MUST NOT
// BE DONE ABOVE OR INSIDE THE COPYRIGHT NOTICE. A RE-DISTRIBUTION
// MUST CONTAIN THE INTACT AND UNCHANGED COPYRIGHT NOTICE.
// CONTRIBUTIONS AND CHANGES TO THE PROGRAM CODE SHOULD BE MARKED AS SUCH.

package net.yacy.document.parser.html;

import java.util.Set;

import net.yacy.kelondro.util.MemoryControl;

public abstract class AbstractScraper implements Scraper {

    protected static final String EMPTY_STRING = new String();

    public static final char sp = ' ';
    public static final char lb = '<';
    public static final char rb = '>';
    public static final char sl = '/';

    private Set<String> tags0;
    private Set<String> tags1;

    /**
     * create a scraper. the tag sets must contain tags in lowercase!
     * @param tags0
     * @param tags1
     */
    public AbstractScraper(final Set<String> tags0, final Set<String> tags1) {
        this.tags0  = tags0;
        this.tags1  = tags1;
    }

    @Override
    public boolean isTag0(final String tag) {
        return (this.tags0 != null) && (this.tags0.contains(tag.toLowerCase()));
    }

    @Override
    public boolean isTag1(final String tag) {
        return (this.tags1 != null) && (this.tags1.contains(tag.toLowerCase()));
    }

    //the 'missing' method that shall be implemented:
    @Override
    public abstract void scrapeText(char[] text, String insideTag);

    // the other methods must take into account to construct the return value correctly
    @Override
    public abstract void scrapeTag0(ContentScraper.Tag tag);

    @Override
    public abstract void scrapeTag1(ContentScraper.Tag tag);

    public static String stripAllTags(final char[] s) {
        if (s.length > 80 && !MemoryControl.request(s.length * 2, false)) return "";
        final StringBuilder r = new StringBuilder(s.length);
        int bc = 0;
        for (final char c : s) {
            if (c == lb) {
                bc++;
                if (r.length() > 0 && r.charAt(r.length() - 1) != sp) r.append(sp);
            } else if (c == rb) {
                bc--;
            } else if (bc <= 0) {
                r.append(c);
            }
        }
        return r.toString().trim();
    }

    protected final static String cleanLine(final String s) {
        if (!MemoryControl.request(s.length() * 2, false)) return EMPTY_STRING;
        final StringBuilder sb = new StringBuilder(s.length());
        char l = ' ';
        char c;
        for (int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            if (c < ' ') c = ' ';
            if (c == ' ') {
                if (l != ' ') sb.append(c);
            } else {
                sb.append(c);
            }
            l = c;
        }

        // return result
        return sb.toString().trim();
    }

    @Override
    public void close() {
        // free resources
        this.tags0 = null;
        this.tags1 = null;
    }

    public static void main(String[] args) {
        String t = "<script src=\"navigation.js\" type=\"text/javascript\"></script>\\n <script src=\"../js/prototype.js\" type=\"text/javascript\"></script>";
        System.out.println("'" + stripAllTags(t.toCharArray()) + "'");
    }

}


