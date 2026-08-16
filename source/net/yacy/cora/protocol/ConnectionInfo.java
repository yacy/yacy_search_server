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
    /* Stale cleanup is a safety net; normal request and client lifecycles remove their own entries. */
    private static final long CLIENT_STALE_AFTER_MILLIS = 30L * 60L * 1000L;
    private static final long SERVER_STALE_AFTER_MILLIS = 30L * 60L * 1000L;
    
    private static int maxcount = 20;
    private static volatile int serverMaxCount = 50;

    private final String protocol;
    private final String targetHost;
    private final String command;
    private final long id;
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
    public ConnectionInfo(final String protocol, final String targetHost, final String command, final long id,
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
    public long getID() {
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
     * gets the number of active incoming server requests
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
     * @return whether the incoming server request limit is reached
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
     * gets the maximum active incoming server request count
     * 
     * @return max connections
     */
    public static int getServerMaxcount() {
    	return serverMaxCount;
    }
    
    /**
     * sets the maximum active incoming server request count
     * 
     * @param max connections
     * @TODO Is it correct to only set if max > 0? What if maxcount is > 0 and max = 0 ?
     */
    public static void setServerMaxcount(final int max) {
        if (max > 0) {
            synchronized (serverConnections) {
                serverMaxCount = max;
            }
        }
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
     * add an incoming server request to the active request list
     * 
     * @param conInfo
     */
    public static void addServerConnection(final ConnectionInfo conInfo) {
        getServerConnections().add(conInfo);
    }

    /**
     * Add an incoming server request when capacity is available. The capacity check
     * and insertion are performed while holding the same lock, so concurrent
     * callers cannot exceed the configured limit.
     *
     * @param conInfo the connection to track
     * @param limitExempt when true, add the connection regardless of the limit
     * @return true when the connection was added, false when it was rejected
     */
    public static boolean tryAddServerConnection(final ConnectionInfo conInfo, final boolean limitExempt) {
        synchronized (serverConnections) {
            if (!limitExempt && serverConnections.size() >= serverMaxCount) {
                return false;
            }
            return serverConnections.add(conInfo);
        }
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
     * remove an incoming server request from the active request list
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
    public static void removeConnection(final long id) {
        removeConnection(new ConnectionInfo(null, null, null, id, 0, 0));
    }

    /**
     * Remove stale client and server entries. Prefer the pool-specific cleanup
     * methods when the caller treats the two pools independently.
     */
    public static void cleanUp() {
        cleanUpClientConnections();
        cleanUpServerConnections();
    }

    /**
     * Remove client connection entries that outlived the stale threshold.
     *
     * @return the number of removed entries
     */
    public static int cleanUpClientConnections() {
        return cleanup(allConnections, CLIENT_STALE_AFTER_MILLIS);
    }

    /**
     * Remove incoming request entries that outlived the stale threshold.
     *
     * @return the number of removed entries
     */
    public static int cleanUpServerConnections() {
        return cleanup(serverConnections, SERVER_STALE_AFTER_MILLIS);
    }

    private static int cleanup(final Set<ConnectionInfo> connectionSet, final long staleAfterMillis) {
        int removed = 0;
        synchronized (connectionSet) {
            final Iterator<ConnectionInfo> iter = connectionSet.iterator();
            while (iter.hasNext()) {
                final ConnectionInfo con = iter.next();
                if (con.getLifetime() > staleAfterMillis) {
                    iter.remove();
                    removed++;
                }
            }
        }
        return removed;
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
        return Long.hashCode(this.id);
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
