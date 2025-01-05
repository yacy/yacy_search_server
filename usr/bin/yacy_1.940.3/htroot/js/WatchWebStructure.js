/*    
* Copyright (C) 2007, 2010 Alexander Schier, Michael Benz
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

function changeHost(){
	window.location.replace("http://"+window.location.host+":"+window.location.port+"/WatchWebStructure_p.html?host="+document.getElementById("host").value);
}
function keydown(ev){
        if(ev.which==13){
                changeHost();
        }
}

/* Check if the input matches some RGB hex values */
function isValidColor(hexcolor) {
	var strPattern = /^[0-9a-f]{3,6}$/i;
	return strPattern.test(hexcolor);
} 
	
function checkform(form) {
	if (isValidColor(form.colorback.value)) {
		if (isValidColor(form.colortext.value)) {
			if (isValidColor(form.colorline.value)) {
				if (isValidColor(form.colordot.value)) {
					if (isValidColor(form.colorlineend.value)) {
						return true;
					} else {
						alert("Invalid Dot-end value: " + form.colorlineend.value);
						return false;
					}
				} else {
					alert("Invalid Dot value: " + form.colordot.value);
					return false;
				}
			} else {
				alert("Invalid Line value: " + form.colorline.value);
				return false;
			}
		} else {
			alert("Invalid Text value: " + form.colortext.value);
			return false;
		}
	} else {
		alert("Invalid Background value: " + form.colorback.value);
		return false;
	}
}