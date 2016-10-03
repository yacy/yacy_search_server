/*    
* Copyright (C) 2005 - 2014 Alexander Schier, Michael Peter Christen, 
* and other YaCy developers (see http://yacy.net/en/Join.html)
*         
* This file is part of YaCy.
* 
* YaCy is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* YaCy is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with YaCy.  If not, see <http://www.gnu.org/licenses/>.
*/

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
	var statusTag = getFirstChild(statusResponse, "status");
	
	var ppm = getValue(getFirstChild(statusTag, "ppm"));
	
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
    var traffic = getFirstChild(statusTag, "traffic");
    var trafficCrawlerValue = getValue(getFirstChild(traffic, "crawler"));
    var trafCrawlerSpan = document.getElementById("trafficCrawler");
    removeAllChildren(trafCrawlerSpan);
	trafCrawlerSpan.appendChild(document.createTextNode(Math.round((trafficCrawlerValue) / 1024 / 10.24) / 100));
	
	var dbsize = getFirstChild(statusTag, "dbsize");
	var urlpublictext = getValue(getFirstChild(dbsize, "urlpublictext"));
	var urlpublictextSegmentCount = getValue(getFirstChild(dbsize, "urlpublictextSegmentCount"));
	var webgraph = getValue(getFirstChild(dbsize, "webgraph"));
	var webgraphSegmentCount = getValue(getFirstChild(dbsize, "webgraphSegmentCount"));
	var citation = getValue(getFirstChild(dbsize, "citation"));
	var citationSegmentCount = getValue(getFirstChild(dbsize, "citationSegmentCount"));
	var rwipublictext = getValue(getFirstChild(dbsize, "rwipublictext"));
	var rwipublictextSegmentCount = getValue(getFirstChild(dbsize, "rwipublictextSegmentCount"));
	document.getElementById("urlpublictextSize").firstChild.nodeValue=urlpublictext;
	document.getElementById("urlpublictextSegmentCount").firstChild.nodeValue=urlpublictextSegmentCount;
	document.getElementById("webgraphSize").firstChild.nodeValue=webgraph;
	document.getElementById("webgraphSegmentCount").firstChild.nodeValue=webgraphSegmentCount;
	document.getElementById("citationSize").firstChild.nodeValue=citation;
	document.getElementById("citationSegmentCount").firstChild.nodeValue=citationSegmentCount;
	document.getElementById("rwipublictextSize").firstChild.nodeValue=rwipublictext;
	document.getElementById("rwipublictextSegmentCount").firstChild.nodeValue=rwipublictextSegmentCount;

	var postprocessing = getFirstChild(statusTag, "postprocessing");
	document.getElementById("postprocessing_status").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "status"));
	document.getElementById("postprocessing_collection").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "collectionRemainingCount"));
	document.getElementById("postprocessing_webgraph").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "webgraphRemainingCount"));
	document.getElementById("postprocessing_remainingTimeMinutes").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "remainingTimeMinutes"));
	document.getElementById("postprocessing_remainingTimeSeconds").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "remainingTimeSeconds"));
	var postprocessingElapsedTime = parseInt(getValue(getFirstChild(postprocessing, "ElapsedTime")));
	var postprocessingRemainingTime = parseInt(getValue(getFirstChild(postprocessing, "RemainingTime")));
	var p = 100 * postprocessingElapsedTime / (postprocessingElapsedTime + postprocessingRemainingTime) || 0;
	document.getElementById("postprocessing_bar").firstChild.setAttribute("value",  p);
	//document.getElementById("postprocessing_speed").firstChild.nodeValue=getValue(getFirstChild(postprocessing, "speed"));
	
	var load = getFirstChild(statusTag, "load");
	document.getElementById("load").firstChild.nodeValue=getValue(load);
	
	var loaderqueue = getFirstChild(statusTag, "loaderqueue");	
	var loaderqueue_size = getValue(getFirstChild(loaderqueue, "size"));
	var loaderqueue_max = getValue(getFirstChild(loaderqueue, "max"));
	document.getElementById("loaderqueuesize").firstChild.nodeValue=loaderqueue_size;
	document.getElementById("loaderqueuemax").firstChild.nodeValue=loaderqueue_max;
	
	var localcrawlerqueue = getFirstChild(statusTag, "localcrawlerqueue");
	var localcrawlerqueue_size = getValue(getFirstChild(localcrawlerqueue, "size"));
	var localcrawlerqueue_state = getValue(getFirstChild(localcrawlerqueue, "state"));
	document.getElementById("localcrawlerqueuesize").firstChild.nodeValue=localcrawlerqueue_size;
	putQueueState("localcrawler", localcrawlerqueue_state);
	
	var limitcrawlerqueue = getFirstChild(statusTag, "limitcrawlerqueue");
	var limitcrawlerqueue_size = getValue(getFirstChild(limitcrawlerqueue, "size"));
	var limitcrawlerqueue_state = getValue(getFirstChild(limitcrawlerqueue, "state"));
	document.getElementById("limitcrawlerqueuesize").firstChild.nodeValue=limitcrawlerqueue_size;
	putQueueState("limitcrawler", limitcrawlerqueue_state);
	
	var remotecrawlerqueue = getFirstChild(statusTag, "remotecrawlerqueue");
	var remotecrawlerqueue_size = getValue(getFirstChild(remotecrawlerqueue, "size"));
	var remotecrawlerqueue_state = getValue(getFirstChild(remotecrawlerqueue, "state"));
	document.getElementById("remotecrawlerqueuesize").firstChild.nodeValue=remotecrawlerqueue_size;
	putQueueState("remotecrawler", remotecrawlerqueue_state);
	
	var noloadcrawlerqueue = getFirstChild(statusTag, "noloadcrawlerqueue");
	var noloadcrawlerqueue_size = getValue(getFirstChild(noloadcrawlerqueue, "size"));
	var noloadcrawlerqueue_state = getValue(getFirstChild(noloadcrawlerqueue, "state"));
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
