/*    
* Copyright (C) 2011 Stefan Förster
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

/* Initialize Tag Actions */
function tag_action(com,grid) {
	alert("Sorry, the function you have requested is not yet available!");
	if (com=='Add') {
		flex = grid;			
		$('#tagaddform').resetForm();
		$("#tagadd").dialog('open');
	} else 	{
		$('#tageditform').resetForm();
		$("#tagedit").dialog('open');
	}				
};