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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.order.CloneableIterator;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.index.Index;
import net.yacy.kelondro.index.Row;
import net.yacy.kelondro.index.Row.Entry;
import net.yacy.kelondro.index.RowCollection;


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
    
    @Override
    public void optimize() {
    }

    @Override
    public long mem() {
        return 0;
    }

    @Override
    public byte[] smallestKey() {
        return null;
    }

    @Override
    public byte[] largestKey() {
        return null;
    }

    @Override
    public String filename() {
        return "dbtest." + this.theDBConnection.hashCode();
    }

    @Override
    public synchronized void close() {
        if (this.theDBConnection != null) try {
            this.theDBConnection.close();
        } catch (final SQLException e) {
            ConcurrentLog.logException(e);
        }
        this.theDBConnection = null;
    }

    @Override
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
            ConcurrentLog.logException(e);
            return -1;
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Row row() {
        return this.rowdef;
    }

    @Override
    public boolean has(final byte[] key) {
        try {
            return (get(key, false) != null);
        } catch (final IOException e) {
            return false;
        }
    }

    @Override
    public ArrayList<RowCollection> removeDoubles() {
        return new ArrayList<RowCollection>();
    }

    @Override
    public Row.Entry get(final byte[] key, final boolean forcecopy) throws IOException {
        try {
            final String sqlQuery = "SELECT value from test where hash = ?";

            final PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery);
            sqlStatement.setString(1, ASCII.String(key));

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

    @Override
    public Map<byte[], Row.Entry> get(final Collection<byte[]> keys, final boolean forcecopy) throws IOException, InterruptedException {
        final Map<byte[], Row.Entry> map = new TreeMap<byte[], Row.Entry>(row().objectOrder);
        Row.Entry entry;
        for (final byte[] key: keys) {
            entry = get(key, forcecopy);
            if (entry != null) map.put(key, entry);
        }
        return map;
    }

    @Override
    public Row.Entry replace(final Row.Entry row) throws IOException {
        try {
            final Row.Entry oldEntry = remove(row.getPrimaryKeyBytes());
            final String sqlQuery = "INSERT INTO test (" +
                    "hash, " +
                    "value) " +
                    "VALUES (?,?)";

            final PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery);

            sqlStatement.setString(1, row.getPrimaryKeyASCII());
            sqlStatement.setBytes(2, row.bytes());
            sqlStatement.execute();

            sqlStatement.close();

            return oldEntry;
        } catch (final Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public boolean put(final Row.Entry row) throws IOException {
        try {
            final String sqlQuery = "INSERT INTO test (" +
                    "hash, " +
                    "value) " +
                    "VALUES (?,?)";

            final PreparedStatement sqlStatement = this.theDBConnection.prepareStatement(sqlQuery);

            sqlStatement.setString(1, row.getPrimaryKeyASCII());
            sqlStatement.setBytes(2, row.bytes());
            sqlStatement.execute();

            sqlStatement.close();

            return false;
        } catch (final Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    public synchronized void addUnique(final Row.Entry row) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Row.Entry remove(final byte[] key) throws IOException {
        PreparedStatement sqlStatement = null;
        try {

            final Row.Entry entry =  this.get(key, false);
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
				} catch (final SQLException e) {
				    ConcurrentLog.logException(e);
				}
        	}
        }
    }

    @Override
    public boolean delete(final byte[] key) throws IOException {
        return remove(key) != null;
    }

    @Override
    public Row.Entry removeOne() {
        return null;
    }

    @Override
    public List<Row.Entry> top(final int count) throws IOException {
        return null;
    }

    @Override
    public List<Row.Entry> random(final int count) throws IOException {
        return null;
    }

    @Override
    public CloneableIterator<Row.Entry> rows(final boolean up, final byte[] startKey) throws IOException {
        // Objects are of type kelondroRow.Entry
        return null;
    }

    @Override
    public Iterator<Entry> iterator() {
        try {
            return rows();
        } catch (final IOException e) {
            return null;
        }
    }

    @Override
    public CloneableIterator<Row.Entry> rows() throws IOException {
        return null;
    }

    @Override
    public CloneableIterator<byte[]> keys(final boolean up, final byte[] startKey) {
        // Objects are of type byte[]
        return null;
    }

    @Override
    public void clear() {
        // do nothing
    }

    @Override
    public void deleteOnExit() {
        // do nothing
    }
}
