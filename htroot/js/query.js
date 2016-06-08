/*    
* Copyright (C) 2008, 2013 Michael Peter Christen, Roland Haeder
*         
* This file is part of YaCy.
* 
* YaCy is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* YaCy is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with YaCy.  If not, see <http://www.gnu.org/licenses/>.
*/

var query = new Object();

function getQueryProps() {
  var text = "";
  for (property in query) {
    text += property + " = " + query[property] + ";\n";
  }
  return text;
}

function getURLparameters() {
  if (self.location.search.indexOf("=") == -1) {return;}
  var parameterArray = unescape(self.location.search).substring(1).split("&");
  for (var i=0;i<parameterArray.length;i++) {
    parameterArray[i] = parameterArray[i].split("=");
    eval("query." + parameterArray[i][0] + " = \"" + parameterArray[i][1] + "\"");
 }
}