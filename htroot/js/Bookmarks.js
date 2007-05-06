AJAX_OFF="/env/grafics/empty.gif";
AJAX_ON="/env/grafics/ajax.gif";

function handleResponse(){
    if(http.readyState == 4){
        var response = http.responseXML;
        title=response.getElementsByTagName("title")[0].firstChild.nodeValue;
        document.getElementsByName("title")[0].value=title;
		
		// remove the ajax image
		document.getElementsByName("ajax")[0].setAttribute("src", AJAX_OFF);
    }
}
function loadTitle(){
	// displaying ajax image
	document.getElementsByName("ajax")[0].setAttribute("src",AJAX_ON);
	
	url=document.getElementsByName("url")[0].value;
	if(document.getElementsByName("title")[0].value==""){
		sndReq('/xml/util/getpageinfo_p.xml?actions=title&url='+url);
	}
}
