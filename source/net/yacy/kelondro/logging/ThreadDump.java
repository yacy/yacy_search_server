// ThreadDump.java
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004-2010
//
// This File contains contributions from Alexander Fieger
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

package net.yacy.kelondro.logging;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.yacy.document.parser.html.CharacterCoding;
import net.yacy.kelondro.util.FileUtils;
import net.yacy.kelondro.util.OS;
import net.yacy.utils.nxTools;

public class ThreadDump extends HashMap<ThreadDump.StackTrace, List<String>> implements Map<ThreadDump.StackTrace, List<String>> {

    private static final long serialVersionUID = -5587850671040354397L;

    private static final String multiDumpFilter = ".*((java.net.DatagramSocket.receive)|(java.lang.Thread.getAllStackTraces)|(java.net.SocketInputStream.read)|(java.net.ServerSocket.accept)|(java.net.Socket.connect)).*";
    private static final Pattern multiDumpFilterPattern = Pattern.compile(multiDumpFilter);

    public static class StackTrace {
        private String text;
        private Thread.State state;
        public StackTrace(final String text, final Thread.State state) {
            this.state = state;
            this.text = text;
        }
        @Override
        public boolean equals(final Object a) {
            return (a != null && a instanceof StackTrace && this.text.equals(((StackTrace) a).text));
        }
        public boolean equals(final StackTrace a) {
            return (a != null && this.text.equals(a.text));
        }
        @Override
        public int hashCode() {
            return this.text.hashCode();
        }
        @Override
        public String toString() {
            return this.text;
        }
    }

    public static class Lock {
        private String id;
        public Lock(final String name) {
            this.id = name;
        }
        @Override
        public boolean equals(final Object a) {
            return (a != null && a instanceof Lock && this.id.equals(((Lock) a).id));
        }
        public boolean equals(final Lock a) {
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

    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        return Thread.getAllStackTraces();
    }

    public static boolean canProduceLockedBy(final File logFile) {

        // check os version
        if (!OS.canExecUnix) return false;

        // check if log file exists
        if (!logFile.exists()) return false;

        // check last modification date of log file
        if (System.currentTimeMillis() - logFile.lastModified() > 60000) return false;

        return true;
    }

    public ThreadDump(final File logFile) throws IOException {
        super();

        // try to get the thread dump from yacy.log which is available when YaCy is started with
        // startYACY.sh -l
        long sizeBefore = 0;
        if (canProduceLockedBy(logFile)) {
            sizeBefore = logFile.length();

            // get the current process PID
            final int pid = OS.getPID();

            // call kill -3 on the pid
            if (pid >= 0) try {OS.execSynchronous("kill -3 " + pid);} catch (final IOException e) {}
        }

        // read the log from the dump
        final long sizeAfter = logFile.length();
        if (sizeAfter <= sizeBefore) return;

        final RandomAccessFile raf = new RandomAccessFile(logFile, "r");
        raf.seek(sizeBefore);
        final byte[] b = new byte[(int) (sizeAfter - sizeBefore)];
        raf.readFully(b);
        raf.close();

        // import the thread dump;
        importText(new ByteArrayInputStream(b));
    }

    public ThreadDump(final InputStream is) throws IOException {
        super();
        importText(is);
    }

    private static final String statestatement = "java.lang.Thread.State:";

    public static Thread.State threadState(String line) {
        int p = line.indexOf(statestatement);
        if (p < 0) return null;
        line = line.substring(p + statestatement.length()).trim();
        p = line.indexOf(' ');
        if (p >= 0) line = line.substring(0, p).trim();
        try {
            return Thread.State.valueOf(line);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    private void importText(final InputStream is) throws IOException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        String thread = null;
        int p;
        List<String> list = new ArrayList<String>();
        Thread.State state = null;
        Thread.State state0;
        while ((line = br.readLine()) != null) {
            state0 = threadState(line);
            if (state0 != null) state = state0;
            if (line.isEmpty()) {
                if (thread != null) {
                    put(new ThreadDump.StackTrace(thread, state), list);
                }
                list = new ArrayList<String>();
                thread = null;
                state = null;
                continue;
            }
            if (line.charAt(0) == '"' && (p = line.indexOf("\" prio=",0)) > 0) {
                // start a new thread
                thread = line.substring(1, p);
                continue;
            }
            if (thread != null) {
                list.add(line);
            }
        }
        // recognize last thread
        if (thread != null) {
            put(new ThreadDump.StackTrace(thread, state), list);
        }
    }

    public ThreadDump(
            final File appPath,
            final Map<Thread, StackTraceElement[]> stackTraces,
            final boolean plain,
            final Thread.State stateIn) {
        super();
        final File classPath = new File(appPath, "source");

        Thread thread;
        // collect single dumps
        for (final Map.Entry<Thread, StackTraceElement[]> entry: stackTraces.entrySet()) {
            thread = entry.getKey();
            final StackTraceElement[] stackTraceElements = entry.getValue();
            StackTraceElement ste;
            String line;
            String tracename = "";
            File classFile;
            if ((stateIn == null || stateIn.equals(thread.getState())) && stackTraceElements.length > 0) {
                final StringBuilder sb = new StringBuilder(3000);
                if (plain) {
                    classFile = getClassFile(classPath, stackTraceElements[stackTraceElements.length - 1].getClassName());
                    tracename = classFile.getName();
                    if (tracename.endsWith(".java")) tracename = tracename.substring(0, tracename.length() - 5);
                    if (tracename.length() > 20) tracename = tracename.substring(0, 20);
                    while (tracename.length() < 20) tracename = tracename + "_";
                    tracename = "[" + tracename + "] ";
                }
                final String threadtitle = tracename + "Thread= " + thread.getName() + " " + (thread.isDaemon()?"daemon":"") + " id=" + thread.getId() + " " + thread.getState().toString();
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
                        if (line != null && !line.isEmpty()) {
                            bufferappend(sb, plain, tracename + "at " + CharacterCoding.unicode2html(ste.toString(), true) + " [" + line.trim() + "]");
                        } else {
                            bufferappend(sb, plain, tracename + "at " + CharacterCoding.unicode2html(ste.toString(), true));
                        }
                    }
                }
                final String threaddump = sb.toString();
                List<String> threads = get(threaddump);
                if (threads == null) threads = new ArrayList<String>();
                Thread.State state = null;
                for (final String t: threads) {
                    final int p = t.indexOf(statestatement);
                    if (p >= 0) {
                        state = Thread.State.valueOf(t.substring(p + statestatement.length()).trim());
                    }
                }
                threads.add(threadtitle);
                put(new StackTrace(threaddump, state), threads);
            }
        }
    }

    public void appendStackTraces(
            final StringBuilder buffer,
            final boolean plain,
            final Thread.State stateIn) {
        bufferappend(buffer, plain, "THREADS WITH STATES: " + stateIn.toString());
        bufferappend(buffer, plain, "&nbsp;");

        // write dumps
        for (final Map.Entry<StackTrace, List<String>> entry: entrySet()) {
            final List<String> threads = entry.getValue();
            for (final String t: threads) bufferappend(buffer, plain, t);
            bufferappend(buffer, plain, entry.getKey().text);
            bufferappend(buffer, plain, "&nbsp;");
        }
        bufferappend(buffer, plain, "&nbsp;");
    }

    public void appendBlockTraces(
            final StringBuilder buffer,
            final boolean plain) {
        bufferappend(buffer, plain, "THREADS WITH STATES: LOCK FOR OTHERS");
        bufferappend(buffer, plain, "&nbsp;");

        final Map<StackTrace, Integer> locks = countLocks();
        for (int i = size() + 10; i > 0; i--) {
            for (final Map.Entry<StackTrace, Integer> entry: locks.entrySet()) {
                if (entry.getValue().intValue() == i) {
                	bufferappend(buffer, plain, "holds lock for " + i + " threads:");
                	final List<String> list = get(entry.getKey());
                    if (list == null) continue;
                    bufferappend(buffer, plain, "Thread= " + entry.getKey());
                    for (final String s: list) bufferappend(buffer, plain, "  " + (plain ? s : s.replaceAll("<", "&lt;").replaceAll(">", "&gt;")));
                    bufferappend(buffer, plain, "&nbsp;");
                }
            }
        }
        bufferappend(buffer, plain, "&nbsp;");
    }


    public static void appendStackTraceStats(
            final File rootPath,
            final StringBuilder buffer,
            final List<Map<Thread, StackTraceElement[]>> stackTraces,
            final boolean plain) {

        // collect single dumps
        final Map<String, Integer> dumps = new HashMap<String, Integer>();
        ThreadDump x;
        for (final Map<Thread, StackTraceElement[]> trace: stackTraces) {
            x = new ThreadDump(rootPath, trace, plain, Thread.State.RUNNABLE);
            for (final Map.Entry<StackTrace, List<String>> e: x.entrySet()) {
                if (multiDumpFilterPattern.matcher(e.getKey().text).matches()) continue;
                Integer c = dumps.get(e.getKey().text);
                if (c == null) dumps.put(e.getKey().text, Integer.valueOf(1));
                else {
                    c = Integer.valueOf(c.intValue() + 1);
                    dumps.put(e.getKey().text, c);
                }
            }
        }

        // write dumps
        while (!dumps.isEmpty()) {
            final Map.Entry<String, Integer> e = removeMax(dumps);
            bufferappend(buffer, plain, "Occurrences: " + e.getValue());
            bufferappend(buffer, plain, e.getKey());
            bufferappend(buffer, plain, "&nbsp;");
        }
        bufferappend(buffer, plain, "&nbsp;");
    }

    private static Map.Entry<String, Integer> removeMax(final Map<String, Integer> result) {
        Map.Entry<String, Integer> max = null;
        for (final Map.Entry<String, Integer> e: result.entrySet()) {
            if (max == null || e.getValue().intValue() > max.getValue().intValue()) {
                max = e;
            }
        }
        result.remove(max.getKey());
        return max;
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


    public List<Map.Entry<StackTrace, List<String>>> freerun() {
        final List<Map.Entry<StackTrace, List<String>>> runner = new ArrayList<Map.Entry<StackTrace, List<String>>>();
        runf: for (final Map.Entry<StackTrace, List<String>> entry: entrySet()) {
            // check if the thread is locked or holds a lock
            if (entry.getKey().state != Thread.State.RUNNABLE) continue runf;
            for (final String s: entry.getValue()) {
                if (s.indexOf("locked <") > 0 || s.indexOf("waiting to lock",0) > 0) continue runf;
            }
            runner.add(entry);
        }
        return runner;
    }

    /**
     * find all locks in this dump
     * @return a map from lock ids to the name of the thread where the lock occurs
     */
    public Map<Lock, StackTrace> locks() {
        int p;
        final Map<Lock, StackTrace> locks = new HashMap<Lock, StackTrace>();
        for (final Map.Entry<StackTrace, List<String>> entry: entrySet()) {
            for (final String s: entry.getValue()) {
                if ((p = s.indexOf("locked <",0)) > 0) {
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
    public Lock lockedBy(final StackTrace threadName) {
        int p;
        final List<String> list = get(threadName);
        if (list == null) return null;
        for (final String s: list) {
            if ((p = s.indexOf('<',0)) > 0 && s.indexOf("locked <",0) < 0) {
                return new Lock(s.substring(p + 1, s.indexOf('>')));
            }
        }
        return null;
    }

    public Map<StackTrace, Integer> countLocks() {
        final Map<Lock, StackTrace> locks = locks();
        final Map<StackTrace, Integer> count = new HashMap<StackTrace, Integer>();
        for (final Map.Entry<Lock, StackTrace> entry: locks.entrySet()) {
            // look where the lock has an effect
            int c = 0;
            for (final StackTrace thread: keySet()) if (entry.getKey().equals(lockedBy(thread))) c++;
            if (c > 0) count.put(entry.getValue(), c);
        }
        return count;
    }

    public void print() {
        for (final StackTrace thread: keySet()) print(thread);
    }

    public void print(final StackTrace thread) {
        final List<String> list = get(thread);
        if (list == null) return;
        System.out.println("Thread: " + thread);
        for (final String s: list) System.out.println("  " + s);
        System.out.println("");
    }

    public static void main(final String[] args) {
        ThreadDump dump = null;
        if (args.length == 0) {
            //dump = new ThreadDump();
        }
        if (args.length == 2 && args[0].equals("-f")) {
            final File dumpfile = new File(args[1]);
            try {
                dump = new ThreadDump(dumpfile);
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        //dump.print();
        assert dump != null;
        final Map<StackTrace, Integer> locks = dump.countLocks();
        final List<Map.Entry<StackTrace, List<String>>> freerun = dump.freerun();
        assert locks != null;
        System.out.println("*** Thread Dump Lock report; dump size = " + dump.size() + ", locks = " + locks.size() + ", freerunner = " + freerun.size());
        for (int i = 0; i < dump.size() + 10; i++) {
            for (final Map.Entry<StackTrace, Integer> entry: locks.entrySet()) {
                if (entry.getValue().intValue() == i) {
                    System.out.println("holds lock for " + i + " threads:");
                    dump.print(entry.getKey());
                }
            }
        }

        System.out.println("*** Thread freerunner report; dump size = " + dump.size() + ", locks = " + locks.size() + ", freerunner = " + freerun.size());
        for (final Map.Entry<StackTrace, List<String>> entry: freerun) {
            System.out.println("freerunner:");
            dump.print(entry.getKey());
        }

    }
}
