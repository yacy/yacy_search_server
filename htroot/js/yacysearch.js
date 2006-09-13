function AllSnippets() {
    var query = document.getElementsByName("former")[0].value;
    
	var span = document.getElementsByTagName("span");
	for(var x=0;x<span.length;x++) {
		if (span[x].className == 'snippetLoading') {
				var url = document.getElementById("url" + span[x].id);
				requestSnippet(url,query);
		}
	}
}


function requestSnippet(url, query){
	var request=createRequestObject();
	request.open('get', '/xml/snippet.xml?url=' + escape(url) + '&search=' + escape(query),true);
	request.onreadystatechange = function () {handleState(request)};
	request.send(null);
}

function handleState(req) {
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
		//span.setAttribute("class", "snippetLoaded");
	} else {
		span.className = "snippetError";
		//span.setAttribute("class", "snippetError");
	}
	
	var pos=snippetText.indexOf("<b>");
	var pos2=snippetText.indexOf("</b>");
	var tmpNode=null;
	var tmpNode2=null;
	while(pos >= 0 && pos2 > pos){
		tmpNode = document.createTextNode(snippetText.substring(0, pos); //other text
		if(tmpNode != ""){
			span.appendChild(tmpNode);
		}
		//add the bold text
		tmpNode=document.createElement("strong")
		tmpNode2=document.createTextNode(snippetText.substring(pos+3,pos2);
		tmpNode.append(tmpNode2);
		span.appendChild(tmpNode)
		
		snippetText=substring(pos2+4)
		var pos=snippetText.indexOf("<b>");
		var pos2=snippetText.indexOf("</b>");
	}
	if(snippetText != ""){
		tmpNode = document.createTextNode(snippetText.substring(0, pos); //other text
		span.appendChild(tmpNode);
	}
}
