/**
 *  HttpConnectionInfo.java
 *  First published 07.04.2008 by Daniel Raap; danielr@users.berlios.de under the GPL
 *  Copyright 2010 by Michael Peter Christen for LGPL
 *  Dual-Licensing for LGPL granted by Daniel Raap 07.08.2010 by email
 *  
 *  $LastChangedDate$
 *  $LastChangedRevision$
 *  $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.cora.protocol;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Information about a connection
 * 
 * @author daniel
 * @author sixcooler
 */
public class ConnectionInfo implements Comparable<ConnectionInfo> {
    /**
     * a list of all current connections to be shown in Connections_p
     */
    private final static Set<ConnectionInfo> allConnections = Collections
            .synchronizedSet(new HashSet<ConnectionInfo>());
    private final static Set<ConnectionInfo> serverConnections = Collections
            .synchronizedSet(new HashSet<ConnectionInfo>());
    // this is only for statistics, so it can be bigger to see lost connectionInfos
    private final static int staleAfterMillis = 30 * 60000; // 30 minutes
    
    private static int maxcount = 20;
    private static int serverMaxCount = 50;

    private final String protocol;
    private final String targetHost;
    private final String command;
    private final int id;
    private final long initTime;
    private final long upbytes;

    /**
     * constructor setting all data
     * 
     * @param protocol
     * @param targetHost
     * @param command
     * @param id
     * @param initTime
     */
    public ConnectionInfo(final String protocol, final String targetHost, final String command, final int id,
            final long initTime, final long upbytes) {
        this.protocol = protocol;
        this.targetHost = targetHost;
        this.command = command;
        this.id = id;
        this.initTime = initTime;
        this.upbytes = upbytes;
    }

    /**
     * @return
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * @return
     */
    public long getLifetime() {
        return System.currentTimeMillis() - initTime;
    }

    /**
     * @return
     */
    public long getUpbytes() {
        return upbytes;
    }

    /**
     * @return
     */
    public String getCommand() {
        return command;
    }

    /**
     * @return
     */
    public String getTargetHost() {
        return targetHost;
    }

    /**
     * @return
     */
    public int getID() {
        return id;
    }

    /**
     * gets a {@link Set} of all collected ConnectionInfos
     * 
     * Important: iterations must be synchronized!
     * 
     * @return the allConnections
     */
    public static Set<ConnectionInfo> getAllConnections() {
        return allConnections;
    }

    /**
     * gets a {@link Set} of all collected server ConnectionInfos
     * 
     * Important: iterations must be synchronized!
     * 
     * @return the allConnections
     */
    public static Set<ConnectionInfo> getServerConnections() {
        return serverConnections;
    }
    
    /**
     * gets the number of active client connections
     * 
     * @return count of active connections
     */
    public static int getCount() {
    	return getAllConnections().size();
    }
    
    /**
     * gets the number of active server connections
     * 
     * @return count of active connections
     */
    public static int getServerCount() {
    	return getServerConnections().size();
    }
    
    /**
     * gets the usage of the Client connection manager by active connections
     * 
     * @return load in percent
     */
    public static int getLoadPercent() {
    	return getCount() * 100 / getMaxcount();
    }
    
    /**
     * @return wether the server max connection-count is reached
     */
    public static boolean isServerCountReached() {
    	return getServerCount() >= getServerMaxcount();
    }
    
    /**
     * @return how many bytes queued up
     */
    public static long getActiveUpbytes() {
        long up = 0L;
        Iterator<ConnectionInfo> iter = getAllConnections().iterator();
        synchronized (iter) { 
            while (iter.hasNext()) {
                ConnectionInfo con = iter.next();
                up += con.getUpbytes();
            }
        }
        return up;
    }
    
    /**
     * gets the max connection count of the Client connection manager
     * 
     * @return max connections
     */
    public static int getMaxcount() {
    	return maxcount;
    }
    
    /**
     * gets the max connection count of the Client connection manager
     * to be used in statistics
     * 
     * @param max connections
     * @TODO Is it correct to only set if max > 0? What if maxcount is > 0 and max = 0 ?
     */
    public static void setMaxcount(final int max) {
    	if (max > 0) maxcount = max;
    }
    
    /**
     * gets the max connection count of the Server connection manager
     * 
     * @return max connections
     */
    public static int getServerMaxcount() {
    	return serverMaxCount;
    }
    
    /**
     * gets the max connection count of the Sever connection manager
     * to be used in statistics
     * 
     * @param max connections
     * @TODO Is it correct to only set if max > 0? What if maxcount is > 0 and max = 0 ?
     */
    public static void setServerMaxcount(final int max) {
    	if (max > 0) serverMaxCount = max;
    }

    /**
     * add a connection to the list of all current connections
     * 
     * @param conInfo
     */
    public static void addConnection(final ConnectionInfo conInfo) {
    	getAllConnections().add(conInfo);
    }

    /**
     * add a Server connection to the list of all current connections
     * 
     * @param conInfo
     */
    public static void addServerConnection(final ConnectionInfo conInfo) {
    	getServerConnections().add(conInfo);
    }

    /**
     * remove a connection from the list of all current connections
     * 
     * @param conInfo
     */
    protected static void removeConnection(final ConnectionInfo conInfo) {
    	getAllConnections().remove(conInfo);
    }

    /**
     * remove a Server connection from the list of all current connections
     * 
     * @param conInfo
     */
    public static void removeServerConnection(final ConnectionInfo conInfo) {
    	getServerConnections().remove(conInfo);
    }

    /**
     * connections with same id {@link equals()} another
     * 
     * @param id
     */
    public static void removeConnection(final int id) {
        removeConnection(new ConnectionInfo(null, null, null, id, 0, 0));
    }

    /**
     * connections with same id {@link equals()} another
     * 
     * @param id
     */
    public static void removeServerConnection(final int id) {
        removeServerConnection(new ConnectionInfo(null, null, null, id, 0, 0));
    }
    
    /**
     * removes stale connections
     */
    public static void cleanUp() {
    	cleanup(getAllConnections());
    	cleanup(getServerConnections());
    }
    
    private static void cleanup(final Set<ConnectionInfo> connectionSet) {
    	final Iterator<ConnectionInfo> iter = connectionSet.iterator();
    	synchronized (iter) { 
            while (iter.hasNext()) {
                ConnectionInfo con = null;
                try {
                    con = iter.next();
                } catch (Throwable e) {break;} // this must break because otherwise there is danger that the loop does never terminates
                try {
                    if (con.getLifetime() > staleAfterMillis) {
                        connectionSet.remove(con);
                    }
                } catch (Throwable e) {continue;}
            }
        }
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder string = new StringBuilder(50);
        string.append("ID ");
        string.append(getID());
        string.append(", ");
        string.append(getProtocol());
        string.append("://");
        string.append(getTargetHost());
        string.append(" ");
        string.append(getCommand());
        string.append(", since ");
        string.append(getLifetime());
        string.append(" ms");
        return string.toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)  return true;
        if (obj == null)  return false;
        if (getClass() != obj.getClass()) return false;
        final ConnectionInfo other = (ConnectionInfo) obj;
        return this.id == other.id;
    }

    @Override
    public int compareTo(ConnectionInfo o) {
        if(o==null) throw new NullPointerException("ConnectionInfo: compare() : passed argument is null \n");
        if(this.initTime>o.initTime) return 1;
        else if(this.initTime<o.initTime) return -1;
        else return 0;
    }
}
