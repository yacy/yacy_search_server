// SQLTable.java
// this class was written by Martin Thelian
// (the class was once a sub-class of dbtest.java)
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.kelondro.table;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.yacy.cora.document.UTF8;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.RowCollection;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.order.ByteOrder;
import net.yacy.kelondro.order.CloneableIterator;
import net.yacy.kelondro.order.NaturalOrder;


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

public class SQLTable implements Index, Iterable<Row.Entry> {

    private static final String db_driver_str_mysql = "org.gjt.mm.mysql.Driver";
    private static final String db_driver_str_pgsql = "org.postgresql.Driver";
    
    private static final String db_conn_str_mysql    = "jdbc:mysql://192.168.0.2:3306/yacy";
    private static final String db_conn_str_pgsql   = "jdbc:postgresql://192.168.0.2:5432";
    
    private static final String db_usr_str    = "yacy";
    private static final String db_pwd_str    = "yacy";
    
    private Connection theDBConnection = null;
    private static final ByteOrder order = new NaturalOrder(true);
    private final Row rowdef;
    
    public SQLTable(final String dbType, final Row rowdef) throws Exception {
        this.rowdef = rowdef;
        openDatabaseConnection(dbType);
    }
    
    private void openDatabaseConnection(final String dbType) throws Exception{

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
        } catch (final Exception e) {
            throw new Exception ("Unable to load the jdbc driver: " + e.getMessage(),e);
        }
        try {
            this.theDBConnection = DriverManager.getConnection (dbConnStr, db_usr_str, db_pwd_str);
        } catch (final Exception e) {
            throw new Exception ("Unable to establish a database connection: " + e.getMessage(),e);
        }
        
    }
    
    public long mem() {
        return 0;
    }
    
    public byte[] smallestKey() {
        return null;
    }
    
    public byte[] largestKey() {
        return null;
    }

    public String filename() {
        return "dbtest." + theDBConnection.hashCode();
    }
    
    public void close() {
        if (this.theDBConnection != null) try {
            this.theDBConnection.close();
        } catch (final SQLException e) {
            Log.logException(e);
        }
        this.theDBConnection = null;
    }
    
    public int size() {
        int size = -1;
        try {
            final String sqlQuery = "SELECT count(value) from test";
            
            final PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery); 
            final ResultSet result = sqlStatement.executeQuery();
            
            while (result.next()) {
                size = result.getInt(1);
            }  
            
            result.close();
            sqlStatement.close();
            
            return size;
        } catch (final Exception e) {
            Log.logException(e);
            return -1;
        }
    }
    
    public boolean isEmpty() {
        return size() == 0;
    }
    
    public Row row() {
        return this.rowdef;
    }
    
    public boolean has(final byte[] key) {
        try {
            return (get(key) != null);
        } catch (final IOException e) {
            return false;
        }
    }
    
    public ArrayList<RowCollection> removeDoubles() {
        return new ArrayList<RowCollection>();
    }
    
    public Row.Entry get(final byte[] key) throws IOException {
        try {
            final String sqlQuery = "SELECT value from test where hash = ?";
            
            final PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery); 
            sqlStatement.setString(1, UTF8.String(key));
            
            byte[] value = null;
            final ResultSet result = sqlStatement.executeQuery();
            while (result.next()) {
                value = result.getBytes("value");
            }  
            
            result.close();
            sqlStatement.close();
            
            if (value == null) return null;
            final Row.Entry entry = this.rowdef.newEntry(value);
            return entry;
        } catch (final Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public Map<byte[], Row.Entry> get(Collection<byte[]> keys) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<byte[], Row.Entry>(this.row().objectOrder);
        Row.Entry entry;
        for (byte[] key: keys) {
            entry = get(key);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }

    public Row.Entry replace(final Row.Entry row) throws IOException {
        try {
            final Row.Entry oldEntry = remove(row.getPrimaryKeyBytes());
            final String sqlQuery = "INSERT INTO test (" +
                    "hash, " +
                    "value) " +
                    "VALUES (?,?)";
            
            final PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery);     
            
            sqlStatement.setString(1, row.getColString(0));
            sqlStatement.setBytes(2, row.bytes());
            sqlStatement.execute();
            
            sqlStatement.close();
            
            return oldEntry;
        } catch (final Exception e) {
            throw new IOException(e.getMessage());
        }
    }
    
    public boolean put(final Row.Entry row) throws IOException {
        try {
            final String sqlQuery = "INSERT INTO test (" +
                    "hash, " +
                    "value) " +
                    "VALUES (?,?)";                
            
            final PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery);     
            
            sqlStatement.setString(1, row.getColString(0));
            sqlStatement.setBytes(2, row.bytes());
            sqlStatement.execute();
            
            sqlStatement.close();
            
            return false;
        } catch (final Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public synchronized void addUnique(final Row.Entry row) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    public synchronized void addUnique(final Row.Entry row, final Date entryDate) {
        throw new UnsupportedOperationException();
    }
    
    public synchronized void addUnique(final List<Row.Entry> rows) {
        throw new UnsupportedOperationException();
    }
    
    public Row.Entry remove(final byte[] key) throws IOException {
        PreparedStatement sqlStatement = null;
        try {
            
            final Row.Entry entry =  this.get(key);
            if (entry == null) return null;
            
            final String sqlQuery = "DELETE FROM test WHERE hash = ?";                
            
            
			sqlStatement = this.theDBConnection.prepareStatement(sqlQuery);                 
            sqlStatement.setString(1, UTF8.String(key));
            sqlStatement.execute();
            
            return entry;
        } catch (final Exception e) {
            throw new IOException(e.getMessage());
        } finally {
        	if(sqlStatement != null) {
        		try {
					sqlStatement.close();
				} catch (SQLException e) {
				    Log.logException(e);
				}
        	}
        }
    }
    
    public boolean delete(final byte[] key) throws IOException {
        return remove(key) != null;
    }
    
    public Row.Entry removeOne() {
        return null;
    }
    
    public List<Row.Entry> top(int count) throws IOException {
        return null;
    }
    
    public CloneableIterator<Row.Entry> rows(final boolean up, final byte[] startKey) throws IOException {
        // Objects are of type kelondroRow.Entry
        return null;
    }
    
    public Iterator<Entry> iterator() {
        try {
            return rows();
        } catch (IOException e) {
            return null;
        }
    }

    public CloneableIterator<Row.Entry> rows() throws IOException {
        return null;
    }

    public CloneableIterator<byte[]> keys(final boolean up, final byte[] startKey) {
        // Objects are of type byte[]
        return null;
    }

    public int columns() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int columnSize(final int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    public ByteOrder order() {
        return order;
    }
    
    public int primarykey() {
        return 0;
    }
    
    public final int cacheObjectChunkSize() {
        // dummy method
        return -1;
    }
    
    public long[] cacheObjectStatus() {
        // dummy method
        return null;
    }
    
    public final int cacheNodeChunkSize() {
        return -1;
    }
    
    public final int[] cacheNodeStatus() {
        return new int[]{0,0,0,0,0,0,0,0,0,0};
    }

    public void clear() {
        // do nothing
    }

    public void deleteOnExit() {
        // do nothing
    }
}
