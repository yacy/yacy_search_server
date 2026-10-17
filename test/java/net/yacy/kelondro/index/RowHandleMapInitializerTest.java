/*
 *  RowHandleMapInitializerTest
 *  Copyright 2026 by YaCy contributors
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 */

package net.yacy.kelondro.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import net.yacy.cora.order.NaturalOrder;

public class RowHandleMapInitializerTest {

    @Test
    public void boundedQueueAppliesBackpressureWithoutDroppingEntries() throws Exception {
        final RowHandleMap map = new RowHandleMap(4, NaturalOrder.naturalOrder, 8, 4, "initializer-test");
        final RowHandleMap.initDataConsumer initializer = new RowHandleMap.initDataConsumer(map, 1);
        final byte[] firstKey = key(1);
        final byte[] secondKey = key(2);
        initializer.consume(firstKey, 11L);

        final CountDownLatch producerStarted = new CountDownLatch(1);
        final CountDownLatch producerCompleted = new CountDownLatch(1);
        final AtomicReference<Throwable> producerFailure = new AtomicReference<>();
        final Thread producer = new Thread(() -> {
            producerStarted.countDown();
            try {
                initializer.consume(secondKey, 22L);
            } catch (final Throwable failure) {
                producerFailure.set(failure);
            } finally {
                producerCompleted.countDown();
            }
        }, "RowHandleMapInitializerTest.producer");
        producer.start();
        assertTrue("producer did not start", producerStarted.await(2, TimeUnit.SECONDS));
        final boolean producerBlockedByFullQueue = waitUntilBlocked(producer, 2, TimeUnit.SECONDS);

        final AtomicReference<RowHandleMap> initializedMap = new AtomicReference<>();
        final AtomicReference<Throwable> consumerFailure = new AtomicReference<>();
        final Thread consumer = new Thread(() -> {
            try {
                initializedMap.set(initializer.call());
            } catch (final Throwable failure) {
                consumerFailure.set(failure);
            }
        }, "RowHandleMapInitializerTest.consumer");
        consumer.start();

        final boolean producerCompletedAfterConsumerStarted = producerCompleted.await(2, TimeUnit.SECONDS);
        initializer.finish();
        producer.join(2000);
        consumer.join(2000);

        try {
            assertTrue("a full bounded queue must block its producer", producerBlockedByFullQueue);
            assertTrue("consumer should make room for the blocked producer", producerCompletedAfterConsumerStarted);
            assertFalse("producer should finish", producer.isAlive());
            assertFalse("consumer should finish after the poison entry", consumer.isAlive());
            assertNull("producer failed", producerFailure.get());
            assertNull("consumer failed", consumerFailure.get());
            assertNotNull("initializer returned no map", initializedMap.get());
            assertEquals(2, initializedMap.get().size());
            assertEquals(11L, initializedMap.get().get(firstKey));
            assertEquals(22L, initializedMap.get().get(secondKey));
        } finally {
            map.close();
        }
    }

    private static boolean waitUntilBlocked(final Thread thread, final long timeout, final TimeUnit unit)
            throws InterruptedException {
        final long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING) return true;
            if (!thread.isAlive()) return false;
            Thread.sleep(1L);
        }
        return thread.getState() == Thread.State.WAITING;
    }

    private static byte[] key(final int value) {
        return new byte[] {0, 0, 0, (byte) value};
    }
}
