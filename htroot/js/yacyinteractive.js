function xmlhttpPost() {
    var searchform = document.forms['searchform'];
    var rsslink = document.getElementById("rsslink");
    if (rsslink != null) rsslink.href="yacysearch.rss?query=" + searchform.query.value;
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
var modifier = "";
var modifiertype = "";

function search(search) {
  query = search;
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
    document.getElementById("searchresults").innerHTML = resultImages();
  } else {
    document.getElementById("searchresults").innerHTML = resultList();
  }
  document.getElementById("searchnavigation").innerHTML = resultNavigation();
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
  if (totalResults > 0) {
      html += "<div>" + searchresult.length + " results from a total of " + totalResults + " docs in index (not showing offline resources); search time: " + ((new Date()).getTime() - start.getTime()) + " milliseconds.&nbsp;";
      html += "<div id=\"downloadbutton\" style=\"inline\"></div></div>";
  } else {
      if (query == "") {
         html += "please enter some search words<br\>or use the following predefined search queries:<br\>";
         html += "find images: ";
         html += "(<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=png+filetype:png\">png</a>),";
         html += "(<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=gif+filetype:gif\">gif</a>),";
         html += "(<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=jpg+filetype:jpg\">jpg</a>)<br>";
         html += "list: ";
         html += "<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=pdf+/date+filetype:pdf\">recent pdf</a><br>";
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
        extnav += "<a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=" + query + "+filetype:" + key + "\">" + key + "</a>(" + filetypes[key] + ")&nbsp;&nbsp;";
      }
  }
  if (extnav.length > 0) {
	  html += "<div style=\"display:block\">apply a <b>filter</b> by filetype:&nbsp;&nbsp;&nbsp;&nbsp;" + extnav + "</div>";
  } else {
      // check if there is a filetype constraint and offer a removal
      if (modifier != "") {
        html += "<span style=\"display:block\"><a style=\"text-decoration:underline\" href=\"/yacyinteractive.html?query=" + query.substring(0, query.length - 12) + "\">remove the filter '" + modifier + "'</a></span>";
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
    html += "<table class=\"sortable\" id=\"sortable\" border=\"0\" cellpadding=\"0\" cellspacing=\"1\" width=\"99%\">";
    html += "<tr class=\"TableHeader\" valign=\"bottom\"><td width=\"10\">count</td><td width=\"40\">Protocol</td><td width=\"60\">Host</td><td width=\"260\">Path</td><td width=\"360\">Name</td><td width=\"60\">Size</td><td width=\"75\">Date</td></tr>";
    for (var i = 0; i < searchresult.length; i++) { html += resultLine("row", searchresult[i], i + 1); }
    html += "</table>";
  }
  return html;
}

function resultImages() {
  var html = "";
  for (var i = 0; i < searchresult.length; i++) { html += resultLine("image", searchresult[i]); }
  return html;
}

function resultLine(type, item, linenumber) {
  // evaluate item
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
    html += "<tr class=\"TableCellLight\">";
    html += "<td align=\"left\">" + linenumber + "</td>";
    html += "<td align=\"left\">" + protocol + "</td>";
    html += "<td align=\"left\"><a href=\"" + protocol + "://" + host + "/" + "\">" + host + "</a></td>";
    html += "<td align=\"left\"><a href=\"" + item.link + "\">" + path + "</a></td>";
    html += "<td align=\"left\"><a href=\"" + item.link + "\">" + title + "</a></td>";
    html += "<td align=\"right\">" + item.sizename + "</td>";
    //html += "<td>" + item.description + "</td>";
    html += "<td align=\"right\">" + pd + "</td>";
    html += "</tr>";
  }
  if (type == "image") {
    html += "<div style=\"float:left\">";
    html += "<a href=\"" + item.link + "\" class=\"thumblink\" onclick=\"return hs.expand(this)\">";
    html += "<img src=\"/ViewImage.png?maxwidth=96&amp;maxheight=96&amp;code=" + item.code + "\" alt=\"" + title + "\" />";
    html += "</a>";
    var name = title;
    while ((p = name.indexOf("/")) >= 0) { name = name.substring(p + 1); }
    html += "<div class=\"highslide-caption\"><a href=\"" + item.link + "\">" + name + "</a><br /><a href=\"" + protocol + "://" + host + "/" + "\">" + host + "</a></div>";
    html += "</div>";
  }
    
  // return entry
  return html;
}
