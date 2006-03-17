// test application
// shall be used to compare the kelondroDB with other databases
// with relevance to yacy-specific use cases

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Random;
import java.util.Date;
import java.util.Iterator;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroSplittedTree;
import de.anomic.kelondro.kelondroTree;
import de.anomic.ymage.ymageChart;
import de.anomic.ymage.ymagePNGEncoderAWT;
import de.anomic.server.serverMemory;
import de.anomic.server.serverInstantThread;

public class dbtest {

    public final static int keylength = 12;
    public final static int valuelength = 223; // sum of all data length as defined in plasmaURL
    //public final static long buffer = 0;
    public final static long buffer = 8192 * 1024; // 8 MB buffer
    public static byte[] dummyvalue1 = new byte[valuelength];
    public static byte[] dummyvalue2 = new byte[valuelength];
    static {
        // fill the dummy value
        for (int i = 0; i < valuelength; i++) dummyvalue1[i] = '.';
        dummyvalue1[0] = '[';
        dummyvalue1[valuelength - 1] = ']';
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
            final long source = new Long(new String(this.value).trim()).longValue();
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
                getTable().put(new byte[][] { entry.getKey(), entry.getValue() , entry.getValue() });
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
                final byte[][] entryBytes = getTable().get(entry.getKey());
                if (entryBytes != null) {
                    System.out.println("ENTRY=" + new String(entryBytes[1]));
                    final STEntry dbEntry = new STEntry(entryBytes[0], entryBytes[1]);
                    if (!dbEntry.isValid()) {
                        System.out.println(dbEntry);
                    } else {
                        getTable().remove(entry.getKey());
                    }
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
            if (dbe.equals("kelondroold")) {
                File tablefile = new File(tablename + ".kelondro.db");
                if (tablefile.exists()) {
                    table = new kelondroTree(tablefile, buffer);
                } else {
                    table = new kelondroTree(tablefile, buffer, new int[]{keylength, valuelength, valuelength}, true);
                }
            }
            
            if (dbe.equals("kelondro")) {
                File tablepath = new File(tablename).getParentFile();
                table = kelondroSplittedTree.open(tablepath, tablename, kelondroBase64Order.enhancedCoder,
                                buffer,
                                8,
                                new int[]{keylength, valuelength, valuelength}, 1, 80,
                                true);
            }
            if (dbe.equals("mysql")) {
                table = new dbTable("mysql");
            }
            
            if (dbe.equals("pgsql")) {
                table = new dbTable("pgsql");
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
                for (int i = 0; i < count; i++) {
                    table.put(new byte[][]{randomHash(random), dummyvalue1, dummyvalue2});
                    if (i % 500 == 0) {
                        System.out.println(i + " entries processed so far.");
                    }
                }
            }
            
            if (command.equals("list")) {
                Iterator i = table.rows(true, false, null);
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
            
            // finally close the database/table
            if (table instanceof kelondroTree) ((kelondroTree) table).close();
            if (table instanceof dbTable) ((dbTable)table).closeDatabaseConnection();
            
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
    private PreparedStatement sqlStatement;
    private int commandCount = 0;
    private int batchlimit = 1;
    
    public dbTable(String dbType) throws Exception {
        openDatabaseConnection(dbType);
    }
    
    private void openDatabaseConnection(String dbType) throws Exception{
        try {
            if (dbType == null) throw new IllegalArgumentException(); 
            
            String dbDriverStr = null, dbConnStr = null;            
            if (dbType.equalsIgnoreCase("mysql")) {
                dbDriverStr = db_driver_str_mysql;
                dbConnStr = db_conn_str_mysql;
            } else if (dbType.equalsIgnoreCase("pgsql")) {
                dbDriverStr = db_driver_str_pgsql;
                dbConnStr = db_conn_str_pgsql;
            }                
            
            Class.forName(dbDriverStr).newInstance();
            
            this.theDBConnection = DriverManager.getConnection (dbConnStr,this.db_usr_str,this.db_pwd_str);
            
            String sqlQuery = new String
            (
                    "INSERT INTO test (" +
                    "hash, " +
                    "value) " +
                    "VALUES (?,?)"
            );        
            
            this.sqlStatement = this.theDBConnection.prepareStatement(sqlQuery);     
            
        } catch (Exception e) {
            throw new Exception ("Unable to establish a database connection.");
        }
        
    }        
    
    public void closeDatabaseConnection() throws Exception {
        try {
            if (commandCount != 0) {
                sqlStatement.executeBatch();
            }
            
            sqlStatement.close();

            this.theDBConnection.close();
        } catch (Exception e) {
            throw new Exception ("Unable to close the database connection.");
        }
    }
    
    
    public byte[][] get(byte[] key) throws IOException {
        return null;
    }

    public byte[][] put(byte[][] row) throws IOException {
        try {
            this.sqlStatement.setString(1,new String(row[0]));
            sqlStatement.setBytes(2,row[1]);
            sqlStatement.addBatch();
            commandCount++;
            
            if (commandCount >= batchlimit) {
                sqlStatement.executeBatch();
                commandCount = 0;
            }
            
            return row;
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public byte[][] remove(byte[] key) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
    
    public Iterator rows(boolean up, boolean rotating, byte[] startKey) throws IOException {
        // Objects are of type byte[][]
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
