DELETE_STRING="delete";
BAR_IMG1="/env/grafics/green-block.png";
BAR_IMG2="/env/grafics/red-block.png";
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
	statusTag=getFirstChild(statusResponse, "status")
	
	ppm=getValue(getFirstChild(statusTag, "ppm"))
	
	var ppmNum = document.getElementById("ppmNum");
	removeAllChildren(ppmNum);
	ppmNum.appendChild(document.createTextNode(ppm));
	
	var ppmSpan = document.getElementById("ppmSpan");
	removeAllChildren(ppmSpan);
	for(i=0;i<ppm;i++){
		img=document.createElement("img");
		img.setAttribute("src", BAR_IMG1);
		ppmSpan.appendChild(img);
	}
	
	var wordCache=getValue(getFirstChild(statusTag, "wordCacheWSize"));
	var wordCacheMax=getValue(getFirstChild(statusTag, "wordCacheMaxCount"));

	wordCacheNum=document.getElementById("wordcacheNum");
	removeAllChildren(wordCacheNum);
	wordCacheNum.appendChild(document.createTextNode(wordCache+"/"+wordCacheMax));
	
	wordCacheSpan=document.getElementById("wordcacheSpan");
	removeAllChildren(wordCacheSpan);
	var img;
	var percent=Math.round(wordCache/wordCacheMax*100);
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
		clearTable(document.getElementById("queueTable"), 1);
	
		indexingqueue=getFirstChild(xml, "indexingqueue");
		updateTable(indexingqueue, "indexing");
		
		indexingqueue_size=getValue(getFirstChild(indexingqueue, "size"));
		indexingqueue_max=getValue(getFirstChild(indexingqueue, "max"));
		document.getElementById("indexingqueuesize").firstChild.nodeValue=indexingqueue_size;
		document.getElementById("indexingqueuemax").firstChild.nodeValue=indexingqueue_max;
		
		dbsize=getFirstChild(xml, "dbsize");
		urlpublictextSize=getValue(getFirstChild(dbsize, "urlpublictext"));
		rwipublictextSize=getValue(getFirstChild(dbsize, "rwipublictext"));
		document.getElementById("urldbsize").firstChild.nodeValue=urlpublictextSize;
		document.getElementById("rwidbsize").firstChild.nodeValue=rwipublictextSize;
		
		loaderqueue=getFirstChild(xml, "loaderqueue");
		updateTable(loaderqueue, "loader");
		
		loaderqueue_size=getValue(getFirstChild(loaderqueue, "size"));
		loaderqueue_max=getValue(getFirstChild(loaderqueue, "max"));
		document.getElementById("loaderqueuesize").firstChild.nodeValue=loaderqueue_size;
		document.getElementById("loaderqueuemax").firstChild.nodeValue=loaderqueue_max;
		
		localcrawlerqueue=getFirstChild(xml, "localcrawlerqueue");
		localcrawlerqueue_size=getValue(getFirstChild(localcrawlerqueue, "size"));
		document.getElementById("localcrawlerqueuesize").firstChild.nodeValue=localcrawlerqueue_size;
		updateTable(localcrawlerqueue, "local crawler");
		
		remotecrawlerqueue=getFirstChild(xml, "remotecrawlerqueue");
		updateTable(remotecrawlerqueue, "remoteCrawlerTable");
		
		remotecrawlerqueue_size=getValue(getFirstChild(remotecrawlerqueue, "size"));
		document.getElementById("remotecrawlerqueuesize").firstChild.nodeValue=remotecrawlerqueue_size;
		updateTable(remotecrawlerqueue, "remote crawler");
	}
}

function updateTable(indexingqueue, tablename){
	indexingTable=document.getElementById("queueTable");
	entries=indexingqueue.getElementsByTagName("entry");
        
    dark=false;
    for(i=0;i<entries.length;i++){
		profile=getValue(getFirstChild(entries[i], "profile"));
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
		if (tablename=="indexingTable")
			deletebutton=createLinkCol("IndexCreateIndexingQueue_p.html?deleteEntry="+hash, DELETE_STRING);
		else
			deletebutton=createCol("");
		row=createIndexingRow(tablename, profile, initiator, depth, modified, anchor, url, size, deletebutton);
		
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

function createIndexingRow(queue, profile, initiator, depth, modified, anchor, url, size, deletebutton){
    row=document.createElement("tr");
    row.setAttribute("height", 10);
    row.appendChild(createCol(queue));
    row.appendChild(createCol(profile));
	row.appendChild(createCol(initiator));
	row.appendChild(createCol(depth));
	row.appendChild(createCol(modified));
	row.appendChild(createCol(anchor));
	row.appendChild(createLinkCol(url, url));
	row.appendChild(createCol(size));
	row.appendChild(deletebutton);
	return row;
}
