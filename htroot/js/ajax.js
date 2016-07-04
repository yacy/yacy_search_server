/*    
* Copyright (C) 2005, 2010 Alexander Schier, Marc Nause
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

function createRequestObject() {
    var ro;
    if (window.XMLHttpRequest) {
        ro = new XMLHttpRequest();
    } else if (window.ActiveXObject) {
        ro = new ActiveXObject("Microsoft.XMLHTTP");
    }
    return ro;
}
var http = createRequestObject();

function sndReq(action) {
    //http.open('get', 'rpc.php?action='+action);
    http.open('get', action);
    http.onreadystatechange = handleResponse;
    http.send(null);
}
