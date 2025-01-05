/*    
* Copyright (C) 2006 Alexander Schier, Michael Peter Christen
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

function removeAllChildren(element){
	if(element==null){
		return;
	}
	child=element.firstChild;
	while(child!=null){
		element.removeChild(child);
		child=element.firstChild;
	}
}

function getValue(element){
	if(element == null){
		return "";
	}else if(element.nodeType == 3){ //Textnode
		return element.nodeValue;
	}else if(element.firstChild != null && element.firstChild.nodeType == 3){
		return element.firstChild.nodeValue;
	}
	return "";
}
function getFirstChild(element, childname){
	if(childname==null){
		childname="";
	}
	if(element == null){
		return null;
	}
	child=element.firstChild;
	while(child != null){
		if(child.nodeType!=3 && (child.nodeName.toLowerCase()==childname.toLowerCase() || childname=="")){
			return child;
		}
		child=child.nextSibling;
	}
	return null;
}
function getNextSibling(element, childname){
	if(childname==null){
		childname="";
	}
	if(element == null){
		return null;
	}
	child=element.nextSibling;
	while(child != null){
		if(child.nodeType==1 && (child.nodeName.toLowerCase()==childname.toLowerCase() || childname=="")){
			return child;
		}
		child=child.nextSibling;
	}
	return null;
}