function xmlhttpPost() {
    var searchform = document.forms['searchform'];
    search(searchform.query.value);
}

function search(query) {
//    var xmlHttpReq = false;
    start = new Date();
    var self = this;
    if (window.XMLHttpRequest) { // Mozilla/Safari
        self.xmlHttpReq = new XMLHttpRequest(); 
    }
    else if (window.ActiveXObject) { // IE
        self.xmlHttpReq = new ActiveXObject("Microsoft.XMLHTTP");
    }
    self.xmlHttpReq.open('GET', "yacysearch.json?verify=false&resource=local&maximumRecords=1000&nav=all&query=" + query, true);
    self.xmlHttpReq.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    self.xmlHttpReq.onreadystatechange = function() {
        if (self.xmlHttpReq.readyState == 4) {
            stop = new Date();
            updatepage(query, self.xmlHttpReq.responseText, stop.getTime() - start.getTime());
        }
    }
    self.xmlHttpReq.send(null);
}

function navget(list, name) {
  for (var i = 0; i < list.length; i++) {
    if (list[i].facetname == name) return list[i];
  }
}

var searchresult;

function makeDownloadScript() {
  script = "<div style=\"float:left\"><pre>";
  for (var i = 0; i < searchresult.length; i++) {
        var item = searchresult[i];
        script += "curl -OL \"" + item.link + "\"\n";
  }
  script += "</pre></div>";
  document.getElementById("downloadscript").innerHTML = script;
  document.getElementById("downloadbutton").innerHTML = "<input id=\"downloadbutton\" type=\"button\" value=\"hide the download script\" onClick=\"hideDownloadScript();\"/></form>";
}

function hideDownloadScript() {
  document.getElementById("downloadscript").innerHTML = "";
  var dlb = document.getElementById("downloadbutton");
  if (dlb) dlb.innerHTML = "<input type=\"button\" value=\"create a download script\" onClick=\"makeDownloadScript();\"/></form>";
}

function updatepage(query, str, time) {
  var raw = document.getElementById("raw");
  if (raw != null) raw.innerHTML = str;
  var rsp = eval("("+str+")");
  var firstChannel = rsp.channels[0];
  searchresult = firstChannel.items;
  var totalResults = firstChannel.totalResults.replace(/[,.]/,"");
//  var startIndex = firstChannel.startIndex;
//  var itemsPerPage = firstChannel.itemsPerPage;
  var navigation = firstChannel.navigation;
  var topics = navget(navigation, "topics");
  
  // analyse the search result
  var filetypes = {};
  for (var i = 0; i < firstChannel.items.length; i++) {
    item = firstChannel.items[i];
    if (item.link && item.link.length > 4) {
      ext = item.link.substring(item.link.length - 4);
      if (ext.charAt(0) == ".") {
        ext = ext.substring(1).toLowerCase();
        var count = filetypes[ext];
        if (count) filetypes[ext]++; else filetypes[ext] = 1;
      }
    }
  }
  for (var key in filetypes) {
    if (query.indexOf("filetype:" + key) >= 0) delete filetypes[key];
  }

  // show statistics
  var html = "<span id=\"resCounter\" style=\"display: inline;\">";
  if (firstChannel.items.length > 0) {
      html += "<form><div style=\"float:left\">" + firstChannel.items.length + " results from a total of " + totalResults + " docs in index; search time: " + time + " milliseconds. </div>";
      html += "<div id=\"downloadbutton\" style=\"float:left\"></div></form>";
  } else {
      if (query == "") {
         html += "please enter some search words";
      } else {
         html += "no results";
      }
  }
  html += "<br>";

  // add extension navigation
  var extnav = "";
  for (var key in filetypes) {
      if (filetypes[key] > 0)  { extnav += "<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=" + query + "+filetype:"+ key + "\">" + key + "</a>(" + filetypes[key] + ")&nbsp;&nbsp;";}
  }
  if (extnav.length > 0) {
	  html += "apply a <b>filter</b> by filetype:&nbsp;&nbsp;&nbsp;&nbsp;" + extnav;
  }

  // add topic navigation  
  if (topics && topics.length > 0) {
    var topwords = "";
    for (var i = 0; i < topics.elements.length; i++) {
        topwords += "<a href=\"/yacyinteractive.html?query=" + query + "+" + topics.elements[i].name + "\">" + topics.elements[i].name + "</a> ";
        if (i > 10) break;
    }
    html += "&nbsp;&nbsp;&nbsp;topwords: " + topwords;
  }
  html += "<br><div id=\"downloadscript\"></div></span><br>";
  
  // display result
  if (firstChannel.items.length > 0) {
    var item;
    html += "<table class=\"sortable\" id=\"sortable\" border=\"0\" cellpadding=\"2\" cellspacing=\"1\" width=\"99%\">";
    html += "<tr class=\"TableHeader\" valign=\"bottom\">";
    html += "<td width=\"40\">Protocol</td>";
    html += "<td width=\"60\">Host</td>";
    html += "<td width=\"60\">Path</td>";
    html += "<td width=\"60\">Name</td>";
    html += "<td width=\"50\">Size</td>";
    //html += "<td>Description</td>";
    html += "<td width=\"50\">Date</td></tr>";
    for (var i = 0; i < firstChannel.items.length; i++) {
        item = firstChannel.items[i];
        p = item.link.indexOf("//");
	    protocol = "";
        host = "";
        path = item.link;
        if (p > 0) {
        	q = item.link.indexOf("/", p + 2);
            protocol = item.link.substring(0, p - 1);
            host = item.link.substring(p + 2, q);
            path = item.link.substring(q + 1);
        }
        html += "<tr class=\"TableCellLight\">";
        html += "<td align=\"left\">" + protocol + "</td>";
        html += "<td align=\"left\"><a href=\"" + protocol + "://" + host + "/" + "\">" + host + "</a></td>";
        html += "<td align=\"left\"><a href=\"" + item.link + "\">" + path + "</a></td>";
        title = item.title;
        if (title == "") title = path;
        html += "<td align=\"left\"><a href=\"" + item.link + "\">" + title + "</a></td>";
        html += "<td align=\"right\">" + item.sizename + "</td>";
        //html += "<td>" + item.description + "</td>";
        pd = item.pubDate;
        if (pd.substring(pd.length - 6) == " +0000") pd = pd.substring(0, pd.length - 6);
        if (pd.substring(pd.length - 9) == " 00:00:00") pd = pd.substring(0, pd.length - 9);
        if (pd.substring(pd.length - 5) == " 2010") pd = pd.substring(0, pd.length - 5);
        html += "<td align=\"right\">" + pd + "</td>";
        html += "</tr>";
    }
    html += "</table>";
  }
  document.getElementById("searchresults").innerHTML = html;
  hideDownloadScript();
}