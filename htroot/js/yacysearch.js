function Progressbar(length, parent) {
  // the description (displayed above the progressbar while loading the results), should be translated
  var DESCRIPTION_STRING = "Loading results...";

  // the number of steps of the bar
  this.length = length;
  // the current position, expressed in steps (so 100% = length)
  this.position = 0;
  // the current percentage of the bar
  this.percentage = 0


  // use this function to display the progress, because it updates everything
  this.step = function(count) {
    this.position += count;
    // update the bar
    this.percentage = this.position*(100/this.length);
    this.fill.style.width = this.percentage + "%";

    // if the end is reached, the bar is hidden/removed
    if(this.position==this.length)
      removeAllChildren(this.element);
  }

  // the actual progressbar
  var bar = document.createElement("div");
  bar.className = "ProgressBar";
  bar.style.width = "100%";
  bar.style.height = "20px";
  bar.style.margin = "10px auto";
  bar.style.textAlign = "left";

  // the actual bar
  this.fill = document.createElement("div");
  this.fill.className = "ProgressBarFill";
  this.fill.style.width = "0%"
  bar.appendChild(this.fill);

  // a description for the bar
  var description = document.createTextNode(DESCRIPTION_STRING);
  var textcontainer = document.createElement("strong");
  textcontainer.appendChild(description);

  // the container for the elements used by the Progressbar
  this.element = document.createElement("div");
  this.element.style.textAlign = "center";
  // get hasLayout in IE, needed because of the percentage as width of the bar
  this.element.className = "gainlayout";
  this.element.appendChild(textcontainer);
  this.element.appendChild(bar);
  parent.appendChild(this.element);
}

function AllTextSnippets(query) {
	var span = document.getElementsByTagName("span");
	for(var x=0;x<span.length;x++) {
		if (span[x].className == 'snippetLoading') {
				var url = document.getElementById("url" + span[x].id.substring(1));
				requestTextSnippet(url,query);
		}
	}
}

function AllMediaSnippets(query, mediatype) {
	var span = document.getElementsByTagName("span");
	for(var x=0;x<span.length;x++) {
		if (span[x].className == 'snippetLoading') {
				var url = document.getElementById("url" + span[x].id);
				requestMediaSnippet(url,query,mediatype);
		}
	}
}

function AllImageSnippets(urls, query) {
  document.getElementById("linkcount").innerHTML = 0;
  var container = document.getElementById("results");
  var progressbar = new Progressbar(urls.length, container);
  for(url in urls) {
    requestImageSnippet(urls[url],query,progressbar);
  }
}

function requestTextSnippet(url, query){
	var request=createRequestObject();
	request.open('get', '/xml/snippet.xml?url=' + escape(url) + '&remove=true&media=text&search=' + escape(query),true);
	request.onreadystatechange = function () {handleTextState(request)};
	request.send(null);
}

function requestMediaSnippet(url, query, mediatype){
	var request=createRequestObject();
	request.open('get', '/xml/snippet.xml?url=' + escape(url) + '&remove=true&media=' + escape(mediatype) + '&search=' + escape(query),true);
	request.onreadystatechange = function () {handleMediaState(request)};
	request.send(null);
}

function requestImageSnippet(url, query, progressbar){
	var request=createRequestObject();
	request.open('get', '/xml/snippet.xml?url=' + escape(url) + '&remove=true&media=image&search=' + escape(query),true);
	request.onreadystatechange = function () {handleImageState(request, progressbar)};
	request.send(null);
}

function show_hidden_results(){
  var results = document.getElementsByTagName("div");
  for (var i = 0; i < results.length; i++) {
    var result = results[i];
    if (result.className == "searchresults hidden")
      result.className = "searchresults";
  }
  document.getElementById("hidden_results").innerHTML = "";
}
function handleTextState(req) {
    if(req.readyState != 4){
		return;
	}
	
	var response = req.responseXML;
	
	var snippetText = response.getElementsByTagName("text")[0].firstChild.data;
	var urlHash = response.getElementsByTagName("urlHash")[0].firstChild.data;
	var status = response.getElementsByTagName("status")[0].firstChild.data;

	
	var span = document.getElementById("h" + urlHash);
	removeAllChildren(span);
	//span.removeChild(span.firstChild);
	
	if (status < 11) {
		span.className = "snippetLoaded";
	} else {
		span.className = "snippetError";
		span.parentNode.parentNode.className = "searchresults hidden";
		document.getElementById("hidden_results").innerHTML='Some results were hidden, because they do not contain your searchwords anymore, or because they are not accessible. Click here to <a href="javascript:show_hidden_results()">show them</a>';
	}
	
	// set URL to favicon (if a link-tag was found in the document)
	if (response.getElementsByTagName("favicon")[0].firstChild != null) {
		var img = document.getElementById("f" + urlHash);
		img.src = "ViewImage.png?width=16&height=16&url=" + response.getElementsByTagName("favicon")[0].firstChild.data;
	}

	// replace "<b>" text by <strong> node
	var pos1=snippetText.indexOf("<b>");
	var pos2=snippetText.indexOf("</b>");
	while (pos1 >= 0 && pos2 > pos1) {
		leftString = document.createTextNode(snippetText.substring(0, pos1)); //other text
		if (leftString != "") span.appendChild(leftString);

		//add the bold text
		strongNode=document.createElement("strong");
		middleString=document.createTextNode(snippetText.substring(pos1 + 3, pos2));
		strongNode.appendChild(middleString);
		span.appendChild(strongNode);
		
		// cut out left and middle and go on with remaining text
		snippetText=snippetText.substring(pos2 + 4);
		pos1=snippetText.indexOf("<b>");
		pos2=snippetText.indexOf("</b>");
	}
	
	// add remaining string
	if (snippetText != "") {
		span.appendChild(document.createTextNode(snippetText));
	}
}

function handleMediaState(req) {
    if(req.readyState != 4){
		return;
	}
	
	var response = req.responseXML;
	var urlHash = response.getElementsByTagName("urlHash")[0].firstChild.data;
	var links = response.getElementsByTagName("links")[0].firstChild.data;
	var span = document.getElementById(urlHash)
	removeAllChildren(span);
	
	if (links > 0) {
		span.className = "snippetLoaded";
		for (i = 0; i < links; i++) {
			var type = response.getElementsByTagName("type")[i].firstChild.data;
			var href = response.getElementsByTagName("href")[i].firstChild.data;
			var name = response.getElementsByTagName("name")[i].firstChild.data;
			var attr = response.getElementsByTagName("attr")[i].firstChild.data;

			var nameanchor = document.createElement("a");
			nameanchor.setAttribute("href", href);
			nameanchor.appendChild(document.createTextNode(name));
			
			var linkanchor = document.createElement("a");
			linkanchor.setAttribute("href", href);
			linkanchor.appendChild(document.createTextNode(href));
			
			var col1 = document.createElement("td");
			col1.setAttribute("width", "200");
			col1.appendChild(nameanchor);
			
			var col2 = document.createElement("td");
			col2.setAttribute("width", "500");
			col2.appendChild(linkanchor);
			
			var row = document.createElement("tr");
			row.className = "TableCellDark";
			row.appendChild(col1);
			row.appendChild(col2);

			var table = document.createElement("table");
			table.appendChild(row);
			span.appendChild(table);
		}
	} else {
		span.className = "snippetError";
		span.appendChild(document.createTextNode(""));
	}
}

function handleImageState(req, progressbar) {
  if(req.readyState != 4)
		return;
	
	var response = req.responseXML;
  // on errors, the result might not contain the expected elements and would throw an error, so we check for it here
  if (response.getElementsByTagName("urlHash")) {
    // the urlHash is not needed anymore at the moment
	  //var urlHash = response.getElementsByTagName("urlHash")[0].firstChild.data;
	  var links = response.getElementsByTagName("links")[0].firstChild.data;
	  var div = document.getElementById("results")
	
	  if (links > 0) {
		  for (i = 0; i < links; i++) {
			  var type = response.getElementsByTagName("type")[i].firstChild.data;
			  var href = response.getElementsByTagName("href")[i].firstChild.data;
			  var name = response.getElementsByTagName("name")[i].firstChild.data;
			  var attr = response.getElementsByTagName("attr")[i].firstChild.data;

            // <a href="#[url]#"><img src="/ViewImage.png?maxwidth=96&amp;maxheight=96&amp;url=#[url]#" /></a><br /><a href="#[url]#">#[name]#</a>
			  var img = document.createElement("img");
			  img.setAttribute("src", "/ViewImage.png?maxwidth=96&maxheight=96&url=" + href);
			  img.setAttribute("alt", name);
			
			  var imganchor = document.createElement("a");
			  imganchor.setAttribute("href", href);
			  imganchor.className = "thumblink";
			  imganchor.appendChild(img);
			
			  var nameanchor = document.createElement("a");
			  nameanchor.setAttribute("href", href);
			  nameanchor.appendChild(document.createTextNode(name));
			
			
			  var textcontainer = document.createElement("div");
			  textcontainer.className = "TableCellDark";
			  textcontainer.appendChild(nameanchor);
			
			  var thumbcontainer = document.createElement("div");
			  thumbcontainer.className = "thumbcontainer";
			  thumbcontainer.appendChild(imganchor);
			  thumbcontainer.appendChild(textcontainer);
			  div.appendChild(thumbcontainer);
			  //span.appendChild(imganchor);

        document.getElementById("linkcount").innerHTML++;
		  }
    }
  }
  progressbar.step(1);
}


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
