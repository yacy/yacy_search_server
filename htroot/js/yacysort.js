/*    
* Copyright (C) 2017 Jeremy Rand, Ryszard Goń, luccioman
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

/* Functions dedicated to (re)sort YaCy search results in the browser (configuration setting : search.jsresort=true)
 * See related style sheet env/yacysort.css */

var itemCount = 0;
var currentPageNumber = 0;

/* Set to true to enable browser console log traces */
var logEnabled = false;

/* Indicates if the results fetching from server is running */
var fetchingResults = true;

/* Holds the last known navigators generation known from the server */
var lastNavGeneration = null;

/**
 * Refresh the results page, checking and eventually updating each result CSS class depending on its position.
 * Important : resorting based on ranking must have been done before.
 * @param {Boolean} isPageChange when true this refresh is done in response to a page change
 * @param {Number} pageNumber the page number to display
 */
var displayPage = function(isPageChange, pageNumber) {
	var offset = 1;
	var totalcount = 0;
	var itemscount = 0;
	// For every search item that has already been displayed and sorted ...
	$("#resultscontainer").find(".searchresults").each(function(i) {
		var item = $(this);
		var isFresh = item.hasClass("fresh");
		if(isPageChange && isFresh) {
			/* When changing page, remove the 'fresh' mark from all results, 
			 * so that the related insertion/removal animations are not performed */
			item.removeClass("fresh");
		}
		totalcount++;
		var earlierPage = i < (pageNumber * requestedResults);
		
		// Apply the "earlierpage" class IFF the item is from an earlier page.
		item.toggleClass("earlierpage", earlierPage);
		if (earlierPage) {
			item.removeClass("currentpage");
			if(isPageChange) {
				/* Use the "hidden" CSS class when changing page to hide it without unnecessary animation */
				item.addClass("hidden");
			}
			offset++;
			itemscount++;
		} else {
			var laterPage = ((i - offset + 1) >= requestedResults);
			item.toggleClass("laterpage", laterPage);
			// If we now have too many results, hide the lowest-ranking ones.
			if (laterPage) {
				item.removeClass("currentpage");
				if(isPageChange) {
					/* Use the "hidden" CSS class when changing page to hide it without unnecessary animation */
					item.addClass("hidden");
				}
			} else {
				item.removeClass("hidden");
				item.addClass("currentpage");
				itemscount++;
			}
		}
	});
	
  // TODO: collected totalcount here seems to often be smaller than the "totalcount" that statistics() ends up with.  Why is that?

  // TODO: The following statistical displays could maybe be moved to the
	// latestinfo() call.

  $("#offset").html(offset);
  $("#itemscount").html(itemscount);

  var buttonsList = document.getElementById("paginationButtons");
  if(buttonsList != null) {
	  renderPaginationButtons(buttonsList, offset, requestedResults, totalcount, null, theLocalQuery, true);
  }

  //latestinfo();

  if(logEnabled) {
	  console.log("Showing results " + ($("#resultscontainer").find(".searchresults.earlierpage").length + 1) 
			  + " - " + ($("#resultscontainer").find(".searchresults.earlierpage").length + requestedResults) 
			  + " out of " + $("#resultscontainer").find(".searchresults").length + "; notEarlierPage = " + $("#resultscontainer").find(".searchresults:not(.earlierpage)").length);
  }
};

/**
 * Update the current page to the requested page number
 * @param {Number} pageNumber the requested page number (starts at 0)
 */
var numberedPage = function(pageNumber) {
  // Find all items.
  var allItems = $("#resultscontainer").find(".searchresults");

  // Check if the pageNumber is too high before updating the currentPage
  var checkedPageNumber;
  if(allItems.length > 0) {
	  checkedPageNumber = Math.min(pageNumber, Math.floor(allItems.length / requestedResults));
  } else {
	  checkedPageNumber = 0;
  }
  var pageChange = currentPageNumber != checkedPageNumber;
  currentPageNumber = checkedPageNumber;

  // Update the display to show the new page.
  displayPage(pageChange, checkedPageNumber);
};

var processSidebarNavProtocols = function(navProtocolsOld, navProtocolsNew) {
  navProtocolsOld.find(".btn-group-xs").each( function(index, oldProtocol) {
    var protocolId = $(oldProtocol).attr("id");

    var newProtocol = navProtocolsNew.find("#" + protocolId);

    // Check whether the protocol has been removed in the new sidebar.
    if (newProtocol.length === 0) {
    	if(logEnabled) {
    		console.log("Deleting nav-protocol...");
    	}
    	$(oldProtocol).hide(1000);
    }
  } );

  navProtocolsNew.find(".btn-group-xs").each( function(index, newProtocol) {
    var protocolId = $(newProtocol).attr("id");

    var oldProtocol = navProtocolsOld.find("#" + protocolId);

    // Check whether the protocol exists in both the new and old sidebar
    if (oldProtocol.length === 1) {
      // Replace the HTML.
      // TODO: Look into smoother animations.
      $(oldProtocol).html($(newProtocol).html()).show(1000);
    }

    // Check whether the protocol has been added in the new sidebar.
    if (oldProtocol.length === 0) {
      // We need to insert the protocol in the right position.

      // TODO: Insert in the correct position instead of the end.
      $(newProtocol).hide();
      $(navProtocolsOld).append($(newProtocol));
      $(newProtocol).show(1000);
    }
  } );
};

/**
 * Refresh a navigator list element with the "menugroup" class.
 * @param parentSidebar the parent navigators side bar currently displayed
 * @param listOld the old navigator list
 * @param listNew the new navigator list
 */
var processSidebarMenuGroup = function(parentSidebar, listOld, listNew) {
  if ( $(listNew).length === 1) {
    if ( $(listOld).length < 1) {
    	/* List old doesn't exist : insert it at the beginning of the list */
    	var allNavs = parentSidebar.find(".nav.nav-sidebar.menugroup");
    	if(allNavs.length > 0) {
    		listNew.insertBefore(allNavs[0]);
    	}
        return;
    }

    var childrenOld = $(listOld).children("li");
    if ( childrenOld.length >= 2 ) {
      // There are at least 2 <li> elements in the list.
      // The first one is the heading, so skip that one.
      var childToCheck = childrenOld[1];
      if ( $(childToCheck).css("display") == "block" ) {
        // The list has been expanded by the user already.
        // That means we need to expand the new list to match.
        // If we don't do this, the new list will be collapsed every time we update,
        // which would annoy the user.

        $(listNew).children("li").css("display", "block");
      }
    }

    // TODO: animate
    listOld.html(listNew.html());
  }
};

var processSidebar = function(data) {
  var oldSidebar = $("#sidebar");
  var newSidebar = $('<div class="col-sm-4 col-md-3 sidebar" id="sidebar">\n\n' + data + '\n\n</div>');

  if( oldSidebar.children().length === 0 ) {
	if(logEnabled) {
		console.log("Initializing sidebar...");
	}
    oldSidebar.html(newSidebar.html());
  }
  else {
	if(logEnabled) {
		console.log("Sidebar has changed, updating...");
	}

    var navProtocolsOld = $("#nav-protocols");
    var navProtocolsNew = newSidebar.find("#nav-protocols");
    
    if( navProtocolsNew.length === 1 ) {
      processSidebarNavProtocols(navProtocolsOld, navProtocolsNew);
    }

    var tagCloudOld = $("#tagcloud");
    var tagCloudNew = newSidebar.find("#tagcloud");

    if ( tagCloudNew.length === 1 ) {
      // TODO: animate
      tagCloudOld.html(tagCloudNew.html());
    }

    var catLocationOld = $("#cat-location");
    var catLocationNew = newSidebar.find("#cat-location");

    if ( catLocationNew.length === 1 ) {
      // TODO: animate
      catLocationOld.html(catLocationNew.html());
    }

    // TODO: nav-dates

    // hosts (AKA providers)
    processSidebarMenuGroup(oldSidebar, $("#nav-hosts"), newSidebar.find("#nav-hosts"));
    
    processSidebarMenuGroup(oldSidebar, $("#nav-languages"), newSidebar.find("#nav-languages"));

    processSidebarMenuGroup(oldSidebar, $("#nav-authors"), newSidebar.find("#nav-authors"));

    processSidebarMenuGroup(oldSidebar, $("#nav-namespace"), newSidebar.find("#nav-namespace"));

    processSidebarMenuGroup(oldSidebar, $("#nav-filetype"), newSidebar.find("#nav-filetype"));

    // TODO: navs

    // TODO: nav-vocabulary

    // TODO: nav-about
    
    /* Store the new nav-generation data attribute if provided */
    var navGenerationHolder = $("#rankingButtons");
    if(navGenerationHolder.length > 0) {
        var newNavGenerationHolder = newSidebar.find("#rankingButtons");
        
        if(newNavGenerationHolder.length > 0) {
        	var navGeneration = newNavGenerationHolder.data("nav-generation");
        	if(navGeneration != null) {
        		navGenerationHolder.data("nav-generation", navGeneration);
        		lastNavGeneration = navGeneration;
        	}
        }
    }
  }

  if(fetchingResults) {
	  setTimeout(updateSidebar, 500);
  }
};

/**
 * Update the search navigators (facets) sidebar if necessary.
 */
var updateSidebar = function() {
  var navGenerationHolder = $("#rankingButtons");
  var shouldUpdateSideBar = true;
  if(navGenerationHolder.length > 0) {
	  var oldNavGeneration = navGenerationHolder.data("nav-generation");
	  if(oldNavGeneration != null && lastNavGeneration != null && oldNavGeneration == lastNavGeneration) {
		  /* nav-generation has not changed : no need to refresh the navigators side bar*/
		  shouldUpdateSideBar = false;
		  
		  if(logEnabled) {
			  console.log("Prevented unnecessary sidebar update");
		  }
	  }
 }
	
 if(shouldUpdateSideBar) {
		var trailerParams = {
		  "eventID": theEventID,
	      "resource": "global"
		};
		var searchForm = document.forms.searchform;
		if(searchForm != null) {
			if(searchForm.resource != null && searchForm.resource.value != null) {
				trailerParams.resource = searchForm.resource.value;
			}
			if(searchForm.auth != null && searchForm.auth.value != null) {
				trailerParams.auth = searchForm.auth.value;
			}
		}
		$.get(
			"yacysearchtrailer.html",
			trailerParams,
			processSidebar
		);
 } else if(fetchingResults) {
	setTimeout(updateSidebar, 500);
 }
};

/**
 * Process result from yacysearchlatestinfo.json and re-launch results fetching
 * only is the results feeders are not terminated on the server
 */
var processLatestInfo = function(latestInfo) {
	if (latestInfo.feedRunning) {
		$.get("yacysearchlatestinfo.json", {
			eventID : theEventID
		}, processItem);
	} else {
		fetchingResults = false;
	}
}

var processItem = function(data) {
  var newItem = $(data);
  newItem.addClass("hidden fresh");

  /* If we didn't get a valid response from YaCy, wait a bit and check if the results feeders are still running on the server side */
  if( ! newItem.data("ranking") ) {
    setTimeout(function() {
      $.get(
        "yacysearchlatestinfo.json",
        {
          eventID: theEventID
        },
        processLatestInfo
      );
    }, 1000);
    return;
  }

  // For every search item that has already been displayed...
  var allResults = $("#resultscontainer").find(".searchresults");
  var allResultsLength = allResults.length;
  
  // Special case if this is the first search item.
  if(allResultsLength === 0) {
	  // Display the new item
	  newItem.appendTo("#resultscontainer");
  } else {
	  allResults.each( function(i) {
		  // If the existing search item is lower-ranked than the new item...
		  if (parseFloat($(this).data("ranking")) <= parseFloat(newItem.data("ranking")) ) {
			  // Insert new item before the existing item
			  newItem.insertBefore(this);

			  return false;
		  }
		  // If the new item is lower-ranked than all existing items...
		  else if (i == allResultsLength - 1) {
			  // And if the new item (position i + 1) would be ranked 0 to requestedResults - 1...
			  if (i + 1 < requestedResults) {
				  // Insert new item at the end
				  newItem.appendTo("#resultscontainer");
				  return false;
			  }
			  // If the new item is too irrelevant to be displayed...
			  else {
				  // Insert new item at the end
				  newItem.appendTo("#resultscontainer");
				  if(logEnabled) {
					  console.log("Hiding search result because ranking " + newItem.data("ranking") + " too low.");
				  }
				  return false;
			  }
		  }
	  });
  }
  
  var navGeneration =  newItem.data("nav-generation");
  if(navGeneration != null) {
	  /* Store the navigators generation new value when the item provided this info */
	  lastNavGeneration = navGeneration;
  }

  displayPage(false, currentPageNumber);

  if (itemCount === 0) {
    updateSidebar();
  }

  // Increment itemCount and get another item.
  itemCount++;
  $.get(
    "yacysearchitem.html",
    {
      eventID: theEventID,
      item: itemCount
    },
    processItem
  );
};
