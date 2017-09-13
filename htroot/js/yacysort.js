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
var highestRanking = Infinity;

var displayPage = function() {
  // For every search item that has already been displayed...
  $("#resultscontainer").find(".searchresults").each( function(i) {
    // Apply the "earlierpage" class IFF the item is from an earlier page.
    $(this).toggleClass("earlierpage", parseFloat($(this).data("ranking")) > highestRanking);
  });

  // For every search item from an earlier page...
  $("#resultscontainer").find(".searchresults.earlierpage").each( function(i) {
    // Hide the item
    $(this).removeClass("currentpage");
  });

  // For every search item from a current or later page...
  $("#resultscontainer").find(".searchresults:not(.earlierpage)").each( function(i) {
	var laterPage = (i >= requestedResults);
	$(this).toggleClass("laterpage", laterPage);
    // If we now have too many results, hide the lowest-ranking ones.
    if (laterPage) {
      $(this).removeClass("currentpage");
    }
    else {
      $(this).removeClass("hidden");
      $(this).addClass("currentpage");
    }
  });

  // TODO: The following statistical displays could maybe be moved to the latestinfo() call.

  var offset = $("#resultscontainer").find(".searchresults.earlierpage").length + 1;
  var itemscount = $("#resultscontainer").find(".searchresults.earlierpage").length + $("#resultscontainer").find(".searchresults.currentpage").length;

  // TODO: This seems to often be smaller than the "totalcount" that statistics() ends up with.  Why is that?
  var totalcount = $("#resultscontainer").find(".searchresults").length;

  $("#offset").html(offset);
  $("#itemscount").html(itemscount);

  $("#resNav").html(renderPaginationButtons(offset, requestedResults, totalcount, null, theLocalQuery, true));

  //latestinfo();

  console.log("Showing results " + ($("#resultscontainer").find(".searchresults.earlierpage").length + 1) + " - " + ($("#resultscontainer").find(".searchresults.earlierpage").length + requestedResults) + " out of " + $("#resultscontainer").find(".searchresults").length + "; notEarlierPage = " + $("#resultscontainer").find(".searchresults:not(.earlierpage)").length);
};

var earlierPage = function() {
  // Find all items that are on an earlier page.
  var allEarlierItems = $("#resultscontainer").find(".searchresults.earlierpage");

  // If going back one page would put us at the beginning...
  if (allEarlierItems.length <= requestedResults) {
    highestRanking = Infinity;
  }
  // If going back one page would still be in the middle of the results...
  else {
    var earlierItem = allEarlierItems.get().reverse()[ requestedResults - 1 ];
    highestRanking = parseFloat($(earlierItem).data("ranking"));
    console.log("highestRanking is now " + highestRanking);
  }

  // Update the display to show the new page.
  displayPage();
};

var laterPage = function() {
  // Find all items that are on a later page.
  var allCurrentAndLaterItems = $("#resultscontainer").find(".searchresults:not(.earlierpage)");

  // If going forward one page would put us past the end...
  if (allCurrentAndLaterItems.length <= requestedResults) {
    return;
  }
  // If going forward one page would still be in the middle of the results...
  else {
    var laterItem = allCurrentAndLaterItems.get(requestedResults);
    highestRanking = parseFloat($(laterItem).data("ranking"));
    console.log("highestRanking is now " + highestRanking);
  }

  // Update the display to show the new page.
  displayPage();
};

// pageNumber starts at 0.
var numberedPage = function(pageNumber) {
  // Find all items.
  var allItems = $("#resultscontainer").find(".searchresults");

  var itemNumber = pageNumber * requestedResults;

  // Check if the item number is too high.
  while ( allItems.length - 1 < itemNumber) {
    itemNumber = itemNumber - requestedResults;
  }

  // If the beginning of results is requested, set highestRanking to Infinity.
  if ( itemNumber <= 0 ) {
    highestRanking = Infinity;
  }
  else {
    var item = allItems.get(itemNumber);
    highestRanking = parseFloat($(item).data("ranking"));
  }

  console.log("highestRanking is now " + highestRanking);

  // Update the display to show the new page.
  displayPage();
};

var processSidebarNavProtocols = function(navProtocolsOld, navProtocolsNew) {
  navProtocolsOld.find(".btn-group-xs").each( function(index, oldProtocol) {
    var protocolId = $(oldProtocol).attr("id");

    var newProtocol = navProtocolsNew.find("#" + protocolId);

    // Check whether the protocol has been removed in the new sidebar.
    if (newProtocol.length === 0) {
      console.log("Deleting nav-protocol...");
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

// TODO: test this function
// This is for sidebar items that are <ul> elements with the "menugroup" class.
var processSidebarMenuGroup = function(listOld, listNew) {
  if ( $(listNew).length === 1) {
    if ( $(listOld).length < 1) {
      console.warn("listOld doesn't exist, so can't replace it with listNew.");
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

  /*
  if( old_sidebar.html() == data ) {
    console.log("Sidebar unchanged.");
    return;
  }
  */
  /*
  if( $.trim(old_sidebar.html()) == $.trim(data) ) {
    console.log("Sidebar unchanged.");
    return;
  }
  */
  //else if( $.trim(old_sidebar.html()) == $.trim("") ) {
  if( oldSidebar.children().length === 0 ) {
    console.log("Initializing sidebar...");
    oldSidebar.html(newSidebar.html());
  }
  else {
    console.log("Sidebar has changed, updating...");

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

    // domains (AKA providers)
    // TODO: test domains
    // TODO: we should assign an "id" attribute to the "domains" <ul>.
    processSidebarMenuGroup($("#domains_0").parent(), newSidebar.find("#domains_0").parent());
    
    // TODO: test languages
    // TODO: we should assign an "id" attribute to the "languages" <ul>.
    processSidebarMenuGroup($("#languages_0").parent(), newSidebar.find("#languages_0").parent());

    // TODO: test authors
    // TODO: we should assign an "id" attribute to the "authors" <ul>.
    processSidebarMenuGroup($("#authors_0").parent(), newSidebar.find("#authors_0").parent());

    // TODO: test Wiki Name Space
    // TODO: we should assign an "id" attribute to the "namespace" <ul>.
    processSidebarMenuGroup($("#namespace_0").parent(), newSidebar.find("#namespace_0").parent());

    // TODO: test filetype
    // TODO: we should assign an "id" attribute to the "filetype" <ul>.
    processSidebarMenuGroup($("#filetype_0").parent(), newSidebar.find("#filetype_0").parent());

    // TODO: navs

    // TODO: nav-vocabulary

    // TODO: nav-about
  }

  // TODO: figure out if a better timeout strategy is feasible
  setTimeout(updateSidebar, 500);
};

var updateSidebar = function() {
  $.get(
    "yacysearchtrailer.html",
    {
      "eventID": theEventID,
    },
    processSidebar
  );
  
  //$("#sidebar").load("yacysearchtrailer.html", {"eventID": theEventID});
};

var processItem = function(data) {
  var newItem = $(data);
  newItem.addClass("hidden");

  // If we didn't get a valid response from YaCy, wait 1 second and try again.
  if( ! newItem.data("ranking") ) {
    //console.log("Got undefined item, waiting 1 second...");
    setTimeout(function() {
      $.get(
        "yacysearchitem.html",
        {
          eventID: theEventID,
          item: itemCount
        },
        processItem
      );
    }, 1000);
    return;
  }

  // For every search item that has already been displayed...
  $("#resultscontainer").find(".searchresults").each( function(i) {
    // If the existing search item is lower-ranked than the new item...
    if (parseFloat($(this).data("ranking")) <= parseFloat(newItem.data("ranking")) ) {
      // Insert new item before the existing item
      newItem.insertBefore(this);

      return false;
    }
    // If the new item is lower-ranked than all existing items...
    else if (i == $("#resultscontainer").find(".searchresults").length - 1) {
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
        console.log("Hiding search result because ranking " + newItem.data("ranking") + " too low.");
        return false;
      }
    }
  });

  // Special case if this is the first search item...
  if ($("#resultscontainer").find(".searchresults").length === 0) {
    // Display the new item
    newItem.appendTo("#resultscontainer");
  }

  displayPage();

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
