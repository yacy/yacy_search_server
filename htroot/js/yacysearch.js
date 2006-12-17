function AllTextSnippets() {
    var query = document.getElementsByName("former")[0].value;
    
	var span = document.getElementsByTagName("span");
	for(var x=0;x<span.length;x++) {
		if (span[x].className == 'snippetLoading') {
				var url = document.getElementById("url" + span[x].id);
				requestTextSnippet(url,query);
		}
	}
}

function AllMediaSnippets(mediatype) {
    var query = document.getElementsByName("former")[0].value;
    
	var span = document.getElementsByTagName("span");
	for(var x=0;x<span.length;x++) {
		if (span[x].className == 'snippetLoading') {
				var url = document.getElementById("url" + span[x].id);
				requestMediaSnippet(url,query,mediatype);
		}
	}
}

function AllImageSnippets() {
    var query = document.getElementsByName("former")[0].value;
    
	var span = document.getElementsByTagName("span");
	for(var x=0;x<span.length;x++) {
		if (span[x].className == 'snippetLoading') {
				var url = document.getElementById("url" + span[x].id);
				requestImageSnippet(url,query);
		}
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

function requestImageSnippet(url, query){
	var request=createRequestObject();
	request.open('get', '/xml/snippet.xml?url=' + escape(url) + '&remove=true&media=image&search=' + escape(query),true);
	request.onreadystatechange = function () {handleImageState(request)};
	request.send(null);
}

function handleTextState(req) {
    if(req.readyState != 4){
		return;
	}
	
	var response = req.responseXML;
	
	var snippetText = response.getElementsByTagName("text")[0].firstChild.data;
	var urlHash = response.getElementsByTagName("urlHash")[0].firstChild.data;
	var status = response.getElementsByTagName("status")[0].firstChild.data;
	
	var span = document.getElementById(urlHash)
	removeAllChildren(span);
	//span.removeChild(span.firstChild);
	
	if (status < 11) {
		span.className = "snippetLoaded";
	} else {
		span.className = "snippetError";
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
			row.setAttribute("class", "TableCellDark");
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

function handleImageState(req) {
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

            // <a href="#[url]#"><img src="/ViewImage.png?maxwidth=96&amp;maxheight=96&amp;url=#[url]#" /></a><br /><a href="#[url]#">#[name]#</a>
			var img = document.createElement("img");
			img.setAttribute("src", "/ViewImage.png?maxwidth=96&maxheight=96&url=" + href);
			img.setAttribute("alt", name);
			
			var imganchor = document.createElement("a");
			imganchor.setAttribute("href", href);
			imganchor.appendChild(img);
			
			var nameanchor = document.createElement("a");
			nameanchor.setAttribute("href", href);
			nameanchor.appendChild(document.createTextNode(name));
			
			var col1 = document.createElement("td");
			col1.setAttribute("width", "100");
			col1.appendChild(imganchor);
			
			var row1 = document.createElement("tr");
			//row1.setAttribute("class", "TableCellLight");
			row1.appendChild(col1);
			
			var col2 = document.createElement("td");
			col2.setAttribute("width", "100");
			col2.appendChild(nameanchor);
			
			var row2 = document.createElement("tr");
			row2.setAttribute("class", "TableCellDark");
			row2.appendChild(col2);

			var table = document.createElement("table");
			table.setAttribute("class", "imgtable");
			table.appendChild(row1);
			table.appendChild(row2);
			span.appendChild(table);
			//span.appendChild(imganchor);
		}
	} else {
		span.className = "snippetError";
		span.appendChild(document.createTextNode(""));
	}
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