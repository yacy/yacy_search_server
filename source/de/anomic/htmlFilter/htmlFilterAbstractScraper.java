// htmlFilterAbstractScraper.java 
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
// last major change: 18.02.2004
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

package de.anomic.htmlFilter;

import java.util.HashSet;
import java.util.Properties;

public abstract class htmlFilterAbstractScraper implements htmlFilterScraper {

    public static final char lb = '<';
    public static final char rb = '>';
    public static final char sl = '/';
 
    private HashSet<String> tags0;
    private HashSet<String> tags1;

    /**
     * create a scraper. the tag sets must contain tags in lowercase!
     * @param tags0
     * @param tags1
     */
    public htmlFilterAbstractScraper(final HashSet<String> tags0, final HashSet<String> tags1) {
        this.tags0  = tags0;
        this.tags1  = tags1;
    }

    public boolean isTag0(final String tag) {
        return (tags0 != null) && (tags0.contains(tag.toLowerCase()));
    }

    public boolean isTag1(final String tag) {
        return (tags1 != null) && (tags1.contains(tag.toLowerCase()));
    }

    //the 'missing' method that shall be implemented:
    public abstract void scrapeText(char[] text, String insideTag);

    // the other methods must take into account to construct the return value correctly
    public abstract void scrapeTag0(String tagname, Properties tagopts);

    public abstract void scrapeTag1(String tagname, Properties tagopts, char[] text);

    protected static String stripAllTags(String s) {
        StringBuffer r = new StringBuffer(s.length());
        int bc = 0;
        char c;
        for (int p = 0; p < s.length(); p++) {
            c = s.charAt(p);
            if (c == lb) {
                bc++;
                r.append(' ');
            } else if (c == rb) {
                bc--;
            } else if (bc <= 0) {
                r.append(c);
            }
        }
        return r.toString().trim();
    }

    public static String stripAll(String s) {
        return htmlFilterCharacterCoding.html2unicode(stripAllTags(s));
    }

    public void close() {
        // free resources
        tags0 = null;
        tags1 = null;
    }
    
    protected void finalize() {
        close();
    }
    
}


