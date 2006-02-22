DELETE_STRING="delete";
BAR_IMG1="/env/grafics/green-bar.png";
BAR_IMG2="/env/grafics/red-bar.png";
WORDCACHEBAR_LENGTH=1/4;


var statusRPC;
var queuesRPC;
var refreshInterval=5;
var wait=0;
var changing=false; //change the interval

refresh();
//loadInterval=window.setInterval("refresh()", refreshInterval*1000);
countInterval=window.setInterval("countdown()", 1000);

function changeInterval(){
	if(!changing){
		window.clearInterval(countInterval);
		counter=document.getElementById("nextUpdate");
		counter.innerHTML='<input type="text" id="newInterval" onblur="newInterval()" size="2" />';
		document.getElementById("newInterval").focus();
		changing=true;
	}
}
function newInterval(){
	refreshInterval=document.getElementById("newInterval").value;
	refresh();
	countInterval=window.setInterval("countdown()", 1000);
	changing=false;
}
function countdown(){
	document.getElementById("nextUpdate").innerHTML=wait;
	wait--;
	if(wait==0){
		refresh()
	}
}
function refresh(){
	wait=refreshInterval;
	requestStatus();
	requestQueues();
}

function requestStatus(){
	statusRPC=createRequestObject()
	statusRPC.open('get', '/xml/status_p.xml');
	statusRPC.onreadystatechange = handleStatus;
	statusRPC.send(null)
}
function requestQueues(){
	queuesRPC=createRequestObject()
	queuesRPC.open('get', '/xml/queues_p.xml');
	queuesRPC.onreadystatechange = handleQueues;
	queuesRPC.send(null);

}



function handleStatus(){
    if(statusRPC.readyState != 4){
		return;
	}
	var statusResponse = statusRPC.responseXML;
	statusTag=getFirstChild(getFirstChild(statusResponse, ""), "status")
	
	ppm=getValue(getFirstChild(statusTag, "ppm"))
	var ppmSpan = document.getElementById("ppm");
	removeAllChildren(ppmSpan);
	ppmSpan.appendChild(document.createTextNode(ppm));
	ppmSpan.appendChild(document.createElement("br"));
	for(i=0;i<ppm;i++){
		img=document.createElement("img");
		img.setAttribute("src", BAR_IMG1);
		ppmSpan.appendChild(img);
	}
	
	var wordCache=getValue(getFirstChild(statusTag, "wordCacheSize"));
	var wordCacheMaxLow=getValue(getFirstChild(statusTag, "wordCacheMaxLow"));
	var wordCacheMaxHigh=getValue(getFirstChild(statusTag, "wordCacheMaxHigh"));
	//use Low as default, but High if there are more wordcaches
	var wordCacheMax=wordCacheMaxLow;
	if(wordCache>wordCacheMax){
		wordCacheMax=wordCacheMaxHigh;
	}
	var percent=Math.round(wordCache/wordCacheMax*100);

	
	wordCacheSpan=document.getElementById("wordcache");

	removeAllChildren(wordCacheSpan);
	wordCacheSpan.appendChild(document.createTextNode(wordCache+"/"+wordCacheMax));
	wordCacheSpan.appendChild(document.createElement("br"));
	var img;
	for(i=0;i<percent*WORDCACHEBAR_LENGTH;i++){
		img=document.createElement("img");
		img.setAttribute("src", BAR_IMG2);
		wordCacheSpan.appendChild(img);
	}
	for(i=0;i<(100-percent)*WORDCACHEBAR_LENGTH;i++){
		img=document.createElement("img");
		img.setAttribute("src", BAR_IMG1);
		wordCacheSpan.appendChild(img);
	}
}


function handleQueues(){
    if(queuesRPC.readyState != 4){
		return;
	}
	var queuesResponse = queuesRPC.responseXML;
	xml=getFirstChild(queuesResponse);
	if(queuesResponse != null){
		indexingqueue=getFirstChild(xml, "indexingqueue");
		createIndexingTable(indexingqueue);
		indexingqueue_size=getValue(getFirstChild(indexingqueue, "size"));
		indexingqueue_max=getValue(getFirstChild(indexingqueue, "max"));
		document.getElementById("indexingqueuesize").firstChild.nodeValue=indexingqueue_size;
		document.getElementById("indexingqueuemax").firstChild.nodeValue=indexingqueue_max;
		
		loaderqueue=getFirstChild(xml, "loaderqueue");
		createLoaderTable(getFirstChild(xml, "loaderqueue"));
		loaderqueue_size=getValue(getFirstChild(loaderqueue, "size"));
		loaderqueue_max=getValue(getFirstChild(loaderqueue, "max"));
		document.getElementById("loaderqueuesize").firstChild.nodeValue=loaderqueue_size;
		document.getElementById("loaderqueuemax").firstChild.nodeValue=loaderqueue_max;
		
		localcrawlerqueue=getFirstChild(xml, "localcrawlerqueue");
		localcrawlerqueue_size=getValue(getFirstChild(localcrawlerqueue, "size"));
		document.getElementById("localcrawlerqueuesize").firstChild.nodeValue=localcrawlerqueue_size;
		createLocalCrawlerTable(localcrawlerqueue);
		
		remotecrawlerqueue=getFirstChild(xml, "remotecrawlerqueue");
		createRemoteCrawlerTable(remotecrawlerqueue);
		remotecrawlerqueue_size=getValue(getFirstChild(remotecrawlerqueue, "size"));
		document.getElementById("remotecrawlerqueuesize").firstChild.nodeValue=remotecrawlerqueue_size;
		createRemoteCrawlerTable(remotecrawlerqueue);
	}
}
function removeAllChildren(element){
	child=element.firstChild;
	while(child!=null){
		element.removeChild(child);
		child=element.firstChild;
	}
}
function clearTable(table, numSkip){
	if(numSkip==null){
		numSkip=0;
	}
	row=getFirstChild(getFirstChild(table, "tbody"), "tr");
	//skip numSkip rows
	for(i=0;i<numSkip;i++){
		row=getNextSibling(row, "tr");
	}
    while(row != null){ //delete old entries
        getFirstChild(table, "tbody").removeChild(row);
   		row=getFirstChild(getFirstChild(table, "tbody"), "tr");
		//skip numSkip rows
		for(i=0;i<numSkip;i++){
			row=getNextSibling(row, "tr");
		}
    }
}
function createLoaderTable(loaderqueue){
	entries=loaderqueue.getElementsByTagName("entry");
	loaderTable=document.getElementById("loaderTable");
	clearTable(loaderTable, 1);
	dark=false;
    for(i=0;i<entries.length;i++){
		initiator=getValue(getFirstChild(entries[i], "initiator"));
		depth=getValue(getFirstChild(entries[i], "depth"));
		modified=getValue(getFirstChild(entries[i], "modified"));
		anchor=getValue(getFirstChild(entries[i], "anchor"));
		url=getValue(getFirstChild(entries[i], "url"));
		
		row=createLoaderRow(initiator, depth, modified, anchor, url);
		
		//create row
		if(dark){
            row.setAttribute("class", "TableCellDark");
        }else{
            row.setAttribute("class", "TableCellLight");
        }
        getFirstChild(loaderTable, "tbody").appendChild(row);
        dark=!dark;
    }	
}
function createIndexingTable(indexingqueue){
	indexingTable=document.getElementById("indexingTable");
	entries=indexingqueue.getElementsByTagName("entry");
    clearTable(indexingTable, 1);
        
    dark=false;
    for(i=0;i<entries.length;i++){
		initiator=getValue(getFirstChild(entries[i], "initiator"));
		depth=getValue(getFirstChild(entries[i], "depth"));
		modified=getValue(getFirstChild(entries[i], "modified"));
		anchor=getValue(getFirstChild(entries[i], "anchor"));
		url=getValue(getFirstChild(entries[i], "url"));
		size=getValue(getFirstChild(entries[i], "size"));
		hash=getValue(getFirstChild(entries[i], "hash"));
		inProcess=false;
		if(getValue(getFirstChild(entries[i], "inProcess"))=="true"){
			inProcess=true;
		}
		
		row=createIndexingRow(initiator, depth, modified, anchor, url, size, hash);
		
		//create row
		if(inProcess){
            row.setAttribute("class", "TableCellActive");
        }else if(dark){
            row.setAttribute("class", "TableCellDark");
        }else{
            row.setAttribute("class", "TableCellLight");
        }
        getFirstChild(indexingTable, "tbody").appendChild(row);
        dark=!dark;
    }
}
function createLocalCrawlerTable(localcrawlerqueue){
	localCrawlerTable=document.getElementById("localCrawlerTable");
	entries=localcrawlerqueue.getElementsByTagName("entry");
    clearTable(localCrawlerTable, 1);
        
    dark=false;
    for(i=0;i<entries.length;i++){
		initiator=getValue(getFirstChild(entries[i], "initiator"));
		depth=getValue(getFirstChild(entries[i], "depth"));
		modified=getValue(getFirstChild(entries[i], "modified"));
		anchor=getValue(getFirstChild(entries[i], "anchor"));
		url=getValue(getFirstChild(entries[i], "url"));
		//hash=getValue(getFirstChild(entries[i], "hash"));
		inProcess=false;
		if(getValue(getFirstChild(entries[i], "inProcess"))=="true"){
			inProcess=true;
		}
		
		row=createLocalCrawlerRow(initiator, depth, modified, anchor, url);
		
		//create row
		if(inProcess){
            row.setAttribute("class", "TableCellActive");
        }else if(dark){
            row.setAttribute("class", "TableCellDark");
        }else{
            row.setAttribute("class", "TableCellLight");
        }
        getFirstChild(localCrawlerTable, "tbody").appendChild(row);
        dark=!dark;
    }
}
function createRemoteCrawlerTable(remotecrawlerqueue){
	remoteCrawlerTable=document.getElementById("remoteCrawlerTable");
	entries=remotecrawlerqueue.getElementsByTagName("entry");
    clearTable(remoteCrawlerTable, 1);
        
    dark=false;
    for(i=0;i<entries.length;i++){
		profile=getValue(getFirstChild(entries[i], "profile"));
		depth=getValue(getFirstChild(entries[i], "depth"));
		modified=getValue(getFirstChild(entries[i], "modified"));
		anchor=getValue(getFirstChild(entries[i], "anchor"));
		url=getValue(getFirstChild(entries[i], "url"));
		//hash=getValue(getFirstChild(entries[i], "hash"));
		inProcess=false;
		if(getValue(getFirstChild(entries[i], "inProcess"))=="true"){
			inProcess=true;
		}
		
		row=createRemoteCrawlerRow(profile, depth, modified, anchor, url);
		
		//create row
		if(inProcess){
            row.setAttribute("class", "TableCellActive");
        }else if(dark){
            row.setAttribute("class", "TableCellDark");
        }else{
            row.setAttribute("class", "TableCellLight");
        }
        getFirstChild(remoteCrawlerTable, "tbody").appendChild(row);
        dark=!dark;
    }
}
function getValue(element){
	if(element == null){
		return "";
	}else if(element.nodeType == 3){ //Textnode
		return element.nodeValue;
	}else if(element.firstChild != null && element.firstChild.nodeType == 3){
		return element.firstChild.nodeValue;
	}
	return "";
}
function getFirstChild(element, childname){
	if(childname==null){
		childname="";
	}
	if(element == null){
		return null;
	}
	child=element.firstChild;
	while(child != null){
		if(child.nodeType!=3 && (child.nodeName.toLowerCase()==childname.toLowerCase() || childname=="")){
			return child;
		}
		child=child.nextSibling;
	}
	return null;
}
function getNextSibling(element, childname){
	if(childname==null){
		childname="";
	}
	if(element == null){
		return null;
	}
	child=element.nextSibling;
	while(child != null){
		if(child.nodeType==1 && (child.nodeName.toLowerCase()==childname.toLowerCase() || childname=="")){
			return child;
		}
		child=child.nextSibling;
	}
	return null;
}


function createCol(content){
	col=document.createElement("td");
	text=document.createTextNode(content);
	col.appendChild(text);
	return col;
}
function createIndexingRow(initiator, depth, modified, anchor, url, size, hash){
    row=document.createElement("tr");
	row.appendChild(createCol(initiator));
	row.appendChild(createCol(depth));
	row.appendChild(createCol(modified));
	row.appendChild(createCol(anchor));
	row.appendChild(createLinkCol(url, url));
	row.appendChild(createCol(size));
	row.appendChild(createLinkCol("IndexCreateIndexingQueue_p.html?deleteEntry="+hash, DELETE_STRING));
	return row;
}
function createLoaderRow(initiator, depth, modified, anchor, url){
    row=document.createElement("tr");
	row.appendChild(createCol(initiator));
	row.appendChild(createCol(depth));
	row.appendChild(createCol(modified));
	row.appendChild(createCol(anchor));
	row.appendChild(createLinkCol(url, url));
	return row;
}
function createLocalCrawlerRow(initiator, depth, modified, anchor, url){
    row=document.createElement("tr");
	row.appendChild(createCol(initiator));
	row.appendChild(createCol(depth));
	row.appendChild(createCol(modified));
	row.appendChild(createCol(anchor));
	row.appendChild(createLinkCol(url, url));
	return row;
}
function createRemoteCrawlerRow(profile, depth, modified, anchor, url){
    row=document.createElement("tr");
	row.appendChild(createCol(profile));
	row.appendChild(createCol(depth));
	row.appendChild(createCol(modified));
	row.appendChild(createCol(anchor));
	row.appendChild(createLinkCol(url, url));
	return row;
}
function createLinkCol(url, linktext){
	col=document.createElement("td");
	link=document.createElement("a");
	link.setAttribute("href", url);
	link.setAttribute("target", "_blank");
	text=document.createTextNode(linktext);
	link.appendChild(text);
	col.appendChild(link)
	return col
}
