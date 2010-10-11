function xmlhttpPost() {
    var searchform = document.forms['searchform'];
    search(searchform.query.value);
}

function search(query) {
//    var xmlHttpReq = false;
    var self = this;
    if (window.XMLHttpRequest) { // Mozilla/Safari
        self.xmlHttpReq = new XMLHttpRequest(); 
    }
    else if (window.ActiveXObject) { // IE
        self.xmlHttpReq = new ActiveXObject("Microsoft.XMLHTTP");
    }
    self.xmlHttpReq.open('GET', "yacysearch.json?verify=false&resource=local&maximumRecords=100&nav=none&query=" + query, true);
    self.xmlHttpReq.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    self.xmlHttpReq.onreadystatechange = function() {
        if (self.xmlHttpReq.readyState == 4) {
            updatepage(self.xmlHttpReq.responseText);
        }
    }
    self.xmlHttpReq.send(null);
}

function navget(list, name) {
  for (var i = 0; i < list.length; i++) {
    if (list[i].facetname == name) return list[i];
  }
}

function updatepage(str) {
  var raw = document.getElementById("raw");
  if (raw != null) raw.innerHTML = str;
  var rsp = eval("("+str+")");
  var firstChannel = rsp.channels[0];
  var totalResults = firstChannel.totalResults.replace(/[,.]/,"");
//  var startIndex = firstChannel.startIndex;
//  var itemsPerPage = firstChannel.itemsPerPage;
  var navigation = firstChannel.navigation;
  var topics = navget(navigation, "topics");
  
  var html = "<span id=\"resCounter\" style=\"display: inline;\">total results = " + totalResults;
  if (topics && topics.length > 0) {
    var topwords = "";
    for (var i = 0; i < topics.elements.length; i++) {
        topwords += "<a href=\"yacyinteractive.html?query=" + firstChannel.searchTerms + "+" + topics.elements[i].name + "\">" + topics.elements[i].name + "</a> ";
        if (i > 10) break;
    }
    html += "&nbsp;&nbsp;&nbsp;topwords: " + topwords;
  }
  html += "</span><br>";
  
  if (totalResults > 0) {
    var item;
    html += "<table class=\"sortable\" border=\"0\" cellpadding=\"2\" cellspacing=\"1\" width=\"99%\">";
    html += "<tr class=\"TableHeader\" valign=\"bottom\">";
    html += "<td>Name</td>";
    html += "<td width=\"60\">Size</td>";
    //html += "<td>Description</td>";
    html += "<td width=\"180\">Date</td></tr>";
    for (var i = 0; i < firstChannel.items.length; i++) {
        item = firstChannel.items[i];
        html += "<tr class=\"TableCellLight\"><td align=\"left\"><a href=\"" + item.link + "\">" + item.title + "</a></td>";
        html += "<td align=\"right\">" + item.sizename + "</td>";
        //html += "<td>" + item.description + "</td>";
        html += "<td align=\"right\">" + item.pubDate + "</td></tr>";
    }
    html += "</table>";
  }
  document.getElementById("searchresults").innerHTML = html;
}