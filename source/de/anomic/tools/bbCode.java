// unHTML.java 
// ---------------------------
// part of YaCy
// (C) by Marc Nause; marc.nause@gmx.de.
// Braunschweig, Germany, 2005
// last major change: 27.06.2005
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

/**Some code to avoid people being able to mess with the message system
  *by using HTML.
  *@author Marc Nause
  extended for bbCode by Alexander Schier
  */
  
package de.anomic.tools;  
  
public class bbCode {
	
	String text;
	/**init - no Code yet*/
	public bbCode()
	{
	}

	/**Replaces all < and > with &lt; and &gt; in a string.
	*@author Marc Nause
	*@return String
	*/
	public String escapeHtml(String input){
		String output = "";
		int iter = 0;
		
		while(iter < input.length()){
			String temp = input.substring(iter,iter+1);
			iter++;
			if(temp.equals("<")) {temp = "&lt;";}
			else if (temp.equals(">")) {temp = "&gt;";}
			output = output + temp;
		}
	
	return output;
	}
	
	/**Parses parts of bbCode (TODO: [Img],[URL],[List],[List=]).
	*@author Alexander Schier
	*@author Roland Ramthun
	*@return String
	*/
	public String bb(String input){
		String output = escapeHtml(input);
		//Parse bold
		output = output.replaceAll("\\[b\\]", "<b>");
		output = output.replaceAll("\\[/b\\]", "</b>");
		//Parse italic
		output = output.replaceAll("\\[i\\]", "<i>");
		output = output.replaceAll("\\[/i\\]", "</i>");
		//Parse underlined
		output = output.replaceAll("\\[u\\]", "<u>");
		output = output.replaceAll("\\[/u\\]", "</u>");
		
		return output;
	}
}
