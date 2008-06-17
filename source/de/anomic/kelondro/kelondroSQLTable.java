// kelondroSQLTable.java
// this class was written by Martin Thelian
// (the class was once a sub-class of dbtest.java)
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
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

package de.anomic.kelondro;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

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

public class kelondroSQLTable implements kelondroIndex {

    private final String db_driver_str_mysql = "org.gjt.mm.mysql.Driver";
    private final String db_driver_str_pgsql = "org.postgresql.Driver";
    
    private final String db_conn_str_mysql    = "jdbc:mysql://192.168.0.2:3306/yacy";
    private final String db_conn_str_pgsql   = "jdbc:postgresql://192.168.0.2:5432";
    
    private final String db_usr_str    = "yacy";
    private final String db_pwd_str    = "yacy";
    
    private Connection theDBConnection = null;
    private final kelondroByteOrder order = new kelondroNaturalOrder(true);
    private kelondroRow rowdef;
    
    public kelondroSQLTable(String dbType, kelondroRow rowdef) throws Exception {
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
    
    public String filename() {
        return "dbtest." + theDBConnection.hashCode();
    }
    
    public void close() {
        if (this.theDBConnection != null) try {
            this.theDBConnection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        this.theDBConnection = null;
    }
    
    public int size() {
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
            e.printStackTrace();
            return -1;
        }
    }
    
    public kelondroRow row() {
        return this.rowdef;
    }
    
    public boolean has(byte[] key) {
        try {
            return (get(key) != null);
        } catch (IOException e) {
            return false;
        }
    }
    
    public ArrayList<kelondroRowCollection> removeDoubles() {
        return new ArrayList<kelondroRowCollection>();
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

    public synchronized void putMultiple(List<kelondroRow.Entry> rows) throws IOException {
        Iterator<kelondroRow.Entry> i = rows.iterator();
        while (i.hasNext()) put(i.next());
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry row, Date entryDate) throws IOException {
        return put(row);
    }
    
    public kelondroRow.Entry put(kelondroRow.Entry row) throws IOException {
        try {
            
            kelondroRow.Entry oldEntry = remove(row.getColBytes(0), false);            
            
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

    public synchronized boolean addUnique(kelondroRow.Entry row) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    public synchronized void addUnique(kelondroRow.Entry row, Date entryDate) {
        throw new UnsupportedOperationException();
    }
    
    public synchronized int addUniqueMultiple(List<kelondroRow.Entry> rows) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    public kelondroRow.Entry remove(byte[] key, boolean keepOrder) throws IOException {
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
    
    public kelondroCloneableIterator<kelondroRow.Entry> rows(boolean up, byte[] startKey) throws IOException {
        // Objects are of type kelondroRow.Entry
        return null;
    }

    public kelondroCloneableIterator<byte[]> keys(boolean up, byte[] startKey) {
        // Objects are of type byte[]
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

    public kelondroByteOrder order() {
        return this.order;
    }
    
    public int primarykey() {
        return 0;
    }
    
    public kelondroProfile profile() {
        return new kelondroProfile();
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
        // TODO Auto-generated method stub
        
    }
}
