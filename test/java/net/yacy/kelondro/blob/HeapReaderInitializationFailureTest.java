/*
 *  HeapReaderInitializationFailureTest
 *  Copyright 2026 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 */

package net.yacy.kelondro.blob;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.order.ByteOrder;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.storage.HandleMap;
import net.yacy.kelondro.io.CachedFileWriter;
import net.yacy.kelondro.io.Writer;

public class HeapReaderInitializationFailureTest {

    private static final int KEY_LENGTH = 4;
    private static final byte[] KEY = ASCII.getBytes("0001");
    private static final byte[] VALUE = ASCII.getBytes("payload");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rebuildFailureClosesBuilderAndHeapFile() throws Exception {
        final File directory = this.temporaryFolder.newFolder("failed-rebuild");
        final File heapFile = new File(directory, "data.blob");
        writeHeapRecord(heapFile);

        final AtomicBoolean fileClosed = new AtomicBoolean();
        final AtomicBoolean builderClosed = new AtomicBoolean();
        final HeapReader.HeapFileFactory files = file -> {
            final Writer delegate = new CachedFileWriter(file);
            return (Writer) Proxy.newProxyInstance(
                    Writer.class.getClassLoader(),
                    new Class<?>[] {Writer.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("close")) fileClosed.set(true);
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (final InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        };

        final HeapReader.HeapIndexFactory<HandleMap> indexes =
                new HeapReader.HeapIndexFactory<HandleMap>() {
                    @Override
                    public HandleMap load(final int keylength, final ByteOrder ordering,
                            final File dump) {
                        throw new AssertionError("No fingerprint should be loaded");
                    }

                    @Override
                    public HeapReader.HeapIndexBuilder<HandleMap> newBuilder(
                            final File file, final int keylength,
                            final ByteOrder ordering, final int expectedspace) {
                        return new HeapReader.HeapIndexBuilder<HandleMap>() {
                            @Override
                            public void consume(final byte[] key, final long offset)
                                    throws IOException {
                                throw new IOException("forced index rebuild failure");
                            }

                            @Override
                            public HandleMap finish() throws IOException {
                                throw new IOException("finish must not be reached");
                            }

                            @Override
                            public void close() {
                                builderClosed.set(true);
                            }
                        };
                    }

                    @Override
                    public void prepareForHeapMutation(final HandleMap index) {
                    }
                };

        try {
            new TestHeapReader(heapFile, indexes, files);
            throw new AssertionError("index rebuild failure was swallowed");
        } catch (final IOException expected) {
            assertEquals("forced index rebuild failure", expected.getMessage());
        }
        assertTrue("The failed builder must be closed", builderClosed.get());
        assertTrue("The heap file must be closed", fileClosed.get());

        /* Cleanup must leave the authoritative heap usable for a normal retry. */
        final Heap recovered = new Heap(
                heapFile, KEY_LENGTH, NaturalOrder.naturalOrder, 0);
        try {
            assertArrayEquals(VALUE, recovered.get(KEY));
        } finally {
            recovered.close(false);
        }
    }

    private static void writeHeapRecord(final File heapFile) throws IOException {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(heapFile.toPath())))) {
            output.writeInt(KEY_LENGTH + VALUE.length);
            output.write(KEY);
            output.write(VALUE);
        }
    }

    private static final class TestHeapReader extends HeapReader<HandleMap> {
        private TestHeapReader(final File heapFile,
                final HeapIndexFactory<HandleMap> indexFactory,
                final HeapFileFactory heapFileFactory) throws IOException {
            super(heapFile, KEY_LENGTH, NaturalOrder.naturalOrder,
                    indexFactory, heapFileFactory);
        }
    }
}
