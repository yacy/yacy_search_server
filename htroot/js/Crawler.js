DELETE_STRING="delete";
BAR_IMG1="/env/grafics/green-block.png";
BAR_IMG2="/env/grafics/red-block.png";
WORDCACHEBAR_LENGTH=1/4;

var statusRPC;
var refreshInterval=2;
var wait=0;
var changing=false; //change the interval
var statusLoaded=true;

function initCrawler(){
    refresh();
    //loadInterval=window.setInterval("refresh()", refreshInterval*1000);
    countInterval=window.setInterval("countdown()", 1000);
}

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
	var newInterval=document.getElementById("nextUpdate").value;
        // make sure that only positive intervals can be set
        if(newInterval>0){
                refreshInterval=newInterval;
        }
	refresh();
	countInterval=window.setInterval("countdown()", 1000);
	changing=false;
}

function countdown(){
	if(statusLoaded){
        wait--;
		if (wait == 0) {
			refresh();
		}
	}
}

function refresh(){
	wait=refreshInterval;
	statusLoaded=false;
	requestStatus();
	getRSS("/api/feed.xml?count=20&set=REMOTEINDEXING,LOCALINDEXING&time=" + (new Date()).getTime());
}

function requestStatus(){
	statusRPC=createRequestObject();
	statusRPC.open('get', '/api/status_p.xml?html=');
	statusRPC.onreadystatechange = handleStatus;
	statusRPC.send(null);
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
	
	// ppmBar start
	var ppmBar = document.getElementById("ppmbar");
	var ppmBarMaxRead = document.getElementById("customPPM");
	
    var ppmforppmbar = ppm.replace(/\.*/g,"");	
	ppmBar.setAttribute("value", ppmforppmbar);
    ppmBar.setAttribute("max", ppmBarMaxRead.value);
	// ppmBar end
	
	// traffic output (no bar up to now)
    traffic = getFirstChild(statusTag, "traffic");
    trafficCrawlerValue = getValue(getFirstChild(traffic, "crawler"));
    trafCrawlerSpan = document.getElementById("trafficCrawler");
    removeAllChildren(trafCrawlerSpan);
	trafCrawlerSpan.appendChild(document.createTextNode(Math.round((trafficCrawlerValue) / 1024 / 10.24) / 100));
	
	dbsize=getFirstChild(statusTag, "dbsize");
	urlpublictext=getValue(getFirstChild(dbsize, "urlpublictext"));
	urlpublictextSegmentCount=getValue(getFirstChild(dbsize, "urlpublictextSegmentCount"));
	webgraph=getValue(getFirstChild(dbsize, "webgraph"));
	webgraphSegmentCount=getValue(getFirstChild(dbsize, "webgraphSegmentCount"));
	citation=getValue(getFirstChild(dbsize, "citation"));
	citationSegmentCount=getValue(getFirstChild(dbsize, "citationSegmentCount"));
	rwipublictext=getValue(getFirstChild(dbsize, "rwipublictext"));
	rwipublictextSegmentCount=getValue(getFirstChild(dbsize, "rwipublictextSegmentCount"));
	document.getElementById("urlpublictextSize").firstChild.nodeValue=urlpublictext;
	document.getElementById("urlpublictextSegmentCount").firstChild.nodeValue=urlpublictextSegmentCount;
	document.getElementById("webgraphSize").firstChild.nodeValue=webgraph;
	document.getElementById("webgraphSegmentCount").firstChild.nodeValue=webgraphSegmentCount;
	document.getElementById("citationSize").firstChild.nodeValue=citation;
	document.getElementById("citationSegmentCount").firstChild.nodeValue=citationSegmentCount;
	document.getElementById("rwipublictextSize").firstChild.nodeValue=rwipublictext;
	document.getElementById("rwipublictextSegmentCount").firstChild.nodeValue=rwipublictextSegmentCount;

	postprocessing=getFirstChild(statusTag, "postprocessing");
	document.getElementById("postprocessing_status").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "status"));
	document.getElementById("postprocessing_collection").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "collectionRemainingCount"));
	document.getElementById("postprocessing_webgraph").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "webgraphRemainingCount"));
	document.getElementById("postprocessing_remainingTimeMinutes").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "remainingTimeMinutes"));
	document.getElementById("postprocessing_remainingTimeSeconds").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "remainingTimeSeconds"));
	postprocessingElapsedTime=getValue(getFirstChild(postprocessing, "postprocessingElapsedTime"));
	postprocessingRemainingTime=getValue(getFirstChild(postprocessing, "postprocessingRemainingTime"));
	p = 100 * postprocessingElapsedTime / (postprocessingElapsedTime + postprocessingRemainingTime);
	bar="<progress id='postprocessingBar' max='" + p + "' value='100' style='width:94%;'/>";
	document.getElementById("postprocessing_bar").firstChild.nodeValue=bar;
	//document.getElementById("postprocessing_speed").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "speed"));
	
	load=getFirstChild(statusTag, "load");
	document.getElementById("load").firstChild.nodeValue=getValue(load);
	
	loaderqueue=getFirstChild(statusTag, "loaderqueue");	
	loaderqueue_size=getValue(getFirstChild(loaderqueue, "size"));
	loaderqueue_max=getValue(getFirstChild(loaderqueue, "max"));
	document.getElementById("loaderqueuesize").firstChild.nodeValue=loaderqueue_size;
	document.getElementById("loaderqueuemax").firstChild.nodeValue=loaderqueue_max;
	
	localcrawlerqueue=getFirstChild(statusTag, "localcrawlerqueue");
	localcrawlerqueue_size=getValue(getFirstChild(localcrawlerqueue, "size"));
	localcrawlerqueue_state=getValue(getFirstChild(localcrawlerqueue, "state"));
	document.getElementById("localcrawlerqueuesize").firstChild.nodeValue=localcrawlerqueue_size;
	putQueueState("localcrawler", localcrawlerqueue_state);
	
	limitcrawlerqueue=getFirstChild(statusTag, "limitcrawlerqueue");
	limitcrawlerqueue_size=getValue(getFirstChild(limitcrawlerqueue, "size"));
	limitcrawlerqueue_state=getValue(getFirstChild(limitcrawlerqueue, "state"));
	document.getElementById("limitcrawlerqueuesize").firstChild.nodeValue=limitcrawlerqueue_size;
	putQueueState("limitcrawler", limitcrawlerqueue_state);
	
	remotecrawlerqueue=getFirstChild(statusTag, "remotecrawlerqueue");
	remotecrawlerqueue_size=getValue(getFirstChild(remotecrawlerqueue, "size"));
	remotecrawlerqueue_state=getValue(getFirstChild(remotecrawlerqueue, "state"));
	document.getElementById("remotecrawlerqueuesize").firstChild.nodeValue=remotecrawlerqueue_size;
	putQueueState("remotecrawler", remotecrawlerqueue_state);
	
	noloadcrawlerqueue=getFirstChild(statusTag, "noloadcrawlerqueue");
	noloadcrawlerqueue_size=getValue(getFirstChild(noloadcrawlerqueue, "size"));
	noloadcrawlerqueue_state=getValue(getFirstChild(noloadcrawlerqueue, "state"));
	document.getElementById("noloadcrawlerqueuesize").firstChild.nodeValue=noloadcrawlerqueue_size;
	putQueueState("noloadcrawler", noloadcrawlerqueue_state);

	statusLoaded=true;
}

function putQueueState(queue, state) {
	a = document.getElementById(queue + "stateA");
	img = document.getElementById(queue + "stateIMG");
	if (state == "paused") {
		a.href = "Crawler_p.html?continue=" + queue;
		a.title = "Continue this queue (" + state + ")";
		img.src = "/env/grafics/start.gif";
		img.alt = "Continue this queue";
	} else {
		a.href = "Crawler_p.html?pause=" + queue;
		a.title = "Pause this queue (" + state + ")";
		img.src = "/env/grafics/pause.gif";
		img.alt = "Pause this queue";
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

crawllist_head = "<table cellpadding='2' cellspacing='1' ><tr class='TableHeader'><td width='50%'><strong>Title</strong></td><td width='50%'><strong>URL</strong></td></tr>";
crawllist_body = "";
crawllist_tail = "</table>";
function showRSS(RSS) {
  var doc = document.getElementById("crawllist");
  if (doc != null) {
    if (crawllist_body.length > 100000) crawllist_body = "";
    for (var i=0; i<RSS.items.length; i++) {
      crawllist_body = "<tr class='TableCellLight'><td><a href='ViewFile.html?action=info&urlHash=" + RSS.items[i].guid.value + "' class='small' target='_blank' title='" + RSS.items[i].link + "'>" + RSS.items[i].description + "</a></td><td><a href='ViewFile.html?action=info&urlHash=" + RSS.items[i].guid.value + "' class='small' target='_blank' title='" + RSS.items[i].link + "'>" + RSS.items[i].link + "</a></td></tr>" + crawllist_body;
    }
    doc.innerHTML = crawllist_head + crawllist_body + crawllist_tail;
  }
  return true;
}
