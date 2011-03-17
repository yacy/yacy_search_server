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
import de.anomic.tools.nxTools;

public class ThreadDump extends HashMap<ThreadDump.StackTrace, List<String>> implements Map<ThreadDump.StackTrace, List<String>> {

    private static final long serialVersionUID = -5587850671040354397L;

    private static final String multiDumpFilter = ".*((java.net.DatagramSocket.receive)|(java.lang.Thread.getAllStackTraces)|(java.net.SocketInputStream.read)|(java.net.ServerSocket.accept)|(java.net.Socket.connect)).*";
    private static final Pattern multiDumpFilterPattern = Pattern.compile(multiDumpFilter);
    
    public static class StackTrace {
        public String text;
        public StackTrace(final String text) {
            this.text = text;
        }
        @Override
        public boolean equals(Object a) {
            return (a != null && a instanceof StackTrace && this.text.equals(((StackTrace) a).text));
        }
        public boolean equals(StackTrace a) {
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

    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        return Thread.getAllStackTraces();
    }
    
    public static boolean canProduceLockedBy(File logFile) {

        // check os version
        if (!OS.canExecUnix) return false;
        
        // check if log file exists
        if (!logFile.exists()) return false;
        
        // check last modification date of log file
        if (System.currentTimeMillis() - logFile.lastModified() > 60000) return false;
        
        return true;
    }
    
    public ThreadDump(File logFile) throws IOException {
        super();
        
        // try to get the thread dump from yacy.log which is available when YaCy is started with
        // startYACY.sh -l
        if (!canProduceLockedBy(logFile)) return;
        long sizeBefore = logFile.length();
        
        // get the current process PID
        int pid = OS.getPID();
        
        // call kill -3 on the pid
        if (pid >= 0) try {OS.execSynchronous("kill -3 " + pid);} catch (IOException e) {}

        // read the log from the dump
        long sizeAfter = logFile.length();
        if (sizeAfter <= sizeBefore) return;

        RandomAccessFile raf = new RandomAccessFile(logFile, "r");
        raf.seek(sizeBefore);
        byte[] b = new byte[(int) (sizeAfter - sizeBefore)];
        raf.readFully(b);
        raf.close();
        
        // import the thread dump;
        importText(new ByteArrayInputStream(b));
    }
    
    public ThreadDump(final InputStream is) throws IOException {
        super();
        importText(is);
    }

    private void importText(final InputStream is) throws IOException {
        final BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        String thread = null;
        int p;
        List<String> list = new ArrayList<String>();
        while ((line = br.readLine()) != null) {
            if (line.isEmpty()) {
                if (thread != null) {
                    this.put(new ThreadDump.StackTrace(thread), list);
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
        // recognize last thread
        if (thread != null) {
            this.put(new ThreadDump.StackTrace(thread), list);
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
        for (final Entry<Thread, StackTraceElement[]> entry: stackTraces.entrySet()) {
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
                List<String> threads = this.get(threaddump);
                if (threads == null) threads = new ArrayList<String>();
                threads.add(threadtitle);
                this.put(new StackTrace(threaddump), threads);
            }
        }
    }
    
    public void appendStackTraces(
            final StringBuilder buffer,
            final boolean plain,
            final Thread.State stateIn) {
        bufferappend(buffer, plain, "THREADS WITH STATES: " + stateIn.toString());
        bufferappend(buffer, plain, "");
        
        // write dumps
        for (final Entry<StackTrace, List<String>> entry: this.entrySet()) {
            List<String> threads = entry.getValue();
            for (final String t: threads) bufferappend(buffer, plain, t);
            bufferappend(buffer, plain, entry.getKey().text);
            bufferappend(buffer, plain, "");
        }
        bufferappend(buffer, plain, "");
    }

    public void appendBlockTraces(
            final StringBuilder buffer,
            final boolean plain) {
        bufferappend(buffer, plain, "THREADS WITH STATES: LOCK FOR OTHERS");
        bufferappend(buffer, plain, "");
        
        Map<StackTrace, Integer> locks = this.countLocks();
        for (int i = this.size() + 10; i > 0; i--) {
            for (Map.Entry<StackTrace, Integer> entry: locks.entrySet()) {
                if (entry.getValue().intValue() == i) {
                	bufferappend(buffer, plain, "holds lock for " + i + " threads:");
                	final List<String> list = this.get(entry.getKey());
                    if (list == null) continue;
                    bufferappend(buffer, plain, "Thread= " + entry.getKey());
                    for (String s: list) bufferappend(buffer, plain, "  " + (plain ? s : s.replaceAll("<", "&lt;").replaceAll(">", "&gt;")));
                    bufferappend(buffer, plain, "");
                }
            }
        }
        bufferappend(buffer, plain, "");
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
            for (final Entry<StackTrace, List<String>> e: x.entrySet()) {
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
            final Entry<String, Integer> e = removeMax(dumps);
            bufferappend(buffer, plain, "Occurrences: " + e.getValue());
            bufferappend(buffer, plain, e.getKey());
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
    public Map<Lock, StackTrace> locks() {
        int p;
        final Map<Lock, StackTrace> locks = new HashMap<Lock, StackTrace>();
        for (final Map.Entry<StackTrace, List<String>> entry: this.entrySet()) {
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
    public Lock lockedBy(final StackTrace threadName) {
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
    
    public Map<StackTrace, Integer> countLocks() {
        final Map<Lock, StackTrace> locks = locks();
        final Map<StackTrace, Integer> count = new HashMap<StackTrace, Integer>();
        for (final Map.Entry<Lock, StackTrace> entry: locks.entrySet()) {
            // look where the lock has an effect
            int c = 0;
            for (final StackTrace thread: this.keySet()) if (entry.getKey().equals(lockedBy(thread))) c++;
            if (c > 0) count.put(entry.getValue(), c);
        }
        return count;
    }
    
    public void print() {
        for (final StackTrace thread: this.keySet()) print(thread);
    }

    public void print(StackTrace thread) {
        final List<String> list = this.get(thread);
        if (list == null) return;
        System.out.println("Thread: " + thread);
        for (String s: list) System.out.println("  " + s);
        System.out.println("");
    }
    
    public static void main(String[] args) {
        ThreadDump dump = null;
        if (args.length == 0) {
            //dump = new ThreadDump();
        }
        if (args.length == 2 && args[0].equals("-f")) {
            File dumpfile = new File(args[1]);
            try {
                dump = new ThreadDump(dumpfile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //dump.print();
        Map<StackTrace, Integer> locks = dump.countLocks();
        System.out.println("*** Thread Dump Lock report; dump size = " + dump.size() + ", locks = " + locks.size());
        for (int i = 0; i < dump.size() + 10; i++) {
            for (Map.Entry<StackTrace, Integer> entry: locks.entrySet()) {
                if (entry.getValue().intValue() == i) {
                    System.out.println("holds lock for " + i + " threads:");
                    dump.print(entry.getKey());
                }
            }
        }
    }
}
