/**
 *  AlternativeDomainNames
 * (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
 * first published 05.05.2008 on http://yacy.net
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

package net.yacy.server.http;

public interface AlternativeDomainNames {

    /**
     * for a given domain name, return a new address
     * the new address may also be a combination of a standard domain name or an IP with ':' and a port number
     * @param name: a domain name
     * @return an alternative name
     */
    public String resolve(String name);
    
    /**
     * while other servers may have alternative addresses, this server may also have an alternative
     * address
     * @return the alternative address of this server which other servers may can resolve
     */
    public String myAlternativeAddress();
    
    /**
     * return the IP as string of my server address
     * @return IP as string of this server
     */
    public String myIP();
    
    /**
     * return the port of my server address
     * @return port number of this server
     */
    public int myPort();
    
    /**
     * return a name of this server. this may be any string and there is no need that it must be unique
     * @return
     */
    public String myName();
    
    /**
     * return an unique ID of this server
     * @return
     */
    public String myID();
    
}
