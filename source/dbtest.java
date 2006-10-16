// test application
// shall be used to compare the kelondroDB with other databases
// with relevance to yacy-specific use cases

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroFlexSplitTable;
import de.anomic.kelondro.kelondroFlexTable;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroNaturalOrder;
import de.anomic.kelondro.kelondroOrder;
import de.anomic.kelondro.kelondroProfile;
import de.anomic.kelondro.kelondroRow;
import de.anomic.kelondro.kelondroSplittedTree;
import de.anomic.kelondro.kelondroTree;
import de.anomic.server.serverInstantThread;
import de.anomic.server.serverMemory;
import de.anomic.ymage.ymageChart;
import de.anomic.ymage.ymagePNGEncoderAWT;

public class dbtest {

    public final static int keylength = 12;
    public final static int valuelength = 223; // sum of all data length as defined in plasmaURL
    //public final static long buffer = 0;
    public final static long buffer = 8192 * 1024; // 8 MB buffer
    public final static long preload = 1000; // 1 second
    public static byte[] dummyvalue2 = new byte[valuelength];
    static {
        // fill the dummy value
        for (int i = 0; i < valuelength; i++) dummyvalue2[i] = '-';
        dummyvalue2[0] = '{';
        dummyvalue2[valuelength - 1] = '}';
    }
    
    public static byte[] randomHash(final long r0, final long r1) {
        // a long can have 64 bit, but a 12-byte hash can have 6 * 12 = 72 bits
        // so we construct a generic Hash using two long values
        return (kelondroBase64Order.enhancedCoder.encodeLong(Math.abs(r0), 11).substring(5) +
                kelondroBase64Order.enhancedCoder.encodeLong(Math.abs(r1), 11).substring(5)).getBytes();
    }
    
    public static byte[] randomHash(Random r) {
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
            String s = new String(this.value).trim();
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
        private final kelondroIndex table;

        private final long source;

        public STJob(final kelondroIndex aTable, final long aSource) {
            this.table = aTable;
            this.source = aSource;
        }

        public kelondroIndex getTable() {
            return this.table;
        }

        public abstract void run();

        public long getSource() {
            return this.source;
        }
    }

    public static final class WriteJob extends STJob {
        public WriteJob(final kelondroIndex aTable, final long aSource) {
            super(aTable, aSource);
        }

        public void run() {
            final STEntry entry = new STEntry(this.getSource());
            try {
                getTable().put(getTable().row().newEntry(new byte[][] { entry.getKey(), entry.getValue() , entry.getValue() }));
            } catch (IOException e) {
                System.err.println(e);
                e.printStackTrace();
                System.exit(0);
            }
        }
    }

    public static final class ReadJob extends STJob {
        public ReadJob(final kelondroIndex aTable, final long aSource) {
            super(aTable, aSource);
        }

        public void run() {
            final STEntry entry = new STEntry(this.getSource());
            try {
                final kelondroRow.Entry entryBytes = getTable().get(entry.getKey());
                if (entryBytes != null) {
                    //System.out.println("ENTRY=" + entryBytes.getColString(1, null));
                    final STEntry dbEntry = new STEntry(entryBytes.getColBytes(0), entryBytes.getColBytes(1));
                    if (!dbEntry.isValid()) {
                        System.out.println("INVALID: " + dbEntry);
                    }/* else {
                        System.out.println("_VALID_: " + dbEntry);
                        getTable().remove(entry.getKey());
                    }*/
                }
            } catch (IOException e) {
                System.err.println(e);
                e.printStackTrace();
                System.exit(0);
            }
        }
    }
    
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        
        String dbe = args[0];       // the database engine
        String command = args[1];   // test command
        String tablename = args[2]; // name of test-table
        long startup = System.currentTimeMillis();
        
        try {
            kelondroIndex table = null;
            // create a memory profiler
            memprofiler profiler = new memprofiler(1024, 320, 120, new File(tablename + ".profile.png"));
            profiler.start();
            
            // create the database access
            kelondroRow testRow = new kelondroRow("byte[] key-" + keylength + ", byte[] dummy-" + keylength + ", value-" + valuelength);
            if (dbe.equals("kelondroTree")) {
                File tablefile = new File(tablename + ".kelondro.db");
                table = new kelondroTree(tablefile, buffer, preload, kelondroTree.defaultObjectCachePercent, testRow);
            }
            if (dbe.equals("kelondroSplittedTree")) {
                File tablepath = new File(tablename).getParentFile();
                tablename = new File(tablename).getName();
                table = new kelondroSplittedTree(tablepath, tablename, kelondroBase64Order.enhancedCoder,
                                buffer, preload,
                                8, testRow, 1, 80);
            }
            if (dbe.equals("kelondroFlexTable")) {
                File tablepath = new File(tablename).getParentFile();
                table = new kelondroFlexTable(tablepath, new File(tablename).getName(), buffer, preload, testRow, kelondroBase64Order.enhancedCoder);
            }
            if (dbe.equals("kelondroFlexSplitTable")) {
                File tablepath = new File(tablename).getParentFile();
                table = new kelondroFlexSplitTable(tablepath, new File(tablename).getName(), buffer, preload, testRow, kelondroBase64Order.enhancedCoder);
            }
            if (dbe.equals("mysql")) {
                table = new dbTable("mysql", testRow);
            }
            
            if (dbe.equals("pgsql")) {
                table = new dbTable("pgsql", testRow);
            }
            
            long afterinit = System.currentTimeMillis();
            System.out.println("Test for db-engine " + dbe +  " started to create file " + tablename + " with test " + command);
            
            // execute command
            if (command.equals("create")) {
                // do nothing, since opening of the database access must handle this
                System.out.println("Database created");
            }
            
            if (command.equals("fill")) {
                // fill database with random entries;
                // args: <number-of-entries> <random-startpoint>
                long count = Long.parseLong(args[3]);
                long randomstart = Long.parseLong(args[4]);
                Random random = new Random(randomstart);
                byte[] key;
                kelondroProfile ioProfileAcc = new kelondroProfile();
                kelondroProfile cacheProfileAcc = new kelondroProfile();
                kelondroProfile[] profiles;
                for (int i = 0; i < count; i++) {
                    key = randomHash(random);
                    table.put(table.row().newEntry(new byte[][]{key, key, dummyvalue2}));
                    if (i % 1000 == 0) {
                        System.out.println(i + " entries. " + ((table instanceof kelondroTree) ? ((kelondroTree) table).cacheNodeStatusString() : ""));
                        if (table instanceof kelondroTree) {
                            profiles = ((kelondroTree) table).profiles();
                            System.out.println("Cache Delta: " + kelondroProfile.delta(profiles[0], cacheProfileAcc).toString());
                            System.out.println("IO    Delta: " + kelondroProfile.delta(profiles[1],    ioProfileAcc).toString());
                            cacheProfileAcc = (kelondroProfile) profiles[0].clone();
                               ioProfileAcc = (kelondroProfile) profiles[1].clone();
                        }
                    }
                }
            }
            
            if (command.equals("benchfill")) {
                // fill database with random entries;
                // args: <number-of-entries> <random-startpoint>
                long count = Long.parseLong(args[3]);
                long time = System.currentTimeMillis();
                long randomstart = Long.parseLong(args[4]);
                Random random = new Random(randomstart);
                byte[] key;
                for (int i = 0; i < count; i++) {
                    key = randomHash(random);
                    table.put(table.row().newEntry(new byte[][]{key, key, dummyvalue2}));
                    if (i % 10000 == 0) {
                        System.out.println(System.currentTimeMillis() - time);
                    }
                }
            }
            
            if (command.equals("read")) {
                // read the database and compare with random entries;
                // args: <number-of-entries> <random-startpoint>
                long count = Long.parseLong(args[3]);
                long randomstart = Long.parseLong(args[4]);
                Random random = new Random(randomstart);
                kelondroRow.Entry entry;
                byte[] key;
                for (int i = 0; i < count; i++) {
                    key = randomHash(random);
                    entry = table.get(key);
                    if (entry == null) System.out.println("missing value for entry " + new String(key)); else
                    if (!(new String(entry.getColBytes(1)).equals(new String(key)))) System.out.println("wrong value for entry " + new String(key) + ": " + new String(entry.getColBytes(1)));
                    if (i % 1000 == 0) {
                        System.out.println(i + " entries processed so far.");
                    }
                }
            }
            
            if (command.equals("benchread")) {
                // read the database and compare with random entries;
                // args: <number-of-entries> <random-startpoint>
                long count = Long.parseLong(args[3]);
                long time = System.currentTimeMillis();
                long randomstart = Long.parseLong(args[4]);
                Random random = new Random(randomstart);
                kelondroRow.Entry entry;
                byte[] key;
                for (int i = 0; i < count; i++) {
                    key = randomHash(random);
                    entry = table.get(key);
                    if (entry == null) System.out.println("missing value for entry " + new String(key)); else
                    if (!(new String(entry.getColBytes(1)).equals(new String(key)))) System.out.println("wrong value for entry " + new String(key) + ": " + new String(entry.getColBytes(1)));
                    if (i % 10000 == 0) {
                        System.out.println(System.currentTimeMillis() - time);
                    }
                }
            }
            
            if (command.equals("ramtest")) {
                // fill database with random entries and delete them again;
                // this is repeated without termination; after each loop
                // the current ram is printed out
                // args: <number-of-entries> <random-startpoint>
                long count = Long.parseLong(args[3]);
                long randomstart = Long.parseLong(args[4]);
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
                        table.put(table.row().newEntry(new byte[][]{key, key, dummyvalue2}));
                    }
                    write = System.currentTimeMillis() - start;

                    // delete
                    random = new Random(randomstart);
                    start = System.currentTimeMillis();
                    for (int i = 0; i < count; i++) {
                        key = randomHash(random);
                        table.remove(key);
                    }
                    remove = System.currentTimeMillis() - start;
                    
                    System.out.println("Loop " + loop + ": Write = " + write + ", Remove = " + remove);
                    System.out.println(" bevore GC: " +
                              "free = " + Runtime.getRuntime().freeMemory() +
                            ", max = " + Runtime.getRuntime().maxMemory() +
                            ", total = " + Runtime.getRuntime().totalMemory());
                    System.gc();
                    System.out.println(" after  GC: " +
                            "free = " + Runtime.getRuntime().freeMemory() +
                          ", max = " + Runtime.getRuntime().maxMemory() +
                          ", total = " + Runtime.getRuntime().totalMemory());
                  loop++;
                }
            }
            
            if (command.equals("list")) {
                Iterator i = null;
                if (table instanceof kelondroSplittedTree) i = ((kelondroSplittedTree) table).rows(true, false, null);
                if (table instanceof kelondroTree) i = ((kelondroTree) table).rows(true, false, null);
                if (table instanceof dbTable) i = ((dbTable) table).rows(true, false, null);
                byte[][] row;
                while (i.hasNext()) {
                    row = (byte[][]) i.next();
                    for (int j = 0; j < row.length; j++) System.out.print(new String(row[j]) + ",");
                    System.out.println();
                }
            }
            
            if (command.equals("stressThreaded")) {
                // 
                // args: <number-of-writes> <number-of-reads-per-write> <random-startpoint>
                long writeCount = Long.parseLong(args[3]);
                long readCount = Long.parseLong(args[4]);
                long randomstart = Long.parseLong(args[5]);
                final Random random = new Random(randomstart);
                for (int i = 0; i < writeCount; i++) {
                    serverInstantThread.oneTimeJob(new WriteJob(table, i), random.nextLong() % 1000, 50);
                    for (int j = 0; j < readCount; j++) {
                        serverInstantThread.oneTimeJob(new ReadJob(table, random.nextLong() % writeCount), random.nextLong() % 1000, 20);
                    }
                }
                while (serverInstantThread.instantThreadCounter > 0)
                    try {Thread.sleep(100);} catch (InterruptedException e) {} // wait for all tasks to finish
                try {Thread.sleep(6000);} catch (InterruptedException e) {}
            }
            
            if (command.equals("stressSequential")) {
                // 
                // args: <number-of-writes> <number-of-reads> <random-startpoint>
                long writeCount = Long.parseLong(args[3]);
                long readCount = Long.parseLong(args[4]);
                long randomstart = Long.parseLong(args[5]);
                final Random random = new Random(randomstart);
                for (int i = 0; i < writeCount; i++) {
                    new WriteJob(table, i).run();
                    for (int j = 0; j < readCount; j++) {
                        new ReadJob(table, random.nextLong() % writeCount).run();
                    }
                }
            }
            
            long aftercommand = System.currentTimeMillis();
            // final report
            System.out.println("Database size = " + table.size() + " unique entries.");
            
            // finally close the database/table
            if (table instanceof kelondroTree) ((kelondroTree) table).close();
            if (table instanceof kelondroFlexTable) ((kelondroFlexTable) table).close();
            if (table instanceof kelondroSplittedTree) ((kelondroSplittedTree) table).close();
            if (table instanceof dbTable) ((dbTable)table).close();
            
            long afterclose = System.currentTimeMillis();
            
            System.out.println("Execution time: open=" + (afterinit - startup) + ", command=" + (aftercommand - afterinit) + ", close=" + (afterclose - aftercommand) + ", total=" + (afterclose - startup));
            profiler.terminate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/*
 * Commands to create a database using mysql:
 * 
 * CREATE database yacy;
 * USE yacy;
 * CREATE TABLE hash CHAR(12) not null primary key, value BLOB);
 * insert into user (Host, User, Password) values ('%','yacy',password('yacy'));
 * insert into db (User, Db, Select_priv, Insert_priv, Update_priv, Delete_priv) values ('yacy@%','yacy','Y','Y','Y','Y')
 * grant ALL on yacy.* to yacy;
 */
final class dbTable implements kelondroIndex {

    private final String db_driver_str_mysql = "org.gjt.mm.mysql.Driver";
    private final String db_driver_str_pgsql = "org.postgresql.Driver";
    
    private final String db_conn_str_mysql    = "jdbc:mysql://192.168.0.2:3306/yacy";
    private final String db_conn_str_pgsql   = "jdbc:postgresql://192.168.0.2:5432";
    
    private final String db_usr_str    = "yacy";
    private final String db_pwd_str    = "yacy";
    
    private Connection theDBConnection = null;
    private final kelondroOrder order = new kelondroNaturalOrder(true);
    private kelondroRow rowdef;
    
    public dbTable(String dbType, kelondroRow rowdef) throws Exception {
        this.rowdef = rowdef;
        openDatabaseConnection(dbType);
    }
    
    private void openDatabaseConnection(String dbType) throws Exception{

        if (dbType == null) throw new IllegalArgumentException(); 

        String dbDriverStr = null, dbConnStr = null;            
        if (dbType.equalsIgnoreCase("mysql")) {
            dbDriverStr = db_driver_str_mysql;
            dbConnStr = db_conn_str_mysql;
        } else if (dbType.equalsIgnoreCase("pgsql")) {
            dbDriverStr = db_driver_str_pgsql;
            dbConnStr = db_conn_str_pgsql;
        }                
        try {            
            Class.forName(dbDriverStr).newInstance();
        } catch (Exception e) {
            throw new Exception ("Unable to load the jdbc driver: " + e.getMessage(),e);
        }
        try {
            this.theDBConnection = DriverManager.getConnection (dbConnStr,this.db_usr_str,this.db_pwd_str);
        } catch (Exception e) {
            throw new Exception ("Unable to establish a database connection: " + e.getMessage(),e);
        }
        
    }        
    
    public void close() throws IOException {
        try {
            this.theDBConnection.close();
        } catch (Exception e) {
            throw new IOException("Unable to close the database connection.");
        }
    }
    
    public int size() throws IOException {
        int size = -1;
        try {
            String sqlQuery = new String
            (
                "SELECT count(value) from test"
            );
            
            PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery); 
            ResultSet result = sqlStatement.executeQuery();
            
            while (result.next()) {
                size = result.getInt(1);
            }  
            
            result.close();
            sqlStatement.close();
            
            return size;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public kelondroRow row() {
        return this.rowdef;
    }
    
    public kelondroRow.Entry get(byte[] key) throws IOException {
        try {
            String sqlQuery = new String
            (
                "SELECT value from test where hash = ?"
            );
            
            PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery); 
            sqlStatement.setString(1, new String(key));
            
            byte[] value = null;
            ResultSet result = sqlStatement.executeQuery();
            while (result.next()) {
                value = result.getBytes("value");
            }  
            
            result.close();
            sqlStatement.close();
            
            if (value == null) return null;
            kelondroRow.Entry entry = this.rowdef.newEntry(value);
            return entry;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        return put(row);
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        try {
            
            kelondroRow.Entry oldEntry = remove(row.getColBytes(0));            
            
            String sqlQuery = new String
            (
                    "INSERT INTO test (" +
                    "hash, " +
                    "value) " +
                    "VALUES (?,?)"
            );                
            
            
            PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery);     
            
            sqlStatement.setString(1, new String(row.getColString(0, null)));
            sqlStatement.setBytes(2, row.bytes());
            sqlStatement.execute();
            
            sqlStatement.close();
            
            return oldEntry;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public kelondroRow.Entry remove(byte[] key) throws IOException {
        try {
            
            kelondroRow.Entry entry =  this.get(key);
            if (entry == null) return entry;
            
            String sqlQuery = new String
            (
                    "DELETE FROM test WHERE hash = ?"
            );                
            
            
            PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery);                 
            sqlStatement.setString(1, new String(key));
            sqlStatement.execute();
            
            return entry;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public kelondroRow.Entry removeOne() {
        return null;
    }
    
    public Iterator rows(boolean up, boolean rotating, byte[] startKey) throws IOException {
        // Objects are of type byte[][]
        return null;
    }

    public Iterator keys(boolean up, boolean rotating, byte[] startKey) {
        // Objects are of type String
        return null;
    }

    public int columns() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int columnSize(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    public kelondroOrder order() {
        return this.order;
    }
    
    public int primarykey() {
        return 0;
    }
    
    public kelondroProfile profile() {
        return new kelondroProfile();
    }
    
}


final class memprofiler extends Thread {
    
    ymageChart memChart;
    boolean run;
    File outputFile;
    long start;
    
    public memprofiler(int width, int height, int expectedTimeSeconds, File outputFile) {
        this.outputFile = outputFile;
        int expectedKilobytes = 20 * 1024;//(Runtime.getRuntime().totalMemory() / 1024);
        memChart = new ymageChart(width, height, "000010", 50, 20, 20, 20, "MEMORY CHART FROM EXECUTION AT " + new Date());
        int timescale = 10; // steps with each 10 seconds
        int memscale = 1024;
        memChart.declareDimension(ymageChart.DIMENSION_BOTTOM, timescale, (width - 40) * timescale / expectedTimeSeconds, "FFFFFF", "555555", "SECONDS");
        memChart.declareDimension(ymageChart.DIMENSION_LEFT, memscale, (height - 40) * memscale / expectedKilobytes , "FFFFFF", "555555", "KILOBYTES");
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
            try {Thread.sleep(100);} catch (InterruptedException e) {}
        }
        try {
            ymagePNGEncoderAWT.toPNG(memChart, true, outputFile);
        } catch (IOException e) {}
    }
    
    public void terminate() {
        run = false;
        while (this.isAlive()) {
            try {Thread.sleep(1000);} catch (InterruptedException e) {}
        }
    }
}

