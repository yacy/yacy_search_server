AJAX_OFF="/env/grafics/empty.gif";
AJAX_ON="/env/grafics/ajax.gif";

function handleResponse(){
    if(http.readyState == 4){
        var response = http.responseXML;
        title=response.getElementsByTagName("title")[0].firstChild.nodeValue;
        tags_field=document.getElementById("tags");
        document.getElementById("title").value=title;
        
        tags=response.getElementsByTagName("tag");
        for(i=0;i<tags.length-1;i++){
        	tags_field.value+=tags[i].getAttribute("name")+",";
        }
        tags_field.value+=tags[tags.length-1].getAttribute("name");
		
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
