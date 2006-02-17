function handleResponse(){
    if(http.readyState == 4){
        var response = http.responseXML;
        title=response.getElementsByTagName("title")[0].firstChild.nodeValue;
        document.getElementsByName("title")[0].value=title;
    }
}
function loadTitle(){
	url=document.getElementsByName("url")[0].value;
	if(document.getElementsByName("title")[0].value==""){
		sndReq('/xml/util/getpageinfo_p.xml?actions=title&url='+url);
	}
}
