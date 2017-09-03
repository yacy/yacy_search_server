var itemCount = 0;
var highestRanking = Infinity;

var displayPage = function() {
  // For every search item that has already been displayed...
  $("#resultscontainer .searchresults").each( function(i) {
    // Apply the "earlierpage" class IFF the item is from an earlier page.
    $(this).toggleClass("earlierpage", parseFloat($(this).data("ranking")) > highestRanking);
  });

  // For every search item from an earlier page...
  $("#resultscontainer .searchresults.earlierpage").each( function(i) {
    // Hide the item
    $(this).removeClass("currentpage");
    $(this).hide(1000);
  });

  // For every search item from a current or later page...
  $("#resultscontainer .searchresults:not(.earlierpage)").each( function(i) {
    // If we now have too many results, hide the lowest-ranking ones.
    if (i >= requestedResults) {
      $(this).removeClass("currentpage");
      $(this).hide(1000);
    }
    else {
      $(this).addClass("currentpage");
      $(this).show(1000);
    }
  });

  $("#offset").html($("#resultscontainer .searchresults.earlierpage").length + 1);
  $("#itemscount").html($("#resultscontainer .searchresults.earlierpage").length + $("#resultscontainer .searchresults.currentpage").length);

  console.log("Showing results " + ($("#resultscontainer .searchresults.earlierpage").length + 1) + " - " + ($("#resultscontainer .searchresults.earlierpage").length + requestedResults) + " out of " + $("#resultscontainer .searchresults").length + "; notEarlierPage = " + $("#resultscontainer .searchresults:not(.earlierpage)").length);
};

var earlierPage = function() {
  // Find all items that are on an earlier page.
  var allEarlierItems = $("#resultscontainer .searchresults.earlierpage");

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
  var allCurrentAndLaterItems = $("#resultscontainer .searchresults:not(.earlierpage)");

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

var processItem = function(data) {
  var newItem = $(data).hide();

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
  $("#resultscontainer .searchresults").each( function(i) {
    // If the existing search item is lower-ranked than the new item...
    if (parseFloat($(this).data("ranking")) <= parseFloat(newItem.data("ranking")) ) {
      // Insert new item before the existing item
      newItem.insertBefore(this);

      return false;
    }
    // If the new item is lower-ranked than all existing items...
    else if (i == $("#resultscontainer .searchresults").length - 1) {
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
  if ($("#resultscontainer .searchresults").length === 0) {
    // Display the new item
    newItem.appendTo("#resultscontainer");
  }

  displayPage();

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
