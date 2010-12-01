function xmlhttpPost() {
    var searchform = document.forms['searchform'];
    search(searchform.query.value);
}

// static variables
var start = new Date();
var query = "";
var searchresult;
var totalResults = 0;
var filetypes;
var topics;
var script = "";

function search(search) {
  query = search;
  start = new Date();
  var self = this;
  if (window.XMLHttpRequest) { // Mozilla/Safari
    self.xmlHttpReq = new XMLHttpRequest(); 
  } else if (window.ActiveXObject) { // IE
    self.xmlHttpReq = new ActiveXObject("Microsoft.XMLHTTP");
  }
  self.xmlHttpReq.open('GET', "yacysearch.json?verify=false&resource=local&maximumRecords=1000&nav=all&query=" + query, true);
  self.xmlHttpReq.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
  self.xmlHttpReq.onreadystatechange = function() {
    if (self.xmlHttpReq.readyState == 4) {
      preparepage(self.xmlHttpReq.responseText);
    }
  }
  self.xmlHttpReq.send(null);
}

function navget(list, name) {
  for (var i = 0; i < list.length; i++) {
    if (list[i].facetname == name) return list[i];
  }
}

function preparepage(str) {
  var raw = document.getElementById("raw");
  if (raw != null) raw.innerHTML = str;
  var rsp = eval("("+str+")");
  var firstChannel = rsp.channels[0];
  searchresult = firstChannel.items;
  totalResults = firstChannel.totalResults.replace(/[,.]/,"");
//  var startIndex = firstChannel.startIndex;
//  var itemsPerPage = firstChannel.itemsPerPage;
  topics = navget(firstChannel.navigation, "topics");
  filetypes = {};
  script = "";
  
  document.getElementById("searchresults").innerHTML = resultList();
  document.getElementById("searchnavigation").innerHTML = resultStart();
  hideDownloadScript();
}

function makeDownloadScript() {
  document.getElementById("downloadscript").innerHTML = "<div style=\"float:left\"><pre>" + script + "</pre></div>";
  document.getElementById("downloadbutton").innerHTML = "<input id=\"downloadbutton\" type=\"button\" value=\"hide the download script\" onClick=\"hideDownloadScript();\"/>";
}

function hideDownloadScript() {
  document.getElementById("downloadscript").innerHTML = "";
  var dlb = document.getElementById("downloadbutton");
  if (dlb) dlb.innerHTML = "<input type=\"button\" value=\"create a download script\" onClick=\"makeDownloadScript();\"/>";
}

function resultStart() {
  var html = "<span style=\"display:block\">";
  if (totalResults > 0) {
      html += "<form><div style=\"float:left\">" + searchresult.length + " results from a total of " + totalResults + " docs in index; search time: " + ((new Date()).getTime() - start.getTime()) + " milliseconds. </div>";
      html += "<div id=\"downloadbutton\" style=\"float:left\"></div></form>";
  } else {
      if (query == "") {
         html += "please enter some search words";
      } else {
         html += "no results";
      }
  }
  html += "</span>";

  // add extension navigation
  var extnav = "";
  for (var key in filetypes) {
      if (filetypes[key] > 0)  {
        extnav += "<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=" + query + "+filetype:" + key + "\">" + key + "</a>(" + filetypes[key] + ")&nbsp;&nbsp;";
      }
  }
  if (extnav.length > 0) {
	  html += "<span style=\"display:block\">apply a <b>filter</b> by filetype:&nbsp;&nbsp;&nbsp;&nbsp;" + extnav + "</span>";
  } else {
      // check if there is a filetype constraint and offer a removal
      if (query.length >= 13 && query.substring(query.length - 13, query.length - 3) == " filetype:") {
        html += "<span style=\"display:block\"><a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=" + query.substring(0, query.length - 12) + "\">remote the filter '" + query.substring(query.length - 12) + "'</a></span>";
      }
  }

  // add topic navigation
  /*
  if (topics && topics.length > 0) {
    var topwords = "";
    for (var i = 0; i < topics.elements.length; i++) {
        topwords += "<a href=\"/yacyinteractive.html?query=" + query + "+" + topics.elements[i].name + "\">" + topics.elements[i].name + "</a> ";
        if (i > 10) break;
    }
    html += "&nbsp;&nbsp;&nbsp;topwords: " + topwords;
  }
  */
  return html;
}

function resultList() {
  var html = "";
  if (searchresult.length > 0) {
    var item;
    html += "<table class=\"sortable\" id=\"sortable\" border=\"0\" cellpadding=\"0\" cellspacing=\"1\" width=\"99%\">";
    html += "<tr class=\"TableHeader\" valign=\"bottom\">";
    html += "<td width=\"40\">Protocol</td>";
    html += "<td width=\"60\">Host</td>";
    html += "<td width=\"260\">Path</td>";
    html += "<td width=\"360\">Name</td>";
    html += "<td width=\"60\">Size</td>";
    //html += "<td>Description</td>";
    html += "<td width=\"70\">Date</td></tr>";
    for (var i = 0; i < searchresult.length; i++) {
      var item = searchresult[i];
      html += resultLine(item);
    }
    html += "</table>";
  }
  return html;
}

function resultLine(item) {
  var html = "";
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
  if (path.length >= 40) path = path.substring(0, 18) + "..." + path.substring(path.length - 19);
  html += "<tr class=\"TableCellLight\">";
  html += "<td align=\"left\">" + protocol + "</td>";
  html += "<td align=\"left\"><a href=\"" + protocol + "://" + host + "/" + "\">" + host + "</a></td>";
  html += "<td align=\"left\"><a href=\"" + item.link + "\">" + path + "</a></td>";
  title = item.title;
  if (title == "") title = path;
  if (title.length >= 60) title = title.substring(0, 28) + "..." + title.substring(title.length - 29);
  html += "<td align=\"left\"><a href=\"" + item.link + "\">" + title + "</a></td>";
  html += "<td align=\"right\">" + item.sizename + "</td>";
  //html += "<td>" + item.description + "</td>";
  pd = item.pubDate;
  if (pd.substring(pd.length - 6) == " +0000") pd = pd.substring(0, pd.length - 6);
  if (pd.substring(pd.length - 9) == " 00:00:00") pd = pd.substring(0, pd.length - 9);
  if (pd.substring(pd.length - 5) == " 2010") pd = pd.substring(0, pd.length - 5);
  html += "<td align=\"right\">" + pd + "</td>";
  html += "</tr>";
  
  // update navigation
  if (item.link && item.link.length > 4) {
    ext = item.link.substring(item.link.length - 4);
    if (ext.charAt(0) == "." && ext.charAt(3) != "/") {
      ext = ext.substring(1).toLowerCase();
      var count = filetypes[ext];
      if (count) filetypes[ext]++; else filetypes[ext] = 1;
    }
  }
  for (var key in filetypes) {
    if (query.indexOf("filetype:" + key) >= 0) delete filetypes[key];
  }
  
  // update download script
  script += "curl -OL \"" + item.link + "\"\n";
  
  // return table row
  return html;
}
