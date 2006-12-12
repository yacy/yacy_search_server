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

function AllMediaSnippets() {
    var query = document.getElementsByName("former")[0].value;
    
	var span = document.getElementsByTagName("span");
	for(var x=0;x<span.length;x++) {
		if (span[x].className == 'snippetLoading') {
				var url = document.getElementById("url" + span[x].id);
				requestMediaSnippet(url,query);
		}
	}
}

function requestTextSnippet(url, query){
	var request=createRequestObject();
	request.open('get', '/xml/snippet.xml?url=' + escape(url) + '&remove=true&media=text&search=' + escape(query),true);
	request.onreadystatechange = function () {handleTextState(request)};
	request.send(null);
}

function requestMediaSnippet(url, query){
	var request=createRequestObject();
	request.open('get', '/xml/snippet.xml?url=' + escape(url) + '&remove=true&media=audio&search=' + escape(query),true);
	request.onreadystatechange = function () {handleMediaState(request)};
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
			var width1 = document.createAttribute("width");
			width1.nodeValue = 200;
			col1.setAttributeNode(width1);
			col1.appendChild(nameanchor);
			var col2 = document.createElement("td");
			var width2 = document.createAttribute("width");
			width2.nodeValue = 500;
			col2.setAttributeNode(width2);
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