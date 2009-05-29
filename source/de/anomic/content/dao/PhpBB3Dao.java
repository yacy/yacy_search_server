// PhpBB3Dao.java
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 26.05.2009 on http://yacy.net
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

package de.anomic.content.dao;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import de.anomic.content.DCEntry;
import de.anomic.yacy.yacyURL;

public class PhpBB3Dao implements Dao {

    private Connection conn = null;
    private String urlstub, prefix;
    private HashMap<Integer, String> users;

    public PhpBB3Dao(
            String urlstub,
            String dbType,
            String host,
            int port,
            String dbname,
            String prefix,
            String user,
            String pw) throws Exception  {
        this.conn = getConnection(dbType, host, port, dbname, user, pw);
        this.urlstub = urlstub;
        this.prefix = prefix;
        this.users = new HashMap<Integer, String>();
    }
    
    protected void finalize() throws Throwable {
        closeConnection();
    }
    
    private Connection getConnection(final String dbType, String host, int port, String dbname, String user, String pw) throws Exception {
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
            throw new Exception("Unable to load the jdbc driver: " + e.getMessage(),e);
        }
        
        try {
            return DriverManager.getConnection(dbConnStr, user, pw);
        } catch (final Exception e) {
            throw new Exception("Unable to establish a database connection: " + e.getMessage(),e);
        }
    }
    
    public void closeConnection() {
        if (conn != null) { 
            try {
                conn.close();
                conn = null;
            } catch (SQLException e) {
                System.out.println("PhpBB3Dao: " + e);
            }
        }
    }
    
    public Date first() {
        StringBuilder sql = new StringBuilder(256);
        sql.append("select min(post_time) from " + prefix + "posts");
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql.toString());
            if (rs.next()) {
                return new Date(rs.getLong(1) * 1000L);
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (rs != null) try {rs.close();} catch (SQLException e) {}
            if (stmt != null) try {stmt.close();} catch (SQLException e) {}
        }
    }

    public Date latest() {
        StringBuilder sql = new StringBuilder(256);
        sql.append("select max(post_time) from " + prefix + "posts");
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql.toString());
            if (rs.next()) {
                return new Date(rs.getLong(1) * 1000L);
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (rs != null) try {rs.close();} catch (SQLException e) {}
            if (stmt != null) try {stmt.close();} catch (SQLException e) {}
        }
    }

    public int size() {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery("select count(*) from " + prefix + "posts");
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        } finally {
            if (rs != null) try {rs.close();} catch (SQLException e) {}
            if (stmt != null) try {stmt.close();} catch (SQLException e) {}
        }
    }

    public DCEntry get(int item) {
        StringBuilder sql = new StringBuilder(256);
        sql.append("select * from " + prefix + "posts where post_id = ");
        sql.append(item);
        return getOne(sql);
    }
    
    public BlockingQueue<DCEntry> query(int from, int until, int queueSize) {
        // define the sql query
        final StringBuilder sql = new StringBuilder(256);
        sql.append("select * from " + prefix + "posts where post_id >= ");
        sql.append(from);
        if (until > from) {
            sql.append(" and post_id < ");
            sql.append(until);
        }
        sql.append(" order by post_id");

        // execute the query and push entries to a queue concurrently
        return toQueue(sql, queueSize);
    }
    
    public BlockingQueue<DCEntry> query(Date from, int queueSize) {
     // define the sql query
        final StringBuilder sql = new StringBuilder(256);
        sql.append("select * from " + prefix + "posts where post_time >= ");
        sql.append(from.getTime() / 1000);
        sql.append(" order by post_id");

        // execute the query and push entries to a queue concurrently
        return toQueue(sql, queueSize);
    }
    
    
    private DCEntry getOne(StringBuilder sql) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql.toString());
            if (rs.next()) {
                try {
                    return parseResultSet(rs);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (rs != null) try {rs.close();} catch (SQLException e) {}
            if (stmt != null) try {stmt.close();} catch (SQLException e) {}
        }
    }
    
    private BlockingQueue<DCEntry> toQueue(final StringBuilder sql, int queueSize) {
        // execute the query and push entries to a queue concurrently
        final BlockingQueue<DCEntry> queue = new ArrayBlockingQueue<DCEntry>(queueSize);
        Thread dbreader = new Thread() {
            public void run() {
                Statement stmt = null;
                ResultSet rs = null;
                try {
                    stmt = conn.createStatement();
                    rs = stmt.executeQuery(sql.toString());
                    while (rs.next()) {
                        try {
                            queue.put(parseResultSet(rs));
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                    queue.put(DCEntry.poison);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (SQLException e) {
                    e.printStackTrace();
                } finally {
                    if (rs != null) try {rs.close();} catch (SQLException e) {}
                    if (stmt != null) try {stmt.close();} catch (SQLException e) {}
                }
            }
        };
        dbreader.start();
        return queue;
    }
    
    private DCEntry parseResultSet(ResultSet rs) throws SQLException, MalformedURLException {
        yacyURL url;
        int item = rs.getInt("post_id");
        url = new yacyURL(this.urlstub + "/viewtopic.php?t=" + item);
        String subject = rs.getString("post_subject");
        String text = xmlCleaner(rs.getString("post_text"));
        String user = getUser(rs.getInt("poster_id"));
        Date date = new Date(rs.getLong("post_time") * 1000L);
        return new DCEntry(url, date, subject, user, text);
    }
    
    public static String xmlCleaner(String s) {
        if (s == null) return null;

        StringBuilder sbOutput = new StringBuilder(s.length());
        char c;

        for (int i = 0; i < s.length(); i++ ) {
                c = s.charAt(i);
                if ((c >= 0x0020 && c <= 0xD7FF) || 
                    (c >= 0xE000 && c <= 0xFFFD) ||
                     c == 0x0009 ||
                     c == 0x000A || 
                     c == 0x000D ) {
                    sbOutput.append(c);
                }
        }
        return sbOutput.toString().trim();
    }

    private String getUser(int poster_id) {
        String nick = this.users.get(poster_id);
        if (nick != null) return nick;
        
        StringBuilder sql = new StringBuilder(256);
        sql.append("select * from " + prefix + "users where user_id = ");
        sql.append(poster_id);
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql.toString());
            if (rs.next()) nick = rs.getString("username");
            if (nick == null) nick = "";
            this.users.put(poster_id, nick);
            return nick;
        } catch (SQLException e) {
            e.printStackTrace();
            return "";
        } finally {
            if (rs != null) try {rs.close();} catch (SQLException e) {}
            if (stmt != null) try {stmt.close();} catch (SQLException e) {}
        }
    }
    
    public int writeSurrogates(
        BlockingQueue<DCEntry> queue,
        File targetdir,
        String versioninfo,
        int maxEntriesInFile
    ) {
        try {
            // generate output file name and attributes
            String targethost = new yacyURL(this.urlstub, null).getHost();
            int fc = 0;
            File outputfiletmp = null, outputfile = null;
            
            // write the result from the query concurrently in a file
            OutputStreamWriter osw = null;
            DCEntry e;
            int c = 0;
            while ((e = queue.take()) != DCEntry.poison) {
                if (osw == null) {
                    outputfiletmp = new File(targetdir, targethost + "." + versioninfo + "." + fc + ".xml.tmp");
                    outputfile = new File(targetdir, targethost + "." + versioninfo + "." + fc + ".xml");
                    if (outputfiletmp.exists()) outputfiletmp.delete();
                    if (outputfile.exists()) outputfile.delete();
                    osw = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(outputfiletmp)), "UTF-8");
                    osw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<surrogates xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n");
                }
                e.writeXML(osw);
                c++;
                if (c >= maxEntriesInFile) {
                    osw.write("</surrogates>\n");
                    osw.close();
                    outputfiletmp.renameTo(outputfile);
                    osw = null;
                    c = 0;
                    fc++;
                }
            }
            osw.write("</surrogates>\n");
            osw.close();
            outputfiletmp.renameTo(outputfile);
            return fc + 1;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    public void close() {
        try {
            this.conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        PhpBB3Dao db;
        try {
            db = new PhpBB3Dao(
                "http://forum.yacy-websuche.de",
                "mysql",
                "localhost",
                3306,
                "forum",
                "forum_",
                "root",
                ""
                );
            System.out.println("Posts in database : " + db.size());
            System.out.println("First entry       : " + db.first());
            System.out.println("Last entry        : " + db.latest());
            File targetdir = new File("x").getParentFile();
            db.writeSurrogates(db.query(0, -1, 100), targetdir, "id0-current", 3000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
