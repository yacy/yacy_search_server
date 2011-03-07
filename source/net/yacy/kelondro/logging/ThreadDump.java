// ThreadDump.java
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004-2010
//
// This File contains contributions from Alexander Fieger
//
// $LastChangedDate: 2010-09-02 21:24:22 +0200 (Do, 02 Sep 2010) $
// $LastChangedRevision: 7092 $
// $LastChangedBy: orbiter $
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

package net.yacy.kelondro.logging;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.FileUtils;
import de.anomic.tools.nxTools;

public class ThreadDump extends HashMap<ThreadDump.Thread, List<String>> implements Map<ThreadDump.Thread, List<String>> {

    private static final long serialVersionUID = -5587850671040354397L;

    public class Thread {
        public String name;
        public Thread(final String name) {
            this.name = name;
        }
        @Override
        public boolean equals(Object a) {
            return (a != null && a instanceof Thread && this.name.equals(((Thread) a).name));
        }
        public boolean equals(Thread a) {
            return (a != null && this.name.equals(a.name));
        }
        @Override
        public int hashCode() {
            return this.name.hashCode();
        }
        @Override
        public String toString() {
            return this.name;
        }
    }
    
    public class Lock {
        public String id;
        public Lock(final String name) {
            this.id = name;
        }
        @Override
        public boolean equals(Object a) {
            return (a != null && a instanceof Lock && this.id.equals(((Lock) a).id));
        }
        public boolean equals(Lock a) {
            return (a != null && this.id.equals(a.id));
        }
        @Override
        public int hashCode() {
            return this.id.hashCode();
        }
        @Override
        public String toString() {
            return this.id;
        }
    }

    public static Map<java.lang.Thread, StackTraceElement[]> getAllStackTraces() {
        return java.lang.Thread.getAllStackTraces();
    }
    
    public ThreadDump(final File f) throws IOException {
        this(new FileInputStream(f));
    }
    
    public ThreadDump(final InputStream is) throws IOException {
        super();
        final BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        String thread = null;
        int p;
        List<String> list = new ArrayList<String>();
        while ((line = br.readLine()) != null) {
            if (line.isEmpty()) {
                if (thread != null) {
                    this.put(new ThreadDump.Thread(thread), list);
                }
                list = new ArrayList<String>();
                thread = null;
                continue;
            }
            if (line.charAt(0) == '"' && (p = line.indexOf("\" prio=")) > 0) {
                // start a new thread
                thread = line.substring(1, p);
                continue;
            }
            if (thread != null) {
                list.add(line);
            }
        }
    }
    
    public static void appendStackTraces(final File rootPath,
            final StringBuilder buffer,
            final Map<java.lang.Thread, StackTraceElement[]> stackTraces,
            final boolean plain,
            final java.lang.Thread.State stateIn)
    {
        bufferappend(buffer, plain, "THREADS WITH STATES: " + stateIn.toString());
        bufferappend(buffer, plain, "");
        // collect single dumps
        final Map<String, SortedSet<String>> dumps = dumpCollection(rootPath, stackTraces, plain, stateIn);
        
        // write dumps
        for (final Entry<String, SortedSet<String>> entry: dumps.entrySet()) {
            SortedSet<String> threads = entry.getValue();
            for (final String t: threads) bufferappend(buffer, plain, t);
            bufferappend(buffer, plain, entry.getKey());
            bufferappend(buffer, plain, "");
        }
        bufferappend(buffer, plain, "");
    }
    
    public static void appendStackTraceStats(final File rootPath,
            final StringBuilder buffer,
            final List<Map<java.lang.Thread, StackTraceElement[]>> traces,
            final boolean plain,
            final java.lang.Thread.State stateIn)
    {
        if (stateIn != null) {
            bufferappend(buffer, plain, "THREADS WITH STATES: " + stateIn.toString());
            bufferappend(buffer, plain, "");
        }
        // collect single dumps
        Map<String, Integer> dumps = dumpStatistic(rootPath, traces, plain, stateIn);
        
        // write dumps
        while (!dumps.isEmpty()) {
            final Entry<String, Integer> e = removeMax(dumps);
            bufferappend(buffer, plain, "Occurrences: " + e.getValue());
            bufferappend(buffer, plain, e.getKey());
            //bufferappend(buffer, plain, "");
        }
        bufferappend(buffer, plain, "");
    }
    
    private static Entry<String, Integer> removeMax(final Map<String, Integer> result) {
        Entry<String, Integer> max = null;
        for (final Entry<String, Integer> e: result.entrySet()) {
            if (max == null || e.getValue().intValue() > max.getValue().intValue()) {
                max = e;
            }
        }
        result.remove(max.getKey());
        return max;
    }
    
    private static Map<String, Integer> dumpStatistic(final File rootPath,
            final List<Map<java.lang.Thread,StackTraceElement[]>> stackTraces,
            final boolean plain,
            final java.lang.Thread.State stateIn)
    {
        final Map<String, Integer> result = new HashMap<String, Integer>();
        Map<String, SortedSet<String>> x;
        int count;
        for (final Map<java.lang.Thread,StackTraceElement[]> trace: stackTraces) {
            x = dumpCollection(rootPath, trace, plain, stateIn);
            for (final Entry<String, SortedSet<String>> e: x.entrySet()) {
                Integer c = result.get(e.getKey());
                count = e.getValue().size();
                if (c == null) result.put(e.getKey(), Integer.valueOf(count));
                else {
                    c = Integer.valueOf(c.intValue() + count);
                    result.put(e.getKey(), c);
                }
            }
        }
        return result;
    }
    
    private static Map<String, SortedSet<String>> dumpCollection(final File appPath,
            final Map<java.lang.Thread,StackTraceElement[]> stackTraces,
            final boolean plain,
            final java.lang.Thread.State stateIn)
    {
        final File classPath = new File(appPath, "source");
  
        java.lang.Thread thread;
        // collect single dumps
        final Map<String, SortedSet<String>> dumps = new HashMap<String, SortedSet<String>>();
        for (final Entry<java.lang.Thread, StackTraceElement[]> entry: stackTraces.entrySet()) {
            thread = entry.getKey();
            final StackTraceElement[] stackTraceElements = entry.getValue();
            StackTraceElement ste;
            String line;
            String tracename = "";
            File classFile;
            if ((stateIn == null || stateIn.equals(thread.getState())) && stackTraceElements.length > 0) {
                StringBuilder sb = new StringBuilder(3000);
                if (plain) {
                    classFile = getClassFile(classPath, stackTraceElements[stackTraceElements.length - 1].getClassName());
                    tracename = classFile.getName();
                    if (tracename.endsWith(".java")) tracename = tracename.substring(0, tracename.length() - 5);
                    if (tracename.length() > 20) tracename = tracename.substring(0, 20);
                    while (tracename.length() < 20) tracename = tracename + "_";
                    tracename = "[" + tracename + "] ";                
                }                
                String threadtitle = tracename + "Thread= " + thread.getName() + " " + (thread.isDaemon()?"daemon":"") + " id=" + thread.getId() + " " + thread.getState().toString();
                String className;
                boolean cutcore = true;
                for (int i = 0; i < stackTraceElements.length; i++) {
                    ste = stackTraceElements[i];
                    className = ste.getClassName();
                    if (cutcore && (className.startsWith("java.") || className.startsWith("sun."))) {
                        sb.setLength(0);
                        bufferappend(sb, plain, tracename + "at " + CharacterCoding.unicode2html(ste.toString(), true));
                    } else {
                        cutcore = false;
                        if (i == 0) {
                            line = getLine(getClassFile(classPath, className), ste.getLineNumber());
                        } else {
                            line = null;
                        }
                        if ((line != null) && (line.length() > 0)) {
                            bufferappend(sb, plain, tracename + "at " + CharacterCoding.unicode2html(ste.toString(), true) + " [" + line.trim() + "]");
                        } else {
                            bufferappend(sb, plain, tracename + "at " + CharacterCoding.unicode2html(ste.toString(), true));
                        }
                    }
                }
                final String threaddump = sb.toString();
                SortedSet<String> threads = dumps.get(threaddump);
                if (threads == null) threads = new TreeSet<String>();
                threads.add(threadtitle);
                dumps.put(threaddump, threads);
            }
        }
        return dumps;
    }
    
    private static File getClassFile(final File sourcePath, final String classname) {
        final String classPath = classname.replace('.', '/') + ".java";
        final File file = new File(sourcePath, classPath);
        return file;
    }
    
    private static String getLine(final File file, final int line) {
        // find class
        if (!file.exists()) return "";
        try {
            final String lineString = nxTools.line(FileUtils.read(file), line);
            if (lineString == null) return "@ERROR";
            return lineString;
        } catch (final IOException e) {
            return "@EXCEPTION: " + e.getMessage();
        }
    }
    
    public static void bufferappend(final StringBuilder buffer, final boolean plain, final String a) {
        buffer.append(a);
        buffer.append(plain ? "\n" : "<br />");
    }
    
    /**
     * find all locks in this dump
     * @return a map from lock ids to the name of the thread where the lock occurs
     */
    public Map<Lock, Thread> locks() {
        int p;
        final Map<Lock, Thread> locks = new HashMap<Lock, Thread>();
        for (final Map.Entry<Thread, List<String>> entry: this.entrySet()) {
            for (final String s: entry.getValue()) {
                if ((p = s.indexOf("locked <")) > 0) {
                    locks.put(new Lock(s.substring(p + 8, s.indexOf('>'))), entry.getKey());
                    
                }
            }
        }
        return locks;
    }
    
    /**
     * check if a thread is locked by another thread
     * @param threadName
     * @return the thread id if there is a lock or null if there is none
     */
    public Lock lockedBy(final Thread threadName) {
        int p;
        final List<String> list = this.get(threadName);
        if (list == null) return null;
        for (final String s: list) {
            if ((p = s.indexOf("<")) > 0 && s.indexOf("locked <") < 0) {
                return new Lock(s.substring(p + 1, s.indexOf('>')));
            }
        }
        return null;
    }
    
    public Map<Thread, Integer> countLocks() {
        final Map<Lock, Thread> locks = locks();
        final Map<Thread, Integer> count = new HashMap<Thread, Integer>();
        for (final Map.Entry<Lock, Thread> entry: locks.entrySet()) {
            // look where the lock has an effect
            int c = 0;
            for (final Thread thread: this.keySet()) if (entry.getKey().equals(lockedBy(thread))) c++;
            if (c > 0) count.put(entry.getValue(), c);
        }
        return count;
    }
    
    public void print() {
        for (final Thread thread: this.keySet()) print(thread);
    }

    public void print(Thread thread) {
        final List<String> list = this.get(thread);
        if (list == null) return;
        System.out.println("Thread: " + thread);
        for (String s: list) System.out.println("  " + s);
        System.out.println("");
    }
    
    public static void main(String[] args) {
        if (args.length == 2 && args[0].equals("-f")) {
            File dumpfile = new File(args[1]);
            ThreadDump dump = null;
            try {
                dump = new ThreadDump(dumpfile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //dump.print();
            Map<Thread, Integer> locks = dump.countLocks();
            for (int i = 0; i < dump.size() + 10; i++) {
                for (Map.Entry<Thread, Integer> entry: locks.entrySet()) {
                    if (entry.getValue().intValue() == i) {
                        System.out.println("holds lock for " + i + " threads:");
                        dump.print(entry.getKey());
                    }
                }
            }
        }
    }
}
