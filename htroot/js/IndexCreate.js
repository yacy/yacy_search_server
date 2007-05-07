AJAX_OFF="/env/grafics/empty.gif";
AJAX_ON="/env/grafics/ajax.gif";
timeout="";
function handleResponse(){
    if(http.readyState == 4){
        var response = http.responseXML;

		// getting the document title
        title="";		
        if(response.getElementsByTagName("title")[0].firstChild!=null){
	        title=response.getElementsByTagName("title")[0].firstChild.nodeValue;
	    }
		document.getElementById("title").innerHTML=title;
		
		// deterime if crawling is allowed by the robots.txt
        robotsOK="";		
        if(response.getElementsByTagName("robots")[0].firstChild!=null){
	        robotsOK=response.getElementsByTagName("robots")[0].firstChild.nodeValue;
	    }
        robotsOKspan=document.getElementById("robotsOK");
        if(robotsOKspan.firstChild){
	        robotsOKspan.removeChild(robotsOKspan.firstChild);
        }
        if(robotsOK==1){
        	img=document.createElement("img");
        	img.setAttribute("src", "/env/grafics/ok.png");
        	img.setAttribute("width", "32px");
        	img.setAttribute("height", "32px");
        	robotsOKspan.appendChild(img);
        }else if(robotsOK==0){
			img=document.createElement("img");
        	img.setAttribute("src", "/env/grafics/bad.png");
        	img.setAttribute("width", "32px");
        	img.setAttribute("height", "32px");
        	robotsOKspan.appendChild(img);
        	robotsOKspan.appendChild(img);
        }else{
	        robotsOKspan.appendChild(document.createTextNode(""));
	        document.getElementById("robotsOK").innerHTML="";
        }		
		
		// getting the sitemap URL contained in the robots.txt
		sitemap="";		
        if(response.getElementsByTagName("sitemap")[0].firstChild!=null){
	        sitemap=response.getElementsByTagName("sitemap")[0].firstChild.nodeValue;
	    }		
		document.getElementsByName("sitemapURL")[0].value=sitemap;
		document.getElementById("sitemap").disabled=false;
        
		// clear the ajax image
		document.getElementsByName("ajax")[0].setAttribute("src", AJAX_OFF);
    }
}
function changed(){
	window.clearTimeout(timeout);
	timeout=window.setTimeout("loadInfos()", 1500);
}
function loadInfos(){
	// displaying ajax image
	document.getElementsByName("ajax")[0].setAttribute("src",AJAX_ON);	
	
	url=document.getElementsByName("crawlingURL")[0].value;
	sndReq('/xml/util/getpageinfo_p.xml?actions=title,robots&url='+url);
}
