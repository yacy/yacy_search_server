
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
	var req=createRequestObject();
	req.open('get', '/xml/snippet.xml?url=' + escape(url) + '&search=' + escape(query),true);
	req.onreadystatechange = function () {handleState(req)};
	req.send(null);
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
	span.removeChild(span.firstChild);
	
	if (status < 11) {
		span.className = "snippetLoaded";
		//span.setAttribute("class", "snippetLoaded");
	} else {
		span.className = "snippetError";
		//span.setAttribute("class", "snippetError");
	}
	
	var snippetNode = document.createTextNode(snippetText);
	span.appendChild(snippetNode);
}