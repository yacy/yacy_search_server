// parser for rss2

function RSS2Enclosure(encElement) {
  if (encElement == null) {
    this.url = null;
    this.length = null;
    this.type = null;
  } else {
    this.url = encElement.getAttribute("url");
    this.length = encElement.getAttribute("length");
    this.type = encElement.getAttribute("type");
  }
}

function RSS2Guid(guidElement) {
  if (guidElement == null) {
    this.isPermaLink = null;
    this.value = null;
  } else {
    this.isPermaLink = guidElement.getAttribute("isPermaLink");
    this.value = guidElement.childNodes[0].nodeValue;
  }
}

function RSS2Source(souElement) {
  if (souElement == null) {
    this.url = null;
    this.value = null;
  } else {
    this.url = souElement.getAttribute("url");
    this.value = souElement.childNodes[0].nodeValue;
  }
}

function RSS2Item(itemxml) {
  //required
  this.title;
  this.link;
  this.description;

  //optional vars
  this.author;
  this.comments;
  this.pubDate;

  //optional objects
  this.category;
  this.enclosure;
  this.guid;
  this.source;

  var properties = new Array("title", "link", "description", "author", "comments", "pubDate");
  //var properties = new Array("title", "link");
  var tmpElement = null;
  for (var i=0; i<properties.length; i++) {
    tmpElement = itemxml.getElementsByTagName(properties[i])[0];
    if ((tmpElement != null) && (tmpElement.firstChild))
      eval("this."+properties[i]+"=tmpElement.firstChild.nodeValue");
  }

  this.category = new RSS2Category(itemxml.getElementsByTagName("category")[0]);
  this.enclosure = new RSS2Enclosure(itemxml.getElementsByTagName("enclosure")[0]);
  this.guid = new RSS2Guid(itemxml.getElementsByTagName("guid")[0]);
  this.source = new RSS2Source(itemxml.getElementsByTagName("source")[0]);
}

function RSS2Category(catElement) {
  if (catElement == null) {
    this.domain = null;
    this.value = null;
  } else {
    this.domain = catElement.getAttribute("domain");
    this.value = catElement.childNodes[0].nodeValue;
  }
}

function RSS2Image(imgElement) {
  if (imgElement == null) {
  this.url = null;
  this.link = null;
  this.width = null;
  this.height = null;
  this.description = null;
  } else {
    imgAttribs = new Array("url","title","link","width","height","description");
    for (var i=0; i<imgAttribs.length; i++)
      if (imgElement.getAttribute(imgAttribs[i]) != null)
        eval("this."+imgAttribs[i]+"=imgElement.getAttribute("+imgAttribs[i]+")");
  }
}

function RSS2Channel(rssxml) {
  //required
  this.title;
  this.link;
  this.description;

  //array of RSS2Item objects
  this.items = new Array();

  //optional vars
  this.language;
  this.copyright;
  this.managingEditor;
  this.webMaster;
  this.pubDate;
  this.lastBuildDate;
  this.generator;
  this.docs;
  this.ttl;
  this.rating;

  //optional objects
  this.category;
  this.image;

  var chanElement = rssxml.getElementsByTagName("channel")[0];
  var itemElements = rssxml.getElementsByTagName("item");

  for (var i=0; i<itemElements.length; i++) {
    Item = new RSS2Item(itemElements[i]);
    this.items.push(Item);
    //chanElement.removeChild(itemElements[i]);
  }

  var properties = new Array("title", "link", "description", "language", "copyright", "managingEditor", "webMaster", "pubDate", "lastBuildDate", "generator", "docs", "ttl", "rating");
  var tmpElement = null;
  for (var i=0; i<properties.length; i++) {
    tmpElement = chanElement.getElementsByTagName(properties[i])[0];
    if ((tmpElement!= null) && (tmpElement.firstChild))
      eval("this."+properties[i]+"=tmpElement.firstChild.nodeValue");
  }

  this.category = new RSS2Category(chanElement.getElementsByTagName("category")[0]);
  this.image = new RSS2Image(chanElement.getElementsByTagName("image")[0]);
}


// loader and display method for rss
// this needs ajax.js and a method showRSS(RSS) to display a rss file
var xhr;

function getRSS(url){
  xhr = createRequestObject();
  xhr.open("GET",url,true);
  xhr.setRequestHeader("Cache-Control", "no-cache");
  xhr.setRequestHeader("Pragma", "no-cache");
  xhr.onreadystatechange = processRSS;
  xhr.send(null);
}

function processRSS() {
  if (xhr.readyState == 4) {
    if (xhr.status == 200) {
      if ((xhr.responseText != null) && (xhr.responseXML != null)) {
        RSS = new RSS2Channel(xhr.responseXML);
        showRSS(RSS);
      } else {
        alert("rss file not found.");
        return false;
      }
    }
    else
      //alert("Error code " + xhr.status + " received: " + xhr.statusText);
      return false;
  }
}

