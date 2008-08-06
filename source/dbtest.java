// test application
// shall be used to compare the kelondroDB with other databases
// with relevance to yacy-specific use cases

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import javax.imageio.ImageIO;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroCache;
import de.anomic.kelondro.kelondroCloneableIterator;
import de.anomic.kelondro.kelondroEcoTable;
import de.anomic.kelondro.kelondroFlexTable;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroIntBytesMap;
import de.anomic.kelondro.kelondroProfile;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroRowSet;
import de.anomic.kelondro.kelondroSQLTable;
import de.anomic.kelondro.kelondroSplitTable;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.serverInstantBusyThread;
import de.anomic.server.serverMemory;
import de.anomic.server.logging.serverLog;
import de.anomic.ymage.ymageChart;

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
        final String s = (kelondroBase64Order.enhancedCoder.encodeLong(Math.abs(r0), 6) +
                    kelondroBase64Order.enhancedCoder.encodeLong(Math.abs(r1), 6));
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
            final String s = new String(this.value).trim();
            if (s.length() == 0) return false;
            final long source = new Long(s).longValue();
            return new String(this.key).equals(new String(randomHash(source, source)));
        }

        public byte[] getKey() {
            return this.key;
        }

        public byte[] getValue() {
            return this.value;
        }

        public String toString() {
            return new String(this.key) + "#" + new String(this.value);
        }

    }

    public static abstract class STJob implements Runnable {
        private final kelondroIndex table_test, table_reference;

        private final long source;

        public STJob(final kelondroIndex table_test, final kelondroIndex table_reference, final long aSource) {
            this.table_test = table_test;
            this.table_reference = table_reference;
            this.source = aSource;
        }

        public kelondroIndex getTable_test() {
            return this.table_test;
        }

        public kelondroIndex getTable_reference() {
            return this.table_reference;
        }

        public abstract void run();

        public long getSource() {
            return this.source;
        }
    }

    public static final class WriteJob extends STJob {
        public WriteJob(final kelondroIndex table_test, final kelondroIndex table_reference, final long aSource) {
            super(table_test, table_reference, aSource);
        }

        public void run() {
            final STEntry entry = new STEntry(this.getSource());
            System.out.println("write:  " + serverLog.arrayList(entry.getKey(), 0, entry.getKey().length));
            try {
                getTable_test().put(getTable_test().row().newEntry(new byte[][] { entry.getKey(), entry.getValue() , entry.getValue() }));
                if (getTable_reference() != null) getTable_reference().put(getTable_test().row().newEntry(new byte[][] { entry.getKey(), entry.getValue() , entry.getValue() }));
            } catch (final IOException e) {
                System.err.println(e);
                e.printStackTrace();
                System.exit(0);
            }
        }
    }

    public static final class RemoveJob extends STJob {
        public RemoveJob(final kelondroIndex table_test, final kelondroIndex table_reference, final long aSource) {
            super(table_test, table_reference, aSource);
        }

        public void run() {
            final STEntry entry = new STEntry(this.getSource());
            System.out.println("remove: " + serverLog.arrayList(entry.getKey(), 0, entry.getKey().length));
            try {
                getTable_test().remove(entry.getKey());
                if (getTable_reference() != null) getTable_reference().remove(entry.getKey());
            } catch (final IOException e) {
                System.err.println(e);
                e.printStackTrace();
                System.exit(0);
            }
        }
    }

    public static final class ReadJob extends STJob {
        public ReadJob(final kelondroIndex table_test, final kelondroIndex table_reference, final long aSource) {
            super(table_test, table_reference, aSource);
        }

        public void run() {
            final STEntry entry = new STEntry(this.getSource());
            try {
                kelondroRow.Entry entryBytes = getTable_test().get(entry.getKey());
                if (entryBytes != null) {
                    final STEntry dbEntry = new STEntry(entryBytes.getColBytes(0), entryBytes.getColBytes(1));
                    if (!dbEntry.isValid()) {
                        System.out.println("INVALID table_test: " + dbEntry);
                    } /* else {
                        System.out.println("_VALID_ table_test: " + dbEntry);
                        getTable().remove(entry.getKey(), true);
                    } */
                }
                if (getTable_reference() != null) {
                entryBytes = getTable_reference().get(entry.getKey());
                if (entryBytes != null) {
                    final STEntry dbEntry = new STEntry(entryBytes.getColBytes(0), entryBytes.getColBytes(1));
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
                e.printStackTrace();
                System.exit(0);
            }
        }
    }
    
    public static kelondroIndex selectTableType(final String dbe, final String tablename, final kelondroRow testRow) throws Exception {
        if (dbe.equals("kelondroRowSet")) {
            return new kelondroRowSet(testRow, 0);
        }
        if (dbe.equals("kelondroTree")) {
            final File tablefile = new File(tablename + ".kelondro.db");
            return new kelondroCache(new kelondroTree(tablefile, true, 0, testRow));
        }
        if (dbe.equals("kelondroFlexTable")) {
            final File tablepath = new File(tablename).getParentFile();
            return new kelondroFlexTable(tablepath, new File(tablename).getName(), testRow, 0, true);
        }
        if (dbe.equals("kelondroSplitTable")) {
            final File tablepath = new File(tablename).getParentFile();
            return new kelondroSplitTable(tablepath, new File(tablename).getName(), testRow, true);
        }
        if (dbe.equals("kelondroEcoTable")) {
            return new kelondroEcoTable(new File(tablename), testRow, kelondroEcoTable.tailCacheForceUsage, 1000, 0);
        }
        if (dbe.equals("mysql")) {
            return new kelondroSQLTable("mysql", testRow);
        }
        if (dbe.equals("pgsql")) {
            return new kelondroSQLTable("pgsql", testRow);
        }
        return null;
    }
    
    public static boolean checkEquivalence(final kelondroIndex test, final kelondroIndex reference) throws IOException {
        if (reference == null) return true;
        if (test.size() == reference.size()) {
            System.out.println("* Testing equivalence of test table to reference table, " + test.size() + " entries");
        } else  {
            System.out.println("* Testing equivalence of test table to reference table: FAILED! the tables have different sizes: test.size() = " + test.size() + ", reference.size() = " + reference.size());
            return false;
        }
        boolean eq = true;
        kelondroRow.Entry test_entry, reference_entry;
        
        Iterator<kelondroRow.Entry> i = test.rows(true, null);
        System.out.println("* Testing now by enumeration over test table");
        final long ts = System.currentTimeMillis();
        while (i.hasNext()) {
            test_entry = i.next();
            reference_entry = reference.get(test_entry.getPrimaryKeyBytes());
            if (!test_entry.equals(reference_entry)) {
                System.out.println("* FAILED: test entry with key '" + new String(test_entry.getPrimaryKeyBytes()) + "' has no equivalent entry in reference");
                eq = false;
            }
        }
        
        i = reference.rows(true, null);
        System.out.println("* Testing now by enumeration over reference table");
        final long rs = System.currentTimeMillis();
        while (i.hasNext()) {
            reference_entry = i.next();
            test_entry = test.get(reference_entry.getPrimaryKeyBytes());
            if (!test_entry.equals(reference_entry)) {
                System.out.println("* FAILED: reference entry with key '" + new String(test_entry.getPrimaryKeyBytes()) + "' has no equivalent entry in test");
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
        assert assertionenabled = true;
        if (assertionenabled) System.out.println("*** Asserts are enabled"); else System.out.println("*** HINT: YOU SHOULD ENABLE ASSERTS! (include -ea in start arguments");
        final long mb = serverMemory.available() / 1024 / 1024;
        System.out.println("*** RAM = " + mb + " MB");
        System.out.print(">java " +
                ((assertionenabled) ? "-ea " : "") +
                ((mb > 100) ? ("-Xmx" + (mb + 11) + "m ") : " ") +
                "-classpath classes dbtest ");
        for (int i = 0; i < args.length; i++) System.out.print(args[i] + " ");
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
            final kelondroRow testRow = new kelondroRow("byte[] key-" + keylength + ", byte[] dummy-" + keylength + ", value-" + valuelength, kelondroBase64Order.enhancedCoder, 0);
            final kelondroIndex table_test = selectTableType(dbe_test, tablename_test, testRow);
            final kelondroIndex table_reference = (dbe_reference == null) ? null : selectTableType(dbe_reference, tablename_reference, testRow);
            
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
                kelondroProfile ioProfileAcc = new kelondroProfile();
                kelondroProfile cacheProfileAcc = new kelondroProfile();
                kelondroProfile[] profiles;
                for (int i = 0; i < count; i++) {
                    key = randomHash(random);
                    table_test.put(testRow.newEntry(new byte[][]{key, key, dummyvalue2}));
                    if (table_reference != null) table_reference.put(testRow.newEntry(new byte[][]{key, key, dummyvalue2}));
                    if (i % 1000 == 0) {
                        System.out.println(i + " entries. ");
                        if (table_test instanceof kelondroTree) {
                            profiles = ((kelondroTree) table_test).profiles();
                            System.out.println("Cache Delta: " + kelondroProfile.delta(profiles[0], cacheProfileAcc).toString());
                            System.out.println("IO    Delta: " + kelondroProfile.delta(profiles[1],    ioProfileAcc).toString());
                            cacheProfileAcc = profiles[0].clone();
                               ioProfileAcc = profiles[1].clone();
                        }
                    }
                }
            }
            
            if (command.equals("read")) {
                // read the database and compare with random entries;
                // args: <number-of-entries> <random-startpoint>
                final long count = Long.parseLong(args[4]);
                final long randomstart = Long.parseLong(args[5]);
                final Random random = new Random(randomstart);
                kelondroRow.Entry entry;
                byte[] key;
                for (int i = 0; i < count; i++) {
                    key = randomHash(random);
                    entry = table_test.get(key);
                    if (entry == null)
                        System.out.println("missing value for entry " + new String(key) + " in test table");
                    else
                        if (!(new String(entry.getColBytes(1)).equals(new String(key)))) System.out.println("wrong value for entry " + new String(key) + ": " + new String(entry.getColBytes(1)) + " in test table");
                    if (table_reference != null) {
                        entry = table_reference.get(key);
                        if (entry == null)
                            System.out.println("missing value for entry " + new String(key) + " in reference table");
                        else
                            if (!(new String(entry.getColBytes(1)).equals(new String(key)))) System.out.println("wrong value for entry " + new String(key) + ": " + new String(entry.getColBytes(1)) + " in reference table");
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
                        table_test.remove(key);
                    }
                    remove = System.currentTimeMillis() - start;
                    
                    System.out.println("Loop " + loop + ": Write = " + write + ", Remove = " + remove);
                    System.out.println(" bevore GC: " +
                              "free = " + serverMemory.free() +
                            ", max = " + serverMemory.max() +
                            ", total = " + serverMemory.total());
                    System.gc();
                    System.out.println(" after  GC: " +
                            "free = " + serverMemory.free() +
                          ", max = " + serverMemory.max() +
                          ", total = " + serverMemory.total());
                  loop++;
                }
            }
            
            if (command.equals("list")) {
                kelondroCloneableIterator<kelondroRow.Entry> i = null;
                if (table_test instanceof kelondroTree) i = ((kelondroTree) table_test).rows(true, null);
                if (table_test instanceof kelondroSQLTable) i = ((kelondroSQLTable) table_test).rows(true, null);
                if(i != null) {
                    kelondroRow.Entry row;
                    while (i.hasNext()) {
                        row = i.next();
                        for (int j = 0; j < row.columns(); j++) System.out.print(row.getColString(j, null) + ",");
                        System.out.println();
                    }
                }
            }
            
            if (command.equals("stressThreaded")) {
                // 
                // args: <number-of-writes> <number-of-reads-per-write> <random-startpoint>
                // example: java -ea  -classpath classes dbtest stressThreaded kelondroEcoTable kelondroFlexTable stressthreadedtest 500 50 0 
                /* result with svn 4346
                   kelondroFlex:
                   removed: 70, size of jcontrol set: 354, size of kcontrol set: 354
                   Database size = 354 unique entries.
                   Execution time: open=1329, command=36234, close=17, total=37580
                   kelondroEco:
                   removed: 70, size of jcontrol set: 354, size of kcontrol set: 354
                   Database size = 354 unique entries.
                   Execution time: open=1324, command=34032, close=1, total=35357
                 */
                final long writeCount = Long.parseLong(args[4]);
                final long readCount = Long.parseLong(args[5]);
                final long randomstart = Long.parseLong(args[6]);
                final Random random = new Random(randomstart);
                long r;
                Long R;
                int p, rc=0;
                final ArrayList<Long> ra = new ArrayList<Long>();
                final HashSet<Long> jcontrol = new HashSet<Long>();
                final kelondroIntBytesMap kcontrol = new kelondroIntBytesMap(1, 0);
                for (int i = 0; i < writeCount; i++) {
                    r = Math.abs(random.nextLong() % 1000);
                    jcontrol.add(Long.valueOf(r));
                    kcontrol.putb((int) r, "x".getBytes());
                    serverInstantBusyThread.oneTimeJob(new WriteJob(table_test, table_reference, r), 0, 50);
                    if (random.nextLong() % 5 == 0) ra.add(Long.valueOf(r));
                    for (int j = 0; j < readCount; j++) {
                        serverInstantBusyThread.oneTimeJob(new ReadJob(table_test, table_reference, random.nextLong() % writeCount), random.nextLong() % 1000, 20);
                    }
                    if ((ra.size() > 0) && (random.nextLong() % 7 == 0)) {
                        rc++;
                        p = Math.abs(random.nextInt() % ra.size());
                        R = ra.get(p);
                        jcontrol.remove(R);
                        kcontrol.removeb((int) R.longValue());
                        System.out.println("remove: " + R.longValue());
                        serverInstantBusyThread.oneTimeJob(new RemoveJob(table_test, table_reference, (ra.remove(p)).longValue()), 0, 50);
                    }
                }
                System.out.println("removed: " + rc + ", size of jcontrol set: " + jcontrol.size() + ", size of kcontrol set: " + kcontrol.size());
                while (serverInstantBusyThread.instantThreadCounter > 0) {
                    try {Thread.sleep(1000);} catch (final InterruptedException e) {} // wait for all tasks to finish
                    System.out.println("count: "  + serverInstantBusyThread.instantThreadCounter + ", jobs: " + serverInstantBusyThread.jobs.toString());
                }
                try {Thread.sleep(6000);} catch (final InterruptedException e) {}
            }
            
            if (command.equals("stressSequential")) {
                // 
                // args: <number-of-writes> <number-of-reads> <random-startpoint>
                final long writeCount = Long.parseLong(args[4]);
                final long readCount = Long.parseLong(args[5]);
                final long randomstart = Long.parseLong(args[6]);
                final Random random = new Random(randomstart);
                long r;
                Long R;
                int p, rc=0;
                final ArrayList<Long> ra = new ArrayList<Long>();
                final HashSet<Long> jcontrol = new HashSet<Long>();
                final kelondroIntBytesMap kcontrol = new kelondroIntBytesMap(1, 0);
                for (int i = 0; i < writeCount; i++) {
                    //if (i == 30) random = new Random(randomstart);
                    r = Math.abs(random.nextLong() % 1000);
                    jcontrol.add(Long.valueOf(r));
                    kcontrol.putb((int) r, "x".getBytes());
                    new WriteJob(table_test, table_reference, r).run();
                    if (random.nextLong() % 5 == 0) ra.add(Long.valueOf(r));
                    for (int j = 0; j < readCount; j++) {
                        new ReadJob(table_test, table_reference, random.nextLong() % writeCount).run();
                    }
                    if ((ra.size() > 0) && (random.nextLong() % 7 == 0)) {
                        rc++;
                        p = Math.abs(random.nextInt() % ra.size());
                        R = ra.get(p);
                        jcontrol.remove(R);
                        kcontrol.removeb((int) R.longValue());
                        new RemoveJob(table_test, table_reference, (ra.remove(p)).longValue()).run();
                    }
                }
                try {Thread.sleep(1000);} catch (final InterruptedException e) {}
                System.out.println("removed: " + rc + ", size of jcontrol set: " + jcontrol.size() + ", size of kcontrol set: " + kcontrol.size());
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
            e.printStackTrace();
        }
    }
}

final class memprofiler extends Thread {
    
    ymageChart memChart;
    boolean run;
    File outputFile;
    long start;
    
    public memprofiler(final int width, final int height, final int expectedTimeSeconds, final File outputFile) {
        this.outputFile = outputFile;
        final int expectedKilobytes = 20 * 1024;//(Runtime.getRuntime().totalMemory() / 1024);
        memChart = new ymageChart(width, height, "FFFFFF", "000000", "000000", 50, 20, 20, 20, "MEMORY CHART FROM EXECUTION AT " + new Date(), null);
        final int timescale = 10; // steps with each 10 seconds
        final int memscale = 1024;
        memChart.declareDimension(ymageChart.DIMENSION_BOTTOM, timescale, (width - 40) * timescale / expectedTimeSeconds, 0, "FFFFFF", "555555", "SECONDS");
        memChart.declareDimension(ymageChart.DIMENSION_LEFT, memscale, (height - 40) * memscale / expectedKilobytes, 0, "FFFFFF", "555555", "KILOBYTES");
        run = true;
        start = System.currentTimeMillis();
    }
    
    public void run() {
        int seconds0 = 0, kilobytes0 = 0;
        int seconds1 = 0, kilobytes1 = 0;
        while(run) {
            memChart.setColor("FF0000");
            seconds1 = (int) ((System.currentTimeMillis() - start) / 1000);
            kilobytes1 = (int) (serverMemory.used() / 1024);
            memChart.chartLine(ymageChart.DIMENSION_BOTTOM, ymageChart.DIMENSION_LEFT, seconds0, kilobytes0, seconds1, kilobytes1);
            seconds0 = seconds1;
            kilobytes0 = kilobytes1;
            try {Thread.sleep(100);} catch (final InterruptedException e) {}
        }
        try {
            ImageIO.write(memChart.getImage(), "png", outputFile);
        } catch (final IOException e) {}
    }
    
    public void terminate() {
        run = false;
        while (this.isAlive()) {
            try {Thread.sleep(1000);} catch (final InterruptedException e) {}
        }
    }
}

