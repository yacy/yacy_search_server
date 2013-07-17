// DatabaseConnection.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 11.06.2009 on http://yacy.net
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

package net.yacy.document.content.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import net.yacy.cora.util.ConcurrentLog;

public class DatabaseConnection {

	private Connection connection;
	
    public DatabaseConnection(final String dbType, String host, int port, String dbname, String user, String pw) throws SQLException {
        String dbDriverStr = null, dbConnStr = null;            
        if (dbType.equalsIgnoreCase("mysql")) {
            dbDriverStr = "com.mysql.jdbc.Driver";
            dbConnStr = "jdbc:mysql://" + host + ":" + port + "/" + dbname;
        } else if (dbType.equalsIgnoreCase("pgsql")) {
            dbDriverStr = "org.postgresql.Driver";
            dbConnStr = "jdbc:postgresql://" + host + ":" + port + "/" + dbname;
        } else throw new IllegalArgumentException();
        
        try {            
            Class.forName(dbDriverStr).newInstance();
        } catch (final Exception e) {
            throw new SQLException("Unable to load the jdbc driver: " + e.getMessage());
        }
        
        try {
            this.connection =  DriverManager.getConnection(dbConnStr, user, pw);
        } catch (final Exception e) {
            throw new SQLException("Unable to establish a database connection: " + e.getMessage());
        }
    }
    
    public void setAutoCommit(boolean b) {
    	try {
			this.connection.setAutoCommit(b);
		} catch (final SQLException e) {
		    ConcurrentLog.logException(e);
		}
    }
    
    public int count(String tablename) throws SQLException {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = this.connection.createStatement();
            rs = stmt.executeQuery("select count(*) from " + tablename);
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (final SQLException e) {
            throw e;
        } finally {
            if (rs != null) try {rs.close();} catch (final SQLException e) {}
            if (stmt != null) try {stmt.close();} catch (final SQLException e) {}
        }
    }
    
    public synchronized void close() {
        if (connection != null) { 
            try {
            	connection.close();
            	connection = null;
            } catch (final SQLException e) {
            }
        }
    }
    
    public Statement statement() throws SQLException {
    	return this.connection.createStatement();
    }
}
