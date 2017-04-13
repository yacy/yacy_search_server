/*    
* Copyright (C) 2006 - 2014 Martin Thelian, Alexander Schier, Michael Hamann, 
* Michael Peter Christen, Franz Brausse, fuchsi
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

function addHover() {
  if (document.all&&document.getElementById) {
    var divs = document.getElementsByTagName("div");
    for (i=0; i<divs.length; i++) {
      var node = divs[i];
      if (node.className=="searchresults") {
        node.onmouseover=function() {
          this.className+=" hover";
        }
        node.onmouseout=function() {
          this.className=this.className.replace(" hover", "");
        }
      }
    }
  }
}

function fadeOutBar() {
	document.getElementById("progressbar").setAttribute('style',"transition:transform 0s;-webkit-transition:-webkit-transform 0s;backgroundColor:transparent;");
}

/**
 * @returns pagination buttons
 */
function renderPaginationButtons(offset, itemscount, itemsperpage, totalcount, localResourceSize, remoteResourceSize, remoteIndexCount, remotePeerCount, navurlbase, localQuery) {
	var resnav = "<ul class=\"pagination\">";
	var thispage = Math.floor(offset / itemsperpage);
	var firstPage = thispage - (thispage % 10);
	if (thispage == 0) {
		resnav += "<li class=\"disabled\"><a title=\"Previous page\" href=\"#\">&laquo;</a></li>";
	} else {
	 	resnav += "<li><a id=\"prevpage\" title=\"Previous page\" href=\"";
	    resnav += (navurlbase + "&amp;startRecord=" + ((thispage - 1) * itemsperpage));
	  	resnav += "\">&laquo;</a></li>";
	}
	
	var totalPagesNb = Math.floor(1 + ((totalcount - 1) / itemsperpage));
	var numberofpages = Math.min(10, totalPagesNb - firstPage);
	if (!numberofpages) numberofpages = 10;
	for (i = firstPage; i < (firstPage + numberofpages); i++) {
	    if (i == thispage) {
	       resnav += "<li class=\"active\"><a href=\"#\">";
	       resnav += (i + 1);
	       resnav += "</a></li>";
	    } else {
	       resnav += "<li><a href=\"";
	       resnav += (navurlbase + "&amp;startRecord=" + (i * itemsperpage));
	       resnav += "\">" + (i + 1) + "</a></li>";
	    }
	}
	if ((localQuery && thispage >= (totalPagesNb - 1)) || (!localQuery && thispage >= (numberofpages - 1))) {
		resnav += "<li class=\"disabled\"><a href=\"#\" title=\"Next page\">&raquo;</a></li>";
	} else {
	    resnav += "<li><a id=\"nextpage\" title=\"Next page\" href=\"";
	    resnav += (navurlbase + "&amp;startRecord=" + ((thispage + 1) * itemsperpage));
	    resnav += "\">&raquo;</a>";
	}
	resnav += "</ul>";
	return resnav;
}

/**
 * Parses a string representing an integer value
 * @param strIntValue formatted string
 * @returns the number value or undefined when the string is undefined, or NaN when the string is not a number
 */
function parseFormattedInt(strIntValue) {
	var inValue;
	if(strIntValue != null && strIntValue.replace != null) {
		/* Remove thousands separator and try to parse as integer */
		intValue = parseInt(strIntValue.replace(/[\.\,]/g,''))
	}
	return intValue;
}

function statistics(offset, itemscount, itemsperpage, totalcount, localResourceSize, remoteResourceSize, remoteIndexCount, remotePeerCount, navurlbase, localQuery) {
  var totalcountIntValue = parseFormattedInt(totalcount);
  var offsetIntValue = parseFormattedInt(offset);
  var itemscountIntValue = parseFormattedInt(itemscount);
  var itemsperpageIntValue = parseFormattedInt(itemsperpage);
  
  if (totalcountIntValue == 0) {
	  return;
  }
  if (offsetIntValue >= 0) document.getElementById("offset").innerHTML = offset;
  if (offsetIntValue >= 0) document.getElementById("startRecord").setAttribute('value', offsetIntValue - 1);
  if (itemscountIntValue >= 0) document.getElementById("itemscount").firstChild.nodeValue = itemscount;
  document.getElementById("totalcount").firstChild.nodeValue = totalcount;
  if (document.getElementById("localResourceSize") != null) document.getElementById("localResourceSize").firstChild.nodeValue = localResourceSize;
  if (document.getElementById("remoteResourceSize") != null) document.getElementById("remoteResourceSize").firstChild.nodeValue = remoteResourceSize;
  if (document.getElementById("remoteIndexCount") != null) document.getElementById("remoteIndexCount").firstChild.nodeValue = remoteIndexCount;
  if (document.getElementById("remotePeerCount") != null) document.getElementById("remotePeerCount").firstChild.nodeValue = remotePeerCount;
  // compose page navigation

  if (document.getElementById("progressbar").getAttribute('class') != "progress-bar progress-bar-success") {
	  var percent = 100 * (itemscountIntValue - offsetIntValue + 1) / itemsperpageIntValue;
	  if (percent == 100) {
		  document.getElementById("progressbar").setAttribute('style',"transition:transform 0s;-webkit-transition:-webkit-transform 0s;");
		  document.getElementById("progressbar").setAttribute('class',"progress-bar progress-bar-success");
		  window.setTimeout(fadeOutBar, 500);
	  }
	  document.getElementById("progressbar").setAttribute('style',"width:" + percent + "%");
  }
  var resnavElement = document.getElementById("resNav");
  if (resnavElement != null) {
	  resnavElement.innerHTML = renderPaginationButtons(offsetIntValue, itemscountIntValue, itemsperpageIntValue, 
			  totalcountIntValue, parseFormattedInt(localResourceSize), parseFormattedInt(remoteResourceSize), parseFormattedInt(remoteIndexCount), 
			  parseFormattedInt(remotePeerCount), navurlbase, localQuery);
  }
}


