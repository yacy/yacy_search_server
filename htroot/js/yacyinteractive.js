function xmlhttpPost() {
    var searchform = document.forms['searchform'];
    var rsslink = document.getElementById("rsslink");
    if (rsslink != null) rsslink.href="yacysearch.rss?query=" + searchform.query.value;
    search(searchform.query.value, searchform.maximumRecords.value, searchform.startRecord.value);
}

// static variables
var start = new Date();
var query = "";
var maximumRecords = "1000";
var startRecord = "0";
var searchresult;
var totalResults = 0;
var filetypes;
var topics;
var script = "";
var modifier = "";
var modifiertype = "";

function search(search, count, offset) {
  var navhtml = document.getElementById("searchnavigation");
  if (navhtml != null) navhtml.innerHTML = "<div>loading from local index...</div>";
  query = search;
  maximumRecords = count;
  if (count == "") maximumRecords = 100;
  startRecord = offset;
  if (offset == "") startRecord = 0;
  start = new Date();
  if (query == null || query == "") {
    return;
  }
  var self = this;
  if (window.XMLHttpRequest) { // Mozilla/Safari
    self.xmlHttpReq = new XMLHttpRequest(); 
  } else if (window.ActiveXObject) { // IE
    self.xmlHttpReq = new ActiveXObject("Microsoft.XMLHTTP");
  }
  self.xmlHttpReq.open('GET', "yacysearch.json?verify=false&resource=local&nav=all&contentdom=all&maximumRecords=" + maximumRecords + "&startRecord=" + startRecord + "&query=" + query, true);
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
  document.getElementById("searchnavigation").innerHTML = "<div>parsing result...</div>";
  var raw = document.getElementById("raw");
  if (raw != null) raw.innerHTML = str;
  var rsp = eval("(" + str + ")");
  var firstChannel = rsp.channels[0];
  searchresult = firstChannel.items;
  totalResults = firstChannel.totalResults.replace(/[,.]/,"");
//  var startIndex = firstChannel.startIndex;
//  var itemsPerPage = firstChannel.itemsPerPage;
  topics = navget(firstChannel.navigation, "topics");
  filetypes = {};
  script = "";
  if (query.length >= 13 && query.substring(query.length - 13, query.length - 3) == " filetype:") {
    modifier = query.substring(query.length - 12);
  }
  if (modifier != "") modifiertype = modifier.substring(modifier.length - 3)


  if (modifiertype == "png" || modifiertype == "gif" || modifiertype == "jpg") {
    var tt = resultImages();
    document.getElementById("searchresults").innerHTML = tt;
  } else {
    var tt = resultList();
    document.getElementById("searchresults").innerHTML = tt;
  }
  var tt = resultNavigation();
  document.getElementById("searchnavigation").innerHTML = tt;
  document.getElementById("serverlist").innerHTML = "";
  hideDownloadScript();
}

function makeDownloadScript() {
  document.getElementById("downloadscript").innerHTML = "<div><pre>" + script + "</pre><br/></div>";
  document.getElementById("downloadbutton").innerHTML = "<input id=\"downloadbutton\" type=\"button\" value=\"hide the download script\" onClick=\"hideDownloadScript();\"/>";
}

function hideDownloadScript() {
  document.getElementById("downloadscript").innerHTML = "";
  var dlb = document.getElementById("downloadbutton");
  if (dlb) dlb.innerHTML = "<input type=\"button\" value=\"create a download script\" onClick=\"makeDownloadScript();\"/>";
}

function resultNavigation() {
  var html = "";
  if (searchresult.length > totalResults) totalResults = searchresult.length;
  if (totalResults > 0) {
      html += "<div>" + searchresult.length + " results from a total of " + totalResults + " docs in index; search time: " + ((new Date()).getTime() - start.getTime()) + " milliseconds.&nbsp;";
      html += "<div style=\"clear:left\">";
      html += "<div style=\"display:inline\" id=\"downloadbutton\"></div>";
      if (maximumRecords != 10 && totalResults >= 10) html += "<input type=\"button\" value=\"10 results\" onClick=\"window.location.href='/yacyinteractive.html?query=" + query + "&startRecord=" + startRecord + "&maximumRecords=10'\"/>";
      if (maximumRecords != 100 && totalResults >= 100) html += "<input type=\"button\" value=\"100 results\" onClick=\"window.location.href='/yacyinteractive.html?query=" + query + "&startRecord=" + startRecord + "&maximumRecords=100'\"/>";
      if (maximumRecords != 1000 && totalResults >= 1000) html += "<input type=\"button\" value=\"1000 results\" onClick=\"window.location.href='/yacyinteractive.html?query=" + query + "&startRecord=" + startRecord + "&maximumRecords=1000'\"/>";
      if (totalResults <= 10000 && maximumRecords < totalResults) html += "<input type=\"button\" value=\"all results\" onClick=\"window.location.href='/yacyinteractive.html?query=" + query + "&startRecord=" + startRecord + "&maximumRecords=" + totalResults + "'\"/>";
      html += "</div>"; // for inline
      html += "</div>"; // for result statistic wrapper
  } else {
      if (query == "") {
         html += "please enter some search words<br\>or use the following predefined search queries:<br\>";
         html += "find images: ";
         html += "(<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=png+filetype:png&startRecord=" + startRecord + "&maximumRecords=" + maximumRecords + "\">png</a>),";
         html += "(<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=gif+filetype:gif&startRecord=" + startRecord + "&maximumRecords=" + maximumRecords + "\">gif</a>),";
         html += "(<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=jpg+filetype:jpg&startRecord=" + startRecord + "&maximumRecords=" + maximumRecords + "\">jpg</a>)<br>";
         html += "list: ";
         html += "<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=pdf+/date+filetype:pdf&startRecord=" + startRecord + "&maximumRecords=" + maximumRecords + "\">recent pdf</a><br>";
         //html += "<iframe src=\"rssTerminal.html?set=LOCALINDEXING&amp;width=600px&amp;height=180px&amp;maxlines=20&amp;maxwidth=120\" ";
         //html += "style=\"width:600px;height:180px;margin:0px;\" scrolling=\"no\" name=\"newsframe\"></iframe>";
      } else {
         html += "no results";
      }
  }

  // add extension navigation
  var extnav = "";
  for (var key in filetypes) {
      if (filetypes[key] > 0)  {
        extnav += "<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=" + query + "+filetype:" + key + "&startRecord=" + startRecord + "&maximumRecords=" + maximumRecords + "\">" + key + "</a>(" + filetypes[key] + ")&nbsp;&nbsp;";
      }
  }
  if (extnav.length > 0) {
	  html += "<div style=\"display:block\">apply a <b>filter</b> by filetype:&nbsp;&nbsp;&nbsp;&nbsp;" + extnav + "</div>";
  } else {
      // check if there is a filetype constraint and offer a removal
      if (modifier != "") {
        html += "<span style=\"display:block\"><a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=" + query.substring(0, query.length - 12) + "&startRecord=" + startRecord + "&maximumRecords=" + maximumRecords + "\">remove the filter '" + modifier + "'</a></span>";
      }
  }

  // add topic navigation
  /*
  if (topics && topics.length > 0) {
    var topwords = "";
    for (var i = 0; i < topics.elements.length; i++) {
        topwords += "<a href=\"/yacyinteractive.html?query=" + query + "+" + topics.elements[i].name + "&startRecord=" + startRecord + "&maximumRecords=" + maximumRecords + "\">" + topics.elements[i].name + "</a> ";
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
    document.getElementById("searchnavigation").innerHTML = "<div>found " + searchresult.length + " documents, preparing table...</div>";
    html += "<table class=\"sortable\" id=\"sortable\" border=\"0\" cellpadding=\"0\" cellspacing=\"1\" width=\"99%\">";
    html += "<tr class=\"TableHeader\" valign=\"bottom\"><td width=\"10\">count</td><td width=\"40\">Protocol</td><td width=\"60\">Host</td><td width=\"260\">Path</td><td width=\"360\">Name</td><td width=\"60\">Size</td><td width=\"75\">Date</td></tr>";
    for (var i = 0; i < searchresult.length; i++) { html += resultLine("row", searchresult[i], i + 1); }
    html += "</table>";
  }
  return html;
}

function resultImages() {
  var html = "";
  document.getElementById("searchnavigation").innerHTML = "<div>found " + searchresult.length + " images, preparing...</div>";
  for (var i = 0; i < searchresult.length; i++) { html += resultLine("image", searchresult[i]); }
  return html;
}

function resultLine(type, item, linenumber) {
  // evaluate item
  if (item == null) return "";
  if (item.link == null) return "";
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
  title = item.title;
  q = path.lastIndexOf("/");
  if (q > 0) path = path.substring(0, q);
  path = unescape(path);
  if (path.length >= 40) path = path.substring(0, 18) + "..." + path.substring(path.length - 19);
  if (title == "") title = path;
  if (title.length >= 60) title = title.substring(0, 28) + "..." + title.substring(title.length - 29);
  pd = item.pubDate;
  if (pd.substring(pd.length - 6) == " +0000") pd = pd.substring(0, pd.length - 6);
  if (pd.substring(pd.length - 9) == " 00:00:00") pd = pd.substring(0, pd.length - 9);
  if (pd.substring(pd.length - 5) == " 2010") pd = pd.substring(0, pd.length - 5);
    
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
  if (item.link.indexOf("smb://") >= 0) script += "smbget -n -a -r \"" + item.link + "\"\n"; else script += "curl -OL \"" + item.link + "\"\n";
  
  // make table row
  var html = "";
  if (type == "row") {
    html += "<tr class=\"TableCellDark\">";
    html += "<td align=\"left\">" + linenumber + "</td>";
    html += "<td align=\"left\">" + protocol + "</td>";
    html += "<td align=\"left\"><a href=\"" + protocol + "://" + host + "/" + "\">" + host + "</a></td>";
    html += "<td align=\"left\"><a href=\"" + protocol + "://" + host + "/" + path + "/\">" + path + "</a></td>";
    html += "<td align=\"left\"><a href=\"" + item.link + "\">" + title + "</a></td>";
    if (item.sizename == "-1 bytes") html += "<td></td>"; else html += "<td align=\"right\">" + item.sizename + "</td>";
    //html += "<td>" + item.description + "</td>";
    html += "<td align=\"right\">" + pd + "</td>";
    html += "</tr>";
  }
  if (type == "image") {
    html += "<div style=\"float:left\">";
    html += "<a href=\"" + item.link + "\" class=\"thumblink\" onclick=\"return hs.expand(this)\">";
    //html += "<img src=\"/ViewImage.png?maxwidth=96&amp;maxheight=96&amp;code=" + item.code + "\" alt=\"" + title + "\" />";
    html += "<img src=\"" + item.link + "\" width=\"96\" height=\"96\" alt=\"" + title + "\" />";
    html += "</a>";
    var name = title;
    while ((p = name.indexOf("/")) >= 0) { name = name.substring(p + 1); }
    html += "<div class=\"highslide-caption\"><a href=\"" + item.link + "\">" + name + "</a><br /><a href=\"" + protocol + "://" + host + "/" + "\">" + host + "</a></div>";
    html += "</div>";
  }
    
  // return entry
  return html;
}
