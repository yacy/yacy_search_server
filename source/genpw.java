import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import de.anomic.kelondro.kelondroBase64Order;
import de.anomic.server.serverCodings;

// migration.java
// -----------------------
// (C) by Alexander Schier
//
// last change: $LastChangedDate: $ by $LastChangedBy: $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


public class genpw {
	public static void main(String[] args){
        String username="";
        String password="";
        if(args.length==2){
            username=args[0];
            password=args[1];
        }else if(args.length<2){
            if(args.length==1){
                username=args[0];
            }else{
                username="admin";
            }
            if(args.length<1){
                BufferedReader br=new BufferedReader(new InputStreamReader(System.in));
                username="admin";
                try {
                    password=br.readLine();
                } catch (IOException e) {
                    System.err.println("IOException while reading from stdin");
                    System.exit(1);
                }
            }
        }
        
		System.out.println(serverCodings.encodeMD5Hex(kelondroBase64Order.standardCoder.encodeString(username+":"+password)));
	}
}
