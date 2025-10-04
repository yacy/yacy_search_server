/**
 *  MessageStacks
 *  Copyright 2025 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 10.09.2025 at https://yacy.net
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A persistent, thread-safe message stack manager with multiple named stacks.
 * Messages are JSON objects with a unique ID and metadata.
 * Push and pop operations are logged to disk for durability.
 * Supports pushing to the top, popping from the top, and removing from the bottom.
 * Logs are periodically rewritten to remove deleted messages. These logs ensure that all
 * stacks can be recreated to the status when the queues had been shut down.
 */
public final class MessageStacks {

    public final static String ATTR_ID         = "id";
    public final static String ATTR_QUEUE      = "queue";
    public final static String ATTR_OBJECT     = "object";
    public final static String ATTR_OP         = "op";
    public final static String ATTR_CREATED_AT = "createdAt";
    public final static String ATTR_DELETED_AT = "deletedAt";
    
    /**
     * Message metadata including unique ID, queue name, creation timestamp, and the JSON object.
     * This message is created for each object that is pushed onto a stack.
     */
    public final static class Message extends JSONObject {
        public Message(String jsonString) throws JSONException {
            super(jsonString);
        }
        
        public Message(final String id, final String queue, final JSONObject object, final String createdAt) throws JSONException {
            super();
            this.put(ATTR_ID, id);
            this.put(ATTR_QUEUE, queue);
            this.put(ATTR_CREATED_AT, createdAt);
            this.put(ATTR_OBJECT, object);
        }

        public Message(final String id, final String queue, final JSONObject object) throws JSONException {
            this(id, queue, object, isoNow());
        }
        
        private final Del toDel(final String op) throws JSONException {
            return new Del(this.getString(ATTR_ID), this.getString(ATTR_QUEUE), op);
        }
    }
    
    private final static class Del extends JSONObject {
        private Del(final String id, final String queue, final String op) throws JSONException {
            super();
            this.put(ATTR_ID, id);
            this.put(ATTR_QUEUE, queue);
            this.put(ATTR_OP, op);
            this.put(ATTR_DELETED_AT, isoNow());
        }
    }
    
    private final static String uuid() {
        return UUID.randomUUID().toString();
    }

    private final static String isoNow() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    }
    
    private final Path dataDir;
    private final Path pushLog;
    private final Path deleteLog;
    private final Path pushBackup;
    private final Path deleteBackup;

    private final ConcurrentMap<String, ArrayDeque<Message>> stacks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock> stackLocks = new ConcurrentHashMap<>();
    private final ReentrantLock pushLogLock = new ReentrantLock();
    private final ReentrantLock deleteLogLock = new ReentrantLock();
    private final ReadWriteLock stacksDictLock = new ReentrantReadWriteLock();

    /**
     * Create a MessageStacks manager with specified data directory.
     * @param dir the storage directory for message log files
     * @param rewriteLogOnStart if true, rewrite log files on startup to remove deleted messages
     * @param keepBackup if true, keep backups of old log files when rewriting
     * @throws JSONException
     */
    public MessageStacks(final String dir, final boolean rewriteLogOnStart, final boolean keepBackup) throws JSONException {
        this.dataDir = Paths.get(dir);
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create data dir: " + dir, e);
        }
        this.pushLog = dataDir.resolve("push_log.jsonl");
        this.deleteLog = dataDir.resolve("delete_log.jsonl");
        this.pushBackup = dataDir.resolve("push_log.backup");
        this.deleteBackup = dataDir.resolve("delete_log.backup");

        replay();
        if (rewriteLogOnStart) rewriteLog(keepBackup);
    }

    private final ReentrantLock getStackLock(final String queue) {
        ReentrantLock l = stackLocks.get(queue);
        if (l != null) return l;
        stacksDictLock.writeLock().lock();
        try {
            return stackLocks.computeIfAbsent(queue, k -> new ReentrantLock());
        } finally {
            stacksDictLock.writeLock().unlock();
        }
    }

    private final ArrayDeque<Message> getStack(final String queue) {
        ArrayDeque<Message> s = stacks.get(queue);
        if (s != null) return s;
        stacksDictLock.writeLock().lock();
        try {
            return stacks.computeIfAbsent(queue, k -> new ArrayDeque<>());
        } finally {
            stacksDictLock.writeLock().unlock();
        }
    }

    private void appendJsonLine(final Path file, final JSONObject json, final ReentrantLock lock) throws JSONException {
        final byte[] bytes = (json.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        final ByteBuffer buf = ByteBuffer.wrap(bytes);
        lock.lock();
        try (java.nio.channels.SeekableByteChannel ch = Files.newByteChannel(
                file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            while (buf.hasRemaining()) ch.write(buf);
            if (ch instanceof FileChannel) ((FileChannel) ch).force(true); // Durability
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Load all messages that have not been deleted from the log files into memory.
     * This therefore re-creates the last status of the stack at the time of the shutdown of the stack.
     */
    private final void replay() {
        Set<String> deleted = new HashSet<>();
        if (Files.isRegularFile(deleteLog)) {
            try (BufferedReader br = Files.newBufferedReader(deleteLog, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    try {
                    JSONObject obj = new JSONObject(line);
                    String id = obj.optString(ATTR_ID, null);
                    if (id != null) deleted.add(id);
                    } catch (JSONException e) {
                        System.out.println("MessageStacks.replay: ignoring invalid JSON line in delete log: " + line);
                    }
                }
            } catch (IOException ignored) {}
        }
        if (Files.isRegularFile(pushLog)) {
            try (BufferedReader br = Files.newBufferedReader(pushLog, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    try {
                        Message meta = new Message(line);
                        String id = meta.optString(ATTR_ID, null);
                        String queue = meta.optString(ATTR_QUEUE, null);
                        if (id == null || queue == null) continue;
                        if (deleted.contains(id)) continue;
                        ArrayDeque<Message> stack = getStack(queue);
                        stack.add(meta);
                    } catch (JSONException e) {
                        System.out.println("MessageStacks.replay: ignoring invalid JSON line in push log: " + line);
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    private final void rewriteLog(final boolean keepBackup) throws JSONException {
        if (keepBackup) {
            // Append current logs to *.backup (best effort)
            tryAppend(pushLog, pushBackup);
            tryAppend(deleteLog, deleteBackup);
        }
        
        // Truncate delete log
        try {
            Files.writeString(deleteLog, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {}

        // Rebuild push log from RAM using temp file then atomic replace
        Path tmp = null;
        try {
            tmp = Files.createTempFile(dataDir, "push_log.tmp.", ".jsonl");
            try (BufferedWriter bw = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                // Write queues in stable order; within a queue preserve stack order
                List<String> names = new ArrayList<>(stacks.keySet());
                Collections.sort(names);
                for (String q : names) {
                    ArrayDeque<Message> stack = getStack(q);
                    ReentrantLock sl = getStackLock(q);
                    sl.lock();
                    try {
                        for (Message m : stack) {
                            bw.write(m.toString());
                            bw.write("\n");
                        }
                    } finally {
                        sl.unlock();
                    }
                }
                bw.flush();
            }
            // Replace
            try {
                Files.move(tmp, pushLog, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, pushLog, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignore2) {}
            }
        }
    }

    private final void tryAppend(final Path src, final Path dst) {
        if (!Files.isRegularFile(src)) return;
        try (InputStream in = Files.newInputStream(src);
             OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            in.transferTo(out);
            out.flush();
        } catch (IOException ignored) {}
    }

    /**
     * Push a JSON object onto the specified queue.
     * @param queue name of the queue
     * @param object the JSON object to push
     * @return the message metadata including unique ID and timestamps
     * @throws JSONException
     */
    public final Message push(final String queue, final JSONObject object) throws JSONException {
        Message meta = new Message(uuid(), queue, object);
        appendJsonLine(pushLog, meta, pushLogLock);
        ArrayDeque<Message> stack = getStack(queue);
        ReentrantLock sl = getStackLock(queue);
        sl.lock();
        try {
            stack.addLast(meta);
        } finally {
            sl.unlock();
        }

        return meta;
    }

    /**
     * Pop a JSON object from the top of the specified queue:
     * this removes the most recently pushed object.
     * Using pop creates a FILO stack behavior.
     * @param queue the name of the queue
     * @return the most recently pushed message, or null if the stack is empty
     * @throws JSONException
     */
    public final Message pop(final String queue) throws JSONException {
        ArrayDeque<Message> stack = getStack(queue);
        ReentrantLock sl = getStackLock(queue);
        sl.lock();
        try {
            if (stack.isEmpty()) return null;
            Message meta = stack.peekLast();
            if (meta == null) return null;
            Del del = meta.toDel("pop");
            appendJsonLine(deleteLog, del, deleteLogLock);
            stack.removeLast();
            return meta;
        } finally {
            sl.unlock();
        }
    }

    /**
     * his removes the earliest pushed object.
     * Using pot creates a FIFO queue behavior.
     * @param queue the name of the queue
     * @return the most earliest pushed message, or null if the stack is empty
     * @throws JSONException
     */
    public final Message pot(final String queue) throws JSONException {
        ArrayDeque<Message> stack = getStack(queue);
        ReentrantLock sl = getStackLock(queue);
        sl.lock();
        try {
            if (stack.isEmpty()) return null;
            Message meta = stack.peekFirst();
            if (meta == null) return null;
            Del del = meta.toDel("pot");
            appendJsonLine(deleteLog, del, deleteLogLock);
            stack.removeFirst();
            return meta;
        } finally {
            sl.unlock();
        }
    }

    /**
     * Peek at the top n messages of the specified queue without removing them.
     * @param queue the name of the queue
     * @param n     the number of messages to peek at from the top
     * @return a list of up to n most recently pushed messages, or an empty list if the stack is empty
     */
    public final List<Message> top(final String queue, int n) {
        if (n <= 0) return Collections.emptyList();
        ArrayDeque<Message> stack = getStack(queue);
        ReentrantLock sl = getStackLock(queue);
        ArrayList<Message> res = new ArrayList<>();
        sl.lock();
        try {
            int c = Math.min(stack.size(), n);
            Iterator<Message> i = stack.descendingIterator();
            while (i.hasNext() && c-- > 0) {
                Message m = i.next();
                res.add(m);
            }
        } finally {
            sl.unlock();
        }
        return res;
    }

    /**
     * Peek at the bottom n messages of the specified queue without removing them.
     * @param queue the name of the queue
     * @param n     the number of messages to peek at from the bottom
     * @return a list of up to n earliest pushed messages, or an empty list if the stack is empty
     */
    public final List<Message> bot(final String queue, int n) {
        if (n <= 0) return Collections.emptyList();
        ArrayDeque<Message> stack = getStack(queue);
        ReentrantLock sl = getStackLock(queue);
        ArrayList<Message> res = new ArrayList<>();
        sl.lock();
        try {
            int c = Math.min(stack.size(), n);
            Iterator<Message> i = stack.iterator();
            while (i.hasNext() && c-- > 0) {
                Message m = i.next();
                res.add(m);
            }
        } finally {
            sl.unlock();
        }
        return res;
    }
    
    /**
     * Get the sizes of all queues as a JSON object.
     * @return a map of queue names to their sizes
     */
    public Map<String, Integer> queuesJson() {
        List<String> names = new ArrayList<>(stacks.keySet());
        Collections.sort(names);
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (String q : names) {
            ReentrantLock sl = getStackLock(q);
            sl.lock();
            try {
                ArrayDeque<Message> s = stacks.get(q);
                map.put(q, s == null ? 0 : s.size());
            } finally {
                sl.unlock();
            }
        }
        return map;
    }

    /**
     * Get statistics about a specific queue including its size and timestamps of
     * the oldest and newest messages.
     * @param queue the name of the queue
     * @return a JSON object with the queue's name, size, oldest and newest message timestamps
     * @throws JSONException
     */
    public final JSONObject queueStats(final String queue) throws JSONException {
        ArrayDeque<Message> s = getStack(queue);
        ReentrantLock sl = getStackLock(queue);
        String oldest = null, newest = null;
        int size;
        sl.lock();
        try {
            size = s.size();
            if (size > 0) {
                oldest = s.peekFirst().getString(ATTR_CREATED_AT);
                newest = s.peekLast().getString(ATTR_CREATED_AT);
            }
        } finally {
            sl.unlock();
        }
        JSONObject stats = new JSONObject();
        stats.put("name", queue);
        stats.put("size", size);
        stats.put("oldest_created_at", oldest);
        stats.put("newest_created_at", newest);
        return stats;
    }

    /**
     * Testing
     */
    public static void main(String[] args) throws Exception {
        System.out.println("MessageStacks mass test starting…");
        final Path tmp = Files.createTempDirectory("messagestacks-test-");
        final String dir = tmp.toString();
        System.out.println("Using data dir: " + dir);
        final int QUEUES = 8;
        final int PHASE1_PUSH_PER_Q = 1000;
        final int PHASE2_PRODUCERS = 6;
        final int PHASE2_PUSH_PER_PRODUCER = 2000;
        final long RANDOM_SEED = 42L;
        final List<String> queueNames = new ArrayList<>();
        for (int i = 0; i < QUEUES; i++) queueNames.add("q" + i);

        // deterministic single-thread tests
        final MessageStacks ms1 = new MessageStacks(dir, false, false);
        System.out.println("Phase 1: deterministic single-thread exercises…");
        for (String q : queueNames) {
            for (int i = 0; i < PHASE1_PUSH_PER_Q; i++) {
                JSONObject payload = new JSONObject()
                        .put("n", i)
                        .put("queue", q)
                        .put("phase", 1);
                ms1.push(q, payload);
            }
        }
        // queue sizes
        for (String q : queueNames) {
            int size = ms1.queuesJson().get(q);
            assertEquals(PHASE1_PUSH_PER_Q, size, "Phase1 size after push for " + q);
        }
        // top/bot
        for (String q : queueNames) {
            List<Message> top5 = ms1.top(q, 5);
            assertEquals(5, top5.size(), "top(5) size");
            // top ist LIFO: letztes gepushtes hat n = PHASE1_PUSH_PER_Q-1
            int n0 = top5.get(0).getJSONObject(ATTR_OBJECT).getInt("n");
            int n4 = top5.get(4).getJSONObject(ATTR_OBJECT).getInt("n");
            assertTrue(n0 == PHASE1_PUSH_PER_Q - 1 && n4 == PHASE1_PUSH_PER_Q - 5, "top order");
            List<Message> bot5 = ms1.bot(q, 5);
            assertEquals(5, bot5.size(), "bot(5) size");
            int b0 = bot5.get(0).getJSONObject(ATTR_OBJECT).getInt("n");
            int b4 = bot5.get(4).getJSONObject(ATTR_OBJECT).getInt("n");
            assertTrue(b0 == 0 && b4 == 4, "bot order");
        }
        // pop/pot
        for (String q : queueNames) {
            Message mTop = ms1.pop(q);
            Message mBot = ms1.pot(q);
            assertNotNull(mTop, "pop returned null");
            assertNotNull(mBot, "pot returned null");
        }
        for (String q : queueNames) {
            int size = ms1.queuesJson().get(q);
            assertEquals(PHASE1_PUSH_PER_Q - 2, size, "Phase1 size after pop+pot for " + q);
        }

        // multithreaded tests
        System.out.println("Phase 2: multithreaded producers…");
        final int totalPhase2Push = PHASE2_PRODUCERS * PHASE2_PUSH_PER_PRODUCER;
        Thread[] producers = new Thread[PHASE2_PRODUCERS];
        final java.util.concurrent.atomic.AtomicIntegerArray perQueueAdds = new java.util.concurrent.atomic.AtomicIntegerArray(QUEUES);

        for (int p = 0; p < PHASE2_PRODUCERS; p++) {
            final int pid = p;
            producers[p] = new Thread(() -> {
                java.util.Random rnd = new java.util.Random(RANDOM_SEED + pid);
                for (int i = 0; i < PHASE2_PUSH_PER_PRODUCER; i++) {
                    String q = queueNames.get(rnd.nextInt(QUEUES));
                    try {
                        JSONObject payload = new JSONObject().put("producer", pid).put("i", i).put("phase", 2);
                        ms1.push(q, payload);
                        perQueueAdds.incrementAndGet(queueNames.indexOf(q));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, "producer-" + p);
            producers[p].start();
        }
        for (Thread t : producers) t.join();
        
        // check counts
        int sumAdds = 0;
        for (int i = 0; i < QUEUES; i++) sumAdds += perQueueAdds.get(i);
        assertEquals(totalPhase2Push, sumAdds, "Phase2 total pushes accounted for");
        
        // check expected sizes
        Map<String, Integer> sizes = ms1.queuesJson();
        for (int i = 0; i < QUEUES; i++) {
            String q = queueNames.get(i);
            int expected = (PHASE1_PUSH_PER_Q - 2) + perQueueAdds.get(i);
            assertEquals(expected, sizes.get(q), "Phase2 size for " + q);
        }
        
        // check global size
        int expectedTotalAfterP2 = QUEUES * (PHASE1_PUSH_PER_Q - 2) + totalPhase2Push;
        int actualTotalAfterP2   = sizes.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(expectedTotalAfterP2, actualTotalAfterP2, "Phase2 global size");

        // check replay
        System.out.println("Phase 3: restart & replay check…");
        final MessageStacks ms3 = new MessageStacks(dir, false, false);
        Map<String, Integer> sizesAfterReplay = ms3.queuesJson();
        for (String q : queueNames) {
            assertEquals(sizes.get(q), sizesAfterReplay.get(q), "Replay size matches for " + q);
        }
        
        // check global size
        int actualTotalAfterReplay = sizesAfterReplay.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(expectedTotalAfterP2, actualTotalAfterReplay, "Replay preserves global size");

        // check rewrite
        System.out.println("Phase 4: rewrite-on-start (keep backups)…");
        final MessageStacks ms4 = new MessageStacks(dir, true, true);
        Map<String, Integer> sizesAfterRewrite = ms4.queuesJson();
        for (String q : queueNames) {
            assertEquals(sizesAfterReplay.get(q), sizesAfterRewrite.get(q), "Rewrite size matches for " + q);
        }
        
        // check global size
        int actualTotalAfterRewrite = sizesAfterRewrite.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(expectedTotalAfterP2, actualTotalAfterRewrite, "Rewrite preserves global size");
        
        // check logs
        Set<String> names = new HashSet<>();
        try (var sDir = Files.list(tmp)) {
            sDir.forEach(p -> names.add(p.getFileName().toString()));
        }
        assertTrue(names.contains("push_log.jsonl"), "push_log.jsonl exists");
        assertTrue(names.contains("delete_log.jsonl"), "delete_log.jsonl exists");
        assertTrue(names.contains("push_log.backup"), "push_log.backup exists (keepBackup)");
        assertTrue(names.contains("delete_log.backup"), "delete_log.backup exists (keepBackup)");

        // parallel drain
        System.out.println("Phase 5: parallel drain (mix pop/pot)…");
        final int consumers = Math.max(4, Runtime.getRuntime().availableProcessors());
        final java.util.concurrent.atomic.AtomicInteger drained = new java.util.concurrent.atomic.AtomicInteger(0);
        final int initialTotal = sizesAfterRewrite.values().stream().mapToInt(Integer::intValue).sum();

        Thread[] workers = new Thread[consumers];
        for (int c = 0; c < consumers; c++) {
            final int cid = c;
            workers[c] = new Thread(() -> {
                java.util.Random rnd = new java.util.Random(RANDOM_SEED ^ (cid * 1337L));
                int local = 0;
                while (true) {
                    // zufällig Queue wählen
                    String q = queueNames.get(rnd.nextInt(QUEUES));
                    try {
                        Message m;
                        if (rnd.nextBoolean()) {
                            m = ms4.pop(q); // top
                        } else {
                            m = ms4.pot(q); // bottom
                        }
                        if (m != null) {
                            local++;
                            int total = drained.incrementAndGet();
                            if (total % 10_000 == 0) {
                                System.out.println("…drained " + total + " messages");
                            }
                        } else {
                            // prüfen, ob global nichts mehr da ist
                            int rest = ms4.queuesJson().values().stream().mapToInt(Integer::intValue).sum();
                            if (rest == 0) break; // fertig
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println("consumer-" + cid + " drained " + local);
            }, "consumer-" + c);
            workers[c].start();
        }
        for (Thread t : workers) t.join();

        // cleanup check
        int remaining = ms4.queuesJson().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(0, remaining, "all queues empty after drain");
        assertEquals(initialTotal, drained.get(), "drained count equals initial total");

        // edge cases
        System.out.println("Phase 6: edge cases…");
        // top/bot on enpty Queue
        List<Message> emptyTop = ms4.top(queueNames.get(0), 5);
        List<Message> emptyBot = ms4.bot(queueNames.get(0), 5);
        assertTrue(emptyTop.isEmpty(), "top on empty returns empty");
        assertTrue(emptyBot.isEmpty(), "bot on empty returns empty");
        // negative/zero n
        assertTrue(ms4.top(queueNames.get(0), 0).isEmpty(), "top(0) empty");
        assertTrue(ms4.bot(queueNames.get(0), -10).isEmpty(), "bot(-10) empty");

        // done
        System.out.println("All tests passed ✅");
        System.out.println("Data dir kept at: " + dir);
    }

    // assert-helpers
    private static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            throw new AssertionError(msg + " | expected=" + expected + " actual=" + actual);
        }
    }
    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
    private static void assertNotNull(Object o, String msg) {
        if (o == null) {
            throw new AssertionError(msg);
        }
    }

}
