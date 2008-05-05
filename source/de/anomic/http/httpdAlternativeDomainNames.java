// httpdAlternativeDomainNames.java
// (C) 2008 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 05.05.2008 on http://yacy.net
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

package de.anomic.http;

public interface httpdAlternativeDomainNames {

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
