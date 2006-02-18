timeout="";
function handleResponse(){
    if(http.readyState == 4){
        var response = http.responseXML;
        title="";
        robotsOK="";
        if(response.getElementsByTagName("title")[0].firstChild!=null){
	        title=response.getElementsByTagName("title")[0].firstChild.nodeValue;
	    }
        if(response.getElementsByTagName("robots")[0].firstChild!=null){
	        robotsOK=response.getElementsByTagName("robots")[0].firstChild.nodeValue;
	    }
        document.getElementById("title").innerHTML=title;
        robotsOKspan=document.getElementById("robotsOK");
        if(robotsOKspan.firstChild){
	        robotsOKspan.removeChild(robotsOKspan.firstChild);
        }
        if(robotsOK==1){
        	img=document.createElement("img");
        	img.setAttribute("src", "/env/grafics/ok.png");
        	img.setAttribute("width", "24px");
        	img.setAttribute("height", "24px");
        	robotsOKspan.appendChild(img);
        }else if(robotsOK==0){
			img=document.createElement("img");
        	img.setAttribute("src", "/env/grafics/failed.png");
        	img.setAttribute("width", "24px");
        	img.setAttribute("height", "24px");
        	robotsOKspan.appendChild(img);
        	robotsOKspan.appendChild(img);
        }else{
	        robotsOKspan.appendChild(document.createTextNode(""));
	        document.getElementById("robotsOK").innerHTML="";
        }
    }
}
function changed(){
	window.clearTimeout(timeout);
	timeout=window.setTimeout("loadInfos()", 1500);
}
function loadInfos(){
	url=document.getElementsByName("crawlingURL")[0].value;
	sndReq('/xml/util/getpageinfo_p.xml?actions=title,robots&url='+url);
}
