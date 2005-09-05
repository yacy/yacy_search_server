// robotsParser.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// This file ist contributed by Alexander Schier
// last major change: 05.09.2005
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

import java.lang.String;
import java.util.Vector;
import java.util.Iterator;
import java.io.IOException;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URL;

/*
 * A class for Parsing robots.txt files.
 * It only parses the Deny Part, yet.
 * TODO: Allow, Do not deny if User-Agent: != yacy
 *
 * Use:
 * robotsParser rp=new Robotsparser(robotsfile);
 * if(rp.isAllowedRobots("/test")){
 *   System.out.println("/test is allowed");
 * }
 */
public class robotsParser{
	String robotstxt="";
	Vector deny;
	/*
	 * constructor with a robots.txt as string
	 */
	public robotsParser(String robots){
		robotstxt=robots;
		parse();
	}
	/*
	 * constructor with a robots.txt as Filehandle
	 */
	public robotsParser(File robotsFile){
		String robots="";
		String line="";
		try{
			BufferedReader br=new BufferedReader(new FileReader(robotsFile));
			while( (line=br.readLine()) != null){
				robots+=line+"\n";
			}
		}catch(IOException e){
			System.out.println("File Error");
		}
		robotstxt=robots;
		parse();
	}
	/*public robotsParser(URL robotsUrl){
	}*/
	/*
	 * this parses the robots.txt.
	 * at the Moment it only creates a list of Deny Paths
	 */
	private void parse(){
		deny=new Vector();
		String[] lines = robotstxt.split("\n");
		String line;
		for(int i=0;i<lines.length;i++){
			line=lines[i];
			if(line.startsWith("Disallow:")){
				line=line.substring(9);
				if(line.startsWith(" ")){
					line=line.substring(1);
				}
				deny.add(line);
			}
		}
	}
	/*
	 * Check if a url is allowed.
	 */
	public boolean isAllowedRobots(String url){
		boolean allowed=false;
		if(deny !=null){
			Iterator it=deny.iterator();
			allowed=true;
			while(it.hasNext()){
				if(url.indexOf((String)it.next()) >= 0){
					allowed=false;
					break;
				}
			}
		}
		return allowed;
	}
	
	/*
	 * Test the class with a file robots.txt in the workdir and a testpath as argument.
	 */
	public static void main(String[] args){
		robotsParser rp=new robotsParser(new File("robots.txt"));
		for(int i=0;i<args.length;i++){
			if(rp.isAllowedRobots(args[i])){
				System.out.println(args[i]+" is allowed.");
			}else{
				System.out.println(args[i]+" is NOT allowed.");
			}
		}
	}
}
