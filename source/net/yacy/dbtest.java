package net.yacy;
// test application
// shall be used to compare the kelondroDB with other databases
// with relevance to yacy-specific use cases

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;

import javax.imageio.ImageIO;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.Base64Order;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.order.NaturalOrder;
import net.yacy.cora.util.ByteBuffer;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.SpaceExceededException;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowSet;
import net.yacy.kelondro.table.SQLTable;
import net.yacy.kelondro.table.SplitTable;
import net.yacy.kelondro.table.Table;
import net.yacy.kelondro.util.MemoryControl;
import net.yacy.visualization.ChartPlotter;


public class dbtest {

    public final static int keylength = 12;
    public final static int valuelength = 223; // sum of all data length as defined in plasmaURL
    private static final byte[] dummyvalue2 = new byte[valuelength];
    static {
        // fill the dummy value
        for (int i = 0; i < valuelength; i++) dummyvalue2[i] = '-';
        dummyvalue2[0] = '{';
        dummyvalue2[valuelength - 1] = '}';
    }

    public static byte[] randomHash(final long r0, final long r1) {
        // a long can have 64 bit, but a 12-byte hash can have 6 * 12 = 72 bits
        // so we construct a generic Hash using two long values
        final String s = (Base64Order.enhancedCoder.encodeLongSB(Math.abs(r0), 6).toString() +
                    Base64Order.enhancedCoder.encodeLongSB(Math.abs(r1), 6).toString());
        return s.getBytes();
    }

    public static byte[] randomHash(final Random r) {
        return randomHash(r.nextLong(), r.nextLong());
    }

    public final static class STEntry {
        private final byte[] key;
        private final byte[] value;

        public STEntry(final long aSource) {
            this.key = randomHash(aSource, aSource);
            this.value = new byte[valuelength];
            for (int i = 0; i < valuelength; i++) this.value[i] = 0;
            final byte[] tempKey = String.valueOf(aSource).getBytes();
            System.arraycopy(tempKey, 0, this.value, 0, tempKey.length);
        }

        public STEntry(final byte[] aKey, final byte[] aValue) {
            this.key = aKey;
            this.value = aValue;
        }

        public boolean isValid() {
            final String s = UTF8.String(this.value).trim();
            if (s.isEmpty()) return false;
            final long source = Long.parseLong(s);
            return ByteBuffer.equals(this.key, randomHash(source, source));
        }

        public byte[] getKey() {
            return this.key;
        }

        public byte[] getValue() {
            return this.value;
        }

        @Override
        public String toString() {
            return UTF8.String(this.key) + "#" + UTF8.String(this.value);
        }

    }

    public static abstract class STJob implements Runnable {
        private final Index table_test, table_reference;

        private final long source;

        public STJob(final Index table_test, final Index table_reference, final long aSource) {
            this.table_test = table_test;
            this.table_reference = table_reference;
            this.source = aSource;
        }

        public Index getTable_test() {
            return this.table_test;
        }

        public Index getTable_reference() {
            return this.table_reference;
        }

        @Override
        public abstract void run();

        public long getSource() {
            return this.source;
        }
    }

    public static final class WriteJob extends STJob {
        public WriteJob(final Index table_test, final Index table_reference, final long aSource) {
            super(table_test, table_reference, aSource);
        }

        @Override
        public void run() {
            final STEntry entry = new STEntry(getSource());
            System.out.println("write:  " + NaturalOrder.arrayList(entry.getKey(), 0, entry.getKey().length));
            try {
                getTable_test().put(getTable_test().row().newEntry(new byte[][] { entry.getKey(), entry.getValue() , entry.getValue() }));
                if (getTable_reference() != null) getTable_reference().put(getTable_test().row().newEntry(new byte[][] { entry.getKey(), entry.getValue() , entry.getValue() }));
            } catch (final IOException e) {
                System.err.println(e);
                ConcurrentLog.logException(e);
                System.exit(0);
            } catch (final SpaceExceededException e) {
                System.err.println(e);
                ConcurrentLog.logException(e);
                System.exit(0);
            }
        }
    }

    public static final class RemoveJob extends STJob {
        public RemoveJob(final Index table_test, final Index table_reference, final long aSource) {
            super(table_test, table_reference, aSource);
        }

        @Override
        public void run() {
            final STEntry entry = new STEntry(getSource());
            System.out.println("remove: " + NaturalOrder.arrayList(entry.getKey(), 0, entry.getKey().length));
            try {
                getTable_test().remove(entry.getKey());
                if (getTable_reference() != null) getTable_reference().delete(entry.getKey());
            } catch (final IOException e) {
                System.err.println(e);
                ConcurrentLog.logException(e);
                System.exit(0);
            }
        }
    }

    public static final class ReadJob extends STJob {
        public ReadJob(final Index table_test, final Index table_reference, final long aSource) {
            super(table_test, table_reference, aSource);
        }

        @Override
        public void run() {
            final STEntry entry = new STEntry(getSource());
            try {
                Row.Entry entryBytes = getTable_test().get(entry.getKey(), false);
                if (entryBytes != null) {
                    final STEntry dbEntry = new STEntry(entryBytes.getPrimaryKeyBytes(), entryBytes.getColBytes(1, true));
                    if (!dbEntry.isValid()) {
                        System.out.println("INVALID table_test: " + dbEntry);
                    } /* else {
                        System.out.println("_VALID_ table_test: " + dbEntry);
                        getTable().remove(entry.getKey(), true);
                    } */
                }
                if (getTable_reference() != null) {
                entryBytes = getTable_reference().get(entry.getKey(), false);
                if (entryBytes != null) {
                    final STEntry dbEntry = new STEntry(entryBytes.getPrimaryKeyBytes(), entryBytes.getColBytes(1, true));
                    if (!dbEntry.isValid()) {
                        System.out.println("INVALID table_reference: " + dbEntry);
                    } /* else {
                        System.out.println("_VALID_ table_reference: " + dbEntry);
                        getTable().remove(entry.getKey(), true);
                    } */
                }
                }
            } catch (final IOException e) {
                System.err.println(e);
                ConcurrentLog.logException(e);
                System.exit(0);
            }
        }
    }

    public static Index selectTableType(final String dbe, final String tablename, final Row testRow) throws Exception {
        if (dbe.equals("kelondroRowSet")) {
            return new RowSet(testRow, 0);
        }
        if (dbe.equals("kelondroSplitTable")) {
            final File tablepath = new File(tablename).getParentFile();
            return new SplitTable(tablepath, new File(tablename).getName(), testRow, true, true);
        }
        if (dbe.equals("kelondroEcoTable")) {
            return new Table(new File(tablename), testRow, 1000, 0, true, true, true);
        }
        if (dbe.equals("mysql")) {
            return new SQLTable("mysql", testRow);
        }
        if (dbe.equals("pgsql")) {
            return new SQLTable("pgsql", testRow);
        }
        return null;
    }

    public static boolean checkEquivalence(final Index test, final Index reference) throws IOException {
        if (reference == null) return true;
        if (test.size() == reference.size()) {
            System.out.println("* Testing equivalence of test table to reference table, " + test.size() + " entries");
        } else  {
            System.out.println("* Testing equivalence of test table to reference table: FAILED! the tables have different sizes: test.size() = " + test.size() + ", reference.size() = " + reference.size());
            return false;
        }
        boolean eq = true;
        Row.Entry test_entry, reference_entry;

        Iterator<Row.Entry> i = test.rows();
        System.out.println("* Testing now by enumeration over test table");
        final long ts = System.currentTimeMillis();
        while (i.hasNext()) {
            test_entry = i.next();
            reference_entry = reference.get(test_entry.getPrimaryKeyBytes(), false);
            if (!test_entry.equals(reference_entry)) {
                System.out.println("* FAILED: test entry with key '" + UTF8.String(test_entry.getPrimaryKeyBytes()) + "' has no equivalent entry in reference");
                eq = false;
            }
        }

        i = reference.rows();
        System.out.println("* Testing now by enumeration over reference table");
        final long rs = System.currentTimeMillis();
        while (i.hasNext()) {
            reference_entry = i.next();
            test_entry = test.get(reference_entry.getPrimaryKeyBytes(), false);
            if (!test_entry.equals(reference_entry)) {
                System.out.println("* FAILED: reference entry with key '" + UTF8.String(test_entry.getPrimaryKeyBytes()) + "' has no equivalent entry in test");
                eq = false;
            }
        }

        System.out.println("* finished checkEquivalence; test-enumeration = " + ((rs - ts) / 1000) + " seconds, reference-enumeration = "+ ((System.currentTimeMillis() - rs) / 1000) + " seconds");
        if (eq) System.out.println("* SUCCESS! the tables are equal."); else System.out.println("* FAILED! the tables are not equal.");
        return eq;
    }

    public static void main(final String[] args) {
        System.setProperty("java.awt.headless", "true");

        System.out.println("*** YaCy Database Test");
        // print out command line
        boolean assertionenabled = false;
        assert (assertionenabled = true) == true; // compare to true to remove warning: "Possible accidental assignement"
        if (assertionenabled) System.out.println("*** Asserts are enabled"); else System.out.println("*** HINT: YOU SHOULD ENABLE ASSERTS! (include -ea in start arguments");
        final long mb = MemoryControl.available() / 1024 / 1024;
        System.out.println("*** RAM = " + mb + " MB");
        System.out.print(">java " +
                ((assertionenabled) ? "-ea " : "") +
                ((mb > 100) ? ("-Xmx" + (mb + 11) + "m ") : " ") +
                "-classpath classes dbtest ");
        for (final String arg : args)
            System.out.print(arg + " ");
        System.out.println();

        // read command options
        final String command        = args[0]; // test command
        final String dbe_test       = args[1]; // the database engine that shall be tested
        String dbe_reference  = args[2]; // the reference database engine (may be null)
        final String tablename_test = args[3]; // name of test-table
        if (dbe_reference.toLowerCase().equals("null")) dbe_reference = null;
        final String tablename_reference = (dbe_reference == null) ? null : (tablename_test + ".reference");

        // start test
        final long startup = System.currentTimeMillis();
        try {
            // create a memory profiler
            final memprofiler profiler = new memprofiler(1024, 320, 120, new File(tablename_test + ".profile.png"));
            profiler.start();

            // create the database access
            final Row testRow = new Row("byte[] key-" + keylength + ", byte[] dummy-" + keylength + ", value-" + valuelength, Base64Order.enhancedCoder);
            final Index table_test = selectTableType(dbe_test, tablename_test, testRow);
            final Index table_reference = (dbe_reference == null) ? null : selectTableType(dbe_reference, tablename_reference, testRow);

            final long afterinit = System.currentTimeMillis();
            System.out.println("Test for db-engine " + dbe_test +  " started to create file " + tablename_test + " with test " + command);

            // execute command
            if (command.equals("create")) {
                // do nothing, since opening of the database access must handle this
                System.out.println("Database created");
            }

            if (command.equals("fill")) {
                // fill database with random entries;
                // args: <number-of-entries> <random-startpoint>
                // example: java -ea -Xmx200m fill kelondroEcoTable kelondroFlexTable filldbtest 50000 0
                final long count = Long.parseLong(args[4]);
                final long randomstart = Long.parseLong(args[5]);
                final Random random = new Random(randomstart);
                byte[] key;
                for (int i = 0; i < count; i++) {
                    key = randomHash(random);
                    table_test.put(testRow.newEntry(new byte[][]{key, key, dummyvalue2}));
                    if (table_reference != null) table_reference.put(testRow.newEntry(new byte[][]{key, key, dummyvalue2}));
                    if (i % 1000 == 0) {
                        System.out.println(i + " entries. ");
                    }
                }
            }

            if (command.equals("read")) {
                // read the database and compare with random entries;
                // args: <number-of-entries> <random-startpoint>
                final long count = Long.parseLong(args[4]);
                final long randomstart = Long.parseLong(args[5]);
                final Random random = new Random(randomstart);
                Row.Entry entry;
                byte[] key;
                for (int i = 0; i < count; i++) {
                    key = randomHash(random);
                    entry = table_test.get(key, false);
                    if (entry == null)
                        System.out.println("missing value for entry " + UTF8.String(key) + " in test table");
                    else
                        if (!ByteBuffer.equals(entry.getColBytes(1, false), key)) System.out.println("wrong value for entry " + UTF8.String(key) + ": " + UTF8.String(entry.getColBytes(1, false)) + " in test table");
                    if (table_reference != null) {
                        entry = table_reference.get(key, false);
                        if (entry == null)
                            System.out.println("missing value for entry " + UTF8.String(key) + " in reference table");
                        else
                            if (!ByteBuffer.equals(entry.getColBytes(1, false), key)) System.out.println("wrong value for entry " + UTF8.String(key) + ": " + UTF8.String(entry.getColBytes(1, false)) + " in reference table");
                    }

                    if (i % 1000 == 0) {
                        System.out.println(i + " entries processed so far.");
                    }
                }
            }

            if (command.equals("ramtest")) {
                // fill database with random entries and delete them again;
                // this is repeated without termination; after each loop
                // the current ram is printed out
                // args: <number-of-entries> <random-startpoint>
                final long count = Long.parseLong(args[4]);
                final long randomstart = Long.parseLong(args[5]);
                byte[] key;
                Random random;
                long start, write, remove;
                int loop = 0;
                while (true) {
                    // write
                    random = new Random(randomstart);
                    start = System.currentTimeMillis();
                    for (int i = 0; i < count; i++) {
                        key = randomHash(random);
                        table_test.put(table_test.row().newEntry(new byte[][]{key, key, dummyvalue2}));
                    }
                    write = System.currentTimeMillis() - start;

                    // delete
                    random = new Random(randomstart);
                    start = System.currentTimeMillis();
                    for (int i = 0; i < count; i++) {
                        key = randomHash(random);
                        table_test.delete(key);
                    }
                    remove = System.currentTimeMillis() - start;

                    System.out.println("Loop " + loop + ": Write = " + write + ", Remove = " + remove);
                    System.out.println(" bevore GC: " +
                              "free = " + MemoryControl.free() +
                            ", max = " + MemoryControl.maxMemory() +
                            ", total = " + MemoryControl.total());
                    System.gc();
                    System.out.println(" after  GC: " +
                            "free = " + MemoryControl.free() +
                          ", max = " + MemoryControl.maxMemory() +
                          ", total = " + MemoryControl.total());
                  loop++;
                }
            }

            if (command.equals("list")) {
                CloneableIterator<Row.Entry> i = null;
                if (table_test instanceof SQLTable) i = ((SQLTable) table_test).rows();
                if(i != null) {
                    Row.Entry row;
                    while (i.hasNext()) {
                        row = i.next();
                        for (int j = 0; j < row.columns(); j++) System.out.print(row.getColUTF8(j) + ",");
                        System.out.println();
                    }
                }
            }

            final long aftercommand = System.currentTimeMillis();
            checkEquivalence(table_test, table_reference);
            final long afterequiv = System.currentTimeMillis();
            // final report
            System.out.println("Database size = " + table_test.size() + " unique entries.");

            // finally close the database/table
            table_test.close();
            if (table_reference != null) table_reference.close();

            final long afterclose = System.currentTimeMillis();

            System.out.println("Execution time: open=" + (afterinit - startup) +
                    ", command=" + (aftercommand - afterinit) +
                    ", equiv=" + (afterequiv - aftercommand) +
                    ", close=" + (afterclose - afterequiv) +
                    ", total=" + (afterclose - startup));
            profiler.terminate();
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
    }
}

final class memprofiler extends Thread {

    ChartPlotter memChart;
    boolean run;
    File outputFile;
    long start;

    public memprofiler(final int width, final int height, final int expectedTimeSeconds, final File outputFile) {
        this.outputFile = outputFile;
        final int expectedKilobytes = 20 * 1024;//(Runtime.getRuntime().totalMemory() / 1024);
        this.memChart = new ChartPlotter(width, height, "FFFFFF", "000000", "000000", 50, 20, 20, 20, "MEMORY CHART FROM EXECUTION AT " + new Date(), null);
        final int timescale = 10; // steps with each 10 seconds
        final int memscale = 1024;
        this.memChart.declareDimension(ChartPlotter.DIMENSION_BOTTOM, timescale, (width - 40) * timescale / expectedTimeSeconds, 0, "FFFFFF", "555555", "SECONDS");
        this.memChart.declareDimension(ChartPlotter.DIMENSION_LEFT, memscale, (height - 40) * memscale / expectedKilobytes, 0, "FFFFFF", "555555", "KILOBYTES");
        this.run = true;
        this.start = System.currentTimeMillis();
    }

    @Override
    public void run() {
        try {
            int seconds0 = 0, kilobytes0 = 0;
            int seconds1 = 0, kilobytes1 = 0;
            while (this.run) {
                this.memChart.setColor(Long.parseLong("FF0000", 16));
                seconds1 = (int) ((System.currentTimeMillis() - this.start) / 1000);
                kilobytes1 = (int) (MemoryControl.used() / 1024);
                this.memChart.chartLine(ChartPlotter.DIMENSION_BOTTOM, ChartPlotter.DIMENSION_LEFT, seconds0, kilobytes0, seconds1, kilobytes1);
                seconds0 = seconds1;
                kilobytes0 = kilobytes1;
                try {Thread.sleep(100);} catch (final InterruptedException e) {}
            }
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
        ImageIO.setUseCache(false);
        try {
            ImageIO.write(this.memChart.getImage(), "png", this.outputFile);
        } catch (final IOException e) {
            // do noting
        } catch (final Exception e) {
            ConcurrentLog.logException(e);
        }
    }

    public void terminate() {
        this.run = false;
        while (isAlive()) {
            try {Thread.sleep(1000);} catch (final InterruptedException e) {}
        }
    }
}

