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
	/* Also ensure the accessibility property for progress current value is set to 100% */
	document.getElementById("progressbar").setAttribute("aria-valuenow", 100);
}

/**
 * @param buttonsList the DOM list element containing the pagination buttons
 * @param offset item number to start with
 * @param itemsperpage count of items requested per page
 * @param totalcount count of items available from YaCy node for this query
 * @param navurlbase the search url without pagination parameters
 * @param localQuery when true the search query is limited to the YaCy peer local data
 * @param jsResort when true results resorting with JavaScript is enabled
 */
function renderPaginationButtons(buttonsList, offset, itemsperpage, totalcount,
		navurlbase, localQuery, jsResort) {
	var buttons = buttonsList.getElementsByTagName("li");
	if (buttons.length < 2) {
		/* At least prev and next page buttons are expected to be here */
		return;
	}
	
	var thispage = Math.floor(offset / itemsperpage);
	var firstPage = thispage - (thispage % 10);
	
	var prevPageElement = buttons[0];
	var links = prevPageElement.getElementsByTagName("a");
	var prevPageLink = links.length == 1 ? links[0] : null;
	if (thispage == 0) {
		/* First page : the prev page button is disabled */
		prevPageElement.className = "disabled";
		if (prevPageLink != null) {
			prevPageLink.accessKey = null;
			prevPageLink.href = "#";
		}
	} else {
		prevPageElement.className = "";
		if (prevPageLink != null) {
			prevPageLink.accessKey = "p";
			if (jsResort) {
				prevPageLink.href = "javascript:numberedPage(" + (thispage - 1)
						+ ");";
			} else {
				prevPageLink.href = (navurlbase + "&startRecord=" + ((thispage - 1) * itemsperpage));
			}
		}
	}
	
	var totalPagesNb = Math.floor(1 + ((totalcount - 1) / itemsperpage));
	var numberofpages = Math.min(10, totalPagesNb - firstPage);
	if (!numberofpages) {
		numberofpages = 10;
	}
	if(totalPagesNb > 1 && numberofpages >= 1) {
		buttonsList.className = "pagination";
	} else {
		/* Hide the pagination buttons when there is less than one page of results */
		buttonsList.className = "pagination hidden";
	}
	var btnIndex = 1;
	var btnElement, pageLink;
	/* Update existing buttons or add new ones according to the new pagination */
	for (var i = firstPage; i < (firstPage + numberofpages); i++) {
		if (btnIndex < (buttons.length - 1)) {
			btnElement = buttons[btnIndex];
			pageLink = btnElement.firstChild;
		} else {
			btnElement = document.createElement("li");
			btnElement.id = "pageBtn" + btnIndex;
			pageLink = document.createElement("a");
			btnElement.appendChild(pageLink);
		}
		if (pageLink != null) {
			if (i == thispage) {
				btnElement.className = "active";
				pageLink.href = "#";
			} else {
				btnElement.className = "";
				if (jsResort) {
					pageLink.href = "javascript:numberedPage(" + (i) + ");";
				} else {
					pageLink.href = navurlbase + "&startRecord=" + (i * itemsperpage);
				}
			}
			pageLink.innerText = (i + 1);
		}

		if (btnIndex >= (buttons.length - 1)) {
			/*
			 * Insert the newly created button now that all its modifications
			 * are done
			 */
			buttonsList.insertBefore(btnElement, buttons[buttons.length - 1]);
		}

		btnIndex++;
	}

	/* Remove existing buttons now in excess */
	while (btnIndex < (buttons.length - 1)) {
		buttonsList.removeChild(buttons[buttons.length - 2]);
	}

	var nextPageElement = buttons[buttons.length - 1];
	links = nextPageElement.getElementsByTagName("a");
	var nextPageLink = links.length == 1 ? links[0] : null;
	if ((localQuery && thispage >= (totalPagesNb - 1))
			|| (!localQuery && thispage >= (numberofpages - 1))) {
		/* Last page on a local query, or last fetchable page in p2p mode : the next page button is disabled */
		nextPageElement.className = "disabled";
		if (nextPageLink != null) {
			nextPageLink.accessKey = null;
			nextPageLink.href = "#";
		}
	} else {
		nextPageElement.className = "";
		if (nextPageLink != null) {
			nextPageLink.accessKey = "n";
			if (jsResort) {
				nextPageLink.href = "javascript:numberedPage(" + (thispage + 1)
						+ ");";
			} else {
				nextPageLink.href = navurlbase + "&startRecord="
						+ ((thispage + 1) * itemsperpage);
			}
		}
	}
}

/**
 * Parses a string representing an integer value
 * 
 * @param strIntValue
 *            formatted string
 * @returns the number value or undefined when the string is undefined, or NaN
 *          when the string is not a number
 */
function parseFormattedInt(strIntValue) {
	var inValue;
	if(strIntValue != null && strIntValue.replace != null) {
		/* Remove thousands separator and try to parse as integer */
		intValue = parseInt(strIntValue.replace(/[\.\,]/g,''))
	}
	return intValue;
}

function statistics(offset, itemscount, itemsperpage, totalcount, localIndexCount, remoteIndexCount, remotePeerCount, navurlbase, localQuery, feedRunning, jsResort, updatePagination) {
  var totalcountIntValue = parseFormattedInt(totalcount);
  var offsetIntValue = parseFormattedInt(offset);
  var itemscountIntValue = parseFormattedInt(itemscount);
  var itemsperpageIntValue = parseFormattedInt(itemsperpage);
  
  var feedingStatusElement = document.getElementById("feedingStatus");
  if(feedingStatusElement != null) {
	  if(feedRunning) {
		  feedingStatusElement.style.visibility = "visible";
	  } else {
		  feedingStatusElement.style.visibility = "hidden";
	  }
  }
  
  /* Display the eventual button allowing to refresh the sort of cached results 
   * only when all feeds are terminated and when there is more than one result */
  var resortCachedElement = document.getElementById("resortCached");
  if(resortCachedElement != null) {
	  if(feedRunning) {
		  resortCachedElement.style.visibility = "hidden";
	  } else if(totalcountIntValue > 1){
		  resortCachedElement.style.visibility = "visible";
	  }
  }
  
  if (totalcountIntValue == 0) {
	  return;
  }
  var elementToUpdate = document.getElementById("offset");
  if (offsetIntValue >= 0 && elementToUpdate != null) {
	  elementToUpdate.innerHTML = offset;
  }
  
  elementToUpdate = document.getElementById("startRecord");
  if (offsetIntValue >= 0 && elementToUpdate != null) {
	  elementToUpdate.setAttribute('value', offsetIntValue - 1);
  }
  
  elementToUpdate = document.getElementById("itemscount");
  if (itemscountIntValue >= 0 && elementToUpdate != null) {
	  elementToUpdate.firstChild.nodeValue = itemscount;
  }
  
  elementToUpdate = document.getElementById("totalcount");
  if(elementToUpdate != null) {
	  elementToUpdate.firstChild.nodeValue = totalcount;
  }
  
  elementToUpdate = document.getElementById("localIndexCount");
  if (elementToUpdate != null) {
	  elementToUpdate.firstChild.nodeValue = localIndexCount;
  }
  
  elementToUpdate = document.getElementById("remoteIndexCount");
  if (elementToUpdate != null) {
	  elementToUpdate.firstChild.nodeValue = remoteIndexCount;
  }
  
  elementToUpdate = document.getElementById("remotePeerCount");
  if (elementToUpdate != null) {
	  elementToUpdate.firstChild.nodeValue = remotePeerCount;
  }
  // compose page navigation

  var progresseBarElement = document.getElementById("progressbar");
  if (progresseBarElement.getAttribute('class') != "progress-bar progress-bar-success") {
	  var percent = 100 * (itemscountIntValue - offsetIntValue + 1) / itemsperpageIntValue;
	  if (percent == 100) {
		  progresseBarElement.setAttribute('style',"transition:transform 0s;-webkit-transition:-webkit-transform 0s;");
		  progresseBarElement.setAttribute('class',"progress-bar progress-bar-success");
		  window.setTimeout(fadeOutBar, 500);
	  } else {
		  progresseBarElement.setAttribute('aria-valuenow', percent);
	  }
	  progresseBarElement.setAttribute('style',"width:" + percent + "%");
  }
  var buttonsList = document.getElementById("paginationButtons");
  if (buttonsList != null && !jsResort && updatePagination) {
	  renderPaginationButtons(buttonsList, offsetIntValue, itemsperpageIntValue, totalcountIntValue, navurlbase, localQuery, jsResort);
  }
}

/**
 * Toggle visibility on a block of tags (keywords) beyond the initial limit of tags to display.
 * @param {HTMLButtonElement} button the button used to expand the tags
 * @param {String} moreTagsId the id of the container of tags which visibility has to be toggled
 */
function toggleMoreTags(button, moreTagsId) {
	var moreTagsContainer = document.getElementById(moreTagsId);
	if(button != null && moreTagsContainer != null) {
		if(button.getAttribute("aria-expanded") == "true") {
			/* Additionnaly we modify the aria-expanded state for improved accessiblity */
			button.setAttribute("aria-expanded", "false");
			button.title = "Show all";
			moreTagsContainer.className = "hidden";
		} else {
			/* Additionnaly we modify the aria-expanded state for improved accessiblity */
			button.setAttribute("aria-expanded", "true");
			button.title = "Show only the first elements";
			moreTagsContainer.className = ""; 
		}
	}
}

/**
 * Handle a rendering error on a result image thumbnail.
 * 
 * @param imgElem
 *            {HTMLImageElement} the html img element that could not be rendered
 */
function handleResultThumbError(imgElem) {
	if (imgElem.parentNode != null && imgElem.parentNode.parentNode != null
			&& imgElem.parentNode.parentNode.className == "thumbcontainer") {
		/* Hide the thumbnail container */
		imgElem.parentNode.parentNode.className = "thumbcontainer thumbError hidden";
		var errorsInfoElem = document.getElementById("imageErrorsInfo");
		if (errorsInfoElem != null && errorsInfoElem.className.indexOf("hidden") >= 0) {
			/* Show the image errors information block */
			errorsInfoElem.className = errorsInfoElem.className.replace("hidden", "");
		}
		var errorsCountElem = document.getElementById("imageErrorsCount");
		if (errorsCountElem != null) {
			/* Increase the count of thumbnails rendering errors */
			errorsCountElem.firstChild.nodeValue = parseInt(errorsCountElem.firstChild.nodeValue) + 1;
		}
	}
}

/**
 * Show result thumbnails that were hidden because a rendering error occurred.
 */
function showErrThumbnails() {
	var thumbs = document.getElementsByClassName("thumbError hidden");
	var length = thumbs.length;
	for (var i = 0; i < length && thumbs.length > 0; i++) {
		thumbs[0].className = thumbs[0].className.replace("hidden", ""); // after that the element is removed from the thumbs live collection
	}
	var showBtn = document.getElementById("showErrorImagesBtn");
	if(showBtn != null) {
		showBtn.className = showBtn.className + " hidden";
	}
	var hideBtn = document.getElementById("hideErrorImagesBtn");
	if(hideBtn != null) {
		hideBtn.className = hideBtn.className.replace("hidden", "");
	}
}

/**
 * Hide result thumbnails that had a rendering error.
 */
function hideErrThumbnails() {
	var thumbs = document.getElementsByClassName("thumbError");
	for (var i = 0; i < thumbs.length; i++) {
		if(thumbs[i].className.indexOf("hidden") < 0) {
			thumbs[i].className = thumbs[i].className + " hidden";	
		}
	}
	var showBtn = document.getElementById("showErrorImagesBtn");
	if(showBtn != null) {
		showBtn.className = showBtn.className.replace("hidden", "");
	}
	var hideBtn = document.getElementById("hideErrorImagesBtn");
	if(hideBtn != null) {
		hideBtn.className = hideBtn.className + " hidden";
	}
}