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
		//counter.innerHTML='<input type="text" id="newInterval" onblur="newInterval()" size="2" />';
		//document.getElementById("newInterval").focus();
		counter.value=refreshInterval;
		changing=true;
	}
}
function newInterval(){
	refreshInterval=document.getElementById("nextUpdate").value;
	refresh();
	countInterval=window.setInterval("countdown()", 1000);
	changing=false;
}
function countdown(){
	document.getElementById("nextUpdate").value=wait;
	wait--;
	if(wait==0){
		refresh();
	}
}
function refresh(){
	wait=refreshInterval;
	requestStatus();
	requestQueues();
}

function requestStatus(){
	statusRPC=createRequestObject();
	statusRPC.open('get', '/xml/status_p.xml?html=');
	statusRPC.onreadystatechange = handleStatus;
	statusRPC.send(null);
}
function requestQueues(){
	queuesRPC=createRequestObject();
	queuesRPC.open('get', '/xml/queues_p.xml?html=');
	queuesRPC.onreadystatechange = handleQueues;
	queuesRPC.send(null);

}

function handleStatus(){
    if(statusRPC.readyState != 4){
		return;
	}
	var statusResponse = statusRPC.responseXML;
	statusTag=getFirstChild(statusResponse, "status");
	
	ppm=getValue(getFirstChild(statusTag, "ppm"));
	
	var ppmNum = document.getElementById("ppmNum");
	removeAllChildren(ppmNum);
	ppmNum.appendChild(document.createTextNode(ppm));
	
	var ppmSpan = document.getElementById("ppmSpan");
	removeAllChildren(ppmSpan);
	for(i = 0; i < ppm / 10; i++){
		img=document.createElement("img");
		img.setAttribute("src", BAR_IMG1);
		ppmSpan.appendChild(img);
	}
	
	// traffic output (no bar up to now)
    traffic = getFirstChild(statusTag, "traffic");
    trafficCrawler = getValue(getFirstChild(traffic, "crawler"));
    trafCrawlerSpan = document.getElementById("trafficCrawler");
    removeAllChildren(trafCrawlerSpan);
	trafCrawlerSpan.appendChild(document.createTextNode(Math.round((trafficCrawler) / 1024 / 10.24) / 100));
    
	var wordCache=getValue(getFirstChild(statusTag, "wordCacheWCount"));
	var wordCacheSize=getValue(getFirstChild(statusTag, "wordCacheWSize"));
	var wordCacheMax=getValue(getFirstChild(statusTag, "wordCacheMaxCount"));
	var wordCacheMaxSize=getValue(getFirstChild(statusTag, "wordCacheMaxSize"));

	wordCacheNum=document.getElementById("wordcacheNum");
	removeAllChildren(wordCacheNum);
	wordCacheNum.appendChild(document.createTextNode(wordCacheSize+"/"+wordCacheMaxSize));
	
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
	//xml=getFirstChild(queuesResponse);
	xml=getFirstChild(queuesResponse, "queues");
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
		localcrawlerqueue_state=getValue(getFirstChild(localcrawlerqueue, "state"));
		document.getElementById("localcrawlerqueuesize").firstChild.nodeValue=localcrawlerqueue_size;
		putQueueState("localcrawler", localcrawlerqueue_state);
		
		updateTable(localcrawlerqueue, "local crawler");
		
		limitcrawlerqueue=getFirstChild(xml, "limitcrawlerqueue");
		updateTable(limitcrawlerqueue, "limitCrawlerTable");
		limitcrawlerqueue_size=getValue(getFirstChild(limitcrawlerqueue, "size"));
		limitcrawlerqueue_state=getValue(getFirstChild(limitcrawlerqueue, "state"));
		document.getElementById("limitcrawlerqueuesize").firstChild.nodeValue=limitcrawlerqueue_size;
		putQueueState("limitcrawler", limitcrawlerqueue_state);
		updateTable(limitcrawlerqueue, "limit crawler");
		
		remotecrawlerqueue=getFirstChild(xml, "remotecrawlerqueue");
		updateTable(remotecrawlerqueue, "remoteCrawlerTable");
		remotecrawlerqueue_size=getValue(getFirstChild(remotecrawlerqueue, "size"));
		remotecrawlerqueue_state=getValue(getFirstChild(remotecrawlerqueue, "state"));
		document.getElementById("remotecrawlerqueuesize").firstChild.nodeValue=remotecrawlerqueue_size;
		putQueueState("remotecrawler", remotecrawlerqueue_state);
		updateTable(remotecrawlerqueue, "remote crawler");
	}
}

function putQueueState(queue, state) {
	a = document.getElementById(queue + "stateA");
	img = document.getElementById(queue + "stateIMG");
	if (state == "paused") {
		a.href = "WatchCrawler_p.html?continue=" + queue;
		a.title = "Continue this queue";
		img.src = "/env/grafics/start.gif";
		img.alt = "Continue this queue";
	} else {
		a.href = "WatchCrawler_p.html?pause=" + queue;
		a.title = "Pause this queue";
		img.src = "/env/grafics/stop.gif";
		img.alt = "Pause this queue";
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

function shortenURL(url) {
	if (url.length > 80) {
		return url.substr(0, 80) + "...";
	} else {
		return url;
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
	row.appendChild(createLinkCol(url, shortenURL(url)));
	row.appendChild(createCol(size));
	row.appendChild(deletebutton);
	return row;
}
