// test application
// shall be used to compare the kelondroDB with other databases
// with relevance to yacy-specific use cases

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;
import java.util.Date;

import de.anomic.server.serverCodings;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroTree;
import de.anomic.tools.ImageChart;
import de.anomic.server.serverMemory;

public class dbtest {

    public final static int keylength = 12;
    public final static int valuelength = 446; // sum of all data length as defined in plasmaURL
    public final static long buffer = 8192 * 1024; // 8 MB buffer
    public static byte[] dummyvalue = new byte[valuelength];
    static {
        // fill the dummy value
        for (int i = 0; i < valuelength; i++) dummyvalue[i] = '.';
    }
    
    public static byte[] randomHash(Random r) {
        // a long can have 64 bit, but a 12-byte hash can have 6 * 12 = 72 bits
        // so we construct a generic Hash using two long values
        return (serverCodings.enhancedCoder.encodeBase64Long(Math.abs(r.nextLong()), 11).substring(5) +
                serverCodings.enhancedCoder.encodeBase64Long(Math.abs(r.nextLong()), 11).substring(5)).getBytes();
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
            if (dbe.equals("kelondro")) {
                File tablefile = new File(tablename + ".kelondro.db");
                if (tablefile.exists()) {
                    table = new kelondroTree(tablefile, buffer);
                } else {
                    table = new kelondroTree(tablefile, buffer, keylength, valuelength);
                }
            }
            
            if (dbe.equals("mysql")) {
                table = new dbTable("mysql");
            }
            
            if (dbe.equals("pgsql")) {
                table = new dbTable("pgsql");
            }
            
            long afterinit = System.currentTimeMillis();
            
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
                    table.put(new byte[][]{randomHash(random), dummyvalue});
                    if (i % 500 == 0) {
                        System.out.println(i + " entries processed so far.");
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
    
}

final class memprofiler extends Thread {
    
    ImageChart memChart;
    boolean run;
    File outputFile;
    long start;
    
    public memprofiler(int width, int height, int expectedTimeSeconds, File outputFile) {
        this.outputFile = outputFile;
        int expectedKilobytes = (int) 20 * 1024;//(Runtime.getRuntime().totalMemory() / 1024);
        memChart = new ImageChart(width, height, "000010", 50, 20, 20, 20, "MEMORY CHART FROM EXECUTION AT " + new Date());
        int timescale = 10; // steps with each 10 seconds
        int memscale = 1024;
        memChart.declareDimension(ImageChart.DIMENSION_BOTTOM, timescale, (width - 40) * timescale / expectedTimeSeconds, "FFFFFF", "555555", "SECONDS");
        memChart.declareDimension(ImageChart.DIMENSION_LEFT, memscale, (height - 40) * memscale / expectedKilobytes , "FFFFFF", "555555", "KILOBYTES");
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
            memChart.chartLine(ImageChart.DIMENSION_BOTTOM, ImageChart.DIMENSION_LEFT, seconds0, kilobytes0, seconds1, kilobytes1);
            seconds0 = seconds1;
            kilobytes0 = kilobytes1;
            try {Thread.sleep(100);} catch (InterruptedException e) {}
        }
        try {
            memChart.toPNG(true, outputFile);
        } catch (IOException e) {}
    }
    
    public void terminate() {
        run = false;
        while (this.isAlive()) {
            try {Thread.sleep(1000);} catch (InterruptedException e) {}
        }
    }
}