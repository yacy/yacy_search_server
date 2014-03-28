// PhpBB3Dao.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 26.05.2009 on http://yacy.net
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;

import net.yacy.cora.document.encoding.UTF8;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.kelondro.util.FileUtils;


public class ImportDump {

    private final DatabaseConnection conn;

    public ImportDump(
            String dbType,
            String host,
            int port,
            String dbname,
            String user,
            String pw) throws Exception  {
        this.conn = new DatabaseConnection(dbType, host, port, dbname, user, pw);
        this.conn.setAutoCommit(true);
    }
    
    public void imp(File dump) throws SQLException {
    	Statement statement = null;
    	//int maxBatch = 1048576;
        try {
        	statement = conn.statement();
        	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        	FileUtils.copy(dump, baos);
        	
        	String s = UTF8.String(baos.toByteArray());
        	int p, q;
        	String t;
        	loop: while (!s.isEmpty()) {
        		p = s.indexOf("INSERT INTO", 1);
        		q = s.indexOf("CREATE TABLE", 1);
        		if (q >= 0 && q < p) p = q;
        		if (p < 0) {
        			// finalize process
        			statement.executeBatch();
        			System.out.println(s);
        			statement.addBatch(s);
                    statement.executeBatch();
                    break loop;
        		}
        		t = s.substring(0, p);
        		s = s.substring(p);
        		//if (batchSize + t.length() >= maxBatch) {
        			statement.executeBatch();
        		//}
    			System.out.println(t);
        		statement.addBatch(t);
        	}
        	statement.executeBatch();
        } catch (final SQLException e) {
            ConcurrentLog.logException(e);
            throw e;
        } catch (final IOException e) {
            ConcurrentLog.logException(e);
            throw new SQLException(e.getMessage());
		} finally {
            if (statement != null) try {statement.close();} catch (final SQLException e) {}
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
    
    public synchronized void close() {
        this.conn.close();
    }
    
    
}
