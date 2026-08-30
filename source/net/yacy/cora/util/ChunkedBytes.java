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
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    private volatile boolean closed;

    public ChunkedBytes() {
        this.segments = new ArrayList<>();
        this.size = 0;
        this.closed = false;
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
    
    private ChunkedBytes(final List<Segment> segments, final long size,
            final List<FileBacking> fileBackings) {
        this.segments = new ArrayList<>(segments);
        this.size = size;
        this.fileBackings.addAll(fileBackings);
        this.closed = false;
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
        void force() { this.chunk.force(); }
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
        default void force() { /* heap and read-only chunks need no synchronization */ }
        @Override default void close() throws IOException { /* no-op by default */ }
    }

    /**
     * One channel ownership shared by all segments of an appended file and by
     * read-only clones. A reference belongs to a ChunkedBytes instance, not to
     * every segment, so a multi-chunk file is closed exactly once per owner.
     */
    private static final class FileBacking implements Closeable {
        final FileChannel channel;
        private final AtomicInteger references;

        FileBacking(final FileChannel channel) {
            this.channel = channel;
            this.references = new AtomicInteger(1);
        }

        FileBacking retain() {
            int current;
            do {
                current = this.references.get();
                if (current <= 0) throw new IllegalStateException("File backing is closed");
                if (current == Integer.MAX_VALUE) {
                    throw new IllegalStateException("Too many references to file backing");
                }
            } while (!this.references.compareAndSet(current, current + 1));
            return this;
        }

        @Override
        public void close() throws IOException {
            final int remaining = this.references.decrementAndGet();
            if (remaining == 0) {
                this.channel.close();
            } else if (remaining < 0) {
                throw new IOException("File backing closed more than once");
            }
        }
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
        final FileBacking backing;
        final long fileOffset;       // offset in the file where this chunk starts
        final int len;
        final boolean writable;
        private volatile MappedByteBuffer mm; // lazily created

        FileChunk(FileBacking backing, long fileOffset, int len, boolean writable) {
            this.backing = backing; this.fileOffset = fileOffset; this.len = len; this.writable = writable;
        }
        private MappedByteBuffer map() {
            MappedByteBuffer local = this.mm;
            if (local == null) {
                synchronized (this) {
                    local = this.mm;
                    if (local == null) {
                        final FileChannel.MapMode mapMode = this.writable ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY;
                        try {
                            this.mm = local = this.backing.channel.map(mapMode, this.fileOffset, this.len);
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
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
            if (!this.writable) throw new ReadOnlyBufferException();
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
            if (!this.writable) throw new ReadOnlyBufferException();
            this.map().put((int)p, b);
        }
        @Override public int length() { return this.len; }
        @Override public Object clone() { return new FileChunk(this.backing, this.fileOffset, this.len, this.writable); }
        @Override public void force() {
            final MappedByteBuffer local = this.mm;
            if (this.writable && local != null) local.force();
        }
        @Override public void close() throws IOException {
            /* Clear the reference before cleaning so close cannot clean it twice. */
            final MappedByteBuffer local;
            synchronized (this) {
                local = this.mm;
                this.mm = null;
            }
            if (local != null) {
                try {
                    Unmapper.unmap(local);
                } catch (final Exception e) {
                    throw new IOException("Cannot unmap file chunk at " + this.fileOffset, e);
                }
            }
            // The shared FileBacking is released by the owning ChunkedBytes.
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
                unsafeClass.getMethod("invokeCleaner", ByteBuffer.class).invoke(unsafe, bb);
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
    public long size() {
        requireOpen();
        return this.size;
    }

    /** Append bytes from an InputStream into on-heap chunks. */
    public void writeFrom(final InputStream in) throws IOException {
        requireOpen();
        if (in == null) throw new NullPointerException("in");
        final byte[] tmp = new byte[64 * 1024];
        int r;
        while ((r = in.read(tmp)) != -1) {
            // InputStream.read(byte[]) may legally return 0
            if (r == 0) continue;
            /*
             * InputStream.read() is foreign code and must run without holding the
             * ChunkedBytes monitor. Otherwise an input implementation which owns
             * another lock can invert that lock order when its owner concurrently
             * calls append() or close(). append() provides the required, narrowly
             * scoped synchronization for the actual state change.
             */
            this.append(tmp, 0, r);
        }

    }

    /** Append a byte array (copied into on-heap chunks). */
    public synchronized void append(final byte[] src, int off, int len) {
        requireOpen();
        checkBounds(src, off, len, "src");
        if (len == 0) return;
        Math.addExact(this.size, len);
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

    public void append(final byte[] src) {
        if (src == null) throw new NullPointerException("src");
        this.append(src, 0, src.length);
    }

    public void append(final String src, final int off, final int len) {
        if (src == null) throw new NullPointerException("src");
        this.append(src.substring(off, off + len));
    }

    public void append(final String src) {
        if (src == null) throw new NullPointerException("src");
        this.append(UTF8.getBytes(src));
    }

    /** Adopt an entire file as zero-copy file-backed segments (read-only). */
    public void appendFile(final Path path) {
        this.appendFile(path, false);
    }

    /** Adopt a file; set writable=true to allow modifications to the mapped bytes. */
    public synchronized void appendFile(final Path path, final boolean writable) {
        requireOpen();
        if (path == null) throw new NullPointerException("path");
        FileChannel ch = null;
        try {
            ch = FileChannel.open(path, writable
                    ? new OpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE}
                    : new OpenOption[]{StandardOpenOption.READ});
            final long fileSize = ch.size();
            if (fileSize == 0) return;
            final long newSize = Math.addExact(this.size, fileSize);
            final FileBacking backing = new FileBacking(ch);
            final List<Segment> fileSegments = new ArrayList<>();
            long pos = 0;
            while (pos < fileSize) {
                final int len = (int)Math.min(CHUNK_SIZE, fileSize - pos);
                fileSegments.add(new Segment(
                        new FileChunk(backing, pos, len, writable), this.size + pos, len));
                pos += len;
            }
            this.segments.addAll(fileSegments);
            this.size = newSize;
            // The shared backing owns the channel until this instance and all clones close.
            this.fileBackings.add(backing);
            ch = null; // prevent closing in finally
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (ch != null) try { ch.close(); } catch (final IOException ignore) {}
        }
    }

    /** Read into dst starting at logical position pos. Returns bytes read or -1 at EOF. */
    public int read(final long pos, final byte[] dst, final int off, final int len) {
        requireOpen();
        if (pos < 0) throw new IllegalArgumentException("pos < 0");
        checkBounds(dst, off, len, "dst");
        if (len == 0) return 0;
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
    public int write(final long pos, final byte[] src, final int off, final int len) {
        requireOpen();
        if (pos < 0) throw new IllegalArgumentException("pos < 0");
        checkBounds(src, off, len, "src");
        if (len == 0) return 0;
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
        requireOpen();
        return new InputStream() {
            long pos = 0;
            boolean streamClosed = false;
            private void requireStreamOpen() throws IOException {
                if (this.streamClosed) throw new IOException("Stream is closed");
            }
            @Override public int read() throws IOException {
                requireStreamOpen();
                final byte[] one = new byte[1];
                final int n = this.read(one, 0, 1);
                return n < 0 ? -1 : (one[0] & 0xFF);
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                requireStreamOpen();
                final int n = ChunkedBytes.this.read(this.pos, b, off, len);
                if (n > 0) this.pos += n;
                return n;
            }
            @Override public long skip(long n) throws IOException {
                requireStreamOpen();
                ChunkedBytes.this.requireOpen();
                if (n <= 0) return 0;
                final long k = Math.min(n, ChunkedBytes.this.size - this.pos);
                this.pos += k; return k;
            }
            @Override public int available() throws IOException {
                requireStreamOpen();
                ChunkedBytes.this.requireOpen();
                final long rem = ChunkedBytes.this.size - this.pos;
                return rem > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rem;
            }
            @Override public void close() {
                this.streamClosed = true;
            }
        };
    }

    /** Write the whole content to an OutputStream. */
    public void writeTo(final OutputStream out) {
        requireOpen();
        if (out == null) throw new NullPointerException("out");
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
            throw new UncheckedIOException(e);
        }
    }

    /** Materialize as a single byte[] (only if total size fits in an int). */
    public byte[] toByteArray() {
        requireOpen();
        if (this.size > Integer.MAX_VALUE) throw new IllegalStateException("Size > Integer.MAX_VALUE");
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
        requireOpen();
        // Append single byte at end (OutputStream semantics)
        final byte[] one = new byte[] { (byte) b };
        this.append(one, 0, 1);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        requireOpen();
        checkBounds(b, off, len, "b");
        // Append to end; grows with heap chunks as needed
        this.append(b, off, len);
    }

    public void writeBytes(byte[] b) {
        if (b == null) throw new NullPointerException("b");
        this.write(b, 0, b.length);
    }

    @Override
    public void flush() {
        force();
    }

    /** Force changes in every writable mapped chunk to its storage device. */
    public synchronized void force() {
        requireOpen();
        for (final Segment s : this.segments) {
            s.force();
        }
    }

    @Override
    public synchronized void close() {
        if (this.closed) return;

        Exception failure = null;
        try {
            /* Mapped writes must be forced before the buffers are unmapped. */
            force();
        } catch (final RuntimeException e) {
            failure = e;
        }
        this.closed = true;

        for (final Segment s : this.segments) {
            try {
                s.close();
            } catch (final Exception e) {
                failure = addFailure(failure, e);
            }
        }
        for (final FileBacking backing : this.fileBackings) {
            try {
                backing.close();
            } catch (final Exception e) {
                failure = addFailure(failure, e);
            }
        }
        if (failure instanceof UncheckedIOException) throw (UncheckedIOException) failure;
        if (failure instanceof IOException) {
            throw new UncheckedIOException("Cannot close ChunkedBytes", (IOException) failure);
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw new RuntimeException("Cannot close ChunkedBytes", failure);
    }

    @Override
    public String toString() {
        return UTF8.String(this.toByteArray());
    }

    public byte get(long pos) {
        requireOpen();
        if (pos < 0) throw new IllegalArgumentException("pos < 0");
        if (pos >= this.size) throw new IndexOutOfBoundsException("pos >= size");
        final int idx = this.findSegment(pos);
        final Segment s = this.segments.get(idx);
        final long rel = pos - s.start;
        return s.chunk.get(rel);
    }

    @Override
    public synchronized Object clone() {
        requireOpen();
        final List<FileBacking> retainedBackings = new ArrayList<>(this.fileBackings.size());
        try {
            for (final FileBacking backing : this.fileBackings) {
                retainedBackings.add(backing.retain());
            }
            final List<Segment> clonedSegments = new ArrayList<>(this.segments.size());
            for (final Segment segment : this.segments) {
                clonedSegments.add((Segment) segment.clone());
            }
            return new ChunkedBytes(clonedSegments, this.size, retainedBackings);
        } catch (final RuntimeException | Error e) {
            releaseRetainedBackings(retainedBackings, e);
            throw e;
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof ChunkedBytes) {
            final ChunkedBytes cb = (ChunkedBytes)o;
            if (this.size != cb.size) return false;
            return compareChunked(cb) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        requireOpen();
        int result = 1;
        final byte[] buffer = new byte[64 * 1024];
        long position = 0;
        while (position < this.size) {
            final int length = (int) Math.min(buffer.length, this.size - position);
            final int read = this.read(position, buffer, 0, length);
            if (read != length) throw new IllegalStateException("Short read while hashing");
            for (int i = 0; i < read; i++) result = 31 * result + buffer[i];
            position += read;
        }
        return result;
    }

    /** Content comparison retained for callers which previously used equals(byte[]). */
    public boolean contentEquals(final byte[] bytes) {
        requireOpen();
        if (bytes == null || this.size != bytes.length) return false;
        return compareBytes(bytes) == 0;
    }

    /** Content comparison retained for callers which previously used equals(String). */
    public boolean contentEquals(final String text) {
        requireOpen();
        return text != null && contentEquals(UTF8.getBytes(text));
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof ChunkedBytes) {
            return compareChunked((ChunkedBytes) o);
        }
        if (o instanceof byte[]) {
            return compareBytes((byte[]) o);
        }
        if (o instanceof String) {
            return this.compareTo(UTF8.getBytes((String) o));
        }
        throw new IllegalArgumentException("Cannot compare to " + (o == null ? "null" : o.getClass().getName()));
    }

    // ------------------- internals -------------------

    private final List<FileBacking> fileBackings = new ArrayList<>();

    private void requireOpen() {
        if (this.closed) throw new IllegalStateException("ChunkedBytes is closed");
    }

    private static void checkBounds(final byte[] bytes, final int offset,
            final int length, final String name) {
        if (bytes == null) throw new NullPointerException(name);
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IndexOutOfBoundsException(
                    "offset=" + offset + ", length=" + length
                    + ", array length=" + bytes.length);
        }
    }

    private static Exception addFailure(final Exception current, final Exception added) {
        if (current == null) return added;
        current.addSuppressed(added);
        return current;
    }

    private static void releaseRetainedBackings(
            final List<FileBacking> retainedBackings,
            final Throwable failure) {
        for (final FileBacking backing : retainedBackings) {
            try {
                backing.close();
            } catch (final IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private int compareChunked(final ChunkedBytes other) {
        requireOpen();
        other.requireOpen();
        final long minimumLength = Math.min(this.size, other.size);
        final byte[] left = new byte[64 * 1024];
        final byte[] right = new byte[left.length];
        long position = 0;
        while (position < minimumLength) {
            final int length = (int) Math.min(left.length, minimumLength - position);
            if (this.read(position, left, 0, length) != length
                    || other.read(position, right, 0, length) != length) {
                throw new IllegalStateException("Short read while comparing ChunkedBytes");
            }
            for (int i = 0; i < length; i++) {
                final int difference = (left[i] & 0xff) - (right[i] & 0xff);
                if (difference != 0) return difference;
            }
            position += length;
        }
        return Long.compare(this.size, other.size);
    }

    private int compareBytes(final byte[] bytes) {
        requireOpen();
        final long minimumLength = Math.min(this.size, bytes.length);
        final byte[] left = new byte[64 * 1024];
        long position = 0;
        while (position < minimumLength) {
            final int length = (int) Math.min(left.length, minimumLength - position);
            if (this.read(position, left, 0, length) != length) {
                throw new IllegalStateException("Short read while comparing bytes");
            }
            final int byteOffset = (int) position;
            for (int i = 0; i < length; i++) {
                final int difference = (left[i] & 0xff) - (bytes[byteOffset + i] & 0xff);
                if (difference != 0) return difference;
            }
            position += length;
        }
        return Long.compare(this.size, bytes.length);
    }

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
