/**
 *  URLRewriterLibrary
 *  Copyright 2012 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  first published 08.10.2012 on http://yacy.net
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

package net.yacy.crawler.retrieval;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.yacy.cora.storage.Files;
import net.yacy.cora.util.ConcurrentLog;


public class URLRewriterLibrary {

    private final static ConcurrentLog log = new ConcurrentLog(URLRewriterLibrary.class.getName());

    private final File rewritingPath;
    private final Map<Pattern, String> rewriters;

    public URLRewriterLibrary(final File rewritingPath) {
        this.rewriters = new HashMap<Pattern, String>();
        this.rewritingPath = rewritingPath;
        if (this.rewritingPath == null || !this.rewritingPath.exists()) {
            return;
        }
        final String[] files = this.rewritingPath.list();
        for (final String f: files) {
            File ff = new File(this.rewritingPath, f);
            try {
                BlockingQueue<String> list = Files.concurentLineReader(ff, 1000);
                String line;
                while ((line = list.take()) != Files.POISON_LINE) {
                    line = line.trim();
                    if (line.length() == 0 || line.charAt(0) == '#') continue;
                    if (!line.startsWith("s/")) {
                        int p = line.indexOf('=');
                        if (p < 0) p = line.indexOf(':');
                        if (p > 0) try {
                            this.rewriters.put(Pattern.compile(line.substring(0, p)), line.substring(p + 1));
                        } catch (final PatternSyntaxException e) {
                            log.warn("bad pattern: " + line.substring(0, p));
                        }
                    }
                }
            } catch (final Throwable e) {
                log.warn("cannot read stemming file " + f, e);
            }
        }
    }
    
    public URLRewriterLibrary() {
        this.rewriters = new HashMap<Pattern, String>();
        this.rewritingPath = null;
    }
    
    public String apply(String s) {
        if (this.rewriters == null || this.rewriters.size() == 0) return s;
        for (Map.Entry<Pattern, String> entry: this.rewriters.entrySet()) {
            Matcher m = entry.getKey().matcher(s);
            if (m.matches()) s = m.replaceAll(entry.getValue());
        }
        return s;
    }

    public static void main(String[] args) {
        URLRewriterLibrary lib = new URLRewriterLibrary();
        lib.rewriters.put(Pattern.compile("cln_\\d+\\/"), ""); // www.bund.de
        lib.rewriters.put(Pattern.compile("&amp;administration=[0-9a-z]*"), ""); // http://www.lichtenau.de/
        lib.rewriters.put(Pattern.compile("\\?administration=[0-9a-z]*"), ""); // http://www.lichtenau.de/
        lib.rewriters.put(Pattern.compile("\\(X\\([1]\\"), ""); // herzogenrath
        lib.rewriters.put(Pattern.compile("\\(S\\([0-9a-z]+\\)\\)\\/"), ""); // herzogenrath
        lib.rewriters.put(Pattern.compile("&amp;ccm=[0-9]*"), ""); // herne
        lib.rewriters.put(Pattern.compile("&sid=[0-9]{14}.{8}"), ""); // startercenter
        String s = "";
        Pattern p = Pattern.compile("a");
        s = p.matcher(s).replaceAll("b");
    }

}
