/*
 *  SSTableHandleMap
 *  Copyright 2026 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *  First published 30.08.2026 on http://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 */

package net.yacy.kelondro.index;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.storage.ImmutableHandleMap;
import net.yacy.cora.util.ChunkedBytes;
import net.yacy.cora.util.SpaceExceededException;

/**
 * A file-backed {@link ImmutableHandleMap} over a sorted fixed-width index.
 *
 * <p>The backing file contains {@code keyLength + valueLength} byte records.
 * Keys are sorted with the supplied {@link ByteOrder}; values use the b256
 * encoding used by {@link RowHandleMap}. Lookups binary-search the mapped file,
 * so the number of entries does not determine Java heap consumption.</p>
 *
 * <p>Keys are immutable, but removals replace their non-negative file offset
 * with {@code -1} directly in the table. This keeps deletion state with the
 * index and needs no sidecar file. Adding or changing live values is deliberately
 * not supported. {@link #dump(File)} omits deleted records while preserving the
 * existing RowHandleMap dump format.</p>
 */
public final class SSTableHandleMap implements ImmutableHandleMap, Iterable<Map.Entry<byte[], Long>> {

    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final int DEFAULT_RUN_MEMORY_BYTES = 8 * 1024 * 1024;
    private static final int MAX_MERGE_FAN_IN = 64;
    private static final int ESTIMATED_ARRAY_OVERHEAD = 24;
    private static final byte[] DELETED_OFFSET = NaturalOrder.encodeLong(-1L, Long.BYTES);
    private static final String BUILD_LOCK_SUFFIX = ".sstlock";
    private static final Pattern WORKING_ARTIFACT = Pattern.compile(
            "^sst-work-[0-9a-f]{1,8}-.+\\.sstwork$");
    private static final Pattern BUILD_ARTIFACT = Pattern.compile(
            "^(sst-build-[0-9a-f]{1,8}-[0-9a-f]{32})-.+\\.(?:run|merge|sst)$");
    private static final Pattern BUILD_LOCK_ARTIFACT = Pattern.compile(
            "^sst-build-[0-9a-f]{1,8}-[0-9a-f]{32}\\.sstlock$");
    private static final Pattern LEGACY_BUILD_ARTIFACT = Pattern.compile(
            "^sst-[0-9a-f]{1,8}-.+\\.(?:run|merge|sst)$");

    private final int keyLength;
    private final int valueLength;
    private final int recordLength;
    private final int recordCount;
    private final ByteOrder ordering;
    private final AtomicInteger liveCount;
    private final Path promotionDirectory;
    private final String promotionPrefix;
    private final ReentrantReadWriteLock backingLock;

    /* These fields change together while backingLock's write lock is held. */
    private volatile File tableFile;
    private volatile ChunkedBytes table;
    private boolean writable;
    private boolean deleteOnClose;
    private FileChannel ownershipChannel;
    private FileLock ownershipLock;

    private volatile boolean closed;

    /** Result of one repository-wide temporary-artifact recovery pass. */
    public static final class RecoveryReport {
        private long candidates;
        private long deleted;
        private long inUse;
        private long failures;

        public long candidates() {
            return this.candidates;
        }

        public long deleted() {
            return this.deleted;
        }

        public long inUse() {
            return this.inUse;
        }

        public long failures() {
            return this.failures;
        }
    }

    /**
     * Open a sorted RowHandleMap dump as a Sorted String Table.
     * (where those strings may be represented by binary objects)
     *
     * @param keyLength fixed key width
     * @param ordering ordering used to sort keys in the file
     * @param valueLength fixed b256 value width; offsets require eight bytes
     * @param file uncompressed RowHandleMap dump
     * @throws IOException when the table is invalid
     */
    public SSTableHandleMap(final int keyLength, final ByteOrder ordering,
            final int valueLength, final File file) throws IOException {
        this(keyLength, ordering, valueLength, file,
                false, null, null, true, null, null);
    }

    private SSTableHandleMap(final int keyLength, final ByteOrder ordering,
            final int valueLength, final File file, final boolean deleteOnClose) throws IOException {
        this(keyLength, ordering, valueLength, file,
                deleteOnClose, null, null, true, null, null);
    }

    private SSTableHandleMap(final int keyLength, final ByteOrder ordering,
            final int valueLength, final File file, final boolean deleteOnClose,
            final FileChannel ownershipChannel, final FileLock ownershipLock,
            final boolean writable, final Path promotionDirectory,
            final String promotionPrefix) throws IOException {
        if (keyLength <= 0) throw new IllegalArgumentException("keyLength must be positive");
        if (valueLength != Long.BYTES) {
            throw new IllegalArgumentException("SSTable offsets require valueLength 8");
        }
        if (ordering == null) throw new NullPointerException("ordering");
        if (file == null) throw new NullPointerException("file");
        if (file.getName().endsWith(".gz")) {
            throw new IOException("SSTableHandleMap requires an uncompressed index: " + file);
        }
        if (!file.isFile()) throw new IOException("SSTable index does not exist: " + file);

        this.keyLength = keyLength;
        this.valueLength = valueLength;
        this.recordLength = Math.addExact(keyLength, valueLength);
        final long fileLength = file.length();
        if (fileLength % this.recordLength != 0) {
            throw new IOException("SSTable index length " + fileLength
                    + " is not a multiple of record length " + this.recordLength + ": " + file);
        }
        final long records = fileLength / this.recordLength;
        if (records > Integer.MAX_VALUE) {
            throw new IOException("SSTable contains too many records for HandleMap: " + records);
        }

        this.recordCount = (int) records;
        this.ordering = ordering;
        this.tableFile = file.getAbsoluteFile();
        this.table = mapFile(this.tableFile, writable);
        this.writable = writable;
        this.deleteOnClose = deleteOnClose;
        this.ownershipChannel = ownershipChannel;
        this.ownershipLock = ownershipLock;
        this.promotionDirectory = promotionDirectory;
        this.promotionPrefix = promotionPrefix;
        this.backingLock = new ReentrantReadWriteLock();
        this.closed = false;

        final int live;
        try {
            if (this.table.size() != fileLength) {
                throw new IOException("SSTable changed while it was opened: expected "
                        + fileLength + " bytes, mapped " + this.table.size() + ": " + file);
            }
            live = validateRecords();
        } catch (final IOException | RuntimeException | Error failure) {
            try {
                this.table.close();
            } catch (final RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        this.liveCount = new AtomicInteger(live);
    }

    /**
     * Validate every record before binary-searching the mapped table. Alignment
     * alone is insufficient: an out-of-order or duplicate key would make lookups
     * return false negatives, while a negative value other than the designated
     * {@code -1} tombstone has no defined meaning.
     */
    private int validateRecords() throws IOException {
        final byte[] previousKey = new byte[this.keyLength];
        final byte[] record = new byte[this.recordLength];
        int live = 0;

        for (int ordinal = 0; ordinal < this.recordCount; ordinal++) {
            readRecord(ordinal, record);
            if (ordinal > 0) {
                final int comparison = this.ordering.compare(
                        previousKey, 0, record, 0, this.keyLength);
                if (comparison >= 0) {
                    final String reason = comparison == 0
                            ? "duplicate key" : "keys are out of order";
                    throw new IOException("Invalid SSTable at records "
                            + (ordinal - 1) + " and " + ordinal + ": " + reason
                            + ": " + this.tableFile);
                }
            }

            final long value = NaturalOrder.decodeLong(
                    record, this.keyLength, this.valueLength);
            if (value == -1L) {
                /* The only valid negative value is the deletion tombstone. */
            } else if (value < 0L) {
                throw new IOException("Invalid negative SSTable offset " + value
                        + " at record " + ordinal + ": " + this.tableFile);
            } else {
                live++;
            }
            System.arraycopy(record, 0, previousKey, 0, this.keyLength);
        }
        return live;
    }

    /**
     * Open an immutable fingerprint snapshot with lazy writable promotion.
     *
     * <p>A fingerprint index belongs to one exact heap generation and is therefore
     * treated as a published, immutable cache artifact. This class nevertheless
     * has to persist removals by writing {@code -1} offsets. Mapping the fingerprint
     * itself writable would mix these two lifecycles and would also make deletion or
     * replacement of a mapped fingerprint unsafe on platforms such as Windows.</p>
     *
     * <p>An uncompressed snapshot is initially mapped read-only without creating a
     * working file. {@link #prepareForMutation()}, {@link #remove(byte[])},
     * {@link #removeone()}, or {@link #clear()} atomically copy the mapped bytes to a
     * private locked working file, switch the active mapping, and unmap the published
     * snapshot. This order also permits HeapReader to invalidate or replace the
     * fingerprint safely on Windows. A gzip snapshot cannot be mapped and is therefore
     * decompressed eagerly into its private working file.</p>
     *
     * @param sourceFingerprint sorted RowHandleMap fingerprint dump, optionally gzip compressed
     * @return an SSTable which promotes itself to a private writable file on demand
     */
    public static SSTableHandleMap openWorkingCopy(final int keyLength,
            final ByteOrder ordering, final int valueLength,
            final File sourceFingerprint) throws IOException {
        if (sourceFingerprint == null) throw new NullPointerException("sourceFingerprint");
        if (!sourceFingerprint.isFile()) {
            throw new IOException("Fingerprint index does not exist: " + sourceFingerprint);
        }
        final File directoryFile = sourceFingerprint.getAbsoluteFile().getParentFile();
        if (directoryFile == null) {
            throw new IOException("Fingerprint index has no parent directory: " + sourceFingerprint);
        }
        final Path directory = directoryFile.toPath();
        final String workingPrefix = workingPrefix(sourceFingerprint);

        if (!sourceFingerprint.getName().endsWith(".gz")) {
            return new SSTableHandleMap(
                    keyLength, ordering, valueLength, sourceFingerprint,
                    false, null, null, false, directory, workingPrefix);
        }

        Path workingFile = null;
        try {
            workingFile = Files.createTempFile(directory, workingPrefix, ".sstwork");
            copyFingerprint(sourceFingerprint.toPath(), workingFile);
            final SSTableHandleMap result = openOwnedWorkingFile(
                    keyLength, ordering, valueLength, workingFile.toFile());

            /* Ownership has moved to the returned map. */
            workingFile = null;
            return result;
        } finally {
            if (workingFile != null) Files.deleteIfExists(workingFile);
        }
    }

    /**
     * Map a completed private table before taking its ownership lock. On POSIX,
     * closing any descriptor for a file can release process-scoped record locks;
     * therefore no copy or mapping channel may be opened after this lock is taken.
     */
    private static SSTableHandleMap openOwnedWorkingFile(
            final int keyLength, final ByteOrder ordering,
            final int valueLength, final File workingFile) throws IOException {
        final SSTableHandleMap result = new SSTableHandleMap(
                keyLength, ordering, valueLength, workingFile, true,
                null, null, true, null, null);
        FileChannel ownershipChannel = null;
        FileLock ownershipLock = null;
        boolean complete = false;
        try {
            ownershipChannel = FileChannel.open(workingFile.toPath(),
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
            ownershipLock = ownershipChannel.tryLock();
            if (ownershipLock == null) {
                throw new IOException("Cannot lock SSTable working file " + workingFile);
            }
            result.ownershipChannel = ownershipChannel;
            result.ownershipLock = ownershipLock;
            complete = true;
            return result;
        } finally {
            if (!complete) {
                if (ownershipLock != null) {
                    try {
                        ownershipLock.release();
                    } catch (final IOException ignored) {
                    }
                }
                if (ownershipChannel != null) {
                    try {
                        ownershipChannel.close();
                    } catch (final IOException ignored) {
                    }
                }
                result.table.close();
            }
        }
    }

    private static void copyFingerprint(final Path source, final Path target) throws IOException {
        try (InputStream input = openFingerprintInput(source);
                OutputStream output = new BufferedOutputStream(Files.newOutputStream(target,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING),
                        COPY_BUFFER_SIZE)) {
            final byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
        }
    }

    private static InputStream openFingerprintInput(final Path source) throws IOException {
        final BufferedInputStream input = new BufferedInputStream(
                Files.newInputStream(source), COPY_BUFFER_SIZE);
        if (!source.getFileName().toString().endsWith(".gz")) return input;
        try {
            return new GZIPInputStream(input, COPY_BUFFER_SIZE);
        } catch (final IOException e) {
            try {
                input.close();
            } catch (final IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private static String workingPrefix(final File fingerprint) {
        return "sst-work-" + Integer.toHexString(
                fingerprint.getAbsolutePath().hashCode()) + "-";
    }

    /**
     * Remove private SSTable generations and builder files left by a crashed process.
     *
     * <p>The supplied roots are normalized and overlapping roots are scanned only
     * once. The walk does not follow symbolic links, so recovery cannot escape a
     * configured storage tree. Live tables lock their working file, while an active
     * builder locks one marker shared by all files of its build session. Such files
     * are counted as in use and are never removed. Legacy builder artifacts from
     * versions without a session marker are also recognized; recovery is therefore
     * intended to run before repositories start building indexes. All other I/O
     * failures are recorded instead of aborting application startup.</p>
     *
     * <p>This operation is intended to run once before repositories are opened. A
     * normal close still removes its own file immediately.</p>
     *
     * @param roots repository roots which may contain temporary SSTable artifacts
     * @return counts describing the best-effort recovery pass
     */
    public static RecoveryReport recoverWorkingCopies(final File... roots) {
        if (roots == null) throw new NullPointerException("roots");
        final RecoveryReport report = new RecoveryReport();
        final ArrayList<Path> scanRoots = new ArrayList<>();
        for (final File root : roots) {
            if (root == null) continue;
            final Path configured = root.toPath().toAbsolutePath().normalize();
            final Path candidate;
            try {
                /* Resolve an explicitly configured symlink root, but walkFileTree()
                 * still does not follow any symlink found below that root. */
                candidate = configured.toRealPath();
            } catch (final NoSuchFileException e) {
                /* A not-yet-created configured directory simply has nothing to recover. */
                continue;
            } catch (final IOException | SecurityException e) {
                report.failures++;
                continue;
            }
            if (!Files.isDirectory(candidate)) continue;
            boolean covered = false;
            for (final Path existing : new ArrayList<>(scanRoots)) {
                if (candidate.startsWith(existing)) {
                    covered = true;
                    break;
                }
                if (existing.startsWith(candidate)) scanRoots.remove(existing);
            }
            if (!covered) scanRoots.add(candidate);
        }

        for (final Path root : scanRoots) {
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file,
                            final BasicFileAttributes attributes) {
                        if (attributes.isRegularFile()
                                && isRecoverableArtifact(file.getFileName().toString())) {
                            report.candidates++;
                            recoverArtifact(file, report);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(final Path file,
                            final IOException failure) {
                        report.failures++;
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (final IOException | SecurityException e) {
                report.failures++;
            }
        }
        return report;
    }

    private static boolean isRecoverableArtifact(final String name) {
        return WORKING_ARTIFACT.matcher(name).matches()
                || BUILD_ARTIFACT.matcher(name).matches()
                || BUILD_LOCK_ARTIFACT.matcher(name).matches()
                || LEGACY_BUILD_ARTIFACT.matcher(name).matches();
    }

    private static void recoverArtifact(
            final Path file, final RecoveryReport report) {
        final Matcher buildArtifact = BUILD_ARTIFACT.matcher(
                file.getFileName().toString());
        final Path sessionLock = buildArtifact.matches()
                ? file.resolveSibling(buildArtifact.group(1) + BUILD_LOCK_SUFFIX)
                : null;
        /* A finished Builder result outlives its session marker and owns a lock on
         * the table itself. Intermediate files use the shared session marker. */
        final Path lockFile = sessionLock != null
                && Files.isRegularFile(sessionLock, LinkOption.NOFOLLOW_LINKS)
                ? sessionLock : file;

        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            FileLock lock = null;
            try {
                lock = channel.tryLock();
                if (lock == null) {
                    report.inUse++;
                    return;
                }
            } catch (final OverlappingFileLockException e) {
                report.inUse++;
                return;
            } finally {
                if (lock != null) lock.release();
            }
        } catch (final IOException | SecurityException e) {
            report.failures++;
            return;
        }

        try {
            if (Files.deleteIfExists(file)) report.deleted++;
        } catch (final IOException | SecurityException e) {
            report.failures++;
        }
    }

    /**
     * Detach this table from its published fingerprint before the heap generation
     * is changed or the fingerprint is deleted.
     *
     * <p>This method does not change a key or offset. It only establishes a private
     * writable backing generation. HeapModifier calls it before invalidating the
     * fingerprint, while the mutating map methods also call it defensively so direct
     * users cannot accidentally write into a read-only mapping.</p>
     *
     * @throws IOException when the private generation cannot be created completely
     */
    public void prepareForMutation() throws IOException {
        this.backingLock.writeLock().lock();
        try {
            requireOpen();
            promoteToWorkingCopy();
        } finally {
            this.backingLock.writeLock().unlock();
        }
    }

    private void promoteToWorkingCopy() throws IOException {
        if (this.writable) return;
        if (this.promotionDirectory == null || this.promotionPrefix == null) {
            throw new IOException("Read-only SSTable has no working-file configuration: "
                    + this.tableFile);
        }

        Path workingFile = null;
        FileChannel workingChannel = null;
        FileLock workingLock = null;
        ChunkedBytes workingTable = null;
        try {
            workingFile = Files.createTempFile(
                    this.promotionDirectory, this.promotionPrefix, ".sstwork");

            /*
             * Copy through the mapping, not through the fingerprint path. The heap
             * may already have invalidated that path in an exceptional recovery flow;
             * the mapped generation remains the authoritative index until this switch.
             */
            copyTable(this.table, workingFile);
            workingTable = mapFile(workingFile.toFile(), true);
            /* Take ownership only after every other descriptor used for copying
             * and mapping has been closed; see openOwnedWorkingFile(). */
            workingChannel = FileChannel.open(workingFile,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
            workingLock = workingChannel.tryLock();
            if (workingLock == null) {
                throw new IOException("Cannot lock SSTable working file " + workingFile);
            }

            final ChunkedBytes publishedTable = this.table;
            this.table = workingTable;
            this.tableFile = workingFile.toFile().getAbsoluteFile();
            this.writable = true;
            this.deleteOnClose = true;
            this.ownershipChannel = workingChannel;
            this.ownershipLock = workingLock;

            /* Ownership moved to this instance before the published mapping closes. */
            workingTable = null;
            workingFile = null;
            workingChannel = null;
            workingLock = null;
            publishedTable.close();
        } finally {
            if (workingTable != null) workingTable.close();
            if (workingLock != null) {
                try {
                    workingLock.release();
                } catch (final IOException ignored) {
                }
            }
            if (workingChannel != null) {
                try {
                    workingChannel.close();
                } catch (final IOException ignored) {
                }
            }
            if (workingFile != null) Files.deleteIfExists(workingFile);
        }
    }

    private static void copyTable(final ChunkedBytes source, final Path target)
            throws IOException {
        try (InputStream input = source.openStream();
                OutputStream output = new BufferedOutputStream(Files.newOutputStream(target,
                        StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING),
                        COPY_BUFFER_SIZE)) {
            final byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
        }
    }

    /**
     * Builds a sorted table from an unsorted stream while keeping Java heap use bounded.
     * Records are sorted in fixed-size memory runs and then merged with a bounded fan-in.
     * The returned table owns its temporary backing file and removes it on close.
     */
    public static final class Builder implements AutoCloseable {

        private final int keyLength;
        private final int valueLength;
        private final int recordLength;
        private final ByteOrder ordering;
        private final Path directory;
        private final String temporaryPrefix;
        private final int maxBufferedRecords;
        private final ArrayList<byte[]> buffer;
        private final ArrayList<Path> runs;
        private final ArrayList<Path> ownedFiles;
        private final Path sessionLockFile;
        private final FileChannel sessionLockChannel;
        private final FileLock sessionLock;

        private boolean finished;
        private boolean closed;

        public Builder(final int keyLength, final ByteOrder ordering,
                final int valueLength, final File directory, final String name) throws IOException {
            this(keyLength, ordering, valueLength, directory, name, DEFAULT_RUN_MEMORY_BYTES);
        }

        /**
         * @param maxRunMemoryBytes approximate upper bound for record payload and array overhead
         */
        public Builder(final int keyLength, final ByteOrder ordering,
                final int valueLength, final File directory, final String name,
                final int maxRunMemoryBytes) throws IOException {
            if (keyLength <= 0) throw new IllegalArgumentException("keyLength must be positive");
            if (valueLength != Long.BYTES) {
                throw new IllegalArgumentException("SSTable offsets require valueLength 8");
            }
            if (ordering == null) throw new NullPointerException("ordering");
            if (directory == null) throw new NullPointerException("directory");
            if (maxRunMemoryBytes <= 0) {
                throw new IllegalArgumentException("maxRunMemoryBytes must be positive");
            }
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Cannot create SSTable build directory: " + directory);
            }
            if (!directory.isDirectory()) {
                throw new IOException("SSTable build path is not a directory: " + directory);
            }

            this.keyLength = keyLength;
            this.valueLength = valueLength;
            this.recordLength = Math.addExact(keyLength, valueLength);
            this.ordering = ordering;
            this.directory = directory.toPath();
            final long estimatedRecordBytes = this.recordLength + ESTIMATED_ARRAY_OVERHEAD;
            this.maxBufferedRecords = (int) Math.max(1L, Math.min(Integer.MAX_VALUE,
                    maxRunMemoryBytes / estimatedRecordBytes));
            this.buffer = new ArrayList<>(this.maxBufferedRecords);
            this.runs = new ArrayList<>();
            this.ownedFiles = new ArrayList<>();
            this.finished = false;
            this.closed = false;

            final String sessionName = "sst-build-" + Integer.toHexString(
                    name == null ? 0 : name.hashCode()) + "-"
                    + UUID.randomUUID().toString().replace("-", "");
            this.temporaryPrefix = sessionName + "-";
            this.sessionLockFile = this.directory.resolve(sessionName + BUILD_LOCK_SUFFIX);
            FileChannel lockChannel = null;
            FileLock lock = null;
            try {
                lockChannel = FileChannel.open(this.sessionLockFile,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.READ, StandardOpenOption.WRITE);
                lock = lockChannel.tryLock();
                if (lock == null) {
                    throw new IOException("Cannot lock SSTable build session "
                            + this.sessionLockFile);
                }
            } catch (final IOException | RuntimeException failure) {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (final IOException closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
                if (lockChannel != null) {
                    try {
                        lockChannel.close();
                    } catch (final IOException closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
                try {
                    Files.deleteIfExists(this.sessionLockFile);
                } catch (final IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
            this.sessionLockChannel = lockChannel;
            this.sessionLock = lock;
        }

        public void consume(final byte[] key, final long offset) throws IOException {
            requireBuilding();
            if (key == null) throw new NullPointerException("key");
            if (offset < 0) throw new IllegalArgumentException("offset must not be negative");

            final byte[] record = new byte[this.recordLength];
            System.arraycopy(key, 0, record, 0, Math.min(key.length, this.keyLength));
            NaturalOrder.encodeLong(offset, record, this.keyLength, this.valueLength);
            this.buffer.add(record);
            if (this.buffer.size() >= this.maxBufferedRecords) flushRun();
        }

        public SSTableHandleMap finish() throws IOException {
            requireBuilding();
            flushRun();

            List<Path> current = new ArrayList<>(this.runs);
            this.runs.clear();
            while (current.size() > MAX_MERGE_FAN_IN) {
                final ArrayList<Path> next = new ArrayList<>(
                        (current.size() + MAX_MERGE_FAN_IN - 1) / MAX_MERGE_FAN_IN);
                for (int start = 0; start < current.size(); start += MAX_MERGE_FAN_IN) {
                    final int end = Math.min(current.size(), start + MAX_MERGE_FAN_IN);
                    if (end - start == 1) {
                        next.add(current.get(start));
                        continue;
                    }
                    final List<Path> group = new ArrayList<>(current.subList(start, end));
                    final Path merged = createTemporaryFile(".merge");
                    mergeRuns(group, merged);
                    for (final Path source : group) deleteOwned(source);
                    next.add(merged);
                }
                current = next;
            }

            final Path resultFile;
            if (current.isEmpty()) {
                resultFile = createTemporaryFile(".sst");
            } else if (current.size() == 1) {
                resultFile = current.get(0);
            } else {
                resultFile = createTemporaryFile(".sst");
                mergeRuns(current, resultFile);
                for (final Path source : current) deleteOwned(source);
            }

            final SSTableHandleMap result = openOwnedWorkingFile(
                    this.keyLength, this.ordering, this.valueLength,
                    resultFile.toFile());
            this.ownedFiles.remove(resultFile);
            this.finished = true;
            return result;
        }

        private void flushRun() throws IOException {
            if (this.buffer.isEmpty()) return;
            this.buffer.sort((left, right) -> this.ordering.compare(
                    left, 0, right, 0, this.keyLength));
            final Path run = createTemporaryFile(".run");
            boolean complete = false;
            try (OutputStream output = new BufferedOutputStream(
                    new FileOutputStream(run.toFile()), COPY_BUFFER_SIZE)) {
                byte[] pending = null;
                for (final byte[] record : this.buffer) {
                    if (pending == null) {
                        pending = record;
                    } else if (sameKey(pending, record)) {
                        if (readOffset(record) > readOffset(pending)) pending = record;
                    } else {
                        output.write(pending);
                        pending = record;
                    }
                }
                if (pending != null) output.write(pending);
                complete = true;
            } finally {
                this.buffer.clear();
                if (!complete) deleteOwned(run);
            }
            this.runs.add(run);
        }

        private void mergeRuns(final List<Path> sources, final Path target) throws IOException {
            final ArrayList<RunCursor> cursors = new ArrayList<>(sources.size());
            IOException failure = null;
            try (OutputStream output = new BufferedOutputStream(
                    new FileOutputStream(target.toFile()), COPY_BUFFER_SIZE)) {
                final Comparator<RunCursor> comparator = (left, right) -> {
                    final int compared = this.ordering.compare(left.record, 0,
                            right.record, 0, this.keyLength);
                    return compared == 0 ? Integer.compare(left.ordinal, right.ordinal) : compared;
                };
                final PriorityQueue<RunCursor> queue = new PriorityQueue<>(comparator);
                int ordinal = 0;
                for (final Path source : sources) {
                    final RunCursor cursor = new RunCursor(source, ordinal++);
                    cursors.add(cursor);
                    if (cursor.advance()) queue.add(cursor);
                }

                final byte[] mergedRecord = new byte[this.recordLength];
                while (!queue.isEmpty()) {
                    RunCursor cursor = queue.poll();
                    System.arraycopy(cursor.record, 0, mergedRecord, 0, this.keyLength);
                    long newestOffset = readOffset(cursor.record);
                    if (cursor.advance()) queue.add(cursor);

                    while (!queue.isEmpty() && sameKey(mergedRecord, queue.peek().record)) {
                        cursor = queue.poll();
                        newestOffset = Math.max(newestOffset, readOffset(cursor.record));
                        if (cursor.advance()) queue.add(cursor);
                    }
                    NaturalOrder.encodeLong(newestOffset, mergedRecord,
                            this.keyLength, this.valueLength);
                    output.write(mergedRecord);
                }
            } catch (final IOException e) {
                failure = e;
                throw e;
            } finally {
                IOException closeFailure = null;
                for (final RunCursor cursor : cursors) {
                    try {
                        cursor.close();
                    } catch (final IOException e) {
                        if (failure != null) failure.addSuppressed(e);
                        else if (closeFailure == null) closeFailure = e;
                        else closeFailure.addSuppressed(e);
                    }
                }
                if (failure == null && closeFailure != null) throw closeFailure;
            }
        }

        private boolean sameKey(final byte[] left, final byte[] right) {
            return this.ordering.equal(left, 0, right, 0, this.keyLength);
        }

        private long readOffset(final byte[] record) {
            return NaturalOrder.decodeLong(record, this.keyLength, this.valueLength);
        }

        private Path createTemporaryFile(final String suffix) throws IOException {
            final Path file = Files.createTempFile(
                    this.directory, this.temporaryPrefix, suffix);
            this.ownedFiles.add(file);
            return file;
        }

        private void deleteOwned(final Path file) throws IOException {
            Files.deleteIfExists(file);
            this.ownedFiles.remove(file);
        }

        private void requireBuilding() {
            if (this.closed) throw new IllegalStateException("SSTable builder is closed");
            if (this.finished) throw new IllegalStateException("SSTable builder is already finished");
        }

        @Override
        public void close() throws IOException {
            if (this.closed) return;
            this.closed = true;
            this.buffer.clear();
            IOException failure = null;
            for (final Path file : new ArrayList<>(this.ownedFiles)) {
                try {
                    deleteOwned(file);
                } catch (final IOException e) {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }
            if (this.sessionLock.isValid()) {
                try {
                    this.sessionLock.release();
                } catch (final IOException e) {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }
            try {
                this.sessionLockChannel.close();
            } catch (final IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
            try {
                Files.deleteIfExists(this.sessionLockFile);
            } catch (final IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
            if (failure != null) throw failure;
        }

        private final class RunCursor implements AutoCloseable {
            private final InputStream input;
            private final int ordinal;
            private final byte[] record;

            private RunCursor(final Path run, final int ordinal) throws IOException {
                this.input = new BufferedInputStream(
                        Files.newInputStream(run), COPY_BUFFER_SIZE);
                this.ordinal = ordinal;
                this.record = new byte[Builder.this.recordLength];
            }

            private boolean advance() throws IOException {
                final int first = this.input.read();
                if (first < 0) return false;
                this.record[0] = (byte) first;
                int offset = 1;
                while (offset < this.record.length) {
                    final int read = this.input.read(
                            this.record, offset, this.record.length - offset);
                    if (read < 0) {
                        throw new EOFException("Truncated SSTable run record");
                    }
                    if (read == 0) continue;
                    offset += read;
                }
                return true;
            }

            @Override
            public void close() throws IOException {
                this.input.close();
            }
        }
    }

    @Override
    public long mem() {
        /* File-backed pages belong to the OS page cache, not the Java heap. */
        return 0L;
    }

    @Override
    public void optimize() {
        /* Immutable sorted storage has no heap capacity to trim. */
    }

    @Override
    public int dump(final File file) throws IOException {
        requireOpen();
        if (file == null) throw new NullPointerException("file");
        if (this.tableFile.equals(file.getAbsoluteFile())) {
            if (this.liveCount.get() == this.recordCount) return this.recordCount;
            throw new IOException("Cannot compact an open SSTable in place: " + file);
        }

        final File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create SSTable dump directory: " + parent);
        }
        final File temporary = new File(parent, file.getName() + ".prt");
        int written = 0;
        final byte[] record = new byte[this.recordLength];
        try (FileOutputStream fileStream = new FileOutputStream(temporary);
                OutputStream buffered = new BufferedOutputStream(fileStream, COPY_BUFFER_SIZE);
                OutputStream output = file.getName().endsWith(".gz")
                        ? new GZIPOutputStream(buffered, COPY_BUFFER_SIZE) {{
                            this.def.setLevel(Deflater.BEST_COMPRESSION);
                        }}
                        : buffered) {
            for (int ordinal = 0; ordinal < this.recordCount; ordinal++) {
                if (isDeleted(ordinal)) continue;
                readRecord(ordinal, record);
                output.write(record);
                written++;
            }
        } catch (final IOException | RuntimeException e) {
            Files.deleteIfExists(temporary.toPath());
            if (e instanceof IOException) throw (IOException) e;
            throw e;
        }

        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException e) {
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException moveFailure) {
                try {
                    Files.deleteIfExists(temporary.toPath());
                } catch (final IOException cleanupFailure) {
                    moveFailure.addSuppressed(cleanupFailure);
                }
                throw moveFailure;
            }
        } catch (final IOException moveFailure) {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (final IOException cleanupFailure) {
                moveFailure.addSuppressed(cleanupFailure);
            }
            throw moveFailure;
        }
        return written;
    }

    @Override
    public synchronized void clear() {
        this.backingLock.writeLock().lock();
        try {
            requireOpen();
            if (this.liveCount.get() == 0) return;
            promoteToWorkingCopy();
            for (int ordinal = 0; ordinal < this.recordCount; ordinal++) {
                if (!isDeleted(ordinal)) writeDeletedOffset(ordinal);
            }
            this.liveCount.set(0);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            this.backingLock.writeLock().unlock();
        }
    }

    @Override
    public byte[] smallestKey() {
        requireOpen();
        for (int ordinal = 0; ordinal < this.recordCount; ordinal++) {
            if (!isDeleted(ordinal)) return readKey(ordinal);
        }
        return null;
    }

    @Override
    public byte[] largestKey() {
        requireOpen();
        for (int ordinal = this.recordCount - 1; ordinal >= 0; ordinal--) {
            if (!isDeleted(ordinal)) return readKey(ordinal);
        }
        return null;
    }

    @Override
    public boolean has(final byte[] key) {
        return get(key) >= 0;
    }

    @Override
    public long get(final byte[] key) {
        requireOpen();
        final int ordinal = find(key);
        return ordinal < 0 || isDeleted(ordinal) ? -1 : readValue(ordinal);
    }

    @Override
    public ArrayList<long[]> removeDoubles() throws SpaceExceededException {
        /* An SSTable is required to contain unique sorted keys. */
        return new ArrayList<>();
    }

    @Override
    public ArrayList<byte[]> top(final int count) {
        requireOpen();
        final ArrayList<byte[]> result = new ArrayList<>(Math.max(0, Math.min(count, size())));
        if (count <= 0) return result;
        for (int ordinal = this.recordCount - 1;
                ordinal >= 0 && result.size() < count; ordinal--) {
            if (!isDeleted(ordinal)) result.add(readKey(ordinal));
        }
        return result;
    }

    @Override
    public synchronized long remove(final byte[] key) {
        this.backingLock.writeLock().lock();
        try {
            requireOpen();
            final int ordinal = find(key);
            if (ordinal < 0 || isDeleted(ordinal)) return -1;
            final long previous = readValue(ordinal);
            promoteToWorkingCopy();
            writeDeletedOffset(ordinal);
            this.liveCount.decrementAndGet();
            return previous;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            this.backingLock.writeLock().unlock();
        }
    }

    @Override
    public synchronized long removeone() {
        this.backingLock.writeLock().lock();
        try {
            requireOpen();
            for (int ordinal = this.recordCount - 1; ordinal >= 0; ordinal--) {
                if (isDeleted(ordinal)) continue;
                final long previous = readValue(ordinal);
                promoteToWorkingCopy();
                writeDeletedOffset(ordinal);
                this.liveCount.decrementAndGet();
                return previous;
            }
            return -1;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            this.backingLock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        requireOpen();
        return this.liveCount.get();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public CloneableIterator<byte[]> keys(final boolean up, final byte[] firstKey) {
        requireOpen();
        return new KeyIterator(up, firstKey);
    }

    @Override
    public Iterator<Map.Entry<byte[], Long>> iterator() {
        requireOpen();
        return new EntryIterator();
    }

    @Override
    public synchronized void close() {
        this.backingLock.writeLock().lock();
        try {
            if (this.closed) return;
            this.closed = true;
            this.table.close();

            IOException failure = null;
            if (this.writable && this.ownershipChannel != null) {
                try {
                    this.ownershipChannel.force(true);
                } catch (final IOException e) {
                    failure = e;
                }
            } else if (this.writable) {
                try (FileChannel channel = FileChannel.open(
                        this.tableFile.toPath(), StandardOpenOption.WRITE)) {
                    channel.force(true);
                } catch (final IOException e) {
                    failure = e;
                }
            }

            /* Release ownership only after all mapped writes have been flushed. */
            if (this.ownershipLock != null && this.ownershipLock.isValid()) {
                try {
                    this.ownershipLock.release();
                } catch (final IOException e) {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }
            if (this.ownershipChannel != null) {
                try {
                    this.ownershipChannel.close();
                } catch (final IOException e) {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }

            if (this.deleteOnClose) {
                try {
                    Files.deleteIfExists(this.tableFile.toPath());
                } catch (final IOException e) {
                    this.tableFile.deleteOnExit();
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw new UncheckedIOException(
                        "Cannot close SSTable " + this.tableFile, failure);
            }
        } finally {
            this.backingLock.writeLock().unlock();
        }
    }

    private int find(final byte[] sourceKey) {
        final byte[] key = normalizeKey(sourceKey);
        final byte[] pivotKey = new byte[this.keyLength];
        int low = 0;
        int high = this.recordCount;
        while (low < high) {
            final int pivot = (low + high) >>> 1;
            readKey(pivot, pivotKey);
            final int comparison = this.ordering.compare(pivotKey, 0, key, 0, this.keyLength);
            if (comparison < 0) low = pivot + 1;
            else high = pivot;
        }
        if (low >= this.recordCount) return -1;
        readKey(low, pivotKey);
        return this.ordering.equal(pivotKey, 0, key, 0, this.keyLength) ? low : -1;
    }

    private int lowerBound(final byte[] sourceKey) {
        if (sourceKey == null || sourceKey.length == 0) return 0;
        final byte[] key = normalizeKey(sourceKey);
        final byte[] pivotKey = new byte[this.keyLength];
        int low = 0;
        int high = this.recordCount;
        while (low < high) {
            final int pivot = (low + high) >>> 1;
            readKey(pivot, pivotKey);
            if (this.ordering.compare(pivotKey, 0, key, 0, this.keyLength) < 0) low = pivot + 1;
            else high = pivot;
        }
        return low;
    }

    private byte[] normalizeKey(final byte[] key) {
        if (key == null) throw new NullPointerException("key");
        if (key.length == this.keyLength) return key;
        final byte[] normalized = new byte[this.keyLength];
        System.arraycopy(key, 0, normalized, 0, Math.min(key.length, this.keyLength));
        return normalized;
    }

    private byte[] readKey(final int ordinal) {
        final byte[] key = new byte[this.keyLength];
        readKey(ordinal, key);
        return key;
    }

    private void readKey(final int ordinal, final byte[] key) {
        readTable(recordOffset(ordinal), key, 0, this.keyLength);
    }

    private long readValue(final int ordinal) {
        final byte[] value = new byte[this.valueLength];
        readTable(recordOffset(ordinal) + this.keyLength,
                value, 0, this.valueLength);
        return NaturalOrder.decodeLong(value, 0, value.length);
    }

    private void readRecord(final int ordinal, final byte[] record) {
        readTable(recordOffset(ordinal), record, 0, this.recordLength);
    }

    private long recordOffset(final int ordinal) {
        if (ordinal < 0 || ordinal >= this.recordCount) {
            throw new IndexOutOfBoundsException("record ordinal " + ordinal);
        }
        return (long) ordinal * this.recordLength;
    }

    private boolean isDeleted(final int ordinal) {
        /* With an eight-byte big-endian value, the first bit is the sign bit. */
        this.backingLock.readLock().lock();
        try {
            return this.table.get(recordOffset(ordinal) + this.keyLength) < 0;
        } finally {
            this.backingLock.readLock().unlock();
        }
    }

    private void writeDeletedOffset(final int ordinal) throws IOException {
        final long offset = recordOffset(ordinal) + this.keyLength;
        if (this.table.write(offset, DELETED_OFFSET, 0, DELETED_OFFSET.length)
                != DELETED_OFFSET.length) {
            throw new IOException("Cannot persist deleted SSTable offset at record " + ordinal);
        }
    }

    private void requireOpen() {
        if (this.closed) throw new IllegalStateException("SSTableHandleMap is closed: " + this.tableFile);
    }

    private void readTable(final long position, final byte[] target,
            final int offset, final int length) {
        this.backingLock.readLock().lock();
        try {
            readFully(this.table, position, target, offset, length);
        } finally {
            this.backingLock.readLock().unlock();
        }
    }

    private static ChunkedBytes mapFile(final File file, final boolean writable) throws IOException {
        final ChunkedBytes bytes = new ChunkedBytes();
        try {
            bytes.appendFile(file.toPath(), writable);
            return bytes;
        } catch (final RuntimeException e) {
            bytes.close();
            if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
            throw new IOException("Cannot map SSTable file " + file, e);
        }
    }

    private static void readFully(final ChunkedBytes bytes, final long position,
            final byte[] target, final int offset, final int length) {
        final int read = bytes.read(position, target, offset, length);
        if (read != length) {
            throw new IllegalStateException("Short SSTable read at " + position
                    + ": expected " + length + ", got " + read);
        }
    }

    private final class KeyIterator implements CloneableIterator<byte[]> {

        private final boolean up;
        private int nextOrdinal;
        private int preparedOrdinal;
        private byte[] lastKey;

        private KeyIterator(final boolean up, final byte[] firstKey) {
            this.up = up;
            this.nextOrdinal = up ? lowerBound(firstKey) : SSTableHandleMap.this.recordCount - 1;
            this.preparedOrdinal = -1;
            this.lastKey = null;
        }

        @Override
        public boolean hasNext() {
            requireOpen();
            if (this.preparedOrdinal >= 0) return true;
            int ordinal = this.nextOrdinal;
            while (ordinal >= 0 && ordinal < SSTableHandleMap.this.recordCount
                    && isDeleted(ordinal)) ordinal += this.up ? 1 : -1;
            if (ordinal < 0 || ordinal >= SSTableHandleMap.this.recordCount) return false;
            this.preparedOrdinal = ordinal;
            return true;
        }

        @Override
        public byte[] next() {
            if (!hasNext()) throw new NoSuchElementException();
            final int ordinal = this.preparedOrdinal;
            this.preparedOrdinal = -1;
            this.nextOrdinal = ordinal + (this.up ? 1 : -1);
            this.lastKey = readKey(ordinal);
            return this.lastKey;
        }

        @Override
        public void remove() {
            if (this.lastKey == null) throw new IllegalStateException();
            SSTableHandleMap.this.remove(this.lastKey);
            this.lastKey = null;
        }

        @Override
        public CloneableIterator<byte[]> clone(final Object modifier) {
            return new KeyIterator(this.up, (byte[]) modifier);
        }

        @Override
        public void close() {
        }
    }

    private final class EntryIterator implements Iterator<Map.Entry<byte[], Long>> {

        private int nextOrdinal = 0;
        private int preparedOrdinal = -1;
        private byte[] lastKey;

        @Override
        public boolean hasNext() {
            requireOpen();
            if (this.preparedOrdinal >= 0) return true;
            int ordinal = this.nextOrdinal;
            while (ordinal < SSTableHandleMap.this.recordCount && isDeleted(ordinal)) ordinal++;
            if (ordinal >= SSTableHandleMap.this.recordCount) return false;
            this.preparedOrdinal = ordinal;
            return true;
        }

        @Override
        public Map.Entry<byte[], Long> next() {
            if (!hasNext()) throw new NoSuchElementException();
            final int ordinal = this.preparedOrdinal;
            this.preparedOrdinal = -1;
            this.nextOrdinal = ordinal + 1;
            this.lastKey = readKey(ordinal);
            return new AbstractMap.SimpleImmutableEntry<>(this.lastKey, readValue(ordinal));
        }

        @Override
        public void remove() {
            if (this.lastKey == null) throw new IllegalStateException();
            SSTableHandleMap.this.remove(this.lastKey);
            this.lastKey = null;
        }
    }
}
