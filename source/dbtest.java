// test application
// shall be used to compare the kelondroDB with other databases
// with relevance to yacy-specific use cases

import java.io.File;
import java.io.IOException;
import java.util.Random;

import de.anomic.server.serverCodings;
import de.anomic.kelondro.kelondroIndex;
import de.anomic.kelondro.kelondroTree;

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
        String dbe = args[0];       // the database engine
        String command = args[1];   // test command
        String tablename = args[2]; // name of test-table
        
        long startup = System.currentTimeMillis();
        try {
            kelondroIndex table = null;
            
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
                //...
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
                }
            }
            
            long aftercommand = System.currentTimeMillis();
            
            // finally close the database/table
            if (table instanceof kelondroTree) ((kelondroTree) table).close();
            
            long afterclose = System.currentTimeMillis();
            
            System.out.println("Execution time: open=" + (afterinit - startup) + ", command=" + (aftercommand - afterinit) + ", close=" + (afterclose - aftercommand) + ", total=" + (afterclose - startup));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
