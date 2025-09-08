/**
 *  ChunkedBytes
 *  Copyright 26.8.2025 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
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

package net.yacy.cora.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import net.yacy.cora.document.encoding.UTF8;

/**
 * This class implements an output stream in which the data is
 * written into an arbitrary-length store which is composed of
 * RAM chunks and/or file-mapped chunks.
 */
public final class ChunkedBytes extends OutputStream implements Comparable<Object>, Closeable, Cloneable {

    /** Keep mapped/heap chunks well under Integer.MAX_VALUE; 64 MiB is a good default. */
    public static final int CHUNK_SIZE = 64 * 1024 * 1024;

    private final List<Segment> segments;
    private long size;

    public ChunkedBytes() {
        this.segments = new ArrayList<>();
        this.size = 0;
    }

    public ChunkedBytes(InputStream in) throws IOException {
        this();
        this.writeFrom(in);
    }

    public ChunkedBytes(byte[] initialData) {
        this();
        this.append(initialData, 0, initialData.length);
    }

    public ChunkedBytes(String initialData) {
        this();
        this.append(UTF8.getBytes(initialData));
    }
    
	private ChunkedBytes(List<Segment> segments, long size) {
		this.segments = new ArrayList<>(segments);
		this.size = size;
	}

    /** Represents one contiguous region in the logical address space. */
    private static final class Segment implements Closeable, Cloneable {
        final Chunk chunk;
        final long start;    // global start offset
        final int length;    // length within this segment (<= CHUNK_SIZE)
        Segment(Chunk chunk, long start, int length) {
            this.chunk = chunk; this.start = start; this.length = length;
        }
        @Override public Object clone() {
			return new Segment((Chunk) this.chunk.clone(), this.start, this.length);
		}
        @Override public void close() throws IOException { this.chunk.close(); }
    }

    /** Common interface for heap/file-backed chunks. */
    private interface Chunk extends Closeable, Cloneable {
        int read(long relPos, byte[] dst, int off, int len);
        int write(long relPos, byte[] src, int off, int len);
        byte get(long relPos);
        void set(long relPos, byte b);
        int length();
        Object clone();
        @Override default void close() { /* no-op by default */ }
    }

    /** On-heap chunk. */
    private static final class HeapChunk implements Chunk, Cloneable {
        final byte[] buf;
        HeapChunk(int cap) {
            assert cap > 0 && cap <= CHUNK_SIZE : "Invalid HeapChunk capacity: " + cap;
            this.buf = new byte[cap];
        }
        @Override public int read(long p, byte[] dst, int off, int len) {
            final int pos = (int)p; final int n = Math.min(len, this.buf.length - pos);
            if (n <= 0) return -1;
            System.arraycopy(this.buf, pos, dst, off, n);
            return n;
        }
        @Override public int write(long p, byte[] src, int off, int len) {
            final int pos = (int)p; final int n = Math.min(len, this.buf.length - pos);
            if (n <= 0) return -1;
            System.arraycopy(src, off, this.buf, pos, n);
            return n;
        }
        @Override public byte get(long p) { return this.buf[(int)p]; }
        @Override public void set(long p, byte b) { this.buf[(int)p] = b; }
        @Override public int length() { return this.buf.length; }
        @Override public Object clone() {
        	HeapChunk hc = new HeapChunk(this.buf.length);
        	System.arraycopy(this.buf, 0, hc.buf, 0, this.buf.length);
        	return hc;
        }
    }

    /** File-backed chunk using MappedByteBuffer (lazy mapped). */
    private static final class FileChunk implements Chunk, Cloneable {
        final FileChannel ch;
        final long fileOffset;       // offset in the file where this chunk starts
        final int len;
        final boolean writable;
        private volatile MappedByteBuffer mm; // lazily created

        FileChunk(FileChannel ch, long fileOffset, int len, boolean writable) {
            this.ch = ch; this.fileOffset = fileOffset; this.len = len; this.writable = writable;
        }
        private MappedByteBuffer map() {
            MappedByteBuffer local = this.mm;
            if (local == null) {
                synchronized (this) {
                    local = this.mm;
                    if (local == null) {
                        final FileChannel.MapMode mapMode = this.writable ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY;
                        try {
                            this.mm = local = this.ch.map(mapMode, this.fileOffset, this.len);
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
            return local;
        }
        @Override public int read(long p, byte[] dst, int off, int len) {
            if (p >= this.len) return -1;
            final int take = Math.min(len, this.len - (int)p);
            final ByteBuffer dup = this.map().duplicate();
            dup.position((int)p).limit((int)p + take);
            dup.get(dst, off, take);
            return take;
        }
        @Override public int write(long p, byte[] src, int off, int len) {
            if (!this.writable) throw new RuntimeException("FileChunk is read-only");
            if (p >= this.len) return -1;
            final int take = Math.min(len, this.len - (int)p);
            final ByteBuffer dup = this.map().duplicate();
            dup.position((int)p).limit((int)p + take);
            dup.put(src, off, take);
            return take;
        }
        @Override public byte get(long p) {
            return this.map().get((int)p);
        }
        @Override public void set(long p, byte b) {
            if (!this.writable) throw new RuntimeException("FileChunk is read-only");
            this.map().put((int)p, b);
        }
        @Override public int length() { return this.len; }
        @Override public Object clone() { return new FileChunk(this.ch, this.fileOffset, this.len, this.writable); }
        @Override public void close() {
            // Best-effort explicit unmap to release file handles promptly.
            final MappedByteBuffer local = this.mm;
            if (local != null) {
                try { Unmapper.unmap(local); } catch (final Throwable ignored) {}
            }
            // Channel is closed by owner (we don’t own it here).
        }
    }

    /** Best-effort unmapper compatible with Java 8+ (Unsafe.invokeCleaner fallback). */
    private static final class Unmapper {

        static void unmap(MappedByteBuffer bb) throws Exception {
            // Java 9+: Unsafe.invokeCleaner
            try {
                final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                final var theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                final Object unsafe = theUnsafe.get(null);
                unsafeClass.getMethod("invokeCleaner", MappedByteBuffer.class).invoke(unsafe, bb);
                return;
            } catch (final Throwable ignore) {}
            // Java 8: DirectBuffer.cleaner().clean()
            final Class<?> directBuffer = Class.forName("sun.nio.ch.DirectBuffer");
            final Object db = directBuffer.cast(bb);
            final Object cleaner = directBuffer.getMethod("cleaner").invoke(db);
            if (cleaner != null) cleaner.getClass().getMethod("clean").invoke(cleaner);
        }

    }

    // ------------------- public API -------------------

    /** Total logical size in bytes. */
    public long size() { return this.size; }

    /** Append bytes from an InputStream into on-heap chunks. */
    public void writeFrom(InputStream in) throws IOException {
        final byte[] tmp = new byte[64 * 1024];
        int r;
        while ((r = in.read(tmp)) != -1) {
            // InputStream.read(byte[]) may legally return 0
            if (r == 0) continue;
            this.append(tmp, 0, r);
        }

    }

    /** Append a byte array (copied into on-heap chunks). */
    public void append(byte[] src, int off, int len) {
        while (len > 0) {
            int space = this.spaceInTailHeapChunk();
            if (space == 0) {
                // size the new chunk to what we need now (up to CHUNK_SIZE)
                this.newTailHeapChunk(len);
                space = this.spaceInTailHeapChunk();
            }
            final Segment tail = this.segments.get(this.segments.size() - 1);

            final int take = Math.min(len, space);

            // WRITE AT CURRENT USED LENGTH IN THE TAIL CHUNK (not length - space)
            final int written = tail.chunk.write(tail.length, src, off, take);
            if (written <= 0) break; // defensive; shouldn't happen with HeapChunk

            this.growTailLength(tail, written);
            off  += written;
            len  -= written;
            this.size += written;
        }
    }

    public void append(byte[] src) {
        this.append(src, 0, src.length);
    }

    public void append(String src, int off, int len) {
        this.append(src.substring(off, off + len));
    }

    public void append(String src) {
        this.append(UTF8.getBytes(src));
    }

    /** Adopt an entire file as zero-copy file-backed segments (read-only). */
    public void appendFile(Path path) {
        this.appendFile(path, false);
    }

    /** Adopt a file; set writable=true to allow modifications to the mapped bytes. */
    public void appendFile(Path path, boolean writable) {
        FileChannel ch = null;
        try {
            ch = FileChannel.open(path, writable
                    ? new OpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE}
                    : new OpenOption[]{StandardOpenOption.READ});
            final long fileSize = ch.size();
            long pos = 0;
            while (pos < fileSize) {
                final int len = (int)Math.min(CHUNK_SIZE, fileSize - pos);
                this.segments.add(new Segment(new FileChunk(ch, pos, len, writable), this.size, len));
                this.size += len;
                pos += len;
            }
            // We keep the FileChannel open until this ChunkedBytes is closed.
            this.fileChannels.add(ch);
            ch = null; // prevent closing in finally
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (ch != null) try { ch.close(); } catch (final IOException ignore) {}
        }
    }

    /** Read into dst starting at logical position pos. Returns bytes read or -1 at EOF. */
    public int read(long pos, byte[] dst, int off, int len) {
        if (pos < 0) throw new IllegalArgumentException("pos < 0");
        if (pos >= this.size) return -1;
        long remaining = Math.min(len, this.size - pos);
        int done = 0;
        int idx = this.findSegment(pos);
        long p = pos;
        while (remaining > 0 && idx < this.segments.size()) {
            final Segment s = this.segments.get(idx);
            final long rel = p - s.start;
            final int take = (int)Math.min(remaining, s.length - rel);
            final int n = s.chunk.read(rel, dst, off + done, take);
            if (n <= 0) break;
            done += n; p += n; remaining -= n;
            if (rel + n >= s.length) idx++;
        }
        return done == 0 ? -1 : done;
    }

    /** Write bytes at logical position pos (requires writable backing for those ranges). */
    public int write(long pos, byte[] src, int off, int len) {
        if (pos < 0) throw new IllegalArgumentException("pos < 0");
        if (pos >= this.size) return -1;
        long remaining = Math.min(len, this.size - pos);
        int done = 0; int idx = this.findSegment(pos); long p = pos;
        while (remaining > 0 && idx < this.segments.size()) {
            final Segment s = this.segments.get(idx);
            final long rel = p - s.start;
            final int take = (int)Math.min(remaining, s.length - rel);
            final int n = s.chunk.write(rel, src, off + done, take);
            if (n <= 0) break;
            done += n; p += n; remaining -= n;
            if (rel + n >= s.length) idx++;
        }
        return done == 0 ? -1 : done;
    }

    /** InputStream view (no copying), supports >2 GB. */
    public InputStream openStream() {
        return new InputStream() {
            long pos = 0;
            @Override public int read() throws IOException {
                final byte[] one = new byte[1];
                final int n = this.read(one, 0, 1);
                return n < 0 ? -1 : (one[0] & 0xFF);
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                final int n = ChunkedBytes.this.read(this.pos, b, off, len);
                if (n > 0) this.pos += n;
                return n;
            }
            @Override public long skip(long n) {
                final long k = Math.min(n, ChunkedBytes.this.size - this.pos);
                this.pos += k; return k;
            }
            @Override public int available() {
                final long rem = ChunkedBytes.this.size - this.pos;
                return rem > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rem;
            }
        };
    }

    /** Write the whole content to an OutputStream. */
    public void writeTo(OutputStream out) {
        final byte[] tmp = new byte[256 * 1024];
        long p = 0;
        try {
            while (p < this.size) {
                final int n = this.read(p, tmp, 0, tmp.length);
                if (n < 0) break;
                out.write(tmp, 0, n);
                p += n;
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Materialize as a single byte[] (only if total size fits in an int). */
    public byte[] toByteArray() {
        if (this.size > Integer.MAX_VALUE) throw new RuntimeException("Size > Integer.MAX_VALUE");
        final byte[] all = new byte[(int) this.size];
        this.writeTo(new ByteArrayOutputStream() {
            int offset = 0;
            @Override public void write(byte[] b, int off, int len) {
                System.arraycopy(b, off, all, this.offset, len);
                this.offset += len;
            }
        });
        return all;
    }

    @Override
    public synchronized void write(int b) {
        // Append single byte at end (OutputStream semantics)
        final byte[] one = new byte[] { (byte) b };
        this.append(one, 0, 1);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        if (b == null) throw new NullPointerException("b");
        if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
        // Append to end; grows with heap chunks as needed
        this.append(b, off, len);
    }

    public void writeBytes(byte[] b) {
        this.write(b, 0, b.length);
    }

    @Override
    public void flush() {
        // No-op for heap chunks; file-backed segments via MappedByteBuffer are
        // written eagerly. If you need a hard sync to disk, expose a separate
        // method to force() mapped buffers.
    }

    @Override public void close() {
        // Close/unmap all segments and channels.
        Exception first = null;
        for (final Segment s : this.segments) {
            try { s.close(); } catch (final Exception e) { if (first == null) first = e; }
        }
        for (final FileChannel ch : this.fileChannels) {
            try { ch.close(); } catch (final Exception e) { if (first == null) first = e; }
        }
        if (first != null) throw new RuntimeException(first.getMessage());
    }

    @Override
    public String toString() {
        return UTF8.String(this.toByteArray());
    }

    public byte get(long pos) {
        if (pos < 0) throw new IllegalArgumentException("pos < 0");
        if (pos >= this.size) throw new RuntimeException("pos >= size");
        final int idx = this.findSegment(pos);
        final Segment s = this.segments.get(idx);
        final long rel = pos - s.start;
        return s.chunk.get(rel);
    }

    @Override
    public Object clone() {
		List<Segment> list = new ArrayList<>(this.segments.size());
		for (Segment s: this.segments) list.add((Segment) s.clone());
		final ChunkedBytes cb = new ChunkedBytes(list, this.size);
		return cb;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof ChunkedBytes) {
            final ChunkedBytes cb = (ChunkedBytes)o;
            if (this.size != cb.size) return false;
            for (long i = 0; i < this.size; i++) {
                if (this.get(i) != cb.get(i)) return false;
            }
            return true;
        }
        if (o instanceof byte[]) {
            final byte[] b = (byte[])o;
            if (this.size != b.length) return false;
            for (int i = 0; i < b.length; i++) {
                if (this.get(i) != b[i]) return false;
            }
            return true;
        }
        if (o instanceof String) {
            return this.equals(UTF8.getBytes((String) o));
        }
        return false;
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof ChunkedBytes) {
            final ChunkedBytes cb = (ChunkedBytes)o;
            final int minLen = (int)Math.min(this.size, cb.size);
            for (int i = 0; i < minLen; i++) {
                final int diff = (this.get(i) & 0xFF) - (cb.get(i) & 0xFF);
                if (diff != 0) return diff;
            }
            return Long.compare(this.size, cb.size);
        }
        if (o instanceof byte[]) {
            final byte[] b = (byte[])o;
            final int minLen = (int)Math.min(this.size, b.length);
            for (int i = 0; i < minLen; i++) {
                final int diff = (this.get(i) & 0xFF) - (b[i] & 0xFF);
                if (diff != 0) return diff;
            }
            return Long.compare(this.size, b.length);
        }
        if (o instanceof String) {
            return this.compareTo(UTF8.getBytes((String) o));
        }
        throw new IllegalArgumentException("Cannot compare to " + (o == null ? "null" : o.getClass().getName()));
    }

    // ------------------- internals -------------------

    private final List<FileChannel> fileChannels = new ArrayList<>();

    private int findSegment(long pos) {
        int lo = 0, hi = this.segments.size() - 1;
        while (lo <= hi) {
            final int mid = (lo + hi) >>> 1;
            final Segment s = this.segments.get(mid);
            if (pos < s.start) hi = mid - 1;
            else if (pos >= s.start + s.length) lo = mid + 1;
            else return mid;
        }
        return Math.max(0, Math.min(lo, this.segments.size() - 1));
    }

    private int spaceInTailHeapChunk() {
        if (this.segments.isEmpty()) return 0;
        final Segment tail = this.segments.get(this.segments.size() - 1);
        if (!(tail.chunk instanceof HeapChunk)) return 0;
        return tail.length < tail.chunk.length() ? tail.chunk.length() - tail.length : 0;
    }

    private void newTailHeapChunk(int minCapacity) {
        final int cap = Math.min(CHUNK_SIZE, minCapacity);
        this.segments.add(new Segment(new HeapChunk(cap), this.size, 0));
   }

    private void growTailLength(Segment tail, int inc) {
        // We cannot actually change 'length' as it's final; create a new Segment with updated length.
        final int idx = this.segments.size() - 1;
        this.segments.set(idx, new Segment(tail.chunk, tail.start, tail.length + inc));
    }

 // === Add inside ChunkedBytes class ===
    public static void main(String[] args) throws Exception {
        System.out.println("ChunkedBytes test starting. CHUNK_SIZE=" + (CHUNK_SIZE / (1024*1024)) + " MiB");

        // --------- Parameters ----------
        final long seed = 0x5eedCafeL;
        final long bigLen = 5L * CHUNK_SIZE + (CHUNK_SIZE / 2) + 12345; // > 5 chunks
        final int ioBuf = 1 << 20; // 1 MiB streaming buffer

        // Prepare sample offsets across boundaries
        final long[] samples = new long[] {
            0L,
            CHUNK_SIZE - 1L,
            CHUNK_SIZE,
            CHUNK_SIZE + 1L,
            3L * CHUNK_SIZE - 1L,
            3L * CHUNK_SIZE,
            5L * CHUNK_SIZE + 17L,
            bigLen - 1L
        };

        // =========================
        // A) HEAP-ONLY APPEND TESTS
        // =========================
        try (ChunkedBytes cb = new ChunkedBytes()) {
            System.out.println("[A] Heap-only append of random " + bigLen + " bytes (>5 chunks) via OutputStream");

            // Fill with random data using OutputStream semantics and collect digest + expected sample bytes
            final byte[] expectedSample = new byte[samples.length];
            final byte[] buf = new byte[ioBuf];
            final java.util.Random rnd = new java.util.Random(seed);
            final java.security.MessageDigest mdIn = java.security.MessageDigest.getInstance("SHA-256");

            long pos = 0;
            int si = 0;
            while (pos < bigLen) {
                final int n = (int)Math.min(buf.length, bigLen - pos);
                rnd.nextBytes(buf);
                mdIn.update(buf, 0, n);
                // write using OutputStream.write(byte[],off,len)
                cb.write(buf, 0, n);

                // capture sample bytes as we stream
                while (si < samples.length && samples[si] >= pos && samples[si] < pos + n) {
                    expectedSample[si] = buf[(int)(samples[si] - pos)];
                    si++;
                }
                pos += n;
            }
            final byte[] digestOriginal = mdIn.digest();
            System.out.println("  Original SHA-256: " + toHex(digestOriginal));

            // Validate size
            assertEquals(bigLen, cb.size(), "[A] size");

            // Digest of content read back
            final byte[] digestCb = sha256Of(cb.openStream(), ioBuf);
            System.out.println("  CB stream SHA-256: " + toHex(digestCb));
            assertArrayEquals(digestOriginal, digestCb, "[A] digest equality");

            // Validate sample bytes via random-access read()
            for (int i = 0; i < samples.length; i++) {
                final byte b = readByteAt(cb, samples[i]);
                if (b != expectedSample[i]) {
                    throw new AssertionError("[A] sample mismatch at " + samples[i]);
                }
            }
            System.out.println("  Sample point checks: OK");

            // Test read spanning a boundary
            final long crossStart = CHUNK_SIZE - 2L;
            final byte[] got = new byte[5];
            final int n = cb.read(crossStart, got, 0, got.length);
            assertEquals(5, n, "[A] cross-boundary read length");
            final byte[] expect = regenRange(seed, bigLen, crossStart, 5, ioBuf);
            assertArrayEquals(expect, got, "[A] cross-boundary bytes");

            // Test write(long pos, byte[]...) modifying content and verifying
            final byte[] patch = new byte[] {99, 98, 97, 0, 1, 2, 3};
            final long patchPos = 2L * CHUNK_SIZE + 7;
            final int wrote = cb.write(patchPos, patch, 0, patch.length);
            assertEquals(patch.length, wrote, "[A] write length");
            final byte[] check = new byte[patch.length];
            final int rn = cb.read(patchPos, check, 0, check.length);
            assertEquals(patch.length, rn, "[A] reread length");
            assertArrayEquals(patch, check, "[A] write verification");

            // Test InputStream skip/available/EoF
            try (InputStream in = cb.openStream()) {
                final long skipped = in.skip(patchPos);
                assertEquals(patchPos, skipped, "[A] skip");
                final int avail = in.available();
                if (avail <= 0) throw new AssertionError("[A] available should be > 0 after skip");
                final byte[] tmp = in.readNBytes(32);
                if (tmp.length == 0) throw new AssertionError("[A] read after skip failed");
                // drain
                while (in.read(tmp) >= 0) { /* drain */ }
                if (in.read() != -1) throw new AssertionError("[A] EOF expected");
            }

            // Test writeTo(OutputStream) into a digest sink
            final byte[] digestAfter = sha256Of(cb.openStream(), ioBuf);
            final java.security.MessageDigest mdSink = java.security.MessageDigest.getInstance("SHA-256");
            cb.writeTo(new java.security.DigestOutputStream(new NullOutputStream(), mdSink));
            final byte[] digestWriteTo = mdSink.digest();
            assertArrayEquals(digestAfter, digestWriteTo, "[A] writeTo digest (post-mutation)");

            // Small dataset to test toByteArray()
            try (ChunkedBytes small = new ChunkedBytes()) {
                final byte[] sm = new byte[15000];
                new java.util.Random(123).nextBytes(sm);
                small.write(sm); // OutputStream API
                final byte[] smOut = small.toByteArray();
                assertArrayEquals(sm, smOut, "[A] toByteArray");
            }

            System.out.println("[A] Heap-only tests: OK");
        }

        // =========================================
        // B) FILE-BACKED MAPPING (READ-ONLY) TESTS
        // =========================================
        final Path tmpFile = Files.createTempFile("cb-ro-", ".bin");
        try {
            final long fileLen = 3L * CHUNK_SIZE + 12345;
            System.out.println("[B] Create temp file (read-only mapping) len=" + fileLen);
            byte[] fileDigest;
            try (OutputStream fout = Files.newOutputStream(tmpFile)) {
                fileDigest = writeRandomToStream(fout, seed + 1, fileLen, ioBuf);
            }
            System.out.println("  File SHA-256: " + toHex(fileDigest));

            try (ChunkedBytes cb = new ChunkedBytes()) {
                cb.appendFile(tmpFile); // read-only map
                assertEquals(fileLen, cb.size(), "[B] size");
                final byte[] cbDigest = sha256Of(cb.openStream(), ioBuf);
                System.out.println("  CB map SHA-256: " + toHex(cbDigest));
                assertArrayEquals(fileDigest, cbDigest, "[B] digest equality");

                // spot check boundary
                final long pos = CHUNK_SIZE - 3;
                final byte[] got = new byte[9];
                final int m = cb.read(pos, got, 0, got.length);
                assertEquals(9, m, "[B] boundary read length");
                final byte[] exp = regenRange(seed + 1, fileLen, pos, 9, ioBuf);
                assertArrayEquals(exp, got, "[B] boundary bytes");
            }
            System.out.println("[B] Read-only mapping tests: OK");
        } finally {
            try { Files.deleteIfExists(tmpFile); } catch (final Exception ignore) {}
        }

        // =========================================
        // C) FILE-BACKED MAPPING (WRITABLE) TESTS
        // =========================================
        final Path tmpRW = Files.createTempFile("cb-rw-", ".bin");
        try {
            final long fileLen = 2L * CHUNK_SIZE + 777;
            System.out.println("[C] Create temp file (writable mapping) len=" + fileLen);
            try (OutputStream fout = Files.newOutputStream(tmpRW)) {
                writeRandomToStream(fout, seed + 2, fileLen, ioBuf);
            }

            try (ChunkedBytes cb = new ChunkedBytes()) {
                cb.appendFile(tmpRW, true); // writable
                // Modify three places: start, boundary, end-5
                final long[] offs = new long[] { 0L, CHUNK_SIZE, fileLen - 5 };
                final byte[][] patches = new byte[][] {
                    {7,6,5,4,3},
                    {1,2,3,4},
                    {-1,-2,-3,-4,-5}
                };
                for (int i = 0; i < offs.length; i++) {
                    final int w = cb.write(offs[i], patches[i], 0, patches[i].length);
                    assertEquals(patches[i].length, w, "[C] write length " + i);
                    final byte[] chk = new byte[patches[i].length];
                    final int r = cb.read(offs[i], chk, 0, chk.length);
                    assertEquals(chk.length, r, "[C] reread len " + i);
                    assertArrayEquals(patches[i], chk, "[C] content verify " + i);
                }
            }
            // Verify on-disk after close()
            try (var ch = FileChannel.open(tmpRW, StandardOpenOption.READ)) {
                final byte[] p0 = new byte[5]; readFully(ch, 0L, p0);
                assertArrayEquals(new byte[]{7,6,5,4,3}, p0, "[C] disk verify 0");
                final byte[] p1 = new byte[4]; readFully(ch, CHUNK_SIZE, p1);
                assertArrayEquals(new byte[]{1,2,3,4}, p1, "[C] disk verify 1");
                final byte[] p2 = new byte[5]; readFully(ch, (2L * CHUNK_SIZE + 777) - 5, p2);
                assertArrayEquals(new byte[]{-1,-2,-3,-4,-5}, p2, "[C] disk verify 2");
            }
            System.out.println("[C] Writable mapping tests: OK");
        } finally {
            try { Files.deleteIfExists(tmpRW); } catch (final Exception ignore) {}
        }

        // ==========================
        // D) MIXED SOURCES TESTS
        // ==========================
        System.out.println("[D] Mixed sources (heap + file + single-byte writes)");
        final Path tmpMix = Files.createTempFile("cb-mix-", ".bin");
        // prepare file contents used in this test
        final long mixLen = CHUNK_SIZE + 333;
        try (OutputStream fout = Files.newOutputStream(tmpMix)) {
            writeRandomToStream(fout, seed + 3, mixLen, ioBuf);
        }
        try (ChunkedBytes cb = new ChunkedBytes()) {
            // 1) small heap prefix
            final byte[] prefix = new byte[5000];
            new java.util.Random(42).nextBytes(prefix);
            cb.write(prefix); // OutputStream API

            // 2) file segment
            cb.appendFile(tmpMix); // read-only

            // 3) a tail written byte-by-byte
            for (int i = 0; i < 1000; i++) cb.write(i & 0xFF);

            // Verify size
            final long expectedSize = prefix.length + Files.size(tmpMix) + 1000L;
            assertEquals(expectedSize, cb.size(), "[D] size");

            // Spot checks
            // prefix region
            final byte[] got = new byte[prefix.length];
            final int r = cb.read(0, got, 0, got.length);
            assertEquals(prefix.length, r, "[D] prefix read len");
            assertArrayEquals(prefix, got, "[D] prefix bytes");

            // file region slice
            final byte[] fileSliceExpected = regenRange(seed + 3, Files.size(tmpMix), 123, 256, ioBuf);
            final byte[] fileSliceGot = new byte[256];
            final int r2 = cb.read(prefix.length + 123, fileSliceGot, 0, fileSliceGot.length);
            assertEquals(256, r2, "[D] file slice len");
            assertArrayEquals(fileSliceExpected, fileSliceGot, "[D] file slice bytes");

            // tail region last 10
            final byte[] tail = new byte[10];
            final int r3 = cb.read(expectedSize - 10, tail, 0, 10);
            assertEquals(10, r3, "[D] tail len");
            for (int i = 0; i < 10; i++) {
                final byte exp = (byte)((1000 - 10) + i & 0xFF);
                if (tail[i] != exp) throw new AssertionError("[D] tail byte mismatch at i=" + i);
            }

            // writeTo digest equals digest of concatenation? We can't easily combine digests here;
            // just ensure writeTo writes full length by counting bytes.
            final CountingOutputStream cos = new CountingOutputStream();
            cb.writeTo(cos);
            assertEquals(expectedSize, cos.count, "[D] writeTo count");
        } finally {
            try { Files.deleteIfExists(tmpMix); } catch (final Exception ignore) {}
        }
        System.out.println("[D] Mixed sources tests: OK");

        System.out.println("All tests PASSED.");
    }

    // ----- helpers -----

    private static final class NullOutputStream extends OutputStream {
        @Override public void write(int b) {}
        @Override public void write(byte[] b, int off, int len) {}
    }

    private static final class CountingOutputStream extends OutputStream {
        long count = 0;
        @Override public void write(int b) { this.count++; }
        @Override public void write(byte[] b, int off, int len) { this.count += len; }
    }

    private static void assertEquals(long exp, long got, String where) {
        if (exp != got) throw new AssertionError(where + ": expected " + exp + " but got " + got);
    }
    private static void assertEquals(int exp, int got, String where) {
        if (exp != got) throw new AssertionError(where + ": expected " + exp + " but got " + got);
    }
    private static void assertArrayEquals(byte[] exp, byte[] got, String where) {
        if (!java.util.Arrays.equals(exp, got)) {
            throw new AssertionError(where + ": arrays differ");
        }
    }
    private static String toHex(byte[] d) {
        final StringBuilder sb = new StringBuilder(d.length * 2);
        for (final byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
    private static byte[] sha256Of(InputStream in, int bufSize) throws Exception {
        final java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        final byte[] buf = new byte[bufSize];
        int n;
        while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
        return md.digest();
    }
    private static byte readByteAt(ChunkedBytes cb, long pos) throws IOException {
        final byte[] one = new byte[1];
        final int n = cb.read(pos, one, 0, 1);
        if (n != 1) throw new IOException("Unable to read at pos=" + pos);
        return one[0];
    }
    private static byte[] regenRange(long seed, long totalLen, long start, int len, int bufSize) throws IOException {
        if (start + len > totalLen) throw new IOException("range exceeds totalLen");
        final java.util.Random rnd = new java.util.Random(seed);
        final byte[] buf = new byte[bufSize];
        long pos = 0;
        final byte[] out = new byte[len];
        int outPos = 0;
        while (pos < totalLen && outPos < len) {
            final int n = (int)Math.min(buf.length, totalLen - pos);
            rnd.nextBytes(buf);
            final long end = pos + n;
            if (start < end && (start + len) > pos) {
                final long s = Math.max(start, pos);
                final long e = Math.min(start + len, end);
                final int copy = (int)(e - s);
                System.arraycopy(buf, (int)(s - pos), out, outPos, copy);
                outPos += copy;
            }
            pos = end;
        }
        return out;
    }
    private static byte[] writeRandomToStream(OutputStream out, long seed, long length, int bufSize) throws Exception {
        final java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        final java.util.Random rnd = new java.util.Random(seed);
        final byte[] buf = new byte[bufSize];
        long pos = 0;
        while (pos < length) {
            final int n = (int)Math.min(buf.length, length - pos);
            rnd.nextBytes(buf);
            out.write(buf, 0, n);
            md.update(buf, 0, n);
            pos += n;
        }
        out.flush();
        return md.digest();
    }
    private static void readFully(FileChannel ch, long pos, byte[] dst) throws IOException {
        final ByteBuffer bb = ByteBuffer.wrap(dst);
        while (bb.hasRemaining()) {
            final int n = ch.read(bb, pos);
            if (n < 0) throw new EOFException();
            pos += n;
        }
    }

}
