/**
 *  ConfigurationSet
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.06.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-05-30 10:53:58 +0200 (Mo, 30 Mai 2011) $
 *  $LastChangedRevision: 7759 $
 *  $LastChangedBy: orbiter $
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

package net.yacy.cora.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

/**
 * this class reads configuration attributes as a list of keywords from a list
 * the list may contain lines with one keyword, comment lines, empty lines and out-commented keyword lines
 * when an attribute is changed here, the list is stored again with the original formatting
 *
 * the syntax of configuration files:
 * - all lines beginning with '##' are comments
 * - all non-empty lines not beginning with '#' are keyword lines
 * - all lines beginning with '#' and where the second character is not '#' are commented-out keyword lines
 *
 * @author Michael Christen
 */
public class ConfigurationSet extends AbstractSet<String> implements Set<String> {

    private final File file;
    private String[] lines;

    public ConfigurationSet() {
        this.file = null;
        this.lines = new String[0];
    }

    public ConfigurationSet(final File file) {
        this.file = file;
        try {
            final BufferedReader br = new BufferedReader(new FileReader(this.file));
            final LinkedList<String> sl = new LinkedList<String>();
            String s;
            while ((s = br.readLine()) != null) sl.add(s.trim());
            this.lines = new String[sl.size()];
            int c = 0;
            for (final String s0: sl) this.lines[c++] = s0;
        } catch (final IOException e) {
            this.lines = new String[0];
        }
    }

    public boolean containsDisabled(final String o) {
        if (o == null) return false;
        final Iterator<Entry> i = new EntryIterator();
        Entry e;
        while (i.hasNext()) {
            e = i.next();
            if (!e.enabled() && o.equals(e.key)) return true;
        }
        return false;
    }

    @Override
    public boolean add(final String key) {
        return add(key, null);
    }

    public boolean add(final String key, final String comment) {
        return add(key, comment, true);
    }

    public boolean add(final String key, final String comment, final boolean enabled) {
        if (contains(key)) {
            try {
                if (!enabled) disable(key);
            } catch (final IOException e) {
            }
            return true;
        }
        if (containsDisabled(key)) {
            try {
                if (enabled) enable(key);
            } catch (final IOException e) {
            }
            return false;
        }
        // extend the lines
        final String[] l = new String[this.lines.length + (comment == null ? 2 : 3)];
        System.arraycopy(this.lines, 0, l, 0, this.lines.length);
        l[this.lines.length] = "";
        if (comment != null) l[this.lines.length + 1] = "## " + comment;
        l[this.lines.length + (comment == null ? 1 : 2)] = enabled ? key : "#" + key;
        this.lines = l;
        try {
            commit();
        } catch (final IOException e) {
        }
        return false;
    }

    public void fill(final ConfigurationSet other) {
        final Iterator<Entry> i = other.allIterator();
        Entry e;
        while (i.hasNext()) {
            e = i.next();
            if (contains(e.key) || containsDisabled(e.key)) continue;
            this.add(e.key(), other.commentHeadline(e.key()), e.enabled());
        }
    }

    @Override
    public boolean isEmpty() {
        // a shortcut to a fast 'true' in case that we initialized the class without a configuration file
        return this.lines == null || this.lines.length == 0 || super.isEmpty();
    }

    /**
     * save the configuration back to the file
     * @throws IOException
     */
    private void commit() throws IOException {
        if (this.file == null) return;
        final BufferedWriter writer = new BufferedWriter(new FileWriter(this.file));
        for (final String s: this.lines) {
            writer.write(s);
            writer.write("\n");
        }
        writer.close();
    }

    @Override
    public Iterator<String> iterator() {
        return new LineIterator(true);
    }

    public Iterator<String> disabledIterator() {
        return new LineIterator(false);
    }

    public Iterator<Entry> allIterator() {
        return new EntryIterator();
    }

    private boolean isCommentLine(final int line) {
        return this.lines[line].startsWith("##");
    }

    private boolean isKeyLine(final int line) {
        return this.lines[line].length() > 0 && this.lines[line].charAt(0) != '#';
    }

    private boolean isDisabledLine(final int line) {
        return this.lines[line].length() > 1 && this.lines[line].charAt(0) == '#' && this.lines[line].charAt(1) != '#';
    }

    public void enable(final String key) throws IOException {
        for (int i = 0; i < this.lines.length; i++) {
            if (isDisabledLine(i) && this.lines[i].substring(1).trim().equals(key)) {
                this.lines[i] = key;
                commit();
                return;
            }
        }
    }

    public void disable(final String key) throws IOException {
        for (int i = 0; i < this.lines.length; i++) {
            if (isKeyLine(i) && this.lines[i].equals(key)) {
                this.lines[i] = "#" + key;
                commit();
                return;
            }
        }
    }

    public String commentHeadline(final String key) {
        for (int i = 1; i < this.lines.length; i++) {
            if (this.lines[i].equals(key) ||
                (isDisabledLine(i) && this.lines[i].substring(1).trim().equals(key))
               ) {
                return isCommentLine(i - 1) ? this.lines[i - 1].substring(2).trim() : "";
            }
        }
        return "";
    }

    public class LineIterator implements Iterator<String> {

        EntryIterator i;
        Entry nextEntry;
        private final boolean enabled;

        public LineIterator(final boolean enabled) {
            this.enabled = enabled;
            this.i = new EntryIterator();
            findNextValid();
        }

        public void findNextValid() {
            while (this.i.hasNext()) {
                this.nextEntry = this.i.next();
                if (this.nextEntry.enabled() == this.enabled) return;
            }
            this.nextEntry = null;
        }

        @Override
        public boolean hasNext() {
            return this.nextEntry != null;
        }

        @Override
        public String next() {
            if (this.nextEntry == null) return null;
            final String s = this.nextEntry.key();
            findNextValid();
            return s;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    public class EntryIterator implements Iterator<Entry> {

        private int line;

        public EntryIterator() {
            this.line = -1;
            findNextKeywordLine();
        }

        /**
         * increase line counter until it points to the next keyword line
         * @return true if a next line was found, false if EOL
         */
        private boolean findNextKeywordLine() {
            this.line++;
            if (this.line >= ConfigurationSet.this.lines.length) return false;
            while (ConfigurationSet.this.lines[this.line].length() == 0 ||
                   ConfigurationSet.this.lines[this.line].startsWith("##")) {
                 this.line++;
                 if (this.line >= ConfigurationSet.this.lines.length) return false;
             }
            return true;
        }

        @Override
        public boolean hasNext() {
            return this.line < ConfigurationSet.this.lines.length;
        }

        @Override
        public Entry next() {
            final String s = ConfigurationSet.this.lines[this.line];
            findNextKeywordLine();
            if (s.charAt(0) == '#') return new Entry(s.substring(1).trim(), false);
            return new Entry(s, true);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    public class Entry {
        private final String key;
        private final boolean enabled;
        public Entry(final String key, final boolean enabled) {
            this.enabled = enabled;
            this.key = key;
        }
        public String key() {
            return this.key;
        }
        public boolean enabled() {
            return this.enabled;
        }
    }

    @Override
    public int size() {
        int c = 0;
        for (final String s: this.lines) {
            if (s.length() > 0 && s.charAt(0) != '#') c++;
        }
        return c;
    }

    public static void main(final String[] args) {
        if (args.length == 0) return;
        final File f = new File(args[0]);
        final ConfigurationSet cs = new ConfigurationSet(f);
        Iterator<String> i = cs.iterator();
        String k;
        System.out.println("\nall activated attributes:");
        while (i.hasNext()) {
            k = i.next();
            System.out.println(k + " - " + cs.commentHeadline(k));
        }
        i = cs.disabledIterator();
        System.out.println("\nall deactivated attributes:");
        while (i.hasNext()) {
            k = i.next();
            System.out.println(k + " - " + cs.commentHeadline(k));
        }
    }

}
